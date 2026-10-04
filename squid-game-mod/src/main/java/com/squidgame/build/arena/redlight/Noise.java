package com.squidgame.build.arena.redlight;

/**
 * Deterministic, stateless noise (position hashed with a seed), so every texture in the arena is independent of the
 * order in which things are built. Value noise with smooth interpolation plus a few helpers.
 */
public final class Noise {
    private Noise() {
    }

    public static int hash(int seed, int x, int y, int z) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= x * 0xC2B2AE3D27D4EB4FL;
        h ^= y * 0x165667B19E3779F9L;
        h ^= z * 0x27D4EB2F165667C5L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 29;
        return (int) h;
    }

    /** Uniform [0,1) per lattice point. */
    public static double rand(int seed, int x, int y, int z) {
        return ((hash(seed, x, y, z) >>> 8) & 0xFFFFFF) / 16777216.0;
    }

    public static double rand(int seed, int x, int z) {
        return rand(seed, x, 0, z);
    }

    public static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** Smoothstep between edges a and b. */
    public static double step(double a, double b, double v) {
        return smooth(clamp01((v - a) / (b - a)));
    }

    /** 2D value noise in [0,1]. */
    public static double value2(int seed, double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double tx = smooth(x - x0), tz = smooth(z - z0);
        double a = rand(seed, x0, 0, z0), b = rand(seed, x0 + 1, 0, z0);
        double c = rand(seed, x0, 0, z0 + 1), d = rand(seed, x0 + 1, 0, z0 + 1);
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }

    /** 3D value noise in [0,1]. */
    public static double value3(int seed, double x, double y, double z) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double tx = smooth(x - x0), ty = smooth(y - y0), tz = smooth(z - z0);
        double c000 = rand(seed, x0, y0, z0), c100 = rand(seed, x0 + 1, y0, z0);
        double c010 = rand(seed, x0, y0 + 1, z0), c110 = rand(seed, x0 + 1, y0 + 1, z0);
        double c001 = rand(seed, x0, y0, z0 + 1), c101 = rand(seed, x0 + 1, y0, z0 + 1);
        double c011 = rand(seed, x0, y0 + 1, z0 + 1), c111 = rand(seed, x0 + 1, y0 + 1, z0 + 1);
        double x00 = lerp(c000, c100, tx), x10 = lerp(c010, c110, tx);
        double x01 = lerp(c001, c101, tx), x11 = lerp(c011, c111, tx);
        return lerp(lerp(x00, x10, ty), lerp(x01, x11, ty), tz);
    }

    /** Fractal 2D noise in about [0,1] (octaves of halving amplitude, doubling frequency). */
    public static double fbm2(int seed, double x, double z, int octaves) {
        double sum = 0, amp = 0.5, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * value2(seed + 31 * i, x, z);
            norm += amp;
            amp *= 0.5;
            x *= 2.03;
            z *= 2.03;
        }
        return sum / norm;
    }

    public static double fbm3(int seed, double x, double y, double z, int octaves) {
        double sum = 0, amp = 0.5, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * value3(seed + 31 * i, x, y, z);
            norm += amp;
            amp *= 0.5;
            x *= 2.03;
            y *= 2.03;
            z *= 2.03;
        }
        return sum / norm;
    }

    /**
     * Value noise along a closed loop of length {@code period} (so a painting that runs around four walls has no
     * seam): lattice spacing is adjusted so a whole number of cells fits. Second axis is ordinary.
     */
    public static double loop2(int seed, double p, double v, double cellP, double cellV, int period) {
        int n = Math.max(1, (int) Math.round(period / cellP));
        double u = p / period * n;
        int i0 = (int) Math.floor(u);
        double tu = smooth(u - i0);
        double w = v / cellV;
        int j0 = (int) Math.floor(w);
        double tv = smooth(w - j0);
        int a = Math.floorMod(i0, n), b = Math.floorMod(i0 + 1, n);
        double c00 = rand(seed, a, 0, j0), c10 = rand(seed, b, 0, j0);
        double c01 = rand(seed, a, 0, j0 + 1), c11 = rand(seed, b, 0, j0 + 1);
        return lerp(lerp(c00, c10, tu), lerp(c01, c11, tu), tv);
    }

    /** 1D looped noise in [0,1]. */
    public static double loop1(int seed, double p, double cellP, int period) {
        return loop2(seed, p, 0.0, cellP, 1.0, period);
    }

    /** Fractal looped 1D noise. */
    public static double loopFbm1(int seed, double p, double cellP, int period, int octaves) {
        double sum = 0, amp = 0.5, norm = 0;
        double cell = cellP;
        for (int i = 0; i < octaves; i++) {
            sum += amp * loop1(seed + 17 * i, p, cell, period);
            norm += amp;
            amp *= 0.5;
            cell *= 0.5;
        }
        return sum / norm;
    }

    /** 4x4 ordered-dither (Bayer) threshold in (0,1). */
    public static double bayer4(int x, int y) {
        final int[] m = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
        return (m[((y & 3) << 2) | (x & 3)] + 0.5) / 16.0;
    }
}
