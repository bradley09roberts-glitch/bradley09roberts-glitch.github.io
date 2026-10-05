package com.squidgame.build.arena.hub.control;

import com.squidgame.build.BuildContext;

import java.util.HashSet;
import java.util.Set;

/**
 * Two curved operator console desks round the dais (radius 14 and 19 about the east wall): black desks with glowing
 * pink front blocks, animated monitors facing the operators, chairs behind each desk, cable looms from the ceiling.
 */
final class Consoles {
    private Consoles() {
    }

    static void build(BuildContext c) {
        arc(c, 14.0, 36);
        arc(c, 19.0, 33);
        // cable looms hanging from the ceiling to the desks' backs
        for (double[] p : new double[][]{{70, -9}, {70, 9}, {65, -13}, {65, 13}}) {
            int x = (int) p[0], z = (int) p[1];
            for (int dz = 0; dz <= 1; dz++) {
                c.fill(x, 17, z + dz, x, 32, z + dz, Pal.chain("y"));
            }
        }
    }

    private static void arc(BuildContext c, double radius, double maxDeg) {
        Set<Long> desk = new HashSet<>();
        int i = 0;
        int last = Integer.MIN_VALUE;
        for (double a = -maxDeg; a <= maxDeg + 1e-9; a += 0.35) {
            double rad = Math.toRadians(a);
            int x = (int) Math.round(84 - radius * Math.cos(rad));
            int z = (int) Math.round(radius * Math.sin(rad));
            long key = ((long) x << 32) | (z & 0xffffffffL);
            if (!desk.add(key)) {
                continue;
            }
            boolean glow = (i % 3) == 0;
            c.set(x, 12, z, glow ? Pal.LIGHT_PINK : Pal.PBS);
            if (!glow && (i % 2) == 1) {
                c.set(x, 13, z, Pal.monitor("west"));
            }
            i++;
        }
        // chairs one and a half blocks behind the desk (west side), every second desk cell
        Set<Long> chairs = new HashSet<>();
        int j = 0;
        for (double a = -maxDeg; a <= maxDeg + 1e-9; a += 0.35) {
            double rad = Math.toRadians(a);
            int x = (int) Math.round(84 - (radius + 1.7) * Math.cos(rad));
            int z = (int) Math.round((radius + 1.7) * Math.sin(rad));
            long key = ((long) x << 32) | (z & 0xffffffffL);
            if (!chairs.add(key)) {
                continue;
            }
            if ((j++ % 2) == 0 && !desk.contains(key)) {
                c.set(x, 12, z, Pal.stairs("minecraft:polished_blackstone_stairs", "west", false));
            }
        }
    }
}
