package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Gasu Gasu no Mi — gas Logia. Burning gas jets, gas that explodes at a snap, oxygen pulled out of the air and the
 * creeping death-cloud Shinokuni. Poison means nothing to a body made of gas.
 */
public final class GasuFruit {
    static final String ROBE = "gasu_robe";

    public static void init(DevilFruit f) {
        // Gastille: a burner jet of ignited gas from the lips.
        f.add(f.ability("gastille").cooldown(26).stamina(11).anim("cast_forward").exec((u, c) -> {
            Vec3 from = c.eye().add(0, -0.2, 0);
            Vec3 dir = FruitKit.aimDir(c);
            Vec3 end = PowerKit.beam(c, from, dir, 14, 0.8, FruitKit.dmg(c, 7), FruitKit.info(Element.FIRE, 0.6f).withPoise(25));
            FruitKit.fx(u, "gas_jet", from, (float) end.x, (float) end.y, (float) end.z);
            PowerKit.sound(c, u.position(), SoundEvents.BLAZE_SHOOT, 1f, 0.8f);
            return true;
        }));

        // Gastanet: gas between the palms, ignited — a point-blank explosion.
        f.add(f.ability("gastanet").unlock(5).cooldown(50).stamina(16).anim("double_palm").exec((u, c) -> {
            for (LivingEntity e : Hits.cone(u, 5, 100)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 10), FruitKit.info(Element.IMPACT, 2.6f).withPoise(50))) e.igniteForSeconds(3);
            }
            FruitKit.fx(u, "gas_explosion", c.eye().add(c.look().scale(2.5)), 2.5f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.2f);
            return true;
        }));

        // Oxygen removal: the user pulls the oxygen out of an area; anyone inside chokes, reels and weakens.
        f.add(f.ability("oxygen_drain").unlock(15).cooldown(220).stamina(30).anim("cast_forward").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 22);
            double r = 5 + c.mastery / 30.0;
            for (int i = 0; i < 8; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 10L, () -> {
                    FruitKit.fx(u, "gas_cloud", at, (float) r, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
                        e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
                        if (e instanceof ServerPlayer sp) sp.setAirSupply(Math.max(0, sp.getAirSupply() - 60));
                        if (n >= 1) Hits.hit(u, e, FruitKit.dmg(c, 2 + n * 0.4f), FruitKit.info(Element.POISON, 0f).withPoise(8).asUnblockable());
                    }
                });
            }
            return true;
        }));

        // Gas Robe: the body burns as a robe of flame — attackers who come close catch fire.
        f.add(f.ability("gas_robe").unlock(25).cooldown(400).stamina(26).anim("focus").exec((u, c) -> {
            CombatState.of(u).buffs.put(ROBE, 200 + c.mastery);
            FruitKit.aoe(c, u.position().add(0, 1, 0), 3, FruitKit.dmg(c, 4), FruitKit.info(Element.FIRE, 1.2f).withPoise(20));
            FruitKit.fx(u, "gas_robe", u.position().add(0, 1, 0), 1.5f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.FIRECHARGE_USE, 1f, 0.6f);
            return true;
        }));

        // Awakening — Shinokuni: a giant creeping cloud of poison gas that petrifies whoever breathes it.
        f.add(f.ability("shinokuni").unlock(55).cooldown(1100).stamina(85).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 start = FruitKit.aimPoint(c, 18);
            Vec3 drift = PowerKit.flatAim(c).scale(0.25);
            for (int i = 0; i < 20; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 5L, () -> {
                    Vec3 at = start.add(drift.scale(n));
                    double r = 7 + n * 0.2;
                    FruitKit.fx(u, "shinokuni", at, (float) r, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                        e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 2));
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3));
                        if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 2.5f), FruitKit.info(Element.POISON, 0f).withPoise(10));
                    }
                });
            }
            CombatState.of(u).schedule(c.level.getGameTime() + 102, () -> {
                Vec3 at = start.add(drift.scale(20));
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 10)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 9), FruitKit.info(Element.POISON, 0f).withStun(50).asUnblockable());
                    CanonKit.disable(e, 50, 5);
                }
            });
            return true;
        }));

        // Passive: a gas body — poison cannot touch it and it drifts down instead of falling. With the Gas Robe up,
        // nearby enemies are scorched every second.
        f.tickHook = u -> {
            u.fallDistance = 0;
            if (u.hasEffect(MobEffects.POISON)) u.removeEffect(MobEffects.POISON);
            if (u.tickCount % 20 == 0 && CombatState.of(u).hasBuff(ROBE)) {
                for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 2.6)) {
                    Hits.hit(u, e, 2f * Hits.abilityPower(u), FruitKit.info(Element.FIRE, 0.2f).withPoise(5));
                }
                com.grandline.net.GLNetwork.fx(u, "gas_robe", u.getX(), u.getY() + 1, u.getZ(), 1.2f, 0, 0);
            }
        };
        f.incomingHook = (u, a, amount, info) -> {
            if (a != null && CombatState.of(u).hasBuff(ROBE) && a.distanceTo(u) < 3.5) a.igniteForSeconds(3);
            return amount;
        };
    }

    private GasuFruit() {}
}
