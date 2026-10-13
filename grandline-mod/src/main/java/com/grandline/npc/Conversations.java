package com.grandline.npc;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Safe conversations: while a player is talking to someone (a dialogue opened in the last few seconds, renewed by
 * every choice), no NPC starts a new fight with them. Anyone already fighting them carries on; a player who strikes
 * first is fair game again.
 */
public final class Conversations {
    private static final Map<UUID, Long> TALKING = new HashMap<>();
    private static final int HOLD = 200;

    private Conversations() {}

    public static void mark(ServerPlayer sp) {
        TALKING.put(sp.getUUID(), sp.level().getGameTime() + HOLD);
    }

    public static boolean talking(ServerPlayer sp) {
        Long t = TALKING.get(sp.getUUID());
        if (t == null) return false;
        if (t < sp.level().getGameTime()) {
            TALKING.remove(sp.getUUID());
            return false;
        }
        return true;
    }
}
