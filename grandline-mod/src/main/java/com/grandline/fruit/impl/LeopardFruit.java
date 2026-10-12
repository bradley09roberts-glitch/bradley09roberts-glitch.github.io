package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.Zoan;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Neko Neko no Mi, Model: Leopard — Zoan. A fast, deadly hunter: pounces, claws, flying kicks and stalking. */
public final class LeopardFruit {
    static final String STALK = "leopard_stalk";

    public static void init(DevilFruit f) {
        Hits.Info claw = FruitKit.info(Element.SLASH, 0.8f);
        PowerKit.zoanTransform(f);

        // Pounce: a long leap onto the prey.
        f.add(f.ability("pounce").cooldown(45).stamina(14).anim("pounce").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 dir = PowerKit.flatAim(c);
            FruitKit.impulse(u, new Vec3(dir.x * 1.9, 0.55, dir.z * 1.9), true);
            CombatState.of(u).schedule(c.level.getGameTime() + 7, () -> {
                if (!u.isAlive()) return;
                for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 0.8, 0), 2.6)) Hits.hit(u, e, FruitKit.dmg(c, 9), claw.withPoise(45));
            });
            return true;
        }));

        // Claw rend: a fast flurry of claw swipes.
        f.add(f.ability("claw_rend").unlock(5).cooldown(60).stamina(18).anim("claw_flurry").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            for (int i = 0; i < 4; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i * 3L, () -> {
                    if (!u.isAlive()) return;
                    for (LivingEntity e : Hits.cone(u, 3.5, 100)) Hits.hit(u, e, FruitKit.dmg(c, 3.5f), claw.withPoise(12));
                    FruitKit.fx(u, "claw", u.getEyePosition().add(u.getLookAngle().scale(1.5)), 0, 0, 0);
                });
            }
            return true;
        }));

        // Tempest kick: a kick so fast it sends a blade of air flying.
        f.add(f.ability("tempest_kick").unlock(10).cooldown(50).stamina(18).anim("kick_high").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "tempest_kick", 1.1f, 2.4, FruitKit.dmg(c, Zoan.form(u).isEmpty() ? 8 : 10), FruitKit.info(Element.SLASH, 0.9f).withPoise(35));
            p.pierce = true;
            p.life = 32;
            return true;
        }));

        // Stalk: vanish into the shadows; the next strike lands as an ambush.
        f.add(f.ability("predator_stalk").unlock(25).cooldown(300).stamina(22).anim("focus").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            u.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 80, 0, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 2, false, false));
            CombatState.of(u).buffs.put(STALK, 100);
            for (LivingEntity e : PowerKit.foes(u, u.position(), 16)) PowerKit.loseTrack(u, e);
            FruitKit.fx(u, "transform", u.position().add(0, 1, 0), 0, 0, 0);
            return true;
        }));

        // Awakening — Leopard Rokuogan: the awakened leopard's shockwave strike that ignores guard.
        f.add(f.ability("leopard_rokuogan").unlock(50).cooldown(1000).stamina(80).awakened().anim("double_palm").exec((u, c) -> {
            Zoan.setForm(u, "awakened");
            Vec3 from = c.eye();
            Vec3 dir = FruitKit.aimDir(c);
            for (LivingEntity e : Hits.line(u, from, dir, 7, 1.5))
                Hits.hit(u, e, FruitKit.dmg(c, 20), FruitKit.info(Element.IMPACT, 4f).withPoise(120).asUnblockable());
            FruitKit.fx(u, "air_crack", from.add(dir.scale(3)), 3, 0, 0);
            return true;
        }));

        // Passive: feline body — transformed, the leopard is quick, leaps high, sees in the dark and lands softly.
        // Ambush: the first hit out of Stalk deals 60% more damage.
        f.tickHook = u -> {
            String form = Zoan.form(u);
            if (form.isEmpty()) return;
            if (u.fallDistance > 6) u.fallDistance = 6;
            if (u.tickCount % 20 != 0) return;
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, form.equals("hybrid") ? 0 : 1, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.JUMP, 30, 0, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, false, false));
        };
        f.outgoingHook = (u, v, amount, info) -> {
            CombatState s = CombatState.of(u);
            if (!s.hasBuff(STALK)) return amount;
            s.buffs.remove(STALK);
            u.removeEffect(MobEffects.INVISIBILITY);
            return amount * 1.6f;
        };
    }

    private LeopardFruit() {}
}
