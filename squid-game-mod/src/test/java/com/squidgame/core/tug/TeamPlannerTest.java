package com.squidgame.core.tug;

import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TeamPlannerTest {
    private static List<TeamPlanner.Candidate> candidates(int n, int humans, long seed) {
        Rng rng = new Rng(seed);
        List<TeamPlanner.Candidate> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            out.add(new TeamPlanner.Candidate(i * 3 + 1, i <= humans, TugTraits.rating(Personality.generate(rng))));
        }
        return out;
    }

    private static double sum(List<Integer> ids, List<TeamPlanner.Candidate> all) {
        double s = 0;
        for (int id : ids) {
            for (TeamPlanner.Candidate c : all) {
                if (c.id() == id) {
                    s += c.rating();
                }
            }
        }
        return s;
    }

    @Test
    void everyoneIsPlacedExactlyOnceAndTeamsAreWithinOne() {
        for (int n = 2; n <= 128; n++) {
            List<TeamPlanner.Candidate> c = candidates(n, n % 5, 100 + n);
            TeamPlanner.Plan plan = TeamPlanner.plan(c, new Rng(n));
            assertEquals(TugRules.heatsFor(n), plan.heats().size(), "heats for " + n);
            Set<Integer> seen = new HashSet<>();
            for (TeamPlanner.Heat h : plan.heats()) {
                assertTrue(Math.abs(h.teamA().size() - h.teamB().size()) <= 1, "team sizes in a heat of " + h.size());
                assertTrue(h.teamA().size() <= TugRules.MAX_PER_TEAM && h.teamB().size() <= TugRules.MAX_PER_TEAM);
                assertTrue(h.size() <= TugRules.MAX_HEAT);
                for (int id : h.teamA()) {
                    assertTrue(seen.add(id), "duplicate " + id);
                }
                for (int id : h.teamB()) {
                    assertTrue(seen.add(id), "duplicate " + id);
                }
            }
            assertEquals(n, seen.size());
            assertEquals(n, plan.contestants());
        }
    }

    @Test
    void heatsOfALargeFieldAreEvenlySized() {
        TeamPlanner.Plan plan = TeamPlanner.plan(candidates(128, 0, 1), new Rng(2));
        assertEquals(2, plan.heats().size());
        assertEquals(64, plan.heats().get(0).size());
        assertEquals(64, plan.heats().get(1).size());
        plan = TeamPlanner.plan(candidates(65, 0, 1), new Rng(2));
        assertEquals(2, plan.heats().size());
        assertEquals(1, Math.abs(plan.heats().get(0).size() - plan.heats().get(1).size()));
        plan = TeamPlanner.plan(candidates(64, 0, 1), new Rng(2));
        assertEquals(1, plan.heats().size());
    }

    @Test
    void tooFewContestantsMakeNoHeat() {
        assertTrue(TeamPlanner.plan(List.of(), new Rng(1)).heats().isEmpty());
        assertTrue(TeamPlanner.plan(candidates(1, 1, 1), new Rng(1)).heats().isEmpty());
        TeamPlanner.Plan two = TeamPlanner.plan(candidates(2, 0, 1), new Rng(1));
        assertEquals(1, two.heats().size());
        assertEquals(1, two.heats().get(0).teamA().size());
        assertEquals(1, two.heats().get(0).teamB().size());
    }

    @Test
    void ratingsAreBalancedMuchBetterThanBySplittingInHalf() {
        double planned = 0, naive = 0;
        for (int seed = 0; seed < 60; seed++) {
            List<TeamPlanner.Candidate> c = candidates(32, 0, 500 + seed);
            TeamPlanner.Heat h = TeamPlanner.plan(c, new Rng(seed)).heats().get(0);
            planned += Math.abs(sum(h.teamA(), c) - sum(h.teamB(), c));
            List<TeamPlanner.Candidate> sorted = new ArrayList<>(c);
            Collections.sort(sorted, (x, y) -> Double.compare(x.rating(), y.rating()));
            double a = 0, b = 0;
            for (int i = 0; i < sorted.size(); i++) {
                if (i < 16) {
                    a += sorted.get(i).rating();
                } else {
                    b += sorted.get(i).rating();
                }
            }
            naive += Math.abs(a - b);
        }
        assertTrue(planned / 60 < 0.15, "average rating gap " + planned / 60);
        assertTrue(planned < naive / 10);
    }

    @Test
    void humansAreSpreadOverBothTeams() {
        for (int humans = 2; humans <= 9; humans++) {
            for (int seed = 0; seed < 10; seed++) {
                List<TeamPlanner.Candidate> c = candidates(20 + seed % 3, humans, 900 + seed);
                TeamPlanner.Heat h = TeamPlanner.plan(c, new Rng(seed)).heats().get(0);
                int a = 0, b = 0;
                for (TeamPlanner.Candidate k : c) {
                    if (k.human()) {
                        if (h.teamA().contains(k.id())) {
                            a++;
                        } else {
                            b++;
                        }
                    }
                }
                assertTrue(Math.abs(a - b) <= 1, humans + " humans split " + a + "/" + b);
            }
        }
    }

    @Test
    void humansStandInFrontAndTheStrongestNpcAtTheBack() {
        List<TeamPlanner.Candidate> c = candidates(16, 2, 77);
        TeamPlanner.Heat h = TeamPlanner.plan(c, new Rng(3)).heats().get(0);
        for (int team : new int[]{TugRules.TEAM_A, TugRules.TEAM_B}) {
            List<Integer> slots = h.team(team);
            boolean seenNpc = false;
            double previous = -1;
            for (int id : slots) {
                TeamPlanner.Candidate k = c.stream().filter(x -> x.id() == id).findFirst().orElseThrow();
                if (k.human()) {
                    assertFalse(seenNpc, "humans come first");
                } else {
                    seenNpc = true;
                    assertTrue(k.rating() >= previous, "NPCs get stronger towards the back");
                    previous = k.rating();
                }
            }
        }
    }

    @Test
    void oddHeatsHandicapTheSmallerTeamOnly() {
        for (int n = 3; n <= 41; n += 2) {
            TeamPlanner.Heat h = TeamPlanner.plan(candidates(n, 0, n), new Rng(n)).heats().get(0);
            boolean aSmaller = h.teamA().size() < h.teamB().size();
            assertEquals(1.0, aSmaller ? h.handicapB() : h.handicapA(), 1e-12);
            double bonus = aSmaller ? h.handicapA() : h.handicapB();
            assertEquals(1.0 + 0.7 / ((n - 1) / 2.0), bonus, 1e-9, "bonus for " + n);
            int small = Math.min(h.teamA().size(), h.teamB().size());
            int big = Math.max(h.teamA().size(), h.teamB().size());
            assertEquals(bonus, TeamPlanner.handicap(small, big), 1e-12);
            assertEquals(1.0, TeamPlanner.handicap(big, small), 0.0);
        }
        TeamPlanner.Heat even = TeamPlanner.plan(candidates(20, 0, 5), new Rng(5)).heats().get(0);
        assertEquals(1.0, even.handicapA(), 1e-12);
        assertEquals(1.0, even.handicapB(), 1e-12);
    }

    @Test
    void oddHeatsAreBalancedIncludingTheHandicapNotByGivingTheSmallerTeamTheStrongerMembers() {
        double weightedGap = 0, rawMeanSmall = 0, rawMeanBig = 0;
        int heats = 0;
        for (int seed = 0; seed < 40; seed++) {
            List<TeamPlanner.Candidate> c = candidates(15 + 2 * (seed % 4), 0, 4000 + seed);
            TeamPlanner.Heat h = TeamPlanner.plan(c, new Rng(seed)).heats().get(0);
            double a = sum(h.teamA(), c), b = sum(h.teamB(), c);
            weightedGap += Math.abs(a * h.handicapA() - b * h.handicapB());
            boolean aSmaller = h.teamA().size() < h.teamB().size();
            rawMeanSmall += (aSmaller ? a / h.teamA().size() : b / h.teamB().size());
            rawMeanBig += (aSmaller ? b / h.teamB().size() : a / h.teamA().size());
            heats++;
        }
        assertTrue(weightedGap / heats < 0.2, "weighted rating gap " + weightedGap / heats);
        // the handicap covers 70% of the missing member, the balance makes up the rest: the smaller team's members are a few
        // percent better, not a whole member's worth (which would be 7% to 14% for these sizes)
        double ratio = rawMeanSmall / rawMeanBig;
        assertTrue(ratio > 1.0 && ratio < 1.06, "mean rating of the smaller team relative to the larger: " + ratio);
    }

    @Test
    void theSmallerSideVaries() {
        int aSmaller = 0;
        for (int seed = 0; seed < 60; seed++) {
            TeamPlanner.Heat h = TeamPlanner.plan(candidates(9, 0, 4), new Rng(7).fork(seed)).heats().get(0);
            if (h.teamA().size() < h.teamB().size()) {
                aSmaller++;
            }
        }
        assertTrue(aSmaller > 12 && aSmaller < 48, "A was the smaller team " + aSmaller + " of 60 times");
    }

    @Test
    void deterministicAndIndependentOfTheInputOrder() {
        List<TeamPlanner.Candidate> c = candidates(30, 3, 12);
        TeamPlanner.Plan a = TeamPlanner.plan(c, new Rng(9));
        TeamPlanner.Plan b = TeamPlanner.plan(c, new Rng(9));
        assertEquals(a, b);
        List<TeamPlanner.Candidate> shuffled = new ArrayList<>(c);
        Collections.reverse(shuffled);
        assertEquals(a, TeamPlanner.plan(shuffled, new Rng(9)));
        assertNotEquals(a, TeamPlanner.plan(c, new Rng(10)));
    }

    @Test
    void allHumansOnOneSideOfTheListStillGetSplit() {
        // the humans are the first contestants and every NPC is stronger: the split must still be fair
        List<TeamPlanner.Candidate> c = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            c.add(new TeamPlanner.Candidate(i, i <= 4, i <= 4 ? 0.8 : 1.2));
        }
        TeamPlanner.Heat h = TeamPlanner.plan(c, new Rng(1)).heats().get(0);
        assertEquals(6, h.teamA().size());
        assertEquals(2, h.teamA().stream().filter(id -> id <= 4).count());
        assertEquals(2, h.teamB().stream().filter(id -> id <= 4).count());
        assertEquals(sum(h.teamA(), c), sum(h.teamB(), c), 1e-9);
    }
}
