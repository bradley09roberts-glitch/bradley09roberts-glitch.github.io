package com.starforged.util;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class Targeting {
    private Targeting() {
    }

    /** Ray-casts blocks from the entity's eyes along its look vector. */
    public static BlockHitResult lookBlock(Entity viewer, double range) {
        Vec3 eye = viewer.getEyePosition();
        Vec3 end = eye.add(viewer.getViewVector(1.0F).scale(range));
        return viewer.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
    }

    /**
     * The point on the ground that the viewer is aiming at. When aiming at the sky the point is projected down onto the terrain.
     */
    public static Vec3 groundTarget(ServerLevel level, Entity viewer, double range) {
        BlockHitResult hit = lookBlock(viewer, range);
        if (hit.getType() != HitResult.Type.MISS) {
            return hit.getLocation();
        }
        BlockPos column = BlockPos.containing(hit.getLocation());
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
        return Vec3.atBottomCenterOf(ground);
    }

    /** Ray-casts entities (stopping at blocks) along the look vector. */
    public static @Nullable Entity lookEntity(Entity viewer, double range) {
        Level level = viewer.level();
        Vec3 eye = viewer.getEyePosition();
        Vec3 look = viewer.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }
        AABB box = viewer.getBoundingBox().expandTowards(look.scale(range)).inflate(1.5);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(viewer, eye, end, box,
            e -> !e.isSpectator() && e.isPickable() && e.isAlive(), range * range);
        return entityHit == null ? null : entityHit.getEntity();
    }

    /**
     * Finds the best homing target for a shooter: the hostile creature closest to the crosshair within a cone.
     */
    public static @Nullable LivingEntity homingTarget(ServerLevel level, LivingEntity shooter, double range, double minDot) {
        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getViewVector(1.0F);
        AABB box = shooter.getBoundingBox().inflate(range);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e != shooter && e.isAlive() && !e.isSpectator()
                && (e instanceof Enemy || (e instanceof Mob mob && mob.getTarget() == shooter))
                && Combat.canHit(level, shooter, e)
                && shooter.hasLineOfSight(e))
            .stream()
            .filter(e -> e.getEyePosition().subtract(eye).normalize().dot(look) >= minDot)
            .min(Comparator.comparingDouble(e -> {
                Vec3 to = e.getEyePosition().subtract(eye);
                double dot = to.normalize().dot(look);
                return (1.0 - dot) * 60.0 + to.length() * 0.1;
            }))
            .orElse(null);
    }
}
