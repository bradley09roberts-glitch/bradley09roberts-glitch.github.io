package com.squidgame.game.finale;

import com.squidgame.core.finale.CourtGeometry;
import net.minecraft.world.entity.LivingEntity;

/**
 * The physical body of a fighter, which is either a player (movement comes from the keyboard, the world only adds
 * knock-back and dashes) or an NPC (every bit of movement is computed by the same {@link com.squidgame.core.finale.Motion}
 * the headless simulator uses). The game talks to both through this interface.
 */
abstract class FightBody {
    abstract LivingEntity entity();

    /** True for a player's body. */
    abstract boolean isPlayer();

    /** Adds a knock-back impulse (blocks per tick, horizontal). */
    abstract void impulse(double ix, double iz);

    /** Starts the dash of a dodge. */
    abstract void dash(double vx, double vz, int ticks);

    /** A staggering blow ends a dash in progress. */
    abstract void cancelDash();

    /**
     * Moves the body for this tick. For an NPC {@code wish} is the velocity it wants (blocks per tick); a player
     * ignores it. The dash never carries a body over the line.
     */
    abstract void move(double wishX, double wishZ, CourtGeometry court);

    /** The multiplier on the walking speed implied by the fighter's state (guard, charge, stagger...). */
    abstract void setSpeedFactor(double factor);

    /** Gives the body back: removes every modifier the fight applied. */
    abstract void release();

    double x() {
        return entity().getX();
    }

    double z() {
        return entity().getZ();
    }

    double yaw() {
        return entity().getYRot();
    }
}
