package com.squidgame.client.net;

import com.squidgame.client.screen.ScreenRegistry;
import com.squidgame.client.state.ClientState;
import com.squidgame.net.DangerPayload;
import com.squidgame.net.FadePayload;
import com.squidgame.net.HudPayload;
import com.squidgame.net.NumbersPayload;
import com.squidgame.net.OpenScreenPayload;
import com.squidgame.net.ResultsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.concurrent.ConcurrentHashMap;

/** Receivers for the server's S2C payloads. */
public final class ClientNetworking {
    private ClientNetworking() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(HudPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> {
            ClientState.hud = payload;
            ClientState.hudReceivedAt = System.nanoTime();
        }));
        ClientPlayNetworking.registerGlobalReceiver(FadePayload.TYPE, (payload, ctx) -> ctx.client().execute(() ->
                ClientState.startFade(payload.fadeIn(), payload.hold(), payload.fadeOut(), payload.argb())));
        ClientPlayNetworking.registerGlobalReceiver(DangerPayload.TYPE, (payload, ctx) -> ctx.client().execute(() ->
                ClientState.startDanger(payload.intensity(), payload.ticks(), payload.pulses(), payload.argb())));
        ClientPlayNetworking.registerGlobalReceiver(ResultsPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> {
            ClientState.results = payload;
            ClientState.resultsEndsAt = System.nanoTime() + payload.showTicks() * 50_000_000L;
        }));
        ClientPlayNetworking.registerGlobalReceiver(NumbersPayload.TYPE, (payload, ctx) -> ctx.client().execute(() ->
                ClientState.numbers = new ConcurrentHashMap<>(payload.numbers())));
        ClientPlayNetworking.registerGlobalReceiver(OpenScreenPayload.TYPE, (payload, ctx) -> ctx.client().execute(() ->
                ScreenRegistry.handle(payload.screen(), payload.action(), payload.data())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());
    }
}
