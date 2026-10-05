package com.squidgame.build.arena.hub.stairs;

/**
 * Turtle for walkways. The cursor is the last floor cell (on the centre line) of the previous element; every method
 * builds the next element and returns this. The main route additionally records its centre line for the waypoints.
 * {@link #copy()} + probe mode allow dry runs.
 */
final class Route {
    enum Exit {STRAIGHT, LEFT, RIGHT}

    final Ctx k;
    Dir d;
    int cx, cz, H;
    final boolean main;

    Route(Ctx k, Dir d, int cx, int cz, int H, boolean main) {
        this.k = k;
        this.d = d;
        this.cx = cx;
        this.cz = cz;
        this.H = H;
        this.main = main;
        if (main && !k.probe) {
            k.line.add(new int[]{cx, H, cz});
        }
    }

    Route copy() {
        return new Route(k, d, cx, cz, H, false);
    }

    private void record(int x, int y, int z) {
        if (main && !k.probe) {
            k.line.add(new int[]{x, y, z});
        }
    }

    /** Flat floor of len rows (along the heading) x w columns, entered on the centre line; leaves toward {@code exit}. */
    Route landing(int len, int w, Exit exit, Look lk, int pattern) {
        int half = (w - 1) / 2;
        Dir r = d.right();
        int ax = cx + d.dx, az = cz + d.dz;                  // first row centre
        int bx = cx + d.dx * len, bz = cz + d.dz * len;      // last row centre
        int[] xs = {ax + r.dx * -half, ax + r.dx * half, bx + r.dx * -half, bx + r.dx * half};
        int[] zs = {az + r.dz * -half, az + r.dz * half, bz + r.dz * -half, bz + r.dz * half};
        int x0 = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
        int x1 = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
        int z0 = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3]));
        int z1 = Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3]));
        Kit.landing(k, x0, z0, x1, z1, H, lk, pattern, main);
        int mid = (len + 1) / 2;
        switch (exit) {
            case STRAIGHT -> {
                for (int u = 1; u <= len; u++) {
                    record(cx + d.dx * u, H, cz + d.dz * u);
                }
                cx = bx;
                cz = bz;
            }
            case LEFT, RIGHT -> {
                int s = exit == Exit.RIGHT ? 1 : -1;
                for (int u = 1; u <= mid; u++) {
                    record(cx + d.dx * u, H, cz + d.dz * u);
                }
                int mx = cx + d.dx * mid, mz = cz + d.dz * mid;
                for (int v = 1; v <= half; v++) {
                    record(mx + r.dx * s * v, H, mz + r.dz * s * v);
                }
                cx = mx + r.dx * s * half;
                cz = mz + r.dz * s * half;
                d = exit == Exit.RIGHT ? d.right() : d.left();
            }
        }
        return this;
    }

    /** A straight flight of |rise| cells; positive climbs, negative descends. */
    Route flight(int rise, int lanes, int lw, Look lk) {
        Kit.flight(k, d, cx, cz, H, rise, lanes, lw, lk);
        int n = Math.abs(rise);
        if (rise > 0) {
            for (int i = 1; i <= n; i++) {
                record(cx + d.dx * i, H + i, cz + d.dz * i);
            }
        }
        cx += d.dx * n;
        cz += d.dz * n;
        H += rise;
        return this;
    }

    Route flight(int rise, int lanes, Look lk) {
        return flight(rise, lanes, Kit.LANE, lk);
    }

    /** Flat bridge of len cells and width w (odd). */
    Route bridge(int len, int w, Look lk) {
        return landing(len, w, Exit.STRAIGHT, lk, Kit.PAT_STRIPE);
    }
}
