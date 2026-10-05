package com.squidgame.build.arena.hub.stairs;

import com.squidgame.build.BuildContext;

/**
 * The hall itself: solid shell (walls 3 thick, north wall 6, roof 4), white tiled floor, pastel gradient painted on the
 * four inner faces with cream storey courses every 12 blocks (the heights of the main landings), pilasters with
 * glowing strips, arched niches, a coffered ceiling with big light panels, and the framed entrance in the south wall.
 */
final class Shell {
    private Shell() {
    }

    static final int WEST = -46, EAST = 46, NORTH = -171, SOUTH = -81, ROOF = 73;
    /** perimeter length of the inner faces: 91 + 89 + 91 + 89 */
    static final int PERIM = 360;

    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    static void build(Ctx k) {
        BuildContext c = k.c;
        // foundation, solid block, hollow
        c.fill(-49, -8, -177, 49, -2, -81, "minecraft:stone");
        c.fill(-48, -1, -176, 48, 76, -81, Pal.WHITE);
        c.fill(Ctx.X0, Ctx.Y0, Ctx.Z0, Ctx.X1, Ctx.Y1, Ctx.Z1, Pal.AIR);
        floor(k);
        paintWalls(k);
        courses(k);
        pilasters(k);
        niches(k);
        ceiling(k);
        exterior(k);
        entrance(k);
    }

    // ------------------------------------------------------------------ floor

    private static void floor(Ctx k) {
        BuildContext c = k.c;
        c.fill(Ctx.X0, -1, Ctx.Z0, Ctx.X1, -1, Ctx.Z1, Pal.TILE_WHITE);
        // faint grid of larger white slabs every 8 blocks
        for (int x = Ctx.X0; x <= Ctx.X1; x++) {
            for (int z = Ctx.Z0; z <= Ctx.Z1; z++) {
                if (Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0) {
                    c.set(x, -1, z, Pal.QUARTZ);
                }
            }
        }
        // threshold under the entrance opening
        c.fill(-3, -1, SOUTH, 3, -1, SOUTH, Pal.TILE_WHITE);
        // welcome carpet in front of the grand stair: pink and cream squares with a lilac border and a mint centre line
        for (int x = -9; x <= 9; x++) {
            for (int z = -89; z <= -83; z++) {
                boolean border = x == -9 || x == 9 || z == -89 || z == -83;
                int col = border ? Pal.LILAC : (((x + 9) / 2 + (z + 89) / 2) & 1) == 0 ? Pal.PINK : Pal.CREAM;
                if (Math.abs(x) <= 1 && !border) {
                    col = Pal.MINT;
                }
                c.set(x, -1, z, Pal.block(col));
            }
        }
    }

    // ------------------------------------------------------------------ gradient

    /** Position on the loop of inner faces for a wall cell, clockwise from the north-west corner. */
    static int loop(int face, int a) {
        return switch (face) {
            case 0 -> a + 45;           // north, a = x
            case 1 -> 91 + (a + 170);   // east, a = z
            case 2 -> 180 + (45 - a);   // south, a = x
            default -> 271 + (-82 - a); // west, a = z
        };
    }

    static int wallColor(int p, int y) {
        double t = p * 6.0 / PERIM + y * 1.35 / 72.0 + 0.15;
        int kk = (int) Math.floor(t);
        double f = t - kk;
        double th = (BAYER[(y & 3) * 4 + (p & 3)] + 0.5) / 16.0;
        int base = f > th ? kk + 1 : kk;
        if (y >= 62) {
            // dissolve towards cream under the skylights
            double hash = ((p * 73856093L) ^ (y * 19349663L)) % 1000 / 1000.0;
            if (Math.abs(hash) < (y - 61) / 11.0 * 0.85) {
                return Pal.CREAM;
            }
        }
        return Pal.wheel(base);
    }

    private static void paintWalls(Ctx k) {
        BuildContext c = k.c;
        for (int y = 0; y <= Ctx.Y1; y++) {
            for (int x = Ctx.X0; x <= Ctx.X1; x++) {
                c.set(x, y, NORTH, Pal.block(wallColor(loop(0, x), y)));
                c.set(x, y, SOUTH, Pal.block(wallColor(loop(2, x), y)));
            }
            for (int z = Ctx.Z0; z <= Ctx.Z1; z++) {
                c.set(EAST, y, z, Pal.block(wallColor(loop(1, z), y)));
                c.set(WEST, y, z, Pal.block(wallColor(loop(3, z), y)));
            }
        }
    }

    // ------------------------------------------------------------------ storey courses and skirting

    private static void courses(Ctx k) {
        BuildContext c = k.c;
        for (int lv = 12; lv <= 72; lv += 12) {
            int y = lv - 1;
            if (y > Ctx.Y1) {
                break;
            }
            band(c, y, Pal.block(Pal.CREAM));
            if (lv < 72) {
                // thin accent line under the course
                band(c, y - 1, Pal.block(Pal.PEACH));
            }
        }
        // skirting
        band(c, 0, Pal.block(Pal.CREAM));
        band(c, 1, Pal.block(Pal.CREAM));
        // cornice below the ceiling
        band(c, 71, Pal.block(Pal.CREAM));
        band(c, 72, Pal.block(Pal.CREAM));
    }

    private static void band(BuildContext c, int y, String s) {
        c.fill(Ctx.X0, y, NORTH, Ctx.X1, y, NORTH, s);
        c.fill(Ctx.X0, y, SOUTH, Ctx.X1, y, SOUTH, s);
        c.fill(EAST, y, Ctx.Z0, EAST, y, Ctx.Z1, s);
        c.fill(WEST, y, Ctx.Z0, WEST, y, Ctx.Z1, s);
    }

    // ------------------------------------------------------------------ pilasters

    private static void pilasters(Ctx k) {
        // east / west walls: every 15 blocks along z, north wall between the gates, south wall in a regular rhythm
        for (int z = -165; z <= -90; z += 15) {
            pilaster(k, WEST + 1, z, true);
            pilaster(k, EAST - 1, z, true);
        }
        for (int x = -30; x <= 30; x += 15) {
            pilaster(k, x, NORTH + 1, false);
        }
        for (int x = -40; x <= 40; x += 10) {
            if (Math.abs(x) <= 10) {
                continue; // the entrance has its own frame
            }
            pilaster(k, x, SOUTH - 1, false);
        }
    }

    /** 3 wide, 1 deep column of cream with a vertical dash of light panels; (a, b) is the cell nearest the wall face. */
    private static void pilaster(Ctx k, int a, int b, boolean alongZ) {
        BuildContext c = k.c;
        for (int y = 2; y <= 70; y++) {
            boolean lit = (y % 6) >= 1 && (y % 6) <= 3;
            String mid = lit ? ((y / 6) % 2 == 0 ? Pal.LIGHT_WARM : Pal.LIGHT_WHITE) : Pal.block(Pal.CREAM);
            for (int d = -1; d <= 1; d++) {
                int x = alongZ ? a : b + 0;
                int z = alongZ ? b : a;
                if (alongZ) {
                    z = b + d;
                } else {
                    x = a + d;
                    z = b;
                }
                boolean centre = d == 0;
                c.set(x, y, z, centre ? mid : Pal.block(Pal.CREAM));
            }
        }
    }

    // ------------------------------------------------------------------ arched niches in the thick walls

    /** Round-headed niche 7 wide, 9 high, 2 deep, centred at {@code c} along the wall, bottom row y0. */
    private static void niche(Ctx k, int wall, boolean alongZ, int c0, int y0, int rim, int back) {
        BuildContext c = k.c;
        int hw = 3, straight = 5;
        double r = hw + 0.5;
        for (int y = y0; y <= y0 + 8; y++) {
            for (int a = c0 - hw - 1; a <= c0 + hw + 1; a++) {
                int da = a - c0;
                boolean inside = nicheCell(da, y - y0, hw, straight, r);
                boolean edge = !inside && (nicheCell(da + 1, y - y0, hw, straight, r) || nicheCell(da - 1, y - y0, hw, straight, r)
                        || nicheCell(da, y - y0 + 1, hw, straight, r) || nicheCell(da, y - y0 - 1, hw, straight, r));
                int sgn = wall > 0 ? 1 : -1;
                if (inside) {
                    for (int d = 0; d < 2; d++) {
                        int w = wall + (alongZ ? sgn : sgn) * d * (wall > 0 ? 1 : -1) * (wall > 0 ? 1 : 1);
                        placeNiche(c, alongZ, wall, d, a, y, Pal.AIR);
                    }
                    placeNiche(c, alongZ, wall, 2, a, y, Pal.block(back));
                } else if (edge) {
                    placeNiche(c, alongZ, wall, 0, a, y, Pal.block(rim));
                }
            }
        }
        // a warm panel in the crown and a pale strip down the back
        placeNiche(c, alongZ, wall, 2, c0, y0 + 8, Pal.LIGHT_WARM);
        for (int y = y0 + 1; y <= y0 + 5; y += 2) {
            placeNiche(c, alongZ, wall, 2, c0, y, Pal.LIGHT_WHITE);
        }
    }

    /** depth d into the wall: for wall faces at x = +-46 (alongZ) or z = -171 (north, along x). */
    private static void placeNiche(BuildContext c, boolean alongZ, int wall, int d, int a, int y, String state) {
        if (alongZ) {
            int x = wall > 0 ? wall + d : wall - d;
            c.set(x, y, a, state);
        } else {
            c.set(a, y, wall - d, state);          // north wall: deeper = more negative z
        }
    }

    private static boolean nicheCell(int da, int dy, int hw, int straight, double r) {
        if (dy < 0 || dy > 8) {
            return false;
        }
        if (dy <= straight) {
            return Math.abs(da) <= hw;
        }
        double ddy = dy - straight;
        return da * da + ddy * ddy <= r * r - 0.25;
    }

    private static void niches(Ctx k) {
        // east / west walls: in the bays between the pilasters (every 15 along z, 3 wide), one niche per storey
        for (int bay = 0; bay < 5; bay++) {
            int zc = -157 + bay * 15;                   // bay centres between the pilasters at -165, -150, ..., -90
            for (int s = 0; s < 6; s++) {
                int y0 = 12 * s + 2;
                niche(k, WEST, true, zc, y0, Pal.CREAM, Pal.wheel(bay + s + 2));
                niche(k, EAST, true, zc, y0, Pal.CREAM, Pal.wheel(bay + s + 5));
            }
        }
        // north wall: on the gate axes, in the four storeys of the galleries under the terrace
        int[] gx = {-37, -22, -7, 7, 22, 37};
        for (int g = 0; g < 6; g++) {
            for (int s = 0; s < 4; s++) {
                niche(k, NORTH, false, gx[g], 12 * s + 1, Pal.CREAM, Pal.wheel(g + s + 1));
            }
        }
    }

    // ------------------------------------------------------------------ ceiling

    private static void ceiling(Ctx k) {
        BuildContext c = k.c;
        c.fill(Ctx.X0, ROOF, Ctx.Z0, Ctx.X1, ROOF, Ctx.Z1, Pal.block(Pal.CREAM));
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                int cx = -36 + 12 * i, cz = -164 + 12 * j;
                boolean warm = ((i + j) & 1) == 0;
                int acc = Pal.wheel(i + j);
                // frame ring (y = ROOF) and panel
                c.fill(cx - 4, ROOF, cz - 4, cx + 5, ROOF, cz + 5, Pal.block(acc));
                c.fill(cx - 3, ROOF, cz - 3, cx + 4, ROOF, cz + 4, warm ? Pal.LIGHT_WARM : Pal.LIGHT_WHITE);
                // roof void above the panels is solid already
            }
        }
        // coffer beams
        for (int i = 0; i < 6; i++) {
            int bx = -36 + 12 * i + 6;
            c.fill(bx, Ctx.Y1, Ctx.Z0, bx + 1, Ctx.Y1, Ctx.Z1, Pal.block(Pal.CREAM));
        }
        for (int j = 0; j < 6; j++) {
            int bz = -164 + 12 * j + 6;
            c.fill(Ctx.X0, Ctx.Y1, bz, Ctx.X1, Ctx.Y1, bz + 1, Pal.block(Pal.CREAM));
        }
    }

    // ------------------------------------------------------------------ outside

    private static void exterior(Ctx k) {
        BuildContext c = k.c;
        // cream skin with pastel stripes, readable from the control room windows and from outside
        for (int y = 0; y <= 76; y++) {
            int col = (y / 6) % 2 == 0 ? Pal.CREAM : Pal.wheel(y / 12);
            String s = Pal.block(col);
            c.fill(-48, y, -176, 48, y, -176, s);
            c.fill(-48, y, -176, -48, y, -81, s);
            c.fill(48, y, -176, 48, y, -81, s);
        }
        c.fill(-48, 76, -176, 48, 76, -81, Pal.block(Pal.CREAM));
    }

    // ------------------------------------------------------------------ entrance

    private static void entrance(Ctx k) {
        BuildContext c = k.c;
        // opening (corridor part ends here)
        c.fill(-3, 0, SOUTH, 3, 5, SOUTH, Pal.AIR);
        c.fill(-3, -1, SOUTH, 3, -1, SOUTH, Pal.TILE_WHITE);
        // frame inside the hall: pilasters and lintel
        for (int side = -1; side <= 1; side += 2) {
            int xa = side * 4, xb = side * 5;
            c.fill(Math.min(xa, xb), 0, SOUTH + -1, Math.max(xa, xb), 8, SOUTH - 2, Pal.block(Pal.CREAM));
            c.fill(Math.min(xa, xb), 1, SOUTH - 1, Math.max(xa, xb), 1, SOUTH - 2, Pal.block(Pal.PINK));
            for (int y = 2; y <= 7; y += 2) {
                c.set(side * 4, y, SOUTH - 2, Pal.LIGHT_WARM);
            }
        }
        c.fill(-5, 6, SOUTH - 1, 5, 8, SOUTH - 2, Pal.block(Pal.CREAM));
        c.fill(-3, 6, SOUTH - 1, 3, 6, SOUTH - 1, Pal.LIGHT_WARM);
        c.fill(-5, 8, SOUTH - 1, 5, 8, SOUTH - 2, Pal.block(Pal.PINK));
        // frame on the wall plane around the opening
        c.fill(-4, 0, SOUTH, -4, 7, SOUTH, Pal.block(Pal.PINK));
        c.fill(4, 0, SOUTH, 4, 7, SOUTH, Pal.block(Pal.PINK));
        c.fill(-4, 6, SOUTH, 4, 7, SOUTH, Pal.block(Pal.PINK));
        // the three symbols over the door
        c.fill(-11, 10, SOUTH, -9, 12, SOUTH, "squidgame:symbol_circle");
        c.fill(-1, 10, SOUTH, 1, 12, SOUTH, "squidgame:symbol_triangle");
        c.fill(9, 10, SOUTH, 11, 12, SOUTH, "squidgame:symbol_square");
    }
}
