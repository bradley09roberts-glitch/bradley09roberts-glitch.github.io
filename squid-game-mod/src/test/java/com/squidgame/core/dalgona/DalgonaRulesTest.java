package com.squidgame.core.dalgona;

import com.squidgame.core.Difficulty;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DalgonaRulesTest {
    @Test
    void harderDifficultiesAreMeaningfullyHarder() {
        DalgonaRules.Params n = DalgonaRules.params(Difficulty.NORMAL);
        DalgonaRules.Params h = DalgonaRules.params(Difficulty.HARD);
        DalgonaRules.Params x = DalgonaRules.params(Difficulty.EXTREME);
        assertTrue(n.tolerance() > h.tolerance() && h.tolerance() > x.tolerance(), "tighter corridor");
        assertTrue(n.safeSpeed() > h.safeSpeed() && h.safeSpeed() > x.safeSpeed(), "lower safe speed");
        assertTrue(n.speedStress() < h.speedStress() && h.speedStress() < x.speedStress(), "speed bites harder");
        assertTrue(n.offPathStress() < h.offPathStress() && h.offPathStress() < x.offPathStress());
        assertTrue(n.wobbleStress() < h.wobbleStress() && h.wobbleStress() < x.wobbleStress());
        assertTrue(n.microChance() < h.microChance() && h.microChance() < x.microChance());
        assertTrue(n.licks() > h.licks() && h.licks() > x.licks(), "fewer licks");
        assertTrue(n.lickRelief() > h.lickRelief() && h.lickRelief() > x.lickRelief(), "weaker licks");
        assertTrue(n.restDecay() > h.restDecay() && h.restDecay() > x.restDecay(), "slower recovery");
        assertTrue(n.successThreshold() < h.successThreshold() && h.successThreshold() < x.successThreshold(), "cleaner cut needed");
        assertTrue(n.carveSeconds() > h.carveSeconds() && h.carveSeconds() > x.carveSeconds(), "less time");
        for (DalgonaRules.Params p : new DalgonaRules.Params[]{n, h, x}) {
            assertTrue(p.successThreshold() >= 0.95 && p.successThreshold() <= 0.99);
            assertTrue(p.lickLockTicks() < p.lickCooldownTicks());
            assertTrue(p.microMin() > 0 && p.microMax() > p.microMin() && p.microMax() < 30);
        }
    }

    @Test
    void timesAndLicksFollowTheSpec() {
        assertEquals(150, DalgonaRules.params(Difficulty.NORMAL).carveSeconds());
        assertEquals(120, DalgonaRules.params(Difficulty.HARD).carveSeconds());
        assertEquals(95, DalgonaRules.params(Difficulty.EXTREME).carveSeconds());
        for (Difficulty d : Difficulty.values()) {
            assertEquals((DalgonaRules.params(d).carveSeconds() + DalgonaRules.SELECTION_SECONDS) * 20, DalgonaRules.timeLimitTicks(d));
        }
        assertEquals(200, DalgonaRules.selectionTicks(1.0));
        assertEquals(60, DalgonaRules.selectionTicks(0.3));
        assertEquals(40, DalgonaRules.selectionTicks(0.01), "never shorter than two seconds");
    }

    @Test
    void samplesNeededRespectsTheThreshold() {
        for (Difficulty d : Difficulty.values()) {
            DalgonaRules.Params p = DalgonaRules.params(d);
            for (DalgonaShape s : DalgonaShape.values()) {
                int need = p.samplesNeeded(s);
                assertTrue(need <= s.sampleCount() && need > s.sampleCount() * 0.9);
                assertTrue(need >= p.successThreshold() * s.sampleCount() - 1e-6);
            }
        }
    }

    @Test
    void localSafeSpeedFallsWithFragility() {
        DalgonaRules.Params p = DalgonaRules.params(Difficulty.NORMAL);
        assertEquals(p.safeSpeed(), p.localSafeSpeed(1.0), 1e-9);
        assertEquals(p.safeSpeed(), p.localSafeSpeed(0.2), 1e-9, "never above the nominal speed");
        assertTrue(p.localSafeSpeed(2.0) < p.localSafeSpeed(1.5));
        assertTrue(p.localSafeSpeed(DalgonaShape.MAX_FRAGILITY) > 0.3 * p.safeSpeed());
    }

    @Test
    void shapeWeightsAreDistributionsThatShiftTowardsHardShapes() {
        for (Difficulty d : Difficulty.values()) {
            double sum = 0;
            for (double w : DalgonaRules.shapeWeights(d)) {
                assertTrue(w > 0.05, "every shape stays possible");
                sum += w;
            }
            assertEquals(1.0, sum, 1e-9);
        }
        double[] n = DalgonaRules.shapeWeights(Difficulty.NORMAL);
        double[] x = DalgonaRules.shapeWeights(Difficulty.EXTREME);
        assertTrue(x[DalgonaShape.UMBRELLA.ordinal()] > n[DalgonaShape.UMBRELLA.ordinal()]);
        assertTrue(x[DalgonaShape.CIRCLE.ordinal()] < n[DalgonaShape.CIRCLE.ordinal()]);
    }

    @Test
    void everyShapeAppearsWhenThereAreFourOrMoreContestants() {
        for (int count : new int[]{4, 5, 7, 16, 63, 128}) {
            for (Difficulty d : Difficulty.values()) {
                for (long seed = 0; seed < 25; seed++) {
                    DalgonaShape[] a = DalgonaRules.assignShapes(new Rng(seed), d, count);
                    assertEquals(count, a.length);
                    Set<DalgonaShape> seen = EnumSet.noneOf(DalgonaShape.class);
                    for (DalgonaShape s : a) {
                        assertNotNull(s);
                        seen.add(s);
                    }
                    assertEquals(4, seen.size(), "count " + count + " seed " + seed + " " + d);
                }
            }
        }
    }

    @Test
    void assignmentWorksForTinyFieldsAndIsDeterministic() {
        assertEquals(0, DalgonaRules.assignShapes(new Rng(1), Difficulty.NORMAL, 0).length);
        assertEquals(1, DalgonaRules.assignShapes(new Rng(1), Difficulty.NORMAL, 1).length);
        assertEquals(3, DalgonaRules.assignShapes(new Rng(1), Difficulty.HARD, 3).length);
        assertArrayEquals(DalgonaRules.assignShapes(new Rng(77), Difficulty.HARD, 40), DalgonaRules.assignShapes(new Rng(77), Difficulty.HARD, 40));
        boolean differs = false;
        for (long seed = 1; seed < 10 && !differs; seed++) {
            differs = !java.util.Arrays.equals(DalgonaRules.assignShapes(new Rng(seed), Difficulty.NORMAL, 40),
                    DalgonaRules.assignShapes(new Rng(seed + 100), Difficulty.NORMAL, 40));
        }
        assertTrue(differs, "different seeds give different layouts");
    }

    @Test
    void assignmentFollowsTheWeightsInTheLongRun() {
        int[] counts = new int[4];
        Rng r = new Rng(5);
        for (int i = 0; i < 200; i++) {
            for (DalgonaShape s : DalgonaRules.assignShapes(r, Difficulty.EXTREME, 40)) {
                counts[s.ordinal()]++;
            }
        }
        double total = 200 * 40;
        double[] w = DalgonaRules.shapeWeights(Difficulty.EXTREME);
        for (int i = 0; i < 4; i++) {
            // the guaranteed first four shift the share slightly towards uniform, hence the generous bound
            assertEquals(w[i], counts[i] / total, 0.04, "shape " + i);
        }
    }
}
