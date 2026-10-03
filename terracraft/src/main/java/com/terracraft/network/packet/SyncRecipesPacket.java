package com.terracraft.network.packet;

import com.terracraft.crafting.TerraRecipe;
import com.terracraft.crafting.TerraRecipeManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import com.terracraft.network.PacketContext;

import java.util.List;

/** Server -> client: every Terraria recipe (on login and datapack reload). */
public record SyncRecipesPacket(List<TerraRecipe> recipes) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<SyncRecipesPacket> TYPE = new Type<>(com.terracraft.TerraCraft.id("sync_recipes_packet"));

    @Override
    public Type<SyncRecipesPacket> type() {
        return TYPE;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncRecipesPacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeVarInt(p.recipes.size());
            p.recipes.forEach(r -> r.write(buf));
        },
        buf -> {
            int size = buf.readVarInt();
            TerraRecipe[] recipes = new TerraRecipe[size];
            for (int i = 0; i < size; i++) {
                recipes[i] = TerraRecipe.read(buf);
            }
            return new SyncRecipesPacket(List.of(recipes));
        }
    );

    public static void handle(SyncRecipesPacket packet, PacketContext ctx) {
        TerraRecipeManager.applyClient(packet.recipes);
    }
}
