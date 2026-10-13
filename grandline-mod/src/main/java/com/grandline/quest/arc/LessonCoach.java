package com.grandline.quest.arc;

import com.grandline.npc.notable.NotableCharacters;
import com.grandline.quest.QuestDef;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * The combat lesson's running commentary: each time a part of the lesson is shown (a blow landed, a blow guarded, an
 * attack dodged, a technique used), the teacher who gave the step says so and calls out the next part. Lines:
 * {@code lesson.grandline.<teacher>.<part>} (falling back to {@code lesson.grandline.<part>}) and {@code .done}.
 */
public final class LessonCoach {
    private LessonCoach() {}

    public static void coach(ServerPlayer sp, QuestDef q, CompoundTag prog, String shown) {
        String teacher = q.giverRole().startsWith("notable:") ? q.giverRole().substring(8) : "";
        String next = null;
        for (int i = 0; i < q.objectives().size(); i++) {
            QuestDef.Objective o = q.objectives().get(i);
            if (o.type().equals("lesson") && prog.getInt("o" + i) < o.count()) {
                next = o.target();
                break;
            }
        }
        String line = next == null ? "done" : next;
        MutableComponent text = Component.translatable(key(teacher, "good." + shown)).append(" ").append(Component.translatable(key(teacher, line)));
        var n = NotableCharacters.get(teacher);
        MutableComponent who = n != null ? NotableCharacters.name(n).copy() : Component.translatable("arc.grandline.teacher");
        sp.sendSystemMessage(who.withStyle(ChatFormatting.GOLD).append(Component.literal(": ").withStyle(ChatFormatting.GOLD))
                .append(text.withStyle(ChatFormatting.YELLOW)));
    }

    private static String key(String teacher, String part) {
        String own = "lesson.grandline." + teacher + "." + part;
        return !teacher.isEmpty() && Language.getInstance().has(own) ? own : "lesson.grandline." + part;
    }
}
