package com.squidgame.build.arena.hub.control;

/**
 * Geometry constants of the control room (hub frame). Interior air volume x[44,84] z[-18,18] y[12,32]; floor blocks
 * at y = 11 (standing level 12). West wall x = 43 is shared with the dormitory.
 */
final class Geo {
    private Geo() {
    }

    static final int X0 = 44, X1 = 84, Z0 = -18, Z1 = 18, Y0 = 12, Y1 = 32;
    static final int WEST = 43, EAST = 85, NORTH = -19, SOUTH = 19, CEIL = 33;
    /** Window openings in the west wall plane (z ranges), y range. */
    static final int WIN_Y0 = 14, WIN_Y1 = 30;
    static final int[][] WINDOWS = {{-16, -3}, {3, 16}};
    /** Door opening in the west wall plane. */
    static final int DOOR_Z0 = -2, DOOR_Z1 = 2, DOOR_Y1 = 15;

    /** Centre of the commander's dais (on the east wall). */
    static final double DAIS_CX = 84.5, DAIS_CZ = 0.5;

    static boolean inside(int x, int z) {
        return x >= X0 && x <= X1 && z >= Z0 && z <= Z1;
    }
}
