package com.longnight.content;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.longnight.LongNight;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Data-driven content: cultures, faiths, names, lore books, epitaphs and rumour templates.
 * Everything is loaded from datapacks so new peoples, faiths and books can be added without code.
 */
public final class Content {
    private Content() {}

    public static final Map<String, Culture> CULTURES = new LinkedHashMap<>();
    public static final Map<String, Faith> FAITHS = new LinkedHashMap<>();
    public static final Map<String, NameSet> NAMES = new HashMap<>();
    public static final Map<String, LoreBook> LORE = new LinkedHashMap<>();
    public static final Map<String, List<String>> EPITAPHS = new HashMap<>();
    public static final Map<String, List<String>> RUMOURS = new HashMap<>();

    public static final String[] CULTURE_IDS = {"valemen", "tidefolk", "aszari", "eldrin", "dunmar", "burrowfolk", "ashborn", "skrell"};
    public static final String[] FAITH_IDS = {"hearth", "sun", "tide", "grove", "ancestors", "stars", "ember", "veiled_choir"};

    public record LoreBook(String id, String title, String author, String rarity, List<String> cultures, List<String> tags,
                           List<String> pages) {}

    public static Culture culture(String id) {
        Culture c = CULTURES.get(id);
        if (c == null) c = CULTURES.get("valemen");
        if (c == null) c = fallbackCulture();
        return c;
    }

    public static Faith faith(String id) {
        Faith f = FAITHS.get(id);
        if (f == null) {
            f = new Faith();
            f.id = id;
            f.name = id;
        }
        return f;
    }

    public static NameSet names(String culture) {
        NameSet n = NAMES.get(culture);
        return n != null ? n : NameSet.FALLBACK;
    }

    public static String rumour(String type, Random rng) {
        List<String> l = RUMOURS.get(type);
        if (l == null || l.isEmpty()) return null;
        return l.get(rng.nextInt(l.size()));
    }

    public static String epitaph(String faith, Random rng) {
        List<String> l = EPITAPHS.getOrDefault(faith, EPITAPHS.get("default"));
        if (l == null || l.isEmpty()) return "Remembered.";
        return l.get(rng.nextInt(l.size()));
    }

    private static Culture fallbackCulture() {
        Culture c = new Culture();
        c.id = "valemen";
        c.name = "Valemen";
        c.faiths.put("hearth", 1.0);
        c.governments.add("MONARCHY");
        c.bodies.add("human_2");
        c.hairStyles.add("short");
        c.hairColors.add(0x5a3a22);
        c.clothColors.add(0x7a5c3a);
        return c;
    }

    // ------------------------------------------------------------------ loading

    public static void applyJson(String dir, ResourceLocation id, JsonElement json) {
        if (!LongNight.MODID.equals(id.getNamespace()) || !json.isJsonObject()) return;
        JsonObject o = json.getAsJsonObject();
        String key = id.getPath();
        try {
            switch (dir) {
                case "longnight_cultures" -> CULTURES.put(key, Culture.parse(key, o));
                case "longnight_faiths" -> FAITHS.put(key, Faith.parse(key, o));
                case "names" -> NAMES.put(key, NameSet.parse(o));
                case "lore" -> {
                    if (key.equals("epitaphs")) {
                        EPITAPHS.clear();
                        for (Map.Entry<String, JsonElement> e : o.entrySet()) EPITAPHS.put(e.getKey(), strings(e.getValue()));
                    } else if (key.equals("rumours")) {
                        RUMOURS.clear();
                        JsonObject t = o.has("templates") ? o.getAsJsonObject("templates") : o;
                        for (Map.Entry<String, JsonElement> e : t.entrySet()) RUMOURS.put(e.getKey(), strings(e.getValue()));
                    } else {
                        List<String> cultures = o.has("cultures") ? strings(o.get("cultures")) : List.of();
                        List<String> tags = o.has("tags") ? strings(o.get("tags")) : List.of();
                        LORE.put(key, new LoreBook(key, Culture.str(o, "title", key), Culture.str(o, "author", "Unknown"),
                                Culture.str(o, "rarity", "common"), cultures, tags, strings(o.get("pages"))));
                    }
                }
                default -> {
                }
            }
        } catch (Exception e) {
            LongNight.LOG.error("Bad longnight data file {}/{}: {}", dir, id, e.toString());
        }
    }

    static List<String> strings(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e != null && e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) out.add(x.getAsString());
        return out;
    }

    /** Reload listener for one data directory. */
    public static class Loader extends SimpleJsonResourceReloadListener {
        private final String dir;

        public Loader(String dir) {
            super(new Gson(), dir);
            this.dir = dir;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller profiler) {
            switch (dir) {
                case "longnight_cultures" -> CULTURES.clear();
                case "longnight_faiths" -> FAITHS.clear();
                case "names" -> NAMES.clear();
                case "lore" -> LORE.clear();
                default -> {
                }
            }
            map.forEach((id, json) -> applyJson(dir, id, json));
            LongNight.LOG.info("Loaded longnight {}: cultures={} faiths={} names={} lore={}", dir, CULTURES.size(),
                    FAITHS.size(), NAMES.size(), LORE.size());
        }
    }

    /** For unit tests and early startup: read the bundled files straight from the classpath. */
    public static void loadBundled() {
        for (String c : CULTURE_IDS) {
            readBundled("longnight_cultures", c);
            readBundled("names", c);
        }
        for (String f : FAITH_IDS) readBundled("longnight_faiths", f);
        readBundled("lore", "epitaphs");
        readBundled("lore", "rumours");
    }

    private static void readBundled(String dir, String id) {
        String path = "/data/longnight/" + dir + "/" + id + ".json";
        try (InputStream in = Content.class.getResourceAsStream(path)) {
            if (in == null) return;
            JsonElement e = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            applyJson(dir, ResourceLocation.fromNamespaceAndPath(LongNight.MODID, id), e);
        } catch (Exception ex) {
            LongNight.LOG.warn("Could not read bundled {}: {}", path, ex.toString());
        }
    }
}
