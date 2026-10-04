package com.squidgame.build.arena.bridge;

import com.squidgame.build.BuildContext;

/**
 * The hall shell: solid foundation, pit floor, 3 thick walls and roof, then the interior wall faces (plinth with
 * glowing pink strip, ribbed pit walls, hall walls with ribs and corner pilasters) drawn in a canonical wall frame
 * (u along the wall, local z = depth into the hall) and placed four times with rotations.
 */
final class Hall {
    private Hall() {
    }

    /** Catwalk levels in the pit (floor layer y); ribs are interrupted around them. */
    static final int PIT_CAT_A = -10;
    static final int PIT_CAT_B = 14;

    static void build(BuildContext c) {
        shell(c);
        pitFloor(c);
        walls(c);
    }

    // ------------------------------------------------------------------ solid shell

    private static void shell(BuildContext c) {
        // foundation + pit floor mass (deepslate)
        c.fill(Geo.WX0, Geo.FOUND, Geo.BZ, Geo.WX1, Geo.PIT - 1, Geo.FZ, Pal.DS);
        // the 3 thick walls up to the roof, solid
        c.fill(Geo.WX0, Geo.PIT, Geo.BZ, Geo.X0 - 1, Geo.ROOF_TOP, Geo.FZ, Pal.GRY);
        c.fill(Geo.X1 + 1, Geo.PIT, Geo.BZ, Geo.WX1, Geo.ROOF_TOP, Geo.FZ, Pal.GRY);
        c.fill(Geo.X0, Geo.PIT, Geo.BZ, Geo.X1, Geo.ROOF_TOP, Geo.Z0 - 1, Pal.GRY);
        c.fill(Geo.X0, Geo.PIT, Geo.Z1 + 1, Geo.X1, Geo.ROOF_TOP, Geo.FZ, Pal.GRY);
        // roof slab: black concrete with a deepslate top skin
        c.fill(Geo.X0, Geo.ROOF, Geo.Z0, Geo.X1, Geo.ROOF_TOP - 1, Geo.Z1, Pal.BLK);
        c.fill(Geo.X0, Geo.ROOF_TOP, Geo.Z0, Geo.X1, Geo.ROOF_TOP, Geo.Z1, Pal.DS);
    }

    // ------------------------------------------------------------------ pit floor

    private static void pitFloor(BuildContext c) {
        c.pattern(Geo.X0, Geo.PIT, Geo.Z0, Geo.X1, Geo.PIT, Geo.Z1, (x, y, z) -> {
            int r = Pal.pct(x, y, z, 3);
            // expansion joints every 8 blocks
            if (Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0) {
                return r < 30 ? Pal.DSC : Pal.BLK;
            }
            if (r < 8) {
                return Pal.BS;
            }
            if (r < 24) {
                return Pal.DSC;
            }
            if (r < 30) {
                return Pal.DSB;
            }
            return Pal.DST;
        });
    }

    // ------------------------------------------------------------------ walls

    private static void walls(BuildContext c) {
        // back wall (z = -8 face), interior to +z
        c.at(Geo.X0, 0, Geo.Z0 - 1, 0, () -> run(c, 90, true));
        // front wall (z = 80 face), local u runs towards -x
        c.at(Geo.X1, 0, Geo.Z1 + 1, 2, () -> run(c, 90, true));
        // west wall (x = -46 face), local u runs towards -z
        c.at(Geo.X0 - 1, 0, Geo.Z1, 3, () -> run(c, 87, false));
        // east wall (x = 45 face), local u runs towards +z
        c.at(Geo.X1 + 1, 0, Geo.Z0, 1, () -> run(c, 87, false));
    }

    /** rib centres (canonical u) for the end walls (90 long) and side walls (87 long). */
    private static final int[] RIBS_END = {9, 23, 37, 52, 66, 80};
    private static final int[] RIBS_SIDE = {15, 29, 43, 57, 71};

    private static void run(BuildContext c, int len, boolean end) {
        // 1. wall face pattern (one layer, local z = 0)
        for (int u = 0; u < len; u++) {
            for (int y = Geo.PIT; y < Geo.ROOF; y++) {
                c.set(u, y, 0, face(u, y));
            }
        }
        // 2. belt courses that stand proud of the wall
        belt(c, len);
        // 3. ribs and corner pilasters
        int[] ribs = end ? RIBS_END : RIBS_SIDE;
        for (int u : ribs) {
            // the two ribs flanking the centre of the end walls stand behind the platforms: pit part only
            boolean centre = end && (u == 37 || u == 52);
            rib(c, u, centre);
        }
        pilaster(c, 1);
        pilaster(c, len - 2);
    }

    /** Base/cornice bands (one block proud of the face) and the pink horizon strip at deck level. */
    private static void belt(BuildContext c, int len) {
        for (int u = 0; u < len; u++) {
            // plinth cap of the pit floor
            c.set(u, Geo.PIT + 1, 1, Pal.BLK);
            c.set(u, Geo.PIT + 2, 1, Pal.DSB);
            // girder under the deck level
            c.set(u, Geo.DECK - 2, 1, Pal.PBSB);
            c.set(u, Geo.DECK - 1, 1, Pal.BLK);
            // hall base band
            c.set(u, Geo.DECK + 1, 1, Pal.BLK);
            c.set(u, Geo.DECK + 2, 1, Pal.PBS);
            // beam carrying the guard ring
            c.set(u, Geo.RING - 1, 1, Pal.BLK);
            c.set(u, Geo.RING - 1, 2, Pal.BLK);
            // top cornice
            c.set(u, Geo.ROOF - 1, 1, Pal.BLK);
            c.set(u, Geo.ROOF - 2, 1, Pal.PBS);
        }
        // pit catwalk support beams
        for (int u = 0; u < len; u++) {
            c.set(u, PIT_CAT_A - 1, 1, Pal.BLK);
            c.set(u, PIT_CAT_A - 1, 2, Pal.BLK);
            c.set(u, PIT_CAT_B - 1, 1, Pal.BLK);
            c.set(u, PIT_CAT_B - 1, 2, Pal.BLK);
        }
    }

    private static void rib(BuildContext c, int u, boolean pitOnly) {
        int top = pitOnly ? Geo.DECK - 3 : Geo.ROOF - 3;
        // continuous except where the catwalks pass: ribs stop 2 below a catwalk beam and resume above its headroom
        int[][] segs = {
                {Geo.PIT + 3, PIT_CAT_A - 3},
                {PIT_CAT_A + 4, PIT_CAT_B - 3},
                {PIT_CAT_B + 4, Geo.DECK - 3},
                {Geo.DECK + 3, Geo.RING - 3},
                {Geo.RING + 4, Geo.ROOF - 3}
        };
        for (int[] s : segs) {
            int y0 = s[0], y1 = Math.min(s[1], top);
            if (y1 < y0) {
                continue;
            }
            c.fill(u - 1, y0, 1, u + 1, y1, 1, Pal.BLK);
            c.fill(u, y0, 2, u, y1, 2, Pal.BLK);
            // chamfered caps
            c.set(u - 1, y1, 1, Pal.DSP);
            c.set(u + 1, y1, 1, Pal.DSP);
            c.set(u, y1, 2, Pal.DSP);
            c.set(u - 1, y0, 1, Pal.DSP);
            c.set(u + 1, y0, 1, Pal.DSP);
            c.set(u, y0, 2, Pal.DSP);
        }
    }

    private static void pilaster(BuildContext c, int u) {
        int[][] segs = {
                {Geo.PIT + 3, PIT_CAT_A - 3},
                {PIT_CAT_A + 4, PIT_CAT_B - 3},
                {PIT_CAT_B + 4, Geo.DECK - 3},
                {Geo.DECK + 3, Geo.RING - 3},
                {Geo.RING + 4, Geo.ROOF - 3}
        };
        for (int[] s : segs) {
            c.fill(u - 1, s[0], 1, u + 1, s[1], 3, Pal.BLK);
            c.fill(u - 1, s[0], 1, u - 1, s[1], 1, Pal.GRY);
        }
    }

    // ------------------------------------------------------------------ face pattern

    /** The wall block at (u, y) of the canonical wall face. */
    static String face(int u, int y) {
        int r = Pal.hash(u, y, 11) % 100;
        if (y <= Geo.PIT + 4) { // plinth with the pink glow strip
            return switch (y - Geo.PIT) {
                case 0 -> Pal.BLK;
                case 1 -> r < 25 ? Pal.DSBC : Pal.DSB;
                case 2 -> Pal.PINK_LIGHT;
                case 3 -> r < 25 ? Pal.DSBC : Pal.DSB;
                default -> Pal.BLK;
            };
        }
        if (y < Geo.DECK) { // pit wall: ribbed, weathered, darker with depth
            boolean seamV = Math.floorMod(u, 8) == 0;
            boolean seamH = Math.floorMod(y + 25, 13) == 0;
            if (seamV || seamH) {
                return Pal.BLK;
            }
            int deep = Math.max(0, Math.min(100, (y - Geo.PIT) * 100 / 70));
            if (r < 34 - deep / 3) {
                return Pal.BLK;
            }
            if (r < 52) {
                return Pal.DSB;
            }
            if (r < 62) {
                return Pal.DSBC;
            }
            if (r < 74) {
                return Pal.DS;
            }
            if (r < 88) {
                return Pal.DST;
            }
            return Pal.BS;
        }
        if (y == Geo.DECK) {
            return Pal.PINK_LIGHT; // horizon strip at deck level
        }
        if (y <= Geo.DECK + 3) {
            return Pal.BLK;
        }
        if (y >= Geo.ROOF - 3) { // cornice with a pink lip
            return y == Geo.ROOF - 3 ? Pal.PINK_LIGHT : Pal.BLK;
        }
        // hall wall: gray concrete panels, vertical seams every 5, weathering
        boolean seam = Math.floorMod(u, 5) == 0;
        if (seam) {
            return Pal.BLK;
        }
        if (y >= Geo.RING - 2 && y <= Geo.RING + 3) {
            return r < 55 ? Pal.DST : Pal.DSP;
        }
        if (r < 12) {
            return Pal.BLK;
        }
        if (r < 24) {
            return Pal.DST;
        }
        if (r < 30) {
            return Pal.DSP;
        }
        return Pal.GRY;
    }
}
