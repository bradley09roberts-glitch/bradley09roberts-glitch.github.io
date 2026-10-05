package com.squidgame.core.bridge;

import com.squidgame.core.Difficulty;

/**
 * Pure numbers of the glass bridge, one row of the table per {@link Difficulty}. Everything is in ticks (20 per
 * second) and independent of Minecraft so it can be unit tested.
 *
 * <ul>
 *   <li><b>time</b>: a crossing has at least {@code baseTimeTicks} (the design numbers 330 / 270 / 210 s, enough for the
 *       16 contestants of the show) and, for a long queue, {@code clockBaseTicks + clockPerContestantTicks * n}. The
 *       allowance per contestant is the difficulty ladder for crowds: the gate needs about 7 / 8.5 / 11 s per contestant
 *       (Normal / Hard / Extreme, measured with the unlimited whole-game simulation of the unit tests), and the clock
 *       grants 11.4 / 4.5 / 2.9 s. On Normal everybody who does not fall gets across; on Hard the tail of a big queue
 *       is cut off (about half of a big crowd gets across); on Extreme most of it is still standing in line when the
 *       time is up;</li>
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

    /**
     * @param baseTimeTicks          the shortest time limit (small crowds)
     * @param clockBaseTicks         fixed part of the time limit of a big crowd
     * @param clockPerContestantTicks time a big crowd is granted per contestant (what the gate needs is {@link #neededTicks})
     * @param npcHesitationScale     scales how long NPCs stare at an unrevealed row (smaller = more decisive)
     * @param npcSlipScale           scales how often NPCs misstep although they know the way (nerve)
     */
    public record Params(int baseTimeTicks, int clockBaseTicks, int clockPerContestantTicks,
                         int stallLimitTicks, int stallWarnTicks,
                         int shatterDelayMin, int shatterDelayMax, int confirmTicks,
                         int releaseGapRows, int maxCrossers, int minReleaseSpacingTicks, int callLimitTicks,
                         double npcHesitationScale, double npcSlipScale) {
    }

    public static Params params(Difficulty d) {
        return switch (d) {
            case NORMAL -> new Params(secs(330), secs(98), secs(11.4), secs(25), secs(8), 8, 10, 16, 2, 5, 40, secs(25 + 8), 1.0, 1.0);
            case HARD -> new Params(secs(270), secs(120), secs(4.5), secs(18), secs(6), 7, 9, 15, 3, 4, 50, secs(18 + 8), 0.85, 3.0);
            case EXTREME -> new Params(secs(210), secs(110), secs(2.9), secs(12), secs(5), 6, 8, 14, 3, 3, 60, secs(12 + 8), 0.7, 7.0);
        };
    }

    private static int secs(double s) {
        return (int) Math.round(s * 20);
    }

    /**
     * What a field of {@code contestants} well-playing NPCs needs to get through the gate at this difficulty (queue
     * order, hesitation, hops), measured with the unlimited whole-game simulation: about 62 + 7.1 s per contestant on
     * Normal, 34 + 8.5 s on Hard, 11 s on Extreme (fewer simultaneous crossers and a longer call spacing). The clock
     * is compared with it: generous on Normal, tight on Hard, a fraction on Extreme.
     */
    public static int neededTicks(Difficulty d, int contestants) {
        int n = Math.max(0, contestants);
        return switch (d) {
            case NORMAL -> secs(62) + secs(7.1) * n;
            case HARD -> secs(34) + secs(8.5) * n;
            case EXTREME -> secs(11.0) * n;
        };
    }

    /** Total game time for {@code contestants} participants: never below the base time, longer for a big crowd. */
    public static int timeLimitTicks(Difficulty d, int contestants) {
        Params p = params(d);
        return Math.max(p.baseTimeTicks(), p.clockBaseTicks() + p.clockPerContestantTicks() * Math.max(0, contestants));
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
