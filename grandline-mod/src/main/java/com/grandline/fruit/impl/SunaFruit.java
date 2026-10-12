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

import java.util.HashSet;
import java.util.Set;

/**
 * Suna Suna no Mi — sand Logia. Sandstorms, blades of sand and the power to dry out anything it touches.
 * Weakness: water. While wet (rain, splashing, water attacks) the body cannot turn to sand.
 */
public final class SunaFruit {
    static final String WET = "suna_wet";
    static final BlockState SAND = Blocks.SAND.defaultBlockState();

    public static void init(DevilFruit f) {
        Hits.Info sand = FruitKit.info(Element.SAND, 0.6f);

        // Barjan: crescent blades of sand.
        f.add(f.ability("barjan").cooldown(22).stamina(10).anim("slash_right").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "sand_blade", 1.0f, 2.2, FruitKit.dmg(c, 6), sand.withPoise(20));
            p.pierce = true;
            p.life = 28;
            return true;
        }));

        // Sables: a sandstorm that pulls enemies in and lifts them off their feet.
        f.add(f.ability("sables").unlock(5).cooldown(160).stamina(30).anim("cast_forward").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 24);
            double r = 5 + c.mastery / 25.0;
            for (int i = 0; i < 12; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 5L, () -> {
                    FruitKit.fx(u, "sandstorm", at, (float) r, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                        PowerKit.swirl(e, at, 0.35, 0.3, 0.18);
                        if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 1.5f), FruitKit.info(Element.SAND, 0f).withPoise(6));
                        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
                    }
                });
            }
            PowerKit.sound(c, at, SoundEvents.SAND_BREAK, 1.5f, 0.6f);
            return true;
        }));

        // Ground Secco: a dehydrating wave runs along the ground, turning it to sand and withering what it touches.
        f.add(f.ability("ground_secco").unlock(10).cooldown(120).stamina(26).anim("cast_ground").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 start = u.position();
            int len = 16 + c.mastery / 10;
            Set<Integer> struck = new HashSet<>();
            for (int d = 1; d <= len; d++) {
                int step = d;
                CombatState.of(u).schedule(c.level.getGameTime() + d / 2, () -> {
                    Vec3 p = start.add(dir.scale(step));
                    int half = Math.min(2, step / 5);
                    for (int w = -half; w <= half; w++) {
                        int x = (int) Math.floor(p.x - dir.z * w), z = (int) Math.floor(p.z + dir.x * w);
                        // plants on top wither away (cleared by paintColumn), the ground itself dries to sand
                        PowerKit.paintColumn(c.level, x, (int) Math.floor(start.y), z, SAND, null);
                    }
                    FruitKit.fx(u, "sand_wave", p, (float) dir.x, (float) dir.z, half + 1);
                    for (LivingEntity e : PowerKit.foes(u, p.add(0, 1, 0), 1.8 + half)) {
                        if (!struck.add(e.getId())) continue;
                        if (PowerKit.hit(u, e, FruitKit.dmg(c, 6), FruitKit.info(Element.SAND, 0.2f).withPoise(30))) {
                            e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                            e.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 1));
                        }
                    }
                });
            }
            return true;
        }));

        // Desert Spada: blades of sand rip a fissure through the ground.
        f.add(f.ability("desert_spada").unlock(20).cooldown(160).stamina(32).anim("cast_ground").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 start = u.position();
            Set<Integer> struck = new HashSet<>();
            for (int d = 2; d <= 20; d++) {
                int step = d;
                CombatState.of(u).schedule(c.level.getGameTime() + d / 3, () -> {
                    Vec3 p = start.add(dir.scale(step));
                    BlockPos g = PowerKit.ground(c.level, (int) Math.floor(p.x), (int) Math.floor(start.y), (int) Math.floor(p.z));
                    if (g != null && step % 2 == 0 && PowerKit.dig(c.level, g)) PowerKit.dig(c.level, g.below());
                    FruitKit.fx(u, "sand_fissure", p, (float) dir.x, (float) dir.z, 0);
                    for (LivingEntity e : PowerKit.foes(u, p.add(0, 1, 0), 1.7)) {
                        if (!struck.add(e.getId())) continue;
                        if (PowerKit.hit(u, e, FruitKit.dmg(c, 10), FruitKit.info(Element.SLASH, 0.4f).withPoise(45).withStun(15)))
                            FruitKit.impulse(e, new Vec3(0, 0.6, 0), false);
                    }
                });
            }
            return true;
        }));

        // Dry grasp: seize a target and drain the moisture from their body.
        f.add(f.ability("dehydrate").unlock(30).cooldown(180).stamina(30).anim("grab").exec((u, c) -> {
            LivingEntity t = c.target != null && c.target.distanceTo(u) < 4.5 ? c.target : Hits.cone(u, 4, 70).stream().filter(e -> Hits.hostileTo(u, e)).findFirst().orElse(null);
            if (t == null) return false;
            float dmg = FruitKit.dmg(c, 11);
            if (PowerKit.hit(u, t, dmg, FruitKit.info(Element.SAND, 0.1f).withStun(30).withPoise(60).asUnblockable())) {
                t.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 2));
                t.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 2));
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
                u.heal(dmg * 0.3f);
            }
            FruitKit.fx(u, "sand_drain", t.position().add(0, 1, 0), (float) u.getX(), (float) u.getY() + 1, (float) u.getZ());
            return true;
        }));

        // Awakening — Desert Girasole: the ground becomes a quicksand sinkhole that swallows everything nearby.
        f.add(f.ability("desert_girasole").unlock(50).cooldown(900).stamina(80).awakened().anim("ground_slam").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 30);
            double r = 7;
            PowerKit.paintDisc(c.level, at, r, SAND, null);
            TerrainChanges.crater(c.level, BlockPos.containing(at), 3, u);
            for (int i = 0; i < 16; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + 4 + i * 5L, () -> {
                    FruitKit.fx(u, "quicksand", at, (float) r, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, at, r + 2)) {
                        PowerKit.pull(e, at, 0.4, -0.08);
                        PowerKit.swirl(e, at, 0.15, 0, 0);
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3));
                        if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 2), FruitKit.info(Element.SAND, 0f).withPoise(8));
                    }
                });
            }
            CombatState.of(u).schedule(c.level.getGameTime() + 84, () -> {
                for (LivingEntity e : PowerKit.foes(u, at, 3.5)) Hits.hit(u, e, FruitKit.dmg(c, 10), FruitKit.info(Element.SAND, 0.5f).withStun(30).asUnblockable());
                FruitKit.fx(u, "sandstorm", at, 3.5f, 0, 0);
            });
            return true;
        }));

        // Passive: sand body — water is its weakness. Wet sand cannot scatter, and water hurts more.
        f.tickHook = u -> {
            if (u.tickCount % 10 == 0 && u.isInWaterOrRain()) CombatState.of(u).buffs.put(WET, 60);
        };
        f.intangibleHook = (u, a, info) -> !(u.isInWaterOrRain() || CombatState.of(u).hasBuff(WET));
        f.incomingHook = (u, a, amount, info) -> {
            if (info.element() != Element.WATER) return amount;
            CombatState.of(u).buffs.put(WET, 100);
            return amount * 1.5f;
        };
    }

    private SunaFruit() {}
}
