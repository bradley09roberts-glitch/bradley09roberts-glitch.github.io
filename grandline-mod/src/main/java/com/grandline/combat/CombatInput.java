package com.grandline.combat;

/** Action codes for {@link com.grandline.net.GLNetwork.CombatInput}. */
public final class CombatInput {
    public static final byte TOGGLE_COMBAT = 0;
    public static final byte LIGHT = 1;
    public static final byte HEAVY = 2;
    public static final byte ABILITY = 3;
    public static final byte DODGE = 4;
    public static final byte BLOCK = 5;
    public static final byte GLIDE = 6;
    public static final byte LOCK = 7;
    public static final byte TRANSFORM = 8;
    public static final byte HAKI_TOGGLE = 9;
    public static final byte SET_STYLE = 10;

    private CombatInput() {}
}
