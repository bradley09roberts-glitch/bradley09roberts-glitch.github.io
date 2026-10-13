package com.grandline.combat;

import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.quest.Quests;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The combat lesson. Every time a player does one of the four things the core loop is made of it counts for
 * "lesson" quest objectives (the elder's opening lesson uses them) and, the first time ever, a one-line tip says
 * what to try next:
 * <ol>
 * <li><b>strike</b> — landed a blow (tap to strike, hold for a heavy blow);</li>
 * <li><b>guard</b> — guarded or parried a blow (hold right-click in Combat Mode; just before it lands = parry);</li>
 * <li><b>dodge</b> — slipped a blow with a dodge (Left Alt);</li>
 * <li><b>technique</b> — used a technique (1-9 in Combat Mode).</li>
 * </ol>
 */
public final class Lessons {
    public static final String STRIKE = "strike", GUARD = "guard", DODGE = "dodge", TECHNIQUE = "technique";

    private Lessons() {}

    public static void done(ServerPlayer sp, String lesson) {
        CharacterData d = Characters.get(sp);
        boolean taught = inLesson(d); // a teacher's lesson is running: the teacher comments instead (quest/arc/LessonCoach)
        Quests.progress(sp, "lesson", lesson, 1);
        if (d.flags.add("lesson_" + lesson) && !taught) {
            sp.sendSystemMessage(Component.translatable("grandline.lesson." + lesson).withStyle(ChatFormatting.YELLOW));
        }
        CharacterManager.markDirty(sp);
    }

    private static boolean inLesson(CharacterData d) {
        for (String id : d.questProgress.keySet()) {
            var q = com.grandline.quest.QuestRegistry.get(id);
            if (q != null && q.objectives().stream().anyMatch(o -> o.type().equals("lesson"))) return true;
        }
        return false;
    }
}
