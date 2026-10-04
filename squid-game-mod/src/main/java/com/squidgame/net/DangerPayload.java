package com.squidgame.net;

import com.squidgame.SquidGameMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A readable danger cue: a pulsing coloured screen-edge vignette. {@code intensity} 0..1 is the peak alpha factor,
 * {@code ticks} the duration, {@code pulses} how many times it pulses within that time (0 = steady).
 */
public record DangerPayload(float intensity, int ticks, int pulses, int argb) implements CustomPacketPayload {
    public static final Type<DangerPayload> TYPE = new Type<>(SquidGameMod.id("danger"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DangerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, DangerPayload::intensity, ByteBufCodecs.VAR_INT, DangerPayload::ticks,
            ByteBufCodecs.VAR_INT, DangerPayload::pulses, ByteBufCodecs.INT, DangerPayload::argb, DangerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
