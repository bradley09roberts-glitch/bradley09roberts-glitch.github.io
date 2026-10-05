package com.squidgame.core.bridge;

import com.squidgame.core.Difficulty;
import com.squidgame.core.bridge.BridgeQueue.Flow;
import com.squidgame.core.bridge.BridgeQueue.State;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BridgeQueueTest {
    private static BridgeQueue queue(int... numbers) {
        List<Integer> l = new ArrayList<>();
        for (int n : numbers) {
            l.add(n);
        }
        return new BridgeQueue(l);
    }

    @Test
    void contestantsAreCalledStrictlyInOrder() {
        BridgeQueue q = queue(30, 10, 20);
        assertEquals(30, q.next());
        assertFalse(q.call(10), "nobody may skip ahead");
        assertFalse(q.call(20));
        assertTrue(q.call(30));
        assertFalse(q.call(30), "nobody is called twice");
        assertEquals(10, q.next());
        assertTrue(q.call(10));
        assertTrue(q.call(20));
        assertEquals(-1, q.next());
        assertEquals(20, q.lastCalled());
    }

    @Test
    void positionAndPlayersAheadFollowTheOrder() {
        BridgeQueue q = queue(5, 6, 7, 8);
        assertEquals(0, q.positionOf(5));
        assertEquals(3, q.positionOf(8));
        assertEquals(-1, q.positionOf(99));
        assertEquals(0, q.aheadOf(5));
        assertEquals(3, q.aheadOf(8));
        q.call(5);
        assertEquals(3, q.aheadOf(8), "somebody at the gate is still ahead of you");
        q.onBridge(5);
        assertEquals(2, q.aheadOf(8), "once on the bridge they no longer count");
        q.out(6);
        assertEquals(1, q.aheadOf(8));
    }

    @Test
    void eliminatedContestantsAreSkippedAndStateIsTerminal() {
        BridgeQueue q = queue(1, 2, 3);
        q.out(1);
        assertEquals(2, q.next(), "a contestant eliminated before being called is skipped");
        q.call(2);
        q.onBridge(2);
        q.finished(2);
        q.out(2);
        assertEquals(State.FINISHED, q.state(2), "a finisher stays safe");
        q.out(3);
        assertEquals(State.OUT, q.state(3));
        assertEquals(-1, q.next());
        assertEquals(State.OUT, q.state(99), "strangers count as gone");
    }

    @Test
    void countsPerStateAddUp() {
        BridgeQueue q = queue(1, 2, 3, 4, 5);
        q.call(1);
        q.onBridge(1);
        q.call(2);
        q.call(3);
        assertEquals(2, q.count(State.QUEUED));
        assertEquals(2, q.count(State.CALLED));
        assertEquals(1, q.count(State.ON_BRIDGE));
        assertTrue(q.someoneCalled());
        q.onBridge(2);
        q.onBridge(3);
        assertFalse(q.someoneCalled());
        q.finished(1);
        q.out(2);
        assertEquals(1, q.count(State.FINISHED));
        assertEquals(1, q.count(State.OUT));
        assertEquals(1, q.count(State.ON_BRIDGE));
        assertEquals(5, q.size());
    }

    @Test
    void duplicateNumbersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> queue(1, 2, 1));
    }

    @Test
    void orderIsACopy() {
        BridgeQueue q = queue(4, 3, 2, 1);
        List<Integer> o = q.order();
        o.clear();
        assertEquals(List.of(4, 3, 2, 1), q.order());
    }

    // ------------------------------------------------------------------ gate flow

    private static Flow flow(boolean called, int crossers, long since, boolean hasPrev, boolean gone, int row) {
        return new Flow(called, crossers, since, hasPrev, gone, row);
    }

    @Test
    void firstContestantIsCalledImmediately() {
        for (Difficulty d : Difficulty.values()) {
            assertTrue(BridgeQueue.mayRelease(BridgeRules.params(d), flow(false, 0, 0, false, false, -1)));
        }
    }

    @Test
    void nobodyIsCalledWhileSomeoneStillStandsAtTheGate() {
        BridgeRules.Params p = BridgeRules.params(Difficulty.NORMAL);
        assertFalse(BridgeQueue.mayRelease(p, flow(true, 0, 10_000, true, true, 0)));
    }

    @Test
    void theGateWaitsForTheLeaderToGetAheadOrDisappear() {
        BridgeRules.Params p = BridgeRules.params(Difficulty.NORMAL);
        long spaced = p.minReleaseSpacingTicks() + 1;
        assertFalse(BridgeQueue.mayRelease(p, flow(false, 1, spaced, true, false, 0)), "leader only on row 0");
        assertTrue(BridgeQueue.mayRelease(p, flow(false, 1, spaced, true, false, p.releaseGapRows() - 1)));
        assertTrue(BridgeQueue.mayRelease(p, flow(false, 1, spaced, true, true, 0)), "a fallen leader no longer blocks");
    }

    @Test
    void minimumSpacingAndCrowdingAreRespected() {
        BridgeRules.Params p = BridgeRules.params(Difficulty.NORMAL);
        assertFalse(BridgeQueue.mayRelease(p, flow(false, 1, p.minReleaseSpacingTicks() - 1, true, true, 0)));
        assertFalse(BridgeQueue.mayRelease(p, flow(false, p.maxCrossers(), 100_000, true, true, 0)));
        assertTrue(BridgeQueue.mayRelease(p, flow(false, p.maxCrossers() - 1, 100_000, true, false, 17)));
    }

    @Test
    void harderDifficultiesReleaseNoMoreEagerly() {
        // same situation: leader on row 2 (index), plenty of time since the last call, two on the bridge
        Flow f = flow(false, 2, 1000, true, false, 2);
        boolean normal = BridgeQueue.mayRelease(BridgeRules.params(Difficulty.NORMAL), f);
        boolean hard = BridgeQueue.mayRelease(BridgeRules.params(Difficulty.HARD), f);
        boolean extreme = BridgeQueue.mayRelease(BridgeRules.params(Difficulty.EXTREME), f);
        assertTrue(normal);
        assertTrue(hard);
        assertTrue(extreme, "gap 3 rows = index 2: the leader is far enough on every difficulty");
        Flow close = flow(false, 2, 1000, true, false, 1);
        assertTrue(BridgeQueue.mayRelease(BridgeRules.params(Difficulty.NORMAL), close));
        assertFalse(BridgeQueue.mayRelease(BridgeRules.params(Difficulty.HARD), close));
        assertFalse(BridgeQueue.mayRelease(BridgeRules.params(Difficulty.EXTREME), close));
    }

    @Test
    void theGateNeverDeadlocksWhateverHappensToTheLeader() {
        // random walk of leader states: whenever nobody is at the gate and the leader is gone the gate must open
        Rng rng = new Rng(7);
        BridgeRules.Params p = BridgeRules.params(Difficulty.EXTREME);
        for (int i = 0; i < 2000; i++) {
            int crossers = rng.nextInt(p.maxCrossers());
            Flow gone = flow(false, crossers, p.minReleaseSpacingTicks() + rng.nextInt(500), true, true, rng.nextInt(18));
            assertTrue(BridgeQueue.mayRelease(p, gone), "a leader that is gone must free the gate");
        }
    }

    @Test
    void aResumedGameKeepsTheSavedOrderOfTheContestantsStillAlive() {
        int[] saved = {7, 3, 9, 1, 5, 2};
        // nobody was eliminated: exactly the saved order
        assertEquals(List.of(7, 3, 9, 1, 5, 2), BridgeQueue.restoreOrder(saved, List.of(1, 2, 3, 5, 7, 9)));
        // 9 and 2 fell before the restart: the others keep their relative places
        assertEquals(List.of(7, 3, 1, 5), BridgeQueue.restoreOrder(saved, List.of(5, 1, 3, 7)));
        // the iteration order of the alive collection is irrelevant
        assertEquals(BridgeQueue.restoreOrder(saved, List.of(1, 3, 5, 7)), BridgeQueue.restoreOrder(saved, List.of(7, 5, 3, 1)));
        // somebody the saved order has never heard of (a different roster): no restoration
        assertNull(BridgeQueue.restoreOrder(saved, List.of(7, 3, 4)));
        assertNull(BridgeQueue.restoreOrder(null, List.of(1, 2)));
        assertEquals(List.of(), BridgeQueue.restoreOrder(saved, List.of()));
    }
}
