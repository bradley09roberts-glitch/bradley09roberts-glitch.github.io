package com.squidgame.game.bridge;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers the glass bridge's own payload (discovered by name from {@code ModNetwork.init}). */
public final class BridgeNet {
    private BridgeNet() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(BridgeMapPayload.TYPE, BridgeMapPayload.CODEC);
    }
}
