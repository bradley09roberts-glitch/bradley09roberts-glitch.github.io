package com.grandline.npc;

import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.player.Knockdown;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Consequences of fights: bounty, reputation, XP, arrests, robbery, reinforcements. */
public final class NpcOutcomes {

    public static void callForHelp(GLNpc npc, LivingEntity threat) {
        for (GLNpc ally : npc.level().getEntitiesOfClass(GLNpc.class, npc.getBoundingBox().inflate(24), n -> n != npc && n.faction() == npc.faction())) {
            if (ally.getTarget() == null && ally.archetype().hostility() != Archetype.Hostility.PASSIVE) ally.setTarget(threat);
        }
    }

    /** Marines arrest a downed player: confiscation is waived (agreed: gear stays with you), you're jailed instead. */
    public static void arrestPlayer(GLNpc npc, ServerPlayer sp, CharacterData d) {
        BlockPos cell = PrisonSystem.findCell(sp.serverLevel(), npc.blockPosition());
        sp.stopRiding(); // off the ship's helm/cannon and into the cell
        Knockdown.standUp(sp, d, 0.3f);
        d.flags.add("survived_knockdown");
        d.flags.add("imprisoned");
        d.flags.removeIf(f -> f.startsWith("cell:"));
        if (cell != null) {
            d.flags.add("cell:" + cell.getX() + "," + cell.getY() + "," + cell.getZ());
            sp.teleportTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
        }
        // the guards stand down: a prisoner in a cell is no longer a target until they break out
        for (GLNpc g : sp.serverLevel().getEntitiesOfClass(GLNpc.class, sp.getBoundingBox().inflate(64), n -> n.getTarget() == sp || n.getLastHurtByMob() == sp)) {
            g.setTarget(null);
            g.setLastHurtByMob(null);
        }
        sp.sendSystemMessage(Component.translatable("grandline.arrested", npc.getDisplayName()).withStyle(ChatFormatting.RED));
        com.grandline.quest.QuestEvents.onArrested(sp);
        CharacterManager.markDirty(sp);
    }

    public static void npcArrested(GLNpc npc, ServerPlayer by) {
        CharacterData d = Characters.get(by);
        // "arrest" objectives name a role ("pirate_captain"), a faction ("pirate") or a notable
        com.grandline.quest.Quests.progress(by, "arrest", npc.role(), 1);
        String faction = npc.faction().name().toLowerCase(java.util.Locale.ROOT);
        if (!faction.equals(npc.role())) com.grandline.quest.Quests.progress(by, "arrest", faction, 1);
        if (!npc.notableId().isEmpty()) com.grandline.quest.Quests.progress(by, "arrest", npc.notableId(), 1);
        long reward = rewardFor(npc);
        if (npc.faction() == Faction.PIRATE || npc.faction() == Faction.WILD) {
            reward += com.grandline.career.Careers.onArrest(by, d, npc, reward); // careers: Marine merit, a hunter's licence bonus
            d.beli += reward;
            d.addReputation(Faction.MARINE.id(), 8);
            by.sendSystemMessage(Component.translatable("grandline.bounty.collected", reward).withStyle(ChatFormatting.GOLD));
        }
        npc.discard();
        CharacterManager.markDirty(by);
    }

    /** Players outlaws have just robbed: the gang leaves them alone until this game time. And when each was last robbed. */
    private static final java.util.Map<java.util.UUID, Long> SPARED = new java.util.concurrent.ConcurrentHashMap<>(), ROBBED = new java.util.concurrent.ConcurrentHashMap<>();
    public static final int SPARE_TICKS = 20 * 60, ROB_COOLDOWN = 20 * 180;

    public static boolean spared(ServerPlayer sp) {
        return SPARED.getOrDefault(sp.getUUID(), 0L) > sp.level().getGameTime();
    }

    /** Whether outlaws may take Beli now (once per three minutes); records the robbery. */
    public static boolean mayRob(ServerPlayer sp) {
        long now = sp.level().getGameTime();
        if (ROBBED.getOrDefault(sp.getUUID(), Long.MIN_VALUE / 2) + ROB_COOLDOWN > now) return false;
        ROBBED.put(sp.getUUID(), now);
        return true;
    }

    /** The robbers and every outlaw near them stop fighting the player and leave them be for a minute. */
    public static void spare(ServerPlayer sp) {
        SPARED.put(sp.getUUID(), sp.level().getGameTime() + SPARE_TICKS);
        for (GLNpc n : sp.serverLevel().getEntitiesOfClass(GLNpc.class, sp.getBoundingBox().inflate(48),
                n -> n.faction() == Faction.WILD || n.faction() == Faction.PIRATE)) {
            if (n.getTarget() == sp) n.setTarget(null);
            if (n.getLastHurtByMob() == sp) n.setLastHurtByMob(null);
        }
    }

    public static long rewardFor(GLNpc npc) {
        return (long) (Math.pow(npc.tier(), 2.3) * 400);
    }

    public static void onNpcDeath(GLNpc npc, DamageSource src, ServerLevel level) {
        if (!(src.getEntity() instanceof ServerPlayer sp)) {
            PopulationManager.onNpcGone(level, npc);
            return;
        }
        CharacterData d = Characters.get(sp);
        int xp = 20 + npc.tier() * npc.tier() * 12;
        int levels = d.addXp(xp);
        if (levels > 0) {
            sp.sendSystemMessage(Component.translatable("grandline.level.up", d.level).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            CharacterManager.applyAttributes(sp);
        }
        // reputation & bounty
        Faction f = npc.faction();
        d.addReputation(f.id(), -Math.max(2, npc.tier() * 3));
        if (!npc.islandId().isEmpty()) d.addReputation("island/" + npc.islandId(), f == Faction.CIVILIAN ? -40 : -5);
        long bountyGain = 0;
        if (Faction.isLaw(f)) bountyGain = (long) (Math.pow(npc.tier(), 2.4) * 1500);
        else if (f == Faction.CIVILIAN) bountyGain = 5000L * npc.tier();
        if (f == Faction.PIRATE || f == Faction.WILD) {
            long reward = rewardFor(npc);
            d.beli += reward / 2;
            d.addReputation(Faction.CIVILIAN.id(), 1);
            if (!npc.islandId().isEmpty()) d.addReputation("island/" + npc.islandId(), 3);
        }
        if (bountyGain > 0) Bounties.raise(sp, d, bountyGain, Component.translatable("role.grandline." + npc.role()));
        com.grandline.quest.QuestEvents.onKill(sp, npc);
        PopulationManager.onNpcGone(level, npc);
        CharacterManager.markDirty(sp);
    }

    private NpcOutcomes() {}
}
