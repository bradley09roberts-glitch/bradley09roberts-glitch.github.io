package com.longnight.world;

/**
 * Server-side cost of the mod, measured every tick: the simulation, embodiment, raids, roads and
 * caravans (one handler), and the citizens' own entity ticks (movement, path-finding, their
 * routines). Shown by /longnight admin perf as averages over the last 100 ticks and the worst tick.
 */
public final class Perf {
    private Perf() {}

    static final int WINDOW = 100;
    static final long[] handler = new long[WINDOW];
    static final long[] citizens = new long[WINDOW];
    static final int[] citizenCount = new int[WINDOW];
    static int at;
    static long citizenNanos;
    static int citizenTicks;
    static long ticks;

    public static void addCitizen(long nanos) {
        citizenNanos += nanos;
        citizenTicks++;
    }

    public static void endTick(long handlerNanos) {
        handler[at] = handlerNanos;
        citizens[at] = citizenNanos;
        citizenCount[at] = citizenTicks;
        citizenNanos = 0;
        citizenTicks = 0;
        at = (at + 1) % WINDOW;
        ticks++;
    }

    public static String report() {
        int n = (int) Math.min(ticks, WINDOW);
        if (n == 0) return "no ticks measured yet";
        long sumH = 0, sumC = 0, worst = 0, bodies = 0;
        for (int i = 0; i < n; i++) {
            sumH += handler[i];
            sumC += citizens[i];
            worst = Math.max(worst, handler[i] + citizens[i]);
            bodies += citizenCount[i];
        }
        return String.format(java.util.Locale.ROOT,
                "longnight per tick (last %d): world %.2f ms + citizens %.2f ms (%.0f bodies) = %.2f ms avg, worst %.2f ms",
                n, sumH / 1e6 / n, sumC / 1e6 / n, (double) bodies / n, (sumH + sumC) / 1e6 / n, worst / 1e6);
    }
}
