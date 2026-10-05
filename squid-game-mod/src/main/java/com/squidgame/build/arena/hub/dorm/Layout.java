package com.squidgame.build.arena.hub.dorm;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared geometry of the dormitory (hub frame: +X east, +Z south, standing level y = 0, floor blocks at y = -1).
 *
 * <pre>
 *   z = -33  north wall, EXIT door (x[-3,3] y[0,5])           z = -32 .. -20  exit lobby (guards)
 *   z = -19 .. 19   three rows of bunk towers either side of the central avenue / plaza (podium, hanging prize pig)
 *   z =  20 ..  32  arrival hall: registration plinth against the south wall
 *   x =  41..43  east wall: black windows onto the control room, catwalk + stair on its inner side
 * </pre>
 */
final class Layout {
    private Layout() {
    }

    // interior
    static final int X0 = -40, X1 = 40, Z0 = -32, Z1 = 32, H = 36;
    static final int ROOF = 37;

    // bunk towers: 7 x 9 footprint, six sleeping tiers of pitch 4 and a top frame at y = 24
    static final int TOWER_HX = 3, TOWER_HZ = 4;
    static final int TIERS = 6, PITCH = 4;
    static final int TOWER_TOP = TIERS * PITCH;
    static final int[] TOWER_X = {-29, -16, 16, 29};
    static final int[] TOWER_Z = {-15, 0, 15};

    // roof trusses (span the hall along x)
    static final int[] TRUSS_Z = {-28, -20, -12, -4, 4, 12, 20, 28};
    static final int TRUSS_BOTTOM = 31, TRUSS_TOP = 36;

    // the prize pig hangs over the plaza centre
    static final int PIG_X = 0, PIG_Z = 0, PIG_BOTTOM = 14;

    // central plaza + podium
    static final int PLAZA_R = 11;

    // registration plinth (south wall)
    static final int REG_Z0 = 27, REG_Z1 = 31;

    // east gallery (catwalk)
    static final int GALLERY_Y = 12;          // standing level of the catwalk
    static final int GALLERY_X0 = 36, GALLERY_X1 = 40;
    static final int GALLERY_Z0 = -17, GALLERY_Z1 = 17;

    record Tower(int id, int cx, int cz) {
        String label() {
            return String.valueOf((char) ('A' + id));
        }
    }

    /** The twelve towers, row by row from north to south, west to east. */
    static List<Tower> towers() {
        List<Tower> out = new ArrayList<>();
        int id = 0;
        for (int z : TOWER_Z) {
            for (int x : TOWER_X) {
                out.add(new Tower(id++, x, z));
            }
        }
        return out;
    }
}
