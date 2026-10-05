package com.squidgame.core.marbles;

import com.squidgame.core.marbles.MatchOutcome.Reason;
import com.squidgame.core.util.Rng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The target-throw match of one pair as a pure state machine: every round both partners throw once (the first
 * thrower alternates), the marble that comes to rest closer to the bullseye wins marbles from the other
 * ({@link ThrowScoring#stake}), and the match ends when a side has no marble left, when the round budget / the clock
 * runs out (more marbles wins), or in a sudden-death round (one throw each, closest wins) if that leaves a tie.
 */
public final class ThrowDuel {
    public record Throw(Side by, ThrowScoring.Result result) {
    }

    /** A resolved round. {@code winner} is null for an exact draw (nothing moves). */
    public record RoundResult(int round, Throw first, Throw second, Side winner, int stake, int moved,
                              boolean suddenDeath) {
        public Throw of(Side s) {
            return first.by() == s ? first : second;
        }
    }

    private final MarbleLedger ledger;
    private final int maxRounds;
    private final int stakeCap;
    private final List<RoundResult> history = new ArrayList<>();
    private int roundNo;
    private Side firstThrower;
    private Throw firstThrow;
    private Throw secondThrow;
    private boolean suddenDeath;
    private MatchOutcome outcome;

    public ThrowDuel(int marblesEach, Side firstThrower, int maxRounds, int stakeCap) {
        this.ledger = MarbleLedger.equal(marblesEach);
        this.maxRounds = Math.max(1, maxRounds);
        this.stakeCap = Math.max(1, stakeCap);
        startRound(1, firstThrower);
    }

    private void startRound(int no, Side first) {
        roundNo = no;
        firstThrower = first;
        firstThrow = null;
        secondThrow = null;
    }

    public MarbleLedger ledger() {
        return ledger;
    }

    public int roundNo() {
        return roundNo;
    }

    public int maxRounds() {
        return maxRounds;
    }

    public int stakeCap() {
        return stakeCap;
    }

    public boolean isSuddenDeath() {
        return suddenDeath;
    }

    public Side firstThrower() {
        return firstThrower;
    }

    public List<RoundResult> history() {
        return Collections.unmodifiableList(history);
    }

    public boolean over() {
        return outcome != null;
    }

    public MatchOutcome outcome() {
        return outcome;
    }

    /** Who throws next in this round; null when both have thrown or the match is over. */
    public Side turn() {
        if (outcome != null) {
            return null;
        }
        if (firstThrow == null) {
            return firstThrower;
        }
        return secondThrow == null ? firstThrower.other() : null;
    }

    /** Records the throw of the side whose turn it is; returns false (and changes nothing) otherwise. */
    public boolean recordThrow(Side by, ThrowScoring.Result result) {
        if (turn() != by) {
            return false;
        }
        Throw t = new Throw(by, result);
        if (firstThrow == null) {
            firstThrow = t;
        } else {
            secondThrow = t;
        }
        return true;
    }

    public boolean roundComplete() {
        return outcome == null && secondThrow != null;
    }

    /** The throw already made in the current round, or null. */
    public Throw firstThrowOfRound() {
        return firstThrow;
    }

    /** Compares the two throws and moves the marbles. Ends the match when the loser is out of marbles. */
    public RoundResult resolveRound() {
        if (!roundComplete()) {
            throw new IllegalStateException("round is not complete");
        }
        int cmp = ThrowScoring.compare(firstThrow.result(), secondThrow.result());
        Side winner = cmp == 0 ? null : (cmp < 0 ? firstThrow.by() : secondThrow.by());
        int stake = 0;
        int moved = 0;
        if (winner != null && !suddenDeath) {
            Throw w = firstThrow.by() == winner ? firstThrow : secondThrow;
            Throw l = w == firstThrow ? secondThrow : firstThrow;
            stake = ThrowScoring.stake(w.result(), l.result(), stakeCap);
            moved = ledger.transfer(winner.other(), stake);
        }
        RoundResult rr = new RoundResult(roundNo, firstThrow, secondThrow, winner, stake, moved, suddenDeath);
        history.add(rr);
        if (suddenDeath) {
            if (winner != null) {
                outcome = new MatchOutcome(winner, Reason.SUDDEN_DEATH);
            }
        } else if (winner != null && ledger.isOut(winner.other())) {
            outcome = new MatchOutcome(winner, Reason.OUT_OF_MARBLES);
        }
        return rr;
    }

    /**
     * Between rounds: decides the match by count when the budget is exhausted or {@code timeCalled}, otherwise starts
     * the next round (a drawn sudden-death round is simply repeated).
     */
    public void advance(boolean timeCalled) {
        if (outcome != null) {
            return;
        }
        if (suddenDeath) {
            startRound(roundNo + 1, firstThrower.other());
        } else if (timeCalled || roundNo >= maxRounds) {
            settleByCount();
        } else {
            startRound(roundNo + 1, firstThrower.other());
        }
    }

    /** Time is up: more marbles wins; a tie drops the unfinished round and opens the sudden-death round. */
    public void settleByCount() {
        if (outcome != null) {
            return;
        }
        Side lead = ledger.leader();
        if (lead != null) {
            outcome = new MatchOutcome(lead, Reason.MORE_MARBLES);
        } else if (!suddenDeath) {
            suddenDeath = true;
            startRound(roundNo + 1, firstThrower.other());
        }
    }

    public void forfeit(Side loser) {
        if (outcome == null) {
            outcome = new MatchOutcome(loser.other(), Reason.FORFEIT);
        }
    }

    /** Hard deadline: the leader wins, a tie (including an unfinished sudden death) is a coin flip. */
    public void forceResolve(Rng rng) {
        if (outcome != null) {
            return;
        }
        Side lead = ledger.leader();
        outcome = lead != null ? new MatchOutcome(lead, Reason.MORE_MARBLES)
                : new MatchOutcome(rng.nextBoolean() ? Side.A : Side.B, Reason.COIN_FLIP);
    }
}
