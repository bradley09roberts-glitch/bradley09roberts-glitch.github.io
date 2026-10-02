package com.terracraft.progression;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A data-driven predicate over world progression. Every system that is gated by progression (spawn tables,
 * NPC arrival rules, shop stock, loot, recipes, summon items, ore generation...) uses this one format instead
 * of hard-coded checks.
 * <h2>JSON grammar</h2>
 * <pre>
 * "hardmode_active"                       flag must be set (namespace defaults to terracraft)
 * "!hardmode_active"                      flag must NOT be set
 * ["a", "b"]                              all of
 * {"all": [ ... ]}                        all of
 * {"any": [ ... ]}                        any of
 * {"not": condition}                      negation
 * {"flag": "terracraft:boss_eye_defeated"}
 * {"evil": "crimson"}                     world evil type
 * {"counter": "altars_smashed", "min": 3, "max": 10}
 * true / false                            constant
 * </pre>
 */
public sealed interface ProgressionCondition {
    ProgressionCondition ALWAYS = new Constant(true);
    ProgressionCondition NEVER = new Constant(false);

    boolean test(ProgressionView view);

    JsonElement toJson();

    /** Codec usable inside other codecs (stored as the JSON grammar above). */
    Codec<ProgressionCondition> CODEC = Codec.PASSTHROUGH.comapFlatMap(
        dynamic -> {
            try {
                return DataResult.success(parse(dynamic.convert(JsonOps.INSTANCE).getValue()));
            } catch (RuntimeException e) {
                return DataResult.error(() -> "Invalid progression condition: " + e.getMessage());
            }
        },
        condition -> new Dynamic<>(JsonOps.INSTANCE, condition.toJson())
    );

    static ProgressionCondition flag(ProgressionFlag flag) {
        return new Flag(flag.id(), true);
    }

    static ProgressionCondition notFlag(ProgressionFlag flag) {
        return new Flag(flag.id(), false);
    }

    static ProgressionCondition parse(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return ALWAYS;
        }
        if (json.isJsonPrimitive()) {
            JsonPrimitive primitive = json.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean() ? ALWAYS : NEVER;
            }
            String text = primitive.getAsString().trim();
            boolean negate = text.startsWith("!");
            if (negate) {
                text = text.substring(1);
            }
            return new Flag(ProgressionFlags.resolve(text).id(), !negate);
        }
        if (json.isJsonArray()) {
            return new All(parseList(json.getAsJsonArray()));
        }
        JsonObject obj = json.getAsJsonObject();
        if (obj.has("all")) {
            return new All(parseList(obj.getAsJsonArray("all")));
        }
        if (obj.has("any")) {
            return new Any(parseList(obj.getAsJsonArray("any")));
        }
        if (obj.has("not")) {
            return new Not(parse(obj.get("not")));
        }
        if (obj.has("flag")) {
            boolean expected = !obj.has("value") || obj.get("value").getAsBoolean();
            return new Flag(ProgressionFlags.resolve(obj.get("flag").getAsString()).id(), expected);
        }
        if (obj.has("evil")) {
            return new Evil(WorldVariants.WorldEvil.valueOf(obj.get("evil").getAsString().toUpperCase(Locale.ROOT)));
        }
        if (obj.has("counter")) {
            Identifier id = ProgressionFlags.resolve(obj.get("counter").getAsString()).id();
            int min = obj.has("min") ? obj.get("min").getAsInt() : Integer.MIN_VALUE;
            int max = obj.has("max") ? obj.get("max").getAsInt() : Integer.MAX_VALUE;
            return new Counter(id, min, max);
        }
        throw new JsonParseException("Unknown progression condition " + json);
    }

    private static List<ProgressionCondition> parseList(JsonArray array) {
        List<ProgressionCondition> list = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            list.add(parse(element));
        }
        return List.copyOf(list);
    }

    record Constant(boolean value) implements ProgressionCondition {
        @Override
        public boolean test(ProgressionView view) {
            return value;
        }

        @Override
        public JsonElement toJson() {
            return new JsonPrimitive(value);
        }
    }

    record Flag(Identifier id, boolean expected) implements ProgressionCondition {
        @Override
        public boolean test(ProgressionView view) {
            return view.has(id) == expected;
        }

        @Override
        public JsonElement toJson() {
            return new JsonPrimitive((expected ? "" : "!") + id);
        }
    }

    record All(List<ProgressionCondition> terms) implements ProgressionCondition {
        @Override
        public boolean test(ProgressionView view) {
            for (ProgressionCondition term : terms) {
                if (!term.test(view)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public JsonElement toJson() {
            JsonArray array = new JsonArray();
            terms.forEach(t -> array.add(t.toJson()));
            JsonObject obj = new JsonObject();
            obj.add("all", array);
            return obj;
        }
    }

    record Any(List<ProgressionCondition> terms) implements ProgressionCondition {
        @Override
        public boolean test(ProgressionView view) {
            for (ProgressionCondition term : terms) {
                if (term.test(view)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public JsonElement toJson() {
            JsonArray array = new JsonArray();
            terms.forEach(t -> array.add(t.toJson()));
            JsonObject obj = new JsonObject();
            obj.add("any", array);
            return obj;
        }
    }

    record Not(ProgressionCondition term) implements ProgressionCondition {
        @Override
        public boolean test(ProgressionView view) {
            return !term.test(view);
        }

        @Override
        public JsonElement toJson() {
            JsonObject obj = new JsonObject();
            obj.add("not", term.toJson());
            return obj;
        }
    }

    record Evil(WorldVariants.WorldEvil evil) implements ProgressionCondition {
        @Override
        public boolean test(ProgressionView view) {
            return view.variants().evil() == evil;
        }

        @Override
        public JsonElement toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("evil", evil.getSerializedName());
            return obj;
        }
    }

    record Counter(Identifier id, int min, int max) implements ProgressionCondition {
        @Override
        public boolean test(ProgressionView view) {
            int value = view.counter(id);
            return value >= min && value <= max;
        }

        @Override
        public JsonElement toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("counter", id.toString());
            if (min != Integer.MIN_VALUE) {
                obj.addProperty("min", min);
            }
            if (max != Integer.MAX_VALUE) {
                obj.addProperty("max", max);
            }
            return obj;
        }
    }

    /** Helper for building conditions in Java (tests, defaults). */
    static ProgressionCondition all(ProgressionCondition... terms) {
        return new All(List.of(terms));
    }

    static ProgressionCondition any(ProgressionCondition... terms) {
        return new Any(List.of(terms));
    }
}
