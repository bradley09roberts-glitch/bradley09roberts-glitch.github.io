package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Gomu Gomu no Mi — rubber body (true identity: Hito Hito no Mi, Model: Nika, unlocked on awakening). */
public final class GomuFruit {
    public static void init(DevilFruit f) {
        Hits.Info punch = FruitKit.info(Element.BLUNT, 1.2f);
        f.add(f.ability("pistol").cooldown(16).stamina(8).anim("punch_extend").exec((u, c) -> {
            boolean gear = CombatState.of(u).toggles.contains("gear_second");
            int n = FruitKit.line(c, gear ? 16 : 13, 0.7, FruitKit.dmg(c, gear ? 8 : 6), punch.withPoise(25));
            FruitKit.fx(u, "rubber_arm", c.eye(), (float) c.look().x, (float) c.look().y, (float) c.look().z);
            return true;
        }));
        f.add(f.ability("rocket").unlock(5).cooldown(40).stamina(12).anim("rocket").exec((u, c) -> {
            Vec3 dir = c.look().add(0, 0.35, 0).normalize();
            FruitKit.impulse(u, dir.scale(2.4), true);
            return true;
        }));
        f.add(f.ability("gatling").unlock(10).cooldown(80).stamina(26).anim("gatling").exec((u, c) -> {
            boolean gear = CombatState.of(u).toggles.contains("gear_second");
            for (int i = 0; i < 12; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i * 2L, () -> {
                    if (!u.isAlive()) return;
                    for (LivingEntity e : Hits.cone(u, 7, 50)) Hits.hit(u, e, FruitKit.dmg(c, gear ? 2.6f : 2f), punch.withPoise(6));
                });
            }
            return true;
        }));
        f.add(f.ability("bazooka").unlock(15).cooldown(70).stamina(20).anim("double_palm").exec((u, c) -> {
            FruitKit.cone(c, 5, 60, FruitKit.dmg(c, 10), FruitKit.info(Element.BLUNT, 3.2f).withPoise(70));
            return true;
        }));
        f.add(f.ability("gear_second").unlock(30).cooldown(200).stamina(25).anim("gear_second").exec((u, c) -> {
            var cs = CombatState.of(u);
            if (cs.toggles.remove("gear_second")) return true;
            cs.toggles.add("gear_second");
            cs.buffs.put("gear_second", 20 * 30);
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 30, 2, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 30, 1, false, false));
            FruitKit.fx(u, "steam", u.position().add(0, 1, 0), 30, 0, 0);
            return true;
        }));
        f.add(f.ability("gear_third").unlock(45).cooldown(300).stamina(45).anim("giant_punch").exec((u, c) -> {
            CombatState.of(u).schedule(c.level.getGameTime() + 14, () -> {
                Vec3 at = u.getEyePosition().add(u.getLookAngle().scale(6));
                FruitKit.fx(u, "giant_fist", at, 3f, 0, 0);
                FruitKit.aoe(c, at, 4.5, FruitKit.dmg(c, 18), FruitKit.info(Element.BLUNT, 4f).withPoise(100));
                com.grandline.fruit.TerrainChanges.crater(c.level, net.minecraft.core.BlockPos.containing(at), 3, u);
            });
            return true;
        }));
        f.add(f.ability("nika_drums").unlock(80).cooldown(1200).stamina(90).awakened().anim("nika").exec((u, c) -> {
            // Awakening (Model: Nika): cartoon-physics frenzy — strength, speed, jump and a shockwave
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 40, 3, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 40, 3, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.JUMP, 20 * 40, 3, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 40, 1, false, false));
            FruitKit.aoe(c, u.position().add(0, 1, 0), 10, FruitKit.dmg(c, 10), FruitKit.info(Element.BLUNT, 3f).withStun(40));
            FruitKit.fx(u, "nika", u.position().add(0, 1, 0), 10, 0, 0);
            return true;
        }));
        // Passive: rubber body shrugs off blunt force and bullets, and is immune to electricity.
        f.incomingHook = (u, a, amount, info) -> switch (info.element()) {
            case BLUNT, IMPACT -> amount * 0.2f;
            case LIGHTNING -> 0f;
            case PIERCE -> info.imbued() ? amount : amount * 0.35f;
            default -> amount;
        };
        f.tickHook = u -> u.fallDistance = 0;
    }

    private GomuFruit() {}
}
