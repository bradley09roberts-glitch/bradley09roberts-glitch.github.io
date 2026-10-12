package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Kiri Kiri no Mi — ORIGINAL mist Logia. The user becomes sea mist: jets of condensed water (Water element, so they
 * reach even other Logia users), vanishing into fog, fog banks and sea fog that hide a whole crew, and mist that
 * condenses inside an enemy's lungs.
 * Weakness: wind scatters mist — Wind attacks always connect and deal 50% more. Being water already, the mist body
 * shrugs off water attacks, but the sea itself still drains it like any fruit user.
 */
public final class KiriFruit {
    static final String ACTED = "kiri_acted";

    /** Briefly hide allies (and the user) inside the mist. */
    static void shroud(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false));
    }

    public static void init(DevilFruit f) {
        Hits.Info water = FruitKit.info(Element.WATER, 0.6f);

        // Mist lance: a jet of condensed mist — real water, so it splashes even Logia bodies.
        f.add(f.ability("mist_lance").cooldown(18).stamina(9).anim("punch_extend").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "kiri_lance", 0.5f, 2.6, FruitKit.dmg(c, 6), water.withPoise(20));
            p.life = 30;
            p.onHit = (pr, hit, pos) -> {
                if (hit != null) hit.clearFire();
            };
            return true;
        }));

        // Vanishing mist: burst into mist — enemies around are blinded and lose the user, who drifts away unseen.
        f.add(f.ability("vanishing_mist").unlock(5).cooldown(160).stamina(16).anim("focus").exec((u, c) -> {
            Vec3 start = u.position();
            for (LivingEntity e : PowerKit.foes(u, start.add(0, 1, 0), 6)) {
                Hits.hit(u, e, FruitKit.dmg(c, 2), water.withPoise(10));
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0));
                PowerKit.loseTrack(u, e);
            }
            shroud(u, 70);
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 8;
            FruitKit.blink(u, PowerKit.flatAim(c).scale(-1), 5);
            FruitKit.fx(u, "kiri_puff", start.add(0, 1, 0), 3, 0, 0);
            PowerKit.sound(c, start, SoundEvents.FIRE_EXTINGUISH, 1f, 0.6f);
            return true;
        }));

        // Fog bank: a thick fog rolls over an area — enemies inside are blinded, slowed and lose track; allies vanish.
        f.add(f.ability("fog_bank").unlock(15).cooldown(300).stamina(26).anim("cast_forward").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 20);
            double r = 7 + c.mastery / 25.0;
            OriginalKit.pulses(c, 0, 10, 16, n -> {
                FruitKit.fx(u, "kiri_fog", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 1));
                    PowerKit.loseTrack(u, e);
                    if (n % 4 == 0) Hits.hit(u, e, FruitKit.dmg(c, 1.5f), water.withPoise(5));
                }
                for (LivingEntity a : OriginalKit.allies(u, at.add(0, 1, 0), r, true)) shroud(a, 25);
            });
            return true;
        }));

        // Drowning mist: mist seeps into the target's lungs and condenses — they cough and drown on dry land.
        // Those who breathe water (Fish-Men, Merfolk...) only feel the chill.
        f.add(f.ability("drowning_mist").unlock(20).cooldown(200).stamina(26).anim("palm_strike").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 14);
            if (t == null) return false;
            boolean gills = t.canBreatheUnderwater() || ShioFruit.aquatic(t);
            OriginalKit.pulses(c, 0, 10, 6, n -> {
                if (!t.isAlive()) return;
                t.setAirSupply(Math.max(-19, t.getAirSupply() - 60));
                Hits.hit(u, t, FruitKit.dmg(c, gills ? 1.5f : 3.5f), FruitKit.info(Element.WATER, 0f).withPoise(10).asUnblockable());
                if (!gills) {
                    t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 1));
                    t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0));
                }
                FruitKit.fx(u, "kiri_drown", t.getEyePosition(), t.getBbWidth(), 0, 0);
            });
            PowerKit.sound(c, t.position(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.8f, 0.6f);
            return true;
        }));

        // Sea fog: a vast sea fog settles around the user for a long while — the whole crew (and their ship) vanish
        // from enemy eyes; enemies wandering inside are left in the dark.
        f.add(f.ability("sea_fog").unlock(30).cooldown(1200).stamina(36).anim("cast_up").exec((u, c) -> {
            double r = 16;
            OriginalKit.pulses(c, 0, 20, 15 + c.mastery / 10, n -> {
                Vec3 center = u.position();
                FruitKit.fx(u, "kiri_fog", center, (float) r, 1, 0);
                for (LivingEntity a : OriginalKit.allies(u, center.add(0, 1, 0), r, true)) shroud(a, 30);
                for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), r)) {
                    e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 30, 0));
                    PowerKit.loseTrack(u, e);
                    if (n == 0) Hits.hit(u, e, FruitKit.dmg(c, 2), water.withPoise(5));
                }
            });
            PowerKit.sound(c, u.position(), SoundEvents.WEATHER_RAIN, 1.5f, 0.5f);
            return true;
        }));

        // Awakening — White Abyss: a dome of mist condenses into a freezing downpour, then freezes solid around
        // everyone inside. Allies are cooled and their fires put out.
        f.add(f.ability("white_abyss").unlock(55).cooldown(1000).stamina(80).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 26);
            double r = 10;
            OriginalKit.pulses(c, 0, 5, 16, n -> {
                FruitKit.fx(u, "kiri_downpour", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 2.5f), water.withPoise(10));
                    e.clearFire();
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2));
                }
                for (LivingEntity a : OriginalKit.allies(u, at.add(0, 1, 0), r, true)) a.clearFire();
            });
            CombatState.of(u).schedule(c.level.getGameTime() + 84, () -> {
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 10), FruitKit.info(Element.ICE, 0f).withStun(50).withPoise(80).asUnblockable());
                    e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() + 100));
                }
                FruitKit.fx(u, "kiri_freeze", at, (float) r, 0, 0);
                PowerKit.sound(c, at, SoundEvents.GLASS_BREAK, 1.5f, 0.6f);
            });
            return true;
        }));

        // Passive: mist body. Wind scatters it (always connects, +50%); crouching still, the user fades into mist.
        f.intangibleHook = (u, a, info) -> info.element() != Element.WIND;
        f.incomingHook = (u, a, amount, info) -> info.element() == Element.WIND ? amount * 1.5f : amount;
        f.outgoingHook = (u, v, amount, info) -> {
            CombatState.of(u).buffs.put(ACTED, 40);
            return amount;
        };
        f.tickHook = u -> {
            if (u.tickCount % 10 != 0) return;
            if (u.isShiftKeyDown() && !CombatState.of(u).hasBuff(ACTED) && u.getDeltaMovement().horizontalDistanceSqr() < 0.01) shroud(u, 15);
        };
    }

    private KiriFruit() {}
}
