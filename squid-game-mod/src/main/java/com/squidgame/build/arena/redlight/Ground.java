package com.squidgame.build.arena.redlight;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.redlight.Layout.*;

/**
 * The playground floor: one continuous flat layer at y=0, sun-baked sandy earth (the mod's playground ground mixed
 * with sand, coarse dirt, packed mud and sandstone) in wind-swept patches and streaks, faint foot-paths, a worn start
 * area, scattered gravel, and a lighter, cleaner safe zone beyond the finish line. Painted chalk lines on top.
 * Everything here is a full block flush with the floor, so nothing in the lanes can catch a runner's feet.
 */
public final class Ground {
    private Ground() {
    }

    static final String PLAY = "squidgame:playground_ground";
    static final String SAND = "minecraft:sand";
    static final String COARSE = "minecraft:coarse_dirt";
    static final String MUD = "minecraft:packed_mud";
    static final String ROOTED = "minecraft:rooted_dirt";
    static final String GRAVEL = "minecraft:gravel";
    static final String SMOOTH = "minecraft:cut_sandstone";   // same top texture as smooth_sandstone
    static final String CUT = "minecraft:cut_sandstone";
    static final String WHITE = "minecraft:white_concrete";

    // foot-path centre lines x(z) = a + b*sin(z/c + d), half width w
    private static final double[][] PATHS = {
            {-17, 6.0, 23, 0.4, 1.3},
            {14, 5.0, 19, 2.0, 1.1},
            {39, 4.0, 27, 1.1, 1.0},
            {-41, 4.0, 21, 3.3, 1.0},
            {-2, 3.0, 31, 5.0, 0.9},
    };

    public static void build(BuildContext c) {
        c.fill(-HALF_W, -1, FIELD_Z0, HALF_W, -1, FIELD_Z1, "minecraft:dirt");
        for (int z = FIELD_Z0; z <= FIELD_Z1; z++) {
            for (int x = -HALF_W; x <= HALF_W; x++) {
                c.set(x, 0, z, surface(x, z));
            }
        }
        paintLines(c);
    }

    private static double pathStrength(int x, int z) {
        double best = 0;
        for (double[] p : PATHS) {
            double cx = p[0] + p[1] * Math.sin(z / p[2] + p[3]) + 1.2 * (Noise.value2(111, z / 5.0, p[0]) - 0.5);
            double d = Math.abs(x - cx) / p[4];
            if (d < 1.6) {
                best = Math.max(best, 1.0 - d / 1.6);
            }
        }
        return best;
    }

    static String surface(int x, int z) {
        double r = Noise.rand(101, x, 0, z);
        double r2 = Noise.rand(102, x, 0, z);
        double safeT = Noise.step(SAFE_Z0 - 9, SAFE_Z0 + 3, z);
        if (safeT > 0 && Noise.rand(105, x, 1, z) < safeT) {
            return safeSurface(x, z, r);
        }
        // coherent regions with ragged (dithered) edges
        double pale = Noise.fbm2(103, x / 17.0, z / 17.0, 3);                 // wind-swept pale sand
        double dark = Noise.fbm2(104, x / 8.5, z / 8.5, 3);                   // hard-packed / damp earth
        double streak = Noise.value2(109, x / 36.0 + z * 0.04, z / 3.4);      // long streaks along x
        double palePatch = Noise.step(0.58, 0.72, pale) * 0.92;
        double darkPatch = Noise.step(0.64, 0.80, dark) * 0.85;
        double startWear = 1.0 - Noise.step(0, 22, z);
        double wear = Math.max(0.80 * startWear, 0.55 * pathStrength(x, z));
        wear *= 0.5 + 0.9 * Noise.fbm2(110, x / 5.0, z / 5.0, 2);
        // pebbles: tight clusters and the odd stray
        double pebbles = Noise.value2(106, x / 3.3, z / 3.3);
        if ((pebbles > 0.87 && r < 0.55) || r < 0.004) {
            return GRAVEL;
        }
        double q = Noise.rand(108, x, 0, z);
        if (r2 < wear * 0.62 || r2 < darkPatch * 0.55) {
            double t = Noise.rand(107, x, 0, z);
            if (t < 0.55) {
                return MUD;
            } else if (t < 0.80) {
                return ROOTED;
            } else if (t < 0.93) {
                return COARSE;
            }
            return PLAY;
        }
        if (r2 > 1 - palePatch * 0.75 || (streak > 0.72 && q < 0.55)) {
            return q < 0.62 ? SAND : (q < 0.88 ? CUT : PLAY);
        }
        return q < 0.93 ? PLAY : (q < 0.975 ? SAND : CUT);
    }

    /** Lighter, cleaner surface of the safe zone: pale sandstone paving in broad slabs. */
    static String safeSurface(int x, int z, double r) {
        boolean seam = Math.floorMod(x + 56, 10) == 0 || Math.floorMod(z - SAFE_Z0, 10) == 0;
        if (seam) {
            return r < 0.65 ? CUT : SMOOTH;
        }
        if (r < 0.46) {
            return SMOOTH;
        } else if (r < 0.72) {
            return SAND;
        } else if (r < 0.88) {
            return CUT;
        } else if (r < 0.95) {
            return PLAY;
        }
        return "minecraft:calcite";
    }

    private static void paintLines(BuildContext c) {
        // start / finish lines (2 wide)
        c.fill(-LANE_X, 0, START_LINE_Z, LANE_X, 0, START_LINE_Z + 1, WHITE);
        c.fill(-LANE_X, 0, FINISH_LINE_Z, LANE_X, 0, FINISH_LINE_Z + 1, WHITE);
        // field boundary lines
        c.fill(-LANE_X, 0, START_LINE_Z, -LANE_X, 0, FINISH_LINE_Z + 1, WHITE);
        c.fill(LANE_X, 0, START_LINE_Z, LANE_X, 0, FINISH_LINE_Z + 1, WHITE);
        // lane ticks: every 10 blocks inside both boundary lines (long ones every 50), and short ticks on the lines
        for (int z = START_LINE_Z + 10; z < FINISH_LINE_Z - 2; z += 10) {
            int len = ((z - START_LINE_Z) % 50 == 0) ? 6 : 3;
            c.fill(-LANE_X + 1, 0, z, -LANE_X + len, 0, z, WHITE);
            c.fill(LANE_X - len, 0, z, LANE_X - 1, 0, z, WHITE);
        }
        for (int x = -LANE_X + 10; x < LANE_X; x += 10) {
            c.fill(x, 0, START_LINE_Z + 2, x, 0, START_LINE_Z + 3, WHITE);
            c.fill(x, 0, FINISH_LINE_Z - 2, x, 0, FINISH_LINE_Z - 1, WHITE);
        }
    }
}
