package com.squidgame.build;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parses and rotates block-state strings of the form {@code namespace:id[prop=value,prop=value]}
 * without touching Minecraft classes. Rotation is clockwise when viewed from above, in 90 degree
 * steps; {@code facing}, {@code axis}, {@code rotation} and the connection properties
 * ({@code north/east/south/west}) are rotated, everything else is kept.
 */
public final class StateString {
    private static final String[] DIRS = {"north", "east", "south", "west"};
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private StateString() {
    }

    public static String blockId(String state) {
        int br = state.indexOf('[');
        return br < 0 ? state : state.substring(0, br);
    }

    /** Returns the property value or null. */
    public static String property(String state, String key) {
        int br = state.indexOf('[');
        if (br < 0) {
            return null;
        }
        for (String part : state.substring(br + 1, state.length() - 1).split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(key)) {
                return part.substring(eq + 1);
            }
        }
        return null;
    }

    /** Returns {@code state} with {@code key} set to {@code value} (properties kept sorted). */
    public static String with(String state, String key, String value) {
        String id = blockId(state);
        TreeMap<String, String> props = parse(state);
        props.put(key, value);
        return build(id, props);
    }

    private static TreeMap<String, String> parse(String state) {
        TreeMap<String, String> props = new TreeMap<>();
        int br = state.indexOf('[');
        if (br >= 0) {
            for (String part : state.substring(br + 1, state.length() - 1).split(",")) {
                int eq = part.indexOf('=');
                if (eq > 0) {
                    props.put(part.substring(0, eq), part.substring(eq + 1));
                }
            }
        }
        return props;
    }

    private static String build(String id, Map<String, String> props) {
        if (props.isEmpty()) {
            return id;
        }
        StringBuilder sb = new StringBuilder(id).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : props.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.append(']').toString();
    }

    private static int dirIndex(String d) {
        for (int i = 0; i < 4; i++) {
            if (DIRS[i].equals(d)) {
                return i;
            }
        }
        return -1;
    }

    /** Rotates a state clockwise by {@code steps} quarter turns (0-3, may be negative). */
    public static String rotate(String state, int steps) {
        steps = ((steps % 4) + 4) % 4;
        if (steps == 0 || state.indexOf('[') < 0) {
            return state;
        }
        String key = state + "#" + steps;
        String cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        String id = blockId(state);
        TreeMap<String, String> in = parse(state);
        TreeMap<String, String> out = new TreeMap<>();
        for (Map.Entry<String, String> e : in.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            int di;
            switch (k) {
                case "facing" -> {
                    di = dirIndex(v);
                    out.put(k, di >= 0 ? DIRS[(di + steps) % 4] : v);
                }
                case "axis" -> out.put(k, steps % 2 == 1 ? (v.equals("x") ? "z" : v.equals("z") ? "x" : v) : v);
                case "rotation" -> {
                    try {
                        out.put(k, Integer.toString((Integer.parseInt(v) + 4 * steps) % 16));
                    } catch (NumberFormatException ex) {
                        out.put(k, v);
                    }
                }
                case "north", "east", "south", "west" -> {
                    // connection properties: value moves to the rotated side
                    di = dirIndex(k);
                    out.put(DIRS[(di + steps) % 4], v);
                }
                default -> out.put(k, v);
            }
        }
        String result = build(id, out);
        CACHE.put(key, result);
        return result;
    }

    /** Mirrors a state across the X axis (east <-> west); swaps stair/door handedness. */
    public static String mirrorX(String state) {
        if (state.indexOf('[') < 0) {
            return state;
        }
        String id = blockId(state);
        TreeMap<String, String> in = parse(state);
        TreeMap<String, String> out = new TreeMap<>();
        for (Map.Entry<String, String> e : in.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            switch (k) {
                case "facing" -> out.put(k, v.equals("east") ? "west" : v.equals("west") ? "east" : v);
                case "east" -> out.put("west", v);
                case "west" -> out.put("east", v);
                case "shape" -> out.put(k, v.contains("left") ? v.replace("left", "right")
                        : v.contains("right") ? v.replace("right", "left") : v);
                case "hinge" -> out.put(k, v.equals("left") ? "right" : "left");
                case "rotation" -> {
                    try {
                        out.put(k, Integer.toString((16 - Integer.parseInt(v)) % 16));
                    } catch (NumberFormatException ex) {
                        out.put(k, v);
                    }
                }
                default -> out.put(k, v);
            }
        }
        // 'east'/'west' keys may have been overwritten in order; rebuild consistently
        return build(id, out);
    }
}
