package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import com.grandline.npc.GLNpc;
import com.grandline.registry.ModEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Nemu Nemu no Mi — ORIGINAL sleep Paramecia. The user deals in drowsiness: a contagious yawn, a lullaby, a touch
 * that drops a foe asleep on the spot, nightmares that hurt sleepers without waking them, a sleepwalk that slips
 * past enemies, and at awakening an Eternal Slumber over a whole area.
 * Sleep is real: a sleeper cannot move, attack or use powers (and can be cuffed) until the time runs out or someone
 * hurts them. Drowsiness stacks — three stacks and the target falls asleep. Strong foes (bosses) only nod off briefly.
 * Passive: Rude Awakening — the user's first hit on a sleeper deals 50% more. The user cannot be put to sleep and
 * is immune to sound.
 */
public final class NemuFruit {
    /** Sleeper -> game time the sleep ends. */
    static final Map<LivingEntity, Long> SLEEPERS = Collections.synchronizedMap(new WeakHashMap<>());
    static final Map<LivingEntity, Integer> DROWSY = Collections.synchronizedMap(new WeakHashMap<>());
    static final String DROWSY_BUFF = "nemu_drowsy";
    /** Set while nightmare damage is applied, so it does not wake the dreamer. */
    static boolean dreaming;

    public static boolean asleep(LivingEntity e) {
        Long until = SLEEPERS.get(e);
        if (until == null) return false;
        if (until <= e.level().getGameTime() || !e.hasEffect(ModEffects.KNOCKED_OUT)) {
            SLEEPERS.remove(e);
            return false;
        }
        return true;
    }

    /** Put a target to sleep for {@code ticks} (bosses and Nemu users resist). */
    static boolean sleep(LivingEntity e, int ticks) {
        var fruit = com.grandline.fruit.FruitRules.fruitOf(e);
        if (fruit != null && fruit.id.equals("nemu") && !com.grandline.fruit.FruitRules.weakened(e)) return false;
        if (e instanceof GLNpc n && n.tier() >= 8) ticks = Math.min(ticks, 30);
        if (e.getMaxHealth() > 200) ticks = Math.min(ticks, 30);
        e.addEffect(new MobEffectInstance(ModEffects.KNOCKED_OUT, ticks, 0, false, true));
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false));
        SLEEPERS.put(e, e.level().getGameTime() + ticks);
        DROWSY.remove(e);
        CombatState.of(e).blocking = false;
        if (e instanceof Mob m) m.setTarget(null);
        FruitKit.fx(e, "nemu_sleep", e.position().add(0, e.getBbHeight() + 0.3, 0), ticks / 20f, 0, 0);
        return true;
    }

    /** Add drowsiness; the third stack puts the target to sleep. */
    static void drowse(LivingEntity e, int sleepTicks) {
        CombatState s = CombatState.of(e);
        int n = s.hasBuff(DROWSY_BUFF) ? DROWSY.getOrDefault(e, 0) + 1 : 1;
        s.buffs.put(DROWSY_BUFF, 160);
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, Math.min(3, n)));
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
        if (n >= 3) sleep(e, sleepTicks);
        else DROWSY.put(e, n);
    }

    /** Called when damage lands on any entity: a sleeper wakes up (except from nightmares). */
    public static void onDamaged(LivingEntity e) {
        if (dreaming || !asleep(e)) return;
        SLEEPERS.remove(e);
        e.removeEffect(ModEffects.KNOCKED_OUT);
        e.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        FruitKit.fx(e, "nemu_wake", e.position().add(0, e.getBbHeight(), 0), 0, 0, 0);
    }

    public static void init(DevilFruit f) {
        Hits.Info hush = FruitKit.info(Element.SOUND, 0.2f);

        // Yawn: a contagious yawn washes over everyone in front — drowsy, slowed, weakened.
        f.add(f.ability("yawn").cooldown(30).stamina(10).anim("cast_forward").exec((u, c) -> {
            for (LivingEntity e : Hits.cone(u, 8 + u.getScale(), 100)) {
                if (!Hits.hostileTo(u, e)) continue;
                Hits.hit(u, e, FruitKit.dmg(c, 4), hush.withPoise(20));
                drowse(e, 80 + c.mastery);
            }
            FruitKit.fx(u, "nemu_yawn", c.eye(), (float) c.look().x, (float) c.look().y, (float) c.look().z);
            PowerKit.sound(c, u.position(), SoundEvents.FOX_SLEEP, 1.5f, 0.6f);
            return true;
        }));

        // Lullaby: a soft song around the user — every verse adds drowsiness to all enemies who hear it.
        f.add(f.ability("lullaby").unlock(5).cooldown(220).stamina(20).anim("focus").exec((u, c) -> {
            OriginalKit.pulses(c, 0, 20, 3, n -> {
                for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 7)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 1.5f), hush.withPoise(10));
                    drowse(e, 80 + c.mastery);
                }
                FruitKit.fx(u, "nemu_lullaby", u.position().add(0, 1, 0), 7, 0, 0);
                PowerKit.sound(c, u.position(), SoundEvents.NOTE_BLOCK_HARP.value(), 1.5f, 0.6f + n * 0.1f);
            });
            return true;
        }));

        // Sandman's touch: a pinch of sleep dust — the target drops asleep where it stands.
        f.add(f.ability("sandman_touch").unlock(12).cooldown(240).stamina(22).anim("palm_strike").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 10);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 2), hush.withPoise(10));
            sleep(t, 80 + c.mastery);
            FruitKit.fx(u, "nemu_dust", t.getEyePosition(), 1, 0, 0);
            PowerKit.sound(c, t.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 0.5f);
            return true;
        }));

        // Nightmare: every sleeper nearby suffers a nightmare — hurt and withering without waking; those still awake
        // are haunted by visions (dizzy, in darkness) and grow drowsier.
        f.add(f.ability("nightmare").unlock(20).cooldown(260).stamina(26).anim("cast_forward").exec((u, c) -> {
            OriginalKit.pulses(c, 0, 15, 4, n -> {
                for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 16)) {
                    if (asleep(e)) {
                        dreaming = true;
                        try {
                            Hits.hit(u, e, FruitKit.dmg(c, 4), hush.withPoise(5));
                        } finally {
                            dreaming = false;
                        }
                        e.addEffect(new MobEffectInstance(MobEffects.WITHER, 30, 0));
                        FruitKit.fx(u, "nemu_nightmare", e.position().add(0, e.getBbHeight(), 0), 1, 0, 0);
                    } else if (n == 0 && e.distanceTo(u) < 10) {
                        Hits.hit(u, e, FruitKit.dmg(c, 3), hush.withPoise(10));
                        e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
                        e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
                        drowse(e, 60 + c.mastery);
                    }
                }
            });
            PowerKit.sound(c, u.position(), SoundEvents.ELDER_GUARDIAN_AMBIENT, 1f, 0.6f);
            return true;
        }));

        // Sleepwalk: the user drifts like a sleepwalker, untouchable and unseen, and reappears behind the target with
        // a Rude Awakening strike.
        f.add(f.ability("sleepwalk").unlock(30).cooldown(200).stamina(28).anim("sway").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 18);
            long now = c.level.getGameTime();
            CombatState.of(u).iframesUntil = now + 24;
            u.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 24, 0, false, false));
            FruitKit.fx(u, "nemu_dust", u.position().add(0, 1, 0), 2, 0, 0);
            if (t == null) {
                FruitKit.blink(u, c.look(), 10);
                return true;
            }
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 10)) PowerKit.loseTrack(u, e);
            CombatState.of(u).schedule(now + 16, () -> {
                if (!u.isAlive() || !t.isAlive()) return;
                Vec3 back = t.getLookAngle().multiply(1, 0, 1);
                back = back.lengthSqr() < 1e-4 ? PowerKit.flatAim(c) : back.normalize();
                PowerKit.place(u, t.position().subtract(back.scale(1.4)));
                u.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
                Hits.hit(u, t, FruitKit.dmg(c, 10), FruitKit.info(Element.BLUNT, 1.5f).withStun(15).withPoise(60));
                FruitKit.fx(u, "nemu_wake", t.position().add(0, t.getBbHeight(), 0), 0, 0, 0);
            });
            return true;
        }));

        // Awakening — Eternal Slumber: everything hostile in a wide area falls asleep at once, while the user and
        // allies inside rest and recover in the dream.
        f.add(f.ability("eternal_slumber").unlock(55).cooldown(1200).stamina(85).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 at = u.position();
            double r = 11;
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                Hits.hit(u, e, FruitKit.dmg(c, 3), hush.withPoise(10));
                sleep(e, 120 + c.mastery);
            }
            OriginalKit.pulses(c, 0, 20, 6, n -> {
                FruitKit.fx(u, "nemu_lullaby", at.add(0, 1, 0), (float) r, 1, 0);
                for (LivingEntity a : OriginalKit.allies(u, at.add(0, 1, 0), r, true)) OriginalKit.heal(a, 2, "");
            });
            PowerKit.sound(c, at, SoundEvents.NOTE_BLOCK_HARP.value(), 2f, 0.5f);
            return true;
        }));

        // Passive: Rude Awakening (+50% against sleepers); sound immunity comes with the fruit's element.
        f.outgoingHook = (u, v, amount, info) -> asleep(v) && !dreaming ? amount * 1.5f : amount;
    }

    private NemuFruit() {}
}
