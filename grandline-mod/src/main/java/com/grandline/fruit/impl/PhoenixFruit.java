package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.Zoan;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Tori Tori no Mi, Model: Phoenix — Mythical Zoan. Blue flames of rebirth heal the user while transformed; wings carry
 * them through the sky; talons and flaming wings strike from above.
 */
public final class PhoenixFruit {
    private static final List<Holder<MobEffect>> AILMENTS = List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.WEAKNESS, MobEffects.BLINDNESS, MobEffects.HUNGER, MobEffects.CONFUSION, MobEffects.DIG_SLOWDOWN, MobEffects.DARKNESS);

    public static void init(DevilFruit f) {
        Hits.Info flame = FruitKit.info(Element.FIRE, 1.4f);
        PowerKit.zoanTransform(f);

        // Wing beat: a powerful flap that carries the transformed phoenix up and forward.
        f.add(f.ability("phoenix_flight").cooldown(16).stamina(6).anim("cast_up").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 dir = c.look().normalize();
            FruitKit.impulse(u, new Vec3(dir.x * 1.3, 0.9 + Math.max(0, dir.y) * 0.4, dir.z * 1.3), true);
            u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
            FruitKit.fx(u, "phoenix_wings", u.position().add(0, 1.2, 0), 2.5f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.PHANTOM_FLAP, 1f, 0.8f);
            return true;
        }));

        // Flame wings: a sweep of blue-flame wings that also throws a crescent of fire.
        f.add(f.ability("flame_wings").unlock(5).cooldown(40).stamina(14).anim("wide_slash").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            FruitKit.cone(c, 6, 140, FruitKit.dmg(c, 7), flame.withPoise(30));
            AbilityProjectile p = FruitKit.shoot(c, "phoenix_flame", 1.0f, 2.0, FruitKit.dmg(c, 5), flame.withPoise(20));
            p.pierce = true;
            p.life = 26;
            FruitKit.fx(u, "phoenix_wings", u.position().add(0, 1.2, 0), 3.5f, 0, 0);
            return true;
        }));

        // Phoenix Brand: a diving kick wreathed in blue flame.
        f.add(f.ability("phoenix_brand").unlock(15).cooldown(90).stamina(22).anim("kick_high").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Hits.Info kick = FruitKit.info(Element.FIRE, 3.5f).withPoise(80);
            Vec3 start = u.position().add(0, 1, 0);
            LivingEntity t = PowerKit.target(c, 18);
            if (t != null) {
                Vec3 dir = t.position().subtract(u.position());
                double dist = Math.max(0, dir.length() - 1.4);
                if (dist > 0.2) FruitKit.blink(u, dir.normalize(), dist);
                Hits.hit(u, t, FruitKit.dmg(c, 13), kick);
            } else {
                FruitKit.blink(u, c.look(), 12);
                for (LivingEntity e : Hits.cone(u, 3.5, 90)) Hits.hit(u, e, FruitKit.dmg(c, 13), kick);
            }
            FruitKit.fx(u, "blue_flame_trail", start, (float) u.getX(), (float) u.getY() + 1, (float) u.getZ());
            FruitKit.fx(u, "blue_flame", u.position().add(0, 1, 0), 1.5f, 0, 0);
            return true;
        }));

        // Blue flames of rebirth: heal the user and nearby allies, burning away ailments.
        f.add(f.ability("rebirth_flames").unlock(25).cooldown(400).stamina(30).anim("focus").exec((u, c) -> {
            heal(u, 8 + c.mastery / 10f);
            for (LivingEntity e : Hits.sphere(u, u.position().add(0, 1, 0), 8)) {
                if (Hits.hostileTo(u, e)) continue;
                heal(e, 5 + c.mastery / 20f);
            }
            FruitKit.fx(u, "blue_flame", u.position().add(0, 1, 0), 8, 1, 0);
            PowerKit.sound(c, u.position(), SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.2f);
            return true;
        }));

        // Awakening — Phoenix Pyreapple: soar up, then crash down on a point in a blue inferno.
        f.add(f.ability("phoenix_pyreapple").unlock(45).cooldown(700).stamina(70).awakened().anim("pounce").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 at = FruitKit.aimPoint(c, 30);
            FruitKit.impulse(u, new Vec3(0, 1.6, 0), true);
            FruitKit.fx(u, "phoenix_wings", u.position().add(0, 1.2, 0), 4, 0, 0);
            CombatState.of(u).schedule(c.level.getGameTime() + 14, () -> {
                if (!u.isAlive()) return;
                Vec3 from = u.position().add(0, 1, 0);
                PowerKit.place(u, at);
                FruitKit.fx(u, "blue_flame_trail", from, (float) at.x, (float) at.y + 1, (float) at.z);
                FruitKit.fx(u, "blue_flame", at.add(0, 1, 0), 6, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 6))
                    if (PowerKit.hit(u, e, FruitKit.dmg(c, 16), FruitKit.info(Element.FIRE, 3f).withPoise(100).asUnblockable())) e.igniteForSeconds(4);
                PowerKit.sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.3f);
            });
            return true;
        }));

        // Passive: regeneration flames. Transformed, the phoenix glides and heals quickly; even in human form it slowly mends.
        f.tickHook = u -> {
            String form = Zoan.form(u);
            if (form.isEmpty()) {
                if (u.tickCount % 40 == 0 && u.getHealth() < u.getMaxHealth()) u.heal(0.5f);
                return;
            }
            if (u.tickCount % 20 == 0) {
                if (u.getHealth() < u.getMaxHealth()) {
                    u.heal(form.equals("awakened") ? 2f : 1f);
                    com.grandline.net.GLNetwork.fx(u, "regen_flame", u.getX(), u.getY() + 1, u.getZ(), 0, 0, 0);
                }
                u.clearFire();
            }
            if (!u.onGround() && !u.isShiftKeyDown() && u.tickCount % 5 == 0)
                u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, false, false));
            u.fallDistance = 0;
        };
    }

    private static void heal(LivingEntity e, float amount) {
        e.heal(amount);
        e.clearFire();
        for (Holder<MobEffect> h : AILMENTS) e.removeEffect(h);
        com.grandline.net.GLNetwork.fx(e, "regen_flame", e.getX(), e.getY() + 1, e.getZ(), 0, 0, 0);
    }

    private PhoenixFruit() {}
}
