package io.github.bradley09roberts.hardcorefriends.defence;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Where the village is, for the defence: the camp's world, its centre and its radius (which grows with the village's
 * streets and plots). "In the village" means inside that radius, at most {@value #EDGE} blocks beyond its edge where
 * asked, and up at ground level: a mob in a cave under the houses, or a miner down the camp mine, is not in the village
 * as far as the alarm is concerned. Nothing here loads a chunk or scans the world.
 */
final class Area {
	/** How far beyond the camp's edge a hostile still counts as at the village, closing in. */
	static final int EDGE = 8;
	/** More than this far below the ground (the highest solid block) counts as below the village, not in it. */
	private static final int BELOW_GROUND = 12;

	private Area() {
	}

	/** The world the camp is in, or null when there is no camp (or its world is not loaded). */
	static @Nullable ServerLevel campLevel(MinecraftServer server, CampData data) {
		if (data.campPos().isEmpty()) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (Camp.isCampLevel(level, data)) {
				return level;
			}
		}
		return null;
	}

	/** True if this position is within {@code radius} blocks of the centre (horizontally) and not deep underground. */
	static boolean inside(ServerLevel level, BlockPos pos, BlockPos centre, int radius) {
		if (Camp.horizontalDistSqr(pos, centre) > (double) radius * radius || !level.isLoaded(pos)) {
			return false;
		}
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		return pos.getY() >= ground - BELOW_GROUND;
	}

	/** True if this living thing stands in the village (see the class description), {@code margin} blocks beyond its edge included. */
	static boolean inside(LivingEntity e, int margin) {
		if (!(e.level() instanceof ServerLevel level)) {
			return false;
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return false;
		}
		BlockPos centre = data.campPos().orElseThrow();
		return inside(level, e.blockPosition(), centre, Camp.radius(data) + margin);
	}

	/** True if this friend is at work with the team in the village (not following anyone off, not down the mine). */
	static boolean atHome(CompanionEntity c) {
		return c.isAlive() && !c.isRemoved() && c.isTeamMember() && inside(c, 0);
	}
}
