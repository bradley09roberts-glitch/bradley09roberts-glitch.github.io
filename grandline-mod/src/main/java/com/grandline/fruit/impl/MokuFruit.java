package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Moku Moku no Mi — smoke Logia. A capture specialist: smoke fists, binding smoke snakes, blinding screens, flight on a
 * plume of smoke and a cage of smoke nobody walks out of.
 */
public final class MokuFruit {
    public static void init(DevilFruit f) {
        Hits.Info smoke = FruitKit.info(Element.SMOKE, 1.6f);

        // White Blow: a fist of dense smoke.
        f.add(f.ability("white_blow").cooldown(22).stamina(10).anim("punch_extend").exec((u, c) -> {
            FruitKit.shoot(c, "smoke_fist", 1.0f, 1.8, FruitKit.dmg(c, 6), smoke.withPoise(30));
            return true;
        }));

        // White Snake: a coil of smoke that binds the first enemy it touches and drags them in.
        f.add(f.ability("white_snake").unlock(5).cooldown(80).stamina(18).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "white_snake", 0.7f, 2.0, FruitKit.dmg(c, 3), FruitKit.info(Element.SMOKE, 0f).withStun(50).withPoise(40));
            p.life = 30;
            p.onHit = (pr, hit, pos) -> {
                if (hit == null) return;
                hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3));
                PowerKit.pull(hit, u.position().add(u.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5)), 1.2, 0.15);
                FruitKit.fx(u, "smoke_bind", hit.position().add(0, 1, 0), hit.getBbWidth(), hit.getBbHeight(), 0);
            };
            return true;
        }));

        // White Launcher: rocket forward on a jet of smoke, bowling through anyone in the way.
        f.add(f.ability("white_launcher").unlock(10).cooldown(60).stamina(16).anim("rocket").exec((u, c) -> {
            Vec3 dir = c.look().normalize();
            FruitKit.impulse(u, new Vec3(dir.x * 2.2, Math.max(0.5, dir.y * 2.2), dir.z * 2.2), true);
            u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
            Set<Integer> struck = new HashSet<>();
            Vec3 start = u.position();
            for (int i = 1; i <= 10; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i, () -> {
                    if (!u.isAlive()) return;
                    for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 1.9))
                        if (struck.add(e.getId())) Hits.hit(u, e, FruitKit.dmg(c, 6), smoke.withPoise(35));
                });
            }
            FruitKit.fx(u, "smoke_trail", start.add(0, 1, 0), (float) (start.x + dir.x * 8), (float) (start.y + 1 + dir.y * 8), (float) (start.z + dir.z * 8));
            return true;
        }));

        // White Out: a thick smoke screen — enemies inside are blinded and lose track of the user.
        f.add(f.ability("white_out").unlock(20).cooldown(240).stamina(30).anim("cast_ground").exec((u, c) -> {
            Vec3 center = u.position();
            double r = 7 + c.mastery / 20.0;
            u.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
            for (int i = 0; i < 6; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i * 15L, () -> {
                    FruitKit.fx(u, "smoke_screen", center, (float) r, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), r)) {
                        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                        PowerKit.loseTrack(u, e);
                        Hits.hit(u, e, FruitKit.dmg(c, 1), FruitKit.info(Element.SMOKE, 0f).withPoise(5));
                    }
                });
            }
            return true;
        }));

        // White Vine: smoke tendrils seize everyone in front and reel them in.
        f.add(f.ability("white_vine").unlock(35).cooldown(220).stamina(36).anim("cast_forward").exec((u, c) -> {
            Vec3 front = u.position().add(PowerKit.flatAim(c).scale(1.6));
            int n = 0;
            for (LivingEntity e : Hits.cone(u, 11, 110)) {
                if (!Hits.hostileTo(u, e)) continue;
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 5), FruitKit.info(Element.SMOKE, 0f).withStun(40).withPoise(40))) {
                    PowerKit.pull(e, front, 1.4, 0.2);
                    FruitKit.fx(u, "smoke_bind", e.position().add(0, 1, 0), e.getBbWidth(), e.getBbHeight(), 0);
                    n++;
                }
            }
            FruitKit.fx(u, "smoke_screen", u.position().add(c.look().scale(5)), 4, 0, 0);
            return true;
        }));

        // Awakening — White Cage: a pillar of smoke that holds everyone inside and crushes them at the end.
        f.add(f.ability("white_cage").unlock(55).cooldown(900).stamina(80).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 30);
            double r = 8;
            for (int i = 0; i < 20; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 5L, () -> {
                    FruitKit.fx(u, "smoke_cage", at, (float) r, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, at, r + 4)) {
                        PowerKit.pull(e, at, 0.35, 0.04);
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 4));
                        if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 2), FruitKit.info(Element.SMOKE, 0f).withPoise(10));
                    }
                });
            }
            CombatState.of(u).schedule(c.level.getGameTime() + 102, () -> {
                for (LivingEntity e : PowerKit.foes(u, at, r)) Hits.hit(u, e, FruitKit.dmg(c, 12), FruitKit.info(Element.SMOKE, 1.0f).withStun(40).asUnblockable());
                FruitKit.fx(u, "smoke_screen", at, (float) r, 0, 0);
            });
            return true;
        }));

        // Passive: a smoke body drifts down instead of falling.
        f.tickHook = u -> u.fallDistance = 0;
    }

    private MokuFruit() {}
}
