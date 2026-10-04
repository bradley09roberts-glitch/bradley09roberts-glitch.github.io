package com.squidgame.build.arena.dalgona;

/**
 * Position-hashed, order-independent noise for weathering (worn floorboards, stained plaster...).
 * Unlike {@code BuildContext.rng()} these functions do not depend on call order, so adding or
 * removing one detail never reshuffles the texture of everything built after it.
 */
public final class Noise {
    private Noise() {
    }

    /** 32 bit integer hash of a lattice point. */
    public static int hashInt(int x, int y, int z, int salt) {
        int h = x * 0x27d4eb2d ^ y * 0x165667b1 ^ z * 0x9E3779B1 ^ salt * 0x85ebca6b;
        h ^= h >>> 15;
        h *= 0x2c1b3c6d;
        h ^= h >>> 12;
        h *= 0x297a2d39;
        h ^= h >>> 15;
        return h;
    }

    /** Uniform pseudo random value in [0, 1) for a lattice point. */
    public static double hash(int x, int y, int z, int salt) {
        return (hashInt(x, y, z, salt) >>> 8) / (double) (1 << 24);
    }

    public static double hash(int x, int z, int salt) {
        return hash(x, 0, z, salt);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /** Smooth 2D value noise in [0, 1); {@code scale} is the feature size in blocks. */
    public static double value2(double x, double z, double scale, int salt) {
        double fx = x / scale, fz = z / scale;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        double tx = smooth(fx - ix), tz = smooth(fz - iz);
        double a = hash(ix, 0, iz, salt), b = hash(ix + 1, 0, iz, salt);
        double c = hash(ix, 0, iz + 1, salt), d = hash(ix + 1, 0, iz + 1, salt);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
    }

    /** Smooth 3D value noise in [0, 1). */
    public static double value3(double x, double y, double z, double scale, int salt) {
        double fx = x / scale, fy = y / scale, fz = z / scale;
        int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy), iz = (int) Math.floor(fz);
        double tx = smooth(fx - ix), ty = smooth(fy - iy), tz = smooth(fz - iz);
        double[] v = new double[8];
        for (int i = 0; i < 8; i++) {
            v[i] = hash(ix + (i & 1), iy + ((i >> 1) & 1), iz + ((i >> 2) & 1), salt);
        }
        double x00 = v[0] + (v[1] - v[0]) * tx, x10 = v[2] + (v[3] - v[2]) * tx;
        double x01 = v[4] + (v[5] - v[4]) * tx, x11 = v[6] + (v[7] - v[6]) * tx;
        double y0 = x00 + (x10 - x00) * ty, y1 = x01 + (x11 - x01) * ty;
        return y0 + (y1 - y0) * tz;
    }

    /** Two octaves of value noise normalised to [0, 1). */
    public static double fbm2(double x, double z, double scale, int salt) {
        return (value2(x, z, scale, salt) * 2 + value2(x, z, scale / 2.3, salt + 101)) / 3.0;
    }
}
