package com.squidgame.core.finale;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The reference shape of the painted squid, which the unit tests and the fixture arena share with the real arena. */
class SquidShapeTest {
    @Test
    void theOutlineIsASimplePolygon() {
        List<double[]> v = SquidShape.outline();
        int n = v.size();
        assertTrue(n >= 16);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                boolean adjacent = j == i + 1 || (i == 0 && j == n - 1);
                if (adjacent) {
                    continue;
                }
                assertFalse(intersect(v.get(i), v.get((i + 1) % n), v.get(j), v.get((j + 1) % n)),
                        "edges " + i + " and " + j + " must not cross");
            }
        }
    }

    @Test
    void theOutlineRunsClockwiseFromTheTopOfTheHead() {
        List<double[]> v = SquidShape.outline();
        double[] top = v.get(0);
        assertEquals(0.5, top[0], 1e-9, "vertex 0 is the top of the head circle");
        assertEquals(SquidShape.ZC + 0.5 - (SquidShape.R + 0.5), top[1], 1e-9);
        double area = 0;
        for (int i = 0; i < v.size(); i++) {
            double[] a = v.get(i), b = v.get((i + 1) % v.size());
            area += a[0] * b[1] - b[0] * a[1];
        }
        assertTrue(area / 2 > 0, "clockwise on a map with north up (+Z is south)");
    }

    @Test
    void theDimensionsAreTheOnesOfTheRealArena() {
        CourtGeometry court = SquidShape.court();
        assertEquals(SquidShape.ZC + 0.5, court.circleCenter().z(), 1e-9);
        assertEquals(5.0, court.circleRadius(), 1e-9);
        // the whole squid is 15 wide and 50 long, as the contract says
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double[] p : SquidShape.outline()) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[1]);
            maxZ = Math.max(maxZ, p[1]);
        }
        assertEquals(15.0, maxX - minX, 1e-9);
        assertEquals(50.0, maxZ - minZ, 1e-9);
        // attacker in the square, defender in the triangle
        assertTrue(court.attackerSpawn().z() > SquidShape.ZS);
        assertTrue(court.defenderSpawn().z() < SquidShape.ZB && court.defenderSpawn().z() > SquidShape.ZC + SquidShape.R);
        // the neck is 3 wide between the lines
        assertEquals(3.0, court.neckWidth(), 1e-9);
    }

    @Test
    void shiftingMovesEveryPointByTheOrigin() {
        CourtGeometry a = SquidShape.court();
        CourtGeometry b = SquidShape.court(6000, -250);
        assertEquals(a.circleCenter().x() + 6000, b.circleCenter().x(), 1e-9);
        assertEquals(a.circleCenter().z() - 250, b.circleCenter().z(), 1e-9);
        for (double[] p : new double[][]{{0.5, 24}, {0.5, 26}, {5, 3}, {-3, 12}, {0.5, -19.5}}) {
            assertEquals(a.contains(p[0], p[1]), b.contains(p[0] + 6000, p[1] - 250));
            assertEquals(a.edgeDistance(p[0], p[1]), b.edgeDistance(p[0] + 6000, p[1] - 250), 1e-9);
        }
    }

    @Test
    void theLineCellsFormClosedWallsWithTheHeadOpenedTowardsTheTriangle() {
        List<int[]> cells = SquidShape.lineCells();
        Set<Long> set = new HashSet<>();
        for (int[] c : cells) {
            assertTrue(set.add(((long) c[0] << 32) | (c[1] & 0xffffffffL)), "no cell twice");
        }
        assertTrue(cells.size() > 120 && cells.size() < 220, "a squid painted with one block wide lines: " + cells.size() + " cells");
        // the head circle's lowest arc is open: three cells (the neck's width) in the middle of the row below the circle
        for (int x = -1; x <= 1; x++) {
            assertFalse(set.contains(((long) x << 32) | ((SquidShape.ZC + SquidShape.R) & 0xffffffffL)),
                    "the head is open to the triangle at x=" + x);
        }
        // the base of the triangle, the walls of the neck and the tail are painted
        assertTrue(set.contains(((long) 5 << 32) | (SquidShape.ZB & 0xffffffffL)));
        assertTrue(set.contains(((long) SquidShape.STEM << 32) | ((SquidShape.ZB + 2) & 0xffffffffL)));
        assertTrue(set.contains(((long) 0 << 32) | (SquidShape.ZQ & 0xffffffffL)));
        assertFalse(set.contains(((long) 0 << 32) | (SquidShape.ZB & 0xffffffffL)), "the neck's mouth is open at the base");
    }

    private static boolean intersect(double[] p1, double[] p2, double[] p3, double[] p4) {
        double d1 = cross(p3, p4, p1), d2 = cross(p3, p4, p2), d3 = cross(p1, p2, p3), d4 = cross(p1, p2, p4);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static double cross(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }
}
