package com.squidgame.client.game.dalgona;

import com.squidgame.client.screen.ScreenRegistry;
import com.squidgame.game.dalgona.DalgonaNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client side of the honeycomb game, found by {@link ScreenRegistry#discoverGameClients()}: registers the tin
 * selection screen ("dalgona_tins"), the carving screen ("dalgona") and the receiver for the server's cookie state.
 */
public final class DalgonaClient {
    private DalgonaClient() {
    }

    public static void register() {
        ScreenRegistry.register("dalgona_tins", data -> new TinSelectScreen(data));
        ScreenRegistry.register("dalgona", data -> new DalgonaScreen(data));
        ClientPlayNetworking.registerGlobalReceiver(DalgonaNet.StatePayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> DalgonaScreen.onState(payload)));
    }
}
