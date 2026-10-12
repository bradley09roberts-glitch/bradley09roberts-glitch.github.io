package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Mori Mori no Mi — forest Logia. Roots that impale and drain the life out of their prey, forests grown in an instant
 * and volleys of wooden stakes. In a forest the user mends constantly; fire is the forest's natural enemy.
 */
public final class MoriFruit {
    static final BlockState LOG = Blocks.OAK_LOG.defaultBlockState();
    static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);

    /** Grow a temporary tree trunk (and a leaf crown) on the ground at a column. */
    static void tree(ServerLevel l, double x, double y, double z, int height) {
        BlockPos g = PowerKit.ground(l, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        if (g == null || !l.getBlockState(g).getFluidState().isEmpty()) return;
        BlockPos p = g.above();
        for (int i = 0; i < height; i++) {
            if (!PowerKit.placeFree(l, p.above(i), LOG)) return;
        }
        PowerKit.placeFree(l, p.above(height), LEAVES);
    }

    static void roots(LivingEntity u, Vec3 at, float size) {
        FruitKit.fx(u, "roots", at, size, 0, 0);
    }

    public static void init(DevilFruit f) {
        // Root Spear: roots burst out of the ground under the target and impale it.
        f.add(f.ability("root_spear").cooldown(26).stamina(11).anim("cast_ground").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 18);
            Vec3 at = t != null ? t.position() : FruitKit.aimPoint(c, 18);
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 1.8)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 8), FruitKit.info(Element.PIERCE, 0.2f).withPoise(35)))
                    FruitKit.impulse(e, new Vec3(0, 0.7, 0), false);
            }
            roots(u, at, 1.5f);
            PowerKit.sound(c, at, SoundEvents.ROOTS_BREAK, 1.5f, 0.6f);
            return true;
        }));

        // Nutrient Drain: roots latch on and suck the life out of the target, feeding the user.
        f.add(f.ability("nutrient_drain").unlock(8).cooldown(140).stamina(20).anim("grab").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 10);
            if (t == null) return false;
            for (int i = 0; i < 6; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i * 10L, () -> {
                    if (!u.isAlive() || !t.isAlive() || t.distanceTo(u) > 14) return;
                    float d = FruitKit.dmg(c, 2.2f);
                    if (PowerKit.hit(u, t, d, FruitKit.info(Element.PHYSICAL, 0f).withPoise(6).asUnblockable())) {
                        u.heal(d * 0.6f);
                        t.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 1));
                        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 2));
                    }
                    FruitKit.fx(u, "life_drain", t.position().add(0, 1, 0), (float) u.getX(), (float) u.getY() + 1, (float) u.getZ());
                });
            }
            return true;
        }));

        // Overgrowth: a forest bursts out of the ground — trunks wall the area and roots entangle everyone inside.
        f.add(f.ability("overgrowth").unlock(15).cooldown(220).stamina(32).anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 20);
            double r = 5 + c.mastery / 30.0;
            for (int i = 0; i < 9; i++) {
                double a = i * Math.PI * 2 / 9 + u.getRandom().nextDouble() * 0.4, d = 2.5 + u.getRandom().nextDouble() * (r - 2);
                tree(c.level, at.x + Math.cos(a) * d, at.y, at.z + Math.sin(a) * d, 3 + u.getRandom().nextInt(2));
            }
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 6), FruitKit.info(Element.PIERCE, 0f).withStun(35).withPoise(40)))
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4));
                roots(u, e.position(), 1.2f);
            }
            FruitKit.fx(u, "forest_burst", at, (float) r, 0, 0);
            PowerKit.sound(c, at, SoundEvents.ROOTED_DIRT_BREAK, 2f, 0.5f);
            return true;
        }));

        // Thorn Volley: a rapid barrage of sharpened wooden stakes.
        f.add(f.ability("thorn_volley").unlock(25).cooldown(90).stamina(22).anim("rapid_fire").exec((u, c) -> {
            for (int i = 0; i < 7; i++) {
                int k = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 2L, () -> {
                    if (!u.isAlive()) return;
                    Vec3 d = PowerKit.yaw(FruitKit.aimDir(c), (k % 3 - 1) * 4).add(0, (u.getRandom().nextDouble() - 0.5) * 0.06, 0);
                    AbilityProjectile p = AbilityProjectile.shoot(u, "wood_stake", 0.45f, u.getEyePosition().add(d.scale(0.8)), d, 2.6,
                            FruitKit.dmg(c, 4), FruitKit.info(Element.PIERCE, 0.3f).withPoise(14));
                    p.life = 30;
                });
            }
            return true;
        }));

        // Awakening — Great Forest: the user's body spreads as a forest over the whole area; roots everywhere impale
        // and drain every enemy at once.
        f.add(f.ability("great_forest").unlock(55).cooldown(1100).stamina(85).awakened().anim("ground_slam").exec((u, c) -> {
            Vec3 center = u.position();
            double r = 13;
            for (int i = 0; i < 14; i++) {
                double a = u.getRandom().nextDouble() * Math.PI * 2, d = 4 + u.getRandom().nextDouble() * (r - 4);
                tree(c.level, center.x + Math.cos(a) * d, center.y, center.z + Math.sin(a) * d, 4 + u.getRandom().nextInt(3));
            }
            FruitKit.fx(u, "forest_burst", center, (float) r, 1, 0);
            for (int w = 0; w < 4; w++) {
                CombatState.of(u).schedule(c.level.getGameTime() + w * 10L, () -> {
                    float drained = 0;
                    List<LivingEntity> foes = new ArrayList<>(PowerKit.foes(u, center.add(0, 1, 0), r));
                    for (LivingEntity e : foes) {
                        float d = FruitKit.dmg(c, 5);
                        if (PowerKit.hit(u, e, d, FruitKit.info(Element.PIERCE, 0.2f).withPoise(30))) {
                            drained += d;
                            FruitKit.impulse(e, new Vec3(0, 0.5, 0), false);
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3));
                        }
                        roots(u, e.position(), 1.2f);
                    }
                    if (u.isAlive()) u.heal(drained * 0.25f);
                });
            }
            return true;
        }));

        // Passive: a forest body. In forests (or standing on living ground among trees) the user regenerates;
        // fire burns the forest — more damage, and a burning body cannot scatter.
        f.tickHook = u -> {
            if (u.tickCount % 40 != 0 || u.getHealth() >= u.getMaxHealth()) return;
            BlockPos p = u.blockPosition();
            var biome = u.level().getBiome(p);
            boolean forest = biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE) || biome.is(BiomeTags.IS_TAIGA);
            BlockState below = u.level().getBlockState(p.below());
            boolean living = below.is(BlockTags.DIRT) || below.is(BlockTags.LEAVES) || below.is(BlockTags.LOGS);
            if (forest && living) {
                u.heal(1.5f);
                u.removeEffect(MobEffects.HUNGER);
                com.grandline.net.GLNetwork.fx(u, "leaf_heal", u.getX(), u.getY() + 1, u.getZ(), 0, 0, 0);
            } else if (living) {
                u.heal(0.5f);
            }
        };
        f.intangibleHook = (u, a, info) -> !u.isOnFire() && info.element() != Element.FIRE && info.element() != Element.MAGMA;
        f.incomingHook = (u, a, amount, info) -> info.element() == Element.FIRE || info.element() == Element.MAGMA ? amount * 1.4f : amount;
    }

    private MoriFruit() {}
}
