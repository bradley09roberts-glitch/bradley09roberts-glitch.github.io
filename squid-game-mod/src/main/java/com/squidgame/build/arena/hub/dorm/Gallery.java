package com.squidgame.build.arena.hub.dorm;

import com.squidgame.build.BuildContext;

/**
 * The east gallery: a steel catwalk (standing level y = 12, 5 wide, z[-17,16]) along the inner face of the east wall,
 * right under the black control-room windows, reached by a masonry-and-steel stair that climbs the wall from the south
 * (three flights of four steps with one-block landings, 3 wide). In front of the control-room door (z[-2,2]) the
 * catwalk bulges into a landing. Rails are 1.5-high walls topped with iron bars so the route stays free but nobody
 * walks off; every step is at most one block.
 *
 * <p>Frame: hub frame, east side. Deck blocks at y = 11. Guard posts on the catwalk are emitted by {@link Guards}.
 */
final class Gallery {
    private Gallery() {
    }

    static final int DECK = 11;                // block layer; standing level 12
    static final int X0 = 36, X1 = 40;
    static final int Z_N = -17, Z_S = 16;
    private static final int SEED = 5531;

    /** {first step z, first step block y} of the three flights; landings {z, block y}. */
    private static final int[][] FLIGHTS = {{30, 0}, {25, 4}, {20, 8}};
    private static final int[][] LANDINGS = {{26, 3}, {21, 7}};

    private static final String[] DECK_MIX = {Pal.PANDESITE, Pal.SMOOTH, Pal.STONE, Pal.ANDESITE};
    private static final int[] DECK_W = {40, 30, 18, 12};

    static void build(BuildContext c) {
        platform(c);
        landing(c);
        stair(c);
        rails(c);
        lights(c);
    }

    /** Lit deck tiles on the catwalk, sea lanterns in the stair stringer and wall lamps beside the steps. */
    private static void lights(BuildContext c) {
        for (int z = -15; z <= 15; z += 5) {
            c.set(38, DECK, z, Pal.PANEL_WARM);
        }
        for (int z : new int[]{-3, 3}) {
            c.set(35, DECK, z, Pal.PANEL_WARM);
        }
        for (int[] s : new int[][]{{28, 2}, {25, 4}, {22, 6}, {18, 10}}) {
            c.set(37, s[1], s[0], Pal.SEA);
        }
        for (int[] w : new int[][]{{29, 3}, {24, 7}, {19, 11}}) {
            c.set(41, w[1], w[0], Pal.SEA);
        }
        c.set(39, 3, 26, Pal.PANEL_WARM);
        c.set(39, 7, 21, Pal.PANEL_WARM);
    }

    // ------------------------------------------------------------------------------------------ platform

    private static void platform(BuildContext c) {
        c.pattern(X0, DECK, Z_N, X1, DECK, Z_S, (x, y, z) -> deckBlock(x, z));
        // pink edge stripe along the open side and a mint stripe against the wall
        c.fill(X0, DECK, Z_N, X0, DECK, Z_S, Pal.STEEL_TILES);
        c.fill(X0 + 1, DECK, Z_N, X0 + 1, DECK, Z_S, Pal.PINK);
        // underside: beams and a sturdy cage of columns and bars
        for (int z = -16; z <= 16; z += 4) {
            c.fill(X0, DECK - 1, z, X1, DECK - 1, z, Pal.STEEL_TILES);
        }
        c.fill(X0, DECK - 1, Z_N, X0, DECK - 1, Z_S, Pal.STEEL_TILES);
        c.fill(X1, DECK - 1, Z_N, X1, DECK - 1, Z_S, Pal.STEEL_TILES);
        for (int z = -16; z <= 16; z += 4) {
            c.fill(X0, 0, z, X0, DECK - 2, z, Pal.STEEL);
            // diagonal braces in the front plane
            if (z < 16) {
                c.line(X0, 0, z, X0, DECK - 2, z + 4, Pal.STEEL_WALL);
            }
        }
        // skirt of bars between the columns
        for (int z = -15; z <= 15; z++) {
            if (Math.floorMod(z, 4) == 0) {
                continue;
            }
            c.fill(X0, 1, z, X0, DECK - 2, z, Pal.BARS);
        }
        c.fill(X0, 0, Z_N, X0, DECK - 2, Z_N, Pal.STEEL);
        // under the deck the cavity is closed at both ends
        c.fill(X0, 0, Z_N, X1, DECK - 2, Z_N, Pal.STEEL_TILES);
    }

    private static String deckBlock(int x, int z) {
        return Noise.pick(SEED, x, 0, z, DECK_MIX, DECK_W);
    }

    /** The bulge in front of the control-room door: x[34,35] z[-6,6]. */
    private static void landing(BuildContext c) {
        c.pattern(34, DECK, -6, 35, DECK, 6, (x, y, z) -> deckBlock(x, z));
        c.fill(34, DECK, -6, 34, DECK, 6, Pal.STEEL_TILES);
        c.fill(34, DECK, -6, 35, DECK, -6, Pal.STEEL_TILES);
        c.fill(34, DECK, 6, 35, DECK, 6, Pal.STEEL_TILES);
        c.fill(34, DECK - 1, -6, 35, DECK - 1, 6, Pal.STEEL_TILES);
        for (int z : new int[]{-6, 6}) {
            c.fill(34, 0, z, 34, DECK - 2, z, Pal.STEEL);
        }
        for (int z = -5; z <= 5; z++) {
            c.fill(34, 1, z, 34, DECK - 2, z, Pal.BARS);
        }
        c.fill(34, 0, -6, 34, DECK - 2, -6, Pal.STEEL);
        c.fill(34, 0, 6, 34, DECK - 2, 6, Pal.STEEL);
        // the front edge of the platform between the bulge and the main deck is open
        c.fill(X0, DECK, -5, X0, DECK, 5, deckBlockStatic());
        c.fill(X0 + 1, DECK, -5, X0 + 1, DECK, 5, deckBlockStatic());
        c.fill(X0, 1, -5, X0, DECK - 2, 5, Pal.AIR);
    }

    private static String deckBlockStatic() {
        return Pal.PANDESITE;
    }

    // ------------------------------------------------------------------------------------------ stair

    /** Three flights of four steps along x[38,40]: stairs ascend northwards (facing=north). */
    private static void stair(BuildContext c) {
        String step = Pal.stair(Pal.STEEL_STAIRS, "north", false);
        for (int[] f : FLIGHTS) {
            for (int i = 0; i < 4; i++) {
                int z = f[0] - i, y = f[1] + i;
                c.fill(38, y, z, 40, y, z, step);
                // solid mass under the step and the west stringer
                if (y > 0) {
                    c.fill(38, 0, z, 40, y - 1, z, mass(z));
                }
                if (z != 30) {
                    c.fill(37, 0, z, 37, y, z, Pal.STEEL_TILES);
                }
            }
        }
        for (int[] l : LANDINGS) {
            int z = l[0], y = l[1];
            c.fill(38, y, z, 40, y, z, Pal.PANDESITE);
            c.fill(38, 0, z, 40, y - 1, z, mass(z));
            c.fill(37, 0, z, 37, y, z, Pal.STEEL_TILES);
        }
        // close the cavity under the platform at its south end
        c.fill(X0, 0, Z_S + 1, 37, DECK - 1, Z_S + 1, Pal.STEEL_TILES);
    }

    private static String mass(int z) {
        return Noise.pick(SEED + 1, 0, 0, z, new String[]{Pal.STEEL_TILES, Pal.STEEL, Pal.BSTONE_BRICKS}, new int[]{5, 3, 2});
    }

    // ------------------------------------------------------------------------------------------ rails

    private static void rails(BuildContext c) {
        // platform front rail x = 36, z[-17, 16] except the landing opening z[-5, 5]
        for (int z = Z_N; z <= Z_S; z++) {
            if (z >= -5 && z <= 5) {
                continue;
            }
            c.set(X0, DECK + 1, z, Pal.STEEL_WALL);
            c.set(X0, DECK + 2, z, Pal.BARS);
        }
        // north end
        for (int x = X0; x <= X1; x++) {
            c.set(x, DECK + 1, Z_N, Pal.STEEL_WALL);
            c.set(x, DECK + 2, Z_N, Pal.BARS);
        }
        // landing rails
        for (int z = -6; z <= 6; z++) {
            c.set(34, DECK + 1, z, Pal.STEEL_WALL);
            c.set(34, DECK + 2, z, Pal.BARS);
        }
        for (int x = 34; x <= 35; x++) {
            for (int z : new int[]{-6, 6}) {
                c.set(x, DECK + 1, z, Pal.STEEL_WALL);
                c.set(x, DECK + 2, z, Pal.BARS);
            }
        }
        // south end of the platform, west of the stair top
        for (int x = X0; x <= 37; x++) {
            c.set(x, DECK + 1, Z_S, Pal.STEEL_WALL);
            c.set(x, DECK + 2, Z_S, Pal.BARS);
        }
        // stair rail on x = 37 following the steps (the stringer below is flush with the step blocks)
        for (int[] f : FLIGHTS) {
            for (int i = 0; i < 4; i++) {
                int z = f[0] - i, y = f[1] + i;
                if (z == 30) {
                    continue;
                }
                c.set(37, y + 1, z, Pal.STEEL_WALL);
                c.set(37, y + 2, z, Pal.BARS);
            }
        }
        for (int[] l : LANDINGS) {
            c.set(37, l[1] + 1, l[0], Pal.STEEL_WALL);
            c.set(37, l[1] + 2, l[0], Pal.BARS);
        }
    }
}
