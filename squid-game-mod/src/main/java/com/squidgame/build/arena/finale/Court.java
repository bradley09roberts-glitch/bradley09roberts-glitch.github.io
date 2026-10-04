package com.squidgame.build.arena.finale;

import com.squidgame.build.BuildContext;

import java.util.List;

import static com.squidgame.build.arena.finale.Layout.*;

/** Paints the squid on the sand (white concrete lines, golden target in the head) and emits the court markers/regions. */
final class Court {
    private Court() {
    }

    static final String LINE = "minecraft:white_concrete";
    static final String GOLD_RING = "minecraft:yellow_concrete";
    static final String GOLD_PAD = "minecraft:gold_block";

    static void paint(BuildContext c) {
        for (int[] cell : CourtGeometry.lineCells()) {
            c.set(cell[0], 0, cell[1], LINE);
        }
        // the goal: a golden ring (radius 3) and a small 3x3 gold pad in the middle of the head circle
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= 3.25 && d > 2.25) {
                    c.set(dx, 0, ZC + dz, GOLD_RING);
                }
            }
        }
        c.fill(-1, 0, ZC - 1, 1, 0, ZC + 1, GOLD_PAD);
    }

    static void markers(BuildContext c) {
        c.marker("final.circle", 0.5, 1.0, ZC + 0.5, 0f, "r=" + R);
        c.marker("final.triangle", 0.5, 1.0, ZC + 16.5, 0f, "base=" + (2 * HW) + ",height=" + (ZB - ZC - R + 1));
        c.marker("final.neck", 0.5, 1.0, (ZB + ZS) / 2 + 0.5, 0f, "w=3");
        c.marker("final.attacker_spawn", 0.5, 1.0, ZQ - 2.5, 180f);
        c.marker("final.defender_spawn", 0.5, 1.0, ZC + 14.5, 0f);
        List<double[]> poly = CourtGeometry.OUTLINE;
        for (int i = 0; i < poly.size(); i++) {
            c.marker("final.boundary", poly.get(i)[0], 1.0, poly.get(i)[1], 0f, "i=" + i + ",stand=0");
        }
        c.region("final.court", -HW, 0, COURT_TOP, HW, 12, ZQ);
        c.region("final.attack_zone", -HW, 0, ZS, HW, 12, ZQ);
        c.region("final.defence_zone", -HW, 0, COURT_TOP, HW, 12, ZB);
    }
}
