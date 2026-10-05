package com.squidgame.build.arena.hub.control;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.hub.control.Geo.*;

/**
 * Structure of the control room: heavy underside on pylons, solid mass, carved interior, black tile floor,
 * the doorway in the shared west wall and the glazed windows with mullions and transoms.
 */
final class Shell {
    private Shell() {
    }

    static void build(BuildContext c) {
        underside(c);
        // solid mass: walls 2 thick on three sides, roof 3 thick, west wall exactly the shared plane x = 43
        c.fill(WEST, Y0, -20, 86, 35, 20, Pal.BLACK);
        c.clear(X0, Y0, Z0, X1, Y1, Z1);
        // floor layer
        c.fill(WEST, 11, -20, 86, 11, 20, Pal.PBS_BRICKS);
        c.fill(X0, 11, Z0, X1, 11, Z1, Pal.TILE_BLACK);
        // pink band round the outside at the floor line and a roof cap
        for (int x = WEST; x <= 86; x++) {
            c.set(x, 12, -20, Pal.PINK);
            c.set(x, 12, 20, Pal.PINK);
        }
        for (int z = -20; z <= 20; z++) {
            c.set(86, 12, z, Pal.PINK);
        }
        // doorway in the shared west wall (the dormitory part cuts its own wall layers)
        c.clear(WEST, Y0, DOOR_Z0, WEST, DOOR_Y1, DOOR_Z1);
        windows(c);
    }

    /** Heavy deck below the floor plus corner and mid pylons down to the hub ground. */
    private static void underside(BuildContext c) {
        c.fill(WEST, 6, -20, 86, 10, 20, Pal.PBS_BRICKS);
        c.fill(WEST, 6, -20, 86, 6, 20, Pal.BLACK);
        // pink groove lines in the underside
        for (int x = 46; x <= 84; x += 6) {
            c.fill(x, 6, -18, x, 6, 18, Pal.LIGHT_PINK);
        }
        // pylons: 4x4 columns down to y=-4 (the hub ground plinth)
        int[] px = {45, 63, 82};
        int[] pz = {-19, 15};
        for (int x : px) {
            for (int z : pz) {
                c.fill(x - 1, -4, z - 1, x + 2, 5, z + 2, Pal.PBS_BRICKS);
                c.fill(x - 1, 1, z - 1, x + 2, 1, z + 2, Pal.BLACK);
                c.fill(x - 1, 4, z - 1, x + 2, 4, z + 2, Pal.BLACK);
                for (int dx = 0; dx <= 1; dx++) {
                    c.set(x + dx, 2, z - 1, Pal.LIGHT_PINK);
                    c.set(x + dx, 2, z + 2, Pal.LIGHT_PINK);
                }
            }
        }
    }

    /**
     * Glazing of the two window openings in the shared wall plane x = 43: black tinted glass, black mullions (3-1-6-1-3
     * rhythm), a transom at y=22, frame rows at the sill and head.
     */
    private static void windows(BuildContext c) {
        for (int[] w : WINDOWS) {
            int z0 = w[0], z1 = w[1];
            c.fill(WEST, WIN_Y0, z0, WEST, WIN_Y1, z1, Pal.GLASS_BLACK);
            // mullions
            int[] mul = z0 < 0 ? new int[]{-13, -6} : new int[]{6, 13};
            for (int z : mul) {
                c.fill(WEST, WIN_Y0, z, WEST, WIN_Y1, z, Pal.PBS);
            }
            // transom
            c.fill(WEST, 22, z0, WEST, 22, z1, Pal.PBS);
        }
    }
}
