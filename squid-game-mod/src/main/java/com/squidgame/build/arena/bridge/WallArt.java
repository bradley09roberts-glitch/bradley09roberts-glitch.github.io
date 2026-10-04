package com.squidgame.build.arena.bridge;

import com.squidgame.build.BuildContext;

/**
 * Wall dressing drawn in the canonical wall frame (u along the wall, local z = depth into the hall, y absolute):
 * the huge pink banners with the three symbols, the giant glowing symbols, the balcony gallery of the back wall and
 * the exit door, green EXIT letters and neon of the front wall.
 */
final class WallArt {
    private WallArt() {
    }

    static final int BACK = 0, FRONT = 1, WEST = 2, EAST = 3;

    private static final String[][] SYM = {
            {".###.", "#...#", "#...#", "#...#", ".###."},
            {"..#..", ".#.#.", ".#.#.", "#...#", "#####"},
            {"#####", "#...#", "#...#", "#...#", "#####"}
    };

    private static final String[][] LETTERS = {
            // E
            {"#####", "#....", "#....", "####.", "#....", "#....", "#####"},
            // X
            {"#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"},
            // I
            {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"},
            // T
            {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."}
    };

    static final int BANNER_TOP = 86;
    static final int BANNER_H = 24;

    static void decorate(BuildContext c, int len, int wall) {
        switch (wall) {
            case WEST, EAST -> {
                int[] bays = {8, 22, 36, 50, 64, 78};
                for (int i = 0; i < bays.length; i++) {
                    banner(c, bays[i], i);
                }
            }
            case BACK -> {
                for (int[] b : new int[][]{{16, 0}, {30, 1}, {59, 2}, {73, 0}}) {
                    banner(c, b[0], b[1]);
                }
                balcony(c);
            }
            default -> {
                for (int[] b : new int[][]{{16, 1}, {30, 2}, {59, 0}, {73, 1}}) {
                    banner(c, b[0], b[1]);
                }
                exitWall(c);
            }
        }
    }

    // ------------------------------------------------------------------ banners

    /** A 9 x 24 pink banner with a black field and the three symbols stacked; {@code rot} cycles their order. */
    static void banner(BuildContext c, int uc, int rot) {
        int u0 = uc - 4, u1 = uc + 4;
        int yTop = BANNER_TOP, yBot = BANNER_TOP - BANNER_H + 1;
        c.fill(u0, yBot, 1, u1, yTop, 1, Pal.PINK_PANEL);
        c.fill(u0 + 1, yBot + 1, 1, u1 - 1, yTop - 1, 1, Pal.BLK);
        // hanging rod with caps
        c.fill(u0 - 1, yTop + 1, 1, u1 + 1, yTop + 1, 2, Pal.BLK);
        c.set(u0 - 1, yTop + 1, 1, Pal.PINK_LIGHT);
        c.set(u1 + 1, yTop + 1, 1, Pal.PINK_LIGHT);
        // symbols: margin 2 | 5 | gap 2 | 5 | gap 2 | 5 | margin 1 inside the field
        int y = yTop - 1 - 2;
        for (int i = 0; i < 3; i++) {
            symbol(c, uc - 2, y, SYM[(i + rot) % 3], 1, Pal.WHITE);
            y -= 7;
        }
        // pink glow trim along the bottom edge
        c.fill(u0, yBot, 2, u1, yBot, 2, Pal.PINK_PANEL);
    }

    /** Draws a bitmap ('#' = block) with its top-left at (u0, yTop). */
    static void symbol(BuildContext c, int u0, int yTop, String[] bmp, int z, String state) {
        for (int r = 0; r < bmp.length; r++) {
            for (int k = 0; k < bmp[r].length(); k++) {
                if (bmp[r].charAt(k) == '#') {
                    c.set(u0 + k, yTop - r, z, state);
                }
            }
        }
    }

    // ------------------------------------------------------------------ giant glowing symbols

    /** Ring of outer radius r and given thickness around (uc, yc) (continuous coordinates) in the wall plane. */
    static void neonCircle(BuildContext c, double uc, double yc, double r, double thick, int z, String state) {
        int u0 = (int) Math.floor(uc - r) - 1, u1 = (int) Math.ceil(uc + r) + 1;
        int y0 = (int) Math.floor(yc - r) - 1, y1 = (int) Math.ceil(yc + r) + 1;
        for (int u = u0; u <= u1; u++) {
            for (int y = y0; y <= y1; y++) {
                double d = Math.hypot(u + 0.5 - uc, y + 0.5 - yc);
                if (d <= r && d > r - thick) {
                    c.set(u, y, z, state);
                }
            }
        }
    }

    /** Outline triangle with apex up: base from u0..u1 at yBase, height h, line thickness t. */
    static void neonTriangle(BuildContext c, int u0, int u1, int yBase, int h, int t, int z, String state) {
        double mid = (u0 + u1 + 1) / 2.0;
        double half = (u1 - u0 + 1) / 2.0;
        for (int y = 0; y <= h; y++) {
            double f = 1.0 - (double) y / h;
            double hw = half * f;
            for (int u = u0; u <= u1; u++) {
                double d = Math.abs(u + 0.5 - mid);
                boolean edge = d <= hw && d > hw - t * 1.15;
                boolean base = y < t;
                if (edge || (base && d <= hw)) {
                    c.set(u, yBase + y, z, state);
                }
            }
        }
    }

    // ------------------------------------------------------------------ back wall: gallery balcony

    /** x of block u on the back wall. */
    private static int ux(int u) {
        return u - 45;
    }

    static final int BALCONY_Y = 66;

    private static void balcony(BuildContext c) {
        int u0 = 29, u1 = 60; // x -16..15
        int y = BALCONY_Y;
        // floor (z = 1..6 proud of the wall), plated, with a pink glow lip and recessed light dashes
        for (int u = u0; u <= u1; u++) {
            for (int z = 1; z <= 6; z++) {
                c.set(u, y, z, (z == 6) ? Pal.BLK : (((u + z) & 1) == 0 ? Pal.DST : Pal.DSP));
            }
            c.set(u, y - 1, 6, Pal.PINK_LIGHT);
            c.set(u, y - 1, 5, Pal.BLK);
            c.set(u, y - 1, 4, Pal.DSB);
        }
        for (int u = u0 + 2; u <= u1 - 2; u += 4) {
            c.set(u, y, 2, Pal.PEARL);
            c.set(u + 2, y, 5, Pal.PEARL);
        }
        // glass balustrade along the front edge and the sides
        for (int u = u0; u <= u1; u++) {
            c.set(u, y + 1, 6, "minecraft:black_stained_glass");
            c.set(u, y + 2, 6, "minecraft:black_stained_glass");
        }
        for (int z = 1; z <= 6; z++) {
            for (int u : new int[]{u0, u1}) {
                c.set(u, y + 1, z, "minecraft:black_stained_glass");
                c.set(u, y + 2, z, "minecraft:black_stained_glass");
            }
        }
        // handrail
        c.fill(u0, y + 3, 6, u1, y + 3, 6, Pal.PBS);
        // canopy with a pink glow underside, carried by two pillars
        c.fill(u0 - 1, y + 6, 0, u1 + 1, y + 7, 7, Pal.BLK);
        c.fill(u0, y + 6, 1, u1, y + 6, 6, Pal.PINK_LIGHT);
        c.fill(u0 + 1, y + 6, 2, u1 - 1, y + 6, 5, Pal.BLK);
        for (int u : new int[]{u0, u1}) {
            c.fill(u, y + 3, 6, u, y + 5, 6, Pal.BLK);
        }
        // braces under the floor
        for (int u : new int[]{u0, 36, 44, 45, 53, u1}) {
            c.line(u, y - 6, 1, u, y - 1, 6, Pal.BLK);
        }
        // monitor wall: 14 x 2 screens facing the balcony
        for (int u = 38; u <= 51; u++) {
            for (int yy = y + 2; yy <= y + 3; yy++) {
                c.set(u, yy, 0, "squidgame:monitor[facing=south]");
            }
        }
        c.fill(37, y + 1, 0, 52, y + 1, 0, Pal.BLK);
        c.fill(37, y + 4, 0, 52, y + 4, 0, Pal.BLK);
        // giant glowing circle above the gallery
        neonCircle(c, 45.0, 81.0, 7.5, 2.2, 1, Pal.WHITE_LIGHT);
    }

    // ------------------------------------------------------------------ front wall: exit

    private static void exitWall(BuildContext c) {
        int y0 = Geo.STAND;
        // door opening 9 x 7 through the 3 thick wall: x -4..4 -> u 40..48 (front wall canonical, local z -2..0)
        c.clear(40, y0, -2, 48, y0 + 6, 0);
        // glowing green frame (jambs and lintel, flush with the tunnel), black surround standing 1 proud
        c.fill(39, y0, -2, 39, y0 + 7, 1, Pal.GREEN_LIGHT);
        c.fill(49, y0, -2, 49, y0 + 7, 1, Pal.GREEN_LIGHT);
        c.fill(39, y0 + 7, -2, 49, y0 + 7, 1, Pal.GREEN_LIGHT);
        c.fill(37, y0, 0, 38, y0 + 9, 1, Pal.BLK);
        c.fill(50, y0, 0, 51, y0 + 9, 1, Pal.BLK);
        c.fill(37, y0 + 8, 0, 51, y0 + 9, 1, Pal.BLK);
        c.fill(36, y0 + 10, 0, 52, y0 + 10, 1, Pal.PBS);
        // green strips in the tunnel floor
        for (int z = -2; z <= 0; z++) {
            c.set(40, Geo.DECK, z, Pal.GREEN_LIGHT);
            c.set(48, Geo.DECK, z, Pal.GREEN_LIGHT);
        }
        // EXIT: black plate in the wall face, green letters standing 1 proud (x -11..11 -> u 33..55, rows 52..58)
        c.fill(31, 51, 0, 57, 59, 0, Pal.BLK);
        c.fill(31, 51, 0, 57, 51, 0, Pal.GREEN_LIGHT);
        c.fill(31, 59, 0, 57, 59, 0, Pal.GREEN_LIGHT);
        int u = 33;
        for (String[] letter : LETTERS) {
            symbol(c, u, 58, letter, 1, Pal.GREEN_LIGHT);
            u += 6;
        }
        // giant glowing triangle above the exit
        neonTriangle(c, 38, 51, 68, 13, 2, 1, Pal.WHITE_LIGHT);
    }
}
