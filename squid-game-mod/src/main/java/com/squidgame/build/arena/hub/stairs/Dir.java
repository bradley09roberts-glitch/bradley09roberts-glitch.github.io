package com.squidgame.build.arena.hub.stairs;

/** Horizontal heading in the hub frame (+X east, +Z south): N = -Z. {@code right()} is clockwise seen from above. */
enum Dir {
    N(0, -1, "north"), E(1, 0, "east"), S(0, 1, "south"), W(-1, 0, "west");

    final int dx, dz;
    final String facing;

    Dir(int dx, int dz, String facing) {
        this.dx = dx;
        this.dz = dz;
        this.facing = facing;
    }

    Dir right() {
        return values()[(ordinal() + 1) & 3];
    }

    Dir left() {
        return values()[(ordinal() + 3) & 3];
    }

    Dir back() {
        return values()[(ordinal() + 2) & 3];
    }

    /** Yaw (degrees) of an entity looking along this heading (0 = south, 90 = west, 180 = north, -90 = east). */
    float yaw() {
        return switch (this) {
            case S -> 0f;
            case W -> 90f;
            case N -> 180f;
            case E -> -90f;
        };
    }

    boolean alongX() {
        return this == E || this == W;
    }
}
