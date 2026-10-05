package com.squidgame.build.arena.bridge;

import com.squidgame.build.BuildContext;

/**
 * Dressing of the pit: a staggered field of flush cold sea lanterns in the floor far below, and hanging cables
 * (chains from the roof trusses) that end in soul lanterns above the floor. Nothing is placed under the deck
 * (x -4..3) or reachable from it.
 */
final class Pit {
    private Pit() {
    }

    /** Floor lamp columns, mirror symmetric about the bridge axis. */
    private static final int[] LAMP_X = {-39, -33, -27, -21, -15, -9, -3, 2, 8, 14, 20, 26, 32, 38};

    /** Cable columns and rows (rows are truss lines); {x, bottom y}. */
    private static final int[][] CABLE_X = {{9, -9}, {-10, -12}, {18, -15}, {-19, -9}, {33, -12}, {-34, -15}};

    static void build(BuildContext c) {
        floorLamps(c);
        cables(c);
    }

    private static boolean underPylon(int x, int z) {
        boolean xs = (x >= -8 && x <= -3) || (x >= 2 && x <= 7);
        boolean zs = (z >= -1 && z <= 4) || (z >= 68 && z <= 73);
        return xs && zs;
    }

    private static void floorLamps(BuildContext c) {
        for (int j = 0; j < LAMP_X.length; j++) {
            int x = LAMP_X[j];
            int par = Math.min(j, LAMP_X.length - 1 - j) & 1;
            for (int z = Geo.ZC - 42; z <= Geo.ZC + 42; z += 6) {
                int zz = z + (par == 1 ? 3 : 0);
                if (zz < Geo.Z0 + 3 || zz > Geo.Z1 - 3 || underPylon(x, zz)) {
                    continue;
                }
                c.set(x, Geo.PIT, zz, Pal.SEA);
            }
        }
    }

    private static void cables(BuildContext c) {
        int[] rows = {22, 50};
        int k = 0;
        for (int zc : rows) {
            for (int[] cx : CABLE_X) {
                cable(c, cx[0], zc, cx[1] - (k++ % 2) * 3);
            }
        }
        for (int zc : new int[]{8, 64}) {
            for (int[] cx : new int[][]{{18, -14}, {-19, -11}, {33, -17}, {-34, -8}}) {
                cable(c, cx[0], zc, cx[1]);
            }
        }
    }

    private static void cable(BuildContext c, int x, int z, int yBottom) {
        c.fill(x, yBottom + 1, z, x, Geo.TRUSS_BOT - 1, z, Pal.chain());
        c.set(x, yBottom, z, Pal.SOUL_LANTERN_HANGING);
    }
}
