package com.squidgame.game.marbles;

import com.squidgame.SquidGameMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: whether it is the player's turn to throw a marble and what the charge bar needs to know to draw
 * the sweet spot (the floor plane the marble lands on and the difficulty, which fixes the throw controls).
 * {@code active} false hides the overlay.
 */
public record ThrowStatePayload(boolean active, double floorY, int difficulty, int ticksLeft, int ticksTotal)
        implements CustomPacketPayload {
    public static final Type<ThrowStatePayload> TYPE = new Type<>(SquidGameMod.id("marbles_throw"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ThrowStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ThrowStatePayload::active, ByteBufCodecs.DOUBLE, ThrowStatePayload::floorY,
            ByteBufCodecs.VAR_INT, ThrowStatePayload::difficulty, ByteBufCodecs.VAR_INT, ThrowStatePayload::ticksLeft,
            ByteBufCodecs.VAR_INT, ThrowStatePayload::ticksTotal, ThrowStatePayload::new);

    public static ThrowStatePayload inactive() {
        return new ThrowStatePayload(false, 0, 0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
