package com.squidgame.build.arena.hub.dorm;

import com.squidgame.build.BuildContext;

/**
 * The east wall towards the control room: two huge black-tinted windows (x = 41..43, z[-16,-3] and z[3,16],
 * y[14,30]) in steel frames, the shared wall layer x = 43, and the control-room door opening (x = 41..43, z[-2,2],
 * y[12,15]) with a hazard-striped frame and a sign. The wall is otherwise solid.
 */
final class EastWall {
    private EastWall() {
    }

    static final String GLASS = "minecraft:black_stained_glass";
    private static final String HAZ_Y = "minecraft:yellow_concrete";

    static void build(BuildContext c) {
        // the control room's west wall is the third layer of the shared wall
        c.fill(43, 11, -19, 43, 33, 19, Pal.LGRAY);
        for (int[] w : new int[][]{{-16, -3}, {3, 16}}) {
            window(c, w[0], w[1]);
        }
        door(c);
    }

    private static void window(BuildContext c, int z0, int z1) {
        c.fill(41, 14, z0, 43, 30, z1, GLASS);
        // steel frame in the inner wall plane, with a pastel inner line
        c.fill(41, 13, z0 - 1, 41, 13, z1 + 1, Pal.STEEL);
        c.fill(41, 31, z0 - 1, 41, 31, z1 + 1, Pal.STEEL);
        c.fill(41, 13, z0 - 1, 41, 31, z0 - 1, Pal.STEEL);
        c.fill(41, 13, z1 + 1, 41, 31, z1 + 1, Pal.STEEL);
        c.fill(41, 32, z0 - 1, 41, 32, z1 + 1, Pal.MINT);
        c.fill(41, 12, z0 - 1, 41, 12, z1 + 1, Pal.PINK);
    }

    /**
     * The door (z[-2,2], y[12,15]) touches the two windows (which start at z = +-3 and y = 14), so its frame is only a pair
     * of hazard-striped sills below the glass and a lintel in the 5-wide pier between the windows; above the lintel the pier
     * carries the sign.
     */
    private static void door(BuildContext c) {
        c.clear(41, 12, -2, 43, 15, 2);
        // jambs below the windows (y 12..13) and the lintel in the pier
        for (int y = 12; y <= 13; y++) {
            for (int z : new int[]{-3, 3}) {
                c.set(41, y, z, ((y + Math.abs(z)) & 1) == 0 ? HAZ_Y : Pal.BLACK);
            }
        }
        for (int z = -2; z <= 2; z++) {
            c.set(41, 16, z, (z & 1) == 0 ? HAZ_Y : Pal.BLACK);
        }
        c.fill(41, 17, -2, 41, 17, 2, Pal.STEEL);
        // sign plate on the pier above the lintel
        c.fill(41, 18, -2, 41, 22, 2, Pal.BLACK);
        c.fill(41, 18, -2, 41, 18, 2, Pal.PINK);
        c.fill(41, 22, -2, 41, 22, 2, Pal.PINK);
        c.text(40.9, 20.0, 0.5, "CONTROL", "#FFD84A", 1.7f, 90f, false);
        c.text(40.9, 19.2, 0.5, "ROOM", "#FFD84A", 1.7f, 90f, false);
        c.text(40.9, 18.5, 0.5, "STAFF ONLY", "white", 0.9f, 90f, false);
    }
}
