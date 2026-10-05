package com.squidgame.core.tug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeatClockAndInputGateTest {
    // ------------------------------------------------------------------ BeatClock

    @Test
    void beatsFallOnEpochPlusMultiplesOfThePeriod() {
        BeatClock c = new BeatClock(1000, 28);
        assertEquals(1000, c.beatTick(0));
        assertEquals(1028, c.beatTick(1));
        assertEquals(972, c.beatTick(-1));
        assertEquals(0, c.indexAtOrBefore(1000));
        assertEquals(0, c.indexAtOrBefore(1027));
        assertEquals(1, c.indexAtOrBefore(1028));
        assertEquals(-1, c.indexAtOrBefore(999));
        assertThrows(IllegalArgumentException.class, () -> new BeatClock(0, 1));
    }

    @Test
    void theNearestBeatAndTheSignedError() {
        BeatClock c = new BeatClock(0, 28);
        assertEquals(0, c.nearestBeat(13));
        assertEquals(0, c.nearestBeat(14), "a tie goes to the earlier beat");
        assertEquals(28, c.nearestBeat(15));
        assertEquals(28, c.nearestBeat(30));
        assertEquals(-3, c.errorTo(25), "early is negative");
        assertEquals(2, c.errorTo(30), "late is positive");
        assertEquals(0, c.errorTo(28));
        assertEquals(1, c.nearestIndex(30));
        assertEquals(0, c.nearestIndex(13));
        assertEquals(-1, c.nearestIndex(-20));
        assertEquals(-5, new BeatClock(100, 24).errorTo(95));
    }

    @Test
    void theNextBeatAndThePhase() {
        BeatClock c = new BeatClock(10, 30);
        assertEquals(10, c.nextBeatAtOrAfter(10));
        assertEquals(40, c.nextBeatAtOrAfter(11));
        assertEquals(40, c.nextBeatAtOrAfter(40));
        assertEquals(10, c.nextBeatAtOrAfter(-5), "before the epoch the next beat is the first one");
        assertEquals(0.0, c.phase(10), 1e-12);
        assertEquals(0.5, c.phase(25), 1e-12);
        assertEquals(29 / 30.0, c.phase(39), 1e-12);
        assertEquals(0.0, c.phase(40), 1e-12);
    }

    // ------------------------------------------------------------------ TugInputGate

    @Test
    void aFloodOfMessagesIsDroppedButNormalPlayIsNotLimited() {
        TugInputGate.Limiter l = new TugInputGate.Limiter();
        int accepted = 0;
        for (int i = 0; i < 200; i++) {
            if (l.allow(1000)) {
                accepted++;
            }
        }
        assertEquals(TugInputGate.MAX_MESSAGES, accepted);
        assertTrue(l.allow(1000 + TugInputGate.WINDOW_TICKS), "a new window starts");
        // a busy human: a key change and a refresh every few ticks plus a heave per beat stays far below the limit
        TugInputGate.Limiter busy = new TugInputGate.Limiter();
        for (int t = 0; t < 2000; t++) {
            if (t % 3 == 0) {
                assertTrue(busy.allow(t), "message at tick " + t);
            }
        }
    }

    @Test
    void theClockGoingBackwardsDoesNotLockAPlayerOut() {
        TugInputGate.Limiter l = new TugInputGate.Limiter();
        for (int i = 0; i < 100; i++) {
            l.allow(5000);
        }
        assertTrue(l.allow(100), "a tournament restart resets the tick counter");
    }

    @Test
    void heaveMessagesNeedAGapButTheFirstOneAlwaysPasses() {
        TugInputGate.Limiter l = new TugInputGate.Limiter();
        assertTrue(l.allowHeave(0));
        assertFalse(l.allowHeave(1));
        assertFalse(l.allowHeave(TugInputGate.MIN_HEAVE_MESSAGE_GAP - 1));
        assertTrue(l.allowHeave(TugInputGate.MIN_HEAVE_MESSAGE_GAP));
        assertTrue(new TugInputGate.Limiter().allowHeave(0), "also right after the server started");
    }

    @Test
    void aHeldKeyExpiresWhenTheClientGoesQuiet() {
        TugInputGate.Held held = new TugInputGate.Held();
        assertEquals(0.0, held.effort(0), 0.0);
        held.set(true, false, 100);
        assertEquals(1.0, held.effort(100), 0.0);
        assertFalse(held.brace(100));
        assertEquals(1.0, held.effort(100 + TugInputGate.STALE_TICKS), 0.0);
        assertEquals(0.0, held.effort(100 + TugInputGate.STALE_TICKS + 1), 0.0, "no refresh for three seconds: released");
        held.set(false, true, 500);
        assertTrue(held.brace(510));
        assertFalse(held.brace(500 + TugInputGate.STALE_TICKS + 1));
        held.clear();
        assertFalse(held.brace(510));
        assertEquals(0.0, held.effort(510), 0.0);
    }

    @Test
    void latencyIsCompensatedWithinASaneRange() {
        assertEquals(0, TugInputGate.latencyTicks(0));
        assertEquals(0, TugInputGate.latencyTicks(20));
        assertEquals(1, TugInputGate.latencyTicks(30));
        assertEquals(2, TugInputGate.latencyTicks(100));
        assertEquals(TugRules.MAX_LATENCY_COMPENSATION, TugInputGate.latencyTicks(5000), "a lag spike cannot buy an arbitrary shift");
        assertEquals(0, TugInputGate.latencyTicks(-40));
        assertEquals(98, TugInputGate.pressTick(100, 100));
        assertEquals(100 - TugRules.MAX_LATENCY_COMPENSATION, TugInputGate.pressTick(100, 99999));
    }

    @Test
    void aPressOnTheBeatAsTheClientSawItIsJudgedOnTheBeat() {
        // the client sees the beat one way late and its press needs one way back: it arrives a whole round trip late
        BeatClock c = new BeatClock(0, 28);
        for (int rtt : new int[]{0, 40, 100, 150, 250}) {
            long arrival = 28 + Math.round(rtt / 50.0);
            long press = TugInputGate.pressTick(arrival, rtt);
            assertTrue(Math.abs(c.errorTo(press)) <= 1, "round trip " + rtt + " ms: error " + c.errorTo(press));
        }
    }
}
