package com.squidgame.build.arena.marbles;

import com.squidgame.build.BlockBuffer;
import com.squidgame.build.StateString;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.HashMap;
import java.util.Map;

/**
 * Post-pass over the finished buffer. The world placer does not recompute stair shapes (only fences, panes and walls get
 * their connections fixed), so inner / outer corner stairs are computed here with the vanilla rules
 * ({@code StairBlock#getStairsShape}) from the facing and half of the neighbouring stairs.
 */
final class Fixup {
    private Fixup() {
    }

    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // north, east, south, west (x, z)
    private static final String[] NAMES = {"north", "east", "south", "west"};

    /** parsed stair: facing index 0..3, half 0 = bottom / 1 = top */
    private record Stair(int facing, int half) {
    }

    static void stairShapes(BlockBuffer b) {
        Map<String, Stair> cache = new HashMap<>();
        LongArrayList positions = new LongArrayList();
        b.forEach((x, y, z, s) -> {
            if (s.contains("_stairs[")) {
                positions.add(pack(x, y, z));
            }
        });
        // read all first so rewriting a shape never influences another stair's computation
        int n = positions.size();
        String[] updated = new String[n];
        for (int i = 0; i < n; i++) {
            long p = positions.getLong(i);
            int x = ux(p), y = uy(p), z = uz(p);
            String s = b.get(x, y, z);
            Stair st = parse(s, cache);
            if (st == null) {
                continue;
            }
            String shape = shape(b, cache, x, y, z, st);
            if (!shape.equals(StateString.property(s, "shape"))) {
                updated[i] = StateString.with(s, "shape", shape);
            }
        }
        for (int i = 0; i < n; i++) {
            if (updated[i] != null) {
                long p = positions.getLong(i);
                b.set(ux(p), uy(p), uz(p), updated[i]);
            }
        }
    }

    private static String shape(BlockBuffer b, Map<String, Stair> cache, int x, int y, int z, Stair st) {
        int f = st.facing();
        int[] d = DIRS[f];
        Stair front = parse(b.get(x + d[0], y, z + d[1]), cache);
        if (front != null && front.half() == st.half() && axis(front.facing()) != axis(f)
                && canTake(b, cache, x, y, z, st, opposite(front.facing()))) {
            return front.facing() == ccw(f) ? "outer_left" : "outer_right";
        }
        int[] o = DIRS[opposite(f)];
        Stair back = parse(b.get(x + o[0], y, z + o[1]), cache);
        if (back != null && back.half() == st.half() && axis(back.facing()) != axis(f)
                && canTake(b, cache, x, y, z, st, back.facing())) {
            return back.facing() == ccw(f) ? "inner_left" : "inner_right";
        }
        return "straight";
    }

    private static boolean canTake(BlockBuffer b, Map<String, Stair> cache, int x, int y, int z, Stair st, int face) {
        int[] d = DIRS[face];
        Stair n = parse(b.get(x + d[0], y, z + d[1]), cache);
        return n == null || n.facing() != st.facing() || n.half() != st.half();
    }

    private static int axis(int f) {
        return f & 1; // north/south = 0, east/west = 1
    }

    private static int opposite(int f) {
        return (f + 2) & 3;
    }

    /** counter-clockwise (viewed from above): north -> west -> south -> east -> north */
    private static int ccw(int f) {
        return (f + 3) & 3;
    }

    private static Stair parse(String s, Map<String, Stair> cache) {
        if (s == null || !s.contains("_stairs[")) {
            return null;
        }
        Stair st = cache.get(s);
        if (st == null) {
            String face = StateString.property(s, "facing");
            String half = StateString.property(s, "half");
            int f = -1;
            for (int i = 0; i < 4; i++) {
                if (NAMES[i].equals(face)) {
                    f = i;
                }
            }
            if (f < 0) {
                return null;
            }
            st = new Stair(f, "top".equals(half) ? 1 : 0);
            cache.put(s, st);
        }
        return st;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x + 2_000_000) << 40) | ((long) (y + 1024) << 24) | (long) (z + 2_000_000);
    }

    private static int ux(long p) {
        return (int) (p >> 40) - 2_000_000;
    }

    private static int uy(long p) {
        return (int) ((p >> 24) & 0xFFFF) - 1024;
    }

    private static int uz(long p) {
        return (int) (p & 0xFFFFFF) - 2_000_000;
    }
}
