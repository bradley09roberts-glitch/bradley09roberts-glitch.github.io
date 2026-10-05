package com.squidgame.core.tug;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Whole heats on the simulation with Monte Carlo runs (fixed seeds): the balance of the rules as the players feel it.
 * The thresholds leave room around the tuned values so that small changes of the numbers do not break them, but the
 * claims of the design hold: equal teams are fair, close and decided by the rope, pacing beats burning out early, and a
 * human who hits the beat is worth several members.
 */
class TugMatchTest {
    // ------------------------------------------------------------------ scripted humans

    /** Holds the pull key and never heaves. */
    private static TugMatch.Controller novice() {
        return v -> new TugNpcPolicy.Action(1.0, false, -1);
    }

    /** Holds the pull key and taps heave ten times a second whatever the beat. */
    private static TugMatch.Controller masher() {
        long[] last = {-100};
        return v -> {
            long press = -1;
            if (v.now() - last[0] >= 10 && !v.exhausted()) {
                last[0] = v.now();
                press = v.now();
            }
            return new TugNpcPolicy.Action(1.0, false, press);
        };
    }

    /**
     * A good player: pulls, rests when the stamina is low until it has recovered, and heaves on every beat with at most one
     * tick of error (the server forgives one more tick for latency).
     */
    private static TugMatch.Controller pro(Rng rng) {
        boolean[] resting = {false};
        long[] planned = {Long.MIN_VALUE};
        long[] press = {-1};
        return v -> {
            long next = v.beat().nextBeatAtOrAfter(v.now());
            if (next > planned[0]) {
                planned[0] = next;
                press[0] = next + rng.rangeInt(-1, 1);
            }
            if (resting[0]) {
                if (v.stamina() > 0.5) {
                    resting[0] = false;
                }
            } else if (v.stamina() < 0.22) {
                resting[0] = true;
            }
            if (resting[0]) {
                return new TugNpcPolicy.Action(0.0, false, -1);
            }
            long at = -1;
            if (press[0] >= 0 && v.now() >= press[0]) {
                at = v.exhausted() || v.stamina() <= 0.12 ? -1 : press[0];
                press[0] = -1;
            }
            return new TugNpcPolicy.Action(1.0, false, at);
        };
    }

    // ------------------------------------------------------------------ helpers

    private record Heat(List<TugMatch.Entrant> entrants, int humanId) {
    }

    /** Balanced teams of {@code n} (the planner's split); the first entrant of team A is the human when requested. */
    private static Heat heat(Rng rng, int n, boolean humanInA) {
        List<Personality> people = new ArrayList<>();
        List<TeamPlanner.Candidate> cands = new ArrayList<>();
        for (int i = 0; i < 2 * n; i++) {
            Personality p = Personality.generate(rng);
            people.add(p);
            cands.add(new TeamPlanner.Candidate(i + 1, false, TugTraits.rating(p)));
        }
        TeamPlanner.Heat h = TeamPlanner.plan(cands, rng.fork(5)).heats().get(0);
        int human = humanInA ? h.teamA().get(0) : -1;
        List<TugMatch.Entrant> out = new ArrayList<>();
        for (int id : h.teamA()) {
            out.add(new TugMatch.Entrant(id, TugRules.TEAM_A, people.get(id - 1), id == human));
        }
        for (int id : h.teamB()) {
            out.add(new TugMatch.Entrant(id, TugRules.TEAM_B, people.get(id - 1), false));
        }
        return new Heat(out, human);
    }

    private static double humanWinRate(Difficulty d, int n, int runs, String kind) {
        int wins = 0;
        for (int r = 0; r < runs; r++) {
            Rng rng = new Rng(900 + r * 104729L + n);
            Heat h = heat(rng, n, true);
            Map<Integer, TugMatch.Controller> brain = new HashMap<>();
            brain.put(h.humanId(), switch (kind) {
                case "pro" -> pro(rng.fork(77));
                case "masher" -> masher();
                default -> novice();
            });
            TugMatch.Result res = TugMatch.run(d, h.entrants(), brain, rng.fork(9), null, 1.0, 1.0);
            if (res.winner() == TugRules.TEAM_A) {
                wins++;
            }
        }
        return wins / (double) runs;
    }

    // ------------------------------------------------------------------ equal NPC teams

    @Test
    void equalNpcTeamsAreFairCloseAndDecidedByTheRope() {
        int runs = 60, aWins = 0, byRope = 0, leadChanges = 0;
        List<Integer> durations = new ArrayList<>();
        for (int r = 0; r < runs; r++) {
            Rng rng = new Rng(500 + r * 7919L);
            Heat h = heat(rng, 8, false);
            int[] sign = {0};
            int[] changes = {0};
            TugMatch.Result res = TugMatch.run(Difficulty.NORMAL, h.entrants(), Map.of(), rng.fork(9), (tick, sim) -> {
                if (Math.abs(sim.offset()) > 0.12) {
                    int s = sim.offset() > 0 ? 1 : -1;
                    if (sign[0] != 0 && s != sign[0]) {
                        changes[0]++;
                    }
                    sign[0] = s;
                }
            }, 1.0, 1.0);
            if (res.winner() == TugRules.TEAM_A) {
                aWins++;
            }
            if (!res.timedOut()) {
                byRope++;
            }
            leadChanges += changes[0];
            durations.add(res.ticks());
        }
        assertTrue(aWins >= 0.35 * runs && aWins <= 0.65 * runs, "team A won " + aWins + " of " + runs);
        assertTrue(byRope >= 0.75 * runs, "decided by the rope: " + byRope + " of " + runs);
        assertTrue(leadChanges / (double) runs >= 1.5, "the rope swings: " + leadChanges / (double) runs + " lead changes per heat");
        durations.sort(Integer::compare);
        assertTrue(durations.get(runs / 2) < 60 * 20, "median heat length " + durations.get(runs / 2) / 20 + " s");
        assertTrue(durations.get(runs / 10) > 5 * 20, "no heat is over in a flash");
    }

    @Test
    void largeTeamsAreAlsoDecidedMostOfTheTime() {
        int runs = 25, byRope = 0;
        for (int r = 0; r < runs; r++) {
            Rng rng = new Rng(77 + r * 7919L);
            TugMatch.Result res = TugMatch.run(Difficulty.NORMAL, heat(rng, 32, false).entrants(), rng.fork(9));
            if (!res.timedOut()) {
                byRope++;
            }
            assertTrue(res.ticks() <= TugRules.params(Difficulty.NORMAL).heatLimitTicks() + TugRules.params(Difficulty.NORMAL).suddenDeathTicks());
        }
        assertTrue(byRope >= 0.5 * runs, "32 v 32 decided by the rope: " + byRope + " of " + runs);
    }

    @Test
    void everySizeFromOneAgainstOneToAFullHallProducesAWinner() {
        for (int n : new int[]{1, 2, 3, 5, 8, 16, 32}) {
            for (Difficulty d : Difficulty.values()) {
                Rng rng = new Rng(n * 31L + d.ordinal());
                TugMatch.Result res = TugMatch.run(d, heat(rng, n, false).entrants(), rng.fork(9));
                assertTrue(res.winner() == TugRules.TEAM_A || res.winner() == TugRules.TEAM_B);
                assertTrue(res.ticks() > 0);
                assertTrue(Math.abs(res.offset()) <= 1.0);
            }
        }
    }

    @Test
    void aHeatIsReproducibleFromItsSeed() {
        Rng a = new Rng(5), b = new Rng(5);
        TugMatch.Result x = TugMatch.run(Difficulty.HARD, heat(a, 12, false).entrants(), a.fork(9));
        TugMatch.Result y = TugMatch.run(Difficulty.HARD, heat(b, 12, false).entrants(), b.fork(9));
        assertEquals(x, y);
    }

    @Test
    void oddHeatsAreFairWithTheHandicap() {
        int runs = 60, smallWins = 0;
        for (int r = 0; r < runs; r++) {
            Rng rng = new Rng(3000 + r * 977L);
            List<TeamPlanner.Candidate> cands = new ArrayList<>();
            List<Personality> people = new ArrayList<>();
            for (int i = 0; i < 17; i++) {
                Personality p = Personality.generate(rng);
                people.add(p);
                cands.add(new TeamPlanner.Candidate(i + 1, false, TugTraits.rating(p)));
            }
            TeamPlanner.Heat h = TeamPlanner.plan(cands, rng.fork(5)).heats().get(0);
            List<TugMatch.Entrant> es = new ArrayList<>();
            for (int id : h.teamA()) {
                es.add(new TugMatch.Entrant(id, TugRules.TEAM_A, people.get(id - 1), false));
            }
            for (int id : h.teamB()) {
                es.add(new TugMatch.Entrant(id, TugRules.TEAM_B, people.get(id - 1), false));
            }
            TugMatch.Result res = TugMatch.run(Difficulty.NORMAL, es, Map.of(), rng.fork(9), null, h.handicapA(), h.handicapB());
            boolean aSmaller = h.teamA().size() < h.teamB().size();
            if ((res.winner() == TugRules.TEAM_A) == aSmaller) {
                smallWins++;
            }
        }
        assertTrue(smallWins >= 0.3 * runs && smallWins <= 0.7 * runs, "the smaller team won " + smallWins + " of " + runs);
    }

    // ------------------------------------------------------------------ strategy

    @Test
    void aTeamThatBurnsItsStaminaEarlyLosesLate() {
        int runs = 60, recklessWins = 0;
        double early = 0, late = 0;
        for (int r = 0; r < runs; r++) {
            Rng rng = new Rng(700 + r * 7919L);
            List<TugMatch.Entrant> es = new ArrayList<>();
            int id = 1;
            for (int i = 0; i < 12; i++) {
                float patience = (float) Math.max(0.02, 0.08 + rng.gaussian(0, 0.025));
                es.add(new TugMatch.Entrant(id++, TugRules.TEAM_A,
                        new Personality(Personality.Archetype.RECKLESS, 0.8f, 0.55f, patience, 0.55f, 0.85f, 0.5f, 0.85f), false));
            }
            for (int i = 0; i < 12; i++) {
                float patience = (float) Math.min(0.98, 0.85 + rng.gaussian(0, 0.05));
                es.add(new TugMatch.Entrant(id++, TugRules.TEAM_B,
                        new Personality(Personality.Archetype.CALCULATING, 0.8f, 0.55f, patience, 0.55f, 0.3f, 0.5f, 0.3f), false));
            }
            double[] sums = new double[2];
            int[] counts = new int[2];
            TugMatch.Result res = TugMatch.run(Difficulty.NORMAL, es, Map.of(), rng.fork(9), (tick, sim) -> {
                if (tick < 300) {
                    sums[0] += sim.offset();
                    counts[0]++;
                } else if (tick >= 600 && tick < 900) {
                    sums[1] += sim.offset();
                    counts[1]++;
                }
            }, 1.0, 1.0);
            if (res.winner() == TugRules.TEAM_A) {
                recklessWins++;
            }
            early += sums[0] / Math.max(1, counts[0]);
            late += counts[1] == 0 ? 0 : sums[1] / counts[1];
        }
        assertTrue(early / runs < -0.05, "the reckless team leads in the first 15 s (offset " + early / runs + ")");
        assertTrue(late / runs > early / runs + 0.15, "and the patient team takes over: " + late / runs);
        assertTrue(recklessWins <= 0.4 * runs, "the reckless team won " + recklessWins + " of " + runs);
        assertTrue(recklessWins >= 0.04 * runs, "but it can still win sometimes: " + recklessWins);
    }

    // ------------------------------------------------------------------ the human

    @Test
    void aHumanWhoHitsTheBeatIsWorthSeveralMembers() {
        double pro = humanWinRate(Difficulty.NORMAL, 16, 60, "pro");
        double novice = humanWinRate(Difficulty.NORMAL, 16, 60, "novice");
        double masher = humanWinRate(Difficulty.NORMAL, 16, 60, "masher");
        assertTrue(pro >= 0.7, "a player with a good rhythm wins " + pro);
        assertTrue(novice <= 0.65, "holding the pull key alone is worth about one member: " + novice);
        assertTrue(masher <= 0.65, "mashing the heave key does not help: " + masher);
        assertTrue(pro > novice + 0.15 && pro > masher + 0.15);
    }

    @Test
    void aHumanWhoHitsTheBeatBeatsAnExtraAverageMember() {
        // team A = 16 NPCs plus one extra NPC, against 16 NPCs: the benchmark for "one more member"
        int runs = 60, wins = 0;
        for (int r = 0; r < runs; r++) {
            Rng rng = new Rng(8100 + r * 31L);
            List<TugMatch.Entrant> es = new ArrayList<>();
            for (int i = 0; i < 17; i++) {
                es.add(new TugMatch.Entrant(i + 1, TugRules.TEAM_A, Personality.generate(rng), false));
            }
            for (int i = 0; i < 16; i++) {
                es.add(new TugMatch.Entrant(100 + i, TugRules.TEAM_B, Personality.generate(rng), false));
            }
            if (TugMatch.run(Difficulty.NORMAL, es, Map.of(), rng.fork(9), null, 1.0, 1.0).winner() == TugRules.TEAM_A) {
                wins++;
            }
        }
        double oneExtra = wins / (double) runs;
        double pro = humanWinRate(Difficulty.NORMAL, 16, 60, "pro");
        assertTrue(oneExtra > 0.55 && oneExtra < 0.9, "one extra member: " + oneExtra);
        assertTrue(pro > oneExtra + 0.03, "the rhythm player (" + pro + ") is worth more than one extra member (" + oneExtra + ")");
    }

    @Test
    void harderDifficultiesAreHarderOnTheHuman() {
        double normal = humanWinRate(Difficulty.NORMAL, 16, 60, "pro");
        double extreme = humanWinRate(Difficulty.EXTREME, 16, 60, "pro");
        assertTrue(normal > extreme + 0.12, "normal " + normal + ", extreme " + extreme);
        double extremeNovice = humanWinRate(Difficulty.EXTREME, 16, 60, "novice");
        assertTrue(extremeNovice < 0.5, "an inattentive player on extreme: " + extremeNovice);
    }

    @Test
    void opposingNpcsOnlyGetTheBonusWhenAHumanPlays() {
        List<TugMatch.Entrant> npcOnly = List.of(
                new TugMatch.Entrant(1, 0, Personality.generate(new Rng(1)), false),
                new TugMatch.Entrant(2, 1, Personality.generate(new Rng(2)), false));
        assertEquals(-1, TugMatch.boostedTeam(npcOnly));
        List<TugMatch.Entrant> humanA = List.of(
                new TugMatch.Entrant(1, 0, Personality.generate(new Rng(1)), true),
                new TugMatch.Entrant(2, 1, Personality.generate(new Rng(2)), false));
        assertEquals(TugRules.TEAM_B, TugMatch.boostedTeam(humanA));
        List<TugMatch.Entrant> both = List.of(
                new TugMatch.Entrant(1, 0, Personality.generate(new Rng(1)), true),
                new TugMatch.Entrant(2, 1, Personality.generate(new Rng(2)), true));
        assertEquals(-1, TugMatch.boostedTeam(both), "two humans: a fair fight");
        TugRules.Params extreme = TugRules.params(Difficulty.EXTREME);
        List<TugSim.Spec> specs = TugMatch.specs(humanA, extreme);
        TugSim.Spec plain = TugMatch.specs(npcOnly, extreme).get(1);
        assertTrue(specs.get(1).strength() > plain.strength(), "the boosted team pulls harder");
        assertEquals(TugMatch.specs(npcOnly, extreme).get(0).strength(), specs.get(0).strength(), 1e-12);
    }

    @Test
    void handicapOnlyForTheSmallerTeam() {
        List<TugMatch.Entrant> es = new ArrayList<>();
        Personality p = Personality.generate(new Rng(1));
        for (int i = 0; i < 5; i++) {
            es.add(new TugMatch.Entrant(i, TugRules.TEAM_A, p, false));
        }
        for (int i = 0; i < 4; i++) {
            es.add(new TugMatch.Entrant(10 + i, TugRules.TEAM_B, p, false));
        }
        assertEquals(1.0, TugMatch.handicap(es, TugRules.TEAM_A), 0.0);
        assertEquals(1.0 + 0.7 * (5 / 4.0 - 1), TugMatch.handicap(es, TugRules.TEAM_B), 1e-12);
    }
}
