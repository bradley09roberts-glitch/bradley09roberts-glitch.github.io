package com.squidgame.core.dalgona;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DalgonaShapeTest {
    @Test
    void samplesAreEvenlySpacedAndLieOnTheOutline() {
        for (DalgonaShape s : DalgonaShape.values()) {
            assertTrue(s.sampleCount() > 200, s + " needs enough samples to measure progress");
            assertEquals(DalgonaShape.SPACING, s.sampleStep(), 0.1, s + " spacing");
            for (int i = 0; i < s.sampleCount(); i++) {
                int j = (i + 1) % s.sampleCount();
                double gap = Math.hypot(s.sampleX(j) - s.sampleX(i), s.sampleY(j) - s.sampleY(i));
                assertTrue(gap <= s.sampleStep() + 0.01, s + " sample gap " + gap + " at " + i);
                assertTrue(gap > 0.5, s + " samples must not coincide");
                assertTrue(s.distanceTo(s.sampleX(i), s.sampleY(i)) < 1e-3, s + " sample off the outline");
            }
        }
    }

    @Test
    void shapesFitInsideTheCookie() {
        for (DalgonaShape s : DalgonaShape.values()) {
            for (int i = 0; i < s.vertexCount(); i++) {
                double r = Math.hypot(s.vertexX(i) - DalgonaShape.CENTER, s.vertexY(i) - DalgonaShape.CENTER);
                assertTrue(r <= 440, s + " vertex too close to the rim: " + r);
                assertTrue(s.vertexX(i) >= 0 && s.vertexX(i) <= DalgonaShape.MAX_COORD);
                assertTrue(s.vertexY(i) >= 0 && s.vertexY(i) <= DalgonaShape.MAX_COORD);
            }
        }
    }

    @Test
    void insideTestSeparatesFigureFromWaste() {
        for (DalgonaShape s : DalgonaShape.values()) {
            assertFalse(s.contains(8, 8), s + " corner is waste");
            assertFalse(s.contains(1015, 1015));
        }
        assertTrue(DalgonaShape.CIRCLE.contains(512, 512));
        assertTrue(DalgonaShape.TRIANGLE.contains(512, 512));
        assertTrue(DalgonaShape.STAR.contains(512, 512));
        assertTrue(DalgonaShape.UMBRELLA.contains(512, 250), "dome");
        assertTrue(DalgonaShape.UMBRELLA.contains(512, 700), "handle shaft");
        assertFalse(DalgonaShape.UMBRELLA.contains(300, 700), "beside the handle");
        assertFalse(DalgonaShape.CIRCLE.contains(512, 40));
    }

    @Test
    void distanceIsExactForTheCircle() {
        DalgonaShape c = DalgonaShape.CIRCLE;
        assertEquals(400, c.distanceTo(512, 512), 0.2);
        assertEquals(30, c.distanceTo(512 + 430, 512), 0.2);
        assertEquals(30, c.distanceTo(512, 512 - 370), 0.2);
    }

    @Test
    void nearestReportsTheArcPositionOfTheClosestPoint() {
        double[] arc = new double[1];
        for (DalgonaShape s : DalgonaShape.values()) {
            for (int i = 0; i < s.sampleCount(); i += 7) {
                double d = s.nearest(s.sampleX(i), s.sampleY(i), arc);
                assertTrue(d < 1e-3);
                int back = s.sampleAtArc(arc[0]);
                int diff = Math.abs(back - i);
                diff = Math.min(diff, s.sampleCount() - diff);
                assertTrue(diff <= 1, s + " sample " + i + " resolved to " + back);
            }
        }
    }

    @Test
    void fragilityFollowsTheGeometry() {
        DalgonaShape circle = DalgonaShape.CIRCLE;
        for (int i = 0; i < circle.sampleCount(); i++) {
            assertEquals(1.0, circle.fragility(i), 1e-6, "the circle is robust everywhere");
        }
        for (DalgonaShape s : DalgonaShape.values()) {
            for (int i = 0; i < s.sampleCount(); i++) {
                assertTrue(s.fragility(i) >= 1.0 && s.fragility(i) <= DalgonaShape.MAX_FRAGILITY + 1e-6);
            }
        }
        double starMax = 0;
        int starTipSample = 0;
        for (int i = 0; i < DalgonaShape.STAR.sampleCount(); i++) {
            if (DalgonaShape.STAR.fragility(i) > starMax) {
                starMax = DalgonaShape.STAR.fragility(i);
                starTipSample = i;
            }
        }
        assertTrue(starMax > 2.0, "star tips are fragile");
        // the most fragile star spot sits on a tip (far from the centre), not on the flat sides
        double tipR = Math.hypot(DalgonaShape.STAR.sampleX(starTipSample) - 512, DalgonaShape.STAR.sampleY(starTipSample) - 512);
        assertTrue(tipR > 330, "fragile star spot at radius " + tipR);
        assertTrue(fragileShare(DalgonaShape.UMBRELLA) > fragileShare(DalgonaShape.STAR) - 0.05);
        assertTrue(fragileShare(DalgonaShape.TRIANGLE) > 0.05 && fragileShare(DalgonaShape.TRIANGLE) < 0.4);
        // the umbrella's hook (lowest part of the figure) is among its most fragile spots
        double hook = 0;
        DalgonaShape u = DalgonaShape.UMBRELLA;
        for (int i = 0; i < u.sampleCount(); i++) {
            if (u.sampleY(i) > 840) {
                hook = Math.max(hook, u.fragility(i));
            }
        }
        assertTrue(hook > 2.0, "umbrella hook fragility " + hook);
    }

    private static double fragileShare(DalgonaShape s) {
        int n = 0;
        for (int i = 0; i < s.sampleCount(); i++) {
            if (s.fragility(i) > 1.4) {
                n++;
            }
        }
        return n / (double) s.sampleCount();
    }

    @Test
    void brittlenessIsOrderedByIntricacy() {
        assertTrue(DalgonaShape.CIRCLE.brittleness <= DalgonaShape.TRIANGLE.brittleness);
        assertTrue(DalgonaShape.TRIANGLE.brittleness < DalgonaShape.STAR.brittleness);
        assertTrue(DalgonaShape.STAR.brittleness < DalgonaShape.UMBRELLA.brittleness);
    }

    @Test
    void ordinalLookupIsSafe() {
        assertEquals(DalgonaShape.STAR, DalgonaShape.byOrdinal(2));
        assertEquals(DalgonaShape.CIRCLE, DalgonaShape.byOrdinal(-1));
        assertEquals(DalgonaShape.CIRCLE, DalgonaShape.byOrdinal(99));
        assertEquals("squidgame.game.dalgona.shape.umbrella", DalgonaShape.UMBRELLA.translationKey());
    }
}
