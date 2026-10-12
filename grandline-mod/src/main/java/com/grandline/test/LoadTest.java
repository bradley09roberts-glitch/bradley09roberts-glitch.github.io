package com.grandline.test;

import com.grandline.GrandLine;
import com.grandline.combat.Abilities;
import com.grandline.combat.Ability;
import com.grandline.combat.CombatManager;
import com.grandline.core.Chunks;
import com.grandline.core.Perf;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruits;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.registry.ModEntities;
import com.grandline.ship.SeaEncounters;
import com.grandline.ship.ShipClass;
import com.grandline.ship.ShipEntity;
import com.grandline.ship.Shipyard;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.Settlement;
import com.grandline.world.settlement.SettlementType;
import com.grandline.world.weather.SeaWeather;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server load test (env {@code GL_SELFTEST=load}): simulates a full ~20-player server for ~3 minutes on a dedicated
 * server and writes {@code loadtest-report.txt} (and {@code loadtest.jfr}, a Java Flight Recording) to the server
 * directory, then stops the server.
 * <p>
 * Each simulated player is a {@link FakePlayer} added to the overworld like a real player, so it gets vanilla
 * player chunk tickets (the server's view distance), drives natural spawning, NPC population, encounters, notables,
 * weather and entity tracking exactly as a connected player would. FakePlayers do not tick on their own, so the
 * driver here does what a client would cause: it moves them, fires their player-tick events, and pulls and
 * acknowledges chunk batches (so chunk packets are really built). FakePlayers cannot ride or change dimension: sailors
 * stand on their ship's deck and the sea-encounter roll that {@link SeaEncounters} would make for a riding player is
 * made for them here.
 * <p>
 * Roles (for 20 players): 6 town walkers (the biggest town of each sea, then out into the wilds), 6 sailors (launch
 * from a dock and sail into new chunks, turning away from land), 4 fighters (fight NPC gangs with attacks and Devil
 * Fruit / style techniques), 2 swimmers and 2 ships riding out storms. Players join one per second.
 * <p>
 * Options: {@code GL_LOADTEST_PLAYERS} (20), {@code GL_LOADTEST_SECONDS} (180), {@code GL_LOADTEST_JFR=0} to skip
 * the flight recording.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class LoadTest {
    static final boolean ACTIVE = "load".equals(System.getenv("GL_SELFTEST"));
    private static final int PLAYERS = envInt("GL_LOADTEST_PLAYERS", 20);
    private static final int SECONDS = envInt("GL_LOADTEST_SECONDS", 180);
    private static final boolean JFR = !"0".equals(System.getenv("GL_LOADTEST_JFR"));
    private static final int WINDOW = 400; // 20 s
    private static final int SETUP_TICKS = 40, JOIN_EVERY = 20;
    private static final float CLIENT_CHUNKS_PER_TICK = 9f; // vanilla client's starting batch size
    private static final double WALK = 0.216, RUN = 0.28, SWIM = 0.12;

    enum Role { WALKER, SAILOR, FIGHTER, STORM_SWIMMER, STORM_SAILOR }

    private static final Role[] ORDER = {
            Role.WALKER, Role.SAILOR, Role.FIGHTER, Role.STORM_SWIMMER, Role.WALKER, Role.SAILOR, Role.FIGHTER, Role.STORM_SAILOR,
            Role.WALKER, Role.SAILOR, Role.FIGHTER, Role.STORM_SWIMMER, Role.WALKER, Role.SAILOR, Role.FIGHTER, Role.STORM_SAILOR,
            Role.WALKER, Role.SAILOR, Role.WALKER, Role.SAILOR};
    private static final SeaZone[] SEAS = {SeaZone.EAST_BLUE, SeaZone.NORTH_BLUE, SeaZone.WEST_BLUE, SeaZone.SOUTH_BLUE, SeaZone.PARADISE, SeaZone.NEW_WORLD};
    private static final SeaZone[] FIGHT_SEAS = {SeaZone.EAST_BLUE, SeaZone.PARADISE, SeaZone.NEW_WORLD, SeaZone.SOUTH_BLUE};
    private static final String[] RACES = {"human", "fishman", "mink", "giant", "sky_people", "long_arm", "human", "tontatta"};
    private static final String[] FACTIONS = {"pirate", "marine", "pirate", "revolutionary", "civilian"};
    private static final String[] FRUITS = {"mera", "goro", "hie", "magu", "pika", "gomu"};
    private static final String[] STYLES = {"black_leg", "brawler", "black_leg", "brawler"};
    private static final ShipClass[] SHIP_CLASSES = {ShipClass.SLOOP, ShipClass.CARAVEL, ShipClass.BRIGANTINE, ShipClass.GALLEON};

    static final class Sim {
        final int index;
        final Role role;
        FakePlayer fp;
        String where = "";
        Island island;
        SeaZone zone;
        double homeX, homeZ;
        int joinTick = -1;
        // walking / fighting
        final List<int[]> route = new ArrayList<>();
        int wp;
        // sailing
        ShipClass cls = ShipClass.SLOOP;
        ShipEntity ship;
        int slow, launchTries;
        // storms
        double targetX = Double.NaN, targetZ = Double.NaN;
        // fighting
        final List<Ability> kit = new ArrayList<>();
        final List<HumanoidNpc> foes = new ArrayList<>();
        HumanoidNpc target;
        // stats
        double moved;
        int stalled, attacks, abilities, abilityFails, spawnedFoes, kills, seaEncounters, turns, chunksSent, sentThisTick, stormTicks;
        boolean areaReady;

        Sim(int index, Role role) {
            this.index = index;
            this.role = role;
        }
    }

    private static MinecraftServer server;
    private static ServerLevel ow;
    private static WorldPlan plan;
    private static final List<Sim> SIMS = new ArrayList<>();
    private static final Map<UUID, Sim> BY_ID = new HashMap<>();
    private static final Set<String> USED = new HashSet<>();
    private static int tick = -1, endTick, measureStart;
    private static final List<String> SETUP = new ArrayList<>();
    private static Object recording;

    // ------------------------------------------------------------------ measurement state
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static long serverThreadId;
    private static long tickStartNanos, tickStartCpu;
    private static long[] winWall = new long[WINDOW], winCpu = new long[WINDOW];
    private static int winTicks;
    private static long winStartNanos, winStartSrvCpu, winStartWorkerCpu, winStartGcMs, winStartGcCount;
    private static final List<Long> ALL_WALL = new ArrayList<>(), ALL_CPU = new ArrayList<>();
    private static final AtomicInteger NEW_CHUNKS = new AtomicInteger(), LOADED_EVENTS = new AtomicInteger(), CANCELLED_SPAWNS = new AtomicInteger();
    private static int totalNewChunks;
    private static final List<String[]> ROWS = new ArrayList<>();
    private static final List<Map<String, long[]>> WIN_PERF = new ArrayList<>();
    private static final Map<String, long[]> RUN_PERF = new LinkedHashMap<>();
    private static final Map<String, long[]> ENTITY_TICK = new HashMap<>(); // class → {nanos, ticks, max}
    private static final Map<String, long[]> LEVEL_TICK = new LinkedHashMap<>();
    private static long entityTickStart, levelTickStart, levelNanosThisTick, tickGcMs;
    private static final List<long[]> SLOW = new ArrayList<>(); // {wall, tick}
    private static final Map<Long, String> SLOW_INFO = new HashMap<>();
    private static int measuredTicks;
    private static String startLoad = "";

    // ================================================================== lifecycle

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        if (!ACTIVE) return;
        server = e.getServer();
        ow = server.overworld();
        plan = WorldPlan.of(server);
        serverThreadId = Thread.currentThread().threadId();
        tick = 0;
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityTickEvent.Pre.class, LoadTest::onEntityPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, LoadTest::onEntityPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LevelTickEvent.Pre.class, LoadTest::onLevelPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LevelTickEvent.Post.class, LoadTest::onLevelPost);
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, LoadTest::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(ChunkWatchEvent.Sent.class, LoadTest::onChunkSent);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, FinalizeSpawnEvent.class, LoadTest::onFinalizeSpawn);
        GrandLine.LOGGER.info("[LOADTEST] starting: {} players, {} s", PLAYERS, SECONDS);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTickPre(ServerTickEvent.Pre e) {
        if (tick < 0) return;
        tickStartNanos = System.nanoTime();
        tickStartCpu = THREADS.getCurrentThreadCpuTime();
        tickGcMs = gc()[0];
        levelNanosThisTick = 0;
        Perf.tickStart();
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (tick < 0) return;
        try {
            if (tick == 1) setup();
            if (tick >= SETUP_TICKS) drive();
        } catch (Throwable t) {
            GrandLine.LOGGER.error("[LOADTEST] driver failed", t);
            SETUP.add("DRIVER ERROR at tick " + tick + ": " + t);
            endTick = Math.min(endTick == 0 ? tick : endTick, tick);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTickPost(ServerTickEvent.Post e) {
        if (tick < 0) return;
        if (tick >= SETUP_TICKS) {
            long wall = System.nanoTime() - tickStartNanos, cpu = THREADS.getCurrentThreadCpuTime() - tickStartCpu;
            if (tick == SETUP_TICKS) startWindow();
            winWall[winTicks] = wall;
            winCpu[winTicks] = cpu;
            winTicks++;
            ALL_WALL.add(wall);
            ALL_CPU.add(cpu);
            if (wall > 100_000_000L) noteSlowTick(wall, cpu);
            measuredTicks++;
            if (winTicks == WINDOW) endWindow();
        }
        tick++;
        if (endTick > 0 && tick >= endTick) finish();
    }

    // ================================================================== setup

    private static void setup() {
        if (plan == null) {
            SETUP.add("ERROR: the overworld is not the Grand Line ocean world (level-type=grandline:ocean_world)");
            endTick = tick + 1;
            return;
        }
        startLoad = String.format("%.2f", ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage());
        long t0 = System.nanoTime();
        int walkers = 0, sailors = 0, fighters = 0, storms = 0;
        List<double[]> stormSpots = findStorms(4);
        for (int i = 0; i < PLAYERS; i++) {
            Role role = ORDER[i % ORDER.length];
            Sim s = new Sim(i, role);
            switch (role) {
                case WALKER -> setupWalker(s, SEAS[walkers++ % SEAS.length]);
                case SAILOR -> setupSailor(s, SEAS[(sailors++ + 3) % SEAS.length], SHIP_CLASSES[(sailors - 1) % SHIP_CLASSES.length]);
                case FIGHTER -> setupFighter(s, FIGHT_SEAS[fighters++ % FIGHT_SEAS.length]);
                case STORM_SWIMMER, STORM_SAILOR -> {
                    double[] spot = stormSpots.isEmpty() ? new double[]{20000 + storms * 3000, 1500, 0} : stormSpots.get(storms % stormSpots.size());
                    double off = (storms / Math.max(1, stormSpots.size())) * 120.0;
                    storms++;
                    s.homeX = spot[0] + off;
                    s.homeZ = spot[1] + off;
                    s.zone = WorldLayout.zoneAt((int) s.homeX, (int) s.homeZ);
                    s.cls = ShipClass.CARAVEL;
                    s.where = String.format("%s storm (intensity %.2f at start) at %d,%d", s.zone.id(), spot[2], (int) s.homeX, (int) s.homeZ);
                }
            }
            createCharacter(s, i);
            SIMS.add(s);
            BY_ID.put(s.fp.getUUID(), s);
        }
        SETUP.add(String.format("setup: %d simulated players planned in %.0f ms (%d storm cells found)", SIMS.size(), (System.nanoTime() - t0) / 1e6, stormSpots.size()));
        endTick = SETUP_TICKS + SECONDS * 20;
        measureStart = SETUP_TICKS;
        if (JFR) startJfr();
    }

    private static List<Island> islandsIn(SeaZone z) {
        List<Island> out = new ArrayList<>();
        for (Island i : plan.islands) if (i.zone == z && !USED.contains(i.id) && i.radius >= 60) out.add(i);
        out.sort(Comparator.comparing(i -> i.id));
        return out;
    }

    private static int buildings(Island i) {
        int n = 0;
        for (Plot p : i.settlement().plots) if (p.landmark().isEmpty()) n++;
        return n;
    }

    private static void setupWalker(Sim s, SeaZone zone) {
        Island best = null;
        for (Island i : islandsIn(zone)) if (best == null || buildings(i) > buildings(best)) best = i;
        if (best == null) best = plan.byId("loguetown");
        USED.add(best.id);
        s.island = best;
        s.zone = zone;
        Settlement st = best.settlement();
        // a tour of the town: nearest-neighbour order through every building, then out into the wilds and back
        List<int[]> pts = new ArrayList<>();
        for (Plot p : st.plots) pts.add(new int[]{p.centerX(), p.centerZ()});
        int cx = st.hasCenter ? st.centerX : best.x, cz = st.hasCenter ? st.centerZ : best.z;
        int[] cur = {cx, cz};
        while (!pts.isEmpty() && s.route.size() < 30) {
            int[] near = null;
            double bd = Double.MAX_VALUE;
            for (int[] p : pts) {
                double d = Math.hypot(p[0] - cur[0], p[1] - cur[1]);
                if (d < bd) {
                    bd = d;
                    near = p;
                }
            }
            pts.remove(near);
            s.route.add(near);
            cur = near;
        }
        s.route.addAll(landPoints(best, 3, true));
        if (s.route.isEmpty()) s.route.add(new int[]{best.x, best.z});
        s.homeX = s.route.get(0)[0] + 0.5;
        s.homeZ = s.route.get(0)[1] + 0.5;
        s.where = String.format("%s town %s (%s, %d buildings, %d waypoints)", zone.id(), best.id, best.settlementType, buildings(best), s.route.size());
    }

    private static void setupSailor(Sim s, SeaZone zone, ShipClass cls) {
        Island best = null;
        for (Island i : islandsIn(zone)) if (i.settlement().hasDock && (best == null || buildings(i) > buildings(best))) best = i;
        if (best == null) best = plan.byId("shiokaze_isle");
        USED.add(best.id);
        s.island = best;
        s.zone = zone;
        s.cls = cls;
        Settlement st = best.settlement();
        s.homeX = st.dockTipX() + 0.5;
        s.homeZ = st.dockTipZ() + 0.5;
        s.where = String.format("%s %s from the dock of %s", zone.id(), cls.id(), best.id);
    }

    private static void setupFighter(Sim s, SeaZone zone) {
        Island best = null;
        for (Island i : islandsIn(zone)) {
            if (i.settlementType == SettlementType.NONE || i.radius < 160) continue;
            if (best == null || i.radius > best.radius) best = i;
        }
        if (best == null) best = plan.byId("shells_town");
        USED.add(best.id);
        s.island = best;
        s.zone = zone;
        List<int[]> pts = landPoints(best, 1, true);
        int[] p = pts.isEmpty() ? new int[]{best.x, best.z} : pts.get(0);
        s.homeX = p[0] + 0.5;
        s.homeZ = p[1] + 0.5;
        s.where = String.format("%s wilds of %s (%s, tier %d)", zone.id(), best.id, best.settlementType, best.tier);
    }

    /** Dry-land points on an island (from the world plan, nothing loaded), optionally outside its town. */
    private static List<int[]> landPoints(Island isl, int count, boolean outsideTown) {
        List<int[]> out = new ArrayList<>();
        WorldPlan.Sample smp = new WorldPlan.Sample();
        Settlement st = isl.settlement();
        for (double f : new double[]{0.45, 0.6, 0.3, 0.7}) {
            for (int a = 0; a < 12 && out.size() < count; a++) {
                double ang = a * Math.PI / 6 + f;
                int x = isl.x + (int) (Math.cos(ang) * isl.radius * f), z = isl.z + (int) (Math.sin(ang) * isl.radius * f);
                plan.sample(x, z, smp);
                if (smp.island != isl || smp.height < WorldPlan.SEA_LEVEL + 2) continue;
                if (outsideTown && !st.plots.isEmpty() && st.bounds(x, z)) continue;
                boolean far = true;
                for (int[] o : out) if (Math.hypot(o[0] - x, o[1] - z) < isl.radius * 0.3) far = false;
                if (far) out.add(new int[]{x, z});
            }
        }
        return out;
    }

    /** Strong storm cells over open sea, at least 3000 blocks apart: {x, z, intensity}. */
    private static List<double[]> findStorms(int want) {
        List<double[]> out = new ArrayList<>();
        WorldPlan.Sample smp = new WorldPlan.Sample();
        for (int pass = 0; pass < 2 && out.size() < want; pass++) {
            float min = pass == 0 ? 0.6f : 0.35f;
            for (int x = -56000; x <= 56000 && out.size() < want; x += 400)
                for (int z = -29000; z <= 29000 && out.size() < want; z += 400) {
                    SeaZone zone = WorldLayout.zoneAt(x, z);
                    if (zone == SeaZone.RED_LINE || zone == SeaZone.WORLD_EDGE || zone == SeaZone.CALM_BELT) continue;
                    SeaWeather.Here w = SeaWeather.at(ow, x, z);
                    if ((w.type() != SeaWeather.Type.STORM && w.type() != SeaWeather.Type.CYCLONE) || w.intensity() < min) continue;
                    plan.sample(x, z, smp);
                    if (smp.height > WorldPlan.SEA_LEVEL - 6) continue;
                    boolean far = true;
                    for (double[] o : out) if (Math.hypot(o[0] - x, o[1] - z) < 3000) far = false;
                    if (far) out.add(new double[]{x, z, w.intensity()});
                }
        }
        return out;
    }

    private static void createCharacter(Sim s, int i) {
        FakePlayer fp = FakePlayerFactory.get(ow, new GameProfile(UUID.nameUUIDFromBytes(("grandline-loadtest-" + i).getBytes()), "LoadBot" + i));
        s.fp = fp;
        CharacterData d = Characters.get(fp);
        if (d.created) d.archiveAndReset();
        CompoundTag req = new CompoundTag();
        req.putString("name", "Load Bot " + i);
        req.putString("race", RACES[i % RACES.length]);
        req.putString("faction", s.role == Role.FIGHTER ? "pirate" : FACTIONS[i % FACTIONS.length]);
        req.putString("origin", switch (s.zone == null ? SeaZone.EAST_BLUE : s.zone) {
            case NORTH_BLUE -> "north";
            case WEST_BLUE -> "west";
            case SOUTH_BLUE -> "south";
            default -> "east";
        });
        var err = CharacterManager.create(fp, req);
        if (err != null) SETUP.add("character " + i + " not created: " + err.getString());
        fp.setGameMode(GameType.SURVIVAL);
        if (s.role == Role.FIGHTER) {
            int k = s.index / 4;
            String fruit = FRUITS[k % FRUITS.length];
            if (DevilFruits.get(fruit) == null) fruit = DevilFruits.available().isEmpty() ? null : DevilFruits.available().get(k).id;
            String style = STYLES[k % STYLES.length];
            d.fruit = fruit;
            d.knownStyles.add(style);
            d.activeStyle = style;
            if (fruit != null) for (Ability a : Abilities.of(Ability.Source.FRUIT, fruit)) if (usable(a)) s.kit.add(a);
            for (Ability a : Abilities.of(Ability.Source.STYLE, style)) if (usable(a)) s.kit.add(a);
            for (Ability a : s.kit) d.addMastery(a.masteryKey(), 2_000_000);
            s.where += String.format(" — fruit %s, style %s, %d techniques", fruit, style, s.kit.size());
        }
    }

    private static boolean usable(Ability a) {
        return !a.awakened() && !a.id().endsWith("/toggle") && !a.id().endsWith("/transform") && !a.id().contains("form");
    }

    // ================================================================== driving the simulated players

    private static void drive() {
        long t = Perf.start();
        // join one player per second
        if (tick >= SETUP_TICKS && (tick - SETUP_TICKS) % JOIN_EVERY == 0) {
            int idx = (tick - SETUP_TICKS) / JOIN_EVERY;
            if (idx < SIMS.size()) join(SIMS.get(idx));
        }
        t = Perf.lap("sim:join", t);
        for (Sim s : SIMS) {
            if (s.joinTick < 0 || s.fp.isRemoved()) continue;
            switch (s.role) {
                case WALKER -> walk(s);
                case SAILOR -> sail(s);
                case FIGHTER -> fight(s);
                case STORM_SWIMMER -> swim(s);
                case STORM_SAILOR -> stormSail(s);
            }
        }
        t = Perf.lap("sim:actions", t);
        // what the client's own tick would cause on the server: player tick events
        for (Sim s : SIMS) {
            if (s.joinTick < 0 || s.fp.isRemoved()) continue;
            EventHooks.firePlayerTickPre(s.fp);
            EventHooks.firePlayerTickPost(s.fp);
        }
        t = Perf.lap("sim:playerTickEvents", t);
        // chunk batches: send and acknowledge like a client
        for (Sim s : SIMS) {
            if (s.joinTick < 0 || s.fp.isRemoved()) continue;
            s.sentThisTick = 0;
            s.fp.connection.chunkSender.sendNextChunks(s.fp);
            if (s.sentThisTick > 0) s.fp.connection.chunkSender.onChunkBatchReceivedByClient(CLIENT_CHUNKS_PER_TICK);
        }
        Perf.lap("sim:chunkSend", t);
    }

    private static void join(Sim s) {
        FakePlayer fp = s.fp;
        double y = groundOrPlan(s.homeX, s.homeZ);
        if (s.role == Role.STORM_SWIMMER) y = WorldPlan.SEA_LEVEL + 0.6;
        if (s.role == Role.SAILOR || s.role == Role.STORM_SAILOR) y = WorldPlan.SEA_LEVEL + 2;
        fp.moveTo(s.homeX, y, s.homeZ, 0, 0);
        ow.addNewPlayer(fp);
        s.joinTick = tick;
        GrandLine.LOGGER.info("[LOADTEST] {} joined: {} {}", fp.getGameProfile().getName(), s.role, s.where);
    }

    private static void place(Sim s, double x, double y, double z, float yaw) {
        FakePlayer fp = s.fp;
        double d = Math.hypot(x - fp.getX(), z - fp.getZ());
        boolean moved = d > 1e-4 || Math.abs(y - fp.getY()) > 1e-4;
        fp.moveTo(x, y, z, yaw, fp.getXRot());
        fp.setYHeadRot(yaw);
        s.moved += d;
        // a client sends a movement packet every tick while moving (and every second while idle)
        if (moved || (tick - s.joinTick) % 20 == 0) ow.getChunkSource().move(fp);
    }

    /** Loaded right now: a client cannot walk into terrain the server has not finished generating. */
    private static boolean loaded(double x, double z) {
        return Chunks.ready(ow, Mth.floor(x), Mth.floor(z));
    }

    private static double groundOrPlan(double x, double z) {
        if (loaded(x, z)) return ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        WorldPlan.Sample smp = new WorldPlan.Sample();
        plan.sample(Mth.floor(x), Mth.floor(z), smp);
        return Math.max(smp.height, WorldPlan.SEA_LEVEL) + 1;
    }

    private static float yawTo(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Step toward (tx, tz) on foot; false when waiting for the chunk ahead or already there. */
    private static boolean stepToward(Sim s, double tx, double tz, double speed, double stopAt) {
        FakePlayer fp = s.fp;
        double dx = tx - fp.getX(), dz = tz - fp.getZ(), d = Math.hypot(dx, dz);
        if (d <= stopAt) return false;
        double step = Math.min(speed, d - stopAt * 0.5);
        double nx = fp.getX() + dx / d * step, nz = fp.getZ() + dz / d * step;
        if (!loaded(nx, nz)) {
            s.stalled++;
            return false;
        }
        place(s, nx, ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(nx), Mth.floor(nz)), nz, yawTo(dx, dz));
        return true;
    }

    // ------------------------------------------------------------------ walkers

    private static void walk(Sim s) {
        int[] p = s.route.get(s.wp);
        double tx = p[0] + 0.5, tz = p[1] + 0.5;
        if (Math.hypot(tx - s.fp.getX(), tz - s.fp.getZ()) < 1.5) {
            s.wp = (s.wp + 1) % s.route.size();
            return;
        }
        boolean wild = s.island != null && !s.island.settlement().bounds(p[0], p[1]);
        stepToward(s, tx, tz, wild ? RUN : WALK, 1.0);
    }

    // ------------------------------------------------------------------ sailors

    private static void sail(Sim s) {
        if (s.ship == null || !s.ship.isAlive()) {
            s.ship = null;
            if ((tick - s.joinTick) % 20 != 10) return;
            // launch from the dock once the berth is loaded, like a player buying a ship at the shipwright
            boolean ready = true;
            for (int dx = -2; dx <= 2 && ready; dx++)
                for (int dz = -2; dz <= 2 && ready; dz++) if (!loaded(s.homeX + dx * 16, s.homeZ + dz * 16)) ready = false;
            if (!ready) {
                s.stalled += 20;
                return;
            }
            s.ship = Shipyard.spawnAtDock(s.fp, s.fp, s.cls);
            s.launchTries++;
            if (s.ship == null) return;
            s.ship.autopilot = true;
            s.ship.setAnchored(false);
            s.ship.setSail(s.role == Role.STORM_SAILOR ? 2 : 3);
            if (s.role == Role.SAILOR) turn(s, false); // head for open sea
            return;
        }
        ShipEntity ship = s.ship;
        Vec3 deck = ship.toWorld(0, ship.cls.freeboard + 0.05, 0);
        place(s, deck.x, deck.y, deck.z, ship.getYRot());
        if (ship.speed() < 0.04f) s.slow++;
        else s.slow = 0;
        if (s.slow > 40) {
            Vec3 bow = ship.position().add(ship.heading().scale(ship.cls.length / 2.0 + 1));
            if (!loaded(bow.x, bow.z)) s.stalled++; // waiting for the sea ahead to generate
            else {
                turn(s, true); // ran aground or blocked: find open water
                s.slow = 0;
            }
        }
        seaEncounter(s);
    }

    /** Point the ship along the open-sea heading closest to its current one (world plan only, nothing loaded). */
    private static void turn(Sim s, boolean awayFromCurrent) {
        ShipEntity ship = s.ship;
        WorldPlan.Sample smp = new WorldPlan.Sample();
        float yaw0 = ship.getYRot();
        for (int k = awayFromCurrent ? 2 : 0; k < 32; k++) {
            float yaw = yaw0 + (k % 2 == 0 ? 1 : -1) * (k / 2) * 22.5f;
            double hx = -Math.sin(Math.toRadians(yaw)), hz = Math.cos(Math.toRadians(yaw));
            boolean open = true;
            for (int d = 30; d <= 700 && open; d += 30) {
                int x = (int) (ship.getX() + hx * d), z = (int) (ship.getZ() + hz * d);
                if (Math.abs(x) > WorldLayout.WORLD_BORDER - 2000 || Math.abs(z) > WorldLayout.WORLD_EDGE_Z) open = false;
                else {
                    plan.sample(x, z, smp);
                    if (smp.height > WorldPlan.SEA_LEVEL - 5 || smp.redLine) open = false;
                }
            }
            if (open) {
                ship.setYRot(Mth.wrapDegrees(yaw));
                s.turns++;
                return;
            }
        }
        ship.setYRot(Mth.wrapDegrees(yaw0 + 180));
        s.turns++;
    }

    /** The roll {@link SeaEncounters} makes for a player riding a ship at sea (FakePlayers cannot ride). */
    private static void seaEncounter(Sim s) {
        if (ow.getGameTime() % 600 != 0) return;
        FakePlayer fp = s.fp;
        if (!ow.getEntitiesOfClass(ShipEntity.class, fp.getBoundingBox().inflate(250), sh -> sh.flag(ShipEntity.F_NPC)).isEmpty()) return;
        SeaZone z = WorldLayout.zoneAt(fp.getBlockX(), fp.getBlockZ());
        if (z == SeaZone.CALM_BELT || z == SeaZone.RED_LINE) return;
        CharacterData d = Characters.get(fp);
        double chance = 0.12 + (d.bounty > 0 ? 0.1 + Math.min(0.25, Math.log10(d.bounty + 1) / 40) : 0) + z.dangerTier * 0.02;
        if (ow.random.nextDouble() > chance) return;
        boolean marine = d.bounty > 0 ? ow.random.nextFloat() < 0.65f : ow.random.nextFloat() < 0.3f;
        if (SeaEncounters.spawnRaider(ow, fp, marine, z) != null) s.seaEncounters++;
    }

    // ------------------------------------------------------------------ storms

    private static void swim(Sim s) {
        followStorm(s);
        FakePlayer fp = s.fp;
        if (!Double.isNaN(s.targetX) && Math.hypot(s.targetX - fp.getX(), s.targetZ - fp.getZ()) > 40) {
            double dx = s.targetX - fp.getX(), dz = s.targetZ - fp.getZ(), d = Math.hypot(dx, dz);
            double nx = fp.getX() + dx / d * SWIM, nz = fp.getZ() + dz / d * SWIM;
            if (loaded(nx, nz)) place(s, nx, WorldPlan.SEA_LEVEL + 0.6, nz, yawTo(dx, dz));
            else s.stalled++;
        } else if (loaded(fp.getX(), fp.getZ())) place(s, fp.getX(), WorldPlan.SEA_LEVEL + 0.6, fp.getZ(), fp.getYRot());
    }

    private static void stormSail(Sim s) {
        followStorm(s);
        sail(s);
        if (s.ship == null || (tick - s.joinTick) % 10 != 0) return;
        if (!Double.isNaN(s.targetX) && Math.hypot(s.targetX - s.ship.getX(), s.targetZ - s.ship.getZ()) > 150) {
            float want = yawTo(s.targetX - s.ship.getX(), s.targetZ - s.ship.getZ());
            s.ship.setRudder(Mth.clamp(Mth.wrapDegrees(want - s.ship.getYRot()) / 30f, -1, 1));
        } else s.ship.setRudder(0.3f); // circle inside the storm
    }

    /** Every 5 s: stay in the storm, or head for the strongest storm nearby when it has moved on or faded. */
    private static void followStorm(Sim s) {
        FakePlayer fp = s.fp;
        if ((tick - s.joinTick) % 100 != 0) {
            SeaWeather.Here w = SeaWeather.at(ow, fp.getX(), fp.getZ());
            if (w.type() == SeaWeather.Type.STORM || w.type() == SeaWeather.Type.CYCLONE) s.stormTicks++;
            return;
        }
        SeaWeather.Here w = SeaWeather.at(ow, fp.getX(), fp.getZ());
        if ((w.type() == SeaWeather.Type.STORM || w.type() == SeaWeather.Type.CYCLONE) && w.intensity() > 0.45f) {
            s.targetX = w.eyeX();
            s.targetZ = w.eyeZ();
            return;
        }
        double best = 0.3;
        for (int dx = -1200; dx <= 1200; dx += 100)
            for (int dz = -1200; dz <= 1200; dz += 100) {
                SeaWeather.Here o = SeaWeather.at(ow, fp.getX() + dx, fp.getZ() + dz);
                if ((o.type() == SeaWeather.Type.STORM || o.type() == SeaWeather.Type.CYCLONE) && o.intensity() > best) {
                    best = o.intensity();
                    s.targetX = fp.getX() + dx;
                    s.targetZ = fp.getZ() + dz;
                }
            }
    }

    // ------------------------------------------------------------------ fighters

    private static void fight(Sim s) {
        FakePlayer fp = s.fp;
        int age = tick - s.joinTick;
        if (age < 20) return;
        // a client only fights in terrain it has received: wait until the area around the fighter has loaded
        if (!s.areaReady) {
            boolean ready = true;
            for (int dx = -3; dx <= 3 && ready; dx++)
                for (int dz = -3; dz <= 3 && ready; dz++) ready = loaded(fp.getX() + dx * 16, fp.getZ() + dz * 16);
            if (!ready) {
                s.stalled++;
                return;
            }
            s.areaReady = true;
        }
        CharacterData d = Characters.get(fp);
        if (age % 20 == 0) {
            int before = s.foes.size();
            s.foes.removeIf(n -> !n.isAlive());
            s.kills += before - s.foes.size();
            if (s.foes.size() < 3 && loaded(fp.getX(), fp.getZ())) spawnFoes(s, 1 + ow.random.nextInt(2));
            s.target = null;
            double bd = 24 * 24;
            for (HumanoidNpc n : ow.getEntitiesOfClass(HumanoidNpc.class, fp.getBoundingBox().inflate(24), n -> n.isAlive() && (n.getTarget() == fp || s.foes.contains(n)))) {
                double dd = n.distanceToSqr(fp);
                if (dd < bd) {
                    bd = dd;
                    s.target = n;
                }
            }
        }
        HumanoidNpc t = s.target;
        if (t == null || !t.isAlive()) {
            // patrol around home while no one is around
            int[] p = s.route.isEmpty() ? null : s.route.get(s.wp % s.route.size());
            if (p == null || Math.hypot(p[0] - fp.getX(), p[1] - fp.getZ()) < 1.5) {
                s.route.clear();
                s.route.add(new int[]{(int) s.homeX + ow.random.nextInt(25) - 12, (int) s.homeZ + ow.random.nextInt(25) - 12});
                s.wp = 0;
            } else stepToward(s, p[0] + 0.5, p[1] + 0.5, WALK, 1.0);
            return;
        }
        double dist = fp.distanceTo(t);
        if (dist > 2.6) stepToward(s, t.getX(), t.getZ(), RUN, 2.2);
        fp.lookAt(EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
        // keep the gauges up so the fighter keeps acting like an active player at full strength
        d.stamina = d.maxStamina();
        d.hakiGauge = d.maxHakiGauge();
        if (age % 8 == 0 && dist < 4 && CombatManager.attack(fp, ow.random.nextFloat() < 0.2f, 0)) s.attacks++;
        if (age % 40 == 20 && !s.kit.isEmpty() && dist < 18) {
            Ability a = s.kit.get(ow.random.nextInt(s.kit.size()));
            if (CombatManager.useAbility(fp, a, t)) s.abilities++;
            else s.abilityFails++;
        }
    }

    private static void spawnFoes(Sim s, int count) {
        FakePlayer fp = s.fp;
        Island isl = s.island;
        for (int i = 0; i < count; i++) {
            double ang = ow.random.nextDouble() * Math.PI * 2;
            int x = fp.getBlockX() + (int) (Math.cos(ang) * 10), z = fp.getBlockZ() + (int) (Math.sin(ang) * 10);
            if (!loaded(x, z)) continue;
            int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= WorldPlan.SEA_LEVEL) continue;
            HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(ow);
            if (n == null) continue;
            String role = s.zone.isBlue() ? "bandit" : ow.random.nextInt(4) == 0 ? "pirate_captain" : "pirate";
            n.moveTo(x + 0.5, y, z + 0.5, ow.random.nextFloat() * 360, 0);
            n.configure(role, isl == null ? 2 : isl.tier, isl == null ? "" : isl.id, new BlockPos(x, y, z), "", ow.random);
            ow.addFreshEntity(n);
            n.setTarget(fp);
            s.foes.add(n);
            s.spawnedFoes++;
        }
    }

    // ================================================================== measurement

    private static void onEntityPre(EntityTickEvent.Pre e) {
        if (tick >= SETUP_TICKS && !e.getEntity().level().isClientSide) entityTickStart = System.nanoTime();
    }

    private static void onEntityPost(EntityTickEvent.Post e) {
        if (tick < SETUP_TICKS || entityTickStart == 0 || e.getEntity().level().isClientSide) return;
        long dt = System.nanoTime() - entityTickStart;
        entityTickStart = 0;
        long[] a = ENTITY_TICK.computeIfAbsent(e.getEntity().getClass().getSimpleName(), k -> new long[3]);
        a[0] += dt;
        a[1]++;
        if (dt > a[2]) a[2] = dt;
    }

    private static void onLevelPre(LevelTickEvent.Pre e) {
        if (tick >= SETUP_TICKS && !e.getLevel().isClientSide) levelTickStart = System.nanoTime();
    }

    private static void onLevelPost(LevelTickEvent.Post e) {
        if (tick < SETUP_TICKS || levelTickStart == 0 || e.getLevel().isClientSide) return;
        long dt = System.nanoTime() - levelTickStart;
        levelTickStart = 0;
        String key = "level " + e.getLevel().dimension().location().getPath();
        levelNanosThisTick += dt;
        long[] a = LEVEL_TICK.computeIfAbsent(key, k -> new long[3]);
        a[0] += dt;
        a[1]++;
        if (dt > a[2]) a[2] = dt;
    }

    /** Keeps the 12 slowest ticks with what took the time (GC, level ticks, the mod's probes). */
    private static void noteSlowTick(long wall, long cpu) {
        long t = tick - SETUP_TICKS;
        SLOW.add(new long[]{wall, t});
        SLOW_INFO.put(t, String.format("t=%5.1fs wall %7.1f ms, cpu %6.1f, GC %4d ms, level ticks %6.1f, probes: %s",
                t / 20.0, wall / 1e6, cpu / 1e6, gc()[0] - tickGcMs, levelNanosThisTick / 1e6, Perf.tickTop(3)));
        if (SLOW.size() > 12) {
            SLOW.sort((a, b) -> Long.compare(b[0], a[0]));
            SLOW_INFO.remove(SLOW.remove(SLOW.size() - 1)[1]);
        }
    }

    private static void onChunkLoad(ChunkEvent.Load e) {
        if (e.getLevel() instanceof ServerLevel sl && sl.dimension() == Level.OVERWORLD) {
            LOADED_EVENTS.incrementAndGet();
            if (e.isNewChunk()) NEW_CHUNKS.incrementAndGet();
        }
    }

    /** Natural spawns that were built and then cancelled (vanilla hostiles replaced in the ocean world). */
    private static void onFinalizeSpawn(FinalizeSpawnEvent e) {
        if (tick >= SETUP_TICKS && e.isSpawnCancelled()) CANCELLED_SPAWNS.incrementAndGet();
    }

    private static void onChunkSent(ChunkWatchEvent.Sent e) {
        Sim s = BY_ID.get(e.getPlayer().getUUID());
        if (s != null) {
            s.sentThisTick++;
            s.chunksSent++;
        }
    }

    private static long workerCpu() {
        long sum = 0;
        for (ThreadInfo ti : THREADS.getThreadInfo(THREADS.getAllThreadIds())) {
            if (ti == null || !ti.getThreadName().startsWith("Worker-Main")) continue;
            long c = THREADS.getThreadCpuTime(ti.getThreadId());
            if (c > 0) sum += c;
        }
        return sum;
    }

    private static long[] gc() {
        long ms = 0, n = 0;
        for (GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans()) {
            ms += Math.max(0, g.getCollectionTime());
            n += Math.max(0, g.getCollectionCount());
        }
        return new long[]{ms, n};
    }

    private static void startWindow() {
        winTicks = 0;
        winStartNanos = System.nanoTime();
        winStartSrvCpu = THREADS.getThreadCpuTime(serverThreadId);
        winStartWorkerCpu = workerCpu();
        long[] g = gc();
        winStartGcMs = g[0];
        winStartGcCount = g[1];
        NEW_CHUNKS.set(0);
        LOADED_EVENTS.set(0);
        Perf.drain();
        Perf.enabled = true;
    }

    private static void endWindow() {
        long wallNs = System.nanoTime() - winStartNanos;
        long srvCpu = THREADS.getThreadCpuTime(serverThreadId) - winStartSrvCpu;
        long wCpu = workerCpu() - winStartWorkerCpu;
        long[] g = gc();
        int newChunks = NEW_CHUNKS.getAndSet(0), loadEvents = LOADED_EVENTS.getAndSet(0), cancelled = CANCELLED_SPAWNS.getAndSet(0);
        totalNewChunks += newChunks;
        int n = winTicks;
        long[] w = Arrays.copyOf(winWall, n), c = Arrays.copyOf(winCpu, n);
        Arrays.sort(w);
        Arrays.sort(c);
        double secs = wallNs / 1e9;
        // entity census (overworld)
        int npcs = 0, otherNpc = 0, ships = 0, proj = 0, items = 0, xp = 0, bolts = 0, mobs = 0, players = 0, other = 0, all = 0;
        for (Entity e : ow.getAllEntities()) {
            all++;
            if (e instanceof HumanoidNpc) npcs++;
            else if (e instanceof GLNpc) otherNpc++;
            else if (e instanceof ShipEntity) ships++;
            else if (e instanceof AbilityProjectile) proj++;
            else if (e instanceof ItemEntity) items++;
            else if (e instanceof ExperienceOrb) xp++;
            else if (e instanceof LightningBolt) bolts++;
            else if (e instanceof Player) players++;
            else if (e instanceof Mob) mobs++;
            else other++;
        }
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) >> 20;
        int windowIdx = ROWS.size() + 1;
        ROWS.add(new String[]{
                String.valueOf(windowIdx),
                String.format("%d-%d", (windowIdx - 1) * WINDOW / 20, windowIdx * WINDOW / 20),
                String.valueOf((int) SIMS.stream().filter(s -> s.joinTick >= 0).count()),
                String.format("%.1f", n / secs),
                ms(avg(w)), ms(pct(w, 0.95)), ms(w[n - 1]),
                ms(avg(c)), ms(pct(c, 0.95)),
                String.format("%.0f%%", 100.0 * srvCpu / wallNs),
                String.valueOf(newChunks), String.format("%.1f", newChunks / secs),
                newChunks > 0 ? String.format("%.1f", wCpu / 1e6 / newChunks) : "-",
                String.valueOf(ow.getChunkSource().getLoadedChunksCount()),
                String.valueOf(npcs), String.valueOf(otherNpc), String.valueOf(ships), String.valueOf(proj), String.valueOf(items + xp), String.valueOf(bolts), String.valueOf(mobs), String.valueOf(all),
                String.valueOf(usedMb), String.valueOf(g[0] - winStartGcMs) + "/" + (g[1] - winStartGcCount), String.valueOf(cancelled)
        });
        Map<String, long[]> perf = Perf.drain();
        perf.forEach((k, v) -> {
            long[] r = RUN_PERF.computeIfAbsent(k, x -> new long[3]);
            r[0] += v[0];
            r[1] += v[1];
            r[2] = Math.max(r[2], v[2]);
        });
        WIN_PERF.add(perf);
        dumpJfrAsync(); // keep a usable recording and report even if the process is killed before the end
        writePartialReport();
        GrandLine.LOGGER.info("[LOADTEST] window {}: {} TPS, MSPT avg {} p95 {} max {}, cpu avg {}, new chunks {}, loaded {}, npcs {}, ships {}, entities {}, heap {} MB",
                windowIdx, String.format("%.1f", n / secs), ms(avg(w)), ms(pct(w, 0.95)), ms(w[n - 1]), ms(avg(c)), newChunks, ow.getChunkSource().getLoadedChunksCount(), npcs, ships, all, usedMb);
        startWindow();
    }

    private static double avg(long[] a) {
        if (a.length == 0) return 0;
        double s = 0;
        for (long v : a) s += v;
        return s / a.length;
    }

    private static long pct(long[] sorted, double p) {
        if (sorted.length == 0) return 0;
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(p * sorted.length) - 1)];
    }

    private static String ms(double nanos) {
        return String.format("%.2f", nanos / 1e6);
    }

    // ================================================================== JFR

    private static void startJfr() {
        try {
            jdk.jfr.Recording r = new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"));
            r.setName("grandline-loadtest");
            r.setToDisk(true);
            r.setDestination(Path.of("loadtest.jfr"));
            // short waits matter here: the server thread blocking on a chunk that is still generating
            r.enable("jdk.ThreadPark").withThreshold(java.time.Duration.ofMillis(2)).withStackTrace();
            r.start();
            recording = r;
            SETUP.add("JFR recording (profile settings) -> loadtest.jfr");
        } catch (Throwable t) {
            SETUP.add("JFR unavailable: " + t);
        }
    }

    private static void dumpJfrAsync() {
        if (!(recording instanceof jdk.jfr.Recording r)) return;
        Thread t = new Thread(() -> {
            try {
                r.dump(Path.of("loadtest-partial.jfr"));
            } catch (Throwable ignored) {
            }
        }, "loadtest-jfr-dump");
        t.setDaemon(true);
        t.start();
    }

    private static void stopJfr() {
        if (recording == null) return;
        try {
            ((jdk.jfr.Recording) recording).stop(); // writes the destination file
            ((jdk.jfr.Recording) recording).close();
        } catch (Throwable t) {
            SETUP.add("JFR stop failed: " + t);
        }
    }

    // ================================================================== report

    private static void finish() {
        tick = -1;
        Perf.enabled = false;
        stopJfr();
        List<String> out = report(true);
        try {
            Files.write(Path.of("loadtest-report.txt"), out);
        } catch (IOException ex) {
            GrandLine.LOGGER.error("[LOADTEST] could not write report", ex);
        }
        for (String line : out) GrandLine.LOGGER.info("[LOADTEST] {}", line);
        for (Sim s : SIMS) if (s.joinTick >= 0 && !s.fp.isRemoved()) ow.removePlayerImmediately(s.fp, Entity.RemovalReason.DISCARDED);
        GrandLine.LOGGER.info("[LOADTEST] DONE");
        server.halt(false);
    }

    /** Rewrites the report after every window (off-thread), so a run that is killed early still leaves its numbers. */
    private static void writePartialReport() {
        List<String> out = report(false);
        Thread t = new Thread(() -> {
            try {
                Files.write(Path.of("loadtest-report.txt"), out);
            } catch (IOException ignored) {
            }
        }, "loadtest-report");
        t.setDaemon(true);
        t.start();
    }

    private static List<String> report(boolean complete) {
        List<String> out = new ArrayList<>();
        out.add("Grand Line Odyssey — server load test, " + new Date() + (complete ? "" : "  [PARTIAL: " + ROWS.size() + " windows so far]"));
        out.add(String.format("Machine: %d CPUs (shared), max heap %d MB, load average at start %s, Java %s",
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() >> 20, startLoad, System.getProperty("java.version")));
        out.add(String.format("Server: view distance %d, simulation distance %d, seed %d, %d islands",
                server.getPlayerList().getViewDistance(), server.getPlayerList().getSimulationDistance(), ow.getSeed(), plan == null ? 0 : plan.islands.size()));
        out.add(String.format("Run: %d simulated players joining one per second, %d s measured (%d ticks), windows of %d ticks (%d s)",
                SIMS.size(), SECONDS, measuredTicks, WINDOW, WINDOW / 20));
        out.addAll(SETUP);
        out.add("");
        out.add("MSPT = wall time of a full server tick (ServerTickEvent.Pre..Post, including the mod's Post handlers and this driver).");
        out.add("CPU = server-thread CPU time for the same span (robust against other processes on a shared machine).");
        out.add("busy = server-thread CPU over wall time in the window (includes chunk tasks run between ticks).");
        out.add("gen ms/chunk = world-generation worker CPU per newly generated chunk.");
        out.add("spawns built+cancelled = natural spawns whose mob was created and then cancelled at finalize.");
        out.add("");
        String[] head = {"win", "secs", "players", "TPS", "MSPT avg", "p95", "max", "CPU avg", "CPU p95", "busy", "new chunks", "chunks/s", "gen ms/chunk", "loaded",
                "NPCs", "other GL mobs", "ships", "projectiles", "items+xp", "bolts", "vanilla mobs", "entities", "heap MB", "GC ms/n", "spawns built+cancelled"};
        out.addAll(table(head, ROWS));
        out.add("");
        // overall
        long[] w = ALL_WALL.stream().mapToLong(Long::longValue).sorted().toArray();
        long[] c = ALL_CPU.stream().mapToLong(Long::longValue).sorted().toArray();
        int steadyFrom = Math.min(ALL_WALL.size(), SIMS.size() * JOIN_EVERY + WINDOW / 2);
        long[] ws = ALL_WALL.subList(steadyFrom, ALL_WALL.size()).stream().mapToLong(Long::longValue).sorted().toArray();
        long[] cs = ALL_CPU.subList(steadyFrom, ALL_CPU.size()).stream().mapToLong(Long::longValue).sorted().toArray();
        if (w.length > 0) {
            out.add(String.format("WHOLE RUN: MSPT avg %s p95 %s p99 %s max %s | CPU avg %s p95 %s | ticks over 50 ms: %d of %d | new chunks %d",
                    ms(avg(w)), ms(pct(w, 0.95)), ms(pct(w, 0.99)), ms(w[w.length - 1]), ms(avg(c)), ms(pct(c, 0.95)), Arrays.stream(w).filter(v -> v > 50_000_000L).count(), w.length, totalNewChunks));
        }
        if (ws.length > 0) {
            out.add(String.format("STEADY (all players in, from tick %d): MSPT avg %s p95 %s p99 %s max %s | CPU avg %s p95 %s | ticks over 50 ms: %d of %d",
                    steadyFrom, ms(avg(ws)), ms(pct(ws, 0.95)), ms(pct(ws, 0.99)), ms(ws[ws.length - 1]), ms(avg(cs)), ms(pct(cs, 0.95)), Arrays.stream(ws).filter(v -> v > 50_000_000L).count(), ws.length));
        }
        out.add("");
        out.add("Slowest ticks:");
        SLOW.sort((a, b) -> Long.compare(b[0], a[0]));
        for (long[] sl : SLOW) out.add("  " + SLOW_INFO.get(sl[1]));
        out.add("");
        out.add("Mod subsystems (ServerEvents probes) and the simulation driver, whole run: avg ms per tick | max ms in one tick");
        List<Map.Entry<String, long[]>> perf = new ArrayList<>(RUN_PERF.entrySet());
        perf.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        for (var en : perf)
            out.add(String.format("  %-24s %8.3f | %8.2f", en.getKey(), en.getValue()[0] / 1e6 / Math.max(1, measuredTicks), en.getValue()[2] / 1e6));
        out.add("");
        out.add("Level ticks (ServerLevel.tick incl. chunk source, entities, block ticks): avg ms per tick | max ms");
        for (var en : LEVEL_TICK.entrySet())
            out.add(String.format("  %-24s %8.3f | %8.2f", en.getKey(), en.getValue()[0] / 1e6 / Math.max(1, measuredTicks), en.getValue()[2] / 1e6));
        out.add("");
        out.add("Entity ticks by class (all levels): avg ms per tick | entity-ticks per tick | avg us per entity-tick | max ms");
        List<Map.Entry<String, long[]>> ents = new ArrayList<>(ENTITY_TICK.entrySet());
        ents.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        for (var en : ents.subList(0, Math.min(18, ents.size()))) {
            long[] v = en.getValue();
            out.add(String.format("  %-24s %8.3f | %8.1f | %8.1f | %8.2f", en.getKey(), v[0] / 1e6 / Math.max(1, measuredTicks), v[1] / (double) Math.max(1, measuredTicks), v[0] / 1e3 / Math.max(1, v[1]), v[2] / 1e6));
        }
        out.add("");
        out.add("Per-window mod subsystem time (avg ms per tick):");
        List<String> keys = new ArrayList<>(RUN_PERF.keySet());
        List<String[]> prow = new ArrayList<>();
        for (int i = 0; i < WIN_PERF.size(); i++) {
            String[] r = new String[keys.size() + 1];
            r[0] = String.valueOf(i + 1);
            for (int k = 0; k < keys.size(); k++) {
                long[] v = WIN_PERF.get(i).get(keys.get(k));
                r[k + 1] = v == null ? "-" : String.format("%.3f", v[0] / 1e6 / WINDOW);
            }
            prow.add(r);
        }
        String[] phead = new String[keys.size() + 1];
        phead[0] = "win";
        for (int k = 0; k < keys.size(); k++) phead[k + 1] = keys.get(k);
        out.addAll(table(phead, prow));
        out.add("");
        Map<String, Integer> census = new TreeMap<>();
        for (Entity e : ow.getAllEntities()) if (e instanceof Mob && !(e instanceof GLNpc)) census.merge(e.getType().getDescriptionId().replace("entity.minecraft.", ""), 1, Integer::sum);
        List<Map.Entry<String, Integer>> cl = new ArrayList<>(census.entrySet());
        cl.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder cs2 = new StringBuilder("Vanilla mobs in the overworld at the end:");
        for (var en : cl.subList(0, Math.min(14, cl.size()))) cs2.append(' ').append(en.getKey()).append('=').append(en.getValue());
        out.add(cs2.toString());
        out.add("");
        out.add("Simulated players:");
        for (Sim s : SIMS) {
            String extra = switch (s.role) {
                case FIGHTER -> String.format(", attacks %d, techniques %d (refused %d), foes spawned %d, defeated %d", s.attacks, s.abilities, s.abilityFails, s.spawnedFoes, s.kills);
                case SAILOR, STORM_SAILOR -> String.format(", ship %s%s, turns %d, sea encounters %d%s", s.cls.id(), s.ship == null ? " (not afloat)" : "", s.turns, s.seaEncounters,
                        s.role == Role.STORM_SAILOR ? ", ticks in a storm " + s.stormTicks : "");
                case STORM_SWIMMER -> ", ticks in a storm " + s.stormTicks;
                default -> "";
            };
            out.add(String.format("  #%-2d %-13s moved %6.0f blocks, waited %5d ticks for chunks, chunks sent %5d%s — %s",
                    s.index, s.role, s.moved, s.stalled, s.chunksSent, extra, s.where));
        }
        return out;
    }

    private static List<String> table(String[] head, List<String[]> rows) {
        int[] wd = new int[head.length];
        for (int i = 0; i < head.length; i++) wd[i] = head[i].length();
        for (String[] r : rows) for (int i = 0; i < r.length; i++) wd[i] = Math.max(wd[i], r[i].length());
        List<String> out = new ArrayList<>();
        out.add(row(head, wd));
        for (String[] r : rows) out.add(row(r, wd));
        return out;
    }

    private static String row(String[] r, int[] wd) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.length; i++) {
            if (i > 0) sb.append(" | ");
            sb.append(" ".repeat(wd[i] - r[i].length())).append(r[i]);
        }
        return sb.toString();
    }

    private static int envInt(String k, int def) {
        try {
            String v = System.getenv(k);
            return v == null ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private LoadTest() {}
}
