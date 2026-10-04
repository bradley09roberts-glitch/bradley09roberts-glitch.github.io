package com.squidgame.core.redlight;

import com.squidgame.core.Difficulty;
import com.squidgame.core.util.Rng;

/**
 * Generates the doll's timeline for Red Light, Green Light. Pure data, fully deterministic for
 * a seed, and the single source of truth that the server doll controller, the NPC perception
 * model and the HUD all derive from.
 *
 * <p>One cycle:
 * <pre>
 *  t=0                         chantEnd                  chantEnd+allowance            redEnd
 *  |------- GREEN (chant) ------|---- TURN + allowance ----|------ WATCHED (eyes lit) ----|
 * </pre>
 * The chant is ten syllables ("mu-gung-hwa kko-chi pi-eot-seum-ni-da"); each cycle has its own
 * tempo, so the end is never predictable to the tick, but the song structure is public knowledge:
 * players and NPCs can anticipate the end from the syllable they hear.
 */
public final class DollCycle {
    public static final int SYLLABLES = 10;
    /** Relative length of each syllable within a chant (the last "da" is held longer). */
    private static final double[] WEIGHTS = {1.0, 1.1, 1.1, 0.9, 0.9, 0.8, 1.0, 1.0, 0.9, 1.7};
    private static final double WEIGHT_SUM;

    static {
        double s = 0;
        for (double w : WEIGHTS) {
            s += w;
        }
        WEIGHT_SUM = s;
    }

    /** Tick (relative to cycle start) at which each syllable starts, plus the chant end as the last element. */
    public final int[] syllableStart = new int[SYLLABLES + 1];
    public final int chantTicks;
    public final int allowanceTicks;
    public final int turnTicks;
    public final int watchedTicks;

    private DollCycle(int chantTicks, int allowanceTicks, int turnTicks, int watchedTicks) {
        this.chantTicks = chantTicks;
        this.allowanceTicks = allowanceTicks;
        this.turnTicks = turnTicks;
        this.watchedTicks = watchedTicks;
        double acc = 0;
        for (int i = 0; i < SYLLABLES; i++) {
            syllableStart[i] = (int) Math.round(acc / WEIGHT_SUM * chantTicks);
            acc += WEIGHTS[i];
        }
        syllableStart[SYLLABLES] = chantTicks;
    }

    /** Total length of the cycle in ticks. */
    public int totalTicks() {
        return chantTicks + allowanceTicks + watchedTicks;
    }

    /** Index (0-based) of the syllable that is being sung at {@code tick}, or -1 if the chant is over. */
    public int syllableAt(int tick) {
        if (tick < 0 || tick >= chantTicks) {
            return -1;
        }
        for (int i = SYLLABLES - 1; i >= 0; i--) {
            if (tick >= syllableStart[i]) {
                return i;
            }
        }
        return 0;
    }

    public enum Light {
        /** Chanting: moving is safe. */
        GREEN,
        /** Chant over, doll turning: grace period to stop. */
        TURNING,
        /** Eyes lit: movement is fatal. */
        RED
    }

    public Light lightAt(int tick) {
        if (tick < chantTicks) {
            return Light.GREEN;
        }
        if (tick < chantTicks + allowanceTicks) {
            return Light.TURNING;
        }
        return Light.RED;
    }

    /** Generates one cycle for the given difficulty. {@code progress} in [0,1] (how far the field has advanced). */
    public static DollCycle generate(Rng rng, Difficulty d, double progress) {
        Params p = paramsFor(d);
        // faster chants more often as the game goes on and on harder difficulties
        double fastChance = p.fastChance + 0.12 * progress;
        int chant;
        if (rng.chance(fastChance)) {
            chant = rng.rangeInt(p.fastChantMin, p.fastChantMax);
        } else {
            chant = rng.rangeInt(p.chantMin, p.chantMax);
        }
        int watched = rng.rangeInt(p.watchedMin, p.watchedMax);
        RedLightRules.Params rp = RedLightRules.params(d);
        return new DollCycle(chant, rp.allowanceTicks(), rp.dollTurnTicks(), watched);
    }

    /** Fixed cycle for tests. */
    public static DollCycle of(int chantTicks, int allowanceTicks, int turnTicks, int watchedTicks) {
        return new DollCycle(chantTicks, allowanceTicks, turnTicks, watchedTicks);
    }

    private record Params(int chantMin, int chantMax, int fastChantMin, int fastChantMax,
                          double fastChance, int watchedMin, int watchedMax) {
    }

    private static Params paramsFor(Difficulty d) {
        return switch (d) {
            case NORMAL -> new Params(90, 140, 55, 75, 0.15, 60, 110);
            case HARD -> new Params(70, 120, 42, 62, 0.28, 70, 130);
            case EXTREME -> new Params(55, 100, 32, 50, 0.40, 80, 150);
        };
    }
}
