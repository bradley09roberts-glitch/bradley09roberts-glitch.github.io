package com.squidgame.core.finale;

import com.squidgame.core.finale.CourtGeometry.Pt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CourtGeometryTest {
    private static final CourtGeometry COURT = SquidShape.court();

    @Test
    void spawnsAndGoalAreInsideTheCourt() {
        assertTrue(COURT.contains(COURT.attackerSpawn().x(), COURT.attackerSpawn().z()));
        assertTrue(COURT.contains(COURT.defenderSpawn().x(), COURT.defenderSpawn().z()));
        assertTrue(COURT.contains(COURT.circleCenter().x(), COURT.circleCenter().z()));
        assertTrue(COURT.contains(COURT.neckCenter().x(), COURT.neckCenter().z()));
    }

    @Test
    void pointsOutsideTheSquidAreOutside() {
        assertFalse(COURT.contains(20, 0), "far to the east");
        assertFalse(COURT.contains(0.5, -30), "above the head");
        assertFalse(COURT.contains(0.5, 30), "below the tail");
        // beside the neck: the neck is only 5 wide while the square is 15 wide
        assertFalse(COURT.contains(6, 7.5));
        assertTrue(COURT.contains(6, 15));
    }

    @Test
    void edgeDistanceIsSignedAndExact() {
        // the square's bottom edge is at z = 25 (ZQ + 1), the left edge at x = -7
        assertEquals(1.0, COURT.edgeDistance(0.5, 24.0), 1e-9);
        assertEquals(-1.0, COURT.edgeDistance(0.5, 26.0), 1e-9);
        assertEquals(2.5, COURT.edgeDistance(-4.5, 15.0), 1e-9);
        assertTrue(COURT.edgeDistance(0.5, 7.5) > 0, "the neck centre is inside");
        assertEquals(2.5, COURT.edgeDistance(0.5, 7.5), 1e-9, "5 wide neck: 2.5 to either wall");
    }

    @Test
    void outMarginMakesTheLineItselfDangerous() {
        assertTrue(COURT.isIn(0.5, 24.0));
        assertTrue(COURT.isIn(0.5, 24.7), "inside, but within the margin of the outer edge: still in");
        assertFalse(COURT.isIn(0.5, 24.85), "closer than the out margin to the outer edge: out");
        assertFalse(COURT.isIn(0.5, 25.4));
    }

    @Test
    void rayExitMeasuresThePushDistance() {
        // from the square's centre straight to the east wall (x = 8): 7.5 blocks
        assertEquals(7.5, COURT.rayExit(0.5, 17.0, 1, 0, 50), 1e-9);
        // straight down the axis from the attacker spawn: the tail edge is 3.5 away
        assertEquals(3.5, COURT.rayExit(0.5, 21.5, 0, 1, 50), 1e-9);
        // capped
        assertEquals(2.0, COURT.rayExit(0.5, 17.0, 1, 0, 2.0), 1e-9);
        assertEquals(0.0, COURT.rayExit(20, 0, 1, 0, 5), "a point outside has no room");
    }

    @Test
    void nearestEdgeAndInwardNormal() {
        Pt e = COURT.nearestEdgePoint(0.5, 24.0);
        assertEquals(0.5, e.x(), 1e-9);
        assertEquals(25.0, e.z(), 1e-9);
        Pt in = COURT.inward(0.5, 24.0);
        assertEquals(0.0, in.x(), 1e-9);
        assertEquals(-1.0, in.z(), 1e-9);
        Pt out = COURT.inward(0.5, 26.0);
        assertEquals(-1.0, out.z(), 1e-9, "from outside the inward direction points back into the court");
    }

    @Test
    void clampInsidePullsPointsBackBehindTheMargin() {
        Pt p = COURT.clampInside(new Pt(0.5, 26.0), 1.0);
        assertTrue(COURT.edgeDistance(p.x(), p.z()) >= 0.99);
        Pt same = COURT.clampInside(new Pt(0.5, 15.0), 1.0);
        assertEquals(15.0, same.z(), 1e-9);
    }

    @Test
    void circleCaptureUsesTheGoldenRingInTheHead() {
        Pt c = COURT.circleCenter();
        assertEquals(3.0, COURT.captureRadius(), 1e-9, "60 % of the 5 block circle: the painted gold ring");
        assertTrue(COURT.inCircle(c.x(), c.z()));
        assertTrue(COURT.inCircle(c.x() + 2.9, c.z()));
        assertFalse(COURT.inCircle(c.x() + 3.1, c.z()));
        assertFalse(COURT.inCircle(c.x() + 4.4, c.z()), "standing on the white line is not enough");
        assertFalse(COURT.inCircle(COURT.attackerSpawn().x(), COURT.attackerSpawn().z()));
    }

    @Test
    void axisProgressAndNeck() {
        assertEquals(0.0, COURT.progress(COURT.attackerSpawn().x(), COURT.attackerSpawn().z()), 1e-9);
        assertEquals(1.0, COURT.progress(COURT.circleCenter().x(), COURT.circleCenter().z()), 1e-9);
        assertTrue(COURT.inNeck(0.5, 7.5));
        assertTrue(COURT.inNeck(0.5, 5.0));
        assertFalse(COURT.inNeck(0.5, 15.0), "the square is not the neck");
        assertFalse(COURT.inNeck(0.5, -5.0), "nor is the triangle");
        assertEquals(0.0, COURT.lateral(0.5, 10), 1e-9);
    }

    @Test
    void polygonMustHaveThreeVertices() {
        assertThrows(IllegalArgumentException.class, () -> new CourtGeometry(List.of(new Pt(0, 0), new Pt(1, 1)),
                new Pt(0, 0), 1, new Pt(0, 0), 1, new Pt(0, 0), new Pt(0, 0)));
    }

    @Test
    void lineCellsAreInsideThePolygonAndTheOutlineIsSane() {
        assertTrue(SquidShape.outline().size() >= 16, "the marker contract asks for at least 16 boundary vertices");
        for (int[] c : SquidShape.lineCells()) {
            assertTrue(COURT.contains(c[0] + 0.5, c[1] + 0.5), "line cell " + c[0] + "," + c[1] + " must be inside the outline");
        }
    }
}
