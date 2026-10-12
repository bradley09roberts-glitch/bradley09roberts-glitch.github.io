package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Horo Horo no Mi — ghosts. Negative Hollows pass through a body and drown it in despair (on its knees, unable to
 * fight), Mini Hollows explode, and the user can leave her body behind as an untouchable spirit.
 */
public final class HoroFruit {
    static final String GHOST = "horo_ghost";

    /** A Negative Hollow passes through: the victim collapses in despair, stops fighting and forgets its target. */
    static void despair(LivingEntity u, LivingEntity e, int ticks) {
        CanonKit.disable(e, ticks, 3);
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks * 2, 2));
        if (e instanceof ServerPlayer sp) {
            sp.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks + 40, 0));
            CanonKit.tell(sp, "grandline.horo.despair");
        }
        if (e instanceof net.minecraft.world.entity.Mob m && m.getTarget() == u) m.setTarget(null);
        com.grandline.net.GLNetwork.anim(e, "staggered");
        FruitKit.fx(u, "negative_hollow", e.position().add(0, 1, 0), e.getBbWidth(), e.getBbHeight(), 0);
    }

    public static void init(DevilFruit f) {
        Hits.Info spirit = FruitKit.info(Element.DARKNESS, 0.2f);

        // Negative Hollow: a ghost glides through the target — "I'm sorry I was ever born..."
        f.add(f.ability("negative_hollow").cooldown(60).stamina(14).anim("cast_forward").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "negative_hollow", 0.8f, 1.2, FruitKit.dmg(c, 3), spirit.withPoise(20));
            p.pierce = true;
            p.life = 40;
            p.onHit = (pr, hit, pos) -> {
                if (hit != null) despair(u, hit, 40 + c.mastery / 3);
            };
            PowerKit.sound(c, u.position(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, 1.2f, 0.5f);
            return true;
        }));

        // Mini Hollow: a flock of little ghosts that burst like firecrackers ("Ghost Rap").
        f.add(f.ability("mini_hollow").unlock(5).cooldown(50).stamina(16).anim("throw").exec((u, c) -> {
            Vec3 dir = FruitKit.aimDir(c);
            for (int i = -2; i <= 2; i++) {
                Vec3 d = PowerKit.yaw(dir, i * 9).add(0, 0.05 * Math.abs(i), 0);
                AbilityProjectile p = AbilityProjectile.shoot(u, "mini_hollow", 0.4f, c.eye().add(d.scale(0.7)), d, 1.3, FruitKit.dmg(c, 3), spirit.withPoise(15));
                p.explosion = 1.4f;
                p.life = 36;
            }
            return true;
        }));

        // Toku Hollow: one huge ghost that drifts to the enemy and explodes.
        f.add(f.ability("toku_hollow").unlock(15).cooldown(150).stamina(28).anim("cast_up").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "toku_hollow", 1.6f, 0.9, FruitKit.dmg(c, 12), FruitKit.info(Element.IMPACT, 2.5f).withPoise(60));
            p.explosion = 3.5f;
            p.life = 60;
            return true;
        }));

        // Spirit Body: the user's spirit leaves the body — invisible, weightless and untouchable, but its blows are faint.
        f.add(f.ability("spirit_body").unlock(25).cooldown(500).stamina(24).anim("focus").exec((u, c) -> {
            int dur = 120 + c.mastery;
            CombatState.of(u).buffs.put(GHOST, dur);
            u.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, dur, 0, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, dur, 0, false, false));
            for (LivingEntity e : PowerKit.foes(u, u.position(), 16)) PowerKit.loseTrack(u, e);
            // leaving the body chills anyone next to it
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 3)) Hits.hit(u, e, FruitKit.dmg(c, 3), spirit);
            FruitKit.fx(u, "ghost_body", u.position().add(0, 1, 0), 1, 0, 0);
            return true;
        }));

        // Awakening — Negative Parade: a swarm of Negative Hollows sweeps the whole area.
        f.add(f.ability("negative_parade").unlock(50).cooldown(900).stamina(75).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 center = u.position().add(0, 1, 0);
            FruitKit.fx(u, "hollow_parade", center, 12, 0, 0);
            for (LivingEntity e : PowerKit.foes(u, center, 12)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 6), spirit.withPoise(40).asUnblockable())) despair(u, e, 60 + c.mastery / 2);
            }
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                Vec3 d = new Vec3(Math.cos(a), 0.1, Math.sin(a));
                AbilityProjectile p = AbilityProjectile.shoot(u, "mini_hollow", 0.5f, center.add(d), d, 1.0, FruitKit.dmg(c, 4), spirit.withPoise(15));
                p.explosion = 1.8f;
                p.life = 30;
            }
            PowerKit.sound(c, center, SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, 2f, 0.3f);
            return true;
        }));

        // Passive: ghost princess. Melee attackers risk a stray Negative Hollow (brief despair). As a spirit the user
        // cannot be harmed except by Haki or seastone, but hits only half as hard.
        f.incomingHook = (u, a, amount, info) -> {
            boolean haki = info.imbued() || (a != null && com.grandline.haki.Haki.armamentActive(a));
            if (CombatState.of(u).hasBuff(GHOST) && !haki && !info.seastone()) return 0f;
            if (a != null && a.distanceTo(u) < 4 && u.getRandom().nextFloat() < 0.2f) despair(u, a, 20);
            return amount;
        };
        f.outgoingHook = (u, v, amount, info) -> CombatState.of(u).hasBuff(GHOST) ? amount * 0.5f : amount;
    }

    private HoroFruit() {}
}
