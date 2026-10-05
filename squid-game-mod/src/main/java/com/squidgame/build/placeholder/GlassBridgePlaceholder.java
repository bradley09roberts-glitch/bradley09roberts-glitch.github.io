package com.squidgame.build.placeholder;

import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;
import com.squidgame.build.arena.prefab.WaitingRoomPrefab;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Throw-away fixture for the glass bridge so the game can be developed and tested before the real arena builder is
 * used (it honours exactly the same marker/region contract and geometry): a start platform with the gate wall, the
 * deck of 18 rows x 2 lanes of 2x2 {@code squidgame:bridge_glass} panels at y = 40 (row r at z = 10 + 3r .. 11 + 3r,
 * lane 0 at x -3..-2, lane 1 at x 1..2; one block between rows, two between lanes), an end platform and a pit down
 * to y = -30, all enclosed by plain walls. Open to the sky so it needs no lighting; the shared waiting room is placed
 * behind the start platform like in the real arena. Version 0 so any real builder replaces it.
 *
 * <p>Local frame (world = arena origin (5000, 64, 0) + local): z -35..-8 waiting room, -7..8 start platform, 9 gap,
 * 10..62 deck, 63 gap, 64..79 end platform; floor y = 40, standing height 41, pit floor block y = -30.
 */
public final class GlassBridgePlaceholder implements ArenaBuilder {
    private static final int ROWS = 18;
    private static final int DECK = 40;
    private static final int STAND = 41;
    private static final int PIT = -30;
    private static final int WALL_TOP = 62;
    private static final int X0 = -12, X1 = 11;
    private static final int START_Z0 = -7, START_Z1 = 8;
    private static final int END_Z0 = 64, END_Z1 = 79;
    private static final int GATE_Z = 8;
    private static final String GLASS = "squidgame:bridge_glass";
    private static final String BLACK = "minecraft:black_concrete";
    private static final String TILE_B = "squidgame:tile_black";
    private static final String TILE_W = "squidgame:tile_white";

    @Override
    public ArenaId id() {
        return ArenaId.GLASS_BRIDGE;
    }

    @Override
    public int version() {
        return 0;
    }

    @Override
    public List<String> requiredMarkers() {
        List<String> out = new ArrayList<>(CommonMarkers.REQUIRED);
        out.add(CommonMarkers.GUARD_POST);
        out.addAll(List.of("bridge.panel", "bridge.queue", "bridge.gate", "bridge.finish_spawn", "bridge.pit_floor"));
        return out;
    }

    @Override
    public List<String> requiredRegions() {
        List<String> out = new ArrayList<>(CommonMarkers.REQUIRED_REGIONS);
        out.addAll(List.of("bridge.start", "bridge.finish", "bridge.pit", "bridge.deck"));
        return out;
    }

    @Override
    public void build(BuildContext c) {
        pitAndWalls(c);
        c.at(0, DECK, -8, 0, () -> WaitingRoomPrefab.build(c,
                new WaitingRoomPrefab.Spec(41, 22, 9, "GLASS BRIDGE", "ONLY ONE PANEL IN TWO WILL HOLD YOU", 144)));
        platforms(c);
        gateWall(c);
        deck(c);
        gantry(c);
        markers(c);
        c.text(0.5, 52.0, GATE_Z - 1.05, "GLASS BRIDGE (fixture)", "#FFFFFF", 4f, 180f, true);
    }

    // ------------------------------------------------------------------ shell

    private void pitAndWalls(BuildContext c) {
        // pit floor and the walls that enclose the pit and the platforms (no roof: the arena sky lights everything)
        c.fill(X0 - 1, PIT - 2, START_Z0 - 1, X1 + 1, PIT, END_Z1 + 1, "minecraft:stone_bricks");
        c.fill(X0, PIT, START_Z0, X1, PIT, END_Z1, "minecraft:smooth_stone");
        c.fill(X0 - 1, PIT, START_Z0 - 1, X0 - 1, WALL_TOP, END_Z1 + 1, BLACK);
        c.fill(X1 + 1, PIT, START_Z0 - 1, X1 + 1, WALL_TOP, END_Z1 + 1, BLACK);
        c.fill(X0 - 1, PIT, END_Z1 + 1, X1 + 1, WALL_TOP, END_Z1 + 1, BLACK);
        c.fill(X0 - 1, PIT, START_Z0 - 1, X1 + 1, DECK - 1, START_Z0 - 1, BLACK);
        // invisible fence above the walls so nobody can climb or be pushed out
        c.fill(X0 - 1, WALL_TOP + 1, START_Z0 - 1, X0 - 1, WALL_TOP + 25, END_Z1 + 1, "squidgame:invisible_wall");
        c.fill(X1 + 1, WALL_TOP + 1, START_Z0 - 1, X1 + 1, WALL_TOP + 25, END_Z1 + 1, "squidgame:invisible_wall");
        c.fill(X0 - 1, WALL_TOP + 1, END_Z1 + 1, X1 + 1, WALL_TOP + 25, END_Z1 + 1, "squidgame:invisible_wall");
        // glowing stripes on the pit walls so the drop is readable
        for (int y = PIT + 4; y < DECK - 2; y += 8) {
            c.fill(X0, y, START_Z0, X0, y, END_Z1, "minecraft:sea_lantern");
            c.fill(X1, y, START_Z0, X1, y, END_Z1, "minecraft:sea_lantern");
        }
    }

    private void platforms(BuildContext c) {
        for (int[] z : new int[][]{{START_Z0, START_Z1}, {END_Z0, END_Z1}}) {
            c.fill(X0, DECK - 4, z[0], X1, DECK - 1, z[1], BLACK);
            for (int x = X0; x <= X1; x++) {
                for (int zz = z[0]; zz <= z[1]; zz++) {
                    c.set(x, DECK, zz, isSlot(x, zz, z[0] == END_Z0) ? TILE_W : TILE_B);
                }
            }
        }
        // a glowing line beside the bridge axis (not under the gate / guard / exit markers, which stand on solid tiles)
        c.fill(-1, DECK, START_Z0, -1, DECK, START_Z1, "squidgame:panel_light_pink");
        c.fill(-1, DECK, END_Z0, -1, DECK, END_Z1, "squidgame:panel_light_white");
        // the end wall with the exit
        c.fill(X0 - 1, STAND, END_Z1 + 1, X1 + 1, STAND + 8, END_Z1 + 1, BLACK);
    }

    private void gateWall(BuildContext c) {
        // across the front of the start platform; 7 x 5 opening at the door plane (the server installs the door panels)
        c.fill(X0, STAND, GATE_Z - 1, X1, STAND + 8, GATE_Z, BLACK);
        c.clear(-3, STAND, GATE_Z - 1, 3, STAND + 4, GATE_Z);
        c.fill(-4, STAND + 5, GATE_Z - 1, 4, STAND + 6, GATE_Z, "squidgame:tile_pink");
    }

    private void deck(BuildContext c) {
        for (int r = 0; r < ROWS; r++) {
            int z0 = rowZ(r);
            for (int lane = 0; lane < 2; lane++) {
                int x0 = laneX(lane);
                c.fill(x0, DECK, z0, x0 + 1, DECK, z0 + 1, GLASS);
            }
        }
        // light rails beside the deck (outside the lanes) so the glass is bright from every angle
        for (int z = 9; z <= 63; z += 3) {
            c.set(-5, DECK + 2, z, "squidgame:panel_light_white");
            c.set(4, DECK + 2, z, "squidgame:panel_light_white");
        }
    }

    private void gantry(BuildContext c) {
        // spectators float above the start platform
        c.fill(-3, 59, -6, 3, 59, -2, BLACK);
    }

    // ------------------------------------------------------------------ markers

    private void markers(BuildContext c) {
        for (int r = 0; r < ROWS; r++) {
            for (int lane = 0; lane < 2; lane++) {
                // same convention as the real builder: the centre of the panel's first (west/north) block; the panel covers
                // the blocks x..x+1, z..z+1 from the block that contains the marker
                c.marker("bridge.panel", laneX(lane) + 0.5, STAND, rowZ(r) + 0.5, 0f, "row=" + r + ",lane=" + lane);
            }
        }
        int n = 0;
        for (Cell k : queueCells()) {
            c.marker("bridge.queue", k.x + 0.5, STAND, k.z + 0.5, 0f, "slot=" + n++);
        }
        n = 0;
        for (Cell k : finishCells()) {
            c.marker("bridge.finish_spawn", k.x + 0.5, STAND, k.z + 0.5, 180f, "slot=" + n++);
        }
        c.marker("bridge.gate", 0.5, STAND, GATE_Z + 0.5, 0f, "w=7,h=5");
        c.marker("bridge.pit_floor", 0.5, PIT + 1, 36.5, 0f);
        c.region("bridge.start", X0, DECK, START_Z0, X1, WALL_TOP, START_Z1);
        c.region("bridge.finish", X0, DECK, END_Z0, X1, WALL_TOP, END_Z1);
        c.region("bridge.pit", X0, PIT + 1, START_Z0, X1, DECK - 1, END_Z1);
        c.region("bridge.deck", -4, DECK, 9, 3, DECK + 3, 65);
        c.region("arena.bounds", -23, PIT - 2, -35, 23, WALL_TOP + 25, END_Z1 + 3);
        c.marker("arena.spectator", 0.5, 60, -4.5, 0f);
        c.marker("arena.exit", 0.5, STAND, 72.5, 180f);
        c.marker(CommonMarkers.GUARD_POST, 0.5, STAND, 4.5, 0f, "rank=triangle");
        c.marker(CommonMarkers.GUARD_POST, -8.5, STAND, 70.5, 180f, "rank=triangle");
        c.marker(CommonMarkers.GUARD_POST, 8.5, STAND, 70.5, 180f, "rank=circle");
    }

    private record Cell(int x, int z) {
    }

    /** Queue cells nearest to the gate first: white tiles of the start platform (the doorway column group included). */
    private static List<Cell> queueCells() {
        List<Cell> out = new ArrayList<>();
        for (int x = X0; x <= X1; x++) {
            for (int z = START_Z0; z <= GATE_Z - 1; z++) {
                if (isSlot(x, z, false) && (z < GATE_Z - 1 || (x >= -3 && x <= 3))) {
                    out.add(new Cell(x, z));
                }
            }
        }
        out.sort(Comparator.<Cell>comparingDouble(k -> sq(k.x + 0.5) + sq(k.z + 0.5 - (GATE_Z + 0.5)))
                .thenComparingInt(Cell::x).thenComparingInt(Cell::z));
        return out;
    }

    /** Finish cells on the end platform, nearest to the back wall's centre first. */
    private static List<Cell> finishCells() {
        List<Cell> out = new ArrayList<>();
        for (int x = X0; x <= X1; x++) {
            for (int z = END_Z0 + 1; z <= END_Z1; z++) {
                if (isSlot(x, z, true)) {
                    out.add(new Cell(x, z));
                }
            }
        }
        out.sort(Comparator.<Cell>comparingDouble(k -> sq(k.x + 0.5) + sq(k.z + 0.5 - (END_Z1 + 1.5)))
                .thenComparingInt(Cell::x).thenComparingInt(Cell::z));
        return out;
    }

    private static double sq(double v) {
        return v * v;
    }

    /** White tile = standing cell: a checker, never the glowing aisle in the middle. */
    private static boolean isSlot(int x, int z, boolean end) {
        if (x == -1 || x == 0) {
            return false;
        }
        int xm = x >= 1 ? x : -1 - x;
        return ((xm + z) & 1) == 0 && !(end && z == END_Z0);
    }

    private static int rowZ(int row) {
        return 10 + 3 * row;
    }

    private static int laneX(int lane) {
        return lane == 0 ? -3 : 1;
    }
}
