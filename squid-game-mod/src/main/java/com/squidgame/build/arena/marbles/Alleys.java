package com.squidgame.build.arena.marbles;

import com.squidgame.build.BuildContext;

import java.util.List;

/** Street life in the alleys: ground lanterns at every gate, laundry lines, planters and benches at the junctions. */
final class Alleys {
    private Alleys() {
    }

    static void build(BuildContext c, List<Layout.Slot> slots) {
        gateLamps(c, slots);
        laundry(c, slots);
        junctions(c);
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

    /** Clothes lines across the two long N-S alleys between opposite gate roofs (y = 7, hanging cloths below). */
    private static void laundry(BuildContext c, List<Layout.Slot> slots) {
        U.Rnd r = new U.Rnd(4242);
        for (Layout.Slot s : slots) {
            if (!s.zone.equals("CW1") && !s.zone.equals("CE2")) {
                continue;
            }
            // CW1 mouth faces east (+x) onto W1, CE2 mouth faces west onto E1 -> chain from this gate ridge across the alley
            if (r.chance(0.55)) {
                int[] a = s.block(1, -1);
                int dir = s.zone.equals("CW1") ? 1 : -1;
                int x1 = a[0] + dir, x2 = a[0] + 3 * dir;
                int lo = Math.min(x1, x2), hi = Math.max(x1, x2);
                c.fill(lo, 7, a[1], hi, 7, a[1], Mat.CHAIN_X);
                for (int x = lo; x <= hi; x++) {
                    if (r.chance(0.7)) {
                        String col = Props.randomColor(r);
                        c.set(x, 6, a[1], Mat.wool(col));
                        if (r.chance(0.5)) {
                            c.set(x, 5, a[1], Mat.carpet(col));
                        }
                    }
                }
            }
        }
    }

    /** Corner planters and a lamp at the four crossings of the cross street with the N-S alleys. */
    private static void junctions(BuildContext c) {
        for (int cx : new int[]{-47, -25, 25, 47}) {
            for (int z : new int[]{1, 5}) {
                for (int dx : new int[]{-2, 2}) {
                    Props.planter(c, cx + dx, 1, z, ((cx + dx + z) & 1) == 0);
                }
            }
        }
    }
}
