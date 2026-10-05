package com.squidgame.core.marbles;

import com.squidgame.core.Difficulty;
import com.squidgame.core.util.Rng;

import java.util.Locale;

/**
 * The difficulty table of the marbles game: starting stack, clocks, decision timers, round budgets and the throw
 * controls. Harder difficulties mean fewer marbles, less time to think, a narrower charge window and a larger
 * tremor on top of the stronger NPC opponents ({@link Difficulty#npcSkillBonus}). All durations are in seconds at
 * normal speed; the game scales them with the configured {@code timeScale}.
 */
public final class MarblesRules {
    private MarblesRules() {
    }

    /** What a pair plays. */
    public enum Variant {
        ODD_EVEN, THROW;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The {@code marblesVariant} config value: one variant for everybody, or a seeded pick per pair. */
    public enum VariantSetting {
        ODD_EVEN, THROW, MIXED
    }

    public record Params(int startMarbles, int oddEvenRounds, int throwRounds, int stakeCap,
                         double totalSeconds, double pairingSeconds, double introSeconds,
                         double decisionSeconds, double overtimeDecisionSeconds,
                         double throwSeconds, double overtimeThrowSeconds,
                         double revealSeconds, double resultSeconds, double flightSeconds,
                         double landingHoldSeconds, double overtimeReserveSeconds, ThrowModel.Params throwModel) {

        /** Longest time a drawn-out tie can need after the time call (must fit in {@link #overtimeReserveSeconds}). */
        public double worstCaseOvertimeSeconds() {
            double oddEven = introSeconds + overtimeDecisionSeconds + revealSeconds + resultSeconds;
            double sudden = 2 * (overtimeThrowSeconds + flightSeconds + landingHoldSeconds) + resultSeconds;
            return Math.max(oddEven, sudden);
        }
    }

    public static Params params(Difficulty d) {
        int marbles = Math.max(3, (int) Math.round(10 * d.resourceScale));
        return switch (d) {
            case NORMAL -> new Params(marbles, 12, 8, 4, 210, 45, 4, 25, 12, 20, 10, 3.5, 2.5, 4, 1.5, 40,
                    new ThrowModel.Params(36, 4, 0.25, 0.62, 40, 1.0, 0.020));
            case HARD -> new Params(marbles, 10, 7, 3, 180, 40, 4, 20, 10, 16, 8, 3.5, 2.5, 4, 1.5, 34,
                    new ThrowModel.Params(30, 4, 0.25, 0.62, 40, 1.6, 0.032));
            case EXTREME -> new Params(marbles, 8, 6, 2, 150, 35, 4, 15, 8, 12, 6, 3.5, 2.5, 4, 1.5, 28,
                    new ThrowModel.Params(24, 4, 0.25, 0.62, 40, 2.4, 0.045));
        };
    }

    /** Parses the {@code marblesVariant} config value; anything unknown means {@code mixed}. */
    public static VariantSetting parseVariant(String config) {
        if (config == null) {
            return VariantSetting.MIXED;
        }
        return switch (config.trim().toLowerCase(Locale.ROOT)) {
            case "odd_even", "oddeven", "odd-even", "odd" -> VariantSetting.ODD_EVEN;
            case "throw", "target_throw", "target-throw", "target" -> VariantSetting.THROW;
            default -> VariantSetting.MIXED;
        };
    }

    /** The variant one pair plays: fixed by the setting, or a coin flip of the given (per-pair) generator for mixed. */
    public static Variant variantFor(VariantSetting setting, Rng pairRng) {
        return switch (setting) {
            case ODD_EVEN -> Variant.ODD_EVEN;
            case THROW -> Variant.THROW;
            case MIXED -> pairRng.nextBoolean() ? Variant.ODD_EVEN : Variant.THROW;
        };
    }
}
