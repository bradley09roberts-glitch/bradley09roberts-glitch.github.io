package com.terracraft.network.packet;

import com.terracraft.crafting.TerraRecipeManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Client -> server: craft a recipe {@code times} times. Fully re-validated on the server. */
public record CraftRecipePacket(Identifier recipe, int times) {
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftRecipePacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeIdentifier(p.recipe);
            buf.writeVarInt(p.times);
        },
        buf -> new CraftRecipePacket(buf.readIdentifier(), buf.readVarInt())
    );

    public static void handle(CraftRecipePacket packet, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            TerraRecipeManager.craft(player, packet.recipe, packet.times);
        }
    }

}
