package com.terracraft.network.packet;

import com.terracraft.client.ClientPacketHandlers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server -> client: the active world event id ("" = none), for sky/fog effects. */
public record SyncEventPacket(String event) {
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncEventPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SyncEventPacket::event, SyncEventPacket::new);

    public static void handle(SyncEventPacket packet, CustomPayloadEvent.Context ctx) {
        ClientPacketHandlers.setEvent(packet.event());
    }
}
