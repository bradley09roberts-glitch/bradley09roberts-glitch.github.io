package com.grandline.quest;

import com.grandline.npc.GLNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Hooks from gameplay into the quest system. */
public final class QuestEvents {
    /** player → the last foes credited to them (a foe brought down by a crew member may be reported by more than one hook). */
    private static final java.util.Map<java.util.UUID, java.util.LinkedHashSet<java.util.UUID>> CREDITED = new java.util.HashMap<>();

    /**
     * A foe fell to the player or to their crew (crew kills are reported here for the captain). Each foe counts once
     * per player, whichever hook reports it first.
     */
    public static void onKill(ServerPlayer sp, GLNpc npc) {
        var seen = CREDITED.computeIfAbsent(sp.getUUID(), k -> new java.util.LinkedHashSet<>());
        if (!seen.add(npc.getUUID())) return;
        if (seen.size() > 64) seen.remove(seen.iterator().next());
        Quests.progress(sp, "kill", npc.role(), 1);
        // the awakening trial counts strong foes brought down with the user's own fruit
        var cs = com.grandline.combat.CombatState.of(sp);
        if (npc.tier() >= 6 && cs.lastAbility.startsWith("fruit/") && sp.level().getGameTime() - cs.lastAbilityAt < 100)
            Quests.progress(sp, "fruit_kill", "any", 1);
        if (!npc.notableId().isEmpty()) Quests.progress(sp, "defeat", npc.notableId(), 1);
        com.grandline.career.Careers.onKill(sp, npc); // careers: merit, deeds, bounties collected, missions
    }

    public static void onDiscover(ServerPlayer sp, String islandId) {
        Quests.progress(sp, "discover", islandId, 1);
        if (!islandId.startsWith("any")) com.grandline.career.Careers.onDiscover(sp, islandId); // careers: an explorer's chart
    }

    public static void onTalk(ServerPlayer sp, GLNpc npc) {
        Quests.progress(sp, "talk", npc.role(), 1);
        if (!npc.notableId().isEmpty()) Quests.progress(sp, "talk", npc.notableId(), 1);
        com.grandline.career.Careers.onTalk(sp, npc); // careers: missions that rouse an island
        com.grandline.quest.story.SeaJobs.onTalk(sp, npc); // an escorted merchant brought to the dock
    }

    /**
     * An NPC ship of the living sea that this player fought was beaten or sunk (LivingSea.defeated): counts for
     * "sea_battle" objectives whose target is the ship's side ("pirate", "marine", "bounty_hunter", "merchant") or "any".
     */
    public static void onSeaBattleWon(ServerPlayer sp, String faction) {
        Quests.progress(sp, "sea_battle", faction.isEmpty() ? "any" : faction, 1);
    }

    public static void onPoneglyph(ServerPlayer sp, BlockPos pos) {
        Quests.progress(sp, "poneglyph", "any", 1);
        com.grandline.career.Careers.onPoneglyph(sp); // careers: a scholar's rubbing
    }

    public static void onArrested(ServerPlayer sp) {
        Quests.progress(sp, "arrested", "any", 1);
    }

    public static void onCollect(ServerPlayer sp, String item, int count) {
        Quests.progress(sp, "collect", item, count);
    }

    private QuestEvents() {}
}
