package com.squidgame.client.game.bridge;

import com.squidgame.game.bridge.BridgeMapPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Client side of the glass bridge: receives the public bridge knowledge from the server and draws the small row map
 * ({@link BridgeOverlay}). Found by name from {@code ScreenRegistry.discoverGameClients}.
 */
public final class BridgeClient {
    private BridgeClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BridgeMapPayload.TYPE, (payload, ctx) ->
                ctx.client().execute(() -> BridgeOverlay.update(payload)));
        HudRenderCallback.EVENT.register(BridgeOverlay::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BridgeOverlay.clear());
    }
}
