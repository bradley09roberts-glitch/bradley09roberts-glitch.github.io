package com.squidgame.build.arena.hub.stairs;

/**
 * Building blocks of the maze: stair flights (1 to 3 lanes), landings, bridges, pillars and the automatic railing
 * pass. All coordinates are hub-local. A flight starts one cell beyond the cursor cell (cx, cz) that ends the previous
 * element; its first stair block sits at the height of the lower floor top, so every step is half a block high.
 * Every element reserves its own head-room so nothing can later block a walkway.
 */
final class Kit {
    private Kit() {
    }

    static final int HEAD = 6;
    static final int LANE = 5;
    static final String LANTERN = "minecraft:lantern[hanging=false]";

    /** Total width of a flight made of {@code lanes} lanes of width {@code lw} separated by 1-block stringers. */
    static int flightWidth(int lanes, int lw) {
        return lanes * lw + lanes + 1;
    }

    // ------------------------------------------------------------------ flight

    /**
     * A straight flight toward {@code d}. (cx, cz) is the last cell of the previous floor on the centre line, {@code H}
     * that floor's standing level. A positive {@code rise} climbs (stairs ascend toward d), a negative one descends
     * (stairs ascend toward the cursor). {@code depth}: thickness of solid below each tread (2 = thin), 100 = solid
     * wedge down to the floor.
     */
    static void flight(Ctx k, Dir d, int cx, int cz, int H, int rise, int lanes, int lw, Look lk) {
        int n = Math.abs(rise);
        boolean up = rise > 0;
        int W = flightWidth(lanes, lw);
        int half = (W - 1) / 2;
        Dir r = d.right();
        String facing = up ? d.facing : d.back().facing;
        int lampEvery = lw >= 5 ? 3 : 4;
        if (!k.probe && n >= 6) {
            k.flights.add(new int[]{d.ordinal(), cx, cz, H, rise, lanes, lw, lk.tread(), lk.side(), lk.accent(), k.head >= 6 ? 1 : 0});
        }
        for (int i = 0; i < n; i++) {
            int u = i + 1;
            int bx = cx + d.dx * u, bz = cz + d.dz * u;
            int y = up ? H + i : H - u;
            int yb = lk.depth() >= 100 ? 0 : Math.max(0, y - lk.depth());
            int ordinal = up ? i : n - 1 - i;      // counted from the low end
            for (int v = -half; v <= half; v++) {
                int x = bx + r.dx * v, z = bz + r.dz * v;
                int pos = v + half;
                boolean stringer = pos % (lw + 1) == 0;
                if (stringer) {
                    k.fill(x, yb, z, x, y, z, Pal.block(lk.side()), Occ.SOLID);
                    k.set(x, y + 1, z, ordinal % lampEvery == 1 ? Kit.LANTERN : Pal.ROD, Occ.SOLID);
                } else {
                    k.set(x, y, z, Pal.stair(lk.tread(), facing, false), Occ.SOLID);
                    if (yb <= y - 1) {
                        k.fill(x, yb, z, x, y - 1, z, Pal.block(lk.under()), Occ.SOLID);
                        // soffit light under the middle of each lane every fourth step (thin flights only)
                        if (!k.probe && lk.depth() < 100 && yb == y - lk.depth() && ordinal % 4 == 2 && (pos % (lw + 1)) == (lw + 1) / 2) {
                            k.set(x, yb, z, Pal.LIGHT_WHITE);
                        }
                    }
                    k.mark(x, y + 1, z, x, y + k.head, z, Occ.CLEAR);
                }
            }
        }
    }

    /**
     * Barrier across a flight's width at one cell (panes with glowing rods at the ends): closes the open end of stairs
     * that lead nowhere so nobody walks off. (x, z) is the cell on the centre line, {@code d} the heading of the flight.
     */
    static void cap(Ctx k, Dir d, int x, int z, int yFeet, int lanes, int lw) {
        int W = flightWidth(lanes, lw);
        int half = (W - 1) / 2;
        Dir r = d.right();
        for (int v = -half; v <= half; v++) {
            int px = x + r.dx * v, pz = z + r.dz * v;
            k.set(px, yFeet, pz, Pal.PANE, Occ.SOLID | Occ.RAIL);
            if (v == -half || v == half || v == 0) {
                k.set(px, yFeet + 1, pz, Pal.ROD, Occ.SOLID | Occ.RAIL);
            }
        }
    }

    // ------------------------------------------------------------------ landing

    static final int PAT_PLAIN = 0, PAT_CHECK = 1, PAT_FRAME = 2, PAT_STRIPE = 3;

    /** A platform: floor top at standing level H (floor blocks at H-1), {@code depth} thick. */
    static void landing(Ctx k, int x0, int z0, int x1, int z1, int H, Look lk, int pattern, boolean main) {
        int lx = Math.min(x0, x1), hx = Math.max(x0, x1), lz = Math.min(z0, z1), hz = Math.max(z0, z1);
        int depth = Math.max(2, Math.min(lk.depth(), 3));
        // underside
        k.fill(lx, H - depth, lz, hx, H - 2, hz, Pal.block(lk.under()), Occ.SOLID);
        // recessed soffit lights on a 4-block lattice (they light the space under the platform)
        if (!k.probe && hx - lx >= 4 && hz - lz >= 4) {
            for (int z = lz + 2; z <= hz - 2; z += 4) {
                for (int x = lx + 2; x <= hx - 2; x += 4) {
                    k.set(x, H - depth, z, Pal.LIGHT_WHITE);
                }
            }
        }
        // fascia ring one level down in the side colour
        k.fill(lx, H - 2, lz, hx, H - 2, lz, Pal.block(lk.side()));
        k.fill(lx, H - 2, hz, hx, H - 2, hz, Pal.block(lk.side()));
        k.fill(lx, H - 2, lz, lx, H - 2, hz, Pal.block(lk.side()));
        k.fill(hx, H - 2, lz, hx, H - 2, hz, Pal.block(lk.side()));
        // top surface
        if (k.probe) {
            k.fill(lx, H - 1, lz, hx, H - 1, hz, Pal.block(lk.tread()), Occ.SOLID);   // conflict test of the whole slab
        } else {
            for (int z = lz; z <= hz; z++) {
                for (int x = lx; x <= hx; x++) {
                    int col = surface(pattern, lk, x - lx, z - lz, hx - lx, hz - lz);
                    k.set(x, H - 1, z, Pal.block(col), Occ.SOLID);
                }
            }
        }
        if (!k.probe) {
            k.occ.mark(lx, H, lz, hx, H, hz, Occ.WALK);
            k.walkRects.add(new int[]{lx, lz, hx, hz, H});
            k.allLandings.add(new int[]{lx, lz, hx, hz, H});
            if (main) {
                k.landings.add(new int[]{lx, lz, hx, hz, H});
            }
        }
        k.mark(lx, H, lz, hx, H + k.head, hz, Occ.CLEAR);
    }

    private static int surface(int pattern, Look lk, int a, int b, int ma, int mb) {
        boolean edge = a == 0 || b == 0 || a == ma || b == mb;
        switch (pattern) {
            case PAT_CHECK -> {
                if (edge) {
                    return Pal.CREAM;
                }
                return (((a - 1) / 2 + (b - 1) / 2) & 1) == 0 ? lk.tread() : lk.accent();
            }
            case PAT_FRAME -> {
                if (edge) {
                    return Pal.CREAM;
                }
                boolean inner = a >= 2 && b >= 2 && a <= ma - 2 && b <= mb - 2;
                return inner ? lk.tread() : lk.accent();
            }
            case PAT_STRIPE -> {
                if (edge) {
                    return Pal.CREAM;
                }
                return ((a / 2) & 1) == 0 ? lk.tread() : lk.accent();
            }
            default -> {
                return edge ? Pal.CREAM : lk.tread();
            }
        }
    }

    // ------------------------------------------------------------------ pillars

    /** Pillar (size x size, cells starting at px, pz) from just under {@code yTop} down to the first solid or the floor. */
    static void pillar(Ctx k, int px, int pz, int size, int yTop, int col, int bandCol) {
        for (int y = yTop - 1; y >= 0; y--) {
            boolean hit = false;
            for (int dz = 0; dz < size && !hit; dz++) {
                for (int dx = 0; dx < size; dx++) {
                    if (k.solid(px + dx, y, pz + dz) || k.occ.has(px + dx, y, pz + dz, Occ.CLEAR | Occ.KEEP)) {
                        hit = true;
                        break;
                    }
                }
            }
            if (hit) {
                return;
            }
            boolean band = y % 8 == 7 || y % 8 == 0;
            String s = Pal.block(band ? bandCol : col);
            k.fill(px, y, pz, px + size - 1, y, pz + size - 1, s, Occ.SOLID);
        }
    }

    /** Four corner pillars under a rectangular platform whose floor top is at H. */
    static void cornerPillars(Ctx k, int x0, int z0, int x1, int z1, int H, int size, int col, int bandCol) {
        int lx = Math.min(x0, x1), hx = Math.max(x0, x1), lz = Math.min(z0, z1), hz = Math.max(z0, z1);
        int top = H - 3;
        pillar(k, lx + 1, lz + 1, size, top, col, bandCol);
        pillar(k, hx - size, lz + 1, size, top, col, bandCol);
        pillar(k, lx + 1, hz - size, size, top, col, bandCol);
        pillar(k, hx - size, hz - size, size, top, col, bandCol);
    }

    // ------------------------------------------------------------------ rails

    private static boolean openSide(Ctx k, int x, int y, int z) {
        if (k.solid(x, y, z)) {
            return false;
        }
        return !(k.solid(x, y - 1, z) || k.solid(x, y - 2, z));
    }

    /**
     * Railings on every open rim cell of the recorded walking floors: a white pane with a glowing rod on top every third
     * cell and a lantern post on corners. A rim cell is a floor cell with a 4-neighbour that is free and drops away.
     */
    static void rails(Ctx k, java.util.List<int[]> rects) {
        for (int[] rc : rects) {
            int lx = rc[0], lz = rc[1], hx = rc[2], hz = rc[3], H = rc[4];
            for (int z = lz; z <= hz; z++) {
                for (int x = lx; x <= hx; x++) {
                    if (x != lx && x != hx && z != lz && z != hz) {
                        continue; // interior cells have only floor neighbours
                    }
                    boolean n = openSide(k, x, H, z - 1), s = openSide(k, x, H, z + 1);
                    boolean w = openSide(k, x - 1, H, z), e = openSide(k, x + 1, H, z);
                    if (!(n || s || w || e) || k.solid(x, H, z)) {
                        continue;
                    }
                    boolean corner = (n || s) && (w || e);
                    if (corner) {
                        k.set(x, H, z, Pal.block(Pal.CREAM), Occ.RAIL | Occ.SOLID);
                        k.set(x, H + 1, z, Pal.block(Pal.CREAM), Occ.RAIL | Occ.SOLID);
                        k.set(x, H + 2, z, LANTERN, Occ.RAIL | Occ.SOLID);
                    } else {
                        k.set(x, H, z, Pal.PANE, Occ.RAIL | Occ.SOLID);
                        if (Math.floorMod(x + z, 3) == 0) {
                            k.set(x, H + 1, z, Pal.ROD, Occ.RAIL | Occ.SOLID);
                        }
                    }
                }
            }
        }
    }
}
