package com.grandline.ship;

import com.grandline.npc.GLNpc;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Shipwright services (buy, upgrade, repair, recover sunk ships). Replaced with the full UI in the ships milestone. */
public final class Shipyard {
    public static void open(ServerPlayer sp, GLNpc npc) {
        sp.sendSystemMessage(Component.translatable("grandline.shipyard.soon"));
    }

    private Shipyard() {}
}
