package com.squidgame.core.tug;

import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TugNpcPolicyTest {
    private static final BeatClock BEAT = new BeatClock(0, 28);

    private static Personality person(float courage, float reaction, float patience, float skill, float aggression,
                                      float cooperation, float risk) {
        return new Personality(Personality.Archetype.ROOKIE, courage, reaction, patience, skill, aggression, cooperation, risk);
    }

    private static final Personality BRAVE = person(0.9f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.8f);
    private static final Personality NERVOUS = person(0.1f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.1f);

    private static TugNpcPolicy.View view(long now, double lead, double stamina, boolean exhausted) {
        return new TugNpcPolicy.View(now, lead, stamina, exhausted, 0.0, 0.0, 0.8, BEAT, false);
    }

    /** The stamina and stance rules of the simulation for one member (a rope that stays where it is). */
    private static final class Body {
        double stamina;
        boolean exhausted;

        Body(double stamina) {
            this.stamina = stamina;
        }

        void apply(TugNpcPolicy.Action a) {
            boolean pulling = a.effort() > 0 && !a.brace() && !exhausted;
            if (pulling) {
                stamina -= TugRules.PULL_DRAIN * a.effort();
            } else if (a.brace()) {
                stamina += exhausted ? TugRules.REST_RECOVERY * TugRules.EXHAUSTED_BRACE_RECOVERY
                        : -TugRules.PULL_DRAIN * TugRules.BRACE_DRAIN_FRACTION;
            } else {
                stamina += TugRules.REST_RECOVERY;
            }
            stamina = Math.max(0, Math.min(1, stamina));
            if (stamina <= 0) {
                exhausted = true;
            } else if (exhausted && stamina >= TugRules.RECOVER_THRESHOLD) {
                exhausted = false;
            }
        }
    }

    /** Runs a policy against the stamina rules for {@code ticks} ticks at a fixed rope lead. */
    private static List<TugNpcPolicy.Action> run(TugNpcPolicy policy, int ticks, double lead, double startStamina) {
        return run(policy, ticks, lead, startStamina, null);
    }

    private static List<TugNpcPolicy.Action> run(TugNpcPolicy policy, int ticks, double lead, double startStamina, double[] meanStamina) {
        List<TugNpcPolicy.Action> out = new ArrayList<>();
        Body body = new Body(startStamina);
        double sum = 0;
        for (int t = 0; t < ticks; t++) {
            TugNpcPolicy.Action a = policy.decide(view(t, lead, body.stamina, body.exhausted));
            out.add(a);
            body.apply(a);
            sum += body.stamina;
        }
        if (meanStamina != null) {
            meanStamina[0] = sum / ticks;
        }
        return out;
    }

    private static double meanEffort(List<TugNpcPolicy.Action> actions) {
        return actions.stream().mapToDouble(TugNpcPolicy.Action::effort).average().orElse(0);
    }

    private static double pullingShare(List<TugNpcPolicy.Action> actions) {
        return actions.stream().filter(a -> a.effort() > 0).count() / (double) actions.size();
    }

    @Test
    void braveContestantsGoAllOutWhenLosingNervousOnesPanic() {
        TugNpcPolicy brave = new TugNpcPolicy(BRAVE, 0, new Rng(1), 3.0);
        List<TugNpcPolicy.Action> b = run(brave, 200, -0.8, 0.9);
        assertEquals(TugNpcPolicy.Mode.SURGE, brave.mode());
        assertEquals(1.0, b.get(150).effort(), 0.0);
        assertTrue(b.stream().skip(40).limit(60).allMatch(a -> a.effort() == 1.0 && !a.brace()), "no hesitation");

        TugNpcPolicy nervous = new TugNpcPolicy(NERVOUS, 0, new Rng(1), 3.0);
        List<TugNpcPolicy.Action> n = run(nervous, 200, -0.8, 0.9);
        Set<Double> efforts = new java.util.HashSet<>();
        n.stream().skip(30).forEach(a -> efforts.add(a.effort()));
        assertTrue(efforts.contains(1.0) && efforts.contains(0.25), "erratic: " + efforts);
        assertTrue(pullingShare(n) < pullingShare(b) + 1e-9 || efforts.size() >= 2);
    }

    @Test
    void patientContestantsPaceThemselvesImpatientOnesBurnTheirStaminaFirst() {
        double patientStamina = 0, impatientStamina = 0, patientEffort = 0, impatientEffort = 0;
        for (int seed = 1; seed <= 8; seed++) {
            double[] mean = new double[1];
            List<TugNpcPolicy.Action> p = run(new TugNpcPolicy(person(0.5f, 0.5f, 0.95f, 0.5f, 0.1f, 0.5f, 0.3f), 0, new Rng(seed), 3.0),
                    800, 0.0, 1.0, mean);
            patientStamina += mean[0];
            patientEffort += meanEffort(p);
            List<TugNpcPolicy.Action> q = run(new TugNpcPolicy(person(0.5f, 0.5f, 0.05f, 0.5f, 0.9f, 0.5f, 0.7f), 0, new Rng(seed), 3.0),
                    800, 0.0, 1.0, mean);
            impatientStamina += mean[0];
            impatientEffort += meanEffort(q);
        }
        assertTrue(patientStamina / 8 > impatientStamina / 8 + 0.08,
                "mean stamina over 40 s: patient " + patientStamina / 8 + ", impatient " + impatientStamina / 8);
        assertTrue(patientEffort / 8 < impatientEffort / 8 * 0.85,
                "the patient pull less hard: mean effort " + patientEffort / 8 + " against " + impatientEffort / 8);
        assertTrue(patientEffort / 8 > 0.2, "even the patient put in a good effort: " + patientEffort / 8);
    }

    @Test
    void theComfortablyAheadSaveTheirStrengthAndTheBehindPullThenAnchor() {
        Personality calm = person(0.5f, 0.5f, 0.9f, 0.5f, 0.3f, 0.5f, 0.3f);
        List<TugNpcPolicy.Action> ahead = run(new TugNpcPolicy(calm, 0, new Rng(3), 3.0), 600, 0.7, 0.5);
        assertTrue(pullingShare(ahead.subList(60, 600)) < 0.25, "a patient contestant that is comfortably ahead does not pull: " + pullingShare(ahead));
        List<TugNpcPolicy.Action> behind = run(new TugNpcPolicy(calm, 0, new Rng(3), 3.0), 900, -0.35, 1.0);
        assertTrue(pullingShare(behind.subList(30, 200)) > 0.9, "clearly behind: pulls with everything it has");
        long braced = behind.stream().skip(400).filter(TugNpcPolicy.Action::brace).count();
        assertTrue(braced > 150, "and when its reserve is gone it anchors the line: " + braced);
    }

    @Test
    void aTiredContestantAtAContestedRopeRestsInsteadOfBracing() {
        TugNpcPolicy p = new TugNpcPolicy(person(0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f), 0, new Rng(8), 3.0);
        List<TugNpcPolicy.Action> actions = run(p, 1200, 0.0, 0.1);
        long resting = actions.stream().skip(40).filter(a -> a.effort() == 0 && !a.brace()).count();
        long braced = actions.stream().skip(40).filter(TugNpcPolicy.Action::brace).count();
        assertTrue(resting > braced * 3, "rests " + resting + " braces " + braced);
    }

    @Test
    void anExhaustedContestantStopsPullingAtOnce() {
        TugNpcPolicy p = new TugNpcPolicy(BRAVE, 0, new Rng(4), 3.0);
        for (int t = 0; t < 60; t++) {
            p.decide(view(t, -0.2, 0.8, false));
        }
        TugNpcPolicy.Action a = p.decide(view(60, -0.2, 0.0, true));
        assertEquals(0.0, a.effort(), 0.0);
        assertTrue(p.mode() == TugNpcPolicy.Mode.REST || p.mode() == TugNpcPolicy.Mode.HOLD);
    }

    @Test
    void cooperativeContestantsHeaveMoreOften() {
        int coop = heaves(person(0.5f, 0.5f, 0.3f, 0.5f, 0.5f, 0.95f, 0.5f), 1).size();
        int lone = heaves(person(0.5f, 0.5f, 0.3f, 0.5f, 0.5f, 0.05f, 0.5f), 1).size();
        assertTrue(coop > 2 * lone, coop + " vs " + lone);
        assertTrue(lone > 0, "even a loner joins in now and then");
    }

    @Test
    void quickAndSkilledContestantsHeaveCloserToTheBeat() {
        double precise = meanError(heaves(person(0.5f, 0.95f, 0.3f, 0.9f, 0.5f, 0.95f, 0.5f), 7));
        double sloppy = meanError(heaves(person(0.5f, 0.05f, 0.3f, 0.1f, 0.5f, 0.95f, 0.5f), 7));
        assertTrue(precise < sloppy - 0.8, precise + " vs " + sloppy);
        assertTrue(precise < 2.0);
    }

    @Test
    void theSkillBonusOfAHigherDifficultySharpensTheTiming() {
        Personality p = person(0.5f, 0.5f, 0.3f, 0.3f, 0.5f, 0.95f, 0.5f);
        List<Long> plain = new ArrayList<>(), bonus = new ArrayList<>();
        for (int seed = 1; seed <= 6; seed++) {
            plain.addAll(heaves(p, seed, 0.0));
            bonus.addAll(heaves(p, seed, 0.25));
        }
        assertTrue(meanError(bonus) < meanError(plain), meanError(bonus) + " vs " + meanError(plain));
    }

    @Test
    void atMostOneHeavePerBeatAndOnlyNearTheBeat() {
        List<Long> presses = heaves(person(0.5f, 0.5f, 0.3f, 0.5f, 0.5f, 0.95f, 0.5f), 3);
        Map<Long, Integer> perBeat = new HashMap<>();
        for (long press : presses) {
            perBeat.merge(BEAT.nearestIndex(press), 1, Integer::sum);
            assertTrue(Math.abs(BEAT.errorTo(press)) <= 3 + 3, "press " + press);
        }
        assertTrue(perBeat.values().stream().allMatch(c -> c == 1), "never twice for one beat");
    }

    @Test
    void noHeavesWhileExhaustedOrTooTiredOrResting() {
        TugNpcPolicy p = new TugNpcPolicy(person(0.5f, 0.5f, 0.3f, 0.5f, 0.5f, 0.95f, 0.5f), 0, new Rng(9), 3.0);
        for (int t = 0; t < 2000; t++) {
            assertEquals(-1, p.decide(view(t, 0.0, 0.0, true)).pressTick(), "exhausted at tick " + t);
        }
        TugNpcPolicy tired = new TugNpcPolicy(person(0.5f, 0.5f, 0.3f, 0.5f, 0.5f, 0.95f, 0.5f), 0, new Rng(9), 3.0);
        for (int t = 0; t < 2000; t++) {
            assertEquals(-1, tired.decide(view(t, 0.0, 0.10, false)).pressTick(), "too tired at tick " + t);
        }
    }

    @Test
    void identicalSeedsGiveIdenticalDecisions() {
        TugNpcPolicy a = new TugNpcPolicy(BRAVE, 0.1, new Rng(21), 3.0);
        TugNpcPolicy b = new TugNpcPolicy(BRAVE, 0.1, new Rng(21), 3.0);
        for (int t = 0; t < 1500; t++) {
            double lead = Math.sin(t / 130.0) * 0.8;
            assertEquals(a.decide(view(t, lead, 0.7, false)), b.decide(view(t, lead, 0.7, false)));
        }
    }

    @Test
    void aSlowAiStepStillDeliversTheHeaveAtItsPlannedTick() {
        // an NPC far from every player only thinks every third tick: the press keeps its planned (earlier) tick
        TugNpcPolicy p = new TugNpcPolicy(person(0.5f, 0.5f, 0.3f, 0.5f, 0.5f, 0.95f, 0.5f), 0, new Rng(5), 3.0);
        int delivered = 0;
        for (int t = 0; t < 3000; t += 3) {
            long press = p.decide(view(t, 0.0, 0.9, false)).pressTick();
            if (press >= 0) {
                delivered++;
                assertTrue(press <= t && t - press <= 2, "press " + press + " delivered at " + t);
            }
        }
        assertTrue(delivered > 20);
    }

    @Test
    void effortStaysInRangeWhateverHappens() {
        Rng rng = new Rng(2);
        for (int seed = 0; seed < 20; seed++) {
            TugNpcPolicy p = new TugNpcPolicy(Personality.generate(rng), seed % 2 * 0.25, new Rng(seed), 2.4);
            for (int t = 0; t < 600; t++) {
                TugNpcPolicy.Action a = p.decide(view(t, rng.range(-1, 1), rng.nextDouble(), rng.chance(0.1)));
                assertTrue(a.effort() >= 0.0 && a.effort() <= 1.0);
                assertFalse(a.effort() > 0 && a.brace(), "bracing and pulling do not go together");
            }
        }
    }

    @Test
    void visibleSwingsOfTheRopeAreNoticedOnce() {
        TugNpcPolicy p = new TugNpcPolicy(BRAVE, 0, new Rng(6), 3.0);
        Set<TugNpcPolicy.Swing> seen = EnumSet.noneOf(TugNpcPolicy.Swing.class);
        for (int t = 0; t <= 60; t++) {
            p.decide(view(t, -0.4 * Math.min(1.0, t / 40.0), 0.9, false));
            TugNpcPolicy.Swing s = p.takeSwing();
            seen.add(s);
            if (s != TugNpcPolicy.Swing.NONE) {
                assertEquals(TugNpcPolicy.Swing.NONE, p.takeSwing(), "reading clears it");
            }
        }
        assertTrue(seen.contains(TugNpcPolicy.Swing.JOLT), seen.toString());
        assertFalse(seen.contains(TugNpcPolicy.Swing.RALLY));

        TugNpcPolicy q = new TugNpcPolicy(BRAVE, 0, new Rng(6), 3.0);
        seen.clear();
        for (int t = 0; t <= 60; t++) {
            q.decide(view(t, -0.6 + 0.45 * Math.min(1.0, t / 40.0), 0.9, false));
            seen.add(q.takeSwing());
        }
        assertTrue(seen.contains(TugNpcPolicy.Swing.RALLY), seen.toString());
        assertFalse(seen.contains(TugNpcPolicy.Swing.JOLT));
    }

    // ------------------------------------------------------------------ helpers

    private static List<Long> heaves(Personality person, long seed) {
        return heaves(person, seed, 0.0);
    }

    /** The press ticks of a contestant over 150 beats at a calm rope and good stamina. */
    private static List<Long> heaves(Personality person, long seed, double skillBonus) {
        TugNpcPolicy p = new TugNpcPolicy(person, skillBonus, new Rng(seed), 3.0);
        List<Long> out = new ArrayList<>();
        for (int t = 0; t < 28 * 150; t++) {
            long press = p.decide(view(t, 0.0, 0.9, false)).pressTick();
            if (press >= 0) {
                out.add(press);
            }
        }
        return out;
    }

    private static double meanError(List<Long> presses) {
        double sum = 0;
        for (long press : presses) {
            sum += Math.abs(BEAT.errorTo(press));
        }
        return sum / Math.max(1, presses.size());
    }
}
