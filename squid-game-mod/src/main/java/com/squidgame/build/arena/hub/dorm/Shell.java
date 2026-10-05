package com.squidgame.build.arena.hub.dorm;

import com.squidgame.build.BuildContext;

/**
 * The hall's envelope: foundation, four masonry walls (x = +-41..42, z = +-33..34) banded like an institution
 * (black skirting, concrete dado, pastel stripes, pale upper wall, steel cornice), pilasters, the ceiling with its
 * flush light panels and the steel roof trusses.
 */
final class Shell {
    private Shell() {
    }

    private static final int SEED = 7421;

    private static final String[] MAIN = {Pal.WHITE, Pal.CALCITE, Pal.DIORITE, Pal.LGRAY};
    private static final String[] DADO = {Pal.LGRAY, Pal.STONE, Pal.ANDESITE, Pal.PANDESITE, Pal.GRAY};
    private static final int[] DADO_W = {34, 18, 18, 22, 8};

    static void build(BuildContext c) {
        // foundation block and carved interior
        c.fill(-42, -6, -34, 42, -2, 34, Pal.BRICKS);
        c.fill(-42, -1, -34, 42, 39, 34, Pal.LGRAY);
        c.clear(Layout.X0, 0, Layout.Z0, Layout.X1, Layout.H, Layout.Z1);

        // faces: north wall in its own frame, the others are rotations of it
        int[] ns = {-36, -28, -20, -12, 12, 20, 28, 36};
        int[] west = {-28, -20};   // local u: world z = 28, 20 (the west gallery with its stair is the east one turned half round)
        int[] east = {-28, -20};   // the east wall carries the windows, the catwalk and its stair
        c.at(0, 0, 0, 0, () -> face(c, 33, 41, ns));
        c.at(0, 0, 0, 2, () -> face(c, 33, 41, ns));
        c.at(0, 0, 0, 1, () -> face(c, 41, 32, east));
        c.at(0, 0, 0, 3, () -> face(c, 41, 32, west));
        corners(c);
        ceiling(c);
        trusses(c);
    }

    // ------------------------------------------------------------------------------------------ walls

    /** North-wall frame: the inner wall layer is the plane z = -d, the hall lies towards +z. */
    private static void face(BuildContext c, int d, int half, int[] pilasters) {
        c.pattern(-half, 0, -d, half, Layout.H, -d, (u, y, z) -> wallBlock(u, y));
        // outer layer: plain concrete, a pink band so the skin looks intentional from outside as well
        c.fill(-half, 8, -d - 1, half, 8, -d - 1, Pal.PINK);
        for (int p : pilasters) {
            pilaster(c, p, d);
        }
    }

    private static String wallBlock(int u, int y) {
        if (y == 0) {
            return Noise.pick(SEED, u, y, 0, new String[]{Pal.STEEL_TILES, Pal.BSTONE, Pal.STEEL}, new int[]{5, 2, 3});
        }
        if (y <= 4) {
            return Noise.pick(SEED + 1, u, y, 0, DADO, DADO_W);
        }
        if (y == 5) {
            return Pal.PANEL_PINK;
        }
        if (y == 15 && (u & 1) == 0) {
            return Pal.PANEL_WHITE;
        }
        if (y == 26) {
            return Pal.PANEL_WHITE;
        }
        if (y == 34) {
            return Pal.STEEL;
        }
        if (y > 34) {
            return Pal.STEEL_TILES;
        }
        // pale wall: large soft stains plus speckle
        double n = Noise.fbm2(SEED + 2, u * 0.16, y * 0.22, 3);
        double r = Noise.rand(SEED + 3, u, y, 0);
        if (y > 26) {
            return r < 0.12 ? Pal.CALCITE : (n < 0.42 ? Pal.LGRAY : Pal.WHITE);
        }
        if (n < 0.30) {
            return r < 0.5 ? Pal.LGRAY : Pal.CALCITE;
        }
        if (n < 0.46) {
            return r < 0.22 ? Pal.DIORITE : Pal.CALCITE;
        }
        return r < 0.10 ? Pal.DIORITE : (r < 0.14 ? Pal.CALCITE : Pal.WHITE);
    }

    /** A 3-wide pilaster standing 1 block proud of the wall: steel plinth, pale shaft with pastel bands, steel capital. */
    private static void pilaster(BuildContext c, int p, int d) {
        int z = -d + 1;
        c.fill(p - 1, 0, z, p + 1, 1, z, Pal.STEEL_TILES);
        c.fill(p - 1, 2, z, p + 1, 33, z, Pal.SMOOTH);
        c.fill(p, 2, z, p, 33, z, Pal.PANDESITE);
        c.fill(p - 1, 5, z, p + 1, 5, z, Pal.PANEL_PINK);
        c.fill(p - 1, 26, z, p + 1, 26, z, Pal.PANEL_WHITE);
        c.set(p, 15, z, Pal.PANEL_WHITE);
        c.fill(p - 1, 34, z, p + 1, 36, z, Pal.STEEL);
        // capital lip
        c.fill(p - 1, 33, z, p + 1, 33, z, Pal.STEEL_TILES);
    }

    /** Interior corner columns hide the seams between the wall planes. */
    private static void corners(BuildContext c) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int x = sx * 39, z = sz * 31;
                c.fill(x, 0, z, x + sx, 36, z + sz, Pal.SMOOTH);
                c.fill(x, 0, z, x + sx, 1, z + sz, Pal.STEEL_TILES);
                c.fill(x, 34, z, x + sx, 36, z + sz, Pal.STEEL);
                c.fill(x, 5, z, x + sx, 5, z + sz, Pal.PANEL_PINK);
                c.fill(x, 26, z, x + sx, 26, z + sz, Pal.PANEL_WHITE);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ roof

    private static void ceiling(BuildContext c) {
        c.fill(-42, 38, -34, 42, 38, 34, Pal.BRICKS);
        c.fill(-42, 39, -34, 42, 39, 34, Pal.SMOOTH);
        // underside: pale industrial concrete with a few darker patches
        String[] ceil = {Pal.SMOOTH, Pal.LGRAY, Pal.STONE, Pal.ANDESITE};
        int[] w = {50, 24, 16, 10};
        c.pattern(Layout.X0, Layout.ROOF, Layout.Z0, Layout.X1, Layout.ROOF, Layout.Z1,
                (x, y, z) -> Noise.pick(SEED + 5, x, 0, z, ceil, w));
        // big flush light panels in the bays between the trusses (decorative: the floor is lit by low fixtures)
        for (int z = -24; z <= 24; z += 8) {
            for (int x = -36; x <= 36; x += 8) {
                c.fill(x - 2, Layout.ROOF, z - 1, x + 2, Layout.ROOF, z + 1, Pal.PANEL_WHITE);
                // thin steel frame around each panel
                c.fill(x - 3, Layout.ROOF, z - 2, x + 3, Layout.ROOF, z - 2, Pal.STEEL_TILES);
                c.fill(x - 3, Layout.ROOF, z + 2, x + 3, Layout.ROOF, z + 2, Pal.STEEL_TILES);
                c.fill(x - 3, Layout.ROOF, z - 1, x - 3, Layout.ROOF, z + 1, Pal.STEEL_TILES);
                c.fill(x + 3, Layout.ROOF, z - 1, x + 3, Layout.ROOF, z + 1, Pal.STEEL_TILES);
            }
        }
    }

    private static void trusses(BuildContext c) {
        for (int z : Layout.TRUSS_Z) {
            int b = Layout.TRUSS_BOTTOM, t = Layout.TRUSS_TOP;
            c.fill(-40, b, z, 40, b, z, Pal.STEEL);
            c.fill(-40, t, z, 40, t, z, Pal.STEEL);
            c.fill(-40, b - 1, z, 40, b - 1, z, Pal.STEEL_SLAB_T);
            for (int x0 = -40; x0 < 40; x0 += 10) {
                c.line(x0, b, z, x0 + 5, t, z, Pal.STEEL_TILES);
                c.line(x0 + 5, t, z, x0 + 10, b, z, Pal.STEEL_TILES);
            }
            for (int x = -40; x <= 40; x += 10) {
                c.fill(x, b + 1, z, x, t - 1, z, Pal.STEEL_WALL);
            }
            // gusset blocks at the bottom joints
            for (int x = -40; x <= 40; x += 10) {
                c.set(x, b, z, Pal.STEEL_BRICKS);
            }
        }
        // purlins tying the trusses together just under the deck
        for (int x = -35; x <= 35; x += 10) {
            c.fill(x, Layout.TRUSS_TOP - 1, -28, x, Layout.TRUSS_TOP - 1, 28, Pal.STEEL_SLAB_T);
        }
    }
}
