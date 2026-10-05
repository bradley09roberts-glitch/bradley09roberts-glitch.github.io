package com.squidgame.core.finale;

import com.squidgame.core.util.Rng;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The rule for more than two finalists: a <b>knockout ladder of duels</b> on the same court. The finalists are drawn
 * at random; every round pairs them up, an odd one out advances without a fight (and never gets a second bye while
 * somebody else has none), each duel's loser is eliminated and the winners go on to the next round. When one
 * contestant is left he or she has won; the last duel is the final. With N finalists exactly N - 1 duels are fought.
 * Pure bookkeeping: the live game and the tests drive it with {@link #next} and {@link #report}.
 */
public final class Ladder {
    /** The numbers of the two contestants of one duel. A bye never shows up as a pairing: the ladder settles it itself. */
    public record Pairing(int a, int b) {
        public boolean contains(int number) {
            return a == number || b == number;
        }

        public int other(int number) {
            return a == number ? b : a;
        }
    }

    private final Rng rng;
    private final List<Integer> pending = new ArrayList<>();
    private final List<Integer> advanced = new ArrayList<>();
    private final Set<Integer> hadBye = new HashSet<>();
    private final int total;
    private int round = 1;
    private int duelsPlayed;
    private Pairing current;

    /** @param fighters the contestant numbers; the order only seeds the draw, which is shuffled with {@code rng} */
    public Ladder(Collection<Integer> fighters, Rng rng) {
        this.rng = rng;
        this.total = fighters.size();
        pending.addAll(fighters);
        startRound();
    }

    /** Draws the pairings of a round: shuffles, and gives the bye of an odd field to somebody who had none. */
    private void startRound() {
        rng.shuffle(pending);
        if (pending.size() % 2 == 1) {
            Integer bye = null;
            for (Integer n : pending) {
                if (!hadBye.contains(n)) {
                    bye = n;
                    break;
                }
            }
            if (bye == null) {
                bye = pending.get(pending.size() - 1);
            }
            pending.remove(bye);
            advanced.add(bye);
            hadBye.add(bye);
        }
    }

    /**
     * The next duel to fight, or null when the ladder is over. The same pairing is returned until {@link #report}
     * or {@link #withdraw} settles it.
     */
    public Pairing next() {
        while (current == null) {
            if (pending.size() >= 2) {
                int a = pending.remove(0);
                int b = pending.remove(0);
                current = new Pairing(a, b);
            } else if (pending.size() == 1) {
                advanced.add(pending.remove(0));      // the other half of the draw withdrew: a walkover
            } else if (advanced.size() > 1) {
                pending.addAll(advanced);
                advanced.clear();
                round++;
                startRound();
            } else {
                return null;
            }
        }
        return current;
    }

    /** The current duel is over: {@code winner} advances (the loser is out). */
    public void report(int winner) {
        if (current == null || !current.contains(winner)) {
            throw new IllegalStateException("no duel with " + winner + " is in progress");
        }
        advanced.add(winner);
        duelsPlayed++;
        current = null;
    }

    /** A contestant left the tournament (disconnect, administrator): the opponent advances without a fight. */
    public void withdraw(int number) {
        if (current != null && current.contains(number)) {
            advanced.add(current.other(number));
            current = null;
            return;
        }
        pending.remove(Integer.valueOf(number));
        advanced.remove(Integer.valueOf(number));
    }

    public boolean finished() {
        return current == null && pending.isEmpty() && advanced.size() <= 1;
    }

    /** The winner of the ladder, or -1 while it is still going. */
    public int champion() {
        return finished() && advanced.size() == 1 ? advanced.get(0) : -1;
    }

    public Pairing current() {
        return current;
    }

    /** Contestants still in (fighting, waiting for their turn or advanced). */
    public int remaining() {
        return pending.size() + advanced.size() + (current == null ? 0 : 2);
    }

    /** Duels still to be fought (the one in progress included). */
    public int duelsLeft() {
        return Math.max(0, remaining() - 1);
    }

    public int duelsPlayed() {
        return duelsPlayed;
    }

    /** Total number of duels the ladder started with: always one fewer than the finalists. */
    public int duelsTotal() {
        return Math.max(0, total - 1);
    }

    /** 1-based round number. */
    public int round() {
        return round;
    }

    /** Everybody still in, in a stable order (saved so that a restart keeps the draw). */
    public List<Integer> fighters() {
        List<Integer> all = new ArrayList<>();
        if (current != null) {
            all.add(current.a());
            all.add(current.b());
        }
        all.addAll(pending);
        all.addAll(advanced);
        return all;
    }

    // ================================================================== which duels are shown

    /**
     * Whether a duel is fought live on the court (otherwise the headless simulator settles it behind a short
     * ceremony): always when a human takes part, and for the last rounds - at most four finalists left - so that the
     * semi-finals and the final are always worth watching.
     */
    public static boolean live(int remainingBeforeDuel, boolean humanInvolved) {
        return humanInvolved || remainingBeforeDuel <= 4;
    }

    /**
     * Upper bound for the time budget of the whole ladder: every duel that is probably live gets intro, the full
     * time limit and outro, the others a short ceremony. Used for the game's overall time limit; if more duels turn
     * out to be live than expected the remainder is settled by the simulator when the limit runs out.
     */
    public static int budgetTicks(int finalists, int humans, int duelTicks) {
        int duels = Math.max(0, finalists - 1);
        int live = Math.min(duels, 3 + 2 * humans);
        return live * (FinaleRules.INTRO_TICKS + duelTicks + FinaleRules.OUTRO_TICKS)
                + (duels - live) * FinaleRules.CEREMONY_TICKS;
    }
}
