package com.grandline.fruit.impl;

import com.grandline.combat.Abilities;
import com.grandline.combat.Ability;
import com.grandline.combat.CombatManager;
import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.combat.style.FightingStyle;
import com.grandline.combat.weapon.WeaponClass;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.FruitRules;
import com.grandline.player.Race;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Kagi Kagi no Mi — ORIGINAL key Paramecia (wave 4). Everything has a lock, and the user holds every key: joints locked
 * stiff, techniques locked away so the target cannot use them, the user's own limits unlocked. Born on the dark side of
 * Sabaody, where collars and keys decide who is free. The one lock this fruit can never pick is seastone.
 * Passive: the user's own joints cannot be locked (heavy slows and roots fall away); a locked enemy takes 20% more
 * from the user.
 */
public final class KagiFruit {
    public static final String LOCKED = "kagi_locked";
    static final String ROOTED = "kagi_rooted";

    /** Lock a body's joints: rooted and fumbling for {@code ticks}. */
    static void lockJoints(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6));
        e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 2));
        e.setDeltaMovement(e.getDeltaMovement().multiply(0, 1, 0));
        CombatState.of(e).buffs.put(ROOTED, ticks);
    }

    /**
     * Lock away every technique the target could use (its fruit, fighting style, race and weapon abilities): they all
     * go on cooldown for {@code ticks}. Seastone and Haki are untouched.
     */
    public static int lockTechniques(LivingEntity e, int ticks) {
        long now = e.level().getGameTime();
        CombatState s = CombatState.of(e);
        List<Ability> list = new ArrayList<>();
        DevilFruit fruit = FruitRules.fruitOf(e);
        if (fruit != null) list.addAll(fruit.abilities());
        FightingStyle st = CombatManager.styleOf(e);
        if (st != null) list.addAll(Abilities.of(Ability.Source.STYLE, st.id));
        Race r = com.grandline.race.RaceKit.raceOf(e);
        if (r != null) list.addAll(Abilities.of(Ability.Source.RACE, r.id()));
        WeaponClass wc = CombatManager.weaponOf(e.getMainHandItem());
        if (wc != null) list.addAll(Abilities.of(Ability.Source.WEAPON, wc.id()));
        int n = 0;
        for (Ability a : list) {
            if (s.cooldownLeft(a.id(), now) >= ticks) continue;
            s.startCooldown(a.id(), now, ticks);
            n++;
        }
        s.buffs.put(LOCKED, ticks);
        if (e instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("grandline.kagi.locked", ticks / 20).withStyle(ChatFormatting.GOLD), true);
        return n;
    }

    static boolean locked(LivingEntity e) {
        CombatState s = CombatState.of(e);
        return s.hasBuff(LOCKED) || s.hasBuff(ROOTED);
    }

    public static void init(DevilFruit f) {
        Hits.Info key = FruitKit.info(Element.PIERCE, 0.3f);

        // Key shot: a brass key flies into the target and turns — its joints click stiff for a moment.
        f.add(f.ability("key_shot").cooldown(16).stamina(8).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "kagi_key", 0.35f, 2.4, FruitKit.dmg(c, 5), key.withPoise(20));
            p.life = 32;
            p.onHit = (pr, hit, pos) -> {
                if (hit != null) hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
            };
            PowerKit.sound(c, u.position(), SoundEvents.CHAIN_PLACE, 1f, 1.8f);
            return true;
        }));

        // Lock joint: a key turns in the target's knees — rooted for three seconds.
        f.add(f.ability("lock_joint").unlock(5).cooldown(120).stamina(16).anim("palm_strike").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 6);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 5), key.withStun(10).withPoise(30));
            lockJoints(t, 60 + c.mastery / 2);
            FruitKit.fx(u, "kagi_lock", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            PowerKit.sound(c, t.position(), SoundEvents.CHAIN_PLACE, 1.5f, 0.8f);
            return true;
        }));

        // Lock away: the target's techniques are locked in a box for five seconds — fruit powers, style moves, racial
        // gifts and weapon arts alike.
        f.add(f.ability("lock_away").unlock(12).cooldown(300).stamina(24).anim("grab").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 10);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 6), key.withPoise(30));
            lockTechniques(t, 100 + c.mastery);
            FruitKit.fx(u, "kagi_seal", t.position().add(0, t.getBbHeight() * 0.6, 0), t.getBbWidth(), t.getBbHeight(), 0);
            PowerKit.sound(c, t.position(), SoundEvents.IRON_DOOR_CLOSE, 1.5f, 0.7f);
            return true;
        }));

        // Unlock: the user (and allies nearby) are unlocked — slows, roots and stagger fall away, and their limits are
        // opened: strength and speed for a while. Seastone stays shut.
        f.add(f.ability("unlock").unlock(20).cooldown(400).stamina(18).anim("focus").exec((u, c) -> {
            int dur = 200 + c.mastery * 2;
            for (LivingEntity a : OriginalKit.allies(u, u.position().add(0, 1, 0), 5, true)) {
                a.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                a.removeEffect(MobEffects.DIG_SLOWDOWN);
                a.removeEffect(MobEffects.WEAKNESS);
                CombatState.of(a).staggeredUntil = 0;
                a.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, dur, 0));
                a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 0));
                FruitKit.fx(u, "kagi_unlock", a.position().add(0, 1, 0), a.getBbWidth(), a.getBbHeight(), 0);
            }
            // the burst of the opening lock jars anyone right next to the user
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 2.5)) Hits.hit(u, e, FruitKit.dmg(c, 3), key.withPoise(15));
            PowerKit.sound(c, u.position(), SoundEvents.IRON_DOOR_OPEN, 1.5f, 1.2f);
            return true;
        }));

        // Lockdown: keys turn in every enemy around at once — joints rooted for two seconds, techniques locked for three.
        f.add(f.ability("lockdown").unlock(30).cooldown(500).stamina(32).anim("ground_slam").exec((u, c) -> {
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 7)) {
                Hits.hit(u, e, FruitKit.dmg(c, 5), key.withPoise(30));
                lockJoints(e, 40);
                lockTechniques(e, 60);
                FruitKit.fx(u, "kagi_lock", e.position(), e.getBbWidth(), e.getBbHeight(), 0);
            }
            FruitKit.fx(u, "kagi_ring", u.position(), 7, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.IRON_DOOR_CLOSE, 2f, 0.5f);
            return true;
        }));

        // Awakening — Master Key: the key to everything. The user's own locks spring open (every cooldown of their fruit
        // is reset), and every enemy in a wide area is locked down: techniques locked for six seconds, joints for two.
        f.add(f.ability("master_key").unlock(55).cooldown(1100).stamina(80).awakened().anim("cast_up").exec((u, c) -> {
            CombatState s = CombatState.of(u);
            for (Ability a : f.abilities()) if (!a.id().endsWith("/master_key")) s.cooldowns.remove(a.id());
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 12)) {
                Hits.hit(u, e, FruitKit.dmg(c, 10), key.withStun(20).withPoise(60).asUnblockable());
                lockJoints(e, 40);
                lockTechniques(e, 120);
                FruitKit.fx(u, "kagi_seal", e.position().add(0, e.getBbHeight() * 0.6, 0), e.getBbWidth(), e.getBbHeight(), 1);
            }
            FruitKit.fx(u, "kagi_ring", u.position(), 12, 1, 0);
            PowerKit.sound(c, u.position(), SoundEvents.BELL_RESONATE, 1.5f, 1.5f);
            return true;
        }));

        // Passive: the user's own joints cannot be locked (heavy slows fall away each second); locked enemies take 20%
        // more from the user.
        f.outgoingHook = (u, v, amount, info) -> locked(v) ? amount * 1.2f : amount;
        f.tickHook = u -> {
            if (u.tickCount % 20 != 0) return;
            MobEffectInstance slow = u.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (slow != null && slow.getAmplifier() >= 2) u.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        };
    }

    private KagiFruit() {}
}
