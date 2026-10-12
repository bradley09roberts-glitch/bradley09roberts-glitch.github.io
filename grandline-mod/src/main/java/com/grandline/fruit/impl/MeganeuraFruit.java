package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.Zoan;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Tonbo Tonbo no Mi, Model: Meganeura — ORIGINAL Ancient Zoan (wave 4). The user becomes the griffinfly, the giant
 * dragonfly of the ancient skies and the first great hunter of the air — at home over the White Sea. Buzzing wings
 * that cut, a dart in any direction, a hover, a snatch that carries prey up and drops it, compound eyes that see
 * everything at once, and at awakening a swarm of phantom griffinflies.
 * Passive: in form no fall damage and a quick flier (faster still in the White Sea); the compound eyes sidestep one
 * blow in ten.
 * Weakness: standard Zoan; thin wings burn (fire +25%).
 */
public final class MeganeuraFruit {
    static final String EYES = "tonbo_eyes";

    public static void init(DevilFruit f) {
        Hits.Info wing = FruitKit.info(Element.SLASH, 0.5f);
        PowerKit.zoanTransform(f);

        // Wing slash: the four great wings beat into a cutting buzz in front.
        f.add(f.ability("wing_slash").cooldown(18).stamina(9).anim("wide_slash").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            for (LivingEntity e : Hits.cone(u, 4 + u.getScale(), 120)) if (Hits.hostileTo(u, e)) Hits.hit(u, e, FruitKit.dmg(c, 6), wing.withPoise(25));
            FruitKit.fx(u, "tonbo_wings", c.eye(), (float) c.look().x, (float) c.look().y, (float) c.look().z);
            PowerKit.sound(c, u.position(), SoundEvents.PHANTOM_FLAP, 1.2f, 1.6f);
            return true;
        }));

        // Dart: an instant dart in any direction the user looks (up and down too), slicing through what is in the way.
        f.add(f.ability("dart").unlock(5).cooldown(50).stamina(12).anim("dash").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 start = u.position();
            Vec3 dir = c.look();
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 6;
            Vec3 end = FruitKit.blink(u, dir, 9);
            for (LivingEntity e : Hits.line(u, start.add(0, 1, 0), end.subtract(start), start.distanceTo(end) + 1.5, 1.2))
                if (Hits.hostileTo(u, e)) Hits.hit(u, e, FruitKit.dmg(c, 5), wing.withPoise(25));
            if (dir.y > 0.3) u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
            FruitKit.fx(u, "tonbo_dart", start.add(0, 1, 0), (float) end.x, (float) end.y + 1, (float) end.z);
            PowerKit.sound(c, end, SoundEvents.PHANTOM_FLAP, 1.5f, 2f);
            return true;
        }));

        // Hover: the wings hold the user aloft for six seconds — a short rise, then a slow drift.
        f.add(f.ability("hover").unlock(12).cooldown(240).stamina(16).anim("cast_up").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            int dur = 120 + c.mastery;
            u.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 16, 1, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, dur, 0, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 0, false, false));
            // the downdraught of the wings buffets whoever stands beneath
            for (LivingEntity e : PowerKit.foes(u, u.position(), 3)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.WIND, 0.8f).withPoise(15))) PowerKit.push(e, u.position(), 0.7, 0);
            }
            FruitKit.fx(u, "tonbo_hover", u.position(), 3, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.BEE_LOOP, 1.5f, 0.6f);
            return true;
        }));

        // Snatch: the griffinfly seizes its prey and carries it up into the sky, then lets go.
        f.add(f.ability("snatch").unlock(20).cooldown(200).stamina(22).anim("grab").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            LivingEntity t = PowerKit.target(c, 5 + u.getScale());
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 6), wing.withStun(20).withPoise(40));
            FruitKit.impulse(t, new Vec3(0, 1.25, 0), true);
            FruitKit.impulse(u, new Vec3(0, 1.1, 0), true);
            u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
            FruitKit.fx(u, "tonbo_snatch", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            CombatState.of(u).schedule(c.level.getGameTime() + 14, () -> {
                if (!t.isAlive()) return;
                Hits.hit(u, t, FruitKit.dmg(c, 5), wing.withPoise(30));
                FruitKit.impulse(t, new Vec3(0, -1.2, 0), true);
                FruitKit.fx(u, "tonbo_snatch", t.position(), t.getBbWidth(), t.getBbHeight(), 1);
            });
            PowerKit.sound(c, t.position(), SoundEvents.PHANTOM_BITE, 1.2f, 1.4f);
            return true;
        }));

        // Compound eyes: for fifteen seconds the user sees in every direction at once — every hidden enemy around is
        // revealed (glowing), and one blow in four is seen coming and sidestepped.
        f.add(f.ability("compound_eyes").unlock(30).cooldown(500).stamina(18).anim("focus").exec((u, c) -> {
            int dur = 300 + c.mastery * 2;
            CombatState.of(u).buffs.put(EYES, dur);
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 20)) {
                e.removeEffect(MobEffects.INVISIBILITY);
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, dur, 0));
            }
            // the sudden flicker of the wings dazes whoever is right in front
            for (LivingEntity e : Hits.cone(u, 3.5, 90)) if (Hits.hostileTo(u, e)) Hits.hit(u, e, FruitKit.dmg(c, 3), wing.withStun(10).withPoise(20));
            FruitKit.fx(u, "tonbo_eyes", u.getEyePosition(), 20, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.8f);
            return true;
        }));

        // Awakening — Carboniferous Swarm: a swarm of phantom griffinflies pours out of the sky; for five seconds they
        // dive again and again on every enemy around, cutting and lifting them.
        f.add(f.ability("carboniferous_swarm").unlock(55).cooldown(1000).stamina(80).awakened().anim("roar").exec((u, c) -> {
            Vec3 at = u.position();
            double r = 12;
            OriginalKit.pulses(c, 0, 10, 10, n -> {
                FruitKit.fx(u, "tonbo_swarm", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    if (PowerKit.hit(u, e, FruitKit.dmg(c, 4), wing.withPoise(15))) {
                        e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
                        if (n % 3 == 0) FruitKit.impulse(e, new Vec3(0, 0.45, 0), false);
                    }
                }
            });
            PowerKit.sound(c, at, SoundEvents.BEE_LOOP_AGGRESSIVE, 2f, 0.6f);
            return true;
        }));

        // Passive: compound eyes (one blow in ten sidestepped, one in four with Compound Eyes up), no fall damage in form,
        // a quick flier (faster in the White Sea); thin wings burn (fire +25%).
        f.incomingHook = (u, a, amount, info) -> {
            float dodge = CombatState.of(u).hasBuff(EYES) ? 0.25f : !Zoan.form(u).isEmpty() ? 0.1f : 0f;
            if (dodge > 0 && a != null && u.getRandom().nextFloat() < dodge) {
                com.grandline.net.GLNetwork.fx(u, "dodge", u.getX(), u.getY() + 1, u.getZ(), 0, 0, 0);
                return 0f;
            }
            return info.element() == Element.FIRE || info.element() == Element.MAGMA ? amount * 1.25f : amount;
        };
        f.tickHook = u -> {
            if (Zoan.form(u).isEmpty()) return;
            u.fallDistance = 0;
            if (u.tickCount % 40 == 0)
                u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, u.level().dimension() == com.grandline.world.SkyWorld.LEVEL ? 1 : 0, false, false));
        };
    }

    private MeganeuraFruit() {}
}
