package com.grandline.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Opt-in timing of the mod's per-tick systems, switched on by the load test ({@code GL_SELFTEST=load}). When off,
 * each probe is a single static boolean check. Main server thread only.
 *
 * <pre>
 * long t = Perf.start();
 * SystemA.tick(server); t = Perf.lap("systemA", t);
 * SystemB.tick(server); t = Perf.lap("systemB", t);
 * </pre>
 */
public final class Perf {
    public static boolean enabled;
    /** name → {total nanos, calls, max nanos} since the last {@link #drain()}. */
    private static final Map<String, long[]> TOTALS = new LinkedHashMap<>();

    public static long start() {
        return enabled ? System.nanoTime() : 0L;
    }

    /** Records the time since {@code t0} under {@code name} and returns the current time for the next probe. */
    public static long lap(String name, long t0) {
        if (!enabled || t0 == 0L) return 0L;
        long now = System.nanoTime();
        add(name, now - t0);
        return now;
    }

    public static void add(String name, long nanos) {
        long[] a = TOTALS.computeIfAbsent(name, k -> new long[3]);
        a[0] += nanos;
        a[1]++;
        if (nanos > a[2]) a[2] = nanos;
        TICK.merge(name, nanos, Long::sum);
    }

    /** Time per probe within the current tick (cleared by {@link #tickStart()}), for attributing slow ticks. */
    private static final Map<String, Long> TICK = new LinkedHashMap<>();

    public static void tickStart() {
        TICK.clear();
    }

    /** The probes that took longest in the current tick, e.g. "population 12.3, encounters 1.0". */
    public static String tickTop(int n) {
        StringBuilder sb = new StringBuilder();
        TICK.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(n)
                .forEach(e -> sb.append(sb.isEmpty() ? "" : ", ").append(e.getKey()).append(' ').append(String.format("%.1f", e.getValue() / 1e6)));
        return sb.toString();
    }

    /** Returns the totals collected since the previous call and starts a new period. */
    public static Map<String, long[]> drain() {
        Map<String, long[]> out = new LinkedHashMap<>();
        TOTALS.forEach((k, v) -> out.put(k, v.clone()));
        TOTALS.values().forEach(v -> {
            v[0] = 0;
            v[1] = 0;
            v[2] = 0;
        });
        return out;
    }

    private Perf() {}
}
