package com.squidgame.core;

import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RngTest {
    @Test
    void sameSeedSameSequence() {
        Rng a = new Rng(42), b = new Rng(42);
        for (int i = 0; i < 100; i++) {
            assertEquals(a.nextInt(1000), b.nextInt(1000));
        }
    }

    @Test
    void forksAreReproducibleAndIndependentOfParentUse() {
        Rng a = new Rng(7), b = new Rng(7);
        a.nextInt(10);
        a.nextInt(10); // consuming the parent must not change its forks
        assertEquals(a.fork(3).nextDouble(), b.fork(3).nextDouble());
        assertNotEquals(a.fork(3).nextDouble(), a.fork(4).nextDouble());
    }

    @Test
    void rangeIntIsInclusiveAndBounded() {
        Rng r = new Rng(1);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            int v = r.rangeInt(3, 6);
            assertTrue(v >= 3 && v <= 6);
            seen.add(v);
        }
        assertEquals(4, seen.size());
        assertEquals(5, r.rangeInt(5, 5));
        assertEquals(5, r.rangeInt(5, 2));
    }

    @Test
    void shuffleIsAPermutation() {
        Rng r = new Rng(9);
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            list.add(i);
        }
        List<Integer> copy = new ArrayList<>(list);
        r.shuffle(copy);
        assertNotEquals(list, copy);
        assertEquals(new HashSet<>(list), new HashSet<>(copy));
    }

    @Test
    void chanceExtremes() {
        Rng r = new Rng(5);
        for (int i = 0; i < 200; i++) {
            assertFalse(r.chance(0.0));
            assertTrue(r.chance(1.0));
        }
    }

    @Test
    void clampHelpers() {
        assertEquals(0.0, Rng.clamp01(-3));
        assertEquals(1.0, Rng.clamp01(3));
        assertEquals(5, Rng.clamp(9, 1, 5));
        assertEquals(1.5, Rng.lerp(1, 2, 0.5));
    }
}
