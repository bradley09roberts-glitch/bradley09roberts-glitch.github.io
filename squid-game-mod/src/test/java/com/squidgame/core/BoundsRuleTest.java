package com.squidgame.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundsRuleTest {
    @Test
    void warningThenPutBackThenElimination() {
        int strikes = 0;
        BoundsRule.Step s = BoundsRule.step(true, strikes);
        assertEquals(BoundsRule.Action.WARN, s.action());
        s = BoundsRule.step(true, s.strikes());
        assertEquals(BoundsRule.Action.WARN_AND_PUT_BACK, s.action());
        s = BoundsRule.step(true, s.strikes());
        assertEquals(BoundsRule.Action.WARN_AND_PUT_BACK, s.action());
        s = BoundsRule.step(true, s.strikes());
        assertEquals(BoundsRule.Action.ELIMINATE, s.action());
        assertEquals(4, s.strikes());
    }

    @Test
    void comingBackInsideResetsTheCount() {
        BoundsRule.Step s = BoundsRule.step(true, 0);
        s = BoundsRule.step(true, s.strikes());
        s = BoundsRule.step(false, s.strikes());
        assertEquals(0, s.strikes());
        assertEquals(BoundsRule.Action.NONE, s.action());
        assertEquals(BoundsRule.Action.WARN, BoundsRule.step(true, s.strikes()).action());
    }

    @Test
    void theMarginAroundTheRegionIsTolerated() {
        // region x 944..1056, z -6..178 (block extents): the far edge is x = 1057
        assertFalse(BoundsRule.outside(1059.9, 10, 944, 1056, -6, 178));
        assertTrue(BoundsRule.outside(1060.1, 10, 944, 1056, -6, 178));
        assertFalse(BoundsRule.outside(1000, -8.9, 944, 1056, -6, 178));
        assertTrue(BoundsRule.outside(1000, -9.1, 944, 1056, -6, 178));
        assertFalse(BoundsRule.outside(1000, 181.9, 944, 1056, -6, 178));
        assertTrue(BoundsRule.outside(1000, 182.1, 944, 1056, -6, 178));
        assertFalse(BoundsRule.outside(1000, 90, 944, 1056, -6, 178));
    }
}
