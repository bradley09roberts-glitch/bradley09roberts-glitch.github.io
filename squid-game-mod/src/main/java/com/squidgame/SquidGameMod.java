package com.squidgame;

import com.squidgame.command.SquidCommands;
import com.squidgame.net.ModNetwork;
import com.squidgame.registry.ModBlocks;
import com.squidgame.registry.ModEntities;
import com.squidgame.registry.ModItems;
import com.squidgame.registry.ModSounds;
import com.squidgame.tournament.Restrictions;
import com.squidgame.tournament.TournamentManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (server + client) entry point. Registers content, networking, rules and lifecycle hooks. All client-only
 * code lives in the separate {@code client} source set and cannot leak into a dedicated server.
 */
public class SquidGameMod implements ModInitializer {
    public static final String MOD_ID = "squidgame";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        SquidConfig.load();
        ModSounds.init();
        ModBlocks.init();
        ModEntities.init();
        ModItems.init();
        ModNetwork.init();
        Restrictions.registerEvents();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SquidCommands.register(dispatcher);
            Restrictions.wrapVanillaCommands(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(TournamentManager::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(TournamentManager::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            TournamentManager m = TournamentManager.get();
            if (m != null) {
                m.tick();
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            TournamentManager m = TournamentManager.get();
            if (m != null) {
                m.onPlayerJoin(handler.getPlayer());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            TournamentManager m = TournamentManager.get();
            if (m != null) {
                m.onPlayerDisconnect(handler.getPlayer());
            }
        });
        LOGGER.info("Squid Game Tournament initialised");
    }
}
