package com.squidgame.core.redlight;

import com.squidgame.core.Difficulty;

/**
 * Pure movement rules for Red Light, Green Light, shared by players and NPCs.
 *
 * <h2>The rules, as shown to players</h2>
 * <ul>
 *   <li>While the doll chants (<b>green</b>) you may move freely.</li>
 *   <li>When the last syllable ends the doll turns. You now have the <b>stopping allowance</b>
 *       ({@link Params#allowanceTicks}: 1.1 s on Normal, 0.6 s on Hard, 0.25 s on Extreme) to come
 *       to a halt. Her eyes light up the instant the allowance ends: <b>eyes lit = you are watched</b>.</li>
 *   <li>While the eyes are lit, any <b>movement you cause</b> eliminates you: horizontal motion
 *       above {@link Params#moveThreshold} blocks/tick while you hold a movement key, or leaving the
 *       ground by your own jump. Holding a movement key against a wall is fine (you do not move).
 *       Turning the camera never counts (only position is judged).</li>
 *   <li><b>Externally caused movement</b> (knock-back, being shoved by a neighbour, platform or water
 *       current) does not count as long as you are not pressing a movement key and the displacement
 *       stays below {@link Params#externalMoveThreshold}. Anything bigger that you did not cause
 *       (a teleport, a speed hack) is flagged as {@link Verdict#ILLEGAL}.</li>
 *   <li>If you are already in mid-air when the eyes light you may land; leaving the ground again
 *       by pressing jump is a violation.</li>
 * </ul>
 */
public final class RedLightRules {
    private RedLightRules() {
    }

    /** Difficulty-dependent parameters. */
    public record Params(int allowanceTicks, int dollTurnTicks, double moveThreshold,
                         double externalMoveThreshold, double illegalMoveThreshold) {
    }

    /** A per-tick observation of one contestant (identical for players and NPCs). */
    public record Sample(double horizontalMove, double verticalMove, boolean onGround,
                         boolean hasMoveInput, boolean jumpInput, boolean externalCause) {
    }

    public enum Verdict {
        OK,
        /** Moved while watched. */
        MOVED,
        /** Left the ground by their own jump while watched. */
        JUMPED,
        /** Moved a lot without any input and without a known external cause (teleport / hack). */
        ILLEGAL;

        public boolean isViolation() {
            return this != OK;
        }
    }

    public static Params params(Difficulty d) {
        int allowance = Math.max(3, (int) Math.round(22 * d.allowanceScale));
        int turn = switch (d) {
            case NORMAL -> 18;
            case HARD -> 12;
            case EXTREME -> 8;
        };
        // 0.02 b/tick = 0.4 b/s: below this a player is considered stationary (sub-pixel drift, ground
        // friction creep, client-side prediction noise). Normal walking is ~0.215 b/tick.
        double moveThreshold = switch (d) {
            case NORMAL -> 0.035;
            case HARD -> 0.025;
            case EXTREME -> 0.018;
        };
        return new Params(allowance, turn, moveThreshold, 0.16, 0.9);
    }

    /**
     * Judges one contestant for one tick.
     *
     * @param watched          true while the eyes are lit (enforcement active)
     * @param airborneAtWatch  true if the contestant was in mid-air at the moment enforcement started
     * @param sample           this tick's observation
     */
    public static Verdict evaluate(Params p, boolean watched, boolean airborneAtWatch, Sample sample) {
        if (!watched) {
            return Verdict.OK;
        }
        double h = sample.horizontalMove();
        // 1. cheating: large displacement with no input and no external cause
        if (!sample.hasMoveInput() && !sample.externalCause() && h > p.illegalMoveThreshold()) {
            return Verdict.ILLEGAL;
        }
        // 2. own jump: pressing jump and leaving the ground (a contestant already airborne when the eyes
        //    lit may finish their arc but not start a new one)
        if (sample.jumpInput() && !sample.onGround() && sample.verticalMove() > 0.0 && !airborneAtWatch) {
            return Verdict.JUMPED;
        }
        // 3. deliberate horizontal motion
        if (sample.hasMoveInput() && h > p.moveThreshold()) {
            return Verdict.MOVED;
        }
        // 4. motion without input: only external pushes are tolerated, and only small ones
        if (!sample.hasMoveInput() && !sample.externalCause() && h > p.externalMoveThreshold()) {
            return Verdict.MOVED;
        }
        return Verdict.OK;
    }
}
