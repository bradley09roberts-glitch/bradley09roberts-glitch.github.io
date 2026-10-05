package com.squidgame.build.arena.finale;

/** Small deterministic hash / value-noise helpers (independent of the builder RNG so layers do not disturb each other). */
final class Noise {
    private Noise() {
    }

    static int hash(int x, int y, int z, int seed) {
        int h = seed * 0x27D4EB2F + x * 0x165667B1 + y * 0x9E3779B1 + z * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return h;
    }

    /** Uniform pseudo random value in [0,1) for an integer cell. */
    static double hash01(int x, int z, int seed) {
        return (hash(x, 0, z, seed) >>> 8) / (double) (1 << 24);
    }

    static double hash01(int x, int y, int z, int seed) {
        return (hash(x, y, z, seed) >>> 8) / (double) (1 << 24);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /** Smooth value noise in [0,1]; one lattice cell = one unit. */
    static double value(double x, double z, int seed) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = smooth(x - x0), fz = smooth(z - z0);
        double a = hash01(x0, z0, seed), b = hash01(x0 + 1, z0, seed);
        double c = hash01(x0, z0 + 1, seed), d = hash01(x0 + 1, z0 + 1, seed);
        double top = a + (b - a) * fx;
        double bot = c + (d - c) * fx;
        return top + (bot - top) * fz;
    }

    /** Fractal sum of value noise, normalised to [0,1]. */
    static double fbm(double x, double z, int seed, int octaves) {
        double sum = 0, amp = 0.5, norm = 0, f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += amp * value(x * f, z * f, seed + i * 101);
            norm += amp;
            amp *= 0.5;
            f *= 2;
        }
        return sum / norm;
    }

    /** 4x4 Bayer threshold in (0,1) for ordered dithering. */
    static double bayer4(int x, int y) {
        final int[] m = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
        return (m[((y & 3) << 2) | (x & 3)] + 0.5) / 16.0;
    }
}
