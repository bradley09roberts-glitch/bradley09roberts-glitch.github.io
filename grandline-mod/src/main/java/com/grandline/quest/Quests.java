package com.grandline.quest;

import com.grandline.npc.notable.NotableCharacters;
import com.grandline.npc.notable.NotableState;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.quest.arc.Arcs;
import com.grandline.quest.arc.Consequences;
import com.grandline.quest.arc.Scenes;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Quest state machine. Definitions come from {@link QuestRegistry} (data/grandline/quests/*.json + code).
 * Progress per objective is stored in the character's questProgress compound ("o<i>" = count so far), so it lives with
 * the character: it survives logging out, knockdowns, chunk unloading and travel between seas.
 *
 * <p>Story chapters ({@link com.grandline.quest.arc}) add scenes when a step starts and ends, branch the chain on the
 * player's decisions, and pay choice-dependent rewards; all of it rides on the same start/progress/finish.
 */
public final class Quests {
    private static boolean discoveredNewWorld(ServerPlayer sp, CharacterData d) {
        var plan = com.grandline.world.WorldPlan.of(sp.server);
        if (plan == null) return false;
        for (String id : d.discoveredIslands) {
            var isl = plan.byId(id);
            if (isl != null && isl.zone == com.grandline.world.SeaZone.NEW_WORLD) return true;
        }
        return false;
    }

    public static boolean start(ServerPlayer sp, String questId) {
        QuestDef q = QuestRegistry.get(questId);
        CharacterData d = Characters.get(sp);
        if (q == null || d.questProgress.containsKey(questId) || (QuestRegistry.isDone(d, questId) && !q.repeatable())) return false;
        if (!QuestRegistry.requirementsMet(d, q)) return false;
        CompoundTag prog = new CompoundTag();
        d.questProgress.put(questId, prog);
        sp.sendSystemMessage(Component.translatable("grandline.quest.started", QuestRegistry.title(q)).withStyle(ChatFormatting.AQUA));
        QuestText.send(sp, q, null); // what the quest is about and what to do, objective by objective
        d.trackedQuest = questId; // the newest quest becomes the one the map and minimap guide to
        com.grandline.quest.story.IslandStories.onQuestStarted(sp, q); // wanted posters, letters, parcels for generated jobs
        Scenes.play(sp, q, "start"); // a story step opens with its scene
        // objectives already met when the quest begins count straight away
        boolean credited = credit(sp, d, q, prog);
        if (credited && complete(q, prog)) finish(sp, d, q);
        CharacterManager.markDirty(sp);
        QuestGuide.send(sp);
        return true;
    }

    /**
     * Credit what the character has already done: islands already discovered, people already beaten (by this
     * character, or gone for good), the parts of the combat lesson already shown, a decision already made.
     */
    static boolean credit(ServerPlayer sp, CharacterData d, QuestDef q, CompoundTag prog) {
        boolean credited = false;
        for (int i = 0; i < q.objectives().size(); i++) {
            QuestDef.Objective o = q.objectives().get(i);
            if (prog.getInt("o" + i) >= o.count()) continue;
            boolean met = switch (o.type()) {
                case "discover" -> d.discoveredIslands.contains(o.target()) || o.target().equals("any_new_world") && discoveredNewWorld(sp, d);
                case "defeat", "arrest" -> NotableCharacters.get(o.target()) != null
                        && (d.flags.contains("beat:" + o.target()) || NotableState.get(sp.server).isGone(o.target()));
                case "lesson" -> d.flags.contains("lesson:" + o.target());
                case "choice" -> Arcs.choice(d, o.target()) != null;
                default -> false;
            };
            if (met) {
                prog.putInt("o" + i, o.count());
                credited = true;
            }
        }
        return credited;
    }

    public static void progress(ServerPlayer sp, String type, String target, int amount) {
        CharacterData d = Characters.get(sp);
        // remembered for later steps: a person beaten, a part of the combat lesson shown
        if ((type.equals("defeat") || type.equals("arrest")) && NotableCharacters.get(target) != null) d.flags.add("beat:" + target);
        boolean firstLesson = type.equals("lesson") && d.flags.add("lesson:" + target);
        if (d.questProgress.isEmpty()) {
            if (firstLesson) CharacterManager.markDirty(sp);
            return;
        }
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
                sp.displayClientMessage(Component.translatable("grandline.quest.progress", QuestRegistry.title(q), Math.min(o.count(), cur + amount), o.count()), true);
                if (type.equals("lesson")) com.grandline.quest.arc.LessonCoach.coach(sp, q, prog, target); // the teacher calls out the next part
            }
            if (changed && complete(q, prog)) finish(sp, d, q);
        }
        CharacterManager.markDirty(sp);
    }

    /** Credit what is already met and finish the quest if that completes it (story migration). Returns true if finished. */
    public static boolean recheck(ServerPlayer sp, String questId) {
        CharacterData d = Characters.get(sp);
        QuestDef q = QuestRegistry.get(questId);
        CompoundTag prog = d.questProgress.get(questId);
        if (q == null || prog == null) return false;
        credit(sp, d, q, prog);
        if (!complete(q, prog)) return false;
        finish(sp, d, q);
        return true;
    }

    static boolean complete(QuestDef q, CompoundTag prog) {
        for (int i = 0; i < q.objectives().size(); i++) if (prog.getInt("o" + i) < q.objectives().get(i).count()) return false;
        return true;
    }

    /**
     * Completes a quest and pays it. Only a quest that is actually in progress is finished, so a quest can never be
     * paid twice (a second call, or a call for a quest already completed, does nothing).
     */
    static void finish(ServerPlayer sp, CharacterData d, QuestDef q) {
        if (!d.questProgress.containsKey(q.id())) return; // already finished: rewards are paid once
        QuestCollect.handOver(sp, q); // "collect" objectives: the goods go to the giver
        d.questProgress.remove(q.id());
        d.completedQuests.add(q.id());
        int lv = d.addXp(q.xp());
        d.beli += q.beli();
        q.reputation().forEach(d::addReputation);
        if (q.bounty() > 0) com.grandline.npc.Bounties.raise(sp, d, q.bounty(), QuestRegistry.title(q));
        for (var item : q.rewardItems()) {
            var st = item.get();
            if (!sp.getInventory().add(st)) sp.drop(st, false);
        }
        if (q.flag() != null) d.flags.add(q.flag());
        Consequences.onFinish(sp, d, q); // what the player's choice in the chapter earned
        com.grandline.journey.Journey.onQuestDone(sp, q); // journey: the Parts of the saga and its endings
        com.grandline.career.Careers.onQuestDone(sp, d, q); // careers: story deeds and path quests
        sp.sendSystemMessage(Component.translatable("grandline.quest.complete", QuestRegistry.title(q), q.xp(), q.beli()).withStyle(ChatFormatting.GOLD));
        sp.playNotifySound(com.grandline.registry.ModSounds.UI_QUEST.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
        if (lv > 0) {
            sp.sendSystemMessage(Component.translatable("grandline.level.up", d.level).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            CharacterManager.applyAttributes(sp);
        }
        Scenes.play(sp, q, "end"); // a story step closes with its scene
        String next = Arcs.next(q, d); // a decision sends the chapter down the branch the player chose
        if (next != null) start(sp, QuestRegistry.resolve(next, d));
        CharacterManager.markDirty(sp);
    }

    private Quests() {}
}
