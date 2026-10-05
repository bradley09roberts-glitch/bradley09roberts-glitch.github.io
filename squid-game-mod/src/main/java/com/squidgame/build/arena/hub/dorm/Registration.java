package com.squidgame.build.arena.hub.dorm;

import com.squidgame.build.BuildContext;

/**
 * The registration area in the arrival hall (south end): a white plinth against the south wall carrying the
 * registration terminal, a pink-and-white tiled arrival pad (see {@link Floor}), a bank of monitors and the big rules
 * board on the wall behind. Humans arrive at {@code dorm.player_spawn} on the pad facing the hall (north).
 */
final class Registration {
    private Registration() {
    }

    static void build(BuildContext c) {
        // plinth x[-7,7] z[27,32]: surface y = 1
        c.fill(-7, 0, 27, 7, 0, 32, Pal.WHITE);
        c.fill(-7, 0, 27, 7, 0, 27, Pal.PINK);
        c.fill(-7, 0, 27, -7, 0, 32, Pal.PINK);
        c.fill(7, 0, 27, 7, 0, 32, Pal.PINK);
        c.pattern(-6, 0, 28, 6, 0, 32, (x, y, z) -> ((x + z) & 1) == 0 ? Pal.TILE_WHITE : Pal.TILE_PINK);
        c.fill(-7, -1, 27, 7, -1, 27, Pal.BLACK);
        // central steps
        c.fill(-2, 0, 26, 2, 0, 26, Pal.stair("minecraft:quartz_stairs", "south", false));
        // the terminal, facing the hall
        c.set(0, 1, 30, "squidgame:registration_terminal[facing=north]");
        // the marker names the block itself (not a standing spot)
        c.marker("dorm.registration_terminal", 0.5, 1.0, 30.5, 180f, "stand=0");
        // low desks on either side of the terminal, each with a monitor
        for (int side = -1; side <= 1; side += 2) {
            for (int dx = 2; dx <= 5; dx++) {
                c.set(side * dx, 1, 31, Pal.STEEL_TILES);
                c.set(side * dx, 2, 31, Pal.STEEL_SLAB_B);
            }
            c.set(side * 3, 1, 30, "squidgame:monitor[facing=north]");
            c.set(side * 5, 1, 30, "squidgame:monitor[facing=north]");
        }
        // monitor bank on the south wall
        for (int x = -7; x <= 7; x++) {
            for (int y = 4; y <= 5; y++) {
                c.set(x, y, 32, "squidgame:monitor[facing=north]");
            }
        }
        c.fill(-8, 3, 32, 8, 3, 32, Pal.STEEL_TILES);
        c.fill(-8, 6, 32, 8, 6, 32, Pal.STEEL_TILES);
        c.fill(-8, 4, 32, -8, 5, 32, Pal.STEEL_TILES);
        c.fill(8, 4, 32, 8, 5, 32, Pal.STEEL_TILES);
        board(c);
        // arrival point: on the pad, facing the hall
        c.marker("dorm.player_spawn", 0.5, 0.0, 23.5, 180f);
    }

    /** The rules board on the south wall (text faces north, towards the hall). */
    private static void board(BuildContext c) {
        c.fill(-15, 9, 33, 15, 19, 33, Pal.BLACK);
        c.fill(-15, 9, 33, 15, 9, 33, Pal.PINK);
        c.fill(-15, 19, 33, 15, 19, 33, Pal.PINK);
        c.fill(-15, 9, 33, -15, 19, 33, Pal.PINK);
        c.fill(15, 9, 33, 15, 19, 33, Pal.PINK);
        c.text(0.5, 17.4, 32.9, "WELCOME, CONTESTANT", "#FFD84A", 4.4f, 180f, false);
        c.text(0.5, 14.9, 32.9, "RULE 1   YOU MAY NOT STOP PLAYING THE GAMES", "white", 2.2f, 180f, false);
        c.text(0.5, 13.2, 32.9, "RULE 2   REFUSING TO PLAY MEANS ELIMINATION", "white", 2.2f, 180f, false);
        c.text(0.5, 11.5, 32.9, "RULE 3   A MAJORITY VOTE CAN END THE GAMES", "white", 2.2f, 180f, false);
        c.text(0.5, 9.9, 32.9, "REGISTER AT THE TERMINAL", "#FF8FB3", 2.6f, 180f, false);
    }
}
