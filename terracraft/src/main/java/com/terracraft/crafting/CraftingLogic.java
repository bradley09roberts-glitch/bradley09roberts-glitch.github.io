package com.terracraft.crafting;

import com.terracraft.progression.ProgressionView;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Inventory checks and consumption for Terraria crafting (identical rules on client and server). */
public final class CraftingLogic {
    private CraftingLogic() {}

    public static int count(Player player, TerraRecipe.Ingredient ingredient) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ingredient.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static boolean hasStations(TerraRecipe recipe, Set<?> available) {
        return available.containsAll(recipe.stations());
    }

    public static boolean isUnlocked(TerraRecipe recipe, ProgressionView progression) {
        return recipe.condition().test(progression);
    }

    /** How many times the recipe can be crafted from the inventory (ignores stations). */
    public static int maxCrafts(Player player, TerraRecipe recipe) {
        int max = Integer.MAX_VALUE;
        for (TerraRecipe.Ingredient ingredient : recipe.ingredients()) {
            max = Math.min(max, count(player, ingredient) / Math.max(1, ingredient.count()));
        }
        return max == Integer.MAX_VALUE ? 0 : max;
    }

    /** Removes ingredients for {@code times} crafts. Caller must have checked {@link #maxCrafts}. */
    public static void consume(Player player, TerraRecipe recipe, int times) {
        Inventory inventory = player.getInventory();
        for (TerraRecipe.Ingredient ingredient : recipe.ingredients()) {
            int remaining = ingredient.count() * times;
            for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (ingredient.matches(stack)) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                }
            }
        }
        inventory.setChanged();
    }
}
