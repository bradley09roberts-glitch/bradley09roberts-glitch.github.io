package com.squidgame.net;

import com.squidgame.SquidGameMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: open (or update, or close) a game screen. {@code screen} is a registered id (see the client's
 * {@code ScreenRegistry}), {@code data} an arbitrary NBT payload interpreted by that screen. {@code action}:
 * 0 = open, 1 = update the open screen, 2 = close.
 */
public record OpenScreenPayload(String screen, int action, CompoundTag data) implements CustomPacketPayload {
    public static final Type<OpenScreenPayload> TYPE = new Type<>(SquidGameMod.id("screen"));
    public static final int OPEN = 0, UPDATE = 1, CLOSE = 2;
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreenPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenScreenPayload::screen, ByteBufCodecs.VAR_INT, OpenScreenPayload::action,
            ByteBufCodecs.COMPOUND_TAG, OpenScreenPayload::data, OpenScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
