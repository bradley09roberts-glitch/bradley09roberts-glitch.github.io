package com.grandline.boss;

import com.grandline.combat.CombatState;
import com.grandline.combat.Hits;
import com.grandline.fruit.impl.PowerKit;
import com.grandline.haki.Haki;
import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Conqueror's Haki in boss fights: the burst that opens each phase, and the clash with players who have it too. */
public final class BossHaki {
    static final int CLASH_COOLDOWN = 100;

    /** Power behind a Conqueror's Haki: the same measure the Haki system uses for pressure. */
    static int pressure(LivingEntity e) {
        return Haki.conquerorLevel(e) * 2 + Haki.willpower(e);
    }

    /**
     * A new phase opens with the boss's Conqueror's Haki (or, without it, a roar that throws everyone back). Weak wills
     * black out for a moment, equals stagger; anyone who has Conqueror's Haki of their own meets it in a clash.
     */
    static void phaseBurst(BossFight f, long now) {
        LivingEntity b = f.boss;
        Vec3 c = b.position().add(0, 1, 0);
        double r = 14 + b.getBbWidth();
        int coq = Haki.conquerorLevel(b);
        if (coq > 0) GLNetwork.fx(b, "conqueror_burst", c.x, c.y, c.z, (float) r, 0, 0);
        int power = pressure(b);
        for (LivingEntity e : f.around(c, r)) {
            if (!f.isFoe(e)) continue;
            PowerKit.push(e, b.position(), coq > 0 ? 0.5 : 0.9, 0.3);
            if (coq <= 0) continue;
            if (Haki.conquerorLevel(e) > 0) {
                clash(f, e, now);
                continue;
            }
            int will = Haki.willpower(e);
            if (will * 1.3 < power) {
                e.addEffect(new MobEffectInstance(ModEffects.KNOCKED_OUT, 50, 0));
                if (e instanceof GLNpc n) n.onConquerorKnockout(b);
            } else if (will < power) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
            }
        }
    }

    /**
     * Two Conqueror's Hakis collide: black lightning between them. A will that holds at least 70% of the boss's
     * pressure makes the boss reel (a stagger, which breaks its foresight); a weaker one is overwhelmed and slowed.
     */
    static boolean clash(BossFight f, LivingEntity who, long now) {
        LivingEntity b = f.boss;
        if (Haki.conquerorLevel(b) <= 0 || Haki.conquerorLevel(who) <= 0) return false;
        f.lastClash = now;
        f.clashes++;
        Vec3 a = b.getEyePosition(), p = who.getEyePosition();
        GLNetwork.fx(b, "boss_clash", a.x, a.y, a.z, (float) p.x, (float) p.y, (float) p.z);
        f.level.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.4f, 0.5f);
        PowerKit.push(who, b.position(), 0.7, 0.25);
        boolean holds = pressure(who) >= pressure(b) * 0.7;
        if (holds) {
            CombatState.of(b).staggeredUntil = Math.max(CombatState.of(b).staggeredUntil, now + 30);
            GLNetwork.anim(b, "staggered");
            f.breakForesight(now);
        } else {
            who.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
        }
        if (who instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable(holds ? "grandline.boss.clash.hold" : "grandline.boss.clash.overwhelmed", b.getDisplayName())
                    .withStyle(holds ? ChatFormatting.GOLD : ChatFormatting.DARK_PURPLE));
            Hits.train(sp, Haki.COQ, 6);
        }
        return holds;
    }

    /** A player unleashing their own Conqueror's Haki near the boss starts a clash. */
    static void watchClashes(BossFight f, long now) {
        if (now - f.lastClash < CLASH_COOLDOWN || Haki.conquerorLevel(f.boss) <= 0) return;
        for (LivingEntity e : f.participants.values()) {
            if (!(e instanceof ServerPlayer sp) || Haki.conquerorLevel(sp) <= 0 || sp.distanceToSqr(f.boss) > 24 * 24) continue;
            CombatState s = CombatState.of(sp);
            if (s.lastAbility.startsWith("haki/conqueror") && now - s.lastAbilityAt <= 1) {
                clash(f, sp, now);
                return;
            }
        }
    }

    private BossHaki() {}
}
