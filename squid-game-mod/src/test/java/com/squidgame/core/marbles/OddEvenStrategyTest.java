package com.squidgame.core.marbles;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.Personality.Archetype;
import com.squidgame.core.marbles.OddEvenRound.Reveal;
import com.squidgame.core.marbles.OddEvenStrategy.Call;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** NPC decisions for odd-or-even: legal, personality driven, pattern reading limited to revealed history. */
class OddEvenStrategyTest {
    private static Personality p(Archetype a, float courage, float reaction, float patience, float skill, float aggression,
                                 float cooperation, float risk) {
        return new Personality(a, courage, reaction, patience, skill, aggression, cooperation, risk);
    }

    private static final Personality NERVOUS = p(Archetype.NERVOUS, .2f, .5f, .5f, .4f, .15f, .6f, .15f);
    private static final Personality RECKLESS = p(Archetype.RECKLESS, .85f, .55f, .15f, .45f, .7f, .3f, .9f);
    private static final Personality CALCULATING = p(Archetype.CALCULATING, .6f, .65f, .85f, .85f, .3f, .5f, .35f);

    private static Reveal reveal(int round, Side holder, int hidden, Parity call, int wager) {
        boolean correct = Parity.of(hidden) == call;
        return new Reveal(round, holder, hidden, call, wager, correct, correct ? holder.other() : holder, wager);
    }

    @Test
    void callsWagersAndHoldsAreAlwaysLegal() {
        Rng rng = new Rng(11);
        for (Difficulty d : Difficulty.values()) {
            for (int i = 0; i < 3000; i++) {
                Personality person = Personality.generate(rng);
                int mine = 1 + rng.nextInt(14), theirs = 1 + rng.nextInt(14);
                List<Reveal> hist = new ArrayList<>();
                int rounds = rng.nextInt(8);
                for (int r = 0; r < rounds; r++) {
                    hist.add(reveal(r + 1, rng.nextBoolean() ? Side.A : Side.B, 1 + rng.nextInt(10),
                            rng.nextBoolean() ? Parity.ODD : Parity.EVEN, 1 + rng.nextInt(3)));
                }
                Call call = OddEvenStrategy.call(person, d, Side.A, theirs, hist, rng);
                assertNotNull(call.parity());
                assertTrue(call.confidence() >= 0 && call.confidence() <= 1);
                int w = OddEvenStrategy.wager(person, mine, theirs, call, 0, rng);
                assertTrue(w >= 1 && w <= Math.min(mine, theirs), "wager " + w + " of " + mine + "/" + theirs);
                assertEquals(1, OddEvenStrategy.wager(person, mine, theirs, call, 1, rng));
                int h = OddEvenStrategy.hold(person, d, Side.A, mine, hist, rng);
                assertTrue(h >= 1 && h <= mine, "hold " + h + " of " + mine);
                double think = OddEvenStrategy.thinkFraction(person, rng);
                assertTrue(think >= 0.05 && think <= 0.40);
            }
        }
    }

    @Test
    void aHolderWithOneMarbleCanOnlyHideAnOddNumber() {
        Rng rng = new Rng(2);
        int odd = 0;
        for (int i = 0; i < 400; i++) {
            if (OddEvenStrategy.call(CALCULATING, Difficulty.NORMAL, Side.A, 1, List.of(), rng).parity() == Parity.ODD) {
                odd++;
            }
            assertEquals(1, OddEvenStrategy.hold(CALCULATING, Difficulty.NORMAL, Side.B, 1, List.of(), rng));
        }
        assertTrue(odd > 380, "a skilled NPC knows a lone marble is odd: " + odd + "/400");
        assertEquals(0.5, OddEvenStrategy.uniformOddProbability(4), 1e-9);
        assertEquals(1.0, OddEvenStrategy.uniformOddProbability(1), 1e-9);
        assertEquals(0.6, OddEvenStrategy.uniformOddProbability(5), 1e-9);
    }

    /** Fraction of correct calls over {@code n} rounds when the NPC guesses against a holder following {@code holds}. */
    private static double hitRate(Personality npc, Difficulty d, IntSupplier holds, int rounds, long seed) {
        Rng rng = new Rng(seed);
        int hits = 0, total = 0;
        for (int trial = 0; trial < 400; trial++) {
            List<Reveal> hist = new ArrayList<>();
            for (int r = 1; r <= rounds; r++) {
                int hidden = holds.getAsInt();
                Call call = OddEvenStrategy.call(npc, d, Side.B, 10, hist, rng);
                Reveal rev = reveal(r, Side.A, hidden, call.parity(), 1);
                hist.add(rev);
                if (r > 4) { // judge once there is a history to read
                    total++;
                    if (rev.correct()) {
                        hits++;
                    }
                }
            }
        }
        return hits / (double) total;
    }

    @Test
    void skilledNpcsReadPredictableOpponentsButNotRandomOnes() {
        Rng opp = new Rng(99);
        // an opponent who always hides an odd number, one who strictly alternates, one who is random
        double constant = hitRate(CALCULATING, Difficulty.NORMAL, () -> 3, 12, 1);
        int[] k = {0};
        double alternating = hitRate(CALCULATING, Difficulty.NORMAL, () -> (k[0]++ % 2 == 0) ? 3 : 4, 12, 2);
        double random = hitRate(CALCULATING, Difficulty.NORMAL, () -> 1 + opp.nextInt(10), 12, 3);
        assertTrue(constant > 0.75, "constant odd: " + constant);
        assertTrue(alternating > 0.6, "alternating: " + alternating);
        assertTrue(random > 0.44 && random < 0.58, "against a random holder it is a coin flip: " + random);
        // a rookie reads less than an expert
        Personality rookie = p(Archetype.ROOKIE, .4f, .4f, .4f, .1f, .3f, .5f, .5f);
        double rookieConstant = hitRate(rookie, Difficulty.NORMAL, () -> 3, 12, 1);
        assertTrue(rookieConstant < constant);
        assertTrue(rookieConstant > 0.5, "even a rookie notices a constant opponent: " + rookieConstant);
    }

    @Test
    void harderDifficultiesMakeNpcsReadBetter() {
        Personality average = p(Archetype.VETERAN, .6f, .6f, .5f, .5f, .4f, .5f, .5f);
        double normal = hitRate(average, Difficulty.NORMAL, () -> 3, 12, 5);
        double extreme = hitRate(average, Difficulty.EXTREME, () -> 3, 12, 5);
        assertTrue(extreme > normal);
    }

    private static double meanWager(Personality person, int mine, int theirs, double confidence, long seed) {
        Rng rng = new Rng(seed);
        double sum = 0;
        for (int i = 0; i < 2000; i++) {
            sum += OddEvenStrategy.wager(person, mine, theirs, new Call(Parity.ODD, confidence), 0, rng);
        }
        return sum / 2000;
    }

    @Test
    void cautiousNpcsBetLittleAndGamblersGoBig() {
        double nervous = meanWager(NERVOUS, 10, 10, 0.6, 1);
        double reckless = meanWager(RECKLESS, 10, 10, 0.6, 1);
        assertTrue(nervous < 2.6, "cautious contestants bet one or two marbles, was " + nervous);
        assertTrue(reckless > 4.5, "gamblers bet big, was " + reckless);
        // everybody bets more when they are sure
        assertTrue(meanWager(RECKLESS, 10, 10, 0.95, 2) > meanWager(RECKLESS, 10, 10, 0.05, 2));
        // gamblers press their advantage when ahead and hold back when behind
        assertTrue(meanWager(RECKLESS, 14, 6, 0.6, 3) > meanWager(RECKLESS, 6, 14, 0.6, 3) + 1.0);
        // never more than the poorer stack
        assertTrue(meanWager(RECKLESS, 14, 3, 1.0, 4) <= 3.0);
    }

    @Test
    void thinkingTimeDependsOnThePersonality() {
        Personality patient = p(Archetype.CALCULATING, .6f, .3f, .95f, .8f, .3f, .5f, .35f);
        Personality hasty = p(Archetype.RECKLESS, .85f, .9f, .05f, .45f, .7f, .3f, .9f);
        Rng rng = new Rng(4);
        double a = 0, b = 0;
        for (int i = 0; i < 500; i++) {
            a += OddEvenStrategy.thinkFraction(patient, rng);
            b += OddEvenStrategy.thinkFraction(hasty, rng);
        }
        assertTrue(a / 500 > b / 500 * 2.0, "patient " + a / 500 + " vs hasty " + b / 500);
    }

    @Test
    void holdersFleeAReadablePatternOnlyWhenTheyAreSkilled() {
        // the opponent has called ODD every single time: a skilled holder hides EVEN far more often than not
        List<Reveal> hist = new ArrayList<>();
        for (int r = 1; r <= 8; r++) {
            hist.add(reveal(r * 2 - 1, Side.A, 2, Parity.ODD, 1));
        }
        Rng rng = new Rng(6);
        int even = 0, evenRookie = 0;
        Personality rookie = p(Archetype.ROOKIE, .4f, .4f, .4f, .05f, .3f, .5f, .5f);
        for (int i = 0; i < 1000; i++) {
            if (OddEvenStrategy.hold(CALCULATING, Difficulty.NORMAL, Side.A, 10, hist, rng) % 2 == 0) {
                even++;
            }
            if (OddEvenStrategy.hold(rookie, Difficulty.NORMAL, Side.A, 10, hist, rng) % 2 == 0) {
                evenRookie++;
            }
        }
        assertTrue(even > 600, "skilled holder counters the habit: " + even);
        assertTrue(even > evenRookie);
        // once the opponent has read the holder twice in a row the holder goes random again
        List<Reveal> read = new ArrayList<>(hist);
        read.add(reveal(20, Side.A, 4, Parity.EVEN, 1));
        read.add(reveal(21, Side.A, 6, Parity.EVEN, 1));
        int evenAfter = 0;
        for (int i = 0; i < 1000; i++) {
            if (OddEvenStrategy.hold(CALCULATING, Difficulty.NORMAL, Side.A, 10, read, rng) % 2 == 0) {
                evenAfter++;
            }
        }
        assertTrue(Math.abs(evenAfter - 500) < 150, "random again: " + evenAfter);
    }

    // ------------------------------------------------------------------ whole matches with NPCs on both sides

    private static OddEvenDuel playNpcMatch(Personality a, Personality b, Difficulty d, Rng rng) {
        MarblesRules.Params mp = MarblesRules.params(d);
        OddEvenDuel duel = new OddEvenDuel(mp.startMarbles(), rng.nextBoolean() ? Side.A : Side.B, mp.oddEvenRounds());
        int guard = 0;
        while (!duel.over()) {
            assertTrue(++guard < 50);
            OddEvenRound round = duel.round();
            Side holder = round.holder(), guesser = round.guesser();
            Personality ph = holder == Side.A ? a : b, pg = guesser == Side.A ? a : b;
            int hold = OddEvenStrategy.hold(ph, d, holder, duel.ledger().count(holder), duel.history(), rng);
            assertTrue(round.submitHold(holder, hold).accepted());
            Call call = OddEvenStrategy.call(pg, d, guesser, duel.ledger().count(holder), duel.history(), rng);
            int wager = OddEvenStrategy.wager(pg, duel.ledger().count(guesser), duel.ledger().count(holder), call,
                    round.wagerIsForced() ? 1 : 0, rng);
            assertTrue(round.submitGuess(guesser, wager, call.parity()).accepted());
            duel.resolveRound();
            assertEquals(2 * mp.startMarbles(), duel.ledger().total());
            duel.advance(false);
        }
        return duel;
    }

    @Test
    void npcMatchesAlwaysEndAndAreDeterministic() {
        for (Difficulty d : Difficulty.values()) {
            for (int seed = 0; seed < 150; seed++) {
                Rng r1 = new Rng(seed), r2 = new Rng(seed);
                Personality a = Personality.generate(new Rng(seed * 7L)), b = Personality.generate(new Rng(seed * 13L + 1));
                OddEvenDuel x = playNpcMatch(a, b, d, r1), y = playNpcMatch(a, b, d, r2);
                assertEquals(x.outcome(), y.outcome());
                assertEquals(x.history().size(), y.history().size());
                assertTrue(x.history().size() <= MarblesRules.params(d).oddEvenRounds() + 1);
            }
        }
    }

    @Test
    void aHundredAndTwentyEightContestantsPlaySixtyFourMatchesToCompletion() {
        Rng rng = new Rng(2024);
        List<Integer> nums = new ArrayList<>();
        List<Personality> people = new ArrayList<>();
        for (int i = 0; i < 128; i++) {
            nums.add(i + 1);
            people.add(Personality.generate(rng.fork(i)));
        }
        PairingPlanner.Plan plan = PairingPlanner.complete(nums, java.util.Map.of(), rng);
        assertEquals(64, plan.pairs().size());
        int survivors = 0;
        for (int[] pair : plan.pairs()) {
            OddEvenDuel d = playNpcMatch(people.get(pair[0] - 1), people.get(pair[1] - 1), Difficulty.NORMAL, rng.fork(pair[0]));
            assertTrue(d.over());
            survivors++;
        }
        assertEquals(64, survivors);
    }

    @Test
    void skilledPlayersBeatRookiesMoreOftenThanChance() {
        Personality rookie = p(Archetype.ROOKIE, .4f, .4f, .4f, .05f, .3f, .5f, .5f);
        int wins = 0, n = 400;
        for (int seed = 0; seed < n; seed++) {
            OddEvenDuel d = playNpcMatch(CALCULATING, rookie, Difficulty.NORMAL, new Rng(seed));
            if (d.outcome().winner() == Side.A) {
                wins++;
            }
        }
        // odd-or-even is mostly luck: skill gives an edge, not a guarantee
        assertTrue(wins > 0.5 * n, "skill is worth something: " + wins + "/" + n);
        assertTrue(wins < 0.8 * n, "but luck dominates: " + wins + "/" + n);
    }
}
