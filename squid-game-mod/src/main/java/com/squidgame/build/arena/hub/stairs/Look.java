package com.squidgame.build.arena.hub.stairs;

/** Colour scheme of one staircase / platform: tread colour, side wall (stringer) colour, underside and accent. */
record Look(int tread, int side, int under, int accent, int depth) {
    /** Combos that sit well next to each other: {tread, side, accent}. */
    private static final int[][] COMBOS = {
            {Pal.CREAM, Pal.MINT, Pal.YELLOW},
            {Pal.MINT, Pal.PINK, Pal.SKY},
            {Pal.YELLOW, Pal.SKY, Pal.PEACH},
            {Pal.SKY, Pal.YELLOW, Pal.LILAC},
            {Pal.LILAC, Pal.PEACH, Pal.MINT},
            {Pal.PEACH, Pal.LILAC, Pal.PINK},
            {Pal.MINT, Pal.LILAC, Pal.PEACH},
            {Pal.YELLOW, Pal.LILAC, Pal.SKY},
            {Pal.YELLOW, Pal.PINK, Pal.MINT},
            {Pal.SKY, Pal.PEACH, Pal.PINK},
            {Pal.LILAC, Pal.MINT, Pal.YELLOW},
            {Pal.PEACH, Pal.MINT, Pal.SKY},
    };

    static Look of(int i, int depth) {
        int[] c = COMBOS[Math.floorMod(i, COMBOS.length)];
        return new Look(c[0], c[1], Pal.CREAM, c[2], depth);
    }

    /** The main route is always pink underfoot (the guards' colour): follow the pink. */
    static Look main(int side, int accent, int depth) {
        return new Look(Pal.PINK, side, Pal.CREAM, accent, depth);
    }

    Look withDepth(int d) {
        return new Look(tread, side, under, accent, d);
    }

    Look swapped() {
        return new Look(side, tread, under, accent, depth);
    }
}
