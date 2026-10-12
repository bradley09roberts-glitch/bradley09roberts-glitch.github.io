package com.longnight.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A people and its way of life, loaded from data/longnight/longnight_cultures/<id>.json. */
public class Culture {
    public String id;
    public String name;
    public String people;          // human, elf, dwarf, smallfolk, orc, goblin
    public String description = "";
    public float scale = 1f;       // body scale of adults
    public int lifespan = 70;      // typical lifespan in years
    public final Map<String, Double> faiths = new LinkedHashMap<>();
    public final List<String> governments = new ArrayList<>();
    public final List<String> biomeKeywords = new ArrayList<>();   // matched against biome ids
    public final List<String> avoidBiomeKeywords = new ArrayList<>();
    public final List<String> bodies = new ArrayList<>();
    public final List<String> hairStyles = new ArrayList<>();
    public final List<Integer> hairColors = new ArrayList<>();
    public final List<Integer> clothColors = new ArrayList<>();
    public final Map<String, String> palette = new HashMap<>();
    public String roofStyle = "gable";      // gable | flat | turf
    public final Map<String, Float> temperament = new HashMap<>();
    public boolean outlawProne;              // supplies bandit bands
    public int bannerColor;                  // DyeColor id
    public int bannerAccent;

    public String block(String role, String fallback) {
        return palette.getOrDefault(role, fallback);
    }

    public float trait(String name) {
        return temperament.getOrDefault(name, 0.5f);
    }

    public static Culture parse(String id, JsonObject o) {
        Culture c = new Culture();
        c.id = id;
        c.name = str(o, "name", id);
        c.people = str(o, "people", "human");
        c.description = str(o, "description", "");
        c.scale = o.has("scale") ? o.get("scale").getAsFloat() : 1f;
        c.lifespan = o.has("lifespan") ? o.get("lifespan").getAsInt() : 70;
        if (o.has("faiths")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("faiths").entrySet()) {
                c.faiths.put(e.getKey(), e.getValue().getAsDouble());
            }
        }
        strings(o, "governments", c.governments);
        strings(o, "biomes", c.biomeKeywords);
        strings(o, "avoid_biomes", c.avoidBiomeKeywords);
        strings(o, "bodies", c.bodies);
        strings(o, "hair_styles", c.hairStyles);
        colors(o, "hair_colors", c.hairColors);
        colors(o, "cloth_colors", c.clothColors);
        if (o.has("palette")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("palette").entrySet()) {
                c.palette.put(e.getKey(), e.getValue().getAsString());
            }
        }
        c.roofStyle = str(o, "roof_style", "gable");
        if (o.has("temperament")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("temperament").entrySet()) {
                c.temperament.put(e.getKey(), e.getValue().getAsFloat());
            }
        }
        c.outlawProne = o.has("outlaw_prone") && o.get("outlaw_prone").getAsBoolean();
        c.bannerColor = o.has("banner_color") ? o.get("banner_color").getAsInt() : 14;
        c.bannerAccent = o.has("banner_accent") ? o.get("banner_accent").getAsInt() : 0;
        return c;
    }

    static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    static void strings(JsonObject o, String k, List<String> out) {
        if (!o.has(k) || !o.get(k).isJsonArray()) return;
        JsonArray a = o.getAsJsonArray(k);
        for (JsonElement e : a) out.add(e.getAsString());
    }

    static void colors(JsonObject o, String k, List<Integer> out) {
        if (!o.has(k) || !o.get(k).isJsonArray()) return;
        for (JsonElement e : o.getAsJsonArray(k)) {
            String s = e.getAsString().replace("#", "");
            try {
                out.add(Integer.parseInt(s, 16));
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
