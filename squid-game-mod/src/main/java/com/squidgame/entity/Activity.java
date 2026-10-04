package com.squidgame.entity;

/**
 * Persistent (synced) body pose of a contestant. {@link #NONE} lets locomotion animations (idle / walk /
 * run ...) show through; every other value holds a looping or final-pose animation on the "action"
 * controller until changed. One-shot gestures are not activities: they are triggered with
 * {@link ContestantEntity#triggerAction(String)}.
 */
public enum Activity {
    NONE(null, false),
    FREEZE_BALANCE(Anims.C_FREEZE_BALANCE, true),
    FREEZE_STIFF(Anims.C_FREEZE_STIFF, true),
    ATTENTION(Anims.C_ATTENTION, true),
    HANDS_UP(Anims.C_HANDS_UP, true),
    SIT_IDLE(Anims.C_SIT_IDLE, true),
    PULL_IDLE(Anims.C_PULL_IDLE, true),
    PULL_STRAIN(Anims.C_PULL_STRAIN, true),
    DALGONA_SIT(Anims.C_DALGONA_SIT, true),
    DALGONA_CARVE(Anims.C_DALGONA_CARVE, true),
    DALGONA_FAIL(Anims.C_DALGONA_FAIL, true),
    MARBLE_HOLD(Anims.C_MARBLE_HOLD, true),
    MARBLE_WINDUP(Anims.C_MARBLE_WINDUP, true),
    BRIDGE_HESITATE(Anims.C_BRIDGE_HESITATE, true),
    BRIDGE_BALANCE(Anims.C_BRIDGE_BALANCE, true),
    FIGHT_STANCE(Anims.C_FIGHT_STANCE, true),
    BLOCK(Anims.C_BLOCK, true),
    SPRINT_ATTACK(Anims.C_SPRINT_ATTACK, true),
    COWER(Anims.C_COWER, true),
    SOB(Anims.C_SOB, true),
    CELEBRATE(Anims.C_CELEBRATE, true),
    CELEBRATE_FIST(Anims.C_CELEBRATE_FIST, true),
    THINK(Anims.C_THINK, true),
    INSPECT(Anims.C_INSPECT, true),
    ELIMINATED_FORWARD(Anims.C_ELIM_FORWARD, false),
    ELIMINATED_BACKWARD(Anims.C_ELIM_BACKWARD, false),
    KNOCKED_DOWN(Anims.C_KNOCKED_DOWN, false);

    /** Animation key played on the action controller (null for NONE). */
    public final String animation;
    /** True = loops; false = plays once and holds the final pose. */
    public final boolean loops;

    Activity(String animation, boolean loops) {
        this.animation = animation;
        this.loops = loops;
    }

    public static Activity byOrdinal(int i) {
        Activity[] v = values();
        return i >= 0 && i < v.length ? v[i] : NONE;
    }

    /** True for poses where the contestant lies on the floor / is out of the game visually. */
    public boolean isDown() {
        return this == ELIMINATED_FORWARD || this == ELIMINATED_BACKWARD || this == KNOCKED_DOWN;
    }
}
