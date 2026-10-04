package com.squidgame.net;

import com.squidgame.SquidGameMod;
import com.squidgame.tournament.TournamentManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/** Registers all payload types (both directions) and the server-side receivers. */
public final class ModNetwork {
    private ModNetwork() {
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(HudPayload.TYPE, HudPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FadePayload.TYPE, FadePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DangerPayload.TYPE, DangerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ResultsPayload.TYPE, ResultsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NumbersPayload.TYPE, NumbersPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenScreenPayload.TYPE, OpenScreenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientActionPayload.TYPE, ClientActionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ClientActionPayload.TYPE, (payload, context) ->
                context.server().execute(() -> TournamentManager.onClientAction(context.player(), payload.id(), payload.data())));

        // Games may register additional payload types in a class "com.squidgame.game.<id>.<Name>Net" with a static
        // register() method; discovered by name so adding a game needs no edit here.
        for (String cls : new String[]{
                "com.squidgame.game.redlight.RedLightNet", "com.squidgame.game.dalgona.DalgonaNet",
                "com.squidgame.game.tug.TugNet", "com.squidgame.game.marbles.MarblesNet",
                "com.squidgame.game.bridge.BridgeNet", "com.squidgame.game.finale.FinaleNet"}) {
            try {
                Method m = Class.forName(cls).getMethod("register");
                m.invoke(null);
                SquidGameMod.LOGGER.info("Registered game networking {}", cls);
            } catch (ClassNotFoundException ignored) {
                // optional
            } catch (ReflectiveOperationException e) {
                SquidGameMod.LOGGER.error("Failed to register {}", cls, e);
            }
        }
    }

    public static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
