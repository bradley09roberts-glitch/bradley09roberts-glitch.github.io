package com.terracraft.mining;

import com.terracraft.item.TerraItemStats;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * Pickaxe power of any item. TerraCraft tools declare it in their stats; vanilla pickaxes are mapped onto
 * the Terraria scale so they slot into progression without skipping it (a diamond pickaxe sits between
 * Silver and Tungsten and cannot mine Meteorite, Demonite or Obsidian).
 */
public final class ToolPowers {
    private static final Map<Item, Integer> VANILLA_PICKAXES = Map.of(
        Items.WOODEN_PICKAXE, 20,
        Items.STONE_PICKAXE, 30,
        Items.COPPER_PICKAXE, 35,
        Items.GOLDEN_PICKAXE, 35,
        Items.IRON_PICKAXE, 40,
        Items.DIAMOND_PICKAXE, 45,
        Items.NETHERITE_PICKAXE, 50
    );

    private ToolPowers() {}

    public static int pickaxePower(ItemStack stack) {
        TerraItemStats stats = TerraItemStats.of(stack);
        if (stats.pickPower() > 0) {
            return stats.pickPower();
        }
        return VANILLA_PICKAXES.getOrDefault(stack.getItem(), 0);
    }

    public static int hammerPower(ItemStack stack) {
        return TerraItemStats.of(stack).hammerPower();
    }
}
