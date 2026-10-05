package com.squidgame.core.tug;

import com.squidgame.core.Difficulty;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TugSimTest {
    private static final TugRules.Params NORMAL = TugRules.params(Difficulty.NORMAL);
    private static final BeatClock BEAT = new BeatClock(0, 28);

    /** Team A members have ids 1..a, team B members a+1..a+b; everybody is an average member. */
    private static TugSim sim(int a, int b) {
        return sim(a, b, 1.0, 1.0, NORMAL);
    }

    private static TugSim sim(int a, int b, double handicapA, double handicapB, TugRules.Params params) {
        List<TugSim.Spec> specs = new ArrayList<>();
        for (int i = 0; i < a; i++) {
            specs.add(new TugSim.Spec(1 + i, TugRules.TEAM_A, 1.0, 1.0));
        }
        for (int i = 0; i < b; i++) {
            specs.add(new TugSim.Spec(1 + a + i, TugRules.TEAM_B, 1.0, 1.0));
        }
        return new TugSim(params, BEAT, specs, handicapA, handicapB, 0);
    }

    private static void setAll(TugSim s, int team, double effort, boolean brace) {
        for (int i = 0; i < s.size(); i++) {
            if (s.member(i).team == team) {
                s.setStance(i, effort, brace);
            }
        }
    }

    /** Everybody pulls flat out, so the rope stays put and the heat is not decided while one member is watched. */
    private static TugSim deadlock(int n) {
        TugSim s = sim(n, n);
        setAll(s, TugRules.TEAM_A, 1.0, false);
        setAll(s, TugRules.TEAM_B, 1.0, false);
        return s;
    }

    private static void run(TugSim s, int from, int ticks) {
        for (int t = from; t < from + ticks; t++) {
            s.tick(t);
        }
    }

    // ------------------------------------------------------------------ rope

    @Test
    void equalTeamsDoingTheSameStayExactlyInTheMiddle() {
        TugSim s = sim(8, 8);
        setAll(s, TugRules.TEAM_A, 1.0, false);
        setAll(s, TugRules.TEAM_B, 1.0, false);
        run(s, 0, 300);
        assertEquals(0.0, s.offset(), 1e-12);
        assertEquals(TugSim.Outcome.ONGOING, s.outcome());
        assertTrue(s.strain() > 0.5, "both pulling: the rope is taut");
    }

    @Test
    void theTeamThatPullsMoreWinsAndTheDirectionIsRight() {
        TugSim s = sim(8, 8);
        setAll(s, TugRules.TEAM_A, 1.0, false);
        run(s, 0, 600);
        assertEquals(TugSim.Outcome.A_WINS, s.outcome());
        assertEquals(-1.0, s.offset(), 1e-12);

        s = sim(8, 8);
        setAll(s, TugRules.TEAM_B, 1.0, false);
        run(s, 0, 600);
        assertEquals(TugSim.Outcome.B_WINS, s.outcome());
        assertEquals(1.0, s.offset(), 1e-12);
    }

    @Test
    void theRopeIsHeavyAndNeedsTimeToSwing() {
        TugSim s = sim(8, 8);
        setAll(s, TugRules.TEAM_B, 1.0, false);
        run(s, 0, 20);
        assertTrue(s.offset() > 0 && s.offset() < 0.08, "after one second the rope has hardly moved: " + s.offset());
        run(s, 20, 40);
        assertTrue(s.offset() > 0.2 && s.offset() < 0.55, "after three seconds it is not even half way: " + s.offset());
    }

    @Test
    void resultsAreIdenticalForIdenticalInputs() {
        TugSim a = sim(6, 7, 1.1, 1.0, NORMAL);
        TugSim b = sim(6, 7, 1.1, 1.0, NORMAL);
        for (int t = 0; t < 500; t++) {
            double effort = 0.4 + 0.6 * Math.abs(Math.sin(t / 37.0));
            for (TugSim s : List.of(a, b)) {
                for (int i = 0; i < s.size(); i++) {
                    s.setStance(i, i % 2 == 0 ? effort : 0.5, i % 5 == 0 && t % 90 > 45);
                }
                if (t % 28 == 1) {
                    s.heave(3, t, 0);
                    s.heave(9, t + 1, 1);
                }
                s.tick(t);
            }
            assertEquals(a.offset(), b.offset(), 0.0);
            assertEquals(a.velocity(), b.velocity(), 0.0);
        }
    }

    @Test
    void theOutcomeIsFinal() {
        TugSim s = sim(4, 4);
        setAll(s, TugRules.TEAM_B, 1.0, false);
        run(s, 0, 600);
        assertEquals(TugSim.Outcome.B_WINS, s.outcome());
        setAll(s, TugRules.TEAM_B, 0.0, false);
        setAll(s, TugRules.TEAM_A, 1.0, false);
        run(s, 600, 600);
        assertEquals(TugSim.Outcome.B_WINS, s.outcome());
        assertEquals(1.0, s.offset(), 0.0);
        assertEquals(TugSim.HeaveResult.INACTIVE, s.heave(0, 700, 0).result());
    }

    @Test
    void teamSizeDoesNotChangeTheScaleOfTheForcesButSmallTeamsPullAHeavierRope() {
        // one team pulling flat out against nobody: the same rope speed from 8 v 8 up to 32 v 32 ...
        TugSim eight = sim(8, 8);
        TugSim large = sim(32, 32);
        TugSim small = sim(2, 2);
        for (TugSim s : new TugSim[]{eight, large, small}) {
            setAll(s, TugRules.TEAM_B, 1.0, false);
            run(s, 0, 60);
        }
        assertEquals(large.offset(), eight.offset(), 0.02 * large.offset());
        // ... and a rope that responds 1 / ropeWeight times as fast to a 2 v 2
        assertEquals(large.offset() / TugRules.ropeWeight(2), small.offset(), 0.03 * large.offset());
        assertEquals(large.force(TugRules.TEAM_B), small.force(TugRules.TEAM_B), 1e-9, "the displayed force keeps its scale");
    }

    @Test
    void aHandicapIsAForceMultiplier() {
        TugSim s = sim(8, 8, 1.5, 1.0, NORMAL);
        setAll(s, TugRules.TEAM_A, 1.0, false);
        setAll(s, TugRules.TEAM_B, 1.0, false);
        run(s, 0, 100);
        assertTrue(s.offset() < -0.05, "the handicapped team A wins ground: " + s.offset());
    }

    // ------------------------------------------------------------------ stamina

    @Test
    void pullingDrainsAndRestingRecovers() {
        TugSim s = sim(2, 2);
        s.setStance(0, 1.0, false);
        run(s, 0, 100);
        assertEquals(1.0 - 100 * TugRules.PULL_DRAIN, s.member(0).stamina(), 1e-9);
        assertEquals(TugSim.Stance.PULL, s.member(0).stance());
        s.setStance(0, 0.0, false);
        run(s, 100, 50);
        assertEquals(1.0 - 100 * TugRules.PULL_DRAIN + 50 * TugRules.REST_RECOVERY, s.member(0).stamina(), 1e-9);
        assertEquals(TugSim.Stance.REST, s.member(0).stance());
    }

    @Test
    void halfEffortCostsHalfAndPullsHalfAsHard() {
        TugSim full = sim(4, 4);
        TugSim half = sim(4, 4);
        setAll(full, TugRules.TEAM_B, 1.0, false);
        setAll(half, TugRules.TEAM_B, 0.5, false);
        run(full, 0, 40);
        run(half, 0, 40);
        assertEquals(1.0 - 40 * TugRules.PULL_DRAIN, full.member(4).stamina(), 1e-9);
        assertEquals(1.0 - 20 * TugRules.PULL_DRAIN, half.member(4).stamina(), 1e-9);
        assertTrue(Math.abs(half.force(TugRules.TEAM_B) * 2 - full.force(TugRules.TEAM_B)) < 1e-9);
    }

    @Test
    void bracingIsCheaperThanPulling() {
        TugSim s = sim(2, 2);
        s.setStance(0, 0.0, true);
        s.setStance(1, 1.0, false);
        run(s, 0, 100);
        double braced = 1.0 - s.member(0).stamina();
        double pulled = 1.0 - s.member(1).stamina();
        assertEquals(TugRules.BRACE_DRAIN_FRACTION, braced / pulled, 1e-9);
        assertEquals(TugSim.Stance.BRACE, s.member(0).stance());
    }

    @Test
    void braceOverridesPulling() {
        TugSim s = sim(2, 2);
        s.setStance(0, 1.0, true);
        run(s, 0, 10);
        assertEquals(TugSim.Stance.BRACE, s.member(0).stance());
        assertEquals(0.0, s.force(TugRules.TEAM_A), 1e-12);
    }

    @Test
    void anExhaustedMemberCanOnlyBraceUntilRecovered() {
        TugSim s = deadlock(2);
        int t = 0;
        while (!s.member(0).exhausted()) {
            s.tick(t++);
            assertTrue(t < 1000, "never exhausted");
        }
        assertEquals(0.0, s.member(0).stamina(), 0.0);
        assertEquals(TugSim.Stance.SPENT, s.member(0).stance());
        assertTrue(s.drainEvents().contains(new TugSim.Event(TugSim.EventKind.EXHAUSTED, 1)));
        assertTrue(s.drainEvents().isEmpty(), "events are delivered once");
        // bracing while exhausted: allowed, free, recovers slowly
        s.setStance(0, 0.0, true);
        s.tick(t++);
        assertEquals(TugRules.REST_RECOVERY * TugRules.EXHAUSTED_BRACE_RECOVERY, s.member(0).stamina(), 1e-12);
        assertEquals(TugSim.Stance.SPENT, s.member(0).stance());
        // still holding the pull key while exhausted: this member pulls nothing, but recovers like at rest
        s.setStance(0, 1.0, false);
        double before = s.member(0).stamina();
        double teamForce = s.force(TugRules.TEAM_A);
        s.tick(t++);
        assertEquals(before + TugRules.REST_RECOVERY, s.member(0).stamina(), 1e-12);
        assertTrue(s.force(TugRules.TEAM_A) < teamForce + 1e-12, "the exhausted member adds nothing");
        // the member pulls again once it is back above the threshold
        int spent = 0;
        while (s.member(0).exhausted()) {
            s.tick(t++);
            spent++;
            assertTrue(spent < 2000);
        }
        assertTrue(s.member(0).stamina() >= TugRules.RECOVER_THRESHOLD);
        assertTrue(s.drainEvents().contains(new TugSim.Event(TugSim.EventKind.RECOVERED, 1)));
        s.tick(t);
        assertEquals(TugSim.Stance.PULL, s.member(0).stance());
    }

    @Test
    void anAnchorHoldsAgainstAModestPullButNotAgainstAFullOne() {
        TugSim holds = sim(8, 8);
        setAll(holds, TugRules.TEAM_A, 0.0, true);
        for (int i = 8; i < 11; i++) {
            holds.setStance(i, 1.0, false);                  // 3 of 8 pull: raw 0.375 < the anchor of 0.45
        }
        run(holds, 0, 300);
        assertEquals(0.0, holds.offset(), 1e-9, "the anchored team is not dragged");

        TugSim gives = sim(8, 8);
        setAll(gives, TugRules.TEAM_A, 0.0, true);
        setAll(gives, TugRules.TEAM_B, 1.0, false);
        run(gives, 0, 300);
        assertTrue(gives.offset() > 0.3, "a full pull overcomes the anchor: " + gives.offset());
    }

    @Test
    void anchorsOnlyResistTheDirectionTheTeamIsDragged() {
        // team A braces fully but B does not pull: nothing moves; A cannot pull the rope back by bracing
        TugSim s = sim(8, 8);
        setAll(s, TugRules.TEAM_A, 0.0, true);
        run(s, 0, 100);
        assertEquals(0.0, s.offset(), 1e-12);
    }

    @Test
    void harderDifficultiesDrainMoreStaminaPerTick() {
        double previous = 0;
        for (Difficulty d : Difficulty.values()) {
            TugSim s = sim(2, 2, 1.0, 1.0, TugRules.params(d));
            s.setStance(0, 1.0, false);
            run(s, 0, 100);
            double spent = 1.0 - s.member(0).stamina();
            assertTrue(spent > previous, d + " costs more than the easier one");
            previous = spent;
        }
    }

    @Test
    void enduranceMakesAMemberLastLonger() {
        List<TugSim.Spec> specs = List.of(
                new TugSim.Spec(1, TugRules.TEAM_A, 1.0, 1.4), new TugSim.Spec(2, TugRules.TEAM_A, 1.0, 0.7),
                new TugSim.Spec(3, TugRules.TEAM_B, 1.0, 1.0), new TugSim.Spec(4, TugRules.TEAM_B, 1.0, 1.0));
        TugSim s = new TugSim(NORMAL, BEAT, specs, 1.0, 1.0, 0);
        s.setStance(0, 1.0, false);
        s.setStance(1, 1.0, false);
        run(s, 0, 100);
        assertTrue(s.member(0).stamina() > s.member(1).stamina());
    }

    // ------------------------------------------------------------------ heaves

    @Test
    void aHeaveOnTheBeatMovesTheRopeFartherThanNoHeave() {
        TugSim with = sim(8, 8);
        TugSim without = sim(8, 8);
        for (TugSim s : List.of(with, without)) {
            setAll(s, TugRules.TEAM_B, 1.0, false);
            setAll(s, TugRules.TEAM_A, 1.0, false);
        }
        run(with, 0, 28);
        run(without, 0, 28);
        for (int i = 8; i < 16; i++) {
            assertEquals(TugSim.HeaveResult.HIT, with.heave(i, 28, 0).result());
        }
        run(with, 28, 30);
        run(without, 28, 30);
        assertTrue(with.offset() > without.offset() + 0.01, with.offset() + " vs " + without.offset());
    }

    @Test
    void aMistimedHeaveCostsStaminaAndGivesNothing() {
        TugSim s = sim(4, 4);
        setAll(s, TugRules.TEAM_A, 1.0, false);
        setAll(s, TugRules.TEAM_B, 1.0, false);
        TugSim.HeaveReport r = s.heave(4, 14, 0);                       // half way between two beats
        assertEquals(TugSim.HeaveResult.MISTIMED, r.result());
        assertEquals(0.0, r.quality(), 0.0);
        assertEquals(1.0 - TugRules.HEAVE_COST, s.member(4).stamina(), 1e-9);
        assertEquals(1, s.member(4).heavesMissed());
        run(s, 0, 40);
        assertEquals(0.0, s.offset(), 1e-12, "nothing was gained");
    }

    @Test
    void heaveQualityFallsTheFartherFromTheBeat() {
        double previous = 2;
        for (int err = 0; err <= 3; err++) {
            TugSim s = sim(2, 2);
            TugSim.HeaveReport r = s.heave(0, 56 + err, 0);
            assertEquals(TugSim.HeaveResult.HIT, r.result(), "error " + err);
            assertEquals(err, r.error());
            assertTrue(r.quality() < previous);
            previous = r.quality();
        }
        assertEquals(1.0, sim(2, 2).heave(0, 28, 0).quality(), 1e-12);
        TugSim early = sim(2, 2);
        assertEquals(-2, early.heave(0, 26, 0).error());
        assertEquals(TugSim.HeaveResult.MISTIMED, sim(2, 2).heave(0, 28 + 4, 0).result());
    }

    @Test
    void humansGetTheForgivenessOnTopOfTheWindow() {
        assertEquals(TugSim.HeaveResult.MISTIMED, sim(2, 2).heave(0, 28 + 4, 0).result());
        TugSim.HeaveReport r = sim(2, 2).heave(0, 28 + 4, TugRules.HUMAN_FORGIVENESS);
        assertEquals(TugSim.HeaveResult.HIT, r.result());
        assertTrue(r.quality() > 0 && r.quality() < 0.5);
        assertEquals(1.0, sim(2, 2).heave(0, 28 + 1, TugRules.HUMAN_FORGIVENESS).quality(), 1e-12, "within the forgiveness: perfect");
    }

    @Test
    void theWindowShrinksWithTheDifficulty() {
        TugSim hard = sim(2, 2, 1.0, 1.0, TugRules.params(Difficulty.HARD));
        TugSim extreme = sim(2, 2, 1.0, 1.0, TugRules.params(Difficulty.EXTREME));
        assertEquals(TugSim.HeaveResult.HIT, hard.heave(0, 28 + 2, 0).result());
        assertEquals(TugSim.HeaveResult.MISTIMED, hard.heave(1, 28 + 3, 0).result());
        assertEquals(TugSim.HeaveResult.HIT, extreme.heave(0, 28 + 1, 0).result());
        assertEquals(TugSim.HeaveResult.MISTIMED, extreme.heave(1, 28 + 2, 0).result());
    }

    @Test
    void oneHeavePerBeatAndNoMashing() {
        TugSim s = sim(2, 2);
        assertEquals(TugSim.HeaveResult.HIT, s.heave(0, 28, 0).result());
        assertEquals(TugSim.HeaveResult.TOO_SOON, s.heave(0, 30, 0).result(), "inside the minimum gap: free and ignored");
        assertEquals(1.0 - TugRules.HEAVE_COST, s.member(0).stamina(), 1e-9);
        // late enough to be a new press but still the same beat: a second heave in one beat gives nothing
        assertEquals(TugSim.HeaveResult.MISTIMED, s.heave(0, 28 + TugRules.MIN_HEAVE_GAP, 5).result());
        assertEquals(1.0 - 2 * TugRules.HEAVE_COST, s.member(0).stamina(), 1e-9);
        assertEquals(TugSim.HeaveResult.HIT, s.heave(0, 56, 0).result(), "the next beat is fine");
    }

    @Test
    void noStaminaNoHeave() {
        TugSim s = deadlock(2);
        int t = 0;
        while (!s.member(0).exhausted()) {
            s.tick(t++);
            assertTrue(t < 1000, "never exhausted");
        }
        long beat = BEAT.nextBeatAtOrAfter(t);
        assertEquals(TugSim.HeaveResult.EXHAUSTED, s.heave(0, beat, 0).result());
        assertEquals(0.0, s.member(0).stamina(), 0.0, "refused for free");
    }

    @Test
    void aTiredMemberHeavesWeaker() {
        TugSim fresh = sim(8, 8);
        TugSim tired = deadlock(8);
        run(tired, 0, 380);                                              // stamina about 0.13, not exhausted yet
        run(fresh, 0, 380);
        assertFalse(tired.member(0).exhausted());
        assertTrue(tired.member(0).stamina() < 0.2);
        long beat = BEAT.nextBeatAtOrAfter(380);
        fresh.heave(0, beat, 0);
        tired.heave(0, beat, 0);
        setAll(tired, TugRules.TEAM_A, 0.0, false);
        setAll(tired, TugRules.TEAM_B, 0.0, false);
        fresh.tick(380);
        tired.tick(380);
        assertTrue(tired.force(TugRules.TEAM_A) < fresh.force(TugRules.TEAM_A) * 0.7,
                tired.force(TugRules.TEAM_A) + " vs " + fresh.force(TugRules.TEAM_A));
    }

    @Test
    void togetherIsWorthMoreThanTheSum() {
        // the impulse of k members heaving in the same tick, relative to one member's
        double one = impulseOfHeavers(1);
        double eight = impulseOfHeavers(8);
        assertTrue(eight > 8 * one * 1.25, "eight heaving together: " + eight + " vs 8 x " + one);
        assertTrue(eight < 8 * one * (1 + TugRules.SYNC_BONUS) * 1.0001 + 1e-9, "never more than the sync bonus allows");
    }

    private static double impulseOfHeavers(int k) {
        TugSim s = sim(8, 8);
        run(s, 0, 28);
        for (int i = 8; i < 8 + k; i++) {
            s.heave(i, 28, 0);
        }
        double sum = 0;
        for (int t = 28; t < 28 + TugRules.PULSE_TICKS + 2; t++) {
            s.tick(t);
            sum += s.force(TugRules.TEAM_B);
        }
        return sum;
    }

    // ------------------------------------------------------------------ members leaving, timeouts

    @Test
    void aRemovedMemberStopsContributingWithoutBreakingAnything() {
        TugSim s = sim(4, 4);
        setAll(s, TugRules.TEAM_B, 1.0, false);
        s.tick(0);
        double before = s.force(TugRules.TEAM_B);
        s.remove(4);
        s.tick(1);
        assertEquals(before * 0.75, s.force(TugRules.TEAM_B), 1e-9);
        assertEquals(3, s.presentCount(TugRules.TEAM_B));
        s.remove(4);                                                      // removing twice is harmless
        assertEquals(TugSim.HeaveResult.INACTIVE, s.heave(4, 28, 0).result());
        s.setStance(4, 1.0, false);                                       // and so are inputs for a removed member
        run(s, 2, 100);
        assertEquals(TugSim.Outcome.ONGOING, s.outcome());
    }

    @Test
    void aTeamWithoutMembersForfeits() {
        TugSim s = sim(3, 3);
        for (int i = 0; i < 3; i++) {
            s.remove(i);
        }
        assertEquals(TugSim.Outcome.B_WINS, s.outcome());
        s.tick(0);
        assertEquals(TugSim.Outcome.B_WINS, s.outcome());
        assertEquals(TugSim.Outcome.A_WINS, new TugSim(NORMAL, BEAT, List.of(new TugSim.Spec(1, 0, 1, 1)), 1, 1, 0).outcome());
    }

    @Test
    void theVerdictAtTheTimeoutFollowsTheRope() {
        TugSim s = sim(8, 8);
        setAll(s, TugRules.TEAM_B, 1.0, false);
        run(s, 0, 60);
        assertTrue(s.offset() > TugRules.TIE_EPSILON);
        assertEquals(TugSim.Outcome.B_WINS, s.verdictAtTimeout());
        TugSim tie = sim(8, 8);
        run(tie, 0, 10);
        assertEquals(TugSim.Outcome.ONGOING, tie.verdictAtTimeout(), "an exact tie has no verdict");
    }

    @Test
    void suddenDeathRefillsEverybodyAndStopsTheRope() {
        TugSim s = sim(4, 4);
        setAll(s, TugRules.TEAM_A, 1.0, false);
        setAll(s, TugRules.TEAM_B, 1.0, false);
        run(s, 0, 300);                                                   // everybody is spent
        assertTrue(s.member(0).stamina() < 0.5);
        assertEquals(TugSim.Outcome.ONGOING, s.outcome());
        s.heave(1, BEAT.nextBeatAtOrAfter(300), 0);
        s.startSuddenDeath();
        for (int i = 0; i < s.size(); i++) {
            assertEquals(1.0, s.member(i).stamina(), 0.0);
            assertFalse(s.member(i).exhausted());
        }
        assertEquals(0.0, s.velocity(), 0.0);
    }

    @Test
    void theStrainShowsHowHardBothTeamsPull() {
        TugSim idle = sim(8, 8);
        run(idle, 0, 100);
        assertEquals(0.0, idle.strain(), 1e-9);
        TugSim tug = sim(8, 8);
        setAll(tug, TugRules.TEAM_A, 1.0, false);
        setAll(tug, TugRules.TEAM_B, 1.0, false);
        run(tug, 0, 100);
        assertTrue(tug.strain() > 0.7 && tug.strain() <= 1.0, "strain " + tug.strain());
        TugSim braced = sim(8, 8);
        setAll(braced, TugRules.TEAM_A, 0.0, true);
        setAll(braced, TugRules.TEAM_B, 0.0, true);
        run(braced, 0, 100);
        assertTrue(braced.strain() > 0.2 && braced.strain() < tug.strain());
    }

    @Test
    void meanStaminaAndStrainedShareDescribeTheTeam() {
        TugSim s = sim(4, 8);
        setAll(s, TugRules.TEAM_A, 1.0, false);
        setAll(s, TugRules.TEAM_B, 0.0, true);                            // the eight of B anchor and do not give way
        run(s, 0, 380);
        assertEquals(TugSim.Outcome.ONGOING, s.outcome());
        assertTrue(s.meanStamina(TugRules.TEAM_A) < 0.25, "A pulled for 19 s: " + s.meanStamina(TugRules.TEAM_A));
        assertTrue(s.meanStamina(TugRules.TEAM_B) > 0.7, "B only braced");
        assertEquals(1.0, s.strainedFraction(TugRules.TEAM_A), 1e-9);
        assertEquals(0.0, s.strainedFraction(TugRules.TEAM_B), 1e-9);
    }
}
