package com.grandline.ship;

import com.grandline.GrandLine;
import com.grandline.base.BaseHands;
import com.grandline.base.BaseUpgrades;
import com.grandline.base.CrewBases;
import com.grandline.base.CrewBases.Claim;
import com.grandline.core.Chunks;
import com.grandline.core.GLConfig;
import com.grandline.navigation.Ferries;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.PrisonSystem;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Raids on crew bases, after the pattern of {@link HarbourRaids}: while crew members are near their base, now and then
 * (a small chance every 5 s, a cooldown per base, never within the NPC ship cap's last berth, never in the Calm Belt)
 * a pirate ship, or for a wanted crew a Marine ship, sails in to the shore nearest the flag (the base dock's side
 * first) and lands a party (at most six) at the edge of the base. The ship is captained from here (as the Buster Call's
 * fleet is): she makes for that shore and anchors; the raiders go for the crew and the hired hands and make for the
 * flag.
 * <ul>
 *   <li>Beaten: the raid is repelled, the ship runs, the crew members who fought are paid.</li>
 *   <li>Raiders at the flag with no defender within 16 blocks of it (all down, gone or never there) for
 *   {@code captureSeconds}: the flag is taken, announced to the whole server. Retake it (no raider by the flag, a
 *   defender there) within {@code graceSeconds} or the claim is lost: the flag comes down and the raiders carry off
 *   half the warehouse.</li>
 *   <li>Still standing after three minutes: they plunder a few stacks from the warehouse and leave.</li>
 * </ul>
 * A watchtower sights the ship far out and warns every crew member online; its raiders glow. A raid lives only in
 * memory (a restart drops it) and gives up if its people are unloaded or no crew member is online.
 */
public final class BaseRaids {
    public static final String TAG = "gl_base_raid";
    public static final int APPROACH = 0, ASHORE = 1, FLAG_TAKEN = 2, LEAVING = 3;
    public static final long RAID_TICKS = 20 * 180, LEAVE_TICKS = 20 * 40;
    static final long APPROACH_MAX = 20 * 45, WATCH_APPROACH_MAX = 20 * 90;
    public static final int MAX_RAIDERS = 6;
    /** Defenders count within this distance of the flag; raiders take it from within {@link #AT_FLAG}. */
    public static final double DEFEND = 16, AT_FLAG = 5;

    public static final class Raid {
        public final String island;
        public final String faction;
        public final ShipEntity ship;
        /** Where the ship anchors (water off the shore nearest the flag) and the flag. */
        public final Vec3 dock, flag;
        public final List<UUID> raiders = new ArrayList<>();
        public int phase = APPROACH, stall, missing, capture, alone;
        public long startedAt, phaseAt, landedAt, lostAt;
        public boolean warned;
        public final List<ItemStack> plunder = new ArrayList<>();
        /** How it ended (empty while it lasts): repelled, plundered, captured, called_off, sunk. */
        public String outcome = "";

        Raid(String island, String faction, ShipEntity ship, Vec3 dock, Vec3 flag) {
            this.island = island;
            this.faction = faction;
            this.ship = ship;
            this.dock = dock;
            this.flag = flag;
        }
    }

    /** Knobs for one decision (tests force them). */
    public static final class Rules {
        public boolean force;
        /** Server-wide NPC ship cap to use instead of the living sea's (-1: the usual one). */
        public int cap = -1;
        public int spawnDist = -1;
        @Nullable public String faction;
    }

    private static final Map<String, Raid> RAIDS = new LinkedHashMap<>();

    @Nullable
    public static Raid raidOn(String island) {
        return RAIDS.get(island);
    }

    public static Collection<Raid> active() {
        return Collections.unmodifiableCollection(RAIDS.values());
    }

    // ================================================================== deciding

    /** Every second: run the raids; every 5 s: perhaps start one at a base with crew near. */
    public static void tick(MinecraftServer server, long now) {
        if (!RAIDS.isEmpty()) for (Raid r : new ArrayList<>(RAIDS.values())) update(server, r, now);
        if (now % 100 != 3 || !GLConfig.BASES.get() || !GLConfig.BASE_RAIDS.get()) return;
        List<? extends ServerPlayer> online = CrewBases.online(server);
        if (online.isEmpty()) return;
        for (Claim c : new ArrayList<>(CrewBases.claims(server))) {
            if (RAIDS.containsKey(c.island) || c.raidReadyAt > now) continue;
            if (server.overworld().random.nextDouble() > GLConfig.BASE_RAID_CHANCE.get()) continue; // rolled first: most rounds end here
            List<ServerPlayer> near = CrewBases.crewNear(online, c);
            if (near.isEmpty()) continue;
            decide(server.overworld(), c, near, server.getPlayerList().getPlayerCount(), null);
        }
    }

    /**
     * Start a raid on {@code c} if the rules allow: crew near, not raided already, room under the NPC ship cap, open
     * sea. Returns the raid, or null.
     */
    @Nullable
    public static Raid decide(ServerLevel l, Claim c, List<ServerPlayer> crewNear, int playerCount, @Nullable Rules rules) {
        Rules r = rules == null ? new Rules() : rules;
        if (crewNear.isEmpty() || RAIDS.containsKey(c.island)) return null;
        if (!r.force && c.raidReadyAt > l.getGameTime()) return null;
        WorldPlan plan = WorldPlan.forLevel(l);
        Island isl = plan == null ? null : plan.byId(c.island);
        if (isl == null || !LivingSea.openZone(isl.zone)) return null;
        int cap = r.cap >= 0 ? r.cap : SeaEncounters.cap(playerCount);
        if (cap - SeaEncounters.countNpcShips(l) <= 0) return null;
        ServerPlayer observer = crewNear.get(0);
        for (ServerPlayer p : crewNear) if (p.distanceToSqr(Vec3.atCenterOf(c.flag)) < observer.distanceToSqr(Vec3.atCenterOf(c.flag))) observer = p;
        String faction = r.faction != null ? r.faction : chooseFaction(l, crewNear);
        return start(l, c, isl, observer.position(), r.spawnDist > 0 ? r.spawnDist : LivingSea.spawnDist(l), faction);
    }

    /** Pirates come for anyone; a wanted crew (or one of pirates) may draw the Marines instead. */
    static String chooseFaction(ServerLevel l, List<ServerPlayer> crew) {
        boolean wanted = false;
        for (ServerPlayer p : crew) {
            CharacterData d = Characters.get(p);
            if (d.bounty > 0 || d.faction == Faction.PIRATE || d.faction == Faction.REVOLUTIONARY) wanted = true;
        }
        return wanted && l.random.nextBoolean() ? LivingSea.MARINE : LivingSea.PIRATE;
    }

    /** Where the raid makes for: the base dock, or the water nearest the flag. */
    static Vec3 dockOf(Claim c) {
        if (c.hasDock) return new Vec3(c.dockX + 0.5, WorldPlan.SEA_LEVEL + 0.9, c.dockZ + 0.5);
        return Vec3.atCenterOf(c.flag);
    }

    /** Open water just off the shore along a bearing from the flag (by the world plan), or null. */
    @Nullable
    static Vec3 shoreFrom(WorldPlan plan, Claim c, double ux, double uz) {
        WorldPlan.Sample smp = new WorldPlan.Sample();
        for (int d = 6; d < 1400; d += 3) {
            int x = c.flag.getX() + (int) Math.round(ux * d), z = c.flag.getZ() + (int) Math.round(uz * d);
            plan.sample(x, z, smp);
            if (smp.height < WorldPlan.SEA_LEVEL - 1 && !smp.redLine && !smp.canal)
                return new Vec3(x + ux * 8 + 0.5, WorldPlan.SEA_LEVEL + 0.9, z + uz * 8 + 0.5);
        }
        return null;
    }

    /**
     * A raid ship on open water off the island: searched along bearings from the flag (the base dock's first), some way
     * out from the shore (further with a watchtower), in loaded chunks only, preferably within {@code maxDist} of the
     * crew member who sees it come (else a little beyond: her boats row in all the same).
     */
    @Nullable
    static Raid start(ServerLevel l, Claim c, Island isl, Vec3 observer, int maxDist, String faction) {
        WorldPlan plan = WorldPlan.forLevel(l);
        if (plan == null) return null;
        Vec3 dock = dockOf(c);
        double prefer = c.hasDock ? Math.atan2(dock.z - (c.flag.getZ() + 0.5), dock.x - (c.flag.getX() + 0.5)) : 0;
        var rnd = l.random;
        ShipClass cls = isl.zone.isBlue() ? (rnd.nextBoolean() ? ShipClass.SLOOP : ShipClass.CARAVEL) : rnd.nextInt(3) == 0 ? ShipClass.BRIGANTINE : ShipClass.CARAVEL;
        boolean watch = c.has(BaseUpgrades.WATCHTOWER);
        int far = watch ? 130 : 70, near = watch ? 60 : 24;
        List<Vec3[]> rays = new ArrayList<>(); // {shore water, unit direction}
        for (int k = 0; k < 16; k++) {
            double ang = prefer + (k % 2 == 0 ? 1 : -1) * ((k + 1) / 2) * Math.PI / 8;
            double ux = Math.cos(ang), uz = Math.sin(ang);
            Vec3 shore = shoreFrom(plan, c, ux, uz);
            if (shore != null) rays.add(new Vec3[]{shore, new Vec3(ux, 0, uz)});
        }
        for (int pass = 0; pass < 2; pass++) {
            double reach = pass == 0 ? maxDist + 10 : maxDist * 2 + 60;
            for (Vec3[] ray : rays)
                for (int out = far; out >= near; out -= 10) {
                    Vec3 shore = ray[0], u = ray[1];
                    double x = shore.x + u.x * out, z = shore.z + u.z * out;
                    if (Math.hypot(x - observer.x, z - observer.z) > reach) continue;
                    Vec3 w = LivingSea.openWater(l, x, z, cls.length / 2 + 3);
                    if (w == null) continue;
                    return launch(l, c, isl, faction, cls, w, shore, watch);
                }
        }
        return null;
    }

    private static Raid launch(ServerLevel l, Claim c, Island isl, String faction, ShipClass cls, Vec3 w, Vec3 anchor, boolean watch) {
        var rnd = l.random;
        boolean marine = faction.equals(LivingSea.MARINE);
        ShipEntity ship = SeaEncounters.spawnShip(l, w, LivingSea.yawTo(w, anchor), cls, faction, Math.max(1, isl.tier), marine ? "Marine Raider" : LivingSea.name(LivingSea.PIRATE, rnd));
        if (ship == null) return null;
        ship.npcAiOff = true; // captained from here (no despawning while the raid lasts)
        ship.noDespawn = true;
        ship.addTag(TAG);
        ship.addTag(CrewBases.OF + c.island);
        Raid r = new Raid(c.island, faction, ship, anchor, Vec3.atBottomCenterOf(c.flag));
        r.startedAt = r.phaseAt = l.getGameTime();
        RAIDS.put(c.island, r);
        c.raidReadyAt = l.getGameTime() + GLConfig.BASE_RAID_COOLDOWN_MINUTES.get() * 1200L;
        c.dirty();
        MinecraftServer server = l.getServer();
        Component who = Component.translatable(marine ? "grandline.base.raid.marines" : "grandline.base.raid.pirates", ship.shipName());
        if (watch) {
            r.warned = true;
            int dist = (int) w.distanceTo(Vec3.atCenterOf(c.flag));
            for (ServerPlayer p : CrewBases.crewOnline(CrewBases.online(server), c)) {
                CrewBases.tell(p, Component.translatable("grandline.base.raid.watchtower", who, CrewBases.islandName(server, c), dist).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                p.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1f, 1f);
            }
        }
        GrandLine.LOGGER.info("Base raid: {} ({}) on {} from {}", ship.shipName(), faction, c.island, ship.blockPosition());
        return r;
    }

    // ================================================================== sailing in and out (every half second)

    public static void steer(MinecraftServer server, long now) {
        if (RAIDS.isEmpty()) return;
        ServerLevel l = server.overworld();
        for (Raid r : new ArrayList<>(RAIDS.values())) {
            ShipEntity ship = r.ship;
            if (ship.isRemoved()) continue;
            if (r.phase == APPROACH) {
                if (ship.isSinking() || ship.naval.crewGone) continue; // update() ends it
                Claim c = CrewBases.claimOn(server, r.island);
                double d = ship.position().subtract(r.dock).horizontalDistance();
                boolean late = now - r.phaseAt > (c != null && c.has(BaseUpgrades.WATCHTOWER) ? WATCH_APPROACH_MAX : APPROACH_MAX);
                r.stall = ship.speed() < 0.03f ? r.stall + 1 : 0;
                // in close, held up by rocks and shallows, or out of time: the boats row the party in from here
                if (c != null && (d < 40 || (r.stall > 10 && d < 70) || late)) {
                    if (land(r, l, c, now)) continue;
                }
                if (late && now - r.phaseAt > WATCH_APPROACH_MAX + 600) { // nowhere to land: they give up
                    end(r, l, "called_off", now);
                    continue;
                }
                ship.setAnchored(false);
                float diff = Ferries.helm(ship, l, r.dock, r.island);
                ship.setSail(Math.abs(diff) > 70 || d < 40 ? 1 : 2);
            } else if (r.phase == LEAVING) {
                Vec3 away = ship.position().subtract(r.flag).multiply(1, 0, 1);
                if (away.lengthSqr() < 1) away = ship.heading();
                ship.setAnchored(false);
                ship.setSail(3);
                Ferries.helm(ship, l, ship.position().add(away.normalize().scale(260)), "");
            } else {
                ship.setSail(0);
                ship.steer(0);
                ship.setAnchored(true);
            }
        }
    }

    // ================================================================== landing

    /**
     * Spots on land where the landing party comes up: the edge of the base on the harbour side (or the shore, if that
     * is nearer the flag), in toward the flag (fully loaded chunks only). The boats' row in and the march up from a
     * distant beach are not played out: the party appears at the base's edge.
     */
    public static List<BlockPos> landingSpots(ServerLevel l, Claim c, Vec3 dock) {
        List<BlockPos> out = new ArrayList<>();
        double fx = c.flag.getX() + 0.5, fz = c.flag.getZ() + 0.5;
        double dx = dock.x - fx, dz = dock.z - fz, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1) {
            dx = 1;
            dz = 0;
            len = CrewBases.radius();
        } else {
            dx /= len;
            dz /= len;
        }
        for (double t = Math.min(len, CrewBases.radius() * 0.75); t >= 4 && out.size() < 10; t -= 2) {
            int x = Mth.floor(fx + dx * t), z = Mth.floor(fz + dz * t);
            for (int side = -3; side <= 3 && out.size() < 10; side += 3) {
                int sx = x + (int) Math.round(-dz * side), sz = z + (int) Math.round(dx * side);
                BlockPos b = stand(l, sx, sz);
                if (b != null) out.add(b);
            }
            if (out.size() >= 6) break;
        }
        return out;
    }

    @Nullable
    private static BlockPos stand(ServerLevel l, int x, int z) {
        if (!Chunks.ready(l, x, z)) return null;
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= WorldPlan.SEA_LEVEL) return null;
        BlockPos b = new BlockPos(x, y, z);
        if (!l.getBlockState(b.below()).getFluidState().isEmpty() || !l.getBlockState(b).isAir() || !l.getBlockState(b.above()).isAir()) return null;
        return b;
    }

    static boolean able(GLNpc m) {
        return HarbourRaids.able(m);
    }

    /** Anchor and put the raiders ashore (all hands but one, at most {@link #MAX_RAIDERS}). */
    public static boolean land(Raid r, ServerLevel l, Claim c, long now) {
        List<BlockPos> spots = landingSpots(l, c, r.dock);
        if (spots.isEmpty()) return false;
        ShipEntity ship = r.ship;
        List<GLNpc> free = new ArrayList<>(), gunners = new ArrayList<>();
        for (UUID u : ship.naval.crew) {
            if (!(l.getEntity(u) instanceof GLNpc m) || !able(m)) continue;
            if (m.isPassenger()) gunners.add(m);
            else free.add(m);
        }
        if (free.size() < 3) free.addAll(gunners.subList(0, Math.min(gunners.size(), 3 - free.size())));
        if (free.size() > 1) free.remove(free.size() - 1); // one hand minds the ship
        while (free.size() > MAX_RAIDERS) free.remove(free.size() - 1);
        if (free.isEmpty()) return false;
        boolean glow = c.has(BaseUpgrades.WATCHTOWER);
        int i = 0;
        for (GLNpc m : free) {
            BlockPos b = spots.get(i++ % spots.size());
            m.stopRiding();
            m.teleportTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
            m.setDeltaMovement(Vec3.ZERO);
            m.fallDistance = 0;
            m.getNavigation().stop();
            m.setTarget(null);
            m.addTag(LivingSea.RAIDER);
            m.addTag(CrewBases.RAIDER);
            m.addTag(CrewBases.OF + c.island);
            m.setAllegiance(m.faction(), c.flag); // they make for the flag
            if (glow) m.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) RAID_TICKS + 1200, 0, false, false));
            r.raiders.add(m.getUUID());
            l.sendParticles(ParticleTypes.SPLASH, b.getX() + 0.5, b.getY() + 0.2, b.getZ() + 0.5, 12, 0.4, 0.2, 0.4, 0.1);
        }
        r.phase = ASHORE;
        r.landedAt = r.phaseAt = now;
        ship.setSail(0);
        ship.setAnchored(true);
        l.playSound(null, spots.get(0), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3f, 1f);
        MinecraftServer server = l.getServer();
        Component msg = Component.translatable("grandline.base.raid.landed", ship.shipName(), CrewBases.islandName(server, c), r.raiders.size()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        CrewBases.tellCrew(server, c, msg);
        return true;
    }

    // ================================================================== ashore (every second)

    /** Crew members and hands standing for the base within {@code r} blocks of the flag. */
    public static List<LivingEntity> defenders(ServerLevel l, Claim c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        Vec3 f = Vec3.atBottomCenterOf(c.flag);
        for (ServerPlayer p : CrewBases.crewOnline(CrewBases.online(l.getServer()), c)) {
            if (p.level() != l || p.isSpectator() || !p.isAlive() || Characters.get(p).isKnockedDown() || PrisonSystem.isJailed(p)) continue;
            if (p.position().distanceToSqr(f) < r * r) out.add(p);
        }
        for (HumanoidNpc h : BaseHands.present(l, c)) if (able(h) && h.position().distanceToSqr(f) < r * r) out.add(h);
        return out;
    }

    public static void update(MinecraftServer server, Raid r, long now) {
        ServerLevel l = server.overworld();
        Claim c = CrewBases.claimOn(server, r.island);
        if (c == null && r.phase != LEAVING) {
            leave(r, l, now);
            return;
        }
        switch (r.phase) {
            case APPROACH -> {
                if (r.ship.isRemoved() || r.ship.isSinking() || r.ship.naval.crewGone) {
                    end(r, l, "sunk", now);
                    if (c != null) {
                        c.raidsRepelled++;
                        c.dirty();
                        CrewBases.tellCrew(server, c, Component.translatable("grandline.base.raid.sunk", r.ship.shipName()).withStyle(ChatFormatting.GOLD));
                    }
                    return;
                }
                if (CrewBases.crewOnline(CrewBases.online(server), c).isEmpty() && ++r.alone > 60) end(r, l, "called_off", now);
                else if (!CrewBases.crewOnline(CrewBases.online(server), c).isEmpty()) r.alone = 0;
            }
            case ASHORE, FLAG_TAKEN -> ashore(r, l, c, now);
            case LEAVING -> {
                Entity near = r.ship.isRemoved() ? null : l.getNearestPlayer(r.ship, 96);
                if (r.ship.isRemoved() || now - r.phaseAt > LEAVE_TICKS || (near == null && CrewBases.testPlayers == null)) despawn(r, l);
            }
            default -> {}
        }
    }

    private static void ashore(Raid r, ServerLevel l, Claim c, long now) {
        List<GLNpc> standing = new ArrayList<>();
        int found = 0;
        for (UUID u : r.raiders) {
            if (!(l.getEntity(u) instanceof GLNpc m)) continue;
            found++;
            if (able(m)) standing.add(m);
        }
        if (found == 0 && !Chunks.ready(l, c.flag.getX(), c.flag.getZ())) {
            if (++r.missing > 30) end(r, l, "called_off", now); // nobody near enough to keep the fight loaded
            return;
        }
        r.missing = 0;
        if (standing.isEmpty()) {
            repelled(r, l, c, now);
            return;
        }
        Vec3 f = Vec3.atBottomCenterOf(c.flag);
        List<LivingEntity> around = defenders(l, c, 32);
        List<LivingEntity> guarding = new ArrayList<>();
        for (LivingEntity d : around) if (d.position().distanceToSqr(f) < DEFEND * DEFEND) guarding.add(d);
        int atFlag = 0;
        for (GLNpc m : standing) {
            if (m.position().distanceToSqr(f) < AT_FLAG * AT_FLAG && Math.abs(m.getY() - f.y) < 4) atFlag++;
            LivingEntity t = m.getTarget();
            if (t != null && t.isAlive() && !(t instanceof ServerPlayer tp && Characters.get(tp).isKnockedDown())) continue;
            LivingEntity foe = nearest(m, around, 32);
            if (foe != null) m.setTarget(foe);
            else {
                m.setTarget(null);
                if (m.position().distanceToSqr(f) > 9) m.getNavigation().moveTo(f.x, f.y, f.z, 1.0);
            }
        }
        for (HumanoidNpc h : BaseHands.present(l, c))
            if (able(h) && (h.getTarget() == null || !h.getTarget().isAlive())) h.setTarget(nearest(h, new ArrayList<>(standing), 32));
        MinecraftServer server = l.getServer();
        if (r.phase == ASHORE) {
            if (atFlag > 0 && guarding.isEmpty()) {
                if (r.capture == 0)
                    CrewBases.tellCrew(server, c, Component.translatable("grandline.base.raid.at_flag", CrewBases.islandName(server, c), GLConfig.BASE_CAPTURE_SECONDS.get()).withStyle(ChatFormatting.RED));
                r.capture += 20;
                if (r.capture >= GLConfig.BASE_CAPTURE_SECONDS.get() * 20) flagTaken(r, l, c, now);
            } else if (!guarding.isEmpty()) r.capture = 0;
            if (r.phase == ASHORE && now - r.landedAt > RAID_TICKS) plundered(r, l, c, now);
        } else { // FLAG_TAKEN: the grace period
            boolean raidersAtFlag = false;
            for (GLNpc m : standing) if (m.position().distanceToSqr(f) < 8 * 8) raidersAtFlag = true;
            if (!raidersAtFlag && !guarding.isEmpty()) {
                r.phase = ASHORE;
                r.capture = 0;
                r.phaseAt = now;
                CrewBases.announce(server, Component.translatable("grandline.base.raid.retaken", c.crewName(), CrewBases.islandName(server, c)).withStyle(ChatFormatting.GOLD));
            } else if (now - r.lostAt >= GLConfig.BASE_GRACE_SECONDS.get() * 20L) {
                r.outcome = "captured";
                CrewBases.remove(server, c, "captured", r.plunder);
                leave(r, l, now);
            }
        }
    }

    @Nullable
    private static LivingEntity nearest(Entity from, List<? extends LivingEntity> of, double max) {
        LivingEntity best = null;
        double bd = max * max;
        for (LivingEntity m : of) {
            if (!m.isAlive()) continue;
            double d = m.distanceToSqr(from);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    /** The raiders hold the flag: the grace period starts. */
    public static void flagTaken(Raid r, ServerLevel l, Claim c, long now) {
        r.phase = FLAG_TAKEN;
        r.lostAt = r.phaseAt = now;
        MinecraftServer server = l.getServer();
        l.playSound(null, c.flag, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4f, 0.8f);
        CrewBases.announce(server, Component.translatable(r.faction.equals(LivingSea.MARINE) ? "grandline.base.raid.flag_taken_marines" : "grandline.base.raid.flag_taken",
                c.crewName(), CrewBases.islandName(server, c), GLConfig.BASE_GRACE_SECONDS.get()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    /** All raiders beaten: those of the crew who fought are paid; the ship runs. */
    private static void repelled(Raid r, ServerLevel l, Claim c, long now) {
        MinecraftServer server = l.getServer();
        r.outcome = "repelled";
        c.raidsRepelled++;
        c.dirty();
        WorldPlan plan = WorldPlan.forLevel(l);
        Island isl = plan == null ? null : plan.byId(c.island);
        long reward = 1000L * Math.max(1, isl == null ? 1 : isl.tier);
        Set<UUID> fought = r.ship.life.fought.keySet();
        for (ServerPlayer p : CrewBases.crewOnline(CrewBases.online(server), c)) {
            boolean paid = fought.contains(p.getUUID());
            if (paid) {
                Characters.get(p).beli += reward;
                CharacterManager.markDirty(p);
            }
            CrewBases.tell(p, (paid ? Component.translatable("grandline.base.raid.repelled_paid", CrewBases.islandName(server, c), String.format("%,d", reward))
                    : Component.translatable("grandline.base.raid.repelled", CrewBases.islandName(server, c))).withStyle(ChatFormatting.GOLD));
        }
        leave(r, l, now);
    }

    /** Time is up: the raiders carry off a few stacks from the warehouse and row back. */
    private static void plundered(Raid r, ServerLevel l, Claim c, long now) {
        r.outcome = "plundered";
        int taken = 0;
        for (int i = 0; i < c.warehouse.getContainerSize() && taken < 3; i++) {
            ItemStack st = c.warehouse.getItem(i);
            if (st.isEmpty()) continue;
            r.plunder.add(c.warehouse.removeItemNoUpdate(i));
            taken++;
        }
        if (taken > 0) c.warehouse.setChanged();
        MinecraftServer server = l.getServer();
        CrewBases.tellCrew(server, c, Component.translatable("grandline.base.raid.plundered", CrewBases.islandName(server, c), taken).withStyle(ChatFormatting.RED));
        leave(r, l, now);
    }

    /** Raiders still standing go back aboard; the ship weighs anchor and sails off. */
    static void leave(Raid r, ServerLevel l, long now) {
        ShipEntity ship = r.ship;
        for (UUID u : r.raiders) {
            if (!(l.getEntity(u) instanceof GLNpc m) || !able(m) || ship.isRemoved()) continue;
            Vec3 deck = LivingSea.deckSpot(ship, m.getRandom());
            m.removeTag(LivingSea.RAIDER);
            m.removeTag(CrewBases.RAIDER);
            m.setTarget(null);
            m.setAllegiance(m.faction(), BlockPos.ZERO);
            m.teleportTo(deck.x, deck.y, deck.z);
            m.getNavigation().stop();
        }
        if (r.outcome.isEmpty()) r.outcome = "called_off";
        ship.naval.crewGone = false;
        ship.setAnchored(false);
        ship.ferryWaypoint = null;
        r.phase = LEAVING;
        r.phaseAt = now;
    }

    /** End a raid now (the ship and its people go). */
    static void end(Raid r, ServerLevel l, String outcome, long now) {
        if (r.outcome.isEmpty()) r.outcome = outcome;
        despawn(r, l);
    }

    private static void despawn(Raid r, ServerLevel l) {
        RAIDS.remove(r.island, r);
        for (UUID u : r.raiders) {
            Entity e = l.getEntity(u);
            if (e != null && !e.isRemoved()) e.discard();
        }
        if (!r.ship.isRemoved()) NpcShipAi.despawn(r.ship, l);
    }

    /** End a raid at once: the ship and everyone she brought are gone (tests, operators). */
    public static void dismiss(Raid r, ServerLevel l) {
        end(r, l, "called_off", l.getGameTime());
    }

    /** The claim ended (abandoned, captured, struck): its raid sails away. */
    public static void onClaimRemoved(MinecraftServer server, Claim c) {
        Raid r = RAIDS.get(c.island);
        if (r != null && r.phase != LEAVING) leave(r, server.overworld(), server.overworld().getGameTime());
    }

    /** Server stopping: raids live only in memory. */
    public static void clear() {
        RAIDS.clear();
    }

    private BaseRaids() {}
}
