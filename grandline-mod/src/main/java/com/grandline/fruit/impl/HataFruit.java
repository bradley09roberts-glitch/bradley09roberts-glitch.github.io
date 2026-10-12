package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hata Hata no Mi — ORIGINAL flag Paramecia (wave 4). The user raises flags out of thin air, and in the New World
 * whatever flag flies over a place is its law: a flagpole thrust like a spear, a Jolly Roger branded on a foe so the
 * whole crew can find it, a planted banner whose territory strengthens friends and weighs on enemies, a snapping flag
 * that blows everything back, the white flag of surrender forced over enemies, and at awakening an Emperor's territory.
 * Passive: standing in their own territory the user takes 20% less; a branded enemy takes 20% more from everyone
 * (see {@link Wave4FruitEvents}).
 */
public final class HataFruit {
    /** The banner each user has planted: centre, radius and the game time it falls. */
    record Banner(Vec3 at, double r, long until) {}
    static final Map<LivingEntity, Banner> BANNERS = Collections.synchronizedMap(new WeakHashMap<>());

    /** Branded bodies and the game time each brand fades (a plain map, so damage checks never create combat state). */
    static final Map<LivingEntity, Long> BRANDED = Collections.synchronizedMap(new WeakHashMap<>());

    /** Brand an enemy with the user's Jolly Roger. */
    public static void brand(LivingEntity e, int ticks) {
        BRANDED.put(e, e.level().getGameTime() + ticks);
        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
    }

    public static boolean branded(LivingEntity e) {
        if (BRANDED.isEmpty()) return false;
        Long until = BRANDED.get(e);
        if (until == null) return false;
        if (until > e.level().getGameTime()) return true;
        BRANDED.remove(e);
        return false;
    }

    static boolean inTerritory(LivingEntity u) {
        Banner b = BANNERS.get(u);
        return b != null && b.until > u.level().getGameTime() && u.position().distanceTo(b.at) <= b.r;
    }

    /** A territory: pulses once a second, strengthening allies and weighing on enemies inside. */
    static void territory(com.grandline.combat.AbilityContext c, Vec3 at, double r, int seconds, int allyAmp, float pressure) {
        LivingEntity u = c.user;
        BANNERS.put(u, new Banner(at, r, c.level.getGameTime() + seconds * 20L));
        OriginalKit.pulses(c, 0, 20, seconds, n -> {
            FruitKit.fx(u, "hata_territory", at, (float) r, allyAmp, 0);
            for (LivingEntity a : OriginalKit.allies(u, at.add(0, 1, 0), r, true)) {
                a.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 30, allyAmp, false, true));
                a.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, false, true));
                if (n % 3 == 0) a.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, true));
            }
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0));
                if (pressure > 0) Hits.hit(u, e, pressure, FruitKit.info(Element.PHYSICAL, 0f).withPoise(10));
            }
        });
    }

    public static void init(DevilFruit f) {
        Hits.Info pole = FruitKit.info(Element.PIERCE, 1.2f);

        // Flagpole thrust: a flagpole springs out of the hand like a spear and runs through the line in front.
        f.add(f.ability("flagpole_thrust").cooldown(20).stamina(9).anim("spear_thrust").exec((u, c) -> {
            Vec3 from = c.eye().add(0, -0.3, 0);
            Vec3 dir = FruitKit.aimDir(c);
            double len = PowerKit.rayLength(u, from, dir, 6 + u.getScale());
            for (LivingEntity e : Hits.line(u, from, dir, len, 0.7)) if (Hits.hostileTo(u, e)) Hits.hit(u, e, FruitKit.dmg(c, 7), pole.withPoise(30));
            Vec3 end = from.add(dir.scale(len));
            FruitKit.fx(u, "hata_pole", from, (float) end.x, (float) end.y, (float) end.z);
            PowerKit.sound(c, from, SoundEvents.TRIDENT_THROW.value(), 1f, 1.1f);
            return true;
        }));

        // Jolly Roger: the user's flag is branded onto the target — for twelve seconds it glows and takes 20% more
        // damage from everyone.
        f.add(f.ability("jolly_roger").unlock(5).cooldown(160).stamina(14).anim("palm_strike").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 12);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 4), pole.withPoise(20));
            brand(t, 240 + c.mastery * 2);
            FruitKit.fx(u, "hata_brand", t.position().add(0, t.getBbHeight() + 0.6, 0), 0, 0, 0);
            PowerKit.sound(c, t.position(), SoundEvents.ANVIL_PLACE, 0.6f, 1.6f);
            return true;
        }));

        // Plant the flag: a banner is planted at the user's feet — for fifteen seconds its territory strengthens and
        // shields allies and weighs on enemies; the user takes less inside it.
        f.add(f.ability("plant_the_flag").unlock(12).cooldown(500).stamina(24).anim("ground_slam").exec((u, c) -> {
            Vec3 at = u.position();
            territory(c, at, 7, 15, 0, 0);
            // driving the pole in shakes the ground around it
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 0.5, 0), 3)) Hits.hit(u, e, FruitKit.dmg(c, 4), FruitKit.info(Element.BLUNT, 1f).withPoise(20));
            FruitKit.fx(u, "hata_flag", at, 0, 0, 0);
            PowerKit.sound(c, at, SoundEvents.RAID_HORN.value(), 0.6f, 1.4f);
            return true;
        }));

        // Flag wave: a huge flag snaps open — the gust blows enemies away and knocks projectiles out of the air.
        f.add(f.ability("flag_wave").unlock(20).cooldown(120).stamina(18).anim("wide_slash").exec((u, c) -> {
            for (LivingEntity e : Hits.cone(u, 8, 100)) {
                if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 6), FruitKit.info(Element.WIND, 1.8f).withPoise(35)))
                    PowerKit.push(e, u.position(), 1.3, 0.35);
            }
            Vec3 front = u.position().add(0, 1, 0).add(u.getLookAngle().scale(4));
            for (Projectile p : PowerKit.hostileProjectiles(u, front, 5)) p.discard();
            FruitKit.fx(u, "hata_wave", c.eye(), (float) c.look().x, (float) c.look().y, (float) c.look().z);
            PowerKit.sound(c, u.position(), SoundEvents.ENDER_DRAGON_FLAP, 1.5f, 1.2f);
            return true;
        }));

        // White flag: white flags are forced up over every enemy around — they lose heart: they drop their target,
        // their blows grow weak and their feet heavy.
        f.add(f.ability("white_flag").unlock(30).cooldown(400).stamina(26).anim("cast_up").exec((u, c) -> {
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 8)) {
                Hits.hit(u, e, FruitKit.dmg(c, 4), pole.withStun(30).withPoise(40));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 2));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                if (e instanceof Mob m) m.setTarget(null);
                CombatState.of(e).blocking = false;
                FruitKit.fx(u, "hata_white", e.position().add(0, e.getBbHeight() + 0.8, 0), 0, 0, 0);
            }
            PowerKit.sound(c, u.position(), SoundEvents.ENDER_DRAGON_FLAP, 1f, 1.6f);
            return true;
        }));

        // Awakening — Emperor's Territory: the user's flag flies over a whole area for twenty seconds. Allies inside are
        // far stronger and mend; every enemy is branded and ground down by the pressure of the territory.
        f.add(f.ability("emperors_territory").unlock(55).cooldown(1200).stamina(85).awakened().anim("roar").exec((u, c) -> {
            Vec3 at = u.position();
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 14)) {
                brand(e, 400);
                Hits.hit(u, e, FruitKit.dmg(c, 8), FruitKit.info(Element.PHYSICAL, 1.2f).withPoise(50).asUnblockable());
            }
            territory(c, at, 14, 20, 1, FruitKit.dmg(c, 2));
            FruitKit.fx(u, "hata_flag", at, 1, 0, 0);
            PowerKit.sound(c, at, SoundEvents.RAID_HORN.value(), 1.5f, 0.8f);
            return true;
        }));

        // Passive: under their own flag the user takes 20% less (branded enemies' +20% is in Wave4FruitEvents).
        f.incomingHook = (u, a, amount, info) -> inTerritory(u) ? amount * 0.8f : amount;
    }

    private HataFruit() {}
}
