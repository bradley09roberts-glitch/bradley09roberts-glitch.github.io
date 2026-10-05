package com.squidgame.build.arena.hub.corridor;

import java.util.ArrayList;
import java.util.List;

/**
 * The floor plan of the guard compound (hub local coordinates, standing level y = 0, floor blocks at y = -1).
 *
 * <pre>
 *  z=-80  north wall (doorway to the stairway hall at x[-3,3])
 *        +-------------------------------------------------+
 *        | NW corner    E2: final corridor (z -79..-73)   NE |
 *        |   N2   +---------------------------------+   N1  |
 *        |  (west |  armory | office  | store room   | (east |
 *        |  arm)  +---------------------------------+  arm) |   central block x[-21,21]
 *        |        | barracks| BC |  monitor room      |       |   z[-71,-53]
 *        | SW corner    E1: south corridor (z -51..-45)  SE |
 *        +---------------------------------------------------+
 *        |  canteen   |  checkpoint hall  |   infirmary      |  z[-43,-36]
 *  z=-35 +------------[ dorm door x(-3..3) ]-----------------+
 * </pre>
 *
 * The compound is a ring of 7-wide corridors around a block of guard rooms; the dorm door opens into the checkpoint
 * hall, a 7-wide gate leads north into E1 and from there both arms lead round to E2, whose north wall carries the
 * doorway into the stairway hall.
 */
final class Plan {
    private Plan() {
    }

    /** Envelope of the whole compound. */
    static final int X0 = -30, X1 = 30, Z0 = -80, Z1 = -35;
    /** y of the floor blocks and top of the solid mass. */
    static final int FLOOR_Y = -1, ROOF_Y = 11;
    /** Normal corridor ceiling plane (interior y 0..5) and tall hall ceiling plane (interior y 0..8). */
    static final int CEIL = 6, HALL_CEIL = 9;

    enum Kind { CORRIDOR_X, CORRIDOR_Z, HALL, CORNER, ROOM }

    /** A rectangular carved space. {@code h} = interior height (ceiling block at y = h). */
    static final class Space {
        final String name;
        final Kind kind;
        final int x0, x1, z0, z1, h;
        final int order;

        Space(String name, Kind kind, int x0, int x1, int z0, int z1, int h) {
            this.name = name;
            this.kind = kind;
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.h = h;
            this.order = ALL.size();
            ALL.add(this);
        }

        boolean contains(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        boolean corridor() {
            return kind == Kind.CORRIDOR_X || kind == Kind.CORRIDOR_Z;
        }

        double cx() {
            return (x0 + x1) / 2.0 + 0.5;
        }

        double cz() {
            return (z0 + z1) / 2.0 + 0.5;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static final List<Space> ALL = new ArrayList<>();

    // ---- rooms (painted first so that corridors win shared walls)
    static final Space CK = new Space("checkpoint", Kind.HALL, -10, 10, -43, -36, 9);
    static final Space CANTEEN = new Space("canteen", Kind.ROOM, -29, -12, -43, -36, 6);
    static final Space INFIRM = new Space("infirmary", Kind.ROOM, 12, 29, -43, -36, 6);
    static final Space BARRACKS = new Space("barracks", Kind.ROOM, -21, -5, -61, -53, 6);
    static final Space MONITOR = new Space("monitor", Kind.ROOM, 5, 21, -61, -53, 6);
    static final Space ARMORY = new Space("armory", Kind.ROOM, -21, -8, -71, -63, 6);
    static final Space OFFICE = new Space("office", Kind.ROOM, -6, 6, -71, -63, 7);
    static final Space STORE = new Space("store", Kind.ROOM, 8, 21, -71, -63, 6);
    // ---- corridors
    static final Space BC = new Space("branch", Kind.CORRIDOR_Z, -3, 3, -61, -53, 6);
    static final Space E1 = new Space("e1", Kind.CORRIDOR_X, -29, 29, -51, -45, 6);
    static final Space E2 = new Space("e2", Kind.CORRIDOR_X, -29, 29, -79, -73, 6);
    static final Space N1 = new Space("n1", Kind.CORRIDOR_Z, 23, 29, -72, -52, 6);
    static final Space N2 = new Space("n2", Kind.CORRIDOR_Z, -29, -23, -72, -52, 6);
    // ---- tall halls (override the corridor cells they cover)
    static final Space J1 = new Space("j1", Kind.HALL, -6, 6, -51, -45, 9);
    static final Space C_SE = new Space("corner_se", Kind.CORNER, 23, 29, -51, -45, 9);
    static final Space C_SW = new Space("corner_sw", Kind.CORNER, -29, -23, -51, -45, 9);
    static final Space C_NE = new Space("corner_ne", Kind.CORNER, 23, 29, -79, -73, 9);
    static final Space C_NW = new Space("corner_nw", Kind.CORNER, -29, -23, -79, -73, 9);
    /** Wide vestibule in front of the stairway doorway (overrides part of E2). */
    static final Space E2V = new Space("e2_vestibule", Kind.HALL, -8, 8, -79, -73, 9);

    // ---- owner lookup (last space in ALL order wins)
    private static final Space[][] GRID = new Space[X1 - X0 + 1][Z1 - Z0 + 1];

    static {
        for (Space s : ALL) {
            for (int x = s.x0; x <= s.x1; x++) {
                for (int z = s.z0; z <= s.z1; z++) {
                    GRID[x - X0][z - Z0] = s;
                }
            }
        }
    }

    /** The effective space at a cell or null for solid. */
    static Space at(int x, int z) {
        if (x < X0 || x > X1 || z < Z0 || z > Z1) {
            return null;
        }
        return GRID[x - X0][z - Z0];
    }

    // ---- doors

    /**
     * An opening cut through a 1-thick wall. {@code alongX}: the wall plane is z = {@code z0 == z1} and the opening
     * spans x0..x1; otherwise the plane is x = {@code x0 == x1} and it spans z0..z1. {@code h} = opening height
     * (y 0..h-1). {@code a} is the space on the side of the lower coordinate, {@code b} on the higher side.
     */
    static final class Door {
        final String name;
        final boolean alongX;
        final int x0, x1, z0, z1, h;
        final Space a, b;
        final int number;

        Door(String name, boolean alongX, int x0, int x1, int z0, int z1, int h, Space a, Space b, int number) {
            this.name = name;
            this.alongX = alongX;
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.h = h;
            this.a = a;
            this.b = b;
            this.number = number;
            DOORS.add(this);
        }

        int width() {
            return alongX ? x1 - x0 + 1 : z1 - z0 + 1;
        }

        /** Centre along the wall (x for alongX, else z), block-centre coordinate. */
        double centre() {
            return alongX ? (x0 + x1) / 2.0 + 0.5 : (z0 + z1) / 2.0 + 0.5;
        }
    }

    static final List<Door> DOORS = new ArrayList<>();

    // the wall plane is between a (lower coordinate = north / west) and b (higher = south / east)
    static final Door D_DORM = new Door("dorm", true, -3, 3, -35, -35, 6, CK, null, 0);
    static final Door D_CK_E1 = new Door("ck_e1", true, -3, 3, -44, -44, 6, E1, CK, 1);
    static final Door D_CK_CANTEEN = new Door("ck_canteen", false, -11, -11, -41, -38, 5, CANTEEN, CK, 2);
    static final Door D_CK_INFIRM = new Door("ck_infirm", false, 11, 11, -41, -38, 5, CK, INFIRM, 3);
    static final Door D_E1_CANTEEN = new Door("e1_canteen", true, -21, -17, -44, -44, 4, E1, CANTEEN, 4);
    static final Door D_E1_INFIRM = new Door("e1_infirm", true, 17, 21, -44, -44, 4, E1, INFIRM, 5);
    static final Door D_E1_BARRACKS = new Door("e1_barracks", true, -15, -11, -52, -52, 4, BARRACKS, E1, 6);
    static final Door D_E1_BC = new Door("e1_bc", true, -3, 3, -52, -52, 6, BC, E1, 7);
    static final Door D_E1_MONITOR = new Door("e1_monitor", true, 11, 15, -52, -52, 4, MONITOR, E1, 8);
    static final Door D_BC_OFFICE = new Door("bc_office", true, -2, 2, -62, -62, 5, OFFICE, BC, 9);
    static final Door D_BC_BARRACKS = new Door("bc_barracks", false, -4, -4, -59, -55, 4, BARRACKS, BC, 10);
    static final Door D_BC_MONITOR = new Door("bc_monitor", false, 4, 4, -59, -55, 4, BC, MONITOR, 11);
    static final Door D_E2_ARMORY = new Door("e2_armory", true, -17, -13, -72, -72, 4, E2, ARMORY, 12);
    static final Door D_E2_STORE = new Door("e2_store", true, 12, 16, -72, -72, 4, E2, STORE, 13);
    static final Door D_N2_ARMORY = new Door("n2_armory", false, -22, -22, -69, -65, 4, N2, ARMORY, 14);
    static final Door D_N1_STORE = new Door("n1_store", false, 22, 22, -69, -65, 4, STORE, N1, 15);
    static final Door D_N2_BARRACKS = new Door("n2_barracks", false, -22, -22, -59, -55, 4, N2, BARRACKS, 16);
    static final Door D_N1_MONITOR = new Door("n1_monitor", false, 22, 22, -59, -55, 4, MONITOR, N1, 17);
    static final Door D_STAIRS = new Door("stairs", true, -3, 3, -80, -80, 6, null, E2V, 18);

    static Door door(String name) {
        for (Door d : DOORS) {
            if (d.name.equals(name)) {
                return d;
            }
        }
        throw new IllegalArgumentException(name);
    }
}
