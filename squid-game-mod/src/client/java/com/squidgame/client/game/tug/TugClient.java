package com.squidgame.client.game.tug;

import com.squidgame.game.tug.TugNet;
import com.squidgame.registry.ModEntities;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Client side of the Tug of War, registered by name from {@code ScreenRegistry.discoverGameClients}: the rope renderer, the key
 * bindings (Pull R, Heave F, Brace Left Shift), the input polling, the receiver of the server's match snapshots and the overlay.
 */
public final class TugClient {
    private TugClient() {
    }

    public static void register() {
        EntityRendererRegistry.register(ModEntities.ROPE, RopeRenderer::new);
        TugInput.registerKeys();
        ClientPlayNetworking.registerGlobalReceiver(TugNet.StatePayload.TYPE, (payload, ctx) ->
                ctx.client().execute(() -> TugClientState.accept(payload)));
        ClientTickEvents.START_CLIENT_TICK.register(TugInput::startTick);
        ClientTickEvents.END_CLIENT_TICK.register(TugInput::tick);
        HudRenderCallback.EVENT.register(TugOverlay::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TugClientState.reset());
    }
}
