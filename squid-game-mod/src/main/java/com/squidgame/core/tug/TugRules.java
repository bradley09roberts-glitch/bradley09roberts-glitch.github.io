package com.squidgame.core.tug;

import com.squidgame.core.Difficulty;
import com.squidgame.core.util.Rng;

/**
 * Numbers and small pure formulas of the Tug of War. Everything here is deterministic and free of Minecraft types so
 * the simulation ({@link TugSim}), the NPC policy and the tests share one source of truth. The constants were tuned with
 * Monte Carlo heats (see {@code TugMatchTest}): equal teams swing and are decided in about a minute, a good human is worth
 * several average members, and a team that burns its stamina early loses late.
 *
 * <h2>The rules in short</h2>
 * <ul>
 *   <li>Two teams pull a rope across a pit. The rope offset runs from -1 (team A's edge) to +1 (team B's edge); the team
 *       that pulls the rope all the way to its own side wins, the other team falls.</li>
 *   <li>Every member has <b>stamina</b>. <b>Pulling</b> drains it fast, <b>bracing</b> (leaning back) drains it slowly and
 *       anchors the team against being dragged, <b>resting</b> (doing neither) recovers it. An exhausted member (stamina
 *       0) can only brace until the stamina has recovered a little.</li>
 *   <li>A shared rhythm beats every 1.2 - 1.6 s. A <b>heave</b> inside the beat window adds a burst of force, the closer to
 *       the beat the bigger it is, and the more of the team heaves together the bigger every burst gets (the sync bonus,
 *       up to x1.8). A mistimed heave costs stamina and gives nothing.</li>
 * </ul>
 */
public final class TugRules {
    private TugRules() {
    }

    public static final int TEAM_A = 0;
    public static final int TEAM_B = 1;

    /** Slot markers per team in the arena. */
    public static final int MAX_PER_TEAM = 32;
    /** Largest heat (two full teams); with more contestants alive the game runs several heats. */
    public static final int MAX_HEAT = 2 * MAX_PER_TEAM;

    // ------------------------------------------------------------------ difficulty

    /**
     * Difficulty dependent numbers.
     *
     * @param heatLimitTicks     time limit of one heat (100 / 85 / 70 s)
     * @param suddenDeathTicks   length of the sudden death after an exact tie (10 s)
     * @param window             half width of the heave window in ticks (3.0 / 2.4 / 1.8)
     * @param costScale          multiplies every stamina cost (1 / resourceScale: 1.0 / 1.33 / 2.0)
     * @param npcSkillBonus      added to the skill of the NPC team that opposes a human team
     * @param drag               rope drag; tired teams (higher costs) pull a weaker rope, so the drag shrinks with them and
     *                           the heat stays decisive
     */
    public record Params(int heatLimitTicks, int suddenDeathTicks, double window, double costScale, double npcSkillBonus,
                         double drag) {
    }

    public static Params params(Difficulty d) {
        int heatSeconds = switch (d) {
            case NORMAL -> 100;
            case HARD -> 85;
            case EXTREME -> 70;
        };
        return new Params(heatSeconds * 20, 10 * 20, 3.0 * d.toleranceScale, 1.0 / d.resourceScale, d.npcSkillBonus,
                DRAG * Math.pow(d.resourceScale, DRAG_EXPONENT));
    }

    /** Share of the difficulty's NPC skill bonus that also adds to the opposing team's pulling strength (timing gets all of it). */
    public static final double NPC_STRENGTH_SHARE = 0.4;

    // ------------------------------------------------------------------ timeline of a game (used for the time limit)

    /** Longest time the teams get to walk onto the platforms before the count-in starts. */
    public static final int INTRO_MAX_TICKS = 18 * 20;
    /** Count-in: this many beats tick before the pulling starts. */
    public static final int COUNT_IN_BEATS = 4;
    /** From the moment a team loses until everybody is down and the heat is over. */
    public static final int FALL_TICKS = 9 * 20;
    /** Pause between two heats (platform repaired, next teams in place). */
    public static final int BETWEEN_TICKS = 5 * 20;

    /** Upper bound for the duration of {@code heats} sequential heats (the tournament's game time limit). */
    public static int totalTicks(Params p, int heats) {
        int perHeat = INTRO_MAX_TICKS + COUNT_IN_BEATS * MAX_BEAT_PERIOD + p.heatLimitTicks() + p.suddenDeathTicks() + FALL_TICKS
                + BETWEEN_TICKS;
        return Math.max(1, heats) * perHeat;
    }

    /** Number of heats needed for {@code alive} contestants. */
    public static int heatsFor(int alive) {
        return alive <= MAX_HEAT ? 1 : (alive + MAX_HEAT - 1) / MAX_HEAT;
    }

    // ------------------------------------------------------------------ rhythm

    public static final int MIN_BEAT_PERIOD = 24;
    public static final int MAX_BEAT_PERIOD = 32;

    /** Beat period of a heat: 1.2 - 1.6 s (24 - 32 ticks). */
    public static int beatPeriod(Rng rng) {
        return rng.rangeInt(MIN_BEAT_PERIOD, MAX_BEAT_PERIOD);
    }

    /** Forgiveness (in ticks) granted to human presses on top of the measured latency compensation (rounding, jitter). */
    public static final double HUMAN_FORGIVENESS = 1.0;
    /** Largest network latency (ticks) the server compensates for. */
    public static final int MAX_LATENCY_COMPENSATION = 6;
    /** Minimum ticks between two heaves of one contestant (anything faster is button mashing). */
    public static final int MIN_HEAVE_GAP = 8;

    /** A heave at the very edge of the window is worth this much less than one dead on the beat. */
    private static final double QUALITY_DROP = 0.8;
    private static final double QUALITY_EXPONENT = 1.2;

    /**
     * Quality of a heave pressed {@code absErr} ticks away from the beat: 1.0 dead on the beat, falling to 0.2 at the edge
     * of the window; 0 outside the window (mistimed).
     */
    public static double heaveQuality(double absErr, double window) {
        if (absErr > window + 1e-9) {
            return 0.0;
        }
        double x = window <= 0 ? 0 : Math.min(1.0, absErr / window);
        return 1.0 - QUALITY_DROP * Math.pow(x, QUALITY_EXPONENT);
    }

    // ------------------------------------------------------------------ stamina

    /** Stamina drained per tick by pulling with full effort (empty after about 22 s). */
    public static final double PULL_DRAIN = 0.0023;
    /** Bracing drains this fraction of the pulling cost. */
    public static final double BRACE_DRAIN_FRACTION = 0.30;
    /** Stamina recovered per tick while resting (empty to full in 20 s). */
    public static final double REST_RECOVERY = 1.0 / (20 * 20);
    /** An exhausted member that braces recovers at this fraction of the resting rate. */
    public static final double EXHAUSTED_BRACE_RECOVERY = 0.25;
    /** Stamina cost of one heave, hit or miss (a heave on every beat doubles the cost of pulling). */
    public static final double HEAVE_COST = 0.04;
    /** An exhausted member may pull again once its stamina is back above this. */
    public static final double RECOVER_THRESHOLD = 0.35;

    /** Force multiplier from stamina: 1.0 down to half stamina, then falling smoothly to 0.35 at zero. */
    public static double staminaFactor(double stamina) {
        double t = Math.max(0.0, Math.min(1.0, stamina / 0.5));
        double smooth = t * t * (3 - 2 * t);
        return 0.35 + 0.65 * smooth;
    }

    // ------------------------------------------------------------------ forces and rope

    /**
     * Force of a perfect heave in units of one member's full pull, multiplied by the burst envelope (which peaks at 2.2).
     * A burst lasts only a few ticks, so over a whole beat it is worth about 4 members' pulling: the rhythm is what moves
     * the rope.
     */
    public static final double HEAVE_GAIN = 9.0;
    /** A team in which everybody heaves together multiplies its bursts by 1 + this (x1.8). */
    public static final double SYNC_BONUS = 0.8;
    /** Brace anchor strength relative to a full pull. */
    public static final double ANCHOR_STRENGTH = 0.45;
    /** Pull of the rope back to the centre, in units of a full team pull at full offset. */
    public static final double SPRING = 0.07;
    /** Rope drag on Normal and mass (offset is per tick; force is in units of a full team pull). */
    public static final double DRAG = 50.0;
    /** How strongly the drag follows the resource scale of the difficulty. */
    public static final double DRAG_EXPONENT = 1.2;
    public static final double MASS = 3000.0;
    /**
     * A handful of members decide the rope with single heaves (one clean heave against one miss would move a 2 v 2 rope across
     * the whole pit), so small teams pull a rope that is heavier by {@link #ropeWeight}: it responds as if each team had at
     * least {@code INERTIA_BASE + INERTIA_SLOPE * n} members, and a 2 v 2 still lasts about half a minute.
     */
    public static final double INERTIA_BASE = 3.8;
    public static final double INERTIA_SLOPE = 0.53;
    /** Rope offset closer to zero than this counts as a tie at the timeout. */
    public static final double TIE_EPSILON = 0.02;

    /** How much heavier the rope feels than its team size suggests: 1.0 from 8 members per team up, about 2.4 for 2 v 2. */
    public static double ropeWeight(double meanTeamSize) {
        double n = Math.max(1.0, meanTeamSize);
        return Math.max(n, INERTIA_BASE + INERTIA_SLOPE * n) / n;
    }

    /** Ticks a heave burst lasts: a short rise and an exponential decay. */
    public static final int PULSE_TICKS = 18;
    private static final int PULSE_RISE = 2;
    private static final double PULSE_DECAY = 4.5;

    /**
     * Shape of one heave burst by its age in ticks: 0.5 and 1.0 while it builds up, then a kick of 2.2 at its peak that
     * decays with a time constant of 4.5 ticks; 0 beyond {@link #PULSE_TICKS}. It also measures how much of the team is in
     * a burst (see {@link TugSim#sync}): the sync bonus is full when about half the team heaves together.
     */
    public static double pulseEnvelope(int age) {
        if (age < 0 || age >= PULSE_TICKS) {
            return 0.0;
        }
        if (age < PULSE_RISE) {
            return (age + 1) / (double) PULSE_RISE;
        }
        return Math.exp(-(age - PULSE_RISE + 1) / PULSE_DECAY) * Math.E;
    }
}
