package com.squidgame.build.placeholder;

import com.squidgame.build.ArenaId;
import com.squidgame.build.BlockBuffer;
import com.squidgame.build.BuildContext;
import com.squidgame.build.Marker;
import com.squidgame.build.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Tug of War fixture arena honours the marker contract and has the geometry the game depends on (the real
 * {@code TugOfWarBuilder} wins when it exists, so the fixture is built directly here).
 */
class TugOfWarPlaceholderTest {
    private static final ArenaId ID = ArenaId.TUG_OF_WAR;
    private static final int DECK = 64 + 40;
    private static BlockBuffer buf;
    private static TugOfWarPlaceholder fixture;

    @BeforeAll
    static void build() {
        buf = new BlockBuffer();
        fixture = new TugOfWarPlaceholder();
        fixture.build(new BuildContext(buf, ID.originX, ArenaId.ORIGIN_Y, ID.originZ, 1L));
    }

    @Test
    void itIsOlderThanAnyRealBuilder() {
        assertEquals(0, fixture.version());
        assertEquals(ID, fixture.id());
    }

    @Test
    void everyRequiredMarkerAndRegionExists() {
        List<String> missing = new ArrayList<>();
        for (String m : fixture.requiredMarkers()) {
            if (buf.markers(m).isEmpty()) {
                missing.add(m);
            }
        }
        for (String r : fixture.requiredRegions()) {
            if (buf.regions(r).isEmpty()) {
                missing.add(r);
            }
        }
        assertTrue(missing.isEmpty(), "missing: " + missing);
    }

    @Test
    void theSlotsRunAwayFromTheGapOnBothSides() {
        List<Marker> a = buf.markers("tug.slot_a");
        List<Marker> b = buf.markers("tug.slot_b");
        assertEquals(48, a.size());
        assertEquals(48, b.size());
        for (int k = 0; k < 48; k++) {
            Marker sa = slot(a, k);
            Marker sb = slot(b, k);
            assertEquals(ID.originX - 8.5 - k, sa.x(), 1e-9, "slot_a " + k);
            assertEquals(ID.originX + 8.5 + k, sb.x(), 1e-9, "slot_b " + k);
            assertEquals(sa.z(), sb.z(), 1e-9);
            assertEquals(DECK + 1, sa.y(), 1e-9);
            assertEquals(-90f, sa.yaw(), 1e-6, "team A faces east, towards the gap");
            assertEquals(90f, sb.yaw(), 1e-6, "team B faces west");
        }
    }

    @Test
    void everyoneStandsOnSolidGroundWithRoomAboveTheHead() {
        for (String name : List.of("tug.slot_a", "tug.slot_b", "tug.waiting_a", "tug.waiting_b", "tug.spare")) {
            List<Marker> ms = buf.markers(name);
            assertFalse(ms.isEmpty(), name);
            for (Marker m : ms) {
                assertTrue(buf.isSolidSet(m.bx(), m.by() - 1, m.bz()), name + " floor at " + m);
                assertFalse(buf.isSolidSet(m.bx(), m.by(), m.bz()), name + " feet at " + m);
                assertFalse(buf.isSolidSet(m.bx(), m.by() + 1, m.bz()), name + " head at " + m);
            }
        }
    }

    @Test
    void theRopeSpansTheGapAtHandHeight() {
        Marker a = buf.marker("tug.rope_a");
        Marker b = buf.marker("tug.rope_b");
        Marker c = buf.marker("tug.rope_center");
        assertEquals(ID.originX - 7.0, a.x(), 1e-9);
        assertEquals(ID.originX + 7.0, b.x(), 1e-9);
        assertEquals(ID.originX, c.x(), 1e-9);
        assertEquals(ArenaId.ORIGIN_Y + 42.2, c.y(), 1e-9);
        assertEquals(a.y(), b.y(), 1e-9);
        assertEquals(a.z(), c.z(), 1e-9);
    }

    @Test
    void theWalkwayIsFiveWideAndTwoLayersThickWithRibsAndAGapBetweenTheDecks() {
        for (int dx : new int[]{-8, -20, -59, 7, 30, 58}) {
            int x = ID.originX + dx;
            for (int dz = -2; dz <= 2; dz++) {
                for (int layer = 0; layer < 2; layer++) {
                    assertTrue(buf.isSolidSet(x, DECK - layer, dz), "deck block x=" + dx + " z=" + dz + " layer " + layer);
                }
            }
        }
        // the ribs under the plate (the third layer the game removes) start at the tip of each deck
        for (int dx : new int[]{-8, 7}) {
            for (int dz = -2; dz <= 2; dz++) {
                assertTrue(buf.isSolidSet(ID.originX + dx, DECK - 2, dz), "rib at the tip x=" + dx + " z=" + dz);
            }
        }
        for (int dx = -7; dx <= 6; dx++) {
            for (int dy = DECK - 3; dy <= DECK + 2; dy++) {
                assertFalse(buf.isSolidSet(ID.originX + dx, dy, 0), "gap at x=" + dx + " y=" + dy);
            }
        }
    }

    @Test
    void theEdgeRegionsMarkTheTipColumnOfEachDeck() {
        Region a = buf.region("tug.edge_a");
        Region b = buf.region("tug.edge_b");
        assertEquals(ID.originX - 8, a.maxX(), "the tip column of team A's deck is the edge's inner side");
        assertEquals(ID.originX + 7, b.minX());
        Region pit = buf.region("tug.pit");
        assertTrue(pit.minX() <= a.minX() && pit.maxX() >= b.maxX());
        assertTrue(pit.minY() < DECK - 20 && pit.maxY() < DECK);
        Marker floor = buf.marker("tug.pit_floor");
        assertTrue(floor.y() < DECK - 60, "the pit is deep: " + floor.y());
    }

    private static Marker slot(List<Marker> markers, int k) {
        for (Marker m : markers) {
            if (m.getInt("slot", -1) == k) {
                return m;
            }
        }
        throw new AssertionError("no slot " + k);
    }
}
