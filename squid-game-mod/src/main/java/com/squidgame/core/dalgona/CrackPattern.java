package com.squidgame.core.dalgona;

import com.squidgame.core.util.Rng;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The hairline cracks that grow across a stressed cookie: jagged branches generated from a seed the server hands to
 * the client, so the picture is identical for everyone and needs no per-crack networking. Each branch starts to
 * appear when the stress passes its {@link Branch#threshold()} and has fully grown {@value #SPAN} stress points later
 * (it grows from its start point, segment by segment). Pure and deterministic.
 */
public final class CrackPattern {
    private CrackPattern() {
    }

    /** Stress range over which a branch grows from nothing to its full length. */
    public static final double SPAN = 22.0;

    /** One jagged crack: canvas coordinates of its vertices, starting at the cookie rim side. */
    public record Branch(double threshold, float[] xs, float[] ys) {
        public int segments() {
            return xs.length - 1;
        }
    }

    public static List<Branch> generate(long seed) {
        Rng r = new Rng(seed * 0x9E3779B97F4A7C15L + 0x2545F4914F6CDD1DL);
        List<Branch> out = new ArrayList<>();
        int mains = 7;
        for (int m = 0; m < mains; m++) {
            double ang = (m + r.range(-0.3, 0.3)) * 2 * Math.PI / mains;
            double rad = r.range(330, 470);
            double x = DalgonaShape.CENTER + rad * Math.cos(ang);
            double y = DalgonaShape.CENTER + rad * Math.sin(ang);
            double heading = ang + Math.PI + r.range(-0.5, 0.5);
            int segs = r.rangeInt(7, 11);
            double threshold = 12 + m * 9 + r.range(-3, 3);
            Branch main = walk(r, x, y, heading, segs, threshold, 38, 70);
            out.add(main);
            int forks = r.chance(0.55) ? 2 : 1;
            for (int f = 0; f < forks; f++) {
                int at = r.rangeInt(2, Math.max(2, main.xs().length - 3));
                double h = Math.atan2(main.ys()[at + 1] - main.ys()[at], main.xs()[at + 1] - main.xs()[at])
                        + (r.nextBoolean() ? 1 : -1) * r.range(0.5, 1.0);
                out.add(walk(r, main.xs()[at], main.ys()[at], h, r.rangeInt(3, 6), main.threshold() + 10 + r.range(0, 12), 26, 48));
            }
        }
        out.sort(Comparator.comparingDouble(Branch::threshold));
        return out;
    }

    private static Branch walk(Rng r, double x, double y, double heading, int segs, double threshold, double minLen, double maxLen) {
        float[] xs = new float[segs + 1];
        float[] ys = new float[segs + 1];
        xs[0] = clamp((float) x);
        ys[0] = clamp((float) y);
        for (int i = 1; i <= segs; i++) {
            heading += r.gaussian(0, 0.38);
            double len = r.range(minLen, maxLen);
            x += Math.cos(heading) * len;
            y += Math.sin(heading) * len;
            xs[i] = clamp((float) x);
            ys[i] = clamp((float) y);
        }
        return new Branch(Math.max(0, Math.min(100, threshold)), xs, ys);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(DalgonaShape.MAX_COORD, v));
    }

    /** How many segments of the branch are visible at this stress (fractional: the last one is still growing). */
    public static double revealed(Branch b, double stress) {
        double t = (stress - b.threshold()) / SPAN;
        t = Math.max(0, Math.min(1, t));
        return t * b.segments();
    }
}
