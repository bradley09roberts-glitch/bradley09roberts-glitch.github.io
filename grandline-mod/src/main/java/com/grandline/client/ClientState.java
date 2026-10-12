package com.grandline.client;

import com.grandline.player.CharacterData;
import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

/** Client-side mirror of the local character plus per-entity visuals received from the server. */
public final class ClientState {
    public static final CharacterData LOCAL = new CharacterData();
    public static boolean hasData = false;
    public static final Map<Integer, CompoundTag> VISUALS = new HashMap<>();
    public static boolean combatMode = false;
    public static int abilitySet = 0;
    public static int lockTarget = -1;
    public static boolean blocking = false;
    /** Locally predicted cooldown end times (client ticks) per ability id, for HUD display only. */
    public static final Map<String, Long> COOLDOWNS = new HashMap<>();
    public static long clientTicks = 0;

    public static CompoundTag visuals(int entityId) {
        return VISUALS.getOrDefault(entityId, EMPTY);
    }

    private static final CompoundTag EMPTY = new CompoundTag();

    public static void reset() {
        VISUALS.clear();
        hasData = false;
        combatMode = false;
        lockTarget = -1;
        COOLDOWNS.clear();
    }

    private ClientState() {}
}
