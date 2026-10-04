package com.squidgame.core;

import com.squidgame.core.redlight.DollCycle;
import com.squidgame.core.redlight.RedLightRules;
import com.squidgame.core.redlight.RedLightRules.Params;
import com.squidgame.core.redlight.RedLightRules.Sample;
import com.squidgame.core.redlight.RedLightRules.Verdict;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedLightRulesTest {
    private static final Params P = RedLightRules.params(Difficulty.NORMAL);

    private static Sample s(double h, double v, boolean ground, boolean input, boolean jump, boolean ext) {
        return new Sample(h, v, ground, input, jump, ext);
    }

    @Test
    void greenLightNeverEliminates() {
        assertEquals(Verdict.OK, RedLightRules.evaluate(P, false, false, s(0.4, 0.5, false, true, true, false)));
    }

    @Test
    void standingStillWhileWatchedIsSafe() {
        assertEquals(Verdict.OK, RedLightRules.evaluate(P, true, false, s(0.0, 0.0, true, false, false, false)));
        // camera rotation never changes position, so a stationary sample is OK even with no input
        assertEquals(Verdict.OK, RedLightRules.evaluate(P, true, false, s(0.001, 0.0, true, false, false, false)));
    }

    @Test
    void deliberateMovementIsEliminated() {
        assertEquals(Verdict.MOVED, RedLightRules.evaluate(P, true, false, s(0.2, 0.0, true, true, false, false)));
    }

    @Test
    void pressingKeysAgainstAWallIsNotMovement() {
        assertEquals(Verdict.OK, RedLightRules.evaluate(P, true, false, s(0.0, 0.0, true, true, false, false)));
    }

    @Test
    void ownJumpIsEliminated() {
        assertEquals(Verdict.JUMPED, RedLightRules.evaluate(P, true, false, s(0.0, 0.42, false, false, true, false)));
    }

    @Test
    void alreadyAirborneContestantMayLand() {
        assertEquals(Verdict.OK, RedLightRules.evaluate(P, true, true, s(0.0, -0.2, false, false, false, false)));
    }

    @Test
    void smallPushWithoutInputIsTolerated() {
        assertEquals(Verdict.OK, RedLightRules.evaluate(P, true, false, s(0.08, 0.0, true, false, false, false)));
    }

    @Test
    void largeUnexplainedMoveWithoutInputIsIllegal() {
        assertEquals(Verdict.ILLEGAL, RedLightRules.evaluate(P, true, false, s(3.0, 0.0, true, false, false, false)));
    }

    @Test
    void knockbackWithKnownExternalCauseIsTolerated() {
        assertEquals(Verdict.OK, RedLightRules.evaluate(P, true, false, s(0.5, 0.2, false, false, false, true)));
    }

    @Test
    void harderDifficultiesTightenTheRules() {
        Params normal = RedLightRules.params(Difficulty.NORMAL);
        Params hard = RedLightRules.params(Difficulty.HARD);
        Params extreme = RedLightRules.params(Difficulty.EXTREME);
        assertTrue(normal.allowanceTicks() > hard.allowanceTicks());
        assertTrue(hard.allowanceTicks() > extreme.allowanceTicks());
        assertTrue(normal.moveThreshold() > hard.moveThreshold());
        assertTrue(hard.moveThreshold() > extreme.moveThreshold());
        assertTrue(normal.dollTurnTicks() > extreme.dollTurnTicks());
    }

    @Test
    void dollCycleIsDeterministicAndWellFormed() {
        for (Difficulty d : Difficulty.values()) {
            DollCycle a = DollCycle.generate(new Rng(42), d, 0.3);
            DollCycle b = DollCycle.generate(new Rng(42), d, 0.3);
            assertEquals(a.chantTicks, b.chantTicks);
            assertEquals(a.totalTicks(), b.totalTicks());
            int prev = -1;
            for (int i = 0; i <= DollCycle.SYLLABLES; i++) {
                assertTrue(a.syllableStart[i] >= prev, "syllable starts must be monotonic");
                prev = a.syllableStart[i];
            }
            assertEquals(a.chantTicks, a.syllableStart[DollCycle.SYLLABLES]);
            assertEquals(DollCycle.Light.GREEN, a.lightAt(0));
            assertEquals(DollCycle.Light.TURNING, a.lightAt(a.chantTicks));
            assertEquals(DollCycle.Light.RED, a.lightAt(a.chantTicks + a.allowanceTicks));
            assertEquals(-1, a.syllableAt(a.chantTicks));
            assertEquals(DollCycle.SYLLABLES - 1, a.syllableAt(a.chantTicks - 1));
            assertTrue(a.chantTicks >= 30, "chant must leave time to move");
        }
    }

    @Test
    void hardChantsAreOnAverageShorter() {
        double normal = 0, extreme = 0;
        Rng r = new Rng(7);
        for (int i = 0; i < 400; i++) {
            normal += DollCycle.generate(r, Difficulty.NORMAL, 0.5).chantTicks;
            extreme += DollCycle.generate(r, Difficulty.EXTREME, 0.5).chantTicks;
        }
        assertTrue(normal > extreme);
    }
}
