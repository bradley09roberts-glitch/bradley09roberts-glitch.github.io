package com.terracraft.network.packet;

import com.terracraft.client.ClientPacketHandlers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server -> client: open the Terraria crafting screen (sent when a crafting station is used). */
public record OpenCraftingPacket() {
    public static final OpenCraftingPacket INSTANCE = new OpenCraftingPacket();
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCraftingPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(OpenCraftingPacket packet, CustomPayloadEvent.Context ctx) {
        ClientPacketHandlers.openCrafting();
    }
}
