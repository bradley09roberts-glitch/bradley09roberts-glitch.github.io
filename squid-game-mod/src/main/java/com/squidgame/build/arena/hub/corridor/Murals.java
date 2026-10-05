package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;

/**
 * Big painted circle / triangle / square symbols made of blocks, drawn on any axis-aligned wall plane.
 * kind 0 = circle, 1 = triangle, 2 = square.
 */
final class Murals {
    private Murals() {
    }

    /** True if the point (du, dv) (block-centre offsets from the symbol centre, v up) lies on the symbol outline. */
    static boolean onSymbol(int kind, double du, double dv, double r, double thick) {
        switch (kind) {
            case 0: {
                double d = Math.sqrt(du * du + dv * dv);
                return Math.abs(d - r) <= thick / 2;
            }
            case 2: {
                double m = Math.max(Math.abs(du), Math.abs(dv));
                return Math.abs(m - r) <= thick / 2 && Math.abs(du) <= r + thick / 2 && Math.abs(dv) <= r + thick / 2;
            }
            default: {
                // equilateral-ish triangle: apex (0, r), base corners (+-r*0.95, -r*0.8)
                double ax = 0, ay = r, bx = -r * 0.98, by = -r * 0.8, cx = r * 0.98, cy = -r * 0.8;
                double d = Math.min(segDist(du, dv, ax, ay, bx, by), Math.min(segDist(du, dv, bx, by, cx, cy), segDist(du, dv, cx, cy, ax, ay)));
                return d <= thick / 2;
            }
        }
    }

    private static double segDist(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        double qx = ax + t * dx, qy = ay + t * dy;
        return Math.hypot(px - qx, py - qy);
    }

    /**
     * Paints a symbol on a wall plane.
     *
     * @param planeZ  true: the plane is z = fixed and spans x; false: plane x = fixed spans z
     * @param fixed   the plane coordinate
     * @param a0,a1   inclusive range along the plane
     * @param y0,y1   inclusive height range
     * @param centreA centre along the plane (block-centre coordinate, e.g. -47.5 for the cell range -51..-45... use cell+0.5)
     * @param centreY centre height (block-centre coordinate)
     * @param r       radius of the symbol in blocks
     */
    static void paint(BuildContext c, int kind, boolean planeZ, int fixed, int a0, int a1, int y0, int y1,
                      double centreA, double centreY, double r, String fg, String bg) {
        for (int a = a0; a <= a1; a++) {
            for (int y = y0; y <= y1; y++) {
                double du = (a + 0.5) - centreA;
                double dv = (y + 0.5) - centreY;
                String b = onSymbol(kind, du, dv, r, 1.15) ? fg : bg;
                if (b == null) {
                    continue;
                }
                if (planeZ) {
                    c.set(a, y, fixed, b);
                } else {
                    c.set(fixed, y, a, b);
                }
            }
        }
    }
}
