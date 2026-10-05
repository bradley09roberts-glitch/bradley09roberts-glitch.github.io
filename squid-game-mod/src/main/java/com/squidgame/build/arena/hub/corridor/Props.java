package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;

/**
 * Small reusable props. Unless stated otherwise a prop is written in a canonical frame whose front faces local +Z
 * (south) and whose x axis runs left to right as seen from the front; place it with {@code c.at(x, y, z, rot, ...)}
 * (rot 0 = front faces south, 1 = west, 2 = north, 3 = east).
 */
final class Props {
    private Props() {
    }

    static final String[] FACING = {"south", "west", "north", "east"};

    /** Facing name for a rotation step of a canonical +Z-facing prop. */
    static String facing(int rot) {
        return FACING[Math.floorMod(rot, 4)];
    }

    // ------------------------------------------------------------------ cameras

    /**
     * Ceiling-hung security camera at cell (x, yTop, z): slab mount in the ceiling row, wedge housing below it,
     * dark glass lens in the facing direction and a tiny red status lamp. {@code facing} = look direction.
     */
    static void camera(BuildContext c, int x, int yTop, int z, String facing) {
        c.set(x, yTop, z, "minecraft:polished_blackstone_slab[type=top]");
        c.set(x, yTop - 1, z, Pal.stairs("minecraft:polished_blackstone_stairs", facing, true));
        int dx = 0, dz = 0;
        switch (facing) {
            case "north" -> dz = -1;
            case "south" -> dz = 1;
            case "east" -> dx = 1;
            default -> dx = -1;
        }
        // lens: a dark glass pane-block just in front of the wedge, under the ceiling
        c.set(x + dx, yTop, z + dz, "minecraft:polished_blackstone_slab[type=top]");
        c.set(x + dx, yTop - 1, z + dz, "minecraft:black_stained_glass");
    }

    /** Wall-mounted camera: cell (x,y,z) is the free cell next to the wall; {@code toward} = direction pointing out of the wall. */
    static void wallCamera(BuildContext c, int x, int y, int z, String toward) {
        c.set(x, y, z, Pal.stairs("minecraft:polished_blackstone_stairs", toward, true));
        c.set(x, y + 1, z, "minecraft:polished_blackstone_slab[type=top]");
        // hmm: lens block hanging under the wedge
    }

    // ------------------------------------------------------------------ seating and tables

    static String opposite(String facing) {
        return switch (facing) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    /** Chair (stair block) whose occupant looks towards {@code facing} (the stair's high back is behind the sitter). */
    static void chair(BuildContext c, int x, int y, int z, String facing, String stairBlock) {
        c.set(x, y, z, Pal.stairs(stairBlock, opposite(facing), false));
    }

    static void chairBlack(BuildContext c, int x, int y, int z, String facing) {
        chair(c, x, y, z, facing, "minecraft:polished_blackstone_stairs");
    }

    static void chairPink(BuildContext c, int x, int y, int z, String facing) {
        chair(c, x, y, z, facing, "squidgame:pastel_pink_stairs");
    }

    /** Bench: a row of bottom slabs along x from x0..x1 at (y, z) on dark legs at both ends. */
    static void bench(BuildContext c, int x0, int x1, int y, int z, String slab, String leg) {
        for (int x = x0; x <= x1; x++) {
            c.set(x, y, z, slab);
        }
        c.set(x0, y - 1 < 0 ? 0 : y - 1, z, leg);
        c.set(x1, y - 1 < 0 ? 0 : y - 1, z, leg);
    }

    // ------------------------------------------------------------------ lockers / shelves

    /** A locker column (1 wide, 3 high) whose door faces local +Z: gray body, vent trapdoor, handle. */
    static void locker(BuildContext c, int color) {
        String body = color == 0 ? "minecraft:light_blue_concrete" : color == 1 ? "minecraft:gray_concrete" : "minecraft:light_gray_concrete";
        c.set(0, 0, 0, body);
        c.set(0, 1, 0, body);
        c.set(0, 2, 0, body);
        // front details one block forward are not possible in a 1-deep wall locker; use trapdoors on the front instead
        c.set(0, 2, 1, Pal.trapdoor("minecraft:iron_trapdoor", "south", true, false));
        c.set(0, 0, 1, Pal.trapdoor("minecraft:iron_trapdoor", "south", true, false));
    }

    /** Wall of lockers along +x starting at the origin, n lockers, door side facing local +Z. */
    static void lockerRow(BuildContext c, int n) {
        for (int i = 0; i < n; i++) {
            int col = (i % 3);
            c.at(i, 0, 0, 0, () -> locker(c, col == 0 ? 1 : col));
            c.set(i, 3, 0, "minecraft:black_concrete");
        }
    }

    // ------------------------------------------------------------------ bunk bed

    /**
     * Double bunk along local +Z: cells z=0 (head, against a wall) and z=1 are the beds, a black post column at z=2.
     * Lower bed at y=0, upper at y=2. {@code lower}/{@code upper} are bed colours.
     */
    static void bunk(BuildContext c, String lower, String upper) {
        c.set(0, 0, 0, Pal.bed(lower, "north", true));
        c.set(0, 0, 1, Pal.bed(lower, "north", false));
        c.set(0, 2, 0, Pal.bed(upper, "north", true));
        c.set(0, 2, 1, Pal.bed(upper, "north", false));
        // posts and rails
        for (int y = 0; y <= 3; y++) {
            c.set(0, y, 2, y == 3 ? "minecraft:polished_blackstone_slab[type=bottom]" : "minecraft:polished_blackstone_wall");
        }
        c.set(0, 1, 1, "minecraft:air");
        c.set(0, 1, 0, "minecraft:air");
    }

    // ------------------------------------------------------------------ desks and consoles

    /** A desk of length n along +x: counter at y=0 (black with a quartz top slab), pink front trim on the +Z face row. */
    static void desk(BuildContext c, int n) {
        for (int i = 0; i < n; i++) {
            c.set(i, 0, 0, "minecraft:polished_blackstone");
            c.set(i, 1, 0, "minecraft:smooth_quartz_slab[type=bottom]");
        }
    }

    /** Monitor on a desk top cell facing {@code facing}. */
    static void deskMonitor(BuildContext c, int x, int y, int z, String facing) {
        c.set(x, y, z, Pal.monitor(facing));
    }

    /** Potted-plant-like decoration: flower pot with fern on a table. */
    static void plant(BuildContext c, int x, int y, int z) {
        c.set(x, y, z, "minecraft:potted_fern");
    }

    // ------------------------------------------------------------------ misc wall furniture

    /** 2x1 vent grille on a wall: two open iron trapdoors in the free cells next to the wall. */
    static void vent(BuildContext c, int x, int y, int z, String towardRoom) {
        c.set(x, y, z, Pal.trapdoor("minecraft:iron_trapdoor", towardRoom, true, false));
    }

    /** Fire extinguisher box (red) + hose: two cells tall at floor level beside a wall. */
    static void extinguisher(BuildContext c, int x, int y, int z) {
        c.set(x, y, z, "minecraft:red_concrete");
        c.set(x, y + 1, z, "minecraft:white_concrete");
    }
}
