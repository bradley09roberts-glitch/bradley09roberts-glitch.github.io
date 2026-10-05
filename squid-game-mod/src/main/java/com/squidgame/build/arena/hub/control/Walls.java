package com.squidgame.build.arena.hub.control;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.hub.control.Geo.*;

/**
 * Skins the interior surfaces of the control room: deepslate-tile panels between polished blackstone pilasters with
 * vertical pink light slits and a teal sea-lantern cove line on the long north and south walls, the window wall
 * (piers, base, corner columns), and a coffered ceiling with long pink light strips and teal rosettes.
 */
final class Walls {
    private Walls() {
    }

    static void build(BuildContext c) {
        longWalls(c);
        windowWall(c);
        eastWall(c);
        ceiling(c);
    }

    // ------------------------------------------------------------------ north and south walls

    private static void longWalls(BuildContext c) {
        for (int x = X0; x <= X1; x++) {
            boolean pil = Math.floorMod(x, 6) == 2;
            boolean slit = pil && Math.floorMod(x, 12) == 2;
            for (int y = Y0; y <= Y1; y++) {
                String b;
                if (pil) {
                    b = slit && y >= 15 && y <= 29 ? Pal.LIGHT_PINK : Pal.PBS;
                } else if (y <= 13) {
                    b = Pal.PBS_BRICKS;
                } else if (y == 22) {
                    // teal cove dashes
                    b = Math.floorMod(x, 6) >= 3 ? Pal.SEA : Pal.PBS;
                } else if (y == Y1) {
                    b = Pal.PBS;
                } else {
                    b = ((x + y) & 3) == 0 ? Pal.POLISHED_DEEPSLATE : Pal.DEEPSLATE_TILES;
                }
                c.set(x, y, NORTH, b);
                c.set(x, y, SOUTH, b);
            }
        }
    }

    // ------------------------------------------------------------------ west wall (shared with the dormitory)

    private static void windowWall(BuildContext c) {
        // below the sills and above the heads, and the corner columns beyond the windows, the pier above the door
        for (int z = Z0; z <= Z1; z++) {
            boolean inWindow = (z >= -16 && z <= -3) || (z >= 3 && z <= 16);
            boolean inDoor = z >= DOOR_Z0 && z <= DOOR_Z1;
            for (int y = Y0; y <= Y1; y++) {
                if (inWindow && y >= WIN_Y0 && y <= WIN_Y1) {
                    continue;
                }
                if (inDoor && y <= DOOR_Y1) {
                    continue;
                }
                String b = y <= 13 ? Pal.PBS_BRICKS : ((z + y) & 3) == 0 ? Pal.POLISHED_DEEPSLATE : Pal.DEEPSLATE_TILES;
                if (y == Y1 || y == 31) {
                    b = Pal.PBS;
                }
                c.set(WEST, y, z, b);
            }
        }
        // glowing sill line under the windows (teal)
        for (int z = -16; z <= 16; z++) {
            boolean inWindow = (z >= -16 && z <= -3) || (z >= 3 && z <= 16);
            if (inWindow && z % 2 == 0) {
                c.set(WEST, 13, z, Pal.SEA);
            }
        }
        // door frame on the interior side: pink-edged black pilasters
        for (int z : new int[]{-3, 3}) {
            for (int y = Y0; y <= 17; y++) {
                c.set(44, y, z, y == Y0 ? Pal.PBS : Pal.LIGHT_PINK);
            }
        }
        for (int z = -3; z <= 3; z++) {
            c.set(44, 16, z, Pal.PBS);
            c.set(44, 17, z, Pal.LIGHT_PINK);
        }
    }

    // ------------------------------------------------------------------ east wall backing (monitors are placed by DisplayWall)

    private static void eastWall(BuildContext c) {
        c.fill(EAST, Y0, Z0, EAST, Y1, Z1, Pal.BLACK);
        // corner columns
        for (int y = Y0; y <= Y1; y++) {
            c.set(X1, y, Z0, Pal.PBS);
            c.set(X1, y, Z1, Pal.PBS);
        }
    }

    // ------------------------------------------------------------------ ceiling

    private static void ceiling(BuildContext c) {
        c.fill(X0, CEIL, Z0, X1, CEIL, Z1, Pal.DEEPSLATE_TILES);
        // cross beams (hanging two blocks) every 6 blocks
        for (int x = X0 + 2; x <= X1; x += 6) {
            c.fill(x, 31, Z0, x, 32, Z1, Pal.PBS);
        }
        // long pink light strips under the ceiling plane between the beams
        for (int z : new int[]{-12, -6, 0, 6, 12}) {
            for (int x = X0; x <= X1; x++) {
                if (Math.floorMod(x, 6) != 2) {
                    c.set(x, CEIL, z, Pal.LIGHT_PINK);
                }
            }
        }
        // teal rosettes (sea lanterns) at the beam crossings of the outer strips
        for (int x = X0 + 2; x <= X1; x += 6) {
            for (int z : new int[]{-9, -3, 3, 9}) {
                c.set(x, 31, z, Pal.SEA);
            }
        }
    }
}
