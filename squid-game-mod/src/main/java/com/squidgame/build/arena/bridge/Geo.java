package com.squidgame.build.arena.bridge;

/**
 * Shared geometry of the Glass Bridge hall (local block coordinates, origin = arena origin).
 *
 * <pre>
 *  z:  -35 ..... -8 | -7 ... 8 | 9 ..... 63 | 64 ... 79 | 80 .. 82 | 83 ..... 108
 *      waiting room   start      bridge deck   end         exit wall   finish lounge
 *      (prefab)       platform   (18 rows)     platform    + door
 *  y:  pit floor -30 (stand -29), deck / platforms 40 (stand 41), guard ring 60, roof 90..93
 *  x:  hall interior -45 .. 44 (90 wide), bridge lanes -3..-2 and 1..2, platforms -12..11
 * </pre>
 * The hall is mirror symmetric about the plane x = 0 (block x maps to -x-1) and about z = 36 (block z maps to 72-z)
 * except for odd width features (doors, waiting room) that are centred on block x = 0.
 */
final class Geo {
    private Geo() {
    }

    // vertical levels (block y of the floor layer)
    static final int FOUND = -34;
    static final int PIT = -30;
    static final int DECK = 40;
    static final int STAND = 41;
    static final int RING = 60;
    static final int GANTRY = 75;
    static final int SIDE_GANTRY = 56;
    static final int ROOF = 90;
    static final int ROOF_TOP = 93;
    static final int TRUSS_BOT = 78;
    static final int TRUSS_TOP = 89;

    // hall interior (inclusive)
    static final int X0 = -45, X1 = 44;
    static final int Z0 = -7, Z1 = 79;
    /** Hall centre column (block z); the hall is symmetric about it. */
    static final int ZC = 36;

    // outer shell (walls are 3 thick)
    static final int WX0 = -48, WX1 = 47;
    static final int BZ = -10, FZ = 82;

    // platforms (24 x 16)
    static final int PX0 = -12, PX1 = 11;
    static final int START_Z0 = -7, START_Z1 = 8;
    static final int END_Z0 = 64, END_Z1 = 79;
    /** Door plane (block layer) of the start gate. */
    static final int GATE_Z = 8;
    /** Waiting room gate wall plane. */
    static final int ROOM_Z = -8;

    // glass deck
    static final int ROWS = 18;
    static final int FIRST_Z = 10;
    static final int PITCH = 3;
    static final int LANE0_X = -3;
    static final int LANE1_X = 1;
    static final int LAST_Z = FIRST_Z + PITCH * (ROWS - 1) + 1; // 62

    // finish lounge (behind the exit wall)
    static final int LZ0 = 83, LZ1 = 106;
    static final int LX0 = -20, LX1 = 20;

    /** Mirror of block x about the bridge axis. */
    static int mx(int x) {
        return -1 - x;
    }

    /** Mirror of block z about the hall centre. */
    static int mz(int z) {
        return 2 * ZC - z;
    }
}
