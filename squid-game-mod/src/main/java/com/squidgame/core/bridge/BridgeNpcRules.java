package com.squidgame.core.bridge;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;

/**
 * How personality shapes an NPC's play on the bridge, as pure functions so the live behaviour and the unit-test
 * simulator use the very same numbers. None of these functions can see the route: an NPC only decides <i>how long
 * it hesitates</i>, <i>which lane it picks when it has no information</i> and <i>how likely it is to slip</i> when it does.
 */
public final class BridgeNpcRules {
    private BridgeNpcRules() {
    }

    /**
     * Probability that an NPC notices a public event (a panel holding or shattering). Everybody stares at the bridge
     * while waiting, so misses are rare; a miss costs a coin-flip gamble on a row the others already know.
     */
    public static double attention(Personality p, Difficulty d) {
        return Math.min(1.0, 0.985 + 0.015 * p.effectiveSkill(d));
    }

    /**
     * Probability of stepping onto the wrong panel although the safe lane of the row is known (a slip of nerve or of
     * memory). Grows with the pressure of the stall timer and with fear after seeing others fall; sharper
     * contestants (and harder difficulties, which add NPC skill) slip less.
     *
     * @param pressure fraction of the stall limit already used (0..1)
     * @param fear     0..1, how shaken the contestant is by recent falls
     */
    public static double slipChance(Personality p, Difficulty d, double pressure, double fear) {
        double skill = p.effectiveSkill(d);
        double base = 0.001 + 0.012 * (1.0 - skill);
        double stress = 0.5 + 1.5 * pressure + 0.6 * fear;
        return Rng.clamp(base * stress + (1.0 - p.courage()) * 0.03 * pressure * pressure, 0.0, 0.20);
    }

    /**
     * Ticks an NPC pauses on its panel before committing to a guess in a row nobody has revealed: reckless contestants
     * go almost at once, nervous or patient ones stare at the glass for many seconds. Always shorter than the stall
     * limit so a normal NPC never breaks the glass by dithering.
     */
    public static int hesitationTicks(Personality p, BridgeRules.Params params, double fear, Rng rng) {
        double seconds = Rng.lerp(1.0, 7.5, p.caution());
        seconds *= 1.0 + 0.6 * fear;
        seconds *= 1.15 - 0.3 * p.skill();
        seconds *= params.npcHesitationScale();
        seconds *= Math.max(0.5, 1.0 + rng.gaussian() * 0.22);
        int cap = (int) Math.round(params.stallLimitTicks() * 0.6);
        return (int) Rng.clamp(Math.round(seconds * 20), 10, cap);
    }

    /** Ticks between touching down and the next decision (recovering balance after the leap). */
    public static int settleTicks(Personality p, Rng rng) {
        double t = Rng.lerp(14, 6, p.reactionSpeed()) + rng.gaussian() * 1.5;
        return (int) Rng.clamp(Math.round(t), 5, 20);
    }

    /**
     * Whether this contestant freezes in a row that nobody has revealed and stays put until the stall limit breaks
     * the glass under them. Only very low courage does this, and rarely.
     */
    public static boolean freezes(Personality p, Difficulty d, boolean unknownRow, Rng rng) {
        double chance = Rng.clamp((0.30 - p.courage()) * 1.1, 0.0, 0.30) * (1.0 - 0.5 * p.effectiveSkill(d));
        if (!unknownRow) {
            chance *= 0.2;
        }
        return rng.chance(chance);
    }

    /**
     * Lane (0 or 1) picked in a row nobody has revealed. The route is uniformly random, so no strategy beats a coin
     * flip; what the personality changes is the <i>look</i> of the choice: a stable left/right preference and a
     * superstition about repeating or alternating the previous lane.
     *
     * @param previousLane the lane taken in the previous row, or -1
     */
    public static int guessLane(Personality p, int previousLane, Rng rng) {
        double p1 = 0.5 + 0.22 * bias(p);
        if (previousLane >= 0) {
            p1 += 0.16 * superstition(p) * (previousLane == 1 ? 1 : -1);
        }
        return rng.chance(Rng.clamp(p1, 0.15, 0.85)) ? 1 : 0;
    }

    /** Stable left/right preference in [-1, 1] derived from the personality (positive favours lane 1). */
    public static double bias(Personality p) {
        return Math.sin(p.courage() * 12.9898 + p.riskTolerance() * 78.233);
    }

    /** Stable superstition in [-1, 1]: positive repeats the previous lane, negative alternates. */
    public static double superstition(Personality p) {
        return Math.cos(p.patience() * 37.719 + p.skill() * 11.135);
    }

    /** Walking speed on a panel in blocks per tick: nervous contestants shuffle, confident ones step out. */
    public static double walkSpeed(Personality p) {
        return Rng.lerp(0.07, 0.115, Rng.clamp01(0.55 * p.courage() + 0.45 * (1.0 - p.caution())));
    }

    /** Ticks an NPC needs to react when the gate calls it. */
    public static int callReactionTicks(Personality p, Difficulty d, Rng rng) {
        return p.reactionDelayTicks(rng, d) + 4;
    }
}
