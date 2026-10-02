package com.terracraft.core;

import com.terracraft.TerraCraft;
import com.terracraft.command.TerrariaCommand;
import com.terracraft.config.TerraConfig;
import com.terracraft.progression.WorldProgression;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;

import java.lang.invoke.MethodHandles;

/** Server lifecycle: command registration and world rules required by the conversion. */
public final class ServerEvents {
    private ServerEvents() {}

    static void register() {
        BusGroup.DEFAULT.register(MethodHandles.lookup(), ServerEvents.class);
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        TerrariaCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        com.terracraft.world.gen.WorldgenVariants.clear();
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        // Initialise progression/variants eagerly so the first chunk generation sees them.
        WorldProgression progression = WorldProgression.get(server);
        GameRules rules = server.getGameRules();
        if (TerraConfig.COMMON.terrariaLifeRegen.get() && rules.get(GameRules.NATURAL_HEALTH_REGENERATION)) {
            rules.set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            TerraCraft.LOGGER.info("Disabled natural_health_regeneration: Terraria life regeneration is active");
        }
        if (TerraConfig.COMMON.softcoreDeaths.get() && !rules.get(GameRules.KEEP_INVENTORY)) {
            rules.set(GameRules.KEEP_INVENTORY, true, server);
            TerraCraft.LOGGER.info("Enabled keep_inventory: Terraria softcore deaths (coins are dropped instead)");
        }
        if (TerraConfig.COMMON.disableWanderingTraders.get() && rules.get(GameRules.SPAWN_WANDERING_TRADERS)) {
            rules.set(GameRules.SPAWN_WANDERING_TRADERS, false, server);
        }
        if (TerraConfig.COMMON.disableVanillaHostileSpawns.get()) {
            // Pillager patrols and phantoms are vanilla mob progression with no Terraria counterpart.
            rules.set(GameRules.SPAWN_PATROLS, false, server);
            rules.set(GameRules.SPAWN_PHANTOMS, false, server);
        }
        TerraCraft.LOGGER.info("TerraCraft world ready (hardmode={}, evil={})", progression.isHardmode(), progression.variants().evil());
    }
}
