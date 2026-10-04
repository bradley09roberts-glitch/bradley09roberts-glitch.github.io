package com.squidgame.build.arena.tug;

import com.squidgame.build.BuildContext;

import java.util.Random;

/** Dressing of the hall floor ring (y = 0) far below the galleries: rim fence, light masts, crates, barrels, forklifts. */
final class RingProps {
    private RingProps() {
    }

    static void build(BuildContext c) {
        rimFence(c);
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            masts(c, sgn);
            clutter(c, sgn);
            forklift(c, -41, sgn * 26, sgn);
        }
    }

    /** Iron-bar fence with steel posts along the pit rim (the pit is open on top by design but nobody falls by accident). */
    private static void rimFence(BuildContext c) {
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            int z = sgn * (Geo.PZ + 1);
            Sym.fill(c, Geo.PX0, 1, z, -1, 2, z, Pal.BARS);
            for (int x = Geo.PX0; x <= -1; x += 6) {
                Sym.fill(c, x, 1, z, x, 3, z, Pal.STEEL);
            }
        }
    }

    /** Tall lamp masts in the ring: steel pole, caged lantern head, hazard-striped foot. */
    private static void masts(BuildContext c, int sgn) {
        for (int x : new int[]{-45, -25, -5}) {
            int z = sgn * 26;
            Sym.fill(c, x, 1, z, x, 9, z, Pal.STEEL);
            for (int y = 1; y <= 2; y++) {
                Sym.set(c, x, y, z, Pal.hazard(y + x));
            }
            Sym.fill(c, x - 1, 9, z - 1, x + 1, 9, z + 1, Pal.STEEL);
            Sym.fill(c, x, 10, z, x, 11, z, Pal.SEA);
            Sym.fill(c, x - 1, 10, z - 1, x + 1, 12, z + 1, Pal.BARS);
            Sym.fill(c, x, 10, z, x, 11, z, Pal.SEA);
            Sym.fill(c, x, 12, z, x, 12, z, Pal.STEEL);
        }
    }

    private static boolean freeBox(BuildContext c, int x1, int z1, int x2, int z2, int h) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                for (int y = 1; y <= h; y++) {
                    if (c.isSolid(x, y, z)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean reserved(int x, int az) {
        // door aprons, gallery columns and wall risers stay clear
        if (az >= 31 && ((x >= -34 && x <= -17) || (x >= 16 && x <= 33))) {
            return true;
        }
        for (int xc : new int[]{-55, -35, -15, 14, 34, 54}) {
            if (x >= xc - 1 && x <= xc + 2 && az >= 27) {
                return true;
            }
        }
        return Math.abs(x) <= 1;
    }

    private static void clutter(BuildContext c, int sgn) {
        Random r = new Random(9100 + sgn);
        int placed = 0;
        for (int i = 0; i < 300 && placed < 24; i++) {
            int x = r.nextInt(112) - 56;
            int az = 24 + r.nextInt(10);
            int z = sgn * az;
            if (reserved(x, az) || reserved(x + 2, az) || reserved(x, az + 2)) {
                continue;
            }
            int x2 = x + 1, z2 = z + sgn;
            int lo = Math.min(z, z2), hi = Math.max(z, z2);
            if (!freeBox(c, x - 1, lo - 1, x2 + 1, hi + 1, 3)) {
                continue;
            }
            switch (r.nextInt(3)) {
                case 0 -> {                              // crate stack on a pallet
                    c.fill(x, 1, lo, x2, 1, hi, "minecraft:dark_oak_slab[type=bottom]");
                    c.fill(x, 2, lo, x2, 3, hi, "minecraft:spruce_planks");
                    c.fill(x, 4, lo, x2, 4, hi, r.nextBoolean() ? "minecraft:spruce_planks" : "minecraft:dark_oak_planks");
                    c.set(x, 3, lo, Pal.IRON);
                    c.set(x2, 2, hi, Pal.IRON);
                }
                case 1 -> {                              // barrels
                    c.set(x, 1, lo, "minecraft:barrel[facing=up]");
                    c.set(x2, 1, lo, "minecraft:barrel[facing=up]");
                    c.set(x, 1, hi, "minecraft:barrel[facing=up]");
                    c.set(x2, 1, hi, "minecraft:barrel[facing=up]");
                    c.set(x, 2, lo, "minecraft:barrel[facing=up]");
                }
                default -> {                             // hazard-painted steel container
                    c.fill(x, 1, lo, x2 + 1, 3, hi, Pal.IRON);
                    for (int xx = x; xx <= x2 + 1; xx++) {
                        c.set(xx, 2, lo, Pal.hazard(xx));
                        c.set(xx, 2, hi, Pal.hazard(xx + 2));
                    }
                }
            }
            placed++;
        }
    }

    /** A small forklift (yellow body, black cab frame, steel forks) facing the pit. */
    private static void forklift(BuildContext c, int x, int z, int sgn) {
        int dz = -sgn;                      // forks point toward the pit
        for (int k = 0; k < 3; k++) {
            for (int xx = x; xx <= x + 1; xx++) {
                c.set(xx, 1, z + sgn * k, Pal.YELLOW);
                c.set(xx, 2, z + sgn * k, k == 1 ? Pal.YELLOW : Pal.BLACK);
            }
        }
        c.fill(x, 3, z + sgn * 2, x + 1, 3, z + sgn * 2, Pal.BLACK);
        c.set(x, 3, z + sgn, Pal.BARS);
        c.set(x + 1, 3, z + sgn, Pal.BARS);
        c.set(x, 4, z + sgn, Pal.BARS);
        c.set(x + 1, 4, z + sgn, Pal.BARS);
        c.fill(x, 5, z + sgn, x + 1, 5, z + sgn * 2, Pal.BLACK);
        for (int k = 1; k <= 2; k++) {
            c.fill(x, 1, z + dz * k, x, 1, z + dz * k, Pal.STEEL);
            c.fill(x + 1, 1, z + dz * k, x + 1, 1, z + dz * k, Pal.STEEL);
        }
        c.fill(x, 2, z + dz, x + 1, 4, z + dz, Pal.BARS);                // mast
    }
}
