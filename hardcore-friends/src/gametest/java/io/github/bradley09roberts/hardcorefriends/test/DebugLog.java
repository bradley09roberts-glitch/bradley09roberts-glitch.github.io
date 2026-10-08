package io.github.bradley09roberts.hardcorefriends.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;

/** TEMPORARY: development diagnostics, removed before commit. */
final class DebugLog {
	private DebugLog() {
	}

	static void done(GameTestHelper helper, String name, CampData data) {
		StringBuilder sb = new StringBuilder();
		sb.append("WP4DEBUG ").append(name).append(" done at tick ").append(helper.getTick()).append(" completed=").append(data.completed());
		data.sites().forEach((k, v) -> sb.append("\n  site ").append(k).append(" rel ").append(helper.relativePos(v.origin)).append(" rot ").append(v.rotation)
			.append(" progress ").append(v.progress));
		for (int y = 2; y <= 8; y++) {
			sb.append("\n y=").append(y).append('\n');
			for (int z = 0; z < 32; z++) {
				for (int x = 0; x < 32; x++) {
					BlockState s = helper.getBlockState(new BlockPos(x, y, z));
					sb.append(symbol(s));
				}
				sb.append('\n');
			}
		}
		HardcoreFriends.LOGGER.info(sb.toString());
	}

	private static char symbol(BlockState s) {
		if (s.isAir()) {
			return '.';
		}
		String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		if (id.endsWith("planks")) return 'P';
		if (id.endsWith("_log")) return 'L';
		if (id.endsWith("slab")) return 'S';
		if (id.endsWith("door")) return 'D';
		if (id.equals("glass_pane")) return 'G';
		if (id.contains("torch")) return 'T';
		if (id.equals("chest")) return 'C';
		if (id.equals("crafting_table")) return 'W';
		if (id.equals("furnace")) return 'F';
		if (id.equals("campfire")) return 'f';
		if (id.equals("hopper")) return 'H';
		if (id.endsWith("pressure_plate")) return '_';
		if (id.equals("cobblestone")) return 'c';
		if (id.endsWith("fence")) return '|';
		if (id.equals("ladder")) return '#';
		if (id.equals("short_grass") || id.equals("tall_grass")) return ',';
		return '?';
	}
}
