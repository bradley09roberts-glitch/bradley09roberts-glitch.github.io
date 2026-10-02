package com.terracraft.core;

import com.terracraft.player.PlayerEvents;
import com.terracraft.player.TerraCapabilities;

/**
 * Registers every game-bus listener class. Each class owns a static {@code register()} that uses a
 * lookup created inside that class, so listener methods can stay package-private.
 */
public final class GameEventHandlers {
    private GameEventHandlers() {}

    public static void register() {
        TerraCapabilities.register();
        PlayerEvents.register();
        ServerEvents.register();
        com.terracraft.combat.CombatEvents.register();
        com.terracraft.economy.EconomyEvents.register();
        com.terracraft.data.DataEvents.register();
        com.terracraft.mining.MiningEvents.register();
        com.terracraft.world.VanillaSuppression.register();
        com.terracraft.world.spawn.TerrariaSpawner.register();
    }
}
