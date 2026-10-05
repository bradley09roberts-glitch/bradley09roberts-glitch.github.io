package com.squidgame.core.bridge;

import com.squidgame.core.bridge.BridgeKnowledge.LaneState;
import com.squidgame.core.bridge.BridgeKnowledge.Outcome;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BridgeKnowledgeTest {
    @Test
    void startsWithoutAnyKnowledge() {
        BridgeKnowledge k = new BridgeKnowledge(18);
        assertEquals(0, k.knownRows());
        assertEquals(0, k.frontier());
        assertEquals(0, k.eventCount());
        for (int r = 0; r < 18; r++) {
            assertFalse(k.isKnown(r));
            assertEquals(BridgeKnowledge.UNKNOWN, k.safeLane(r));
            assertEquals(LaneState.UNKNOWN, k.laneState(r, 0));
            assertEquals(LaneState.UNKNOWN, k.laneState(r, 1));
        }
    }

    @Test
    void aPanelThatHeldRevealsItsLane() {
        BridgeKnowledge k = new BridgeKnowledge(18);
        assertTrue(k.record(0, 1, Outcome.HELD, 100));
        assertEquals(1, k.safeLane(0));
        assertEquals(LaneState.SAFE, k.laneState(0, 1));
        assertEquals(LaneState.WEAK, k.laneState(0, 0));
        assertEquals(1, k.frontier());
        assertEquals(1, k.knownRows());
    }

    @Test
    void aPanelThatShatteredRevealsTheOtherLane() {
        BridgeKnowledge k = new BridgeKnowledge(18);
        assertTrue(k.record(3, 0, Outcome.BROKE, 50));
        assertEquals(1, k.safeLane(3));
        assertEquals(LaneState.BROKEN, k.laneState(3, 0));
        assertEquals(LaneState.SAFE, k.laneState(3, 1));
        assertEquals(0, k.frontier(), "rows 0..2 are still unknown");
    }

    @Test
    void duplicatesAndContradictionsAreIgnored() {
        BridgeKnowledge k = new BridgeKnowledge(18);
        assertTrue(k.record(2, 1, Outcome.HELD, 10));
        assertFalse(k.record(2, 1, Outcome.HELD, 20), "already known");
        assertFalse(k.record(2, 0, Outcome.HELD, 30), "contradiction");
        assertFalse(k.record(2, 1, Outcome.BROKE, 40), "contradiction");
        assertEquals(1, k.safeLane(2));
        assertEquals(1, k.eventCount());
    }

    @Test
    void seeingAKnownWeakPanelBreakIsNewButChangesNothingElse() {
        BridgeKnowledge k = new BridgeKnowledge(18);
        k.record(4, 1, Outcome.HELD, 10);
        assertEquals(LaneState.WEAK, k.laneState(4, 0));
        assertTrue(k.record(4, 0, Outcome.BROKE, 60), "somebody slipped onto the known fragile panel");
        assertEquals(LaneState.BROKEN, k.laneState(4, 0));
        assertEquals(1, k.safeLane(4));
        assertFalse(k.record(4, 0, Outcome.BROKE, 70));
    }

    @Test
    void outOfRangeReportsAreIgnored() {
        BridgeKnowledge k = new BridgeKnowledge(18);
        assertFalse(k.record(-1, 0, Outcome.HELD, 0));
        assertFalse(k.record(18, 0, Outcome.HELD, 0));
        assertFalse(k.record(0, 2, Outcome.HELD, 0));
        assertFalse(k.record(0, -1, Outcome.BROKE, 0));
        assertEquals(0, k.eventCount());
        assertEquals(BridgeKnowledge.UNKNOWN, k.safeLane(99));
        assertEquals(LaneState.UNKNOWN, k.laneState(99, 0));
    }

    @Test
    void frontierAdvancesOverAKnownPrefixAndSkipsNothing() {
        BridgeKnowledge k = new BridgeKnowledge(6);
        k.record(0, 0, Outcome.HELD, 1);
        k.record(1, 1, Outcome.BROKE, 2);
        k.record(3, 1, Outcome.HELD, 3);
        assertEquals(2, k.frontier());
        k.record(2, 0, Outcome.HELD, 4);
        assertEquals(4, k.frontier());
        k.record(4, 0, Outcome.HELD, 5);
        k.record(5, 1, Outcome.HELD, 6);
        assertEquals(6, k.frontier());
        assertEquals(6, k.knownRows());
    }

    @Test
    void eventLogCanBeReplayedByALateObserver() {
        BridgeKnowledge k = new BridgeKnowledge(18);
        k.record(0, 1, Outcome.HELD, 10);
        k.record(1, 0, Outcome.BROKE, 20);
        k.record(1, 1, Outcome.HELD, 30); // duplicate information: not logged
        BridgeKnowledge replay = new BridgeKnowledge(18);
        for (BridgeKnowledge.Event e : k.events()) {
            assertTrue(replay.record(e.row(), e.lane(), e.outcome(), e.tick()));
        }
        assertEquals(2, k.eventCount());
        for (int r = 0; r < 18; r++) {
            assertEquals(k.safeLane(r), replay.safeLane(r));
        }
        assertEquals(0, k.event(0).index());
        assertEquals(1, k.event(1).index());
        assertThrows(UnsupportedOperationException.class, () -> k.events().clear());
    }

    @Test
    void snapshotMirrorsTheLaneStates() {
        BridgeKnowledge k = new BridgeKnowledge(3);
        k.record(0, 1, Outcome.HELD, 1);
        k.record(1, 0, Outcome.BROKE, 2);
        byte[] s = k.snapshot();
        assertEquals(6, s.length);
        assertEquals(LaneState.WEAK.ordinal(), s[0]);
        assertEquals(LaneState.SAFE.ordinal(), s[1]);
        assertEquals(LaneState.BROKEN.ordinal(), s[2]);
        assertEquals(LaneState.SAFE.ordinal(), s[3]);
        assertEquals(LaneState.UNKNOWN.ordinal(), s[4]);
        assertEquals(LaneState.UNKNOWN.ordinal(), s[5]);
    }

    @Test
    void knowledgeBuiltFromRealOutcomesNeverContradictsTheRouteAndNeverRunsAhead() {
        Rng rng = new Rng(2024);
        for (int game = 0; game < 300; game++) {
            BridgeRoute route = BridgeRoute.forGame(rng.nextInt(1_000_000), game, 18);
            BridgeKnowledge k = new BridgeKnowledge(18);
            // contestants walk in turn: each steps on the frontier panel of their choice, the outcome is public
            int steps = rng.rangeInt(0, 14);
            for (int i = 0; i < steps && k.frontier() < 18; i++) {
                int row = k.frontier();
                int lane = rng.nextInt(2);
                k.record(row, lane, route.isSafe(row, lane) ? Outcome.HELD : Outcome.BROKE, i);
            }
            for (int r = 0; r < 18; r++) {
                if (k.isKnown(r)) {
                    assertEquals(route.safeLane(r), k.safeLane(r), "knowledge must match the route");
                }
            }
            assertTrue(k.knownRows() <= steps, "can never know more rows than were observed");
            assertEquals(k.frontier(), k.knownRows(), "a prefix of rows is known when steps go row by row");
            for (int r = k.knownRows(); r < 18; r++) {
                assertFalse(k.isKnown(r), "rows nobody tested stay unknown");
            }
        }
    }

    @Test
    void rowsTheGuardsShowAreKnownLikeRowsThatHeld() {
        BridgeKnowledge k = new BridgeKnowledge(18);
        for (int r = 0; r < 13; r++) {
            assertTrue(k.record(r, r % 2, Outcome.SHOWN, 0));
        }
        assertEquals(13, k.knownRows());
        assertEquals(13, k.frontier(), "the shown rows are a prefix: the gamble starts right behind them");
        assertEquals(13, k.eventCount(), "one public event per shown row: the same channel NPCs and overlays read");
        assertEquals(LaneState.SAFE, k.laneState(0, 0));
        assertEquals(LaneState.WEAK, k.laneState(0, 1), "the other lane of a shown row is known to be fragile");
        assertEquals(LaneState.SAFE, k.laneState(1, 1));
        assertEquals(LaneState.UNKNOWN, k.laneState(13, 0), "the rows behind stay unknown");
        assertEquals(LaneState.UNKNOWN, k.laneState(17, 1));
        for (int r = 0; r < 13; r++) {
            assertEquals(r % 2, k.safeLane(r));
            assertEquals(Outcome.SHOWN, k.event(r).outcome());
        }
    }

    @Test
    void aShownRowCannotBeContradictedAndDoesNotBlockLaterNews() {
        BridgeKnowledge k = new BridgeKnowledge(18);
        k.record(2, 1, Outcome.SHOWN, 0);
        assertFalse(k.record(2, 1, Outcome.HELD, 50), "somebody standing on the shown lane teaches nothing new");
        assertFalse(k.record(2, 0, Outcome.HELD, 60), "a report that contradicts the guards is ignored");
        assertTrue(k.record(2, 0, Outcome.BROKE, 70), "...but a panel that is seen breaking is still news (the hole)");
        assertEquals(LaneState.BROKEN, k.laneState(2, 0));
        assertEquals(1, k.safeLane(2));
    }

    @Test
    void theOverlaySnapshotShowsShownRowsAsKnown() {
        BridgeKnowledge k = new BridgeKnowledge(18);
        k.record(0, 0, Outcome.SHOWN, 0);
        k.record(1, 1, Outcome.SHOWN, 0);
        byte[] snap = k.snapshot();
        assertEquals(LaneState.SAFE.ordinal(), snap[0]);
        assertEquals(LaneState.WEAK.ordinal(), snap[1]);
        assertEquals(LaneState.WEAK.ordinal(), snap[2]);
        assertEquals(LaneState.SAFE.ordinal(), snap[3]);
        assertEquals(LaneState.UNKNOWN.ordinal(), snap[4]);
    }
}
