package com.squidgame.build.arena.hub.control;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.hub.control.Geo.*;

/**
 * Viewing galleries in front of the two windows: terraced platforms (standing level 14, two steps up from the floor)
 * so that eyes are above the window sill and one can look down over the dormitory; black mullion pilasters with
 * pink light strips frame each pane. The central doorway zone (|z| &lt;= 6) is left free for the lobby.
 */
final class Galleries {
    private Galleries() {
    }

    static void build(BuildContext c) {
        for (int[] w : new int[][]{{Z0, -7}, {7, Z1}}) {
            int z0 = w[0], z1 = w[1];
            // platform (x 44..45) top standing level 14, solid under the upper step (x 46) and the lower step (x 47)
            c.fill(44, 12, z0, 45, 13, z1, Pal.PBS_BRICKS);
            c.fill(46, 12, z0, 46, 12, z1, Pal.PBS_BRICKS);
            for (int z = z0; z <= z1; z++) {
                c.set(46, 13, z, Pal.stairs("minecraft:polished_blackstone_brick_stairs", "west", false));
                c.set(47, 12, z, Pal.stairs("minecraft:polished_blackstone_brick_stairs", "west", false));
                // glowing strip along the foot of the glass (flush with the platform top)
                if (z % 2 == 0) {
                    c.set(44, 13, z, Pal.LIGHT_PINK);
                }
                // the floor tile row in front of the lower step
                c.set(48, 11, z, Pal.PBS);
            }
        }
        // pilasters over the mullions (interior side), with pink light strips
        for (int z : new int[]{-13, -6, 6, 13}) {
            for (int y = Math.abs(z) == 6 ? 12 : 14; y <= Y1; y++) {
                c.set(44, y, z, Pal.PBS);
            }
        }
        // light strips along the pier edges beside the door and over the window edges
        for (int z : new int[]{-3, 3}) {
            for (int y = 16; y <= 31; y++) {
                c.set(44, y, z, Pal.LIGHT_PINK);
            }
        }
        // the pier above the doorway: stacked symbols
        c.set(44, 22, 0, Pal.SYM_CIRCLE);
        c.set(44, 25, 0, Pal.SYM_TRIANGLE);
        c.set(44, 28, 0, Pal.SYM_SQUARE);
        for (int y = 17; y <= 30; y++) {
            if (y != 22 && y != 25 && y != 28) {
                c.set(44, y, 0, Pal.BLACK);
            }
        }
    }
}
