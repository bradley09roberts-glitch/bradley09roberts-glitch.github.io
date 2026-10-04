package com.squidgame.build.arena.tug;

import com.squidgame.build.BuildContext;

/**
 * The two cantilevered steel decks. Each deck is 5 blocks wide (z -2..2) with black plate-girder curbs at z = +-3,
 * 53 blocks long (d = 0 at the gap tip .. 52 at the pier), carried by two planar lattice walls that stand on the pit
 * floor 70 blocks below, X-braced and tied together under the deck. At the tip stand the rope-anchor portals.
 */
final class DeckBuilder {
    private DeckBuilder() {
    }

    /** Lattice column positions (d of the first of 2 blocks) and horizontal chord levels. */
    static final int[] COLS = {10, 26, 42};
    static final int[] LV = {35, 19, 3, -13, -29};

    static void build(BuildContext c) {
        deck(new Side(c, -1, Pal.RED, "red"));
        deck(new Side(c, +1, Pal.BLUE, "blue"));
    }

    /** One deck seen in its own frame: d = distance from the gap along the deck. */
    static final class Side {
        final BuildContext c;
        final int s;
        final String team;
        final String teamName;

        Side(BuildContext c, int s, String team, String teamName) {
            this.c = c;
            this.s = s;
            this.team = team;
            this.teamName = teamName;
        }

        int x(int d) {
            return s < 0 ? -8 - d : 7 + d;
        }

        int d(int x) {
            return s < 0 ? -8 - x : x - 7;
        }

        String toGap() {
            return s < 0 ? "east" : "west";
        }

        String awayGap() {
            return s < 0 ? "west" : "east";
        }

        void fill(int d1, int d2, int y1, int y2, int z1, int z2, String st) {
            c.fill(x(d1), y1, z1, x(d2), y2, z2, st);
        }

        void set(int d, int y, int z, String st) {
            c.set(x(d), y, z, st);
        }

        void line(int d1, int y1, int z1, int d2, int y2, int z2, String st) {
            c.line(x(d1), y1, z1, x(d2), y2, z2, st);
        }
    }

    private static void deck(Side sd) {
        BuildContext c = sd.c;
        int last = Geo.DECK_LEN - 1;                // d = 52 at the pier
        // --- walking surface (y = 40): lanes along the deck, cross joints, team stripes
        for (int d = 0; d <= last; d++) {
            boolean joint = d % 4 == 1;
            for (int z = -2; z <= 2; z++) {
                String st;
                int az = Math.abs(z);
                if (d <= 1) {
                    st = Pal.hazard(d + z + 100);                          // danger band at the drop
                } else if (joint) {
                    st = az == 1 ? sd.team : Pal.BS_POLISHED;
                } else if (az == 1) {
                    st = sd.team;
                } else if (az == 2) {
                    st = Pal.PLANK_D;
                } else if (d % 4 == 3) {
                    st = Pal.WHITE;                                        // ruler tick in the team lane every 4 blocks
                } else {
                    st = Pal.hash(d, 0, 9) < 0.15 ? "minecraft:stripped_spruce_log[axis=z]" : Pal.PLANK_S;
                }
                c.set(sd.x(d), Geo.DECK, z, st);
            }
        }
        // --- flush floor lights in the outer lanes (every 4 blocks), 3-2 pattern gives >= level 10 on the centre lane
        for (int d = 3; d <= last; d += 4) {
            c.set(sd.x(d), Geo.DECK, -2, Pal.PANEL_WARM);
            c.set(sd.x(d), Geo.DECK, 2, Pal.PANEL_WARM);
        }
        // --- plate under the deck, ribs, curbs (plate girders) at z = +-3
        sd.fill(0, last, 39, 39, -3, 3, Pal.BLACK);
        for (int d = 0; d <= last; d += 3) {
            sd.fill(d, d, 38, 38, -2, 2, Pal.BLACK);
        }
        for (int side = -1; side <= 1; side += 2) {
            int z = 3 * side;
            sd.fill(0, last, 36, 40, z, z, Pal.BLACK);
            sd.fill(0, last, 40, 40, z, z, Pal.BS_POLISHED);              // curb top
            sd.fill(0, last, 36, 36, z, z, Pal.IRON);                     // bottom flange
            for (int d = 0; d <= last; d += 3) {
                sd.set(d, 38, z, Pal.BS_BRICKS);                          // rivet plates
            }
            // safety rails (iron bars), open at the gap edge: portal posts take over there
            sd.fill(2, last, 41, 42, z, z, Pal.BARS);
            // hazard stripe vertical band at the tip nose
            sd.set(0, 38, z, Pal.YELLOW);
            sd.set(0, 37, z, Pal.BLACK);
        }
        // tip nose: face of the deck toward the gap, hazard band
        for (int z = -3; z <= 3; z++) {
            sd.set(0, 39, z, Pal.hazard(z + 100));
            sd.set(0, 38, z, Pal.hazard(z + 102));
        }
        lattice(sd);
        portal(sd);
    }

    // ------------------------------------------------------------------ lattice

    private static void lattice(Side sd) {
        int last = Geo.DECK_LEN - 1;
        for (int plane = 0; plane < 2; plane++) {
            int zLo = plane == 0 ? 4 : -5;                 // plane covers zLo..zLo+1
            int zHi = zLo + 1;
            // columns with iron collars at the chord levels and a hazard-striped foot
            for (int col : COLS) {
                sd.fill(col, col + 1, -29, 35, zLo, zHi, Pal.STEEL);
                for (int lv : LV) {
                    sd.fill(col, col + 1, lv, lv, zLo, zHi, Pal.IRON);
                }
                for (int y = -29; y <= -26; y++) {
                    sd.fill(col, col + 1, y, y, zLo, zHi, Pal.hazard(y + col));
                }
            }
            // chords: heavy top chord just under the girder, light ones below, running into the pier
            int dA = COLS[0];
            for (int k = 0; k < LV.length; k++) {
                sd.fill(dA, last, LV[k], LV[k], zLo, zHi, Pal.STEEL);
            }
            sd.fill(dA, last, 34, 34, zLo, zHi, Pal.STEEL);
            // X bracing in every panel (45 degrees in the square bays), a single diagonal in the last bay at the pier
            for (int i = 0; i < COLS.length; i++) {
                int d0 = COLS[i] + 2;
                int d1 = i + 1 < COLS.length ? COLS[i + 1] - 1 : last;
                for (int k = 0; k + 1 < LV.length; k++) {
                    int y0 = LV[k] - 1, y1 = LV[k + 1] + 1;
                    for (int z = zLo; z <= zHi; z++) {
                        if (i + 1 < COLS.length) {
                            sd.line(d0, y0, z, d1, y1, z, Pal.STEEL);
                            sd.line(d0, y1, z, d1, y0, z, Pal.STEEL);
                        } else if (((i + k) & 1) == 0) {
                            sd.line(d0, y0, z, d1, y1, z, Pal.STEEL);
                        } else {
                            sd.line(d0, y1, z, d1, y0, z, Pal.STEEL);
                        }
                    }
                }
            }
        }
        // cross ties between the two planes: struts under the deck at every column, X ties in the YZ plane lower down
        for (int col : COLS) {
            for (int k = 0; k < LV.length; k += 2) {
                sd.fill(col, col + 1, LV[k], LV[k], -3, 3, Pal.STEEL);
            }
            for (int k : new int[]{0, 2}) {
                for (int dd = 0; dd < 2; dd++) {
                    sd.line(col + dd, LV[k] - 1, -3, col + dd, LV[k + 1] + 1, 3, Pal.STEEL);
                    sd.line(col + dd, LV[k] - 1, 3, col + dd, LV[k + 1] + 1, -3, Pal.STEEL);
                }
            }
        }
        // cantilever knee braces from the first column up under the tip girder (45 degrees)
        for (int zLo : new int[]{4, -5}) {
            for (int z = zLo; z <= zLo + 1; z++) {
                sd.line(COLS[0], 26, z, 1, 35, z, Pal.STEEL);
                sd.line(COLS[0] + 1, 26, z, 2, 35, z, Pal.STEEL);
                sd.line(COLS[0], 18, z, 1, 34, z, Pal.STEEL);
            }
        }
    }

    // ------------------------------------------------------------------ rope anchor portal at the gap edge

    private static void portal(Side sd) {
        BuildContext c = sd.c;
        String logY = Pal.log("stripped_dark_oak_log", 'y');
        for (int side = -1; side <= 1; side += 2) {
            int zA = side > 0 ? 3 : -4;             // post footprint z zA..zA+1
            // footing beside the curb
            sd.fill(0, 1, 36, 40, zA, zA + 1, Pal.BLACK);
            sd.fill(0, 1, 40, 40, zA, zA + 1, Pal.BS_POLISHED);
            // thick post (2x2 stripped dark oak) with iron collars
            sd.fill(0, 1, 41, 47, zA, zA + 1, logY);
            sd.fill(0, 1, 41, 41, zA, zA + 1, Pal.IRON);
            sd.fill(0, 1, 45, 45, zA, zA + 1, Pal.IRON);
        }
        // cross beam over the lane (7 blocks clear above the deck)
        sd.fill(0, 1, 48, 48, -4, 4, Pal.log("stripped_dark_oak_log", 'z'));
        sd.fill(0, 1, 47, 47, -4, -3, Pal.IRON);
        sd.fill(0, 1, 47, 47, 3, 4, Pal.IRON);
        // hook: chain from the beam with a lantern, directly above the rope end
        sd.set(0, 47, 0, Pal.chain());
        sd.set(0, 46, 0, Pal.chain());
        sd.set(0, 45, 0, Pal.chain());
        sd.set(0, 44, 0, "minecraft:lantern[hanging=true]");
    }
}
