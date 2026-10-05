package com.squidgame.core.finale;

/**
 * Mutable state of one fighter inside a {@link Duel}. Only the duel changes it (the owner of the body feeds the pose
 * and the buttons through the duel's methods). Package-private on purpose: the rest of the code works with
 * {@link FighterView}s.
 */
final class Fighter {
    final int slot;
    Role role;

    // pose, fed by the owner every tick (the live game reads it from the entity)
    double x, z, yaw;
    boolean sprinting;

    // vitals
    double health = FinaleRules.HEALTH_MAX;
    double stamina;
    boolean exhausted;

    // current act
    Act act = Act.NONE;
    ActionKind kind;
    int actTick, impactAt, endAt;
    boolean impacted;
    double power;
    int stunLeft;

    // attack button
    boolean attackHeld;
    int heldTicks;
    boolean cmdAttackDown, cmdAttackUp;
    int bufAttack;
    boolean bufAttackUp;

    // other buttons
    boolean guardHeld;
    int guardTicks;
    boolean cmdShove;
    int bufShove;
    boolean cmdDodge;
    double dodgeX, dodgeZ;

    // timers
    int grace, iframes, dodgeCooldown, regenDelayLeft, sinceHit = 1000, combo;

    // the dash of a dodge: the world moves the body this fast for this many ticks
    int dashLeft;
    double dashX, dashZ;

    final Stats stats = new Stats();

    Fighter(int slot, Role role, double staminaMax) {
        this.slot = slot;
        this.role = role;
        this.stamina = staminaMax;
    }

    /** Counters for results screens, tests and balancing. */
    static final class Stats {
        int lights, heavies, shoves, dodges, hitsLanded, hitsTaken, blocked, parries, guardBreaks, whiffs, denied;
        double damageDealt, damageTaken;
    }
}
