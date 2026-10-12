package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.Zoan;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Ryu Ryu no Mi, Model: Pteranodon — Ancient Zoan. Great leathery wings: wing beats that carry the user into the sky,
 * diving talon strikes, wing-edge blades and gales that blow attacks back.
 */
public final class PteranodonFruit {
    public static void init(DevilFruit f) {
        Hits.Info wing = FruitKit.info(Element.SLASH, 0.9f);
        PowerKit.zoanTransform(f);

        // Wing Beat: one powerful beat lifts the transformed user high into the air.
        f.add(f.ability("wing_beat").cooldown(16).stamina(6).anim("cast_up").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 dir = c.look().normalize();
            FruitKit.impulse(u, new Vec3(dir.x * 1.1, 1.0 + Math.max(0, dir.y) * 0.5, dir.z * 1.1), true);
            u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
            for (LivingEntity e : PowerKit.foes(u, u.position(), 3)) Hits.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.WIND, 1.5f).withPoise(15));
            FruitKit.fx(u, "wing_gust", u.position(), 3, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.PHANTOM_FLAP, 1.2f, 0.6f);
            return true;
        }));

        // Talon Dive: fold the wings and drop onto the prey, talons first.
        f.add(f.ability("talon_dive").unlock(6).cooldown(70).stamina(18).anim("kick_high").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            LivingEntity t = PowerKit.target(c, 20);
            Vec3 start = u.position().add(0, 1, 0);
            Hits.Info talons = FruitKit.info(Element.SLASH, 1.5f).withStun(20).withPoise(60);
            if (t != null) {
                Vec3 dir = t.position().subtract(u.position());
                double dist = Math.max(0, dir.length() - 1.3);
                if (dist > 0.2) FruitKit.blink(u, dir.normalize(), dist);
                Hits.hit(u, t, FruitKit.dmg(c, 11), talons);
            } else {
                FruitKit.blink(u, c.look(), 12);
                for (LivingEntity e : Hits.cone(u, 3.5, 90)) Hits.hit(u, e, FruitKit.dmg(c, 11), talons);
            }
            FruitKit.fx(u, "dive_trail", start, (float) u.getX(), (float) u.getY() + 1, (float) u.getZ());
            return true;
        }));

        // Wing Blade: the wing edges cut the air into twin crescents.
        f.add(f.ability("wing_blade").unlock(15).cooldown(45).stamina(16).anim("wide_slash").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 dir = FruitKit.aimDir(c);
            for (int s = -1; s <= 1; s += 2) {
                AbilityProjectile p = AbilityProjectile.shoot(u, "wing_crescent", 0.9f, c.eye().add(dir.scale(0.8)), PowerKit.yaw(dir, s * 6), 2.4, FruitKit.dmg(c, 7), wing.withPoise(25));
                p.pierce = true;
                p.life = 28;
            }
            return true;
        }));

        // Gale Wings: a beat of both wings blasts everything in front away — projectiles included.
        f.add(f.ability("gale_wings").unlock(25).cooldown(120).stamina(22).anim("double_palm").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            for (LivingEntity e : Hits.cone(u, 9, 90)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 5), FruitKit.info(Element.WIND, 0f).withPoise(30))) PowerKit.push(e, u.position(), 1.8, 0.4);
            }
            Vec3 look = c.look();
            for (Projectile p : PowerKit.hostileProjectiles(u, u.getEyePosition().add(look.scale(3)), 5)) PowerKit.reflect(u, p);
            FruitKit.fx(u, "wing_gust", u.position().add(look.scale(3)), 5, (float) look.x, (float) look.z);
            PowerKit.sound(c, u.position(), SoundEvents.ENDER_DRAGON_FLAP, 1.2f, 1.2f);
            return true;
        }));

        // Awakening — Sky Tyrant: soar high above the battlefield, then crash down in a storm of wing blades.
        f.add(f.ability("sky_tyrant").unlock(50).cooldown(1000).stamina(75).awakened().anim("pounce").exec((u, c) -> {
            Zoan.setForm(u, "awakened");
            Vec3 at = FruitKit.aimPoint(c, 30);
            FruitKit.impulse(u, new Vec3(0, 1.6, 0), true);
            FruitKit.fx(u, "wing_gust", u.position(), 4, 0, 0);
            CombatState.of(u).schedule(c.level.getGameTime() + 14, () -> {
                if (!u.isAlive()) return;
                Vec3 from = u.position().add(0, 1, 0);
                PowerKit.place(u, at);
                FruitKit.fx(u, "dive_trail", from, (float) at.x, (float) at.y + 1, (float) at.z);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 6))
                    Hits.hit(u, e, FruitKit.dmg(c, 16), FruitKit.info(Element.SLASH, 3f).withPoise(100).asUnblockable());
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
                    AbilityProjectile p = AbilityProjectile.shoot(u, "wing_crescent", 0.8f, at.add(0, 1, 0).add(d), d, 2.0, FruitKit.dmg(c, 6), wing.withPoise(25));
                    p.pierce = true;
                    p.life = 18;
                }
                FruitKit.fx(u, "shockwave", at.add(0, 0.2, 0), 6, 0, 0);
            });
            return true;
        }));

        // Passive: wings — transformed, the user glides instead of falling (hold sneak to drop) and never takes fall
        // damage; attacks from the air land 20% harder. Ancient Zoan toughness heals slowly.
        f.tickHook = u -> {
            if (Zoan.form(u).isEmpty()) return;
            u.fallDistance = 0;
            if (!u.onGround() && !u.isShiftKeyDown() && u.tickCount % 5 == 0)
                u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, false, false));
            if (u.tickCount % 60 == 0 && u.getHealth() < u.getMaxHealth()) u.heal(1f);
        };
        f.outgoingHook = (u, v, amount, info) -> !Zoan.form(u).isEmpty() && !u.onGround() ? amount * 1.2f : amount;
    }

    private PteranodonFruit() {}
}
