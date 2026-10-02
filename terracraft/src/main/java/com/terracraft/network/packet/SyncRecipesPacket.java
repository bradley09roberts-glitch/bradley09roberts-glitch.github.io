package com.terracraft.network.packet;

import com.terracraft.crafting.TerraRecipe;
import com.terracraft.crafting.TerraRecipeManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.List;

/** Server -> client: every Terraria recipe (on login and datapack reload). */
public record SyncRecipesPacket(List<TerraRecipe> recipes) {
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

    public static void handle(SyncRecipesPacket packet, CustomPayloadEvent.Context ctx) {
        TerraRecipeManager.applyClient(packet.recipes);
    }
}
