package com.grandline.boss;

import com.grandline.combat.CombatState;
import com.grandline.combat.Hits;
import com.grandline.fruit.impl.FruitKit;
import com.grandline.fruit.impl.PowerKit;
import com.grandline.net.GLNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One telegraphed attack in flight: the marker geometry is fixed when the wind-up starts, ground markers are drawn
 * until it lands ({@code boss_mark_*} effects, turning "hot" for the last half second), then everyone still inside
 * is hit. Foes who left the marker in time are not touched.
 */
public final class Telegraph {
    /** Vertical window around the marker's ground level in which an entity counts as standing in it. */
    static final double BELOW = 2.5, ABOVE = 4.0;

    final BossFight fight;
    public final BossMove move;
    /** The boss's own signature attack (it stands still and winds up) or an arena event. */
    public final boolean own;
    final Vec3 origin, dir;
    final List<Vec3> centers;
    public final long start, hitAt;
    boolean done;
    /** Result per entity of the last resolution (tests and debugging). */
    final Map<LivingEntity, Hits.Result> results = new LinkedHashMap<>();

    Telegraph(BossFight fight, BossMove move, boolean own, Vec3 origin, Vec3 dir, List<Vec3> centers, long start, int windup) {
        this.fight = fight;
        this.move = move;
        this.own = own;
        this.origin = origin;
        this.dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize();
        this.centers = centers;
        this.start = start;
        this.hitAt = start + Math.max(1, windup);
    }

    public boolean done() { return done; }
    public List<Vec3> centers() { return Collections.unmodifiableList(centers); }
    public Vec3 origin() { return origin; }

    /** Entities hit (HIT or BLOCKED) by the resolution. */
    public List<LivingEntity> victims() {
        List<LivingEntity> out = new ArrayList<>();
        results.forEach((e, r) -> {
            if (r == Hits.Result.HIT || r == Hits.Result.BLOCKED) out.add(e);
        });
        return out;
    }

    public Map<LivingEntity, Hits.Result> results() { return Collections.unmodifiableMap(results); }

    private double bossHalf() {
        return fight.boss.getBbWidth() * 0.5;
    }

    // ------------------------------------------------------------------ geometry

    /** Whether an entity stands inside the marker. */
    public boolean contains(LivingEntity e) {
        Vec3 p = e.position();
        double half = e.getBbWidth() * 0.5;
        switch (move.shape) {
            case CIRCLE, RAIN -> {
                for (Vec3 c : centers) if (vertical(p, c) && flatDist(p, c) <= move.size + half) return true;
                return false;
            }
            case SELF -> {
                return vertical(p, origin) && flatDist(p, origin) <= move.size + bossHalf() + half;
            }
            case RING -> {
                double d = flatDist(p, origin);
                return vertical(p, origin) && d + half * 0.5 >= move.size2 + bossHalf() && d <= move.size + bossHalf() + half;
            }
            case CONE -> {
                if (!vertical(p, origin)) return false;
                Vec3 v = new Vec3(p.x - origin.x, 0, p.z - origin.z);
                double d = v.length();
                if (d > move.size + bossHalf() + half) return false;
                if (d < 1.0 + bossHalf()) return true;
                double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, v.normalize().dot(dir)))));
                return ang <= move.size2 / 2 + Math.toDegrees(Math.atan2(half, d));
            }
            case LINE -> {
                return vertical(p, origin) && inStrip(p, origin, dir, -half - bossHalf(), move.size + half, move.size2 + half);
            }
            case CROSS -> {
                Vec3 c = centers.get(0);
                Vec3 perp = new Vec3(-dir.z, 0, dir.x);
                double l = move.size / 2;
                return vertical(p, c) && (inStrip(p, c, dir, -l - half, l + half, move.size2 + half) || inStrip(p, c, perp, -l - half, l + half, move.size2 + half));
            }
        }
        return false;
    }

    private static boolean vertical(Vec3 p, Vec3 ground) {
        double dy = p.y - ground.y;
        return dy >= -BELOW && dy <= ABOVE;
    }

    private static double flatDist(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Point within a strip that starts at {@code from} and runs along {@code d} (flat). */
    private static boolean inStrip(Vec3 p, Vec3 from, Vec3 d, double t0, double t1, double halfWidth) {
        double vx = p.x - from.x, vz = p.z - from.z;
        double t = vx * d.x + vz * d.z;
        if (t < t0 || t > t1) return false;
        double px = vx - d.x * t, pz = vz - d.z * t;
        return px * px + pz * pz <= halfWidth * halfWidth;
    }

    AABB bounds() {
        double r = move.size + move.size2 + 3 + bossHalf();
        AABB box = new AABB(origin, origin).inflate(r, ABOVE + 2, r);
        for (Vec3 c : centers) box = box.minmax(new AABB(c, c).inflate(move.size + 3, ABOVE + 2, move.size + 3));
        return box;
    }

    /** End of a LINE (its far edge) on the marker's ground level. */
    public Vec3 lineEnd() {
        return origin.add(dir.scale(move.size));
    }

    // ------------------------------------------------------------------ markers

    void tickFx(long now) {
        if (done || (now - start) % 3 != 0) return;
        long left = hitAt - now;
        boolean hot = left <= 10;
        float progress = (float) Math.min(1, (now - start) / (double) Math.max(1, hitAt - start));
        String s = hot ? "_hot" : "";
        LivingEntity b = fight.boss;
        switch (move.shape) {
            case CIRCLE, RAIN -> {
                for (Vec3 c : centers) GLNetwork.fx(b, "boss_mark_circle" + s, c.x, c.y + 0.15, c.z, (float) move.size, progress, 0);
            }
            case SELF -> GLNetwork.fx(b, "boss_mark_circle" + s, origin.x, origin.y + 0.15, origin.z, (float) (move.size + bossHalf()), progress, 0);
            case RING -> GLNetwork.fx(b, "boss_mark_ring" + s, origin.x, origin.y + 0.15, origin.z, (float) (move.size + bossHalf()), (float) (move.size2 + bossHalf()), progress);
            case CONE -> GLNetwork.fx(b, "boss_mark_cone" + s, origin.x, origin.y + 0.15, origin.z,
                    (float) (dir.x * (move.size + bossHalf())), (float) (dir.z * (move.size + bossHalf())), (float) move.size2);
            case LINE -> {
                Vec3 end = lineEnd();
                GLNetwork.fx(b, "boss_mark_line" + s, origin.x, origin.y + 0.15, origin.z, (float) end.x, (float) end.z, (float) move.size2);
            }
            case CROSS -> {
                Vec3 c = centers.get(0), perp = new Vec3(-dir.z, 0, dir.x);
                double l = move.size / 2;
                Vec3 a0 = c.subtract(dir.scale(l)), a1 = c.add(dir.scale(l)), b0 = c.subtract(perp.scale(l)), b1 = c.add(perp.scale(l));
                GLNetwork.fx(b, "boss_mark_line" + s, a0.x, c.y + 0.15, a0.z, (float) a1.x, (float) a1.z, (float) move.size2);
                GLNetwork.fx(b, "boss_mark_line" + s, b0.x, c.y + 0.15, b0.z, (float) b1.x, (float) b1.z, (float) move.size2);
            }
        }
        // wind-up pull (gravity wells, whirlpools, black holes): drag foes towards the marker's centre
        if (move.pull > 0) {
            Vec3 c = centers.isEmpty() ? origin : centers.get(0);
            double reach = move.size * 1.8 + 2;
            for (LivingEntity e : fight.inBox(new AABB(c, c).inflate(reach, 4, reach)))
                if (fight.isFoe(e) && flatDist(e.position(), c) <= reach) PowerKit.pull(e, c, move.pull * 3, 0);
        }
    }

    // ------------------------------------------------------------------ resolution

    void resolve() {
        if (done) return;
        done = true;
        LivingEntity boss = fight.boss;
        ServerLevel level = fight.level;
        if (!boss.isAlive()) return;
        if (own) GLNetwork.anim(boss, move.hitAnim);
        if (move.dash) dashBoss();
        float dmg = fight.strike() * move.power * fight.damageScale();
        boolean imbued = com.grandline.haki.Haki.armamentLevel(boss) > 0;
        Hits.Info info = new Hits.Info(move.element, 0f, 40 + move.power * 25, move.stun, imbued, false, move.unblockable, "hit");
        List<LivingEntity> candidates = fight.inBox(bounds());
        for (LivingEntity e : candidates) {
            if (move.power <= 0) break; // the marker announces a reused power, which does its own hitting below
            if (!fight.isFoe(e) || !contains(e)) continue;
            // the marker showed where it lands; the blow comes from the marked area itself (see originFor)
            Hits.Result r = Hits.announced(() -> com.grandline.combat.Cover.from(originFor(e), () -> Hits.hit(boss, e, dmg, info)));
            results.put(e, r);
            if (r != Hits.Result.HIT && r != Hits.Result.BLOCKED) continue;
            Vec3 from = pushFrom(e);
            if (move.knockback > 0 || move.lift > 0) PowerKit.push(e, from, move.knockback * 0.45 * (r == Hits.Result.BLOCKED ? 0.4 : 1), move.lift);
            for (BossMove.EffectSpec fx : move.effects) e.addEffect(fx.make());
            if (move.ignite > 0) e.igniteForSeconds(move.ignite);
            if (move.onHit != null) move.onHit.apply(fight, this, e);
        }
        hitFx();
        if (!move.reuse.isEmpty()) BossManager.runAbility(fight, move.reuse, fight.boss.getTarget());
        if (move.after != null) move.after.apply(fight, this);
        fight.onResolved(this);
    }

    /**
     * Where a marked attack comes from, for line of effect: the nearest marked circle (a little above the ground) for
     * ground eruptions and falling strikes, the boss's body for sweeps, rings, cones and lines. A wall between that
     * point and the target shelters it, exactly as for a player's blows.
     */
    private Vec3 originFor(LivingEntity e) {
        return switch (move.shape) {
            case CIRCLE, RAIN, CROSS -> {
                Vec3 best = centers.get(0);
                for (Vec3 c : centers) if (flatDist(e.position(), c) < flatDist(e.position(), best)) best = c;
                yield best.add(0, 1.0, 0);
            }
            default -> origin.add(0, Math.max(1.0, fight.boss.getBbHeight() * 0.5), 0);
        };
    }

    /** Knockback direction: away from the marker's centre for ground circles, away from the boss otherwise. */
    private Vec3 pushFrom(LivingEntity e) {
        return switch (move.shape) {
            case CIRCLE, RAIN, CROSS -> {
                Vec3 best = centers.get(0);
                for (Vec3 c : centers) if (flatDist(e.position(), c) < flatDist(e.position(), best)) best = c;
                yield flatDist(e.position(), best) < 0.3 ? best.subtract(dir) : best;
            }
            default -> origin;
        };
    }

    private void dashBoss() {
        LivingEntity boss = fight.boss;
        Vec3 to = switch (move.shape) {
            case LINE -> lineEnd();
            case CIRCLE, CROSS -> centers.get(0).subtract(dir.scale(1.5));
            default -> null;
        };
        if (to == null) return;
        Vec3 start = boss.position().add(0, 1, 0);
        Vec3 d = to.subtract(boss.position());
        double dist = Math.sqrt(d.x * d.x + d.z * d.z);
        if (dist > 0.5) FruitKit.blink(boss, new Vec3(d.x, 0, d.z), dist);
        CombatState.of(boss).iframesUntil = Math.max(CombatState.of(boss).iframesUntil, fight.level.getGameTime() + 4);
        GLNetwork.fx(boss, "boss_dash_" + move.style, start.x, start.y, start.z, (float) boss.getX(), (float) boss.getY() + 1, (float) boss.getZ());
    }

    private void hitFx() {
        LivingEntity b = fight.boss;
        String st = move.style;
        switch (move.shape) {
            case CIRCLE, RAIN -> {
                for (Vec3 c : centers) {
                    GLNetwork.fx(b, "boss_hit_" + st, c.x, c.y + 0.2, c.z, (float) move.size, 0, 0);
                    if (!move.theme.isEmpty()) GLNetwork.fx(b, move.theme, c.x, c.y + 0.2, c.z, (float) move.size, move.themeB, 0);
                }
            }
            case SELF -> {
                GLNetwork.fx(b, "boss_hit_" + st, origin.x, origin.y + 0.2, origin.z, (float) (move.size + bossHalf()), 0, 0);
                if (!move.theme.isEmpty()) GLNetwork.fx(b, move.theme, origin.x, origin.y + 0.2, origin.z, (float) (move.size + bossHalf()), move.themeB, 0);
            }
            case RING -> {
                GLNetwork.fx(b, "boss_ring_" + st, origin.x, origin.y + 0.2, origin.z, (float) (move.size + bossHalf()), (float) (move.size2 + bossHalf()), 0);
                if (!move.theme.isEmpty()) GLNetwork.fx(b, move.theme, origin.x, origin.y + 0.2, origin.z, (float) (move.size + bossHalf()), move.themeB, 0);
            }
            case CONE -> {
                GLNetwork.fx(b, "boss_cone_" + st, origin.x, origin.y + 0.6, origin.z, (float) (dir.x * move.size), (float) (dir.z * move.size), (float) move.size2);
                if (!move.theme.isEmpty()) {
                    Vec3 mid = origin.add(dir.scale(move.size * 0.6));
                    GLNetwork.fx(b, move.theme, mid.x, mid.y + 0.2, mid.z, (float) (move.size * 0.5), move.themeB, 0);
                }
            }
            case LINE -> {
                Vec3 end = lineEnd();
                GLNetwork.fx(b, "boss_line_" + st, origin.x, origin.y + 0.8, origin.z, (float) end.x, (float) end.z, (float) move.size2);
                if (!move.theme.isEmpty()) GLNetwork.fx(b, move.theme, end.x, end.y + 0.4, end.z, (float) Math.max(1.5, move.size2 * 2), move.themeB, 0);
            }
            case CROSS -> {
                Vec3 c = centers.get(0), perp = new Vec3(-dir.z, 0, dir.x);
                double l = move.size / 2;
                Vec3 a0 = c.subtract(dir.scale(l)), a1 = c.add(dir.scale(l)), b0 = c.subtract(perp.scale(l)), b1 = c.add(perp.scale(l));
                GLNetwork.fx(b, "boss_line_" + st, a0.x, c.y + 0.8, a0.z, (float) a1.x, (float) a1.z, (float) move.size2);
                GLNetwork.fx(b, "boss_line_" + st, b0.x, c.y + 0.8, b0.z, (float) b1.x, (float) b1.z, (float) move.size2);
                if (!move.theme.isEmpty()) GLNetwork.fx(b, move.theme, c.x, c.y + 0.2, c.z, (float) move.size2 * 2, move.themeB, 0);
            }
        }
        Vec3 at = centers.isEmpty() ? origin : centers.get(0);
        fight.level.playSound(null, at.x, at.y, at.z, hitSound(), SoundSource.HOSTILE, 1.6f, 0.7f + fight.level.random.nextFloat() * 0.2f);
    }

    private SoundEvent hitSound() {
        return switch (move.element) {
            case FIRE, MAGMA -> SoundEvents.BLAZE_SHOOT;
            case LIGHTNING -> SoundEvents.LIGHTNING_BOLT_IMPACT;
            case LIGHT -> SoundEvents.BEACON_DEACTIVATE;
            case ICE -> SoundEvents.GLASS_BREAK;
            case WATER -> SoundEvents.GENERIC_SPLASH;
            case DARKNESS -> SoundEvents.WARDEN_SONIC_BOOM;
            case SLASH, PIERCE -> SoundEvents.PLAYER_ATTACK_SWEEP;
            case POISON, SMOKE -> SoundEvents.FIRECHARGE_USE;
            case SOUND -> SoundEvents.BELL_BLOCK;
            case WIND -> SoundEvents.BREEZE_WIND_CHARGE_BURST.value();
            default -> SoundEvents.GENERIC_EXPLODE.value();
        };
    }

    /** Wind-up sound when the marker appears. */
    void windupSound() {
        Vec3 at = fight.boss.position();
        SoundEvent s = own ? (move.windup >= 36 ? com.grandline.registry.ModSounds.BOSS_TELEGRAPH.get() : SoundEvents.EVOKER_PREPARE_ATTACK) : SoundEvents.BEACON_POWER_SELECT;
        fight.level.playSound(null, at.x, at.y, at.z, s, SoundSource.HOSTILE, own ? 1.4f : 0.7f, own ? 0.8f : 1.6f);
    }

    @Nullable
    Vec3 firstCenter() {
        return centers.isEmpty() ? null : centers.get(0);
    }
}
