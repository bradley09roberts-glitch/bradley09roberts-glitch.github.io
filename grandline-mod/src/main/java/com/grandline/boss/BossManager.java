package com.grandline.boss;

import com.grandline.GrandLine;
import com.grandline.combat.Abilities;
import com.grandline.combat.Ability;
import com.grandline.combat.AbilityContext;
import com.grandline.combat.Hits;
import com.grandline.fruit.FruitRules;
import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.PrisonSystem;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.registry.ModEffects;
import com.grandline.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Turns the strongest of the story cast into boss fights ({@link Bosses}). Tracks boss entities, starts a
 * {@link BossFight} when one is engaged, ticks the fights, and applies the boss rules to damage, effects and
 * knockdowns. Hooks used by shared code: {@link #hostility} (who the boss fights), {@link #busy} (the ordinary
 * combat AI waits during a wind-up), {@link #deferKnockdown} (no execution or arrest while others still fight) and
 * {@link BossCombat#observationDodge} (foresight).
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class BossManager {
    private static final Map<UUID, BossFight> FIGHTS = new LinkedHashMap<>();
    private static final Map<UUID, HumanoidNpc> BOSSES = new LinkedHashMap<>();
    /** Exceptions caught while running fights (logged; the self-test asserts there are none). */
    static final List<String> ERRORS = Collections.synchronizedList(new ArrayList<>());
    private static final ResourceLocation STURDY = GrandLine.id("boss_sturdy");

    // ------------------------------------------------------------------ queries

    @Nullable
    public static BossDef defOf(@Nullable Entity e) {
        return e instanceof HumanoidNpc n && !n.notableId().isEmpty() ? Bosses.get(n.notableId()) : null;
    }

    public static boolean isBoss(@Nullable Entity e) {
        return defOf(e) != null;
    }

    /** The running fight of a boss entity (null for anything that is not a boss in a fight). */
    @Nullable
    public static BossFight fightOf(@Nullable LivingEntity e) {
        if (e == null) return null;
        BossFight f = FIGHTS.get(e.getUUID());
        return f == null || f.ended || f.boss != e ? null : f;
    }

    /** Start (or get) the fight of a boss. */
    @Nullable
    public static BossFight start(HumanoidNpc boss) {
        BossDef def = defOf(boss);
        if (def == null || !(boss.level() instanceof ServerLevel) || !boss.isAlive()) return null;
        BossFight f = FIGHTS.get(boss.getUUID());
        if (f != null && !f.ended && f.boss == boss) return f;
        f = new BossFight(boss, def);
        FIGHTS.put(boss.getUUID(), f);
        if (f.phase > 1) f.enterPhase(f.phase); // a wounded boss picks up where it left off
        return f;
    }

    static void forget(BossFight f) {
        FIGHTS.remove(f.boss.getUUID(), f);
    }

    public static Collection<BossFight> fights() {
        return Collections.unmodifiableCollection(FIGHTS.values());
    }

    public static List<String> errors() {
        return List.copyOf(ERRORS);
    }

    static void error(String what, Throwable t) {
        if (ERRORS.size() >= 50) return; // a fault that repeats every tick is reported once, not forever
        ERRORS.add(what + ": " + t);
        GrandLine.LOGGER.error("[bosses] {}", what, t);
    }

    // ------------------------------------------------------------------ hooks for shared code

    /** Everyone engaged in a boss's fight is its foe ({@code null} = the usual rules). */
    @Nullable
    public static Boolean hostility(GLNpc npc, LivingEntity e) {
        BossFight f = FIGHTS.get(npc.getUUID());
        if (f == null || f.ended || f.boss != npc || !f.isParticipant(e)) return null;
        if (e instanceof ServerPlayer sp && PrisonSystem.isJailed(sp)) return null;
        return Boolean.TRUE;
    }

    /** A boss winding up a signature attack or changing phase: its ordinary combat AI waits. */
    public static boolean busy(GLNpc npc) {
        BossFight f = FIGHTS.get(npc.getUUID());
        return f != null && !f.ended && f.boss == npc && f.busy();
    }

    /**
     * A boss knocked a player down: while anyone else is still fighting it, it neither finishes, robs nor arrests the
     * downed player, it turns to the next foe (allies can revive). The usual outcome waits until nobody is left.
     */
    public static boolean deferKnockdown(GLNpc npc, ServerPlayer sp) {
        BossFight f = FIGHTS.get(npc.getUUID());
        if (f == null || f.ended || f.boss != npc) return false;
        f.engage(sp);
        if (!f.otherPlayersFighting(sp)) return false;
        f.deferred.put(sp.getUUID(), sp);
        sp.sendSystemMessage(Component.translatable("grandline.boss.moves_on", npc.getDisplayName()).withStyle(ChatFormatting.YELLOW));
        return true;
    }

    /** Run an existing ability as the boss (no cooldown or stamina; full mastery). */
    static boolean runAbility(BossFight f, String id, @Nullable LivingEntity target) {
        Ability a = Abilities.get(id);
        if (a == null) {
            ERRORS.add("unknown ability " + id + " for boss " + f.def.id);
            return false;
        }
        AbilityContext ctx = new AbilityContext(f.level, f.boss, null, target, 100, true, Hits.abilityPower(f.boss));
        try {
            if (!a.anim().isEmpty()) GLNetwork.anim(f.boss, a.anim());
            return a.exec().run(f.boss, ctx);
        } catch (RuntimeException ex) {
            error("ability " + id + " of " + f.def.id, ex);
            return false;
        }
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof HumanoidNpc n) || defOf(n) == null) return;
        BOSSES.put(n.getUUID(), n);
        AttributeInstance kb = n.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.addOrUpdateTransientModifier(new AttributeModifier(STURDY, 0.75, AttributeModifier.Operation.ADD_VALUE));
    }

    /** Leaving the world: an arrest pays out (cuffed and taken away); anything else just closes the fight. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof HumanoidNpc n)) return;
        BossDef def = defOf(n);
        if (def == null) return;
        BOSSES.remove(n.getUUID(), n);
        BossFight f = FIGHTS.get(n.getUUID());
        if (n.getRemovalReason() == Entity.RemovalReason.DISCARDED && n.flag(GLNpc.FLAG_ARRESTED)) {
            Player by = n.level().getNearestPlayer(n, 8);
            BossRewards.defeated(n, def, by, "arrest", f);
        }
        if (f != null && f.boss == n) f.end(false);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        if (BOSSES.isEmpty() && FIGHTS.isEmpty()) return;
        for (HumanoidNpc b : new ArrayList<>(BOSSES.values())) {
            if (b.isRemoved() || !b.isAlive() || FIGHTS.containsKey(b.getUUID()) || b.flag(GLNpc.FLAG_SURRENDER)) continue;
            LivingEntity t = b.getTarget();
            if (t != null && t.isAlive() && !(t instanceof Player p && (p.isCreative() || p.isSpectator()))) start(b);
        }
        for (BossFight f : new ArrayList<>(FIGHTS.values())) {
            try {
                f.tick(f.level.getGameTime());
            } catch (RuntimeException ex) {
                error("fight of " + f.def.id, ex);
                f.current = null;
                f.telegraphs.clear();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        DamageSource src = e.getSource();
        if (v instanceof ServerPlayer sp) {
            protectDowned(e, sp);
            return;
        }
        if (!(v instanceof HumanoidNpc boss) || defOf(boss) == null || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Entity by = src.getEntity();
        BossFight f = FIGHTS.get(boss.getUUID());
        if (by instanceof LivingEntity le && le != boss && !boss.flag(GLNpc.FLAG_SURRENDER)) {
            if (f == null || f.ended) f = start(boss);
            if (f != null && !(le instanceof Player p && p.isCreative())) f.engage(le);
        }
        if (f == null || f.ended || f.boss != boss) return;
        if (f.transitioning()) { // the roar between phases: untouchable
            e.setCanceled(true);
            return;
        }
        if (BossCombat.ignoresVanillaHit(boss, src)) {
            e.setCanceled(true);
            FruitRules.intangibleFx(boss);
            return;
        }
        float amount = e.getAmount() / f.healthScale();
        // before the last phase nothing can bring the boss down in one blow (the threshold clamp follows after armour)
        if (f.def.threshold(f.phase) > 0 && amount >= boss.getHealth()) amount = Math.max(0, boss.getHealth() - 1);
        e.setAmount(amount);
    }

    /** Phase thresholds: damage that would cross one stops on it and starts the transition. */
    @SubscribeEvent
    public static void onDamagePre(LivingDamageEvent.Pre e) {
        if (!(e.getEntity() instanceof HumanoidNpc boss) || boss.level().isClientSide) return;
        BossFight f = FIGHTS.get(boss.getUUID());
        if (f == null || f.ended || f.boss != boss || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (f.transitioning()) {
            e.setNewDamage(0);
            return;
        }
        float thr = f.def.threshold(f.phase);
        if (thr <= 0) return;
        float floor = thr * boss.getMaxHealth();
        if (boss.getHealth() - e.getNewDamage() < floor) {
            e.setNewDamage(Math.max(0, boss.getHealth() - floor));
            f.beginTransition(f.phase + 1);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof HumanoidNpc boss) || boss.level().isClientSide) return;
        BossDef def = defOf(boss);
        if (def == null) return;
        BossFight f = FIGHTS.get(boss.getUUID());
        try {
            BossRewards.defeated(boss, def, e.getSource().getEntity(), "death", f != null && f.boss == boss ? f : null);
        } catch (RuntimeException ex) {
            error("rewards of " + def.id, ex);
        }
        if (f != null && f.boss == boss) f.end(false);
    }

    /** Bosses cannot be knocked out (Conqueror's Haki, sleep): they reel instead (see {@link BossCombat#resist}). */
    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable e) {
        if (e.getEffectInstance().getEffect().is(ModEffects.KNOCKED_OUT) && isBoss(e.getEntity()))
            e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    /** Seastone cuffs only close on a boss that is beaten down (a quarter of its health), yielded or out cold. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide() || !(e.getTarget() instanceof HumanoidNpc boss) || !isBoss(boss)) return;
        if (!e.getItemStack().is(ModItems.SEASTONE_CUFFS.get())) return;
        boolean beaten = boss.getHealth() <= boss.getMaxHealth() * 0.25f || boss.flag(GLNpc.FLAG_SURRENDER);
        if (beaten) return;
        e.setCanceled(true);
        e.getEntity().displayClientMessage(Component.translatable("grandline.boss.cuffs_resist", boss.getDisplayName()).withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        for (BossFight f : new ArrayList<>(FIGHTS.values())) f.end(false);
        FIGHTS.clear();
        BOSSES.clear();
    }

    /** While anyone else still fights the boss, a downed participant is safe from the boss and its arena. */
    private static void protectDowned(LivingIncomingDamageEvent e, ServerPlayer sp) {
        if (FIGHTS.isEmpty()) return;
        CharacterData d = Characters.get(sp);
        if (!d.created || !d.isKnockedDown()) return;
        DamageSource src = e.getSource();
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Entity by = src.getEntity();
        for (BossFight f : FIGHTS.values()) {
            if (f.ended || !f.isParticipant(sp) || !f.otherPlayersFighting(sp)) continue;
            boolean arena = by == null && (src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypes.HOT_FLOOR) || src.is(DamageTypeTags.IS_FREEZING));
            if (by == f.boss || arena) {
                e.setCanceled(true);
                return;
            }
        }
    }

    private BossManager() {}
}
