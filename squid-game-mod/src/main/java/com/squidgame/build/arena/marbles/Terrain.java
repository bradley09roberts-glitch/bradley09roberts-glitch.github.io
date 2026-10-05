package com.squidgame.build.arena.marbles;

import com.squidgame.build.BuildContext;

/** Foundation and the base paving of the whole village (alleys); courts, the square and the hall overwrite it. */
final class Terrain {
    private Terrain() {
    }

    // N-S alley centres (width 5) and E-W alleys (centre row, half width)
    private static final int[] NS_ALLEYS = {-47, -25, 25, 47};

    static void build(BuildContext c) {
        int ext = Layout.WALL + 3;
        c.fill(Layout.X0 - ext, -14, Layout.Z0 - ext, Layout.X1 + ext, -1, Layout.Z1 + ext, Mat.STONE);
        c.fill(Layout.X0 - ext, -14, Layout.Z0 - ext, Layout.X1 + ext, -9, Layout.Z1 + ext, Mat.DEEPSLATE);
        c.pattern(Layout.X0 - Layout.WALL, 0, Layout.Z0 - Layout.WALL, Layout.X1 + Layout.WALL, 0, Layout.Z1 + Layout.WALL,
                (x, y, z) -> pave(x, z));
    }

    /** Alley pavement for local block (x,z). */
    static String pave(int x, int z) {
        double r = U.rand(x, z, 11);
        double big = U.fbm(x * 0.07, z * 0.07, 5, 3);
        double fine = U.vnoise(x * 0.45, z * 0.45, 9);
        // wet patches / puddles
        if (big > 0.60 && fine > 0.42) {
            return r < 0.12 ? Mat.P_ANDESITE : Mat.PUDDLE;
        }
        if (big > 0.55 && r < 0.45) {
            return Mat.ANDESITE;
        }
        // stone-brick strips: alley centre lines and cross bands
        for (int cx : NS_ALLEYS) {
            if (x == cx && z != 3) {
                return r < 0.15 ? Mat.SB_CRACK : Mat.SB;
            }
            if (Math.abs(x - cx) <= 2 && Math.floorMod(z + 5, 14) == 0) {
                return r < 0.2 ? Mat.SB_MOSS : Mat.SB;
            }
        }
        if (z == 3 && Math.abs(x) > 16) {
            return r < 0.15 ? Mat.SB_CRACK : Mat.SB;
        }
        if (Math.abs(z - 3) <= 2 && Math.abs(x) > 16 && Math.floorMod(x + 4, 12) == 0) {
            return Mat.SB;
        }
        String[] pal = {Mat.COBBLE, Mat.GRAVEL, Mat.MOSSY_COBBLE, Mat.ANDESITE, Mat.PATH, Mat.COARSE, Mat.STONE, Mat.TUFF};
        // districts: west damp and mossy, east dry and gravelly, centre mixed earth
        double[] w = x < -22 ? new double[]{54, 8, 14, 6, 5, 4, 5, 4}
                : x > 22 ? new double[]{36, 26, 3, 12, 9, 7, 5, 2}
                : new double[]{44, 14, 8, 8, 12, 8, 3, 3};
        return U.pick(r, pal, w);
    }
}
