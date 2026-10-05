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
    void theClockMakesTheDifficultyLadderForBigCrowds() {
        // a well-playing NPC crowd: on Normal most get across and the clock never cuts anybody off, on Hard about half
        // get across (the tail of the queue is cut off), on Extreme few do
        for (int n : new int[]{40, 100}) {
            double normal = mean(Difficulty.NORMAL, n, 8, r -> r.finished()) / n;
            double hard = mean(Difficulty.HARD, n, 8, r -> r.finished()) / n;
            double extreme = mean(Difficulty.EXTREME, n, 8, r -> r.finished()) / n;
            assertTrue(normal > 0.6, n + " on Normal: " + normal);
            assertTrue(hard > 0.3 && hard < 0.6, n + " on Hard: " + hard);
            assertTrue(extreme < 0.35, n + " on Extreme: " + extreme);
            assertTrue(normal > hard + 0.1 && hard > extreme + 0.1, n + ": a clear ladder " + normal + " > " + hard + " > " + extreme);
            assertEquals(0.0, mean(Difficulty.NORMAL, n, 8, r -> r.timedOut()), 1e-9, "the Normal clock never cuts anybody off");
        }
    }

    @Test
    void theSixteenOfTheShowHaveEnoughTimeOnEveryDifficulty() {
        for (Difficulty d : Difficulty.values()) {
            double timedOut = mean(d, 16, 40, r -> r.timedOut());
            assertTrue(timedOut < 0.5, d + ": the clock hardly ever cuts off a crowd of 16 (" + timedOut + " on average)");
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

    private static double[] smallField(Difficulty d, int n, int games) {
        double crossed = 0, anybody = 0;
        for (int s = 0; s < games; s++) {
            BridgeSim.Result r = BridgeSim.runAsPlayed(52000 + s, d, n);
            assertEquals(n, r.total());
            crossed += r.finished();
            anybody += r.finished() > 0 ? 1 : 0;
        }
        return new double[]{crossed / games / n, anybody / games};
    }

    @Test
    void aSmallFieldGetsSomebodyAcrossOnEveryDifficultyThanksToTheShownRows() {
        // 4..11 contestants: at least 70 % of the games get somebody across, and the share of the field that crosses is
        // roughly 20-45 % (Extreme a little lower); 3 contestants are not sent to the bridge but must still work
        for (Difficulty d : Difficulty.values()) {
            for (int n : new int[]{4, 6, 8, 11}) {
                double[] r = smallField(d, n, 200);
                assertTrue(r[1] >= 0.70, d + " n=" + n + ": somebody crosses in only " + r[1]);
                assertTrue(r[0] >= 0.14 && r[0] <= 0.50, d + " n=" + n + ": share across " + r[0]);
            }
            double[] three = smallField(d, 3, 200);
            assertTrue(three[1] >= 0.65, d + " n=3: somebody crosses in only " + three[1]);
        }
    }

    @Test
    void withoutTheShownRowsASmallFieldWouldMostlyEndWithNobodyAcross() {
        // the reason for the rule: the bare bridge needs about ten fallers to reveal itself
        for (Difficulty d : Difficulty.values()) {
            double any = 0;
            for (int s = 0; s < 150; s++) {
                any += BridgeSim.run(52000 + s, d, 6).finished() > 0 ? 1 : 0;
            }
            assertTrue(any / 150 < 0.35, d + ": the bare bridge gets somebody across a field of 6 in " + any / 150);
        }
    }

    @Test
    void theSmallFieldLadderFollowsTheDifficulty() {
        double normal = 0, hard = 0, extreme = 0;
        int[] ns = {4, 6, 8, 11};
        for (int n : ns) {
            normal += smallField(Difficulty.NORMAL, n, 150)[0];
            hard += smallField(Difficulty.HARD, n, 150)[0];
            extreme += smallField(Difficulty.EXTREME, n, 150)[0];
        }
        assertTrue(normal > hard && hard > extreme - 0.02, "share across Normal " + normal / ns.length + " Hard " + hard / ns.length + " Extreme " + extreme / ns.length);
    }

    @Test
    void shownRowsAreNeverGambledOn() {
        // a gamble death needs a row nobody knew: with the first rows shown there are at most U of them per game, and the
        // falls on rows everybody knew (a slip of nerve, a missed event) stay rare
        BridgeSim.avoidableDeaths = 0;
        BridgeSim.gambleDeaths = 0;
        int games = 100;
        for (int s = 0; s < games; s++) {
            BridgeSim.runAsPlayed(77000 + s, Difficulty.NORMAL, 6);
        }
        int unknown = BridgeRules.unknownRows(Difficulty.NORMAL, 6, 18);
        assertEquals(7, unknown);
        assertTrue(BridgeSim.gambleDeaths <= games * (long) unknown, "at most one gamble per unknown row: " + BridgeSim.gambleDeaths);
        double perGame = BridgeSim.gambleDeaths / (double) games;
        assertTrue(perGame > unknown / 2.0 - 1.0 && perGame < unknown / 2.0 + 1.0, "about half of the unknown rows kill: " + perGame);
        assertTrue(BridgeSim.avoidableDeaths < games * 0.6, "falls on rows everybody knew are rare: " + BridgeSim.avoidableDeaths);
    }
}
