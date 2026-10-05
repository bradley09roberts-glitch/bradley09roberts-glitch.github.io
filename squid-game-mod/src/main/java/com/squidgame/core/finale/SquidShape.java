package com.squidgame.core.finale;

import com.squidgame.core.finale.CourtGeometry.Pt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The shape of the painted squid, in the local block coordinates of the final arena (origin = arena origin, +X east,
 * +Z south): head circle at the top (-Z), triangle body, a 6 long neck and the square tail at the bottom (+Z). The
 * numbers are the ones of the real arena builder (build/arena/finale/Layout), so the fixture arena and the unit tests
 * play on exactly the court the game will find on the real arena. The game itself never uses this class: it reads the
 * outline from the {@code final.boundary} markers.
 */
public final class SquidShape {
    private SquidShape() {
    }

    /** Head circle centre row, radius, half width of triangle base and square, base line, neck length, square, stems. */
    public static final int ZC = -20, R = 5, HW = 7, ZB = ZC + 24, NECK_LEN = 6, ZS = ZB + NECK_LEN, SQ = 14, ZQ = ZS + SQ;
    /** Offset of the stems where the triangle sides meet the circle, and the first row below the centre they start. */
    public static final int STEM = 2, STEM_ROW = R;

    /** The court of the real arena (local coordinates). */
    public static CourtGeometry court() {
        return court(0, 0);
    }

    /** The court of the real arena shifted by the arena origin. */
    public static CourtGeometry court(double ox, double oz) {
        List<Pt> poly = new ArrayList<>();
        for (double[] p : outline()) {
            poly.add(new Pt(ox + p[0], oz + p[1]));
        }
        return new CourtGeometry(poly, new Pt(ox + 0.5, oz + ZC + 0.5), R, new Pt(ox + 0.5, oz + (ZB + ZS) / 2 + 0.5), 3,
                new Pt(ox + 0.5, oz + ZQ - 2.5), new Pt(ox + 0.5, oz + ZC + 14.5));
    }

    /**
     * Ordered (clockwise seen from above) vertices of the outer outline of the painted squid: vertex 0 is the top of
     * the head, the circle is traced in 15 degree steps, then the triangle side, base, neck, square and back up.
     */
    public static List<double[]> outline() {
        List<double[]> v = new ArrayList<>();
        double cx = 0.5, cz = ZC + 0.5, rho = R + 0.5;
        double xj = cx + (STEM + 0.5);
        double dzj = Math.sqrt(rho * rho - (STEM + 0.5) * (STEM + 0.5));
        double phiJ = Math.toDegrees(Math.atan2(dzj, STEM + 0.5));
        for (double phi = -90; phi < phiJ - 7.5; phi += 15) {
            v.add(new double[]{cx + rho * Math.cos(Math.toRadians(phi)), cz + rho * Math.sin(Math.toRadians(phi))});
        }
        v.add(new double[]{xj, cz + dzj});
        v.add(new double[]{HW + 1.0, ZB});
        v.add(new double[]{HW + 1.0, ZB + 1.0});
        v.add(new double[]{STEM + 1.0, ZB + 1.0});
        v.add(new double[]{STEM + 1.0, ZS});
        v.add(new double[]{HW + 1.0, ZS});
        v.add(new double[]{HW + 1.0, ZQ + 1.0});
        v.add(new double[]{-HW, ZQ + 1.0});
        v.add(new double[]{-HW, ZS});
        v.add(new double[]{-STEM, ZS});
        v.add(new double[]{-STEM, ZB + 1.0});
        v.add(new double[]{-HW, ZB + 1.0});
        v.add(new double[]{-HW, ZB});
        v.add(new double[]{cx - (STEM + 0.5), cz + dzj});
        double phiL = 180 - phiJ;
        for (double phi = phiL + 7.5; phi < 270 - 7.5; phi += 15) {
            double p = Math.round(phi / 15.0) * 15.0;
            v.add(new double[]{cx + rho * Math.cos(Math.toRadians(p)), cz + rho * Math.sin(Math.toRadians(p))});
        }
        return v;
    }

    /** Every 1x1 cell {x, z} of the white line of the squid (circle ring, triangle sides, base, neck walls, square). */
    public static List<int[]> lineCells() {
        Set<Long> seen = new HashSet<>();
        List<int[]> out = new ArrayList<>();
        List<int[]> all = new ArrayList<>();
        // head ring: the lowest arc stays open where the head joins the triangle
        int ir = R + 1;
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dz = -ir; dz <= ir; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= R + 0.25 && d > R - 0.75 && dz < STEM_ROW) {
                    all.add(new int[]{dx, ZC + dz});
                }
            }
        }
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            line(sgn * STEM, ZC + STEM_ROW, sgn * HW, ZB, all);
        }
        for (int x = -HW; x <= HW; x++) {
            if (Math.abs(x) >= STEM) {
                all.add(new int[]{x, ZB});
                all.add(new int[]{x, ZS});
            }
            all.add(new int[]{x, ZQ});
        }
        for (int z = ZB + 1; z < ZS; z++) {
            all.add(new int[]{-STEM, z});
            all.add(new int[]{STEM, z});
        }
        for (int z = ZS + 1; z < ZQ; z++) {
            all.add(new int[]{-HW, z});
            all.add(new int[]{HW, z});
        }
        for (int[] c : all) {
            if (seen.add(((long) c[0] << 32) | (c[1] & 0xffffffffL))) {
                out.add(c);
            }
        }
        return out;
    }

    private static void line(int x1, int z1, int x2, int z2, List<int[]> out) {
        int dx = Math.abs(x2 - x1), dz = Math.abs(z2 - z1);
        int sx = Integer.compare(x2, x1), sz = Integer.compare(z2, z1);
        int dm = Math.max(dx, dz);
        int x = x1, z = z1;
        int ex = dm / 2, ez = dm / 2;
        for (int i = 0; i <= dm; i++) {
            out.add(new int[]{x, z});
            ex -= dx;
            if (ex < 0) {
                ex += dm;
                x += sx;
            }
            ez -= dz;
            if (ez < 0) {
                ez += dm;
                z += sz;
            }
        }
    }
}
