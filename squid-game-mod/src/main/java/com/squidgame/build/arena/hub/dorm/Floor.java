package com.squidgame.build.arena.hub.dorm;

import com.squidgame.build.BuildContext;

/**
 * The floor layer (y = -1): worn poured concrete with expansion joints, a polished central avenue with pastel centre
 * lines, a painted ring round the plaza, yellow bunk-zone outlines round every tower, the pink-and-white arrival
 * pad in front of the registration plinth and the hazard-striped threshold of the exit door.
 */
final class Floor {
    private Floor() {
    }

    private static final int SEED = 3187;
    static final int Y = -1;

    private static final String[] BASE = {Pal.LGRAY, Pal.STONE, Pal.ANDESITE, Pal.SMOOTH, Pal.PANDESITE, Pal.GRAY};
    private static final int[] BASE_W = {38, 20, 16, 10, 10, 6};
    private static final String[] AVENUE = {Pal.PANDESITE, Pal.SMOOTH, Pal.PANDESITE, Pal.CALCITE};
    private static final int[] AVENUE_W = {40, 30, 20, 10};

    static void build(BuildContext c) {
        c.pattern(Layout.X0, Y, Layout.Z0, Layout.X1, Y, Layout.Z1, (x, y, z) -> block(x, z));
    }

    static String block(int x, int z) {
        String s = base(x, z);
        s = avenue(s, x, z);
        s = plaza(s, x, z);
        s = towerZones(s, x, z);
        s = arrival(s, x, z);
        s = exitThreshold(s, x, z);
        return s;
    }

    private static String base(int x, int z) {
        // large soft patches of cleaner / dirtier concrete
        double n = Noise.fbm2(SEED, x * 0.11, z * 0.11, 3);
        String[] set = BASE;
        int[] w = BASE_W;
        String s = Noise.pick(SEED + 1, x, 0, z, set, w);
        if (n > 0.62 && Noise.rand(SEED + 2, x, 0, z) < 0.5) {
            s = Pal.PANDESITE;
        }
        if (n < 0.34 && Noise.rand(SEED + 3, x, 0, z) < 0.45) {
            s = Pal.ANDESITE;
        }
        // expansion joints on an 8-block grid
        if ((Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0) && Noise.rand(SEED + 4, x, 0, z) < 0.75) {
            s = Pal.GRAY;
        }
        return s;
    }

    /** Polished central avenue, x[-5,5], with pastel guide lines. */
    private static String avenue(String s, int x, int z) {
        int ax = Math.abs(x);
        if (ax > 6) {
            return s;
        }
        if (ax == 6) {
            return Pal.MINT;
        }
        if (ax == 5 || ax == 3) {
            return ax == 5 ? Pal.CREAM : Pal.CREAM;
        }
        if (ax == 0) {
            return (Math.floorMod(z, 4) < 3) ? Pal.PINK : Pal.CREAM;
        }
        return Noise.pick(SEED + 6, x, 0, z, AVENUE, AVENUE_W);
    }

    /** Painted rings round the podium (centre 0,0). */
    private static String plaza(String s, int x, int z) {
        double r = Math.sqrt(x * x + z * z);
        if (r < Layout.PLAZA_R + 1.5 && r > 8.2) {
            if (r > Layout.PLAZA_R - 0.6 && r <= Layout.PLAZA_R + 0.5) {
                return Pal.PINK;
            }
            if (r > Layout.PLAZA_R - 2.0 && r <= Layout.PLAZA_R - 1.4) {
                return Pal.CREAM;
            }
            if (r > Layout.PLAZA_R - 2.8 && r <= Layout.PLAZA_R - 2.0) {
                return Pal.MINT;
            }
            if (r <= Layout.PLAZA_R - 2.8 && x != 0) {
                return Noise.pick(SEED + 7, x, 0, z, AVENUE, AVENUE_W);
            }
        }
        if (r <= 8.2 && r > 5.0) {
            return ((x + z) & 1) == 0 ? Pal.PANDESITE : Pal.SMOOTH;
        }
        return s;
    }

    /** Dark concrete pad under each tower and a yellow outline one block outside it. */
    private static String towerZones(String s, int x, int z) {
        for (Layout.Tower t : Layout.towers()) {
            int dx = Math.abs(x - t.cx()), dz = Math.abs(z - t.cz());
            if (dx <= Layout.TOWER_HX + 1 && dz <= Layout.TOWER_HZ + 1) {
                if (dx == Layout.TOWER_HX + 1 || dz == Layout.TOWER_HZ + 1) {
                    return Pal.YELLOW;
                }
                return Noise.pick(SEED + 8, x, 0, z, new String[]{Pal.GRAY, Pal.STONE, Pal.ANDESITE}, new int[]{5, 2, 2});
            }
        }
        return s;
    }

    /** Pink and white tile pad in the arrival hall, pastel border. */
    private static String arrival(String s, int x, int z) {
        if (z >= 20 && z <= 32 && Math.abs(x) <= 11) {
            if (z == 20 || Math.abs(x) == 11) {
                return Pal.PINK;
            }
            if (z == 21 || Math.abs(x) == 10) {
                return Pal.CREAM;
            }
            return ((x + z) & 1) == 0 ? Pal.TILE_WHITE : Pal.TILE_PINK;
        }
        return s;
    }

    /** Black and yellow hazard threshold in front of the exit door. */
    private static String exitThreshold(String s, int x, int z) {
        if (z <= -26 && Math.abs(x) <= 7) {
            if (z >= -29 && Math.abs(x) <= 6) {
                // diagonal hazard stripes
                return Math.floorMod(x + z, 4) < 2 ? "minecraft:yellow_concrete" : Pal.BLACK;
            }
            if (z <= -30) {
                return ((x + z) & 1) == 0 ? Pal.TILE_BLACK : Pal.TILE_PINK;
            }
            return Pal.TILE_BLACK;
        }
        return s;
    }
}
