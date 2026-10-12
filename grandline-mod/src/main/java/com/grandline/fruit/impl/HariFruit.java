package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Hari Hari no Mi — ORIGINAL needle Paramecia. The user grows and fires needles: volleys, a skin that bristles,
 * pressure-point strikes that lock a body up, needle caltrops sown as a trap, and healing acupuncture for the crew.
 * Passive: needle-skin — anyone who strikes the user up close is pricked (much harder while bristling).
 */
public final class HariFruit {
    static final String BRISTLE = "hari_bristle";
    private static boolean pricking;

    private static final List<Holder<MobEffect>> KNOTS = List.of(MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS, MobEffects.DIG_SLOWDOWN,
            MobEffects.CONFUSION, MobEffects.POISON, MobEffects.HUNGER);

    public static void init(DevilFruit f) {
        Hits.Info needle = FruitKit.info(Element.PIERCE, 0.2f);

        // Needle volley: a fan of five needles.
        f.add(f.ability("needle_volley").cooldown(18).stamina(9).anim("throw").exec((u, c) -> {
            for (int i = -2; i <= 2; i++) {
                AbilityProjectile p = FruitKit.shoot(c, "hari_needle", 0.2f, 2.8, FruitKit.dmg(c, 2.5f), needle.withPoise(8));
                p.setDeltaMovement(PowerKit.yaw(p.getDeltaMovement(), i * 6));
                p.life = 30;
            }
            PowerKit.sound(c, u.position(), SoundEvents.ARROW_SHOOT, 1f, 1.8f);
            return true;
        }));

        // Pincushion: the body bristles with needles — attackers up close are skewered, and a burst pricks those nearby.
        f.add(f.ability("pincushion").unlock(5).cooldown(260).stamina(16).anim("harden").exec((u, c) -> {
            CombatState.of(u).buffs.put(BRISTLE, 160 + c.mastery);
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 3)) Hits.hit(u, e, FruitKit.dmg(c, 4), needle.withPoise(15));
            FruitKit.fx(u, "hari_bristle", u.position().add(0, 1, 0), u.getBbWidth() + 0.6f, u.getBbHeight(), 0);
            PowerKit.sound(c, u.position(), SoundEvents.PUFFER_FISH_BLOW_UP, 1f, 0.8f);
            return true;
        }));

        // Pressure point: a needle into a nerve cluster — the target locks up, unable to move or guard.
        f.add(f.ability("pressure_point").unlock(12).cooldown(150).stamina(20).anim("finger_pistol").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 12);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 4), needle.withStun(40 + c.mastery / 4).withPoise(60).asUnblockable());
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
            CombatState.of(t).blocking = false;
            FruitKit.fx(u, "hari_pin", t.getEyePosition(), 0, 0, 0);
            PowerKit.sound(c, t.position(), SoundEvents.TRIDENT_HIT, 1f, 1.8f);
            return true;
        }));

        // Caltrops: needles sown over the ground — a trap that pricks, slows and bleeds anyone who walks through.
        f.add(f.ability("caltrops").unlock(20).cooldown(220).stamina(22).anim("throw").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 14);
            double r = 4;
            OriginalKit.pulses(c, 2, 10, 20 + c.mastery / 10, n -> {
                if (n % 3 == 0) FruitKit.fx(u, "hari_caltrops", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 0.5, 0), r)) {
                    if (Math.abs(e.getY() - at.y) > 1.5) continue;
                    Hits.hit(u, e, FruitKit.dmg(c, 2), needle.withPoise(6));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2));
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
                }
            });
            PowerKit.sound(c, at, SoundEvents.CHAIN_PLACE, 1f, 1.6f);
            return true;
        }));

        // Acupuncture: needles in the right places — the user and nearby allies heal, shake off knots and fight harder.
        f.add(f.ability("acupuncture").unlock(30).cooldown(500).stamina(24).anim("focus").exec((u, c) -> {
            for (LivingEntity a : OriginalKit.allies(u, u.position().add(0, 1, 0), 6, true)) {
                OriginalKit.heal(a, 6 + c.mastery / 10f, "hari_acupuncture");
                for (Holder<MobEffect> h : KNOTS) a.removeEffect(h);
                a.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                a.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
            }
            // a few stray needles find the enemies close by
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 4)) Hits.hit(u, e, FruitKit.dmg(c, 3), needle.withPoise(10));
            PowerKit.sound(c, u.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            return true;
        }));

        // Awakening — Thousand Needle Rain: a storm of needles rains from the sky over an area.
        f.add(f.ability("needle_rain").unlock(50).cooldown(900).stamina(70).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 30);
            double r = 8;
            OriginalKit.pulses(c, 4, 4, 15, n -> {
                FruitKit.fx(u, "hari_rain", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 2.5f), needle.withPoise(8));
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1));
                }
                for (int k = 0; k < 3; k++) {
                    double a = u.getRandom().nextDouble() * Math.PI * 2, d = u.getRandom().nextDouble() * r;
                    Vec3 to = at.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                    AbilityProjectile p = AbilityProjectile.shoot(u, "hari_needle", 0.2f, to.add(0, 14, 0), new Vec3(0, -1, 0), 2.5, FruitKit.dmg(c, 2), needle);
                    p.life = 20;
                }
            });
            return true;
        }));

        // Passive: needle skin. Close attackers are pricked (skewered while bristling) and blows are blunted a little.
        f.incomingHook = (u, a, amount, info) -> {
            boolean bristling = CombatState.of(u).hasBuff(BRISTLE);
            if (a != null && !pricking && a.distanceTo(u) < 3.5 && info.element().physical() && Hits.hostileTo(u, a)) {
                pricking = true;
                try {
                    Hits.hit(u, a, bristling ? 4f : 1f, FruitKit.info(Element.PIERCE, 0f).withPoise(bristling ? 20 : 5));
                } finally {
                    pricking = false;
                }
            }
            return bristling ? amount * 0.8f : amount;
        };
    }

    private HariFruit() {}
}
