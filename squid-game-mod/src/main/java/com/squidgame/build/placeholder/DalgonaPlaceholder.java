package com.squidgame.build.placeholder;

import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;
import com.squidgame.build.arena.prefab.WaitingRoomPrefab;

import java.util.ArrayList;
import java.util.List;

/**
 * Throw-away fixture for the Dalgona game: a flat, crude classroom hall with the real hall's seating geometry
 * (8 rows x 4 bench blocks x 5 seats, seats 2 blocks apart, rows 8 apart, 0.5 high bench behind a free floor cell,
 * a {@code dalgona_station} in front of every seat, everything facing north) and the exact marker / region contract of
 * {@code docs/ARENA_MARKERS.md}, so the game works unchanged on {@code DalgonaBuilder}. Used automatically only when the
 * real builder class is not on the class path ({@code ArenaBuilders.get}); version 0 so any real builder replaces it.
 *
 * <p>Local frame (same as the real hall): +X east, +Z south, floor block y = 0, hall interior x -35..35, z -91..-2,
 * rear wall z = -1 with the doorway to the shared waiting room prefab (z >= 0, turned half a turn).
 */
public final class DalgonaPlaceholder implements ArenaBuilder {
    private static final int HALF_W = 35;
    private static final int Z_FRONT = -91;
    private static final int Z_REAR_WALL = -1;
    private static final int[][] BLOCKS = {{-31, -21}, {-15, -5}, {5, 15}, {21, 31}};
    private static final int SEATS_PER_BLOCK = 5;
    private static final int ROWS = 8;
    private static final int ROW_PITCH = 8;
    private static final int ROW0_Z = -80;
    /** The seat marker sits 0.7 into its cell, 0.3 in front of the bench (same as the real hall). */
    private static final double SEAT_Z = 0.7;

    private static final String FLOOR = "minecraft:spruce_planks";
    private static final String WALL = "minecraft:white_concrete";
    private static final String DESK = "minecraft:dark_oak_slab[type=bottom]";
    private static final String TABLE = "minecraft:spruce_slab[type=bottom]";

    @Override
    public ArenaId id() {
        return ArenaId.DALGONA;
    }

    @Override
    public List<String> requiredMarkers() {
        List<String> l = new ArrayList<>(CommonMarkers.REQUIRED);
        l.add("dalgona.seat");
        l.add("dalgona.station");
        l.add("dalgona.front");
        l.add("dalgona.board");
        l.add(CommonMarkers.GUARD_POST);
        return l;
    }

    @Override
    public List<String> requiredRegions() {
        List<String> l = new ArrayList<>(CommonMarkers.REQUIRED_REGIONS);
        l.add("dalgona.seating");
        return l;
    }

    /** Placeholders are always older than any real builder (version >= 1), so a real builder replaces them. */
    @Override
    public int version() {
        return 0;
    }

    @Override
    public void build(BuildContext c) {
        hall(c);
        seating(c);
        front(c);
        c.at(0, 0, 0, 2, () -> WaitingRoomPrefab.build(c, WaitingRoomPrefab.Spec.of("DALGONA")));
        // the prefab's two wall-worker guard posts land on its own bench slabs when it is turned half a turn
        c.air(-19, 1, 12);
        c.air(19, 1, 12);
        markers(c);
    }

    private void hall(BuildContext c) {
        c.fill(-HALF_W - 2, -2, Z_FRONT - 2, HALF_W + 2, -1, Z_REAR_WALL + 1, "minecraft:stone_bricks");
        c.fill(-HALF_W - 1, 0, Z_FRONT - 1, HALF_W + 1, 0, Z_REAR_WALL, FLOOR);
        c.shell(-HALF_W - 1, 0, Z_FRONT - 1, HALF_W + 1, 12, Z_REAR_WALL, WALL);
        c.fill(-HALF_W - 1, 0, Z_FRONT - 1, HALF_W + 1, 0, Z_REAR_WALL, FLOOR);
        c.clear(-HALF_W, 1, Z_FRONT, HALF_W, 11, Z_REAR_WALL - 1);
        // the doorway through the rear wall (the prefab opens its own gate wall at z = 0)
        c.clear(-3, 1, Z_REAR_WALL, 3, 5, Z_REAR_WALL);
        for (int x = -32; x <= 32; x += 8) {
            for (int z = -88; z <= -4; z += 8) {
                c.set(x, 11, z, "minecraft:sea_lantern");
            }
        }
    }

    private void seating(BuildContext c) {
        int slot = 0;
        for (int row = 0; row < ROWS; row++) {
            int z0 = ROW0_Z + ROW_PITCH * row;
            for (int[] block : BLOCKS) {
                for (int x = block[0]; x <= block[1]; x++) {
                    c.set(x, 1, z0, DESK);
                    c.set(x, 1, z0 + 1, TABLE);
                    c.set(x, 1, z0 + 3, DESK);
                }
                for (int j = 0; j < SEATS_PER_BLOCK; j++) {
                    int x = block[0] + 1 + 2 * j;
                    c.set(x, 1, z0 + 1, "squidgame:dalgona_station[facing=north]");
                    c.marker("dalgona.seat", x + 0.5, 1.0, z0 + 2 + SEAT_Z, 180f, "slot=" + slot);
                    c.marker("dalgona.station", x + 0.5, 2.0, z0 + 1 + 0.5, 180f, "slot=" + slot);
                    slot++;
                }
            }
        }
        c.region("dalgona.seating", BLOCKS[0][0] - 1, 0, ROW0_Z - 1, BLOCKS[BLOCKS.length - 1][1] + 1, 4, ROW0_Z + ROW_PITCH * (ROWS - 1) + 4);
    }

    private void front(BuildContext c) {
        // stage, teacher's desk and the chalkboard on the front wall
        c.fill(-15, 1, Z_FRONT, 15, 1, -85, "minecraft:dark_oak_planks");
        c.fill(-3, 2, -89, 3, 2, -88, DESK);
        c.fill(-16, 4, Z_FRONT - 1, 16, 13, Z_FRONT - 1, "minecraft:green_concrete");
        c.text(0.5, 12.0, -90.95, "THE HONEYCOMB GAME (placeholder hall)", "#f4f1e6", 2.4f, 0f, false);
    }

    private void markers(BuildContext c) {
        c.marker("dalgona.front", 0.5, 2.0, -84.5, 0f);
        c.marker("dalgona.board", 0.5, 9.5, -90.95, 0f);
        c.marker(CommonMarkers.EXIT, 0.5, 1.0, -81.5, 180f);
        c.marker(CommonMarkers.SPECTATOR, 0.5, 10.0, -6.5, 180f);
        for (int z = -80; z <= -20; z += 20) {
            c.marker(CommonMarkers.GUARD_POST, -34.5, 1.0, z + 0.5, -90f, "rank=triangle");
            c.marker(CommonMarkers.GUARD_POST, 34.5, 1.0, z + 0.5, 90f, "rank=triangle");
        }
        c.marker(CommonMarkers.GUARD_POST, -13.5, 2.0, -86.5, 0f, "rank=triangle");
        c.marker(CommonMarkers.GUARD_POST, 13.5, 2.0, -86.5, 0f, "rank=triangle");
        c.marker(CommonMarkers.GUARD_POST, -6.5, 1.0, -4.5, 180f, "rank=circle");
        c.marker(CommonMarkers.GUARD_POST, 6.5, 1.0, -4.5, 180f, "rank=circle");
        c.marker(CommonMarkers.GUARD_POST, -9.5, 2.0, -88.5, 0f, "rank=square");
        c.region(CommonMarkers.REGION_BOUNDS, -HALF_W, 0, Z_FRONT, HALF_W, 23, Z_REAR_WALL);
    }
}
