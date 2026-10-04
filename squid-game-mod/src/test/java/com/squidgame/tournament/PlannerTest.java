package com.squidgame.tournament;

import com.squidgame.core.GameKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlannerTest {
    @Test
    void firstGameIsRedLight() {
        assertEquals(GameKind.RED_LIGHT, Planner.next(null, 40));
        assertEquals(GameKind.RED_LIGHT, Planner.next(null, 2));
    }

    @Test
    void followsTheCanonicalOrder() {
        assertEquals(GameKind.DALGONA, Planner.next(GameKind.RED_LIGHT, 30));
        assertEquals(GameKind.TUG_OF_WAR, Planner.next(GameKind.DALGONA, 20));
        assertEquals(GameKind.MARBLES, Planner.next(GameKind.TUG_OF_WAR, 10));
        assertEquals(GameKind.GLASS_BRIDGE, Planner.next(GameKind.MARBLES, 6));
        assertEquals(GameKind.FINAL, Planner.next(GameKind.GLASS_BRIDGE, 3));
    }

    @Test
    void gamesWithTooFewSurvivorsAreSkipped() {
        // tug of war needs four contestants
        assertEquals(GameKind.MARBLES, Planner.next(GameKind.DALGONA, 3));
    }

    @Test
    void twoSurvivorsJumpStraightToTheFinal() {
        assertEquals(GameKind.FINAL, Planner.next(GameKind.RED_LIGHT, 2));
        assertEquals(GameKind.FINAL, Planner.next(GameKind.TUG_OF_WAR, 2));
    }

    @Test
    void oneOrNoSurvivorEndsTheTournament() {
        assertNull(Planner.next(GameKind.RED_LIGHT, 1));
        assertNull(Planner.next(null, 1));
        assertNull(Planner.next(GameKind.DALGONA, 0));
    }

    @Test
    void nothingFollowsTheFinal() {
        assertEquals(null, Planner.next(GameKind.FINAL, 5));
        assertNull(Planner.next(GameKind.FINAL, 2));
    }

    @Test
    void finalIsPlayedWithSeveralSurvivorsAfterTheBridge() {
        assertEquals(GameKind.FINAL, Planner.next(GameKind.GLASS_BRIDGE, 7));
    }
}
