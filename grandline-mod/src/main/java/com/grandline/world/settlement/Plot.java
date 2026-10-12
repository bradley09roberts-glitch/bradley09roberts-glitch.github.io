package com.grandline.world.settlement;

import net.minecraft.world.level.block.Rotation;

/**
 * A placed building footprint. (x, z) is the world-space minimum corner of the rotated footprint; y is the floor level.
 * Landmark plots carry the landmark id and an explicit world-space size.
 */
public record Plot(BuildingType type, int x, int y, int z, int sizeX, int sizeZ, Rotation rotation, long seed, String landmark, boolean flatten) {
    public boolean contains(int wx, int wz, int margin) {
        return wx >= x - margin && wx < x + sizeX + margin && wz >= z - margin && wz < z + sizeZ + margin;
    }

    public int centerX() {
        return x + sizeX / 2;
    }

    public int centerZ() {
        return z + sizeZ / 2;
    }

    /** Signed distance outside the footprint (0 inside). */
    public double outsideDistance(int wx, int wz) {
        int dx = Math.max(Math.max(x - wx, 0), wx - (x + sizeX - 1));
        int dz = Math.max(Math.max(z - wz, 0), wz - (z + sizeZ - 1));
        return Math.sqrt(dx * dx + dz * dz);
    }
}
