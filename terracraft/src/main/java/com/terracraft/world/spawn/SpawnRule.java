package com.terracraft.world.spawn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.terracraft.progression.ProgressionCondition;
import com.terracraft.progression.ProgressionView;
import com.terracraft.world.TerrariaLayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One entry of a Terraria spawn table, loaded from {@code data/<ns>/terracraft/spawns/*.json}:
 * <pre>{
 *   "entity": "terracraft:zombie",
 *   "weight": 10,
 *   "time": "night",                      // day | night | any
 *   "layers": ["surface"],                // space, surface, underground, cavern, underworld
 *   "biomes": ["#minecraft:is_overworld"],// optional ids or #tags
 *   "exclude_biomes": ["#minecraft:is_ocean"],
 *   "ground": ["#terracraft:corruption"],  // optional: block (or tag) under the spawn position
 *   "exclude_ground": ["#terracraft:evil"],
 *   "sky": true,                          // optional: must (not) see the sky
 *   "placement": "ground",                // ground | air
 *   "group": [1, 2],
 *   "dimension": "minecraft:overworld",
 *   "condition": "!hardmode_active"       // progression condition
 * }</pre>
 * A file may also hold {@code {"spawns": [ ... ]}}.
 */
public record SpawnRule(
    Identifier entity,
    int weight,
    Time time,
    Set<TerrariaLayer> layers,
    List<BiomeMatcher> biomes,
    List<BiomeMatcher> excludedBiomes,
    List<BlockMatcher> ground,
    List<BlockMatcher> excludedGround,
    Boolean sky,
    Placement placement,
    int minGroup,
    int maxGroup,
    ResourceKey<Level> dimension,
    ProgressionCondition condition
) {
    public enum Time { DAY, NIGHT, ANY }
    public enum Placement { GROUND, AIR }

    public record BiomeMatcher(Identifier id, boolean tag) {
        boolean matches(Holder<Biome> biome) {
            return tag ? biome.is(TagKey.create(Registries.BIOME, id)) : biome.is(id);
        }

        static BiomeMatcher parse(String value) {
            return value.startsWith("#") ? new BiomeMatcher(Identifier.parse(value.substring(1)), true) : new BiomeMatcher(Identifier.parse(value), false);
        }
    }

    /** Block id or #tag tested against the block the enemy would stand on. */
    public record BlockMatcher(Identifier id, boolean tag) {
        boolean matches(net.minecraft.world.level.block.state.BlockState state) {
            return tag ? state.is(TagKey.create(Registries.BLOCK, id))
                : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id);
        }

        static BlockMatcher parse(String value) {
            return value.startsWith("#") ? new BlockMatcher(Identifier.parse(value.substring(1)), true) : new BlockMatcher(Identifier.parse(value), false);
        }
    }

    public boolean matches(SpawnContext context, ProgressionView progression) {
        if (!context.dimension().equals(dimension)) {
            return false;
        }
        if (time == Time.DAY && !context.day() || time == Time.NIGHT && context.day()) {
            return false;
        }
        if (!layers.contains(context.layer())) {
            return false;
        }
        if (sky != null && sky != context.sky()) {
            return false;
        }
        if (!biomes.isEmpty() && biomes.stream().noneMatch(b -> b.matches(context.biome()))) {
            return false;
        }
        if (excludedBiomes.stream().anyMatch(b -> b.matches(context.biome()))) {
            return false;
        }
        if (!ground.isEmpty() && ground.stream().noneMatch(b -> b.matches(context.ground()))) {
            return false;
        }
        if (excludedGround.stream().anyMatch(b -> b.matches(context.ground()))) {
            return false;
        }
        return condition.test(progression);
    }

    public static List<SpawnRule> parseFile(JsonElement json) {
        List<SpawnRule> rules = new ArrayList<>();
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(e -> rules.add(parse(e.getAsJsonObject())));
        } else if (json.getAsJsonObject().has("spawns")) {
            json.getAsJsonObject().getAsJsonArray("spawns").forEach(e -> rules.add(parse(e.getAsJsonObject())));
        } else {
            rules.add(parse(json.getAsJsonObject()));
        }
        return rules;
    }

    public static SpawnRule parse(JsonObject json) {
        Set<TerrariaLayer> layers = EnumSet.noneOf(TerrariaLayer.class);
        if (json.has("layers")) {
            for (JsonElement e : json.getAsJsonArray("layers")) {
                layers.add(TerrariaLayer.valueOf(e.getAsString().toUpperCase(Locale.ROOT)));
            }
        } else {
            layers.add(TerrariaLayer.SURFACE);
        }
        int minGroup = 1;
        int maxGroup = 1;
        if (json.has("group")) {
            JsonArray group = json.getAsJsonArray("group");
            minGroup = group.get(0).getAsInt();
            maxGroup = group.size() > 1 ? group.get(1).getAsInt() : minGroup;
        }
        return new SpawnRule(
            Identifier.parse(GsonHelper.getAsString(json, "entity")),
            GsonHelper.getAsInt(json, "weight", 10),
            Time.valueOf(GsonHelper.getAsString(json, "time", "any").toUpperCase(Locale.ROOT)),
            layers,
            matchers(json, "biomes"),
            matchers(json, "exclude_biomes"),
            blockMatchers(json, "ground"),
            blockMatchers(json, "exclude_ground"),
            json.has("sky") ? GsonHelper.getAsBoolean(json, "sky") : null,
            Placement.valueOf(GsonHelper.getAsString(json, "placement", "ground").toUpperCase(Locale.ROOT)),
            minGroup,
            Math.max(minGroup, maxGroup),
            ResourceKey.create(Registries.DIMENSION, Identifier.parse(GsonHelper.getAsString(json, "dimension", "minecraft:overworld"))),
            json.has("condition") ? ProgressionCondition.parse(json.get("condition")) : new ProgressionCondition.Constant(true));
    }

    private static List<BiomeMatcher> matchers(JsonObject json, String key) {
        List<BiomeMatcher> list = new ArrayList<>();
        if (json.has(key)) {
            for (JsonElement e : json.getAsJsonArray(key)) {
                list.add(BiomeMatcher.parse(e.getAsString()));
            }
        }
        return list;
    }

    private static List<BlockMatcher> blockMatchers(JsonObject json, String key) {
        List<BlockMatcher> list = new ArrayList<>();
        if (json.has(key)) {
            for (JsonElement e : json.getAsJsonArray(key)) {
                list.add(BlockMatcher.parse(e.getAsString()));
            }
        }
        return list;
    }

    /** Everything a rule can test about a candidate spawn position ({@code ground} = the block below it). */
    public record SpawnContext(ResourceKey<Level> dimension, Holder<Biome> biome, TerrariaLayer layer, boolean day, boolean sky,
                               net.minecraft.world.level.block.state.BlockState ground) {}
}
