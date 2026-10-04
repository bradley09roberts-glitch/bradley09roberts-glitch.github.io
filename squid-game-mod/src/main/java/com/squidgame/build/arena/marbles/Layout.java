package com.squidgame.build.arena.marbles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The village plan (all coordinates are arena-local; floor blocks at y=0, standing height 1.0).
 *
 * <pre>
 *  x -65 ................................................ 65     (interior 131 x 111, wall + sky roof outside)
 *  N band  : relief houses | 5 courts (mouths south) | alley N1 | gap | WAITING HALL (gate faces south)
 *  centre  : pairing square x[-15,15] z[-9,15] framed by houses west/east, hall north, alley S1 south
 *  S band  : S1 | court row S-A | houses | alley S2 | court row S-B | relief houses
 *  flanks  : 4 columns of 13 courts: [relief 4][CW1 12][alley W1 5][CW2 12][houses 5][alley W2 5] (mirrored east)
 *  cross street z[1,5] links the square with W1/E1; alleys W1,W2,E1,E2 run the full depth
 * </pre>
 * A court is entered from an alley through a gate; the lane (local +Z) leads to a house.
 */
final class Layout {
    private Layout() {
    }

    static final int X0 = -65, X1 = 65, Z0 = -55, Z1 = 55;
    static final int WALL = 3;
    static final int ROOF_Y = 48;

    // hall (waiting room): gate wall plane z = HALL_ZG, room extends towards -Z
    static final int HALL_ZG = -10;
    static final int HALL_W = 37, HALL_D = 21, HALL_H = 9;

    // pairing square
    static final int SQ_X0 = -15, SQ_X1 = 15, SQ_Z0 = -9, SQ_Z1 = 15;

    // court geometry (court-local frame: +Z leads from the gate to the house)
    static final int COURT_LEN = 12;      // interior rows 0..11
    static final int LINE_ROW = 2;        // painted throw line
    static final int AB_ROW = 1;          // partners stand here (1 block behind the line)
    static final int TARGET_ROW = 9;      // bullseye row = line + 7

    enum Kind {COURT, EXIT, TOWER, SHRINE}

    static final class Slot {
        Kind kind;
        String zone;
        int ox, oz, rot;       // origin (gate-line centre, local x=0,z=0) and quarter turns of the local frame
        int houseDepth;
        int k = -1;            // pair spot index for COURT slots

        Slot(Kind kind, String zone, int ox, int oz, int rot, int houseDepth) {
            this.kind = kind;
            this.zone = zone;
            this.ox = ox;
            this.oz = oz;
            this.rot = rot;
            this.houseDepth = houseDepth;
        }

        /** World(arena-local) block of local block (lx, lz). */
        int[] block(int lx, int lz) {
            return switch (rot & 3) {
                case 0 -> new int[]{ox + lx, oz + lz};
                case 1 -> new int[]{ox - lz, oz + lx};
                case 2 -> new int[]{ox - lx, oz - lz};
                default -> new int[]{ox + lz, oz - lx};
            };
        }

        /** Centre of the court interior in arena-local block coordinates. */
        double[] centre() {
            int[] a = block(0, 5);
            return new double[]{a[0] + 0.5, a[1] + 0.5};
        }

        int hx() {
            return block(0, 0)[0];
        }
    }

    /** All plots in a deterministic order; COURT slots carry their pair index k (0..63) ordered by walking distance. */
    static List<Slot> slots() {
        List<Slot> all = new ArrayList<>();
        int[] northZ = new int[7];
        int[] southZ = new int[6];
        for (int i = 0; i < 7; i++) {
            northZ[i] = -52 + 8 * i;
        }
        for (int j = 0; j < 6; j++) {
            southZ[j] = 10 + 8 * j;
        }
        // flank columns: name, mouth x, rot, house depth
        Object[][] cols = {
                {"CW1", -50, 1, 4}, {"CW2", -44, 3, 5}, {"CE2", 44, 1, 5}, {"CE1", 50, 3, 4}
        };
        for (Object[] col : cols) {
            for (int z : northZ) {
                all.add(new Slot(Kind.COURT, (String) col[0], (Integer) col[1], z, (Integer) col[2], (Integer) col[3]));
            }
            for (int z : southZ) {
                all.add(new Slot(Kind.COURT, (String) col[0], (Integer) col[1], z, (Integer) col[2], (Integer) col[3]));
            }
        }
        for (int cx = -16; cx <= 16; cx += 8) {
            all.add(new Slot(cx == 0 ? Kind.EXIT : Kind.COURT, "N", cx, -41, 2, 3));
            all.add(new Slot(cx == 0 ? Kind.TOWER : Kind.COURT, "SA", cx, 20, 0, 4));
            all.add(new Slot(cx == 0 ? Kind.SHRINE : Kind.COURT, "SB", cx, 41, 0, 3));
        }
        List<Slot> courts = new ArrayList<>();
        for (Slot s : all) {
            if (s.kind == Kind.COURT) {
                courts.add(s);
            }
        }
        courts.sort(Comparator.comparingDouble((Slot s) -> {
            double[] c = s.centre();
            return Math.abs(c[0]) + Math.abs(c[1] - 3) * 1.15 + (s.zone.startsWith("C") ? 0 : 0);
        }).thenComparingInt(s -> s.ox).thenComparingInt(s -> s.oz));
        for (int i = 0; i < courts.size(); i++) {
            courts.get(i).k = i;
        }
        return all;
    }
}
