package com.squidgame.core.bridge;

import com.squidgame.core.Difficulty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Whole-game behaviour of the rules with NPC-like contestants (see {@link BridgeSim}). */
class BridgeSimTest {
    private static double mean(Difficulty d, int n, int games, java.util.function.ToDoubleFunction<BridgeSim.Result> f) {
        double sum = 0;
        for (int s = 0; s < games; s++) {
            sum += f.applyAsDouble(BridgeSim.run(1000 + s, d, n));
        }
        return sum / games;
    }

    @Test
    void everyContestantIsResolvedExactlyOnceAndTheGameAlwaysEnds() {
        for (Difficulty d : Difficulty.values()) {
            for (int n : new int[]{2, 3, 5, 16, 40, 100, 128}) {
                for (int s = 0; s < 6; s++) {
                    BridgeSim.Result r = BridgeSim.run(s * 31 + n, d, n);
                    assertEquals(n, r.total(), d + " n=" + n);
                    assertTrue(r.endTick() <= r.limitTicks(), "game never runs past its limit");
                    assertTrue(r.finished() + r.fell() + r.stalled() + r.timedOut() == n);
                }
            }
        }
    }

    @Test
    void theSimulationIsDeterministic() {
        for (Difficulty d : Difficulty.values()) {
            BridgeSim.Result a = BridgeSim.run(42, d, 24);
            BridgeSim.Result b = BridgeSim.run(42, d, 24);
            assertEquals(a.finished(), b.finished());
            assertEquals(a.endTick(), b.endTick());
            assertArrayEquals(a.fateByPosition(), b.fateByPosition());
        }
    }

    @Test
    void sixteenNpcsOnNormalLoseAboutHalfAsInTheShow() {
        double finished = mean(Difficulty.NORMAL, 16, 60, r -> r.finished());
        assertTrue(finished > 4.0 && finished < 11.0, "mean finishers of 16 on Normal: " + finished);
        double fell = mean(Difficulty.NORMAL, 16, 60, r -> r.fell());
        assertTrue(fell > 4.0, "plenty of drama: " + fell);
    }

    @Test
    void laterContestantsLearnFromEarlierOnes() {
        int games = 120;
        double early = 0, late = 0;
        for (int s = 0; s < games; s++) {
            BridgeSim.Result r = BridgeSim.run(500 + s, Difficulty.NORMAL, 20);
            for (int i = 0; i < 5; i++) {
                early += r.fateByPosition()[i] == BridgeSim.Fate.FINISHED.ordinal() ? 1 : 0;
                late += r.fateByPosition()[14 + i] == BridgeSim.Fate.FINISHED.ordinal() ? 1 : 0;
            }
        }
        double earlyRate = early / (games * 5), lateRate = late / (games * 5);
        assertTrue(earlyRate < 0.25, "the first five rarely make it: " + earlyRate);
        assertTrue(lateRate > earlyRate + 0.35, "the last five benefit from what they saw: early " + earlyRate + " late " + lateRate);
    }

    @Test
    void theFirstContestantsDecideEverythingWhenTheRouteIsStillUnknown() {
        // with 2 contestants nobody can cross an 18 row bridge by guessing: the game still ends cleanly
        int crossed = 0;
        for (int s = 0; s < 200; s++) {
            BridgeSim.Result r = BridgeSim.run(900 + s, Difficulty.NORMAL, 2);
            crossed += r.finished();
            assertEquals(2, r.total());
        }
        assertEquals(0, crossed, "two NPCs cannot guess 18 rows (or reveal enough of them)");
    }

    @Test
    void aLongQueueIsNotCutOffByItsScaledTimeLimitOnAnyDifficulty() {
        // the tail of a long queue must not lose to the clock merely for being long (the gate's throughput is
        // covered by the per-contestant allowance), on every difficulty
        for (Difficulty d : Difficulty.values()) {
            for (int n : new int[]{24, 40, 100}) {
                double finished = mean(d, n, 6, r -> r.finished());
                double timedOut = mean(d, n, 6, r -> r.timedOut());
                assertTrue(finished > n * 0.4, d + " " + n + " contestants: only " + finished + " crossed");
                assertTrue(timedOut < n * 0.05, d + " " + n + " contestants: " + timedOut + " ran out of time");
            }
        }
    }

    @Test
    void aHoleInTheGlassIsPlainToSeeEvenForContestantsWhoMissedEveryCrash() {
        BridgeSim.blind = true;
        BridgeSim.holeLandings = 0;
        BridgeSim.activeStalls = 0;
        try {
            int resolved = 0;
            for (int s = 0; s < 40; s++) {
                resolved += BridgeSim.run(300 + s, Difficulty.NORMAL, 30).total();
            }
            assertEquals(40 * 30, resolved);
            assertEquals(0, BridgeSim.activeStalls, "nobody waits for a hole to fill until the stall rule breaks the glass under them");
            assertEquals(0, BridgeSim.holeLandings, "nobody jumps into a hole");
        } finally {
            BridgeSim.blind = false;
        }
    }

    @Test
    void harderDifficultiesLeaveLessSlackInTheTimeLimit() {
        // the same crowd needs a larger share of the available time on Extreme than on Normal
        double normalUse = mean(Difficulty.NORMAL, 40, 8, r -> r.endTick() / (double) r.limitTicks());
        double extremeUse = mean(Difficulty.EXTREME, 40, 8, r -> r.endTick() / (double) r.limitTicks());
        assertTrue(extremeUse > normalUse, "Normal uses " + normalUse + " of its time, Extreme " + extremeUse);
    }

    @Test
    void aFrozenContestantNeverBlocksTheQueueForLongerThanTheStallLimit() {
        BridgeRules.Params p = BridgeRules.params(Difficulty.NORMAL);
        int maxFinished = 0;
        for (int s = 0; s < 30; s++) {
            // the 3rd contestant freezes on the first unrevealed row it reaches
            BridgeSim.Result frozen = BridgeSim.runWithFreezer(7000 + s, Difficulty.NORMAL, 30, 2);
            assertEquals(30, frozen.total());
            assertTrue(frozen.endTick() < frozen.limitTicks(), "everybody was still resolved in time");
            maxFinished = Math.max(maxFinished, frozen.finished());
            assertTrue(frozen.stalled() <= 30);
        }
        assertTrue(maxFinished > 10, "queue kept moving after the freezer (best run: " + maxFinished + ")");
        assertTrue(p.stallLimitTicks() > 0);
    }

    @Test
    void stalledPanelsDoNotKillTheContestantsWaitingBehind() {
        // waiting behind somebody must not cost stall time: stalls stay rare in a normal game
        double stalled = mean(Difficulty.NORMAL, 30, 40, r -> r.stalled());
        assertTrue(stalled < 3.0, "mean stalls per game " + stalled);
    }
}
