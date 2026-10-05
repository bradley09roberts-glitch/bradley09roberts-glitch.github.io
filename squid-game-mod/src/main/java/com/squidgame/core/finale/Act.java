package com.squidgame.core.finale;

/**
 * What a fighter is busy with. Everything except {@link #NONE} blocks other actions until it ends (a dodge may cancel
 * a wind-up). The act is public: opponents see it, and the NPC brain reacts to it with its reaction delay.
 */
public enum Act {
    /** Free: may move, guard and start any action. */
    NONE,
    /** Holding the attack button: a heavy strike is being charged (visible once held for a few ticks). */
    CHARGE,
    /** A light strike: wind-up, impact, recovery. */
    LIGHT,
    /** A released heavy strike: short wind-up, impact, long recovery. */
    HEAVY,
    /** A shove: wind-up, impact, recovery. */
    SHOVE,
    /** A dodge: dash with a short invulnerability window, then recovery. */
    DODGE,
    /** Staggered by a hit: cannot act. */
    STUN,
    /** Guard broken or strike parried: a longer stagger. */
    BROKEN;

    public boolean isAttack() {
        return this == LIGHT || this == HEAVY || this == SHOVE;
    }

    public boolean isStagger() {
        return this == STUN || this == BROKEN;
    }
}
