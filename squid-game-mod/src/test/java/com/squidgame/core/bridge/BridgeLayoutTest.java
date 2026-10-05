package com.squidgame.core.bridge;

import com.squidgame.core.bridge.BridgeLayout.Panel;
import com.squidgame.core.bridge.BridgeLayout.PanelSpec;
import com.squidgame.core.bridge.BridgeLayout.Rect;
import com.squidgame.core.bridge.BridgeLayout.Vec2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests on the documented arena geometry (world x/z = local + arena origin offset is irrelevant: shifted copy tested too). */
class BridgeLayoutTest {
    private static final double TOP = 41.0;

    /**
     * 18 rows x 2 lanes of 2x2 panels: lane 0 at x -3..-2, lane 1 at x 1..2, row r at z 10 + 3r .. 11 + 3r, with the
     * markers where the arena contract puts them (centre of the first block: x -2.5 / 1.5, z 10.5 + 3r).
     */
    private static BridgeLayout layout(double dx, double dz) {
        List<PanelSpec> specs = new ArrayList<>();
        for (int r = 0; r < 18; r++) {
            specs.add(new PanelSpec(r, 0, -2.5 + dx, 10.5 + 3 * r + dz, TOP));
            specs.add(new PanelSpec(r, 1, 1.5 + dx, 10.5 + 3 * r + dz, TOP));
        }
        Rect start = new Rect(-12 + dx, 12 + dx, -7 + dz, 9 + dz);
        Rect finish = new Rect(-12 + dx, 12 + dx, 64 + dz, 80 + dz);
        return BridgeLayout.of(specs, start, finish);
    }

    @Test
    void footprintsAreTwoByTwoBlocksOnTheDocumentedPositions() {
        BridgeLayout l = layout(0, 0);
        Rect p = l.panel(0, 0).rect();
        assertEquals(-3.0, p.minX());
        assertEquals(-1.0, p.maxX());
        assertEquals(10.0, p.minZ());
        assertEquals(12.0, p.maxZ());
        Rect q = l.panel(17, 1).rect();
        assertEquals(1.0, q.minX());
        assertEquals(3.0, q.maxX());
        assertEquals(61.0, q.minZ());
        assertEquals(63.0, q.maxZ());
    }

    @Test
    void footprintFollowsTheMarkerContractAndToleratesCornerCentres() {
        // contract: the marker is the centre of the panel's first block (x -2.5 / 1.5, z 10.5 + 3r)
        for (int r = 0; r < 18; r++) {
            assertEquals(new Rect(-3, -1, 10 + 3 * r, 12 + 3 * r), BridgeLayout.footprint(-2.5, 10.5 + 3 * r));
            assertEquals(new Rect(1, 3, 10 + 3 * r, 12 + 3 * r), BridgeLayout.footprint(1.5, 10.5 + 3 * r));
            // the same panel given by its corner centre
            assertEquals(BridgeLayout.footprint(-2.0, 11.0 + 3 * r), BridgeLayout.footprint(-2.5, 10.5 + 3 * r));
            assertEquals(BridgeLayout.footprint(2.0, 11.0 + 3 * r), BridgeLayout.footprint(1.5, 10.5 + 3 * r));
        }
    }

    @Test
    void worldCoordinatesOfTheRealArenaGiveTheSameFootprints() {
        // arena origin x = 5000: the marker x is 4997.5 / 5001.5
        assertEquals(new Rect(4997, 4999, 10, 12), BridgeLayout.footprint(4997.5, 10.5));
        assertEquals(new Rect(5001, 5003, 61, 63), BridgeLayout.footprint(5001.5, 61.5));
        assertEquals(new Rect(4997, 4999, 10, 12), BridgeLayout.footprint(4998.0, 11.0));
    }

    @Test
    void gapsBetweenRowsAndLanesHaveTheDocumentedWidth() {
        BridgeLayout l = layout(0, 0);
        for (int r = 0; r < 17; r++) {
            double gap = l.panel(r + 1, 0).rect().minZ() - l.panel(r, 0).rect().maxZ();
            assertEquals(1.0, gap, 1e-9, "1 block between rows");
        }
        double laneGap = l.panel(5, 1).rect().minX() - l.panel(5, 0).rect().maxX();
        assertEquals(2.0, laneGap, 1e-9, "2 blocks between lanes");
        assertEquals(1.0, l.panel(0, 0).rect().minZ() - l.start().maxZ(), 1e-9, "1 block between the platform and row 0");
        assertEquals(1.0, l.finish().minZ() - l.panel(17, 0).rect().maxZ(), 1e-9, "1 block between row 17 and the end platform");
    }

    @Test
    void panelLookupFindsOnlyPointsOnGlass() {
        BridgeLayout l = layout(0, 0);
        assertSame(l.panel(0, 0), l.panelAt(-2.5, 10.5));
        assertSame(l.panel(0, 1), l.panelAt(1.2, 11.9));
        assertNull(l.panelAt(0.0, 10.5), "the aisle between the lanes is air");
        assertNull(l.panelAt(-2.0, 12.5), "the gap between rows is air");
        assertNull(l.panelAt(-2.0, 9.5));
        assertSame(l.panel(17, 0), l.panelAt(-1.1, 62.9));
    }

    @Test
    void supportingPanelUsesTheWholeHitbox() {
        BridgeLayout l = layout(0, 0);
        assertSame(l.panel(3, 0), l.supporting(-2.0, 20.0));
        // centre 0.2 beyond the far edge of row 3 (z 19..21): the hitbox still overlaps the panel
        assertSame(l.panel(3, 0), l.supporting(-2.0, 21.2));
        // centre 0.4 beyond the edge: the 0.6 wide hitbox no longer touches anything
        assertNull(l.supporting(-2.0, 21.4));
        assertNull(l.supporting(0.0, 20.0));
        // a body in the gap, half over each of two panels never exists (gap is 1 wide), but nearest wins
        assertSame(l.panel(4, 1), l.supporting(1.9, 22.8));
    }

    @Test
    void lanesAreLabelledRelativeToTheDirectionOfTravel() {
        BridgeLayout l = layout(0, 0);
        assertEquals(-2.0, l.laneCenterX(0), 1e-9);
        assertEquals(2.0, l.laneCenterX(1), 1e-9);
        assertTrue(l.isLeftLane(1), "walking towards +Z, east (+X) is on the left");
        assertFalse(l.isLeftLane(0));
    }

    @Test
    void everyHopOfTheBridgeIsPossibleAndLandsSafelyInsideThePanel() {
        BridgeLayout l = layout(0, 0);
        double longest = 0;
        for (int r = 0; r < 17; r++) {
            for (int from = 0; from < 2; from++) {
                for (int to = 0; to < 2; to++) {
                    Panel a = l.panel(r, from), b = l.panel(r + 1, to);
                    Vec2 take = l.takeoff(a, b);
                    Vec2 land = l.landing(take, b);
                    assertInside(a.rect().inset(BridgeLayout.TAKEOFF_MARGIN - 1e-9), take);
                    assertInside(b.rect().inset(BridgeLayout.LANDING_INSET - 1e-9), land);
                    double d = take.distanceTo(land);
                    longest = Math.max(longest, d);
                    HopPlanner.Plan plan = HopPlanner.plan(d);
                    assertTrue(plan.speed() <= HopPlanner.MAX_SPEED, "row " + r + " " + from + "->" + to + " needs " + plan.speed());
                    assertTrue(plan.speed() <= 0.33, "leaps should look natural, got " + plan.speed());
                }
            }
        }
        assertTrue(longest < 4.0, "longest hop " + longest);
        assertTrue(longest > 3.3, "the diagonal lane change is the long one: " + longest);
    }

    @Test
    void straightHopsAreShortAndDiagonalOnesLonger() {
        BridgeLayout l = layout(0, 0);
        Vec2 take = l.takeoff(l.panel(4, 0), l.panel(5, 0));
        Vec2 land = l.landing(take, l.panel(5, 0));
        assertEquals(BridgeLayout.TAKEOFF_MARGIN + 1.0 + BridgeLayout.LANDING_INSET, take.distanceTo(land), 1e-9);
        assertEquals(-2.0, take.x(), 1e-9, "straight ahead keeps the lane");
        Vec2 dTake = l.takeoff(l.panel(4, 0), l.panel(5, 1));
        Vec2 dLand = l.landing(dTake, l.panel(5, 1));
        assertTrue(dTake.distanceTo(dLand) > take.distanceTo(land) + 1.0);
        assertTrue(dTake.x() > -2.0 && dLand.x() > dTake.x(), "the diagonal heads for the inner corner");
    }

    @Test
    void hopFromThePlatformAndOntoTheFinishAreInRange() {
        BridgeLayout l = layout(0, 0);
        for (int lane = 0; lane < 2; lane++) {
            Panel first = l.panel(0, lane);
            Vec2 take = l.gateTakeoff(first);
            Vec2 land = l.landing(take, first);
            assertEquals(first.rect().centerX(), take.x(), 1e-9);
            assertTrue(take.z() < l.start().maxZ() - BridgeLayout.TAKEOFF_MARGIN + 1e-9, "stands on the platform");
            assertTrue(take.z() > l.start().maxZ() - 0.7);
            assertTrue(take.distanceTo(land) < 3.0);

            Panel last = l.panel(17, lane);
            Vec2 lastTake = l.takeoff(last, last);
            Vec2 fin = l.finishLanding(new Vec2(lastTake.x(), last.rect().maxZ() - BridgeLayout.TAKEOFF_MARGIN));
            assertTrue(l.finish().contains(fin.x(), fin.z()));
            assertTrue(fin.z() - l.finish().minZ() >= BridgeLayout.LANDING_INSET);
            assertTrue(HopPlanner.plan(lastTake.distanceTo(fin)).speed() <= HopPlanner.MAX_SPEED);
        }
    }

    @Test
    void geometryIsTranslationInvariant() {
        BridgeLayout a = layout(0, 0), b = layout(5000, 0);
        for (int r = 0; r < 17; r++) {
            for (int from = 0; from < 2; from++) {
                for (int to = 0; to < 2; to++) {
                    Vec2 t1 = a.takeoff(a.panel(r, from), a.panel(r + 1, to));
                    Vec2 t2 = b.takeoff(b.panel(r, from), b.panel(r + 1, to));
                    assertEquals(t1.x() + 5000, t2.x(), 1e-9);
                    assertEquals(t1.z(), t2.z(), 1e-9);
                    assertEquals(t1.distanceTo(a.landing(t1, a.panel(r + 1, to))),
                            t2.distanceTo(b.landing(t2, b.panel(r + 1, to))), 1e-9);
                }
            }
        }
    }

    @Test
    void incompleteOrDuplicatedPanelsAreRejected() {
        List<PanelSpec> specs = new ArrayList<>();
        for (int r = 0; r < 3; r++) {
            specs.add(new PanelSpec(r, 0, -2.0, 11.0 + 3 * r, TOP));
        }
        Rect any = new Rect(0, 1, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> BridgeLayout.of(specs, any, any));
        List<PanelSpec> dup = new ArrayList<>();
        dup.add(new PanelSpec(0, 0, -2.0, 11.0, TOP));
        dup.add(new PanelSpec(0, 0, -2.0, 11.0, TOP));
        assertThrows(IllegalArgumentException.class, () -> BridgeLayout.of(dup, any, any));
    }

    private static void assertInside(Rect r, Vec2 p) {
        assertTrue(p.x() >= r.minX() - 1e-9 && p.x() <= r.maxX() + 1e-9 && p.z() >= r.minZ() - 1e-9 && p.z() <= r.maxZ() + 1e-9,
                "point " + p + " outside " + r);
    }
}
