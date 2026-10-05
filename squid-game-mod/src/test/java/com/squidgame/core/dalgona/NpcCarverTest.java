package com.squidgame.core.dalgona;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Monte-Carlo checks of the NPC hands: the same cookie physics the players are judged by, with fixed seeds. */
class NpcCarverTest {
    private record Outcome(boolean freed, int ticks, int licks, double firstLickStress) {
    }

    private static NpcCarver.Profile base(Personality p, Difficulty d, long seed) {
        return NpcCarver.Profile.of(p, d, new Rng(seed).fork(2));
    }

    private static Outcome play(DalgonaShape shape, Difficulty d, NpcCarver.Profile prof, long seed, int interval, int limitTicks) {
        Rng r = new Rng(seed);
        DalgonaRules.Params prm = DalgonaRules.params(d);
        CookieSim sim = new CookieSim(shape, prm, r.fork(1).seed());
        NpcCarver carver = new NpcCarver(shape, sim, prof, r.fork(3));
        int t = 0;
        double firstLick = -1;
        for (; t < limitTicks; t++) {
            sim.tick();
            if (t % interval == 0) {
                double before = sim.stress();
                NpcCarver.Step s = carver.step(interval, limitTicks - t);
                if (s.lickStarted() && firstLick < 0) {
                    firstLick = before;
                }
            }
            if (sim.isResolved()) {
                break;
            }
        }
        return new Outcome(sim.isDone(), t, prm.licks() - sim.licksLeft(), firstLick);
    }

    private static Outcome play(DalgonaShape shape, Difficulty d, Personality p, long seed, int interval) {
        return play(shape, d, base(p, d, seed), seed, interval, DalgonaRules.params(d).carveSeconds() * 20);
    }

    private static double successRate(DalgonaShape shape, Difficulty d, int runs, int interval) {
        Rng pr = new Rng(12345 + d.ordinal());
        int ok = 0;
        for (int i = 0; i < runs; i++) {
            if (play(shape, d, Personality.generate(pr), 1000L * i + shape.ordinal(), interval).freed()) {
                ok++;
            }
        }
        return ok / (double) runs;
    }

    @Test
    void passRatesFollowTheDifficultyTargets() {
        double[] normal = rates(Difficulty.NORMAL, 120);
        double[] hard = rates(Difficulty.HARD, 120);
        double[] extreme = rates(Difficulty.EXTREME, 120);
        double n = mean(normal), h = mean(hard), x = mean(extreme);
        assertTrue(n > 0.72 && n < 0.92, "Normal NPC success " + n);
        assertTrue(h > 0.52 && h < 0.76, "Hard NPC success " + h);
        assertTrue(x > 0.32 && x < 0.58, "Extreme NPC success " + x);
        assertTrue(n > h + 0.08 && h > x + 0.08, "clear steps between the difficulties");
        for (double[] cells : new double[][]{normal, hard, extreme}) {
            for (double c : cells) {
                assertTrue(c > 0.03 && c < 1.0, "no shape is impossible or guaranteed: " + c);
            }
        }
    }

    @Test
    void theUmbrellaIsClearlyTheHardestAndTheCircleTheEasiest() {
        for (Difficulty d : Difficulty.values()) {
            double[] r = rates(d, 120);
            double circle = r[DalgonaShape.CIRCLE.ordinal()];
            double tri = r[DalgonaShape.TRIANGLE.ordinal()];
            double star = r[DalgonaShape.STAR.ordinal()];
            double umb = r[DalgonaShape.UMBRELLA.ordinal()];
            assertTrue(umb < circle - 0.2, d + ": umbrella " + umb + " vs circle " + circle);
            assertTrue(umb < star - 0.03, d + ": umbrella " + umb + " vs star " + star);
            assertTrue(umb < tri - 0.1, d + ": umbrella " + umb + " vs triangle " + tri);
            assertTrue(circle >= tri - 0.03 && tri > star, d + ": circle " + circle + " triangle " + tri + " star " + star);
        }
    }

    private static double[] rates(Difficulty d, int runs) {
        double[] out = new double[4];
        for (DalgonaShape s : DalgonaShape.values()) {
            out[s.ordinal()] = successRate(s, d, runs, 1);
        }
        return out;
    }

    private static double mean(double[] v) {
        double s = 0;
        for (double x : v) {
            s += x;
        }
        return s / v.length;
    }

    @Test
    void fasterNpcsFailMore() {
        Personality p = new Personality(Personality.Archetype.ROOKIE, 0.5f, 0.5f, 0.5f, 0.5f, 0.3f, 0.5f, 0.5f);
        for (Difficulty d : new Difficulty[]{Difficulty.NORMAL, Difficulty.HARD, Difficulty.EXTREME}) {
            int slow = 0, fast = 0;
            int runs = 150;
            for (int i = 0; i < runs; i++) {
                NpcCarver.Profile b = base(p, d, i);
                NpcCarver.Profile slowProfile = withPace(b, 0.6);
                NpcCarver.Profile fastProfile = withPace(b, 1.6);
                int limit = DalgonaRules.params(d).carveSeconds() * 20;
                if (play(DalgonaShape.STAR, d, slowProfile, 500 + i, 1, limit).freed()) {
                    slow++;
                }
                if (play(DalgonaShape.STAR, d, fastProfile, 500 + i, 1, limit).freed()) {
                    fast++;
                }
            }
            double margin = d == Difficulty.NORMAL ? 0.05 : 0.12;
            assertTrue(slow > fast + runs * margin, d + ": careful " + slow + "/" + runs + " vs hasty " + fast + "/" + runs);
        }
    }

    private static NpcCarver.Profile withPace(NpcCarver.Profile b, double pace) {
        return new NpcCarver.Profile(pace, b.wobbleSigma, b.slipRate, b.wobbleTau, b.trackTau, b.awareness, b.lookAhead,
                b.lickAt, b.restAt, b.restUntil, b.rush, b.reaction, b.cautiousTicks);
    }

    @Test
    void thePhysicsDoNotDependOnHowOftenTheNpcIsStepped() {
        for (Difficulty d : new Difficulty[]{Difficulty.NORMAL, Difficulty.EXTREME}) {
            double every = successRate(DalgonaShape.TRIANGLE, d, 120, 1);
            double third = successRate(DalgonaShape.TRIANGLE, d, 120, 3);
            assertEquals(every, third, 0.12, d + ": far-away NPCs (stepped every 3rd tick) play as well as near ones");
        }
    }

    @Test
    void personalityShapesTheObservableDecisions() {
        Personality nervous = new Personality(Personality.Archetype.NERVOUS, 0.2f, 0.5f, 0.6f, 0.4f, 0.1f, 0.6f, 0.15f);
        Personality reckless = new Personality(Personality.Archetype.RECKLESS, 0.9f, 0.5f, 0.1f, 0.4f, 0.7f, 0.3f, 0.9f);
        Personality veteran = new Personality(Personality.Archetype.VETERAN, 0.7f, 0.8f, 0.7f, 0.9f, 0.4f, 0.5f, 0.45f);
        NpcCarver.Profile n = base(nervous, Difficulty.NORMAL, 1);
        NpcCarver.Profile r = base(reckless, Difficulty.NORMAL, 1);
        NpcCarver.Profile v = base(veteran, Difficulty.NORMAL, 1);
        assertTrue(r.pace > n.pace + 0.4, "reckless hands move faster: " + r.pace + " vs " + n.pace);
        assertTrue(n.lickAt < r.lickAt - 15, "nervous contestants lick early, reckless ones late");
        assertTrue(n.restAt < r.restAt);
        assertTrue(v.wobbleSigma < n.wobbleSigma && v.slipRate < n.slipRate, "skill steadies the hand");
        assertTrue(v.awareness > n.awareness && v.lookAhead > n.lookAhead, "and looks ahead");
        assertTrue(r.rush > n.rush - 1e-9 || r.rush > 1.2);
        NpcCarver.Profile veteranExtreme = base(veteran, Difficulty.EXTREME, 1);
        assertTrue(veteranExtreme.wobbleSigma < v.wobbleSigma + 1e-9, "stronger opponents on harder difficulties");
    }

    @Test
    void stressedNpcsLickAndCautiousOnesLickSooner() {
        Personality cautious = new Personality(Personality.Archetype.CALCULATING, 0.3f, 0.5f, 0.9f, 0.5f, 0.2f, 0.5f, 0.2f);
        Personality bold = new Personality(Personality.Archetype.BRAWLER, 0.9f, 0.5f, 0.2f, 0.5f, 0.8f, 0.3f, 0.9f);
        double cautiousAt = 0, boldAt = 0;
        int cn = 0, bn = 0;
        for (int i = 0; i < 200; i++) {
            Outcome c = play(DalgonaShape.STAR, Difficulty.NORMAL, cautious, i, 1);
            Outcome b = play(DalgonaShape.STAR, Difficulty.NORMAL, bold, i, 1);
            if (c.firstLickStress() >= 0) {
                cautiousAt += c.firstLickStress();
                cn++;
            }
            if (b.firstLickStress() >= 0) {
                boldAt += b.firstLickStress();
                bn++;
            }
        }
        assertTrue(cn > 20 && bn > 20, "both lick sometimes (" + cn + ", " + bn + ")");
        assertTrue(cautiousAt / cn < boldAt / bn - 8, "cautious " + cautiousAt / cn + " vs bold " + boldAt / bn);
    }

    @Test
    void outcomesAreDeterministicPerSeed() {
        Personality p = Personality.generate(new Rng(9));
        for (DalgonaShape s : DalgonaShape.values()) {
            Outcome a = play(s, Difficulty.HARD, p, 77, 1);
            Outcome b = play(s, Difficulty.HARD, p, 77, 1);
            assertEquals(a, b);
        }
    }

    @Test
    void whenTheClockRunsOutTheyRushAndFinishSooner() {
        Personality patient = new Personality(Personality.Archetype.CALCULATING, 0.5f, 0.5f, 0.95f, 0.8f, 0.2f, 0.5f, 0.3f);
        int faster = 0;
        int comparable = 0;
        for (int i = 0; i < 60; i++) {
            NpcCarver.Profile prof = base(patient, Difficulty.NORMAL, i);
            Outcome relaxed = play(DalgonaShape.CIRCLE, Difficulty.NORMAL, prof, 300 + i, 1, 3000);
            Outcome pressed = play(DalgonaShape.CIRCLE, Difficulty.NORMAL, prof, 300 + i, 1, 500);
            if (relaxed.freed() && pressed.freed()) {
                comparable++;
                if (pressed.ticks() < relaxed.ticks()) {
                    faster++;
                }
            }
        }
        assertTrue(comparable > 15, "enough runs finished both ways: " + comparable);
        assertTrue(faster > comparable * 0.8, "pressed NPCs speed up: " + faster + "/" + comparable);
    }

    @Test
    void clumsyHandsStillFinishGivenEnoughTime() {
        // missed stretches are touched up afterwards, so a shaky hand frees the circle most of the time
        Personality clumsy = new Personality(Personality.Archetype.ROOKIE, 0.5f, 0.5f, 0.6f, 0.05f, 0.2f, 0.5f, 0.3f);
        int freed = 0;
        for (int i = 0; i < 80; i++) {
            NpcCarver.Profile prof = base(clumsy, Difficulty.NORMAL, i);
            if (play(DalgonaShape.CIRCLE, Difficulty.NORMAL, prof, 40 + i, 1, 4000).freed()) {
                freed++;
            }
        }
        assertTrue(freed > 40, "freed " + freed + " of 80");
    }
}
