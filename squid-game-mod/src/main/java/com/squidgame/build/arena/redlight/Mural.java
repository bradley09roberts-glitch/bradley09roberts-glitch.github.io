package com.squidgame.build.arena.redlight;

import java.util.Random;

/**
 * The painted sky backdrop, computed as one picture on a closed loop around the four inner wall faces (loop
 * coordinate {@code p} runs clockwise from above: near wall west-to-east, east wall north-to-south, far wall
 * east-to-west, west wall south-to-north), so clouds, hills and skyline continue seamlessly around the corners.
 *
 * <p>Layers, back to front: sky gradient (white haze at the horizon, deep blue overhead, ordered dithering plus
 * brush-stroke noise), puffy clouds (bigger ones higher), pale distant buildings, hazy far hills, two rows of trees
 * and a grass strip at the foot of the wall.
 */
public final class Mural {
    public static final int P = Layout.PERIMETER;
    private static final int H = Layout.WALL_TOP + 2;

    // sky palette (horizon -> zenith)
    private static final String WHITE = "minecraft:white_concrete";
    private static final String SKY1 = "minecraft:light_blue_wool";
    private static final String SKY2 = "minecraft:light_blue_concrete";
    private static final String SKY3 = "minecraft:blue_concrete";
    private static final String CYAN = "minecraft:cyan_concrete";

    private final String[][] px = new String[P][H];
    private final boolean[][] cloud = new boolean[P][H];

    public Mural() {
        paintSky();
        makeClouds();
        paintClouds();
        paintBuildings();
        paintHills();
        paintTrees(new Random(7001), 0.80, true);
        paintTrees(new Random(7002), 1.00, false);
        paintGrass();
    }

    /** Block at loop position p and height y (y in [MURAL_Y0, MURAL_Y1]). */
    public String at(int p, int y) {
        return px[Math.floorMod(p, P)][y];
    }

    private static double clamp01(double v) {
        return Noise.clamp01(v);
    }

    private static int wrap(int p) {
        return Math.floorMod(p, P);
    }

    // ------------------------------------------------------------------ sky

    private void paintSky() {
        for (int p = 0; p < P; p++) {
            for (int y = Layout.MURAL_Y0; y <= Layout.MURAL_Y1; y++) {
                px[p][y] = skyBlock(p, y);
            }
        }
    }

    private static String skyBlock(int p, int y) {
        double s = (y - 8) / 32.0;
        s += 0.075 * (Noise.loop2(11, p, y, 34, 7, P) - 0.5) * 2;      // slow brush strokes
        s += 0.03 * (Noise.loop2(12, p, y, 9, 2.5, P) - 0.5) * 2;      // small ripples
        s = clamp01(s);
        // piecewise levels: 0 white, 1 pale (light blue wool), 2 light blue, 3 deep blue
        double pos;
        if (s < 0.30) {
            pos = s / 0.30 * 1.0;
        } else if (s < 0.62) {
            pos = 1.0 + (s - 0.30) / 0.32;
        } else {
            pos = 2.0 + (s - 0.62) / 0.38;
        }
        // ease so each band keeps a recognisable core colour
        int base = (int) Math.floor(pos);
        double f = pos - base;
        f = Noise.smooth(f);
        double th = 0.62 * Noise.bayer4(p, y) + 0.38 * Noise.rand(13, p, 0, y);
        int lv = f > th ? base + 1 : base;
        lv = Math.min(3, lv);
        switch (lv) {
            case 0:
                return WHITE;
            case 1:
                return SKY1;
            case 2:
                // a little turquoise in the middle band, like a real painted sky
                return (Noise.rand(14, p, 0, y) < 0.10 && s > 0.2 && s < 0.7) ? CYAN : SKY2;
            default:
                return SKY3;
        }
    }

    // ------------------------------------------------------------------ clouds

    private void makeClouds() {
        Random r = new Random(4242);
        double p = r.nextDouble() * 18;
        int last = -100;
        while (p < P) {
            int y0 = 15 + r.nextInt(22);
            // bigger clouds higher up
            double hf = (y0 - 15) / 21.0;
            double w = 8 + hf * 14 + r.nextDouble() * (7 + 12 * hf);
            double hgt = w * (0.30 + r.nextDouble() * 0.14);
            hgt = Math.min(hgt, 41 - y0 - 1);
            if (hgt >= 3 && (y0 + hgt) <= Layout.MURAL_Y1) {
                cloudShape(r, p, y0, w, hgt);
            }
            // sometimes a smaller companion at another height
            if (r.nextDouble() < 0.35) {
                int y1 = 14 + r.nextInt(24);
                double w1 = 5 + r.nextDouble() * 8;
                double h1 = Math.min(w1 * 0.4, 41 - y1 - 1);
                if (h1 >= 2.5) {
                    cloudShape(r, p + w * 0.6 + 4 + r.nextInt(8), y1, w1, h1);
                }
            }
            p += w + 9 + r.nextDouble() * 26;
        }
    }

    private void cloudShape(Random r, double pc, int y0, double w, double hgt) {
        int puffs = 3 + (int) (w / 4);
        for (int i = 0; i < puffs; i++) {
            double t = (i + 0.5) / puffs;
            double arch = Math.sin(Math.PI * t);
            double rad = hgt * (0.40 + 0.50 * arch) * (0.82 + 0.35 * r.nextDouble());
            double cu = pc + (t - 0.5) * w + (r.nextDouble() - 0.5) * 1.5;
            double cv = y0 + rad * 0.70 + hgt * 0.18 * arch * r.nextDouble();
            int pu0 = (int) Math.floor(cu - rad - 2), pu1 = (int) Math.ceil(cu + rad + 2);
            int v0 = (int) Math.floor(cv - rad - 2), v1 = (int) Math.ceil(cv + rad + 2);
            for (int pu = pu0; pu <= pu1; pu++) {
                for (int y = v0; y <= v1; y++) {
                    if (y < y0 - 1 || y > Layout.MURAL_Y1) {
                        continue;
                    }
                    double dp = pu - cu, dv = (y - cv) * 1.12;
                    double wob = 1.0 + 0.30 * (Noise.value2(21, pu * 0.9, y * 0.9) - 0.5);
                    if (dp * dp + dv * dv <= rad * rad * wob) {
                        // flat-ish underside with a little noise
                        double floorY = y0 + 0.9 * Noise.value2(22, pu * 0.5, 3.3);
                        if (y >= floorY) {
                            cloud[wrap(pu)][y] = true;
                        }
                    }
                }
            }
        }
    }

    private void paintClouds() {
        for (int p = 0; p < P; p++) {
            for (int y = Layout.MURAL_Y0; y <= Layout.MURAL_Y1; y++) {
                if (!cloud[p][y]) {
                    continue;
                }
                boolean top = !cloud[p][y + 1];
                boolean under = !cloud[p][y - 1];
                boolean under2 = !under && !cloud[p][y - 2];
                boolean left = !cloud[wrap(p - 1)][y];
                boolean right = !cloud[wrap(p + 1)][y];
                double rr = Noise.rand(31, p, 0, y);
                String b;
                if (under) {
                    b = rr < 0.45 ? "minecraft:clay" : (rr < 0.85 ? WHITE : "minecraft:calcite");
                } else if (under2) {
                    b = rr < 0.14 ? "minecraft:clay" : (rr < 0.70 ? WHITE : "minecraft:calcite");
                } else if (top) {
                    b = rr < 0.5 ? "minecraft:calcite" : (rr < 0.8 ? "minecraft:quartz_block" : "minecraft:white_wool");
                } else if (left || right) {
                    b = rr < 0.55 ? WHITE : "minecraft:calcite";
                } else {
                    b = rr < 0.45 ? "minecraft:calcite" : (rr < 0.80 ? WHITE : (rr < 0.92 ? "minecraft:quartz_block" : "minecraft:white_wool"));
                }
                px[p][y] = b;
            }
        }
    }

    // ------------------------------------------------------------------ landscape

    private void paintBuildings() {
        Random r = new Random(9090);
        int p = r.nextInt(6);
        while (p < P) {
            int w = 3 + r.nextInt(6);
            double q = r.nextDouble();
            int h = 4 + (int) (q * q * 12);
            boolean tower = r.nextDouble() < 0.14;
            if (tower) {
                h += 4;
                w = Math.max(3, w - 1);
            }
            boolean gap = r.nextDouble() < 0.30;
            if (!gap) {
                String body = r.nextDouble() < 0.5 ? "minecraft:clay" : "minecraft:light_blue_terracotta";
                String roof = "minecraft:light_gray_concrete";
                int stepWin = 2;
                for (int dp = 0; dp < w; dp++) {
                    for (int y = 1; y <= h; y++) {
                        String b = body;
                        boolean win = (y >= 3) && (y < h) && (y % stepWin == 1) && ((dp % 2) == 1) && dp < w - 1;
                        if (win) {
                            b = "minecraft:packed_ice";
                        }
                        if (y == h) {
                            b = r.nextDouble() < 0.5 ? roof : body;
                        }
                        px[wrap(p + dp)][y] = b;
                    }
                }
                if (tower && w >= 3) {
                    int mid = w / 2;
                    for (int y = h + 1; y <= h + 3; y++) {
                        px[wrap(p + mid)][y] = "minecraft:light_gray_concrete";
                    }
                }
            }
            p += w + (gap ? 3 + r.nextInt(8) : r.nextInt(2));
        }
    }

    private void paintHills() {
        for (int p = 0; p < P; p++) {
            double hh = 4.0 + 5.5 * Noise.loopFbm1(61, p, 46, P, 3);
            int top = (int) Math.round(hh);
            for (int y = 1; y <= top; y++) {
                double rr = Noise.rand(62, p, 0, y);
                String b;
                double rel = (double) y / top;
                if (rel > 0.8) {
                    b = rr < 0.55 ? "minecraft:weathered_copper" : "minecraft:prismarine";
                } else {
                    b = rr < 0.35 ? "minecraft:prismarine" : (rr < 0.85 ? "minecraft:weathered_copper" : "minecraft:lime_terracotta");
                }
                px[p][y] = b;
            }
        }
    }

    /** One row of trees. back = lighter blue-greens, smaller; front = darker greens. */
    private void paintTrees(Random r, double scale, boolean back) {
        int p = r.nextInt(8);
        int seed = back ? 71 : 72;
        while (p < P) {
            boolean cone = r.nextDouble() < (back ? 0.25 : 0.30);
            if (!cone) {
                double rad = (2.3 + r.nextDouble() * 3.2) * scale;
                int trunk = 1 + r.nextInt(3);
                int base = back ? 1 : 1;
                double cy = base + trunk + rad * 0.80;
                for (int dp = (int) -Math.ceil(rad) - 1; dp <= (int) Math.ceil(rad) + 1; dp++) {
                    for (int y = base; y <= (int) Math.ceil(cy + rad) + 1; y++) {
                        if (y > 24) {
                            continue;
                        }
                        int pp = wrap(p + dp);
                        double dd = Math.sqrt(dp * dp + (y - cy) * (y - cy) * 1.1);
                        double wob = 1.0 + 0.28 * (Noise.value2(seed, (p + dp) * 0.8, y * 0.8) - 0.5);
                        boolean leaf = dd <= rad * wob;
                        boolean trunkPix = dp == 0 && y < cy - rad * 0.2;
                        if (leaf) {
                            px[pp][y] = treeTone(pp, y, (y - (cy - rad)) / (2 * rad), back);
                        } else if (trunkPix) {
                            px[pp][y] = back ? "minecraft:dark_prismarine" : "minecraft:brown_concrete";
                        }
                    }
                }
                p += (int) (rad * 1.5 + 1 + r.nextInt(4));
            } else {
                int h = (int) ((7 + r.nextInt(7)) * scale);
                double half = 1.4 + r.nextDouble() * 1.4;
                for (int y = 1; y <= h; y++) {
                    double t = (double) (y - 1) / h;
                    double hw = (y <= 1) ? 0.0 : (1.0 - t) * half + 0.35;
                    int ih = (int) Math.round(hw);
                    for (int dp = -ih; dp <= ih; dp++) {
                        int pp = wrap(p + dp);
                        px[pp][y] = treeTone(pp, y, t, back);
                    }
                }
                p += (int) (half * 2 + 1 + r.nextInt(4));
            }
        }
    }

    private static String treeTone(int p, int y, double rel, boolean back) {
        double rr = Noise.rand(81, p, 0, y);
        if (back) {
            if (rel > 0.65) {
                return rr < 0.45 ? "minecraft:weathered_copper" : (rr < 0.85 ? "minecraft:moss_block" : "minecraft:prismarine");
            }
            return rr < 0.40 ? "minecraft:moss_block" : (rr < 0.80 ? "minecraft:lime_terracotta" : "minecraft:dark_prismarine");
        }
        if (rel > 0.7) {
            return rr < 0.50 ? "minecraft:moss_block" : (rr < 0.80 ? "minecraft:green_concrete" : "minecraft:lime_concrete");
        }
        if (rel > 0.35) {
            return rr < 0.55 ? "minecraft:green_concrete" : (rr < 0.88 ? "minecraft:moss_block" : "minecraft:dark_prismarine");
        }
        return rr < 0.65 ? "minecraft:green_concrete" : "minecraft:dark_prismarine";
    }

    private void paintGrass() {
        for (int p = 0; p < P; p++) {
            double rr = Noise.rand(91, p, 0, 0);
            int top = 1 + (rr < 0.4 ? 1 : 0) + (Noise.rand(92, p / 3, 0, 1) < 0.3 ? 1 : 0);
            for (int y = 1; y <= top; y++) {
                double q = Noise.rand(93, p, 0, y);
                px[p][y] = q < 0.55 ? "minecraft:green_concrete" : (q < 0.85 ? "minecraft:moss_block" : "minecraft:lime_concrete");
            }
        }
    }
}
