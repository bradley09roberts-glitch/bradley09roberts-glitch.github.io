package com.squidgame.core.bridge;

import com.squidgame.core.Difficulty;

/**
 * Pure numbers of the glass bridge, one row of the table per {@link Difficulty}. Everything is in ticks (20 per
 * second) and independent of Minecraft so it can be unit tested.
 *
 * <ul>
 *   <li><b>time</b>: the whole crossing has {@code baseTimeTicks} for up to {@code freeContestants} contestants and
 *       {@code extraTicksPerContestant} more for every additional one. That allowance is the time the gate needs per
 *       contestant at that difficulty (a crossing takes 35-45 s and only {@code maxCrossers} are on the bridge at once,
 *       so a harder difficulty with fewer crossers has a <i>larger</i> allowance); the difficulty shows in how little
 *       slack the base time leaves, so a long queue never loses its tail merely for being long;</li>
 *   <li><b>stall</b>: nobody may stay on one panel longer than {@code stallLimitTicks}; afterwards the glass gives way
 *       (it is rebuilt a few seconds later so the bridge stays passable);</li>
 *   <li><b>shatter</b>: a fragile panel cracks the moment it is touched and shatters
 *       {@code shatterDelayMin..Max} ticks later;</li>
 *   <li><b>flow</b>: the gate calls the next contestant only when the previous one is {@code releaseGapRows} rows into
 *       the bridge (or gone) and fewer than {@code maxCrossers} are on it.</li>
 * </ul>
 */
public final class BridgeRules {
    /** A panel that was broken by the stall rule (it was tempered, so the bridge must stay passable) re-forms after this long. */
    public static final int REBUILD_TICKS = 100;

    private BridgeRules() {
    }

    public record Params(int baseTimeTicks, int freeContestants, int extraTicksPerContestant,
                         int stallLimitTicks, int stallWarnTicks,
                         int shatterDelayMin, int shatterDelayMax, int confirmTicks,
                         int releaseGapRows, int maxCrossers, int minReleaseSpacingTicks, int callLimitTicks,
                         double npcHesitationScale) {
    }

    public static Params params(Difficulty d) {
        return switch (d) {
            case NORMAL -> new Params(secs(330), 16, secs(9.0), secs(25), secs(8), 8, 10, 16, 2, 5, 40, secs(25 + 8), 1.0);
            case HARD -> new Params(secs(270), 16, secs(10.0), secs(18), secs(6), 7, 9, 15, 3, 4, 50, secs(18 + 8), 0.85);
            case EXTREME -> new Params(secs(210), 16, secs(12.0), secs(12), secs(5), 6, 8, 14, 3, 3, 60, secs(12 + 8), 0.7);
        };
    }

    private static int secs(double s) {
        return (int) Math.round(s * 20);
    }

    /** Total game time for {@code contestants} participants. */
    public static int timeLimitTicks(Difficulty d, int contestants) {
        Params p = params(d);
        int extra = Math.max(0, contestants - p.freeContestants());
        return p.baseTimeTicks() + extra * p.extraTicksPerContestant();
    }

    /**
     * True when landing on {@code landedRow} means a whole row was jumped over (a 4 block sprint jump from the edge of
     * one row reaches the next but one): the contestant has to step on every row, so such a landing is not accepted.
     *
     * @param rowReached the highest row (0-based) the contestant has stood on, -1 = still on the start platform
     */
    public static boolean skipsRow(int rowReached, int landedRow) {
        return landedRow > rowReached + 1;
    }

    // ------------------------------------------------------------------ stall timer

    /** Ticks left before the panel a contestant arrived on at {@code arrivedTick} gives way. */
    public static int stallRemaining(Params p, long arrivedTick, long now) {
        long left = p.stallLimitTicks() - (now - arrivedTick);
        return (int) Math.max(0, Math.min(p.stallLimitTicks(), left));
    }

    public static boolean stallExpired(Params p, long arrivedTick, long now) {
        return now - arrivedTick >= p.stallLimitTicks();
    }

    /** 0 = plenty of time, 1 = warning (countdown highlighted), 2 = urgent (last third of the warning), 3 = last second. */
    public static int stallWarning(Params p, int remainingTicks) {
        if (remainingTicks > p.stallWarnTicks()) {
            return 0;
        }
        if (remainingTicks <= 20) {
            return 3;
        }
        return remainingTicks <= p.stallWarnTicks() / 2 ? 2 : 1;
    }

    /** Fraction (0..1) of the stall limit already used: the "pressure" an NPC feels. */
    public static double stallPressure(Params p, int remainingTicks) {
        return 1.0 - Math.max(0, Math.min(p.stallLimitTicks(), remainingTicks)) / (double) p.stallLimitTicks();
    }
}
