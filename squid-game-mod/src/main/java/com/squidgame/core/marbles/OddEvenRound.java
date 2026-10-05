package com.squidgame.core.marbles;

import com.squidgame.core.util.Rng;

/**
 * One round of odd-or-even, with the rules of the game spelled out in the checks:
 * <ul>
 *   <li>the <b>holder</b> secretly puts 1..own marbles in a fist (only the parity of that number matters, but the
 *       number itself is revealed afterwards and becomes public history);</li>
 *   <li>the <b>guesser</b> wagers 1..min(own, holder's) marbles and calls odd or even - nobody can bet marbles the
 *       other cannot pay;</li>
 *   <li>both decide at the same time and neither choice is visible to the other side until {@link #resolve()};</li>
 *   <li>a correct call wins the wager from the holder, a wrong call gives it to the holder.</li>
 * </ul>
 * Humans and NPCs submit through the same two methods, so the validation here is the single source of truth.
 */
public final class OddEvenRound {
    public enum Submit {
        OK, WRONG_ROLE, OUT_OF_RANGE, ALREADY_LOCKED;

        public boolean accepted() {
            return this == OK;
        }
    }

    /** The public record of a resolved round: shown to both partners and kept as history for pattern reading. */
    public record Reveal(int round, Side holder, int hidden, Parity guess, int wager, boolean correct, Side winner,
                         int moved) {
        public Parity hiddenParity() {
            return Parity.of(hidden);
        }

        public Side guesser() {
            return holder.other();
        }
    }

    private final int roundNo;
    private final Side holder;
    private final int holderMarbles;
    private final int guesserMarbles;
    private final int forcedWager;
    private int hidden;
    private int wager;
    private Parity guess;

    /**
     * @param forcedWager 0 for a normal round; &gt; 0 fixes the wager (the overtime round is played for one marble)
     */
    public OddEvenRound(int roundNo, Side holder, int holderMarbles, int guesserMarbles, int forcedWager) {
        if (holderMarbles < 1 || guesserMarbles < 1) {
            throw new IllegalArgumentException("both partners need a marble to play a round");
        }
        this.roundNo = roundNo;
        this.holder = holder;
        this.holderMarbles = holderMarbles;
        this.guesserMarbles = guesserMarbles;
        this.forcedWager = forcedWager;
    }

    public int roundNo() {
        return roundNo;
    }

    public Side holder() {
        return holder;
    }

    public Side guesser() {
        return holder.other();
    }

    public int maxHold() {
        return holderMarbles;
    }

    /** Largest legal wager: bounded by what both partners own (or the fixed overtime stake). */
    public int maxWager() {
        return forcedWager > 0 ? forcedWager : Math.min(guesserMarbles, holderMarbles);
    }

    public boolean wagerIsForced() {
        return forcedWager > 0;
    }

    public boolean holdLocked() {
        return hidden > 0;
    }

    public boolean guessLocked() {
        return guess != null;
    }

    public boolean isComplete() {
        return holdLocked() && guessLocked();
    }

    public Submit submitHold(Side who, int count) {
        if (who != holder) {
            return Submit.WRONG_ROLE;
        }
        if (holdLocked()) {
            return Submit.ALREADY_LOCKED;
        }
        if (count < 1 || count > holderMarbles) {
            return Submit.OUT_OF_RANGE;
        }
        hidden = count;
        return Submit.OK;
    }

    public Submit submitGuess(Side who, int requestedWager, Parity call) {
        if (who != holder.other()) {
            return Submit.WRONG_ROLE;
        }
        if (guessLocked()) {
            return Submit.ALREADY_LOCKED;
        }
        int w = forcedWager > 0 ? forcedWager : requestedWager;
        if (call == null || w < 1 || w > maxWager()) {
            return Submit.OUT_OF_RANGE;
        }
        wager = w;
        guess = call;
        return Submit.OK;
    }

    /** What {@code who} has locked in as the holder (0 = nothing yet or not the holder): nobody can read the other side's choice. */
    public int ownHold(Side who) {
        return who == holder ? hidden : 0;
    }

    /** The call {@code who} has locked in as the guesser (null = nothing yet or not the guesser). */
    public Parity ownGuess(Side who) {
        return who == holder.other() ? guess : null;
    }

    /** The wager {@code who} has locked in as the guesser (0 = nothing yet or not the guesser). */
    public int ownWager(Side who) {
        return who == holder.other() && guess != null ? wager : 0;
    }

    /** Timer expired: a random legal number of marbles in the fist. No-op if the holder already locked in. */
    public void autoHold(Rng rng) {
        if (!holdLocked()) {
            hidden = rng.rangeInt(1, holderMarbles);
        }
    }

    /** Timer expired: a random call for the smallest wager (a player who does not act never gambles big by accident). */
    public void autoGuess(Rng rng) {
        if (!guessLocked()) {
            wager = forcedWager > 0 ? forcedWager : 1;
            guess = rng.nextBoolean() ? Parity.ODD : Parity.EVEN;
        }
    }

    /** The decided outcome. Requires both decisions; does not touch any ledger. */
    public Reveal resolve() {
        if (!isComplete()) {
            throw new IllegalStateException("round is not complete");
        }
        boolean correct = Parity.of(hidden) == guess;
        Side winner = correct ? guesser() : holder;
        return new Reveal(roundNo, holder, hidden, guess, wager, correct, winner, wager);
    }
}
