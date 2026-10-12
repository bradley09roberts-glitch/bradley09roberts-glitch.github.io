package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.ship.ShipEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Abura Abura no Mi — ORIGINAL oil Special Paramecia. The user's body is slick oil: oil globs that leave a target
 * "oiled", slicks where nobody keeps their footing, a spark that sets every oiled thing ablaze (oiled creatures take
 * 75% more from ANY fire — Mera users love an Abura crewmate), a body too slippery to hit, and oil bombs that coat enemy
 * ships so the fire can eat their hulls.
 * Passive: oily skin — melee blows slide off (-20%). Weakness: the user burns too — fire deals 50% more to them.
 */
public final class AburaFruit {
    static final String SLIPPERY = "abura_slippery";
    /** Oiled creatures and ships -> game time the oil wears off. */
    static final Map<Entity, Long> OILED = Collections.synchronizedMap(new WeakHashMap<>());

    /** An oil slick on the ground or the sea. */
    record Slick(Vec3 at, double r, long until) {}

    static final Map<LivingEntity, List<Slick>> SLICKS = Collections.synchronizedMap(new WeakHashMap<>());

    public static boolean oiled(Entity e) {
        Long t = OILED.get(e);
        if (t == null) return false;
        if (t <= e.level().getGameTime()) {
            OILED.remove(e);
            return false;
        }
        return true;
    }

    public static void oil(Entity e, int ticks) {
        OILED.put(e, e.level().getGameTime() + ticks);
        if (e instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0));
    }

    /** Lay a slick: enemies inside slip and slide (guards break, footing lost) and get oiled. */
    static void slick(com.grandline.combat.AbilityContext c, Vec3 at, double r, int ticks) {
        LivingEntity u = c.user;
        long now = c.level.getGameTime(), until = now + ticks;
        List<Slick> mine = SLICKS.computeIfAbsent(u, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (mine) {
            mine.removeIf(sl -> sl.until <= now);
            mine.add(new Slick(at, r, until));
        }
        OriginalKit.pulses(c, 0, 10, ticks / 10, n -> {
            if (n % 3 == 0) FruitKit.fx(u, "abura_slick", at, (float) r, 0, 0);
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 0.5, 0), r)) {
                if (Math.abs(e.getY() - at.y) > 2) continue;
                oil(e, 100);
                CombatState.of(e).blocking = false;
                if (e.getRandom().nextInt(3) == 0) {
                    FruitKit.impulse(e, new Vec3(e.getRandom().nextGaussian() * 0.35, 0.05, e.getRandom().nextGaussian() * 0.35), false);
                    CombatState.of(e).staggeredUntil = Math.max(CombatState.of(e).staggeredUntil, c.level.getGameTime() + 10);
                }
            }
            for (ShipEntity s : OriginalKit.ships(u, at, r)) if (!OriginalKit.friendly(u, s)) oil(s, 100);
        });
    }

    public static void init(DevilFruit f) {
        Hits.Info glob = FruitKit.info(Element.BLUNT, 0.4f);
        Hits.Info fire = FruitKit.info(Element.FIRE, 0.6f);

        // Oil shot: a glob of oil — the target (or a ship it splashes) is oiled for a while.
        f.add(f.ability("oil_shot").cooldown(18).stamina(8).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "abura_glob", 0.5f, 2.0, FruitKit.dmg(c, 4), glob.withPoise(15));
            p.gravity = 0.015;
            p.life = 40;
            p.onHit = (pr, hit, pos) -> {
                if (hit != null) oil(hit, 200);
                for (ShipEntity s : OriginalKit.ships(u, pos, 2)) if (!OriginalKit.friendly(u, s)) oil(s, 400);
            };
            return true;
        }));

        // Oil slick: oil spreads over the ground (or sea) at a point — nobody keeps their footing on it.
        f.add(f.ability("oil_slick").unlock(5).cooldown(160).stamina(18).anim("cast_ground").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 18);
            slick(c, at, 4 + c.mastery / 40.0, 240);
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 0.5, 0), 4)) Hits.hit(u, e, FruitKit.dmg(c, 2), glob.withPoise(30));
            PowerKit.sound(c, at, SoundEvents.HONEY_BLOCK_SLIDE, 1.5f, 0.5f);
            return true;
        }));

        // Ignite: a spark — every oiled enemy (and oiled ship) within reach bursts into flame, and the user's slicks
        // become pools of fire for a few seconds.
        f.add(f.ability("ignite").unlock(12).cooldown(140).stamina(22).anim("finger_pistol").exec((u, c) -> {
            long now = c.level.getGameTime();
            int burned = 0;
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 20)) {
                boolean slick = oiled(e);
                if (!slick && e.distanceTo(u) > 4 + u.getScale()) continue;
                Hits.hit(u, e, FruitKit.dmg(c, slick ? 8 : 4), fire.withPoise(30));
                e.igniteForSeconds(slick ? 6 : 2);
                FruitKit.fx(u, "abura_flare", e.position().add(0, 1, 0), e.getBbWidth() + 0.5f, 0, 0);
                burned++;
            }
            for (ShipEntity s : OriginalKit.ships(u, u.position(), 30)) {
                if (!oiled(s) || OriginalKit.friendly(u, s)) continue;
                OriginalKit.pulses(c, 0, 20, 5, n -> OriginalKit.wreck(u, s, 12 + c.mastery / 10f));
                FruitKit.fx(u, "abura_flare", s.position().add(0, 1, 0), s.cls.beam, 1, 0);
                OILED.remove(s);
            }
            List<Slick> mine = SLICKS.getOrDefault(u, List.of());
            List<Slick> live = new ArrayList<>();
            synchronized (mine) {
                mine.removeIf(sl -> sl.until <= now);
                live.addAll(mine);
                mine.clear();
            }
            for (Slick sl : live) {
                OriginalKit.pulses(c, 0, 10, 8, n -> {
                    FruitKit.fx(u, "abura_fire", sl.at, (float) sl.r, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, sl.at.add(0, 0.5, 0), sl.r)) {
                        Hits.hit(u, e, FruitKit.dmg(c, 2.5f), fire.withPoise(10));
                        e.igniteForSeconds(3);
                    }
                    if (n % 2 == 0) for (ShipEntity s : OriginalKit.ships(u, sl.at, sl.r)) if (!OriginalKit.friendly(u, s)) OriginalKit.wreck(u, s, 8);
                });
            }
            PowerKit.sound(c, u.position(), SoundEvents.FIRECHARGE_USE, 1.2f, 0.9f + Math.min(0.5f, burned * 0.1f));
            return true;
        }));

        // Slippery body: the user coats themself in oil — almost every blow slides off and they skate around quickly.
        f.add(f.ability("slippery_body").unlock(20).cooldown(400).stamina(18).anim("harden").exec((u, c) -> {
            int dur = 160 + c.mastery;
            CombatState.of(u).buffs.put(SLIPPERY, dur);
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 6;
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1, false, true));
            // slick hands send anyone grappling the user sprawling
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 3)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 3), glob.withStun(15).withPoise(40))) oil(e, 100);
            }
            FruitKit.fx(u, "abura_coat", u.position().add(0, 1, 0), u.getBbWidth(), u.getBbHeight(), 0);
            PowerKit.sound(c, u.position(), SoundEvents.HONEY_BLOCK_SLIDE, 1f, 1.2f);
            return true;
        }));

        // Oil barrage: five oil bombs lobbed across an area — each bursts into a small slick, oiling whoever (and
        // whichever ship) it lands on.
        f.add(f.ability("oil_barrage").unlock(30).cooldown(260).stamina(30).anim("throw").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 26);
            for (int i = 0; i < 5; i++) {
                Vec3 spot = i == 0 ? at : at.add((u.getRandom().nextDouble() - 0.5) * 9, 0, (u.getRandom().nextDouble() - 0.5) * 9);
                CombatState.of(u).schedule(c.level.getGameTime() + 4 + i * 4L, () -> {
                    slick(c, spot, 2.5, 200);
                    for (LivingEntity e : PowerKit.foes(u, spot.add(0, 1, 0), 2.5)) Hits.hit(u, e, FruitKit.dmg(c, 4), glob.withPoise(20));
                    FruitKit.fx(u, "abura_splash", spot, 2.5f, 0, 0);
                });
            }
            PowerKit.sound(c, u.position(), SoundEvents.SPLASH_POTION_BREAK, 1.2f, 0.6f);
            return true;
        }));

        // Awakening — Sea of Flames: a vast slick floods an area and erupts at once into a sea of fire; enemy ships
        // caught in it burn.
        f.add(f.ability("sea_of_flames").unlock(55).cooldown(1100).stamina(85).awakened().anim("cast_ground").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 26);
            double r = 10;
            OriginalKit.pulses(c, 0, 5, 16, n -> {
                FruitKit.fx(u, n == 0 ? "abura_slick" : "abura_fire", at, (float) r, 1, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    oil(e, 60);
                    if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 3), fire.withPoise(12));
                    e.igniteForSeconds(4);
                }
                if (n % 4 == 0) for (ShipEntity s : OriginalKit.ships(u, at, r)) if (!OriginalKit.friendly(u, s)) OriginalKit.wreck(u, s, 18 + c.mastery / 6f);
            });
            PowerKit.sound(c, at, SoundEvents.FIRECHARGE_USE, 2f, 0.5f);
            return true;
        }));

        // Passive: oily skin — physical blows slide off (-20%, -75% while Slippery Body is up). The oil burns: fire
        // deals the user 50% more.
        f.incomingHook = (u, a, amount, info) -> {
            if (info.element() == Element.FIRE || info.element() == Element.MAGMA) return amount * 1.5f;
            if (!info.element().physical()) return amount;
            return amount * (CombatState.of(u).hasBuff(SLIPPERY) ? 0.25f : 0.8f);
        };
    }

    private AburaFruit() {}
}
