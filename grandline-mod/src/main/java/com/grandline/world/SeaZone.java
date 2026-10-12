package com.grandline.world;

import java.util.Locale;

/** Large-scale regions of the ocean world. */
public enum SeaZone {
    EAST_BLUE(1, 44, 0x3D8FD8),
    WEST_BLUE(1, 44, 0x3D8FD8),
    NORTH_BLUE(1, 40, 0x2E6FB0),
    SOUTH_BLUE(1, 46, 0x48A6DC),
    CALM_BELT(4, 34, 0x5FC4C0),
    PARADISE(3, 32, 0x2A6FBE),
    NEW_WORLD(6, 26, 0x1D4F94),
    RED_LINE(5, 40, 0x3D8FD8),
    WORLD_EDGE(1, 30, 0x2E6FB0),
    /** The White Sea above the clouds (sky dimension). */
    SKY_SEA(5, 47, 0xE8F4FF),
    /** The deep sea 10,000 m down (deep-sea dimension, Fish-Man Island). */
    DEEP_SEA(7, -20, 0x0B2A4A);

    /** Base danger tier (1..10) used to scale encounters and island difficulty. */
    public final int dangerTier;
    /** Typical sea-floor height. */
    public final int floorY;
    public final int waterColor;

    SeaZone(int dangerTier, int floorY, int waterColor) {
        this.dangerTier = dangerTier;
        this.floorY = floorY;
        this.waterColor = waterColor;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isBlue() {
        return this == EAST_BLUE || this == WEST_BLUE || this == NORTH_BLUE || this == SOUTH_BLUE;
    }

    public boolean isGrandLine() {
        return this == PARADISE || this == NEW_WORLD;
    }
}
