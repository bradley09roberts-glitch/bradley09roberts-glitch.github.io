package com.squidgame.build.arena.hub.control;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.hub.control.Geo.*;

/**
 * The commander's dais at the foot of the display wall: three concentric half-disc tiers (standing levels 13, 14, 15)
 * with glowing pink rims and a stair flight on the west side, the throne with a tall back and armrests, and the
 * curved commander's desk with monitors turned towards the throne.
 */
final class Dais {
    private Dais() {
    }

    static final double CX = 84, CZ = 0;
    static final double R1 = 10.5, R2 = 8.0, R3 = 5.5;

    static int tier(int x, int z) {
        if (x > 84) {
            return 0;
        }
        double r = Math.hypot(x - CX, z - CZ);
        return r <= R3 ? 3 : r <= R2 ? 2 : r <= R1 ? 1 : 0;
    }

    static void build(BuildContext c) {
        for (int x = 72; x <= 84; x++) {
            for (int z = -12; z <= 12; z++) {
                int t = tier(x, z);
                if (t == 0) {
                    continue;
                }
                c.fill(x, 12, z, x, 11 + t, z, Pal.PBS_BRICKS);
                double r = Math.hypot(x - CX, z - CZ);
                double rt = t == 3 ? R3 : t == 2 ? R2 : R1;
                boolean rim = r > rt - 1.0;
                boolean westEdge = tier(x - 1, z) < t;
                String top;
                if (westEdge && Math.abs(z) <= 5 && t <= 3) {
                    // stair flight on the west side (high side east)
                    top = Pal.stairs("minecraft:polished_blackstone_brick_stairs", "east", false);
                } else if (rim) {
                    top = ((x + z) & 1) == 0 ? Pal.LIGHT_PINK : Pal.PBS;
                } else {
                    top = Pal.PBS;
                }
                c.set(x, 11 + t, z, top);
            }
        }
        // flush rim lights on the middle tier beside the stair flight
        for (int z : new int[]{-4, -2, 2, 4}) {
            c.set(78, 13, z, Pal.LIGHT_PINK);
        }
        throne(c);
        commanderDesk(c);
    }

    private static void throne(BuildContext c) {
        // seat: pink stair, high back to the east (the occupant faces west); the cells above the seat stay free
        c.set(83, 15, 0, Pal.stairs("squidgame:pastel_pink_stairs", "east", false));
        // armrests with glowing front caps
        for (int z = -1; z <= 1; z += 2) {
            c.set(83, 15, z, Pal.PBS);
            c.set(83, 16, z, "minecraft:polished_blackstone_slab[type=bottom]");
            c.set(82, 15, z, Pal.stairs("minecraft:polished_blackstone_brick_stairs", "west", false));
            c.set(82, 16, z, Pal.LIGHT_PINK);
        }
        // tall back against the wall: spine and broad shoulders, glowing pink edges and crown
        for (int y = 15; y <= 22; y++) {
            for (int z = -1; z <= 1; z++) {
                c.set(84, y, z, Pal.PBS);
            }
        }
        for (int y = 18; y <= 25; y++) {
            for (int z = -3; z <= 3; z++) {
                c.set(84, y, z, y == 25 ? Pal.LIGHT_PINK : Pal.PBS);
            }
        }
        for (int y = 15; y <= 24; y++) {
            c.set(84, y, -4, Pal.LIGHT_PINK);
            c.set(84, y, 4, Pal.LIGHT_PINK);
        }
        // the three symbols on the spine (the Front Man's colours)
        c.set(84, 17, 0, Pal.SYM_SQUARE);
        c.set(84, 20, 0, Pal.SYM_TRIANGLE);
        c.set(84, 23, 0, Pal.SYM_CIRCLE);
        // footrest
        c.set(81, 15, 0, Pal.slab("minecraft:polished_blackstone_slab", false));
        c.marker("control.commander", 82.5, 15.0, 0.5, 90f);
    }

    /** Curved desk of radius 4.5 in front of the throne on the top tier. */
    private static void commanderDesk(BuildContext c) {
        boolean[][] seen = new boolean[100][50];
        for (double a = -38; a <= 38; a += 0.5) {
            double rad = Math.toRadians(a);
            int x = (int) Math.round(CX - 4.5 * Math.cos(rad));
            int z = (int) Math.round(CZ + 4.5 * Math.sin(rad));
            if (seen[x][z + 20]) {
                continue;
            }
            seen[x][z + 20] = true;
            c.set(x, 15, z, ((x + z) & 1) == 0 ? Pal.LIGHT_PINK : Pal.PBS);
        }
        for (int z : new int[]{-2, 0, 2}) {
            int x = (int) Math.round(CX - 4.5 * Math.cos(Math.asin(z / 4.5)));
            c.set(x, 16, z, Pal.monitor("east"));
        }
    }
}
