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

    private static void door(BuildContext c) {
        c.clear(41, 12, -2, 43, 15, 2);
        // frame: jambs at z = +-3 and lintel at y = 16, hazard striped
        for (int y = 12; y <= 16; y++) {
            for (int z = -3; z <= 3; z++) {
                boolean frame = y == 16 || z == -3 || z == 3;
                if (frame) {
                    c.set(41, y, z, ((y + Math.abs(z)) & 1) == 0 ? HAZ_Y : Pal.BLACK);
                }
            }
        }
        c.fill(41, 17, -4, 41, 17, 4, Pal.STEEL);
        // sign plate above the door
        c.fill(41, 18, -6, 41, 20, 6, Pal.BLACK);
        c.fill(41, 18, -6, 41, 18, 6, Pal.PINK);
        c.fill(41, 20, -6, 41, 20, 6, Pal.PINK);
        c.text(40.9, 19.4, 0.5, "CONTROL ROOM", "#FFD84A", 2.6f, 90f, false);
        c.text(40.9, 18.5, 0.5, "STAFF ONLY", "white", 1.4f, 90f, false);
    }
}
