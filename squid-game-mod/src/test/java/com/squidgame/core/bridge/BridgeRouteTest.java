package com.squidgame.core.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BridgeRouteTest {
    @Test
    void sameSeedAndGameGiveTheSameRoute() {
        BridgeRoute a = BridgeRoute.forGame(123456789L, 5, BridgeRoute.DEFAULT_ROWS);
        BridgeRoute b = BridgeRoute.forGame(123456789L, 5, BridgeRoute.DEFAULT_ROWS);
        assertEquals(a, b);
        assertEquals(a.toBits(), b.toBits());
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentSeedsAndGameNumbersGiveDifferentRoutes() {
        BridgeRoute base = BridgeRoute.forGame(1L, 5, BridgeRoute.DEFAULT_ROWS);
        int different = 0;
        for (long seed = 2; seed < 60; seed++) {
            if (!BridgeRoute.forGame(seed, 5, BridgeRoute.DEFAULT_ROWS).equals(base)) {
                different++;
            }
        }
        assertTrue(different >= 57, "routes of different seeds should (almost) never collide: " + different);
        assertNotEquals(BridgeRoute.forGame(1L, 4, 18), BridgeRoute.forGame(1L, 5, 18));
    }

    @Test
    void exactlyOneSafeLanePerRow() {
        BridgeRoute r = BridgeRoute.generate(99L, 18);
        for (int row = 0; row < 18; row++) {
            int safe = r.safeLane(row);
            assertTrue(safe == 0 || safe == 1);
            assertTrue(r.isSafe(row, safe));
            assertFalse(r.isSafe(row, 1 - safe));
        }
    }

    @Test
    void persistenceRoundTripKeepsTheRoute() {
        for (long seed = 0; seed < 200; seed++) {
            BridgeRoute r = BridgeRoute.forGame(seed * 7919, 3, 18);
            BridgeRoute back = BridgeRoute.ofBits(r.toBits(), r.rows());
            assertEquals(r, back);
            for (int row = 0; row < 18; row++) {
                assertEquals(r.safeLane(row), back.safeLane(row));
            }
        }
    }

    @Test
    void lanesAreFairAndRowsIndependent() {
        int lane1 = 0, same = 0, total = 0, pairs = 0;
        for (long seed = 0; seed < 3000; seed++) {
            BridgeRoute r = BridgeRoute.forGame(seed, 2, 18);
            for (int row = 0; row < 18; row++) {
                total++;
                lane1 += r.safeLane(row);
                if (row > 0) {
                    pairs++;
                    same += r.safeLane(row) == r.safeLane(row - 1) ? 1 : 0;
                }
            }
        }
        assertEquals(0.5, lane1 / (double) total, 0.02, "lane 1 should be safe half of the time");
        assertEquals(0.5, same / (double) pairs, 0.02, "a row must say nothing about the next one");
    }

    @Test
    void firstRowIsNotBiased() {
        int lane1 = 0;
        for (long seed = 0; seed < 4000; seed++) {
            lane1 += BridgeRoute.forGame(seed, 1, 18).safeLane(0);
        }
        assertEquals(0.5, lane1 / 4000.0, 0.03);
    }

    @Test
    void toStringNeverLeaksTheLanes() {
        BridgeRoute r = BridgeRoute.generate(5L, 18);
        String s = r.toString();
        assertTrue(s.contains("hidden"));
        assertFalse(s.contains(Long.toBinaryString(r.toBits())));
        assertFalse(s.contains(Long.toString(r.toBits())));
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> BridgeRoute.generate(1, 0));
        assertThrows(IllegalArgumentException.class, () -> BridgeRoute.ofBits(0, BridgeRoute.MAX_ROWS + 1));
        BridgeRoute r = BridgeRoute.generate(1, 18);
        assertThrows(IndexOutOfBoundsException.class, () -> r.safeLane(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> r.safeLane(18));
    }

    @Test
    void restoredBitsAreMaskedToTheRowCount() {
        BridgeRoute r = BridgeRoute.ofBits(-1L, 18);
        assertEquals((1L << 18) - 1, r.toBits());
        assertEquals(1, r.safeLane(17));
    }
}
