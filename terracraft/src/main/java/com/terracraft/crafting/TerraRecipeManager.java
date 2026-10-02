package com.terracraft.crafting;

import com.google.gson.JsonElement;
import com.terracraft.TerraCraft;
import com.terracraft.network.packet.SyncRecipesPacket;
import com.terracraft.progression.ProgressionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds Terraria recipes. The server loads them from datapacks; clients receive a copy for the crafting UI.
 * Crafting requests are always re-validated on the server (stations, materials, progression).
 */
public final class TerraRecipeManager {
    private static Map<Identifier, TerraRecipe> serverRecipes = Map.of();
    private static Map<Identifier, TerraRecipe> clientRecipes = Map.of();
    /** Hard cap on crafts per request (shift-click crafts up to this many). */
    public static final int MAX_BATCH = 64;

    private TerraRecipeManager() {}

    public static void load(Map<Identifier, JsonElement> files) {
        Map<Identifier, TerraRecipe> loaded = new LinkedHashMap<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try {
                loaded.put(entry.getKey(), TerraRecipe.parse(entry.getKey(), entry.getValue()));
            } catch (RuntimeException e) {
                TerraCraft.LOGGER.error("Invalid TerraCraft recipe {}: {}", entry.getKey(), e.getMessage());
            }
        });
        serverRecipes = Collections.unmodifiableMap(loaded);
        TerraCraft.LOGGER.info("Loaded {} Terraria recipes", loaded.size());
    }

    public static Collection<TerraRecipe> server() {
        return serverRecipes.values();
    }

    public static Collection<TerraRecipe> client() {
        return clientRecipes.values();
    }

    public static void applyClient(List<TerraRecipe> recipes) {
        Map<Identifier, TerraRecipe> map = new LinkedHashMap<>();
        recipes.forEach(r -> map.put(r.id(), r));
        clientRecipes = Collections.unmodifiableMap(map);
    }

    /** Every station referenced by at least one recipe (what the station scan looks for). */
    public static Set<Identifier> stationsUsed(Collection<TerraRecipe> recipes) {
        Set<Identifier> stations = new LinkedHashSet<>();
        recipes.forEach(r -> stations.addAll(r.stations()));
        return stations;
    }

    public static SyncRecipesPacket syncPacket() {
        return new SyncRecipesPacket(new ArrayList<>(serverRecipes.values()));
    }

    /** Server-side craft request handler. */
    public static void craft(ServerPlayer player, Identifier recipeId, int requested) {
        TerraRecipe recipe = serverRecipes.get(recipeId);
        if (recipe == null || player.isSpectator()) {
            return;
        }
        if (!CraftingLogic.isUnlocked(recipe, ProgressionManager.data(player.level().getServer()))) {
            return;
        }
        Set<Identifier> stations = CraftingStations.nearby(player.level(), player.blockPosition(), recipe.stations());
        if (!CraftingLogic.hasStations(recipe, stations)) {
            player.sendOverlayMessage(Component.translatable("message.terracraft.crafting.missing_station").withStyle(ChatFormatting.RED));
            return;
        }
        int times = Math.min(Math.max(1, requested), Math.min(MAX_BATCH, CraftingLogic.maxCrafts(player, recipe)));
        if (times <= 0) {
            return;
        }
        CraftingLogic.consume(player, recipe, times);
        int total = recipe.count() * times;
        while (total > 0) {
            ItemStack result = recipe.resultStack();
            int amount = Math.min(total, result.getMaxStackSize());
            result.setCount(amount);
            total -= amount;
            result.onCraftedBy(player, amount);
            if (!player.getInventory().add(result)) {
                player.drop(result, false);
            }
        }
        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.UI_STONECUTTER_TAKE_RESULT,
            net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.2F);
    }
}
