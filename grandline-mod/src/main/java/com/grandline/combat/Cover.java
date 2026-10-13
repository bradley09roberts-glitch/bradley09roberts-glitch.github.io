package com.grandline.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Line of effect. Every hit needs an open path from where it comes from to some part of the victim's body, traced
 * through the blocks' real collision shapes: walls, floors, closed doors, fences and leaves stop it; open doors,
 * grass, flowers, water and the gap above a half slab do not. Partial cover therefore protects only the covered part:
 * a foe behind a waist-high wall can still be struck in the head.
 * <p>
 * Where a hit comes from:
 * <ul>
 * <li>melee and most techniques: the attacker's eyes, or its body when the eyes are inside a block (giants under a
 * ceiling);</li>
 * <li>projectiles: the point where the projectile met the victim (its flight is swept against blocks first);</li>
 * <li>explosions, shockwaves and lingering zones: their centre, checked when the area picks its victims
 * ({@link Hits#sphere});</li>
 * <li>anything else that sets an origin with {@link #from}.</li>
 * </ul>
 * Exceptions are narrow and marked on the hit itself ({@link Hits.Info#penetrating()}): boss floor hazards that hurt
 * whoever stands in them, and a few powers that work through matter by design (they are listed in OVERHAUL_AUDIT.md).
 */
public final class Cover {
    /** Pairs a target picker has already checked this tick: (attacker id << 32 | victim id) to game time. */
    private static final Map<Long, Long> CLEARED = new HashMap<>();
    /** Where the hit being resolved comes from, when it is not the attacker's body. */
    private static final ThreadLocal<Vec3> ORIGIN = new ThreadLocal<>();
    private static long checks, blocked;

    private Cover() {}

    // ------------------------------------------------------------------ rays

    /** Whether nothing solid lies on the straight segment a to b. */
    public static boolean clear(Level level, Vec3 a, Vec3 b) {
        if (a.distanceToSqr(b) < 1e-6) return true;
        return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
                .getType() == HitResult.Type.MISS;
    }

    /** Whether a point lies inside a block's collision shape. */
    public static boolean insideBlock(Level level, Vec3 p) {
        BlockPos bp = BlockPos.containing(p);
        VoxelShape s = level.getBlockState(bp).getCollisionShape(level, bp, CollisionContext.empty());
        if (s.isEmpty()) return false;
        for (AABB box : s.toAabbs()) if (box.move(bp).contains(p)) return true;
        return false;
    }

    /** Points on a body that count as "it": centre, head, feet and, for broad bodies, both flanks. */
    private static Vec3[] samples(AABB b, Vec3 from) {
        Vec3 c = b.getCenter();
        double h = b.getYsize();
        Vec3 near = new Vec3(clamp(from.x, b.minX + 0.05, b.maxX - 0.05), clamp(from.y, b.minY + 0.05, b.maxY - 0.05),
                clamp(from.z, b.minZ + 0.05, b.maxZ - 0.05));
        Vec3 head = new Vec3(c.x, b.minY + h * 0.88, c.z), feet = new Vec3(c.x, b.minY + Math.min(0.3, h * 0.2), c.z);
        if (b.getXsize() < 1.2) return new Vec3[]{c, head, near, feet};
        Vec3 to = c.subtract(from).multiply(1, 0, 1);
        Vec3 side = to.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : new Vec3(-to.z, 0, to.x).normalize();
        double w = b.getXsize() * 0.4;
        return new Vec3[]{c, head, near, feet, c.add(side.scale(w)), c.subtract(side.scale(w))};
    }

    private static double clamp(double v, double lo, double hi) {
        return lo > hi ? (lo + hi) / 2 : Math.max(lo, Math.min(hi, v));
    }

    /** Whether some part of the victim's body can be reached in a straight line from a point. */
    public static boolean exposed(Level level, Vec3 from, Entity victim) {
        return exposed(level, from, victim.getBoundingBox());
    }

    public static boolean exposed(Level level, Vec3 from, AABB body) {
        checks++;
        if (body.inflate(0.3).contains(from)) return true;
        for (Vec3 p : samples(body, from)) if (clear(level, from, p)) return true;
        blocked++;
        return false;
    }

    /** The first solid point on the way from the attacker to the victim (for the "blocked" spark), or null. */
    @Nullable
    public static Vec3 obstruction(LivingEntity attacker, Entity victim) {
        Vec3 a = attacker.getEyePosition(), b = victim.getBoundingBox().getCenter();
        var r = attacker.level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return r.getType() == HitResult.Type.MISS ? null : r.getLocation();
    }

    /** Where an entity's blows come from: its eyes, else its chest, else its waist (whichever is not inside a block). */
    public static Vec3[] origins(LivingEntity attacker) {
        Level l = attacker.level();
        AABB b = attacker.getBoundingBox();
        Vec3 eye = attacker.getEyePosition(), mid = b.getCenter(), low = new Vec3(mid.x, b.minY + Math.min(0.5, b.getYsize() * 0.3), mid.z);
        Vec3[] all = {eye, mid, low};
        int n = 0;
        Vec3[] out = new Vec3[3];
        for (Vec3 v : all) if (!insideBlock(l, v)) out[n++] = v;
        return java.util.Arrays.copyOf(out, n);
    }

    /** Whether the attacker could strike some part of the victim from where it stands. */
    public static boolean reachable(LivingEntity attacker, Entity victim) {
        for (Vec3 o : origins(attacker)) if (exposed(attacker.level(), o, victim)) return true;
        return false;
    }

    /** Whether a viewer can see a target (lock-on, AI line of sight, nameplates): the same test as for blows. */
    public static boolean canSee(LivingEntity viewer, Entity target) {
        return reachable(viewer, target);
    }

    /** Move a point that sits inside a block (an impact on a wall's face, a mark on the ground) out into the open. */
    public static Vec3 freeOrigin(Level level, Vec3 p, @Nullable Vec3 toward) {
        if (!insideBlock(level, p)) return p;
        if (toward != null) {
            Vec3 d = toward.subtract(p);
            if (d.lengthSqr() > 1e-4) {
                d = d.normalize();
                for (double s = 0.25; s <= 1.01; s += 0.25) {
                    Vec3 q = p.add(d.scale(s));
                    if (!insideBlock(level, q)) return q;
                }
            }
        }
        for (double y = 0.5; y <= 1.51; y += 0.5) {
            Vec3 q = p.add(0, y, 0);
            if (!insideBlock(level, q)) return q;
        }
        return p;
    }

    // ------------------------------------------------------------------ the gate in Hits.hit

    /** Run a hit (or several) as coming from a point instead of the attacker's body. */
    public static void from(Vec3 origin, Runnable body) {
        Vec3 prev = ORIGIN.get();
        ORIGIN.set(origin);
        try {
            body.run();
        } finally {
            ORIGIN.set(prev);
        }
    }

    public static <T> T from(Vec3 origin, Supplier<T> body) {
        Vec3 prev = ORIGIN.get();
        ORIGIN.set(origin);
        try {
            return body.get();
        } finally {
            ORIGIN.set(prev);
        }
    }

    @Nullable
    public static Vec3 currentOrigin() {
        return ORIGIN.get();
    }

    /**
     * The victim was found in the open from the attack's origin by a target picker (Hits.cone/sphere/line) or by a
     * projectile's swept flight; the hit that follows in the same tick does not need a second check.
     */
    public static void markCleared(LivingEntity attacker, LivingEntity victim) {
        long now = attacker.level().getGameTime();
        if (CLEARED.size() > 4096) {
            for (Iterator<Long> it = CLEARED.values().iterator(); it.hasNext(); ) if (it.next() < now - 1) it.remove();
        }
        CLEARED.put(key(attacker, victim), now);
    }

    private static long key(LivingEntity a, LivingEntity v) {
        return ((long) a.getId() << 32) | (v.getId() & 0xFFFFFFFFL);
    }

    /** The check every hit passes through: picked in the open this tick, or reachable from its origin now. */
    static boolean lineOfEffect(LivingEntity attacker, LivingEntity victim) {
        Level l = attacker.level();
        Long t = CLEARED.get(key(attacker, victim));
        if (t != null && t >= l.getGameTime() - 1) return true;
        Vec3 o = ORIGIN.get();
        if (o != null) return exposed(l, freeOrigin(l, o, victim.getBoundingBox().getCenter()), victim);
        return reachable(attacker, victim);
    }

    /** Diagnostics for the self-test: how many exposure checks ran and how many found full cover. */
    public static long[] stats() {
        return new long[]{checks, blocked};
    }
}
