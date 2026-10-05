package com.squidgame.core.marbles;

import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pairing rules for the square: who accepts a partnership, whom an unpaired NPC asks, and how the remaining
 * contestants are paired at random when the time is up (the odd one out gets a bye). Pure and deterministic for a
 * given generator state, so every outcome is reproducible from the tournament seed.
 */
public final class PairingPlanner {
    private PairingPlanner() {
    }

    /** The final pairing: pairs of contestant numbers (smaller first) and the number with the bye, or -1. */
    public record Plan(List<int[]> pairs, int bye) {
        public boolean hasBye() {
            return bye >= 0;
        }
    }

    /** A possible partner as an unpaired NPC sees them. */
    public record Candidate(int number, double distance, boolean declinedUs) {
    }

    /**
     * Completes a partial pairing. {@code locked} maps a number to the partner it already agreed with (entries must be
     * symmetric and refer to listed numbers, anything else is ignored); every other contestant is paired at random,
     * and with an odd number of leftovers one of them gets the bye. Contestants with an agreed partner never get it.
     */
    public static Plan complete(List<Integer> numbers, Map<Integer, Integer> locked, Rng rng) {
        List<Integer> sorted = new ArrayList<>(new java.util.TreeSet<>(numbers));
        Set<Integer> present = new HashSet<>(sorted);
        Set<Integer> used = new HashSet<>();
        List<int[]> pairs = new ArrayList<>();
        for (int n : sorted) {
            Integer m = locked.get(n);
            if (m == null || m == n || used.contains(n) || used.contains(m) || !present.contains(m)
                    || !Integer.valueOf(n).equals(locked.get(m))) {
                continue;
            }
            pairs.add(new int[]{Math.min(n, m), Math.max(n, m)});
            used.add(n);
            used.add(m);
        }
        List<Integer> rest = new ArrayList<>();
        for (int n : sorted) {
            if (!used.contains(n)) {
                rest.add(n);
            }
        }
        rng.shuffle(rest);
        int bye = -1;
        if (rest.size() % 2 == 1) {
            bye = rest.remove(rest.size() - 1);
        }
        for (int i = 0; i + 1 < rest.size(); i += 2) {
            int a = rest.get(i), b = rest.get(i + 1);
            pairs.add(new int[]{Math.min(a, b), Math.max(a, b)});
        }
        return new Plan(pairs, bye);
    }

    /**
     * Chance that an NPC accepts a partnership offer. Cooperative contestants say yes almost always, loners mostly
     * refuse; a proposer who stands right next to them is harder to refuse, and an NPC that is waiting for the answer
     * to its own offer is reluctant to commit elsewhere.
     */
    public static double acceptChance(Personality p, double distance, boolean busyWithOwnOffer) {
        double base = 0.28 + 0.62 * p.cooperation() + 0.10 * (1.0 - Math.min(1.0, distance / 12.0))
                - 0.08 * p.aggression() * (1.0 - p.cooperation());
        if (busyWithOwnOffer) {
            base -= 0.30;
        }
        return Rng.clamp(base, 0.06, 0.97);
    }

    /** Index of the candidate an unpaired NPC asks next (nearest, with some personal taste), or -1 if there is none. */
    public static int pickTarget(List<Candidate> candidates, Rng rng) {
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            if (c.declinedUs()) {
                continue;
            }
            double score = c.distance() * (0.75 + 0.5 * rng.nextDouble());
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /**
     * When an unpaired NPC first goes looking for a partner, as a fraction of the pairing time: cooperative
     * contestants start almost at once, loners wait and see.
     */
    public static double firstProposalFraction(Personality p, Rng rng) {
        return 0.04 + 0.30 * (1.0 - p.cooperation()) * (0.4 + 0.6 * rng.nextDouble());
    }

    /** Think time (ticks at normal speed) before an NPC answers an offer. */
    public static int answerDelayTicks(Personality p, Rng rng) {
        return 20 + (int) Math.round((1.0 - p.reactionSpeed()) * 40 + p.patience() * 30) + rng.rangeInt(0, 14);
    }
}
