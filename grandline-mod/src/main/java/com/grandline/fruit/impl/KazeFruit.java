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

/**
 * Kaze Kaze no Mi — ORIGINAL wind Logia created for this mod. The user becomes wind: blades of air, updrafts,
 * a pulling cyclone, and a tailwind that fills the sails of any ship they stand on.
 */
public final class KazeFruit {
    public static void init(DevilFruit f) {
        Hits.Info wind = FruitKit.info(Element.WIND, 1.0f);
        f.add(f.ability("gale_blade").cooldown(20).stamina(9).anim("slash_right").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "wind_blade", 0.9f, 2.4, FruitKit.dmg(c, 5), FruitKit.info(Element.SLASH, 0.6f));
            p.pierce = true;
            p.life = 30;
            return true;
        }));
        f.add(f.ability("updraft").unlock(5).cooldown(50).stamina(14).anim("cast_up").exec((u, c) -> {
            FruitKit.impulse(u, new Vec3(0, 1.4, 0), false);
            u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
            for (LivingEntity e : Hits.sphere(u, u.position(), 5)) {
                if (!Hits.hostileTo(u, e)) continue;
                Hits.hit(u, e, FruitKit.dmg(c, 3), wind.withStun(15));
                FruitKit.impulse(e, new Vec3(0, 1.2, 0), false);
            }
            FruitKit.fx(u, "updraft", u.position(), 5, 0, 0);
            return true;
        }));
        f.add(f.ability("cyclone").unlock(20).cooldown(140).stamina(30).anim("cast_forward").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 18);
            for (int i = 0; i < 12; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i * 5L, () -> {
                    FruitKit.fx(u, "cyclone", at, 5, 0, 0);
                    for (LivingEntity e : Hits.sphere(u, at.add(0, 1, 0), 6)) {
                        if (!Hits.hostileTo(u, e)) continue;
                        Vec3 pull = at.subtract(e.position()).normalize().scale(0.35).add(0, 0.12, 0);
                        FruitKit.impulse(e, pull, false);
                        Hits.hit(u, e, FruitKit.dmg(c, 1.5f), FruitKit.info(Element.WIND, 0f));
                    }
                });
            }
            return true;
        }));
        f.add(f.ability("tailwind").unlock(10).cooldown(400).stamina(20).anim("cast_forward").exec((u, c) -> {
            // fills the sails of the ship the user stands on (see ShipEntity.windBonus)
            CombatState.of(u).buffs.put("tailwind", 20 * 60);
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60, 1, false, false));
            FruitKit.fx(u, "tailwind", u.position().add(0, 1, 0), 0, 0, 0);
            return true;
        }));
        f.add(f.ability("tempest_spear").unlock(45).cooldown(300).stamina(45).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "tempest", 2.2f, 1.6, FruitKit.dmg(c, 16), wind.withPoise(90));
            p.pierce = true;
            p.explosion = 3.5f;
            return true;
        }));
        f.tickHook = u -> {
            u.fallDistance = 0;
            if (!u.onGround() && u.getDeltaMovement().y < -0.3 && u.tickCount % 5 == 0)
                u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, false, false));
        };
    }

    private KazeFruit() {}
}
