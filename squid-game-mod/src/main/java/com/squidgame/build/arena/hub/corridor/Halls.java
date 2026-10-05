package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.hub.corridor.Plan.Space;

/**
 * The tall junction halls: J1 (where the checkpoint gate meets the south corridor), the four corner halls of the ring and
 * the rainbow vestibule in front of the stairway doorway.
 */
final class Halls {
    private Halls() {
    }

    static void build(BuildContext c) {
        j1(c);
        for (Space s : new Space[]{Plan.C_SE, Plan.C_SW, Plan.C_NE, Plan.C_NW}) {
            corner(c, s);
        }
        vestibule(c);
    }

    // ------------------------------------------------------------------ J1

    private static void j1(BuildContext c) {
        // ceiling: ring of light panels around a black eye, black cross beams hanging at the edges
        int cx = 0, cz = -48;
        for (int x = -6; x <= 6; x++) {
            for (int z = -51; z <= -45; z++) {
                double r = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (r <= 3.3 && r >= 2.2) {
                    c.set(x, 9, z, Pal.LIGHT_WHITE);
                } else if (r < 1.2) {
                    c.set(x, 9, z, Pal.BLACK);
                } else {
                    c.set(x, 9, z, Pal.WHITE);
                }
            }
        }
        for (int x = -6; x <= 6; x += 3) {
            c.fill(x, 8, -51, x, 8, -45, Pal.BLACK);
        }
        c.fill(-6, 8, -51, 6, 8, -51, Pal.BLACK);
        c.fill(-6, 8, -45, 6, 8, -45, Pal.BLACK);
        // pendant lanterns on chains round the centre
        for (int[] p : new int[][]{{-2, -50}, {2, -50}, {-2, -46}, {2, -46}}) {
            c.fill(p[0], 6, p[1], p[0], 8, p[1], Pal.chain("y"));
            c.set(p[0], 5, p[1], Pal.lantern(true));
        }
        // floor: glowing heart of the junction
        c.set(0, -1, -48, Pal.LIGHT_PINK);
        // three symbol cubes above the staff wing doorway (north wall z=-52) and above the checkpoint gate (z=-44)
        for (int i = -1; i <= 1; i++) {
            c.set(i * 2, 7, -52, Pal.symbol(i + 1));
            c.set(i * 2, 7, -44, Pal.symbol(i + 1));
        }
        for (int x = -4; x <= 4; x++) {
            if (Math.abs(x) % 2 == 1) {
                c.set(x, 7, -52, Pal.PINK);
                c.set(x, 7, -44, Pal.PINK);
            }
            if (Math.abs(x) % 2 == 0 && Math.abs(x) > 2) {
                c.set(x, 7, -52, Pal.PINK);
                c.set(x, 7, -44, Pal.PINK);
            }
            c.set(x, 6, -52, Pal.BLACK);
            c.set(x, 8, -52, Pal.BLACK);
            c.set(x, 6, -44, Pal.BLACK);
            c.set(x, 8, -44, Pal.BLACK);
        }
        c.text(0.5, 6.1, -52.04, "STAFF WING", "white", 1.2f, 180f, false);
    }

    // ------------------------------------------------------------------ corners

    private static void corner(BuildContext c, Space s) {
        double cx = (s.x0 + s.x1) / 2.0, cz = (s.z0 + s.z1) / 2.0;
        int ix = (int) Math.round(cx), iz = (int) Math.round(cz);
        c.set(ix, -1, iz, Pal.LIGHT_PINK);
        // ceiling: 3x3 light cluster in a black frame
        c.fill(ix - 2, 9, iz - 2, ix + 2, 9, iz + 2, Pal.BLACK);
        c.fill(ix - 1, 9, iz - 1, ix + 1, 9, iz + 1, Pal.LIGHT_WHITE);
        for (int dx = -3; dx <= 3; dx += 6) {
            for (int dz = -3; dz <= 3; dz += 6) {
                c.fill(ix + dx, 6, iz + dz, ix + dx, 8, iz + dz, Pal.chain("y"));
                c.set(ix + dx, 5, iz + dz, Pal.lantern(true));
            }
        }
    }

    // ------------------------------------------------------------------ vestibule (rainbow frame round the stairway doorway)

    private static void vestibule(BuildContext c) {
        // north wall z=-80, x -8..8, y 1..8
        for (int x = -8; x <= 8; x++) {
            int ax = Math.abs(x);
            for (int y = 1; y <= 8; y++) {
                String b;
                if (ax >= 4) {
                    // vertical pastel columns, pink next to the doorway ... sky at the edge
                    b = y == 8 ? Pal.BLACK : Pal.pastel(ax - 4);
                } else if (y >= 6) {
                    b = y == 8 ? Pal.pastel(x + 3) : Pal.BLACK;
                } else {
                    continue;
                }
                c.set(x, y, -80, b);
            }
            c.set(x, 0, -80, ax >= 4 ? Pal.LIGHT_WHITE : Pal.TILE_PINK);
        }
        // the doorway itself stays clear (cut by Doors); re-clear defensively
        c.clear(-3, 0, -80, 3, 5, -80);
        c.text(0.5, 6.15, -79.04 + 0.0, "STAIRWAY", "white", 2.2f, 0f, false);
        // ceiling: three rows of light panels over the runner
        c.fill(-8, 9, -79, 8, 9, -73, Pal.WHITE);
        for (int z = -78; z <= -74; z += 2) {
            c.fill(-3, 9, z, 3, 9, z, Pal.LIGHT_WHITE);
        }
        for (int x = -8; x <= 8; x += 4) {
            c.fill(x, 8, -79, x, 8, -73, Pal.BLACK);
        }
    }
}
