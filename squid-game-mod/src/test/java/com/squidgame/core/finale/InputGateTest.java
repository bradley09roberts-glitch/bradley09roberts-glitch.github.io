package com.squidgame.core.finale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** What the server accepts from a client in the final: only known messages with legal values, never too many. */
class InputGateTest {
    @Test
    void knownMessagesParse() {
        InputGate.Command atk = InputGate.parse(InputGate.ID_ATTACK, true, 0);
        assertEquals(InputGate.Kind.ATTACK, atk.kind());
        assertTrue(atk.down());
        assertFalse(InputGate.parse(InputGate.ID_ATTACK, false, 0).down());
        assertEquals(InputGate.Kind.GUARD, InputGate.parse(InputGate.ID_GUARD, true, 0).kind());
        assertFalse(InputGate.parse(InputGate.ID_GUARD, false, 0).down());
        assertEquals(InputGate.Kind.SHOVE, InputGate.parse(InputGate.ID_SHOVE, true, 0).kind());
    }

    @Test
    void shoveAndDodgeAreAlwaysPresses() {
        assertTrue(InputGate.parse(InputGate.ID_SHOVE, false, 0).down(), "a shove has no 'up': a stray flag cannot cancel it");
        InputGate.Command dodge = InputGate.parse(InputGate.ID_DODGE, false, 3);
        assertEquals(InputGate.Kind.DODGE, dodge.kind());
        assertTrue(dodge.down());
        assertEquals(3, dodge.sector());
    }

    @Test
    void unknownIdsAndIllegalSectorsAreRejected() {
        assertNull(InputGate.parse(null, true, 0));
        assertNull(InputGate.parse("", true, 0));
        assertNull(InputGate.parse("finale.cheat", true, 0));
        assertNull(InputGate.parse("marbles.release", true, 0));
        assertNull(InputGate.parse(InputGate.ID_DODGE, true, -1));
        assertNull(InputGate.parse(InputGate.ID_DODGE, true, 8));
        assertNull(InputGate.parse(InputGate.ID_DODGE, true, Integer.MAX_VALUE));
        assertNull(InputGate.parse(InputGate.ID_DODGE, true, Integer.MIN_VALUE));
        for (int s = 0; s < 8; s++) {
            assertNotNull(InputGate.parse(InputGate.ID_DODGE, true, s), "sector " + s);
        }
    }

    @Test
    void aBurstIsLimitedAndTheBucketRefills() {
        InputGate gate = new InputGate();
        int accepted = 0;
        for (int i = 0; i < 100; i++) {
            if (gate.allow(1000)) {
                accepted++;
            }
        }
        assertEquals(40, accepted, "a flood in one tick gets the burst size and nothing more");
        assertFalse(gate.allow(1000));
        assertTrue(gate.allow(1002), "after two ticks there are tokens again");
        int later = 0;
        for (int i = 0; i < 100; i++) {
            if (gate.allow(1102)) {
                later++;
            }
        }
        assertEquals(40, later, "the bucket refills up to the burst size, not beyond");
    }

    @Test
    void anHonestPlayerIsNeverLimited() {
        // about the most a client can send: a very fast clicker (10 clicks a second = a press and a release each),
        // the heartbeats of a held guard, and four shoves or dodges a second
        InputGate gate = new InputGate();
        long tick = 5000;
        for (int second = 0; second < 60; second++) {
            for (int t = 0; t < 20; t++, tick++) {
                int messages = 0;
                if (t % 2 == 0) {
                    messages += 2;
                }
                if (t % 8 == 0) {
                    messages += 1;
                }
                if (t % 5 == 0) {
                    messages += 1;
                }
                for (int m = 0; m < messages; m++) {
                    assertTrue(gate.allow(tick), "second " + second + " tick " + t);
                }
            }
        }
    }

    @Test
    void aSustainedFloodIsCappedNearTheRefillRate() {
        InputGate gate = new InputGate();
        int accepted = 0;
        for (long t = 0; t < 200; t++) {
            for (int i = 0; i < 20; i++) {
                if (gate.allow(t)) {
                    accepted++;
                }
            }
        }
        // 40 burst + 1.5 per tick over 199 ticks
        assertTrue(accepted <= 40 + 1.5 * 200 + 1, "accepted " + accepted);
        assertTrue(accepted >= 1.5 * 199, "but the refill rate is honoured: " + accepted);
    }

    @Test
    void aClockThatGoesBackwardsDoesNotGiveFreeTokens() {
        InputGate gate = new InputGate();
        for (int i = 0; i < 40; i++) {
            assertTrue(gate.allow(500));
        }
        assertFalse(gate.allow(500));
        assertFalse(gate.allow(100), "an earlier tick refills nothing");
        assertFalse(gate.allow(500), "and does not rewind the clock either");
    }

    @Test
    void dodgeSectorsAreRelativeToTheViewDirection() {
        // looking south (+Z, yaw 0): forward = +Z, right = -X (the player's right hand), back = -Z, left = +X
        double[] fwd = InputGate.dodgeDirection(0, 0);
        assertEquals(0, fwd[0], 1e-9);
        assertEquals(1, fwd[1], 1e-9);
        double[] right = InputGate.dodgeDirection(0, 2);
        assertEquals(-1, right[0], 1e-9);
        assertEquals(0, right[1], 1e-9);
        double[] back = InputGate.dodgeDirection(0, 4);
        assertEquals(-1, back[1], 1e-9);
        double[] left = InputGate.dodgeDirection(0, 6);
        assertEquals(1, left[0], 1e-9);
        // looking west (yaw 90, -X): forward = -X
        double[] west = InputGate.dodgeDirection(90, 0);
        assertEquals(-1, west[0], 1e-9);
        assertEquals(0, west[1], 1e-9);
        for (int s = 0; s < 8; s++) {
            double[] d = InputGate.dodgeDirection(37, s);
            assertEquals(1.0, Math.hypot(d[0], d[1]), 1e-9, "unit vectors");
        }
    }

    @Test
    void aHeldButtonExpiresAfterTheTimeoutWhichIsLongerThanTheHeartbeat() {
        assertTrue(InputGate.HOLD_TIMEOUT > 8 * 2, "at least two missed heartbeats (the client resends every 8 ticks) before a release");
        assertTrue(InputGate.HOLD_TIMEOUT <= FinaleRules.HEAVY_MAX_HOLD, "a lost 'up' never keeps a charge or a guard for long");
    }
}
