package com.grandline.navigation;

import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.registry.ModEntities;
import com.grandline.ship.ShipClass;
import com.grandline.ship.ShipEntity;
import com.grandline.ship.Shipyard;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Settlement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Earned speed within a sea (agreed: no teleport between seas): dockworkers run ferries to islands you have already
 * discovered in the same sea, and the station masters of the Water 7 line run the Sea Train. Both are real ships
 * that sail there through the world — steering round islands, faster than sail and indifferent to the wind — and
 * put their passengers ashore on the destination's dock.
 */
public final class Ferries {
    public static final String SEA_TRAIN_LANDMARK = "sea_train_station";

    /** Dialogue options "ferry|island|price|name" for this NPC (empty if it runs no service). */
    public static void addOptions(ServerPlayer sp, GLNpc npc, ListTag opts) {
        for (String o : destinations(sp, npc)) opts.add(StringTag.valueOf(o));
    }

    static List<String> destinations(ServerPlayer sp, GLNpc npc) {
        List<String> out = new ArrayList<>();
        WorldPlan plan = WorldPlan.forLevel(sp.level());
        if (plan == null) return out;
        Island here = plan.byId(npc.islandId());
        if (here == null) here = plan.nearest(npc.getBlockX(), npc.getBlockZ(), 300);
        if (here == null) return out;
        CharacterData d = Characters.get(sp);
        boolean seaTrain = npc.role().equals("station_master") && hasStation(here);
        boolean ferry = npc.role().equals("dockworker");
        if (!seaTrain && !ferry) return out;
        List<Island> candidates = new ArrayList<>();
        for (Island i : plan.islands) {
            if (i == here || !i.settlement().hasDock) continue;
            if (seaTrain) {
                if (hasStation(i)) candidates.add(i);
            } else if (i.zone == here.zone && d.discoveredIslands.contains(i.id) && i.distance(here.x, here.z) < 16000) candidates.add(i);
        }
        final Island from = here;
        candidates.sort(Comparator.comparingDouble(i -> i.distance(from.x, from.z)));
        for (Island i : candidates.subList(0, Math.min(6, candidates.size()))) {
            long price = seaTrain ? 300 : 30 + (long) (i.distance(here.x, here.z) / 40);
            String name = i.name != null ? i.name : Component.translatable(i.translationKey()).getString();
            out.add((seaTrain ? "seatrain|" : "ferry|") + i.id + "|" + price + "|" + name);
        }
        return out;
    }

    private static boolean hasStation(Island i) {
        for (Island.LandmarkPlacement lp : i.landmarks) if (lp.id().equals(SEA_TRAIN_LANDMARK)) return true;
        return false;
    }

    /** Handle a chosen "ferry|..." / "seatrain|..." option. */
    public static boolean choose(ServerPlayer sp, GLNpc npc, String option) {
        if (!option.startsWith("ferry|") && !option.startsWith("seatrain|")) return false;
        if (!destinations(sp, npc).contains(option)) return true; // stale or forged
        String[] p = option.split("\\|");
        boolean train = p[0].equals("seatrain");
        WorldPlan plan = WorldPlan.forLevel(sp.level());
        Island dest = plan == null ? null : plan.byId(p[1]);
        long price = Long.parseLong(p[2]);
        CharacterData d = Characters.get(sp);
        if (dest == null) return true;
        if (d.beli < price && !sp.isCreative()) {
            sp.sendSystemMessage(Component.translatable("grandline.shop.poor").withStyle(ChatFormatting.RED));
            return true;
        }
        ShipEntity ship = launch(sp, npc, dest, train);
        if (ship == null) {
            sp.sendSystemMessage(Component.translatable("grandline.ship.no_berth"));
            return true;
        }
        if (!sp.isCreative()) d.beli -= price; // only charged once the passenger is actually aboard
        CharacterManager.markDirty(sp);
        sp.sendSystemMessage(Component.translatable(train ? "grandline.ferry.seatrain_board" : "grandline.ferry.board", p[3]).withStyle(ChatFormatting.AQUA));
        return true;
    }

    /** Launch a ferry (or the Sea Train) next to {@code near} with {@code passenger} aboard; null if there is no water or no seat. */
    @Nullable
    public static ShipEntity launch(Entity passenger, Entity near, Island dest, boolean train) {
        if (!(passenger.level() instanceof ServerLevel l)) return null;
        ShipClass cls = train ? ShipClass.BRIGANTINE : ShipClass.CARAVEL;
        // berth off the local dock tip (open water, bow to the sea) when there is one, else beside the NPC
        WorldPlan plan = WorldPlan.forLevel(l);
        Island here = plan == null ? null : plan.nearest(near.getBlockX(), near.getBlockZ(), 300);
        Vec3 pos = here != null && here.settlement().hasDock ? Shipyard.findWaterPublic(l, BlockPos.containing(dockOf(here))) : null;
        if (pos == null) pos = Shipyard.findWaterPublic(l, near.blockPosition());
        if (pos == null) return null;
        ShipEntity ship = ModEntities.SHIPS.get(cls).get().create(l);
        if (ship == null) return null;
        Vec3 target = dockOf(dest);
        float yaw = (float) Math.toDegrees(Math.atan2(-(target.x - pos.x), target.z - pos.z));
        ship.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        ship.setHull(cls.hull);
        ship.setShipName(train ? "Puffing Tom" : "Ferry");
        if (train) ship.setColors(0x3A3A44, 0x2A6FB0);
        ship.ferryTarget = target;
        ship.ferryIsland = dest.id;
        ship.ferrySpeed = train ? 3.2f : 1.9f;
        l.addFreshEntity(ship);
        int seat = ship.cls.cannons + 1;
        while (seat < ship.seatCount() && !ship.seatPlayer(passenger, seat)) seat++;
        if (passenger.getVehicle() != ship) {
            ship.discard();
            return null;
        }
        return ship;
    }

    /** A point off the destination's dock tip (or its nearest shore). */
    static Vec3 dockOf(Island i) {
        Settlement s = i.settlement();
        if (s.hasDock) {
            double ux = s.dockDirX / 1000.0, uz = s.dockDirZ / 1000.0;
            return new Vec3(s.dockTipX() + ux * 14, WorldPlan.SEA_LEVEL + 0.9, s.dockTipZ() + uz * 14);
        }
        return new Vec3(i.x + i.radius * 1.2, WorldPlan.SEA_LEVEL + 0.9, i.z);
    }

    // ------------------------------------------------------------------ steering (called from ShipEntity)

    /**
     * Steer toward the target, around land; returns the speed to hold this tick. Arrives and lands passengers.
     * Two layers: islands on the way (from the world plan) are rounded through a waypoint off their flank, and the
     * hull's width is swept ahead for docks, rocks and coastline the plan does not know about.
     */
    public static float steer(ShipEntity ship, ServerLevel l) {
        Vec3 to = ship.ferryTarget.subtract(ship.position());
        double dist = to.horizontalDistance();
        if (ship.getPassengers().isEmpty() && ++ship.ferryIdle > 400) {
            ship.discard();
            return 0;
        }
        if (dist < 26) {
            arrive(ship, l);
            return 0;
        }
        if (ship.ferryWaypoint == null || l.getGameTime() % 20 == 0) ship.ferryWaypoint = waypoint(ship, l);
        Vec3 aim = ship.ferryWaypoint != null ? ship.ferryWaypoint.subtract(ship.position()) : to;
        float want = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
        int look = 40 + (int) ship.cls.length;
        if (ship.ferryDetourT > 0) {
            ship.ferryDetourT--;
            want = ship.ferryDetour;
        } else if (free(l, ship, want, look) < look) {
            // bear off to the nearest clear heading; if none is fully clear take the most open one and hold it a while
            float best = want;
            int bestFree = -1;
            search:
            for (int off = 15; off <= 180; off += 15)
                for (int sgn = 1; sgn >= -1; sgn -= 2) {
                    float y = want + off * sgn;
                    int f = free(l, ship, y, look);
                    if (f >= look) { best = y; break search; }
                    if (f > bestFree) { bestFree = f; best = y; }
                }
            ship.ferryDetour = best;
            ship.ferryDetourT = 80;
            want = best;
        }
        // aground or pinned against a quay: the crew poles her off toward the most open water
        if (ship.speed() < 0.02f) {
            if (++ship.ferryStuck > 60) {
                pushOff(ship, l, look);
                ship.ferryStuck = 0;
            }
        } else ship.ferryStuck = 0;
        float diff = Mth.wrapDegrees(want - ship.getYRot());
        ship.setRudder(Mth.clamp(diff / 25f, -1, 1));
        if (ship.shipName().equals("Puffing Tom") && l.getGameTime() % 3 == 0) {
            Vec3 stack = ship.toWorld(0, 6, ship.cls.length * 0.3);
            l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, stack.x, stack.y, stack.z, 2, 0.2, 0.3, 0.2, 0.01);
        }
        float slow = dist < 90 ? (float) Math.max(0.3, dist / 90) : 1f;
        if (Math.abs(diff) > 70) slow = Math.min(slow, 0.35f); // come about before making way
        return ship.cls.speed * ship.ferrySpeed * slow;
    }

    /** A point off the flank of the first island between the ship and its target (null if the way is open). */
    @Nullable
    private static Vec3 waypoint(ShipEntity ship, ServerLevel l) {
        WorldPlan plan = WorldPlan.forLevel(l);
        if (plan == null) return null;
        double px = ship.getX(), pz = ship.getZ();
        double dx = ship.ferryTarget.x - px, dz = ship.ferryTarget.z - pz;
        double len2 = dx * dx + dz * dz;
        if (len2 < 1) return null;
        // inside an island's waters (leaving port, arriving, or pushed in by a detour) with the course running over the
        // island itself: work round it, a quarter-step at a time toward the side the target lies on
        for (Island i : plan.islands) {
            double r = i.radius * 1.25 + 24;
            double cx = i.x - px, cz = i.z - pz;
            if (cx * cx + cz * cz >= r * r) continue;
            double t = Mth.clamp((cx * dx + cz * dz) / len2, 0, 1);
            double qx = dx * t - cx, qz = dz * t - cz;
            double core = core(i, ship);
            if (qx * qx + qz * qz >= core * core) continue;
            double ang = Math.atan2(pz - i.z, px - i.x);
            double delta = Mth.wrapDegrees(Math.toDegrees(Math.atan2(ship.ferryTarget.z - i.z, ship.ferryTarget.x - i.x) - ang));
            double a2 = ang + Math.toRadians(Math.copySign(Math.min(Math.abs(delta), 45), delta));
            return new Vec3(i.x + Math.cos(a2) * (r + 10), ship.getY(), i.z + Math.sin(a2) * (r + 10));
        }
        // islands on the way: round the first one the course crosses (the destination's own core only: its dock lies
        // inside its waters)
        Island first = null;
        double firstT = 2, firstR = 0;
        for (Island i : plan.islands) {
            boolean dest = i.id.equals(ship.ferryIsland);
            double r = dest ? core(i, ship) : i.radius * 1.25 + 24;
            double cx = i.x - px, cz = i.z - pz;
            if (cx * cx + cz * cz < r * r) continue;
            double t = (cx * dx + cz * dz) / len2;
            if (t <= 0 || t >= 1) continue;
            double qx = dx * t - cx, qz = dz * t - cz;
            if (qx * qx + qz * qz >= r * r) continue;
            if (t < firstT) { first = i; firstT = t; firstR = r; }
        }
        if (first == null) return null;
        // pass on the side of the line the island centre is not on
        double qx = px + dx * firstT - first.x, qz = pz + dz * firstT - first.z;
        double ql = Math.sqrt(qx * qx + qz * qz);
        if (ql < 1) { qx = -dz; qz = dx; ql = Math.sqrt(len2); }
        double off = firstR + 30;
        return new Vec3(first.x + qx / ql * off, ship.getY(), first.z + qz / ql * off);
    }

    private static void pushOff(ShipEntity ship, ServerLevel l, int look) {
        float best = ship.getYRot();
        int bestFree = -1;
        for (int a = 0; a < 360; a += 15) {
            int f = free(l, ship, a, look);
            if (f > bestFree) { bestFree = f; best = a; }
        }
        double yaw = Math.toRadians(best);
        double nx = ship.getX() - Math.sin(yaw) * 2.0, nz = ship.getZ() + Math.cos(yaw) * 2.0;
        BlockPos p = BlockPos.containing(nx, WorldPlan.SEA_LEVEL, nz);
        if (!l.getBlockState(p).getFluidState().isSource()) return;
        ship.setPos(nx, ship.getY(), nz);
        ship.setYRot(best);
        ship.ferryDetour = best;
        ship.ferryDetourT = 60;
    }

    /** The island's solid core; for the destination it stops short of the dock point, which may lie close in. */
    private static double core(Island i, ShipEntity ship) {
        double core = i.radius * 1.05 + 8;
        if (i.id.equals(ship.ferryIsland)) core = Math.min(core, i.distance((int) ship.ferryTarget.x, (int) ship.ferryTarget.z) - 8);
        return core;
    }

    /** Clear water ahead on a heading, swept across the hull's beam: blocks until the first obstacle (max if none). */
    private static int free(ServerLevel l, ShipEntity ship, float yawDeg, int max) {
        double yaw = Math.toRadians(yawDeg);
        double c = Math.cos(yaw), s = Math.sin(yaw);
        double half = ship.cls.beam / 2.0 + 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        // from the flanks forward: the hull's own collision checks a quarter-length back, so a ship with a flank on the
        // quay must not see "clear ahead"
        for (int d = (int) (ship.cls.length / 4); d <= max; d += 3)
            for (double side = -half; side <= half + 0.01; side += half) {
                double x = ship.getX() + side * c - d * s, z = ship.getZ() + side * s + d * c;
                p.set(Mth.floor(x), WorldPlan.SEA_LEVEL, Mth.floor(z));
                if (!l.hasChunkAt(p)) return max;
                var at = l.getBlockState(p);
                if (!at.getCollisionShape(l, p).isEmpty() || (!at.getFluidState().isSource() && !at.isAir())) return d;
                p.move(0, 1, 0);
                if (!l.getBlockState(p).getCollisionShape(l, p).isEmpty()) return d;
            }
        return max;
    }

    private static void arrive(ShipEntity ship, ServerLevel l) {
        WorldPlan plan = WorldPlan.forLevel(l);
        Island dest = plan == null ? null : plan.byId(ship.ferryIsland);
        BlockPos land = null;
        if (dest != null && dest.settlement().hasDock) {
            Settlement s = dest.settlement();
            land = new BlockPos(s.dockX, 0, s.dockZ);
            land = land.atY(l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, land.getX(), land.getZ()));
        }
        for (Entity e : List.copyOf(ship.getPassengers())) {
            e.stopRiding();
            if (land == null) continue;
            e.teleportTo(land.getX() + 0.5, land.getY() + 0.1, land.getZ() + 0.5);
            if (e instanceof ServerPlayer sp) sp.sendSystemMessage(Component.translatable("grandline.ferry.arrived").withStyle(ChatFormatting.GOLD));
        }
        ship.ferryTarget = null;
        ship.setAnchored(true);
        ship.ferryIdle = 0;
        ship.discard();
    }

    private Ferries() {}
}
