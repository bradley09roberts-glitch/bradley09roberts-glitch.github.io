package com.terracraft.npc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.terracraft.TerraCraft;
import com.terracraft.item.TerraItemStats;
import com.terracraft.progression.ProgressionCondition;
import com.terracraft.progression.ProgressionView;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NPC shops from {@code data/<ns>/terracraft/shops/<npc>.json}:
 * <pre>{"npc": "merchant", "items": [
 *   {"item": "minecraft:torch", "price": 50},
 *   {"item": "terracraft:lesser_healing_potion"},              // price = the item's Terraria value
 *   {"item": "terracraft:mining_potion", "condition": "boss_eye_defeated", "time": "night"}
 * ]}</pre>
 * Several files for the same NPC are merged, so other datapacks can add items.
 */
public final class NpcShops {
    /** @param price fixed price in copper, or -1 for the item's Terraria value (resolved on use: components are not bound during loading) */
    public record Entry(Item item, int count, long price, ProgressionCondition condition, String time) {
        @Override
        public long price() {
            return price >= 0 ? price : Math.max(1, TerraItemStats.of(new ItemStack(item)).value()) * (long) count;
        }

        public boolean available(ProgressionView progression, boolean night) {
            if ("night".equals(time) && !night || "day".equals(time) && night) {
                return false;
            }
            return condition.test(progression);
        }
    }

    private static volatile Map<String, List<Entry>> shops = Map.of();

    private NpcShops() {}

    public static List<Entry> shop(String npc) {
        return shops.getOrDefault(npc, List.of());
    }

    public static void load(Map<Identifier, JsonElement> data) {
        Map<String, List<Entry>> loaded = new HashMap<>();
        data.forEach((id, json) -> {
            try {
                JsonObject obj = json.getAsJsonObject();
                String npc = GsonHelper.getAsString(obj, "npc", id.getPath());
                for (JsonElement element : obj.getAsJsonArray("items")) {
                    JsonObject e = element.getAsJsonObject();
                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(GsonHelper.getAsString(e, "item")));
                    if (item == null || item == Items.AIR) {
                        TerraCraft.LOGGER.warn("Unknown shop item in {}: {}", id, e);
                        continue;
                    }
                    long price = e.has("price") ? e.get("price").getAsLong() : -1;
                    ProgressionCondition condition = e.has("condition") ? ProgressionCondition.parse(e.get("condition")) : new ProgressionCondition.Constant(true);
                    loaded.computeIfAbsent(npc, k -> new ArrayList<>())
                        .add(new Entry(item, GsonHelper.getAsInt(e, "count", 1), price, condition, GsonHelper.getAsString(e, "time", "any")));
                }
            } catch (Exception ex) {
                TerraCraft.LOGGER.error("Invalid shop file {}", id, ex);
            }
        });
        shops = Map.copyOf(loaded);
        TerraCraft.LOGGER.info("Loaded {} NPC shops", loaded.size());
    }
}
