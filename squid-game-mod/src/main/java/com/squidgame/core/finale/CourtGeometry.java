package com.squidgame.core.finale;

import java.util.List;

/**
 * Pure geometry of the squid-shaped court in world x/z coordinates: the boundary polygon (the outer outline of the
 * painted white line, from the {@code final.boundary} markers), the head circle (the attacker's goal), the neck and
 * the two spawn points. Nothing here knows about Minecraft; the polygon is closed implicitly.
 *
 * <p>Edge distances are signed: positive inside the court, negative outside. A fighter is "out" as soon as its
 * centre is closer than {@link FinaleRules#OUT_MARGIN} to the edge or beyond it.
 */
public final class CourtGeometry {
    /** A point on the floor plane. */
    public record Pt(double x, double z) {
        public double dist(Pt o) {
            return Math.hypot(x - o.x, z - o.z);
        }
    }

    private static final double EPS = 1e-9;

    private final double[] vx, vz;
    private final int n;
    private final Pt circleCenter, neckCenter, attackerSpawn, defenderSpawn, axis;
    private final double circleRadius, neckWidth, minX, maxX, minZ, maxZ, axisLength;

    /**
     * @param polygon        the outline vertices in order (at least 3)
     * @param circleCenter   centre of the head circle
     * @param circleRadius   radius to the centre of the painted ring
     * @param neckCenter     centre of the neck gap
     * @param neckWidth      clear width of the neck
     * @param attackerSpawn  where the attacker starts (in the square)
     * @param defenderSpawn  where the defender starts (in the triangle)
     */
    public CourtGeometry(List<Pt> polygon, Pt circleCenter, double circleRadius, Pt neckCenter, double neckWidth,
                         Pt attackerSpawn, Pt defenderSpawn) {
        if (polygon.size() < 3) {
            throw new IllegalArgumentException("a court needs at least 3 boundary vertices");
        }
        this.n = polygon.size();
        this.vx = new double[n];
        this.vz = new double[n];
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            vx[i] = polygon.get(i).x();
            vz[i] = polygon.get(i).z();
            x0 = Math.min(x0, vx[i]);
            x1 = Math.max(x1, vx[i]);
            z0 = Math.min(z0, vz[i]);
            z1 = Math.max(z1, vz[i]);
        }
        this.minX = x0;
        this.maxX = x1;
        this.minZ = z0;
        this.maxZ = z1;
        this.circleCenter = circleCenter;
        this.circleRadius = circleRadius;
        this.neckCenter = neckCenter;
        this.neckWidth = neckWidth;
        this.attackerSpawn = attackerSpawn;
        this.defenderSpawn = defenderSpawn;
        double ax = circleCenter.x() - attackerSpawn.x(), az = circleCenter.z() - attackerSpawn.z();
        double len = Math.hypot(ax, az);
        this.axisLength = len;
        this.axis = len < EPS ? new Pt(0, -1) : new Pt(ax / len, az / len);
    }

    // ------------------------------------------------------------------ accessors

    public Pt circleCenter() {
        return circleCenter;
    }

    public double circleRadius() {
        return circleRadius;
    }

    public Pt neckCenter() {
        return neckCenter;
    }

    public double neckWidth() {
        return neckWidth;
    }

    public Pt attackerSpawn() {
        return attackerSpawn;
    }

    public Pt defenderSpawn() {
        return defenderSpawn;
    }

    /** Unit vector from the attacker's spawn towards the circle: the long axis of the court. */
    public Pt axis() {
        return axis;
    }

    public int vertexCount() {
        return n;
    }

    public double vertexX(int i) {
        return vx[i];
    }

    public double vertexZ(int i) {
        return vz[i];
    }

    // ------------------------------------------------------------------ inside / distance

    /** Even-odd point in polygon test (the edge itself counts as outside). */
    public boolean contains(double x, double z) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return false;
        }
        boolean in = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            if ((vz[i] > z) != (vz[j] > z) && x < (vx[j] - vx[i]) * (z - vz[i]) / (vz[j] - vz[i]) + vx[i]) {
                in = !in;
            }
        }
        return in;
    }

    /** Distance to the nearest edge, positive inside the court and negative outside. */
    public double edgeDistance(double x, double z) {
        double best = Double.MAX_VALUE;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            best = Math.min(best, segmentDistance(x, z, vx[j], vz[j], vx[i], vz[i]));
        }
        return contains(x, z) ? best : -best;
    }

    /** True when the fighter is still in: inside the polygon and not closer to the edge than the out margin. */
    public boolean isIn(double x, double z) {
        return edgeDistance(x, z) >= FinaleRules.OUT_MARGIN;
    }

    /** The point of the boundary closest to (x, z). */
    public Pt nearestEdgePoint(double x, double z) {
        double best = Double.MAX_VALUE;
        double bx = x, bz = z;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double[] p = closestOnSegment(x, z, vx[j], vz[j], vx[i], vz[i]);
            double d = Math.hypot(x - p[0], z - p[1]);
            if (d < best) {
                best = d;
                bx = p[0];
                bz = p[1];
            }
        }
        return new Pt(bx, bz);
    }

    /** Unit vector pointing from the nearest edge into the court (away from the line). */
    public Pt inward(double x, double z) {
        Pt e = nearestEdgePoint(x, z);
        double dx = x - e.x(), dz = z - e.z();
        double len = Math.hypot(dx, dz);
        if (len < EPS) {
            // exactly on the line: use the direction to the circle as a sane fallback
            return axis;
        }
        double s = contains(x, z) ? 1.0 : -1.0;
        return new Pt(s * dx / len, s * dz / len);
    }

    /**
     * Distance along the unit direction (dx, dz) from (x, z) until the court is left, capped at {@code max};
     * 0 when the start is outside. This is how far a fighter can be pushed in that direction.
     */
    public double rayExit(double x, double z, double dx, double dz, double max) {
        if (!contains(x, z)) {
            return 0;
        }
        double best = max;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double ex = vx[i] - vx[j], ez = vz[i] - vz[j];
            double den = dx * ez - dz * ex;
            if (Math.abs(den) < EPS) {
                continue;
            }
            double wx = vx[j] - x, wz = vz[j] - z;
            double t = (wx * ez - wz * ex) / den;
            double u = (wx * dz - wz * dx) / den;
            if (t > 0 && u >= 0 && u <= 1 && t < best) {
                best = t;
            }
        }
        return best;
    }

    /** The point nearest to {@code p} that is at least {@code margin} inside the court (p itself when it already is). */
    public Pt clampInside(Pt p, double margin) {
        double d = edgeDistance(p.x(), p.z());
        if (d >= margin) {
            return p;
        }
        Pt in = inward(p.x(), p.z());
        double push = margin - d;
        return new Pt(p.x() + in.x() * push, p.z() + in.z() * push);
    }

    // ------------------------------------------------------------------ circle, neck, axis

    public double circleDistance(double x, double z) {
        return Math.hypot(x - circleCenter.x(), z - circleCenter.z());
    }

    /** Radius within which the attacker counts as standing in the circle (the golden target ring). */
    public double captureRadius() {
        return circleRadius * FinaleRules.CAPTURE_FRACTION;
    }

    public boolean inCircle(double x, double z) {
        return circleDistance(x, z) <= captureRadius();
    }

    /** Blocks travelled along the long axis from the attacker's spawn (negative behind it). */
    public double along(double x, double z) {
        return (x - attackerSpawn.x()) * axis.x() + (z - attackerSpawn.z()) * axis.z();
    }

    /** Distance from the attacker's spawn to the circle's centre. */
    public double axisLength() {
        return axisLength;
    }

    /** {@link #along} as a fraction of the way from the attacker's spawn (0) to the circle's centre (1). */
    public double progress(double x, double z) {
        return axisLength < EPS ? 0 : along(x, z) / axisLength;
    }

    /** Signed lateral offset from the long axis (positive to the right of the direction of travel). */
    public double lateral(double x, double z) {
        return -(x - attackerSpawn.x()) * axis.z() + (z - attackerSpawn.z()) * axis.x();
    }

    /** True inside the neck: within its half-width plus a margin of the axis and a few blocks of its centre. */
    public boolean inNeck(double x, double z) {
        double along = Math.abs((x - neckCenter.x()) * axis.x() + (z - neckCenter.z()) * axis.z());
        double across = Math.abs(-(x - neckCenter.x()) * axis.z() + (z - neckCenter.z()) * axis.x());
        return along <= NECK_HALF_LENGTH && across <= neckWidth / 2 + 1.0;
    }

    /** The neck is about 6 blocks long on the real court. */
    public static final double NECK_HALF_LENGTH = 3.5;

    // ------------------------------------------------------------------ segment helpers

    private static double[] closestOnSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az;
        double len2 = dx * dx + dz * dz;
        double t = len2 < EPS ? 0 : ((px - ax) * dx + (pz - az) * dz) / len2;
        t = Math.max(0, Math.min(1, t));
        return new double[]{ax + t * dx, az + t * dz};
    }

    private static double segmentDistance(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az;
        double len2 = dx * dx + dz * dz;
        double t = len2 < EPS ? 0 : ((px - ax) * dx + (pz - az) * dz) / len2;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (ax + t * dx), pz - (az + t * dz));
    }
}
