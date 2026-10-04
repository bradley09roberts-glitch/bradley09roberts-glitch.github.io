package com.squidgame.tournament;

/**
 * Tiny always-on section profiler for the server thread (all game logic runs there). Sections accumulate nanoseconds
 * over a window of {@link #WINDOW_TICKS} server ticks; {@code /squid debug perf} prints the averages so the cost of a
 * tournament with many NPCs can be judged without an external profiler. Cost per measurement: two nanoTime calls.
 */
public final class Profiler {
    public enum Section {
        /** Everything the tournament manager does in a tick (includes GAME). */
        MANAGER("tournament manager"),
        /** The running game's own tick. */
        GAME("game logic"),
        /** HUD synchronisation to the players. */
        HUD("HUD sync"),
        /** Whole entity tick of every NPC contestant (AI, navigation, physics, animation state). */
        NPC_ENTITY("NPC entity ticks"),
        /** Only the per-game decision code of the NPCs. */
        NPC_BEHAVIOR("NPC behaviours");

        final String label;

        Section(String label) {
            this.label = label;
        }
    }

    public static final int WINDOW_TICKS = 100;
    private static final int N = Section.values().length;
    private static final long[] ACC = new long[N];
    private static final long[] CALLS = new long[N];
    private static final double[] AVG_MS = new double[N];
    private static final double[] AVG_CALLS = new double[N];
    private static int ticks;
    private static long tickStartNanos = System.nanoTime();
    private static double tickAccMs;
    private static double avgTickMs;
    private static double worstTickMs;
    private static double windowWorst;

    private Profiler() {
    }

    public static long start() {
        return System.nanoTime();
    }

    public static void end(Section s, long startNanos) {
        ACC[s.ordinal()] += System.nanoTime() - startNanos;
        CALLS[s.ordinal()]++;
    }

    /** Call at the very start of every server tick (START_SERVER_TICK). */
    public static void beginTick() {
        tickStartNanos = System.nanoTime();
    }

    /** Call once at the end of every server tick (END_SERVER_TICK): closes the measurement of the whole tick. */
    public static void tick() {
        double ms = (System.nanoTime() - tickStartNanos) / 1_000_000.0;
        tickAccMs += ms;
        windowWorst = Math.max(windowWorst, ms);
        if (++ticks >= WINDOW_TICKS) {
            for (int i = 0; i < N; i++) {
                AVG_MS[i] = ACC[i] / 1_000_000.0 / ticks;
                AVG_CALLS[i] = CALLS[i] / (double) ticks;
                ACC[i] = 0;
                CALLS[i] = 0;
            }
            avgTickMs = tickAccMs / WINDOW_TICKS;
            tickAccMs = 0;
            worstTickMs = windowWorst;
            windowWorst = 0;
            ticks = 0;
        }
    }

    /** Human readable report of the last completed window. */
    public static String report(int npcCount) {
        StringBuilder sb = new StringBuilder("Squid Game profiler (average over the last ").append(WINDOW_TICKS).append(" ticks, ")
                .append(npcCount).append(" NPCs)\n");
        for (Section s : Section.values()) {
            double ms = AVG_MS[s.ordinal()];
            double calls = AVG_CALLS[s.ordinal()];
            sb.append(String.format("  %-20s %6.3f ms/tick", s.label, ms));
            if (calls > 1.5) {
                sb.append(String.format("  (%.0f calls/tick, %.4f ms each)", calls, ms / calls));
            }
            sb.append('\n');
        }
        sb.append(String.format("  whole server tick: %.2f ms average, %.1f ms worst (budget 50 ms)", avgTickMs, worstTickMs));
        return sb.toString();
    }
}
