package com.squidgame.build;

/**
 * A named point of interest produced by an arena builder and consumed by game logic (spawn
 * positions, lines, posts...). Positions are in <b>world</b> coordinates once the builder context
 * has applied its origin; {@code yaw} is Minecraft yaw in degrees (0 = south/+Z, 90 = west/-X,
 * 180 = north/-Z, -90 = east/+X). {@code data} is free-form (e.g. "row=3,lane=1").
 */
public record Marker(String name, double x, double y, double z, float yaw, String data) {
    public Marker(String name, double x, double y, double z, float yaw) {
        this(name, x, y, z, yaw, "");
    }

    /** Block coordinate helpers. */
    public int bx() {
        return (int) Math.floor(x);
    }

    public int by() {
        return (int) Math.floor(y);
    }

    public int bz() {
        return (int) Math.floor(z);
    }

    /** Value of {@code key} in a {@code k=v,k=v} data string, or {@code fallback}. */
    public String get(String key, String fallback) {
        if (data == null || data.isEmpty()) {
            return fallback;
        }
        for (String part : data.split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).trim().equals(key)) {
                return part.substring(eq + 1).trim();
            }
        }
        return fallback;
    }

    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, Integer.toString(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
