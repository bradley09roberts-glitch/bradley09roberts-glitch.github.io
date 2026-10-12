package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Hanabi Hanabi no Mi — ORIGINAL firework Paramecia (wave 4). The user's body is packed like a festival night in the
 * Land of Wano: rockets launched from the fingertips, sparkler blades, a rocket to ride, a "star mine" of shells that
 * burst one after another, the great chrysanthemum shell, and at awakening a grand finale that lights up the sky.
 * Passive: a firework body cannot be burnt (immune to fire).
 * Weakness: damp powder — water hits deal 30% more, and in rain or water the user's fireworks lose a third of their
 * force.
 */
public final class HanabiFruit {

    static boolean damp(LivingEntity u) {
        return u.isInWaterOrRain();
    }

    /** One shell bursting at a point: damage, a spark of fire and a dazzle. */
    static void burst(LivingEntity u, Vec3 at, double r, float dmg, Hits.Info info, int colour) {
        for (LivingEntity e : PowerKit.foes(u, at, r)) {
            if (PowerKit.hit(u, e, dmg, info)) {
                e.igniteForSeconds(2);
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0));
            }
        }
        FruitKit.fx(u, "hanabi_burst", at, (float) r, colour, 0);
    }

    public static void init(DevilFruit f) {
        Hits.Info fire = FruitKit.info(Element.FIRE, 0.8f);

        // Rocket: a bottle rocket whistles off the fingertip and bursts on impact.
        f.add(f.ability("rocket").cooldown(18).stamina(8).anim("finger_pistol").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "hanabi_rocket", 0.45f, 2.2, FruitKit.dmg(c, 6), fire.withPoise(20));
            p.life = 36;
            p.onHit = (pr, hit, pos) -> burst(u, pos, 2.2, FruitKit.dmg(c, 3), fire.withPoise(10), u.getRandom().nextInt(6));
            PowerKit.sound(c, u.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.2f, 1.2f);
            return true;
        }));

        // Sparkler: both hands become fizzing sparklers — three quick slashes of sparks in front.
        f.add(f.ability("sparkler").unlock(5).cooldown(40).stamina(12).anim("claw_flurry").exec((u, c) -> {
            OriginalKit.pulses(c, 0, 4, 3, n -> {
                for (LivingEntity e : Hits.cone(u, 4 + u.getScale(), 100))
                    if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 3), fire.withPoise(12))) e.igniteForSeconds(2);
                FruitKit.fx(u, "hanabi_sparkler", u.getEyePosition(), (float) u.getLookAngle().x, (float) u.getLookAngle().y, (float) u.getLookAngle().z);
            });
            PowerKit.sound(c, u.position(), SoundEvents.FIREWORK_ROCKET_TWINKLE, 1f, 1.6f);
            return true;
        }));

        // Rocket ride: the user rides a rocket forward and up, then comes down in a shower of sparks.
        f.add(f.ability("rocket_ride").unlock(12).cooldown(120).stamina(18).anim("rocket").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            FruitKit.impulse(u, new Vec3(dir.x * 1.6, 1.0 + Math.max(0, c.look().y), dir.z * 1.6), true);
            FruitKit.fx(u, "hanabi_trail", u.position(), (float) dir.x, 1, (float) dir.z);
            PowerKit.sound(c, u.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.5f, 0.8f);
            // the launch blast scorches whoever stood next to the user
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 0.5, 0), 2.5)) Hits.hit(u, e, FruitKit.dmg(c, 3), fire.withPoise(15));
            OriginalKit.onLanding(c, 50, () -> {
                burst(u, u.position().add(0, 0.5, 0), 3.5, FruitKit.dmg(c, 7), fire.withPoise(35), 3);
                PowerKit.sound(c, u.position(), SoundEvents.FIREWORK_ROCKET_BLAST, 1.5f, 1f);
            });
            return true;
        }));

        // Star mine: five shells are planted around a point and burst one after another.
        f.add(f.ability("star_mine").unlock(20).cooldown(220).stamina(24).anim("cast_ground").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 18);
            OriginalKit.pulses(c, 4, 6, 5, n -> {
                double a = n * Math.PI * 2 / 5;
                Vec3 p = n == 4 ? at.add(0, 1, 0) : at.add(Math.cos(a) * 1.8, 1, Math.sin(a) * 1.8);
                burst(u, p, 2.6, FruitKit.dmg(c, 5), fire.withPoise(20), n);
                PowerKit.sound(c, p, SoundEvents.FIREWORK_ROCKET_BLAST, 1.5f, 1.2f + n * 0.1f);
            });
            return true;
        }));

        // Chrysanthemum: the classic great shell — it rises over the target and opens into a falling chrysanthemum of
        // fire that burns and dazzles a wide circle.
        f.add(f.ability("chrysanthemum").unlock(30).cooldown(300).stamina(30).anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 22);
            FruitKit.fx(u, "hanabi_trail", at, 0, 1, 0);
            CombatState.of(u).schedule(c.level.getGameTime() + 12, () -> {
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 7)) {
                    if (PowerKit.hit(u, e, FruitKit.dmg(c, 11), fire.withStun(15).withPoise(50))) {
                        e.igniteForSeconds(4);
                        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                    }
                }
                FruitKit.fx(u, "hanabi_kiku", at, 7, 0, 0);
                PowerKit.sound(c, at, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2.5f, 0.8f);
            });
            PowerKit.sound(c, u.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 2f, 0.6f);
            return true;
        }));

        // Awakening — Grand Finale: for four seconds the sky above the user erupts — shell after shell bursts over the
        // enemies around, ending in one enormous chrysanthemum.
        f.add(f.ability("grand_finale").unlock(55).cooldown(1100).stamina(85).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 at = u.position();
            double r = 14;
            OriginalKit.pulses(c, 0, 5, 16, n -> {
                List<LivingEntity> foes = PowerKit.foes(u, at.add(0, 1, 0), r);
                if (!foes.isEmpty()) {
                    LivingEntity t = foes.get(u.getRandom().nextInt(foes.size()));
                    burst(u, t.position().add(0, 1, 0), 2.8, FruitKit.dmg(c, 5), fire.withPoise(15), n % 6);
                } else {
                    FruitKit.fx(u, "hanabi_burst", at.add((u.getRandom().nextDouble() - 0.5) * r, 8, (u.getRandom().nextDouble() - 0.5) * r), 2.5f, n % 6, 0);
                }
                if (n % 3 == 0) PowerKit.sound(c, at, SoundEvents.FIREWORK_ROCKET_BLAST, 2f, 0.8f + n * 0.04f);
            });
            CombatState.of(u).schedule(c.level.getGameTime() + 82, () -> {
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r))
                    if (PowerKit.hit(u, e, FruitKit.dmg(c, 14), fire.withStun(30).withPoise(80).asUnblockable())) e.igniteForSeconds(5);
                FruitKit.fx(u, "hanabi_kiku", at, (float) r, 1, 0);
                PowerKit.sound(c, at, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 3f, 0.5f);
            });
            return true;
        }));

        // Passive: immune to fire (own element). Damp powder: water hits +30%, the user's fire -33% when wet.
        f.incomingHook = (u, a, amount, info) -> info.element() == Element.WATER ? amount * 1.3f : amount;
        f.outgoingHook = (u, v, amount, info) -> damp(u) && info.element() == Element.FIRE ? amount * 0.67f : amount;
        f.tickHook = u -> {
            if (u.tickCount % 20 == 0 && u.isOnFire()) u.clearFire();
        };
    }

    private HanabiFruit() {}
}
