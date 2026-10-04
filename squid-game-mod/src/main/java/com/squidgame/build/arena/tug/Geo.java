package com.squidgame.build.arena.tug;

/**
 * Shared geometry of the Tug of War hall (local block coordinates, origin = arena centre).
 *
 * <pre>
 *  x:  -100 .. -76 | -75 ...... -61 | -60 ......... -8 | -7 .. 6 | 7 ......... 59 | 60 ...... 74 | 75 .. 98
 *      waiting room | west plateau   | deck A            |  gap    | deck B         | east plateau  | exit lounge
 *  y:  pit floor -30, ring (hall floor) 0, decks / plateaus / galleries 40, catwalks 58, roof 75..77
 *  z:  hall interior -35..35, pit -20..20, decks -2..2 (+ curbs and rails at +-3)
 * </pre>
 * The hall is mirror symmetric about the plane x = 0 (block x maps to -x-1).
 */
final class Geo {
    private Geo() {
    }

    // levels (block y of the floor layer)
    static final int PIT = -30;
    static final int RING = 0;
    static final int DECK = 40;
    static final int CAT = 58;
    static final int CEIL = 75;
    static final int ROOF_TOP = 77;
    static final int FOUND = -34;

    // hall interior (blocks, inclusive)
    static final int HX0 = -75, HX1 = 74;
    static final int HZ = 35;
    // outer shell
    static final int WX0 = -78, WX1 = 77;
    static final int WZ = 38;

    // pit (open volume above the floor layer)
    static final int PX0 = -60, PX1 = 59;
    static final int PZ = 20;

    // piers / plateaus: west x -75..-61, east 60..74
    static final int PIER_X0 = -75, PIER_X1 = -61;

    // deck: z -2..2, west deck blocks x -60..-8 (d = 0 at the tip)
    static final int TIP_A = -8;
    static final int REAR_A = -60;
    static final int DECK_LEN = 53;

    /** First block x of the upper catwalks in the west frame (after the 18 steps of the stairs). */
    static int CAT_X0() {
        return -54;
    }

    /** Block x of the d-th block from the gap on the west deck (d=0 is the tip block x=-8). */
    static int xa(int d) {
        return -8 - d;
    }

    /** Block x of the d-th block from the gap on the east deck (d=0 is x=7). */
    static int xb(int d) {
        return 7 + d;
    }
}
