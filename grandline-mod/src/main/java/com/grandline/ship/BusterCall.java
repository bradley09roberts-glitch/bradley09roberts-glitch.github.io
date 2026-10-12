package com.grandline.ship;

import com.grandline.GrandLine;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.core.Chunks;
import com.grandline.core.GLConfig;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.TerrainChanges;
import com.grandline.journey.DenDenMushi;
import com.grandline.journey.Journey;
import com.grandline.npc.Bounties;
import com.grandline.npc.GLNpc;
import com.grandline.npc.PrisonSystem;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.registry.ModDamageTypes;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.TownProtection;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.SettlementType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Buster Call: Marine Headquarters' answer to a pirate grown too dangerous. A player whose bounty has passed the
 * threshold (config, 500M) can be <b>reported</b> by the Marines: a crime seen near a Marine-held island (any bounty
 * raise within {@link #BASE_REACH} blocks of one), or a Vice Admiral (or Admiral) who fought them and lived to tell.
 * A report becomes a Buster Call only by chance (config, 35%), never twice within the cooldown, never while another
 * call is under way, and only in the ocean world. It is telegraphed: a server-wide announcement, a title and a horn
 * for the target, a countdown (config, 60 s); black snails overhear the order. Then a squadron of Marine warships
 * (one Vice Admiral aboard each; never more than the room left under the shared NPC ship cap) appears on the open
 * sea around the target, converges on their ship or island and bombards it with mortar shells for the configured
 * time. Shells hurt ships and everyone in the blast (never Marines, the story cast, or a downed or jailed player), and
 * crater only the wild (never a town; craters revert through {@link TerrainChanges}). The call ends when the
 * bombardment runs out, the fleet is sunk or beaten, the target slips away or leaves the sea (all count as
 * surviving: a journey advancement and a higher bounty), or when the target is knocked down (the Vice Admiral's
 * shell: the Marines take them to a cell) or logs out. The fleet then withdraws and is gone.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class BusterCall {
    /** Tag on the fleet's ships and their shells. */
    public static final String TAG = "gl_buster_call";
    /** A crime within this many blocks of a Marine-held island's shore is seen by the base. */
    public static final int BASE_REACH = 300;
    /** A Vice Admiral reports a player once they have not traded blows for this long (and is still alive). */
    public static final long WITNESS_QUIET = 20 * 20;
    static final double SHELL_RADIUS = 4.5, SPREAD = 9, RANGE = 120, LOST = 650, GRAVITY = 0.02;
    /** For this long after the first warship appears, the rest of the squadron keeps arriving as open water allows. */
    static final int REINFORCE_TICKS = 20 * 30;
    static final float SHELL_DAMAGE = 16, SHELL_HULL = 45;
    static final int SHELL_PERIOD = 50;

    public enum Phase { WARNING, SAILING, BOMBARD, WITHDRAW, DONE }

    /** Knobs for one call (tests shorten the times; {@code force} skips the chance roll, never the bounty rule). */
    public static final class Rules {
        public boolean force;
        public int warnTicks = -1, bombardTicks = -1, ships = -1, spawnDist = -1, withdrawTicks = -1, sailTicks = -1;
    }

    public static final class Call {
        public final ServerPlayer target;
        public final UUID characterId;
        public final String name, reason;
        public Phase phase = Phase.WARNING;
        public final long issuedAt, warnUntil;
        public long sailFrom, bombardFrom, bombardUntil, withdrawUntil;
        public final List<ShipEntity> ships = new ArrayList<>();
        final Map<UUID, Double> bearings = new HashMap<>();
        /** Warships sunk, beaten or taken (as opposed to left behind when the target sailed out of the loaded world). */
        final Set<UUID> beaten = new HashSet<>();
        public Vec3 focus;
        public int shells, impacts, spawned, wanted, room, hurt, hulls;
        public boolean survived;
        public String outcome = "";
        final int bombardTicks, maxShips, spawnDist, withdrawTicks, sailTicks;

        Call(ServerPlayer target, CharacterData d, String reason, Rules r, long now) {
            this.target = target;
            this.characterId = d.characterId;
            this.name = d.name;
            this.reason = reason;
            this.issuedAt = now;
            this.warnUntil = now + (r.warnTicks >= 0 ? r.warnTicks : GLConfig.BUSTER_CALL_WARNING_SECONDS.get() * 20L);
            this.bombardTicks = r.bombardTicks >= 0 ? r.bombardTicks : GLConfig.BUSTER_CALL_SECONDS.get() * 20;
            this.maxShips = r.ships > 0 ? r.ships : GLConfig.BUSTER_CALL_SHIPS.get();
            this.spawnDist = r.spawnDist; // <= 0: inside the simulation distance, like the living sea's ships
            this.withdrawTicks = r.withdrawTicks >= 0 ? r.withdrawTicks : 20 * 12;
            this.sailTicks = r.sailTicks > 0 ? r.sailTicks : 20 * 150;
            this.focus = target.position();
        }

        public boolean active() {
            return phase == Phase.WARNING || phase == Phase.SAILING || phase == Phase.BOMBARD;
        }
    }

    private static final Rules DEFAULT = new Rules();
    /** Tests: rules for reports that come in through the game's hooks (crimes, witnesses). Null in normal play. */
    @Nullable public static Rules hookRules;
    private static final List<Call> CALLS = new ArrayList<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    private static final class Witness {
        final UUID npc;
        final ServerLevel level;
        ServerPlayer player;
        long last;

        Witness(UUID npc, ServerLevel level) {
            this.npc = npc;
            this.level = level;
        }
    }

    private static final Map<UUID, Witness> WITNESSES = new HashMap<>();

    // ================================================================== reports

    /** The calls under way or winding down (read-only view). */
    public static List<Call> calls() {
        return Collections.unmodifiableList(CALLS);
    }

    @Nullable
    public static Call activeFor(Player p) {
        for (Call c : CALLS) if (c.active() && c.target.getUUID().equals(p.getUUID())) return c;
        return null;
    }

    /** Whether a report against this player could become a Buster Call right now (everything but the chance roll). */
    public static boolean eligible(ServerPlayer sp) {
        CharacterData d = Characters.get(sp);
        if (!GLConfig.BUSTER_CALL.get() || !d.created || d.bounty < GLConfig.BUSTER_CALL_BOUNTY.get()) return false;
        if (sp.level() != sp.server.overworld() || WorldPlan.of(sp.server) == null || PrisonSystem.isJailed(sp)) return false;
        for (Call c : CALLS) if (c.phase != Phase.DONE) return false; // one Buster Call at a time, fleet withdrawn first
        return COOLDOWN.getOrDefault(sp.getUUID(), Long.MIN_VALUE) <= sp.server.overworld().getGameTime();
    }

    /**
     * The Marines report a player to Headquarters. Returns the call when the report becomes a Buster Call (see the class
     * comment for the rules); {@code rules} null uses the configuration.
     */
    @Nullable
    public static Call report(ServerPlayer sp, String reason, @Nullable Rules rules) {
        Rules r = rules != null ? rules : hookRules != null ? hookRules : DEFAULT;
        if (!eligible(sp)) return null;
        CharacterData d = Characters.get(sp);
        DenDenMushi.overheard(sp.server, Component.translatable("grandline.buster.report", d.name, reasonText(sp.server, reason)));
        if (!r.force && sp.getRandom().nextDouble() >= GLConfig.BUSTER_CALL_CHANCE.get()) return null;
        return issue(sp, d, reason, r);
    }

    private static Call issue(ServerPlayer sp, CharacterData d, String reason, Rules r) {
        MinecraftServer server = sp.server;
        long now = server.overworld().getGameTime();
        Call c = new Call(sp, d, reason, r, now);
        CALLS.add(c);
        int secs = (int) ((c.warnUntil - now) / 20);
        Component place = placeName(server, sp.position());
        Component msg = Component.translatable("grandline.buster.issued", d.name, String.format("%,d", d.bounty), place, secs)
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        server.getPlayerList().broadcastSystemMessage(msg, false);
        if (!server.getPlayerList().getPlayers().contains(sp)) sp.sendSystemMessage(msg);
        sp.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("grandline.buster.title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("grandline.buster.subtitle", secs).withStyle(ChatFormatting.GOLD)));
        sp.level().playSound(null, sp.blockPosition(), com.grandline.registry.ModSounds.SHIP_HORN.get(), SoundSource.HOSTILE, 6f, 0.85f);
        DenDenMushi.overheard(server, Component.translatable("grandline.buster.overheard", d.name, place));
        GrandLine.LOGGER.info("Buster Call issued against {} (bounty {}) near {} after a report ({})", d.name, d.bounty, place.getString(), reason);
        return c;
    }

    /** Bounties.raise: a crime seen from a Marine-held island is reported. */
    public static void onCrime(ServerPlayer sp) {
        if (!eligible(sp)) return;
        Island base = marineBaseNear(sp);
        if (base != null) report(sp, "crime:" + base.id, null);
    }

    @Nullable
    public static Island marineBaseNear(Entity e) {
        WorldPlan plan = WorldPlan.forLevel(e.level());
        if (plan == null || plan.kind != WorldPlan.Kind.OCEAN) return null;
        for (Island isl : plan.islands) {
            if (!isl.faction.equals("marine") && isl.settlementType != SettlementType.MARINE_BASE) continue;
            if (isl.distance(e.getBlockX(), e.getBlockZ()) < isl.radius + BASE_REACH) return isl;
        }
        return null;
    }

    static boolean witness(GLNpc n) {
        return n.role().equals("vice_admiral") || n.role().equals("admiral");
    }

    /** A Vice Admiral and a player traded blows: if the Vice Admiral is still alive when it is over, they report it. */
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (!(victim.level() instanceof ServerLevel sl)) return;
        Entity by = e.getSource().getEntity();
        GLNpc admiral = null;
        ServerPlayer sp = null;
        if (victim instanceof GLNpc n && witness(n) && by instanceof ServerPlayer p) {
            admiral = n;
            sp = p;
        } else if (victim instanceof ServerPlayer p && by instanceof GLNpc n && witness(n) && !n.getTags().contains(TAG)) {
            admiral = n;
            sp = p;
        }
        if (admiral == null || admiral.getTags().contains(TAG) || !Characters.get(sp).created) return;
        GLNpc a = admiral;
        Witness w = WITNESSES.computeIfAbsent(admiral.getUUID(), k -> new Witness(k, (ServerLevel) a.level()));
        w.player = sp;
        w.last = sl.getGameTime();
    }

    /** Vice Admirals whose fight is over: the living ones report (the dead tell no tales). Returns reports made. */
    public static int checkWitnesses(MinecraftServer server, long now) {
        int n = 0;
        for (Iterator<Witness> it = WITNESSES.values().iterator(); it.hasNext(); ) {
            Witness w = it.next();
            if (now - w.last < WITNESS_QUIET) continue;
            it.remove();
            if (!(w.level.getEntity(w.npc) instanceof GLNpc admiral) || !admiral.isAlive() || gone(w.player)) continue;
            n++;
            report(w.player, "vice_admiral", null);
        }
        return n;
    }

    public static boolean witnessed(UUID npc) {
        return WITNESSES.containsKey(npc);
    }

    // ================================================================== the call, tick by tick

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        MinecraftServer s = e.getServer();
        long now = s.overworld().getGameTime();
        if (now % 100 == 0 && !WITNESSES.isEmpty()) checkWitnesses(s, now);
        if (CALLS.isEmpty()) return;
        for (Call c : List.copyOf(CALLS)) tick(s, c, now);
    }

    static void tick(MinecraftServer s, Call c, long now) {
        ServerLevel l = s.overworld();
        switch (c.phase) {
            case WARNING -> {
                if (!present(s, c)) {
                    finish(s, c, "gone", false);
                    return;
                }
                long left = c.warnUntil - now;
                if (left > 0) {
                    if (left % 200 == 0) {
                        c.target.sendSystemMessage(Component.translatable("grandline.buster.countdown", left / 20).withStyle(ChatFormatting.RED));
                        c.target.level().playSound(null, c.target.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE, 1.5f, 0.6f);
                    }
                    return;
                }
                if (now % 20 != 0) return; // the fleet looks for open water once a second
                c.focus = focusOf(c.target);
                c.room = SeaEncounters.cap(l.players().size()) - SeaEncounters.countNpcShips(l);
                c.wanted = Math.min(c.maxShips, c.room);
                spawnFleet(l, c, c.wanted);
                if (!c.ships.isEmpty()) {
                    c.phase = Phase.SAILING;
                    c.sailFrom = now;
                    Component msg = Component.translatable("grandline.buster.fleet", c.ships.size(), c.name).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                    tellNear(l, c, msg);
                } else if (now - c.warnUntil > 20 * 60) finish(s, c, "no_fleet", false);
            }
            case SAILING, BOMBARD -> {
                if (!present(s, c)) {
                    boolean elsewhere = !gone(c.target) && c.characterId.equals(Characters.get(c.target).characterId);
                    finish(s, c, elsewhere ? "escaped" : "gone", elsewhere && c.phase == Phase.BOMBARD); // the Knock Up Stream, a dive...
                    return;
                }
                if (Characters.get(c.target).isKnockedDown() || PrisonSystem.isJailed(c.target)) {
                    finish(s, c, "captured", false);
                    return;
                }
                if (now % 10 == 0) c.focus = focusOf(c.target);
                for (ShipEntity sh : c.ships) if (sh.isSinking() || sh.naval.crewGone || !sh.flag(ShipEntity.F_NPC)) c.beaten.add(sh.getUUID());
                c.ships.removeIf(sh -> sh.isRemoved() || !sh.flag(ShipEntity.F_NPC));
                List<ShipEntity> fighting = new ArrayList<>();
                for (ShipEntity sh : c.ships) if (!sh.isSinking() && !sh.naval.crewGone) fighting.add(sh);
                if (fighting.isEmpty()) {
                    finish(s, c, c.beaten.isEmpty() ? "escaped" : "fleet_beaten", true);
                    return;
                }
                double nearest = Double.MAX_VALUE;
                for (ShipEntity sh : fighting) nearest = Math.min(nearest, horiz(sh.position(), c.focus));
                if (nearest > LOST || (c.phase == Phase.SAILING && now - c.sailFrom > c.sailTicks)) {
                    finish(s, c, "escaped", true); // outran the fleet (or it never got within range)
                    return;
                }
                if (c.spawned < c.wanted && now - c.sailFrom < REINFORCE_TICKS && now % 20 == 0) { // the rest of the squadron
                    int room = SeaEncounters.cap(l.players().size()) - SeaEncounters.countNpcShips(l);
                    spawnFleet(l, c, Math.min(c.wanted - c.spawned, room));
                }
                for (ShipEntity sh : fighting) {
                    steer(l, c, sh);
                    if (now % 10 == Math.floorMod(sh.getId(), 10)) LivingSea.deckDiscipline(sh, l);
                }
                if (c.phase == Phase.SAILING && nearest <= RANGE) {
                    c.phase = Phase.BOMBARD;
                    c.bombardFrom = now;
                    c.bombardUntil = now + c.bombardTicks;
                    tellNear(l, c, Component.translatable("grandline.buster.bombard", c.name).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                }
                if (c.phase == Phase.BOMBARD) {
                    for (ShipEntity sh : fighting)
                        if (horiz(sh.position(), c.focus) <= RANGE && (now + sh.getId() * 7L) % SHELL_PERIOD == 0) fire(l, sh, c);
                    if (now >= c.bombardUntil) finish(s, c, "survived", true);
                }
            }
            case WITHDRAW -> {
                c.ships.removeIf(Entity::isRemoved);
                for (ShipEntity sh : c.ships) {
                    if (!sh.flag(ShipEntity.F_NPC) || sh.isSinking()) continue;
                    Vec3 away = sh.position().subtract(c.focus).multiply(1, 0, 1);
                    if (away.lengthSqr() < 1) away = sh.heading();
                    sh.setAnchored(false);
                    sh.setSail(3);
                    com.grandline.navigation.Ferries.helm(sh, l, sh.position().add(away.normalize().scale(300)), "");
                }
                if (now >= c.withdrawUntil) {
                    for (ShipEntity sh : c.ships) if (sh.flag(ShipEntity.F_NPC) && !sh.isRemoved()) NpcShipAi.despawn(sh, l);
                    c.ships.clear();
                    c.phase = Phase.DONE;
                    CALLS.remove(c);
                }
            }
            default -> CALLS.remove(c);
        }
    }

    /**
     * Up to {@code n} more warships on open water in a ring round the target (one Vice Admiral aboard each), tried at a
     * spread of bearings and, where the near water is crowded with land, a little closer in. Returns how many came.
     */
    static int spawnFleet(ServerLevel l, Call c, int n) {
        if (n <= 0) return 0;
        RandomSource r = l.random;
        SeaZone zone = WorldLayout.zoneAt(Mth.floor(c.focus.x), Mth.floor(c.focus.z));
        int tier = Math.max(1, zone.dangerTier) + 3;
        double base = r.nextDouble() * Math.PI * 2, far = c.spawnDist > 0 ? c.spawnDist : LivingSea.spawnDist(l);
        int added = 0, slots = Math.max(6, n * 2);
        for (int k = 0; k < slots * 3 && added < n; k++) {
            double ang = base + (k % slots) * (Math.PI * 2 / slots) + r.nextGaussian() * 0.1;
            double dist = far * (1 - 0.15 * (k / slots)); // the outer ring first, then closer in
            ShipClass cls = c.spawned == 0 ? ShipClass.GALLEON : ShipClass.BRIGANTINE;
            Vec3 at = LivingSea.openWater(l, c.focus.x + Math.cos(ang) * dist, c.focus.z + Math.sin(ang) * dist, cls.length / 2 + 4);
            if (at == null) continue;
            ShipEntity s = SeaEncounters.spawnShip(l, at, LivingSea.yawTo(at, c.focus), cls, LivingSea.MARINE, tier, "Marine HQ Warship");
            if (s == null) continue;
            s.npcAiOff = true; // captained from here: converge and bombard (no despawning while the call lasts)
            s.addTag(TAG);
            s.addUpgrade("paddle_wheel"); // Marine warships keep way in any wind
            s.setShipName("Buster Call Warship " + (c.spawned + 1));
            for (UUID u : s.naval.crew) if (l.getEntity(u) instanceof GLNpc m) m.addTag(TAG);
            c.ships.add(s);
            c.bearings.put(s.getUUID(), Math.atan2(at.z - c.focus.z, at.x - c.focus.x));
            c.spawned++;
            added++;
        }
        return added;
    }

    /** Each warship keeps to its own bearing from the target, closing to its station off the target. */
    private static void steer(ServerLevel l, Call c, ShipEntity sh) {
        double b = c.bearings.getOrDefault(sh.getUUID(), 0.0);
        double station = 60 + sh.cls.length / 2.0;
        Vec3 st = c.focus.add(Math.cos(b) * station, 0, Math.sin(b) * station);
        double d = horiz(sh.position(), st);
        sh.setAnchored(false);
        if (d < 14) {
            sh.setSail(0);
            sh.setRudder(0);
            return;
        }
        sh.setSail(d > 50 ? 3 : 2);
        com.grandline.navigation.Ferries.helm(sh, l, new Vec3(st.x, sh.getY(), st.z), "");
    }

    /** A mortar shell from a warship onto the target (lobbed high over the town and the trees). */
    static void fire(ServerLevel l, ShipEntity sh, Call c) {
        RandomSource r = l.random;
        Vec3 aim = c.focus.add(r.nextGaussian() * SPREAD * 0.6, 0, r.nextGaussian() * SPREAD * 0.6);
        int ax = Mth.floor(aim.x), az = Mth.floor(aim.z);
        boolean afloat = focusShip(c.target) != null;
        double ay = !afloat && Chunks.ready(l, ax, az) ? l.getHeight(Heightmap.Types.MOTION_BLOCKING, ax, az) : c.focus.y + 0.5;
        aim = new Vec3(aim.x, ay, aim.z);
        Vec3 to = aim.subtract(sh.position());
        Vec3 starboard = sh.toWorld(1, 0, 0).subtract(sh.toWorld(0, 0, 0));
        int side = to.x * starboard.x + to.z * starboard.z >= 0 ? 1 : -1;
        Vec3 origin = sh.toWorld(side * (sh.cls.beam / 2.0 + 0.6), sh.cls.freeboard + 1.6, (r.nextDouble() - 0.5) * sh.cls.length * 0.5);
        shell(l, origin, aim, sh, c, viceAdmiral(l, sh));
        l.playSound(null, BlockPos.containing(origin), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5f, 0.6f);
        l.sendParticles(ParticleTypes.LARGE_SMOKE, origin.x, origin.y, origin.z, 10, 0.4, 0.4, 0.4, 0.03);
    }

    /** Launch one shell from {@code origin} that comes down on {@code aim}. */
    public static AbilityProjectile shell(ServerLevel l, Vec3 origin, Vec3 aim, @Nullable ShipEntity from, @Nullable Call c, @Nullable LivingEntity gunner) {
        Vec3 d = aim.subtract(origin);
        int t = (int) Mth.clamp(d.horizontalDistance() * 0.75, 30, 150);
        Vec3 v = new Vec3(d.x / t, (d.y + GRAVITY * t * (t - 1) / 2.0) / t, d.z / t);
        AbilityProjectile p = AbilityProjectile.shoot(l, null, "cannonball", 0.6f, origin, v, v.length(), 0f, Hits.Info.of(Element.IMPACT, 1.5f));
        p.gravity = GRAVITY;
        p.life = t + 60;
        p.ignoreShip = from;
        p.addTag(TAG);
        p.onHit = (proj, hit, pos) -> {
            Impact im = burst(l, pos, proj, gunner);
            if (c != null) {
                c.impacts++;
                c.hurt += im.hurt;
                c.hulls += im.hulls;
            }
        };
        if (c != null) c.shells++;
        return p;
    }

    /** What one shell did. */
    public record Impact(int hurt, int hulls, int blocks) {}

    /**
     * A shell bursts at {@code pos}: everyone within {@link #SHELL_RADIUS} but the {@link #spared} takes damage
     * (falling off with distance), every ship but the fleet's own whose hull is within reach is holed, and in the wild
     * (never in or at the edge of a settlement) a small crater is blasted that reverts later.
     */
    public static Impact burst(ServerLevel l, Vec3 pos, @Nullable Entity shell, @Nullable LivingEntity gunner) {
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.5, pos.z, 14, 1.5, 0.8, 1.5, 0.02);
        l.playSound(null, BlockPos.containing(pos), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3f, 0.7f + l.random.nextFloat() * 0.3f);
        DamageSource src = ModDamageTypes.source(l, ModDamageTypes.CANNON, shell, gunner != null && gunner.isAlive() ? gunner : null);
        int hurt = 0, hulls = 0, blocks = 0;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(SHELL_RADIUS + 1))) {
            if (!e.isAlive() || spared(e)) continue;
            double dist = e.getBoundingBox().getCenter().distanceTo(pos) - e.getBbWidth() * 0.5;
            if (dist > SHELL_RADIUS) continue;
            float dmg = (float) (SHELL_DAMAGE * (1 - Math.max(0, dist) / (SHELL_RADIUS + 1.5)));
            float before = e.getHealth();
            e.hurt(src, dmg);
            if (e.getHealth() < before || !e.isAlive()) hurt++;
        }
        for (ShipEntity s : l.getEntitiesOfClass(ShipEntity.class, new AABB(pos, pos).inflate(SHELL_RADIUS + 22, 12, SHELL_RADIUS + 22),
                s -> !s.getTags().contains(TAG) && !s.isSinking())) {
            Vec3 local = s.toLocal(pos);
            if (Math.abs(local.x) > s.cls.beam / 2.0 + SHELL_RADIUS || Math.abs(local.z) > s.cls.length / 2.0 + SHELL_RADIUS) continue;
            if (pos.y > s.getY() + s.cls.freeboard + 8 || pos.y < s.getY() - 6) continue;
            float before = s.hull();
            s.hurtHull(SHELL_HULL, null);
            if (s.hull() < before) hulls++;
        }
        if (GLConfig.BUSTER_CALL_TERRAIN.get() && wild(l, pos)) blocks = crater(l, BlockPos.containing(pos));
        return new Impact(hurt, hulls, blocks);
    }

    /** Who a shell never hurts: Marines (they aim away from their own), the story cast, the downed, the jailed. */
    static boolean spared(LivingEntity e) {
        if (e instanceof GLNpc n) return n.faction() == Faction.MARINE || n.faction() == Faction.WORLD_GOVERNMENT || !n.notableId().isEmpty();
        if (e instanceof Player p) {
            if (p.isCreative() || p.isSpectator()) return true;
            CharacterData d = Characters.get(p);
            return d.isKnockedDown() || (p instanceof ServerPlayer sp && PrisonSystem.isJailed(sp));
        }
        return false;
    }

    /** The wild: no settlement or set piece reaches here (towns are never cratered), and town protection agrees. */
    public static boolean wild(ServerLevel l, Vec3 pos) {
        WorldPlan plan = WorldPlan.forLevel(l);
        if (plan == null) return false;
        int x = Mth.floor(pos.x), z = Mth.floor(pos.z);
        for (Island isl : plan.around(x, z)) {
            if (isl.settlementType == SettlementType.NONE && isl.landmarks.isEmpty()) continue;
            if (isl.settlement().bounds(x, z)) return false;
        }
        return TownProtection.canModify(l, BlockPos.containing(pos));
    }

    /**
     * A small crater of temporary changes (reverted by TerrainChanges). Only solid soft ground is blasted, and never
     * from under something that would pop off, fall or flow in (a flower, sand, water, a torch): those columns stay, so
     * that every block the shell changed is one TerrainChanges puts back.
     */
    static int crater(ServerLevel l, BlockPos c) {
        Set<BlockPos> blast = new LinkedHashSet<>();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -2, -2), c.offset(2, 1, 2))) {
            if (p.distSqr(c) > 5) continue;
            var st = l.getBlockState(p);
            if (st.isAir() || st.liquid() || st.getBlock() instanceof net.minecraft.world.level.block.FallingBlock || !st.isCollisionShapeFullBlock(l, p)) continue;
            float h = st.getDestroySpeed(l, p);
            if (h < 0 || h > 3 || l.getBlockEntity(p) != null) continue;
            blast.add(p.immutable());
        }
        for (boolean changed = true; changed; ) { // keep every block that holds up or walls in something staying put
            changed = false;
            for (var it = blast.iterator(); it.hasNext(); ) {
                BlockPos p = it.next();
                boolean keep = false;
                for (var dir : net.minecraft.core.Direction.values()) {
                    if (dir == net.minecraft.core.Direction.DOWN) continue;
                    BlockPos q = p.relative(dir);
                    if (blast.contains(q)) continue;
                    var n = l.getBlockState(q);
                    if (n.isAir()) continue;
                    if (dir == net.minecraft.core.Direction.UP || n.liquid() || !n.isCollisionShapeFullBlock(l, q)) keep = true;
                }
                if (keep) {
                    it.remove();
                    changed = true;
                }
            }
        }
        int n = 0;
        for (BlockPos p : blast) if (TerrainChanges.place(l, p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())) n++;
        return n;
    }

    private static void finish(MinecraftServer s, Call c, String outcome, boolean survived) {
        long now = s.overworld().getGameTime();
        c.outcome = outcome;
        c.survived = survived;
        COOLDOWN.put(c.target.getUUID(), now + GLConfig.BUSTER_CALL_COOLDOWN_MINUTES.get() * 1200L);
        Component msg;
        if (survived && !gone(c.target) && c.characterId.equals(Characters.get(c.target).characterId)) {
            CharacterData d = Characters.get(c.target);
            Journey.milestone(c.target, Journey.BUSTER_CALL);
            long raise = Math.max(50_000_000L, d.bounty / 10);
            Bounties.raise(c.target, d, raise, Component.translatable("grandline.buster.survived_reason"));
            msg = Component.translatable("grandline.buster.over." + outcome, c.name, String.format("%,d", d.bounty)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        } else msg = Component.translatable("grandline.buster.over." + outcome, c.name).withStyle(ChatFormatting.GRAY);
        s.getPlayerList().broadcastSystemMessage(msg, false);
        if (!gone(c.target) && !s.getPlayerList().getPlayers().contains(c.target)) c.target.sendSystemMessage(msg);
        GrandLine.LOGGER.info("Buster Call against {} over: {} ({} ships, {} shells, {} impacts)", c.name, outcome, c.spawned, c.shells, c.impacts);
        if (c.ships.isEmpty()) {
            c.phase = Phase.DONE;
            CALLS.remove(c);
        } else {
            c.phase = Phase.WITHDRAW;
            c.withdrawUntil = now + c.withdrawTicks;
        }
    }

    /** Called off (admin): the fleet withdraws at once. */
    public static boolean cancel(MinecraftServer s, Player target) {
        Call c = activeFor(target);
        if (c == null) return false;
        finish(s, c, "cancelled", false);
        return true;
    }

    // ================================================================== helpers

    private static boolean gone(ServerPlayer p) {
        return p == null || p.isRemoved() || p.hasDisconnected();
    }

    /** Still the same character, alive and on the ocean world. */
    private static boolean present(MinecraftServer s, Call c) {
        if (gone(c.target)) return false;
        CharacterData d = Characters.get(c.target);
        return d.created && c.characterId.equals(d.characterId) && c.target.level() == s.overworld();
    }

    @Nullable
    private static ShipEntity focusShip(ServerPlayer p) {
        if (p.getVehicle() instanceof ShipEntity s) return s;
        return NavalCombat.shipOf(p);
    }

    /** Where the fleet aims: the target's ship, or the target where they stand. */
    static Vec3 focusOf(ServerPlayer p) {
        ShipEntity s = focusShip(p);
        return s != null ? s.position() : p.position();
    }

    @Nullable
    private static LivingEntity viceAdmiral(ServerLevel l, ShipEntity sh) {
        LivingEntity any = null;
        for (UUID u : sh.naval.crew) {
            if (!(l.getEntity(u) instanceof GLNpc m) || !m.isAlive()) continue;
            if (m.role().equals("vice_admiral")) return m;
            if (any == null) any = m;
        }
        return any;
    }

    private static double horiz(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void tellNear(ServerLevel l, Call c, Component msg) {
        for (ServerPlayer p : l.players()) if (p != c.target && horiz(p.position(), c.focus) < 500) p.sendSystemMessage(msg);
        if (!gone(c.target)) c.target.sendSystemMessage(msg);
    }

    static Component placeName(MinecraftServer s, Vec3 at) {
        WorldPlan plan = WorldPlan.of(s);
        Island isl = plan == null ? null : plan.nearest(Mth.floor(at.x), Mth.floor(at.z), 400);
        if (isl == null) return Component.translatable("grandline.buster.open_sea");
        return isl.canon && isl.name == null ? Component.translatable(isl.translationKey()) : Component.literal(isl.name);
    }

    private static Component reasonText(MinecraftServer s, String reason) {
        if (reason.startsWith("crime:")) {
            WorldPlan plan = WorldPlan.of(s);
            Island isl = plan == null ? null : plan.byId(reason.substring(6));
            Component base = isl == null ? Component.literal(reason.substring(6))
                    : isl.canon && isl.name == null ? Component.translatable(isl.translationKey()) : Component.literal(isl.name);
            return Component.translatable("grandline.buster.reason.crime", base);
        }
        return Component.translatable("grandline.buster.reason." + (reason.equals("vice_admiral") ? "vice_admiral" : "orders"));
    }

    /** What a black snail picks up about Buster Calls under way. */
    public static List<Component> intercepts() {
        List<Component> out = new ArrayList<>();
        for (Call c : CALLS) {
            if (!c.active()) continue;
            String key = c.phase == Phase.WARNING ? "grandline.buster.intercept_warning" : "grandline.buster.intercept_fleet";
            out.add(Component.translatable(key, c.name, c.ships.size()));
        }
        return out;
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        CALLS.clear();
        COOLDOWN.clear();
        WITNESSES.clear();
        hookRules = null;
    }

    // ================================================================== /bustercall (operators)

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("bustercall").requires(s -> s.hasPermission(2))
                .then(Commands.literal("status").executes(BusterCall::status))
                .then(Commands.literal("issue").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer sp = EntityArgument.getPlayer(c, "player");
                    Rules r = new Rules();
                    r.force = true;
                    Call call = report(sp, "orders", r);
                    if (call == null) {
                        c.getSource().sendFailure(Component.literal("Not eligible (disabled, bounty below " + GLConfig.BUSTER_CALL_BOUNTY.get()
                                + ", not on the ocean world, jailed, cooldown, or another call under way)"));
                        return 0;
                    }
                    c.getSource().sendSuccess(() -> Component.literal("Buster Call issued against " + call.name), true);
                    return 1;
                })))
                .then(Commands.literal("cancel").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> cancel(c.getSource().getServer(), EntityArgument.getPlayer(c, "player")) ? 1 : 0))));
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        StringBuilder sb = new StringBuilder("Buster Calls: " + CALLS.size());
        long now = c.getSource().getServer().overworld().getGameTime();
        for (Call call : CALLS)
            sb.append("\n ").append(call.name).append(" ").append(call.phase).append(" ships ").append(call.ships.size())
                    .append(" shells ").append(call.shells).append(" impacts ").append(call.impacts)
                    .append(call.phase == Phase.WARNING ? " arrives in " + (call.warnUntil - now) / 20 + " s" : "")
                    .append(call.phase == Phase.BOMBARD ? " ends in " + (call.bombardUntil - now) / 20 + " s" : "");
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return CALLS.size();
    }

    private BusterCall() {}
}
