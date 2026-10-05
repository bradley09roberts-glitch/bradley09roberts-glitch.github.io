package com.squidgame.build.arena.hub.control;

import com.squidgame.build.BuildContext;

/**
 * Mezzanine on the north side: a deck at standing level 20 (x[52,75], z[-18,-12]) on pylons above the server racks,
 * reached by a 3-wide stair flight from the dais side (x 76..83), a glass-fronted VIP office with sofa, bar and desk,
 * and a glass balcony railing along the front edge.
 */
final class Mezzanine {
    private Mezzanine() {
    }

    static void build(BuildContext c) {
        // deck
        c.fill(52, 19, -18, 75, 19, -12, Pal.PBS_BRICKS);
        for (int x = 52; x <= 75; x++) {
            // glowing front edge strip
            c.set(x, 19, -12, (x & 1) == 0 ? Pal.LIGHT_PINK : Pal.PBS);
        }
        // pylons under the front edge
        for (int x : new int[]{52, 58, 64, 70, 75}) {
            c.fill(x, 12, -12, x, 18, -12, Pal.PBS);
            c.set(x, 14, -12, Pal.LIGHT_PINK);
            c.set(x, 16, -12, Pal.LIGHT_PINK);
        }
        // stairs: eight steps rising west from x=83 (y=12) to x=76 (y=19); solid below, rail on the south side
        for (int i = 0; i < 8; i++) {
            int x = 83 - i, y = 12 + i;
            for (int z = -18; z <= -16; z++) {
                c.fill(x, 12, z, x, y - 1, z, Pal.PBS_BRICKS);
                c.set(x, y, z, Pal.stairs("minecraft:polished_blackstone_brick_stairs", "west", false));
            }
            c.set(x, y + 1, -15, "minecraft:iron_bars");
            c.set(x, y + 0, -15, Pal.PBS);
        }
        // landing at the top: stairs end at x=76; deck from x=75
        // balcony railing: glass panes on a low black wall with a glowing cap
        for (int x = 52; x <= 75; x++) {
            c.set(x, 20, -12, "minecraft:black_stained_glass_pane");
            c.set(x, 21, -12, "minecraft:black_stained_glass_pane");
            c.set(x, 22, -12, x % 2 == 0 ? Pal.PBS : Pal.LIGHT_PINK);
        }
        // the stair entrance gap at the east end: railing stops at x=75 (stairs arrive at x=76)
        office(c);
    }

    /** VIP office on the deck: x[58,72], z[-17,-15], front glass wall at z=-14, door gap x 64..66. */
    private static void office(BuildContext c) {
        // floor tile and walls
        c.fill(57, 19, -18, 73, 19, -14, Pal.TILE_BLACK);
        for (int y = 20; y <= 24; y++) {
            for (int z = -18; z <= -14; z++) {
                c.set(57, y, z, y == 24 ? Pal.LIGHT_PINK : Pal.PBS);
                c.set(73, y, z, y == 24 ? Pal.LIGHT_PINK : Pal.PBS);
            }
            for (int x = 57; x <= 73; x++) {
                boolean door = x >= 64 && x <= 66 && y <= 23;
                if (door) {
                    continue;
                }
                boolean glass = y >= 20 && y <= 23 && x > 57 && x < 73;
                c.set(x, y, -14, glass ? Pal.GLASS_BLACK : (y == 24 ? Pal.LIGHT_PINK : Pal.PBS));
            }
        }
        for (int x = 57; x <= 73; x++) {
            for (int z = -18; z <= -14; z++) {
                c.set(x, 25, z, Pal.DEEPSLATE_TILES);
            }
        }
        // mullions on the glass front
        for (int x : new int[]{60, 63, 67, 70}) {
            c.fill(x, 20, -14, x, 23, -14, Pal.PBS);
        }
        // interior: lounge sofa along the back wall, table, bar, desk with monitors
        for (int x = 59; x <= 63; x++) {
            c.set(x, 20, -17, Pal.stairs("squidgame:pastel_pink_stairs", "north", false));
            c.set(x, 20, -16, "minecraft:air");
        }
        c.set(61, 20, -16, Pal.PBS);
        c.set(61, 21, -16, "minecraft:polished_blackstone_pressure_plate");
        for (int x = 68; x <= 72; x++) {
            c.set(x, 20, -17, Pal.PBS);
            c.set(x, 20, -16, Pal.PBS);
            c.set(x, 21, -17, (x & 1) == 0 ? Pal.monitor("south") : "minecraft:air");
        }
        c.set(70, 20, -15, Pal.stairs("minecraft:polished_blackstone_stairs", "north", false));
        // bar shelf with lights on the west wall
        for (int z = -18; z <= -15; z++) {
            c.set(58, 20, z, Pal.PBS);
            c.set(58, 21, z, Pal.lantern(false));
        }
        // lighting
        for (int x = 60; x <= 71; x += 3) {
            c.set(x, 24, -16, Pal.LIGHT_WARM);
        }
        for (int[] p : new int[][]{{60, -16}, {66, -16}, {71, -16}}) {
            c.fill(p[0], 22, p[1], p[0], 23, p[1], Pal.chain("y"));
            c.set(p[0], 21, p[1], Pal.lantern(true));
        }
        c.text(65.5, 24.6, -13.9, "VIP", "#FFD84A", 2.0f, 0f, false);
    }
}
