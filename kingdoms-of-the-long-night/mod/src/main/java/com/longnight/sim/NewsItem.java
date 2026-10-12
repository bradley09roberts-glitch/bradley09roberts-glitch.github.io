package com.longnight.sim;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Something that happened. News starts at its origin (witnesses) and travels with caravans,
 * travellers and messengers; each settlement records the day it learned of it.
 * {@code text} is the factual account; {@code vars} hold names so that people can retell it
 * in their own words using rumour templates.
 */
public record NewsItem(long id, long day, String type, String text, UUID origin, int importance, Map<String, String> vars) {
    public String retell(String template) {
        if (template == null) return text;
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) out = out.replace("{" + e.getKey() + "}", e.getValue());
        // if the template needs something this news does not have, fall back to the plain account
        return out.contains("{") ? text : out;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("id", id);
        t.putLong("day", day);
        t.putString("type", type);
        t.putString("text", text);
        Nbt.putUUID(t, "origin", origin);
        t.putInt("imp", importance);
        CompoundTag v = new CompoundTag();
        vars.forEach(v::putString);
        t.put("vars", v);
        return t;
    }

    public static NewsItem load(CompoundTag t) {
        Map<String, String> vars = new HashMap<>();
        CompoundTag v = t.getCompound("vars");
        for (String k : v.getAllKeys()) vars.put(k, v.getString(k));
        return new NewsItem(t.getLong("id"), t.getLong("day"), t.getString("type"), t.getString("text"),
                Nbt.getUUID(t, "origin"), t.getInt("imp"), vars);
    }
}
