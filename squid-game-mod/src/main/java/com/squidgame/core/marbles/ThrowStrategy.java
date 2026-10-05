package com.squidgame.core.marbles;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.marbles.ThrowModel.Vec;
import com.squidgame.core.util.Rng;

/**
 * How an NPC plans a throw. It uses the same controls as a human: it picks a spot to aim at (the bullseye plus an
 * error that shrinks with skill and grows with nerves) and a charge to release at (the ideal charge for that spot plus
 * a timing error), then the shared {@link ThrowModel} adds the tremor and flies the marble. Nothing here is hidden
 * information - the target and the distance are painted on the ground.
 */
public final class ThrowStrategy {
    private ThrowStrategy() {
    }

    /** The spot an NPC aims at and the number of ticks it winds up (= charges) before releasing. */
    public record Plan(Vec aim, int chargeTicks) {
    }

    /** Standard deviation (blocks) of the aiming error in each axis. */
    public static double aimSigma(double skill, double nerves) {
        return Rng.lerp(1.05, 0.30, skill) * (1.0 + 0.45 * nerves);
    }

    /** Standard deviation (ticks) of the release timing error. */
    public static double timingSigma(double skill, double nerves) {
        return Rng.lerp(2.1, 0.80, skill) * (1.0 + 0.45 * nerves);
    }

    /**
     * @param pressure 0..1, how much is at stake for this NPC (behind on marbles, down to its last marbles); scaled by
     *                 the personality's lack of courage into "nerves"
     */
    public static Plan plan(Personality p, Difficulty d, ThrowModel.Params tp, Vec origin, Vec bullseye,
                            double pressure, Rng rng) {
        double skill = p.effectiveSkill(d);
        double nerves = Rng.clamp01(pressure) * (1.0 - p.courage());
        double as = aimSigma(skill, nerves);
        Vec aim = new Vec(bullseye.x() + rng.gaussian(0, as), bullseye.y(), bullseye.z() + rng.gaussian(0, as));
        double ideal = ThrowModel.idealCharge(tp, origin, aim);
        double charge = ideal + rng.gaussian(0, timingSigma(skill, nerves));
        int ticks = (int) Math.round(Rng.clamp(charge, tp.minChargeTicks(), tp.maxChargeTicks()));
        return new Plan(aim, ticks);
    }
}
