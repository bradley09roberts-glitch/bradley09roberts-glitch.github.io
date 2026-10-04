package com.squidgame.core.util;

import java.util.List;
import java.util.Random;

/** Small seeded RNG wrapper with the helpers the rules/AI code needs. Deterministic per seed. */
public final class Rng {
    private final Random random;
    private final long seed;

    public Rng(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    public long seed() {
        return seed;
    }

    /** Independent, reproducible child generator (e.g. one per contestant). */
    public Rng fork(long salt) {
        long z = seed ^ (salt * 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return new Rng(z ^ (z >>> 31));
    }

    public double nextDouble() {
        return random.nextDouble();
    }

    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    public boolean nextBoolean() {
        return random.nextBoolean();
    }

    /** Uniform in [min, max). */
    public double range(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    /** Uniform integer in [min, max] inclusive. */
    public int rangeInt(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    public double gaussian() {
        return random.nextGaussian();
    }

    public double gaussian(double mean, double sd) {
        return mean + random.nextGaussian() * sd;
    }

    public boolean chance(double probability) {
        return random.nextDouble() < probability;
    }

    public <T> T pick(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    public <T> void shuffle(List<T> list) {
        java.util.Collections.shuffle(list, random);
    }

    public static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
