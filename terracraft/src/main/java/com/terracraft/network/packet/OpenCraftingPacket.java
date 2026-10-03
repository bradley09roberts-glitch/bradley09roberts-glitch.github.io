package com.terracraft.network.packet;

import com.terracraft.client.ClientPacketHandlers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import com.terracraft.network.PacketContext;

/** Server -> client: open the Terraria crafting screen (sent when a crafting station is used). */
public record OpenCraftingPacket() implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<OpenCraftingPacket> TYPE = new Type<>(com.terracraft.TerraCraft.id("open_crafting_packet"));

    @Override
    public Type<OpenCraftingPacket> type() {
        return TYPE;
    }

    public static final OpenCraftingPacket INSTANCE = new OpenCraftingPacket();
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCraftingPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(OpenCraftingPacket packet, PacketContext ctx) {
        ClientPacketHandlers.openCrafting();
    }
}
