package com.squidgame.core.tug;

import com.squidgame.core.util.Rng;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Splits the contestants into heats and every heat into two balanced teams.
 *
 * <ul>
 *   <li>At most {@link TugRules#MAX_HEAT} (64) contestants per heat, so at most 32 per team: with more alive, shuffled
 *       groups of near-equal size play one after another (humans are dealt first so they spread over the heats).</li>
 *   <li>Team sizes differ by at most one. The summed rating (strength + stamina + cooperation) is balanced greedily and
 *       then improved by swapping NPCs between the teams; humans are spread alternately over both teams and never swapped.</li>
 *   <li>With an odd heat the smaller team gets a handicap (a force multiplier) that makes up most of the missing member;
 *       the balance takes it into account, so the smaller team does not also get the stronger members.</li>
 *   <li>Slot order: humans stand at the front (best view of the rope and the pit), NPCs behind them from weakest to
 *       strongest - the strongest is the anchor at the back.</li>
 * </ul>
 * Pure and deterministic for a given input and {@link Rng}.
 */
public final class TeamPlanner {
    private TeamPlanner() {
    }

    /** One contestant as the planner sees them. */
    public record Candidate(int id, boolean human, double rating) {
    }

    /** One heat: both teams in slot order (index 0 = nearest the gap) and the force multipliers. */
    public record Heat(List<Integer> teamA, List<Integer> teamB, double handicapA, double handicapB) {
        public int size() {
            return teamA.size() + teamB.size();
        }

        public List<Integer> team(int team) {
            return team == TugRules.TEAM_A ? teamA : teamB;
        }
    }

    public record Plan(List<Heat> heats) {
        public int contestants() {
            int n = 0;
            for (Heat h : heats) {
                n += h.size();
            }
            return n;
        }
    }

    /** Share of the missing member's contribution the smaller team of an odd heat is compensated with. */
    private static final double HANDICAP_SHARE = 0.7;

    /** Force multiplier of a team with {@code own} members against {@code other}: above 1 only for the smaller team. */
    public static double handicap(int own, int other) {
        return own > 0 && own < other ? 1.0 + HANDICAP_SHARE * ((double) other / own - 1.0) : 1.0;
    }

    public static Plan plan(List<Candidate> alive, Rng rng) {
        List<Candidate> all = new ArrayList<>(alive);
        all.sort(Comparator.comparingInt(Candidate::id));
        if (all.size() < 2) {
            return new Plan(List.of());
        }
        int heats = TugRules.heatsFor(all.size());
        List<List<Candidate>> groups = new ArrayList<>();
        if (heats == 1) {
            groups.add(all);
        } else {
            rng.shuffle(all);
            List<Candidate> ordered = new ArrayList<>();
            for (Candidate c : all) {
                if (c.human()) {
                    ordered.add(c);
                }
            }
            for (Candidate c : all) {
                if (!c.human()) {
                    ordered.add(c);
                }
            }
            for (int i = 0; i < heats; i++) {
                groups.add(new ArrayList<>());
            }
            for (int i = 0; i < ordered.size(); i++) {
                groups.get(i % heats).add(ordered.get(i));
            }
        }
        List<Heat> out = new ArrayList<>();
        for (List<Candidate> g : groups) {
            out.add(split(g, rng));
        }
        return new Plan(out);
    }

    /**
     * Splits one group (2..64) into two balanced teams. The balance is the rating sum times the force multiplier of the team,
     * so an odd heat is fair because of its handicap, not because the smaller team got the stronger members.
     */
    static Heat split(List<Candidate> group, Rng rng) {
        int n = group.size();
        int big = (n + 1) / 2;
        int small = n / 2;
        boolean smallIsA = rng.nextBoolean();
        double bonus = handicap(small, big);
        double[] weight = {smallIsA ? bonus : 1.0, smallIsA ? 1.0 : bonus};
        int[] capacity = {smallIsA ? small : big, smallIsA ? big : small};
        List<List<Candidate>> teams = List.of(new ArrayList<>(), new ArrayList<>());
        double[] sum = new double[2];

        List<Candidate> humans = new ArrayList<>();
        List<Candidate> npcs = new ArrayList<>();
        for (Candidate c : group) {
            (c.human() ? humans : npcs).add(c);
        }
        rng.shuffle(humans);
        boolean firstToA = rng.nextBoolean();
        for (int i = 0; i < humans.size(); i++) {
            int t = (i % 2 == 0) == firstToA ? 0 : 1;
            if (teams.get(t).size() >= capacity[t]) {
                t = 1 - t;
            }
            add(teams.get(t), t, humans.get(i), sum);
        }
        // strongest first, so the greedy fill balances well; the id breaks ties deterministically
        npcs.sort(Comparator.comparingDouble(Candidate::rating).reversed().thenComparingInt(Candidate::id));
        for (Candidate c : npcs) {
            int t = sum[0] * weight[0] <= sum[1] * weight[1] ? 0 : 1;
            if (teams.get(t).size() >= capacity[t]) {
                t = 1 - t;
            }
            add(teams.get(t), t, c, sum);
        }
        improve(teams.get(0), teams.get(1), sum, weight);
        return new Heat(slotOrder(teams.get(0)), slotOrder(teams.get(1)), weight[0], weight[1]);
    }

    private static void add(List<Candidate> team, int index, Candidate c, double[] sum) {
        team.add(c);
        sum[index] += c.rating();
    }

    /** Swaps NPCs between the teams while that makes the weighted rating sums closer (bounded, deterministic). */
    private static void improve(List<Candidate> a, List<Candidate> b, double[] sum, double[] weight) {
        for (int round = 0; round < 64; round++) {
            double diff = sum[0] * weight[0] - sum[1] * weight[1];
            double best = Math.abs(diff);
            int bi = -1, bj = -1;
            for (int i = 0; i < a.size(); i++) {
                if (a.get(i).human()) {
                    continue;
                }
                for (int j = 0; j < b.size(); j++) {
                    if (b.get(j).human()) {
                        continue;
                    }
                    double delta = a.get(i).rating() - b.get(j).rating();
                    double d = diff - delta * (weight[0] + weight[1]);
                    if (Math.abs(d) < best - 1e-12) {
                        best = Math.abs(d);
                        bi = i;
                        bj = j;
                    }
                }
            }
            if (bi < 0) {
                return;
            }
            Candidate x = a.get(bi), y = b.get(bj);
            a.set(bi, y);
            b.set(bj, x);
            sum[0] += y.rating() - x.rating();
            sum[1] += x.rating() - y.rating();
        }
    }

    private static List<Integer> slotOrder(List<Candidate> team) {
        List<Candidate> sorted = new ArrayList<>(team);
        sorted.sort(Comparator.comparing(Candidate::human).reversed()
                .thenComparingDouble(Candidate::rating).thenComparingInt(Candidate::id));
        List<Integer> ids = new ArrayList<>(sorted.size());
        for (Candidate c : sorted) {
            ids.add(c.id());
        }
        return ids;
    }
}
