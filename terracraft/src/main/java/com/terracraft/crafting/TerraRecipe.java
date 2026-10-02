package com.terracraft.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.terracraft.progression.ProgressionCondition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A Terraria recipe: ingredients with counts, a result, required crafting stations and an optional
 * progression condition.
 * <pre>
 * data/&lt;ns&gt;/terracraft/recipe/&lt;name&gt;.json
 * {
 *   "result": { "item": "terracraft:tin_broadsword", "count": 1 },
 *   "ingredients": [ { "item": "terracraft:tin_bar", "count": 8 } ],
 *   "stations": [ "terracraft:anvil" ],
 *   "condition": "!hardmode_active",          (optional, see ProgressionCondition)
 *   "category": "weapons"                     (optional, used by the UI filter)
 * }
 * </pre>
 * Ingredients may use {@code "tag": "minecraft:planks"} instead of an item (Terraria's "Any Wood" groups).
 */
public record TerraRecipe(Identifier id, Item result, int count, List<Ingredient> ingredients, List<Identifier> stations,
                          ProgressionCondition condition, String category) {

    public record Ingredient(@Nullable Item item, @Nullable TagKey<Item> tag, int count) {
        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            return item != null ? stack.is(item) : stack.is(tag);
        }

        public Component displayName() {
            if (item != null) {
                return item.getName(new ItemStack(item));
            }
            return Component.translatableWithFallback("tag.item." + tag.location().toLanguageKey(), "Any " + tag.location().getPath());
        }

        /** A representative stack for display (first item of the tag on the client). */
        public ItemStack icon() {
            if (item != null) {
                return new ItemStack(item, count);
            }
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                return new ItemStack(holder.value(), count);
            }
            return ItemStack.EMPTY;
        }
    }

    public ItemStack resultStack() {
        return new ItemStack(result, count);
    }

    // ------------------------------------------------------------------ JSON

    public static TerraRecipe parse(Identifier id, JsonElement json) {
        JsonObject obj = json.getAsJsonObject();
        JsonObject resultObj = obj.getAsJsonObject("result");
        Item result = item(resultObj.get("item").getAsString());
        int count = resultObj.has("count") ? resultObj.get("count").getAsInt() : 1;
        List<Ingredient> ingredients = new ArrayList<>();
        for (JsonElement element : obj.getAsJsonArray("ingredients")) {
            JsonObject ing = element.getAsJsonObject();
            int amount = ing.has("count") ? ing.get("count").getAsInt() : 1;
            if (ing.has("tag")) {
                ingredients.add(new Ingredient(null, TagKey.create(Registries.ITEM, Identifier.parse(ing.get("tag").getAsString())), amount));
            } else {
                ingredients.add(new Ingredient(item(ing.get("item").getAsString()), null, amount));
            }
        }
        List<Identifier> stations = new ArrayList<>();
        if (obj.has("stations")) {
            for (JsonElement element : obj.getAsJsonArray("stations")) {
                stations.add(Identifier.parse(element.getAsString()));
            }
        }
        ProgressionCondition condition = obj.has("condition") ? ProgressionCondition.parse(obj.get("condition")) : ProgressionCondition.ALWAYS;
        String category = obj.has("category") ? obj.get("category").getAsString() : "misc";
        return new TerraRecipe(id, result, count, List.copyOf(ingredients), List.copyOf(stations), condition, category);
    }

    private static Item item(String id) {
        Identifier key = Identifier.parse(id);
        return BuiltInRegistries.ITEM.getOptional(key).orElseThrow(() -> new IllegalArgumentException("Unknown item " + key));
    }

    // ------------------------------------------------------------------ network

    public void write(FriendlyByteBuf buf) {
        buf.writeIdentifier(id);
        buf.writeIdentifier(BuiltInRegistries.ITEM.getKey(result));
        buf.writeVarInt(count);
        buf.writeVarInt(ingredients.size());
        for (Ingredient ingredient : ingredients) {
            buf.writeBoolean(ingredient.item != null);
            buf.writeIdentifier(ingredient.item != null ? BuiltInRegistries.ITEM.getKey(ingredient.item) : ingredient.tag.location());
            buf.writeVarInt(ingredient.count);
        }
        buf.writeCollection(stations, FriendlyByteBuf::writeIdentifier);
        buf.writeUtf(condition.toJson().toString());
        buf.writeUtf(category, 64);
    }

    public static TerraRecipe read(FriendlyByteBuf buf) {
        Identifier id = buf.readIdentifier();
        Item result = BuiltInRegistries.ITEM.getValue(buf.readIdentifier());
        int count = buf.readVarInt();
        int size = buf.readVarInt();
        List<Ingredient> ingredients = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            boolean isItem = buf.readBoolean();
            Identifier key = buf.readIdentifier();
            int amount = buf.readVarInt();
            ingredients.add(isItem ? new Ingredient(BuiltInRegistries.ITEM.getValue(key), null, amount)
                : new Ingredient(null, TagKey.create(Registries.ITEM, key), amount));
        }
        List<Identifier> stations = buf.readList(FriendlyByteBuf::readIdentifier);
        ProgressionCondition condition = ProgressionCondition.parse(JsonParser.parseString(buf.readUtf()));
        String category = buf.readUtf(64);
        return new TerraRecipe(id, result, count, List.copyOf(ingredients), List.copyOf(stations), condition, category);
    }
}
