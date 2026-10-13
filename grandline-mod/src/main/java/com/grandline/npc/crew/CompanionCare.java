package com.grandline.npc.crew;

import com.grandline.GrandLine;
import com.grandline.combat.CombatState;
import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.player.Characters;
import com.grandline.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeping companions in the fight, forgivingly.
 * <ul>
 * <li><b>Downed, not dead.</b> A blow that would kill a companion leaves it kneeling at 1 health instead. Downed
 * companions take no damage, and foes stop targeting them. The captain helps one up by holding [use] on it for about
 * 1.5 s ({@link #HELP_UP_TICKS}); a support companion revives it twice as fast as anyone else; and if nobody does, it
 * gets up on its own {@link #GET_UP_AFTER} ticks after the fight with {@link #GET_UP_HEALTH} of its health. Only
 * commands (/kill) can end a companion; the void puts it back next to the captain.</li>
 * <li><b>Healing</b> (support role): a song pulse restoring {@link #HEAL_SHARE} of the most hurt ally's health
 * (captain or crewmate within {@link #HEAL_RADIUS} blocks), every {@link #HEAL_COOLDOWN} ticks at most and at most
 * {@link #HEALS_PER_FIGHT} times per fight. Bond rank 3 adds Encore: once per fight, every ally nearby recovers 10 %.</li>
 * <li><b>Rest.</b> Out of a fight a companion recovers 2 % of its health every two seconds.</li>
 * <li><b>No friendly fire.</b> Crew blows never hurt the captain or a crewmate (CrewManager.friendlyFire); the side
 * effects of such a blow that the shared hit code applies after the damage (knockback, slowness, fire, stagger)
 * are undone here too.</li>
 * </ul>
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class CompanionCare {
    /** Ticks a companion needs at the captain's side to pull a knocked-down captain (or a downed crewmate) up. */
    public static final int REVIVE_TICKS = 50, SUPPORT_REVIVE_TICKS = 25;
    /** The captain's hold of [use] on a downed companion (interactions repeat every 4 ticks while held). */
    public static final int HELP_UP_TICKS = 30;
    /** A downed companion gets up on its own this long after the fight is over. */
    public static final int GET_UP_AFTER = 200;
    public static final float GET_UP_HEALTH = 0.30f, REVIVED_HEALTH = 0.35f;
    public static final int HEAL_COOLDOWN = 600, HEALS_PER_FIGHT = 3;
    public static final float HEAL_SHARE = 0.25f, HEAL_RADIUS = 8, ENCORE_SHARE = 0.10f;

    private CompanionCare() {}

    public static boolean isDowned(GLNpc npc) {
        CrewManager.Member m = CrewManager.memberOf(npc);
        return m != null && m.downed;
    }

    // ================================================================== downed

    static void goDown(GLNpc npc, CrewManager.Member m, @Nullable Entity by) {
        long now = npc.level().getGameTime();
        m.downed = true;
        m.downedSince = now;
        m.quietSince = now;
        m.reviveProgress = 0;
        m.focusId = -1;
        npc.setHealth(1f);
        npc.setTarget(null);
        npc.getNavigation().stop();
        npc.clearFire();
        CombatState.of(npc).blocking = false;
        knockedOut(npc);
        GLNetwork.anim(npc, "knocked_down");
        releaseFoes(npc);
        ServerPlayer cap = CrewManager.captainOf(npc);
        if (cap != null) cap.sendSystemMessage(Component.translatable("grandline.crew.downed", npc.getDisplayName()).withStyle(ChatFormatting.RED));
        CrewManager.data(npc.getServer()).setDirty();
    }

    /** Back on their feet with at least {@code share} of their health. */
    public static void standUp(GLNpc npc, CrewManager.Member m, float share, boolean announce) {
        m.downed = false;
        m.reviveProgress = 0;
        npc.removeEffect(com.grandline.registry.ModEffects.KNOCKED_OUT);
        npc.setHealth(Math.max(npc.getHealth(), npc.getMaxHealth() * share));
        GLNetwork.anim(npc, "get_up");
        npc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1, false, false)); // a moment to find their feet
        ServerPlayer cap = CrewManager.captainOf(npc);
        if (announce && cap != null) cap.sendSystemMessage(Component.translatable("grandline.crew.back_up", npc.getDisplayName()).withStyle(ChatFormatting.GREEN));
        if (npc.getServer() != null) CrewManager.data(npc.getServer()).setDirty();
    }

    /**
     * The shared "knocked out" marker while downed (kept topped up): every other system already treats such an NPC as
     * out of action (no moving, no blows or techniques, not counted as a fighter by bosses and ship battles).
     */
    private static void knockedOut(GLNpc npc) {
        npc.addEffect(new MobEffectInstance(com.grandline.registry.ModEffects.KNOCKED_OUT, 40, 0, false, false));
    }

    /** Foes forget a downed companion: it is no longer worth a blow. */
    static void releaseFoes(GLNpc npc) {
        for (Mob mob : npc.level().getEntitiesOfClass(Mob.class, npc.getBoundingBox().inflate(32), x -> x.getTarget() == npc || x.getLastHurtByMob() == npc)) {
            if (mob.getTarget() == npc) mob.setTarget(null);
            if (mob.getLastHurtByMob() == npc) mob.setLastHurtByMob(null);
        }
    }

    /**
     * The captain holds [use] on a downed companion: each interaction adds 4 ticks (the client repeats them every 4
     * ticks while the key is held); a pause of more than 12 ticks starts over.
     *
     * @return true when the companion is back up
     */
    public static boolean helpUp(ServerPlayer sp, GLNpc npc, long now) {
        CrewManager.Member m = CrewManager.memberOf(npc);
        if (m == null || !m.downed || !CrewManager.isCrewOf(npc, sp)) return false;
        if (now - m.lastRevivePoke > 12) m.reviveProgress = 0;
        m.lastRevivePoke = now;
        m.reviveProgress += 4;
        int pct = Math.min(100, m.reviveProgress * 100 / HELP_UP_TICKS);
        sp.displayClientMessage(Component.translatable("grandline.crew.helping_up", npc.getDisplayName(), pct).withStyle(ChatFormatting.GREEN), true);
        if (m.reviveProgress < HELP_UP_TICKS) return false;
        standUp(npc, m, REVIVED_HEALTH, false);
        sp.sendSystemMessage(Component.translatable("grandline.crew.helped_up", npc.getDisplayName()).withStyle(ChatFormatting.GREEN));
        return true;
    }

    /** Out of the void (or lava) and back next to the captain, or home. */
    static void rescue(GLNpc npc, CrewManager.Member m) {
        ServerPlayer cap = CrewManager.captainOf(npc);
        npc.setDeltaMovement(Vec3.ZERO);
        npc.fallDistance = 0;
        npc.clearFire();
        if (cap != null && cap.level() == npc.level() && Companions.teleportNear(npc, cap)) return;
        BlockPos anchor = m.holdPos() != null ? m.holdPos() : !npc.home().equals(BlockPos.ZERO) ? npc.home() : null;
        if (anchor != null && npc.level() instanceof ServerLevel sl) {
            BlockPos spot = NotableSpawner.findStand(sl, anchor.getX(), anchor.getZ(), 16);
            if (spot != null) {
                npc.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                return;
            }
        }
        npc.teleportTo(npc.getX(), npc.level().getMaxBuildHeight() - 2, npc.getZ()); // last resort: out of the void, slow fall below
        npc.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0, false, false));
    }

    /** Every half second for a member with a body: downed timers, rest, hazards. */
    static void upkeep(HumanoidNpc npc, CrewManager.Member m, @Nullable ServerPlayer cap, long now) {
        boolean fight = cap != null && Companions.inCombat(cap) && Companions.near(npc, cap, 48);
        if (m.downed) {
            if (npc.getHealth() > 1f) npc.setHealth(1f);
            knockedOut(npc);
            npc.getNavigation().stop();
            npc.setTarget(null);
            releaseFoes(npc);
            if (npc.isInLava() || npc.getY() < npc.level().getMinBuildHeight()) rescue(npc, m);
            if ((now - m.downedSince) % 100 < 10) GLNetwork.anim(npc, "knocked_down"); // players who arrive later see them kneel too
            if (fight || m.quietSince < m.downedSince) m.quietSince = now;
            if (now - m.quietSince >= GET_UP_AFTER) standUp(npc, m, GET_UP_HEALTH, true);
            return;
        }
        if (!fight && npc.getTarget() == null && npc.getHealth() < npc.getMaxHealth() && (now / 10) % 4 == 0) npc.heal(npc.getMaxHealth() * 0.02f);
    }

    // ================================================================== healing (support role)

    /** The ally within the heal radius with the lowest share of health below {@code below}, or null. */
    @Nullable
    static LivingEntity mostHurtAlly(GLNpc healer, ServerPlayer cap, float below) {
        LivingEntity best = null;
        float bestShare = below;
        List<LivingEntity> allies = new ArrayList<>(Companions.activeBodies(cap));
        if (!Characters.get(cap).isKnockedDown() && cap.isAlive()) allies.add(cap);
        for (LivingEntity a : allies) {
            if (a.level() != healer.level() || a.distanceToSqr(healer) > HEAL_RADIUS * HEAL_RADIUS || !a.isAlive()) continue;
            if (a instanceof GLNpc g && isDowned(g)) continue;
            float share = a.getHealth() / Math.max(1f, a.getMaxHealth());
            if (share < bestShare) {
                bestShare = share;
                best = a;
            }
        }
        return best;
    }

    /**
     * A support companion's song: the most hurt ally nearby (below 75 %) recovers a quarter of its health. Only in a
     * fight, at most {@link #HEALS_PER_FIGHT} times per fight and once per {@link #HEAL_COOLDOWN} ticks.
     */
    public static boolean trySupportHeal(GLNpc npc, ServerPlayer cap, long now) {
        CrewManager.Member m = CrewManager.memberOf(npc);
        if (m == null || m.downed || !m.active || CompanionRole.of(npc) != CompanionRole.SUPPORT) return false;
        if (!Companions.inCombat(cap) || m.fightHeals >= HEALS_PER_FIGHT || now < m.healReadyAt) return false;
        LivingEntity t = mostHurtAlly(npc, cap, 0.75f);
        if (t == null) return false;
        float amount = t.getMaxHealth() * HEAL_SHARE;
        t.heal(amount);
        m.fightHeals++;
        m.healReadyAt = now + HEAL_COOLDOWN;
        song(npc, t);
        cap.displayClientMessage(Component.translatable("grandline.crew.healed", npc.getDisplayName(), t.getDisplayName(), Math.round(amount),
                HEALS_PER_FIGHT - m.fightHeals).withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    /** Bond rank 3, support: once per fight, when two or more allies are below 60 %, everyone nearby recovers 10 %. */
    public static boolean tryEncore(GLNpc npc, ServerPlayer cap, long now) {
        CrewManager.Member m = CrewManager.memberOf(npc);
        if (m == null || m.downed || m.encoreUsed || m.bondRank() < 3 || CompanionRole.of(npc) != CompanionRole.SUPPORT || !Companions.inCombat(cap)) return false;
        List<LivingEntity> allies = new ArrayList<>(Companions.activeBodies(cap));
        if (!Characters.get(cap).isKnockedDown()) allies.add(cap);
        allies.removeIf(a -> a.level() != npc.level() || a.distanceToSqr(npc) > HEAL_RADIUS * HEAL_RADIUS || a instanceof GLNpc g && isDowned(g));
        int hurt = 0;
        for (LivingEntity a : allies) if (a.getHealth() < a.getMaxHealth() * 0.6f) hurt++;
        if (hurt < 2) return false;
        for (LivingEntity a : allies) a.heal(a.getMaxHealth() * ENCORE_SHARE);
        m.encoreUsed = true;
        song(npc, npc);
        cap.displayClientMessage(Component.translatable("grandline.crew.encore", npc.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    private static void song(GLNpc npc, LivingEntity to) {
        if (!(npc.level() instanceof ServerLevel sl)) return;
        GLNetwork.anim(npc, "cheer");
        sl.playSound(null, npc.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1f, 1.2f);
        sl.playSound(null, to.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 0.8f, 1.0f);
        sl.sendParticles(ParticleTypes.NOTE, npc.getX(), npc.getY() + 2.2, npc.getZ(), 4, 0.4, 0.2, 0.4, 1);
        sl.sendParticles(ParticleTypes.HEART, to.getX(), to.getY() + to.getBbHeight() + 0.3, to.getZ(), 3, 0.3, 0.2, 0.3, 0);
    }

    // ================================================================== events

    /** Crew members hit by their own side this tick (their friendly-fire side effects are undone at the end of the tick). */
    private static final List<LivingEntity> RESIDUE = new ArrayList<>();
    private static long residueTick = -1;

    private static boolean residue(LivingEntity e) {
        return residueTick == e.level().getGameTime() && RESIDUE.contains(e);
    }

    /** Before the story layer's yield rule (LOW): friendly fire, then downed instead of dead. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof GLNpc npc) || npc.level().isClientSide) return;
        CrewManager.Member m = CrewManager.memberOf(npc);
        if (m == null) return;
        DamageSource src = e.getSource();
        if (CrewManager.friendlyFire(npc, src.getEntity())) {
            e.setCanceled(true);
            long now = npc.level().getGameTime();
            if (residueTick != now) {
                RESIDUE.clear();
                residueTick = now;
            }
            if (!RESIDUE.contains(npc)) RESIDUE.add(npc);
            return;
        }
        if (src.is(DamageTypes.GENERIC_KILL)) return; // commands still work
        if (src.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            e.setCanceled(true);
            rescue(npc, m);
            return;
        }
        if (m.downed) {
            e.setCanceled(true);
            return;
        }
        if (e.getAmount() >= npc.getHealth() + npc.getAbsorptionAmount()) {
            e.setCanceled(true);
            goDown(npc, m, src.getEntity());
        }
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        if (residue(v) || v instanceof GLNpc g && isDowned(g)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || !residue(v)) return;
        if (e.getEffectInstance().getEffect().value().getCategory() == MobEffectCategory.HARMFUL) e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    /** Undo what a blocked friendly blow left behind (fire, stagger): at the end of the tick, or before the victim's next tick at the latest. */
    private static void undoResidue(LivingEntity v) {
        if (!v.isAlive()) return;
        v.clearFire();
        CombatState s = CombatState.of(v);
        long now = v.level().getGameTime();
        if (s.staggeredUntil > now) s.staggeredUntil = now;
        s.poise = Math.max(s.poise, 60);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        if (RESIDUE.isEmpty()) return;
        for (LivingEntity v : RESIDUE) undoResidue(v);
        RESIDUE.clear();
        residueTick = -1;
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre e) {
        if (!RESIDUE.isEmpty() && e.getEntity() instanceof LivingEntity v && RESIDUE.contains(v)) undoResidue(v); // before fire can burn
    }

    /** Foes never pick a downed companion; companions never pick anyone who is not a foe of their party. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent e) {
        LivingEntity who = e.getEntity(), t = e.getNewAboutToBeSetTarget();
        if (t == null || who.level().isClientSide) return;
        if (t instanceof GLNpc g && isDowned(g) && !(who instanceof GLNpc w && CrewManager.allied(w, g))) {
            e.setNewAboutToBeSetTarget(null);
            return;
        }
        if (who instanceof GLNpc npc && CrewManager.isCrew(npc) && !Companions.isFoe(npc, t)) e.setCanceled(true);
    }

    /** Holding [use] on a downed companion helps it up; seastone cuffs never go on your own crew. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide || !(e.getTarget() instanceof GLNpc npc) || !(e.getEntity() instanceof ServerPlayer sp)) return;
        CrewManager.Member m = CrewManager.memberOf(npc);
        if (m == null) return;
        boolean cuffs = sp.getItemInHand(e.getHand()).is(ModItems.SEASTONE_CUFFS.get());
        if (!CrewManager.isCrewOf(npc, sp)) {
            if (m.downed && cuffs) { // nobody arrests a downed companion: it is out of the fight
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.FAIL);
            }
            return;
        }
        if (m.downed) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            if (e.getHand() == InteractionHand.MAIN_HAND) helpUp(sp, npc, npc.level().getGameTime());
            return;
        }
        if (cuffs) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        }
    }
}
