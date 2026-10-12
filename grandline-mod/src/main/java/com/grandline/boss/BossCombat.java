package com.grandline.boss;

import com.grandline.combat.CombatState;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.FruitRules;
import com.grandline.fruit.FruitType;
import com.grandline.haki.Haki;
import com.grandline.registry.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/** Boss rules for Haki and crowd control. */
public final class BossCombat {
    /** Harmful effects on a boss last at most this long (seastone keeps its bite a little longer). */
    static final int EFFECT_CAP = 40, SEASTONE_CAP = 200, STAGGER_CAP = 20, AMP_CAP = 1;

    /**
     * Crowd-control resistance, every tick of a fight: never knocked out, harmful effects shortened and weakened,
     * staggers cut short (a stagger still breaks the boss's foresight, see {@link BossFight#breakForesight}).
     */
    static void resist(BossFight f, long now) {
        LivingEntity b = f.boss;
        if (b.hasEffect(ModEffects.KNOCKED_OUT)) b.removeEffect(ModEffects.KNOCKED_OUT);
        if (!b.getActiveEffects().isEmpty()) {
            for (MobEffectInstance inst : new ArrayList<>(b.getActiveEffects())) {
                Holder<MobEffect> h = inst.getEffect();
                if (h.value().getCategory() != MobEffectCategory.HARMFUL) continue;
                int cap = h.is(ModEffects.SEASTONE_DRAIN) ? SEASTONE_CAP : EFFECT_CAP;
                if (inst.endsWithin(cap) && inst.getAmplifier() <= AMP_CAP) continue;
                int dur = inst.isInfiniteDuration() ? cap : Math.min(cap, inst.getDuration());
                b.removeEffect(h);
                b.addEffect(new MobEffectInstance(h, dur, Math.min(AMP_CAP, inst.getAmplifier()), inst.isAmbient(), inst.isVisible()));
            }
        }
        CombatState s = CombatState.of(b);
        if (s.staggeredUntil > now + STAGGER_CAP) s.staggeredUntil = now + STAGGER_CAP;
    }

    /**
     * Observation Haki of a boss in a fight (hook from {@link Haki#tryObservationDodge}). Bosses with advanced
     * Observation (80+) read every blow, even unblockable ones, until a stagger breaks their foresight; no boss can
     * sidestep while staggered. {@code null} = the ordinary rules.
     */
    @Nullable
    public static Boolean observationDodge(LivingEntity victim, LivingEntity attacker, Hits.Info info) {
        BossFight f = BossManager.fightOf(victim);
        if (f == null) return null;
        long now = f.level.getGameTime();
        if (CombatState.of(victim).staggered(now)) return false;
        if (!f.hasForesight()) return null;
        if (!f.foresightActive(now)) return false;
        double chance = 0.30 + (Haki.observationLevel(victim) - 80) * 0.015 - Haki.observationLevel(attacker) * 0.002
                - (Haki.conquerorLevel(attacker) > 0 ? 0.05 : 0);
        chance = Math.max(0.1, Math.min(0.6, chance));
        return victim.getRandom().nextDouble() < chance;
    }

    /**
     * A Logia boss is intangible to blows that never went through the combat system (vanilla melee, arrows): the
     * attacker needs Armament Haki (or seastone, or the sea) just as with {@link FruitRules#intangibleTo}.
     */
    static boolean ignoresVanillaHit(LivingEntity boss, DamageSource src) {
        if (src.typeHolder().unwrapKey().map(k -> k.location().getNamespace().equals("grandline")).orElse(false)) return false; // resolved by Hits already
        if (!(src.getEntity() instanceof LivingEntity attacker) || attacker == boss) return false;
        DevilFruit fruit = FruitRules.fruitOf(boss);
        if (fruit == null || fruit.type != FruitType.LOGIA) return false;
        return FruitRules.intangibleTo(boss, attacker, Hits.Info.physical(0.5f), Haki.armamentActive(attacker));
    }

    private BossCombat() {}
}
