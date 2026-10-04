package com.squidgame.entity;

/** Animation names (see docs/ASSET_CONTRACT.md). The strings are the full keys inside the .animation.json files. */
public final class Anims {
    private Anims() {
    }

    public static String c(String name) {
        return "animation.contestant." + name;
    }

    public static String g(String name) {
        return "animation.guard." + name;
    }

    public static String d(String name) {
        return "animation.doll." + name;
    }

    // ---- contestant locomotion
    public static final String C_IDLE = c("idle");
    public static final String C_IDLE_NERVOUS = c("idle_nervous");
    public static final String C_IDLE_CONFIDENT = c("idle_confident");
    public static final String C_WALK = c("walk");
    public static final String C_RUN = c("run");
    public static final String C_STOP_SKID = c("stop_skid");
    public static final String C_TURN_LEFT = c("turn_left");
    public static final String C_TURN_RIGHT = c("turn_right");
    public static final String C_FREEZE_BALANCE = c("freeze_balance");
    public static final String C_FREEZE_STIFF = c("freeze_stiff");
    public static final String C_SNEAK_WALK = c("sneak_walk");
    public static final String C_JUMP_LEAP = c("jump_leap");
    public static final String C_FALL_LOOP = c("fall_loop");
    public static final String C_LAND = c("land");

    // ---- contestant reactions / actions
    public static final String C_STUMBLE = c("stumble");
    public static final String C_LOSE_BALANCE = c("lose_balance");
    public static final String C_SHOCKED = c("shocked");
    public static final String C_COWER = c("cower");
    public static final String C_SOB = c("sob");
    public static final String C_RELIEVED = c("relieved");
    public static final String C_CELEBRATE = c("celebrate");
    public static final String C_CELEBRATE_FIST = c("celebrate_fist");
    public static final String C_WAVE = c("wave");
    public static final String C_POINT = c("point");
    public static final String C_NOD = c("nod");
    public static final String C_SHAKE_HEAD = c("shake_head");
    public static final String C_THINK = c("think");
    public static final String C_INSPECT = c("inspect");
    public static final String C_INTERACT = c("interact");
    public static final String C_ELIM_FORWARD = c("eliminated_forward");
    public static final String C_ELIM_BACKWARD = c("eliminated_backward");
    public static final String C_KNOCKED_DOWN = c("knocked_down");

    // ---- tug of war
    public static final String C_PULL_IDLE = c("pull_idle");
    public static final String C_PULL_HEAVE = c("pull_heave");
    public static final String C_PULL_STRAIN = c("pull_strain");
    public static final String C_PULL_SLIP = c("pull_slip");

    // ---- dalgona
    public static final String C_DALGONA_SIT = c("dalgona_sit");
    public static final String C_DALGONA_CARVE = c("dalgona_carve");
    public static final String C_DALGONA_LICK = c("dalgona_lick");
    public static final String C_DALGONA_CRACK = c("dalgona_crack");
    public static final String C_DALGONA_SUCCESS = c("dalgona_success");
    public static final String C_DALGONA_FAIL = c("dalgona_fail");

    // ---- marbles
    public static final String C_MARBLE_HOLD = c("marble_hold");
    public static final String C_MARBLE_GUESS = c("marble_guess");
    public static final String C_MARBLE_REVEAL = c("marble_reveal");
    public static final String C_MARBLE_WINDUP = c("marble_throw_windup");
    public static final String C_MARBLE_RELEASE = c("marble_throw_release");

    // ---- glass bridge
    public static final String C_BRIDGE_STEP = c("bridge_step");
    public static final String C_BRIDGE_HESITATE = c("bridge_hesitate");
    public static final String C_BRIDGE_BALANCE = c("bridge_balance");

    // ---- combat
    public static final String C_FIGHT_STANCE = c("fight_stance");
    public static final String C_PUNCH_LEFT = c("punch_left");
    public static final String C_PUNCH_RIGHT = c("punch_right");
    public static final String C_SHOVE = c("shove");
    public static final String C_BLOCK = c("block");
    public static final String C_DODGE_LEFT = c("dodge_left");
    public static final String C_DODGE_RIGHT = c("dodge_right");
    public static final String C_KNOCKED_BACK = c("knocked_back");
    public static final String C_SPRINT_ATTACK = c("sprint_attack");

    // ---- misc
    public static final String C_SIT_IDLE = c("sit_idle");
    public static final String C_ATTENTION = c("attention");
    public static final String C_HANDS_UP = c("hands_up");

    // ---- guard
    public static final String G_IDLE = g("idle");
    public static final String G_IDLE_ALERT = g("idle_alert");
    public static final String G_IDLE_RIGID = g("idle_rigid");
    public static final String G_WALK = g("walk");
    public static final String G_RUN = g("run");
    public static final String G_AIM = g("aim");
    public static final String G_FIRE = g("fire");
    public static final String G_LOWER = g("lower");
    public static final String G_TURN_LEFT = g("turn_left");
    public static final String G_TURN_RIGHT = g("turn_right");
    public static final String G_POINT_FORWARD = g("point_forward");
    public static final String G_POINT_DOWN = g("point_down");
    public static final String G_SALUTE = g("salute");
    public static final String G_OPEN_DOOR = g("open_door");
    public static final String G_INSPECT = g("inspect");
    public static final String G_CARRY_POSE = g("carry_pose");
    public static final String G_CLAP = g("clap");
    public static final String G_WAVE_ON = g("wave_on");

    // ---- doll
    public static final String D_DORMANT = d("dormant");
    public static final String D_WAKE = d("wake");
    public static final String D_IDLE_TREE = d("idle_tree");
    public static final String D_TURN_TO_PLAYERS = d("turn_to_players");
    public static final String D_IDLE_PLAYERS = d("idle_players");
    public static final String D_SCAN_PLAYERS = d("scan_players");
    public static final String D_LOCK_ON = d("lock_on");
    public static final String D_TURN_TO_TREE = d("turn_to_tree");
}
