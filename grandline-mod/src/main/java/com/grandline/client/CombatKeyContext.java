package com.grandline.client;

import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * Keys that only act in Combat Mode (lock-on on the middle button, the next technique set on Tab). They share
 * buttons with vanilla actions that make no sense mid-fight (pick block, the player list), so they do not count as a
 * conflict in the Controls menu; in Combat Mode the vanilla action is suppressed (see ClientEvents).
 */
public enum CombatKeyContext implements IKeyConflictContext {
    INSTANCE;

    @Override
    public boolean isActive() {
        return KeyConflictContext.IN_GAME.isActive() && ClientState.combatMode;
    }

    @Override
    public boolean conflicts(IKeyConflictContext other) {
        return other == this;
    }
}
