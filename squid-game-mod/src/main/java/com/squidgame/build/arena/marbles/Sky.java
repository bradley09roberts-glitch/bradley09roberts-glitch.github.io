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
        // panorama coordinate: offset per wall so the skylines differ
        int t = u + wall * 211;
        double r = U.rand(u, y, 40 + wall);
        // ---- sky colour by height (night: navy, with a warm city glow low on the horizon)
        String sky = skyColor(y, r, t);
        // ---- far hills (blue-purple silhouettes)
        double farH = 11 + 4.5 * Math.sin(t * 0.045 + 1.0) + 3.0 * Math.sin(t * 0.13 + 2.0) + 2.0 * Math.sin(t * 0.31);
        if (y <= farH) {
            sky = r < 0.5 ? cc("purple") : cc("blue");
            if (y > farH - 4 && r < 0.3) {
                sky = cc("blue");
            }
        }
        // ---- far skyline (dark blue, no lights)
        double fs = skyline(t * 0.8 + 77, 3.0, 5, 15, 31);
        if (y <= fs && y > 5) {
            sky = r < 0.65 ? cc("blue") : "minecraft:blue_terracotta";
            if (r > 0.97) {
                sky = cc("light_blue");
            }
        }
        // ---- near skyline: black towers with lit windows
        double ns = skyline(t, 5.0, 4, 11, 27);
        if (y <= ns && y > 3) {
            boolean windowCol = Math.floorMod(t, 2) == 0;
            boolean windowRow = y % 3 == 1;
            double on = U.rand(t / 2, y / 3, 52);
            if (windowCol && windowRow && on < 0.55 && y < ns - 1) {
                sky = on < 0.22 ? cc("yellow") : on < 0.38 ? cc("orange") : cc("white");
                if (on < 0.03) {
                    sky = "minecraft:glowstone";
                }
            } else {
                sky = r < 0.8 ? cc("black") : cc("gray");
            }
        }
        // ---- near hills: black with tiny house lights
        double nh = 8 + 3.5 * Math.sin(t * 0.06 + 4.0) + 2.5 * Math.sin(t * 0.17 + 0.4) + 1.5 * Math.sin(t * 0.43);
        if (y <= nh) {
            sky = cc("black");
            if (y >= nh - 3 && r < 0.05) {
                sky = cc("orange");
            }
        }
        return sky;
    }

    /** Height of a column-block skyline at coordinate t: buildings of random width and height. */
    private static double skyline(double t, double w, int minH, int lo, int hi) {
        int bi = (int) Math.floor(t / w);
        double h = lo + (hi - lo) * Math.pow(U.rand(bi, 5, minH), 1.7);
        // some districts are low
        double district = U.vnoise(t * 0.03, 0.5, 91);
        h = lo + (h - lo) * (0.35 + 0.9 * district);
        // gaps between buildings
        if (U.rand(bi, 9, minH) < 0.12) {
            h = 0;
        }
        return h;
    }

    private static String skyColor(int y, double r, int t) {
        // horizon glow 6..22, then deepening navy
        if (y < 7) {
            return r < 0.6 ? cc("orange") : cc("red");
        }
        if (y < 12) {
            return r < 0.35 ? cc("orange") : r < 0.6 ? cc("magenta") : r < 0.8 ? cc("red") : cc("pink");
        }
        if (y < 17) {
            return r < 0.45 ? cc("magenta") : r < 0.8 ? cc("purple") : cc("red");
        }
        if (y < 23) {
            return r < 0.5 ? cc("purple") : r < 0.85 ? cc("blue") : cc("magenta");
        }
        if (y < 30) {
            return r < 0.55 ? cc("blue") : r < 0.9 ? cc("purple") : cc("black");
        }
        if (y < 38) {
            double star = U.rand(t, y, 66);
            if (star < 0.012) {
                return cc("white");
            }
            return r < 0.55 ? cc("blue") : r < 0.9 ? cc("black") : cc("purple");
        }
        double star = U.rand(t, y, 67);
        if (star < 0.015) {
            return star < 0.003 ? "minecraft:sea_lantern" : cc("white");
        }
        return r < 0.45 ? cc("black") : r < 0.85 ? cc("blue") : cc("purple");
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
        double score = n + 0.35 * (r - 0.5);
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
