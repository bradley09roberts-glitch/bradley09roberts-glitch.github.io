package net.palemeridian.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;
import net.palemeridian.PaleMeridian;
import net.palemeridian.state.PMState;

/** One-time world setup (spawn at the Landing) and a guard against non-campaign world types. */
public final class WorldSetup {
	private static boolean valleyWorld = true;

	private WorldSetup() {
	}

	public static void onServerStarted(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		valleyWorld = overworld.getChunkSource().getGenerator().getBiomeSource() instanceof ValleyBiomeSource;
		if (!valleyWorld) {
			PaleMeridian.LOG.warn("*** This world was not created with the Pale Meridian valley (world type must be 'Default'). "
				+ "The campaign cannot run here. Create a new world with the default world type. ***");
			return;
		}
		if (PMState.get(server, "#spawnset") != 1) {
			ValleyLayout l = ValleyLayout.get();
			BlockPos pos = new BlockPos(l.spawnX, l.spawnY, l.spawnZ);
			overworld.setRespawnData(LevelData.RespawnData.of(overworld.dimension(), pos, l.spawnYaw, 0.0F));
			PMState.set(server, "#spawnset", 1);
			PaleMeridian.LOG.info("World spawn set to the Landing at {}", pos);
		}
	}

	public static void onJoin(ServerPlayer player) {
		if (!valleyWorld) {
			player.sendSystemMessage(Component.literal(
					"[Pale Meridian] This world was not created with the 'Default' world type, so the valley and the story are missing. "
						+ "Please create a new world and leave World Type on Default.")
				.withStyle(ChatFormatting.RED));
		}
	}

	public static boolean isValleyWorld() {
		return valleyWorld;
	}
}
