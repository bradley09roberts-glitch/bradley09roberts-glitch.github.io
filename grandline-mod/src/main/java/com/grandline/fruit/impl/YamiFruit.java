package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.FruitRules;
import com.grandline.fruit.TerrainChanges;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Yami Yami no Mi — darkness. Infinite gravity that swallows attacks and people, and a touch that cancels Devil Fruit
 * powers. The price: the user's body cannot become intangible and draws in pain (takes 20% more damage).
 */
public final class YamiFruit {
    /** Things swallowed by the darkness (projectiles, rubble) — released again by Liberation. */
    static final Map<LivingEntity, Integer> SWALLOWED = Collections.synchronizedMap(new WeakHashMap<>());

    static void swallow(LivingEntity u, Vec3 center, double r) {
        int n = 0;
        for (Projectile p : PowerKit.hostileProjectiles(u, center, r)) {
            p.discard();
            n++;
        }
        if (n > 0) SWALLOWED.merge(u, n, (a, b) -> Math.min(24, a + b));
    }

    public static void init(DevilFruit f) {
        Hits.Info dark = FruitKit.info(Element.DARKNESS, 0f);

        // Black Hole: darkness spreads over the ground, dragging everything into it.
        f.add(f.ability("black_hole").cooldown(160).stamina(28).anim("cast_ground").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 20);
            double r = 6 + c.mastery / 25.0;
            PowerKit.paintDisc(c.level, at, Math.min(4.5, r - 1), Blocks.BLACK_CONCRETE.defaultBlockState(), null);
            for (int i = 0; i < 20; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 5L, () -> {
                    FruitKit.fx(u, "black_hole", at, (float) r, 0, 0);
                    swallow(u, at.add(0, 1, 0), r);
                    for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                        PowerKit.pull(e, at, 0.25, -0.05);
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2));
                        e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0));
                        if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 1), dark.withPoise(6));
                    }
                });
            }
            return true;
        }));

        // Kurouzu: a vortex of darkness that yanks a target into the user's grip.
        f.add(f.ability("kurouzu").unlock(5).cooldown(90).stamina(20).anim("grab").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 22);
            if (t == null) return false;
            Vec3 dir = PowerKit.flatAim(c);
            double reach = Math.max(0.6, PowerKit.rayLength(u, u.position().add(0, 0.5, 0), dir, 1.5) - 0.3);
            Vec3 front = u.position().add(dir.scale(reach));
            Vec3 from = t.position().add(0, 1, 0);
            PowerKit.place(t, front);
            Hits.hit(u, t, FruitKit.dmg(c, 6), FruitKit.info(Element.DARKNESS, 0f).withStun(30).withPoise(40));
            FruitKit.fx(u, "dark_pull", from, (float) front.x, (float) front.y + 1, (float) front.z);
            return true;
        }));

        // Darkness grab: while held in the dark, the target's own Devil Fruit stops working.
        f.add(f.ability("dark_grasp").unlock(15).cooldown(160).stamina(26).anim("grab").exec((u, c) -> {
            LivingEntity t = c.target != null && c.target.distanceTo(u) < 4.5 ? c.target : Hits.cone(u, 4, 70).stream().filter(e -> Hits.hostileTo(u, e)).findFirst().orElse(null);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 7), FruitKit.info(Element.DARKNESS, 0f).withStun(40).withPoise(50).asUnblockable());
            FruitRules.applySeastoneContact(t, 100 + c.mastery);
            t.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
            FruitKit.fx(u, "dark_grasp", t.position().add(0, 1, 0), t.getBbWidth(), t.getBbHeight(), 0);
            PowerKit.sound(c, t.position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6f, 0.5f);
            return true;
        }));

        // Liberation: everything the darkness swallowed bursts back out.
        f.add(f.ability("liberation").unlock(25).cooldown(200).stamina(34).anim("double_palm").exec((u, c) -> {
            int stored = SWALLOWED.getOrDefault(u, 0);
            SWALLOWED.remove(u);
            Vec3 center = u.position().add(0, 1, 0);
            FruitKit.aoe(c, center, 7, FruitKit.dmg(c, 9 + Math.min(12, stored)), FruitKit.info(Element.DARKNESS, 3f).withPoise(60));
            int shards = Math.min(16, 6 + stored);
            for (int i = 0; i < shards; i++) {
                double a = i * Math.PI * 2 / shards;
                Vec3 dir = new Vec3(Math.cos(a), 0.15 + u.getRandom().nextDouble() * 0.2, Math.sin(a));
                AbilityProjectile p = AbilityProjectile.shoot(u, "dark_debris", 0.5f, center.add(dir.scale(0.8)), dir, 1.4, FruitKit.dmg(c, 4), FruitKit.info(Element.BLUNT, 0.8f));
                p.gravity = 0.03;
                p.life = 40;
            }
            FruitKit.fx(u, "darkness_burst", center, 7, 0, 0);
            return true;
        }));

        // Awakening — Black World: the darkness swallows the whole area, ground and all.
        f.add(f.ability("black_world").unlock(55).cooldown(1100).stamina(90).awakened().anim("roar").exec((u, c) -> {
            Vec3 center = u.position();
            double r = 14;
            for (int i = 0; i < 12; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i * 10L, () -> {
                    FruitKit.fx(u, "black_world", center, (float) r, 0, 0);
                    swallow(u, center.add(0, 1, 0), r);
                    for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), r)) {
                        PowerKit.pull(e, center, 0.18, 0);
                        e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                        Hits.hit(u, e, FruitKit.dmg(c, 3), dark.withPoise(15));
                    }
                    for (int k = 0; k < 2; k++) {
                        double a = u.getRandom().nextDouble() * Math.PI * 2, d = 4 + u.getRandom().nextDouble() * (r - 4);
                        TerrainChanges.crater(c.level, BlockPos.containing(center.add(Math.cos(a) * d, 0, Math.sin(a) * d)), 2, u);
                    }
                });
            }
            return true;
        }));

        // Passive: darkness draws everything in — including pain — and can never let attacks pass through.
        f.intangibleHook = (u, a, info) -> false;
        f.incomingHook = (u, a, amount, info) -> amount * 1.2f;
    }

    private YamiFruit() {}
}
