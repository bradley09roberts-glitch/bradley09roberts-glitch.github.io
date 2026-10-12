package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.TerrainChanges;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Magu Magu no Mi — magma Logia, the most destructive of the elemental fruits. Magma beats fire and ice, scorches the
 * ground into (temporary) magma and turns the sea it touches into obsidian.
 */
public final class MaguFruit {
    static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
    static final BlockState OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState();

    public static void init(DevilFruit f) {
        Hits.Info magma = FruitKit.info(Element.MAGMA, 1.0f);

        // Dai Funka: a giant magma fist that leaves the ground molten.
        f.add(f.ability("dai_funka").cooldown(45).stamina(18).anim("punch_extend").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "magma_fist", 1.4f, 1.4, FruitKit.dmg(c, 10), magma.withPoise(40));
            p.explosion = 2.0f;
            p.breaksBlocks = true;
            p.gravity = 0.01;
            p.onHit = (pr, hit, pos) -> PowerKit.paintDisc(c.level, pos, 2.2, MAGMA, OBSIDIAN);
            PowerKit.sound(c, u.position(), SoundEvents.FIRECHARGE_USE, 1f, 0.6f);
            return true;
        }));

        // Meigo: a piercing magma thrust that burns through guards.
        f.add(f.ability("meigo").unlock(5).cooldown(70).stamina(20).anim("heavy_punch").exec((u, c) -> {
            LivingEntity t = c.target != null && c.target.distanceTo(u) < 4.5 ? c.target : Hits.cone(u, 4, 60).stream().filter(e -> Hits.hostileTo(u, e)).findFirst().orElse(null);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 14), FruitKit.info(Element.MAGMA, 1.6f).withPoise(80).asUnblockable());
            t.igniteForSeconds(8);
            FruitKit.fx(u, "magma_burst", t.position().add(0, 1, 0), 1.2f, 0, 0);
            return true;
        }));

        // Magma floor: the ground around the user melts; anyone standing on it burns.
        f.add(f.ability("magma_floor").unlock(15).cooldown(160).stamina(28).anim("cast_ground").exec((u, c) -> {
            Vec3 center = u.position();
            double r = 5 + c.mastery / 25.0;
            PowerKit.paintDisc(c.level, center, r, MAGMA, OBSIDIAN);
            FruitKit.fx(u, "magma_floor", center, (float) r, 0, 0);
            for (int i = 0; i < 6; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + 5 + i * 10L, () -> {
                    for (LivingEntity e : PowerKit.foes(u, center, r + 0.5)) {
                        if (Math.abs(e.getY() - center.y) > 2.5) continue;
                        Hits.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.MAGMA, 0.1f).withPoise(10));
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 1));
                    }
                    FruitKit.fx(u, "magma_floor", center, (float) r, 0, 0);
                });
            }
            return true;
        }));

        // Ryusei Kazan: magma fists fall from the sky like meteors.
        f.add(f.ability("ryusei_kazan").unlock(30).cooldown(320).stamina(45).anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 40);
            FruitKit.fx(u, "meteor_sky", at.add(0, 22, 0), 8, 0, 0);
            for (int i = 0; i < 14; i++) {
                double ox = (u.getRandom().nextDouble() - 0.5) * 14, oz = (u.getRandom().nextDouble() - 0.5) * 14;
                CombatState.of(u).schedule(c.level.getGameTime() + 8 + i * 3L, () -> {
                    Vec3 from = at.add(ox * 0.6, 22, oz * 0.6);
                    Vec3 to = at.add(ox, 0, oz);
                    AbilityProjectile p = AbilityProjectile.shoot(u, "meteor", 1.1f, from, to.subtract(from), 1.6, FruitKit.dmg(c, 8), FruitKit.info(Element.MAGMA, 1.2f).withPoise(40));
                    p.gravity = 0.04;
                    p.explosion = 2.2f;
                    p.breaksBlocks = true;
                    p.life = 80;
                    p.onHit = (pr, hit, pos) -> PowerKit.paintDisc(c.level, pos, 1.3, MAGMA, OBSIDIAN);
                });
            }
            return true;
        }));

        // Awakening — Bakuretsu Kazan: the user erupts like a volcano.
        f.add(f.ability("bakuretsu_kazan").unlock(55).cooldown(1000).stamina(85).awakened().anim("ground_slam").exec((u, c) -> {
            Vec3 center = u.position();
            PowerKit.paintDisc(c.level, center, 8, MAGMA, OBSIDIAN);
            for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), 9)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 16), FruitKit.info(Element.MAGMA, 2.5f).withPoise(100).asUnblockable()))
                    FruitKit.impulse(e, new Vec3(0, 1.0, 0), false);
            }
            FruitKit.fx(u, "eruption", center, 9, 0, 0);
            PowerKit.sound(c, center, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.6f);
            for (int i = 1; i <= 3; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i * 15L, () -> {
                    for (int k = 0; k < 4; k++) {
                        double a = u.getRandom().nextDouble() * Math.PI * 2, d = 3 + u.getRandom().nextDouble() * 6;
                        Vec3 p = center.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                        FruitKit.fx(u, "eruption", p, 2.5f, 0, 0);
                        FruitKit.aoe(c, p.add(0, 1, 0), 2.5, FruitKit.dmg(c, 6), FruitKit.info(Element.MAGMA, 0.8f));
                        TerrainChanges.crater(c.level, BlockPos.containing(p), 1, u);
                    }
                });
            }
            return true;
        }));

        // Passive: a body of magma — fire and heat cannot hurt it; ice melts against it.
        f.tickHook = u -> {
            if (u.tickCount % 20 == 0) u.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false));
        };
        f.incomingHook = (u, a, amount, info) -> switch (info.element()) {
            case FIRE -> 0f;
            case ICE -> amount * 0.5f;
            default -> amount;
        };
    }

    private MaguFruit() {}
}
