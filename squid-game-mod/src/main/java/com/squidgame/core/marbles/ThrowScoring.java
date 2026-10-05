package com.squidgame.core.marbles;

/**
 * Scoring of a thrown marble against the target painted in the ground: a gold bullseye block surrounded by five rings
 * (red, white, blue, white, red) laid out on the block grid, so the ring a marble scores is simply the colour of the
 * block it comes to rest on. The winner of a throw duel is decided by the exact distance to the bullseye centre; the
 * ring only sets how many marbles the win is worth.
 *
 * <pre>
 *  ring 5  gold   (the centre block)           squared block offset 0
 *  ring 4  red                                  1
 *  ring 3  white                                2
 *  ring 2  blue                                 4
 *  ring 1  white                                5
 *  ring 0  red    (the outer corners)           8
 *  miss    any other block                      -1
 * </pre>
 */
public final class ThrowScoring {
    public static final int BULLSEYE = 5;
    public static final int OUTER_RING = 0;
    public static final int MISS = -1;
    /** Distance below which two throws count as equally close (a draw). */
    public static final double DRAW_EPSILON = 1.0E-6;

    private ThrowScoring() {
    }

    /** Where a marble came to rest relative to the bullseye centre. */
    public record Result(int ring, double distance) {
        public boolean onTarget() {
            return ring >= OUTER_RING;
        }
    }

    /** Scores a resting position given as the offset (blocks) from the bullseye centre (the middle of the gold block). */
    public static Result score(double dx, double dz) {
        int ox = (int) Math.floor(dx + 0.5);
        int oz = (int) Math.floor(dz + 0.5);
        return new Result(ringOfBlock(ox, oz), Math.hypot(dx, dz));
    }

    /** Ring of the painted block at the given block offset from the centre block. */
    public static int ringOfBlock(int ox, int oz) {
        if (Math.abs(ox) > 2 || Math.abs(oz) > 2) {
            return MISS;
        }
        return switch (ox * ox + oz * oz) {
            case 0 -> BULLSEYE;
            case 1 -> 4;
            case 2 -> 3;
            case 4 -> 2;
            case 5 -> 1;
            default -> OUTER_RING;
        };
    }

    /** Points of a ring: 6 for the bullseye down to 1 for the outer ring, 0 for a miss. */
    public static int points(int ring) {
        return Math.max(0, ring + 1);
    }

    /** Negative when {@code a} landed closer, positive when {@code b} did, 0 for a draw. */
    public static int compare(Result a, Result b) {
        double d = a.distance() - b.distance();
        return Math.abs(d) <= DRAW_EPSILON ? 0 : (d < 0 ? -1 : 1);
    }

    /**
     * Marbles at stake when {@code winner} beat {@code loser}: 1, plus 1 for every two points the winning ring is
     * better than the losing one, capped by {@code cap}.
     */
    public static int stake(Result winner, Result loser, int cap) {
        int bonus = Math.max(0, points(winner.ring()) - points(loser.ring())) / 2;
        return Math.max(1, Math.min(Math.max(1, cap), 1 + bonus));
    }

    /** Translation key suffix describing a ring (squidgame.game.marbles.ring.&lt;suffix&gt;). */
    public static String ringKey(int ring) {
        return switch (ring) {
            case BULLSEYE -> "bullseye";
            case 4 -> "red";
            case 3 -> "white";
            case 2 -> "blue";
            case 1 -> "white_outer";
            case OUTER_RING -> "red_outer";
            default -> "miss";
        };
    }
}
