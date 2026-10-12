package com.grandline.fruit.impl;

import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Mera Mera no Mi — fire Logia. */
public final class MeraFruit {
    public static void init(DevilFruit f) {
        Hits.Info fire = FruitKit.info(Element.FIRE, 0.8f);
        f.add(f.ability("fire_fist").cooldown(40).stamina(18).anim("punch_extend").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "fire_fist", 1.3f, 1.6, FruitKit.dmg(c, 9), fire.withPoise(35));
            p.explosion = 2.2f;
            p.breaksBlocks = true;
            return true;
        }));
        f.add(f.ability("fire_gun").unlock(8).cooldown(50).stamina(20).anim("rapid_fire").exec((u, c) -> {
            for (int i = 0; i < 6; i++) {
                int delay = i * 3;
                com.grandline.combat.CombatState.of(u).schedule(c.level.getGameTime() + delay, () -> {
                    if (!u.isAlive()) return;
                    AbilityProjectile p = FruitKit.shoot(c, "fire_bullet", 0.35f, 2.2, FruitKit.dmg(c, 2.5f), fire);
                    p.setDeltaMovement(p.getDeltaMovement().add((u.getRandom().nextDouble() - 0.5) * 0.15, (u.getRandom().nextDouble() - 0.5) * 0.1, (u.getRandom().nextDouble() - 0.5) * 0.15));
                });
            }
            return true;
        }));
        f.add(f.ability("flame_dash").unlock(15).cooldown(60).stamina(16).anim("dash").exec((u, c) -> {
            Vec3 start = u.position();
            Vec3 end = FruitKit.blink(u, c.look().multiply(1, 0.3, 1), 10);
            FruitKit.fx(u, "fire_trail", start, (float) end.x, (float) end.y, (float) end.z);
            for (LivingEntity e : Hits.line(u, start.add(0, 1, 0), end.subtract(start), start.distanceTo(end), 1.2)) {
                Hits.hit(u, e, FruitKit.dmg(c, 5), fire);
            }
            return true;
        }));
        f.add(f.ability("flame_ring").unlock(25).cooldown(120).stamina(30).anim("cast_ground").exec((u, c) -> {
            Vec3 center = u.position().add(0, 1, 0);
            FruitKit.aoe(c, center, 6, FruitKit.dmg(c, 7), fire.withPoise(40));
            FruitKit.fx(u, "fire_ring", center, 6, 0, 0);
            BlockPos base = u.blockPosition();
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI / 12;
                BlockPos p = base.offset((int) Math.round(Math.cos(a) * 5), 0, (int) Math.round(Math.sin(a) * 5));
                if (c.level.getBlockState(p).isAir() && c.level.getBlockState(p.below()).isSolidRender(c.level, p.below())) FruitKit.temp(c.level, p, Blocks.FIRE.defaultBlockState());
            }
            return true;
        }));
        f.add(f.ability("great_flame_emperor").unlock(60).cooldown(600).stamina(70).anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 40);
            AbilityProjectile p = AbilityProjectile.shoot(u, "flame_emperor", 4.5f, u.getEyePosition().add(0, 6, 0), at.subtract(u.getEyePosition().add(0, 6, 0)), 0.9, FruitKit.dmg(c, 22), fire.withPoise(100).asUnblockable());
            p.explosion = 7f;
            p.breaksBlocks = true;
            p.life = 120;
            return true;
        }));
    }

    /** Passive: fire immunity is applied in FruitPassives. */
    private MeraFruit() {}
}
