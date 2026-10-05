package com.squidgame.build.arena.marbles;

import com.squidgame.build.BuildContext;

import java.util.List;

/**
 * Street life in the alleys: ground lanterns at every gate, lantern strings and laundry lines across the two long
 * N-S alleys and the cross street, power poles with wires, corner planters at the crossings, lamp pairs at the dead ends.
 */
final class Alleys {
    private Alleys() {
    }

    static final int[] NS_ALLEY_X = {-47, -25, 25, 47};

    static void build(BuildContext c, List<Layout.Slot> slots) {
        gateLamps(c, slots);
        strings(c, slots);
        crossStreet(c);
        junctions(c);
        poles(c);
        deadEnds(c);
        trees(c);
    }

    /** Ground lanterns flanking every gate, on the alley side (local z = -2). */
    private static void gateLamps(BuildContext c, List<Layout.Slot> slots) {
        for (Layout.Slot s : slots) {
            c.at(s.ox, 0, s.oz, s.rot, () -> {
                c.set(-2, 1, -2, Mat.lantern(false));
                c.set(2, 1, -2, Mat.lantern(false));
            });
        }
    }

    /**
     * Lantern strings and clothes lines across W1 / E1 between the gate pillars of facing courts (chain at y = 5 from pillar
     * top to pillar top, lanterns or cloths hanging below).
     */
    private static void strings(BuildContext c, List<Layout.Slot> slots) {
        U.Rnd r = new U.Rnd(4242);
        for (Layout.Slot s : slots) {
            if (!s.zone.equals("CW1") && !s.zone.equals("CE2")) {
                continue;
            }
            for (int lx : new int[]{-3, 3}) {
                if (!r.chance(0.5)) {
                    continue;
                }
                int[] a = s.block(lx, -1);
                int x1 = a[0] + 1, x2 = a[0] + 3;
                c.fill(x1, 5, a[1], x2, 5, a[1], Mat.CHAIN_X);
                boolean lanterns = r.chance(0.6);
                for (int x = x1; x <= x2; x++) {
                    if (lanterns) {
                        if (x == x1 + 1 || r.chance(0.3)) {
                            c.set(x, 4, a[1], Mat.lantern(true));
                        }
                    } else if (r.chance(0.8)) {
                        c.set(x, 4, a[1], Mat.wool(Props.randomColor(r)));
                    }
                }
            }
        }
    }

    /** Cross street z in [1,5]: lantern lines and clothes lines from wall coping to wall coping (chain at y = 5). */
    private static void crossStreet(BuildContext c) {
        U.Rnd r = new U.Rnd(777);
        for (int side = -1; side <= 1; side += 2) {
            for (int x = 14; x <= 62; x += 3) {
                int xx = side * x;
                // keep the crossings with the N-S alleys free of lines
                boolean nearAlley = false;
                for (int cx : NS_ALLEY_X) {
                    if (Math.abs(xx - cx) <= 3) {
                        nearAlley = true;
                    }
                }
                if (nearAlley || !r.chance(0.55)) {
                    continue;
                }
                c.fill(xx, 5, 1, xx, 5, 5, Mat.CHAIN_Z);
                if (r.chance(0.55)) {
                    c.set(xx, 4, 3, Mat.lantern(true));
                } else {
                    for (int z = 2; z <= 4; z++) {
                        if (r.chance(0.7)) {
                            c.set(xx, 4, z, Mat.wool(Props.randomColor(r)));
                        }
                    }
                }
            }
        }
    }

    /** Corner planters and a stone lamp at the crossings of the cross street with the N-S alleys. */
    private static void junctions(BuildContext c) {
        for (int cx : NS_ALLEY_X) {
            for (int z : new int[]{1, 5}) {
                for (int dx : new int[]{-2, 2}) {
                    if ((dx > 0) == (z == 1)) {
                        Props.stoneLamp(c, cx + dx, 1, z);
                    } else if (((cx + dx + z) & 3) == 0) {
                        Civic.smallTree(c, cx + dx, z);
                    } else {
                        Props.planter(c, cx + dx, 1, z, ((cx + dx + z) & 1) == 0);
                    }
                }
            }
        }
    }

    /** Power poles with two wires: along the cross street and the civic alleys W2 / E2. */
    private static void poles(BuildContext c) {
        for (int side = -1; side <= 1; side += 2) {
            // cross street: poles against the north wall, wires along x at y = 10
            int[] px = {62, 54, 38, 21};
            for (int i = 0; i < px.length; i++) {
                int x = side * px[i];
                pole(c, x, 1, 'z');
                if (i + 1 < px.length) {
                    int x2 = side * px[i + 1];
                    int lo = Math.min(x, x2) + 1, hi = Math.max(x, x2) - 1;
                    c.fill(lo, 10, 1, hi, 10, 1, Mat.CHAIN_X);
                    c.fill(lo, 10, 3, hi, 10, 3, Mat.CHAIN_X);
                }
            }
            // civic alleys: poles on the inner edge, wires along z at y = 11
            int[] pz = {-52, -36, -20, 14, 30, 46};
            for (int i = 0; i < pz.length; i++) {
                pole(c, side * 23, pz[i], side < 0 ? 'w' : 'e');
                if (i + 1 < pz.length && pz[i + 1] - pz[i] <= 16) {
                    c.fill(side * 24, 11, pz[i] + 1, side * 24, 11, pz[i + 1] - 1, Mat.CHAIN_Z);
                    c.fill(side * 26, 11, pz[i] + 1, side * 26, 11, pz[i + 1] - 1, Mat.CHAIN_Z);
                }
            }
        }
    }

    /** A timber pole 10 high with a cross arm along {@code arm} ('z' = along z, 'w'/'e' = towards -x / +x) and end-rod insulators. */
    private static void pole(BuildContext c, int x, int z, char arm) {
        c.fill(x, 1, z, x, 11, z, Mat.log(Mat.LOG, "y"));
        if (arm == 'z') {
            c.fill(x, 10, z, x, 10, z + 2, Mat.log(Mat.LOG_S, "z"));
            c.set(x, 11, z, "minecraft:end_rod[facing=up]");
            c.set(x, 11, z + 2, "minecraft:end_rod[facing=up]");
        } else {
            int d = arm == 'w' ? -1 : 1;
            c.fill(Math.min(x, x + 3 * d), 11, z, Math.max(x, x + 3 * d), 11, z, Mat.log(Mat.LOG_S, "x"));
            c.set(x + d, 12, z, "minecraft:end_rod[facing=up]");
            c.set(x + 3 * d, 12, z, "minecraft:end_rod[facing=up]");
        }
        c.set(x, 12, z, Mat.slabB(Mat.TILE_SL));
    }

    /** A few small flowering trees on the inner edge of the civic alleys and at the ends of N1 / S1 / S2 (breaks up the grid). */
    private static void trees(BuildContext c) {
        for (int side = -1; side <= 1; side += 2) {
            for (int z : new int[]{-44, -28, -12, 20, 36, 52}) {
                Civic.smallTree(c, side * 23, z + (side > 0 ? 2 : 0));
            }
            // corners of N1 / S2 next to the civic column, on the alleys' outer edge rows (keeps them 4 wide)
            Civic.smallTree(c, side * 22, -36);
            Civic.smallTree(c, side * 22, 36);
        }
    }

    /** Lamp pairs at the dead ends of the long alleys and of the cross street. */
    private static void deadEnds(BuildContext c) {
        for (int cx : NS_ALLEY_X) {
            for (int zEnd : new int[]{-54, 54}) {
                Props.stoneLamp(c, cx - 1, 1, zEnd);
                Props.stoneLamp(c, cx + 1, 1, zEnd);
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            Props.stoneLamp(c, side * 64, 1, 2);
            Props.stoneLamp(c, side * 64, 1, 4);
        }
    }
}
