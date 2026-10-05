package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;

/**
 * Small reusable props of the guard compound: ceiling security cameras, chairs, lockers and double bunks. Props that
 * take a rotation (lockers, bunks) are written in a canonical frame whose front faces local +Z (south) and are placed
 * with {@code c.at(x, y, z, rot, ...)} (rot 0 = front faces south, 1 = west, 2 = north, 3 = east).
 */
final class Props {
    private Props() {
    }

    static String opposite(String facing) {
        return switch (facing) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    // ------------------------------------------------------------------ cameras

    /**
     * Ceiling-hung security camera at cell (x, yTop, z): a slab mount in the top row, an upside-down wedge housing below
     * it pointing in the look direction {@code facing}, and a dark glass lens block just in front of the housing.
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
        c.set(x + dx, yTop, z + dz, "minecraft:polished_blackstone_slab[type=top]");
        c.set(x + dx, yTop - 1, z + dz, "minecraft:black_stained_glass");
    }

    // ------------------------------------------------------------------ seating

    /** Chair (stair block) whose occupant looks towards {@code facing} (the stair's high back is behind the sitter). */
    static void chair(BuildContext c, int x, int y, int z, String facing, String stairBlock) {
        c.set(x, y, z, Pal.stairs(stairBlock, opposite(facing), false));
    }

    static void chairBlack(BuildContext c, int x, int y, int z, String facing) {
        chair(c, x, y, z, facing, "minecraft:polished_blackstone_stairs");
    }

    // ------------------------------------------------------------------ lockers and bunks

    /** A locker column (1 wide, 3 high, door side facing local +Z): steel body with vent grilles on the door. */
    static void locker(BuildContext c, int color) {
        String body = color == 0 ? "minecraft:light_blue_concrete" : color == 1 ? "minecraft:gray_concrete" : "minecraft:light_gray_concrete";
        c.set(0, 0, 0, body);
        c.set(0, 1, 0, body);
        c.set(0, 2, 0, body);
        c.set(0, 2, 1, Pal.trapdoor("minecraft:iron_trapdoor", "south", true, false));
        c.set(0, 0, 1, Pal.trapdoor("minecraft:iron_trapdoor", "south", true, false));
    }

    /**
     * Double bunk along local +Z: cells z=0 (head, against a wall) and z=1 are the beds, a black post column at z=2.
     * Lower bed at y=0, upper at y=2. {@code lower}/{@code upper} are bed colours.
     */
    static void bunk(BuildContext c, String lower, String upper) {
        c.set(0, 0, 0, Pal.bed(lower, "north", true));
        c.set(0, 0, 1, Pal.bed(lower, "north", false));
        c.set(0, 2, 0, Pal.bed(upper, "north", true));
        c.set(0, 2, 1, Pal.bed(upper, "north", false));
        for (int y = 0; y <= 3; y++) {
            c.set(0, y, 2, y == 3 ? "minecraft:polished_blackstone_slab[type=bottom]" : "minecraft:polished_blackstone_wall");
        }
        c.set(0, 1, 1, Pal.AIR);
        c.set(0, 1, 0, Pal.AIR);
    }
}
