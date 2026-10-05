package com.squidgame.core.tug;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TugRulesTest {
    @Test
    void harderDifficultiesChangeEveryNumberTheRightWay() {
        TugRules.Params n = TugRules.params(Difficulty.NORMAL);
        TugRules.Params h = TugRules.params(Difficulty.HARD);
        TugRules.Params e = TugRules.params(Difficulty.EXTREME);
        assertEquals(100 * 20, n.heatLimitTicks());
        assertEquals(85 * 20, h.heatLimitTicks());
        assertEquals(70 * 20, e.heatLimitTicks());
        assertTrue(n.window() > h.window() && h.window() > e.window(), "the heave window shrinks");
        assertEquals(3.0, n.window(), 1e-12);
        assertTrue(n.costScale() < h.costScale() && h.costScale() < e.costScale(), "stamina costs grow");
        assertTrue(n.npcSkillBonus() < h.npcSkillBonus() && h.npcSkillBonus() < e.npcSkillBonus(), "opposing NPCs improve");
        assertTrue(n.drag() > h.drag() && h.drag() > e.drag(), "tired teams get a lighter rope");
        assertEquals(10 * 20, n.suddenDeathTicks());
    }

    @Test
    void theGameTimeLimitGrowsWithTheNumberOfHeatsAndShrinksWithTheDifficulty() {
        TugRules.Params n = TugRules.params(Difficulty.NORMAL);
        assertEquals(2 * TugRules.totalTicks(n, 1), TugRules.totalTicks(n, 2));
        assertTrue(TugRules.totalTicks(n, 1) > TugRules.totalTicks(TugRules.params(Difficulty.HARD), 1));
        assertTrue(TugRules.totalTicks(TugRules.params(Difficulty.HARD), 1) > TugRules.totalTicks(TugRules.params(Difficulty.EXTREME), 1));
        assertTrue(TugRules.totalTicks(n, 1) > n.heatLimitTicks() + TugRules.FALL_TICKS, "room for the intro, the fall and a sudden death");
        assertEquals(TugRules.totalTicks(n, 1), TugRules.totalTicks(n, 0), "at least one heat is always budgeted");
    }

    @Test
    void heatsFollowTheArenaCapacity() {
        assertEquals(1, TugRules.heatsFor(2));
        assertEquals(1, TugRules.heatsFor(64));
        assertEquals(2, TugRules.heatsFor(65));
        assertEquals(2, TugRules.heatsFor(128));
        assertEquals(3, TugRules.heatsFor(129));
        assertEquals(TugRules.MAX_PER_TEAM * 2, TugRules.MAX_HEAT);
    }

    @Test
    void heaveQualityIsFullOnTheBeatAndFallsToTheEdgeOfTheWindow() {
        double window = 3.0;
        assertEquals(1.0, TugRules.heaveQuality(0, window), 1e-12);
        double previous = 1.0;
        for (double err = 0.5; err <= window; err += 0.5) {
            double q = TugRules.heaveQuality(err, window);
            assertTrue(q < previous && q > 0, "error " + err);
            previous = q;
        }
        assertEquals(0.2, TugRules.heaveQuality(window, window), 1e-12);
        assertEquals(0.0, TugRules.heaveQuality(window + 0.01, window), 0.0);
        assertEquals(0.0, TugRules.heaveQuality(50, window), 0.0);
        // a narrower window is a harder rhythm: the same error is worth less
        assertTrue(TugRules.heaveQuality(1.5, 1.8) < TugRules.heaveQuality(1.5, 3.0));
    }

    @Test
    void staminaFactorIsMonotonicAndBounded() {
        double previous = 0;
        for (double s = 0; s <= 1.0001; s += 0.05) {
            double f = TugRules.staminaFactor(s);
            assertTrue(f >= 0.35 - 1e-12 && f <= 1.0 + 1e-12);
            assertTrue(f >= previous - 1e-12);
            previous = f;
        }
        assertEquals(0.35, TugRules.staminaFactor(0), 1e-12);
        assertEquals(1.0, TugRules.staminaFactor(0.5), 1e-12);
        assertEquals(1.0, TugRules.staminaFactor(1.0), 1e-12);
        assertEquals(0.35, TugRules.staminaFactor(-3), 1e-12);
    }

    @Test
    void aBurstBuildsUpKicksAndDiesAway() {
        assertEquals(0.0, TugRules.pulseEnvelope(-1), 0.0);
        assertEquals(0.0, TugRules.pulseEnvelope(TugRules.PULSE_TICKS), 0.0);
        double peak = 0;
        int peakAge = -1;
        for (int age = 0; age < TugRules.PULSE_TICKS; age++) {
            double v = TugRules.pulseEnvelope(age);
            assertTrue(v > 0);
            if (v > peak) {
                peak = v;
                peakAge = age;
            }
        }
        assertEquals(2, peakAge);
        assertEquals(2.2, peak, 0.05);
        for (int age = 3; age < TugRules.PULSE_TICKS; age++) {
            assertTrue(TugRules.pulseEnvelope(age) < TugRules.pulseEnvelope(age - 1), "decays after the kick");
        }
    }

    @Test
    void smallTeamsPullAHeavierRope() {
        assertEquals(1.0, TugRules.ropeWeight(8), 0.01);
        assertEquals(1.0, TugRules.ropeWeight(16), 0.0);
        assertEquals(1.0, TugRules.ropeWeight(32), 0.0);
        double previous = Double.MAX_VALUE;
        for (int n = 1; n <= 12; n++) {
            double w = TugRules.ropeWeight(n);
            assertTrue(w >= 1.0 && w <= previous + 1e-12, "the weight never grows with the team size: n=" + n);
            previous = w;
        }
        assertTrue(TugRules.ropeWeight(2) > 2.0 && TugRules.ropeWeight(2) < 3.0, "2 v 2: " + TugRules.ropeWeight(2));
        assertTrue(TugRules.ropeWeight(4) > 1.2 && TugRules.ropeWeight(4) < 1.6, "4 v 4: " + TugRules.ropeWeight(4));
    }

    @Test
    void beatPeriodsAreBetweenOnePointTwoAndOnePointSixSeconds() {
        Rng rng = new Rng(11);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            int p = TugRules.beatPeriod(rng);
            assertTrue(p >= 24 && p <= 32);
            seen.add(p);
        }
        assertTrue(seen.size() >= 7, "the tempo varies from heat to heat");
    }

    @Test
    void traitsMapToStrengthEnduranceAndRating() {
        Personality weak = new Personality(Personality.Archetype.ROOKIE, 0.1f, 0.5f, 0.1f, 0.05f, 0.05f, 0.1f, 0.5f);
        Personality strong = new Personality(Personality.Archetype.ATHLETE, 0.9f, 0.5f, 0.9f, 0.95f, 0.9f, 0.9f, 0.5f);
        Personality average = new Personality(Personality.Archetype.ROOKIE, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f);
        assertTrue(TugTraits.strength(weak) < TugTraits.strength(average) && TugTraits.strength(average) < TugTraits.strength(strong));
        assertEquals(1.0, TugTraits.strength(average), 0.02);
        assertEquals(1.0, TugTraits.endurance(average), 0.05);
        assertTrue(TugTraits.endurance(weak) < TugTraits.endurance(strong));
        assertTrue(TugTraits.rating(weak) < TugTraits.rating(average) && TugTraits.rating(average) < TugTraits.rating(strong));
        assertTrue(TugTraits.strength(average, 0.25) > TugTraits.strength(average), "the skill bonus makes opponents stronger");
        assertTrue(TugTraits.strength(strong, 0.5) <= TugTraits.strength(strong, 0.05) + 0.34 * 0.05 + 1e-9, "skill is capped at 1");
        // a cooperative member is worth more than an uncooperative one of the same strength
        Personality coop = new Personality(Personality.Archetype.TEAM_PLAYER, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.95f, 0.5f);
        Personality lone = new Personality(Personality.Archetype.LONE_WOLF, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.05f, 0.5f);
        assertTrue(TugTraits.rating(coop) > TugTraits.rating(lone));
        assertEquals(TugTraits.strength(coop), TugTraits.strength(lone), 1e-12);
    }
}
