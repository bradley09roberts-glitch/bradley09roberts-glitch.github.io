package com.grandline.world;

/**
 * Flat projection of the canon globe (x = east, z = south). See docs/DESIGN.md.
 * Red Line A at x≈0 with Reverse Mountain at (0,0); Paradise east of it; Red Line B / Mary Geoise at x≈60000;
 * the New World west of Red Line A, entered from the far west after crossing via Fish-Man Island.
 */
public final class WorldLayout {
    public static final int RED_LINE_HALF_WIDTH = 320;
    public static final int RED_LINE_B_X = 60_000;
    public static final int RED_LINE_C_X = -60_000;
    public static final int GRAND_LINE_HALF = 4_000;
    public static final int CALM_BELT_OUTER = 6_500;
    public static final int WORLD_EDGE_Z = 31_000;
    public static final int WORLD_BORDER = 64_000;
    public static final int REVERSE_MOUNTAIN_RADIUS = 700;
    public static final int RED_LINE_TOP = 300;

    public static SeaZone zoneAt(int x, int z) {
        if (isRedLine(x, z)) return SeaZone.RED_LINE;
        int az = Math.abs(z);
        if (az > WORLD_EDGE_Z) return SeaZone.WORLD_EDGE;
        if (az < GRAND_LINE_HALF) return x >= 0 ? SeaZone.PARADISE : SeaZone.NEW_WORLD;
        if (az < CALM_BELT_OUTER) return SeaZone.CALM_BELT;
        if (x >= 0) return z < 0 ? SeaZone.NORTH_BLUE : SeaZone.EAST_BLUE;
        return z < 0 ? SeaZone.WEST_BLUE : SeaZone.SOUTH_BLUE;
    }

    public static boolean isRedLine(int x, int z) {
        return Math.abs(x) < RED_LINE_HALF_WIDTH
                || Math.abs(x - RED_LINE_B_X) < RED_LINE_HALF_WIDTH
                || Math.abs(x - RED_LINE_C_X) < RED_LINE_HALF_WIDTH;
    }

    /** Distance (blocks) to the nearest Red Line wall centre plane. */
    public static int redLineDistance(int x) {
        return Math.min(Math.abs(x), Math.min(Math.abs(x - RED_LINE_B_X), Math.abs(x - RED_LINE_C_X)));
    }

    private WorldLayout() {}
}
