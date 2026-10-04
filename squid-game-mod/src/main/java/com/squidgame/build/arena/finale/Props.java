package com.squidgame.build.arena.finale;

import com.squidgame.build.BuildContext;

/**
 * Old schoolyard props, each written once in its own frame (prefab style) and placed with
 * {@code c.at(x, 0, z, rot, ...)}; the origin cell sits on the sand (floor block y = 0, first prop block y = 1).
 * Frames: +Z is "south"; slide / see-saw run along Z, swing sets and monkey bars along X.
 */
final class Props {
    private Props() {
    }

    // palette: faded primary colours on warm wood and old steel
    static final String WOOD_LOG = "minecraft:stripped_spruce_log";
    static final String WOOD = "minecraft:spruce_planks";
    static final String WOOD_SLAB = "minecraft:spruce_slab";
    static final String STEEL = "minecraft:andesite_wall";
    static final String RED = "minecraft:red_terracotta";
    static final String YELLOW = "minecraft:yellow_terracotta";
    static final String BLUE = "minecraft:blue_terracotta";
    static final String GREEN = "minecraft:green_terracotta";

    // ------------------------------------------------------------------ swing set

    /** Swing set with {@code seats} swings; beam along +X from x=0 to x=3*seats+1, swinging along Z. Footprint z in [-3,3]. */
    static void swingSet(BuildContext c, int seats) {
        int w = 3 * seats + 1;
        for (int x : new int[]{0, w}) {
            // A-frame: two legs leaning to the beam
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                c.line(x, 1, sgn * 3, x, 7, 0, STEEL);
            }
            c.fill(x, 4, -1, x, 4, 1, "minecraft:iron_bars");   // cross brace between the legs
            c.set(x, 7, 0, STEEL);
        }
        c.fill(0, 8, 0, w, 8, 0, WOOD_LOG + "[axis=x]");
        c.fill(0, 7, 0, 0, 7, 0, WOOD_LOG + "[axis=x]");
        c.fill(w, 7, 0, w, 7, 0, WOOD_LOG + "[axis=x]");
        for (int i = 0; i < seats; i++) {
            int x = 2 + 3 * i;
            for (int dx = 0; dx <= 1; dx++) {
                c.fill(x + dx, 4, 0, x + dx, 7, 0, "minecraft:chain[axis=y]");
                c.fill(x + dx, 3, 0, x + dx, 3, 0, "minecraft:chain[axis=y]");
            }
            c.set(x, 2, 0, "minecraft:dark_oak_slab[type=top]");
            c.set(x + 1, 2, 0, "minecraft:dark_oak_slab[type=top]");
        }
        // worn ground under the swings
        for (int x = 1; x < w; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Noise.hash01(x, z, 41) < 0.55) {
                    c.set(x, 0, z, "minecraft:coarse_dirt");
                }
            }
        }
    }

    // ------------------------------------------------------------------ slide

    /**
     * Slide: a 5x5 deck at stand height 5.0 on four posts (ladder on the north side, x=0), a roof on top, and a 3 wide
     * chute running south from z=3 to z=10 in half block steps (5.0 down to 1.5) between solid side walls.
     */
    static void slide(BuildContext c, String colour) {
        for (int x : new int[]{-2, 2}) {
            for (int z : new int[]{-2, 2}) {
                c.fill(x, 1, z, x, 4, z, colour);
            }
        }
        c.fill(-2, 1, -2, 2, 4, -2, colour);                       // back wall: carries the ladder
        c.fill(-2, 4, -2, 2, 4, 2, WOOD);                          // deck
        for (int y = 1; y <= 4; y++) {
            c.set(0, y, -3, "minecraft:ladder[facing=north]");
        }
        // rails around the deck (open at the ladder head and at the chute mouth)
        for (int x = -2; x <= 2; x++) {
            if (x != 0) {
                c.fill(x, 5, -2, x, 6, -2, "minecraft:iron_bars");
            }
        }
        for (int z = -2; z <= 2; z++) {
            c.fill(-2, 5, z, -2, 6, z, "minecraft:iron_bars");
            c.fill(2, 5, z, 2, 6, z, "minecraft:iron_bars");
        }
        // roof
        c.fill(-3, 8, -3, 3, 8, 3, colour);
        for (int x : new int[]{-2, 2}) {
            for (int z : new int[]{-2, 2}) {
                c.fill(x, 5, z, x, 7, z, "minecraft:stone_brick_wall");
            }
        }
        // chute with solid side walls
        for (int i = 0; i <= 7; i++) {
            int z = 3 + i;
            double top = 5.0 - 0.5 * i;
            for (int x = -1; x <= 1; x++) {
                if (i % 2 == 0) {
                    c.set(x, (int) top - 1, z, x == 0 ? "minecraft:smooth_stone" : "minecraft:light_gray_concrete");
                } else {
                    c.set(x, (int) Math.floor(top), z, "minecraft:smooth_stone_slab[type=bottom]");
                }
            }
            int wallTop = (int) Math.floor(top);
            c.fill(-2, 1, z, -2, wallTop, z, colour);
            c.fill(2, 1, z, 2, wallTop, z, colour);
        }
        // worn ground at the end of the chute
        for (int x = -2; x <= 2; x++) {
            for (int z = 11; z <= 13; z++) {
                if (Noise.hash01(x, z, 43) < 0.6) {
                    c.set(x, 0, z, "minecraft:coarse_dirt");
                }
            }
        }
    }

    // ------------------------------------------------------------------ see-saw

    /** See-saw along Z (z from -3 to 3), the south end up. The pivot stands at z=0. */
    static void seesaw(BuildContext c) {
        // board top heights: 1.5 2.0 2.5 3.0 3.5 4.0 4.5 from the low north end
        for (int i = 0; i <= 6; i++) {
            int z = -3 + i;
            double top = 1.5 + 0.5 * i;
            if (i % 2 == 0) {
                c.set(0, (int) Math.floor(top), z, "minecraft:" + "spruce_slab[type=bottom]");
            } else {
                c.set(0, (int) top - 1, z, WOOD);
            }
        }
        // pivot: small frame under the middle of the board
        c.set(0, 1, 0, "minecraft:stone_bricks");
        c.set(-1, 1, 0, "minecraft:stone_brick_wall");
        c.set(1, 1, 0, "minecraft:stone_brick_wall");
        // handles at both ends
        c.set(0, 2, -3, "minecraft:iron_bars");
        c.fill(0, 5, 3, 0, 6, 3, "minecraft:iron_bars");
        // buried tyre at the low end (shock absorber)
        c.set(0, 1, -4, "minecraft:black_concrete");
        c.set(0, 0, -4, "minecraft:black_concrete");
    }

    // ------------------------------------------------------------------ climbing

    /** Monkey bars: two end frames and a horizontal ladder along X from x=0 to x=len. */
    static void monkeyBars(BuildContext c, int len, String colour) {
        for (int x : new int[]{0, len}) {
            for (int z : new int[]{-1, 1}) {
                c.fill(x, 1, z, x, 5, z, colour);
            }
            c.fill(x, 5, -1, x, 5, 1, colour);
        }
        for (int z : new int[]{-1, 1}) {
            c.fill(1, 5, z, len - 1, 5, z, "minecraft:chain[axis=x]");
        }
        for (int x = 2; x <= len - 2; x += 2) {
            c.set(x, 5, 0, "minecraft:chain[axis=z]");
        }
        // entry platform and ladder at the start
        c.fill(-2, 3, -1, -1, 3, 1, WOOD);
        c.set(-3, 1, 0, "minecraft:ladder[facing=west]");
        c.set(-3, 2, 0, "minecraft:ladder[facing=west]");
        c.set(-3, 3, 0, "minecraft:ladder[facing=west]");
        c.fill(-2, 1, -1, -2, 2, -1, colour);
        c.fill(-2, 1, 1, -2, 2, 1, colour);
        c.fill(-3, 1, -1, -3, 2, -1, colour);
    }

    /** Cubic jungle gym: 5x5x5, lattice of chain rods on steel nodes, a roof platform. */
    static void jungleGym(BuildContext c, String node) {
        int[] g = {0, 2, 4};
        int[] gy = {1, 3, 5};
        for (int x : g) {
            for (int z : g) {
                for (int y : gy) {
                    c.set(x, y, z, node);
                }
                c.set(x, 2, z, "minecraft:chain[axis=y]");
                c.set(x, 4, z, "minecraft:chain[axis=y]");
            }
        }
        for (int y : gy) {
            for (int z : g) {
                c.set(1, y, z, "minecraft:chain[axis=x]");
                c.set(3, y, z, "minecraft:chain[axis=x]");
            }
            for (int x : g) {
                c.set(x, y, 1, "minecraft:chain[axis=z]");
                c.set(x, y, 3, "minecraft:chain[axis=z]");
            }
        }
        // a few boards make it look used
        c.fill(1, 5, 1, 3, 5, 1, WOOD_SLAB + "[type=bottom]");
        c.fill(0, 3, 1, 0, 3, 3, WOOD_SLAB + "[type=bottom]");
    }

    // ------------------------------------------------------------------ tyres

    /** A row of half-buried tyres standing as arches along X ({@code n} arches, 5 apart), colours cycling. */
    static void tyreArches(BuildContext c, int n) {
        String[] cols = {"black", "red", "blue", "yellow", "black", "green"};
        for (int i = 0; i < n; i++) {
            int x = i * 5;
            String col = "minecraft:" + cols[i % cols.length] + "_concrete";
            c.set(x, 1, 0, col);
            c.set(x + 3, 1, 0, col);
            c.set(x + 1, 2, 0, col);
            c.set(x + 2, 2, 0, col);
            c.set(x, 0, 0, col);
            c.set(x + 3, 0, 0, col);
            c.set(x + 1, 1, 0, "minecraft:air");
            c.set(x + 2, 1, 0, "minecraft:air");
        }
    }

    /** Flat tyres lying in the sand (rings of 3x3), n of them 4 apart along X. */
    static void tyreRing(BuildContext c, int n) {
        String[] cols = {"black", "red", "black", "blue", "black", "yellow"};
        for (int i = 0; i < n; i++) {
            int x = i * 4;
            String col = "minecraft:" + cols[i % cols.length] + "_concrete";
            for (int dx = 0; dx <= 2; dx++) {
                for (int dz = 0; dz <= 2; dz++) {
                    if (dx == 1 && dz == 1) {
                        continue;
                    }
                    c.set(x + dx, 0, dz, col);
                }
            }
        }
    }

    // ------------------------------------------------------------------ furniture

    /** Park bench of {@code n} blocks along X: end arms, spruce seat slabs, trapdoor backrest on the north (back) side. */
    static void bench(BuildContext c, int n) {
        for (int x = 0; x < n; x++) {
            boolean end = x == 0 || x == n - 1;
            if (end) {
                c.set(x, 1, 0, "minecraft:stone_brick_wall");
                c.set(x, 2, 0, "minecraft:stone_brick_slab[type=bottom]");
            } else {
                c.set(x, 1, 0, WOOD_SLAB + "[type=bottom]");
                c.set(x, 2, 0, "minecraft:spruce_trapdoor[facing=south,half=bottom,open=true]");
            }
        }
    }

    /** Wash stand: tiled back wall with taps, a water trough in front (6 long along X, trough at z=0, wall at z=-1). */
    static void fountain(BuildContext c) {
        c.fill(0, 1, -1, 6, 3, -1, "minecraft:stone_bricks");
        c.fill(0, 4, -1, 6, 4, -1, "minecraft:stone_brick_slab[type=bottom]");
        c.fill(0, 1, 1, 6, 1, 1, "minecraft:stone_bricks");        // front rim
        c.fill(0, 1, 0, 0, 1, 0, "minecraft:stone_bricks");        // end rims
        c.fill(6, 1, 0, 6, 1, 0, "minecraft:stone_bricks");
        c.fill(1, 1, 0, 5, 1, 0, "minecraft:water");
        for (int x = 1; x <= 5; x += 2) {
            c.set(x, 3, 0, "minecraft:lightning_rod[facing=south]");
        }
        // drinking pillar at the end
        c.fill(8, 1, 0, 8, 3, 0, "minecraft:quartz_pillar[axis=y]");
        c.set(8, 4, 0, "minecraft:cauldron");
        c.set(8, 5, 0, "minecraft:lightning_rod[facing=up]");
    }

    /** Hopscotch painted on the ground (white concrete): rows of 3x3 outlines sharing their lines, running along +Z. */
    static void hopscotch(BuildContext c) {
        String w = "minecraft:white_concrete";
        boolean[] pair = {false, false, true, false, true, false, true};
        int z0 = 0;
        for (boolean p : pair) {
            if (p) {
                outline(c, -2, z0, 0, z0 + 2, w);
                outline(c, 0, z0, 2, z0 + 2, w);
            } else {
                outline(c, -1, z0, 1, z0 + 2, w);
            }
            z0 += 2;
        }
        // the half circle "home" at the end
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = 0; dz <= 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= 3.25 && d > 2.25) {
                    c.set(dx, 0, z0 + dz, w);
                }
            }
        }
    }

    private static void outline(BuildContext c, int x1, int z1, int x2, int z2, String s) {
        for (int x = x1; x <= x2; x++) {
            c.set(x, 0, z1, s);
            c.set(x, 0, z2, s);
        }
        for (int z = z1; z <= z2; z++) {
            c.set(x1, 0, z, s);
            c.set(x2, 0, z, s);
        }
    }

    /** Classic street lamp: pole, short arm towards +X, hanging lantern. */
    static void lampPost(BuildContext c, int h) {
        c.set(0, 1, 0, "minecraft:stone_bricks");
        c.fill(0, 2, 0, 0, h, 0, "minecraft:stone_brick_wall");
        c.set(0, h + 1, 0, "minecraft:stone_brick_slab[type=bottom]");
        c.set(1, h, 0, "minecraft:dark_oak_fence");
        c.set(1, h - 1, 0, "minecraft:lantern[hanging=true]");
    }

    /** Wooden sandbox (6x6 outside) with a bucket and a spade. */
    static void sandbox(BuildContext c) {
        for (int i = 0; i <= 5; i++) {
            for (int z : new int[]{0, 5}) {
                c.set(i, 1, z, "minecraft:spruce_log[axis=x]");
            }
            for (int x : new int[]{0, 5}) {
                c.set(x, 1, i, "minecraft:spruce_log[axis=z]");
            }
        }
        c.fill(1, 0, 1, 4, 0, 4, "minecraft:smooth_sandstone");
        c.set(2, 1, 2, "minecraft:cauldron");
        c.set(3, 1, 3, "minecraft:flower_pot");
    }

    /** Football goal frame facing +Z: two posts and a crossbar. */
    static void goal(BuildContext c) {
        c.fill(0, 1, 0, 0, 3, 0, "minecraft:white_concrete");
        c.fill(6, 1, 0, 6, 3, 0, "minecraft:white_concrete");
        c.fill(0, 3, 0, 6, 3, 0, "minecraft:white_concrete");
        c.fill(1, 1, 1, 5, 2, 1, "minecraft:white_stained_glass_pane");
        c.fill(1, 2, 0, 5, 2, 0, "minecraft:air");
    }
}
