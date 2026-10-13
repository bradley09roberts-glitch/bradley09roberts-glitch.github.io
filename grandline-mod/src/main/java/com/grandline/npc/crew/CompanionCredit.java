package com.grandline.npc.crew;

import com.grandline.GrandLine;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What companions earn for their captain, and what they earn themselves.
 * <ul>
 * <li><b>Kills count for the captain.</b> An NPC felled by a companion is credited exactly as if the captain had felled
 * it (NpcOutcomes.onNpcDeath asks {@link #captainFor}): quest and career objectives (QuestEvents.onKill, once: the
 * final blow decides who is credited, so a foe both struck is never counted twice), reputation, bounty and the reward
 * for outlaws. Experience: all of it when the captain also struck the foe in the last five seconds, half otherwise.
 * The captain must be within {@link #CREDIT_RANGE} blocks.</li>
 * <li><b>Bond.</b> Every fight won together (the party felled at least one foe) gives the active companions who were
 * there {@link #FIGHT_BOND} point, every story quest finished with them at the captain's side {@link #QUEST_BOND}.
 * Ranks 1-5 need 0, 3, 8, 15 and 25 points; each rank makes the companion a little stronger
 * (balance/CompanionTuning) and rank 3 unlocks its role technique.</li>
 * </ul>
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class CompanionCredit {
    public static final float SOLO_XP_SHARE = 0.5f;
    public static final int FIGHT_BOND = 1, QUEST_BOND = 2;
    public static final double CREDIT_RANGE = 64;

    private CompanionCredit() {}

    /** The player credited for an NPC felled by {@code killer} when the killer is a companion near its captain, else null. */
    @Nullable
    public static ServerPlayer captainFor(GLNpc dead, @Nullable Entity killer) {
        if (!(killer instanceof GLNpc g) || !CrewManager.isCrew(g)) return null;
        ServerPlayer cap = CrewManager.captainOf(g);
        if (cap == null || cap.level() != dead.level() || cap.distanceToSqr(dead) > CREDIT_RANGE * CREDIT_RANGE) return null;
        return cap;
    }

    /** Share of the kill experience the captain gets for a companion's kill. */
    public static float xpShare(GLNpc dead, ServerPlayer cap) {
        return dead.getKillCredit() == cap ? 1f : SOLO_XP_SHARE;
    }

    /** Count the party's kills for the fight in progress (fights won raise the bond). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead.level().isClientSide || e.isCanceled()) return;
        Entity k = e.getSource().getEntity();
        ServerPlayer cap = k instanceof ServerPlayer sp ? sp : k instanceof GLNpc g && CrewManager.isCrew(g) ? CrewManager.captainOf(g) : null;
        if (cap == null || CrewManager.crewOf(cap) == null) return;
        if (dead instanceof GLNpc g2 && CrewManager.isCrewOf(g2, cap)) return;
        Companions.markCombat(cap, cap.level().getGameTime()).kills++;
    }

    static void fightWon(ServerPlayer cap, CrewManager.Crew crew) {
        for (CrewManager.Member m : List.copyOf(crew.members.values())) {
            if (!m.active || m.stowed != null) continue;
            HumanoidNpc b = CrewManager.body(cap.server, m.id);
            if (b != null && Companions.near(b, cap, 48)) addBond(cap, m, FIGHT_BOND);
        }
    }

    /** Every few seconds: story quests the captain finished since the last look, with the active companions present. */
    static void questBond(ServerPlayer cap, CrewManager.Crew crew) {
        CharacterData d = Characters.get(cap);
        int n = d.completedQuests.size();
        if (crew.questsSeen < 0 || n < crew.questsSeen) {
            crew.questsSeen = n;
            CrewManager.data(cap.server).setDirty();
            return;
        }
        if (n == crew.questsSeen) return;
        List<String> done = new ArrayList<>(d.completedQuests); // insertion order: the newest are last
        int story = 0;
        for (int i = crew.questsSeen; i < n; i++) {
            QuestDef q = QuestRegistry.get(done.get(i));
            if (q != null && !q.repeatable() && !QuestRegistry.isGenerated(q.id())) story++;
        }
        crew.questsSeen = n;
        CrewManager.data(cap.server).setDirty();
        if (story == 0) return;
        for (CrewManager.Member m : List.copyOf(crew.members.values())) {
            if (!m.active || m.stowed != null) continue;
            HumanoidNpc b = CrewManager.body(cap.server, m.id);
            if (b != null && Companions.near(b, cap, 48)) addBond(cap, m, QUEST_BOND * story);
        }
    }

    /** Add bond points; announce a new rank (and the role technique at rank 3). */
    public static void addBond(ServerPlayer cap, CrewManager.Member m, int points) {
        int before = m.bondRank();
        m.bond += points;
        int after = m.bondRank();
        CrewManager.data(cap.server).setDirty();
        if (after <= before) return;
        HumanoidNpc b = CrewManager.body(cap.server, m.id);
        if (b != null) com.grandline.balance.CompanionTuning.apply(b);
        var n = NotableCharacters.get(m.id);
        Component name = n == null ? Component.literal(m.id) : NotableCharacters.name(n);
        cap.sendSystemMessage(Component.translatable("grandline.crew.bond_up", name, after).withStyle(ChatFormatting.GOLD));
        if (before < 3 && after >= 3) {
            CompanionRole role = CompanionRole.of(n);
            cap.sendSystemMessage(Component.translatable("grandline.crew.technique_unlocked", name, Component.translatable(role.techniqueKey()),
                    Component.translatable(role.techniqueKey() + ".desc")).withStyle(ChatFormatting.GOLD));
        }
    }
}
