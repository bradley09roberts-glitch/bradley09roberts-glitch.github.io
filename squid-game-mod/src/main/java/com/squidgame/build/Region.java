package com.squidgame.build;

/** A named inclusive axis-aligned box in world block coordinates. */
public record Region(String name, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Region {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            int x1 = Math.min(minX, maxX), x2 = Math.max(minX, maxX);
            int y1 = Math.min(minY, maxY), y2 = Math.max(minY, maxY);
            int z1 = Math.min(minZ, maxZ), z2 = Math.max(minZ, maxZ);
            minX = x1;
            maxX = x2;
            minY = y1;
            maxY = y2;
            minZ = z1;
            maxZ = z2;
        }
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
    }

    public boolean containsXZ(double x, double z) {
        return x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1;
    }

    public double centerX() {
        return (minX + maxX + 1) / 2.0;
    }

    public double centerY() {
        return (minY + maxY + 1) / 2.0;
    }

    public double centerZ() {
        return (minZ + maxZ + 1) / 2.0;
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }
}
