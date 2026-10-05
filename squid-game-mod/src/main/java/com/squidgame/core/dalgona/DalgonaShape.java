package com.squidgame.core.dalgona;

import java.util.ArrayList;
import java.util.List;

/**
 * The four shapes pressed into the honeycomb, as closed outlines on a 1024 x 1024 canvas (the cookie is the disc
 * inscribed in that square, centre (512, 512), radius 500). Every shape is available in three forms:
 * <ul>
 *   <li>the polygon the outline is made of ({@link #vertexX}/{@link #vertexY}): exact geometry for distance
 *       queries, fills and the inside test;</li>
 *   <li>{@link #sampleCount()} samples spaced about {@value #SPACING} units apart along the outline: the unit
 *       the carving progress is counted in;</li>
 *   <li>a per-sample <b>fragility</b> (>= 1): how easily the sugar breaks there. It is derived from the geometry
 *       (sharp turns and thin features such as star tips, the umbrella's handle and hook), so the circle is
 *       uniformly robust and the umbrella is clearly the hardest.</li>
 * </ul>
 * Pure Java, deterministic: the server, the NPCs and the client all use the very same numbers.
 */
public enum DalgonaShape {
    CIRCLE("circle", 1.0),
    TRIANGLE("triangle", 1.0),
    STAR("star", 1.1),
    UMBRELLA("umbrella", 1.5);

    public static final int CANVAS = 1024;
    public static final int MAX_COORD = CANVAS - 1;
    public static final double CENTER = CANVAS / 2.0;
    /** Nominal distance between two neighbouring outline samples (canvas units). */
    public static final double SPACING = 8.0;

    /** Fragility model: sharp turns and thin features raise it up to {@link #MAX_FRAGILITY}. */
    public static final double MAX_FRAGILITY = 2.8;
    private static final double TURN_WINDOW = 64.0;
    private static final double TURN_REF = 0.35;
    private static final double TURN_SPAN = 1.2;
    private static final double THIN_NEAR = 30.0;
    private static final double THIN_FAR = 100.0;
    private static final double THIN_MIN_ARC = 100.0;
    private static final double FRAGILITY_WEIGHT = 0.9;

    public final String id;
    /** How easily the whole cookie breaks: intricate shapes (star, umbrella) use more brittle sugar. Scales all stress. */
    public final double brittleness;
    private final double[] vx;
    private final double[] vy;
    private final double[] cum;
    private final double length;
    private final float[] sx;
    private final float[] sy;
    private final float[] fragility;
    private final double sampleStep;

    DalgonaShape(String id, double brittleness) {
        this.id = id;
        this.brittleness = brittleness;
        double[][] poly = switch (id) {
            case "circle" -> circle();
            case "triangle" -> triangle();
            case "star" -> star();
            default -> umbrella();
        };
        int n = poly.length;
        vx = new double[n];
        vy = new double[n];
        cum = new double[n + 1];
        for (int i = 0; i < n; i++) {
            vx[i] = poly[i][0];
            vy[i] = poly[i][1];
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            cum[i + 1] = cum[i] + Math.hypot(vx[j] - vx[i], vy[j] - vy[i]);
        }
        length = cum[n];
        int count = (int) Math.round(length / SPACING);
        sampleStep = length / count;
        sx = new float[count];
        sy = new float[count];
        int seg = 0;
        for (int k = 0; k < count; k++) {
            double s = k * sampleStep;
            while (seg < n - 1 && cum[seg + 1] <= s) {
                seg++;
            }
            int j = (seg + 1) % n;
            double len = cum[seg + 1] - cum[seg];
            double t = len <= 0 ? 0 : (s - cum[seg]) / len;
            sx[k] = (float) (vx[seg] + (vx[j] - vx[seg]) * t);
            sy[k] = (float) (vy[seg] + (vy[j] - vy[seg]) * t);
        }
        fragility = computeFragility(sx, sy);
    }

    // ------------------------------------------------------------------ outline polygons

    private static double[][] circle() {
        int n = 180;
        double r = 400;
        double[][] p = new double[n][2];
        for (int i = 0; i < n; i++) {
            double a = -Math.PI / 2 + 2 * Math.PI * i / n;
            p[i][0] = CENTER + r * Math.cos(a);
            p[i][1] = CENTER + r * Math.sin(a);
        }
        return p;
    }

    private static double[][] triangle() {
        double r = 430;
        double[][] p = new double[3][2];
        for (int i = 0; i < 3; i++) {
            double a = -Math.PI / 2 + 2 * Math.PI * i / 3;
            p[i][0] = CENTER + r * Math.cos(a);
            p[i][1] = CENTER + r * Math.sin(a);
        }
        return p;
    }

    private static double[][] star() {
        double outer = 430;
        double inner = 185;
        double[][] p = new double[10][2];
        for (int i = 0; i < 10; i++) {
            double r = (i % 2 == 0) ? outer : inner;
            double a = -Math.PI / 2 + Math.PI * i / 5;
            p[i][0] = CENTER + r * Math.cos(a);
            p[i][1] = CENTER + r * Math.sin(a);
        }
        return p;
    }

    /**
     * Dome with four scallops and a J-shaped handle, one closed outline: the dome arc left to right, the scalloped
     * lower edge right to left (interrupted by the handle), the handle's right edge down, around the hook and back up.
     */
    private static double[][] umbrella() {
        List<double[]> pts = new ArrayList<>();
        double domeY = 470;
        double domeR = 380;
        for (int i = 0; i <= 72; i++) {
            double t = Math.PI + Math.PI * i / 72.0;
            pts.add(new double[]{CENTER + domeR * Math.cos(t), domeY + domeR * Math.sin(t)});
        }
        // scallops: circular arcs, chord 190, sagitta 48
        double chord = 190;
        double sag = 48;
        double rs = (chord * chord / 4 + sag * sag) / (2 * sag);
        double cy = domeY + sag - rs;
        double half = Math.atan2(domeY - cy, chord / 2);
        double handleHalf = 28;
        for (int k = 0; k < 4; k++) {
            double xr = 892 - chord * k;
            double cx = xr - chord / 2;
            double from = half;
            double to = Math.PI - half;
            if (k == 1) {
                to = Math.acos((CENTER + handleHalf - cx) / rs);
            } else if (k == 2) {
                from = Math.acos((CENTER - handleHalf - cx) / rs);
            }
            int steps = 14;
            for (int i = 0; i <= steps; i++) {
                double a = from + (to - from) * i / steps;
                pts.add(new double[]{cx + rs * Math.cos(a), cy + rs * Math.sin(a)});
            }
            if (k == 1) {
                appendHandle(pts, handleHalf);
            }
        }
        // arcs meet at shared end points (cusps, dome ends): drop consecutive duplicates, including the closing one
        List<double[]> out = new ArrayList<>();
        for (double[] p : pts) {
            double[] last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last == null || Math.hypot(p[0] - last[0], p[1] - last[1]) > 1e-6) {
                out.add(p);
            }
        }
        double[] first = out.get(0);
        double[] last = out.get(out.size() - 1);
        if (Math.hypot(first[0] - last[0], first[1] - last[1]) <= 1e-6) {
            out.remove(out.size() - 1);
        }
        return out.toArray(new double[0][]);
    }

    private static void appendHandle(List<double[]> pts, double half) {
        double top = pts.get(pts.size() - 1)[1];
        double shaftEnd = 800;
        double centerline = 68;
        double hookX = CENTER - centerline;
        double outer = centerline + half;
        double inner = centerline - half;
        double right = CENTER + half;
        pts.add(new double[]{right, top + 20});
        pts.add(new double[]{right, shaftEnd - 40});
        pts.add(new double[]{right, shaftEnd});
        for (int i = 1; i <= 16; i++) {
            double a = Math.PI * i / 16.0;
            pts.add(new double[]{hookX + outer * Math.cos(a), shaftEnd + outer * Math.sin(a)});
        }
        // flat cap of the hook, then the inner arc back to the shaft
        for (int i = 0; i <= 12; i++) {
            double a = Math.PI - Math.PI * i / 12.0;
            pts.add(new double[]{hookX + inner * Math.cos(a), shaftEnd + inner * Math.sin(a)});
        }
        pts.add(new double[]{CENTER - half, shaftEnd - 40});
        pts.add(new double[]{CENTER - half, top + 20});
        pts.add(new double[]{CENTER - half, top});
    }

    // ------------------------------------------------------------------ fragility

    private static float[] computeFragility(float[] sx, float[] sy) {
        int n = sx.length;
        int w = (int) Math.round(TURN_WINDOW / SPACING);
        int far = (int) Math.round(THIN_MIN_ARC / SPACING);
        float[] f = new float[n];
        for (int i = 0; i < n; i++) {
            int a = ((i - w) % n + n) % n;
            int b = (i + w) % n;
            double ax = sx[i] - sx[a], ay = sy[i] - sy[a];
            double bx = sx[b] - sx[i], by = sy[b] - sy[i];
            double la = Math.hypot(ax, ay), lb = Math.hypot(bx, by);
            double turn = 0;
            if (la > 1e-6 && lb > 1e-6) {
                double c = (ax * bx + ay * by) / (la * lb);
                turn = Math.acos(Math.max(-1, Math.min(1, c)));
            }
            double turnFactor = clamp01((turn - TURN_REF) / TURN_SPAN);
            double nearest = Double.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                int d = Math.abs(j - i);
                d = Math.min(d, n - d);
                if (d >= far) {
                    double dd = Math.hypot(sx[j] - sx[i], sy[j] - sy[i]);
                    if (dd < nearest) {
                        nearest = dd;
                    }
                }
            }
            double thinFactor = clamp01((THIN_FAR - nearest) / (THIN_FAR - THIN_NEAR));
            f[i] = (float) Math.min(MAX_FRAGILITY, 1.0 + FRAGILITY_WEIGHT * (turnFactor + thinFactor));
        }
        // a light smoothing so the caution zones fade in and out instead of switching on sharply
        float[] g = new float[n];
        for (int i = 0; i < n; i++) {
            float s = 0;
            for (int k = -2; k <= 2; k++) {
                s += f[((i + k) % n + n) % n];
            }
            g[i] = Math.max(f[i] * 0.8f + 0.2f * (s / 5f), s / 5f);
            g[i] = Math.min((float) MAX_FRAGILITY, Math.max(1f, g[i]));
        }
        return g;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    // ------------------------------------------------------------------ accessors

    public String translationKey() {
        return "squidgame.game.dalgona.shape." + id;
    }

    public static DalgonaShape byOrdinal(int i) {
        DalgonaShape[] v = values();
        return i >= 0 && i < v.length ? v[i] : CIRCLE;
    }

    public int vertexCount() {
        return vx.length;
    }

    public double vertexX(int i) {
        return vx[i];
    }

    public double vertexY(int i) {
        return vy[i];
    }

    public int sampleCount() {
        return sx.length;
    }

    public float sampleX(int i) {
        return sx[i];
    }

    public float sampleY(int i) {
        return sy[i];
    }

    /** Fragility (1 = robust .. {@link #MAX_FRAGILITY}) of the outline sample {@code i} (wraps around). */
    public double fragility(int i) {
        int n = fragility.length;
        return fragility[((i % n) + n) % n];
    }

    public double outlineLength() {
        return length;
    }

    /** Actual arc length between two neighbouring samples (about {@link #SPACING}). */
    public double sampleStep() {
        return sampleStep;
    }

    // ------------------------------------------------------------------ queries

    /**
     * Distance from a point to the outline. When {@code arcOut} is not null, {@code arcOut[0]} receives the arc
     * position of the closest outline point in sample units (0 .. sampleCount), so callers can look up the nearest
     * sample and its fragility without a second pass.
     */
    public double nearest(double x, double y, double[] arcOut) {
        double best = Double.MAX_VALUE;
        double bestArc = 0;
        int n = vx.length;
        for (int i = 0; i < n; i++) {
            int j = i + 1 == n ? 0 : i + 1;
            double ex = vx[j] - vx[i], ey = vy[j] - vy[i];
            double len2 = ex * ex + ey * ey;
            double t = len2 <= 0 ? 0 : ((x - vx[i]) * ex + (y - vy[i]) * ey) / len2;
            t = t < 0 ? 0 : (t > 1 ? 1 : t);
            double dx = x - (vx[i] + ex * t), dy = y - (vy[i] + ey * t);
            double d2 = dx * dx + dy * dy;
            if (d2 < best) {
                best = d2;
                bestArc = cum[i] + t * (cum[i + 1] - cum[i]);
            }
        }
        if (arcOut != null) {
            arcOut[0] = bestArc / sampleStep;
        }
        return Math.sqrt(best);
    }

    public double distanceTo(double x, double y) {
        return nearest(x, y, null);
    }

    /** Index of the outline sample closest to an arc position returned by {@link #nearest}. */
    public int sampleAtArc(double arcInSamples) {
        int n = sx.length;
        int i = (int) Math.round(arcInSamples);
        return ((i % n) + n) % n;
    }

    /** Even-odd point-in-polygon test: true inside the figure (the part that is being freed). */
    public boolean contains(double x, double y) {
        boolean in = false;
        int n = vx.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            if ((vy[i] > y) != (vy[j] > y) && x < (vx[j] - vx[i]) * (y - vy[i]) / (vy[j] - vy[i]) + vx[i]) {
                in = !in;
            }
        }
        return in;
    }
}
