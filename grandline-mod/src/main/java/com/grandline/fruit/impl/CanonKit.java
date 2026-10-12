package com.grandline.fruit.impl;

import com.grandline.combat.AbilityContext;
import com.grandline.combat.CombatState;
import com.grandline.combat.Hits;
import com.grandline.npc.GLNpc;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Helpers shared by the third (canon) wave of fruits: melee targets, timed reverts that run even if one side dies,
 * disabling a target, "forgetting", combat checks and timed size changes.
 */
public final class CanonKit {
    static final List<Holder<MobEffect>> AILMENTS = List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.WEAKNESS, MobEffects.BLINDNESS, MobEffects.HUNGER, MobEffects.CONFUSION, MobEffects.DIG_SLOWDOWN, MobEffects.DARKNESS);

    /** Lock-on target within reach, else the nearest hostile in a frontal cone. */
    @Nullable
    public static LivingEntity melee(AbilityContext c, double reach) {
        if (c.target != null && c.target.isAlive() && c.target.distanceTo(c.user) <= reach) return c.target;
        for (LivingEntity e : Hits.cone(c.user, reach, 80)) if (Hits.hostileTo(c.user, e)) return e;
        return null;
    }

    /** Run {@code r} once after {@code ticks}; scheduled on both entities so it still happens if either one dies. */
    public static void later(LivingEntity a, @Nullable LivingEntity b, int ticks, Runnable r) {
        AtomicBoolean done = new AtomicBoolean();
        Runnable once = () -> {
            if (done.compareAndSet(false, true)) r.run();
        };
        long at = a.level().getGameTime() + ticks;
        CombatState.of(a).schedule(at, once);
        if (b != null && b != a) CombatState.of(b).schedule(at, once);
    }

    /** Stop an entity from attacking or using techniques for a while, and slow it. */
    public static void disable(LivingEntity e, int ticks, int slow) {
        CombatState s = CombatState.of(e);
        long now = e.level().getGameTime();
        s.staggeredUntil = Math.max(s.staggeredUntil, now + ticks);
        s.nextAttackAllowed = Math.max(s.nextAttackAllowed, now + ticks);
        s.blocking = false;
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, slow, false, true));
    }

    /** Lift a {@link #disable} early (time travel returns, toys turned back). */
    public static void enable(LivingEntity e) {
        CombatState s = CombatState.of(e);
        long now = e.level().getGameTime();
        s.staggeredUntil = Math.min(s.staggeredUntil, now);
        s.nextAttackAllowed = Math.min(s.nextAttackAllowed, now);
    }

    /** Mobs hunting {@code e} lose track of it, and it forgets its own target. */
    public static void forget(LivingEntity e, double radius) {
        for (Mob m : e.level().getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(radius), m -> m.getTarget() == e)) m.setTarget(null);
        if (e instanceof Mob m) m.setTarget(null);
    }

    /** Fighting right now: has a target, or hit/was hit within the last 10 seconds. */
    public static boolean inCombat(LivingEntity u, @Nullable LivingEntity target) {
        if (target != null && target.isAlive()) return true;
        if (u instanceof Mob m && m.getTarget() != null) return true;
        if (u.getLastHurtByMob() != null && u.tickCount - u.getLastHurtByMobTimestamp() < 200) return true;
        return u.getLastHurtMob() != null && u.tickCount - u.getLastHurtMobTimestamp() < 200;
    }

    /** Observation Haki is up (toggle, Future Sight, or an NPC that fights with it). */
    public static boolean observing(LivingEntity e) {
        CombatState s = CombatState.of(e);
        return s.observation || s.hasBuff("future_sight") || (e instanceof GLNpc n && n.usesObservation());
    }

    /** Temporary multiplicative size change (transient: never saved, so a restart always restores the size). */
    public static void scale(LivingEntity e, ResourceLocation id, double multiplier) {
        AttributeInstance inst = e.getAttribute(Attributes.SCALE);
        if (inst == null) return;
        if (multiplier == 0) inst.removeModifier(id);
        else inst.addOrUpdateTransientModifier(new AttributeModifier(id, multiplier, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        e.refreshDimensions();
    }

    public static void cleanse(LivingEntity e) {
        for (Holder<MobEffect> h : AILMENTS) e.removeEffect(h);
    }

    public static void tell(LivingEntity e, String key, Object... args) {
        if (e instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.GRAY), true);
    }

    /** Health fraction of an entity (0..1). */
    public static float hp(LivingEntity e) {
        return e.getHealth() / Math.max(1f, e.getMaxHealth());
    }

    private CanonKit() {}
}
