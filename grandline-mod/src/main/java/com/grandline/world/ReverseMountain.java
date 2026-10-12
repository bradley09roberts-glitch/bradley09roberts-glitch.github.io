package com.grandline.world;

/**
 * Reverse Mountain at (0,0): a peak on Red Line A with four canals climbing from the four Blues and one canal
 * descending east into Paradise. Ships entering a canal mouth are carried along it (see ship code).
 */
public final class ReverseMountain {
    public static final int SUMMIT_SURFACE = 290;
    public static final int CANAL_HALF_WIDTH = 6;
    /** Canal segments: {startX, startZ, endX, endZ}; start is the mouth at sea level, end is the summit. */
    public static final int[][] UP_CANALS = {
            {780, -780, 0, 0},   // North Blue
            {780, 780, 0, 0},    // East Blue
            {-780, -780, 0, 0},  // West Blue
            {-780, 780, 0, 0}    // South Blue
    };
    public static final int[] DOWN_CANAL = {0, 0, 1150, 0}; // into Paradise

    public static double mountainHeight(int x, int z) {
        double d = Math.sqrt((double) x * x + (double) z * z);
        if (d > 1150) return Double.NEGATIVE_INFINITY;
        return 63 + 262 * Math.pow(1 - d / 1150.0, 0.9);
    }

    /** Water surface height of the canal at (x,z), or NaN when not inside a canal. */
    public static double canalSurface(int x, int z) {
        double best = Double.NaN;
        for (int[] c : UP_CANALS) best = pick(best, segment(x, z, c, false));
        best = pick(best, segment(x, z, DOWN_CANAL, true));
        return best;
    }

    private static double pick(double a, double b) {
        if (Double.isNaN(a)) return b;
        if (Double.isNaN(b)) return a;
        return Math.max(a, b);
    }

    private static double segment(int x, int z, int[] c, boolean descending) {
        double ax = c[0], az = c[1], bx = c[2], bz = c[3];
        double vx = bx - ax, vz = bz - az;
        double len2 = vx * vx + vz * vz;
        double t = ((x - ax) * vx + (z - az) * vz) / len2;
        if (t < -0.02 || t > 1.0) return Double.NaN;
        t = Math.max(0, t);
        double px = ax + vx * t, pz = az + vz * t;
        double dd = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
        if (dd > CANAL_HALF_WIDTH) return Double.NaN;
        // descending canal: t=0 at summit, 1 at sea
        double k = descending ? (1 - t) : t;
        return 63 + (SUMMIT_SURFACE - 63) * k;
    }

    private ReverseMountain() {}
}
