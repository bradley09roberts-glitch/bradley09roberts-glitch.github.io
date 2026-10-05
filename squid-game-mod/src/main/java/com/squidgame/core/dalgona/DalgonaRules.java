package com.squidgame.core.dalgona;

import com.squidgame.core.Difficulty;
import com.squidgame.core.util.Rng;

/**
 * The numbers behind the honeycomb game, one table per difficulty (pure Java, unit tested).
 *
 * <h2>The rules, as shown to players</h2>
 * <ul>
 *   <li>Trace the shape pressed into the cookie with the needle. Carving within {@link Params#tolerance} of the
 *       groove frees it; the shape is free once {@link Params#successThreshold} of the groove is carved.</li>
 *   <li>The cookie has a stress meter (0..100, it cracks at 100). Stress rises when the needle moves faster than
 *       the safe speed (quadratically; fragile spots lower the limit), a little while it wanders from the centre of
 *       the groove (even inside the corridor, so a steady hand keeps the meter calm) and a lot when it cuts into the
 *       cookie away from the groove (into the figure hurts more than into the waste). It slowly drains while the
 *       needle rests.</li>
 *   <li>A few licks soften the sugar: each one removes {@link Params#lickRelief} stress (limited number, cooldown).</li>
 *   <li>Rarely the sugar fractures by itself (micro-fracture): more likely when stressed and when rushing.</li>
 * </ul>
 */
public final class DalgonaRules {
    private DalgonaRules() {
    }

    public static final int TIN_COUNT = 4;
    /** Time the contestants get to pick a tin (real seconds at timeScale 1); it is part of the game clock. */
    public static final int SELECTION_SECONDS = 10;
    /** Safe speed at a spot of fragility f is {@code safeSpeed / f^SPEED_EXPONENT}. */
    public static final double SPEED_EXPONENT = 0.7;

    /**
     * Concrete numbers for one difficulty. Distances are canvas units (see {@link DalgonaShape#CANVAS}), speeds are
     * units per server tick, stress is on a 0..100 scale.
     */
    public record Params(double tolerance, double safeSpeed, double speedStress, double offPathStress, double wobbleStress,
                         double restDecay, double carveDecay, double microChance, double microMin, double microMax,
                         int licks, double lickRelief, int lickCooldownTicks, int lickLockTicks,
                         double successThreshold, int carveSeconds) {
        /** Number of carved samples that frees the shape. */
        public int samplesNeeded(DalgonaShape shape) {
            return Math.max(1, (int) Math.ceil(successThreshold * shape.sampleCount() - 1e-9));
        }

        /** The speed (units/tick) a fragile spot tolerates. */
        public double localSafeSpeed(double fragility) {
            return safeSpeed / Math.pow(Math.max(1.0, fragility), SPEED_EXPONENT);
        }
    }

    public static Params params(Difficulty d) {
        // how hard every kind of stress bites, and the speed (units/tick) below which carving is stress free
        double sens = switch (d) {
            case NORMAL -> 0.7;
            case HARD -> 1.0;
            case EXTREME -> 1.75;
        };
        double safe = switch (d) {
            case NORMAL -> 7.2;
            case HARD -> 6.6;
            case EXTREME -> 6.0;
        };
        double micro = switch (d) {
            case NORMAL -> 0.0006;
            case HARD -> 0.0009;
            case EXTREME -> 0.0013;
        };
        double relief = switch (d) {
            case NORMAL -> 38;
            case HARD -> 34;
            case EXTREME -> 30;
        };
        double restDecay = switch (d) {
            case NORMAL -> 0.10;
            case HARD -> 0.09;
            case EXTREME -> 0.08;
        };
        double threshold = switch (d) {
            case NORMAL -> 0.96;
            case HARD -> 0.97;
            case EXTREME -> 0.98;
        };
        int seconds = switch (d) {
            case NORMAL -> 150;
            case HARD -> 120;
            case EXTREME -> 95;
        };
        int licks = (int) Math.floor(5 * d.resourceScale + 1e-9);
        return new Params(22.0 * d.toleranceScale, safe, 1.2 * sens, 0.14 * sens, 0.05 * sens, restDecay, 0.02, micro, 6, 13,
                licks, relief, 100, 36, threshold, seconds);
    }

    /** Game duration in ticks (unscaled): carving time plus the tin selection. */
    public static int timeLimitTicks(Difficulty d) {
        return (params(d).carveSeconds() + SELECTION_SECONDS) * 20;
    }

    /** Length of the tin selection in ticks for the given config time scale (never shorter than two seconds). */
    public static int selectionTicks(double timeScale) {
        return Math.max(40, (int) Math.round(SELECTION_SECONDS * 20 * timeScale));
    }

    /** Relative frequency of each shape (indexed by ordinal): harder games hand out more stars and umbrellas. */
    public static double[] shapeWeights(Difficulty d) {
        return switch (d) {
            case NORMAL -> new double[]{0.30, 0.30, 0.22, 0.18};
            case HARD -> new double[]{0.20, 0.27, 0.28, 0.25};
            case EXTREME -> new double[]{0.10, 0.22, 0.31, 0.37};
        };
    }

    /**
     * Decides which shape each of {@code count} contestants finds in the tin they pick. Every shape appears at least
     * once whenever there are four or more contestants; the rest follows {@link #shapeWeights}. The result is a
     * shuffled list, deterministic for the generator.
     */
    public static DalgonaShape[] assignShapes(Rng rng, Difficulty d, int count) {
        DalgonaShape[] all = DalgonaShape.values();
        double[] w = shapeWeights(d);
        DalgonaShape[] out = new DalgonaShape[count];
        int i = 0;
        if (count >= all.length) {
            for (DalgonaShape s : all) {
                out[i++] = s;
            }
        }
        for (; i < count; i++) {
            double r = rng.nextDouble();
            int k = 0;
            double acc = w[0];
            while (k < w.length - 1 && r >= acc) {
                k++;
                acc += w[k];
            }
            out[i] = all[k];
        }
        for (int a = count - 1; a > 0; a--) {
            int b = rng.nextInt(a + 1);
            DalgonaShape t = out[a];
            out[a] = out[b];
            out[b] = t;
        }
        return out;
    }
}
