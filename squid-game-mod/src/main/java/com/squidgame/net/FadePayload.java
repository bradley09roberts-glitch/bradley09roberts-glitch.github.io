package com.squidgame.net;

import com.squidgame.SquidGameMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Full-screen colour fade: in {@code fadeIn} ticks to opaque, hold {@code hold}, out {@code fadeOut}. */
public record FadePayload(int fadeIn, int hold, int fadeOut, int argb) implements CustomPacketPayload {
    public static final Type<FadePayload> TYPE = new Type<>(SquidGameMod.id("fade"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FadePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FadePayload::fadeIn, ByteBufCodecs.VAR_INT, FadePayload::hold,
            ByteBufCodecs.VAR_INT, FadePayload::fadeOut, ByteBufCodecs.INT, FadePayload::argb, FadePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
