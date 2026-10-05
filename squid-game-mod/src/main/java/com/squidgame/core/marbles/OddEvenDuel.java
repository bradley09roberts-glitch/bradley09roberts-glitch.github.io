package com.squidgame.core.marbles;

import com.squidgame.core.marbles.MatchOutcome.Reason;
import com.squidgame.core.marbles.OddEvenRound.Reveal;
import com.squidgame.core.util.Rng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The odd-or-even match of one pair as a pure state machine (no timers, no Minecraft): rounds alternate the roles,
 * marbles move through a {@link MarbleLedger}, and the match ends when a side is out of marbles, when the round
 * budget / the clock runs out (more marbles wins), or in a one-marble overtime round if that leaves a tie.
 *
 * <p>Typical driver loop: decisions are submitted to {@link #round()}, then {@link #resolveRound()} applies the
 * outcome, then {@link #advance(boolean)} starts the next round or decides the match.
 */
public final class OddEvenDuel {
    private final MarbleLedger ledger;
    private final int maxRounds;
    private final List<Reveal> history = new ArrayList<>();
    private Side holder;
    private int roundNo;
    private boolean overtime;
    private OddEvenRound round;
    private MatchOutcome outcome;

    public OddEvenDuel(int marblesEach, Side firstHolder, int maxRounds) {
        this.ledger = MarbleLedger.equal(marblesEach);
        this.maxRounds = Math.max(1, maxRounds);
        startRound(1, firstHolder, 0);
    }

    private void startRound(int no, Side holderSide, int forcedWager) {
        this.roundNo = no;
        this.holder = holderSide;
        this.round = new OddEvenRound(no, holderSide, ledger.count(holderSide), ledger.count(holderSide.other()), forcedWager);
    }

    public MarbleLedger ledger() {
        return ledger;
    }

    public int maxRounds() {
        return maxRounds;
    }

    public int roundNo() {
        return roundNo;
    }

    public boolean isOvertime() {
        return overtime;
    }

    /** The round being played (decisions are submitted here). Meaningless once the match is over. */
    public OddEvenRound round() {
        return round;
    }

    /** Public history of every resolved round, oldest first. */
    public List<Reveal> history() {
        return Collections.unmodifiableList(history);
    }

    public boolean over() {
        return outcome != null;
    }

    public MatchOutcome outcome() {
        return outcome;
    }

    /**
     * Applies the current round (both decisions must be locked): marbles change hands, the reveal is appended to the
     * history. Ends the match if the loser of the round is out of marbles, or if this was the overtime round.
     */
    public Reveal resolveRound() {
        if (outcome != null) {
            throw new IllegalStateException("match is over");
        }
        Reveal r = round.resolve();
        ledger.transfer(r.winner().other(), r.moved());
        history.add(r);
        if (ledger.isOut(r.winner().other())) {
            outcome = new MatchOutcome(r.winner(), Reason.OUT_OF_MARBLES);
        } else if (overtime) {
            Side lead = ledger.leader();
            // an overtime round moves one marble, so the counts can no longer be equal
            outcome = new MatchOutcome(lead != null ? lead : r.winner(), Reason.SUDDEN_DEATH);
        }
        return r;
    }

    /**
     * Between rounds: decides the match by count when the budget is exhausted or {@code timeCalled}, otherwise starts
     * the next round with the roles swapped. A tie opens the overtime round (check {@link #isOvertime()}).
     */
    public void advance(boolean timeCalled) {
        if (outcome != null) {
            return;
        }
        if (timeCalled || roundNo >= maxRounds) {
            settleByCount();
        } else {
            startRound(roundNo + 1, holder.other(), 0);
        }
    }

    /**
     * Time is up: the side with more marbles wins; on a tie the (unresolved) round is dropped and a one-marble
     * overtime round starts. Does nothing once the match is over.
     */
    public void settleByCount() {
        if (outcome != null) {
            return;
        }
        Side lead = ledger.leader();
        if (lead != null) {
            outcome = new MatchOutcome(lead, Reason.MORE_MARBLES);
        } else if (!overtime) {
            overtime = true;
            startRound(roundNo + 1, holder.other(), 1);
        }
    }

    /** The partner of {@code loser} wins because {@code loser} left the tournament. */
    public void forfeit(Side loser) {
        if (outcome == null) {
            outcome = new MatchOutcome(loser.other(), Reason.FORFEIT);
        }
    }

    /** Last resort when nothing could decide the match (hard deadline): the leader wins, a tie is a coin flip. */
    public void forceResolve(Rng rng) {
        if (outcome != null) {
            return;
        }
        Side lead = ledger.leader();
        outcome = lead != null ? new MatchOutcome(lead, Reason.MORE_MARBLES)
                : new MatchOutcome(rng.nextBoolean() ? Side.A : Side.B, Reason.COIN_FLIP);
    }
}
