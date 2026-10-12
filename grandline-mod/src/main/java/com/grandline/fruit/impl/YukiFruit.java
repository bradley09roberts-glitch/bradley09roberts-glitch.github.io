package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Yuki Yuki no Mi — snow Logia. Blades and shuriken of packed snow, blizzards that bury the ground, a snow dome that
 * shrugs off ranged attacks and, awakened, a whiteout in which the user vanishes. Heat is its weakness: a burning
 * snow body melts and cannot scatter.
 */
public final class YukiFruit {
    static final BlockState SNOW = Blocks.SNOW_BLOCK.defaultBlockState();
    static final String DOME = "yuki_kamakura";

    static void chill(LivingEntity e, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, amp));
        e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 100, e.getTicksFrozen() + ticks));
    }

    public static void init(DevilFruit f) {
        // Tabira Yuki: a long blade of hardened snow sweeps everything in front.
        f.add(f.ability("tabira_yuki").cooldown(24).stamina(10).anim("wide_slash").exec((u, c) -> {
            Hits.Info blade = FruitKit.info(Element.ICE, 1.0f).withPoise(30);
            for (LivingEntity e : Hits.cone(u, 5.5, 120)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 7), blade)) chill(e, 40, 1);
            }
            FruitKit.fx(u, "snow_blade", c.eye().add(c.look().scale(2)), 5.5f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.SNOW_BREAK, 1.2f, 0.7f);
            return true;
        }));

        // Yuki Rindo: a fan of snowflake shuriken.
        f.add(f.ability("yuki_rindo").unlock(5).cooldown(40).stamina(14).anim("throw").exec((u, c) -> {
            Vec3 dir = FruitKit.aimDir(c);
            for (int i = -2; i <= 2; i++) {
                AbilityProjectile p = AbilityProjectile.shoot(u, "snow_shuriken", 0.45f, c.eye().add(dir.scale(0.7)), PowerKit.yaw(dir, i * 7), 2.3,
                        FruitKit.dmg(c, 4), FruitKit.info(Element.ICE, 0.3f).withPoise(12));
                p.life = 30;
                p.onHit = (pr, hit, pos) -> {
                    if (hit != null) chill(hit, 30, 0);
                };
            }
            PowerKit.sound(c, u.position(), SoundEvents.SNOWBALL_THROW, 1f, 1.3f);
            return true;
        }));

        // Yuki Gaki: a blizzard over the target area buries the ground in snow and freezes everyone inside.
        f.add(f.ability("yuki_gaki").unlock(15).cooldown(170).stamina(30).anim("cast_forward").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 26);
            double r = 5 + c.mastery / 25.0;
            PowerKit.paintDisc(c.level, at, r - 1, SNOW, null);
            for (int i = 0; i < 10; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 6L, () -> {
                    FruitKit.fx(u, "blizzard", at, (float) r, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                        chill(e, 30, 2);
                        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
                        if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 2.5f), FruitKit.info(Element.ICE, 0.05f).withPoise(10));
                    }
                });
            }
            PowerKit.sound(c, at, SoundEvents.POWDER_SNOW_STEP, 2f, 0.5f);
            return true;
        }));

        // Kamakura: a dome of packed snow — ranged attacks are smothered and those who strike up close are frozen.
        f.add(f.ability("kamakura").unlock(25).cooldown(420).stamina(26).anim("harden").exec((u, c) -> {
            CombatState.of(u).buffs.put(DOME, 160 + c.mastery);
            FruitKit.fx(u, "kamakura", u.position(), 2.2f, 0, 0);
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 3.5)) {
                Hits.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.ICE, 1.6f).withPoise(20));
                chill(e, 40, 1);
            }
            PowerKit.sound(c, u.position(), SoundEvents.SNOW_PLACE, 1.4f, 0.6f);
            return true;
        }));

        // Awakening — White Land: a whiteout swallows the area; the user disappears into it and everything freezes solid.
        f.add(f.ability("white_land").unlock(55).cooldown(1000).stamina(85).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 center = u.position();
            double r = 13;
            PowerKit.paintDisc(c.level, center, 9, SNOW, Blocks.SNOW_BLOCK.defaultBlockState());
            u.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 90, 0, false, false));
            for (int i = 0; i < 16; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 5L, () -> {
                    FruitKit.fx(u, "whiteout", center, (float) r, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), r)) {
                        chill(e, 30, 3);
                        PowerKit.loseTrack(u, e);
                        if (n % 3 == 0) Hits.hit(u, e, FruitKit.dmg(c, 2.5f), FruitKit.info(Element.ICE, 0f).withPoise(10));
                    }
                });
            }
            CombatState.of(u).schedule(c.level.getGameTime() + 84, () -> {
                for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), r)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 11), FruitKit.info(Element.ICE, 0.4f).withStun(40).asUnblockable());
                    FruitKit.fx(u, "ice_encase", e.position(), e.getBbWidth(), e.getBbHeight(), 0);
                }
            });
            return true;
        }));

        // Passive: a body of snow — immune to cold and frost, swift and slowly mending on snow and in cold lands.
        // Weakness: heat. Fire hurts more, and while burning the melting body cannot scatter.
        f.tickHook = u -> {
            if (u.getTicksFrozen() > 0) u.setTicksFrozen(0);
            if (u.tickCount % 40 != 0) return;
            BlockPos p = u.blockPosition();
            boolean cold = u.level().getBiome(p).value().coldEnoughToSnow(p) || u.level().getBlockState(p).is(Blocks.SNOW) || u.level().getBlockState(p.below()).is(Blocks.SNOW_BLOCK);
            if (!cold) return;
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0, false, false));
            if (u.getHealth() < u.getMaxHealth()) u.heal(1f);
        };
        f.intangibleHook = (u, a, info) -> !u.isOnFire() && info.element() != Element.FIRE && info.element() != Element.MAGMA;
        f.incomingHook = (u, a, amount, info) -> {
            CombatState s = CombatState.of(u);
            if (info.element() == Element.FIRE || info.element() == Element.MAGMA) return amount * 1.3f;
            if (s.hasBuff(DOME)) {
                if (a != null && a.distanceTo(u) > 4.5) return amount * 0.4f;
                if (a != null) chill(a, 40, 1);
            }
            return amount;
        };
    }

    private YukiFruit() {}
}
