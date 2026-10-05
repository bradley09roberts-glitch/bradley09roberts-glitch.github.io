package com.squidgame.core.tug;

/**
 * The shared rhythm of one heat: beat number {@code k} falls on server tick {@code epoch + k * period}. It is public
 * information (the client draws the beat ring from it, NPCs read it like a human would see it), so everything here
 * is plain arithmetic on absolute server ticks.
 */
public final class BeatClock {
    public final long epoch;
    public final int period;

    public BeatClock(long epoch, int period) {
        if (period < 2) {
            throw new IllegalArgumentException("beat period must be >= 2 ticks: " + period);
        }
        this.epoch = epoch;
        this.period = period;
    }

    /** Tick of beat number {@code index}. */
    public long beatTick(long index) {
        return epoch + index * period;
    }

    /** Index of the last beat at or before {@code tick} (may be negative before the epoch). */
    public long indexAtOrBefore(long tick) {
        return Math.floorDiv(tick - epoch, (long) period);
    }

    /** Tick of the beat nearest to {@code tick} (ties go to the earlier beat). */
    public long nearestBeat(long tick) {
        long before = beatTick(indexAtOrBefore(tick));
        long after = before + period;
        return (tick - before) <= (after - tick) ? before : after;
    }

    /** Index of the beat nearest to {@code tick}. */
    public long nearestIndex(long tick) {
        return Math.round((nearestBeat(tick) - epoch) / (double) period);
    }

    /** Signed distance in ticks from the nearest beat (negative = early, positive = late). */
    public int errorTo(long tick) {
        return (int) (tick - nearestBeat(tick));
    }

    /** First beat tick that is {@code >= tick}. */
    public long nextBeatAtOrAfter(long tick) {
        long before = beatTick(indexAtOrBefore(tick));
        return before == tick ? before : before + period;
    }

    /** Position within the current beat period: 0 on the beat, approaching 1 just before the next one. */
    public double phase(long tick) {
        return (tick - beatTick(indexAtOrBefore(tick))) / (double) period;
    }
}
