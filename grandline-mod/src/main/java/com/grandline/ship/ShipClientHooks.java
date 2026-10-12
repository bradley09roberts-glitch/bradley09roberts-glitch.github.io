package com.grandline.ship;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Client-only helpers referenced from ShipEntity behind isClientSide checks. */
public final class ShipClientHooks {
    public static boolean isLocalPlayer(Player p) {
        return p == Minecraft.getInstance().player;
    }

    private ShipClientHooks() {}
}
