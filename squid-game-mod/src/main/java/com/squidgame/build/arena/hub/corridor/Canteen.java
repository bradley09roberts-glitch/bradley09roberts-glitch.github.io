package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;

/**
 * Guards' canteen (x[-29,-12], z[-43,-36]): a serving counter and kitchen at the west end, long tables with benches
 * along the north and south walls (aisle z -41..-38 stays free), vending machines, menu board.
 */
final class Canteen {
    private Canteen() {
    }

    static void build(BuildContext c) {
        // serving counter along x=-26 with kitchen behind it
        for (int z = -42; z <= -37; z++) {
            c.set(-26, 0, z, "minecraft:smooth_quartz");
            c.set(-26, 1, z, "minecraft:smooth_quartz_slab[type=bottom]");
            c.set(-27, 0, z, Pal.BLACK);
            c.set(-27, 1, z, (z & 1) == 0 ? "minecraft:smoker[facing=east,lit=false]" : Pal.BLACK);
        }
        for (int z = -42; z <= -37; z += 2) {
            c.set(-26, 2, z, "minecraft:lantern[hanging=false]");
        }
        c.set(-28, 0, -41, "minecraft:cauldron");
        c.set(-28, 0, -38, "minecraft:cauldron");
        for (int z = -43; z <= -36; z++) {
            c.set(-29, 0, z, Pal.GRAY);
            c.set(-29, 1, z, Pal.GRAY);
        }
        // tables along the north wall (door gap x -21..-17) and the south wall
        for (int x = -25; x <= -13; x++) {
            boolean doorGap = x >= -21 && x <= -17;
            if (!doorGap) {
                c.set(x, 0, -43, "minecraft:smooth_quartz");
                c.set(x, 1, -43, "minecraft:smooth_quartz_slab[type=bottom]");
                Props.chair(c, x, 0, -42, "north", "squidgame:pastel_pink_stairs");
            }
            c.set(x, 0, -36, "minecraft:smooth_quartz");
            c.set(x, 1, -36, "minecraft:smooth_quartz_slab[type=bottom]");
            Props.chair(c, x, 0, -37, "south", "squidgame:pastel_pink_stairs");
        }
        // vending machines along the east wall, flanking the door
        for (int z : new int[]{-43, -42, -37, -36}) {
            c.set(-12, 0, z, "minecraft:light_blue_concrete");
            c.set(-12, 1, z, "minecraft:light_blue_stained_glass");
            c.set(-12, 2, z, "minecraft:light_blue_concrete");
        }
        // warm floor lights along both aisle edges
        for (int x = -24; x <= -14; x += 3) {
            c.set(x, -1, -41, Pal.LIGHT_WARM);
            c.set(x, -1, -38, Pal.LIGHT_WARM);
        }
        // ceiling: panels above the tables, pendant lamps over the aisle
        for (int x = -24; x <= -14; x += 2) {
            c.set(x, 6, -42, Pal.LIGHT_WHITE);
            c.set(x, 6, -37, Pal.LIGHT_WHITE);
        }
        for (int x : new int[]{-23, -19, -15}) {
            c.set(x, 5, -39, Pal.chain("y"));
            c.set(x, 4, -39, Pal.lantern(true));
        }
        c.text(-28.4, 3.6, -39.5, "MENU: BREAD AND MILK", "white", 1.0f, -90f, false);
        Props.camera(c, -12, 5, -43, "west");
    }
}
