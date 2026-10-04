package com.squidgame.build.arena.marbles;

import com.squidgame.build.BuildContext;

/**
 * One pair spot ("court"): a walled yard entered from an alley through a gate, with the throw line, the bullseye and a
 * house at the far end. Court frame: x lateral (interior -3..3, walls at +-4), z from the gate (0) to the house (12+).
 * Partners stand on the coloured pads one block behind the throw line, three blocks apart (x = -1.0 and 2.0 around the
 * lane axis x = 0.5), the bullseye is seven blocks beyond the line.
 */
final class Court {
    private Court() {
    }

    static final String[] FLOOR_NAMES = {"cobble", "dirt", "brick"};

    static void build(BuildContext c, Layout.Slot s, House.Spec house) {
        int k = s.k;
        U.Rnd r = new U.Rnd(7000 + k * 13L);
        int style = (k * 7 + (k >> 2)) % 3;
        floor(c, s, style);
        walls(c);
        gate(c, String.valueOf(k + 1));
        lamps(c);
        paint(c);
        table(c, k);
        props(c, r, k);
        // the house at the far end
        house.number = k + 1;
        c.at(0, 0, Layout.COURT_LEN, 0, () -> House.build(c, house));
        markers(c, k);
    }

    // ------------------------------------------------------------------ floor

    private static void floor(BuildContext c, Layout.Slot s, int style) {
        String[] pal;
        double[] w;
        switch (style) {
            case 0 -> {
                pal = new String[]{Mat.COBBLE, Mat.MOSSY_COBBLE, Mat.GRAVEL, Mat.ANDESITE, Mat.STONE, Mat.TUFF};
                w = new double[]{52, 12, 14, 8, 8, 6};
            }
            case 1 -> {
                pal = new String[]{Mat.COARSE, Mat.PATH, Mat.GRAVEL, Mat.COBBLE, Mat.MUD, Mat.DIRT};
                w = new double[]{34, 22, 16, 12, 10, 6};
            }
            default -> {
                pal = new String[]{Mat.SB, Mat.SB_CRACK, Mat.SB_MOSS, Mat.P_ANDESITE, Mat.ANDESITE, Mat.COBBLE};
                w = new double[]{40, 14, 14, 14, 10, 8};
            }
        }
        final String[] fp = pal;
        final double[] fw = w;
        c.pattern(-3, 0, 0, 3, 0, Layout.COURT_LEN - 1, (x, y, z) -> {
            double rr = U.rand(c.worldX(x, z), c.worldZ(x, z), 31 + style);
            if (Math.abs(x) == 3) {
                return rr < 0.2 ? Mat.SB_MOSS : rr < 0.6 ? Mat.SB : Mat.COBBLE;
            }
            return U.pick(rr, fp, fw);
        });
        // a stone-brick apron at the gate and in front of the house
        c.pattern(-2, 0, 0, 2, 0, 0, (x, y, z) -> U.rand(x, z, 3) < 0.3 ? Mat.SB_CRACK : Mat.SB);
    }

    // ------------------------------------------------------------------ walls and gate

    static void walls(BuildContext c) {
        for (int side = -1; side <= 1; side += 2) {
            int x = 4 * side;
            int z1 = -1, z2 = Layout.COURT_LEN - 1;
            c.fill(x, 1, z1, x, 1, z2, Mat.SB);
            c.noise(x, 2, z1, x, 3, z2, new String[]{Mat.PLASTER, Mat.CALCITE, Mat.DIORITE}, new double[]{62, 30, 8});
            // timber beam along the top of the plaster and posts every four blocks
            c.fill(x, 3, z1, x, 3, z2, Mat.log(Mat.LOG_S, "z"));
            for (int z = z1; z <= z2; z += 4) {
                c.fill(x, 2, z, x, 2, z, Mat.log(Mat.LOG_S, "y"));
            }
            // tile coping (one slab wide)
            c.fill(x, 4, 0, x, 4, z2, Mat.slabB(Mat.TILE_SL));
        }
    }

    static void gate(BuildContext c, String label) {
        for (int side = -1; side <= 1; side += 2) {
            int x = 3 * side;
            c.fill(x, 1, -1, x, 2, -1, Mat.SB);
            c.fill(x, 3, -1, x, 5, -1, Mat.log(Mat.LOG_S, "y"));
        }
        // lintel beam
        c.fill(-3, 5, -1, 3, 5, -1, Mat.log(Mat.LOG_S, "x"));
        // small gable roof over the gate: ridge along x
        c.fill(-3, 6, -2, 3, 6, -2, Mat.stair(Mat.TILE_ST, "south"));
        c.fill(-3, 6, -1, 3, 6, -1, Mat.TILE);
        c.fill(-3, 7, -1, 3, 7, -1, Mat.slabB(Mat.TILE_SL));
        c.fill(-3, 6, 0, 3, 6, 0, Mat.stair(Mat.TILE_ST, "north"));
        // hanging lanterns
        c.set(-2, 4, -1, Mat.lantern(true));
        c.set(2, 4, -1, Mat.lantern(true));
        // number plaque on the lintel (faces the alley)
        c.text(0.5, 5.5, -1.03, label, "#FFE8A0", label.length() > 3 ? 1.4f : 2.4f, 180f, false);
    }

    private static void lamps(BuildContext c) {
        Props.stoneLamp(c, -3, 1, 3);
        Props.stoneLamp(c, 3, 1, 6);
        Props.stoneLamp(c, -3, 1, 8);
        Props.stoneLamp(c, 3, 1, 10);
    }

    // ------------------------------------------------------------------ painting: throw line, pads, bullseye

    private static void paint(BuildContext c) {
        // throw line (full width of the lane)
        c.fill(-3, 0, Layout.LINE_ROW, 3, 0, Layout.LINE_ROW, Mat.concrete("white"));
        // standing pads, partner A (left) and B (right)
        c.fill(-2, 0, Layout.AB_ROW, -1, 0, Layout.AB_ROW, Mat.concrete("light_blue"));
        c.fill(1, 0, Layout.AB_ROW, 2, 0, Layout.AB_ROW, Mat.concrete("orange"));
        // bullseye 5x5: gold, red, white, blue, white, red (by squared distance 0,1,2,4,5,8)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int d2 = dx * dx + dz * dz;
                String col = switch (d2) {
                    case 0 -> "yellow";
                    case 1 -> "red";
                    case 2 -> "white";
                    case 4 -> "blue";
                    case 5 -> "white";
                    default -> "red";
                };
                c.set(dx, 0, Layout.TARGET_ROW + dz, Mat.concrete(col));
            }
        }
    }

    private static void table(BuildContext c, int k) {
        c.set(0, 1, Layout.AB_ROW, Mat.slabB("minecraft:spruce_slab"));
        c.set(0, 0, Layout.AB_ROW, Mat.SB);
    }

    private static void props(BuildContext c, U.Rnd r, int k) {
        // small props along the two wall columns (x = +-3) away from the lamps
        int[] zs = {1, 2, 4, 5, 7, 9, 11};
        for (int side = -1; side <= 1; side += 2) {
            for (int z : zs) {
                if (!r.chance(0.18)) {
                    continue;
                }
                int x = 3 * side;
                switch (r.i(6)) {
                    case 0 -> Props.barrel(c, x, 1, z);
                    case 1 -> Props.crate(c, x, 1, z, 1 + r.i(2));
                    case 2 -> Props.pot(c, x, 1, z, r);
                    case 3 -> Props.jar(c, x, 1, z);
                    case 4 -> Props.planter(c, x, 1, z, r.chance(0.5));
                    default -> Props.pot(c, x, 1, z, r);
                }
            }
        }
        if (r.chance(0.4)) {
            Props.laundryX(c, -3, 3, 5, 5, r);
        }
    }

    // ------------------------------------------------------------------ markers and region

    private static void markers(BuildContext c, int k) {
        String data = "k=" + k;
        c.marker("marbles.pair_a", -1.0, 1.0, Layout.AB_ROW + 0.5, -90f, data);
        c.marker("marbles.pair_b", 2.0, 1.0, Layout.AB_ROW + 0.5, 90f, data);
        c.marker("marbles.pair_line", 0.5, 1.0, Layout.LINE_ROW + 0.5, 0f, data);
        c.marker("marbles.pair_target", 0.5, 1.0, Layout.TARGET_ROW + 0.5, 0f, data);
        c.marker("marbles.table", 0.5, 1.5, Layout.AB_ROW + 0.5, 0f, data);
        c.region("marbles.plot", -3, 0, 0, 3, 8, Layout.COURT_LEN - 1);
    }
}
