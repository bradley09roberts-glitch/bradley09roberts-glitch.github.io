package com.squidgame.build.arena.finale;

import java.util.Random;

/**
 * The painted sunset: a continuous panorama indexed by (s, y) where s runs clockwise around the enclosure
 * (see {@link Backdrop}) and y is the height above the ground. Layers, back to front: dithered orange-pink-purple
 * gradient with a warm glow, a low sun, soft clouds (warm underside / cool top), faint stars, birds, a hazy far
 * skyline and a black near skyline of old houses and apartment blocks with a few lit windows.
 */
final class Mural {
    final int perimeter;
    final int height;

    private final String[][] far;
    private final String[][] near;
    private final double sunS;
    private final double sunY = 20;
    private final double[][] clouds; // s, y, halfLength, halfThickness, seed
    private final double[][] birds;

    /** Sky ramp, bottom (horizon) to top. */
    private static final String[] RAMP = {
            "minecraft:yellow_concrete", "minecraft:orange_wool", "minecraft:orange_concrete", "minecraft:pink_concrete",
            "minecraft:magenta_concrete", "minecraft:purple_wool", "minecraft:purple_concrete"};
    /** Height (above ground) at which each ramp colour is at full strength. */
    private static final double[] STOP = {6, 13, 19, 26, 33, 39, 46};

    private static final String FAR = "minecraft:purple_terracotta";
    private static final String FAR2 = "minecraft:blue_terracotta";
    private static final String NEAR = "minecraft:black_concrete";
    private static final String NEAR2 = "minecraft:gray_concrete";

    Mural(int perimeter, int height, double sunS) {
        this.perimeter = perimeter;
        this.height = height;
        this.sunS = sunS;
        this.far = new String[perimeter][height + 2];
        this.near = new String[perimeter][height + 2];
        Random r = new Random(60061L);
        skyline(r, far, 9, 22, 5, 14, false);
        skyline(r, near, 6, 19, 4, 12, true);
        this.clouds = makeClouds(r);
        this.birds = makeBirds(r);
    }

    // ------------------------------------------------------------------ skyline

    private void skyline(Random r, String[][] layer, int minH, int maxH, int minW, int maxW, boolean nearLayer) {
        int s = -r.nextInt(6);
        while (s < perimeter) {
            int w = minW + r.nextInt(maxW - minW + 1);
            int h = minH + r.nextInt(maxH - minH + 1);
            double sd = wrapDist(s + w / 2.0, sunS);
            if (sd < 22) {
                h = Math.min(h, (int) (7 + sd / 3.0));
            }
            double kind = r.nextDouble();
            if (kind < 0.46) {
                apartment(r, layer, s, w, h, nearLayer);
            } else if (kind < 0.86) {
                house(r, layer, s, w, Math.max(5, Math.min(h, 13)), nearLayer);
            } else {
                tower(r, layer, s, Math.max(3, w / 3), h + 4, nearLayer);
            }
            s += w + (r.nextInt(5) == 0 ? 1 + r.nextInt(3) : 0);
        }
    }

    private void put(String[][] layer, int s, int y, String b) {
        int ss = ((s % perimeter) + perimeter) % perimeter;
        if (y >= 0 && y < layer[ss].length) {
            layer[ss][y] = b;
        }
    }

    private static boolean lit(Random r, double chance) {
        return r.nextDouble() < chance;
    }

    private void apartment(Random r, String[][] layer, int s0, int w, int h, boolean nearLayer) {
        String wall = nearLayer ? (r.nextInt(4) == 0 ? NEAR2 : NEAR) : (r.nextBoolean() ? FAR : FAR2);
        for (int u = 0; u < w; u++) {
            for (int y = 0; y < h; y++) {
                put(layer, s0 + u, y, wall);
            }
        }
        if (nearLayer) {
            // windows: 1 wide x 2 tall on a 3 x 4 grid, a few lit warm
            for (int u = 1; u < w - 1; u += 3) {
                for (int y = 2; y < h - 2; y += 4) {
                    boolean on = lit(r, 0.22);
                    String win = on ? (r.nextInt(5) == 0 ? "minecraft:shroomlight" : (r.nextInt(3) == 0 ? "minecraft:orange_concrete" : "minecraft:yellow_concrete")) : "minecraft:gray_concrete";
                    put(layer, s0 + u, y, win);
                    put(layer, s0 + u, y + 1, win);
                }
            }
        }
        // rooftop box / water tank and antenna
        if (w >= 6 && r.nextBoolean()) {
            int bw = 2 + r.nextInt(2);
            int bu = 1 + r.nextInt(w - bw - 1);
            for (int u = 0; u < bw; u++) {
                put(layer, s0 + bu + u, h, wall);
                put(layer, s0 + bu + u, h + 1, wall);
            }
        }
        if (r.nextInt(3) == 0) {
            int au = 1 + r.nextInt(Math.max(1, w - 2));
            int ah = 3 + r.nextInt(4);
            for (int y = h; y < h + ah; y++) {
                put(layer, s0 + au, y, wall);
            }
        }
    }

    private void house(Random r, String[][] layer, int s0, int w, int hb, boolean nearLayer) {
        String wall = nearLayer ? NEAR : (r.nextBoolean() ? FAR : FAR2);
        for (int u = 0; u < w; u++) {
            for (int y = 0; y < hb; y++) {
                put(layer, s0 + u, y, wall);
            }
        }
        int g = Math.max(2, (int) Math.round(w * 0.45));
        for (int v = 0; v < g; v++) {
            double half = (w / 2.0 + 0.5) * (1.0 - v / (double) g);
            for (int u = 0; u < w; u++) {
                if (Math.abs(u + 0.5 - w / 2.0) <= half) {
                    put(layer, s0 + u, hb + v, wall);
                }
            }
        }
        if (w >= 6) {
            int cu = (int) Math.round(w * (r.nextBoolean() ? 0.25 : 0.72));
            for (int y = hb + 1; y < hb + g + 2; y++) {
                put(layer, s0 + cu, y, wall);
                put(layer, s0 + cu + 1, y, wall);
            }
        }
        if (nearLayer) {
            for (int u = 1; u < w - 1; u += 3) {
                int y = 2;
                if (hb >= 6 && lit(r, 0.5)) {
                    y = 3;
                }
                boolean on = lit(r, 0.3);
                String win = on ? (r.nextInt(3) == 0 ? "minecraft:orange_concrete" : "minecraft:yellow_concrete") : "minecraft:gray_concrete";
                put(layer, s0 + u, y, win);
                put(layer, s0 + u, y + 1, win);
            }
        }
    }

    private void tower(Random r, String[][] layer, int s0, int w, int h, boolean nearLayer) {
        String wall = nearLayer ? NEAR : FAR;
        for (int u = 0; u < w; u++) {
            for (int y = 0; y < h; y++) {
                put(layer, s0 + u, y, wall);
            }
        }
        int mast = r.nextInt(3) + 5;
        for (int y = h; y < h + mast; y++) {
            put(layer, s0 + w / 2, y, wall);
        }
        if (nearLayer) {
            put(layer, s0 + w / 2, h + mast, "minecraft:shroomlight");
        }
    }

    // ------------------------------------------------------------------ clouds / birds

    private double[][] makeClouds(Random r) {
        int n = perimeter / 15;
        double[][] c = new double[n][5];
        for (int i = 0; i < n; i++) {
            c[i][0] = r.nextDouble() * perimeter;
            c[i][1] = 14 + r.nextDouble() * 26;
            c[i][2] = 8 + r.nextDouble() * 20;
            c[i][3] = 1.3 + r.nextDouble() * 2.3;
            c[i][4] = r.nextInt(1000);
        }
        return c;
    }

    private double[][] makeBirds(Random r) {
        double[][] b = new double[perimeter / 22][2];
        for (int i = 0; i < b.length; i++) {
            b[i][0] = r.nextDouble() * perimeter;
            b[i][1] = 22 + r.nextDouble() * 16;
        }
        return b;
    }

    // ------------------------------------------------------------------ sampling

    private double wrapDist(double a, double b) {
        double d = Math.abs(a - b) % perimeter;
        return Math.min(d, perimeter - d);
    }

    /** Fractional ramp index of the plain sky at (s, y), with glow around the sun and a gentle wobble. */
    private double rampIndex(double s, double y) {
        double yy = y + 3.2 * (Noise.fbm(s * 0.035, y * 0.06, 77, 2) - 0.5);
        double f;
        if (yy <= STOP[0]) {
            f = 0;
        } else if (yy >= STOP[STOP.length - 1]) {
            f = STOP.length - 1;
        } else {
            int i = 0;
            while (yy > STOP[i + 1]) {
                i++;
            }
            f = i + (yy - STOP[i]) / (STOP[i + 1] - STOP[i]);
        }
        double dx = wrapDist(s, sunS), dy = y - sunY;
        double g = Math.exp(-(dx * dx + dy * dy * 1.6) / 520.0);
        return f - 2.6 * g;
    }

    private String ramp(double f, int s, int y) {
        f = Math.max(0, Math.min(RAMP.length - 1, f));
        int i = (int) Math.floor(f);
        double frac = f - i;
        if (i >= RAMP.length - 1) {
            return RAMP[RAMP.length - 1];
        }
        return frac > Noise.bayer4(s, y) ? RAMP[i + 1] : RAMP[i];
    }

    /** Block of the mural at perimeter position s (any integer, wraps) and height y (1..height). */
    String block(int s, int y) {
        int ss = ((s % perimeter) + perimeter) % perimeter;
        String n = ss < perimeter && y < near[ss].length ? near[ss][y] : null;
        if (n != null) {
            return n;
        }
        String f = y < far[ss].length ? far[ss][y] : null;
        if (f != null) {
            return f;
        }
        // sun disc (partly hidden by the skyline in front of it)
        double dx = wrapDist(ss, sunS), dy = y - sunY;
        double rr = Math.sqrt(dx * dx + dy * dy);
        if (rr <= 10.0) {
            boolean slice = dy < -1 && ((int) Math.floor(-dy) % 3 == 0) && rr > 3;
            if (!slice) {
                return rr > 8.4 ? "minecraft:orange_wool" : (rr < 3.4 ? "minecraft:ochre_froglight" : "minecraft:yellow_wool");
            }
        }
        double base = rampIndex(ss, y);
        // clouds
        for (double[] c : clouds) {
            double ds = wrapDist(ss, c[0]);
            if (ds > c[2] + 2) {
                continue;
            }
            double ddy = y - c[1];
            double nz = Noise.value(ss * 0.18, y * 0.5, (int) c[4]) - 0.5;
            double m = 1.0 - Math.sqrt((ds / c[2]) * (ds / c[2]) + (ddy / c[3]) * (ddy / c[3])) + 0.55 * nz;
            if (m > 0.0) {
                double shift = ddy > 0.5 ? 1.2 : (ddy < -0.6 ? -2.4 : -0.9);
                if (m < 0.18) {
                    shift *= 0.5;
                }
                return ramp(base + shift, ss, y);
            }
        }
        // birds: little flat V shapes
        for (double[] b : birds) {
            int bs = (int) Math.round(b[0]), by = (int) Math.round(b[1]);
            int du = Math.floorMod(ss - bs + perimeter / 2, perimeter) - perimeter / 2;
            if (((du == -2 || du == 2) && y == by + 1) || ((du >= -1 && du <= 1) && y == by)) {
                return "minecraft:black_concrete";
            }
        }
        // faint stars high up
        if (y > 34 && Noise.hash01(ss, y, 91) < 0.012 * (y - 34) / 10.0) {
            return "minecraft:white_concrete";
        }
        return ramp(base, ss, y);
    }
}
