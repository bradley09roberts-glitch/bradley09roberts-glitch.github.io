package com.squidgame.build.arena.bridge;

import com.squidgame.build.BuildContext;

/**
 * The hall shell: solid foundation, pit floor, 3 thick walls and roof, then the interior wall faces (plinth with
 * glowing pink strip, plated pit walls, hall walls with ribs and corner pilasters, catwalks, ladders) drawn in a
 * canonical wall frame (u along the wall, local z = depth into the hall, y absolute) and placed four times with
 * rotations. Reading direction of anything drawn along +u is left to right for a viewer facing the wall.
 */
final class Hall {
    private Hall() {
    }

    /** Catwalk levels in the pit (floor layer y); ribs are interrupted around them. */
    static final int PIT_CAT_A = -10;
    static final int PIT_CAT_B = 14;

    /** rib centres (canonical u) for the end walls (90 long) and side walls (87 long). */
    static final int[] RIBS_END = {9, 23, 37, 52, 66, 80};
    static final int[] RIBS_SIDE = {15, 29, 43, 57, 71};

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
        c.at(Geo.X0, 0, Geo.Z0 - 1, 0, () -> run(c, 90, WallArt.BACK));
        // front wall (z = 80 face), local u runs towards -x
        c.at(Geo.X1, 0, Geo.Z1 + 1, 2, () -> run(c, 90, WallArt.FRONT));
        // west wall (x = -46 face), local u runs towards -z
        c.at(Geo.X0 - 1, 0, Geo.Z1, 3, () -> run(c, 87, WallArt.WEST));
        // east wall (x = 45 face), local u runs towards +z
        c.at(Geo.X1 + 1, 0, Geo.Z0, 1, () -> run(c, 87, WallArt.EAST));
        // security cameras on the four corner posts of the guard ring: a black housing and an end rod lens
        for (int[] k : new int[][]{{Geo.X0 + 3, Geo.Z0 + 3, 1}, {Geo.X1 - 3, Geo.Z0 + 3, -1}, {Geo.X0 + 3, Geo.Z1 - 3, 1}, {Geo.X1 - 3, Geo.Z1 - 3, -1}}) {
            c.set(k[0], Geo.RING + 4, k[1], Pal.BLK);
            c.set(k[0] + k[2], Geo.RING + 4, k[1], "minecraft:end_rod[facing=" + (k[2] > 0 ? "east" : "west") + "]");
        }
        // the corner squares of the catwalks get their own light (the runs overwrite each other's dashes there)
        for (int y : new int[]{Geo.RING, PIT_CAT_A, PIT_CAT_B}) {
            for (int x : new int[]{Geo.X0 + 1, Geo.X1 - 1}) {
                for (int z : new int[]{Geo.Z0 + 1, Geo.Z1 - 1}) {
                    c.set(x, y, z, y == Geo.RING ? Pal.WHITE_LIGHT : Pal.PINK_LIGHT);
                }
            }
        }
    }

    private static void run(BuildContext c, int len, int wall) {
        boolean end = wall == WallArt.BACK || wall == WallArt.FRONT;
        // 1. wall face pattern (one layer, local z = 0)
        for (int u = 0; u < len; u++) {
            for (int y = Geo.PIT; y < Geo.ROOF; y++) {
                c.set(u, y, 0, face(u, y));
            }
        }
        // 2. belt courses that stand proud of the wall
        belt(c, len, end);
        // 3. ribs and corner pilasters
        int[] ribs = end ? RIBS_END : RIBS_SIDE;
        for (int u : ribs) {
            // the two ribs flanking the centre of the end walls stand behind the platforms: pit part only
            boolean centre = end && (u == 37 || u == 52);
            rib(c, u, centre);
        }
        pilaster(c, 1);
        pilaster(c, len - 2);
        // 4. catwalks: the guard ring and the two pit levels
        catwalk(c, len, Geo.RING, false, ribs);
        catwalk(c, len, PIT_CAT_A, true, ribs);
        catwalk(c, len, PIT_CAT_B, true, ribs);
        // 5. ladders in the pit (floor -> level A -> level B), not reaching the platforms
        int u1 = end ? 16 : 22, u2 = end ? 73 : 64;
        ladder(c, u1, Geo.PIT + 2, PIT_CAT_A + 1);
        ladder(c, u2, PIT_CAT_A + 1, PIT_CAT_B + 1);
        // 6. banners, balcony, exit
        WallArt.decorate(c, len, wall);
    }

    /** Base/cornice bands (one block proud of the face) and catwalk support beams. */
    private static void belt(BuildContext c, int len, boolean end) {
        for (int u = 0; u < len; u++) {
            // plinth courses of the pit floor with an open groove at PIT + 2 that shows the pink glow strip
            c.set(u, Geo.PIT + 1, 1, Pal.BLK);
            c.set(u, Geo.PIT + 3, 1, Pal.DSB);
            // girder under the deck level
            c.set(u, Geo.DECK - 2, 1, Pal.PBSB);
            c.set(u, Geo.DECK - 1, 1, Pal.BLK);
            // hall base band (not where a platform stands against the wall: u 33..56 on the end walls)
            if (!(end && u >= 33 && u <= 56)) {
                c.set(u, Geo.DECK + 1, 1, Pal.BLK);
                c.set(u, Geo.DECK + 2, 1, Pal.PBS);
            }
            // beams carrying the catwalks
            for (int y : new int[]{Geo.RING - 1, PIT_CAT_A - 1, PIT_CAT_B - 1}) {
                c.set(u, y, 1, Pal.BLK);
                c.set(u, y, 2, Pal.BLK);
            }
            // top cornice
            c.set(u, Geo.ROOF - 1, 1, Pal.BLK);
            c.set(u, Geo.ROOF - 2, 1, Pal.PBS);
        }
    }

    private static final int[][] SEGS = {
            {Geo.PIT + 3, PIT_CAT_A - 3},
            {PIT_CAT_A + 4, PIT_CAT_B - 3},
            {PIT_CAT_B + 4, Geo.DECK - 3},
            {Geo.DECK + 3, Geo.RING - 3},
            {Geo.RING + 4, Geo.ROOF - 3}
    };

    private static void rib(BuildContext c, int u, boolean pitOnly) {
        int top = pitOnly ? Geo.DECK - 3 : Geo.ROOF - 3;
        for (int[] s : SEGS) {
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
        for (int[] s : SEGS) {
            c.fill(u - 1, s[0], 1, u + 1, s[1], 3, Pal.BLK);
            c.fill(u - 1, s[0], 1, u - 1, s[1], 1, Pal.GRY);
        }
    }

    // ------------------------------------------------------------------ catwalks and ladders

    /**
     * A 4 wide catwalk along the wall (local z = 1..4) at floor layer y: plated floor with a dashed glow line, a
     * rail on the open edge (z = 4) that stops 4 blocks before each corner (the corner squares stay open) with a
     * corner post, support braces under the floor at the ribs.
     */
    private static void catwalk(BuildContext c, int len, int y, boolean pit, int[] ribs) {
        for (int u = 0; u < len; u++) {
            c.set(u, y, 1, Pal.DST);
            c.set(u, y, 2, Pal.DSP);
            c.set(u, y, 3, Pal.DSP);
            c.set(u, y, 4, Pal.DST);
            if (u >= 4 && u <= len - 5) {
                c.set(u, y + 1, 4, Pal.BARS);
                c.set(u, y + 2, 4, Pal.BARS);
            }
        }
        for (int u = 2; u < len - 2; u += 4) {
            c.set(u, y, 2, pit ? Pal.PINK_LIGHT : Pal.WHITE_LIGHT);
        }
        for (int u = 4; u <= len - 5; u += 7) {
            c.fill(u, y + 1, 4, u, y + 3, 4, Pal.BLK);
        }
        for (int u : new int[]{3, len - 4}) {
            c.fill(u, y + 1, 4, u, y + 3, 4, Pal.BLK);
            c.set(u, y + 3, 4, Pal.PBS);
        }
        for (int u : ribs) {
            c.line(u, y - 6, 1, u, y - 1, 4, Pal.BLK);
        }
        c.line(1, y - 6, 1, 1, y - 1, 4, Pal.BLK);
        c.line(len - 2, y - 6, 1, len - 2, y - 1, 4, Pal.BLK);
    }

    private static void ladder(BuildContext c, int u, int y0, int y1) {
        for (int y = y0; y <= y1; y++) {
            c.set(u, y, 1, Pal.ladder("south"));
        }
    }

    // ------------------------------------------------------------------ face pattern

    /** A plate of the hall wall at world column x / height y (used to re-skin the waiting room gate wall). */
    static String plate(int x, int y) {
        int u = x + 45;
        int yy = y - Geo.DECK - 4;
        if (Math.floorMod(u, 5) == 0 || Math.floorMod(yy, 10) == 0) {
            return Pal.BLK;
        }
        int tone = Pal.hash(Math.floorDiv(u, 5), Math.floorDiv(yy, 10), 91) % 100;
        return tone < 50 ? Pal.GRY : (tone < 72 ? Pal.DST : (tone < 88 ? Pal.DSP : Pal.BLK));
    }

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
        if (y < Geo.DECK) { // pit wall: plated, weathered, darker with depth
            int pu = Math.floorDiv(u, 7), py = Math.floorDiv(y + 25, 13);
            if (Math.floorMod(u, 7) == 0 || Math.floorMod(y + 25, 13) == 0) {
                return Pal.BLK;
            }
            int deep = Math.max(0, Math.min(100, (y - Geo.PIT) * 100 / 70));
            int tone = Pal.hash(pu, py, 71) % 100;
            if (tone < 30 - deep / 4) {
                return r < 85 ? Pal.BLK : Pal.DSBC;
            }
            if (tone < 55) {
                return r < 82 ? Pal.DSB : (r < 92 ? Pal.DSBC : Pal.BLK);
            }
            if (tone < 78) {
                return r < 85 ? Pal.DS : Pal.DSB;
            }
            return r < 85 ? Pal.DST : Pal.DSC;
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
        // hall wall: plates of 5 x 10 with black seams, four tones
        int yy = y - Geo.DECK - 4;
        if (Math.floorMod(u, 5) == 0 || Math.floorMod(yy, 10) == 0) {
            return Pal.BLK;
        }
        int tone = Pal.hash(Math.floorDiv(u, 5), Math.floorDiv(yy, 10), 91) % 100;
        String base = tone < 50 ? Pal.GRY : (tone < 72 ? Pal.DST : (tone < 88 ? Pal.DSP : Pal.BLK));
        if (r < 5) {
            return base.equals(Pal.GRY) ? Pal.DST : Pal.GRY;
        }
        return base;
    }
}
