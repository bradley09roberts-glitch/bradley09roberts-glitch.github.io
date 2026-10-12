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
import net.minecraft.world.phys.Vec3;

/**
 * Pika Pika no Mi — light Logia. Everything happens at the speed of light: instant lasers, light-speed travel and
 * kicks, a barrage of exploding light beads, a sword of solid light that empowers melee, and a rain of light pillars.
 */
public final class PikaFruit {
    static final String SWORD = "pika_sword";

    public static void init(DevilFruit f) {
        Hits.Info light = FruitKit.info(Element.LIGHT, 0.6f);

        // Finger laser: instant, travels until it meets a wall.
        f.add(f.ability("light_beam").cooldown(30).stamina(12).anim("cast_forward").exec((u, c) -> {
            Vec3 from = c.eye().add(0, -0.2, 0);
            Vec3 end = PowerKit.beam(c, from, FruitKit.aimDir(c), 32, 0.6, FruitKit.dmg(c, 7), light.withPoise(25));
            FruitKit.fx(u, "light_beam", from, (float) end.x, (float) end.y, (float) end.z);
            FruitKit.fx(u, "light_burst", end, 1.2f, 0, 0);
            PowerKit.sound(c, end, SoundEvents.BEACON_DEACTIVATE, 0.6f, 2f);
            return true;
        }));

        // Yata no Kagami: become light and reappear far away.
        f.add(f.ability("yata_no_kagami").unlock(5).cooldown(60).stamina(14).anim("dash").exec((u, c) -> {
            Vec3 start = u.position().add(0, 1, 0);
            Vec3 end = FruitKit.blink(u, c.look(), 30 + c.mastery / 10.0);
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 6;
            FruitKit.fx(u, "light_trail", start, (float) end.x, (float) end.y + 0.5f, (float) end.z);
            PowerKit.sound(c, end, SoundEvents.BEACON_ACTIVATE, 0.5f, 2f);
            return true;
        }));

        // Light-speed kick: arrive next to the target before it can react.
        f.add(f.ability("light_speed_kick").unlock(15).cooldown(70).stamina(20).anim("kick_high").exec((u, c) -> {
            Hits.Info kick = FruitKit.info(Element.LIGHT, 3.2f).withPoise(70);
            Vec3 start = u.position().add(0, 1, 0);
            LivingEntity t = PowerKit.target(c, 22);
            if (t != null) {
                Vec3 dir = t.position().subtract(u.position()).multiply(1, 0, 1);
                if (dir.lengthSqr() < 1e-4) dir = PowerKit.flatAim(c);
                dir = dir.normalize();
                double dist = Math.max(0, u.position().distanceTo(t.position()) - 1.4);
                if (dist > 0.2) FruitKit.blink(u, dir, dist);
                Hits.hit(u, t, FruitKit.dmg(c, 12), kick);
                FruitKit.fx(u, "light_burst", t.position().add(0, 1, 0), 1.6f, 0, 0);
            } else {
                FruitKit.blink(u, c.look(), 14);
                for (LivingEntity e : Hits.cone(u, 3.5, 90)) Hits.hit(u, e, FruitKit.dmg(c, 12), kick);
            }
            FruitKit.fx(u, "light_trail", start, (float) u.getX(), (float) u.getY() + 1, (float) u.getZ());
            return true;
        }));

        // Yasakani no Magatama: a storm of exploding light beads.
        f.add(f.ability("yasakani_no_magatama").unlock(25).cooldown(140).stamina(32).anim("rapid_fire").exec((u, c) -> {
            for (int i = 0; i < 18; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i * 2L, () -> {
                    if (!u.isAlive()) return;
                    AbilityProjectile p = FruitKit.shoot(c, "light_bead", 0.35f, 3.0, FruitKit.dmg(c, 3), light.withPoise(10));
                    p.setDeltaMovement(p.getDeltaMovement().add((u.getRandom().nextDouble() - 0.5) * 0.45, (u.getRandom().nextDouble() - 0.5) * 0.3, (u.getRandom().nextDouble() - 0.5) * 0.45));
                    p.explosion = 1.3f;
                    p.life = 40;
                });
            }
            return true;
        }));

        // Ama no Murakumo: a sword of light — an immediate wide cut, then empowered melee for a while.
        f.add(f.ability("ama_no_murakumo").unlock(40).cooldown(600).stamina(30).anim("wide_slash").exec((u, c) -> {
            for (LivingEntity e : Hits.cone(u, 5.5, 140)) Hits.hit(u, e, FruitKit.dmg(c, 9), FruitKit.info(Element.LIGHT, 1.2f).withPoise(40));
            CombatState.of(u).buffs.put(SWORD, 20 * (15 + c.mastery / 10));
            FruitKit.fx(u, "light_sword", u.position().add(0, 1, 0), 5.5f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
            return true;
        }));

        // Awakening — Amaterasu: pillars of light rain down on an area.
        f.add(f.ability("amaterasu").unlock(60).cooldown(900).stamina(80).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 50);
            FruitKit.fx(u, "light_burst", at.add(0, 14, 0), 6f, 0, 0);
            for (LivingEntity e : PowerKit.foes(u, at, 10)) e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            for (int i = 0; i < 16; i++) {
                double ox = (u.getRandom().nextDouble() - 0.5) * 16, oz = (u.getRandom().nextDouble() - 0.5) * 16;
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + 10 + i * 3L, () -> {
                    Vec3 p = at.add(ox, 0, oz);
                    FruitKit.fx(u, "light_pillar", p, 2.8f, 16, 0);
                    FruitKit.aoe(c, p.add(0, 1, 0), 2.8, FruitKit.dmg(c, 9), FruitKit.info(Element.LIGHT, 1.4f).withPoise(45).asUnblockable());
                    if (n % 3 == 0) TerrainChanges.crater(c.level, BlockPos.containing(p), 2, u);
                    PowerKit.sound(c, p, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.8f);
                });
            }
            return true;
        }));

        // Passive: light body — faster while sprinting; the light sword empowers physical strikes.
        f.tickHook = u -> {
            if (u.tickCount % 10 == 0 && u.isSprinting()) u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 15, 1, false, false));
        };
        f.outgoingHook = (u, v, amount, info) -> CombatState.of(u).hasBuff(SWORD) && info.element().physical() ? amount * 1.6f : amount;
    }

    private PikaFruit() {}
}
