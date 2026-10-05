package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;

/**
 * Infirmary (x[12,29], z[-43,-36]): white beds with pink curtains along the north and south walls, instrument
 * cabinets, a big red cross on the east wall, pendant lamps.
 */
final class Infirmary {
    private Infirmary() {
    }

    static void build(BuildContext c) {
        // north row: heads against the north wall (z=-43), foot at z=-42; the doorway x 17..21 stays free
        for (int x : new int[]{23, 25, 27, 29}) {
            c.set(x, 0, -43, Pal.bed("white", "north", true));
            c.set(x, 0, -42, Pal.bed("white", "north", false));
        }
        for (int x : new int[]{24, 26, 28}) {
            c.set(x, 0, -42, "minecraft:pink_stained_glass_pane");
            c.set(x, 1, -42, "minecraft:pink_stained_glass_pane");
            c.set(x, 0, -43, "minecraft:pink_stained_glass_pane");
            c.set(x, 1, -43, "minecraft:pink_stained_glass_pane");
        }
        // south row: heads against the south wall (z=-36), foot at z=-37
        for (int x : new int[]{14, 16, 18, 20, 22, 24, 26, 28}) {
            c.set(x, 0, -36, Pal.bed("white", "south", true));
            c.set(x, 0, -37, Pal.bed("white", "south", false));
        }
        for (int x : new int[]{15, 17, 19, 21, 23, 25, 27}) {
            c.set(x, 0, -36, "minecraft:pink_stained_glass_pane");
            c.set(x, 1, -36, "minecraft:pink_stained_glass_pane");
            c.set(x, 0, -37, "minecraft:pink_stained_glass_pane");
            c.set(x, 1, -37, "minecraft:pink_stained_glass_pane");
        }
        // cabinets along the east wall
        for (int z = -41; z <= -38; z++) {
            c.set(29, 0, z, "minecraft:smooth_quartz");
            c.set(29, 1, z, "minecraft:smooth_quartz");
            c.set(29, 2, z, Pal.trapdoor("minecraft:iron_trapdoor", "west", true, false));
        }
        // red cross above the cabinets on the east wall plane (x=30)
        for (int z = -42; z <= -37; z++) {
            for (int y = 3; y <= 5; y++) {
                boolean cross = (z == -40 || z == -39) || y == 4;
                c.set(30, y, z, cross ? "minecraft:red_concrete" : Pal.WHITE);
            }
        }
        // pendant lamps
        for (int[] p : new int[][]{{16, -40}, {20, -40}, {24, -40}, {28, -40}}) {
            c.set(p[0], 5, p[1], Pal.chain("y"));
            c.set(p[0], 4, p[1], Pal.lantern(true));
        }
        for (int x = 14; x <= 28; x += 2) {
            c.set(x, 6, -40, Pal.LIGHT_WHITE);
        }
        c.text(20.5, 4.4, -44 + 0.0 - 0.04 + 1.08, "INFIRMARY", "white", 1.2f, 0f, false);
        Props.camera(c, 12, 5, -36, "east");
    }
}
