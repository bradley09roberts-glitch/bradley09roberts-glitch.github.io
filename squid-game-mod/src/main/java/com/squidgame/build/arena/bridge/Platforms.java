package com.squidgame.build.arena.bridge;

import com.squidgame.build.BuildContext;

/**
 * The two 24 x 16 platforms. Both are built from the same local frame (x -12..11, zl -7..8) where zl = -7 is the row
 * against the hall wall and zl = 8 is the edge facing the bridge. The end platform is the mirror image about z = 36.
 *
 * <p>Surface: a checker of white tiles (the queue/gathering slots) on black tiles, glowing lane lines that also light
 * the platform (>= level 12 everywhere), a lit aisle along the bridge axis, pink glow strip under the top edge,
 * safety rails on the two sides (never in the walking area), a stepped concrete keel and two pylons down to the pit.
 */
final class Platforms {
    private Platforms() {
    }

    /** Lane line columns that glow white (mirror symmetric about the bridge axis). */
    static final int[] LANE_LINES = {-10, -5, 4, 9};

    static void build(BuildContext c) {
        platform(c, false);
        platform(c, true);
        gateWall(c);
    }

    // ------------------------------------------------------------------ helpers

    private static int zz(int zl, boolean end) {
        return end ? Geo.mz(zl) : zl;
    }

    private static void fz(BuildContext c, boolean end, int x1, int y1, int zl1, int x2, int y2, int zl2, String s) {
        c.fill(x1, y1, zz(zl1, end), x2, y2, zz(zl2, end), s);
    }

    private static void sz(BuildContext c, boolean end, int x, int y, int zl, String s) {
        c.set(x, y, zz(zl, end), s);
    }

    /** True for the cells where a contestant is meant to stand (the white tiles of the checker), mirror symmetric. */
    static boolean isSlot(int x, int zl) {
        if (x == -1 || x == 0 || x < -11 || x > 10) {
            return false;
        }
        int xm = x >= 1 ? x : Geo.mx(x);
        return ((xm + zl) & 1) == 0;
    }

    // ------------------------------------------------------------------ platform

    private static void platform(BuildContext c, boolean end) {
        int hz = Geo.START_Z0; // -7
        // 1. surface
        for (int x = Geo.PX0; x <= Geo.PX1; x++) {
            for (int zl = hz; zl <= 8; zl++) {
                sz(c, end, x, Geo.DECK, zl, surface(x, zl, end));
            }
        }
        // 2. slab and stepped keel underneath
        fz(c, end, -12, 38, -7, 11, 39, 8, Pal.BLK);
        fz(c, end, -11, 37, -7, 10, 37, 7, Pal.DSP);
        fz(c, end, -10, 36, -7, 9, 36, 6, Pal.BLK);
        fz(c, end, -9, 35, -7, 8, 35, 5, Pal.DSB);
        fz(c, end, -8, 34, -7, 7, 34, 4, Pal.BLK);
        fz(c, end, -7, 33, -7, 6, 33, 3, Pal.DSP);
        // pink glow line along the three free edges, just under the top tiles
        fz(c, end, -12, 39, -7, -12, 39, 8, Pal.PINK_LIGHT);
        fz(c, end, 11, 39, -7, 11, 39, 8, Pal.PINK_LIGHT);
        fz(c, end, -12, 39, 8, 11, 39, 8, Pal.PINK_LIGHT);
        // dark fascia details: rivet rows
        for (int x = -12; x <= 11; x += 2) {
            sz(c, end, x, 38, 8, Pal.DSCH);
        }
        for (int zl = -7; zl <= 8; zl += 2) {
            sz(c, end, -12, 38, zl, Pal.DSCH);
            sz(c, end, 11, 38, zl, Pal.DSCH);
        }
        // 3. rails
        rails(c, end);
        // 4. pylons
        pylon(c, end, -7);
        pylon(c, end, 3);
        // X braces between the pylons
        brace(c, end, Geo.PIT + 4, -4);
        brace(c, end, -4, 22);
    }

    private static String surface(int x, int zl, boolean end) {
        if (x == Geo.PX0 || x == Geo.PX1) {
            return Pal.PBS;
        }
        if (x == -1 || x == 0) { // aisle along the bridge axis
            return end ? Pal.GREEN_LIGHT : Pal.PINK_LIGHT;
        }
        for (int l : LANE_LINES) {
            if (x == l) {
                return Pal.WHITE_LIGHT;
            }
        }
        if (zl == 8) { // bridge-facing edge
            return end ? Pal.TILE_P : Pal.TILE_B;
        }
        return isSlot(x, zl) ? Pal.TILE_W : Pal.TILE_B;
    }

    private static void rails(BuildContext c, boolean end) {
        int zEnd = end ? 8 : 7; // the start platform meets the gate wall at zl = 7..8
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? Geo.PX0 : Geo.PX1;
            for (int zl = -7; zl <= zEnd; zl++) {
                boolean post = zl == -7 || zl == zEnd || Math.floorMod(zl + 7, 4) == 0;
                if (post) {
                    sz(c, end, x, 41, zl, Pal.BLK);
                    sz(c, end, x, 42, zl, Pal.BLK);
                    sz(c, end, x, 43, zl, Pal.PINK_LIGHT);
                } else {
                    sz(c, end, x, 41, zl, Pal.BARS);
                    sz(c, end, x, 42, zl, Pal.BARS);
                }
            }
        }
    }

    /** A 4x4 pylon at x0..x0+3, zl 0..3 from the pit floor up into the keel, with glowing pink rings. */
    private static void pylon(BuildContext c, boolean end, int x0) {
        int x1 = x0 + 3;
        fz(c, end, x0, Geo.PIT + 1, 0, x1, 32, 3, Pal.BLK);
        // corner posts and bands
        for (int y = Geo.PIT + 1; y <= 32; y++) {
            int m = Math.floorMod(y - Geo.PIT, 8);
            if (m == 0 || m == 1) {
                fz(c, end, x0, y, 0, x1, y, 3, Pal.DSB);
            }
        }
        for (int dx : new int[]{x0, x1}) {
            fz(c, end, dx, Geo.PIT + 1, 0, dx, 32, 0, Pal.DSP);
            fz(c, end, dx, Geo.PIT + 1, 3, dx, 32, 3, Pal.DSP);
        }
        // glowing pink rings (perimeter of the 4x4)
        for (int y : new int[]{-23, -7, 9, 25}) {
            for (int x = x0; x <= x1; x++) {
                for (int zl = 0; zl <= 3; zl++) {
                    boolean edge = x == x0 || x == x1 || zl == 0 || zl == 3;
                    if (edge) {
                        sz(c, end, x, y, zl, Pal.PINK_LIGHT);
                    }
                }
            }
        }
        // flared foot
        fz(c, end, x0 - 1, Geo.PIT + 1, -1, x1 + 1, Geo.PIT + 2, 4, Pal.DSB);
        fz(c, end, x0 - 1, Geo.PIT + 3, -1, x1 + 1, Geo.PIT + 3, 4, Pal.BLK);
    }

    /** One X shaped brace between the two pylons (they stand 6 apart), 2 thick, between two heights. */
    private static void brace(BuildContext c, boolean end, int yLow, int yHigh) {
        int xa = -3, xb = 2;
        for (int zl = 1; zl <= 2; zl++) {
            int z = zz(zl, end);
            c.line(xa, yLow, z, xb, yHigh, z, Pal.BLK);
            c.line(xa, yLow + 1, z, xb, yHigh + 1, z, Pal.BLK);
            c.line(xb, yLow, z, xa, yHigh, z, Pal.BLK);
            c.line(xb, yLow + 1, z, xa, yHigh + 1, z, Pal.BLK);
        }
        // cross members at both ends
        fz(c, end, xa, yLow, 0, xb, yLow + 1, 3, Pal.DSB);
        fz(c, end, xa, yHigh, 0, xb, yHigh + 1, 3, Pal.DSB);
    }

    // ------------------------------------------------------------------ gate wall of the start platform

    /**
     * The wall across the front of the start platform (z = 7..8). The opening (x -3..3, y 41..45) at the door plane
     * z = 8 is left empty: the server installs the sliding door panels there (marker bridge.gate).
     */
    private static void gateWall(BuildContext c) {
        int zA = 7, zB = 8;
        c.fill(-12, 41, zA, 11, 49, zB, Pal.BLK);
        // panelled faces (queue side z = 7, bridge side z = 8)
        for (int x = -12; x <= 11; x++) {
            for (int y = 43; y <= 48; y++) {
                int r = Pal.pct(x, y, 0, 5);
                String s = Math.floorMod(x, 6) == 0 ? Pal.BLK : (r < 18 ? Pal.DST : (r < 26 ? Pal.DSP : Pal.GRY));
                c.set(x, y, zA, s);
                c.set(x, y, zB, s);
            }
        }
        // pink glow strip along the wall base on both sides
        for (int x = -12; x <= 11; x++) {
            c.set(x, 41, zA, Pal.PINK_LIGHT);
            c.set(x, 41, zB, Pal.PINK_LIGHT);
        }
        // opening 7 x 5 through both layers
        c.clear(-3, 41, zA, 3, 45, zB);
        // glowing pink jambs and a black lintel with the three symbols
        for (int y = 41; y <= 47; y++) {
            for (int z = zA; z <= zB; z++) {
                c.set(-4, y, z, Pal.PINK_LIGHT);
                c.set(4, y, z, Pal.PINK_LIGHT);
            }
        }
        c.fill(-4, 46, zA, 4, 48, zB, Pal.BLK);
        for (int z = zA; z <= zB; z++) {
            c.set(-1, 47, z, Pal.SYM_CIRCLE);
            c.set(0, 47, z, Pal.SYM_TRIANGLE);
            c.set(1, 47, z, Pal.SYM_SQUARE);
        }
        // crown: black band with a pink glow lip, stepped cornice
        c.fill(-12, 50, zA, 11, 53, zB, Pal.BLK);
        c.fill(-12, 50, zA, 11, 50, zA, Pal.PINK_LIGHT);
        c.fill(-12, 50, zB, 11, 50, zB, Pal.PINK_LIGHT);
        c.fill(-13, 54, zA - 1, 12, 54, zB + 1, Pal.PBS);
        c.fill(-12, 55, zA, 11, 55, zB, Pal.BLK);
        // corner towers of the gate frame
        for (int[] t : new int[][]{{-12, -9}, {8, 11}}) {
            c.fill(t[0], 41, zA, t[1], 56, zB, Pal.BLK);
            c.fill(t[0], 41, zA, t[0], 56, zA, Pal.DSP);
            c.fill(t[1], 41, zA, t[1], 56, zA, Pal.DSP);
            c.fill(t[0] + 1, 56, zA, t[1] - 1, 56, zB, Pal.WHITE_LIGHT);
        }
        // sign on the queue side
        c.text(0.5, 52.0, zA - 0.05, "GLASS BRIDGE", "#FFFFFF", 9f, 180f, false);
        c.text(0.5, 49.6, zA - 0.05, "ONE PANEL IN TWO HOLDS", "#FF7AA8", 3f, 180f, false);
    }
}
