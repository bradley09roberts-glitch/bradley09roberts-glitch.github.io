package com.squidgame.build.arena.marbles;

/** Deterministic hashing / value-noise helpers (no shared RNG state, so results do not depend on build order). */
final class U {
    private U() {
    }

    /** 32-bit integer hash of three ints. */
    static int hash(int a, int b, int c) {
        int h = a * 0x27d4eb2d + b * 0x165667b1 + c * 0x9E3779B9 + 0x7f4a7c15;
        h ^= h >>> 15;
        h *= 0x2c1b3c6d;
        h ^= h >>> 12;
        h *= 0x297a2d39;
        h ^= h >>> 15;
        return h;
    }

    /** Uniform [0,1) from a hash. */
    static double rand(int a, int b, int c) {
        return (hash(a, b, c) >>> 8) / (double) (1 << 24);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /** Smooth 2D value noise in [0,1). */
    static double vnoise(double x, double z, int seed) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = smooth(x - x0), fz = smooth(z - z0);
        double a = rand(x0, z0, seed), b = rand(x0 + 1, z0, seed);
        double c = rand(x0, z0 + 1, seed), d = rand(x0 + 1, z0 + 1, seed);
        double top = a + (b - a) * fx, bot = c + (d - c) * fx;
        return top + (bot - top) * fz;
    }

    /** Fractal noise in [0,1). */
    static double fbm(double x, double z, int seed, int octaves) {
        double sum = 0, amp = 0.5, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += vnoise(x, z, seed + i * 31) * amp;
            norm += amp;
            x *= 2.03;
            z *= 2.03;
            amp *= 0.5;
        }
        return sum / norm;
    }

    /** Weighted pick from a hash-derived uniform value. */
    static <T> T pick(double r, T[] items, double[] weights) {
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        double t = r * total;
        for (int i = 0; i < items.length; i++) {
            t -= weights[i];
            if (t < 0) {
                return items[i];
            }
        }
        return items[items.length - 1];
    }

    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    /** Small splitmix64 generator for per-object randomness. */
    static final class Rnd {
        private long s;

        Rnd(long seed) {
            this.s = seed * 0x9E3779B97F4A7C15L + 0x1234567L;
            next();
            next();
        }

        long next() {
            s += 0x9E3779B97F4A7C15L;
            long z = s;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        double d() {
            return (next() >>> 11) / (double) (1L << 53);
        }

        int i(int bound) {
            return (int) ((next() >>> 33) % bound);
        }

        /** inclusive range */
        int range(int lo, int hi) {
            return lo + i(hi - lo + 1);
        }

        boolean chance(double p) {
            return d() < p;
        }

        <T> T pick(T[] a) {
            return a[i(a.length)];
        }
    }
}
