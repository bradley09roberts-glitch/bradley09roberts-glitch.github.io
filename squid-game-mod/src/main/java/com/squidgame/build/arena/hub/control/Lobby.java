package com.squidgame.build.arena.hub.control;

import com.squidgame.build.BuildContext;

/**
 * The entrance lobby behind the doorway in the shared wall (x[44,49], z[-6,6]): a glowing pink runner from the door, a
 * security scanner arch, a reception counter with a monitor, symbol pylons and the room's name sign.
 */
final class Lobby {
    private Lobby() {
    }

    static void build(BuildContext c) {
        // floor: pink runner and glowing threshold
        for (int x = 44; x <= 51; x++) {
            for (int z = -1; z <= 1; z++) {
                c.set(x, 11, z, Pal.TILE_PINK);
            }
            c.set(x, 11, -2, Pal.TILE_WHITE);
            c.set(x, 11, 2, Pal.TILE_WHITE);
        }
        for (int z = -2; z <= 2; z++) {
            c.set(43, 11, z, Pal.LIGHT_PINK);
        }
        c.set(46, 11, 0, Pal.LIGHT_PINK);
        c.set(49, 11, 0, Pal.LIGHT_PINK);
        // scanner arch at x = 47
        for (int z : new int[]{-3, 3}) {
            for (int y = 12; y <= 15; y++) {
                c.set(47, y, z, y == 13 ? Pal.LIGHT_PINK : Pal.PBS);
            }
        }
        for (int z = -3; z <= 3; z++) {
            c.set(47, 16, z, Pal.PBS);
        }
        c.set(47, 16, 0, Pal.LIGHT_PINK);
        c.text(46.95, 17.2, 0.5, "CONTROL ROOM", "#FFFFFF", 1.6f, 90f, false);
        c.text(46.95, 16.75, 0.5, "AUTHORIZED PERSONNEL ONLY", "#FF7AA8", 0.8f, 90f, false);
        // reception counters either side of the runner
        for (int side = -1; side <= 1; side += 2) {
            for (int x = 45; x <= 46; x++) {
                c.set(x, 12, side * 5, Pal.PBS);
                c.set(x, 12, side * 4, x == 45 ? Pal.LIGHT_PINK : Pal.PBS);
            }
            c.set(46, 13, side * 5, Pal.monitor("east"));
            c.set(45, 13, side * 5, Pal.monitor("east"));
        }
        // symbol pylons
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 12; y <= 14; y++) {
                c.set(49, y, side * 5, y == 14 ? Pal.symbol(side + 1) : Pal.PBS);
            }
        }
    }
}
