package com.squidgame.core.bridge;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BridgeNpcRulesTest {
    private static final BridgeRules.Params NORMAL = BridgeRules.params(Difficulty.NORMAL);

    private static Personality pers(float courage, float skill, float patience, float risk) {
        return new Personality(Personality.Archetype.ROOKIE, courage, 0.5f, patience, skill, 0.3f, 0.5f, risk);
    }

    private static List<Personality> crowd(int n, long seed) {
        Rng r = new Rng(seed);
        List<Personality> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(Personality.generate(r.fork(i)));
        }
        return l;
    }

    @Test
    void hesitationNeverReachesTheStallLimitAndStaysPositive() {
        Rng rng = new Rng(1);
        for (Difficulty d : Difficulty.values()) {
            BridgeRules.Params p = BridgeRules.params(d);
            for (Personality pe : crowd(300, 5)) {
                for (double fear : new double[]{0, 0.5, 1}) {
                    int t = BridgeNpcRules.hesitationTicks(pe, p, fear, rng);
                    assertTrue(t >= 10, "at least half a second");
                    assertTrue(t <= p.stallLimitTicks() * 0.6 + 1, "a normal NPC must not dither into the stall limit: " + t);
                }
            }
        }
    }

    @Test
    void cautiousNpcsHesitateLongerThanRecklessOnes() {
        Personality reckless = pers(0.9f, 0.5f, 0.1f, 0.95f);
        Personality nervous = pers(0.15f, 0.4f, 0.8f, 0.1f);
        Rng rng = new Rng(2);
        double r = 0, n = 0;
        for (int i = 0; i < 500; i++) {
            r += BridgeNpcRules.hesitationTicks(reckless, NORMAL, 0, rng);
            n += BridgeNpcRules.hesitationTicks(nervous, NORMAL, 0, rng);
        }
        assertTrue(n > r * 2.0, "nervous " + n / 500 + " vs reckless " + r / 500);
    }

    @Test
    void fearAndDifficultyChangeHesitation() {
        Personality p = pers(0.5f, 0.5f, 0.5f, 0.5f);
        double calm = 0, scared = 0, extreme = 0;
        Rng rng = new Rng(3);
        for (int i = 0; i < 500; i++) {
            calm += BridgeNpcRules.hesitationTicks(p, NORMAL, 0, rng);
            scared += BridgeNpcRules.hesitationTicks(p, NORMAL, 1, rng);
            extreme += BridgeNpcRules.hesitationTicks(p, BridgeRules.params(Difficulty.EXTREME), 0, rng);
        }
        assertTrue(scared > calm * 1.3, "fear slows contestants down");
        assertTrue(extreme < calm, "harder difficulties give faster NPCs");
    }

    @Test
    void slipsGetMoreLikelyUnderPressureAndFear() {
        Personality p = pers(0.5f, 0.5f, 0.5f, 0.5f);
        double calm = BridgeNpcRules.slipChance(p, Difficulty.NORMAL, 0, 0);
        double pressed = BridgeNpcRules.slipChance(p, Difficulty.NORMAL, 1, 0);
        double afraid = BridgeNpcRules.slipChance(p, Difficulty.NORMAL, 0, 1);
        assertTrue(pressed > calm * 2);
        assertTrue(afraid > calm);
        assertTrue(calm > 0 && pressed < 0.35 + 1e-9);
    }

    @Test
    void skilledAndCourageousNpcsSlipLess() {
        double clumsy = BridgeNpcRules.slipChance(pers(0.3f, 0.2f, 0.5f, 0.5f), Difficulty.NORMAL, 0.5, 0.2);
        double sharp = BridgeNpcRules.slipChance(pers(0.8f, 0.9f, 0.5f, 0.5f), Difficulty.NORMAL, 0.5, 0.2);
        assertTrue(sharp < clumsy / 2);
        Personality p = pers(0.5f, 0.4f, 0.5f, 0.5f);
        double normal = BridgeNpcRules.slipChance(p, Difficulty.NORMAL, 0.5, 0.2);
        double hard = BridgeNpcRules.slipChance(p, Difficulty.HARD, 0.5, 0.2);
        double extreme = BridgeNpcRules.slipChance(p, Difficulty.EXTREME, 0.5, 0.2);
        assertTrue(normal < hard && hard < extreme, "the harder difficulties' nerve scale outweighs their NPC skill bonus");
        assertTrue(extreme > normal * 4, "Extreme contestants lose their nerve far more often: " + extreme + " vs " + normal);
    }

    @Test
    void onlyVeryLowCourageFreezes() {
        Rng rng = new Rng(4);
        Personality brave = pers(0.7f, 0.5f, 0.5f, 0.5f);
        Personality scared = pers(0.05f, 0.3f, 0.5f, 0.2f);
        int braveFreezes = 0, scaredFreezes = 0, scaredKnown = 0;
        for (int i = 0; i < 5000; i++) {
            braveFreezes += BridgeNpcRules.freezes(brave, Difficulty.NORMAL, true, rng) ? 1 : 0;
            scaredFreezes += BridgeNpcRules.freezes(scared, Difficulty.NORMAL, true, rng) ? 1 : 0;
            scaredKnown += BridgeNpcRules.freezes(scared, Difficulty.NORMAL, false, rng) ? 1 : 0;
        }
        assertEquals(0, braveFreezes);
        assertTrue(scaredFreezes > 300 && scaredFreezes < 1500, "scared freeze " + scaredFreezes);
        assertTrue(scaredKnown < scaredFreezes / 3, "a revealed row is much less frightening");
    }

    @Test
    void noPersonalityBeatsACoinFlipOnAnUnknownRow() {
        // the route is uniform: whatever lane preference an NPC has, its success rate on a fresh row is 50 %
        int hits = 0, tries = 0;
        Rng rng = new Rng(11);
        for (Personality p : crowd(200, 77)) {
            int prev = -1;
            for (int i = 0; i < 100; i++) {
                BridgeRoute route = BridgeRoute.forGame(rng.nextInt(10_000_000), i, 18);
                int lane = BridgeNpcRules.guessLane(p, prev, rng);
                prev = lane;
                hits += route.isSafe(i % 18, lane) ? 1 : 0;
                tries++;
            }
        }
        assertEquals(0.5, hits / (double) tries, 0.02);
    }

    @Test
    void laneGuessesLookDifferentPerPersonalityButAreNotDegenerate() {
        Rng rng = new Rng(8);
        double min = 1, max = 0;
        for (Personality p : crowd(300, 21)) {
            int ones = 0;
            for (int i = 0; i < 400; i++) {
                ones += BridgeNpcRules.guessLane(p, -1, rng);
            }
            double f = ones / 400.0;
            min = Math.min(min, f);
            max = Math.max(max, f);
            assertTrue(f > 0.2 && f < 0.8, "no contestant always picks the same side: " + f);
        }
        assertTrue(max - min > 0.2, "personalities should differ in their preferred side (" + min + ".." + max + ")");
    }

    @Test
    void superstitiousContestantsRepeatOrAlternate() {
        Rng rng = new Rng(9);
        double repeat = 0, alternate = 0;
        int nRep = 0, nAlt = 0;
        for (Personality p : crowd(400, 31)) {
            double s = BridgeNpcRules.superstition(p);
            if (Math.abs(s) < 0.6) {
                continue;
            }
            int same = 0;
            for (int i = 0; i < 300; i++) {
                int prev = rng.nextInt(2);
                same += BridgeNpcRules.guessLane(p, prev, rng) == prev ? 1 : 0;
            }
            if (s > 0) {
                repeat += same / 300.0;
                nRep++;
            } else {
                alternate += same / 300.0;
                nAlt++;
            }
        }
        assertTrue(nRep > 5 && nAlt > 5);
        assertTrue(repeat / nRep > alternate / nAlt + 0.1);
    }

    @Test
    void walkSpeedAndSettleTimeStayInHumanRanges() {
        Rng rng = new Rng(12);
        for (Personality p : crowd(300, 41)) {
            double v = BridgeNpcRules.walkSpeed(p);
            assertTrue(v >= 0.07 - 1e-9 && v <= 0.115 + 1e-9);
            int s = BridgeNpcRules.settleTicks(p, rng);
            assertTrue(s >= 5 && s <= 20);
            assertTrue(BridgeNpcRules.callReactionTicks(p, Difficulty.NORMAL, rng) >= 5);
        }
    }

    @Test
    void attentionIsHighButNotPerfectForTheUnskilled() {
        double low = BridgeNpcRules.attention(pers(0.5f, 0.05f, 0.5f, 0.5f), Difficulty.NORMAL);
        double high = BridgeNpcRules.attention(pers(0.5f, 0.95f, 0.5f, 0.5f), Difficulty.EXTREME);
        assertTrue(low >= 0.9 && low < 1.0);
        assertEquals(1.0, high, 1e-9);
    }
}
