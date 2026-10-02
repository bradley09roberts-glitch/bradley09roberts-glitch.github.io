package com.terracraft.network.packet;

import com.terracraft.client.ClientPacketHandlers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server -> client: open the developer menu (the server has already checked permissions). */
public record OpenDevMenuPacket() {
    public static final OpenDevMenuPacket INSTANCE = new OpenDevMenuPacket();
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDevMenuPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(OpenDevMenuPacket packet, CustomPayloadEvent.Context ctx) {
        ClientPacketHandlers.openDevMenu();
    }
}
