package com.grandline.quest.story;

import com.grandline.quest.story.JobBoard.StartGift;
import com.grandline.ship.SeaEncounters;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * A convoy's merchantman counts against the one server-wide cap on NPC ships like every other NPC ship: while the cap
 * is full she is not put to sea (first launch or a rebuild after a restart); she waits in port and the player is told
 * so (once a minute), and she sails out as soon as there is room (the convoy's own tick tries again every half second).
 */
public final class ConvoyCap {
    private static final Map<String, Long> TOLD = new HashMap<>();
    /** Self-test: launches held back by the cap. */
    public static int held;

    /** True when the cap is full (she waits; the player hears why). */
    static boolean held(ServerPlayer sp, ServerLevel l, SeaJobs.Convoy c, StartGift g) {
        if (SeaEncounters.countNpcShips(l) < SeaEncounters.cap(l.players().size())) return false;
        held++;
        long now = l.getGameTime();
        String key = SeaJobs.key(c.character, c.quest);
        if (TOLD.getOrDefault(key, Long.MIN_VALUE) <= now) {
            TOLD.put(key, now + 1200);
            SeaJobs.tell(sp, Component.translatable("job.grandline.convoy.held", g.name()).withStyle(ChatFormatting.GRAY));
        }
        return true;
    }

    /** Forget the message timers (server stop, tests). */
    public static void clear() {
        TOLD.clear();
    }

    private ConvoyCap() {}
}
