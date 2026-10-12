package com.grandline.quest;

import com.grandline.npc.GLNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Hooks from gameplay into the quest system. */
public final class QuestEvents {
    public static void onKill(ServerPlayer sp, GLNpc npc) {
        Quests.progress(sp, "kill", npc.role(), 1);
        if (!npc.notableId().isEmpty()) Quests.progress(sp, "defeat", npc.notableId(), 1);
    }

    public static void onDiscover(ServerPlayer sp, String islandId) {
        Quests.progress(sp, "discover", islandId, 1);
    }

    public static void onTalk(ServerPlayer sp, GLNpc npc) {
        Quests.progress(sp, "talk", npc.role(), 1);
        if (!npc.notableId().isEmpty()) Quests.progress(sp, "talk", npc.notableId(), 1);
    }

    public static void onPoneglyph(ServerPlayer sp, BlockPos pos) {
        Quests.progress(sp, "poneglyph", "any", 1);
    }

    public static void onArrested(ServerPlayer sp) {
        Quests.progress(sp, "arrested", "any", 1);
    }

    public static void onCollect(ServerPlayer sp, String item, int count) {
        Quests.progress(sp, "collect", item, count);
    }

    private QuestEvents() {}
}
