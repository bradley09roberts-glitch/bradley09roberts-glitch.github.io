package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

/**
 * Toki Toki no Mi — time travel, but only forwards. The user (or whoever she touches) skips ahead in time: gone from
 * the present, untouchable and unable to act, then back moments later. Rule: in a fight the user cannot leap twice in
 * a row — another technique must come between two leaps.
 */
public final class TokiFruit {
    static final String LEAPT = "toki_leapt";

    /** Send an entity into the future: it vanishes (cannot act or be harmed) and reappears after {@code ticks}. */
    static void sendForward(LivingEntity owner, LivingEntity e, int ticks, Runnable onReturn) {
        CombatState s = CombatState.of(e);
        s.iframesUntil = Math.max(s.iframesUntil, e.level().getGameTime() + ticks);
        CanonKit.disable(e, ticks, 9);
        e.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false));
        CanonKit.forget(e, 32);
        FruitKit.fx(owner, "time_vanish", e.position().add(0, 1, 0), e.getBbWidth(), e.getBbHeight(), 0);
        CanonKit.later(owner, e, ticks, () -> {
            s.iframesUntil = Math.min(s.iframesUntil, e.level().getGameTime());
            CanonKit.enable(e);
            e.removeEffect(MobEffects.INVISIBILITY);
            e.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (!e.isAlive()) return;
            com.grandline.net.GLNetwork.fx(e, "time_return", e.getX(), e.getY() + 1, e.getZ(), e.getBbWidth(), e.getBbHeight(), 0);
            onReturn.run();
        });
    }

    /** Any technique other than a leap resets the no-chaining rule. */
    static void acted(LivingEntity u) {
        CombatState.of(u).buffs.remove(LEAPT);
    }

    public static void init(DevilFruit f) {
        // Time Leap: the user skips a few seconds into the future — gone, untouchable, then back where she stood.
        f.add(f.ability("time_leap").cooldown(100).stamina(16).anim("focus").exec((u, c) -> {
            CombatState s = CombatState.of(u);
            if (s.hasBuff(LEAPT) && CanonKit.inCombat(u, c.target)) {
                CanonKit.tell(u, "grandline.toki.no_chain");
                return false;
            }
            int dur = 40 + c.mastery / 4;
            sendForward(u, u, dur, () -> {});
            s.buffs.put(LEAPT, 600 + dur);
            PowerKit.sound(c, u.position(), SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.5f);
            return true;
        }));

        // Time Flight: a touch sends the target into the future; it comes back reeling from the jump.
        f.add(f.ability("time_flight").unlock(8).cooldown(180).stamina(22).anim("palm_strike").exec((u, c) -> {
            LivingEntity t = CanonKit.melee(c, 5);
            if (t == null) return false;
            acted(u);
            sendForward(u, t, 30 + c.mastery / 3, () -> {
                Hits.hit(u, t, FruitKit.dmg(c, 6), FruitKit.info(Element.PHYSICAL, 0f).withPoise(30).asUnblockable());
                t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            });
            PowerKit.sound(c, t.position(), SoundEvents.ENDERMAN_TELEPORT, 1f, 0.7f);
            return true;
        }));

        // Skipped Moment: the user skips the moment it takes to get there and is simply behind the target, striking.
        f.add(f.ability("skipped_moment").unlock(15).cooldown(80).stamina(18).anim("heavy_punch").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 16);
            if (t == null) return false;
            acted(u);
            Vec3 from = u.position();
            Vec3 dir = t.position().subtract(from).multiply(1, 0, 1);
            dir = dir.lengthSqr() < 1e-4 ? PowerKit.flatAim(c) : dir.normalize();
            Vec3 spot = t.position().add(dir.scale(1.3));
            if (!c.level.noCollision(u, u.getBoundingBox().move(spot.subtract(from)))) spot = t.position().subtract(dir.scale(1.3));
            PowerKit.place(u, spot);
            Hits.hit(u, t, FruitKit.dmg(c, 10), FruitKit.info(Element.PHYSICAL, 1.5f).withStun(20).withPoise(50));
            FruitKit.fx(u, "time_trail", from.add(0, 1, 0), (float) spot.x, (float) spot.y + 1, (float) spot.z);
            return true;
        }));

        // Hourglass: years pass in seconds for everyone in the area — they weaken, slow and wither.
        f.add(f.ability("hourglass").unlock(25).cooldown(220).stamina(30).anim("cast_forward").exec((u, c) -> {
            acted(u);
            Vec3 at = FruitKit.aimPoint(c, 20);
            double r = 5 + c.mastery / 30.0;
            for (int i = 0; i < 6; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i * 8L, () -> {
                    FruitKit.fx(u, "hourglass", at, (float) r, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                        e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 1));
                        e.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 1));
                        Hits.hit(u, e, FruitKit.dmg(c, 2.5f), FruitKit.info(Element.PHYSICAL, 0f).withPoise(8).asUnblockable());
                    }
                });
            }
            return true;
        }));

        // Awakening — Twenty Years Hence: everyone around is flung into the future together and comes back aged.
        f.add(f.ability("twenty_years").unlock(55).cooldown(1100).stamina(85).awakened().anim("cast_up").exec((u, c) -> {
            acted(u);
            FruitKit.fx(u, "hourglass", u.position(), 12, 1, 0);
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 12)) {
                sendForward(u, e, 45, () -> {
                    Hits.hit(u, e, FruitKit.dmg(c, 12), FruitKit.info(Element.PHYSICAL, 0f).withPoise(60).asUnblockable());
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
                });
            }
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 45, 1));
            u.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 45, 1));
            PowerKit.sound(c, u.position(), SoundEvents.BEACON_DEACTIVATE, 1.5f, 0.5f);
            return true;
        }));

        // Passive: time moves on for the user — harmful effects wear off twice as fast.
        f.tickHook = u -> {
            if (u.tickCount % 20 != 0) return;
            for (MobEffectInstance inst : new ArrayList<>(u.getActiveEffects())) {
                if (inst.getEffect().value().getCategory() != MobEffectCategory.HARMFUL || inst.isInfiniteDuration() || inst.getDuration() <= 25) continue;
                u.removeEffect(inst.getEffect());
                u.addEffect(new MobEffectInstance(inst.getEffect(), inst.getDuration() - 20, inst.getAmplifier(), inst.isAmbient(), inst.isVisible(), inst.showIcon()));
            }
        };
    }

    private TokiFruit() {}
}
