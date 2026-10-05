package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;

/**
 * Guards' barracks (x[-21,-5], z[-61,-53]): double bunks with their heads against the north and south walls, lockers
 * along the side walls, a pink runner down the 3-wide central aisle between the two doors, warm pendant lamps.
 */
final class Barracks {
    private Barracks() {
    }

    static void build(BuildContext c) {
        // bunks along the north wall (heads at z=-61, posts at z=-59)
        for (int x : new int[]{-19, -17, -15, -13, -11, -9, -7}) {
            final int xx = x;
            c.at(x, 0, -61, 0, () -> Props.bunk(c, (xx & 2) == 0 ? "pink" : "white", (xx & 2) == 0 ? "white" : "pink"));
        }
        // along the south wall (heads at z=-53, posts at z=-55), leaving the doorway x -15..-11 free
        for (int x : new int[]{-19, -17, -9, -7}) {
            final int xx = x;
            c.at(x, 0, -53, 2, () -> Props.bunk(c, (xx & 2) == 0 ? "white" : "pink", (xx & 2) == 0 ? "pink" : "white"));
        }
        // lockers on both side walls (north and south ends)
        for (int z : new int[]{-61, -60, -54, -53}) {
            final int zz = z;
            c.at(-21, 0, z, 3, () -> Props.locker(c, 1 + (zz & 1)));
            c.at(-5, 0, z, 1, () -> Props.locker(c, 1 + (zz & 1)));
        }
        // runner: pink centre, white borders
        for (int x = -20; x <= -6; x++) {
            c.set(x, 0, -57, "minecraft:pink_carpet");
            c.set(x, 0, -58, "minecraft:white_carpet");
            c.set(x, 0, -56, "minecraft:white_carpet");
        }
        // ceiling: warm light strip along the aisle, pendant lamps
        for (int x = -20; x <= -6; x += 2) {
            c.set(x, 6, -57, Pal.LIGHT_WARM);
        }
        for (int x : new int[]{-18, -14, -10, -6}) {
            c.set(x, 5, -57, Pal.chain("y"));
            c.set(x, 4, -57, Pal.lantern(true));
        }
        // small table with a plant at the aisle ends
        c.set(-20, 0, -59, "minecraft:pink_concrete");
        c.set(-20, 1, -59, "minecraft:potted_fern");
        // wall decoration: framed symbols above the doors
        c.set(-13, 4, -52, Pal.SYM_CIRCLE);
        c.text(-12.5, 3.3, -52 - 0.04 + 0.0, "BARRACKS", "white", 1.0f, 180f, false);
        Props.camera(c, -5, 5, -61, "south");
    }
}
