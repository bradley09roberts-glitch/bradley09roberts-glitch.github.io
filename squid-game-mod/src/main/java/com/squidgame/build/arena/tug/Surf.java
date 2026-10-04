package com.squidgame.build.arena.tug;

/** Surface patterns: poured-concrete panelling, floors and pit walls, as pure functions of position. */
final class Surf {
    private Surf() {
    }

    /**
     * Poured concrete wall face: full-height expansion joints every 10 blocks, horizontal pour lines every 6,
     * panel-wise tone variation and a hazard-striped plinth. u = position along the wall, v = height.
     */
    static String wall(int u, int v) {
        int uj = Math.floorMod(u + 5, 10);
        if (uj == 0) {
            return v % 6 == 3 ? Pal.CONC_D : Pal.BLACK;           // expansion joint
        }
        int panel = Math.floorDiv(u + 5, 10);
        int lift = Math.floorDiv(v, 6);
        if (v >= 1 && v <= 2) {
            return Pal.hazard(u + v);                               // plinth hazard band
        }
        if (v == 3) {
            return Pal.BLACK;                                       // plinth cap
        }
        if (Math.floorMod(v, 6) == 0) {
            return Pal.CONC_D;                                      // pour line
        }
        double h = Pal.hash(panel, lift, 7);
        String tone;
        if (h < 0.50) {
            tone = Pal.CONC_L;
        } else if (h < 0.70) {
            tone = Pal.STONE;
        } else if (h < 0.85) {
            tone = Pal.SMOOTH;
        } else if (h < 0.95) {
            tone = Pal.ANDESITE;
        } else {
            tone = Pal.CONC_D;
        }
        // tie holes and grime
        double g = Pal.hash(u, v, 11);
        if (uj == 2 && (Math.floorMod(v, 6) == 2 || Math.floorMod(v, 6) == 5)) {
            return Pal.CONC_D;
        }
        if (g < 0.04) {
            return Pal.CONC_D;
        }
        return tone;
    }

    /** Hall floor around the pit: 10x10 slabs with dark joints, scuffed. */
    static String ring(int x, int z) {
        int fx = Math.floorMod(x, 10), fz = Math.floorMod(z, 10);
        if (fx == 0 || fz == 0) {
            return Pal.CONC_D;
        }
        double h = Pal.hash(x, z, 3);
        if (h < 0.10) {
            return Pal.STONE;
        }
        if (h < 0.16) {
            return Pal.ANDESITE;
        }
        return Pal.SMOOTH;
    }

    /** Pit wall: dirty dark concrete with vertical joints. */
    static String pitWall(int u, int v) {
        int uj = Math.floorMod(u + 5, 10);
        if (uj == 0) {
            return Pal.BLACK;
        }
        if (v >= -2 && v <= -1) {
            return Pal.hazard(u + v);
        }
        double h = Pal.hash(u, v, 5);
        // darker toward the floor
        double depth = Math.min(1.0, Math.max(0.0, (-v) / 30.0));
        if (h < 0.10 + 0.25 * depth) {
            return Pal.DS_BRICKS;
        }
        if (h < 0.16 + 0.30 * depth) {
            return Pal.CONC_D;
        }
        if (h < 0.19 + 0.30 * depth) {
            return Pal.DS_CRACKED;
        }
        return Math.floorMod(Math.floorDiv(v, 6), 2) == 0 ? Pal.CONC_D : Pal.CONC_L;
    }
}
