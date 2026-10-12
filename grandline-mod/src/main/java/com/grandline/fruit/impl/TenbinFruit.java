package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Tenbin Tenbin no Mi — ORIGINAL scales Paramecia. The user weighs and balances: they make things heavier or lighter,
 * even out the health of two bodies, move their own ailments onto an enemy, and judge a foe by the weight of their
 * crimes (bounty, notoriety, allegiance). Passive: no single blow can tip the scales too far — the part of any hit
 * above 20% of the user's health is halved.
 */
public final class TenbinFruit {

    /** Make something heavy: slowed, unable to stay airborne, dragged down. */
    static void weigh(LivingEntity e, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, amp));
        e.removeEffect(MobEffects.SLOW_FALLING);
        e.removeEffect(MobEffects.LEVITATION);
        if (!e.onGround()) FruitKit.impulse(e, new Vec3(0, -0.9, 0), false);
    }

    /** Make something light. */
    static void lighten(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, true));
        e.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 1, false, true));
        e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks, 0, false, true));
        e.fallDistance = 0;
    }

    public static void init(DevilFruit f) {
        // Weigh down: add weight to a target — slowed and dragged out of the air.
        f.add(f.ability("weigh_down").cooldown(30).stamina(10).anim("palm_strike").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 16);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 6), FruitKit.info(Element.BLUNT, 0f).withPoise(35));
            weigh(t, 80, 2);
            FruitKit.fx(u, "tenbin_weight", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            PowerKit.sound(c, t.position(), SoundEvents.ANVIL_LAND, 0.6f, 0.8f);
            return true;
        }));

        // Featherweight: make the user and nearby allies weightless — fast, high-jumping, falling like feathers.
        f.add(f.ability("featherweight").unlock(5).cooldown(300).stamina(14).anim("focus").exec((u, c) -> {
            int dur = 200 + c.mastery * 2;
            for (LivingEntity a : OriginalKit.allies(u, u.position().add(0, 1, 0), 6, true)) {
                lighten(a, dur);
                FruitKit.fx(u, "tenbin_light", a.position(), a.getBbWidth(), a.getBbHeight(), 0);
            }
            PowerKit.sound(c, u.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.6f);
            return true;
        }));

        // Equilibrium: the scales balance the health of user and target — whoever is healthier pays the difference.
        f.add(f.ability("equilibrium").unlock(15).cooldown(400).stamina(26).anim("double_palm").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 12);
            if (t == null) return false;
            float pu = u.getHealth() / u.getMaxHealth(), pt = t.getHealth() / t.getMaxHealth();
            float avg = (pu + pt) / 2f;
            if (pt > pu + 0.05f) {
                float loss = Math.min((pt - avg) * t.getMaxHealth(), FruitKit.dmg(c, 24));
                float before = t.getHealth();
                Hits.hit(u, t, Math.max(1f, loss), FruitKit.info(Element.PHYSICAL, 0f).withPoise(30).asUnblockable());
                float taken = Math.max(0, before - t.getHealth());
                float gain = Math.min((avg - pu) * u.getMaxHealth(), Math.max(taken, 2f) * 1.5f);
                OriginalKit.heal(u, gain, "tenbin_balance");
            } else {
                // the scales already favour the user: they tip, crushing the target instead
                Hits.hit(u, t, FruitKit.dmg(c, 8), FruitKit.info(Element.BLUNT, 0f).withStun(15).withPoise(50));
                weigh(t, 60, 1);
            }
            FruitKit.fx(u, "tenbin_scales", u.position().add(0, u.getBbHeight() + 0.6, 0), (float) t.getX(), (float) t.getY() + t.getBbHeight(), (float) t.getZ());
            PowerKit.sound(c, u.position(), SoundEvents.BELL_RESONATE, 1f, 1.4f);
            return true;
        }));

        // Balance of Burdens: every ailment on the user moves onto the target, and the target's blessings move over.
        f.add(f.ability("burden_swap").unlock(20).cooldown(300).stamina(24).anim("grab").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 12);
            if (t == null) return false;
            List<MobEffectInstance> ills = OriginalKit.effects(u, MobEffectCategory.HARMFUL);
            List<MobEffectInstance> boons = OriginalKit.effects(t, MobEffectCategory.BENEFICIAL);
            boolean burning = u.isOnFire();
            OriginalKit.cleanse(u);
            for (MobEffectInstance i : boons) t.removeEffect(i.getEffect());
            for (MobEffectInstance i : ills) t.addEffect(i);
            for (MobEffectInstance i : boons) u.addEffect(i);
            if (burning) t.igniteForSeconds(4);
            Hits.hit(u, t, FruitKit.dmg(c, 4 + 2 * Math.min(4, ills.size())), FruitKit.info(Element.PHYSICAL, 0f).withPoise(20).asUnblockable());
            FruitKit.fx(u, "tenbin_scales", u.position().add(0, u.getBbHeight() + 0.6, 0), (float) t.getX(), (float) t.getY() + t.getBbHeight(), (float) t.getZ());
            if (u instanceof ServerPlayer sp)
                sp.displayClientMessage(Component.translatable("grandline.tenbin.swap", ills.size(), boons.size()).withStyle(ChatFormatting.GOLD), true);
            return true;
        }));

        // Scales of Judgement: the heavier the target's crimes (bounty, notoriety, outlaw allegiance), the harder the
        // verdict falls. The innocent are barely touched.
        f.add(f.ability("judgement").unlock(30).cooldown(260).stamina(32).anim("cast_up").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 18);
            if (t == null) return false;
            float w = OriginalKit.crimeWeight(t);
            Hits.Info verdict = FruitKit.info(Element.BLUNT, 1f + w).withPoise(30 + 30 * w).asUnblockable();
            if (w >= 1f) verdict = verdict.withStun((int) (20 * w));
            Hits.hit(u, t, FruitKit.dmg(c, 5 + 7 * w), verdict);
            weigh(t, 40 + (int) (30 * w), Math.min(4, (int) (1 + w * 1.5f)));
            FruitKit.fx(u, "tenbin_judgement", t.position(), t.getBbWidth(), w, 0);
            PowerKit.sound(c, t.position(), SoundEvents.ANVIL_LAND, 1.2f, 0.5f);
            if (u instanceof ServerPlayer sp)
                sp.displayClientMessage(Component.translatable("grandline.tenbin.verdict", t.getDisplayName(), String.format("%.1f", w)).withStyle(ChatFormatting.GOLD), true);
            return true;
        }));

        // Awakening — Absolute Balance: a field where enemies grow crushingly heavy and allies light; when it closes
        // every enemy inside is levelled down to the weakest of them.
        f.add(f.ability("absolute_balance").unlock(55).cooldown(1100).stamina(85).awakened().anim("ground_slam").exec((u, c) -> {
            Vec3 at = u.position();
            double r = 10;
            OriginalKit.pulses(c, 0, 10, 10, n -> {
                FruitKit.fx(u, "tenbin_field", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    weigh(e, 20, 3);
                    float pct = Math.min(FruitKit.dmg(c, 6), Math.max(FruitKit.dmg(c, 1.5f), e.getMaxHealth() * 0.02f));
                    Hits.hit(u, e, pct, FruitKit.info(Element.BLUNT, 0f).withPoise(10));
                }
                for (LivingEntity a : OriginalKit.allies(u, at.add(0, 1, 0), r, true)) lighten(a, 30);
            });
            CombatState.of(u).schedule(c.level.getGameTime() + 100, () -> {
                List<LivingEntity> foes = PowerKit.foes(u, at.add(0, 1, 0), r);
                float lowest = 1f;
                for (LivingEntity e : foes) lowest = Math.min(lowest, e.getHealth() / e.getMaxHealth());
                for (LivingEntity e : foes) {
                    float over = e.getHealth() - lowest * e.getMaxHealth();
                    Hits.hit(u, e, Math.max(FruitKit.dmg(c, 4), Math.min(over, FruitKit.dmg(c, 20))), FruitKit.info(Element.BLUNT, 1f).withStun(20).withPoise(60).asUnblockable());
                }
                FruitKit.fx(u, "tenbin_field", at, (float) r, 1, 0);
                PowerKit.sound(c, at, SoundEvents.BELL_RESONATE, 2f, 0.6f);
            });
            return true;
        }));

        // Passive: equilibrium — the part of any single blow above 20% of the user's health is halved.
        f.incomingHook = (u, a, amount, info) -> {
            float cap = u.getMaxHealth() * 0.2f;
            return amount > cap ? cap + (amount - cap) * 0.5f : amount;
        };
    }

    private TenbinFruit() {}
}
