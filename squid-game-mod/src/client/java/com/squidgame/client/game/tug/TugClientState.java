package com.squidgame.client.game.tug;

import com.squidgame.game.tug.TugNet;

/**
 * What the client knows about the Tug of War: the latest server snapshot, a mapping from server ticks to the local clock for the
 * beat ring, and the verdict of the player's last heave for the feedback text.
 *
 * <p>The ring must show the beat where the server judges it. The server's tick numbers are mapped to the local clock with the
 * <em>fastest</em> of the last snapshots (the one with the least network delay), so the ring runs smoothly on the local clock
 * and shows the beat about one way trip after the server fired it; the server corrects a pressed heave by the whole round trip,
 * which brings it back to the beat.
 */
final class TugClientState {
    private TugClientState() {
    }

    private static final long TICK_NANOS = 50_000_000L;
    private static final int SAMPLES = 40;
    /** A snapshot older than this is not shown any more (the player left the game or the server stopped sending). */
    private static final long STALE_NANOS = 2_000_000_000L;

    private static final long[] offsets = new long[SAMPLES];
    private static int sampleCount;
    private static long minOffset;

    private static volatile TugNet.StatePayload state;
    private static volatile long receivedAt;

    // verdict of the last heave
    private static int lastHeaveSeq;
    private static long feedbackAt;
    private static int feedbackResult;
    private static int feedbackError;
    private static float feedbackQuality;

    static void accept(TugNet.StatePayload p) {
        long now = System.nanoTime();
        offsets[sampleCount++ % SAMPLES] = now - p.tick() * TICK_NANOS;
        long min = Long.MAX_VALUE;
        for (int i = 0; i < Math.min(sampleCount, SAMPLES); i++) {
            min = Math.min(min, offsets[i]);
        }
        minOffset = min;
        if (p.heaveSeq() != lastHeaveSeq) {
            if (p.heaveSeq() > lastHeaveSeq) {
                feedbackAt = now;
                feedbackResult = p.heaveResult();
                feedbackError = p.heaveError();
                feedbackQuality = p.heaveQuality();
            }
            lastHeaveSeq = p.heaveSeq();
        }
        state = p;
        receivedAt = now;
    }

    static void reset() {
        state = null;
        sampleCount = 0;
        lastHeaveSeq = 0;
        feedbackAt = 0;
    }

    /** The latest snapshot, or null when nothing (recent) has arrived. */
    static TugNet.StatePayload state() {
        TugNet.StatePayload s = state;
        return s != null && System.nanoTime() - receivedAt < STALE_NANOS ? s : null;
    }

    /** The server tick (fractional) that corresponds to the local clock right now, as the player sees the game. */
    static double tickNow() {
        return (System.nanoTime() - minOffset) / (double) TICK_NANOS;
    }

    /** Nanoseconds since the player's last heave was judged (large when there was none). */
    static long sinceFeedback() {
        return feedbackAt == 0 ? Long.MAX_VALUE : System.nanoTime() - feedbackAt;
    }

    static int feedbackResult() {
        return feedbackResult;
    }

    static int feedbackError() {
        return feedbackError;
    }

    static float feedbackQuality() {
        return feedbackQuality;
    }
}
