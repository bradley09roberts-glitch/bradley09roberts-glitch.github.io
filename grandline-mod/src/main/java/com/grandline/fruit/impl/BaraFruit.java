package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Bara Bara no Mi — the body splits into pieces; immune to cutting. */
public final class BaraFruit {
    public static void init(DevilFruit f) {
        f.add(f.ability("chop_cannon").cooldown(25).stamina(10).anim("punch_extend").exec((u, c) -> {
            FruitKit.shoot(c, "flying_fist", 0.5f, 1.8, FruitKit.dmg(c, 5), FruitKit.info(Element.BLUNT, 0.8f));
            return true;
        }));
        f.add(f.ability("split_dodge").unlock(5).cooldown(60).stamina(15).anim("split").exec((u, c) -> {
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 25;
            FruitKit.fx(u, "split", u.position().add(0, 1, 0), 0, 0, 0);
            return true;
        }));
        f.add(f.ability("chop_festival").unlock(20).cooldown(160).stamina(30).anim("split").exec((u, c) -> {
            // body parts fly out and pummel everything nearby for 3 seconds
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 20;
            for (int i = 0; i < 6; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i * 10L, () -> {
                    for (LivingEntity e : Hits.sphere(u, u.position().add(0, 1, 0), 7)) Hits.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.BLUNT, 0.5f));
                });
            }
            FruitKit.fx(u, "chop_festival", u.position().add(0, 1, 0), 7, 0, 0);
            return true;
        }));
        f.add(f.ability("flying_chop").unlock(12).cooldown(50).stamina(14).anim("rocket").exec((u, c) -> {
            // upper body flies: short flight burst
            Vec3 dir = c.look().normalize();
            FruitKit.impulse(u, new Vec3(dir.x * 1.5, Math.max(0.6, dir.y * 1.5), dir.z * 1.5), true);
            u.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 60, 0, false, false));
            return true;
        }));
        f.incomingHook = (u, a, amount, info) -> (info.element() == Element.SLASH && !info.imbued()) ? 0f : amount;
    }

    private BaraFruit() {}
}
