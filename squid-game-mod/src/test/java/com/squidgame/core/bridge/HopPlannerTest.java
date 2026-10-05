package com.squidgame.core.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HopPlannerTest {
    @Test
    void aVanillaJumpStaysInTheAirTwelveTicks() {
        assertEquals(12, HopPlanner.flightTicks(HopPlanner.JUMP_NORMAL, 0));
        assertEquals(13, HopPlanner.flightTicks(HopPlanner.JUMP_HIGH, 0));
    }

    @Test
    void jumpHeightMatchesVanilla() {
        double peak = 0;
        for (int i = 1; i <= 12; i++) {
            peak = Math.max(peak, HopPlanner.heightAfter(HopPlanner.JUMP_NORMAL, i));
        }
        assertEquals(1.2522, peak, 0.01, "a vanilla jump rises about 1.25 blocks");
        assertEquals(0.0, HopPlanner.heightAfter(HopPlanner.JUMP_NORMAL, 0), 1e-12);
    }

    @Test
    void landingHappensOnTheFirstMoveThatWouldCrossTheSurface() {
        for (double up : new double[]{HopPlanner.JUMP_NORMAL, HopPlanner.JUMP_HIGH, 0.5}) {
            int t = HopPlanner.flightTicks(up, 0);
            for (int i = 1; i < t; i++) {
                assertTrue(HopPlanner.heightAfter(up, i) > 0, "still above the surface before the landing move");
            }
            assertTrue(HopPlanner.heightAfter(up, t) <= 0);
        }
    }

    @Test
    void remainingTicksAgreeWithTheFullFlightAtTakeoff() {
        for (double up : new double[]{0.42, 0.46, 0.5}) {
            assertEquals(HopPlanner.flightTicks(up, 0), HopPlanner.remainingTicks(up, 0));
        }
    }

    @Test
    void remainingTicksCountDownAlongTheArc() {
        double up = HopPlanner.JUMP_NORMAL;
        int total = HopPlanner.flightTicks(up, 0);
        double y = 0, v = up;
        for (int moved = 0; moved < total; moved++) {
            assertEquals(total - moved, HopPlanner.remainingTicks(v, y), "after " + moved + " moves");
            y += v;
            v = (v - HopPlanner.GRAVITY) * HopPlanner.VERTICAL_DRAG;
        }
    }

    @Test
    void landingOnAHigherOrLowerSurfaceChangesTheFlightTime() {
        int level = HopPlanner.flightTicks(0.42, 0);
        assertTrue(HopPlanner.flightTicks(0.42, 1.0) < level, "a higher landing surface is reached sooner");
        assertTrue(HopPlanner.flightTicks(0.42, -3.0) > level, "a lower one later");
    }

    @Test
    void planCoversTheRequestedDistanceExactly() {
        for (double d = 1.0; d <= HopPlanner.maxDistance(); d += 0.05) {
            HopPlanner.Plan plan = HopPlanner.plan(d);
            assertEquals(d, plan.distance(), 1e-9);
            assertEquals(d, plan.speed() * HopPlanner.flightTicks(plan.upSpeed(), 0), 1e-9);
            assertTrue(plan.speed() <= HopPlanner.MAX_SPEED + 1e-9, "distance " + d + " needs speed " + plan.speed());
        }
    }

    @Test
    void shortHopsUseTheNormalJumpAndLongOnesTheStrongerOne() {
        assertEquals(HopPlanner.JUMP_NORMAL, HopPlanner.plan(2.2).upSpeed());
        assertEquals(HopPlanner.JUMP_HIGH, HopPlanner.plan(4.0).upSpeed());
        assertTrue(HopPlanner.plan(2.2).speed() <= HopPlanner.COMFORT_SPEED);
        assertTrue(HopPlanner.plan(4.0).speed() <= HopPlanner.COMFORT_SPEED + 0.05);
    }

    @Test
    void theLongestHopIsLongerThanTheLongestDiagonalOfTheBridge() {
        assertTrue(HopPlanner.maxDistance() > 4.5);
    }
}
