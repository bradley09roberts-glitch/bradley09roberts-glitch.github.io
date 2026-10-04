package com.squidgame.core.redlight;

import com.squidgame.core.Difficulty;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DollCycleTest {
    @Test
    void phasesFollowGreenTurningRed() {
        DollCycle c = DollCycle.of(100, 20, 10, 80);
        assertEquals(200, c.totalTicks());
        assertEquals(DollCycle.Light.GREEN, c.lightAt(0));
        assertEquals(DollCycle.Light.GREEN, c.lightAt(99));
        assertEquals(DollCycle.Light.TURNING, c.lightAt(100));
        assertEquals(DollCycle.Light.TURNING, c.lightAt(119));
        assertEquals(DollCycle.Light.RED, c.lightAt(120));
        assertEquals(DollCycle.Light.RED, c.lightAt(199));
    }

    @Test
    void syllableBoundariesAreOrderedAndCoverTheChant() {
        DollCycle c = DollCycle.of(120, 20, 10, 80);
        assertEquals(0, c.syllableStart[0]);
        assertEquals(120, c.syllableStart[DollCycle.SYLLABLES]);
        for (int i = 1; i <= DollCycle.SYLLABLES; i++) {
            assertTrue(c.syllableStart[i] >= c.syllableStart[i - 1]);
        }
        assertEquals(-1, c.syllableAt(-1));
        assertEquals(0, c.syllableAt(0));
        assertEquals(DollCycle.SYLLABLES - 1, c.syllableAt(119));
        assertEquals(-1, c.syllableAt(120));
        // the last syllable is held longer than the others
        int last = c.syllableStart[DollCycle.SYLLABLES] - c.syllableStart[DollCycle.SYLLABLES - 1];
        int first = c.syllableStart[1] - c.syllableStart[0];
        assertTrue(last > first);
    }

    @Test
    void generatedCyclesAreDeterministicPerSeed() {
        DollCycle a = DollCycle.generate(new Rng(99), Difficulty.HARD, 0.4);
        DollCycle b = DollCycle.generate(new Rng(99), Difficulty.HARD, 0.4);
        assertEquals(a.chantTicks, b.chantTicks);
        assertEquals(a.watchedTicks, b.watchedTicks);
        assertArrayEquals(a.syllableStart, b.syllableStart);
    }

    @Test
    void allowanceShrinksWithDifficultyAndChantsGetFaster() {
        double normal = 0, extreme = 0;
        Rng r = new Rng(5);
        for (int i = 0; i < 400; i++) {
            DollCycle n = DollCycle.generate(r, Difficulty.NORMAL, 0.5);
            DollCycle e = DollCycle.generate(r, Difficulty.EXTREME, 0.5);
            assertEquals(RedLightRules.params(Difficulty.NORMAL).allowanceTicks(), n.allowanceTicks);
            assertEquals(RedLightRules.params(Difficulty.EXTREME).allowanceTicks(), e.allowanceTicks);
            normal += n.chantTicks;
            extreme += e.chantTicks;
        }
        assertTrue(extreme / 400 < normal / 400, "extreme chants are shorter on average");
        assertTrue(RedLightRules.params(Difficulty.EXTREME).allowanceTicks() < RedLightRules.params(Difficulty.NORMAL).allowanceTicks());
    }

    @Test
    void chantNeverEndsAtAPredictableTick() {
        Rng r = new Rng(8);
        java.util.Set<Integer> lengths = new java.util.HashSet<>();
        for (int i = 0; i < 100; i++) {
            lengths.add(DollCycle.generate(r, Difficulty.NORMAL, 0.2).chantTicks);
        }
        assertTrue(lengths.size() > 20);
    }
}
