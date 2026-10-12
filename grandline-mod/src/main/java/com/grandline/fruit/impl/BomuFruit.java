package com.grandline.fruit.impl;

import com.grandline.combat.AbilityContext;
import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.TerrainChanges;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Bomu Bomu no Mi — the bomb body. Every part of the user (even breath and bodily fluids) can explode, and the user is
 * immune to explosions — their own and everyone else's.
 */
public final class BomuFruit {
    static void boom(AbilityContext c, Vec3 at, double r, float dmg, Hits.Info info) {
        FruitKit.aoe(c, at, r, dmg, info);
        FruitKit.fx(c.user, "explosion", at, (float) r, 0, 0);
        PowerKit.sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.1f);
    }

    public static void init(DevilFruit f) {
        // Nose Fancy Cannon: a flicked bomb that explodes on contact.
        f.add(f.ability("nose_fancy_cannon").cooldown(26).stamina(10).anim("finger_pistol").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "bomb_pellet", 0.35f, 2.2, FruitKit.dmg(c, 6), FruitKit.info(Element.IMPACT, 1.5f).withPoise(30));
            p.explosion = 2.0f;
            p.life = 40;
            p.gravity = 0.01;
            return true;
        }));

        // Kick Bomb: a kick that detonates on impact.
        f.add(f.ability("kick_bomb").unlock(5).cooldown(45).stamina(16).anim("kick_high").exec((u, c) -> {
            Vec3 at = c.eye().add(PowerKit.flatAim(c).scale(2)).add(0, -0.8, 0);
            for (LivingEntity e : Hits.cone(u, 4.5, 90)) Hits.hit(u, e, FruitKit.dmg(c, 11), FruitKit.info(Element.IMPACT, 2.8f).withPoise(55));
            FruitKit.fx(u, "explosion", at, 2, 0, 0);
            PowerKit.sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 1.2f);
            return true;
        }));

        // Breeze Breath Bomb: exhaled air that explodes in a chain along its path.
        f.add(f.ability("breeze_breath_bomb").unlock(12).cooldown(110).stamina(24).anim("cast_forward").exec((u, c) -> {
            Vec3 from = c.eye();
            Vec3 dir = FruitKit.aimDir(c);
            double len = PowerKit.rayLength(u, from, dir, 16);
            int steps = (int) Math.max(2, len / 2.5);
            for (int i = 1; i <= steps; i++) {
                int k = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 2L, () -> {
                    Vec3 at = from.add(dir.scale(Math.min(len, k * 2.5)));
                    boom(c, at, 2.2, FruitKit.dmg(c, 5), FruitKit.info(Element.IMPACT, 1.2f).withPoise(25));
                });
            }
            FruitKit.fx(u, "breath_trail", from, (float) (from.x + dir.x * len), (float) (from.y + dir.y * len), (float) (from.z + dir.z * len));
            return true;
        }));

        // Bomb Grab: seize the enemy and detonate the whole body — guard is useless.
        f.add(f.ability("bomb_grab").unlock(25).cooldown(200).stamina(32).anim("grab").exec((u, c) -> {
            LivingEntity t = CanonKit.melee(c, 4.5);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 16), FruitKit.info(Element.IMPACT, 3.5f).withStun(30).withPoise(90).asUnblockable());
            for (LivingEntity e : PowerKit.foes(u, t.position().add(0, 1, 0), 3)) if (e != t) Hits.hit(u, e, FruitKit.dmg(c, 6), FruitKit.info(Element.IMPACT, 1.5f));
            FruitKit.fx(u, "explosion", t.position().add(0, 1, 0), 3, 0, 0);
            PowerKit.sound(c, t.position(), SoundEvents.GENERIC_EXPLODE.value(), 1.3f, 0.8f);
            return true;
        }));

        // Awakening — Chain Detonation: the user becomes a living bomb and explodes in three growing blasts.
        f.add(f.ability("chain_detonation").unlock(55).cooldown(1000).stamina(80).awakened().anim("roar").exec((u, c) -> {
            Vec3 center = u.position().add(0, 1, 0);
            double[] radii = {4, 7, 10};
            float[] dmg = {12, 8, 6};
            for (int w = 0; w < 3; w++) {
                int k = w;
                CombatState.of(u).schedule(c.level.getGameTime() + w * 8L, () -> {
                    for (LivingEntity e : PowerKit.foes(u, center, radii[k]))
                        Hits.hit(u, e, FruitKit.dmg(c, dmg[k]), FruitKit.info(Element.IMPACT, 2.5f).withPoise(60).asUnblockable());
                    FruitKit.fx(u, "explosion", center, (float) radii[k], 0, 0);
                    for (int i = 0; i < 3; i++) {
                        double a = u.getRandom().nextDouble() * Math.PI * 2, d = radii[k] * (0.5 + u.getRandom().nextDouble() * 0.5);
                        TerrainChanges.crater(c.level, BlockPos.containing(center.add(Math.cos(a) * d, -1, Math.sin(a) * d)), 2, u);
                    }
                    PowerKit.sound(c, center, SoundEvents.GENERIC_EXPLODE.value(), 1.5f, 0.7f + k * 0.1f);
                });
            }
            return true;
        }));

        // Passive: a bomb body. Immune to explosions (see Wave3Events) and to the user's own blast element; every touch
        // can detonate, so plain punches and kicks hit 25% harder.
        f.outgoingHook = (u, v, amount, info) -> info.element() == Element.PHYSICAL || info.element() == Element.BLUNT ? amount * 1.25f : amount;
    }

    private BomuFruit() {}
}
