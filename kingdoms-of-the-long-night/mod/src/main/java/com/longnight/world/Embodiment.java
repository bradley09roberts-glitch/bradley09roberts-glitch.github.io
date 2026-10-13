package com.longnight.world;

import com.longnight.Config;
import com.longnight.LongNight;
import com.longnight.entity.CitizenEntity;
import com.longnight.registry.ModBlocks;
import com.longnight.registry.ModEntities;
import com.longnight.sim.Building;
import com.longnight.sim.Person;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Detailed simulation near players, abstract simulation everywhere else. Settlements within
 * range of a player are "active": they are materialized (their pre-existing buildings raised),
 * their people get bodies (at most one per person, capped), and construction done in the
 * distance is caught up. When players leave, bodies are discarded; the people live on in the
 * simulation with identity, memories and possessions intact.
 */
public final class Embodiment {
    private Embodiment() {}

    private static final Map<UUID, UUID> BODIES = new HashMap<>();      // person -> entity
    private static final Set<UUID> ACTIVE = new HashSet<>();            // settlements with bodies
    private static final Set<UUID> VISIBLE = new HashSet<>();           // settlements being raised before the player arrives
    /**
     * Settlements watched by an admin/test "observer", treated as if a player stood there. The centre chunk is forced the
     * vanilla way (that keeps an empty world ticking); the rest load asynchronously, like a player's chunks (forcing them
     * all with setChunkForced generates them on the server thread: a ~10 s stall on a fresh world).
     */
    private static final Set<UUID> OBSERVED = new HashSet<>();

    public static boolean observe(ServerLevel level, Settlement s, boolean on) {
        int r = (s.radius + 32) >> 4;
        int cx = s.center.getX() >> 4, cz = s.center.getZ() >> 4;
        level.setChunkForced(cx, cz, on);
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (x != cx || z != cz) level.getChunkSource().updateChunkForced(new net.minecraft.world.level.ChunkPos(x, z), on);
            }
        }
        return on ? OBSERVED.add(s.id) : OBSERVED.remove(s.id);
    }

    public static boolean isActive(Settlement s) {
        return ACTIVE.contains(s.id);
    }

    public static int bodyCount() {
        return BODIES.size();
    }

    public static UUID bodyOf(UUID person) {
        return BODIES.get(person);
    }

    public static void trackRaider(UUID person, UUID entity) {
        BODIES.put(person, entity);
    }

    public static void clear() {
        BODIES.clear();
        ACTIVE.clear();
        VISIBLE.clear();
        OBSERVED.clear();
    }

    public static void tick(ServerLevel level, WorldSim sim) {
        long gameTime = level.getGameTime();
        long time = level.getDayTime() % 24000L;
        boolean workHours = time >= 1000 && time < 11000;
        int range = Config.EMBODY_DISTANCE.get();
        int budget = Config.PLACE_BLOCKS_PER_TICK.get();
        List<ServerPlayer> players = level.players();
        // catch-up placement runs every tick for settlements a player can (nearly) see
        for (Settlement s : sim.settlements.values()) {
            if (VISIBLE.contains(s.id) && s.materialized && Placer.pending(s)) Placer.catchUp(level, sim, s, budget);
        }
        if (gameTime % 20 != 0) return;
        long day = WorldSim.dayOf(level);
        Set<UUID> nowActive = new HashSet<>();
        for (Settlement s : sim.settlements.values()) {
            if (s.center == null) continue;
            double best = Double.MAX_VALUE;
            for (ServerPlayer p : players) {
                if (p.isSpectator()) continue;
                double dx = p.getX() - s.center.getX(), dz = p.getZ() - s.center.getZ();
                best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
            }
            boolean near = best <= range + s.radius || OBSERVED.contains(s.id);
            boolean visible = near || best <= Math.max(range, 176) + s.radius;
            if (visible && level.isLoaded(s.center)) {
                // raise the place a little before the traveller arrives, so it is already there when seen
                if (!s.materialized) materialize(level, sim, s);
                VISIBLE.add(s.id);
            } else VISIBLE.remove(s.id);
            if (near && level.isLoaded(s.center)) {
                nowActive.add(s.id);
                if (workHours) sim.embodiedTicksToday.merge(s.id, 20 * 24000 / 10000, Integer::sum);
                for (ServerPlayer p : players) {
                    double dx = p.getX() - s.center.getX(), dz = p.getZ() - s.center.getZ();
                    if (Math.sqrt(dx * dx + dz * dz) < s.radius + 24) Arrival.visit(p, sim, s, day);
                }
            }
        }
        // bodies: spawn for active settlements, discard for inactive ones
        for (UUID sid : ACTIVE) {
            if (!nowActive.contains(sid)) {
                Settlement s = sim.settlement(sid);
                if (s != null) for (UUID pid : new ArrayList<>(s.residents)) discardBody(level, pid);
            }
        }
        ACTIVE.clear();
        ACTIVE.addAll(nowActive);
        int total = 0;
        for (UUID sid : ACTIVE) {
            Settlement s = sim.settlement(sid);
            if (s == null || s.kind == Settlement.Kind.RUIN) continue;
            int spawned = 0;
            for (Person p : sim.residents(s)) {
                if (total >= Config.MAX_BODIES_TOTAL.get() || spawned >= Config.MAX_BODIES_PER_SETTLEMENT.get()) break;
                UUID eid = BODIES.get(p.id);
                Entity existing = eid == null ? null : level.getEntity(eid);
                if (existing instanceof CitizenEntity c && c.isAlive()) {
                    if (!s.id.equals(c.settlementId) && c.raidTarget == null) {
                        c.discard();
                        BODIES.remove(p.id);
                    } else {
                        total++;
                        spawned++;
                        continue;
                    }
                }
                if (p.activity.startsWith("leading a caravan") || p.activity.startsWith("returning home")) continue; // away on the road
                if (spawnBody(level, sim, s, p, day) != null) {
                    total++;
                    spawned++;
                }
            }
            // the dead of this place are laid to rest once there is a burial ground
            if (gameTime % 200 == 0) buryDead(level, sim, s);
        }
        // forget bodies that vanished (chunk unloads, deaths)
        BODIES.entrySet().removeIf(e -> {
            Entity en = level.getEntity(e.getValue());
            return en == null || !en.isAlive();
        });
    }

    static CitizenEntity spawnBody(ServerLevel level, WorldSim sim, Settlement s, Person p, long day) {
        BlockPos at = null;
        Building home = s.building(p.home);
        if (home != null && home.origin != null && home.placed >= home.total) at = Places.mark(level, s, home, "work");
        if (at == null) {
            Building work = s.building(p.workplace);
            if (work != null && work.origin != null && work.placed >= work.total) at = Places.mark(level, s, work, "work");
        }
        if (at == null) at = s.center.offset(level.random.nextInt(9) - 4, 0, level.random.nextInt(9) - 4);
        // only where the chunk's entities are loaded and ticking: a body added to a chunk whose entities are still
        // loading is invisible to getEntity, which would make it look missing and get the person a second body
        if (!level.isLoaded(at) || !level.isPositionEntityTicking(at)) return null;
        if (home == null || home.placed < home.total) at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        CitizenEntity e = ModEntities.CITIZEN.get().create(level);
        if (e == null) return null;
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
        e.bind(p, s, day, sim);
        e.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        if (!level.addFreshEntity(e)) return null;
        BODIES.put(p.id, e.getUUID());
        return e;
    }

    public static void discardBody(ServerLevel level, UUID person) {
        UUID eid = BODIES.remove(person);
        if (eid == null) return;
        Entity e = level.getEntity(eid);
        if (e != null) e.discard();
    }

    /** First approach: the settlement's long-standing buildings rise (progressively) and its hearth is set. */
    static void materialize(ServerLevel level, WorldSim sim, Settlement s) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.center);
        s.center = new BlockPos(s.center.getX(), top.getY() - 1, s.center.getZ());
        for (Building b : s.buildings) if (b.origin != null && b.placed == 0) b.origin = new BlockPos(b.origin.getX(), s.center.getY(), b.origin.getZ());
        if (s.kind == Settlement.Kind.SETTLEMENT && !sim.claimed(s.center, 0)) {
            BlockPos hearth = s.center.offset(0, 1, -4); // in front of the well, which occupies the centre plot
            if (level.getBlockState(hearth).canBeReplaced() || level.getBlockState(hearth).isAir()) {
                level.setBlock(hearth, ModBlocks.HEARTHSTONE.get().defaultBlockState(), 3);
            }
        }
        s.materialized = true;
        sim.setDirty();
        LongNight.LOG.info("Materialized {} ({} buildings, {} people)", s.name, s.buildings.size(), s.residents.size());
    }

    static void buryDead(ServerLevel level, WorldSim sim, Settlement s) {
        for (Person p : sim.people.values()) {
            if (!p.alive && !p.buried && s.id.equals(p.settlement) && p.deathDay >= 0) {
                if (!Placer.bury(level, sim, s, p)) return;
            }
        }
    }

    public static void announce(ServerLevel level, Settlement s, String msg) {
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - s.center.getX(), dz = p.getZ() - s.center.getZ();
            if (dx * dx + dz * dz < (s.radius + 200.0) * (s.radius + 200.0) || s.citizenPlayers.contains(p.getUUID())) {
                p.sendSystemMessage(Component.literal("§6[" + s.name + "]§r " + msg));
            }
        }
    }
}
