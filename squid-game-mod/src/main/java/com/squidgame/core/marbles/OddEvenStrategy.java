package com.squidgame.core.marbles;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.marbles.OddEvenRound.Reveal;
import com.squidgame.core.util.Rng;

import java.util.ArrayList;
import java.util.List;

/**
 * How an NPC plays odd-or-even. Every input is something a human at the table could know: the marble counts, the
 * legal ranges and the <i>revealed</i> history of the earlier rounds - never the opponent's current fist or call.
 *
 * <ul>
 *   <li><b>Calling</b> mixes the structure of the game (a holder with one marble can only hide an odd number), the
 *       opponent's revealed habits (recency-weighted frequency, streaks and strict alternation) and a personal taste
 *       for one parity; the weight on the evidence grows with skill and patience, the rest is noise.</li>
 *   <li><b>Wagering</b> turns confidence into stake through the personality's risk appetite: cautious contestants
 *       bet one or two marbles, gamblers go big - especially when they are ahead, or desperate and brave.</li>
 *   <li><b>Holding</b> hides the parity the opponent has called least often, but falls back to pure randomness once
 *       the opponent has read it twice in a row.</li>
 * </ul>
 */
public final class OddEvenStrategy {
    private OddEvenStrategy() {
    }

    /** The parity an NPC calls and how sure it is (0 = coin flip, 1 = certain). */
    public record Call(Parity parity, double confidence) {
    }

    /** Probability of an odd number if the holder picks uniformly from 1..n. */
    public static double uniformOddProbability(int holderMarbles) {
        return ((holderMarbles + 1) / 2) / (double) Math.max(1, holderMarbles);
    }

    /** A stable personal taste for one parity in [-0.1, 0.1] (positive = odd). */
    static double bias(Personality p) {
        double v = (p.courage() * 7.3 + p.patience() * 3.1 + p.aggression() * 1.7) % 1.0;
        return (v - 0.5) * 0.2;
    }

    public static Call call(Personality p, Difficulty d, Side me, int holderMarbles, List<Reveal> history, Rng rng) {
        double skill = p.effectiveSkill(d);
        if (holderMarbles <= 1 && rng.chance(0.45 + 0.55 * skill)) {
            return new Call(Parity.ODD, 1.0);
        }
        List<Parity> seq = new ArrayList<>();
        for (Reveal r : history) {
            if (r.holder() != me) {
                seq.add(r.hiddenParity());
            }
        }
        int n = seq.size();
        double pOdd = uniformOddProbability(holderMarbles);
        if (n > 0) {
            double w = 1, weightSum = 0, oddSum = 0;
            for (int i = n - 1; i >= 0; i--) {
                weightSum += w;
                if (seq.get(i) == Parity.ODD) {
                    oddSum += w;
                }
                w *= 0.78;
            }
            double estimate = (oddSum + 1.0) / (weightSum + 2.0);
            if (n >= 3) {
                Parity a = seq.get(n - 1), b = seq.get(n - 2), c = seq.get(n - 3);
                if (a != b && b != c) {
                    estimate = 0.5 * estimate + 0.5 * (a == Parity.ODD ? 0.12 : 0.88);
                } else if (a == b && b == c) {
                    estimate = 0.5 * estimate + 0.5 * (a == Parity.ODD ? 0.85 : 0.15);
                }
            }
            double weight = skill * n / (n + 2.0) * (0.55 + 0.45 * p.patience());
            pOdd += (estimate - 0.5) * weight * 1.2;
        }
        pOdd += bias(p) + rng.gaussian(0, 0.05 + 0.15 * (1.0 - skill));
        pOdd = Rng.clamp(pOdd, 0.02, 0.98);
        return new Call(pOdd >= 0.5 ? Parity.ODD : Parity.EVEN, Math.abs(pOdd - 0.5) * 2.0);
    }

    /**
     * How many marbles the guesser bets. {@code forced} &gt; 0 is the fixed overtime stake.
     *
     * @return a legal wager: 1..min(own, opponent's)
     */
    public static int wager(Personality p, int myMarbles, int oppMarbles, Call call, int forced, Rng rng) {
        if (forced > 0) {
            return forced;
        }
        int max = Math.min(myMarbles, oppMarbles);
        if (max <= 1) {
            return 1;
        }
        double appeal = 0.15 + 0.85 * call.confidence();
        double appetite = p.riskAppetite(appeal);
        double lead = (myMarbles - oppMarbles) / (double) Math.max(myMarbles, oppMarbles);
        double boldness = 1.0 - p.caution();
        double situation = 1.0 + boldness * 0.9 * lead;
        if (lead < -0.4) {
            situation += 0.5 * p.courage() * (-lead - 0.4);
        }
        double share = Rng.clamp01(Math.pow(appetite, 1.6) * situation);
        int w = 1 + (int) Math.round(share * (max - 1) + rng.gaussian(0, 0.45));
        if (p.caution() > 0.62) {
            w = Math.min(w, max >= 8 ? 3 : 2);
        }
        return Rng.clamp(w, 1, max);
    }

    /** How many marbles the holder hides: 1..myMarbles. */
    public static int hold(Personality p, Difficulty d, Side me, int myMarbles, List<Reveal> history, Rng rng) {
        if (myMarbles <= 1) {
            return 1;
        }
        double skill = p.effectiveSkill(d);
        List<Parity> calls = new ArrayList<>();
        List<Boolean> read = new ArrayList<>();
        for (Reveal r : history) {
            if (r.holder() == me) {
                calls.add(r.guess());
                read.add(r.correct());
            }
        }
        int n = calls.size();
        double pHoldOdd = 0.5;
        if (n > 0) {
            double w = 1, weightSum = 0, oddSum = 0;
            for (int i = n - 1; i >= 0; i--) {
                weightSum += w;
                if (calls.get(i) == Parity.ODD) {
                    oddSum += w;
                }
                w *= 0.78;
            }
            double calledOdd = (oddSum + 1.0) / (weightSum + 2.0);
            double weight = skill * n / (n + 2.0) * (0.5 + 0.5 * p.patience());
            pHoldOdd = 0.5 + (0.5 - calledOdd) * weight * 1.4;
            if (n >= 2 && read.get(n - 1) && read.get(n - 2)) {
                pHoldOdd = 0.5 + (pHoldOdd - 0.5) * 0.3;
            }
        }
        pHoldOdd += bias(p) * 0.6 + rng.gaussian(0, 0.08 + 0.18 * (1.0 - skill));
        Parity target = rng.chance(Rng.clamp(pHoldOdd, 0.05, 0.95)) ? Parity.ODD : Parity.EVEN;
        int k = 1 + (int) (Math.pow(rng.nextDouble(), 1.7) * myMarbles);
        k = Rng.clamp(k, 1, myMarbles);
        if (Parity.of(k) != target) {
            k = k + 1 <= myMarbles ? k + 1 : k - 1;
        }
        return k;
    }

    /** Fraction of the decision timer an NPC takes to think before locking in (patient contestants deliberate). */
    public static double thinkFraction(Personality p, Rng rng) {
        double f = 0.07 + 0.28 * p.patience() * (0.55 + 0.45 * (1.0 - p.reactionSpeed())) + rng.gaussian(0, 0.03);
        return Rng.clamp(f, 0.05, 0.40);
    }
}
