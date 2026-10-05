package com.squidgame.build.arena.hub.dorm;

import com.squidgame.build.BuildContext;

/**
 * Lighting. The hall is 37 blocks high, so ceiling lamps cannot light the floor (light fades one level per block): every
 * walkable surface gets low fixtures instead. Panel-light tiles are let into the floor on a regular 5 x 5 lattice
 * (every cell is then within two blocks of a tile in each axis, i.e. level >= 10), the towers carry their own lamps, the
 * podium and plinth have lit risers and the catwalk has lit deck tiles. The big ceiling panels are decoration for the
 * roof space and the pig.
 */
final class Lights {
    private Lights() {
    }

    static void build(BuildContext c) {
        floorLattice(c);
        plinth(c);
        podium(c);
        gallery(c);
        extras(c);
        roofSpace(c);
    }

    /**
     * Invisible light blocks in the bays of the roof space between the trusses (nobody walks there; this only keeps the
     * pockets of the trusses, the ducts and the pipes from being pitch black).
     */
    private static void roofSpace(BuildContext c) {
        for (int z : new int[]{-24, -16, -8, 0, 8, 16, 24}) {
            for (int x = -35; x <= 35; x += 10) {
                if (free(c, x, 33, z)) {
                    c.set(x, 33, z, "minecraft:light[level=13]");
                }
            }
        }
    }

    /** Flush floor tiles on the multiples of 5; a blocked lattice point moves to the nearest free neighbour. */
    private static void floorLattice(BuildContext c) {
        int[][] around = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        java.util.Set<Long> spots = Bunks.candidateCells();
        for (int z = -30; z <= 30; z += 5) {
            for (int x = -40; x <= 40; x += 5) {
                for (int[] d : around) {
                    int px = x + d[0], pz = z + d[1];
                    if (px < Layout.X0 || px > Layout.X1 || pz < Layout.Z0 || pz > Layout.Z1) {
                        continue;
                    }
                    if (!free(c, px, 0, pz) || !free(c, px, 1, pz) || spots.contains(Bunks.key(px, pz))) {
                        continue;
                    }
                    String f = c.get(px, -1, pz);
                    if (f == null || f.contains("panel_light") || !solidFloor(f)) {
                        continue;
                    }
                    c.set(px, -1, pz, Math.abs(px) <= 1 ? Pal.PANEL_PINK : Pal.PANEL_WHITE);
                    break;
                }
            }
        }
    }

    /** Lit tiles in the registration plinth top. */
    private static void plinth(BuildContext c) {
        for (int x : new int[]{-6, -3, 3, 6}) {
            c.set(x, 0, 28, Pal.PANEL_WARM);
        }
        for (int x : new int[]{-5, 5}) {
            c.set(x, 0, 31, Pal.PANEL_WARM);
        }
        for (int x : new int[]{-6, -2, 2, 6}) {
            c.set(x, 0, 32, Pal.PANEL_WARM);
        }
    }

    /** Lit tiles on the top tier and lamp bollards beside the podium stair. */
    private static void podium(BuildContext c) {
        for (int x : new int[]{-4, 0, 4}) {
            for (int z : new int[]{-2, 2}) {
                c.set(x, 2, z, Pal.PANEL_PINK);
            }
        }
        for (int s : new int[]{-4, 4}) {
            c.set(s, 1, 5, Pal.SEA);
            c.set(s, 2, 4, Pal.SEA);
        }
    }

    /** Lamps in the control-room door frame. */
    private static void gallery(BuildContext c) {
        c.set(41, 13, -3, Pal.PANEL_WARM);
        c.set(41, 13, 3, Pal.PANEL_WARM);
    }

    /** Odd corners the lattice misses. */
    private static void extras(BuildContext c) {
        c.set(37, -1, 31, Pal.PANEL_WHITE);
        c.set(-38, -1, -31, Pal.PANEL_WHITE);
        // glowing thresholds in the two door openings (exit door: both wall layers; control-room door)
        c.set(-2, -1, -33, Pal.PANEL_WHITE);
        c.set(2, -1, -33, Pal.PANEL_WHITE);
        c.set(0, -1, -34, Pal.PANEL_WHITE);
        c.set(42, 11, 0, Pal.PANEL_WARM);
    }

    private static boolean solidFloor(String s) {
        return !s.equals(Pal.AIR);
    }

    private static boolean free(BuildContext c, int x, int y, int z) {
        String s = c.get(x, y, z);
        return s == null || s.equals(Pal.AIR);
    }
}
