package com.longnight.world;

import com.longnight.entity.CitizenEntity;
import com.longnight.registry.ModEntities;
import com.longnight.sim.Person;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Caravans on the road. The caravan itself is simulated abstractly (cargo, timing, payment, news);
 * when its position along the route comes within sight of a player, its leader gets a body walking
 * the road with a pack mule, placed where the simulation says they are. Out of sight, the bodies go.
 */
public final class CaravanBodies {
    private CaravanBodies() {}

    record Bodies(UUID leader, UUID mule) {}

    private static final Map<UUID, Bodies> ACTIVE = new HashMap<>();
    public static final String TAG = "longnight_caravan";

    /** A tagged pack mule that is not part of a caravan on the road right now (e.g. loaded from an old save). */
    public static boolean stray(Entity e) {
        if (!e.getTags().contains(TAG)) return false;
        for (Bodies b : ACTIVE.values()) if (e.getUUID().equals(b.mule())) return false;
        return true;
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static void tick(ServerLevel level, WorldSim sim) {
        long day = WorldSim.dayOf(level);
        double dayFrac = (level.getDayTime() % 24000L) / 24000.0;
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (WorldSim.Caravan cv : sim.caravans) {
            Settlement from = sim.settlement(cv.from), to = sim.settlement(cv.to);
            Person leader = sim.person(cv.leader);
            if (from == null || to == null || from.center == null || to.center == null || leader == null || !leader.alive) continue;
            double span = Math.max(1, cv.arriveDay - cv.departDay);
            double t = Math.max(0, Math.min(1, (day - cv.departDay + dayFrac) / span));
            int x = (int) Math.round(from.center.getX() + (to.center.getX() - from.center.getX()) * t);
            int z = (int) Math.round(from.center.getZ() + (to.center.getZ() - from.center.getZ()) * t);
            // inside a settlement the people there are embodied already
            if (Math.sqrt(from.center.distSqr(new BlockPos(x, from.center.getY(), z))) < from.radius + 10
                    || Math.sqrt(to.center.distSqr(new BlockPos(x, to.center.getY(), z))) < to.radius + 10) continue;
            boolean near = false;
            for (ServerPlayer p : level.players()) {
                double dx = p.getX() - x, dz = p.getZ() - z;
                if (dx * dx + dz * dz < 96 * 96) near = true;
            }
            if (!near || !level.hasChunk(x >> 4, z >> 4)) continue;
            seen.add(cv.id);
            Bodies b = ACTIVE.get(cv.id);
            Entity e = b == null ? null : level.getEntity(b.leader());
            if (e instanceof CitizenEntity c && c.isAlive()) {
                // keep walking toward the destination
                if (c.getNavigation().isDone()) {
                    double dx = to.center.getX() - c.getX(), dz = to.center.getZ() - c.getZ();
                    double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
                    BlockPos step = Places.ground(level, BlockPos.containing(c.getX() + dx / len * 16, c.getY(), c.getZ() + dz / len * 16));
                    c.getNavigation().moveTo(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, 0.5);
                }
                continue;
            }
            Embodiment.discardBody(level, leader.id);
            BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            CitizenEntity body = ModEntities.CITIZEN.get().create(level);
            if (body == null) continue;
            body.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            body.bind(leader, from, day, sim);
            body.brain.pause(Integer.MAX_VALUE / 2); // the road, not the town routine, guides them
            leader.activity = (cv.returning ? "returning home to " + to.name : "leading a caravan to " + to.name);
            level.addFreshEntity(body);
            Mule mule = EntityType.MULE.create(level);
            UUID muleId = null;
            if (mule != null) {
                mule.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
                mule.setTamed(true);
                mule.setChest(true);
                mule.setLeashedTo(body, true);
                mule.setCustomName(Component.literal(from.name + " pack mule"));
                mule.setPersistenceRequired();
                mule.addTag(TAG);
                mule.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
                level.addFreshEntity(mule);
                muleId = mule.getUUID();
            }
            Embodiment.trackRaider(leader.id, body.getUUID());
            ACTIVE.put(cv.id, new Bodies(body.getUUID(), muleId));
        }
        // caravans out of sight (or finished) lose their bodies
        ACTIVE.entrySet().removeIf(en -> {
            if (seen.contains(en.getKey())) return false;
            Entity l = level.getEntity(en.getValue().leader());
            if (l != null) l.discard();
            if (en.getValue().mule() != null) {
                Entity m = level.getEntity(en.getValue().mule());
                if (m != null) m.discard();
            }
            return true;
        });
    }
}
