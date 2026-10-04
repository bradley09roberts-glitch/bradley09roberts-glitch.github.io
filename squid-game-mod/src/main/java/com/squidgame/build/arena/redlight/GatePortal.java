package com.squidgame.build.arena.redlight;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.redlight.Layout.*;

/**
 * The transition from the waiting room to the playground: a covered gate hall in front of the waiting-room doorway
 * (plane z = -8), a monumental lintel carrying the three symbols, and a glazed control gallery on top whose floor is
 * the spectator viewpoint. Floor "runway lights" and pylon light strips keep the covered part brightly lit.
 *
 * <pre>
 *   x[-13,13] z[-7,-1]    hall: floor y=0, free height 13 (y 1..13), 21 wide between the pylons
 *   y[14,22]              the lintel mass (solid), symbols on its south face (z=-1)
 *   y[23,27]              control gallery (interior z -6..-4), glass front at z=-3
 * </pre>
 */
public final class GatePortal {
    private GatePortal() {
    }

    private static final String BLACK = "minecraft:black_concrete";
    private static final String PINK = "minecraft:pink_concrete";
    private static final String WHITE = "minecraft:white_concrete";
    private static final String GRAY = "minecraft:gray_concrete";
    private static final String LANTERN = "minecraft:sea_lantern";

    public static final int MASS_Y0 = 14;
    public static final int MASS_Y1 = 22;
    public static final int GALLERY_FLOOR = MASS_Y1;      // top block of the mass
    public static final int GALLERY_Y1 = 26;              // top interior row
    public static final int GALLERY_ROOF = 27;

    public static void build(BuildContext c) {
        floor(c);
        pylons(c);
        mass(c);
        symbols(c);
        gallery(c);
        c.text(0.5, GALLERY_ROOF + 2.5, -3.5, "RED LIGHT, GREEN LIGHT", "#FFD84A", 7f, 0f, false);
    }

    // ------------------------------------------------------------------ hall floor and lighting

    private static void floor(BuildContext c) {
        c.fill(-13, 0, -7, 13, 0, -1, "squidgame:tile_black");
        // pink runner in line with the doorway, white edge lines
        c.fill(-3, 0, -7, 3, 0, -1, "squidgame:tile_pink");
        c.fill(-4, 0, -7, -4, 0, -1, "squidgame:tile_white");
        c.fill(4, 0, -7, 4, 0, -1, "squidgame:tile_white");
        // runway lights flush in the floor
        for (int z = -7; z <= -1; z += 2) {
            c.set(-5, 0, z, LANTERN);
            c.set(5, 0, z, LANTERN);
        }
        for (int z = -6; z <= -2; z += 4) {
            c.set(-9, 0, z, LANTERN);
            c.set(9, 0, z, LANTERN);
        }
        c.set(-12, 0, -4, LANTERN);
        c.set(12, 0, -4, LANTERN);
        // top up with a covering lattice so no cell of the hall drops below level 11
        Lighting.floorGrid(c, -12, -7, 12, -1, 3, LANTERN, (x, z) -> true,
                (x, z) -> c.get(x, 0, z) != null && !c.isSolid(x, 1, z), LANTERN);
    }

    private static void pylons(BuildContext c) {
        for (int side = -1; side <= 1; side += 2) {
            for (int zb : new int[]{-7, -3}) {
                int x1 = side > 0 ? 11 : -13;
                int x2 = side > 0 ? 13 : -11;
                c.fill(x1, 1, zb, x2, MASS_Y0 - 1, zb + 2, BLACK);
                // pink inlay on the inward-facing side, white plinth and cap
                int inner = side > 0 ? 11 : -11;
                c.fill(inner, 2, zb + 1, inner, MASS_Y0 - 2, zb + 1, PINK);
                c.fill(x1, 1, zb, x2, 1, zb + 2, GRAY);
                c.fill(x1, MASS_Y0 - 1, zb, x2, MASS_Y0 - 1, zb + 2, WHITE);
            }
            // vertical light strips on the front pylons (south face) and inner faces
            int fx = side > 0 ? 12 : -12;
            for (int y = 3; y <= MASS_Y0 - 3; y += 2) {
                c.set(fx, y, -1, LANTERN);
            }
            int ix = side > 0 ? 11 : -11;
            for (int y = 4; y <= MASS_Y0 - 3; y += 3) {
                c.set(ix, y, -2, LANTERN);
                c.set(ix, y, -6, LANTERN);
            }
        }
    }

    private static void mass(BuildContext c) {
        c.fill(-13, MASS_Y0, -7, 13, MASS_Y1, -1, BLACK);
        // underside trim and cornice
        c.fill(-13, MASS_Y0, -7, 13, MASS_Y0, -1, GRAY);
        c.fill(-13, MASS_Y1, -7, 13, MASS_Y1, -1, "squidgame:tile_black");
        c.fill(-13, MASS_Y1 - 1, -1, 13, MASS_Y1 - 1, -1, WHITE);
        c.fill(-13, MASS_Y0 + 1, -1, 13, MASS_Y0 + 1, -1, PINK);
        // embedded ceiling lights over the hall (underside)
        for (int x = -9; x <= 9; x += 6) {
            for (int z = -6; z <= -2; z += 2) {
                c.set(x, MASS_Y0, z, "squidgame:panel_light_warm");
            }
        }
    }

    // ------------------------------------------------------------------ symbols on the lintel face (z = -1)

    private static void symbols(BuildContext c) {
        // circle at x=-8, triangle at x=0, square at x=+8, each 7 wide, y 15..21 (centre y = 18.5)
        circle(c, -8, 18.5, 3.4);
        triangle(c, 0, 15, 21, 3);
        square(c, 5, 15, 11, 21);
    }

    private static void circle(BuildContext c, int cx, double cy, double r) {
        for (int x = cx - 5; x <= cx + 5; x++) {
            for (int y = (int) (cy - 5); y <= (int) (cy + 5); y++) {
                double d = Math.hypot(x - cx, y + 0.5 - cy);
                if (d <= r + 0.3 && d > r - 1.0) {
                    c.set(x, y, -1, WHITE);
                } else if (d <= r - 1.0) {
                    c.set(x, y, -1, PINK);
                }
            }
        }
    }

    private static void triangle(BuildContext c, int cx, int y0, int y1, int halfBase) {
        // filled pink triangle with white outline
        for (int y = y0; y <= y1; y++) {
            double t = (double) (y - y0) / (y1 - y0);
            double hw = halfBase * (1 - t) + 0.4;
            for (int x = cx - halfBase; x <= cx + halfBase; x++) {
                double d = Math.abs(x - cx);
                if (d <= hw) {
                    boolean edge = d > hw - 1.15 || y == y0 || y == y1;
                    c.set(x, y, -1, edge ? WHITE : PINK);
                }
            }
        }
    }

    private static void square(BuildContext c, int x0, int y0, int x1, int y1) {
        c.fill(x0, y0, -1, x1, y1, -1, WHITE);
        c.fill(x0 + 1, y0 + 1, -1, x1 - 1, y1 - 1, -1, PINK);
    }

    // ------------------------------------------------------------------ control gallery

    private static void gallery(BuildContext c) {
        int fy = GALLERY_FLOOR + 1;
        // shell: back wall z=-7, side walls x=+-13, front wall z=-3, roof; interior carved
        c.fill(-13, fy, -7, 13, GALLERY_ROOF, -3, BLACK);
        c.clear(-12, fy, -6, 12, GALLERY_Y1, -4);
        // glass band in the front wall
        c.fill(-12, fy + 1, -3, 12, GALLERY_Y1, -3, "minecraft:gray_stained_glass");
        // frame: mullions every 6 blocks
        for (int x = -12; x <= 12; x += 6) {
            c.fill(x, fy + 1, -3, x, GALLERY_Y1, -3, BLACK);
        }
        // roof overhang with pink edge and lights
        c.fill(-14, GALLERY_ROOF, -8, 14, GALLERY_ROOF, -2, BLACK);
        c.fill(-14, GALLERY_ROOF, -2, 14, GALLERY_ROOF, -2, PINK);
        for (int x = -10; x <= 10; x += 4) {
            for (int z = -6; z <= -4; z += 2) {
                c.set(x, GALLERY_ROOF, z, "squidgame:panel_light_white");
            }
        }
        // monitors on the back wall
        for (int x = -10; x <= 10; x += 2) {
            c.set(x, fy + 1, -7, "squidgame:monitor[facing=south]");
            c.set(x, fy + 2, -7, "squidgame:monitor[facing=south]");
        }
        // front light strip under the roof and a white parapet line under the windows
        c.fill(-12, fy, -3, 12, fy, -3, WHITE);
    }

    /** Standing position of the spectator viewpoint (centre of the gallery). */
    public static void markers(BuildContext c) {
        c.marker("arena.spectator", 0.5, GALLERY_FLOOR + 1.0, -4.5, 0f);
    }
}
