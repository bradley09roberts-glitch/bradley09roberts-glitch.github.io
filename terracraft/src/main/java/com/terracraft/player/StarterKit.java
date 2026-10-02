package com.terracraft.player;

import com.terracraft.TerraCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Terraria's starting inventory (Copper Shortsword, Copper Pickaxe, Copper Axe), given once on a player's
 * first join. Items are looked up by id so the kit degrades gracefully while content is being built.
 */
public final class StarterKit {
    private static final List<Identifier> ITEMS = List.of(
        TerraCraft.id("copper_shortsword"),
        TerraCraft.id("copper_pickaxe"),
        TerraCraft.id("copper_axe")
    );

    private StarterKit() {}

    public static void give(ServerPlayer player) {
        for (Identifier id : ITEMS) {
            BuiltInRegistries.ITEM.getOptional(id).ifPresent((Item item) -> {
                ItemStack stack = new ItemStack(item);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            });
        }
    }
}
