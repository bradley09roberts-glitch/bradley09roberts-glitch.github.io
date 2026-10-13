package com.grandline.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Rebindable controls (agreed: Combat Mode toggle on R, dodge on Left Alt, everything rebindable). */
public final class Keys {
    public static final String CAT = "key.categories.grandline";
    public static final KeyMapping COMBAT_MODE = new KeyMapping("key.grandline.combat_mode", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CAT);
    public static final KeyMapping DODGE = new KeyMapping("key.grandline.dodge", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CAT);
    public static final KeyMapping OBSERVATION = new KeyMapping("key.grandline.observation", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CAT);
    public static final KeyMapping ARMAMENT = new KeyMapping("key.grandline.armament", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CAT);
    public static final KeyMapping CONQUEROR = new KeyMapping("key.grandline.conqueror", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CAT);
    public static final KeyMapping TRANSFORM = new KeyMapping("key.grandline.transform", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CAT);
    public static final KeyMapping LOCK_ON = new KeyMapping("key.grandline.lock_on", KeyConflictContext.IN_GAME, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, CAT);
    public static final KeyMapping CHARACTER = new KeyMapping("key.grandline.character", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CAT);
    public static final KeyMapping CHART = new KeyMapping("key.grandline.chart", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CAT);
    public static final KeyMapping NEXT_SET = new KeyMapping("key.grandline.next_set", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_TAB, CAT);

    public static final KeyMapping ANCHOR = new KeyMapping("key.grandline.anchor", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CAT);
    public static final KeyMapping BURST = new KeyMapping("key.grandline.coup_de_burst", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CAT);

    public static final KeyMapping QUEST_LOG = new KeyMapping("key.grandline.quest_log", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, CAT);
    public static final KeyMapping MINIMAP = new KeyMapping("key.grandline.minimap", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CAT);
    /** Crew roster (companions: active and reserve, orders, swaps) and the quick order (focus what you look at, else regroup). At the helm V/N stay the gun orders. */
    public static final KeyMapping CREW_ROSTER = new KeyMapping("key.grandline.crew_roster", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CAT);
    public static final KeyMapping CREW_ORDER = new KeyMapping("key.grandline.crew_order", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CAT);
    public static final KeyMapping[] ALL = {COMBAT_MODE, DODGE, OBSERVATION, ARMAMENT, CONQUEROR, TRANSFORM, LOCK_ON, CHARACTER, CHART, NEXT_SET, ANCHOR, BURST, QUEST_LOG, MINIMAP,
            CREW_ROSTER, CREW_ORDER};

    private Keys() {}
}
