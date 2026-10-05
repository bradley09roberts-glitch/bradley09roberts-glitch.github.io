package com.squidgame.core.bridge;

/**
 * Ballistic model of a guided hop between two panels. The NPC body is a vanilla {@code LivingEntity}: every tick it
 * moves by its velocity (collision resolved), then {@code vy = (vy - 0.08) * 0.98}. The motor takes off with an up
 * speed and steers the horizontal speed every tick, so only the vertical motion has to be predicted: the number of
 * ticks until the body comes back to the height of the landing surface decides how far a given horizontal speed
 * carries it. Landing happens on the tick whose move would cross the surface; that move is still a full horizontal
 * step, so the landing point is {@code start + ticks * speed}.
 *
 * <p>All lengths are in blocks, all speeds in blocks per tick.
 */
public final class HopPlanner {
    public static final double GRAVITY = 0.08;
    public static final double VERTICAL_DRAG = 0.98;
    /** Take-off speed of a normal jump (vanilla jump strength). */
    public static final double JUMP_NORMAL = 0.42;
    /** A slightly stronger take-off for the long diagonal leaps (more air time at a moderate horizontal speed). */
    public static final double JUMP_HIGH = 0.46;
    /** Horizontal speed that still looks like a human leap. */
    public static final double COMFORT_SPEED = 0.30;
    public static final double MAX_SPEED = 0.42;

    private static final int MAX_TICKS = 80;

    private HopPlanner() {
    }

    /**
     * A planned hop.
     *
     * @param upSpeed take-off vertical velocity (applied before the first move)
     * @param ticks   number of moves from take-off until touching down (the last one included)
     * @param speed   constant horizontal speed per tick
     */
    public record Plan(double upSpeed, int ticks, double speed) {
        /** Horizontal distance covered by the whole hop. */
        public double distance() {
            return ticks * speed;
        }
    }

    /**
     * Number of moves until a body launched with {@code upSpeed} from a surface touches down on a surface
     * {@code heightDelta} blocks higher (negative = lower).
     */
    public static int flightTicks(double upSpeed, double heightDelta) {
        return remainingTicks(upSpeed, -heightDelta);
    }

    /**
     * Moves left until touchdown for a body at {@code heightAboveLanding} (relative to the landing surface) whose
     * vertical velocity for the next move is {@code vy}. Always at least 1; capped for pathological input.
     */
    public static int remainingTicks(double vy, double heightAboveLanding) {
        double y = heightAboveLanding;
        double v = vy;
        for (int n = 1; n <= MAX_TICKS; n++) {
            y += v;
            if (y <= 0 && v <= 0) {
                return n;
            }
            v = (v - GRAVITY) * VERTICAL_DRAG;
        }
        return MAX_TICKS;
    }

    /** Height of the body above its launch surface after {@code moves} moves. */
    public static double heightAfter(double upSpeed, int moves) {
        double y = 0;
        double v = upSpeed;
        for (int i = 0; i < moves; i++) {
            y += v;
            v = (v - GRAVITY) * VERTICAL_DRAG;
        }
        return y;
    }

    /**
     * Plans a hop over {@code distance} blocks between surfaces of equal height: a normal jump while the speed stays
     * comfortable, the stronger jump for long leaps.
     */
    public static Plan plan(double distance) {
        int normal = flightTicks(JUMP_NORMAL, 0);
        if (distance / normal <= COMFORT_SPEED) {
            return new Plan(JUMP_NORMAL, normal, distance / normal);
        }
        int high = flightTicks(JUMP_HIGH, 0);
        return new Plan(JUMP_HIGH, high, distance / high);
    }

    /** Longest hop the planner will attempt (distance) before the speed would look unnatural. */
    public static double maxDistance() {
        return flightTicks(JUMP_HIGH, 0) * MAX_SPEED;
    }
}
