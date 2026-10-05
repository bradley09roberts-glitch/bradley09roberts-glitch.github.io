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
        floodlightGrid(c);
    }

    /**
     * Invisible safety lighting: level-15 light blocks (no collision, like the waiting room prefab uses) on the
     * lattice (3,4) / (-4,3) shifted to z+12, which puts every cell of the court's bounding box within taxicab
     * distance 3 of a source, i.e. block light >= 12 on the whole court even where the sky light would be missing.
     * The open sky already gives 15 under the dimension's noon sun; the towers and lamps add the visible fixtures.
     */
    static void floodlightGrid(BuildContext c) {
        for (int i = -20; i <= 20; i++) {
            for (int j = -20; j <= 20; j++) {
                int x = 3 * i - 4 * j, z = 4 * i + 3 * j + 12;
                if (x >= -HW - 4 && x <= HW + 4 && z >= COURT_TOP - 4 && z <= ZQ + 4) {
                    c.set(x, 1, z, "minecraft:light[level=15]");
                }
            }
        }
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
