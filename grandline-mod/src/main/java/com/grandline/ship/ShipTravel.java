package com.grandline.ship;

import com.grandline.GrandLine;
import com.grandline.world.WorldPlan;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Moves a ship — with everyone seated on it or standing on its deck — to another dimension (Knock Up Stream to the
 * White Sea, Cloud End back down, the coated dive to the deep sea and the ascent to the New World).
 */
public final class ShipTravel {

    /** Find a ship by its entity UUID in any loaded dimension. */
    @Nullable
    public static ShipEntity find(MinecraftServer server, UUID entityId) {
        for (ServerLevel l : server.getAllLevels()) if (l.getEntity(entityId) instanceof ShipEntity s) return s;
        return null;
    }

    /** Dimension key stored in the ship registry (defaults to the overworld for old records). */
    public static ServerLevel level(MinecraftServer server, @Nullable String dim) {
        if (dim == null || dim.isEmpty()) return server.overworld();
        ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dim)));
        return l != null ? l : server.overworld();
    }

    private record Rider(Entity entity, int seat, Vec3 local, float relYaw) {}

    /**
     * @return the ship in its new dimension (a new entity with the same identity), or null if the target could not
     *         be prepared (the old ship is then left untouched).
     */
    @Nullable
    public static ShipEntity transfer(ShipEntity ship, ServerLevel to, double x, double z, float yaw) {
        if (!(ship.level() instanceof ServerLevel from)) return null;
        ShipEntity n = (ShipEntity) ship.getType().create(to);
        if (n == null) return null;
        to.getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);

        List<Rider> riders = new ArrayList<>();
        for (Entity p : ship.getPassengers()) riders.add(new Rider(p, ship.seatIndex(p), ship.toLocal(p.position()), p.getYRot() - ship.getYRot()));
        double reach = ship.cls.length / 2.0 + 2;
        for (Entity e : from.getEntities(ship, ship.getBoundingBox().inflate(reach, 6, reach), en -> !(en instanceof ShipPart) && !en.isPassenger() && ship.carries(en)))
            riders.add(new Rider(e, -1, ship.toLocal(e.position()), e.getYRot() - ship.getYRot()));

        CompoundTag tag = new CompoundTag();
        ship.saveWithoutId(tag);
        ship.ejectPassengers();
        n.load(tag);
        n.ride = -1;
        n.moveTo(x, WorldPlan.SEA_LEVEL + 0.9, z, yaw, 0);
        ship.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        to.addFreshEntity(n);
        ShipRegistry.get(to.getServer()).update(n);

        for (Rider r : riders) {
            Vec3 w = n.toWorld(r.local().x, Math.max(0.6, r.local().y), r.local().z);
            float ry = yaw + r.relYaw();
            Entity e = r.entity();
            if (e instanceof ServerPlayer sp) {
                sp.teleportTo(to, w.x, w.y, w.z, ry, sp.getXRot());
                sp.fallDistance = 0;
                if (r.seat() >= 0) n.seatPlayer(sp, r.seat());
            } else {
                Entity moved = e.changeDimension(new DimensionTransition(to, w, Vec3.ZERO, ry, e.getXRot(), DimensionTransition.DO_NOTHING));
                if (moved != null && r.seat() >= 0) moved.startRiding(n, true);
            }
        }
        GrandLine.LOGGER.info("Ship {} travelled {} -> {} with {} aboard", n.shipName(), from.dimension().location(), to.dimension().location(), riders.size());
        return n;
    }

    /** Send a lone player (no ship) to another dimension, e.g. falling out of the sky. */
    public static void sendPlayer(ServerPlayer sp, ServerLevel to, double x, double y, double z) {
        sp.teleportTo(to, x, y, z, sp.getYRot(), sp.getXRot());
        sp.fallDistance = 0;
    }

    public static boolean isSky(Level l) {
        return l.dimension() == com.grandline.world.SkyWorld.LEVEL;
    }

    public static boolean isDeep(Level l) {
        return l.dimension() == com.grandline.world.DeepWorld.LEVEL;
    }

    private ShipTravel() {}
}
