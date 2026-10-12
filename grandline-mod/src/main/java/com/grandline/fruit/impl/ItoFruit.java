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
import net.minecraft.world.phys.Vec3;

/**
 * Ito Ito no Mi — strings. Fine enough to cut steel and strong enough to puppet a body: string bullets, parasite
 * control, walking on strings hooked to the clouds, a burning whip and the shrinking Birdcage.
 */
public final class ItoFruit {
    static final String SKY = "ito_sky";

    public static void init(DevilFruit f) {
        Hits.Info string = FruitKit.info(Element.SLASH, 0.5f);

        // Tamaito: bullets of hardened string fired from the fingertips.
        f.add(f.ability("tamaito").cooldown(30).stamina(12).anim("finger_pistol").exec((u, c) -> {
            for (int i = 0; i < 5; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i * 2L, () -> {
                    if (!u.isAlive()) return;
                    AbilityProjectile p = FruitKit.shoot(c, "string_bullet", 0.25f, 3.0, FruitKit.dmg(c, 2.6f), FruitKit.info(Element.PIERCE, 0.2f).withPoise(8));
                    p.pierce = true;
                    p.life = 25;
                });
            }
            return true;
        }));

        // Parasite: invisible strings seize a body — the victim can barely move and cannot act.
        f.add(f.ability("parasite").unlock(5).cooldown(140).stamina(22).anim("finger_twist").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 18);
            if (t == null) return false;
            int dur = 60 + c.mastery / 2;
            Hits.hit(u, t, FruitKit.dmg(c, 2), FruitKit.info(Element.SLASH, 0f).withStun(dur).withPoise(80).asUnblockable());
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 4));
            t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, dur, 2));
            PowerKit.loseTrack(u, t);
            for (int i = 0; i < dur / 10; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i * 10L, () -> {
                    if (!t.isAlive() || !u.isAlive()) return;
                    // the puppet is walked, stiffly, towards its master
                    if (t.distanceTo(u) > 3) PowerKit.pull(t, u.position(), 0.18, 0);
                    FruitKit.fx(u, "parasite", t.position().add(0, t.getBbHeight(), 0), 0, 0, 0);
                });
            }
            return true;
        }));

        // Sky walk: strings hooked to the clouds let the user stride through the air.
        f.add(f.ability("sky_walk").unlock(10).cooldown(200).stamina(16).anim("cast_up").exec((u, c) -> {
            Vec3 dir = c.look().multiply(1, 0, 1).normalize();
            FruitKit.impulse(u, new Vec3(dir.x * 0.6, 1.1, dir.z * 0.6), false);
            int dur = 160 + c.mastery;
            u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, dur, 0, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.JUMP, dur, 2, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1, false, false));
            CombatState.of(u).buffs.put(SKY, dur);
            FruitKit.fx(u, "sky_walk", u.position().add(0, 2, 0), 0, 0, 0);
            return true;
        }));

        // Goshikito: five strings slash down, slicing everything (and the ground) in front.
        f.add(f.ability("goshikito").unlock(20).cooldown(120).stamina(28).anim("slash_left").exec((u, c) -> {
            FruitKit.cone(c, 7, 50, FruitKit.dmg(c, 12), string.withPoise(50));
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            for (int s = -2; s <= 2; s++)
                for (int d = 2; d <= 7; d++) {
                    Vec3 p = u.position().add(dir.scale(d)).add(side.scale(s * 0.8));
                    BlockPos g = PowerKit.ground(c.level, (int) Math.floor(p.x), (int) Math.floor(u.getY()), (int) Math.floor(p.z));
                    if (g != null && (d + s) % 2 == 0) PowerKit.dig(c.level, g);
                }
            FruitKit.fx(u, "string_lines", u.position().add(0, 1.5, 0), (float) dir.x, (float) dir.z, 7);
            return true;
        }));

        // Overheat: a rope of strings heated red-hot, lashed out like a whip.
        f.add(f.ability("overheat").unlock(30).cooldown(160).stamina(36).anim("whip_crack").exec((u, c) -> {
            Vec3 from = c.eye().add(0, -0.2, 0);
            Vec3 dir = FruitKit.aimDir(c);
            double len = PowerKit.rayLength(u, from, dir, 22);
            for (LivingEntity e : Hits.line(u, from, dir, len, 0.8)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 13), FruitKit.info(Element.SLASH, 1.6f).withPoise(60))) e.igniteForSeconds(5);
            }
            Vec3 end = from.add(dir.scale(len));
            FruitKit.fx(u, "overheat", from, (float) end.x, (float) end.y, (float) end.z);
            PowerKit.sound(c, u.position(), SoundEvents.FIRECHARGE_USE, 0.8f, 1.4f);
            return true;
        }));

        // Awakening — Birdcage: a dome of strings that slowly closes, cutting anything that touches it.
        f.add(f.ability("birdcage").unlock(55).cooldown(1400).stamina(90).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 center = u.position();
            double start = 14 + c.mastery / 20.0, end = 5;
            int pulses = 20;
            for (int i = 0; i < pulses; i++) {
                double r = start - (start - end) * i / (pulses - 1.0);
                CombatState.of(u).schedule(c.level.getGameTime() + i * 10L, () -> {
                    FruitKit.fx(u, "birdcage", center, (float) r, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, center, r + 3)) {
                        double dist = e.position().distanceTo(center);
                        if (dist > r - 0.8) {
                            // pressed against the strings: cut and thrown back inside
                            Hits.hit(u, e, FruitKit.dmg(c, 4), FruitKit.info(Element.SLASH, 0f).withPoise(20));
                            PowerKit.pull(e, center, 0.7, 0.1);
                        } else {
                            Hits.hit(u, e, FruitKit.dmg(c, 1.5f), FruitKit.info(Element.SLASH, 0f).withPoise(5));
                        }
                    }
                });
            }
            return true;
        }));

        // Passive: strings catch every fall.
        f.tickHook = u -> {
            if (u.fallDistance > 3) u.fallDistance = 3;
        };
    }

    private ItoFruit() {}
}
