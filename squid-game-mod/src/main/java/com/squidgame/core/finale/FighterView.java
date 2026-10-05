package com.squidgame.core.finale;

/**
 * The public face of a fighter: everything a human watching the duel (and reading the HUD bars) can see. NPC
 * brains only ever look at views, so they have exactly the information a player has.
 *
 * @param actTick    ticks since the current act began (negative = it has just started)
 * @param windingUp  an attack that has not landed yet (or a charge): the telegraph
 * @param recovering an attack that has landed or whiffed and still blocks the fighter
 * @param charge     ticks the attack button has been held once the charge is visible, else 0
 * @param guardUp    the guard is fully raised
 * @param guardHeld  the fighter is raising or holding the guard
 * @param invulnerable the dodge window is open
 * @param stunLeft   ticks of stagger left
 */
public record FighterView(int slot, Role role, double x, double z, double yaw, double health, double stamina,
                          Act act, int actTick, boolean windingUp, boolean recovering, int charge,
                          boolean guardUp, boolean guardHeld, boolean invulnerable, int stunLeft,
                          boolean exhausted, boolean dodgeReady, boolean sprinting) {

    public double dist(FighterView o) {
        return Math.hypot(x - o.x, z - o.z);
    }

    /** True when the fighter cannot start anything: it is staggered or in the tail of an action. */
    public boolean helpless() {
        return act.isStagger() || recovering;
    }
}
