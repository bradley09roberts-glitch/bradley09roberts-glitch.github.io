package com.squidgame.build.arena.marbles;

import com.squidgame.build.BuildContext;

/** The sealed set boundary: tall perimeter wall with a painted city / hill panorama and the star-sky roof with a moon. */
final class Sky {
    private Sky() {
    }

    static final int WALL_TOP = Layout.ROOF_Y - 1;
    // roof / wall outer extents
    static final int RX0 = Layout.X0 - Layout.WALL, RX1 = Layout.X1 + Layout.WALL;
    static final int RZ0 = Layout.Z0 - Layout.WALL, RZ1 = Layout.Z1 + Layout.WALL;
    // moon centre on the roof
    static final int MOON_X = -6, MOON_Z = -24, MOON_R = 9;

    static void build(BuildContext c) {
        walls(c);
        roof(c);
    }

    // ------------------------------------------------------------------ perimeter wall

    private static void walls(BuildContext c) {
        String core = "minecraft:blue_concrete";
        // solid cores (outer two layers), then the inner mural layer per wall
        c.fill(RX0, -1, RZ0, RX0 + 1, WALL_TOP, RZ1, core);
        c.fill(RX1 - 1, -1, RZ0, RX1, WALL_TOP, RZ1, core);
        c.fill(RX0, -1, RZ0, RX1, WALL_TOP, RZ0 + 1, core);
        c.fill(RX0, -1, RZ1 - 1, RX1, WALL_TOP, RZ1, core);
        // inner mural layers: west (x = X0-1), east, north (z = Z0-1), south
        c.pattern(Layout.X0 - 1, 0, Layout.Z0 - 1, Layout.X0 - 1, WALL_TOP, Layout.Z1 + 1,
                (x, y, z) -> mural(0, z, y));
        c.pattern(Layout.X1 + 1, 0, Layout.Z0 - 1, Layout.X1 + 1, WALL_TOP, Layout.Z1 + 1,
                (x, y, z) -> mural(1, z, y));
        c.pattern(Layout.X0 - 1, 0, Layout.Z0 - 1, Layout.X1 + 1, WALL_TOP, Layout.Z0 - 1,
                (x, y, z) -> mural(2, x, y));
        c.pattern(Layout.X0 - 1, 0, Layout.Z1 + 1, Layout.X1 + 1, WALL_TOP, Layout.Z1 + 1,
                (x, y, z) -> mural(3, x, y));
        // corner posts in the mural layer so the corners are closed
        c.fill(Layout.X0 - 1, 0, Layout.Z0 - 1, Layout.X0 - 1, WALL_TOP, Layout.Z0 - 1, "minecraft:black_concrete");
        c.fill(Layout.X1 + 1, 0, Layout.Z0 - 1, Layout.X1 + 1, WALL_TOP, Layout.Z0 - 1, "minecraft:black_concrete");
        c.fill(Layout.X0 - 1, 0, Layout.Z1 + 1, Layout.X0 - 1, WALL_TOP, Layout.Z1 + 1, "minecraft:black_concrete");
        c.fill(Layout.X1 + 1, 0, Layout.Z1 + 1, Layout.X1 + 1, WALL_TOP, Layout.Z1 + 1, "minecraft:black_concrete");
    }

    private static String cc(String color) {
        return "minecraft:" + color + "_concrete";
    }

    /** One block of the panorama. wall 0..3 = W,E,N,S; u = coordinate along the wall; y = height. */
    static String mural(int wall, int u, int y) {
        int t = u + wall * 211;
        double r = U.rand(u, y, 40 + wall);
        // ---- dusk glow: orange -> red -> magenta -> purple -> blue -> black, dithered across the band borders
        String sky = gradient(y, r, t);
        // ---- far hills: blue-purple silhouettes
        double farH = 13 + 4.5 * Math.sin(t * 0.045 + 1.0) + 3.0 * Math.sin(t * 0.13 + 2.0) + 2.0 * Math.sin(t * 0.31);
        if (y <= farH) {
            sky = y > farH - 2 || r < 0.15 ? cc("purple") : cc("blue");
        }
        // ---- far skyline: dark blue towers without lights, a few antenna tips
        double fs = skyline(t * 0.8 + 77, 3.0, 5, 15, 31);
        if (y <= fs && y > 5) {
            sky = r < 0.7 ? cc("blue") : "minecraft:blue_terracotta";
            if (y == (int) fs && r < 0.2) {
                sky = cc("light_blue");
            }
        }
        // ---- near skyline: black towers with lit windows
        double ns = skyline(t, 5.0, 4, 12, 27);
        if (y <= ns && y > 3) {
            boolean windowCol = Math.floorMod(t, 2) == 0;
            boolean windowRow = y % 3 == 1;
            double on = U.rand(t / 2, y / 3, 52);
            if (windowCol && windowRow && on < 0.5 && y < ns - 1) {
                sky = on < 0.2 ? cc("yellow") : on < 0.36 ? cc("orange") : cc("white");
                if (on < 0.02) {
                    sky = "minecraft:glowstone";
                }
            } else {
                sky = r < 0.82 ? cc("black") : cc("gray");
            }
        }
        // ---- near hills: black with tiny house lights
        double nh = 9 + 3.5 * Math.sin(t * 0.06 + 4.0) + 2.5 * Math.sin(t * 0.17 + 0.4) + 1.5 * Math.sin(t * 0.43);
        if (y <= nh) {
            sky = cc("black");
            if (y >= nh - 3 && r < 0.05) {
                sky = cc("orange");
            }
        }
        // ---- the tower on the hill (north wall), drawn last
        if (wall == 2) {
            String tw = tower(u, y);
            if (tw != null) {
                sky = tw;
            }
        }
        return sky;
    }

    /** Needle tower with an observation bulb and a red tip light, standing on the hill at x = 27. */
    private static String tower(int u, int y) {
        int d = u - 27;
        if (y >= 8 && y <= 12 && Math.abs(d) <= 3 - (y - 8) / 2) {
            return cc("black");
        }
        if (y >= 12 && y <= 40 && d == 0) {
            return y == 40 ? cc("red") : cc("black");
        }
        if (y >= 24 && y <= 29) {
            int w = y == 24 || y == 29 ? 2 : y == 25 || y == 28 ? 3 : 4;
            if (Math.abs(d) <= w) {
                return (y == 26 || y == 27) && Math.abs(d) < w && (u & 1) == 0 ? cc("yellow") : cc("black");
            }
        }
        if (y >= 33 && y <= 34 && Math.abs(d) <= 1) {
            return cc("black");
        }
        return null;
    }

    private static final String[] BANDS = {"orange", "red", "magenta", "purple", "blue", "black"};
    private static final int[] BAND_Y = {7, 12, 17, 23, 31};

    private static String gradient(int y, double r, int t) {
        // soft clouds: shift the sample height a bit with smooth noise so the bands undulate
        double wob = (U.fbm(t * 0.05, y * 0.12, 61, 3) - 0.5) * 7.0;
        double yy = y + wob;
        int band = 0;
        for (int i = 0; i < BAND_Y.length; i++) {
            if (yy >= BAND_Y[i] + (r - 0.5) * 3.0) {
                band = i + 1;
            }
        }
        String col = BANDS[band];
        if (band == 5) {
            // night sky proper: black with blue/purple wisps and stars
            double star = U.rand(t, y, 66);
            if (star < 0.012) {
                return star < 0.002 ? "minecraft:sea_lantern" : star < 0.007 ? cc("white") : cc("yellow");
            }
            double n = U.fbm(t * 0.06, y * 0.1, 63, 3);
            return n + 0.15 * (r - 0.5) > 0.56 ? cc("blue") : n + 0.15 * (r - 0.5) > 0.5 ? cc("purple") : cc("black");
        }
        return cc(col);
    }

    /** Height of a column-block skyline at coordinate t: buildings of random width and height. */
    private static double skyline(double t, double w, int minH, int lo, int hi) {
        int bi = (int) Math.floor(t / w);
        double h = lo + (hi - lo) * Math.pow(U.rand(bi, 5, minH), 1.7);
        double district = U.vnoise(t * 0.03, 0.5, 91);
        h = lo + (h - lo) * (0.35 + 0.9 * district);
        if (U.rand(bi, 9, minH) < 0.12) {
            h = 0;
        }
        return h;
    }

    // ------------------------------------------------------------------ roof

    private static void roof(BuildContext c) {
        // two plain layers on top, the painted layer is the underside y = ROOF_Y
        c.fill(RX0, Layout.ROOF_Y + 1, RZ0, RX1, Layout.ROOF_Y + 2, RZ1, cc("black"));
        c.pattern(RX0, Layout.ROOF_Y, RZ0, RX1, Layout.ROOF_Y, RZ1, (x, y, z) -> roofBlock(x, z));
        // shooting star
        for (int i = 0; i < 7; i++) {
            c.set(30 - i * 2, Layout.ROOF_Y, 22 + i, cc("white"));
        }
        c.set(32, Layout.ROOF_Y, 21, "minecraft:sea_lantern");
    }

    private static String roofBlock(int x, int z) {
        double dm = Math.hypot(x - MOON_X, z - MOON_Z);
        if (dm <= MOON_R + 5) {
            return moon(x, z, dm);
        }
        double r = U.rand(x, z, 71);
        double n = U.fbm(x * 0.045, z * 0.045, 17, 4);
        double score = n + 0.14 * (r - 0.5);
        // milky way: a diagonal band that is richer in blue and stars
        double bd = Math.abs((x * 0.8 + z * 0.6) - 18 + 9 * Math.sin(x * 0.05));
        double band = bd < 13 ? 1 - bd / 13 : 0;
        String base;
        if (score < 0.38 - 0.1 * band) {
            base = cc("black");
        } else if (score < 0.56 - 0.15 * band) {
            base = r < 0.5 ? cc("black") : cc("blue");
        } else if (score < 0.74) {
            base = cc("blue");
        } else {
            base = r < 0.7 ? cc("purple") : cc("blue");
        }
        double s = U.rand(x, z, 72);
        double dens = 0.012 + 0.03 * band;
        if (s < dens) {
            double k = U.rand(x, z, 73);
            if (k < 0.55) {
                return cc("white");
            }
            if (k < 0.8) {
                return cc("yellow");
            }
            if (k < 0.9) {
                return cc("light_blue");
            }
            return "minecraft:sea_lantern";
        }
        // plus-shaped bright stars
        if (U.rand(x / 3, z / 3, 74) < 0.004 && Math.floorMod(x, 3) == 1 && Math.floorMod(z, 3) == 1) {
            return "minecraft:sea_lantern";
        }
        if (U.rand(Math.floorDiv(x - 1, 3), z / 3, 74) < 0.004 && Math.floorMod(x, 3) == 2 && Math.floorMod(z, 3) == 1) {
            return cc("white");
        }
        return base;
    }

    private static String moon(int x, int z, double d) {
        if (d <= MOON_R) {
            double crater = U.fbm((x - MOON_X) * 0.35 + 9, (z - MOON_Z) * 0.35 + 3, 23, 3);
            if (d > MOON_R - 1.6) {
                return "minecraft:sea_lantern";
            }
            if (crater > 0.62) {
                return cc("light_gray");
            }
            if (crater > 0.52) {
                return cc("white");
            }
            return d > MOON_R - 3.5 ? "minecraft:sea_lantern" : cc("white");
        }
        // halo
        double h = d - MOON_R;
        double r = U.rand(x, z, 75);
        if (h < 2) {
            return r < 0.7 ? cc("light_blue") : cc("white");
        }
        if (h < 3.6) {
            return r < 0.6 ? cc("blue") : cc("light_blue");
        }
        return r < 0.7 ? cc("blue") : cc("black");
    }
}
