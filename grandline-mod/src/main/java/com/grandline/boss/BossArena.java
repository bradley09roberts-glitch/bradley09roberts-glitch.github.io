package com.grandline.boss;

import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.TerrainChanges;
import com.grandline.fruit.impl.PowerKit;
import com.grandline.haki.Haki;
import com.grandline.net.GLNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Arena behaviour: lingering hazard zones (magma, poison gas, swamp, whirlpools, darkness, a closing cage), arena
 * events that reuse the telegraph system (falling stars, lightning, light pillars, shockwaves) and temporary terrain
 * (a lava crust over the sea). Terrain changes go through {@link TerrainChanges}: they revert, and towns are spared.
 */
public final class BossArena {

    /** A zone that pulses every half second while it lasts. */
    public static final class Hazard {
        final Vec3 center;
        final double radius;
        final long until;
        final String style;
        final float power;
        final Element element;
        final List<MobEffectInstance> effects = new ArrayList<>();
        double pull, swirl;
        /** A closing cage: hurts and drags in whoever is OUTSIDE the radius, which shrinks to {@link #endRadius}. */
        boolean outside;
        double endRadius;
        final long born;
        int pulses;

        Hazard(Vec3 center, double radius, long born, long until, String style, float power, Element element) {
            this.center = center;
            this.radius = radius;
            this.endRadius = radius;
            this.born = born;
            this.until = until;
            this.style = style;
            this.power = power;
            this.element = element;
        }

        public Vec3 center() { return center; }
        public String style() { return style; }

        public double radius(long now) {
            if (endRadius == radius || until <= born) return radius;
            double t = Math.min(1, Math.max(0, (now - born) / (double) (until - born)));
            return radius + (endRadius - radius) * t;
        }

        public Hazard effect(Holder<MobEffect> e, int ticks, int amp) {
            effects.add(new MobEffectInstance(e, ticks, amp));
            return this;
        }

        public Hazard pull(double p) { pull = p; return this; }
        public Hazard swirl(double s) { swirl = s; return this; }

        public Hazard cage(double end) {
            outside = true;
            endRadius = end;
            return this;
        }

        /** Whether a foe at this position is caught by the zone. */
        public boolean catches(LivingEntity e, long now) {
            double dy = e.getY() - center.y;
            double dx = e.getX() - center.x, dz = e.getZ() - center.z;
            double d = Math.sqrt(dx * dx + dz * dz), r = radius(now);
            if (outside) return d > r - 0.5 && d < r + 16 && dy > -6 && dy < 12;
            return dy >= -Telegraph.BELOW && dy <= Telegraph.ABOVE && d <= r + e.getBbWidth() * 0.5;
        }

        void tick(BossFight f, long now) {
            pulses++;
            double r = radius(now);
            GLNetwork.fx(f.boss, "boss_hazard_" + style, center.x, center.y + 0.1, center.z, (float) r, outside ? 1 : 0, 0);
            double reach = outside ? r + 16 : r + 2;
            boolean imbued = Haki.armamentLevel(f.boss) > 0;
            for (LivingEntity e : f.inBox(new AABB(center, center).inflate(reach, 8, reach))) {
                if (!f.isFoe(e) || !catches(e, now)) continue;
                if (power > 0 && pulses % 2 == 0) {
                    // a hazard hurts whoever stands in it, walls or not (a documented line-of-effect exception)
                    Hits.announced(() -> Hits.hit(f.boss, e, f.strike() * power * f.damageScale(), new Hits.Info(element, 0f, 6, 0, imbued, false, true, "hit").asPenetrating()));
                }
                for (MobEffectInstance m : effects) e.addEffect(new MobEffectInstance(m));
                if (pull > 0) PowerKit.pull(e, center, pull, 0);
                if (swirl > 0) PowerKit.swirl(e, center, swirl, 0.08, 0);
                if (outside) PowerKit.pull(e, center, 0.6, 0.15);
            }
        }
    }

    // ------------------------------------------------------------------ helpers for arena scripts

    /** Add a hazard zone that lasts {@code seconds}; {@code power} is a fraction of the boss's strike per pulse. */
    public static Hazard hazard(BossFight f, Vec3 center, double radius, int seconds, String style, float power, Element element) {
        long now = f.level.getGameTime();
        Hazard h = new Hazard(center, radius, now, now + seconds * 20L, style, power, element);
        f.hazards.add(h);
        return h;
    }

    /** An arena event through the telegraph system (it does not stop the boss). */
    public static Telegraph event(BossFight f, BossMove move, Vec3 at) {
        return f.startMove(move, null, at, false);
    }

    /** An arena beam that crosses the field from {@code from} through {@code through} (a LINE move). */
    public static Telegraph beam(BossFight f, BossMove move, Vec3 from, Vec3 through) {
        return f.startMove(move, null, through, false, from);
    }

    /** Foes still on their feet near the boss. */
    public static List<LivingEntity> foes(BossFight f) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : f.participants.values()) if (f.fighting(e) && e.distanceToSqr(f.boss) < 40 * 40) out.add(e);
        return out;
    }

    /** A foe's feet, or a random spot around the boss when nobody is near. */
    public static Vec3 nearFoe(BossFight f, double jitter) {
        List<LivingEntity> foes = foes(f);
        var r = f.boss.getRandom();
        if (foes.isEmpty()) return spot(f, 3, 10);
        Vec3 p = foes.get(r.nextInt(foes.size())).position();
        if (jitter <= 0) return p;
        return f.ground(p.x + (r.nextDouble() - 0.5) * 2 * jitter, p.z + (r.nextDouble() - 0.5) * 2 * jitter, p.y);
    }

    /** A random spot on the ground between two distances from the boss. */
    public static Vec3 spot(BossFight f, double min, double max) {
        var r = f.boss.getRandom();
        double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
        Vec3 o = f.boss.position();
        return f.ground(o.x + Math.cos(a) * d, o.z + Math.sin(a) * d, o.y);
    }

    /**
     * Temporary terrain: covers the surface (land or open water) around a point with a block. Reverts on its own;
     * towns are protected. Returns how many blocks changed.
     */
    public static int crust(BossFight f, Vec3 c, int radius, BlockState land, BlockState sea) {
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius || f.boss.getRandom().nextInt(3) == 0) continue;
                int x = (int) Math.floor(c.x) + dx, z = (int) Math.floor(c.z) + dz;
                int top = f.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                if (Math.abs(top - c.y) > 6) continue;
                m.set(x, top, z);
                BlockState s = f.level.getBlockState(m);
                if (s.getFluidState().isSource() && s.is(Blocks.WATER)) {
                    if (TerrainChanges.place(f.level, m.immutable(), sea)) n++;
                } else if (!s.isAir() && s.getFluidState().isEmpty() && s.isCollisionShapeFullBlock(f.level, m) && f.level.getBlockEntity(m) == null) {
                    if (TerrainChanges.place(f.level, m.immutable(), land)) n++;
                }
            }
        return n;
    }

    /** Whether the boss stands at or near open water (sea arenas: on a ship, on the shore, in the shallows). */
    public static boolean nearWater(BossFight f) {
        BlockPos p = f.boss.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-6, -3, -6), p.offset(6, 1, 6)))
            if (f.level.getBlockState(q).is(Blocks.WATER)) return true;
        return false;
    }

    private BossArena() {}
}
