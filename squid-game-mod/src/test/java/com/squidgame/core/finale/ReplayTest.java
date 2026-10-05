package com.squidgame.core.finale;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The time warp that turns a simulated duel into a few seconds of show. */
class ReplayTest {
    private static final CourtGeometry COURT = SquidShape.court();

    private static DuelSimulator.Result duel(long seed) {
        Rng r = new Rng(seed);
        return new DuelSimulator(Difficulty.NORMAL, COURT, Personality.generate(r), Personality.generate(r),
                seed % 2 == 0 ? Role.ATTACKER : Role.DEFENDER, seed * 3).run();
    }

    @Test
    void theTrackStartsAtTheSpawnsAndEndsWhereTheDuelEnded() {
        DuelSimulator.Result r = duel(4);
        assertEquals(r.ticks() + 1, r.poses());
        int atk = r.roles()[0] == Role.ATTACKER ? 0 : 1;
        assertEquals(COURT.attackerSpawn().x(), r.x(atk, 0), 1e-3);
        assertEquals(COURT.attackerSpawn().z(), r.z(atk, 0), 1e-3);
        assertEquals(COURT.defenderSpawn().z(), r.z(1 - atk, 0), 1e-3);
        // the poses move: nobody stands still for a whole duel
        assertTrue(Math.hypot(r.x(0, r.ticks()) - r.x(0, 0), r.z(0, r.ticks()) - r.z(0, 0)) > 0.5
                || Math.hypot(r.x(1, r.ticks()) - r.x(1, 0), r.z(1, r.ticks()) - r.z(1, 0)) > 0.5);
        // out of range ticks are clamped
        assertEquals(r.x(0, r.ticks()), r.x(0, r.ticks() + 50), 1e-9);
        assertEquals(r.x(0, 0), r.x(0, -5), 1e-9);
    }

    @Test
    void theFinishOfACaptureShowsAFighterInsideTheCircle() {
        for (long seed = 1; seed < 80; seed++) {
            DuelSimulator.Result r = duel(seed);
            if (r.outcome().reason() == Duel.Reason.CAPTURE) {
                int atk = r.roles()[0] == Role.ATTACKER ? 0 : 1;
                assertTrue(COURT.inCircle(r.x(atk, r.ticks()), r.z(atk, r.ticks())), "the attacker ends in the golden ring");
                return;
            }
        }
        fail("no capture in 80 duels");
    }

    @Test
    void theShowRunsFromTheStartToTheEndOfTheDuelWithoutGoingBack() {
        for (long seed = 1; seed <= 40; seed++) {
            DuelSimulator.Result r = duel(seed);
            Replay replay = new Replay(r, 100);
            assertEquals(0.0, replay.simTime(0), 1e-9);
            assertEquals(r.ticks(), replay.simTime(replay.lengthTicks()), 1e-9, "seed " + seed);
            assertEquals(r.ticks(), replay.simTime(1_000_000), 1e-9);
            double last = -1;
            for (double t = 0; t <= replay.lengthTicks() + 3; t += 0.25) {
                double now = replay.simTime(t);
                assertTrue(now >= last - 1e-9, "time never runs backwards");
                last = now;
            }
            double slowest = Double.MAX_VALUE;
            for (double t = 0; t < replay.lengthTicks(); t += 0.5) {
                slowest = Math.min(slowest, replay.speedAt(t));
            }
            assertTrue(replay.lengthTicks() <= 200, "never more than twice the planned length: seed " + seed + " " + replay.lengthTicks());
            assertTrue(replay.lengthTicks() <= 100 || slowest >= Replay.MAX_SPEED - 1e-6,
                    "the show fits its length unless the blows would run faster than the maximum: seed " + seed + " "
                            + replay.lengthTicks() + " ticks, blows at " + slowest + "x");
        }
    }

    @Test
    void blowsPassAtAboutNaturalSpeedAndQuietStretchesFast() {
        int checked = 0;
        for (long seed = 1; seed <= 40 && checked < 12; seed++) {
            DuelSimulator.Result r = duel(seed);
            if (r.ticks() < 400) {
                continue;
            }
            checked++;
            Replay replay = new Replay(r, 100);
            double fastest = 0, slowest = Double.MAX_VALUE;
            for (double t = 0; t < replay.lengthTicks(); t += 0.5) {
                double v = replay.speedAt(t);
                fastest = Math.max(fastest, v);
                slowest = Math.min(slowest, v);
            }
            assertTrue(slowest >= 1.0 - 1e-9, "never slower than real time: " + slowest);
            assertTrue(slowest <= 2 * Replay.MAX_SPEED, "blows are shown at a speed you can follow: " + slowest);
            assertTrue(fastest >= 8.0, "quiet stretches are skipped: " + fastest);
            // every event is shown, in order
            double previousShow = -1;
            for (DuelSimulator.Timed ev : r.log()) {
                double showAt = showTickOf(replay, ev.tick());
                assertTrue(showAt >= previousShow - 1e-9);
                previousShow = showAt;
            }
        }
        assertTrue(checked >= 5);
    }

    private static double showTickOf(Replay replay, int simTick) {
        double lo = 0, hi = replay.lengthTicks() + 1;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (replay.simTime(mid) >= simTick) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return hi;
    }

    @Test
    void aShortDuelIsNotStretched() {
        for (long seed = 1; seed <= 200; seed++) {
            DuelSimulator.Result r = duel(seed);
            if (r.ticks() > 160) {
                continue;
            }
            Replay replay = new Replay(r, 400);
            assertTrue(replay.lengthTicks() <= r.ticks(), "a " + r.ticks() + " tick duel is shown in " + replay.lengthTicks());
            for (double t = 0; t < replay.lengthTicks(); t += 1) {
                assertTrue(replay.speedAt(t) >= 1.0 - 1e-9);
            }
            return;
        }
        fail("no short duel in 200 seeds");
    }

    @Test
    void aDuelWithoutEventsIsMostlySkippedButTheFinishIsShown() {
        // an empty log: only the last moments count
        DuelSimulator.Result real = duel(9);
        DuelSimulator.Result quiet = new DuelSimulator.Result(real.outcome(), real.summary(), real.roles(), new ArrayList<>(), real.track());
        Replay replay = new Replay(quiet, 100);
        assertTrue(replay.lengthTicks() < 100);
        assertEquals(real.ticks(), replay.simTime(replay.lengthTicks()), 1e-9);
        assertTrue(replay.speedAt(0) > 5, "the walk to the first meeting is skipped");
    }

    @Test
    void theWarpIsTheSameEveryTime() {
        DuelSimulator.Result r = duel(12);
        Replay a = new Replay(r, 120), b = new Replay(r, 120);
        for (double t = 0; t < 130; t += 3.3) {
            assertEquals(a.simTime(t), b.simTime(t), 0.0);
        }
        List<Double> speeds = new ArrayList<>();
        for (double t = 0; t < a.lengthTicks(); t += 5) {
            speeds.add(a.speedAt(t));
        }
        assertFalse(speeds.isEmpty());
    }
}
