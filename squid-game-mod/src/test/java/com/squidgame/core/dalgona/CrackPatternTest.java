package com.squidgame.core.dalgona;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrackPatternTest {
    @Test
    void sameSeedSamePatternDifferentSeedDifferentPattern() {
        List<CrackPattern.Branch> a = CrackPattern.generate(1234);
        List<CrackPattern.Branch> b = CrackPattern.generate(1234);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).threshold(), b.get(i).threshold(), 0);
            assertArrayEquals(a.get(i).xs(), b.get(i).xs());
            assertArrayEquals(a.get(i).ys(), b.get(i).ys());
        }
        List<CrackPattern.Branch> c = CrackPattern.generate(1235);
        boolean differs = a.size() != c.size() || !java.util.Arrays.equals(a.get(0).xs(), c.get(0).xs());
        assertTrue(differs);
    }

    @Test
    void branchesStayOnTheCanvasAndAppearInOrder() {
        for (long seed = 0; seed < 40; seed++) {
            List<CrackPattern.Branch> list = CrackPattern.generate(seed);
            assertTrue(list.size() >= 8 && list.size() <= 25, "branch count " + list.size());
            double last = -1;
            for (CrackPattern.Branch b : list) {
                assertTrue(b.threshold() >= last, "sorted by threshold");
                last = b.threshold();
                assertTrue(b.threshold() >= 0 && b.threshold() <= 100);
                assertTrue(b.segments() >= 3);
                assertEquals(b.xs().length, b.ys().length);
                for (int i = 0; i < b.xs().length; i++) {
                    assertTrue(b.xs()[i] >= 0 && b.xs()[i] <= DalgonaShape.MAX_COORD);
                    assertTrue(b.ys()[i] >= 0 && b.ys()[i] <= DalgonaShape.MAX_COORD);
                }
            }
            assertTrue(list.get(0).threshold() < 25, "the first hairline shows early");
            assertTrue(list.get(list.size() - 1).threshold() > 60, "the last cracks only show on the verge of breaking");
        }
    }

    @Test
    void cracksGrowWithStress() {
        CrackPattern.Branch b = CrackPattern.generate(7).get(0);
        assertEquals(0, CrackPattern.revealed(b, 0), 0);
        assertEquals(0, CrackPattern.revealed(b, b.threshold()), 0);
        double mid = CrackPattern.revealed(b, b.threshold() + CrackPattern.SPAN / 2);
        assertEquals(b.segments() / 2.0, mid, 1e-9);
        assertEquals(b.segments(), CrackPattern.revealed(b, b.threshold() + CrackPattern.SPAN), 1e-9);
        assertEquals(b.segments(), CrackPattern.revealed(b, 100), 1e-9);
        double prev = -1;
        for (double s = 0; s <= 100; s += 2.5) {
            double r = CrackPattern.revealed(b, s);
            assertTrue(r >= prev);
            prev = r;
        }
    }
}
