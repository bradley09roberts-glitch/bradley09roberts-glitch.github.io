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
import net.minecraft.world.phys.Vec3;

/**
 * Kaeru Kaeru no Mi, Model: Poison Dart Frog — ORIGINAL Zoan. A small, brilliantly coloured frog whose skin is one of
 * the deadliest poisons there is: a sticky tongue that reels foes in, enormous leaps that land in a splash of venom,
 * a skin that turns lethal on demand and arcing venom spit. At awakening it calls down a poison
 * monsoon — and frogs love rain.
 * Passive: toxic skin — in form, anyone who strikes the frog up close is poisoned; poison cannot hurt it.
 * Weakness: dry heat — fire deals 25% more.
 */
public final class DartFrogFruit {
    static final String TOXIN = "kaeru_toxin";

    static void envenom(LivingEntity e, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, amp));
        if (amp >= 1) e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0));
    }

    public static void init(DevilFruit f) {
        Hits.Info venom = FruitKit.info(Element.POISON, 0.3f);
        PowerKit.zoanTransform(f);

        // Tongue lash: a sticky tongue snaps out, slaps the target and reels it in.
        f.add(f.ability("tongue_lash").cooldown(30).stamina(10).anim("punch_extend").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            LivingEntity t = PowerKit.target(c, 12 + u.getScale());
            Vec3 from = c.eye().add(0, -0.3, 0);
            if (t == null) {
                Vec3 end = from.add(c.look().scale(PowerKit.rayLength(u, from, c.look(), 10)));
                FruitKit.fx(u, "kaeru_tongue", from, (float) end.x, (float) end.y, (float) end.z);
                return true;
            }
            Vec3 was = t.getBoundingBox().getCenter();
            Hits.hit(u, t, FruitKit.dmg(c, 5), venom.withStun(10).withPoise(25));
            PowerKit.pull(t, u.position(), 1.4, 0.2);
            envenom(t, 60, 0);
            FruitKit.fx(u, "kaeru_tongue", from, (float) was.x, (float) was.y, (float) was.z);
            PowerKit.sound(c, from, SoundEvents.FROG_TONGUE, 1.5f, 1f);
            return true;
        }));

        // Great leap: a colossal frog jump that lands in a splash of venom.
        f.add(f.ability("great_leap").unlock(5).cooldown(70).stamina(14).anim("vault").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 dir = PowerKit.flatAim(c);
            FruitKit.impulse(u, new Vec3(dir.x * 1.4, 1.3, dir.z * 1.4), true);
            PowerKit.sound(c, u.position(), SoundEvents.FROG_LONG_JUMP, 1.5f, 0.8f);
            OriginalKit.onLanding(c, 40, () -> {
                Vec3 at = u.position();
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 0.5, 0), 4)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 7), venom.withPoise(35));
                    envenom(e, 80, 1);
                }
                FruitKit.fx(u, "kaeru_splash", at, 4, 0, 0);
            });
            return true;
        }));

        // Toxic skin: the skin floods with toxin — attackers are badly poisoned and the frog's own hits poison too.
        f.add(f.ability("toxic_skin").unlock(12).cooldown(300).stamina(16).anim("harden").exec((u, c) -> {
            CombatState.of(u).buffs.put(TOXIN, 200 + c.mastery);
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 3)) {
                Hits.hit(u, e, FruitKit.dmg(c, 3), venom.withPoise(15));
                envenom(e, 100, 1);
            }
            FruitKit.fx(u, "kaeru_skin", u.position().add(0, 1, 0), u.getBbWidth() + 0.4f, u.getBbHeight(), 0);
            PowerKit.sound(c, u.position(), SoundEvents.SLIME_SQUISH, 1f, 1.5f);
            return true;
        }));

        // Venom spit: an arcing glob of venom — poisons, withers and briefly paralyses.
        f.add(f.ability("venom_spit").unlock(30).cooldown(150).stamina(24).anim("cast_forward").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "kaeru_venom", 0.5f, 1.7, FruitKit.dmg(c, 9), venom.withStun(20).withPoise(40));
            p.setDeltaMovement(p.getDeltaMovement().add(0, 0.15, 0));
            p.gravity = 0.03;
            p.life = 60;
            p.onHit = (pr, hit, pos) -> {
                for (LivingEntity e : PowerKit.foes(u, pos, 2.5)) {
                    envenom(e, 120, 2);
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
                }
            };
            // spat at point-blank range it hits at once
            for (LivingEntity e : Hits.cone(u, 2.5 + u.getScale(), 60)) {
                if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 5), venom.withPoise(20))) envenom(e, 120, 2);
            }
            PowerKit.sound(c, u.position(), SoundEvents.LLAMA_SPIT, 1.5f, 0.6f);
            return true;
        }));

        // Awakening — Poison Monsoon: a monsoon of toxic rain over a wide area; enemies are drenched in venom while the
        // frog (and its allies) revel in the rain.
        f.add(f.ability("poison_monsoon").unlock(55).cooldown(1000).stamina(80).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 at = u.position();
            double r = 10;
            OriginalKit.pulses(c, 0, 10, 10, n -> {
                FruitKit.fx(u, "kaeru_monsoon", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 3), venom.withPoise(10));
                    envenom(e, 60, 2);
                    if (n % 3 == 0) e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
                }
                for (LivingEntity a : OriginalKit.allies(u, at.add(0, 1, 0), r, true)) {
                    OriginalKit.heal(a, 1.5f, "");
                    a.clearFire();
                }
            });
            PowerKit.sound(c, at, SoundEvents.WEATHER_RAIN_ABOVE, 2f, 0.7f);
            return true;
        }));

        // Passive: toxic skin and poison immunity; fire dries the frog out (+25%).
        f.incomingHook = (u, a, amount, info) -> {
            boolean toxin = CombatState.of(u).hasBuff(TOXIN);
            if (a != null && a.distanceTo(u) < 3.5 && (toxin || !Zoan.form(u).isEmpty()) && Hits.hostileTo(u, a)) envenom(a, toxin ? 100 : 40, toxin ? 2 : 0);
            return info.element() == Element.FIRE || info.element() == Element.MAGMA ? amount * 1.25f : amount;
        };
        f.outgoingHook = (u, v, amount, info) -> {
            if (CombatState.of(u).hasBuff(TOXIN)) envenom(v, 80, 1);
            return amount;
        };
        f.tickHook = u -> {
            if (u.tickCount % 20 == 0) u.removeEffect(MobEffects.POISON);
        };
    }

    private DartFrogFruit() {}
}
