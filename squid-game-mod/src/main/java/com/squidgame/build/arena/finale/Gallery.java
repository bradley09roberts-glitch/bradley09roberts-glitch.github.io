package com.squidgame.build.arena.finale;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.finale.Layout.*;

/**
 * The raised grandstand behind the east rim wall: eight stepped tiers (yellow-edged concrete, spruce bench stairs as
 * seats and as the steps between tiers, a stone aisle through the middle that continues over the wall into the yard),
 * railings, a rear terrace with lamp posts, benches, a broadcast booth, plus the audience / exit markers.
 */
final class Gallery {
    private Gallery() {
    }

    static final int TOP_FLOOR = G_FLOOR0 + G_TIERS - 1;                  // floor block y of the top tier (= terrace)
    static final int TERRACE_X0 = G_X0 + G_TIER_DEPTH * G_TIERS;           // 77
    static final int TERRACE_X1 = BX1 - 1;                                 // 89
    static final String RAIL = "minecraft:iron_bars";
    static final String BENCH = "minecraft:spruce_stairs[facing=east,half=bottom,shape=straight]";
    static final String AISLE = "minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight]";

    static void build(BuildContext c) {
        for (int r = 0; r < G_TIERS; r++) {
            int a = G_X0 + G_TIER_DEPTH * r;
            int y = G_FLOOR0 + r;
            c.fill(a, -2, G_Z0, a + 2, y - 1, G_Z1, "minecraft:stone_bricks");
            c.fill(a, y, G_Z0, a + 2, y, G_Z1, "minecraft:polished_andesite");
            c.fill(a, y, G_Z0, a, y, G_Z1, "minecraft:yellow_concrete");           // painted tread edge
            c.fill(a + 2, y, G_Z0, a + 2, y, G_Z1, "minecraft:stone_bricks");
            // seats: spruce bench stairs along the tier, stone steps in the aisle
            for (int z = G_Z0 + 1; z <= G_Z1 - 1; z++) {
                c.set(a + 2, y + 1, z, Math.abs(z) <= 2 ? AISLE : BENCH);
            }
            // stepped end railings
            for (int x = a; x <= a + 2; x++) {
                c.fill(x, y + 1, G_Z0, x, y + 2, G_Z0, RAIL);
                c.fill(x, y + 1, G_Z1, x, y + 2, G_Z1, RAIL);
            }
        }
        // front railing on top of the rim wall
        c.fill(WX1, 4, G_Z0, WX1, 5, G_Z1, RAIL);
        // the aisle crosses the rim wall and descends into the yard (three steps)
        c.fill(WX1 - 1, 1, -2, WX1, 3, 2, "minecraft:stone_bricks");
        c.fill(WX1 - 1, 3, -2, WX1, 3, 2, "minecraft:polished_andesite");
        c.fill(WX1 - 1, 4, -2, WX1, 6, 2, "minecraft:air");
        for (int i = 0; i < 3; i++) {
            c.fill(YX1 - 2 + i, i + 1, -2, YX1 - 2 + i, i + 1, 2, "minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight]");
            c.fill(YX1 - 2 + i, -2, -2, YX1 - 2 + i, i, 2, "minecraft:stone_bricks");
        }
        // rail along the yard side of the steps
        for (int i = 0; i < 3; i++) {
            c.fill(YX1 - 2 + i, 1, -3, YX1 - 2 + i, i + 3, -3, RAIL);
            c.fill(YX1 - 2 + i, 1, 3, YX1 - 2 + i, i + 3, 3, RAIL);
        }

        terrace(c);
        booth(c);
        markers(c);
    }

    /** The broadcast booth at the back of the terrace: dark timber cabin with a long window, a console and a monitor wall. */
    private static void booth(BuildContext c) {
        final int y = TOP_FLOOR;
        final int x0 = 84, x1 = 88, z0 = -6, z1 = 6;
        final String wood = "minecraft:dark_oak_planks";
        c.walls(x0, y + 1, z0, x1, y + 4, z1, wood);
        c.fill(x0, y + 1, z0, x1, y + 1, z1, "minecraft:black_concrete");
        c.clear(x0 + 1, y + 1, z0 + 1, x1 - 1, y + 4, z1 - 1);
        c.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, wood);
        // ceiling and an overhanging roof with a dark edge
        c.fill(x0, y + 5, z0, x1, y + 5, z1, wood);
        c.fill(x0 - 1, y + 6, z0 - 1, x1, y + 6, z1 + 1, "minecraft:black_concrete");
        c.fill(x0 - 1, y + 7, z0 - 1, x1, y + 7, z1 + 1, "minecraft:deepslate_tile_slab[type=bottom]");
        // long window on the court side with timber mullions
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            boolean mullion = (z - z0) % 4 == 0;
            for (int yy = y + 3; yy <= y + 4; yy++) {
                c.set(x0, yy, z, mullion ? wood : "minecraft:light_blue_stained_glass_pane");
            }
        }
        // door at the south end (2 wide, 2 high) and a step lamp
        c.clear(x0 + 1, y + 1, z1, x0 + 2, y + 2, z1);
        c.set(x0 + 1, y + 3, z1 + 1, "minecraft:lantern[hanging=false]");
        // console along the window, chairs, monitor wall on the back
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            c.set(x0 + 1, y + 1, z, "minecraft:dark_oak_slab[type=bottom]");
            if (((z - z0) & 1) == 0) {
                c.set(x0 + 2, y + 1, z, "minecraft:spruce_stairs[facing=east,half=bottom,shape=straight]");
            }
        }
        for (int z = z0 + 2; z <= z1 - 2; z += 2) {
            c.set(x1 - 1, y + 3, z, "squidgame:monitor[facing=west]");
            c.set(x1 - 1, y + 4, z, "squidgame:monitor[facing=west]");
            c.set(x1 - 1, y + 1, z, "minecraft:black_concrete");
            c.set(x1 - 1, y + 2, z, "minecraft:black_concrete");
        }
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            c.set(x0 + 2, y + 5, z, "minecraft:sea_lantern");     // a studio light strip along the whole ceiling
        }
        c.text(x0 - 0.05, y + 5.6, 0.5, "GAME BROADCAST", "#FFD84A", 1.4f, 90f, false);
    }

    private static void terrace(BuildContext c) {
        int y = TOP_FLOOR;
        c.fill(TERRACE_X0, -2, G_Z0, TERRACE_X1, y - 1, G_Z1, "minecraft:stone_bricks");
        c.pattern(TERRACE_X0, y, G_Z0, TERRACE_X1, y, G_Z1,
                (x, yy, z) -> ((x + z) & 1) == 0 ? "minecraft:polished_andesite" : "minecraft:smooth_stone");
        // low rails at both ends of the terrace
        c.fill(TERRACE_X0, y + 1, G_Z0, TERRACE_X1, y + 1, G_Z0, RAIL);
        c.fill(TERRACE_X0, y + 1, G_Z1, TERRACE_X1, y + 1, G_Z1, RAIL);
        // lamp posts
        for (int z = -30; z <= 30; z += 10) {
            if (Math.abs(z) < 8) {
                continue;
            }
            lampPost(c, TERRACE_X0 + 1, y + 1, z);
            lampPost(c, TERRACE_X1 - 1, y + 1, z);
        }
        // benches facing the court
        for (int z = -26; z <= 26; z += 13) {
            if (Math.abs(z) < 8) {
                continue;
            }
            c.fill(TERRACE_X0 + 4, y + 1, z, TERRACE_X0 + 4, y + 1, z + 3, BENCH);
        }
    }

    static void lampPost(BuildContext c, int x, int y, int z) {
        c.fill(x, y, z, x, y + 3, z, "minecraft:stone_brick_wall");
        c.set(x, y + 4, z, "minecraft:lantern[hanging=false]");
    }

    private static void markers(BuildContext c) {
        int slot = 0;
        for (int r = 0; r < G_TIERS; r++) {
            int a = G_X0 + G_TIER_DEPTH * r;
            int y = G_FLOOR0 + r;
            for (int z = G_Z0 + 3, k = 0; z <= G_Z1 - 3; z += 3, k++) {
                if (Math.abs(z) <= 3) {
                    continue;
                }
                double x = a + (((k + r) & 1) == 0 ? 0.5 : 1.5);
                c.marker("final.audience", x, y + 1.0, z + 0.5, 90f, "slot=" + slot++);
            }
        }
    }
}
