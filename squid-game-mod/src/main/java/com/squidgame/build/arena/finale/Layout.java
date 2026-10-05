package com.squidgame.build.arena.finale;

/**
 * Shared coordinates of the Final arena (local frame: origin = arena origin, floor block y = 0, standing height 1.0,
 * +X east, +Z south). Everything that has to agree between the helpers lives here.
 *
 * <pre>
 *   z=-72  ---- painted sunset wall (north) ----------------------------------------
 *   z=-60  school (north end, front at z=-51), forecourt, flag pole, podium
 *   z=-25  head circle (centre x=0,z=-20, r=5)  ... squid court x[-7,7] z[-25,24] ...  square ends z=24
 *   z= 60  yard edge; rim wall z=61..62 with the gate ramp (x -5..5) -> landing z=63..65 -> waiting room gate z=66
 *   x= 51  rim wall; grandstand (gallery) x=53..76 on the east side
 * </pre>
 */
final class Layout {
    private Layout() {
    }

    // ---- yard (inside the rim wall) = arena.bounds
    static final int YX0 = -50, YX1 = 50, YZ0 = -60, YZ1 = 60;
    /** Outer extent of the 2-thick rim wall. */
    static final int WX0 = -52, WX1 = 52, WZ0 = -62, WZ1 = 62;

    // ---- painted backdrop wall: coordinates of the painted (innermost) plane; the wall is 3 thick behind it
    static final int BX0 = -62, BX1 = 90, BZ0 = -72, BZ1 = 98;
    static final int BACKDROP_H = 45;

    // ---- squid court (exact geometry in CourtGeometry)
    static final int ZC = -20;          // head circle centre z (x = 0)
    static final int R = 5;             // head circle radius
    static final int HW = 7;            // half width of triangle base and of the square (lines at x = +-7)
    static final int ZB = ZC + 24;      // triangle base line z (=4)
    static final int NECK_LEN = 6;      // base line -> square top line
    static final int ZS = ZB + NECK_LEN; // square top line z (=10)
    static final int SQ = 14;           // square side (line centre to line centre)
    static final int ZQ = ZS + SQ;      // square bottom line z (=24)
    static final int COURT_TOP = ZC - R; // -25

    /** Clear belt around the court where no prop may stand. */
    static final int BELT = 6;

    // ---- gate / waiting room (prefab) on the south side
    static final int GATE_Z = 66;       // plane of the gate wall (prefab local z = 0)
    static final int ROOM_DY = 2;       // waiting room floor block y (stand height 3.0), ramp leads down to the sand
    static final int RAMP_HALF = 5;     // ramp x[-5,5]

    // ---- grandstand on the east side
    static final int G_TIERS = 8;
    static final int G_X0 = 53;          // first tier front column
    static final int G_TIER_DEPTH = 3;
    static final int G_Z0 = -36, G_Z1 = 36;
    static final int G_FLOOR0 = 3;       // first tier floor block y (stand 4.0)

    // ---- school (north end)
    /** Building body x[-40,40] z[-60,-52]; facade plane z = SCH_FACE; pilasters / sills stand one block proud. */
    static final int SCH_X0 = -40, SCH_X1 = 40, SCH_Z0 = -60, SCH_FACE = -52;

    /** True inside the clear belt around the squid court (court bounding box grown by BELT). */
    static boolean inBelt(int x, int z) {
        return x >= -HW - BELT && x <= HW + BELT && z >= COURT_TOP - BELT && z <= ZQ + BELT;
    }
}
