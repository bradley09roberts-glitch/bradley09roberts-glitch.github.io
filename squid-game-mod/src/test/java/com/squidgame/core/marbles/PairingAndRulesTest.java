package com.squidgame.core.marbles;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.Personality.Archetype;
import com.squidgame.core.marbles.MarblesRules.Params;
import com.squidgame.core.marbles.MarblesRules.Variant;
import com.squidgame.core.marbles.MarblesRules.VariantSetting;
import com.squidgame.core.marbles.PairingPlanner.Candidate;
import com.squidgame.core.marbles.PairingPlanner.Plan;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The pairing planner and the difficulty table. */
class PairingAndRulesTest {
    private static List<Integer> numbers(int n) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(11 + 3 * i);
        }
        return l;
    }

    private static void assertValid(List<Integer> numbers, Plan plan) {
        Set<Integer> seen = new HashSet<>();
        for (int[] p : plan.pairs()) {
            assertEquals(2, p.length);
            assertTrue(p[0] < p[1], "smaller number first");
            assertTrue(seen.add(p[0]) && seen.add(p[1]), "nobody plays two matches");
        }
        if (plan.hasBye()) {
            assertTrue(seen.add(plan.bye()), "the bye is not also paired");
        }
        assertEquals(new HashSet<>(numbers), seen, "everybody is either paired or has the bye");
        assertEquals(numbers.size() % 2 == 1, plan.hasBye(), "a bye exactly when the count is odd");
    }

    @Test
    void everyoneIsPairedForAnySizeFromTwoTo128() {
        for (int n = 2; n <= 128; n++) {
            List<Integer> nums = numbers(n);
            assertValid(nums, PairingPlanner.complete(nums, Map.of(), new Rng(n)));
        }
    }

    @Test
    void agreedPairsAreKeptAndBrokenAgreementsAreIgnored() {
        List<Integer> nums = numbers(9);
        Map<Integer, Integer> locked = new HashMap<>();
        locked.put(11, 14);
        locked.put(14, 11);
        locked.put(17, 20);
        locked.put(20, 17);
        locked.put(23, 26); // not mutual: ignored
        locked.put(29, 29); // self: ignored
        locked.put(32, 999); // unknown partner: ignored
        for (int seed = 0; seed < 50; seed++) {
            Plan plan = PairingPlanner.complete(nums, locked, new Rng(seed));
            assertValid(nums, plan);
            boolean a = false, b = false;
            for (int[] p : plan.pairs()) {
                a |= p[0] == 11 && p[1] == 14;
                b |= p[0] == 17 && p[1] == 20;
            }
            assertTrue(a && b, "agreed pairs survive");
            assertTrue(plan.hasBye());
            assertNotEquals(11, plan.bye());
            assertNotEquals(14, plan.bye());
            assertNotEquals(17, plan.bye());
            assertNotEquals(20, plan.bye());
        }
    }

    @Test
    void completeIsDeterministicForTheSeedAndVariesWithIt() {
        List<Integer> nums = numbers(40);
        String a = describe(PairingPlanner.complete(nums, Map.of(), new Rng(5)));
        assertEquals(a, describe(PairingPlanner.complete(nums, Map.of(), new Rng(5))));
        assertNotEquals(a, describe(PairingPlanner.complete(nums, Map.of(), new Rng(6))));
    }

    private static String describe(Plan p) {
        StringBuilder sb = new StringBuilder();
        for (int[] pair : p.pairs()) {
            sb.append(pair[0]).append('-').append(pair[1]).append(' ');
        }
        return sb.append("bye ").append(p.bye()).toString();
    }

    @Test
    void theByeIsSpreadOverAllLeftovers() {
        List<Integer> nums = numbers(7);
        Set<Integer> byes = new HashSet<>();
        for (int seed = 0; seed < 300; seed++) {
            byes.add(PairingPlanner.complete(nums, Map.of(), new Rng(seed)).bye());
        }
        assertEquals(7, byes.size(), "every contestant can be the odd one out");
    }

    @Test
    void cooperativeNpcsAcceptMoreOftenThanLoners() {
        Personality team = new Personality(Archetype.TEAM_PLAYER, .55f, .55f, .6f, .55f, .25f, .95f, .4f);
        Personality loner = new Personality(Archetype.LONE_WOLF, .65f, .6f, .5f, .65f, .5f, .1f, .55f);
        for (double dist : new double[]{1, 6, 15}) {
            assertTrue(PairingPlanner.acceptChance(team, dist, false) > PairingPlanner.acceptChance(loner, dist, false) + 0.3);
        }
        assertTrue(PairingPlanner.acceptChance(team, 3, false) > PairingPlanner.acceptChance(team, 3, true));
        assertTrue(PairingPlanner.acceptChance(team, 1, false) > PairingPlanner.acceptChance(team, 12, false));
        Rng r = new Rng(3);
        for (int i = 0; i < 500; i++) {
            Personality p = Personality.generate(r);
            double c = PairingPlanner.acceptChance(p, r.range(0, 30), r.nextBoolean());
            assertTrue(c >= 0.06 && c <= 0.97);
        }
    }

    @Test
    void mostNpcsFindAPartnerOnTheirOwnInTheTimeAvailable() {
        // an unbiased population: the average accept chance must be high enough that the square pairs up by itself
        Rng r = new Rng(8);
        double sum = 0;
        for (int i = 0; i < 2000; i++) {
            sum += PairingPlanner.acceptChance(Personality.generate(r), 5, false);
        }
        assertTrue(sum / 2000 > 0.55 && sum / 2000 < 0.9, "mean accept chance " + sum / 2000);
    }

    @Test
    void pickTargetPrefersTheNearestAndSkipsThoseWhoDeclined() {
        List<Candidate> list = List.of(new Candidate(1, 9, false), new Candidate(2, 2, true), new Candidate(3, 4, false),
                new Candidate(4, 30, false));
        int near = 0;
        for (int seed = 0; seed < 200; seed++) {
            int i = PairingPlanner.pickTarget(list, new Rng(seed));
            assertNotEquals(1, i, "the decliner at index 1 is never asked again");
            if (i == 2) {
                near++;
            }
        }
        assertTrue(near > 150, "the nearest willing contestant is usually chosen");
        assertEquals(-1, PairingPlanner.pickTarget(List.of(), new Rng(1)));
        assertEquals(-1, PairingPlanner.pickTarget(List.of(new Candidate(5, 1, true)), new Rng(1)));
    }

    @Test
    void cooperativeNpcsStartLookingForAPartnerSooner() {
        Personality team = new Personality(Archetype.TEAM_PLAYER, .55f, .55f, .6f, .55f, .25f, .95f, .4f);
        Personality loner = new Personality(Archetype.LONE_WOLF, .65f, .6f, .5f, .65f, .5f, .1f, .55f);
        double t = 0, l = 0;
        Rng r = new Rng(2);
        for (int i = 0; i < 400; i++) {
            double ft = PairingPlanner.firstProposalFraction(team, r), fl = PairingPlanner.firstProposalFraction(loner, r);
            assertTrue(ft > 0 && ft < 0.5 && fl > 0 && fl < 0.5);
            t += ft;
            l += fl;
        }
        assertTrue(t < l);
        assertTrue(PairingPlanner.answerDelayTicks(team, r) >= 20);
    }

    // ------------------------------------------------------------------ difficulty table

    @Test
    void harderDifficultiesMeanFewerMarblesLessTimeAndHarderThrows() {
        Params n = MarblesRules.params(Difficulty.NORMAL), h = MarblesRules.params(Difficulty.HARD),
                e = MarblesRules.params(Difficulty.EXTREME);
        assertEquals(10, n.startMarbles());
        assertEquals(8, h.startMarbles());
        assertEquals(5, e.startMarbles());
        assertTrue(n.totalSeconds() > h.totalSeconds() && h.totalSeconds() > e.totalSeconds());
        assertEquals(210, n.totalSeconds());
        assertEquals(180, h.totalSeconds());
        assertEquals(150, e.totalSeconds());
        assertTrue(n.pairingSeconds() > h.pairingSeconds() && h.pairingSeconds() > e.pairingSeconds());
        assertTrue(n.decisionSeconds() > h.decisionSeconds() && h.decisionSeconds() > e.decisionSeconds());
        assertTrue(n.throwSeconds() > h.throwSeconds() && h.throwSeconds() > e.throwSeconds());
        assertTrue(n.oddEvenRounds() > h.oddEvenRounds() && h.oddEvenRounds() > e.oddEvenRounds());
        assertTrue(n.throwRounds() > h.throwRounds() && h.throwRounds() > e.throwRounds());
        assertTrue(n.stakeCap() > h.stakeCap() && h.stakeCap() > e.stakeCap());
        assertTrue(n.throwModel().maxChargeTicks() > h.throwModel().maxChargeTicks()
                && h.throwModel().maxChargeTicks() > e.throwModel().maxChargeTicks());
        assertTrue(n.throwModel().jitterYawDeg() < h.throwModel().jitterYawDeg()
                && h.throwModel().jitterYawDeg() < e.throwModel().jitterYawDeg());
        assertTrue(n.throwModel().jitterSpeed() < h.throwModel().jitterSpeed()
                && h.throwModel().jitterSpeed() < e.throwModel().jitterSpeed());
    }

    @Test
    void theOvertimeReserveCoversTheWorstCaseTieBreak() {
        for (Difficulty d : Difficulty.values()) {
            Params p = MarblesRules.params(d);
            assertTrue(p.overtimeReserveSeconds() >= p.worstCaseOvertimeSeconds(),
                    d + ": reserve " + p.overtimeReserveSeconds() + " < worst case " + p.worstCaseOvertimeSeconds());
            // the matches themselves must get a decent share of the clock
            double matchTime = p.totalSeconds() - p.pairingSeconds() - p.introSeconds() - p.overtimeReserveSeconds();
            assertTrue(matchTime >= 70, d + ": only " + matchTime + " s for the matches");
            // a full round has to fit its decision timer
            assertTrue(p.decisionSeconds() + p.revealSeconds() + p.resultSeconds() < matchTime);
        }
    }

    @Test
    void variantSettingParsesAliasesAndFallsBackToMixed() {
        assertEquals(VariantSetting.ODD_EVEN, MarblesRules.parseVariant("odd_even"));
        assertEquals(VariantSetting.ODD_EVEN, MarblesRules.parseVariant(" Odd-Even "));
        assertEquals(VariantSetting.THROW, MarblesRules.parseVariant("throw"));
        assertEquals(VariantSetting.THROW, MarblesRules.parseVariant("target_throw"));
        assertEquals(VariantSetting.MIXED, MarblesRules.parseVariant("mixed"));
        assertEquals(VariantSetting.MIXED, MarblesRules.parseVariant("nonsense"));
        assertEquals(VariantSetting.MIXED, MarblesRules.parseVariant(null));
    }

    @Test
    void mixedGivesBothVariantsFixedSettingsDoNot() {
        Rng base = new Rng(1234);
        int oddEven = 0;
        for (int k = 0; k < 64; k++) {
            assertEquals(Variant.ODD_EVEN, MarblesRules.variantFor(VariantSetting.ODD_EVEN, new Rng(k)));
            assertEquals(Variant.THROW, MarblesRules.variantFor(VariantSetting.THROW, new Rng(k)));
            if (MarblesRules.variantFor(VariantSetting.MIXED, base.fork(k)) == Variant.ODD_EVEN) {
                oddEven++;
            }
        }
        assertTrue(oddEven > 16 && oddEven < 48, "a mixed field plays both: " + oddEven + "/64 odd-even");
        // deterministic per pair
        assertEquals(MarblesRules.variantFor(VariantSetting.MIXED, new Rng(7)), MarblesRules.variantFor(VariantSetting.MIXED, new Rng(7)));
    }
}
