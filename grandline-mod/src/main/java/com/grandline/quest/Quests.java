package com.grandline.quest;

import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Quest state machine. Definitions come from {@link QuestRegistry} (data/grandline/quests/*.json + code).
 * Progress per objective is stored in the character's questProgress compound.
 */
public final class Quests {
    public static boolean start(ServerPlayer sp, String questId) {
        QuestDef q = QuestRegistry.get(questId);
        CharacterData d = Characters.get(sp);
        if (q == null || d.questProgress.containsKey(questId) || (QuestRegistry.isDone(d, questId) && !q.repeatable())) return false;
        if (!QuestRegistry.requirementsMet(d, q)) return false;
        CompoundTag prog = new CompoundTag();
        d.questProgress.put(questId, prog);
        sp.sendSystemMessage(Component.translatable("grandline.quest.started", Component.translatable(q.titleKey())).withStyle(ChatFormatting.AQUA));
        // islands discovered before the quest began still count
        boolean credited = false;
        for (int i = 0; i < q.objectives().size(); i++) {
            QuestDef.Objective o = q.objectives().get(i);
            if (o.type().equals("discover") && d.discoveredIslands.contains(o.target())) {
                prog.putInt("o" + i, o.count());
                credited = true;
            }
        }
        if (credited && complete(q, prog)) finish(sp, d, q);
        CharacterManager.markDirty(sp);
        return true;
    }

    public static void progress(ServerPlayer sp, String type, String target, int amount) {
        CharacterData d = Characters.get(sp);
        if (d.questProgress.isEmpty()) return;
        for (Map.Entry<String, CompoundTag> e : Map.copyOf(d.questProgress).entrySet()) {
            QuestDef q = QuestRegistry.get(e.getKey());
            if (q == null) continue;
            CompoundTag prog = e.getValue();
            boolean changed = false;
            for (int i = 0; i < q.objectives().size(); i++) {
                QuestDef.Objective o = q.objectives().get(i);
                if (!o.type().equals(type) || !(o.target().equals(target) || o.target().equals("any"))) continue;
                int cur = prog.getInt("o" + i);
                if (cur >= o.count()) continue;
                prog.putInt("o" + i, Math.min(o.count(), cur + amount));
                changed = true;
                sp.displayClientMessage(Component.translatable("grandline.quest.progress", Component.translatable(q.titleKey()), Math.min(o.count(), cur + amount), o.count()), true);
            }
            if (changed && complete(q, prog)) finish(sp, d, q);
        }
        CharacterManager.markDirty(sp);
    }

    static boolean complete(QuestDef q, CompoundTag prog) {
        for (int i = 0; i < q.objectives().size(); i++) if (prog.getInt("o" + i) < q.objectives().get(i).count()) return false;
        return true;
    }

    static void finish(ServerPlayer sp, CharacterData d, QuestDef q) {
        d.questProgress.remove(q.id());
        d.completedQuests.add(q.id());
        int lv = d.addXp(q.xp());
        d.beli += q.beli();
        q.reputation().forEach(d::addReputation);
        if (q.bounty() > 0) com.grandline.npc.Bounties.raise(sp, d, q.bounty(), Component.translatable(q.titleKey()));
        for (var item : q.rewardItems()) sp.getInventory().add(item.get());
        if (q.flag() != null) d.flags.add(q.flag());
        sp.sendSystemMessage(Component.translatable("grandline.quest.complete", Component.translatable(q.titleKey()), q.xp(), q.beli()).withStyle(ChatFormatting.GOLD));
        if (lv > 0) {
            sp.sendSystemMessage(Component.translatable("grandline.level.up", d.level).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            CharacterManager.applyAttributes(sp);
        }
        if (q.next() != null) start(sp, QuestRegistry.resolve(q.next(), d));
        CharacterManager.markDirty(sp);
    }

    private Quests() {}
}
