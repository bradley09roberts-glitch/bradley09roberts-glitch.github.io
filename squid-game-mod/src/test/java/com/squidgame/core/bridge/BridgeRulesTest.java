package com.squidgame.core.bridge;

import com.squidgame.core.Difficulty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BridgeRulesTest {
    private static BridgeRules.Params p(Difficulty d) {
        return BridgeRules.params(d);
    }

    @Test
    void tableMatchesTheDesignedNumbers() {
        assertEquals(330 * 20, p(Difficulty.NORMAL).baseTimeTicks());
        assertEquals(270 * 20, p(Difficulty.HARD).baseTimeTicks());
        assertEquals(210 * 20, p(Difficulty.EXTREME).baseTimeTicks());
        assertEquals(25 * 20, p(Difficulty.NORMAL).stallLimitTicks());
        assertEquals(18 * 20, p(Difficulty.HARD).stallLimitTicks());
        assertEquals(12 * 20, p(Difficulty.EXTREME).stallLimitTicks());
    }

    @Test
    void harderDifficultiesAreTighterInEveryTimingNumber() {
        BridgeRules.Params n = p(Difficulty.NORMAL), h = p(Difficulty.HARD), e = p(Difficulty.EXTREME);
        assertTrue(n.baseTimeTicks() > h.baseTimeTicks() && h.baseTimeTicks() > e.baseTimeTicks());
        assertTrue(n.stallLimitTicks() > h.stallLimitTicks() && h.stallLimitTicks() > e.stallLimitTicks());
        assertTrue(n.releaseGapRows() <= h.releaseGapRows() && h.releaseGapRows() <= e.releaseGapRows());
        assertTrue(n.releaseGapRows() < e.releaseGapRows());
        assertTrue(n.maxCrossers() >= h.maxCrossers() && h.maxCrossers() >= e.maxCrossers());
        assertTrue(n.maxCrossers() > e.maxCrossers());
        assertTrue(n.minReleaseSpacingTicks() < h.minReleaseSpacingTicks() && h.minReleaseSpacingTicks() < e.minReleaseSpacingTicks());
        assertTrue(n.shatterDelayMax() > h.shatterDelayMax() && h.shatterDelayMax() > e.shatterDelayMax());
        assertTrue(n.npcHesitationScale() > h.npcHesitationScale() && h.npcHesitationScale() > e.npcHesitationScale());
    }

    @Test
    void everyDifficultyIsInternallyConsistent() {
        for (Difficulty d : Difficulty.values()) {
            BridgeRules.Params q = p(d);
            assertTrue(q.shatterDelayMin() >= 6 && q.shatterDelayMax() <= 10, "suspense window 6..10 ticks");
            assertTrue(q.shatterDelayMin() <= q.shatterDelayMax());
            assertTrue(q.confirmTicks() > q.shatterDelayMax(), "a step is only 'seen holding' after the glass would have gone");
            assertTrue(q.stallWarnTicks() < q.stallLimitTicks());
            assertTrue(q.callLimitTicks() > q.stallLimitTicks(), "walking to the gate needs extra time");
            assertTrue(q.releaseGapRows() >= 1 && q.maxCrossers() >= 2);
        }
    }

    @Test
    void smallCrowdsGetTheDesignTimeAndBigCrowdsMoreOfIt() {
        for (Difficulty d : Difficulty.values()) {
            int base = p(d).baseTimeTicks();
            for (int n = 1; n <= 16; n++) {
                assertEquals(base, BridgeRules.timeLimitTicks(d, n), d + " n=" + n + ": the base time covers the 16 of the show");
            }
            int prev = base;
            for (int n = 17; n <= 128; n++) {
                int t = BridgeRules.timeLimitTicks(d, n);
                assertTrue(t >= prev, "more contestants never get less time");
                prev = t;
            }
        }
        assertTrue(BridgeRules.timeLimitTicks(Difficulty.NORMAL, 128) > p(Difficulty.NORMAL).baseTimeTicks(), "a big crowd needs more than the base time");
    }

    /** Ticks the gate needs per additional contestant at this difficulty (slope of {@link BridgeRules#neededTicks}). */
    private static int needPerContestant(Difficulty d) {
        return BridgeRules.neededTicks(d, 101) - BridgeRules.neededTicks(d, 100);
    }

    @Test
    void theClockAllowanceIsTheDifficultyLadderForCrowds() {
        // Normal grants clearly more time per queued contestant than the gate needs, Hard clearly less (the tail of a big
        // queue is cut off) and Extreme a fraction of it
        double normal = p(Difficulty.NORMAL).clockPerContestantTicks() / (double) needPerContestant(Difficulty.NORMAL);
        double hard = p(Difficulty.HARD).clockPerContestantTicks() / (double) needPerContestant(Difficulty.HARD);
        double extreme = p(Difficulty.EXTREME).clockPerContestantTicks() / (double) needPerContestant(Difficulty.EXTREME);
        assertTrue(normal > 1.4, "Normal: generous slack " + normal);
        assertTrue(hard > 0.4 && hard < 0.65, "Hard: about half of what the gate needs " + hard);
        assertTrue(extreme > 0.2 && extreme < 0.35, "Extreme: about a quarter of what the gate needs " + extreme);
        for (int n : new int[]{16, 24, 40, 64, 100, 128}) {
            assertTrue(BridgeRules.timeLimitTicks(Difficulty.NORMAL, n) >= BridgeRules.neededTicks(Difficulty.NORMAL, n),
                    "a well-playing Normal field of " + n + " always has the time it needs");
            assertTrue(BridgeRules.timeLimitTicks(Difficulty.NORMAL, n) >= BridgeRules.timeLimitTicks(Difficulty.HARD, n)
                    && BridgeRules.timeLimitTicks(Difficulty.HARD, n) >= BridgeRules.timeLimitTicks(Difficulty.EXTREME, n),
                    "harder never means more time (n=" + n + ")");
        }
        // a big crowd on Hard / Extreme really has less than it needs, a crowd of 16 has plenty
        assertTrue(BridgeRules.timeLimitTicks(Difficulty.HARD, 100) < BridgeRules.neededTicks(Difficulty.HARD, 100) * 0.8);
        assertTrue(BridgeRules.timeLimitTicks(Difficulty.EXTREME, 100) < BridgeRules.neededTicks(Difficulty.EXTREME, 100) * 0.5);
        for (Difficulty d : Difficulty.values()) {
            assertTrue(BridgeRules.timeLimitTicks(d, 16) > BridgeRules.neededTicks(d, 16), d + ": the 16 of the show have the time they need");
        }
    }

    @Test
    void theNeededTimeFollowsTheGateThroughput() {
        // fewer simultaneous crossers and longer call spacing (harder) mean a longer queue time per contestant
        assertTrue(needPerContestant(Difficulty.NORMAL) < needPerContestant(Difficulty.HARD)
                && needPerContestant(Difficulty.HARD) < needPerContestant(Difficulty.EXTREME));
        for (Difficulty d : Difficulty.values()) {
            BridgeRules.Params q = p(d);
            // a crossing takes at least ~30 s and only maxCrossers contestants are on the bridge at once
            assertTrue((long) needPerContestant(d) * q.maxCrossers() >= 30 * 20, d + ": the gate cannot be faster than its crossers allow");
            assertTrue(needPerContestant(d) >= q.minReleaseSpacingTicks(), d + ": never faster than the call spacing");
        }
    }

    @Test
    void jumpingOverAWholeRowIsRecognised() {
        // from the start platform the first row is row 0; row 1 would skip it
        assertFalse(BridgeRules.skipsRow(-1, 0));
        assertTrue(BridgeRules.skipsRow(-1, 1));
        // same row (the other lane) and the next row are fine, going back is not a skip
        assertFalse(BridgeRules.skipsRow(4, 4));
        assertFalse(BridgeRules.skipsRow(4, 5));
        assertFalse(BridgeRules.skipsRow(4, 2));
        assertTrue(BridgeRules.skipsRow(4, 6));
        assertTrue(BridgeRules.skipsRow(0, 17));
    }

    @Test
    void stallTimerCountsDownAndNeverGoesNegative() {
        BridgeRules.Params q = p(Difficulty.NORMAL);
        assertEquals(q.stallLimitTicks(), BridgeRules.stallRemaining(q, 1000, 1000));
        assertEquals(q.stallLimitTicks() - 100, BridgeRules.stallRemaining(q, 1000, 1100));
        assertEquals(0, BridgeRules.stallRemaining(q, 0, 100_000));
        assertEquals(q.stallLimitTicks(), BridgeRules.stallRemaining(q, 500, 100), "a clock that started later is clamped");
        assertFalse(BridgeRules.stallExpired(q, 0, q.stallLimitTicks() - 1));
        assertTrue(BridgeRules.stallExpired(q, 0, q.stallLimitTicks()));
    }

    @Test
    void stallWarningRisesAsTimeRunsOut() {
        BridgeRules.Params q = p(Difficulty.NORMAL);
        assertEquals(0, BridgeRules.stallWarning(q, q.stallLimitTicks()));
        assertEquals(0, BridgeRules.stallWarning(q, q.stallWarnTicks() + 1));
        assertEquals(1, BridgeRules.stallWarning(q, q.stallWarnTicks()));
        assertEquals(2, BridgeRules.stallWarning(q, q.stallWarnTicks() / 2));
        assertEquals(3, BridgeRules.stallWarning(q, 20));
        int last = 0;
        for (int rem = q.stallLimitTicks(); rem >= 0; rem--) {
            int w = BridgeRules.stallWarning(q, rem);
            assertTrue(w >= last, "warning level never drops while time runs out");
            last = w;
        }
    }

    @Test
    void stallPressureRunsFromZeroToOne() {
        BridgeRules.Params q = p(Difficulty.HARD);
        assertEquals(0.0, BridgeRules.stallPressure(q, q.stallLimitTicks()), 1e-9);
        assertEquals(1.0, BridgeRules.stallPressure(q, 0), 1e-9);
        assertEquals(0.5, BridgeRules.stallPressure(q, q.stallLimitTicks() / 2), 1e-9);
    }

    @Test
    void aFieldOfTwelveOrMorePlaysTheBareBridge() {
        for (Difficulty d : Difficulty.values()) {
            for (int n : new int[]{12, 13, 16, 40, 100, 128}) {
                assertEquals(0, BridgeRules.revealedRows(d, n, 18), d + " n=" + n);
                assertEquals(18, BridgeRules.unknownRows(d, n, 18));
            }
        }
        assertEquals(11, BridgeRules.MAX_FIELD_WITH_REVEAL);
    }

    @Test
    void aSmallFieldIsShownTheFirstRowsAndAlwaysKeepsSomethingToGambleOn() {
        for (Difficulty d : Difficulty.values()) {
            int prevUnknown = 0;
            for (int n = 1; n <= 11; n++) {
                int unknown = BridgeRules.unknownRows(d, n, 18);
                int shown = BridgeRules.revealedRows(d, n, 18);
                assertEquals(18, shown + unknown, d + " n=" + n);
                assertTrue(unknown >= 2, d + " n=" + n + ": at least two rows stay a gamble");
                assertTrue(unknown >= prevUnknown, d + ": a bigger field is shown fewer rows (n=" + n + ")");
                assertTrue(unknown <= 16 && shown >= 2, d + " n=" + n + ": a field of 11 is shown at least a little");
                prevUnknown = unknown;
            }
        }
    }

    @Test
    void harderDifficultiesAreShownAtLeastAsManyRowsForTheSameChanceOfCrossing() {
        // they lose a little chance to the faster shatter, the shorter stall limit and the edgier NPCs
        for (int n = 1; n <= 11; n++) {
            assertTrue(BridgeRules.revealedRows(Difficulty.HARD, n, 18) >= BridgeRules.revealedRows(Difficulty.NORMAL, n, 18), "n=" + n);
            assertTrue(BridgeRules.revealedRows(Difficulty.EXTREME, n, 18) >= BridgeRules.revealedRows(Difficulty.HARD, n, 18), "n=" + n);
        }
    }

    @Test
    void theRevealNeverExceedsAShorterBridge() {
        assertEquals(0, BridgeRules.revealedRows(Difficulty.NORMAL, 4, 4));
        assertEquals(1, BridgeRules.revealedRows(Difficulty.NORMAL, 4, 5));
        assertEquals(10, BridgeRules.revealedRows(Difficulty.EXTREME, 4, 13));
    }
}
