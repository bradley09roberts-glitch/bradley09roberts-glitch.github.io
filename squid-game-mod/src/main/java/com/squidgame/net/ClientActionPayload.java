package com.squidgame.net;

import com.squidgame.SquidGameMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: a UI/input action ({@code id}) with NBT data (marbles choices, pull presses...). The server
 * routes it to the current game ({@code MiniGame#onClientAction}); it never trusts the content.
 */
public record ClientActionPayload(String id, CompoundTag data) implements CustomPacketPayload {
    public static final Type<ClientActionPayload> TYPE = new Type<>(SquidGameMod.id("action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ClientActionPayload::id, ByteBufCodecs.COMPOUND_TAG, ClientActionPayload::data,
            ClientActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
