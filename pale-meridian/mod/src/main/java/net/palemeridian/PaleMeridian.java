package net.palemeridian;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.palemeridian.admin.AdminCommand;
import net.palemeridian.world.PMWorldgen;
import net.palemeridian.world.Restoration;
import net.palemeridian.world.WorldSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pale Meridian. The campaign logic is a data pack embedded in this mod (data/palemeridian); the Java
 * side provides the designed valley's world generation and a small restoration service.
 */
public final class PaleMeridian implements ModInitializer {
	public static final String MOD_ID = "palemeridian";
	public static final Logger LOG = LoggerFactory.getLogger("PaleMeridian");

	@Override
	public void onInitialize() {
		LOG.info("Pale Meridian initialising");
		PMWorldgen.register();
		ServerLifecycleEvents.SERVER_STARTED.register(WorldSetup::onServerStarted);
		ServerTickEvents.END_SERVER_TICK.register(Restoration::tick);
		ServerChunkEvents.CHUNK_LOAD.register(Restoration::onChunkLoad);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> WorldSetup.onJoin(handler.getPlayer()));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> AdminCommand.register(dispatcher));
	}
}
