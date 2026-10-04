package com.squidgame.build.arena.dalgona;

import com.squidgame.build.BuildContext;

/**
 * The four candy shapes of the honeycomb game as large wall murals (circle, triangle, star, umbrella), drawn
 * in the local frame of {@link Walls} (x along the wall, z = 0 the room-side plane, z = -1 the plaster plane).
 * Each shape is about 10 blocks tall: pastel fill flush with the plaster, a raised honey-brown outline,
 * decorated like a stamped cookie.
 */
public final class Murals {
    private Murals() {
    }

    public enum Kind { CIRCLE, TRIANGLE, STAR, UMBRELLA }

    private static final String OUTLINE = "minecraft:orange_terracotta";
    private static final String SHAFT = "minecraft:brown_terracotta";
    private static final String YELLOW = "squidgame:pastel_yellow";
    private static final String PEACH = "squidgame:pastel_peach";
    private static final String PINK = "squidgame:pastel_pink";
    private static final String MINT = "squidgame:pastel_mint";
    private static final String CREAM = "squidgame:pastel_cream";
    private static final String LILAC = "squidgame:pastel_lilac";

    /** Vertical centre of the shapes (between rows 10 and 11). */
    private static final double CY = 11.0;

    public static void paint(BuildContext c, Kind kind, int mid) {
        for (int du = -4; du <= 4; du++) {
            for (int y = 6; y <= 15; y++) {
                double dv = (y + 0.5) - CY;
                String s = switch (kind) {
                    case CIRCLE -> circle(du, dv);
                    case TRIANGLE -> triangle(du, dv);
                    case STAR -> star(du, dv);
                    case UMBRELLA -> umbrella(du, dv);
                };
                if (s == null) {
                    continue;
                }
                if (s.equals(OUTLINE) || s.equals(SHAFT)) {
                    c.set(mid + du, y, 0, s);
                }
                c.set(mid + du, y, -1, s);
            }
        }
        String name = switch (kind) {
            case CIRCLE -> "CIRCLE";
            case TRIANGLE -> "TRIANGLE";
            case STAR -> "STAR";
            case UMBRELLA -> "UMBRELLA";
        };
        c.text(mid + 0.5, 5.25, 0.03, name, "#8a4b1f", 1.5f, 0f, false);
    }

    // ---------------------------------------------------------------------------------------------

    private static String circle(int du, double dv) {
        double r = Math.hypot(du, dv);
        if (r > 4.7) {
            return null;
        }
        if (r > 3.6) {
            return OUTLINE;
        }
        if (r > 2.5) {
            return YELLOW;
        }
        if (r > 1.6) {
            return PEACH;
        }
        return r > 0.6 ? PINK : YELLOW;
    }

    private static String triangle(int du, double dv) {
        double[][] poly = {{0, 4.8}, {4.5, -4.3}, {-4.5, -4.3}};
        if (!Poly.inside(poly, du, dv)) {
            return null;
        }
        double d = Poly.edgeDist(poly, du, dv);
        if (d < 1.0) {
            return OUTLINE;
        }
        if (d < 2.0) {
            return PINK;
        }
        // candy stripes inside
        return ((int) Math.floor((dv + 10) * 0.5) & 1) == 0 ? PEACH : YELLOW;
    }

    private static String star(int du, double dv) {
        double[][] poly = new double[10][2];
        double cy = -0.45;
        for (int i = 0; i < 10; i++) {
            double r = (i & 1) == 0 ? 4.9 : 1.95;
            double a = Math.toRadians(90 + 36 * i);
            poly[i][0] = r * Math.cos(a);
            poly[i][1] = cy + r * Math.sin(a);
        }
        if (!Poly.inside(poly, du, dv)) {
            return null;
        }
        double d = Poly.edgeDist(poly, du, dv);
        if (d < 0.95) {
            return OUTLINE;
        }
        if (d < 1.9) {
            return YELLOW;
        }
        return Math.hypot(du, dv - cy) < 1.0 ? PINK : PEACH;
    }

    private static String umbrella(int du, double dv) {
        final double baseY = 0.2;       // canopy base line
        final double halfW = 4.4;
        final double apexY = 4.7;
        // hook handle
        if (dv < baseY) {
            if (Math.abs(du) <= 0 && dv > -3.4) {
                return SHAFT;
            }
            double hr = Math.hypot(du + 1.0, dv + 3.3);
            if (dv <= -3.3 && hr > 0.45 && hr < 1.55 && du <= 0.5) {
                return SHAFT;
            }
            if (du == -2 && dv > -3.4 && dv < -2.4) {
                return SHAFT;
            }
        }
        // canopy ellipse (above the base line) and scallops (below)
        boolean in = false;
        double e = 0;
        if (dv >= baseY) {
            e = Math.pow(du / halfW, 2) + Math.pow((dv - baseY) / (apexY - baseY), 2);
            in = e <= 1.0;
        } else {
            double sx = Math.floor((du + halfW) / 2.2) * 2.2 - halfW + 1.1;   // scallop centre
            double sd = Math.hypot(du - sx, dv - baseY);
            in = Math.abs(du) <= halfW && sd <= 1.1 && dv > baseY - 1.2;
            e = 0.5;
        }
        if (dv >= apexY - 0.1 && du == 0 && dv < apexY + 0.6) {
            return OUTLINE;                 // ferrule
        }
        if (!in) {
            return null;
        }
        if (dv >= baseY && e > 0.74) {
            return OUTLINE;
        }
        if (dv < baseY) {
            // scallop rim
            double sx = Math.floor((du + halfW) / 2.2) * 2.2 - halfW + 1.1;
            if (Math.hypot(du - sx, dv - baseY) > 0.5) {
                return OUTLINE;
            }
        }
        // ribs and panels from the apex
        double ang = Math.toDegrees(Math.atan2(du, Math.max(0.3, apexY - dv)));
        double a = Math.abs(ang);
        if (a < 3.0 || Math.abs(a - 27.6) < 3.0) {
            return OUTLINE;
        }
        int panel = (ang < 0 ? 0 : 2) + (a > 27.6 ? 1 : 0);
        return (panel & 1) == 0 ? PINK : PEACH;
    }

    // ---------------------------------------------------------------------------------------------

    /** Tiny 2D polygon helpers (even-odd inside test, distance to the outline). */
    static final class Poly {
        private Poly() {
        }

        static boolean inside(double[][] p, double x, double y) {
            boolean in = false;
            for (int i = 0, j = p.length - 1; i < p.length; j = i++) {
                if ((p[i][1] > y) != (p[j][1] > y)
                        && x < (p[j][0] - p[i][0]) * (y - p[i][1]) / (p[j][1] - p[i][1]) + p[i][0]) {
                    in = !in;
                }
            }
            return in;
        }

        static double edgeDist(double[][] p, double x, double y) {
            double best = 1e9;
            for (int i = 0, j = p.length - 1; i < p.length; j = i++) {
                double ax = p[j][0], ay = p[j][1], bx = p[i][0], by = p[i][1];
                double dx = bx - ax, dy = by - ay;
                double t = ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy);
                t = Math.max(0, Math.min(1, t));
                best = Math.min(best, Math.hypot(x - (ax + t * dx), y - (ay + t * dy)));
            }
            return best;
        }
    }
}
