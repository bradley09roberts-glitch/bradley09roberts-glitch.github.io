package com.squidgame.core.tug;

/**
 * The pure part of the server-side validation of a human's input: how many messages are accepted, how the server
 * corrects for network latency, and when a held key counts as released because the client went quiet. The game adds the
 * parts that need Minecraft (is the sender in the heat, is the heat running, what do the NBT fields contain).
 */
public final class TugInputGate {
    private TugInputGate() {
    }

    /** At most this many messages (state changes and heaves together) per window; the rest of a flood is dropped. */
    public static final int MAX_MESSAGES = 40;
    public static final int WINDOW_TICKS = 20;
    /** Two heave messages closer than this are button mashing; the second one is dropped before it reaches the simulation. */
    public static final int MIN_HEAVE_MESSAGE_GAP = 4;
    /** A held key that was not refreshed for this long counts as released (the client sends a refresh every 10 ticks). */
    public static final int STALE_TICKS = 60;

    /** Per-contestant message limiter. */
    public static final class Limiter {
        private long windowStart = Long.MIN_VALUE;
        private int count;
        private long lastHeave = Long.MIN_VALUE / 4;

        /** Counts a message of any kind; false = over the rate limit, drop it. */
        public boolean allow(long now) {
            if (windowStart == Long.MIN_VALUE || now - windowStart >= WINDOW_TICKS || now < windowStart) {
                windowStart = now;
                count = 0;
            }
            return ++count <= MAX_MESSAGES;
        }

        /** Counts a heave message; false = too soon after the previous one. */
        public boolean allowHeave(long now) {
            if (now - lastHeave < MIN_HEAVE_MESSAGE_GAP) {
                return false;
            }
            lastHeave = now;
            return true;
        }
    }

    /** The keys a human holds, as last reported by their client. */
    public static final class Held {
        private boolean pull;
        private boolean brace;
        private long updatedAt = Long.MIN_VALUE / 4;

        public void set(boolean pull, boolean brace, long now) {
            this.pull = pull;
            this.brace = brace;
            this.updatedAt = now;
        }

        public void clear() {
            pull = false;
            brace = false;
        }

        private boolean fresh(long now) {
            return now - updatedAt <= STALE_TICKS;
        }

        /** Pull effort 0 or 1. */
        public double effort(long now) {
            return pull && fresh(now) ? 1.0 : 0.0;
        }

        public boolean brace(long now) {
            return brace && fresh(now);
        }
    }

    /** Whole ticks of latency to correct for, from the connection's measured round trip in milliseconds. */
    public static int latencyTicks(int latencyMillis) {
        int ticks = (int) Math.round(Math.max(0, latencyMillis) / 50.0);
        return Math.min(TugRules.MAX_LATENCY_COMPENSATION, ticks);
    }

    /**
     * The tick at which the human actually pressed the key: the tick the message arrived minus the round trip (the client
     * sees the beat one way trip late and its press needs another way to arrive).
     */
    public static long pressTick(long arrivalTick, int latencyMillis) {
        return arrivalTick - latencyTicks(latencyMillis);
    }
}
