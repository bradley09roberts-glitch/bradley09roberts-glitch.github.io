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
 * Fusen Fusen no Mi — ORIGINAL balloon Paramecia (wave 4). Anything the user touches can be blown up like a balloon:
 * balloon bombs, enemies puffed up until they float helplessly away towards the White Sea (and pop), a bouncy body,
 * and at awakening a whole battlefield lifted into the sky.
 * Passive: rubbery balloon skin — blunt blows -25%, no fall damage.
 * Weakness: sharp things pop balloons — pierce and slash deal 25% more.
 */
public final class FusenFruit {
    static final String BOUNCY = "fusen_bouncy";

    /** Puff a target up: it floats up helplessly (cannot strike hard) until it pops. */
    static void inflate(LivingEntity e, int ticks, int lift) {
        e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, ticks, lift));
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks + 20, 1));
        CombatState.of(e).blocking = false;
    }

    /** Pop: the balloon bursts and the target drops. */
    static void pop(LivingEntity u, LivingEntity e, float dmg, Hits.Info info) {
        e.removeEffect(MobEffects.LEVITATION);
        Hits.hit(u, e, dmg, info);
        FruitKit.impulse(e, new Vec3(0, -0.4, 0), false);
        FruitKit.fx(u, "fusen_pop", e.position().add(0, e.getBbHeight() * 0.6, 0), e.getBbWidth() + 0.5f, 0, 0);
    }

    public static void init(DevilFruit f) {
        Hits.Info bounce = FruitKit.info(Element.BLUNT, 1.6f);

        // Balloon bomb: a balloon packed tight with air; it bursts on impact and bounces everyone near upward.
        f.add(f.ability("balloon_bomb").cooldown(20).stamina(8).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "fusen_balloon", 0.6f, 1.5, FruitKit.dmg(c, 5), bounce.withPoise(20));
            p.gravity = -0.004;
            p.life = 50;
            p.onHit = (pr, hit, pos) -> {
                for (LivingEntity e : PowerKit.foes(u, pos, 2.5)) FruitKit.impulse(e, new Vec3(0, 0.55, 0), false);
                FruitKit.fx(u, "fusen_pop", pos, 2.5f, 0, 0);
            };
            PowerKit.sound(c, u.position(), SoundEvents.PUFFER_FISH_BLOW_UP, 1f, 1.4f);
            return true;
        }));

        // Inflate: the target swells like a balloon and floats helplessly up for three seconds — then it pops.
        f.add(f.ability("inflate").unlock(5).cooldown(140).stamina(16).anim("palm_strike").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 10);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 3), bounce.withPoise(20));
            inflate(t, 60, 1);
            FruitKit.fx(u, "fusen_inflate", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            CombatState.of(u).schedule(c.level.getGameTime() + 55, () -> {
                if (t.isAlive()) pop(u, t, FruitKit.dmg(c, 6), bounce.withStun(10).withPoise(40));
            });
            PowerKit.sound(c, t.position(), SoundEvents.PUFFER_FISH_BLOW_UP, 1.5f, 0.8f);
            return true;
        }));

        // Bounce body: the user puffs up — blunt blows bounce off (-50%), attackers are bounced back, jumps are huge.
        f.add(f.ability("bounce_body").unlock(10).cooldown(300).stamina(16).anim("harden").exec((u, c) -> {
            int dur = 160 + c.mastery;
            CombatState.of(u).buffs.put(BOUNCY, dur);
            u.addEffect(new MobEffectInstance(MobEffects.JUMP, dur, 2, false, false));
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 2.5)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 3), bounce.withPoise(20))) PowerKit.push(e, u.position(), 1.0, 0.3);
            }
            FruitKit.fx(u, "fusen_body", u.position().add(0, 1, 0), u.getBbWidth() + 0.6f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.PUFFER_FISH_BLOW_UP, 1.2f, 0.6f);
            return true;
        }));

        // Float away: the user puffs up and rises out of reach for two seconds, dropping balloon bombs where they
        // stood, then drifts down slowly wherever they like.
        f.add(f.ability("float_away").unlock(20).cooldown(220).stamina(18).anim("cast_up").exec((u, c) -> {
            Vec3 at = u.position();
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 3.5)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 5), bounce.withPoise(25))) FruitKit.impulse(e, new Vec3(0, 0.5, 0), false);
            }
            u.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 2, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
            FruitKit.fx(u, "fusen_pop", at.add(0, 0.5, 0), 3.5f, 0, 0);
            PowerKit.sound(c, at, SoundEvents.PUFFER_FISH_BLOW_UP, 1.5f, 1.0f);
            return true;
        }));

        // Balloon bouquet: a balloon is tied to every enemy around — they all float up together, then all pop.
        f.add(f.ability("balloon_bouquet").unlock(30).cooldown(300).stamina(28).anim("cast_up").exec((u, c) -> {
            java.util.List<LivingEntity> caught = PowerKit.foes(u, u.position().add(0, 1, 0), 7);
            for (LivingEntity e : caught) {
                Hits.hit(u, e, FruitKit.dmg(c, 3), bounce.withPoise(15));
                inflate(e, 80, 1);
                FruitKit.fx(u, "fusen_inflate", e.position(), e.getBbWidth(), e.getBbHeight(), 0);
            }
            CombatState.of(u).schedule(c.level.getGameTime() + 58, () -> {
                for (LivingEntity e : caught) if (e.isAlive()) pop(u, e, FruitKit.dmg(c, 8), bounce.withStun(15).withPoise(40));
                PowerKit.sound(c, u.position(), SoundEvents.FIREWORK_ROCKET_BLAST, 1.5f, 0.8f);
            });
            PowerKit.sound(c, u.position(), SoundEvents.PUFFER_FISH_BLOW_UP, 2f, 1.2f);
            return true;
        }));

        // Awakening — Great Ascension: everything in a wide area swells and rises high into the sky (towards the
        // White Sea), then pops all at once far above the ground. Allies drift down safely.
        f.add(f.ability("great_ascension").unlock(55).cooldown(1000).stamina(80).awakened().anim("cast_up").exec((u, c) -> {
            java.util.List<LivingEntity> caught = PowerKit.foes(u, u.position().add(0, 1, 0), 11);
            for (LivingEntity e : caught) {
                Hits.hit(u, e, FruitKit.dmg(c, 6), bounce.withPoise(30).asUnblockable());
                inflate(e, 100, 3);
                FruitKit.fx(u, "fusen_inflate", e.position(), e.getBbWidth(), e.getBbHeight(), 1);
            }
            for (LivingEntity a : OriginalKit.allies(u, u.position().add(0, 1, 0), 11, true))
                a.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0, false, false));
            CombatState.of(u).schedule(c.level.getGameTime() + 58, () -> {
                for (LivingEntity e : caught) if (e.isAlive()) pop(u, e, FruitKit.dmg(c, 14), bounce.withStun(30).withPoise(80).asUnblockable());
                PowerKit.sound(c, u.position(), SoundEvents.FIREWORK_ROCKET_BLAST, 2.5f, 0.5f);
            });
            FruitKit.fx(u, "fusen_ascend", u.position(), 11, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.PUFFER_FISH_BLOW_UP, 2.5f, 0.5f);
            return true;
        }));

        // Passive: rubbery balloon skin (blunt -25%, -50% while bouncy and attackers bounced back), no fall damage;
        // sharp things pop it (pierce/slash +25%).
        f.incomingHook = (u, a, amount, info) -> {
            if (info.element() == Element.PIERCE || info.element() == Element.SLASH) return amount * 1.25f;
            boolean bouncy = CombatState.of(u).hasBuff(BOUNCY);
            if (bouncy && a != null && a.distanceTo(u) < 4) PowerKit.push(a, u.position(), 0.9, 0.3);
            if (info.element() == Element.BLUNT || info.element() == Element.PHYSICAL || info.element() == Element.IMPACT)
                return amount * (bouncy ? 0.5f : 0.75f);
            return amount;
        };
        f.tickHook = u -> u.fallDistance = 0;
    }

    private FusenFruit() {}
}
