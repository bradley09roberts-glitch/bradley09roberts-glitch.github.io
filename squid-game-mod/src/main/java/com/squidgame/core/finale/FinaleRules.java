package com.squidgame.core.finale;

import com.squidgame.core.Difficulty;

/**
 * Numbers of the final duel, shared by players, NPCs, the live game and the headless simulator. Everything is
 * measured in blocks and server ticks (20 per second).
 *
 * <h2>The rules in one paragraph</h2>
 * Two fighters, 100 health and a stamina pool each. Left click taps a <b>light strike</b> (fast, cheap, small),
 * holding it charges a <b>heavy strike</b> (visible wind-up, big damage and knock-back, breaks guards), the right
 * mouse button holds a <b>guard</b> (70 % less damage, drains stamina per blocked hit, a hit that lands in the first
 * ticks after raising it is a <b>parry</b>), the dash key or a double-tapped key <b>dodges</b> (short invulnerability,
 * costs stamina) and the shove key <b>shoves</b> (little damage, big knock-back, ignores guards): the tool that pushes
 * someone over the line. A light strike interrupts a wind-up (counter hit); a guard beats light strikes, a heavy or a
 * shove beats a guard, a timed dodge beats everything the opponent has telegraphed.
 */
public final class FinaleRules {
    private FinaleRules() {
    }

    // ---------------------------------------------------------------- movement (vanilla player values)

    /** Walking speed in blocks per tick (4.317 blocks/s). */
    public static final double WALK = 0.2158;
    /** Sprinting speed in blocks per tick (5.6 blocks/s). */
    public static final double SPRINT = 0.2806;
    /** Horizontal velocity kept per tick on the ground (vanilla: block friction 0.6 x 0.91). */
    public static final double FRICTION = 0.546;
    /** Half the body width. */
    public static final double BODY_RADIUS = 0.3;
    /** A fighter whose centre is closer than this to the outer edge of the painted line (or beyond) is out. */
    public static final double OUT_MARGIN = 0.2;
    /**
     * The attacker is "inside the circle" within this fraction of the marked radius of its centre: the golden ring
     * painted in the middle of the head (radius 3 of 5), not just the white line around it.
     */
    public static final double CAPTURE_FRACTION = 0.6;

    // ---------------------------------------------------------------- vitals

    public static final double HEALTH_MAX = 100;
    /** Stamina below which an exhausted fighter may act again. */
    public static final double EXHAUST_RECOVER = 22;
    /** Stamina drained per tick while sprinting. */
    public static final double SPRINT_DRAIN = 0.30;
    /** Stamina drained per tick while charging a heavy strike. */
    public static final double CHARGE_DRAIN = 0.20;
    /** Regeneration multiplier while the guard is held. */
    public static final double GUARD_REGEN = 0.35;

    // ---------------------------------------------------------------- input timing

    /** Ticks from the press of the attack button to the impact of a light strike. */
    public static final int TAP_SWING = 4;
    /** Holding the attack button this long makes the wind-up visible (the fighter is "charging"). */
    public static final int CHARGE_SHOW = 6;
    /** Released after at least this many ticks the strike is a heavy one. */
    public static final int HEAVY_MIN_HOLD = 10;
    /** The charge is released automatically after this long. */
    public static final int HEAVY_MAX_HOLD = 26;
    /** Ticks between pressing the guard and it being up. */
    public static final int GUARD_RAISE = 3;
    /** Half the opening angle of the area in front of a fighter that its guard covers. */
    public static final double GUARD_CONE = 75;
    /** A press made while busy is remembered this long. */
    public static final int INPUT_BUFFER = 5;
    /** Ticks after a stun ends during which a fighter cannot be stunned again (damage still applies). */
    public static final int STUN_GRACE = 8;
    /** Hits taken closer together than this chain a combo: every further hit shortens the stun. */
    public static final int COMBO_WINDOW = 36;

    // ---------------------------------------------------------------- the actions

    /**
     * One attack. {@code windup} = ticks from the start of the swing to the impact, {@code recovery} = ticks
     * after the impact in which the attacker cannot act, {@code stun} = ticks the victim of a clean hit is staggered,
     * {@code blockDrain} = stamina a guard loses when it absorbs the hit, {@code whiff} = extra recovery when it hit
     * nothing.
     */
    public record Spec(int windup, int recovery, double cost, double damage, double knockback, double reach,
                       double cone, int stun, double blockDrain, int whiff) {
    }

    public static final Spec LIGHT = new Spec(TAP_SWING, 8, 8, 7, 0.30, 2.6, 50, 7, 9, 4);
    /** Heavy strike at minimum charge; {@link #heavyDamage} / {@link #heavyKnockback} scale it with the charge. */
    public static final Spec HEAVY = new Spec(5, 14, 20, 20, 0.65, 2.8, 50, 14, 30, 6);
    public static final Spec SHOVE = new Spec(6, 12, 12, 2, 1.05, 2.1, 42, 9, 10, 4);

    /** Hold time at which a heavy strike is fully charged. */
    public static final int HEAVY_FULL_HOLD = 20;

    /** Charge of a heavy strike released after {@code heldTicks}: 0 at the minimum hold, 1 when fully charged. */
    public static double heavyPower(int heldTicks) {
        return Math.min(1.0, Math.max(0.0, (heldTicks - HEAVY_MIN_HOLD) / (double) (HEAVY_FULL_HOLD - HEAVY_MIN_HOLD)));
    }

    public static double heavyDamage(double power) {
        return HEAVY.damage() + 8.0 * power;
    }

    public static double heavyKnockback(double power) {
        return HEAVY.knockback() + 0.35 * power;
    }

    public static double heavyBlockDrain(double power) {
        return HEAVY.blockDrain() + 8.0 * power;
    }

    /** Damage multiplier when the victim is hit while it was winding up (a counter hit). */
    public static final double COUNTER_BONUS = 1.25;
    /** Damage kept by a guard that absorbs a hit. */
    public static final double BLOCK_KEEPS = 0.30;
    /** Knock-back kept by a guard against a strike / against a shove. */
    public static final double BLOCK_KB_STRIKE = 0.45, BLOCK_KB_SHOVE = 0.65;
    /** Stun of a guard that was broken, and of an attacker whose strike was parried. */
    public static final int BROKEN_STUN = 18, PARRIED_STUN = 11;
    public static final double PARRY_REWARD = 8, DODGE_REFUND = 6, HIT_REWARD = 3;

    // dodge
    public static final double DODGE_COST = 16;
    /** Ticks of the whole dodge action, and ticks until another dodge is possible. */
    public static final int DODGE_LENGTH = 12, DODGE_COOLDOWN = 24;
    /** The dash: ticks of controlled movement and its speed in blocks per tick (about 2.9 blocks in total). */
    public static final int DASH_TICKS = 6;
    public static final double DASH_SPEED = 0.48;
    /** A dash stops when its next step would end closer than this to the edge: dodging never loses the duel by itself. */
    public static final double DASH_EDGE = OUT_MARGIN + 0.45;
    /** How far a player keeps sliding after the last tick of a dash (vanilla ground friction): speed x friction / (1 - friction). */
    public static final double DASH_SLIDE = DASH_SPEED * FRICTION / (1.0 - FRICTION);

    /** How far (blocks) a full knock-back impulse carries a fighter: impulse / (1 - friction). */
    public static double travel(double impulse) {
        return impulse / (1.0 - FRICTION);
    }

    // ---------------------------------------------------------------- difficulty

    /**
     * Difficulty dependent numbers (the NPC skill bonus comes from {@link Difficulty#npcSkillBonus}).
     * Harder = shorter time, smaller stamina pool and slower regeneration, tighter dodge and parry windows,
     * more damage per hit.
     */
    public record Params(int duelTicks, double staminaMax, double staminaRegen, int regenDelay, double damageScale,
                         int dodgeIframes, int parryWindow, int captureTicks) {
    }

    public static Params params(Difficulty d) {
        return switch (d) {
            case NORMAL -> new Params(180 * 20, 100, 0.50, 24, 1.00, 6, 4, 40);
            case HARD -> new Params(150 * 20, 88, 0.44, 28, 1.12, 5, 3, 40);
            case EXTREME -> new Params(120 * 20, 75, 0.38, 32, 1.25, 4, 2, 40);
        };
    }

    // ---------------------------------------------------------------- ceremonies (ticks at normal speed)

    /** Coin toss, roles and "ready... fight" before a live duel, and the pause after it. */
    public static final int INTRO_TICKS = 150, OUTRO_TICKS = 70;
    /**
     * A duel that is settled by the simulator is shown as a ceremony: the coin toss, a replay of the simulated duel
     * (about {@code CEREMONY_SHOW_TICKS}; a short duel takes less, a very busy one up to twice as long) and the aftermath.
     * {@code CEREMONY_TICKS} is the most one ceremony may take, used for the time budget of the whole game.
     */
    public static final int CEREMONY_COIN_TICKS = 70, CEREMONY_SHOW_TICKS = 100, CEREMONY_AFTER_TICKS = 60, CEREMONY_TICKS = 70 + 2 * 100 + 60;

    // ---------------------------------------------------------------- helpers shared by the simulation and the NPC brain

    /** Minecraft yaw (degrees, 0 = +Z, 90 = -X) of the direction (dx, dz). */
    public static double yawOf(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    public static double forwardX(double yawDeg) {
        return -Math.sin(Math.toRadians(yawDeg));
    }

    public static double forwardZ(double yawDeg) {
        return Math.cos(Math.toRadians(yawDeg));
    }

    /** Smallest signed difference (degrees) from yaw {@code a} to yaw {@code b}, in [-180, 180). */
    public static double angleDiff(double a, double b) {
        double d = (b - a) % 360.0;
        if (d >= 180) {
            d -= 360;
        } else if (d < -180) {
            d += 360;
        }
        return d;
    }
}
