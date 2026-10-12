package com.grandline.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * The latest quest guidance from the server (QuestGuide): the tracked quest, its current objective, where it is done
 * and the route there. Drawn by the minimap and the sea chart.
 */
public final class Guidance {
    public static Component title, objective, label, hint;
    public static boolean hasTarget;
    public static int tx, tz;
    public static String dim = "";
    /** Route as x,z pairs from the player (when the guidance was made) to the target. */
    public static int[] route = new int[0];

    private Guidance() {}

    public static void accept(CompoundTag t) {
        title = parse(t, "title");
        objective = parse(t, "obj");
        label = parse(t, "label");
        hint = parse(t, "hint");
        hasTarget = t.getBoolean("has");
        tx = t.getInt("tx");
        tz = t.getInt("tz");
        dim = t.getString("dim");
        route = t.getIntArray("route");
    }

    public static boolean active() {
        return title != null;
    }

    /** Whether the target is in the dimension the player is in. */
    public static boolean here() {
        var l = Minecraft.getInstance().level;
        return hasTarget && l != null && l.dimension().location().toString().equals(dim);
    }

    public static Component parse(CompoundTag t, String key) {
        if (!t.contains(key)) return null;
        var l = Minecraft.getInstance().level;
        try {
            return l == null ? Component.literal(t.getString(key)) : Component.Serializer.fromJson(t.getString(key), l.registryAccess());
        } catch (RuntimeException e) {
            return Component.literal(t.getString(key));
        }
    }

    public static void reset() {
        title = objective = label = hint = null;
        hasTarget = false;
        route = new int[0];
    }
}
