package com.squidgame.core.finale;

import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The knockout ladder that decides what happens when more than two finalists survive. */
class LadderTest {
    private record Played(int round, int a, int b, int winner) {
        int loser() {
            return winner == a ? b : a;
        }
    }

    private static List<Integer> field(int n) {
        List<Integer> l = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            l.add(100 + i);
        }
        return l;
    }

    /** Plays the ladder to its end; {@code picker} decides every duel. */
    private static List<Played> playOut(Ladder ladder, Rng picker) {
        List<Played> out = new ArrayList<>();
        Ladder.Pairing p;
        int guard = 0;
        while ((p = ladder.next()) != null) {
            assertTrue(guard++ < 200, "the ladder must end");
            int round = ladder.round();
            int winner = picker.nextBoolean() ? p.a() : p.b();
            out.add(new Played(round, p.a(), p.b(), winner));
            ladder.report(winner);
        }
        return out;
    }

    @Test
    void twoFinalistsFightOneDuelAndTheWinnerIsTheChampion() {
        Ladder l = new Ladder(field(2), new Rng(1));
        assertEquals(1, l.duelsLeft());
        assertEquals(1, l.duelsTotal());
        Ladder.Pairing p = l.next();
        assertNotNull(p);
        assertTrue(p.contains(101) && p.contains(102));
        assertEquals(102, p.other(101));
        assertFalse(l.finished());
        assertEquals(-1, l.champion());
        l.report(102);
        assertNull(l.next());
        assertTrue(l.finished());
        assertEquals(102, l.champion());
        assertEquals(1, l.duelsPlayed());
    }

    @Test
    void nFinalistsFightExactlyNMinusOneDuelsAndLeaveOneChampion() {
        for (int n = 2; n <= 24; n++) {
            for (long seed = 1; seed <= 6; seed++) {
                Ladder l = new Ladder(field(n), new Rng(seed));
                assertEquals(n - 1, l.duelsLeft());
                List<Played> duels = playOut(l, new Rng(seed * 31));
                assertEquals(n - 1, duels.size(), "n=" + n + " seed=" + seed);
                assertTrue(l.finished());
                assertTrue(l.champion() > 0);
                assertEquals(0, l.duelsLeft());
                assertEquals(n - 1, l.duelsPlayed());
                assertEquals(1, l.remaining());
            }
        }
    }

    @Test
    void aLoserNeverFightsAgainAndEveryoneFightsAtLeastOnce() {
        // (n = 2 is covered above)
        for (int n = 3; n <= 17; n++) {
            Ladder l = new Ladder(field(n), new Rng(n));
            List<Played> duels = playOut(l, new Rng(n * 7L));
            Set<Integer> out = new HashSet<>();
            Set<Integer> fought = new HashSet<>();
            for (Played d : duels) {
                assertFalse(out.contains(d.a()) || out.contains(d.b()), "eliminated finalists do not come back (n=" + n + ")");
                out.add(d.loser());
                fought.add(d.a());
                fought.add(d.b());
            }
            assertEquals(n - 1, out.size(), "everyone but the champion lost exactly once");
            assertFalse(out.contains(l.champion()));
            assertEquals(n, fought.size(), "a bye only postpones the first duel: the last duel has two fighters");
        }
    }

    @Test
    void anOddFieldGivesTheByeToSomebodyWhoHadNone() {
        for (int n = 3; n <= 21; n++) {
            for (long seed = 1; seed <= 5; seed++) {
                Ladder l = new Ladder(field(n), new Rng(seed));
                List<Played> duels = playOut(l, new Rng(seed + 100));
                Set<Integer> alive = new HashSet<>(field(n));
                Set<Integer> hadBye = new HashSet<>();
                int rounds = duels.get(duels.size() - 1).round();
                for (int r = 1; r <= rounds; r++) {
                    Set<Integer> fighting = new HashSet<>();
                    Set<Integer> winners = new HashSet<>();
                    for (Played d : duels) {
                        if (d.round() == r) {
                            fighting.add(d.a());
                            fighting.add(d.b());
                            winners.add(d.winner());
                        }
                    }
                    Set<Integer> bye = new HashSet<>(alive);
                    bye.removeAll(fighting);
                    assertTrue(bye.size() <= 1, "at most one bye per round (n=" + n + " round " + r + ")");
                    for (int b : bye) {
                        if (hadBye.contains(b)) {
                            assertTrue(hadBye.containsAll(alive), "nobody gets a second bye while someone has none (n=" + n + ")");
                        }
                        hadBye.add(b);
                    }
                    Set<Integer> next = new HashSet<>(winners);
                    next.addAll(bye);
                    alive = next;
                }
                assertEquals(1, alive.size());
            }
        }
    }

    @Test
    void theSamePairingIsReturnedUntilItIsReported() {
        Ladder l = new Ladder(field(6), new Rng(3));
        Ladder.Pairing a = l.next();
        assertSame(a, l.next());
        assertSame(a, l.current());
        l.report(a.a());
        assertNotSame(a, l.next());
    }

    @Test
    void reportingAStrangerIsRejected() {
        Ladder l = new Ladder(field(4), new Rng(3));
        l.next();
        assertThrows(IllegalStateException.class, () -> l.report(999));
        Ladder idle = new Ladder(field(4), new Rng(3));
        assertThrows(IllegalStateException.class, () -> idle.report(101));
    }

    @Test
    void withdrawingTheOpponentOfTheCurrentDuelAdvancesTheOther() {
        Ladder l = new Ladder(field(2), new Rng(5));
        Ladder.Pairing p = l.next();
        l.withdraw(p.b());
        assertNull(l.current());
        assertNull(l.next());
        assertEquals(p.a(), l.champion());
        assertEquals(0, l.duelsPlayed(), "a walkover is not a duel");
    }

    @Test
    void withdrawingAWaitingFinalistRemovesThemFromTheLadder() {
        Ladder l = new Ladder(field(6), new Rng(5));
        Ladder.Pairing p = l.next();
        int waiting = -1;
        for (int n : l.fighters()) {
            if (!p.contains(n)) {
                waiting = n;
                break;
            }
        }
        assertTrue(waiting > 0);
        l.withdraw(waiting);
        assertEquals(5, l.remaining());
        List<Played> duels = new ArrayList<>();
        Rng picker = new Rng(9);
        do {
            int winner = picker.nextBoolean() ? p.a() : p.b();
            duels.add(new Played(l.round(), p.a(), p.b(), winner));
            l.report(winner);
            p = l.next();
        } while (p != null);
        for (Played d : duels) {
            assertNotEquals(waiting, d.a());
            assertNotEquals(waiting, d.b());
        }
        assertTrue(l.finished());
        assertTrue(l.champion() > 0);
    }

    @Test
    void aLoneFinalistWinsWithoutFighting() {
        Ladder l = new Ladder(field(1), new Rng(1));
        assertNull(l.next());
        assertTrue(l.finished());
        assertEquals(101, l.champion());
        assertEquals(0, l.duelsTotal());
        assertEquals(0, l.duelsLeft());
    }

    @Test
    void theDrawIsDeterministicPerSeedAndVariesWithIt() {
        List<Integer> first = new ArrayList<>();
        for (long seed : new long[]{11, 11, 12, 13, 14}) {
            Ladder l = new Ladder(field(8), new Rng(seed));
            first.add(l.next().a() * 1000 + l.current().b());
        }
        assertEquals(first.get(0), first.get(1), "same seed, same draw");
        assertTrue(new HashSet<>(first).size() > 1, "other seeds draw other pairings");
    }

    @Test
    void duelsLeftAndRemainingCountDownConsistently() {
        Ladder l = new Ladder(field(9), new Rng(2));
        Rng picker = new Rng(4);
        int expectedLeft = 8;
        Ladder.Pairing p;
        while ((p = l.next()) != null) {
            assertEquals(expectedLeft, l.duelsLeft());
            assertEquals(expectedLeft + 1, l.remaining());
            l.report(picker.nextBoolean() ? p.a() : p.b());
            expectedLeft--;
        }
        assertEquals(0, expectedLeft);
        assertEquals(l.duelsTotal(), l.duelsPlayed());
    }

    @Test
    void duelsAreLiveWhenAHumanFightsOrFewFinalistsAreLeft() {
        assertTrue(Ladder.live(12, true));
        assertFalse(Ladder.live(12, false));
        assertFalse(Ladder.live(5, false));
        assertTrue(Ladder.live(4, false), "the semi finals and the final are always worth watching");
        assertTrue(Ladder.live(2, false));
    }

    @Test
    void theTimeBudgetGrowsWithTheFieldAndStaysFinite() {
        int duel = FinaleRules.params(com.squidgame.core.Difficulty.NORMAL).duelTicks();
        assertEquals(FinaleRules.INTRO_TICKS + duel + FinaleRules.OUTRO_TICKS, Ladder.budgetTicks(2, 0, duel),
                "two finalists: one live duel with its ceremonies");
        int previous = 0;
        for (int n = 2; n <= 60; n++) {
            int b = Ladder.budgetTicks(n, 1, duel);
            assertTrue(b >= previous);
            previous = b;
        }
        assertTrue(Ladder.budgetTicks(60, 1, duel) < 20 * 60 * 60, "even a field of 60 fits into an hour");
        assertEquals(0, Ladder.budgetTicks(1, 0, duel));
    }
}
