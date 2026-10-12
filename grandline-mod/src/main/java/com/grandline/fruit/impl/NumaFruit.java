package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.net.GLNetwork;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Numa Numa no Mi — swamp Logia. A bottomless swamp body: it swallows bullets, arrows and blasts that come near,
 * drags enemies under, holds people inside and spits everything back out. Swamp beats sand.
 */
public final class NumaFruit {
    static final BlockState MUD = Blocks.MUD.defaultBlockState();
    static final int CAPACITY = 32;
    /** Things held in the swamp body (swallowed projectiles), spat back out by Swamp Spit. */
    static final Map<LivingEntity, Integer> STORED = Collections.synchronizedMap(new WeakHashMap<>());

    public static int stored(LivingEntity u) {
        return STORED.getOrDefault(u, 0);
    }

    /** Swallow every hostile projectile within {@code r}; returns how many. */
    public static int swallow(LivingEntity u, Vec3 center, double r) {
        int n = 0;
        for (Projectile p : PowerKit.hostileProjectiles(u, center, r)) {
            p.discard();
            n++;
        }
        if (n > 0) {
            STORED.merge(u, n, (a, b) -> Math.min(CAPACITY, a + b));
            GLNetwork.fx(u, "swamp_gulp", center.x, center.y, center.z, 0, 0, 0);
        }
        return n;
    }

    public static void init(DevilFruit f) {
        // Swamp Hand: a hand of mud rises under the target and drags it down.
        f.add(f.ability("swamp_hand").cooldown(28).stamina(11).anim("cast_ground").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 16);
            if (t == null) return false;
            if (PowerKit.hit(u, t, FruitKit.dmg(c, 6), FruitKit.info(Element.PHYSICAL, 0f).withPoise(25))) {
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 4));
                FruitKit.impulse(t, new Vec3(0, -0.4, 0), false);
            }
            FruitKit.fx(u, "mud_grab", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            PowerKit.sound(c, t.position(), SoundEvents.MUD_BREAK, 1.2f, 0.6f);
            return true;
        }));

        // Swamp Field: the ground at the target point turns to swamp; anyone on it sinks and struggles.
        f.add(f.ability("swamp_field").unlock(5).cooldown(150).stamina(24).anim("cast_ground").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 20);
            double r = 4 + c.mastery / 25.0;
            PowerKit.paintDisc(c.level, at, r, MUD, null);
            for (int i = 0; i < 12; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 5L, () -> {
                    FruitKit.fx(u, "swamp", at, (float) r, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                        if (Math.abs(e.getY() - at.y) > 2.5) continue;
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3));
                        FruitKit.impulse(e, new Vec3(0, -0.15, 0), false);
                        if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 1.5f), FruitKit.info(Element.PHYSICAL, 0f).withPoise(8));
                    }
                });
            }
            return true;
        }));

        // Swamp Swallow: the user swallows a target whole — held inside the swamp body, suffocating, then spat out.
        f.add(f.ability("swamp_swallow").unlock(15).cooldown(200).stamina(30).anim("grab").exec((u, c) -> {
            LivingEntity t = CanonKit.melee(c, 6);
            if (t == null) return false;
            CanonKit.disable(t, 50, 4);
            swallow(u, u.position().add(0, 1, 0), 4);
            for (int i = 0; i < 10; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 5L, () -> {
                    if (!u.isAlive() || !t.isAlive()) return;
                    PowerKit.place(t, u.position());
                    t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0));
                    Hits.hit(u, t, FruitKit.dmg(c, 1.5f), FruitKit.info(Element.PHYSICAL, 0f).withPoise(5).asUnblockable());
                    if (n == 0) FruitKit.fx(u, "mud_grab", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
                });
            }
            CombatState.of(u).schedule(c.level.getGameTime() + 52, () -> {
                if (!u.isAlive() || !t.isAlive()) return;
                Vec3 out = PowerKit.flatAim(c);
                PowerKit.place(t, u.position().add(out.scale(1.5)).add(0, 0.5, 0));
                Hits.hit(u, t, FruitKit.dmg(c, 5), FruitKit.info(Element.PHYSICAL, 2.5f).withPoise(40));
                FruitKit.impulse(t, out.scale(1.4).add(0, 0.5, 0), false);
                FruitKit.fx(u, "mud_burst", t.position().add(0, 1, 0), 1.2f, 0, 0);
            });
            PowerKit.sound(c, u.position(), SoundEvents.HONEY_DRINK, 1.4f, 0.4f);
            return true;
        }));

        // Swamp Spit: everything the swamp has swallowed comes flying back out as a volley of mud and debris.
        f.add(f.ability("swamp_spit").unlock(25).cooldown(120).stamina(22).anim("cast_forward").exec((u, c) -> {
            int held = stored(u);
            STORED.remove(u);
            int n = Math.min(20, 4 + held);
            Vec3 dir = FruitKit.aimDir(c);
            for (int i = 0; i < n; i++) {
                int k = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i, () -> {
                    if (!u.isAlive()) return;
                    double spread = (k % 5 - 2) * 4 + (u.getRandom().nextDouble() - 0.5) * 4;
                    Vec3 d = PowerKit.yaw(dir, spread).add(0, (u.getRandom().nextDouble() - 0.4) * 0.12, 0);
                    AbilityProjectile p = AbilityProjectile.shoot(u, "mud_ball", 0.5f, u.getEyePosition().add(d.scale(0.8)), d, 1.9,
                            FruitKit.dmg(c, held > k - 4 && k >= 4 ? 5 : 3.5f), FruitKit.info(Element.BLUNT, 0.6f).withPoise(14));
                    p.gravity = 0.02;
                    p.life = 40;
                    p.onHit = (pr, hit, pos) -> {
                        if (hit != null) hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                    };
                });
            }
            return true;
        }));

        // Awakening — Bottomless Swamp: the whole area becomes a sucking swamp that swallows everything, then erupts.
        f.add(f.ability("bottomless_swamp").unlock(50).cooldown(1000).stamina(80).awakened().anim("ground_slam").exec((u, c) -> {
            Vec3 center = u.position();
            double r = 11;
            PowerKit.paintDisc(c.level, center, 7, MUD, null);
            for (int i = 0; i < 16; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 5L, () -> {
                    FruitKit.fx(u, "swamp", center, (float) r, 1, 0);
                    swallow(u, center.add(0, 1, 0), r);
                    for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), r)) {
                        PowerKit.pull(e, center, 0.3, -0.12);
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 4));
                        if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 2), FruitKit.info(Element.PHYSICAL, 0f).withPoise(10));
                    }
                });
            }
            CombatState.of(u).schedule(c.level.getGameTime() + 84, () -> {
                for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), 5)) {
                    if (PowerKit.hit(u, e, FruitKit.dmg(c, 12), FruitKit.info(Element.BLUNT, 0.5f).withStun(30).asUnblockable()))
                        FruitKit.impulse(e, new Vec3(0, 1.3, 0), false);
                }
                FruitKit.fx(u, "mud_burst", center.add(0, 1, 0), 5, 0, 0);
            });
            return true;
        }));

        // Passive: the swamp body swallows bullets, arrows and thrown techniques that come within reach (kept for
        // Swamp Spit). Swamp beats sand (see FruitRules.counters).
        f.tickHook = u -> swallow(u, u.getBoundingBox().getCenter(), 1.8);
    }

    private NumaFruit() {}
}
