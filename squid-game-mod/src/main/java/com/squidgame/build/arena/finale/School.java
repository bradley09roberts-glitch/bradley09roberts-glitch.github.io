package com.squidgame.build.arena.finale;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.finale.Layout.*;

/**
 * The old school building along the north end of the yard: two storeys of stucco with banded cornices, recessed
 * windows (a few lit), pilasters, a central portico with columns, doors and steps, and a pediment with a clock.
 * The forecourt in front holds the assembly podium and two flag poles. The building is solid (a facade with depth).
 */
final class School {
    private School() {
    }

    static final int F = SCH_FACE;                 // facade plane z = -52
    static final String BAND = "minecraft:light_gray_concrete";
    static final String FRAME = "minecraft:light_gray_concrete";

    static void build(BuildContext c) {
        body(c);
        facade(c);
        roof(c);
        entrance(c);
        podium(c);
        flagPole(c, -16, F + 6, true);
        flagPole(c, 16, F + 6, false);
        forecourt(c);
    }

    // ------------------------------------------------------------------ main block

    private static void body(BuildContext c) {
        c.fill(SCH_X0, -2, SCH_Z0, SCH_X1, 0, F, "minecraft:stone_bricks");
        c.fill(SCH_X0, 1, SCH_Z0, SCH_X1, 12, F, "minecraft:white_concrete");
        // the two ends and the back are plain whitewash with a base course
        c.fill(SCH_X0, 1, SCH_Z0, SCH_X1, 1, F, "minecraft:stone_bricks");
    }

    /** x of the window centre (any of the 3 wide windows) containing |x| / x, or Integer.MIN_VALUE. */
    static int windowCentre(int x) {
        int ax = Math.abs(x);
        if (ax <= 9) {
            for (int cx : new int[]{-5, 0, 5}) {
                if (Math.abs(x - cx) <= 1) {
                    return cx;
                }
            }
            return Integer.MIN_VALUE;
        }
        for (int k = 0; k < 5; k++) {
            int cx = 14 + 5 * k;
            if (Math.abs(ax - cx) <= 1) {
                return x < 0 ? -cx : cx;
            }
        }
        return Integer.MIN_VALUE;
    }

    static boolean isPilaster(int x) {
        int ax = Math.abs(x);
        if (ax <= 9) {
            return ax == 3 || ax == 2 || ax == 8 || ax == 9; // between the three upper centre windows and at the corners of the portico
        }
        return ax == 11 || ax == 12 || (ax >= 16 && ax <= 37 && ((ax - 16) % 5 <= 1));
    }

    private static boolean lit(int cx, int floor) {
        return Noise.hash01(cx * 31 + 7, floor * 17 + 3, 515) < 0.22;
    }

    private static String wall(int x, int y, int z) {
        double h = Noise.hash01(x * 5 + y, z + y * 13, 71);
        return h < 0.55 ? "minecraft:white_concrete" : (h < 0.82 ? "minecraft:calcite" : (h < 0.92 ? "minecraft:diorite" : "minecraft:white_terracotta"));
    }

    private static void facade(BuildContext c) {
        for (int x = SCH_X0; x <= SCH_X1; x++) {
            for (int y = 2; y <= 12; y++) {
                c.set(x, y, F, wall(x, y, F));
            }
            // string courses (proud) at the floor line and under the roof
            c.set(x, 6, F + 1, BAND);
            c.set(x, 12, F + 1, BAND);
            c.set(x, 6, F, BAND);
            c.set(x, 12, F, BAND);
            int wc = windowCentre(x);
            for (int storey = 0; storey < 2; storey++) {
                int y0 = storey == 0 ? 3 : 8;
                int sill = y0 - 1;
                if (wc != Integer.MIN_VALUE && !(Math.abs(x) <= 9 && storey == 0)) {
                    boolean lit = lit(wc, storey);
                    String glass = lit ? "minecraft:yellow_stained_glass" : "minecraft:light_blue_stained_glass";
                    String back = lit ? "minecraft:glowstone" : "minecraft:black_concrete";
                    for (int y = y0; y < y0 + 3; y++) {
                        if (x == wc) {
                            c.set(x, y, F, FRAME);               // mullion
                            c.set(x, y, F - 1, wall(x, y, F));
                        } else {
                            c.set(x, y, F, glass);
                            c.set(x, y, F - 1, back);
                        }
                    }
                    c.set(x, sill, F + 1, "minecraft:stone_brick_slab[type=top]");
                } else if (isPilaster(x)) {
                    for (int y = 2; y <= 11; y++) {
                        if (y != 6 && !(Math.abs(x) <= 9 && y < 7)) {
                            c.set(x, y, F + 1, "minecraft:calcite");
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ portico, doors, pediment

    private static void entrance(BuildContext c) {
        final int zFront = F + 3;
        // porch floor and steps
        c.fill(-9, -1, F + 1, 9, 0, F + 5, "minecraft:stone_bricks");
        c.fill(-9, 1, F + 1, 9, 1, F + 4, "minecraft:polished_andesite");
        c.fill(-9, 1, F + 5, 9, 1, F + 5, "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight]");
        // doors: frame, two leaves, transom
        c.fill(-3, 2, F, 3, 6, F, FRAME);
        for (int y = 2; y <= 4; y++) {
            for (int x = -2; x <= 2; x++) {
                c.set(x, y, F, x == 0 ? "minecraft:polished_blackstone" : "minecraft:dark_oak_planks");
            }
        }
        c.set(-2, 4, F, "minecraft:iron_bars");
        c.set(-1, 4, F, "minecraft:iron_bars");
        c.set(1, 4, F, "minecraft:iron_bars");
        c.set(2, 4, F, "minecraft:iron_bars");
        for (int x = -2; x <= 2; x++) {
            c.set(x, 5, F, "minecraft:light_blue_stained_glass");
        }
        // columns of the portico
        for (int x : new int[]{-8, -4, 4, 8}) {
            c.fill(x, 2, zFront, x, 5, zFront, "minecraft:quartz_pillar[axis=y]");
            c.set(x, 1, zFront, "minecraft:smooth_quartz");
        }
        // entablature / balcony floor and the sign band
        c.fill(-9, 6, F + 1, 9, 6, zFront, "minecraft:light_gray_concrete");
        c.fill(-9, 7, zFront, 9, 7, zFront, "minecraft:black_concrete");
        c.fill(-9, 7, zFront, -9, 7, zFront, "minecraft:polished_blackstone");
        c.fill(9, 7, zFront, 9, 7, zFront, "minecraft:polished_blackstone");
        c.text(0.5, 7.15, zFront + 0.55, "ELEMENTARY SCHOOL", "#F2E6B0", 1.6f, 0f, false);
        // lanterns under the portico roof
        c.set(-6, 5, zFront, "minecraft:lantern[hanging=true]");
        c.set(6, 5, zFront, "minecraft:lantern[hanging=true]");
        c.set(-2, 5, zFront, "minecraft:lantern[hanging=true]");
        c.set(2, 5, zFront, "minecraft:lantern[hanging=true]");
        // pediment with the clock
        int[] half = {9, 7, 5, 3, 1};
        for (int i = 0; i < half.length; i++) {
            int y = 13 + i;
            for (int x = -half[i]; x <= half[i]; x++) {
                boolean edge = Math.abs(x) >= half[i] - 0;
                c.set(x, y, F, edge ? FRAME : "minecraft:white_concrete");
                c.set(x, y, F + 1, edge ? FRAME : "minecraft:light_gray_concrete");
            }
        }
        // clock face: white disc, black ring and hands
        int cy = 14;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d <= 2.6 && cy + dy >= 13) {
                    c.set(dx, cy + dy, F + 2, d > 1.9 ? "minecraft:black_concrete" : "minecraft:white_concrete");
                }
            }
        }
        c.set(0, cy, F + 2, "minecraft:black_concrete");
        c.set(0, cy + 1, F + 2, "minecraft:black_concrete");
        c.set(1, cy, F + 2, "minecraft:black_concrete");
    }

    // ------------------------------------------------------------------ roof

    private static void roof(BuildContext c) {
        // roof surface and parapet
        c.pattern(SCH_X0, 12, SCH_Z0, SCH_X1, 12, F - 1, (x, y, z) -> {
            double h = Noise.hash01(x, z, 99);
            return h < 0.7 ? "minecraft:smooth_stone" : (h < 0.9 ? "minecraft:stone" : "minecraft:gravel");
        });
        c.fill(SCH_X0, 13, F, SCH_X1, 13, F, "minecraft:white_concrete");
        c.fill(SCH_X0, 13, SCH_Z0, SCH_X1, 13, SCH_Z0, "minecraft:white_concrete");
        c.fill(SCH_X0, 13, SCH_Z0, SCH_X0, 13, F, "minecraft:white_concrete");
        c.fill(SCH_X1, 13, SCH_Z0, SCH_X1, 13, F, "minecraft:white_concrete");
        c.fill(SCH_X0, 14, F, SCH_X1, 14, F, "minecraft:stone_brick_slab[type=bottom]");
        // rooftop furniture: ventilation boxes, a water tank
        for (int x : new int[]{-33, -20, 22, 34}) {
            c.fill(x, 13, -58, x + 2, 14, -56, "minecraft:iron_block");
            c.fill(x, 15, -58, x + 2, 15, -56, "minecraft:smooth_stone_slab[type=bottom]");
        }
        c.fill(-3, 13, -58, 3, 13, -55, "minecraft:stone_bricks");
        c.fill(-2, 14, -57, 2, 14, -56, "minecraft:stone_bricks");
        // light for the roof guards
        c.set(-38, 13, -55, "minecraft:lantern[hanging=false]");
        c.set(38, 13, -55, "minecraft:lantern[hanging=false]");
        c.set(-12, 13, -55, "minecraft:lantern[hanging=false]");
        c.set(12, 13, -55, "minecraft:lantern[hanging=false]");
    }

    // ------------------------------------------------------------------ forecourt

    /** The assembly podium: a low stone dais with steps, a lectern and the winner's marker. */
    private static void podium(BuildContext c) {
        int z0 = -47, z1 = -43;
        c.fill(-6, -1, z0, 6, 0, z1 + 5, "minecraft:stone_bricks");
        c.fill(-6, 1, z0, 6, 1, z1, "minecraft:stone_bricks");
        c.fill(-6, 2, z0, 6, 2, z1, "minecraft:smooth_stone");
        c.fill(-6, 2, z0, 6, 2, z0, "minecraft:polished_blackstone");
        c.fill(-6, 2, z0, -6, 2, z1, "minecraft:polished_blackstone");
        c.fill(6, 2, z0, 6, 2, z1, "minecraft:polished_blackstone");
        // steps on the south side: 2.0 then 3.0
        for (int x = -3; x <= 3; x++) {
            c.set(x, 1, z1 + 1, "minecraft:stone_bricks");
            c.set(x, 2, z1 + 1, "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight]");
            c.set(x, 1, z1 + 2, "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight]");
        }
        // lectern and a pair of lanterns on posts
        c.set(0, 3, z0 + 1, "minecraft:lectern[facing=south]");
        c.fill(-5, 3, z0 + 1, -5, 5, z0 + 1, "minecraft:stone_brick_wall");
        c.set(-5, 6, z0 + 1, "minecraft:lantern[hanging=false]");
        c.fill(5, 3, z0 + 1, 5, 5, z0 + 1, "minecraft:stone_brick_wall");
        c.set(5, 6, z0 + 1, "minecraft:lantern[hanging=false]");
        c.marker("final.podium", 0.5, 3.0, z0 + 3.5, 0f);
    }

    private static void flagPole(BuildContext c, int x, int z, boolean national) {
        c.fill(x - 1, -1, z - 1, x + 1, 0, z + 1, "minecraft:stone_bricks");
        c.fill(x - 1, 1, z - 1, x + 1, 1, z + 1, "minecraft:stone_bricks");
        c.set(x, 2, z, "minecraft:stone_brick_wall");
        c.fill(x, 3, z, x, 17, z, "minecraft:iron_bars");
        c.set(x, 18, z, "minecraft:gold_block");
        // the flag flies to the east, face towards the south
        int fy = 13, fw = 6, fh = 4;
        if (national) {
            for (int dx = 1; dx <= fw; dx++) {
                for (int dy = 0; dy < fh; dy++) {
                    c.set(x + dx, fy + dy, z, "minecraft:white_wool");
                }
            }
            c.set(x + 3, fy + 1, z, "minecraft:blue_wool");
            c.set(x + 4, fy + 1, z, "minecraft:blue_wool");
            c.set(x + 3, fy + 2, z, "minecraft:red_wool");
            c.set(x + 4, fy + 2, z, "minecraft:red_wool");
            c.set(x + 1, fy, z, "minecraft:black_wool");
            c.set(x + fw, fy, z, "minecraft:black_wool");
            c.set(x + 1, fy + fh - 1, z, "minecraft:black_wool");
            c.set(x + fw, fy + fh - 1, z, "minecraft:black_wool");
        } else {
            for (int dx = 1; dx <= fw; dx++) {
                for (int dy = 0; dy < fh; dy++) {
                    boolean stripe = dy == 1 || dy == 2;
                    c.set(x + dx, fy + dy, z, stripe ? "minecraft:white_wool" : "minecraft:blue_wool");
                }
            }
        }
    }

    private static void forecourt(BuildContext c) {
        // low planters with bushes along the school front either side of the portico
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            for (int x = 12; x <= 38; x += 1) {
                if ((x - 12) % 5 >= 3) {
                    continue;
                }
                int xx = sgn * x;
                c.set(xx, 1, F + 2, "minecraft:stone_bricks");
                double h = Noise.hash01(xx, 5, 61);
                c.set(xx, 2, F + 2, h < 0.6 ? "minecraft:azalea_leaves[persistent=true]" : "minecraft:flowering_azalea_leaves[persistent=true]");
            }
        }
    }
}
