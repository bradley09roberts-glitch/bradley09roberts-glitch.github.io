package com.squidgame.client.game.finale;

import com.squidgame.game.finale.FightStatePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;

/**
 * Client side of the final: key bindings (Dash, Shove), the input handling that turns clicks and keys into fight
 * actions, and the overlay. Discovered by name from {@code ScreenRegistry.discoverGameClients}.
 *
 * <p>Swings in the air: Fabric's {@link ClientPreAttackCallback} fires every tick the attack button is down, with or
 * without a target. While this client is a fighter in a live duel it cancels the vanilla attack (which would also try to
 * break blocks and hit entities) and {@link FinaleInput} reports the button instead; the server decides what a strike
 * hits, by reach and facing, so it makes no difference whether the crosshair is on the opponent.
 */
@Environment(EnvType.CLIENT)
public final class FinaleClient {
    private FinaleClient() {
    }

    public static void register() {
        FinaleInput.registerKeys();
        ClientPlayNetworking.registerGlobalReceiver(FightStatePayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> FightClientState.accept(payload)));
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            boolean live = FightClientState.isLiveFighter();
            if (live) {
                FinaleInput.noteAttackKey(clickCount);
            }
            return live;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                level.isClientSide && FightClientState.isLiveFighter() ? InteractionResult.FAIL : InteractionResult.PASS);
        ClientTickEvents.END_CLIENT_TICK.register(FinaleInput::tick);
        HudRenderCallback.EVENT.register(FinaleOverlay::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FightClientState.reset());
    }
}
