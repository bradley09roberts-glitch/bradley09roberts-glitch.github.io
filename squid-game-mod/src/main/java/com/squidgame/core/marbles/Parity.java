package com.squidgame.core.marbles;

import java.util.Locale;

/** Odd or even: what the guesser calls and what the number of hidden marbles turns out to be. */
public enum Parity {
    ODD, EVEN;

    public static Parity of(int count) {
        return (count & 1) == 1 ? ODD : EVEN;
    }

    public Parity other() {
        return this == ODD ? EVEN : ODD;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
