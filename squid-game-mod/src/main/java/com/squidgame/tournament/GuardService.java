package com.squidgame.tournament;

import com.squidgame.build.ArenaId;
import com.squidgame.build.CommonMarkers;
import com.squidgame.build.Marker;
import com.squidgame.entity.GuardEntity;
import com.squidgame.world.ArenaData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Spawns the masked guards of an arena from its {@code guard.post} markers and gives them their duties. */
public final class GuardService {
    private final ServerLevel level;
    private final List<UUID> guards = new ArrayList<>();

    public GuardService(ServerLevel level) {
        this.level = level;
    }

    /** Spawns a guard at every guard.post (and patrol routes: markers guard.patrol with data route=<id>). */
    public void spawnFor(ArenaId arena, ArenaData data) {
        for (Marker m : data.markers(arena, CommonMarkers.GUARD_POST)) {
            String r = m.get("rank", "triangle");
            int rank = r.equals("circle") ? GuardEntity.RANK_CIRCLE : r.equals("square") ? GuardEntity.RANK_SQUARE : GuardEntity.RANK_TRIANGLE;
            GuardEntity g = NpcFactory.spawnGuard(level, new Vec3(m.x(), m.y(), m.z()), m.yaw(), rank);
            if (g != null) {
                g.assignPost(new Vec3(m.x(), m.y(), m.z()), m.yaw());
                guards.add(g.getUUID());
            }
        }
        // patrol routes: group markers by route id, order by index
        java.util.Map<String, List<Marker>> routes = new java.util.LinkedHashMap<>();
        for (Marker m : data.markers(arena, "guard.patrol")) {
            routes.computeIfAbsent(m.get("route", "a"), k -> new ArrayList<>()).add(m);
        }
        for (List<Marker> route : routes.values()) {
            route.sort(java.util.Comparator.comparingInt(m -> m.getInt("i", 0)));
            if (route.size() >= 2) {
                Marker first = route.get(0);
                GuardEntity g = NpcFactory.spawnGuard(level, new Vec3(first.x(), first.y(), first.z()), first.yaw(),
                        GuardEntity.RANK_TRIANGLE);
                if (g != null) {
                    List<Vec3> pts = new ArrayList<>();
                    for (Marker m : route) {
                        pts.add(new Vec3(m.x(), m.y(), m.z()));
                    }
                    g.assignPatrol(pts);
                    guards.add(g.getUUID());
                }
            }
        }
    }

    public List<GuardEntity> list() {
        List<GuardEntity> out = new ArrayList<>();
        for (UUID u : guards) {
            if (level.getEntity(u) instanceof GuardEntity g && !g.isRemoved()) {
                out.add(g);
            }
        }
        return out;
    }

    /** Nearest armed, idle guard to a position (any guard if none is armed). */
    @Nullable
    public GuardEntity nearestArmed(Vec3 pos) {
        GuardEntity best = null;
        double bd = Double.MAX_VALUE;
        for (GuardEntity g : list()) {
            if (g.isBusyAiming() || !g.isArmed()) {
                continue;
            }
            double d = g.position().distanceToSqr(pos);
            if (d < bd) {
                bd = d;
                best = g;
            }
        }
        return best;
    }

    /** A guard aims and fires at the target; {@code after} runs when the shot lands (or immediately if no guard is free). */
    public void fireAt(LivingEntity target, int delayTicks, Runnable after) {
        GuardEntity g = nearestArmed(target.position());
        if (g == null || g.position().distanceToSqr(target.position()) > 90 * 90) {
            after.run();
            return;
        }
        g.aimAndFire(target, delayTicks, after);
    }

    public void despawnAll() {
        for (UUID u : guards) {
            var e = level.getEntity(u);
            if (e != null) {
                e.discard();
            }
        }
        guards.clear();
    }
}
