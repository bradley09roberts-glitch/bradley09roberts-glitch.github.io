package com.squidgame.build.arena.hub.stairs;

import com.squidgame.build.BuildContext;

/**
 * The north end: a four-storey arcaded facade (arches on the six gate axes, lit galleries behind them) carrying the
 * gate terrace at y=60, and the six gates in the north wall: 5 x 5 doorways x = -37 ... +37 with a 3-deep alcove, a
 * closed black double door with a light seam, symbol-block jambs, a name board and a big symbol board above.
 */
final class Terrace {
    private Terrace() {
    }

    static final int LEVEL = 60;
    static final int[] GATE_X = {-37, -22, -7, 7, 22, 37};
    static final String[] GATE = {"red_light", "dalgona", "tug_of_war", "marbles", "glass_bridge", "final"};
    static final String[] TITLE = {"RED LIGHT, GREEN LIGHT", "DALGONA", "TUG OF WAR", "MARBLES", "GLASS BRIDGE", "THE FINAL GAME"};
    static final int[] COLOR = {Pal.PINK, Pal.YELLOW, Pal.PEACH, Pal.SKY, Pal.MINT, Pal.LILAC};
    /** 0 circle, 1 triangle, 2 square, 3 all three */
    static final int[] SYMBOL = {0, 1, 2, 0, 2, 3};
    private static final String[] SYM_BLOCK = {"squidgame:symbol_circle", "squidgame:symbol_triangle", "squidgame:symbol_square"};

    static final int FACE = -160;          // south face of the facade, terrace edge row
    static final int WALL = -171;          // inner face of the north wall

    static void build(Ctx k) {
        facade(k);
        floor(k);
        for (int g = 0; g < 6; g++) {
            gate(k, g);
        }
    }

    // ------------------------------------------------------------------ facade

    private static void facade(Ctx k) {
        BuildContext c = k.c;
        String cream = Pal.block(Pal.CREAM);
        // the slab carrying the terrace (floor top at LEVEL, blocks y=55..59), wall to wall over the whole depth
        k.fill(-45, 55, -170, 45, 59, FACE, cream, Occ.SOLID);
        // galleries: floors every 12 blocks behind the arcade (z -170 .. -163), facade wall z -162 .. -160
        for (int s = 0; s < 4; s++) {
            int y0 = 12 * s;
            int wallCol = s % 2 == 0 ? Pal.PEACH : Pal.PINK;
            k.fill(-45, y0, -162, 45, y0 + 9, FACE, Pal.block(wallCol), Occ.SOLID);        // facade wall of the storey
            k.fill(-45, y0 + 10, -170, 45, y0 + 11, FACE, cream, Occ.SOLID);               // floor slab above / ceiling below
            // band lines on the slab edge
            k.fill(-45, y0 + 10, FACE, 45, y0 + 10, FACE, Pal.block(Pal.wheel(s + 1)));
            // gallery interior lights in the ceiling, two rows of warm panels
            for (int x = -42; x <= 42; x += 6) {
                for (int z = -168; z <= -165; z += 3) {
                    c.set(x, y0 + 10, z, Pal.LIGHT_WARM);
                }
            }
            // arches through the facade wall on the gate axes
            for (int g = 0; g < 6; g++) {
                archOpening(k, GATE_X[g], y0, wallCol, Pal.wheel(g + s));
            }
            // storey cornice line at the very top of the wall
            k.fill(-45, y0 + 9, -162, 45, y0 + 9, FACE, cream);
        }
        // cornice storey: frieze with the three symbols, piers keep the rhythm
        k.fill(-45, 48, -162, 45, 54, FACE, Pal.block(Pal.LILAC), Occ.SOLID);
        k.fill(-45, 48, -162, 45, 48, FACE, cream);
        k.fill(-45, 54, -162, 45, 54, FACE, cream);
        for (int x = -43; x <= 43; x += 2) {
            int idx = Math.floorMod(x / 2, 3);
            k.set(x, 51, FACE, SYM_BLOCK[idx], Occ.SOLID);
        }
        // terrace edge: pink fascia row under the floor
        k.fill(-45, 55, FACE, 45, 55, FACE, Pal.block(Pal.PINK));
        k.fill(-45, 59, FACE, 45, 59, FACE, cream);
    }

    /** Round-topped opening 11 wide, 10 high through the 3-thick facade wall, with a 1-block archivolt in {@code ring}. */
    private static void archOpening(Ctx k, int cx, int y0, int wallCol, int ring) {
        BuildContext c = k.c;
        double r = 5.5;
        for (int y = y0; y <= y0 + 9; y++) {
            for (int x = cx - 6; x <= cx + 6; x++) {
                int dx = x - cx;
                boolean inside;
                if (y <= y0 + 3) {
                    inside = Math.abs(dx) <= 5;
                } else {
                    double dy = y - (y0 + 4);
                    inside = dx * dx + dy * dy <= r * r - 0.5;
                }
                boolean rim = false;
                if (!inside) {
                    // cells touching the opening on the outside form the archivolt
                    for (int a = -1; a <= 1 && !rim; a++) {
                        for (int b = -1; b <= 1; b++) {
                            if ((a == 0 || b == 0) && a != b) {
                                int ddx = dx + a, yy = y + b;
                                boolean in2;
                                if (yy <= y0 + 3) {
                                    in2 = yy >= y0 && Math.abs(ddx) <= 5;
                                } else {
                                    double ddy = yy - (y0 + 4);
                                    in2 = yy <= y0 + 9 && ddx * ddx + ddy * ddy <= r * r - 0.5;
                                }
                                if (in2) {
                                    rim = true;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (inside) {
                    c.fill(x, y, -162, x, y, FACE, Pal.AIR);
                } else if (rim) {
                    c.set(x, y, FACE, Pal.block(ring));
                    c.set(x, y, FACE - 1, Pal.block(ring));
                }
            }
        }
    }

    // ------------------------------------------------------------------ terrace floor

    private static void floor(Ctx k) {
        Look lk = Look.of(7, 3);
        Kit.landing(k, -45, -170, 45, FACE, LEVEL, lk, Kit.PAT_PLAIN, true);
        BuildContext c = k.c;
        // white tile with a quartz grid every 5 blocks
        for (int x = -45; x <= 45; x++) {
            for (int z = -170; z <= FACE; z++) {
                boolean grid = Math.floorMod(x, 5) == 0 || Math.floorMod(z, 5) == 0;
                c.set(x, LEVEL - 1, z, grid ? Pal.QUARTZ : Pal.TILE_WHITE);
            }
        }
        // a runner in the colour of each gate, 5 wide, from the edge to the door; cream borders
        for (int g = 0; g < 6; g++) {
            int cx = GATE_X[g];
            c.fill(cx - 3, LEVEL - 1, -170, cx - 3, LEVEL - 1, FACE, Pal.block(Pal.CREAM));
            c.fill(cx + 3, LEVEL - 1, -170, cx + 3, LEVEL - 1, FACE, Pal.block(Pal.CREAM));
            for (int z = -170; z <= FACE; z++) {
                for (int x = cx - 2; x <= cx + 2; x++) {
                    boolean alt = ((z / 2) & 1) == 0;
                    c.set(x, LEVEL - 1, z, Pal.block(alt ? COLOR[g] : Pal.CREAM));
                }
            }
        }
        // the arrival: a wide cream-and-pink mat in front of the foot of the last flight
        c.fill(-3, LEVEL - 1, FACE, 3, LEVEL - 1, FACE, Pal.block(Pal.PINK));
    }

    // ------------------------------------------------------------------ gates

    private static void gate(Ctx k, int g) {
        BuildContext c = k.c;
        int cx = GATE_X[g];
        int col = COLOR[g];
        String cream = Pal.block(Pal.CREAM);
        // alcove 5 wide, 5 high, z -171 .. -173; floor continues
        c.fill(cx - 2, LEVEL, -173, cx + 2, LEVEL + 4, WALL, Pal.AIR);
        c.fill(cx - 2, LEVEL - 1, -173, cx + 2, LEVEL - 1, WALL, Pal.block(col));
        c.fill(cx - 2, LEVEL - 2, -173, cx + 2, LEVEL - 2, WALL, cream);
        // alcove ceiling strip of warm light and cream side linings
        c.fill(cx - 2, LEVEL + 5, -173, cx + 2, LEVEL + 5, WALL, cream);
        c.fill(cx - 1, LEVEL + 5, -172, cx + 1, LEVEL + 5, WALL, Pal.LIGHT_WARM);
        c.fill(cx - 3, LEVEL, -173, cx - 3, LEVEL + 5, WALL, cream);
        c.fill(cx + 3, LEVEL, -173, cx + 3, LEVEL + 5, WALL, cream);
        // closed double door at the back (z = -174): black leaves, a glowing seam, symbol medallion
        c.fill(cx - 2, LEVEL, -174, cx + 2, LEVEL + 4, -174, "squidgame:tile_black");
        c.fill(cx, LEVEL, -174, cx, LEVEL + 4, -174, Pal.LIGHT_WHITE);
        c.fill(cx - 2, LEVEL, -174, cx - 2, LEVEL + 4, -174, Pal.block(col));
        c.fill(cx + 2, LEVEL, -174, cx + 2, LEVEL + 4, -174, Pal.block(col));
        c.fill(cx - 2, LEVEL + 4, -174, cx + 2, LEVEL + 4, -174, Pal.block(col));
        int sym = SYMBOL[g] == 3 ? 0 : SYMBOL[g];
        c.set(cx - 1, LEVEL + 2, -174, SYM_BLOCK[sym == 3 ? 0 : sym]);
        c.set(cx + 1, LEVEL + 2, -174, SYM_BLOCK[SYMBOL[g] == 3 ? 2 : sym]);
        // frame in the wall plane: pastel columns and lintel, 2 wide, up to the sign row
        c.fill(cx - 4, LEVEL, WALL, cx - 4, LEVEL + 5, WALL, Pal.block(col));
        c.fill(cx + 4, LEVEL, WALL, cx + 4, LEVEL + 5, WALL, Pal.block(col));
        c.fill(cx - 3, LEVEL + 5, WALL, cx + 3, LEVEL + 5, WALL, Pal.block(col));
        // jambs standing proud: 5 symbol blocks on each side, cream caps
        for (int side = -1; side <= 1; side += 2) {
            int jx = cx + side * 3;
            c.fill(jx, LEVEL, WALL + 1, jx, LEVEL + 4, WALL + 1, SYM_BLOCK[sym]);
            c.set(jx, LEVEL + 5, WALL + 1, cream);
            c.set(jx, LEVEL + 6, WALL + 1, Kit.LANTERN);
            // lamp on the wall beyond the jamb
            c.set(cx + side * 5, LEVEL + 2, WALL, Pal.SEA);
            c.set(cx + side * 5, LEVEL + 3, WALL, Pal.SEA);
            c.set(cx + side * 5, LEVEL + 1, WALL, cream);
            c.set(cx + side * 5, LEVEL + 4, WALL, cream);
        }
        // sign board (black) with the title, row LEVEL+5 .. LEVEL+6 over the lintel
        c.fill(cx - 6, LEVEL + 6, WALL, cx + 6, LEVEL + 6, WALL, "squidgame:tile_black");
        c.fill(cx - 6, LEVEL + 5, WALL, cx - 5, LEVEL + 5, WALL, "squidgame:tile_black");
        c.fill(cx + 5, LEVEL + 5, WALL, cx + 6, LEVEL + 5, WALL, "squidgame:tile_black");
        double chars = TITLE[g].length();
        float scale = (float) Math.min(4.2, 11.0 / (0.15 * chars));
        c.text(cx + 0.5, LEVEL + 6.15, WALL + 1 - 0.04, TITLE[g], "#FFFFFF", scale, 0f, false);
        // symbol board: rows LEVEL+7 .. LEVEL+12 (y 67..72), 13 wide
        board(k, g);
        c.region("gate." + GATE[g], cx - 2, LEVEL, WALL, cx + 2, LEVEL + 4, WALL);
    }

    /** The big symbol board above a gate: pastel field, cream border, white outline symbol (three icons for the finale). */
    private static void board(Ctx k, int g) {
        BuildContext c = k.c;
        int cx = GATE_X[g];
        int y0 = LEVEL + 7, y1 = Ctx.Y1;                 // 67 .. 72
        int h = y1 - y0 + 1;                              // 6 rows
        int col = COLOR[g];
        int hw = SYMBOL[g] == 3 ? 8 : 6;
        c.fill(cx - hw, y0, WALL, cx + hw, y1, WALL, Pal.block(col));
        c.fill(cx - hw, y0, WALL, cx + hw, y0, WALL, Pal.block(Pal.CREAM));
        c.fill(cx - hw, y1, WALL, cx + hw, y1, WALL, Pal.block(Pal.CREAM));
        c.fill(cx - hw, y0, WALL, cx - hw, y1, WALL, Pal.block(Pal.CREAM));
        c.fill(cx + hw, y0, WALL, cx + hw, y1, WALL, Pal.block(Pal.CREAM));
        int n = h - 2;                                    // icon height 4 inside the border
        if (SYMBOL[g] == 3) {
            icon(c, 0, cx - 5, y0 + 1, n);
            icon(c, 1, cx - 1, y0 + 1, n);
            icon(c, 2, cx + 3, y0 + 1, n);
        } else {
            icon(c, SYMBOL[g], cx - n / 2, y0 + 1, n);
        }
    }

    /** White outline icon of size n x n whose lower-left cell is (x0, y0) on the wall plane. */
    private static void icon(BuildContext c, int shape, int x0, int y0, int n) {
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                if (iconCell(shape, i, j, n)) {
                    c.set(x0 + i, y0 + j, WALL, Pal.WHITE);
                }
            }
        }
    }

    static boolean iconCell(int shape, int i, int j, int n) {
        double m = (n - 1) / 2.0;
        switch (shape) {
            case 0 -> {
                double d = Math.hypot(i - m, j - m);
                return d <= n / 2.0 + 0.15 && d > n / 2.0 - 1.15;
            }
            case 1 -> {
                // triangle pointing up: apex at top row j = n-1
                double t = (double) j / (n - 1);                   // 0 bottom .. 1 top
                double hw = m * (1 - t) + 0.35;
                double d = Math.abs(i - m);
                return j == 0 && d <= m + 0.1 || d <= hw && d > hw - 1.05 && j > 0;
            }
            default -> {
                return i == 0 || j == 0 || i == n - 1 || j == n - 1;
            }
        }
    }
}
