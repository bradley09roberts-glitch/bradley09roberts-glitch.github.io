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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Nikyu Nikyu no Mi — paw pads that repel anything they touch: air (paw-shaped pressure bubbles), people (sent flying
 * far across the sea), the user (instant travel) and even pain and fatigue, pulled out of a body as a bubble.
 */
public final class NikyuFruit {
    static final String DEFLECT_CD = "nikyu_deflect_cd";
    /** Pain and fatigue extracted and kept as a bubble (added to Ursus Shock). */
    static final Map<LivingEntity, Float> PAIN = Collections.synchronizedMap(new WeakHashMap<>());

    public static void init(DevilFruit f) {
        // Pad Ho: a paw-shaped bubble of compressed air that slams the target away.
        f.add(f.ability("pad_ho").cooldown(24).stamina(10).anim("palm_strike").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "paw_bubble", 1.0f, 2.4, FruitKit.dmg(c, 8), FruitKit.info(Element.IMPACT, 3.2f).withPoise(40));
            p.life = 30;
            PowerKit.sound(c, u.position(), SoundEvents.PUFFER_FISH_BLOW_OUT, 1.2f, 0.6f);
            return true;
        }));

        // Tsuppari Pad Ho: a rapid barrage of paw bubbles.
        f.add(f.ability("tsuppari_pad_ho").unlock(8).cooldown(80).stamina(22).anim("rapid_fire").exec((u, c) -> {
            for (int i = 0; i < 6; i++) {
                int k = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 3L, () -> {
                    if (!u.isAlive()) return;
                    Vec3 d = PowerKit.yaw(FruitKit.aimDir(c), (k % 3 - 1) * 6);
                    AbilityProjectile p = AbilityProjectile.shoot(u, "paw_bubble", 0.7f, u.getEyePosition().add(d.scale(0.8)), d, 2.6,
                            FruitKit.dmg(c, 4), FruitKit.info(Element.IMPACT, 1.6f).withPoise(18));
                    p.life = 26;
                });
            }
            return true;
        }));

        // Paw Travel: the user repels their own body and is gone in an instant.
        f.add(f.ability("paw_travel").unlock(15).cooldown(100).stamina(18).anim("dash").exec((u, c) -> {
            Vec3 from = u.position().add(0, 1, 0);
            Vec3 dir = c.look();
            if (c.target != null && c.target.isAlive() && u.distanceTo(c.target) < 4) dir = PowerKit.flatAim(c).scale(-1).add(0, 0.2, 0); // NPCs leap away
            FruitKit.blink(u, dir, 14 + c.mastery / 10.0);
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 6;
            FruitKit.fx(u, "paw_burst", from, (float) u.getX(), (float) u.getY() + 1, (float) u.getZ());
            PowerKit.sound(c, u.position(), SoundEvents.PUFFER_FISH_BLOW_OUT, 1f, 1.4f);
            return true;
        }));

        // Where would you like to travel? A touch of the paw sends the target flying far over the horizon.
        f.add(f.ability("where_to_travel").unlock(25).cooldown(260).stamina(30).anim("palm_strike").exec((u, c) -> {
            LivingEntity t = CanonKit.melee(c, 4.5);
            if (t == null) return false;
            if (PowerKit.hit(u, t, FruitKit.dmg(c, 6), FruitKit.info(Element.IMPACT, 0f).withPoise(50).asUnblockable())) {
                Vec3 away = PowerKit.flatAim(c);
                FruitKit.impulse(t, away.scale(4.2).add(0, 1.4, 0), true);
                t.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0));
                PowerKit.loseTrack(t, u);
                CanonKit.forget(t, 4);
            }
            FruitKit.fx(u, "paw_burst", t.position().add(0, 1, 0), (float) t.getX(), (float) t.getY() + 6, (float) t.getZ());
            PowerKit.sound(c, t.position(), SoundEvents.PUFFER_FISH_BLOW_OUT, 1.4f, 0.4f);
            return true;
        }));

        // Pain Extraction: pull the pain and fatigue out of the user's (and allies') bodies as a bubble.
        f.add(f.ability("pain_extraction").unlock(30).cooldown(500).stamina(10).anim("focus").exec((u, c) -> {
            float healed = 0;
            for (LivingEntity e : Hits.sphere(u, u.position().add(0, 1, 0), 6)) {
                if (Hits.hostileTo(u, e)) continue;
                float h = Math.min(e.getMaxHealth() - e.getHealth(), 4);
                e.heal(h);
                CanonKit.cleanse(e);
                healed += h;
            }
            float self = Math.min(u.getMaxHealth() - u.getHealth(), 6 + (u.getMaxHealth() - u.getHealth()) * 0.2f);
            u.heal(self);
            CanonKit.cleanse(u);
            healed += self;
            PAIN.merge(u, healed * 1.5f + 4, (a, b) -> Math.min(40f, a + b));
            FruitKit.fx(u, "pain_bubble", u.position().add(0, 1.4, 0), 1f, 0, 0);
            return true;
        }));

        // Awakening — Ursus Shock: air compressed into a giant paw that detonates like a bomb (plus any stored pain).
        f.add(f.ability("ursus_shock").unlock(55).cooldown(1000).stamina(85).awakened().anim("double_palm").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 24);
            float pain = PAIN.getOrDefault(u, 0f);
            PAIN.remove(u);
            FruitKit.fx(u, "ursus_charge", at.add(0, 1, 0), 3, 0, 0);
            CombatState.of(u).schedule(c.level.getGameTime() + 20, () -> {
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 8)) {
                    if (PowerKit.hit(u, e, FruitKit.dmg(c, 16) + pain, FruitKit.info(Element.IMPACT, 4.5f).withPoise(120).asUnblockable()))
                        PowerKit.push(e, at, 1.6, 0.8);
                }
                TerrainChanges.crater(c.level, BlockPos.containing(at), 3, u);
                FruitKit.fx(u, "ursus_shock", at.add(0, 1, 0), 8, 0, 0);
                PowerKit.sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.5f);
            });
            return true;
        }));

        // Passive: paw pads repel whatever is shot at the user from the front (one deflection every second).
        f.tickHook = u -> {
            CombatState s = CombatState.of(u);
            if (s.hasBuff(DEFLECT_CD)) return;
            Vec3 look = u.getLookAngle();
            Vec3 center = u.getBoundingBox().getCenter();
            for (Projectile p : PowerKit.hostileProjectiles(u, center, 2.2)) {
                Vec3 to = p.position().subtract(center);
                if (to.lengthSqr() > 1e-4 && to.normalize().dot(look) < 0.2) continue;
                PowerKit.reflect(u, p);
                s.buffs.put(DEFLECT_CD, 20);
                return;
            }
        };
    }

    private NikyuFruit() {}
}
