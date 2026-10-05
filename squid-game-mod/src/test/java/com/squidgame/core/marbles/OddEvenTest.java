package com.squidgame.core.marbles;

import com.squidgame.core.marbles.MatchOutcome.Reason;
import com.squidgame.core.marbles.OddEvenRound.Reveal;
import com.squidgame.core.marbles.OddEvenRound.Submit;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Ledger, single round and whole-duel rules of odd-or-even. */
class OddEvenTest {
    // ------------------------------------------------------------------ ledger

    @Test
    void ledgerConservesMarblesAndCapsTransfers() {
        MarbleLedger l = MarbleLedger.equal(10);
        assertEquals(20, l.total());
        assertEquals(3, l.transfer(Side.A, 3));
        assertEquals(7, l.count(Side.A));
        assertEquals(13, l.count(Side.B));
        // cannot move more than the giver owns
        assertEquals(7, l.transfer(Side.A, 50));
        assertTrue(l.isOut(Side.A));
        assertEquals(20, l.count(Side.A) + l.count(Side.B));
        assertEquals(Side.B, l.leader());
        assertThrows(IllegalArgumentException.class, () -> l.transfer(Side.B, -1));
        assertThrows(IllegalArgumentException.class, () -> new MarbleLedger(-1, 3));
        assertNull(MarbleLedger.equal(4).leader());
    }

    // ------------------------------------------------------------------ one round

    private static OddEvenRound round(int holderMarbles, int guesserMarbles) {
        return new OddEvenRound(1, Side.A, holderMarbles, guesserMarbles, 0);
    }

    @Test
    void holdMustBeOneToOwnCountAndOnlyFromTheHolder() {
        OddEvenRound r = round(5, 8);
        assertEquals(Submit.WRONG_ROLE, r.submitHold(Side.B, 2));
        assertEquals(Submit.OUT_OF_RANGE, r.submitHold(Side.A, 0));
        assertEquals(Submit.OUT_OF_RANGE, r.submitHold(Side.A, 6));
        assertEquals(Submit.OUT_OF_RANGE, r.submitHold(Side.A, -3));
        assertFalse(r.holdLocked());
        assertEquals(Submit.OK, r.submitHold(Side.A, 5));
        assertEquals(Submit.ALREADY_LOCKED, r.submitHold(Side.A, 1));
    }

    @Test
    void wagerIsBoundedByBothStacksAndOnlyFromTheGuesser() {
        OddEvenRound r = round(5, 8);
        assertEquals(5, r.maxWager());
        assertEquals(Submit.WRONG_ROLE, r.submitGuess(Side.A, 1, Parity.ODD));
        assertEquals(Submit.OUT_OF_RANGE, r.submitGuess(Side.B, 0, Parity.ODD));
        assertEquals(Submit.OUT_OF_RANGE, r.submitGuess(Side.B, 6, Parity.ODD));
        assertEquals(Submit.OUT_OF_RANGE, r.submitGuess(Side.B, 2, null));
        assertEquals(Submit.OK, r.submitGuess(Side.B, 5, Parity.EVEN));
        assertEquals(Submit.ALREADY_LOCKED, r.submitGuess(Side.B, 1, Parity.ODD));
        // the guesser is the poorer side: bounded by their own stack
        assertEquals(3, new OddEvenRound(2, Side.B, 9, 3, 0).maxWager());
    }

    @Test
    void correctCallWinsTheWagerFromTheHolderWrongCallGivesItToHim() {
        OddEvenRound r = round(10, 10);
        r.submitHold(Side.A, 4);
        r.submitGuess(Side.B, 3, Parity.EVEN);
        Reveal win = r.resolve();
        assertTrue(win.correct());
        assertEquals(Side.B, win.winner());
        assertEquals(3, win.moved());

        OddEvenRound w = round(10, 10);
        w.submitHold(Side.A, 7);
        w.submitGuess(Side.B, 2, Parity.EVEN);
        Reveal lose = w.resolve();
        assertFalse(lose.correct());
        assertEquals(Side.A, lose.winner());
        assertEquals(Parity.ODD, lose.hiddenParity());
    }

    @Test
    void resolveNeedsBothDecisionsAndNeverLeaksEarly() {
        OddEvenRound r = round(4, 4);
        assertThrows(IllegalStateException.class, r::resolve);
        r.submitHold(Side.A, 2);
        assertFalse(r.isComplete());
        assertThrows(IllegalStateException.class, r::resolve);
    }

    @Test
    void forcedOvertimeWagerIsAlwaysOne() {
        OddEvenRound r = new OddEvenRound(9, Side.B, 6, 6, 1);
        assertEquals(1, r.maxWager());
        assertTrue(r.wagerIsForced());
        assertEquals(Submit.OK, r.submitGuess(Side.A, 5, Parity.ODD));
        r.submitHold(Side.B, 3);
        assertEquals(1, r.resolve().wager());
    }

    @Test
    void autoMovesAreLegalAndRespectLockedDecisions() {
        Rng rng = new Rng(5);
        for (int i = 0; i < 200; i++) {
            OddEvenRound r = round(1 + rng.nextInt(10), 1 + rng.nextInt(10));
            r.autoHold(rng);
            r.autoGuess(rng);
            assertTrue(r.isComplete());
            Reveal rev = r.resolve();
            assertTrue(rev.hidden() >= 1 && rev.hidden() <= r.maxHold());
            assertEquals(1, rev.wager());
        }
        OddEvenRound locked = round(9, 9);
        locked.submitHold(Side.A, 9);
        locked.autoHold(rng);
        locked.submitGuess(Side.B, 4, Parity.ODD);
        locked.autoGuess(rng);
        Reveal rev = locked.resolve();
        assertEquals(9, rev.hidden());
        assertEquals(4, rev.wager());
    }

    // ------------------------------------------------------------------ duel

    /** Plays one round with random legal moves. */
    private static Reveal playRandomRound(OddEvenDuel d, Rng rng) {
        OddEvenRound r = d.round();
        r.submitHold(r.holder(), rng.rangeInt(1, r.maxHold()));
        r.submitGuess(r.guesser(), rng.rangeInt(1, r.maxWager()), rng.nextBoolean() ? Parity.ODD : Parity.EVEN);
        return d.resolveRound();
    }

    @Test
    void rolesAlternateAndHistoryGrows() {
        OddEvenDuel d = new OddEvenDuel(10, Side.B, 12);
        Rng rng = new Rng(1);
        Side expected = Side.B;
        for (int i = 1; i <= 4 && !d.over(); i++) {
            assertEquals(expected, d.round().holder());
            assertEquals(i, d.roundNo());
            playRandomRound(d, rng);
            assertEquals(i, d.history().size());
            expected = expected.other();
            d.advance(false);
        }
    }

    @Test
    void randomDuelsAlwaysTerminateAndConserveMarbles() {
        for (int seed = 0; seed < 300; seed++) {
            Rng rng = new Rng(seed);
            int each = 3 + rng.nextInt(8);
            OddEvenDuel d = new OddEvenDuel(each, rng.nextBoolean() ? Side.A : Side.B, 4 + rng.nextInt(10));
            int guard = 0;
            while (!d.over()) {
                assertTrue(++guard < 60, "duel must end (seed " + seed + ")");
                playRandomRound(d, rng);
                assertEquals(2 * each, d.ledger().total());
                d.advance(false);
            }
            MatchOutcome o = d.outcome();
            assertNotNull(o);
            if (o.reason() == Reason.OUT_OF_MARBLES) {
                assertTrue(d.ledger().isOut(o.loser()));
            } else {
                // decided by count (or by the overtime round): the winner never owns fewer marbles
                assertTrue(d.ledger().count(o.winner()) > d.ledger().count(o.loser()));
            }
        }
    }

    @Test
    void duelsAreDeterministicForASeed() {
        String a = transcript(77), b = transcript(77), c = transcript(78);
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    private static String transcript(long seed) {
        Rng rng = new Rng(seed);
        OddEvenDuel d = new OddEvenDuel(10, Side.A, 12);
        StringBuilder sb = new StringBuilder();
        while (!d.over()) {
            Reveal r = playRandomRound(d, rng);
            sb.append(r.hidden()).append(r.guess().id()).append(r.wager()).append(r.winner()).append(';');
            d.advance(false);
        }
        return sb + d.outcome().toString();
    }

    @Test
    void lastMarbleEndsTheMatchImmediately() {
        OddEvenDuel d = new OddEvenDuel(1, Side.A, 12);
        // one marble each: the holder must hide it (odd), the guesser can bet only one
        assertEquals(1, d.round().maxHold());
        assertEquals(1, d.round().maxWager());
        d.round().submitHold(Side.A, 1);
        d.round().submitGuess(Side.B, 1, Parity.ODD);
        Reveal r = d.resolveRound();
        assertEquals(Side.B, r.winner());
        assertTrue(d.over());
        assertEquals(Reason.OUT_OF_MARBLES, d.outcome().reason());
        assertEquals(Side.B, d.outcome().winner());
    }

    @Test
    void roundBudgetOrTimeCallDecidesByCount() {
        OddEvenDuel d = new OddEvenDuel(5, Side.A, 3);
        // A holds 2 (even), B wrongly calls odd for 2: A wins 2 marbles
        d.round().submitHold(Side.A, 2);
        d.round().submitGuess(Side.B, 2, Parity.ODD);
        d.resolveRound();
        assertFalse(d.over());
        d.advance(true); // time called between rounds
        assertTrue(d.over());
        assertEquals(Reason.MORE_MARBLES, d.outcome().reason());
        assertEquals(Side.A, d.outcome().winner());
    }

    @Test
    void tieAtTimeCallOpensAOneMarbleOvertimeRound() {
        OddEvenDuel d = new OddEvenDuel(6, Side.A, 12);
        d.settleByCount();
        assertFalse(d.over());
        assertTrue(d.isOvertime());
        assertEquals(1, d.round().maxWager());
        assertTrue(d.round().wagerIsForced());
        d.round().submitHold(d.round().holder(), 1);
        d.round().submitGuess(d.round().guesser(), 4, Parity.EVEN); // the wager is ignored: it is forced to 1
        Reveal r = d.resolveRound();
        assertEquals(1, r.wager());
        assertTrue(d.over());
        assertEquals(Reason.SUDDEN_DEATH, d.outcome().reason());
        assertEquals(r.winner(), d.outcome().winner());
        assertEquals(12, d.ledger().total());
    }

    @Test
    void exhaustedBudgetDecidesByCountAndATieGoesToOvertime() {
        OddEvenDuel decided = new OddEvenDuel(4, Side.A, 1);
        decided.round().submitHold(Side.A, 2);
        decided.round().submitGuess(Side.B, 1, Parity.EVEN); // B is right and wins a marble
        decided.resolveRound();
        decided.advance(false);
        assertTrue(decided.over());
        assertEquals(Side.B, decided.outcome().winner());

        OddEvenDuel tie = new OddEvenDuel(4, Side.A, 2);
        tie.round().submitHold(Side.A, 2);
        tie.round().submitGuess(Side.B, 1, Parity.EVEN); // B wins one: 3:5
        tie.resolveRound();
        tie.advance(false);
        assertFalse(tie.over());
        assertEquals(Side.B, tie.round().holder());
        tie.round().submitHold(Side.B, 2);
        tie.round().submitGuess(Side.A, 1, Parity.EVEN); // A wins it back: 4:4
        tie.resolveRound();
        assertFalse(tie.over());
        tie.advance(false);
        assertFalse(tie.over());
        assertTrue(tie.isOvertime());
    }

    @Test
    void forfeitAndForceResolve() {
        OddEvenDuel d = new OddEvenDuel(5, Side.A, 12);
        d.forfeit(Side.A);
        assertEquals(Side.B, d.outcome().winner());
        assertEquals(Reason.FORFEIT, d.outcome().reason());
        d.forfeit(Side.B); // the first outcome stands
        assertEquals(Side.B, d.outcome().winner());

        OddEvenDuel tie = new OddEvenDuel(5, Side.A, 12);
        tie.forceResolve(new Rng(3));
        assertEquals(Reason.COIN_FLIP, tie.outcome().reason());

        OddEvenDuel ahead = new OddEvenDuel(5, Side.A, 12);
        ahead.round().submitHold(Side.A, 1);
        ahead.round().submitGuess(Side.B, 3, Parity.EVEN); // wrong: A wins 3
        ahead.resolveRound();
        ahead.forceResolve(new Rng(3));
        assertEquals(Side.A, ahead.outcome().winner());
        assertEquals(Reason.MORE_MARBLES, ahead.outcome().reason());
    }

    @Test
    void resolvedMatchRejectsFurtherRounds() {
        OddEvenDuel d = new OddEvenDuel(2, Side.A, 12);
        d.forfeit(Side.B);
        assertThrows(IllegalStateException.class, d::resolveRound);
        d.advance(false); // no-op
        d.settleByCount(); // no-op
        assertEquals(Side.A, d.outcome().winner());
    }
}
