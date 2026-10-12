package com.grandline.fruit.impl;

import com.grandline.combat.AbilityContext;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.TerrainChanges;
import com.grandline.net.GLNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Shared helpers for ability implementations (fruits, styles, Haki, weapons). */
public final class FruitKit {
    public static float dmg(AbilityContext c, float base) {
        return base * c.power * c.masteryScale() * (c.awakened ? 1.35f : 1f);
    }

    public static AbilityProjectile shoot(AbilityContext c, String kind, float size, double speed, float damage, Hits.Info info) {
        Vec3 dir = aimDir(c);
        Vec3 from = c.eye().add(dir.scale(0.6)).add(0, -0.15, 0);
        return AbilityProjectile.shoot(c.user, kind, size, from, dir, speed, damage, info);
    }

    /** Aim at the lock-on target when present, else along the look vector. */
    public static Vec3 aimDir(AbilityContext c) {
        if (c.target != null && c.target.isAlive()) {
            return c.target.getBoundingBox().getCenter().subtract(c.eye()).normalize();
        }
        return c.look();
    }

    public static int aoe(AbilityContext c, Vec3 center, double radius, float damage, Hits.Info info) {
        int n = 0;
        for (LivingEntity e : Hits.sphere(c.user, center, radius)) {
            if (Hits.hit(c.user, e, damage, info) == Hits.Result.HIT) n++;
        }
        return n;
    }

    public static int cone(AbilityContext c, double range, double arc, float damage, Hits.Info info) {
        int n = 0;
        for (LivingEntity e : Hits.cone(c.user, range, arc)) {
            if (Hits.hit(c.user, e, damage, info) == Hits.Result.HIT) n++;
        }
        return n;
    }

    public static int line(AbilityContext c, double length, double width, float damage, Hits.Info info) {
        int n = 0;
        Vec3 dir = aimDir(c);
        for (LivingEntity e : Hits.line(c.user, c.eye(), dir, length, width)) {
            if (Hits.hit(c.user, e, damage, info) == Hits.Result.HIT) n++;
        }
        return n;
    }

    /** First solid hit along the aim ray (or the max-range point). */
    public static Vec3 aimPoint(AbilityContext c, double range) {
        if (c.target != null && c.target.isAlive() && c.target.distanceTo(c.user) <= range) return c.target.position();
        Vec3 from = c.eye(), to = from.add(c.look().scale(range));
        BlockHitResult r = c.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.user));
        return r.getType() == HitResult.Type.MISS ? to : r.getLocation();
    }

    /** Blink forward along the look ray up to {@code dist}, stopping before walls. */
    public static Vec3 blink(LivingEntity user, Vec3 dir, double dist) {
        Vec3 from = user.position().add(0, 0.5, 0);
        Vec3 to = from.add(dir.normalize().scale(dist));
        BlockHitResult r = user.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        Vec3 end = r.getType() == HitResult.Type.MISS ? to : r.getLocation().subtract(dir.normalize().scale(0.8));
        user.teleportTo(end.x, Math.max(end.y - 0.5, user.level().getMinBuildHeight() + 1), end.z);
        user.fallDistance = 0;
        return end;
    }

    /** Velocity change: players are client-authoritative so they receive an impulse packet. */
    public static void impulse(LivingEntity e, Vec3 v, boolean set) {
        if (e instanceof ServerPlayer sp) {
            GLNetwork.toPlayer(sp, new GLNetwork.Impulse(v.x, v.y, v.z, set));
        }
        e.setDeltaMovement(set ? v : e.getDeltaMovement().add(v));
        e.hurtMarked = true;
        e.fallDistance = 0;
    }

    public static void fx(LivingEntity near, String kind, Vec3 p, float a, float b, float c) {
        GLNetwork.fx(near, kind, p.x, p.y, p.z, a, b, c);
    }

    public static void temp(ServerLevel level, BlockPos pos, BlockState state) {
        TerrainChanges.place(level, pos, state);
    }

    public static Hits.Info info(Element e, float kb) {
        return Hits.Info.of(e, kb);
    }

    public static List<LivingEntity> nearby(AbilityContext c, double r) {
        return Hits.sphere(c.user, c.user.position().add(0, 1, 0), r);
    }

    private FruitKit() {}
}
