package com.squidgame.game.marbles;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * Networking and interaction hooks of the marbles game, found by name from {@code ModNetwork.init}: the S2C payload
 * that drives the charge bar, and the right-click on another <i>player</i> (the tournament only routes clicks on NPC
 * bodies to the game) so two humans can propose a partnership to each other too.
 */
public final class MarblesNet {
    /** Screen ids (server -> client {@code OpenScreenPayload}). */
    public static final String SCREEN_ODD_EVEN = "marbles_oddeven";
    public static final String SCREEN_PAIR_REQUEST = "marbles_pair_request";
    /** Action ids (client -> server {@code ClientActionPayload}). */
    public static final String ACTION_HOLD = "marbles_hold";
    public static final String ACTION_GUESS = "marbles_guess";
    public static final String ACTION_PAIR_ANSWER = "marbles_pair_answer";
    public static final String ACTION_REOPEN = "marbles_reopen";

    private MarblesNet() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(ThrowStatePayload.TYPE, ThrowStatePayload.CODEC);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide || hand != InteractionHand.MAIN_HAND
                    || !(player instanceof ServerPlayer from) || !(entity instanceof ServerPlayer to)) {
                return InteractionResult.PASS;
            }
            return MarblesGame.onPlayerClickedPlayer(from, to) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
    }
}
