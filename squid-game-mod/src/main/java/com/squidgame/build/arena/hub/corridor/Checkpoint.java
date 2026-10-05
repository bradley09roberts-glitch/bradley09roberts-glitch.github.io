package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;

/**
 * The guard checkpoint hall right behind the dormitory exit: black-and-white checkerboard floor with a pink lane and
 * runway lights, two guard booths (counters, monitors, chairs) flanking the lane, a metal-detector arch, symbol murals
 * beside the north gate, a coffered ceiling with pendant lamps and corner cameras.
 */
final class Checkpoint {
    private Checkpoint() {
    }

    static void build(BuildContext c) {
        floorLights(c);
        northWall(c);
        southWall(c);
        sideWalls(c);
        booth(c, -1);
        booth(c, 1);
        arch(c, -37);
        ceiling(c);
        cameras(c);
        c.text(0.5, 6.4, -39.5, "GUARD CHECKPOINT", "#FFD84A", 2.2f, 0f, false);
    }

    private static void floorLights(BuildContext c) {
        // runway lights flanking the pink lane
        for (int z = -43; z <= -36; z++) {
            if ((z & 1) == 0) {
                c.set(-2, -1, z, Pal.LIGHT_WHITE);
                c.set(2, -1, z, Pal.LIGHT_WHITE);
            }
        }
        // floor inlay lights in the four quarters
        for (int sx = -1; sx <= 1; sx += 2) {
            c.set(sx * 8, -1, -38, Pal.LIGHT_WHITE);
            c.set(sx * 8, -1, -41, Pal.LIGHT_WHITE);
        }
    }

    /** North wall (z=-44, seen from the hall): black panels with a circle and a square either side of the gate. */
    private static void northWall(BuildContext c) {
        for (int side = -1; side <= 1; side += 2) {
            int x0 = side < 0 ? -9 : 5, x1 = side < 0 ? -5 : 9;
            // black field with pink frame
            for (int x = x0; x <= x1; x++) {
                for (int y = 1; y <= 7; y++) {
                    boolean edge = x == x0 || x == x1 || y == 1 || y == 7;
                    c.set(x, y, -44, edge ? Pal.PINK : Pal.BLACK);
                }
            }
            double cxm = (x0 + x1) / 2.0 + 0.5;
            Murals.paint(c, side < 0 ? 0 : 2, true, -44, x0 + 1, x1 - 1, 2, 6, cxm, 4.5, 1.9, Pal.WHITE, Pal.BLACK);
        }
        // lintel above the gate: black band, three symbol cubes on pink, black cornice (J1 does the same on its side)
        for (int x = -4; x <= 4; x++) {
            c.set(x, 6, -44, Pal.BLACK);
            c.set(x, 7, -44, Pal.PINK);
            c.set(x, 8, -44, Pal.BLACK);
        }
        for (int i = -1; i <= 1; i++) {
            c.set(i * 2, 7, -44, Pal.symbol(i + 1));
        }
    }

    private static void southWall(BuildContext c) {
        // above the dorm door (z=-35): sign band
        for (int x = -4; x <= 4; x++) {
            c.set(x, 6, -35, Pal.BLACK);
            c.set(x, 7, -35, Pal.BLACK);
            c.set(x, 8, -35, Pal.PINK);
        }
        c.text(0.5, 6.35, -35.04, "DORMITORY", "white", 2.0f, 180f, false);
        // pink pilasters either side of the door, up to the ceiling
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 2; k++) {
                int x = side * (5 + k);
                for (int y = 1; y <= 8; y++) {
                    c.set(x, y, -35, (y == 8) ? Pal.BLACK : Pal.PINK);
                }
            }
        }
    }

    private static void sideWalls(BuildContext c) {
        // labels above the canteen / infirmary doors
        c.text(-9.96, 6.6, -39.5, "CANTEEN", "white", 1.7f, -90f, false);
        c.text(10.96, 6.6, -39.5, "INFIRMARY", "white", 1.7f, 90f, false);
        for (int z = -43; z <= -36; z++) {
            for (int side = -1; side <= 1; side += 2) {
                int x = side * 11;
                if (z >= -41 && z <= -38) {
                    c.set(x, 6, z, Pal.BLACK);
                    continue;
                }
            }
        }
        // symbol cubes above the side doors
        c.set(-11, 7, -40, Pal.SYM_CIRCLE);
        c.set(-11, 7, -39, Pal.SYM_CIRCLE);
        c.set(11, 7, -40, Pal.SYM_SQUARE);
        c.set(11, 7, -39, Pal.SYM_SQUARE);
    }

    /** A guard booth: counter along z at x = side*4, chairs behind it, monitors on top. */
    private static void booth(BuildContext c, int side) {
        int x = side * 4;
        String towardLane = side < 0 ? "east" : "west";
        for (int z = -42; z <= -38; z++) {
            c.set(x, 0, z, Pal.PINK);
            c.set(x, 1, z, "minecraft:polished_blackstone_slab[type=bottom]");
        }
        // monitors on the counter facing the lane, keyboards in front of the chairs
        for (int z : new int[]{-41, -39}) {
            c.set(x, 2, z, Pal.monitor(towardLane));
        }
        // chairs behind the counter
        for (int z : new int[]{-41, -39}) {
            Props.chairBlack(c, x + side, 0, z, towardLane);
        }
        // rear storage
        c.set(side * 8, 0, -43, "minecraft:polished_blackstone");
        c.set(side * 8, 1, -43, "minecraft:polished_blackstone");
        c.set(side * 9, 0, -43, "minecraft:polished_blackstone");
        c.set(side * 9, 1, -43, "minecraft:polished_blackstone");
    }

    /** Metal-detector arch across the lane at z. */
    private static void arch(BuildContext c, int z) {
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 0; y <= 3; y++) {
                c.set(side * 3, y, z, y == 0 ? Pal.BLACK : Pal.PINK);
            }
            // scanner light strips on the inner faces
            c.set(side * 3, 2, z, Pal.LIGHT_PINK);
        }
        c.fill(-3, 4, z, 3, 4, z, Pal.BLACK);
        c.set(0, 5, z, Pal.LIGHT_PINK);
    }

    private static void ceiling(BuildContext c) {
        // cross beams at x = -10, -5, 0, 5, 10 hanging one block, edge beams along x
        for (int x = -10; x <= 10; x += 5) {
            c.fill(x, 8, -43, x, 8, -36, Pal.BLACK);
        }
        c.fill(-10, 8, -43, 10, 8, -43, Pal.BLACK);
        c.fill(-10, 8, -36, 10, 8, -36, Pal.BLACK);
        // light panels in the coffers
        for (int x = -8; x <= 6; x += 5) {
            c.fill(x, 9, -41, x + 1, 9, -38, Pal.LIGHT_WHITE);
        }
        // pendant lamps over the booths
        for (int side = -1; side <= 1; side += 2) {
            for (int z : new int[]{-41, -38}) {
                c.fill(side * 6, 5, z, side * 6, 8, z, Pal.chain("y"));
                c.set(side * 6, 4, z, Pal.lantern(true));
            }
        }
    }

    private static void cameras(BuildContext c) {
        Props.camera(c, -10, 8, -36, "east");
        Props.camera(c, 10, 8, -36, "west");
        Props.camera(c, -10, 8, -43, "east");
        Props.camera(c, 10, 8, -43, "west");
    }
}
