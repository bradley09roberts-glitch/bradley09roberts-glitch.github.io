package com.grandline.client.anim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grandline.GrandLine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.Reader;
import java.util.*;

/** Loads assets/grandline/animations/*.animation.json (Bedrock format) for the humanoid rig. Reloads with resource packs. */
public final class AnimLibrary implements ResourceManagerReloadListener {
    private static final Map<String, AnimDef> ANIMS = new HashMap<>();

    public static AnimDef get(String name) {
        return ANIMS.get(name);
    }

    public static int count() {
        return ANIMS.size();
    }

    @Override
    public void onResourceManagerReload(ResourceManager rm) {
        ANIMS.clear();
        for (Map.Entry<ResourceLocation, Resource> e : rm.listResources("animations", l -> l.getNamespace().equals(GrandLine.MODID) && l.getPath().endsWith(".animation.json")).entrySet()) {
            try (Reader r = e.getValue().openAsReader()) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                JsonObject anims = root.getAsJsonObject("animations");
                for (Map.Entry<String, JsonElement> a : anims.entrySet()) {
                    String name = a.getKey().contains(".") ? a.getKey().substring(a.getKey().lastIndexOf('.') + 1) : a.getKey();
                    ANIMS.put(name, parse(name, a.getValue().getAsJsonObject()));
                }
            } catch (Exception ex) {
                GrandLine.LOGGER.error("Failed to load animation file {}", e.getKey(), ex);
            }
        }
        GrandLine.LOGGER.info("Loaded {} humanoid animations", ANIMS.size());
    }

    static AnimDef parse(String name, JsonObject o) {
        float len = o.has("animation_length") ? o.get("animation_length").getAsFloat() : 0.5f;
        boolean loop = o.has("loop") && (o.get("loop").isJsonPrimitive() && (o.get("loop").getAsJsonPrimitive().isBoolean() ? o.get("loop").getAsBoolean() : o.get("loop").getAsString().equals("true")));
        Map<String, AnimDef.Track> bones = new HashMap<>();
        if (o.has("bones")) {
            for (Map.Entry<String, JsonElement> b : o.getAsJsonObject("bones").entrySet()) {
                JsonObject bo = b.getValue().getAsJsonObject();
                bones.put(b.getKey(), new AnimDef.Track(keys(bo.get("rotation")), keys(bo.get("position"))));
            }
        }
        return new AnimDef(name, len, loop, bones);
    }

    private static AnimDef.Key[] keys(JsonElement el) {
        if (el == null) return null;
        List<AnimDef.Key> out = new ArrayList<>();
        if (el.isJsonArray()) {
            var arr = el.getAsJsonArray();
            out.add(new AnimDef.Key(0, arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()));
        } else {
            for (Map.Entry<String, JsonElement> k : el.getAsJsonObject().entrySet()) {
                JsonElement v = k.getValue();
                var arr = v.isJsonArray() ? v.getAsJsonArray() : v.getAsJsonObject().getAsJsonArray("post");
                out.add(new AnimDef.Key(Float.parseFloat(k.getKey()), arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()));
            }
            out.sort(Comparator.comparingDouble(AnimDef.Key::t));
        }
        return out.toArray(new AnimDef.Key[0]);
    }
}
