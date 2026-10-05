package com.squidgame.core;

/**
 * The "stay inside the arena" rule for human contestants while a game runs, as pure functions (the tournament calls it every two
 * seconds): a contestant who is outside the arena region (plus a margin) gets a warning, is put back at the second strike and
 * is eliminated at the fourth (about eight seconds outside); a single check inside resets the count.
 */
public final class BoundsRule {
    public static final double MARGIN = 3.0;
    public static final int PUT_BACK_AT = 2;
    public static final int ELIMINATE_AT = 4;

    public enum Action {NONE, WARN, WARN_AND_PUT_BACK, ELIMINATE}

    public record Step(int strikes, Action action) {
    }

    private BoundsRule() {
    }

    /** Whether a position is outside a region whose block extent is [minX, maxX] x [minZ, maxZ] (both inclusive) by more than the margin. */
    public static boolean outside(double x, double z, int minX, int maxX, int minZ, int maxZ) {
        return x < minX - MARGIN || x > maxX + 1 + MARGIN || z < minZ - MARGIN || z > maxZ + 1 + MARGIN;
    }

    /** One watchdog check: the new strike count and what to do about it. */
    public static Step step(boolean outside, int strikes) {
        if (!outside) {
            return new Step(0, Action.NONE);
        }
        int s = strikes + 1;
        if (s >= ELIMINATE_AT) {
            return new Step(s, Action.ELIMINATE);
        }
        return new Step(s, s >= PUT_BACK_AT ? Action.WARN_AND_PUT_BACK : Action.WARN);
    }
}
