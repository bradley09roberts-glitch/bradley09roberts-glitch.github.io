package com.squidgame.core.dalgona;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StrokeValidatorTest {
    private static int[] pts(int... xy) {
        int[] out = new int[xy.length / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = StrokeValidator.pack(xy[2 * i], xy[2 * i + 1]);
        }
        return out;
    }

    private static StrokeValidator.Verdict check(StrokeValidator v, long now, int[] p, int ms, boolean start, boolean end,
                                                 boolean has, double sx, double sy) {
        return v.check(now, p, p.length, ms, start, end, has, sx, sy);
    }

    @Test
    void acceptsAWellFormedMessageAndKeepsThePoints() {
        StrokeValidator v = new StrokeValidator();
        StrokeValidator.Verdict r = check(v, 100, pts(100, 100, 106, 100, 112, 101), 100, true, false, false, 0, 0);
        assertTrue(r.accepted);
        assertEquals(3, r.n);
        assertEquals(106, r.xs[1], 0);
        assertEquals(101, r.ys[2], 0);
        assertTrue(r.newStroke);
        assertEquals(0, r.penalty, 0);
        assertFalse(r.tamper);
        assertTrue(r.dt >= CookieSim.MIN_DT && r.dt <= 2.0, "credited " + r.dt);
    }

    @Test
    void packingRoundTripsTheWholeCanvas() {
        for (int x : new int[]{0, 1, 511, 1023}) {
            for (int y : new int[]{0, 7, 512, 1023}) {
                StrokeValidator.Verdict r = check(new StrokeValidator(), 10, pts(x, y), 50, true, true, false, 0, 0);
                assertTrue(r.accepted);
                assertEquals(x, r.xs[0], 0);
                assertEquals(y, r.ys[0], 0);
            }
        }
    }

    @Test
    void rejectsEmptyOversizedAndOutOfBoundsMessages() {
        StrokeValidator v = new StrokeValidator();
        assertEquals(StrokeValidator.Reject.EMPTY, v.check(1, new int[0], 0, 50, true, false, false, 0, 0).reject);
        assertEquals(StrokeValidator.Reject.EMPTY, v.check(2, null, 3, 50, true, false, false, 0, 0).reject);
        int[] many = new int[StrokeValidator.MAX_POINTS + 1];
        for (int i = 0; i < many.length; i++) {
            many[i] = StrokeValidator.pack(10 + i, 10);
        }
        StrokeValidator.Verdict big = v.check(3, many, many.length, 50, true, false, false, 0, 0);
        assertFalse(big.accepted);
        assertEquals(StrokeValidator.Reject.TOO_MANY, big.reject);
        assertEquals(1, v.strikes());
        // x = 1024 does not fit the canvas
        int bad = (1024 << 10) | 5;
        StrokeValidator.Verdict oob = v.check(4, new int[]{bad}, 1, 50, true, false, false, 0, 0);
        assertEquals(StrokeValidator.Reject.BOUNDS, oob.reject);
        assertEquals(2, v.strikes());
        // a count larger than the array is rejected, not read
        assertFalse(v.check(5, new int[]{StrokeValidator.pack(1, 1)}, 5, 50, true, false, false, 0, 0).accepted);
        // negative packed values (a forged sign bit) are out of bounds too
        assertEquals(StrokeValidator.Reject.BOUNDS, v.check(6, new int[]{-1}, 1, 50, true, false, false, 0, 0).reject);
    }

    @Test
    void maxPointsIsAccepted() {
        StrokeValidator v = new StrokeValidator();
        int[] max = new int[StrokeValidator.MAX_POINTS];
        for (int i = 0; i < max.length; i++) {
            max[i] = StrokeValidator.pack(100 + i, 100);
        }
        assertTrue(v.check(1, max, max.length, 300, true, false, false, 0, 0).accepted);
    }

    @Test
    void rateLimitDropsFloodsButNotNormalTraffic() {
        StrokeValidator v = new StrokeValidator();
        int accepted = 0;
        for (int i = 0; i < 50; i++) {
            if (check(v, 50, pts(200, 200), 50, i == 0, false, i > 0, 200, 200).accepted) {
                accepted++;
            }
        }
        assertEquals((int) StrokeValidator.BUCKET_CAP, accepted, "a burst within one tick is capped");
        // normal traffic: one message every two ticks for a while never trips the limit
        StrokeValidator calm = new StrokeValidator();
        for (int i = 0; i < 400; i++) {
            assertTrue(check(calm, 1000 + 2L * i, pts(200, 200), 100, i == 0, false, i > 0, 200, 200).accepted, "message " + i);
        }
        // and even one message per tick is fine
        StrokeValidator brisk = new StrokeValidator();
        for (int i = 0; i < 400; i++) {
            assertTrue(check(brisk, 1000 + i, pts(200, 200), 50, i == 0, false, i > 0, 200, 200).accepted, "message " + i);
        }
    }

    @Test
    void timeIsCreditedFromTheServersClockNotTheClaim() {
        StrokeValidator v = new StrokeValidator();
        // a client claiming 600 ms (12 ticks) for a message sent 2 ticks after the previous one only gets what it earned
        check(v, 100, pts(100, 100), 50, true, false, false, 0, 0);
        StrokeValidator.Verdict r = check(v, 102, pts(110, 100), 600, false, false, true, 100, 100);
        assertTrue(r.accepted);
        assertTrue(r.dt < 6.5, "credited " + r.dt);
        // after a long pause the credit is capped, a stroke start never starts with more than a few ticks
        StrokeValidator.Verdict start = check(v, 5000, pts(300, 300), 600, true, false, true, 110, 100);
        assertTrue(start.dt <= StrokeValidator.START_CREDIT + 1e-9, "credited " + start.dt);
        // claiming nothing is clamped to the minimum (speed then looks high, which only hurts the cheater)
        StrokeValidator.Verdict zero = check(v, 5002, pts(301, 300), 0, false, false, true, 300, 300);
        assertEquals(CookieSim.MIN_DT, zero.dt, 1e-9);
    }

    @Test
    void anOverFastPathIsCutOffAndPenalised() {
        StrokeValidator v = new StrokeValidator();
        check(v, 100, pts(100, 100), 50, true, false, false, 0, 0);
        // 800 units in one tick while the needle is down: only MAX_PATH_SPEED * credited ticks are allowed
        StrokeValidator.Verdict r = check(v, 101, pts(900, 100), 50, false, false, true, 100, 100);
        assertTrue(r.accepted);
        assertTrue(r.penalty >= StrokeValidator.TELEPORT_PENALTY);
        double travelled = r.xs[r.n - 1] - 100;
        assertTrue(travelled <= StrokeValidator.MAX_PATH_SPEED * r.dt + 1e-6, "travelled " + travelled);
        assertTrue(travelled > 0);
        assertEquals(1, v.strikes());
    }

    @Test
    void teleportingBetweenStrokesIsPenalisedButAFlickIsNot() {
        StrokeValidator v = new StrokeValidator();
        check(v, 100, pts(100, 100), 50, true, true, false, 0, 0);
        // lifted needle: 400 units in 8 ticks = 50 units per tick: fine
        StrokeValidator.Verdict flick = check(v, 108, pts(500, 100), 50, true, false, true, 100, 100);
        assertEquals(0, flick.penalty, 0);
        // 900 units the very next tick: more than any hand can do
        check(v, 109, pts(500, 100), 50, false, true, true, 500, 100);
        StrokeValidator.Verdict jump = check(v, 110, pts(100, 900 - 10), 50, true, false, true, 500, 100);
        assertTrue(jump.accepted);
        assertTrue(jump.penalty >= StrokeValidator.TELEPORT_PENALTY);
    }

    @Test
    void aContinuationWithoutAStartIsTreatedAsANewStroke() {
        StrokeValidator v = new StrokeValidator();
        StrokeValidator.Verdict r = check(v, 10, pts(50, 50, 55, 50), 100, false, false, false, 0, 0);
        assertTrue(r.newStroke, "the server never saw this stroke begin");
        StrokeValidator.Verdict next = check(v, 12, pts(60, 50), 100, false, false, true, 55, 50);
        assertFalse(next.newStroke);
        check(v, 14, pts(65, 50), 100, false, true, true, 60, 50);
        assertTrue(check(v, 16, pts(65, 50), 100, false, false, true, 65, 50).newStroke, "after an end the next message starts a stroke");
    }

    @Test
    void repeatedViolationsMarkTheClientAsTamperingAndStrikesFade() {
        StrokeValidator v = new StrokeValidator();
        int bad = (2000 << 10) | 5;
        boolean tamper = false;
        for (int i = 0; i < StrokeValidator.STRIKE_LIMIT; i++) {
            tamper = v.check(10 + i, new int[]{bad}, 1, 50, true, false, false, 0, 0).tamper;
        }
        assertTrue(tamper);
        // an honest client that was flagged once recovers
        StrokeValidator honest = new StrokeValidator();
        honest.check(1, new int[]{bad}, 1, 50, true, false, false, 0, 0);
        assertEquals(1, honest.strikes());
        honest.check(1 + StrokeValidator.STRIKE_DECAY_TICKS + 5, pts(10, 10), 1, 50, true, false, false, 0, 0);
        assertEquals(0, honest.strikes());
    }

    @Test
    void resetForgetsStrokeContinuity() {
        StrokeValidator v = new StrokeValidator();
        check(v, 10, pts(10, 10), 50, true, false, false, 0, 0);
        v.reset();
        assertTrue(check(v, 12, pts(12, 10), 50, false, false, true, 10, 10).newStroke);
    }
}
