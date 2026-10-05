package com.squidgame.core.bridge;

import com.squidgame.core.util.Rng;

/**
 * The hidden route of the glass bridge: for every row exactly one of the two lanes is tempered (holds), the
 * other one is fragile. Generated once per game from the tournament seed and the game number, retained for the
 * whole game (it never changes when somebody falls) and persisted with the game state so a resumed game keeps it.
 *
 * <p>This object is the only place the route exists. Nothing but the game's panel logic may hold a reference to it:
 * NPC behaviours and clients only ever get {@link BridgeKnowledge} (what has publicly happened). {@link #toString}
 * deliberately prints nothing about the lanes so a log line can never leak it.
 */
public final class BridgeRoute {
    public static final int DEFAULT_ROWS = 18;
    public static final int LANES = 2;
    /** Rows are stored as bits of one long. */
    public static final int MAX_ROWS = 62;

    private static final long SALT = 0xB41D6E5L;

    private final int rows;
    /** Bit r set = lane 1 is the safe one in row r, clear = lane 0. */
    private final long bits;

    private BridgeRoute(int rows, long bits) {
        if (rows < 1 || rows > MAX_ROWS) {
            throw new IllegalArgumentException("rows must be 1.." + MAX_ROWS + ": " + rows);
        }
        this.rows = rows;
        this.bits = bits & ((1L << rows) - 1);
    }

    /** Route of game number {@code gameNumber} of the tournament with the given seed (every lane equally likely). */
    public static BridgeRoute forGame(long tournamentSeed, int gameNumber, int rows) {
        return generate(new Rng(tournamentSeed).fork(SALT).fork(gameNumber).seed(), rows);
    }

    /** A route derived from one seed; the same seed always yields the same route. */
    public static BridgeRoute generate(long seed, int rows) {
        Rng rng = new Rng(seed).fork(SALT);
        long bits = 0;
        for (int r = 0; r < rows && r < MAX_ROWS; r++) {
            if (rng.nextBoolean()) {
                bits |= 1L << r;
            }
        }
        return new BridgeRoute(rows, bits);
    }

    /** Restores a route saved with {@link #toBits()}. */
    public static BridgeRoute ofBits(long bits, int rows) {
        return new BridgeRoute(rows, bits);
    }

    public int rows() {
        return rows;
    }

    /** The lane (0 or 1) that holds in {@code row}. */
    public int safeLane(int row) {
        if (row < 0 || row >= rows) {
            throw new IndexOutOfBoundsException("row " + row);
        }
        return (int) ((bits >>> row) & 1L);
    }

    public boolean isSafe(int row, int lane) {
        return safeLane(row) == lane;
    }

    /** Compact form for persistence (see {@link #ofBits}). */
    public long toBits() {
        return bits;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BridgeRoute r && r.rows == rows && r.bits == bits;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(bits) * 31 + rows;
    }

    @Override
    public String toString() {
        return "BridgeRoute[rows=" + rows + ", hidden]";
    }
}
