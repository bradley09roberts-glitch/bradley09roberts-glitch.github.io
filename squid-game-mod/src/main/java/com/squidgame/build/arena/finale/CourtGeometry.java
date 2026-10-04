package com.squidgame.build.arena.finale;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.squidgame.build.arena.finale.Layout.*;

/**
 * Exact, pure geometry of the painted squid (no block writes): the 1-block-wide line cells (circle, triangle sides,
 * triangle base, neck walls, square), the zone predicates and the outline polygon.
 *
 * <pre>
 *            ooo              head: circle r=5 around cell (0,ZC); its lowest arc is left open (3 wide)
 *          oo   oo             so the head is entered through the neck of the triangle
 *         o       o
 *         o       o
 *          oo   oo
 *            t t               triangle body: sides from the head down to the base line z=ZB, 14 between line centres
 *           t   t
 *           ...                  (about 20 rows tall)
 *       bbbbbn   nbbbbb        base line with the 3 wide neck gap (x -1..1)
 *            n   n             neck walls x=+-2, 5 rows long
 *       ssssss   ssssss        square, top line with the same gap
 *       s            s         14 x 14 between line centres (15 x 15 blocks)
 *       ssssssssssssss
 * </pre>
 *
 * Cell coordinates are block indices (cell x spans [x, x+1)). The court axis is the centre of cell x=0 (x=0.5).
 */
final class CourtGeometry {
    private CourtGeometry() {
    }

    /** x of the stems where the triangle sides meet the head circle (cell offset from the axis). */
    static final int STEM = 2;
    /** First row (below the circle centre) that belongs to the triangle sides. */
    static final int STEM_ROW = R;

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /** The head circle outline: the ring of {@link com.squidgame.build.BuildContext#ring}, minus its lowest row (open junction). */
    static List<int[]> ringCells() {
        List<int[]> out = new ArrayList<>();
        int ir = (int) Math.ceil(R) + 1;
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dz = -ir; dz <= ir; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= R + 0.25 && d > R - 0.75 && dz < STEM_ROW) {
                    out.add(new int[]{dx, ZC + dz});
                }
            }
        }
        return out;
    }

    /** Both slanted sides of the triangle body (z-major Bresenham from the stems down to the base corners). */
    static List<int[]> sideCells() {
        List<int[]> out = new ArrayList<>();
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            line(sgn * STEM, ZC + STEM_ROW, sgn * HW, ZB, out);
        }
        return out;
    }

    /** Triangle base line (two pieces, the neck gap x in [-1,1] stays open). */
    static List<int[]> baseCells() {
        List<int[]> out = new ArrayList<>();
        for (int x = -HW; x <= HW; x++) {
            if (Math.abs(x) >= STEM) {
                out.add(new int[]{x, ZB});
            }
        }
        return out;
    }

    /** The two parallel neck walls between the base line and the square. */
    static List<int[]> neckCells() {
        List<int[]> out = new ArrayList<>();
        for (int z = ZB + 1; z < ZS; z++) {
            out.add(new int[]{-STEM, z});
            out.add(new int[]{STEM, z});
        }
        return out;
    }

    /** The square: top line (with the neck gap), both sides, bottom line. */
    static List<int[]> squareCells() {
        List<int[]> out = new ArrayList<>();
        for (int x = -HW; x <= HW; x++) {
            if (Math.abs(x) >= STEM) {
                out.add(new int[]{x, ZS});
            }
            out.add(new int[]{x, ZQ});
        }
        for (int z = ZS + 1; z < ZQ; z++) {
            out.add(new int[]{-HW, z});
            out.add(new int[]{HW, z});
        }
        return out;
    }

    /** All white line cells of the squid (deduplicated). */
    static List<int[]> lineCells() {
        Set<Long> seen = new HashSet<>();
        List<int[]> out = new ArrayList<>();
        for (List<int[]> part : List.of(ringCells(), sideCells(), baseCells(), neckCells(), squareCells())) {
            for (int[] c : part) {
                if (seen.add(key(c[0], c[1]))) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    /** 2D integer line (same algorithm as BuildContext.line), appended to {@code out}. */
    static void line(int x1, int z1, int x2, int z2, List<int[]> out) {
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

    // ------------------------------------------------------------------ outline polygon

    /**
     * Ordered (clockwise seen from above, x east / z south) vertices of the OUTER outline of the painted squid, in
     * absolute local block coordinates (x/z of cell edges). Vertex 0 is the top of the head; the circle is traced by
     * 22 vertices, followed by the triangle side, the base line, the neck, the square and back up the left side.
     */
    static List<double[]> outline() {
        List<double[]> v = new ArrayList<>();
        double cx = 0.5, cz = ZC + 0.5, rho = R + 0.5;
        double xj = cx + (STEM + 0.5);                      // outer edge of the right stem
        double dzj = Math.sqrt(rho * rho - (STEM + 0.5) * (STEM + 0.5));
        double phiJ = Math.toDegrees(Math.atan2(dzj, STEM + 0.5)); // ~65.4 deg, measured from +x towards +z (clockwise)
        // right half of the head: from the top (-90) clockwise to the junction
        for (double phi = -90; phi < phiJ - 7.5; phi += 15) {
            v.add(new double[]{cx + rho * Math.cos(Math.toRadians(phi)), cz + rho * Math.sin(Math.toRadians(phi))});
        }
        v.add(new double[]{xj, cz + dzj});                  // junction right
        // triangle right side, base, neck, square, left side
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
        v.add(new double[]{cx - (STEM + 0.5), cz + dzj});   // junction left
        // left half of the head: from the junction clockwise (towards the top)
        double phiL = 180 - phiJ;
        for (double phi = phiL + 7.5; phi < 270 - 7.5; phi += 15) {
            double p = Math.round(phi / 15.0) * 15.0;
            v.add(new double[]{cx + rho * Math.cos(Math.toRadians(p)), cz + rho * Math.sin(Math.toRadians(p))});
        }
        return v;
    }

    /** Even-odd point in polygon test. */
    static boolean contains(List<double[]> poly, double x, double z) {
        boolean in = false;
        for (int i = 0, j = poly.size() - 1; i < poly.size(); j = i++) {
            double xi = poly.get(i)[0], zi = poly.get(i)[1], xj = poly.get(j)[0], zj = poly.get(j)[1];
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    /** Scanline polygon rasteriser: every cell whose centre lies inside the polygon. */
    static boolean[][] rasterise(List<double[]> poly, int x0, int z0, int x1, int z1) {
        boolean[][] g = new boolean[x1 - x0 + 1][z1 - z0 + 1];
        for (int z = z0; z <= z1; z++) {
            // crossings of the scanline z+0.5 with the polygon edges
            List<Double> xs = new ArrayList<>();
            double sy = z + 0.5;
            for (int i = 0, j = poly.size() - 1; i < poly.size(); j = i++) {
                double yi = poly.get(i)[1], yj = poly.get(j)[1];
                if ((yi > sy) != (yj > sy)) {
                    double xi = poly.get(i)[0], xj = poly.get(j)[0];
                    xs.add(xi + (sy - yi) * (xj - xi) / (yj - yi));
                }
            }
            xs.sort(Double::compare);
            for (int k = 0; k + 1 < xs.size(); k += 2) {
                int a = (int) Math.ceil(xs.get(k) - 0.5), b = (int) Math.floor(xs.get(k + 1) - 0.5);
                for (int x = Math.max(a, x0); x <= Math.min(b, x1); x++) {
                    g[x - x0][z - z0] = true;
                }
            }
        }
        return g;
    }

    // ------------------------------------------------------------------ zones (by cell)

    static boolean inSquare(int x, int z) {
        return x >= -HW && x <= HW && z >= ZS && z <= ZQ;
    }

    static boolean inNeck(int x, int z) {
        return Math.abs(x) < STEM && z > ZB && z < ZS;
    }

    /** Interior of the head circle (cells with centre inside radius R). */
    static boolean inHead(int x, int z) {
        return Math.hypot(x, z - ZC) < R && z - ZC < STEM_ROW;
    }

    /** Whole squid including the lines. */
    static boolean inCourt(int x, int z) {
        return contains(OUTLINE, x + 0.5, z + 0.5);
    }

    static final List<double[]> OUTLINE = outline();
}
