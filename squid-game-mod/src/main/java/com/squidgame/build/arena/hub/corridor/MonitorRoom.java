package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;

/**
 * CCTV monitoring room (x[5,21], z[-61,-53]): a wall of animated monitors on the north wall above a black console
 * base, two rows of operator desks with monitors and chairs, server cabinets in the corners, pink lighting.
 */
final class MonitorRoom {
    private MonitorRoom() {
    }

    static void build(BuildContext c) {
        // black console base and the monitor wall (clusters separated by black pillars carrying pink light slits)
        c.fill(6, 0, -61, 20, 1, -61, Pal.BLACK);
        for (int x = 6; x <= 20; x++) {
            boolean sep = x == 9 || x == 13 || x == 17;
            for (int y = 2; y <= 5; y++) {
                if (sep) {
                    c.set(x, y, -61, y == 3 || y == 4 ? Pal.LIGHT_PINK : Pal.BLACK);
                } else {
                    c.set(x, y, -61, Pal.monitor("south"));
                }
            }
        }
        // desks, monitors, chairs: row A at z=-60, row B at z=-57 (operators look north)
        for (int row = 0; row < 2; row++) {
            int dz = row == 0 ? -60 : -57;
            for (int x = 7; x <= 19; x++) {
                c.set(x, 0, dz, (x & 1) == 0 ? Pal.LIGHT_PINK : Pal.BLACK);
            }
            for (int x = 8; x <= 18; x += 2) {
                c.set(x, 1, dz, Pal.monitor("south"));
                c.set(x, 1, dz + 1 - 1, Pal.monitor("south"));
                Props.chairBlack(c, x, 0, dz + 1, "north");
                // keyboard
                c.set(x + 1 > 19 ? x : x + 1, 1, dz, "minecraft:polished_blackstone_pressure_plate");
            }
        }
        // server cabinets in the east corners
        for (int z : new int[]{-61, -54}) {
            rack(c, 21, z);
        }
        rack(c, 5, -61);
        rack(c, 5, -54);
        // ceiling pink strips above the rows
        for (int x = 7; x <= 19; x++) {
            c.set(x, 6, -59, Pal.LIGHT_PINK);
            c.set(x, 6, -56, Pal.LIGHT_PINK);
        }
        Props.camera(c, 12, 5, -53, "north");
        c.text(13.5, 5.3, -52 - 0.04, "MONITORING", "white", 1.0f, 180f, false);
    }

    /** Server rack: black cabinet 1x1x3 with indicator lights. */
    static void rack(BuildContext c, int x, int z) {
        for (int y = 0; y <= 2; y++) {
            c.set(x, y, z, Pal.BLACK);
        }
        c.set(x, 1, z, "minecraft:blast_furnace[facing=south,lit=false]");
    }
}
