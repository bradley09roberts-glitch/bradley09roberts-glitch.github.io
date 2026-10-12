package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.TerrainChanges;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Gura Gura no Mi — tremors. Said to be able to destroy the world: quake punches that crack the air, tsunamis raised
 * from the sea, and at its peak, an earthquake that tilts the very ground everyone stands on.
 */
public final class GuraFruit {
    public static void init(DevilFruit f) {
        Hits.Info quake = FruitKit.info(Element.IMPACT, 3.5f);

        // Quake punch: a cone-shaped shockwave that blows enemies away and craters the ground.
        f.add(f.ability("quake_punch").cooldown(50).stamina(18).anim("punch_extend").exec((u, c) -> {
            FruitKit.cone(c, 8, 70, FruitKit.dmg(c, 10), quake.withPoise(60));
            Vec3 at = FruitKit.aimPoint(c, 6);
            TerrainChanges.crater(c.level, BlockPos.containing(at), 2, u);
            Vec3 dir = c.look();
            FruitKit.fx(u, "quake_cone", c.eye(), (float) dir.x, (float) dir.y, (float) dir.z);
            PowerKit.sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.7f);
            return true;
        }));

        // Air crack: the user grips the air itself and shatters it in every direction.
        f.add(f.ability("air_crack").unlock(10).cooldown(90).stamina(24).anim("double_palm").exec((u, c) -> {
            Vec3 center = u.position().add(0, 1, 0);
            FruitKit.aoe(c, center, 6, FruitKit.dmg(c, 9), FruitKit.info(Element.IMPACT, 2.6f).withStun(20).withPoise(50));
            FruitKit.fx(u, "air_crack", center, 6, 0, 0);
            PowerKit.sound(c, center, SoundEvents.GLASS_BREAK, 1.5f, 0.5f);
            return true;
        }));

        // Kaishin: a sea quake raises a tsunami. Over the sea it carries real sea water.
        f.add(f.ability("sea_quake").unlock(20).cooldown(160).stamina(34).anim("cast_forward").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 start = u.position();
            int len = 26 + c.mastery / 10;
            Set<Integer> struck = new HashSet<>();
            for (int d = 2; d <= len; d += 2) {
                int step = d;
                CombatState.of(u).schedule(c.level.getGameTime() + d / 2, () -> {
                    Vec3 p = start.add(dir.scale(step));
                    BlockPos g = PowerKit.ground(c.level, (int) Math.floor(p.x), (int) Math.floor(start.y), (int) Math.floor(p.z));
                    boolean sea = g != null && c.level.getBlockState(g).is(Blocks.WATER);
                    FruitKit.fx(u, "tsunami", p, (float) dir.x, (float) dir.z, sea ? 1 : 0);
                    for (LivingEntity e : PowerKit.foes(u, p.add(0, 1, 0), 2.6)) {
                        if (!struck.add(e.getId())) continue;
                        if (PowerKit.hit(u, e, FruitKit.dmg(c, 8), FruitKit.info(sea ? Element.WATER : Element.IMPACT, 0.5f).withPoise(40)))
                            FruitKit.impulse(e, dir.scale(1.4).add(0, 0.45, 0), false);
                    }
                });
            }
            return true;
        }));

        // Gekishin: a concentrated quake bubble on the fist — guard means nothing against it.
        f.add(f.ability("gekishin").unlock(35).cooldown(200).stamina(40).anim("heavy_punch").exec((u, c) -> {
            LivingEntity t = c.target != null && c.target.distanceTo(u) < 5.5 ? c.target : Hits.cone(u, 5, 60).stream().filter(e -> Hits.hostileTo(u, e)).findFirst().orElse(null);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 18), FruitKit.info(Element.IMPACT, 5f).withPoise(100).asUnblockable());
            Vec3 away = t.position().subtract(u.position()).multiply(1, 0, 1);
            Vec3 behind = t.position().add((away.lengthSqr() < 1e-4 ? PowerKit.flatAim(c) : away.normalize()).scale(2));
            TerrainChanges.crater(c.level, BlockPos.containing(behind), 3, u);
            FruitKit.fx(u, "air_crack", t.position().add(0, 1, 0), 3, 0, 0);
            PowerKit.sound(c, t.position(), SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.5f);
            return true;
        }));

        // Awakening — Island Tilt: an earthquake so strong the ground itself tips over.
        f.add(f.ability("shima_yurashi").unlock(60).cooldown(1200).stamina(95).awakened().anim("ground_slam").exec((u, c) -> {
            Vec3 center = u.position();
            Vec3 tilt = PowerKit.flatAim(c);
            for (int wave = 0; wave < 3; wave++) {
                int w = wave;
                CombatState.of(u).schedule(c.level.getGameTime() + wave * 20L, () -> {
                    FruitKit.fx(u, "island_tilt", center, 24, (float) tilt.x, (float) tilt.z);
                    for (LivingEntity e : PowerKit.foes(u, center, 24)) {
                        float dmg = FruitKit.dmg(c, w == 0 ? 14 : 6);
                        if (PowerKit.hit(u, e, dmg, FruitKit.info(Element.IMPACT, 0f).withPoise(w == 0 ? 100 : 40).asUnblockable()))
                            FruitKit.impulse(e, tilt.scale(1.6).add(0, w == 0 ? 0.8 : 0.4, 0), false);
                    }
                    for (int k = 0; k < 4; k++) {
                        double a = u.getRandom().nextDouble() * Math.PI * 2, d = 5 + u.getRandom().nextDouble() * 14;
                        TerrainChanges.crater(c.level, BlockPos.containing(center.add(Math.cos(a) * d, 0, Math.sin(a) * d)), 3, u);
                    }
                    PowerKit.sound(c, center, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.4f);
                });
            }
            return true;
        }));

        // Passive: a body that is the source of tremors shrugs off blunt force (and is immune to shockwaves).
        f.incomingHook = (u, a, amount, info) -> info.element() == Element.BLUNT ? amount * 0.85f : amount;
    }

    private GuraFruit() {}
}
