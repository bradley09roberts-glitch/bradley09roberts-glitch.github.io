package com.squidgame.game.finale;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Registers the payload of the final game. Discovered by name from {@code ModNetwork.init}. The fight inputs of
 * players travel in the shared {@code ClientActionPayload} (see {@link FightInput}); only the overlay state needs its
 * own packet.
 */
public final class FinaleNet {
    private FinaleNet() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(FightStatePayload.TYPE, FightStatePayload.CODEC);
    }
}
