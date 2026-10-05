package com.squidgame.build.arena.redlight;

/**
 * Shared geometry of the Red Light, Green Light arena (all values are arena-local block coordinates; floor block y=0,
 * standing height 1.0). The playground is a rectangle x[-56,56] z[-7,178] enclosed by four 6-thick painted-sky walls.
 *
 * <pre>
 *   z=-31..-8   waiting room prefab (gate wall = the plane z=-8, which is also the inner face of the near wall)
 *   z=-7..-1    gate hall (covered porch with the gantry lintel)
 *   z=0..11     start zone (spawn grid)      z=12..13  start line
 *   z=14..138   the run                      z=139..140 finish line
 *   z=141..175  safe zone: doll stage (z=147), old tree (z=158)
 *   z=179       inner face of the far wall
 * </pre>
 */
public final class Layout {
    private Layout() {
    }

    /** Free field: x in [-HALF_W, HALF_W]. */
    public static final int HALF_W = 56;
    /** X of the inner face (first wall block) of the side walls: x = +-FACE_X. */
    public static final int FACE_X = HALF_W + 1;
    /** Z of the inner face of the near wall; also the plane of the waiting room gate wall. */
    public static final int NEAR_FACE_Z = -8;
    /** Z of the inner face of the far wall. */
    public static final int FAR_FACE_Z = 179;
    public static final int WALL_T = 6;
    /** Y of the wall's top deck. */
    public static final int WALL_TOP = 41;
    /** First y of the painted mural (the wall base). */
    public static final int MURAL_Y0 = 1;
    /** Last y of the painted mural (y = WALL_TOP is the dark cap row). */
    public static final int MURAL_Y1 = WALL_TOP - 1;

    public static final int FIELD_Z0 = NEAR_FACE_Z + 1;
    public static final int FIELD_Z1 = FAR_FACE_Z - 1;

    /** Painted boundary lines at x = +-LANE_X (the guards stand outside them). */
    public static final int LANE_X = 52;
    public static final int START_ZONE_Z0 = 0;
    public static final int START_ZONE_Z1 = 11;
    /** The start line occupies z = START_LINE_Z .. START_LINE_Z + 1. */
    public static final int START_LINE_Z = 12;
    /** The finish line occupies z = FINISH_LINE_Z .. FINISH_LINE_Z + 1 (the game treats z >= 141 as safe). */
    public static final int FINISH_LINE_Z = 139;
    public static final int SAFE_Z0 = 141;
    public static final int SAFE_Z1 = 175;

    /** Doll pedestal centre (block coordinates). */
    public static final int DOLL_X = 0;
    public static final int DOLL_Z = 147;
    public static final int PEDESTAL_R = 4;
    /** Tree trunk centre (block coordinates). */
    public static final int TREE_X = 0;
    public static final int TREE_Z = 158;

    /** Length of the closed loop around the four inner wall faces (the mural is painted along this loop). */
    public static final int PERIMETER = 2 * (2 * HALF_W + 1) + 2 * (FIELD_Z1 - FIELD_Z0 + 1);

    /** World-to-mirror helper: block x maps to -x (the arena is symmetric about the centre column x = 0). */
    public static int mx(int x) {
        return -x;
    }

    /** Continuous mirror about the centre column: a position x (block centre = block + 0.5) maps to 1 - x. */
    public static double mxd(double x) {
        return 1.0 - x;
    }
}
