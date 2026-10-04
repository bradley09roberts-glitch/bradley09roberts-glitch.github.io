package com.squidgame.build.arena.redlight;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.redlight.Layout.*;

/**
 * The transition from the waiting room to the playground: a covered gate hall in front of the waiting-room doorway
 * (plane z = -8), a monumental lintel whose frieze carries the three symbols, and a glazed control gallery on top whose
 * floor is the spectator viewpoint. Floor "runway lights" and pylon light strips keep the covered part brightly lit.
 *
 * <pre>
 *   x[-15,15] z[-7,-1]    hall: floor y=0, free height 13 (y 1..13), 25 wide between the pylons
 *   y[14,24]              the lintel mass (solid), symbol frieze on its south face (z=-1), y 15..23
 *   y[25,29]              control gallery (interior z -6..-4), glass front at z=-3
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

    /** Half width of the whole structure (outer face of the pylons). */
    private static final int HW = 15;
    /** Inner face of the pylons (the hall is 2*PX-1 wide). */
    private static final int PX = HW - 2;

    public static final int MASS_Y0 = 14;
    public static final int MASS_Y1 = 24;
    public static final int GALLERY_FLOOR = MASS_Y1;      // top block of the mass
    public static final int GALLERY_Y1 = GALLERY_FLOOR + 4;   // top interior row
    public static final int GALLERY_ROOF = GALLERY_Y1 + 1;

    public static void build(BuildContext c) {
        floor(c);
        pylons(c);
        mass(c);
        symbols(c);
        gallery(c);
        c.text(0.5, GALLERY_ROOF + 3.0, -3.5, "RED LIGHT, GREEN LIGHT", "#FFD84A", 8f, 0f, false);
    }

    // ------------------------------------------------------------------ hall floor and lighting

    private static void floor(BuildContext c) {
        c.fill(-HW, 0, -7, HW, 0, -1, "squidgame:tile_black");
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
        c.set(-13, 0, -4, LANTERN);
        c.set(13, 0, -4, LANTERN);
        // top up with a covering lattice so no cell of the hall drops below level 11
        Lighting.floorGrid(c, -PX + 1, -7, PX - 1, -1, 3, LANTERN, (x, z) -> true,
                (x, z) -> c.get(x, 0, z) != null && !c.isSolid(x, 1, z), LANTERN);
    }

    private static void pylons(BuildContext c) {
        for (int side = -1; side <= 1; side += 2) {
            for (int zb : new int[]{-7, -3}) {
                int x1 = side > 0 ? PX : -HW;
                int x2 = side > 0 ? HW : -PX;
                c.fill(x1, 1, zb, x2, MASS_Y0 - 1, zb + 2, BLACK);
                // pink inlay on the inward-facing side, grey plinth and white cap
                int inner = side > 0 ? PX : -PX;
                c.fill(inner, 2, zb + 1, inner, MASS_Y0 - 2, zb + 1, PINK);
                c.fill(x1, 1, zb, x2, 1, zb + 2, GRAY);
                c.fill(x1, MASS_Y0 - 1, zb, x2, MASS_Y0 - 1, zb + 2, WHITE);
            }
            // vertical light strips: front face of the front pylons and the inner faces
            int fx = side > 0 ? PX + 1 : -(PX + 1);
            for (int y = 3; y <= MASS_Y0 - 3; y += 2) {
                c.set(fx, y, -1, LANTERN);
            }
            int ix = side > 0 ? PX : -PX;
            for (int y = 4; y <= MASS_Y0 - 3; y += 3) {
                c.set(ix, y, -2, LANTERN);
                c.set(ix, y, -6, LANTERN);
            }
        }
    }

    private static void mass(BuildContext c) {
        c.fill(-HW, MASS_Y0, -7, HW, MASS_Y1, -1, BLACK);
        // underside trim and cornice
        c.fill(-HW, MASS_Y0, -7, HW, MASS_Y0, -1, GRAY);
        c.fill(-HW, MASS_Y1, -7, HW, MASS_Y1, -1, "squidgame:tile_black");
        c.fill(-HW, MASS_Y1 - 1, -1, HW, MASS_Y1 - 1, -1, WHITE);
        c.fill(-HW, MASS_Y0 + 1, -1, HW, MASS_Y0 + 1, -1, PINK);
        // embedded ceiling lights over the hall (underside)
        for (int x = -9; x <= 9; x += 6) {
            for (int z = -6; z <= -2; z += 2) {
                c.set(x, MASS_Y0, z, "squidgame:panel_light_warm");
            }
        }
    }

    // ------------------------------------------------------------------ symbol frieze on the lintel face (z = -1)

    /** Rows of the frieze: y 15..23 (9 tall). The three 9x9 symbols are white outlines on black, like the guards' masks. */
    private static void symbols(BuildContext c) {
        final int y0 = MASS_Y0 + 1, y1 = MASS_Y1 - 1;
        // circle: columns -14..-6
        double cx = -9.5, cy = (y0 + y1 + 1) / 2.0;
        for (int x = -15; x <= -5; x++) {
            for (int y = y0; y <= y1; y++) {
                double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                if (d <= 4.55 && d > 3.4) {
                    c.set(x, y, -1, WHITE);
                }
            }
        }
        // triangle: columns -4..4, base on the bottom row, apex on the top row
        for (int y = y0; y <= y1; y++) {
            double t = (double) (y - y0) / (y1 - y0);
            double hw = 4.2 * (1 - t) + 0.5;
            for (int x = -5; x <= 5; x++) {
                double d = Math.abs(x + 0.5 - 0.5);
                if (d <= hw && (d > hw - 1.35 || y <= y0 + 1 || y == y1)) {
                    c.set(x, y, -1, WHITE);
                }
            }
        }
        // square: columns 6..14
        c.fill(6, y0, -1, 14, y1, -1, WHITE);
        c.fill(7, y0 + 1, -1, 13, y1 - 1, -1, BLACK);
        // a thin pink line under the frieze and above it
        c.fill(-HW + 1, y0 - 1, -1, HW - 1, y0 - 1, -1, PINK);
    }

    // ------------------------------------------------------------------ control gallery

    private static void gallery(BuildContext c) {
        int fy = GALLERY_FLOOR + 1;
        int gw = HW;
        // shell: back wall z=-7, side walls x=+-HW, front wall z=-3, roof; interior carved
        c.fill(-gw, fy, -7, gw, GALLERY_ROOF, -3, BLACK);
        c.clear(-gw + 1, fy, -6, gw - 1, GALLERY_Y1, -4);
        // glass band in the front wall
        c.fill(-gw + 1, fy + 1, -3, gw - 1, GALLERY_Y1, -3, "minecraft:gray_stained_glass");
        // frame: mullions every 7 blocks
        for (int x = -14; x <= 14; x += 7) {
            c.fill(x, fy + 1, -3, x, GALLERY_Y1, -3, BLACK);
        }
        // roof overhang with pink edge and lights
        c.fill(-gw - 1, GALLERY_ROOF, -8, gw + 1, GALLERY_ROOF, -2, BLACK);
        c.fill(-gw - 1, GALLERY_ROOF, -2, gw + 1, GALLERY_ROOF, -2, PINK);
        for (int x = -12; x <= 12; x += 4) {
            for (int z = -6; z <= -4; z += 2) {
                c.set(x, GALLERY_ROOF, z, "squidgame:panel_light_white");
            }
        }
        // monitors on the back wall
        for (int x = -12; x <= 12; x += 2) {
            c.set(x, fy + 1, -7, "squidgame:monitor[facing=south]");
            c.set(x, fy + 2, -7, "squidgame:monitor[facing=south]");
        }
        // white parapet line under the windows
        c.fill(-gw + 1, fy, -3, gw - 1, fy, -3, WHITE);
    }

    /** Spectator viewpoint: standing on the gallery floor, facing the field. */
    public static void markers(BuildContext c) {
        c.marker("arena.spectator", 0.5, GALLERY_FLOOR + 1.0, -4.5, 0f);
    }
}
