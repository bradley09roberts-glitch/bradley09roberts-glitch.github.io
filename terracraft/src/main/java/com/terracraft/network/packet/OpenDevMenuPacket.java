package com.terracraft.network.packet;

import com.terracraft.client.ClientPacketHandlers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import com.terracraft.network.PacketContext;

/** Server -> client: open the developer menu (the server has already checked permissions). */
public record OpenDevMenuPacket() implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<OpenDevMenuPacket> TYPE = new Type<>(com.terracraft.TerraCraft.id("open_dev_menu_packet"));

    @Override
    public Type<OpenDevMenuPacket> type() {
        return TYPE;
    }

    public static final OpenDevMenuPacket INSTANCE = new OpenDevMenuPacket();
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDevMenuPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(OpenDevMenuPacket packet, PacketContext ctx) {
        ClientPacketHandlers.openDevMenu();
    }
}
