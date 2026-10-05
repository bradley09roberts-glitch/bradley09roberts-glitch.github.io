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
    void timeGrowsWithTheNumberOfContestantsOnlyAboveTheFreeCount() {
        for (Difficulty d : Difficulty.values()) {
            int base = p(d).baseTimeTicks();
            assertEquals(base, BridgeRules.timeLimitTicks(d, 2));
            assertEquals(base, BridgeRules.timeLimitTicks(d, 16));
            int prev = base;
            for (int n = 17; n <= 128; n++) {
                int t = BridgeRules.timeLimitTicks(d, n);
                assertTrue(t > prev, "one more contestant needs more time");
                prev = t;
            }
        }
    }

    @Test
    void theAllowancePerExtraContestantCoversWhatTheGateNeedsAtThatDifficulty() {
        // a crossing takes 35-45 s and only maxCrossers contestants are on the bridge at once, so the gate needs
        // crossing / maxCrossers per contestant: fewer simultaneous crossers (harder) means more time per head
        BridgeRules.Params n = p(Difficulty.NORMAL), h = p(Difficulty.HARD), e = p(Difficulty.EXTREME);
        assertTrue(n.extraTicksPerContestant() < h.extraTicksPerContestant() && h.extraTicksPerContestant() < e.extraTicksPerContestant());
        for (Difficulty d : Difficulty.values()) {
            BridgeRules.Params q = p(d);
            assertTrue((long) q.extraTicksPerContestant() * q.maxCrossers() >= 35 * 20,
                    d + ": the allowance must cover a crossing shared between the simultaneous crossers");
        }
        // ... and the base time is what makes the harder difficulties tighter
        assertTrue(BridgeRules.timeLimitTicks(Difficulty.NORMAL, 16) > BridgeRules.timeLimitTicks(Difficulty.HARD, 16));
        assertTrue(BridgeRules.timeLimitTicks(Difficulty.HARD, 16) > BridgeRules.timeLimitTicks(Difficulty.EXTREME, 16));
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
}
