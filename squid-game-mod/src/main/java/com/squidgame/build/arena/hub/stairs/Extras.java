package com.squidgame.build.arena.hub.stairs;

import com.squidgame.core.util.Rng;

/**
 * Further pieces of the tangle: stand-alone flights (stairs that start and end nowhere), staircases hanging upside
 * down from the ceiling, arch portals over flights, and rows of free-standing arches at floor level.
 */
final class Extras {
    private Extras() {
    }

    static int solo, hanging, portals, arcades;

    // ------------------------------------------------------------------ solo flights

    private static final int[] SOLO_RISES = {9, 12, 12, 15, 18, 24, 30};

    static void soloFlights(Ctx k, int attempts) {
        Rng rng = k.rng;
        int saved = k.head;
        k.head = 5;
        for (int n = 0; n < attempts; n++) {
            Dir d = Dir.values()[rng.nextInt(4)];
            int x = rng.rangeInt(-43, 43), z = rng.rangeInt(-167, -86);
            int H = 3 * rng.rangeInt(0, 21);
            int rise = SOLO_RISES[rng.nextInt(SOLO_RISES.length)] * (rng.chance(0.5) ? 1 : -1);
            if (H + rise < 0 || H + rise > 63) {
                rise = -rise;
            }
            if (H + rise < 0 || H + rise > 63) {
                continue;
            }
            int lanes = rng.chance(0.5) ? 1 : (rng.chance(0.4) ? 2 : 3);
            int lw = rng.chance(0.85) ? 5 : 3;
            boolean wedge = rise > 0 && H <= 2 && rise <= 21 && rng.chance(0.5);
            Look lk = Look.of(rng.nextInt(12), wedge ? 100 : 2);
            Route r = new Route(k, d, x, z, H, false);
            final int fr = rise, fl = lanes, fw = lw;
            final Look flk = lk;
            k.probe = true;
            k.conflict = false;
            try {
                r.copy().flight(fr, fl, fw, flk);
            } finally {
                k.probe = false;
            }
            boolean ok = !k.conflict;
            k.conflict = false;
            if (ok) {
                r.flight(rise, lanes, lw, lk);
                solo++;
                if (rise > 0) {
                    Kit.cap(k, d, r.cx + d.dx, r.cz + d.dz, r.H, lanes, lw);              // top end
                } else {
                    Kit.cap(k, d, x, z, H, lanes, lw);                                      // the high end is where it started
                }
            }
        }
        k.head = saved;
    }

    // ------------------------------------------------------------------ upside-down stairs under the ceiling

    static void ceilingStairs(Ctx k, int attempts) {
        Rng rng = k.rng;
        for (int n = 0; n < attempts; n++) {
            Dir d = Dir.values()[rng.nextInt(4)];
            int lanes = rng.chance(0.55) ? 1 : (rng.chance(0.6) ? 2 : 3);
            int W = Kit.flightWidth(lanes, 5);
            int half = (W - 1) / 2;
            int len = rng.rangeInt(8, 20);
            int x = rng.rangeInt(-40, 40), z = rng.rangeInt(-165, -88);
            Dir r = d.right();
            // footprint corners
            int ex = x + d.dx * len, ez = z + d.dz * len;
            int x0 = Math.min(Math.min(x + r.dx * half, x - r.dx * half), Math.min(ex + r.dx * half, ex - r.dx * half));
            int x1 = Math.max(Math.max(x + r.dx * half, x - r.dx * half), Math.max(ex + r.dx * half, ex - r.dx * half));
            int z0 = Math.min(Math.min(z + r.dz * half, z - r.dz * half), Math.min(ez + r.dz * half, ez - r.dz * half));
            int z1 = Math.max(Math.max(z + r.dz * half, z - r.dz * half), Math.max(ez + r.dz * half, ez - r.dz * half));
            int low = 71 - len;
            k.probe = true;
            k.conflict = false;
            try {
                k.fill(x0, low, z0, x1, 71, z1, Pal.AIR, Occ.DECOR);
            } finally {
                k.probe = false;
            }
            boolean ok = !k.conflict;
            k.conflict = false;
            if (!ok) {
                continue;
            }
            Look lk = Look.of(rng.nextInt(12), 2);
            for (int i = 0; i < len; i++) {
                int bx = x + d.dx * i, bz = z + d.dz * i;
                int yb = 70 - i;
                for (int v = -half; v <= half; v++) {
                    int px = bx + r.dx * v, pz = bz + r.dz * v;
                    boolean stringer = (v + half) % 6 == 0;
                    k.fill(px, yb + 1, pz, px, 71, pz, Pal.block(stringer ? lk.side() : lk.under()), Occ.DECOR);
                    if (stringer) {
                        k.set(px, yb, pz, Pal.block(lk.side()), Occ.DECOR);
                    } else {
                        k.set(px, yb, pz, Pal.stair(lk.tread(), d.facing, true), Occ.DECOR);
                    }
                }
            }
            hanging++;
        }
    }

    // ------------------------------------------------------------------ arch portals over flights

    /** A round arch frame across one lane (posts on the stringers) at forward cell u of a flight. */
    static void portals(Ctx k) {
        java.util.List<int[]> list = new java.util.ArrayList<>(k.flights);
        for (int[] f : list) {
            Dir d = Dir.values()[f[0]];
            int cx = f[1], cz = f[2], H = f[3], rise = f[4], lanes = f[5], lw = f[6];
            boolean up = rise > 0;
            int n = Math.abs(rise);
            Dir r = d.right();
            int W = Kit.flightWidth(lanes, lw);
            int half = (W - 1) / 2;
            int col = f[9];
            int period = 6;
            for (int u = 3; u <= n; u += period) {
                int y = up ? H + u - 1 : H - u;      // tread block of this step
                // each lane gets an arch spanning stringer to stringer
                boolean allOk = true;
                int saved = k.head;
                k.probe = true;
                k.conflict = false;
                try {
                    for (int lane = 0; lane < lanes; lane++) {
                        archFrame(k, d, r, cx + d.dx * u, cz + d.dz * u, -half + lane * (lw + 1), lw + 1, y + 1, col, f[8], f[10] == 1 ? 7 : 6);
                    }
                } finally {
                    k.probe = false;
                }
                allOk = !k.conflict;
                k.conflict = false;
                k.head = saved;
                if (!allOk) {
                    continue;
                }
                for (int lane = 0; lane < lanes; lane++) {
                    archFrame(k, d, r, cx + d.dx * u, cz + d.dz * u, -half + lane * (lw + 1), lw + 1, y + 1, col, f[8], f[10] == 1 ? 7 : 6);
                }
                portals++;
            }
        }
    }

    /**
     * Arch across lateral positions v0 .. v0+span (stringer to stringer) standing on the stringer tops at height y
     * (the cell above the stringer): posts to the spring line, a semicircle above, a hanging lantern in the middle.
     */
    private static void archFrame(Ctx k, Dir d, Dir r, int bx, int bz, int v0, int span, int y, int col, int postCol, int headAbove) {
        double rad = span / 2.0;
        int spring = y + headAbove;      // posts rise to here (the rod cell is y itself); the arc starts above the head-room
        for (int v = 0; v <= span; v++) {
            int px = bx + r.dx * (v0 + v), pz = bz + r.dz * (v0 + v);
            double dx = v - span / 2.0;
            // posts
            if (v == 0 || v == span) {
                k.fill(px, y + 1, pz, px, spring, pz, Pal.block(Pal.CREAM), Occ.DECOR);
            }
            // arc
            for (int yy = spring; yy <= spring + (int) Math.ceil(rad); yy++) {
                double dy = yy - spring + 0.5;
                double dist = Math.hypot(dx, dy);
                if (dist <= rad + 0.5 && dist > rad - 0.75) {
                    k.set(px, yy, pz, Pal.block(col), Occ.DECOR);
                }
            }
        }
        // hanging lantern under the crown
        int mx = bx + r.dx * (v0 + span / 2), mz = bz + r.dz * (v0 + span / 2);
        int crown = spring + (int) Math.ceil(rad) - 1;
        k.set(mx, crown, mz, "minecraft:lantern[hanging=true]", Occ.DECOR);
        k.set(mx, crown + 1, mz, Pal.block(col), Occ.DECOR);
    }

    // ------------------------------------------------------------------ rows of arches on the floor

    static void arcades(Ctx k, int attempts) {
        Rng rng = k.rng;
        for (int n = 0; n < attempts; n++) {
            boolean alongX = rng.nextBoolean();
            int count = rng.rangeInt(3, 6);
            int pitch = 9;
            int x = rng.rangeInt(-42, 40), z = rng.rangeInt(-168, -86);
            int x1 = alongX ? x + count * pitch : x + 2;
            int z1 = alongX ? z + 2 : z + count * pitch;
            int top = 13;
            k.probe = true;
            k.conflict = false;
            try {
                k.fill(x, 0, z, x1, top, z1, Pal.AIR, Occ.DECOR);
            } finally {
                k.probe = false;
            }
            boolean ok = !k.conflict;
            k.conflict = false;
            if (!ok) {
                continue;
            }
            int wall = Pal.wheel(rng.nextInt(6));
            int ring = Pal.wheel(rng.nextInt(6));
            for (int i = 0; i < count; i++) {
                int ax = alongX ? x + i * pitch : x;
                int az = alongX ? z : z + i * pitch;
                arch(k, alongX, ax, az, wall, ring);
            }
            // closing pier
            int px = alongX ? x + count * pitch : x, pz = alongX ? z : z + count * pitch;
            k.fill(px, 0, pz, px + 2, 11, pz + 2, Pal.block(wall), Occ.DECOR);
            arcades++;
        }
    }

    /** One free-standing arch: two 3x3 piers 6 apart (clear opening 5 wide), round head, flat top at y=11. */
    private static void arch(Ctx k, boolean alongX, int ax, int az, int wall, int ring) {
        // pier cells (3 wide in the arch direction, 3 deep)
        int w = 9;
        for (int a = 0; a < w; a++) {
            for (int y = 0; y <= 11; y++) {
                boolean opening = false;
                int da = a - 4;
                if (a >= 2 && a <= 6) {
                    // opening 5 wide: straight up to 5, then a round head of radius 2.5 centred at y=6
                    if (y <= 5) {
                        opening = true;
                    } else {
                        double dy = y - 6 + 0.5;
                        opening = da * da + dy * dy <= 2.5 * 2.5;
                    }
                }
                if (opening) {
                    continue;
                }
                boolean band = y == 11 || y == 10 || y == 1;
                int px = alongX ? ax + a : ax, pz = alongX ? az : az + a;
                int ex = alongX ? px : px + 2, ez = alongX ? pz + 2 : pz;
                k.fill(px, y, pz, ex, y, ez, Pal.block(band ? Pal.CREAM : wall), Occ.DECOR);
            }
        }
        // light in the crown of the opening
        int cxx = alongX ? ax + 4 : ax + 1, czz = alongX ? az + 1 : az + 4;
        k.set(cxx, 8, czz, Pal.LIGHT_WARM, Occ.DECOR);
    }
}
