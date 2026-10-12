package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.Zoan;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Itachi Itachi no Mi, Model: Raiju — ORIGINAL Mythical Zoan (wave 4). The user becomes the thunder beast of the
 * White Sea's storm clouds, the creature that falls to earth inside a lightning bolt: claws that arc to the next foe,
 * a pounce along a bolt, balls of lightning, a leap into the clouds that comes down as a strike, a bristling static
 * coat, and at awakening a rampage that leaps from enemy to enemy as lightning.
 * Passive: in form, immune to lightning; the static coat shocks anyone who strikes it up close; in rain or a
 * thunderstorm the beast is faster and deals 15% more.
 * Weakness: standard Zoan.
 */
public final class RaijuFruit {
    static final String STATIC = "raiju_static";
    private static boolean shocking;

    static boolean stormy(LivingEntity u) {
        return u.level().isRainingAt(u.blockPosition().above()) || u.level().isThundering();
    }

    /** An arc jumps from a struck body to the nearest other enemy. */
    static void arc(LivingEntity u, LivingEntity from, float dmg, Hits.Info info) {
        LivingEntity next = null;
        for (LivingEntity e : PowerKit.foes(u, from.position().add(0, 1, 0), 5)) if (e != from && (next == null || e.distanceToSqr(from) < next.distanceToSqr(from))) next = e;
        if (next == null) return;
        Hits.hit(u, next, dmg, info);
        Vec3 a = from.position().add(0, from.getBbHeight() * 0.6, 0), b = next.position().add(0, next.getBbHeight() * 0.6, 0);
        FruitKit.fx(u, "raiju_arc", a, (float) b.x, (float) b.y, (float) b.z);
    }

    public static void init(DevilFruit f) {
        Hits.Info bolt = FruitKit.info(Element.LIGHTNING, 0.6f);
        PowerKit.zoanTransform(f);

        // Thunder claw: a raking claw full of lightning; the charge arcs on to the next enemy.
        f.add(f.ability("thunder_claw").cooldown(20).stamina(10).anim("claw_flurry").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            for (LivingEntity e : Hits.cone(u, 4 + u.getScale(), 100)) {
                if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 7), bolt.withPoise(30))) arc(u, e, FruitKit.dmg(c, 3), bolt.withPoise(10));
            }
            FruitKit.fx(u, "raiju_claw", c.eye(), (float) c.look().x, (float) c.look().y, (float) c.look().z);
            PowerKit.sound(c, u.position(), SoundEvents.TRIDENT_HIT, 1f, 1.6f);
            return true;
        }));

        // Storm pounce: the beast springs along a flash of lightning straight onto its prey.
        f.add(f.ability("storm_pounce").unlock(5).cooldown(80).stamina(16).anim("pounce").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            LivingEntity t = PowerKit.target(c, 14);
            Vec3 from = u.position();
            if (t != null) {
                Vec3 d = t.position().subtract(from).multiply(1, 0, 1);
                double dist = Math.max(0, d.length() - 1.2);
                if (dist > 0.3) FruitKit.blink(u, d.normalize(), dist);
                Hits.hit(u, t, FruitKit.dmg(c, 8), bolt.withStun(10).withPoise(45));
            } else FruitKit.blink(u, PowerKit.flatAim(c), 10);
            FruitKit.fx(u, "raiju_bolt", from.add(0, 1, 0), (float) u.getX(), (float) u.getY() + 1, (float) u.getZ());
            PowerKit.sound(c, u.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.8f);
            return true;
        }));

        // Ball lightning: a crackling ball of lightning that bursts on impact, zapping everyone near.
        f.add(f.ability("ball_lightning").unlock(12).cooldown(40).stamina(14).anim("cast_forward").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "raiju_ball", 0.6f, 1.6, FruitKit.dmg(c, 7), bolt.withPoise(25));
            p.life = 40;
            p.onHit = (pr, hit, pos) -> {
                for (LivingEntity e : PowerKit.foes(u, pos, 3)) if (e != hit) Hits.hit(u, e, FruitKit.dmg(c, 3), bolt.withPoise(10));
                FruitKit.fx(u, "raiju_zap", pos, 3, 0, 0);
            };
            PowerKit.sound(c, u.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.4f, 2f);
            return true;
        }));

        // Thunderfall: the beast leaps up into the clouds and comes down a moment later as a lightning bolt on the
        // target (or wherever it was looking).
        f.add(f.ability("thunderfall").unlock(20).cooldown(200).stamina(24).anim("vault").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            LivingEntity t = PowerKit.target(c, 20);
            Vec3 at = t != null ? t.position() : FruitKit.aimPoint(c, 20);
            FruitKit.impulse(u, new Vec3(0, 1.2, 0), true);
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 14;
            FruitKit.fx(u, "raiju_bolt", u.position().add(0, 1, 0), (float) u.getX(), (float) u.getY() + 12, (float) u.getZ());
            CombatState.of(u).schedule(c.level.getGameTime() + 12, () -> {
                Vec3 land = t != null && t.isAlive() ? t.position() : at;
                Vec3 off = land.subtract(u.position()).multiply(1, 0, 1);
                Vec3 spot = off.lengthSqr() > 2 ? land.subtract(off.normalize().scale(1.2)) : land;
                if (u.level().noCollision(u, u.getBoundingBox().move(spot.subtract(u.position())))) PowerKit.place(u, spot);
                for (LivingEntity e : PowerKit.foes(u, land.add(0, 1, 0), 4)) Hits.hit(u, e, FruitKit.dmg(c, 12), bolt.withStun(20).withPoise(60));
                FruitKit.fx(u, "raiju_bolt", land.add(0, 14, 0), (float) land.x, (float) land.y, (float) land.z);
                FruitKit.fx(u, "raiju_zap", land, 4, 1, 0);
                PowerKit.sound(c, land, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5f, 1f);
            });
            return true;
        }));

        // Static fur: the coat bristles with charge for twelve seconds — the beast runs fast, anyone striking it is
        // shocked, and the charge pops on everyone close when it rises.
        f.add(f.ability("static_fur").unlock(30).cooldown(400).stamina(20).anim("roar").exec((u, c) -> {
            int dur = 240 + c.mastery * 2;
            CombatState.of(u).buffs.put(STATIC, dur);
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1));
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 3.5)) Hits.hit(u, e, FruitKit.dmg(c, 4), bolt.withStun(8).withPoise(20));
            FruitKit.fx(u, "raiju_static", u.position().add(0, 1, 0), u.getBbWidth() + 0.5f, u.getBbHeight(), 0);
            PowerKit.sound(c, u.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.5f, 1.6f);
            return true;
        }));

        // Awakening — Thunder Beast's Rampage: the beast becomes lightning and leaps from enemy to enemy across a wide
        // area — up to eight strikes — and ends with a thunderclap around it.
        f.add(f.ability("raijin_rampage").unlock(55).cooldown(1100).stamina(85).awakened().anim("pounce").exec((u, c) -> {
            Set<LivingEntity> struck = new HashSet<>();
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 70;
            OriginalKit.pulses(c, 0, 8, 8, n -> {
                List<LivingEntity> foes = new ArrayList<>(PowerKit.foes(u, u.position().add(0, 1, 0), 14));
                foes.sort((a, b) -> Double.compare(a.distanceToSqr(u), b.distanceToSqr(u)));
                LivingEntity next = foes.stream().filter(e -> !struck.contains(e)).findFirst().orElse(foes.isEmpty() ? null : foes.get(0));
                if (next == null) return;
                struck.add(next);
                Vec3 from = u.position();
                Vec3 d = next.position().subtract(from).multiply(1, 0, 1);
                if (d.length() > 1.5) FruitKit.blink(u, d.normalize(), d.length() - 1.2);
                Hits.hit(u, next, FruitKit.dmg(c, 10), bolt.withStun(15).withPoise(50).asUnblockable());
                FruitKit.fx(u, "raiju_bolt", from.add(0, 1, 0), (float) u.getX(), (float) u.getY() + 1, (float) u.getZ());
                PowerKit.sound(c, u.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.6f);
            });
            CombatState.of(u).schedule(c.level.getGameTime() + 66, () -> {
                for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 6)) Hits.hit(u, e, FruitKit.dmg(c, 12), bolt.withStun(30).withPoise(80));
                FruitKit.fx(u, "raiju_zap", u.position(), 6, 1, 0);
                PowerKit.sound(c, u.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 2f, 0.8f);
            });
            return true;
        }));

        // Passive: the thunder beast's coat. In form, close attackers are shocked (harder with Static Fur); in rain or
        // thunder the beast is faster and deals 15% more. Immune to lightning (own element).
        f.incomingHook = (u, a, amount, info) -> {
            boolean charged = CombatState.of(u).hasBuff(STATIC);
            if (a != null && !shocking && a.distanceTo(u) < 3.5 && Hits.hostileTo(u, a) && (charged || !Zoan.form(u).isEmpty())) {
                shocking = true; // two thunder beasts must not shock each other forever
                try {
                    Hits.hit(u, a, charged ? 4 : 2, FruitKit.info(Element.LIGHTNING, 0.3f).withPoise(charged ? 25 : 10));
                } finally {
                    shocking = false;
                }
            }
            return amount;
        };
        f.outgoingHook = (u, v, amount, info) -> stormy(u) ? amount * 1.15f : amount;
        f.tickHook = u -> {
            if (u.tickCount % 40 == 0 && stormy(u) && !Zoan.form(u).isEmpty())
                u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0, false, false));
        };
    }

    private RaijuFruit() {}
}
