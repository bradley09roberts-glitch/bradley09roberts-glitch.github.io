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
        if (!sp.isCreative()) d.beli -= price;
        CharacterManager.markDirty(sp);
        sp.sendSystemMessage(Component.translatable(train ? "grandline.ferry.seatrain_board" : "grandline.ferry.board", p[3]).withStyle(ChatFormatting.AQUA));
        return true;
    }

    @Nullable
    public static ShipEntity launch(ServerPlayer sp, Entity near, Island dest, boolean train) {
        ServerLevel l = sp.serverLevel();
        ShipClass cls = train ? ShipClass.BRIGANTINE : ShipClass.CARAVEL;
        Vec3 pos = Shipyard.findWaterPublic(l, near.blockPosition());
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
        while (seat < ship.seatCount() && !ship.seatPlayer(sp, seat)) seat++;
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

    /** Steer toward the target, around land; returns the speed to hold this tick. Arrives and lands passengers. */
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
        float want = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        // look ahead; if land blocks the way, bear off to whichever side is clear
        if (!clear(l, ship, want, 46)) {
            for (int off = 25; off <= 120; off += 25) {
                if (clear(l, ship, want + off, 46)) { want += off; break; }
                if (clear(l, ship, want - off, 46)) { want -= off; break; }
            }
        }
        float diff = Mth.wrapDegrees(want - ship.getYRot());
        ship.setRudder(Mth.clamp(diff / 25f, -1, 1));
        if (ship.shipName().equals("Puffing Tom") && l.getGameTime() % 3 == 0) {
            Vec3 stack = ship.toWorld(0, 6, ship.cls.length * 0.3);
            l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, stack.x, stack.y, stack.z, 2, 0.2, 0.3, 0.2, 0.01);
        }
        float slow = dist < 90 ? (float) Math.max(0.3, dist / 90) : 1f;
        return ship.cls.speed * ship.ferrySpeed * slow;
    }

    private static boolean clear(ServerLevel l, ShipEntity ship, float yawDeg, int ahead) {
        double yaw = Math.toRadians(yawDeg);
        double hx = -Math.sin(yaw), hz = Math.cos(yaw);
        for (int d = 10; d <= ahead; d += 6) {
            BlockPos p = BlockPos.containing(ship.getX() + hx * d, WorldPlan.SEA_LEVEL + 1, ship.getZ() + hz * d);
            if (!l.hasChunkAt(p)) return true;
            if (!l.getBlockState(p).getCollisionShape(l, p).isEmpty()) return false;
            if (!l.getBlockState(p.below()).getFluidState().isSource() && !l.getBlockState(p.below()).isAir()) return false;
        }
        return true;
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
            if (land != null && e instanceof ServerPlayer sp) {
                sp.teleportTo(land.getX() + 0.5, land.getY() + 0.1, land.getZ() + 0.5);
                sp.sendSystemMessage(Component.translatable("grandline.ferry.arrived").withStyle(ChatFormatting.GOLD));
            }
        }
        ship.ferryTarget = null;
        ship.setAnchored(true);
        ship.ferryIdle = 0;
        ship.discard();
    }

    private Ferries() {}
}
