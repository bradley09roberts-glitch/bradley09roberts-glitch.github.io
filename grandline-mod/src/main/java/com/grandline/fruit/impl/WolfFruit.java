package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.Zoan;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Inu Inu no Mi, Model: Wolf — Zoan with hybrid and full wolf forms (GeckoLib model "wolf"). */
public final class WolfFruit {
    public static void init(DevilFruit f) {
        Hits.Info claw = FruitKit.info(Element.SLASH, 0.6f);
        f.add(f.ability("transform").cooldown(20).stamina(0).anim("transform").exec((u, c) -> {
            if (u instanceof net.minecraft.server.level.ServerPlayer sp) Zoan.cycle(sp);
            else Zoan.setForm(u, Zoan.form(u).isEmpty() ? "hybrid" : "");
            return true;
        }));
        f.add(f.ability("howl").cooldown(160).stamina(15).anim("howl").exec((u, c) -> {
            for (LivingEntity e : Hits.sphere(u, u.position(), 12)) {
                if (!Hits.hostileTo(u, e)) continue;
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
            }
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
            FruitKit.fx(u, "howl", u.position().add(0, 1.5, 0), 12, 0, 0);
            return true;
        }));
        f.add(f.ability("pounce").cooldown(50).stamina(14).anim("pounce").exec((u, c) -> {
            if (Zoan.form(u).isEmpty()) return false;
            Vec3 dir = FruitKit.aimDir(c).multiply(1, 0, 1).normalize();
            FruitKit.impulse(u, new Vec3(dir.x * 1.8, 0.55, dir.z * 1.8), true);
            CombatState.of(u).schedule(c.level.getGameTime() + 8, () -> {
                for (LivingEntity e : Hits.sphere(u, u.position().add(0, 0.8, 0), 2.5)) Hits.hit(u, e, FruitKit.dmg(c, 8), claw.withPoise(45));
            });
            return true;
        }));
        f.add(f.ability("rending_claws").unlock(10).cooldown(70).stamina(20).anim("claw_flurry").exec((u, c) -> {
            if (Zoan.form(u).isEmpty()) return false;
            for (int i = 0; i < 5; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i * 3L, () -> {
                    for (LivingEntity e : Hits.cone(u, 3.5, 100)) Hits.hit(u, e, FruitKit.dmg(c, 3.5f), claw);
                });
            }
            return true;
        }));
        f.add(f.ability("moon_fang").unlock(50).cooldown(400).stamina(50).awakened().anim("howl").exec((u, c) -> {
            Zoan.setForm(u, "awakened");
            u.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 1));
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 2));
            return true;
        }));
        f.tickHook = u -> {
            String form = Zoan.form(u);
            if (form.isEmpty() || u.tickCount % 20 != 0) return;
            u.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, form.equals("full") ? 2 : 1, false, false));
        };
    }

    private WolfFruit() {}
}
