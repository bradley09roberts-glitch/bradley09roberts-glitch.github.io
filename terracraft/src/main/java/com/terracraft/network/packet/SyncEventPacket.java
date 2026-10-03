package com.terracraft.network.packet;

import com.terracraft.client.ClientPacketHandlers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import com.terracraft.network.PacketContext;

/** Server -> client: the active world event id ("" = none), for sky/fog effects. */
public record SyncEventPacket(String event) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<SyncEventPacket> TYPE = new Type<>(com.terracraft.TerraCraft.id("sync_event_packet"));

    @Override
    public Type<SyncEventPacket> type() {
        return TYPE;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncEventPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SyncEventPacket::event, SyncEventPacket::new);

    public static void handle(SyncEventPacket packet, PacketContext ctx) {
        ClientPacketHandlers.setEvent(packet.event());
    }
}
