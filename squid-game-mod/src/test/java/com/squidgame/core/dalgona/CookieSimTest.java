package com.squidgame.core.dalgona;

import com.squidgame.core.Difficulty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CookieSimTest {
    private static CookieSim sim(DalgonaShape s, Difficulty d, long seed) {
        return new CookieSim(s, DalgonaRules.params(d), seed);
    }

    /**
     * Drives the needle along the outline at {@code speed} units per tick (one stroke per tick) with a constant sideways
     * offset, for at most {@code maxTicks} ticks. Returns the tick at which the cookie was resolved (or maxTicks).
     */
    private static int trace(CookieSim sim, double speed, double offset, int maxTicks) {
        double[] p = new double[2];
        double[] x = new double[1];
        double[] y = new double[1];
        double cursor = 0;
        boolean fresh = true;
        for (int t = 0; t < maxTicks; t++) {
            sim.tick();
            cursor += speed / sim.shape().sampleStep();
            positionAt(sim.shape(), cursor, offset, p);
            x[0] = p[0];
            y[0] = p[1];
            CookieSim.Result r = sim.stroke(x, y, 1, 1.0, fresh);
            fresh = false;
            if (r.outcome == CookieSim.Outcome.DONE || r.outcome == CookieSim.Outcome.CRACKED) {
                return t;
            }
        }
        return maxTicks;
    }

    /** Point at fractional sample position {@code cursor} along the outline, {@code offset} units to the side of it. */
    private static void positionAt(DalgonaShape s, double cursor, double offset, double[] out) {
        int n = s.sampleCount();
        int i = (int) Math.floor(cursor);
        double f = cursor - i;
        int a = ((i % n) + n) % n;
        int b = (a + 1) % n;
        double px = s.sampleX(a) + (s.sampleX(b) - s.sampleX(a)) * f;
        double py = s.sampleY(a) + (s.sampleY(b) - s.sampleY(a)) * f;
        int a2 = ((a - 2) % n + n) % n;
        int b2 = (a + 3) % n;
        double tx = s.sampleX(b2) - s.sampleX(a2);
        double ty = s.sampleY(b2) - s.sampleY(a2);
        double len = Math.hypot(tx, ty);
        out[0] = px - ty / len * offset;
        out[1] = py + tx / len * offset;
    }

    @Test
    void messagesBunchedByAServerHiccupAreJudgedLikeSpreadOnes() {
        // the same needle path (5.4 units per tick, one message per tick) delivered tick by tick, and delivered in one
        // bunch because the server stalled: the cookie must not be punished for the bunching
        for (Difficulty d : Difficulty.values()) {
            CookieSim spread = sim(DalgonaShape.TRIANGLE, d, 3);
            CookieSim bunched = sim(DalgonaShape.TRIANGLE, d, 3);
            double[] p = new double[2];
            double[] x = new double[1];
            double[] y = new double[1];
            double cursor = 0;
            // both carve calmly for a while first, so that the carve budget is full
            for (int t = 0; t < 30; t++) {
                cursor += 5.4 / spread.shape().sampleStep();
                positionAt(spread.shape(), cursor, 0, p);
                x[0] = p[0];
                y[0] = p[1];
                spread.tick();
                bunched.tick();
                spread.stroke(x, y, 1, 1.0, t == 0);
                bunched.stroke(x, y, 1, 1.0, t == 0);
            }
            double before = bunched.stress();
            for (int t = 0; t < 40; t++) {
                cursor += 5.4 / spread.shape().sampleStep();
                positionAt(spread.shape(), cursor, 0, p);
                x[0] = p[0];
                y[0] = p[1];
                spread.tick();
                spread.stroke(x, y, 1, 1.0, false);
                bunched.stroke(x, y, 1, 1.0, false);   // no tick() in between: the server was stalled
            }
            assertFalse(bunched.isCracked());
            assertTrue(bunched.stress() - before < 3.0, d + ": stress added by the bunch " + (bunched.stress() - before));
            assertEquals(spread.carvedCount(), bunched.carvedCount(), 2, d + ": the same groove is carved");
        }
    }

    @Test
    void aSteadyHandFreesEveryShapeOnEveryDifficulty() {
        for (Difficulty d : Difficulty.values()) {
            for (DalgonaShape s : DalgonaShape.values()) {
                CookieSim sim = sim(s, d, 1);
                // 45 % of the circle's safe speed: slower than any fragile spot allows
                double speed = 0.4 * DalgonaRules.params(d).safeSpeed() / Math.pow(DalgonaShape.MAX_FRAGILITY, DalgonaRules.SPEED_EXPONENT);
                int ticks = trace(sim, speed, 0, 6000);
                assertTrue(sim.isDone(), s + "/" + d + " should be freed (stopped at tick " + ticks + ")");
                assertFalse(sim.isCracked());
                assertTrue(sim.stress() < 25, s + "/" + d + " stress " + sim.stress());
                assertTrue(sim.progress() >= DalgonaRules.params(d).successThreshold() - 0.01);
            }
        }
    }

    @Test
    void rushingCracksTheCookie() {
        for (Difficulty d : Difficulty.values()) {
            CookieSim sim = sim(DalgonaShape.CIRCLE, d, 2);
            double speed = 3.0 * DalgonaRules.params(d).safeSpeed();
            trace(sim, speed, 0, 3000);
            assertTrue(sim.isCracked(), d + ": three times the safe speed must crack the cookie, stress " + sim.stress());
            assertFalse(sim.isDone());
        }
    }

    @Test
    void mildSpeedingBuildsStressSlowly() {
        CookieSim sim = sim(DalgonaShape.CIRCLE, Difficulty.NORMAL, 3);
        double safe = DalgonaRules.params(Difficulty.NORMAL).safeSpeed();
        trace(sim, 1.15 * safe, 0, 40);
        assertTrue(sim.stress() < 10, "15 % over the limit costs little: " + sim.stress());
        CookieSim fast = sim(DalgonaShape.CIRCLE, Difficulty.NORMAL, 3);
        trace(fast, 1.8 * safe, 0, 40);
        assertTrue(fast.stress() > sim.stress() * 3, "stress grows faster than the speed does");
    }

    @Test
    void wanderingFromTheGrooveCostsStressAndCarvesNothing() {
        DalgonaRules.Params p = DalgonaRules.params(Difficulty.NORMAL);
        CookieSim off = sim(DalgonaShape.CIRCLE, Difficulty.NORMAL, 4);
        trace(off, 0.5 * p.safeSpeed(), 3 * p.tolerance(), 200);
        assertEquals(0, off.carvedCount(), "carving needs the needle on the groove");
        assertTrue(off.stress() > 30, "cutting into the cookie hurts: " + off.stress());
        CookieSim edge = sim(DalgonaShape.CIRCLE, Difficulty.NORMAL, 4);
        trace(edge, 0.5 * p.safeSpeed(), 0.9 * p.tolerance(), 200);
        assertTrue(edge.carvedCount() > 40, "still carving at the edge of the corridor");
        CookieSim centre = sim(DalgonaShape.CIRCLE, Difficulty.NORMAL, 4);
        trace(centre, 0.5 * p.safeSpeed(), 0, 200);
        assertTrue(centre.stress() < edge.stress(), "a steady hand keeps the meter calmer than riding the corridor edge");
    }

    @Test
    void cuttingIntoTheFigureHurtsMoreThanIntoTheWaste() {
        DalgonaRules.Params p = DalgonaRules.params(Difficulty.NORMAL);
        CookieSim inside = sim(DalgonaShape.CIRCLE, Difficulty.NORMAL, 5);
        CookieSim outside = sim(DalgonaShape.CIRCLE, Difficulty.NORMAL, 5);
        // circle samples run clockwise: a positive offset moves to the inside of the figure
        trace(inside, 0.5 * p.safeSpeed(), -2.5 * p.tolerance(), 120);
        trace(outside, 0.5 * p.safeSpeed(), 2.5 * p.tolerance(), 120);
        assertTrue(DalgonaShape.CIRCLE.contains(inside.needleX(), inside.needleY()) != DalgonaShape.CIRCLE.contains(outside.needleX(), outside.needleY()));
        double in = Math.max(inside.stress(), outside.stress());
        double out = Math.min(inside.stress(), outside.stress());
        assertTrue(in > out * 1.1, "one side must be worse: " + in + " vs " + out);
    }

    @Test
    void fragileSpotsHaveALowerSpeedLimit() {
        DalgonaShape star = DalgonaShape.STAR;
        int tip = 0;
        for (int i = 0; i < star.sampleCount(); i++) {
            if (star.fragility(i) > star.fragility(tip)) {
                tip = i;
            }
        }
        double speed = 0.9 * DalgonaRules.params(Difficulty.NORMAL).safeSpeed();
        // the same speed, for the same number of ticks, towards a star tip and along the robust circle
        CookieSim atTip = sim(star, Difficulty.NORMAL, 6);
        CookieSim onCircle = sim(DalgonaShape.CIRCLE, Difficulty.NORMAL, 6);
        double[] p = new double[2];
        double[] x = new double[1];
        double[] y = new double[1];
        for (int t = 0; t < 14; t++) {
            atTip.tick();
            onCircle.tick();
            positionAt(star, tip - 12 + t * speed / star.sampleStep(), 0, p);
            x[0] = p[0];
            y[0] = p[1];
            atTip.stroke(x, y, 1, 1.0, t == 0);
            positionAt(DalgonaShape.CIRCLE, 20 + t * speed / DalgonaShape.CIRCLE.sampleStep(), 0, p);
            x[0] = p[0];
            y[0] = p[1];
            onCircle.stroke(x, y, 1, 1.0, t == 0);
        }
        assertEquals(0, onCircle.stress(), 1e-3, "0.9 x the safe speed on a robust spot is calm");
        assertTrue(atTip.stress() > 0.3, "the same speed is too fast at a tip: " + atTip.stress());
    }

    @Test
    void licksRelieveStressWithCooldownAndLimit() {
        for (Difficulty d : Difficulty.values()) {
            DalgonaRules.Params p = DalgonaRules.params(d);
            CookieSim sim = sim(DalgonaShape.CIRCLE, d, 7);
            sim.penalize(70);
            assertTrue(sim.canLick());
            assertTrue(sim.lick());
            assertEquals(70 - p.lickRelief(), sim.stress(), 1e-9);
            assertEquals(p.licks() - 1, sim.licksLeft());
            assertFalse(sim.lick(), "cooldown / lock");
            for (int i = 0; i < p.lickCooldownTicks() - 1; i++) {
                sim.tick();
                assertFalse(sim.canLick(), "still cooling down at tick " + i);
            }
            sim.tick();
            assertTrue(sim.canLick(), "cooldown over");
            while (sim.licksLeft() > 0) {
                assertTrue(sim.lick());
                for (int i = 0; i < p.lickCooldownTicks(); i++) {
                    sim.tick();
                }
            }
            assertFalse(sim.lick(), "no licks left");
        }
        assertEquals(5, DalgonaRules.params(Difficulty.NORMAL).licks());
        assertEquals(3, DalgonaRules.params(Difficulty.HARD).licks());
        assertEquals(2, DalgonaRules.params(Difficulty.EXTREME).licks());
    }

    @Test
    void theNeedleCannotCarveWhileLicking() {
        CookieSim sim = sim(DalgonaShape.CIRCLE, Difficulty.NORMAL, 8);
        assertTrue(sim.lick());
        double[] x = {DalgonaShape.CIRCLE.sampleX(0)};
        double[] y = {DalgonaShape.CIRCLE.sampleY(0)};
        CookieSim.Result r = sim.stroke(x, y, 1, 1.0, true);
        assertEquals(CookieSim.Outcome.IGNORED, r.outcome);
        assertEquals(0, sim.carvedCount());
        for (int i = 0; i < sim.params().lickLockTicks(); i++) {
            sim.tick();
        }
        assertEquals(CookieSim.Outcome.CONTINUE, sim.stroke(x, y, 1, 1.0, true).outcome);
        assertTrue(sim.carvedCount() > 0);
    }

    @Test
    void stressDrainsFasterWhileTheNeedleRests() {
        CookieSim resting = sim(DalgonaShape.CIRCLE, Difficulty.NORMAL, 9);
        resting.penalize(60);
        for (int i = 0; i < 100; i++) {
            resting.tick();
        }
        CookieSim working = sim(DalgonaShape.CIRCLE, Difficulty.NORMAL, 9);
        working.penalize(60);
        double[] x = {DalgonaShape.CIRCLE.sampleX(0)};
        double[] y = {DalgonaShape.CIRCLE.sampleY(0)};
        for (int i = 0; i < 100; i++) {
            working.tick();
            working.stroke(x, y, 1, 1.0, i == 0);
        }
        assertTrue(resting.stress() < working.stress() - 4, "rest " + resting.stress() + " vs work " + working.stress());
        assertTrue(resting.stress() > 40, "but it takes a while: " + resting.stress());
    }

    @Test
    void clickingAlongTheOutlineDoesNotTeleportTheNeedle() {
        // an automated client that dabs a new spot every tick, 40 units apart: far faster than any safe carving
        CookieSim sim = sim(DalgonaShape.CIRCLE, Difficulty.NORMAL, 10);
        DalgonaShape s = DalgonaShape.CIRCLE;
        double[] x = new double[1];
        double[] y = new double[1];
        int step = (int) Math.round(40 / s.sampleStep());
        int tick = 0;
        for (; tick < s.sampleCount() / step && !sim.isResolved(); tick++) {
            sim.tick();
            x[0] = s.sampleX(tick * step);
            y[0] = s.sampleY(tick * step);
            sim.stroke(x, y, 1, 1.0, true);
        }
        assertTrue(sim.isCracked(), "dab-hopping must crack the cookie (stress " + sim.stress() + ", progress " + sim.progress() + ")");
        assertFalse(sim.isDone());
    }

    @Test
    void touchDownDabIsFreeButNotRepeatedly() {
        CookieSim sim = sim(DalgonaShape.CIRCLE, Difficulty.NORMAL, 11);
        DalgonaShape s = DalgonaShape.CIRCLE;
        double[] x = {s.sampleX(0)};
        double[] y = {s.sampleY(0)};
        CookieSim.Result r = sim.stroke(x, y, 1, 1.0, true);
        assertTrue(r.newlyCarved >= 3);
        assertEquals(0, sim.stress(), 1e-9, "putting the needle down is not speeding");
    }

    @Test
    void terminalStatesAreFinal() {
        CookieSim cracked = sim(DalgonaShape.CIRCLE, Difficulty.NORMAL, 12);
        cracked.penalize(120);
        assertTrue(cracked.isCracked());
        assertEquals(100, cracked.stress(), 1e-9);
        assertFalse(cracked.lick());
        double[] x = {DalgonaShape.CIRCLE.sampleX(5)};
        double[] y = {DalgonaShape.CIRCLE.sampleY(5)};
        assertEquals(CookieSim.Outcome.IGNORED, cracked.stroke(x, y, 1, 1.0, true).outcome);
        cracked.tick();
        assertEquals(100, cracked.stress(), 1e-9, "a cracked cookie does not heal");

        CookieSim done = sim(DalgonaShape.CIRCLE, Difficulty.NORMAL, 12);
        trace(done, 3.0, 0, 4000);
        assertTrue(done.isDone());
        done.penalize(500);
        done.crack();
        assertFalse(done.isCracked(), "a freed shape cannot be cracked any more");
        assertEquals(CookieSim.Outcome.IGNORED, done.stroke(x, y, 1, 1.0, true).outcome);
    }

    @Test
    void identicalInputGivesIdenticalOutcome() {
        CookieSim a = sim(DalgonaShape.STAR, Difficulty.HARD, 99);
        CookieSim b = sim(DalgonaShape.STAR, Difficulty.HARD, 99);
        trace(a, 5.5, 4, 1500);
        trace(b, 5.5, 4, 1500);
        assertEquals(a.stress(), b.stress(), 0.0);
        assertEquals(a.carvedCount(), b.carvedCount());
        assertEquals(a.isDone(), b.isDone());
        assertArrayEquals(a.carvedBits(), b.carvedBits());
        assertEquals(a.spikes(), b.spikes());
    }

    @Test
    void microFracturesAreRareButReal() {
        int spikes = 0;
        int runs = 60;
        DalgonaShape s = DalgonaShape.UMBRELLA;
        double[] x = {s.sampleX(10)};
        double[] y = {s.sampleY(10)};
        for (int i = 0; i < runs; i++) {
            CookieSim sim = sim(s, Difficulty.EXTREME, 1000 + i);
            sim.penalize(50);
            // the needle rests on the groove (no cutting, no speed) for 30 seconds
            for (int t = 0; t < 600 && !sim.isCracked(); t++) {
                sim.tick();
                sim.stroke(x, y, 1, 1.0, t == 0);
            }
            spikes += sim.spikes();
        }
        assertTrue(spikes > 0, "micro-fractures happen");
        assertTrue(spikes < runs * 5, "but they are rare: " + spikes + " in " + runs + " runs");
    }

    @Test
    void progressNeverExceedsTheOutline() {
        CookieSim sim = sim(DalgonaShape.TRIANGLE, Difficulty.NORMAL, 13);
        trace(sim, 4.0, 0, 5000);
        assertTrue(sim.carvedCount() <= sim.sampleCount());
        assertTrue(sim.progress() <= 1.0);
        int bits = 0;
        for (long l : sim.carvedBits()) {
            bits += Long.bitCount(l);
        }
        assertEquals(sim.carvedCount(), bits);
    }
}
