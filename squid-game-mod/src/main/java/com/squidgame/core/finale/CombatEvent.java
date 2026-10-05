package com.squidgame.core.finale;

/**
 * Something the simulation wants the world to show (animation, sound, particles, knock-back). Events carry no
 * hidden information: everything in them is visible to both fighters.
 *
 * @param type   what happened
 * @param actor  the fighter that did it (the attacker of a strike, the dodger of a dodge)
 * @param target the fighter it happened to, or -1
 * @param kind   the kind of action involved, or null
 * @param amount damage dealt (HIT / BLOCKED / GUARD_BREAK), charge power 0..1 (SWING of a heavy), dash ticks (DODGE)
 * @param ix     x of the velocity impulse (blocks per tick) the world must apply to {@link #impulseSlot()}
 * @param iz     z of that impulse
 */
public record CombatEvent(Type type, int actor, int target, ActionKind kind, double amount, double ix, double iz) {
    public enum Type {
        /** An attack started its swing (play the arm animation now; the impact follows after the wind-up). */
        SWING,
        /** The attack button has been held long enough for the wind-up to be visible. */
        CHARGE_START,
        /** The guard came up / went down. */
        GUARD_UP, GUARD_DOWN,
        /** A dodge started (impulse = dash velocity, amount = dash ticks). */
        DODGE,
        /** A clean hit (impulse on the target). */
        HIT,
        /** A guard absorbed a hit (impulse on the target, reduced). */
        BLOCKED,
        /** A guard raised just in time deflected the attack (impulse on the actor: recoil). */
        PARRIED,
        /** A guard absorbed more than its stamina could take: full hit, long stagger. */
        GUARD_BREAK,
        /** The target's invulnerability window absorbed the impact. */
        DODGED,
        /** The strike found nobody in reach or in front of it. */
        WHIFF,
        /** An action was refused for lack of stamina (the HUD flashes the bar). */
        DENIED,
        /** Stamina ran out / came back. */
        EXHAUSTED, RECOVERED
    }

    /** The fighter that receives the impulse. */
    public int impulseSlot() {
        return type == Type.PARRIED || type == Type.DODGE ? actor : target;
    }

    public boolean hasImpulse() {
        return ix != 0 || iz != 0;
    }
}
