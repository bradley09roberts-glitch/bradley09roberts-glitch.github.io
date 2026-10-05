package com.squidgame.core.marbles;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.Personality.Archetype;
import com.squidgame.core.marbles.MatchOutcome.Reason;
import com.squidgame.core.marbles.ThrowModel.Landing;
import com.squidgame.core.marbles.ThrowModel.Vec;
import com.squidgame.core.marbles.ThrowScoring.Result;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Target scoring, throw physics / controls and the throw duel. */
class ThrowTest {
    /** The court geometry of docs/ARENA_MARKERS.md: pads 1 block behind the line, bullseye 7 beyond it (court-local). */
    private static final Vec PAD_A = new Vec(-1.0, 1.0 + 1.62 - 0.1, 1.5);
    private static final Vec PAD_B = new Vec(2.0, 1.0 + 1.62 - 0.1, 1.5);
    private static final Vec BULLSEYE = new Vec(0.5, 1.0, 9.5);

    // ------------------------------------------------------------------ scoring

    @Test
    void ringsMatchThePaintedBlocks() {
        assertEquals(ThrowScoring.BULLSEYE, ThrowScoring.ringOfBlock(0, 0));
        assertEquals(4, ThrowScoring.ringOfBlock(1, 0));
        assertEquals(4, ThrowScoring.ringOfBlock(0, -1));
        assertEquals(3, ThrowScoring.ringOfBlock(1, 1));
        assertEquals(3, ThrowScoring.ringOfBlock(-1, 1));
        assertEquals(2, ThrowScoring.ringOfBlock(2, 0));
        assertEquals(2, ThrowScoring.ringOfBlock(0, -2));
        assertEquals(1, ThrowScoring.ringOfBlock(2, 1));
        assertEquals(1, ThrowScoring.ringOfBlock(-1, 2));
        assertEquals(ThrowScoring.OUTER_RING, ThrowScoring.ringOfBlock(2, 2));
        assertEquals(ThrowScoring.OUTER_RING, ThrowScoring.ringOfBlock(-2, 2));
        assertEquals(ThrowScoring.MISS, ThrowScoring.ringOfBlock(3, 0));
        assertEquals(ThrowScoring.MISS, ThrowScoring.ringOfBlock(0, -3));
        assertEquals(ThrowScoring.MISS, ThrowScoring.ringOfBlock(3, 3));
    }

    @Test
    void scoreUsesTheBlockUnderTheMarbleAndTheExactDistance() {
        Result centre = ThrowScoring.score(0.4, -0.4);
        assertEquals(ThrowScoring.BULLSEYE, centre.ring());
        assertEquals(Math.hypot(0.4, 0.4), centre.distance(), 1e-9);
        assertTrue(centre.onTarget());
        assertEquals(4, ThrowScoring.score(0.6, 0).ring());
        assertEquals(4, ThrowScoring.score(-0.6, 0).ring());
        assertEquals(ThrowScoring.MISS, ThrowScoring.score(2.6, 0).ring());
        assertFalse(ThrowScoring.score(0, 3.1).onTarget());
    }

    @Test
    void closerMarbleWinsAndEqualDistanceIsADraw() {
        Result near = ThrowScoring.score(0.2, 0.1), far = ThrowScoring.score(1.5, 0.5);
        assertTrue(ThrowScoring.compare(near, far) < 0);
        assertTrue(ThrowScoring.compare(far, near) > 0);
        assertEquals(0, ThrowScoring.compare(near, ThrowScoring.score(-0.2, -0.1)));
    }

    @Test
    void stakeGrowsWithTheRingGapAndIsCapped() {
        Result gold = ThrowScoring.score(0, 0), red = ThrowScoring.score(1, 0), outer = ThrowScoring.score(2, 2),
                miss = ThrowScoring.score(5, 5);
        assertEquals(1, ThrowScoring.stake(red, gold, 4), "a winner with a worse ring still wins one marble");
        assertEquals(1, ThrowScoring.stake(gold, red, 4));
        assertEquals(3, ThrowScoring.stake(gold, outer, 4));
        assertEquals(4, ThrowScoring.stake(gold, miss, 4));
        assertEquals(2, ThrowScoring.stake(gold, miss, 2), "capped");
        assertEquals(1, ThrowScoring.stake(gold, miss, 0), "never below one");
        int last = 0;
        for (int ring = ThrowScoring.BULLSEYE; ring >= ThrowScoring.MISS; ring--) {
            int s = ThrowScoring.stake(new Result(ThrowScoring.BULLSEYE, 0.1), new Result(ring, 1), 6);
            assertTrue(s >= last, "the stake grows as the losing ring gets worse");
            last = s;
        }
        assertEquals(4, last);
    }

    // ------------------------------------------------------------------ physics and controls

    private static final ThrowModel.Params NORMAL = MarblesRules.params(Difficulty.NORMAL).throwModel();

    @Test
    void speedAndChargeAreInverseAndMonotonic() {
        double prev = -1;
        for (int t = 0; t <= NORMAL.maxChargeTicks(); t++) {
            double s = ThrowModel.speed(NORMAL, t);
            assertTrue(s > prev);
            prev = s;
            assertEquals(t, ThrowModel.chargeFor(NORMAL, s), 1e-9);
        }
        assertEquals(NORMAL.minSpeed(), ThrowModel.speed(NORMAL, 0), 1e-12);
        assertEquals(NORMAL.maxSpeed(), ThrowModel.speed(NORMAL, NORMAL.maxChargeTicks()), 1e-12);
        // holding longer than the full charge does not add power
        assertEquals(NORMAL.maxSpeed(), ThrowModel.speed(NORMAL, 10 * NORMAL.maxChargeTicks()), 1e-12);
    }

    @Test
    void landingRangeGrowsWithSpeedAndEndsOnTheFloor() {
        double prev = 0;
        for (double s = 0.2; s <= 0.9; s += 0.05) {
            Vec v = ThrowModel.velocity(NORMAL, PAD_A, BULLSEYE, s, 0);
            Landing l = ThrowModel.landing(PAD_A, v, 1.0);
            assertEquals(1.0, l.y(), 1e-12);
            double range = Math.hypot(l.x() - PAD_A.x(), l.z() - PAD_A.z());
            assertTrue(range > prev, "range must grow with speed");
            prev = range;
            assertTrue(l.ticks() > 5 && l.ticks() < 120);
        }
    }

    @Test
    void idealSpeedLandsOnTheAimPointForEveryDistance() {
        for (double dist = 2.5; dist <= 12; dist += 0.5) {
            Vec aim = new Vec(PAD_A.x() + 0.3 * dist, 1.0, PAD_A.z() + dist);
            double s = ThrowModel.idealSpeed(NORMAL, PAD_A, aim);
            Landing l = ThrowModel.landing(PAD_A, ThrowModel.velocity(NORMAL, PAD_A, aim, s, 0), 1.0);
            assertEquals(aim.x(), l.x(), 0.01, "dist " + dist);
            assertEquals(aim.z(), l.z(), 0.01, "dist " + dist);
        }
    }

    @Test
    void launchPointsAtTheAimOnTheFixedArc() {
        Vec aim = new Vec(5, 1.0, -3);
        Vec v = ThrowModel.velocity(NORMAL, PAD_A, aim, 0.5, 0);
        assertEquals(0.5, Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z()), 1e-9);
        assertEquals(Math.toDegrees(Math.asin(v.y() / 0.5)), NORMAL.arcPitchDeg(), 1e-9);
        double dx = aim.x() - PAD_A.x(), dz = aim.z() - PAD_A.z();
        assertEquals(Math.atan2(dz, dx), Math.atan2(v.z(), v.x()), 1e-9);
        // the tremor yaws the throw sideways without changing its speed
        Vec j = ThrowModel.velocity(NORMAL, PAD_A, aim, 0.5, 2.0);
        assertEquals(Math.toRadians(2.0), Math.atan2(j.z(), j.x()) - Math.atan2(v.z(), v.x()), 1e-9);
    }

    @Test
    void lookingAtASpotOnTheFloorAimsThere() {
        Vec spot = new Vec(1.2, 1.0, 8.0);
        Vec look = new Vec(spot.x() - PAD_B.x(), spot.y() - PAD_B.y(), spot.z() - PAD_B.z());
        Vec aim = ThrowModel.aimFromLook(NORMAL, PAD_B, look, 1.0);
        assertEquals(spot.x(), aim.x(), 1e-9);
        assertEquals(spot.z(), aim.z(), 1e-9);
        assertEquals(1.0, aim.y(), 1e-12);
    }

    @Test
    void aimIsClampedToTheThrowableRange() {
        double max = ThrowModel.maxRange(NORMAL, PAD_A.y() - 1.0);
        assertTrue(max >= 10.5, "a full-power throw must clear the whole court, was " + max);
        Vec horizon = ThrowModel.aimFromLook(NORMAL, PAD_A, new Vec(0, 0, 1), 1.0);
        assertEquals(max, horizon.z() - PAD_A.z(), 1e-6);
        Vec up = ThrowModel.aimFromLook(NORMAL, PAD_A, new Vec(0, 0.7, 1), 1.0);
        assertEquals(max, up.z() - PAD_A.z(), 1e-6);
        Vec down = ThrowModel.aimFromLook(NORMAL, PAD_A, new Vec(0, -5, 0.1), 1.0);
        assertEquals(ThrowModel.MIN_AIM_RANGE, PAD_A.horizontalDistance(down), 1e-6);
        Vec vertical = ThrowModel.aimFromLook(NORMAL, PAD_A, new Vec(0, -1, 0), 1.0);
        assertTrue(Double.isFinite(vertical.x()) && Double.isFinite(vertical.z()));
    }

    @Test
    void theBullseyeNeedsASensibleChargeOnEveryDifficultyAndPad() {
        for (Difficulty d : Difficulty.values()) {
            ThrowModel.Params p = MarblesRules.params(d).throwModel();
            for (Vec pad : new Vec[]{PAD_A, PAD_B}) {
                double frac = ThrowModel.idealCharge(p, pad, BULLSEYE) / p.maxChargeTicks();
                assertTrue(frac > 0.4 && frac < 0.9, d + " ideal charge fraction " + frac);
                assertTrue(ThrowModel.idealCharge(p, pad, BULLSEYE) > p.minChargeTicks());
            }
        }
    }

    @Test
    void releasingAtTheIdealChargeLandsOnTheBullseyeBlock() {
        for (Difficulty d : Difficulty.values()) {
            ThrowModel.Params p = MarblesRules.params(d).throwModel();
            for (Vec pad : new Vec[]{PAD_A, PAD_B}) {
                int ticks = (int) Math.round(ThrowModel.idealCharge(p, pad, BULLSEYE));
                Landing l = ThrowModel.landing(pad, ThrowModel.launch(p, pad, BULLSEYE, ticks, null), 1.0);
                Result r = ThrowScoring.score(l.x() - BULLSEYE.x(), l.z() - BULLSEYE.z());
                assertTrue(r.ring() >= 3, d + ": rounding the charge to whole ticks must stay near the middle, was " + r);
            }
        }
    }

    @Test
    void tremorIsDeterministicPerSeedAndGrowsWithDifficulty() {
        int ticks = (int) Math.round(ThrowModel.idealCharge(NORMAL, PAD_A, BULLSEYE));
        Vec a = ThrowModel.launch(NORMAL, PAD_A, BULLSEYE, ticks, new Rng(9));
        Vec b = ThrowModel.launch(NORMAL, PAD_A, BULLSEYE, ticks, new Rng(9));
        assertEquals(a, b);
        assertNotEquals(a, ThrowModel.launch(NORMAL, PAD_A, BULLSEYE, ticks, new Rng(10)));
        double normal = meanDistanceOfIdealThrows(Difficulty.NORMAL), extreme = meanDistanceOfIdealThrows(Difficulty.EXTREME);
        assertTrue(extreme > normal * 1.3, "the tremor must hurt more on Extreme (" + normal + " vs " + extreme + ")");
    }

    private static double meanDistanceOfIdealThrows(Difficulty d) {
        ThrowModel.Params p = MarblesRules.params(d).throwModel();
        Rng rng = new Rng(21);
        int ticks = (int) Math.round(ThrowModel.idealCharge(p, PAD_A, BULLSEYE));
        double sum = 0;
        int n = 600;
        for (int i = 0; i < n; i++) {
            Landing l = ThrowModel.landing(PAD_A, ThrowModel.launch(p, PAD_A, BULLSEYE, ticks, rng), 1.0);
            sum += Math.hypot(l.x() - BULLSEYE.x(), l.z() - BULLSEYE.z());
        }
        return sum / n;
    }

    // ------------------------------------------------------------------ NPC throws

    private static Personality skilled(float skill) {
        return new Personality(Archetype.VETERAN, 0.6f, 0.6f, 0.5f, skill, 0.4f, 0.5f, 0.5f);
    }

    private static double npcMeanDistance(float skill, Difficulty d, double pressure, long seed) {
        ThrowModel.Params p = MarblesRules.params(d).throwModel();
        Personality person = skilled(skill);
        Rng rng = new Rng(seed);
        double sum = 0;
        int n = 500;
        for (int i = 0; i < n; i++) {
            ThrowStrategy.Plan plan = ThrowStrategy.plan(person, d, p, PAD_B, BULLSEYE, pressure, rng);
            assertTrue(plan.chargeTicks() >= p.minChargeTicks() && plan.chargeTicks() <= p.maxChargeTicks());
            Landing l = ThrowModel.landing(PAD_B, ThrowModel.launch(p, PAD_B, plan.aim(), plan.chargeTicks(), rng), 1.0);
            sum += Math.hypot(l.x() - BULLSEYE.x(), l.z() - BULLSEYE.z());
        }
        return sum / n;
    }

    @Test
    void skilledNpcsThrowCloserThanRookies() {
        double rookie = npcMeanDistance(0.05f, Difficulty.NORMAL, 0, 1);
        double average = npcMeanDistance(0.5f, Difficulty.NORMAL, 0, 1);
        double expert = npcMeanDistance(0.95f, Difficulty.NORMAL, 0, 1);
        assertTrue(rookie > average && average > expert, rookie + " > " + average + " > " + expert);
        assertTrue(expert < 0.6 * rookie);
        assertTrue(rookie > 1.0, "a rookie rarely hits the middle: " + rookie);
        assertTrue(expert < 1.0, "an expert nearly always lands on the target: " + expert);
    }

    @Test
    void nervesCostNpcsAccuracyAndStrongerOpponentsOnHarderDifficulties() {
        double calm = npcMeanDistance(0.5f, Difficulty.NORMAL, 0.0, 4);
        double pressured = npcMeanDistance(0.5f, Difficulty.NORMAL, 1.0, 4);
        assertTrue(pressured > calm);
        // the difficulty's skill bonus makes the same contestant a stronger thrower (the tremor is the same for all)
        assertTrue(ThrowStrategy.aimSigma(0.75, 0) < ThrowStrategy.aimSigma(0.5, 0));
        assertTrue(ThrowStrategy.timingSigma(0.75, 0) < ThrowStrategy.timingSigma(0.5, 0));
    }

    // ------------------------------------------------------------------ duel

    private static Result at(double distance) {
        return new Result(ThrowScoring.ringOfBlock((int) Math.round(distance), 0), distance);
    }

    @Test
    void turnsAlternateWithinAndAcrossRounds() {
        ThrowDuel d = new ThrowDuel(10, Side.B, 8, 4);
        assertEquals(Side.B, d.turn());
        assertFalse(d.recordThrow(Side.A, at(1)), "not A's turn");
        assertTrue(d.recordThrow(Side.B, at(1)));
        assertEquals(Side.A, d.turn());
        assertFalse(d.recordThrow(Side.B, at(1)), "one throw per turn");
        assertFalse(d.roundComplete());
        assertTrue(d.recordThrow(Side.A, at(2)));
        assertTrue(d.roundComplete());
        assertNull(d.turn());
        d.resolveRound();
        d.advance(false);
        assertEquals(2, d.roundNo());
        assertEquals(Side.A, d.turn(), "the first thrower alternates");
    }

    @Test
    void closerMarbleWinsMarblesFromTheOther() {
        ThrowDuel d = new ThrowDuel(10, Side.A, 8, 4);
        d.recordThrow(Side.A, new Result(ThrowScoring.BULLSEYE, 0.2));
        d.recordThrow(Side.B, new Result(ThrowScoring.OUTER_RING, 2.6));
        ThrowDuel.RoundResult r = d.resolveRound();
        assertEquals(Side.A, r.winner());
        assertEquals(3, r.stake());
        assertEquals(3, r.moved());
        assertEquals(13, d.ledger().count(Side.A));
        assertEquals(7, d.ledger().count(Side.B));
        assertEquals(20, d.ledger().total());
    }

    @Test
    void exactDrawMovesNothing() {
        ThrowDuel d = new ThrowDuel(5, Side.A, 8, 4);
        d.recordThrow(Side.A, new Result(4, 1.0));
        d.recordThrow(Side.B, new Result(4, 1.0));
        ThrowDuel.RoundResult r = d.resolveRound();
        assertNull(r.winner());
        assertEquals(0, r.moved());
        assertEquals(5, d.ledger().count(Side.A));
        assertFalse(d.over());
    }

    @Test
    void lastMarblesEndTheMatchAndTheStakeNeverExceedsWhatTheLoserHas() {
        ThrowDuel d = new ThrowDuel(2, Side.A, 8, 4);
        d.recordThrow(Side.A, new Result(ThrowScoring.BULLSEYE, 0.1));
        d.recordThrow(Side.B, new Result(ThrowScoring.MISS, 9));
        ThrowDuel.RoundResult r = d.resolveRound();
        assertEquals(4, r.stake());
        assertEquals(2, r.moved());
        assertTrue(d.over());
        assertEquals(Reason.OUT_OF_MARBLES, d.outcome().reason());
        assertEquals(Side.A, d.outcome().winner());
        assertNull(d.turn());
        assertFalse(d.recordThrow(Side.A, at(1)));
    }

    @Test
    void budgetAndTimeCallDecideByCountAndATieIsSuddenDeath() {
        ThrowDuel lead = new ThrowDuel(10, Side.A, 1, 4);
        lead.recordThrow(Side.A, at(0));
        lead.recordThrow(Side.B, at(2));
        lead.resolveRound();
        lead.advance(false);
        assertTrue(lead.over());
        assertEquals(Reason.MORE_MARBLES, lead.outcome().reason());

        ThrowDuel tie = new ThrowDuel(10, Side.A, 8, 4);
        tie.settleByCount();
        assertFalse(tie.over());
        assertTrue(tie.isSuddenDeath());
        assertEquals(Side.B, tie.turn(), "the sudden-death round starts with the other partner");
        tie.recordThrow(Side.B, new Result(3, 1.4));
        tie.recordThrow(Side.A, new Result(4, 0.9));
        ThrowDuel.RoundResult r = tie.resolveRound();
        assertEquals(0, r.moved(), "sudden death decides the match, no marbles move");
        assertTrue(tie.over());
        assertEquals(Reason.SUDDEN_DEATH, tie.outcome().reason());
        assertEquals(Side.A, tie.outcome().winner());
    }

    @Test
    void drawnSuddenDeathIsRepeatedAndTheHardDeadlineFlipsACoin() {
        ThrowDuel d = new ThrowDuel(4, Side.A, 8, 4);
        d.settleByCount();
        d.recordThrow(d.turn(), new Result(4, 1.0));
        d.recordThrow(d.turn(), new Result(4, 1.0));
        d.resolveRound();
        assertFalse(d.over());
        d.advance(false);
        assertEquals(3, d.roundNo());
        assertNotNull(d.turn());
        d.forceResolve(new Rng(1));
        assertEquals(Reason.COIN_FLIP, d.outcome().reason());
    }

    @Test
    void forfeitWinsForThePartner() {
        ThrowDuel d = new ThrowDuel(4, Side.A, 8, 4);
        d.forfeit(Side.B);
        assertEquals(Side.A, d.outcome().winner());
        assertEquals(Reason.FORFEIT, d.outcome().reason());
    }

    @Test
    void npcDuelsAlwaysTerminateWithConservedMarbles() {
        for (Difficulty diff : Difficulty.values()) {
            MarblesRules.Params mp = MarblesRules.params(diff);
            for (int seed = 0; seed < 120; seed++) {
                Rng rng = new Rng(seed * 31L + diff.ordinal());
                Personality pa = Personality.generate(rng.fork(1)), pb = Personality.generate(rng.fork(2));
                ThrowDuel d = new ThrowDuel(mp.startMarbles(), rng.nextBoolean() ? Side.A : Side.B, mp.throwRounds(), mp.stakeCap());
                int guard = 0;
                while (!d.over()) {
                    assertTrue(++guard < 40, "duel must end");
                    while (d.turn() != null) {
                        Side s = d.turn();
                        Vec pad = s == Side.A ? PAD_A : PAD_B;
                        ThrowStrategy.Plan plan = ThrowStrategy.plan(s == Side.A ? pa : pb, diff, mp.throwModel(), pad, BULLSEYE, 0.3, rng);
                        Landing l = ThrowModel.landing(pad, ThrowModel.launch(mp.throwModel(), pad, plan.aim(), plan.chargeTicks(), rng), 1.0);
                        d.recordThrow(s, ThrowScoring.score(l.x() - BULLSEYE.x(), l.z() - BULLSEYE.z()));
                    }
                    d.resolveRound();
                    assertEquals(2 * mp.startMarbles(), d.ledger().total());
                    d.advance(false);
                }
                assertNotNull(d.outcome());
            }
        }
    }

    @Test
    void theBetterThrowerWinsMostMatches() {
        MarblesRules.Params mp = MarblesRules.params(Difficulty.NORMAL);
        Personality strong = skilled(0.9f), weak = skilled(0.1f);
        int strongWins = 0, n = 300;
        for (int seed = 0; seed < n; seed++) {
            Rng rng = new Rng(seed);
            ThrowDuel d = new ThrowDuel(mp.startMarbles(), Side.A, mp.throwRounds(), mp.stakeCap());
            while (!d.over()) {
                while (d.turn() != null) {
                    Side s = d.turn();
                    Vec pad = s == Side.A ? PAD_A : PAD_B;
                    ThrowStrategy.Plan plan = ThrowStrategy.plan(s == Side.A ? strong : weak, Difficulty.NORMAL, mp.throwModel(), pad, BULLSEYE, 0.3, rng);
                    Landing l = ThrowModel.landing(pad, ThrowModel.launch(mp.throwModel(), pad, plan.aim(), plan.chargeTicks(), rng), 1.0);
                    d.recordThrow(s, ThrowScoring.score(l.x() - BULLSEYE.x(), l.z() - BULLSEYE.z()));
                }
                d.resolveRound();
                d.advance(false);
            }
            if (d.outcome().winner() == Side.A) {
                strongWins++;
            }
        }
        assertTrue(strongWins > 0.75 * n, "skill must matter: " + strongWins + "/" + n);
        assertTrue(strongWins < n, "but luck still plays a part");
    }
}
