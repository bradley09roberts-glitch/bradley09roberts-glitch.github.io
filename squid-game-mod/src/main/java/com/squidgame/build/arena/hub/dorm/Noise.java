package com.squidgame.build.arena.hub.dorm;

/**
 * Deterministic, stateless noise (a position hashed with a seed) so every texture of the dormitory is independent of
 * the order in which things are built.
 */
final class Noise {
    private Noise() {
    }

    static int hash(int seed, int x, int y, int z) {
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
    static double rand(int seed, int x, int y, int z) {
        return ((hash(seed, x, y, z) >>> 8) & 0xFFFFFF) / 16777216.0;
    }

    static double rand(int seed, int x, int z) {
        return rand(seed, x, 0, z);
    }

    static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** 2D value noise in [0,1]. */
    static double value2(int seed, double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double tx = smooth(x - x0), tz = smooth(z - z0);
        double a = rand(seed, x0, 0, z0), b = rand(seed, x0 + 1, 0, z0);
        double c = rand(seed, x0, 0, z0 + 1), d = rand(seed, x0 + 1, 0, z0 + 1);
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }

    /** Fractal 2D noise in about [0,1]. */
    static double fbm2(int seed, double x, double z, int octaves) {
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

    /** Weighted choice of {@code states} using a per-position random value. */
    static String pick(int seed, int x, int y, int z, String[] states, int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        double r = rand(seed, x, y, z) * total;
        for (int i = 0; i < states.length; i++) {
            r -= weights[i];
            if (r < 0) {
                return states[i];
            }
        }
        return states[states.length - 1];
    }
}
