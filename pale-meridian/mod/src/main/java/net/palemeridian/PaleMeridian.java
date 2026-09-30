package net.palemeridian;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PaleMeridian implements ModInitializer {
	public static final String MOD_ID = "palemeridian";
	public static final Logger LOG = LoggerFactory.getLogger("PaleMeridian");

	@Override
	public void onInitialize() {
		LOG.info("Pale Meridian initialising");
		net.palemeridian.world.PMWorldgen.register();
	}
}
