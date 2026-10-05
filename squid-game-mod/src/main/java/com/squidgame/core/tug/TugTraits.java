package com.squidgame.core.tug;

import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;

/**
 * What a contestant's {@link Personality} means for the Tug of War: how hard they pull, how long they last and how good
 * a teammate they are. Humans get the traits generated for them like everybody else, so the same formulas rate everyone.
 */
public final class TugTraits {
    private TugTraits() {
    }

    /** Pull strength in units of one average member (about 0.72 - 1.28; an average contestant is 1.0). */
    public static double strength(Personality p, double skillBonus) {
        double skill = Rng.clamp(p.skill() + skillBonus, 0.05, 1.0);
        return 0.72 + 0.34 * skill + 0.12 * p.courage() + 0.10 * p.aggression();
    }

    public static double strength(Personality p) {
        return strength(p, 0.0);
    }

    /** Stamina capacity: costs are divided by it (about 0.7 - 1.35; average 1.0). */
    public static double endurance(Personality p) {
        return 0.70 + 0.30 * p.patience() + 0.25 * p.skill() + 0.10 * p.courage();
    }

    /**
     * Single number used to balance the teams: skill-driven strength, stamina and cooperation (a cooperative member
     * synchronises better, which is worth more than raw strength in this game).
     */
    public static double rating(Personality p) {
        return strength(p) + 0.30 * (endurance(p) - 1.0) + 0.25 * (p.cooperation() - 0.5);
    }
}
