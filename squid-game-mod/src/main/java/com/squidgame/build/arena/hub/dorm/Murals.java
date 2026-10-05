package com.squidgame.build.arena.hub.dorm;

import com.squidgame.build.BuildContext;

/**
 * Neon outlines of the three symbols (circle, triangle, square) in glowing pink panel blocks, set flush into the wall
 * plane: a small trio above the exit door (the end of the long view from the entrance) and three big ones on the west
 * wall above the guards' gallery.
 */
final class Murals {
    private Murals() {
    }

    private static final String NEON = Pal.PANEL_PINK;
    private static final String PLATE = Pal.BLACK;

    static void build(BuildContext c) {
        // north wall: frame in its own frame (inner layer z = -33)
        c.at(0, 0, 0, 0, () -> trio(c, -33, 0, 17, 3.0, 7, 10));
        // west wall: the north frame turned: rot 3 maps local (u, -41) to world (-41, -u)
        c.at(0, 0, 0, 3, () -> trio(c, -41, 0, 23, 4.4, 11, 17));
    }

    /** Three symbols side by side, centred on x = cx, on a black plate (half width hw) with a pink border. */
    private static void trio(BuildContext c, int z, int cx, int cy, double r, int step, int hw) {
        int hh = (int) Math.ceil(r) + 2;
        c.fill(cx - hw, cy - hh, z, cx + hw, cy + hh, z, PLATE);
        c.fill(cx - hw, cy - hh, z, cx + hw, cy - hh, z, Pal.PINK);
        c.fill(cx - hw, cy + hh, z, cx + hw, cy + hh, z, Pal.PINK);
        c.fill(cx - hw, cy - hh, z, cx - hw, cy + hh, z, Pal.PINK);
        c.fill(cx + hw, cy - hh, z, cx + hw, cy + hh, z, Pal.PINK);
        circle(c, cx - step, cy, z, r);
        triangle(c, cx, cy, z, r);
        square(c, cx + step, cy, z, r);
    }

    private static void circle(BuildContext c, int cx, int cy, int z, double r) {
        int ir = (int) Math.ceil(r) + 1;
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dy = -ir; dy <= ir; dy++) {
                double d = Math.hypot(dx, dy);
                if (d <= r + 0.5 && d > r - 0.75) {
                    c.set(cx + dx, cy + dy, z, NEON);
                }
            }
        }
    }

    private static void square(BuildContext c, int cx, int cy, int z, double r) {
        int h = (int) Math.round(r * 0.9);
        c.fill(cx - h, cy - h, z, cx + h, cy - h, z, NEON);
        c.fill(cx - h, cy + h, z, cx + h, cy + h, z, NEON);
        c.fill(cx - h, cy - h, z, cx - h, cy + h, z, NEON);
        c.fill(cx + h, cy - h, z, cx + h, cy + h, z, NEON);
    }

    private static void triangle(BuildContext c, int cx, int cy, int z, double r) {
        int h = (int) Math.round(r);
        int top = cy + h, bottom = cy - h;
        int half = (int) Math.round(r * 1.05);
        c.line(cx, top, z, cx - half, bottom, z, NEON);
        c.line(cx, top, z, cx + half, bottom, z, NEON);
        c.fill(cx - half, bottom, z, cx + half, bottom, z, NEON);
    }
}
