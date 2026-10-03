package com.terracraft.network.packet;

import com.terracraft.crafting.TerraRecipeManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import com.terracraft.network.PacketContext;

/** Client -> server: craft a recipe {@code times} times. Fully re-validated on the server. */
public record CraftRecipePacket(Identifier recipe, int times) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<CraftRecipePacket> TYPE = new Type<>(com.terracraft.TerraCraft.id("craft_recipe_packet"));

    @Override
    public Type<CraftRecipePacket> type() {
        return TYPE;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftRecipePacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeIdentifier(p.recipe);
            buf.writeVarInt(p.times);
        },
        buf -> new CraftRecipePacket(buf.readIdentifier(), buf.readVarInt())
    );

    public static void handle(CraftRecipePacket packet, PacketContext ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            TerraRecipeManager.craft(player, packet.recipe, packet.times);
        }
    }

}
