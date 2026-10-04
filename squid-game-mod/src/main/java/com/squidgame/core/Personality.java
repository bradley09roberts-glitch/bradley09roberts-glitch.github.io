package com.squidgame.core;

import com.squidgame.core.util.Rng;

/**
 * A contestant's persistent personality. All traits are in [0, 1]. They are generated once
 * per contestant, saved with the tournament, and *drive actual decisions* in every game
 * (see the consumers: red light stopping, dalgona carving speed, tug of war effort, marbles
 * wagers, bridge hesitation, final-fight tactics).
 */
public record Personality(Archetype archetype, float courage, float reactionSpeed, float patience,
                          float skill, float aggression, float cooperation, float riskTolerance) {

    /** Flavour archetypes: each biases the generated traits so the field has recognisable types. */
    public enum Archetype {
        //          courage reaction patience skill aggression cooperation risk
        NERVOUS(0.20f, 0.50f, 0.50f, 0.40f, 0.15f, 0.60f, 0.15f),
        RECKLESS(0.85f, 0.55f, 0.15f, 0.45f, 0.70f, 0.30f, 0.90f),
        CALCULATING(0.60f, 0.65f, 0.85f, 0.75f, 0.30f, 0.50f, 0.35f),
        BRAWLER(0.80f, 0.60f, 0.30f, 0.60f, 0.90f, 0.30f, 0.60f),
        TEAM_PLAYER(0.55f, 0.55f, 0.60f, 0.55f, 0.25f, 0.95f, 0.40f),
        LONE_WOLF(0.65f, 0.60f, 0.50f, 0.65f, 0.50f, 0.10f, 0.55f),
        VETERAN(0.70f, 0.75f, 0.70f, 0.80f, 0.45f, 0.55f, 0.45f),
        ROOKIE(0.40f, 0.40f, 0.40f, 0.30f, 0.30f, 0.50f, 0.50f),
        ATHLETE(0.70f, 0.85f, 0.45f, 0.70f, 0.55f, 0.50f, 0.60f),
        SCHEMER(0.50f, 0.60f, 0.70f, 0.60f, 0.35f, 0.20f, 0.50f);

        final float courage, reaction, patience, skill, aggression, cooperation, risk;

        Archetype(float courage, float reaction, float patience, float skill, float aggression,
                  float cooperation, float risk) {
            this.courage = courage;
            this.reaction = reaction;
            this.patience = patience;
            this.skill = skill;
            this.aggression = aggression;
            this.cooperation = cooperation;
            this.risk = risk;
        }

        public String translationKey() {
            return "squidgame.archetype." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static Personality generate(Rng rng) {
        Archetype a = Archetype.values()[rng.nextInt(Archetype.values().length)];
        return generate(rng, a);
    }

    public static Personality generate(Rng rng, Archetype a) {
        double sd = 0.12;
        return new Personality(a,
                trait(a.courage, rng, sd), trait(a.reaction, rng, sd), trait(a.patience, rng, sd),
                trait(a.skill, rng, sd), trait(a.aggression, rng, sd), trait(a.cooperation, rng, sd),
                trait(a.risk, rng, sd));
    }

    private static float trait(float base, Rng rng, double sd) {
        return (float) Rng.clamp(rng.gaussian(base, sd), 0.02, 0.98);
    }

    /** Skill after the difficulty's opponent-strength bonus, clamped to [0.05, 1]. */
    public double effectiveSkill(Difficulty d) {
        return Rng.clamp(skill + d.npcSkillBonus, 0.05, 1.0);
    }

    /**
     * Ticks between an event becoming perceivable and this contestant reacting to it. Fast
     * reactions are about 3 ticks (0.15 s), slow ones about 13; difficulty shaves a little off
     * (stronger opponents).
     */
    public int reactionDelayTicks(Rng rng, Difficulty d) {
        double base = Rng.lerp(13.0, 3.0, reactionSpeed);
        base *= 1.0 - 0.25 * d.npcSkillBonus / 0.25;
        double jitter = rng.gaussian(0, 1.2);
        return (int) Math.max(1, Math.round(base + jitter));
    }

    /** 0 (reckless) .. 1 (very cautious), blending courage, risk tolerance and patience. */
    public double caution() {
        return Rng.clamp01(0.45 * (1 - courage) + 0.35 * (1 - riskTolerance) + 0.20 * patience);
    }

    /** Probability that a risky option is taken given its nominal appeal in [0,1]. */
    public double riskAppetite(double appeal) {
        return Rng.clamp01(appeal * (0.35 + 0.9 * riskTolerance) - 0.25 * (1 - courage) * (1 - appeal));
    }

    /** Short descriptor used in debug output and result screens. */
    public String describe() {
        return String.format("%s [cou %.2f rea %.2f pat %.2f ski %.2f agg %.2f coo %.2f ris %.2f]",
                archetype, courage, reactionSpeed, patience, skill, aggression, cooperation, riskTolerance);
    }
}
