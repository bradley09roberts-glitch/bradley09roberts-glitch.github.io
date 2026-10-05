package com.squidgame.game.finale;

import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaBuilders;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BlockBuffer;
import com.squidgame.build.BuildContext;
import com.squidgame.build.Marker;
import com.squidgame.build.arena.FinalBuilder;
import com.squidgame.build.placeholder.FinalPlaceholder;
import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.finale.CourtGeometry;
import com.squidgame.core.finale.DuelSimulator;
import com.squidgame.core.finale.Role;
import com.squidgame.core.finale.SquidShape;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The game reads the court from the markers of the arena contract. This builds both arenas that can stand in for the
 * final (the real builder and the fixture), reads them exactly like the game does and checks the court they describe.
 */
class FinaleArenaTest {
    private static final ArenaId ID = ArenaId.FINAL;

    private record Built(ArenaBuilder builder, BlockBuffer buffer, BuildContext ctx, CourtGeometry court) {
    }

    private static Built build(ArenaBuilder builder) {
        BlockBuffer buf = new BlockBuffer();
        BuildContext ctx = new BuildContext(buf, ID.originX, ArenaId.ORIGIN_Y, ID.originZ, 42L);
        builder.build(ctx);
        Marker circle = buf.marker("final.circle");
        CourtGeometry court = FinaleArena.courtFrom(buf.markers("final.boundary"), circle, buf.marker("final.neck"),
                buf.marker("final.attacker_spawn"), buf.marker("final.defender_spawn"), buf.region("final.court"));
        return new Built(builder, buf, ctx, court);
    }

    private static final Built REAL = build(new FinalBuilder());
    private static final Built FIXTURE = build(new FinalPlaceholder());

    // ------------------------------------------------------------------ the contract

    private static void checkContract(Built b) {
        for (String m : b.builder().requiredMarkers()) {
            assertFalse(b.buffer().markers(m).isEmpty(), "missing marker " + m);
        }
        for (String r : b.builder().requiredRegions()) {
            assertFalse(b.buffer().regions(r).isEmpty(), "missing region " + r);
        }
        List<Marker> boundary = b.buffer().markers("final.boundary");
        assertTrue(boundary.size() >= 16, "the contract asks for at least 16 boundary vertices");
        Set<Integer> indices = new HashSet<>();
        for (Marker m : boundary) {
            assertTrue(indices.add(m.getInt("i", -1)), "boundary index " + m.get("i", "?") + " is unique");
        }
        for (int i = 0; i < boundary.size(); i++) {
            assertTrue(indices.contains(i), "boundary indices run 0.." + (boundary.size() - 1));
        }
        assertTrue(b.buffer().markers("final.audience").size() >= 40, "at least 40 audience spots");
        Set<Integer> slots = new HashSet<>();
        for (Marker m : b.buffer().markers("final.audience")) {
            assertTrue(slots.add(m.getInt("slot", -1)), "audience slots are unique");
        }
        assertEquals("5", b.buffer().marker("final.circle").get("r", "?"), "the circle's radius is in its data");
        assertEquals("3", b.buffer().marker("final.neck").get("w", "?"));
    }

    @Test
    void theRealArenaHonoursTheContract() {
        checkContract(REAL);
        assertTrue(REAL.builder().version() >= 1, "a real builder is version 1 or higher");
    }

    @Test
    void theFixtureHonoursTheContractAndIsOlderThanAnyRealBuilder() {
        checkContract(FIXTURE);
        assertEquals(0, FIXTURE.builder().version());
        assertEquals(FinalPlaceholder.class.getName(), ArenaBuilders.placeholderClassName(ID),
                "the name the arena registry looks for when a real builder is missing");
    }

    // ------------------------------------------------------------------ the court the game gets

    private static void checkCourt(Built b) {
        CourtGeometry c = b.court();
        assertNotNull(c);
        Marker atk = b.buffer().marker("final.attacker_spawn"), def = b.buffer().marker("final.defender_spawn");
        Marker circle = b.buffer().marker("final.circle"), neck = b.buffer().marker("final.neck");
        assertTrue(c.contains(atk.x(), atk.z()), "the attacker starts inside the squid");
        assertTrue(c.contains(def.x(), def.z()), "so does the defender");
        assertTrue(c.contains(circle.x(), circle.z()));
        assertTrue(c.inNeck(neck.x(), neck.z()));
        assertTrue(c.edgeDistance(atk.x(), atk.z()) > 2.0 && c.edgeDistance(def.x(), def.z()) > 2.0, "spawns are not on the line");
        assertTrue(c.inCircle(circle.x(), circle.z()));
        assertEquals(3.0, c.captureRadius(), 1e-9, "the capture circle is the golden ring");
        // the order along the course: square, neck, triangle, head
        assertTrue(atk.z() > neck.z() && neck.z() > def.z() && def.z() > circle.z(), "square -> neck -> triangle -> head");
        assertTrue(c.axisLength() > 30, "the course is long: " + c.axisLength());
        // everybody who waits is outside the squid, and far enough from the line not to be in the way
        for (Marker m : b.buffer().markers("final.audience")) {
            assertFalse(c.contains(m.x(), m.z()), "audience slot " + m.get("slot", "?") + " is not on the court");
            assertTrue(c.edgeDistance(m.x(), m.z()) < -3.0, "and at least three blocks from the line");
        }
        // the markers' heights agree: everything on the court is on one floor
        assertEquals(atk.y(), def.y(), 1e-9);
        assertEquals(atk.y(), circle.y(), 1e-9);
    }

    @Test
    void theRealCourtIsAsTheGameExpects() {
        checkCourt(REAL);
    }

    @Test
    void theFixtureCourtIsAsTheGameExpects() {
        checkCourt(FIXTURE);
    }

    @Test
    void bothArenasPaintTheSameSquidAsTheReferenceShape() {
        for (Built b : List.of(REAL, FIXTURE)) {
            List<Marker> boundary = new ArrayList<>(b.buffer().markers("final.boundary"));
            boundary.sort(java.util.Comparator.comparingInt(m -> m.getInt("i", 0)));
            List<double[]> reference = SquidShape.outline();
            assertEquals(reference.size(), boundary.size(), b.builder().getClass().getSimpleName());
            for (int i = 0; i < boundary.size(); i++) {
                assertEquals(reference.get(i)[0] + ID.originX, boundary.get(i).x(), 1e-6, "vertex " + i + " x");
                assertEquals(reference.get(i)[1] + ID.originZ, boundary.get(i).z(), 1e-6, "vertex " + i + " z");
            }
            assertEquals(SquidShape.court(ID.originX, ID.originZ).circleCenter().x(), b.court().circleCenter().x(), 1e-6);
        }
    }

    @Test
    void theCourtIsFlatFreeOfObstaclesAndPaintedWithTheLines() {
        for (Built b : List.of(REAL, FIXTURE)) {
            int lines = 0;
            for (int x = -9; x <= 9; x++) {
                for (int z = -27; z <= 27; z++) {
                    double wx = ID.originX + x + 0.5, wz = ID.originZ + z + 0.5;
                    if (!b.court().contains(wx, wz)) {
                        continue;
                    }
                    String floor = b.ctx().get(x, 0, z);
                    assertFalse(isAir(floor), b.builder().getClass().getSimpleName() + ": no floor at " + x + "," + z);
                    for (int y = 1; y <= 3; y++) {
                        String above = b.ctx().get(x, y, z);
                        assertTrue(isAir(above) || above.startsWith("minecraft:light"),
                                b.builder().getClass().getSimpleName() + ": " + above + " in the way at " + x + "," + y + "," + z);
                    }
                    if (floor.equals("minecraft:white_concrete")) {
                        lines++;
                    }
                }
            }
            assertTrue(lines >= 100, b.builder().getClass().getSimpleName() + ": " + lines + " blocks of white line");
        }
    }

    /** A cell the builder never wrote is air too. */
    private static boolean isAir(String state) {
        return state == null || state.equals("minecraft:air");
    }

    // ------------------------------------------------------------------ duels on the arena itself

    @Test
    void npcsDuelOnTheRealCourtAtItsRealPositionInTheWorld() {
        int attackerWins = 0, n = 80;
        for (int i = 0; i < n; i++) {
            Rng r = new Rng(300 + i);
            Personality a = Personality.generate(r), b = Personality.generate(r);
            DuelSimulator.Result res = new DuelSimulator(Difficulty.NORMAL, REAL.court(), a, b,
                    i % 2 == 0 ? Role.ATTACKER : Role.DEFENDER, 9 + i).run();
            assertNotNull(res.outcome());
            if (res.winnerRole() == Role.ATTACKER) {
                attackerWins++;
            }
        }
        assertTrue(attackerWins > n * 0.30 && attackerWins < n * 0.75, "attacker wins " + attackerWins + " of " + n);
    }
}
