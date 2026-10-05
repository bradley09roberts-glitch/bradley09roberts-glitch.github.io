package com.squidgame.core.finale;

/**
 * Validation of the fight inputs a client sends (through the shared {@code ClientActionPayload}): which messages
 * exist, which values are legal and how fast a player may send them. Pure, so it can be unit tested; the server never
 * trusts a message it has not run through here.
 */
public final class InputGate {
    public static final String ID_ATTACK = "finale.atk", ID_GUARD = "finale.guard", ID_SHOVE = "finale.shove", ID_DODGE = "finale.dodge";

    public enum Kind {ATTACK, GUARD, SHOVE, DODGE}

    /**
     * A parsed input. {@code down}: the button went down (true) or up (false) - re-sent every few ticks while the
     * button is held, as a heartbeat; {@code sector}: for a dodge, the direction in eighths of a turn clockwise from
     * the way the player looks (0 forward, 2 right, 4 back, 6 left).
     */
    public record Command(Kind kind, boolean down, int sector) {
    }

    /**
     * Messages a burst may contain, and how many tokens come back per tick (30 messages per second). Generous on
     * purpose: a fast clicker sends two messages per click (down and up), and a dropped "up" would leave a button held.
     */
    private static final double BURST = 40, REFILL = 1.5;
    /** A held button that has not been refreshed for this many ticks is released (a lost "up" message). */
    public static final int HOLD_TIMEOUT = 26;

    private double tokens = BURST;
    private long last = Long.MIN_VALUE;

    /** Parses a message; null when the id is not a fight input or a value is out of range. */
    public static Command parse(String id, boolean down, int sector) {
        if (id == null) {
            return null;
        }
        return switch (id) {
            case ID_ATTACK -> new Command(Kind.ATTACK, down, 0);
            case ID_GUARD -> new Command(Kind.GUARD, down, 0);
            case ID_SHOVE -> new Command(Kind.SHOVE, true, 0);
            case ID_DODGE -> sector >= 0 && sector < 8 ? new Command(Kind.DODGE, true, sector) : null;
            default -> null;
        };
    }

    /** True when the player may still send a message at this server tick (a token bucket). */
    public boolean allow(long tick) {
        if (last == Long.MIN_VALUE) {
            last = tick;
        }
        tokens = Math.min(BURST, tokens + Math.max(0, tick - last) * REFILL);
        last = Math.max(last, tick);
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    /** The world direction (unit vector) of a dodge sector for a player looking along {@code yawDeg}. */
    public static double[] dodgeDirection(double yawDeg, int sector) {
        double yaw = yawDeg + sector * 45.0;
        return new double[]{FinaleRules.forwardX(yaw), FinaleRules.forwardZ(yaw)};
    }
}
