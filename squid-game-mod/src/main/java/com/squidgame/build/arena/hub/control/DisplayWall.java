package com.squidgame.build.arena.hub.control;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.hub.control.Geo.*;

/**
 * The floor-to-ceiling display wall on the east wall (x = 84, monitors face west): a disc of monitors inside a huge
 * glowing pink circle (the halo behind the throne), clusters of monitors with black frames and pink light slits on
 * both sides, and smaller clusters on the south wall above the server racks. Also emits the control.monitor markers.
 */
final class DisplayWall {
    private DisplayWall() {
    }

    /** Centre of the halo (z, y) and its outer radius. */
    static final double HALO_Z = 0, HALO_Y = 21.5, HALO_R = 8.6;

    static void build(BuildContext c) {
        for (int z = Z0 + 1; z <= Z1 - 1; z++) {
            for (int y = Y0; y <= Y1; y++) {
                double dz = z, dy = y - HALO_Y + 0.0;
                double r = Math.hypot(dz, dy);
                String b;
                if (Math.abs(r - HALO_R) <= 0.62) {
                    b = Pal.LIGHT_PINK;
                } else if (isFrame(z, y, r)) {
                    b = (y == 18 || y == 26) && z % 2 != 0 ? Pal.LIGHT_PINK : Pal.PBS;
                } else {
                    b = Pal.monitor("west");
                }
                c.set(X1, y, z, b);
            }
        }
        // stepped black top and bottom frames
        for (int z = Z0 + 1; z <= Z1 - 1; z++) {
            c.set(X1, Y1, z, Pal.PBS);
        }
        // south wall clusters (above the racks): three groups facing north
        for (int g = 0; g < 3; g++) {
            int x0 = 54 + g * 10;
            for (int x = x0; x <= x0 + 7; x++) {
                for (int y = 22; y <= 29; y++) {
                    boolean edge = x == x0 || x == x0 + 7 || y == 22 || y == 29;
                    c.set(x, y, Z1, edge ? Pal.PBS : Pal.monitor("north"));
                }
            }
            c.set(x0 + 3, 22, Z1, Pal.LIGHT_PINK);
            c.set(x0 + 4, 22, Z1, Pal.LIGHT_PINK);
        }
        // north wall clusters above the mezzanine office, facing south
        for (int g = 0; g < 2; g++) {
            int x0 = 58 + g * 9;
            for (int x = x0; x <= x0 + 7; x++) {
                for (int y = 27; y <= 31; y++) {
                    boolean edge = x == x0 || x == x0 + 7 || y == 27 || y == 31;
                    c.set(x, y, Z0, edge ? Pal.PBS : Pal.monitor("south"));
                }
            }
            c.set(x0 + 3, 27, Z0, Pal.LIGHT_PINK);
            c.set(x0 + 4, 27, Z0, Pal.LIGHT_PINK);
            c.marker("control.monitor", 61.5 + g * 9, 29.0, -17.5, 0f, "cluster=north" + g);
        }
        // markers: one per cluster, placed in front of the wall (not standing positions)
        c.marker("control.monitor", 83.5, 21.5, 0.5, 90f, "cluster=halo");
        c.marker("control.monitor", 83.5, 20.0, -13.0, 90f, "cluster=north");
        c.marker("control.monitor", 83.5, 20.0, 14.0, 90f, "cluster=south");
        for (int g = 0; g < 3; g++) {
            c.marker("control.monitor", 58.0 + g * 10, 26.0, 17.5, 180f, "cluster=rack" + g);
        }
    }

    /** Black frame lines and separators between the monitor clusters outside the halo. */
    private static boolean isFrame(int z, int y, double haloR) {
        if (haloR < HALO_R + 1.4 && haloR > HALO_R - 1.5) {
            return false;
        }
        if (Math.abs(z) == 10 && haloR > HALO_R) {
            return true;
        }
        if ((y == 18 || y == 26) && Math.abs(z) >= 10) {
            return true;
        }
        return false;
    }
}
