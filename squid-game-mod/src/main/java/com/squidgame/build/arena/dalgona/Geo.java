package com.squidgame.build.arena.dalgona;

/**
 * Layout constants of the Dalgona hall (local coordinates, +X east, +Z south, floor block y = 0,
 * standing height y = 1).
 *
 * <pre>
 *   z = -95..-92   front wall (north): chalkboard, clock, stage in front of it
 *   z = -91..-2    hall interior, 71 wide (x -35..35), 90 deep
 *   z = -1         rear wall skin (door to the waiting room, lockers, windows, balcony above)
 *   z = 0..        shared waiting room prefab behind the rear wall
 * </pre>
 *
 * The long walls (x = +-36..39) are panelled: pilasters every 10 blocks, recessed plaster panels
 * with arched windows and the four murals. Everything faces north (yaw 180) towards the board.
 */
public final class Geo {
    private Geo() {
    }

    // ---- hall shell ------------------------------------------------------------------------
    public static final int HALF_W = 35;
    public static final int Z_FRONT = -91;
    public static final int Z_REAR = -2;
    public static final int WALL_X = 36;          // inner layer of the long walls (cells x = +-36)
    public static final int FRONT_WALL_Z = -92;   // inner layer of the front wall
    public static final int REAR_WALL_Z = -1;     // inner layer (skin) of the rear wall
    public static final int EAVES = 17;           // top wall cell of the long walls

    /** Underside of the roof deck at column x, in half blocks (46 = ridge at y 23). */
    public static int roofUnder2(int x) {
        double ax = Math.abs(x);
        double u = 18.0 + 5.0 * (1.0 - Math.min(ax, 36.0) / 36.0);
        if (ax > 36) {
            u = 18.0 - (ax - 36.0) * 0.5;
        }
        return (int) Math.round(u * 2.0);
    }

    /** Roof underside height in blocks (fractional). */
    public static double roofUnder(int x) {
        return roofUnder2(x) / 2.0;
    }

    // ---- seating ----------------------------------------------------------------------------
    /** x ranges (inclusive) of the four bench blocks, west to east. Seats sit at odd offsets. */
    public static final int[][] BLOCKS = {{-31, -21}, {-15, -5}, {5, 15}, {21, 31}};
    public static final int SEATS_PER_BLOCK = 5;
    public static final int ROWS = 8;
    public static final int ROW_PITCH = 8;
    /** z of the north-most (desk back) cell of row 0. Row cells: desk z0, station z0+1, seat z0+2, bench z0+3. */
    public static final int ROW0_Z = -80;

    public static int rowZ(int row) {
        return ROW0_Z + ROW_PITCH * row;
    }

    public static int seatX(int block, int j) {
        return BLOCKS[block][0] + 1 + 2 * j;
    }

    // ---- stage (front) ------------------------------------------------------------------------
    public static final int STAGE_X = 15;         // stage spans x -15..15
    public static final int STAGE_Z0 = -91;       // north edge (wall side)
    public static final int STAGE_Z1 = -85;       // south edge
    public static final int STAGE_TOP = 2;        // standing height on the stage

    // ---- balcony --------------------------------------------------------------------------------
    public static final int BALCONY_FLOOR_Y = 10; // floor block y, standing height 11
    public static final int BALCONY_Z0 = -8;      // north (front) edge row, carries the railing
    public static final int BALCONY_Z1 = -2;
}
