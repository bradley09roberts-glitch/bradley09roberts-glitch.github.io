package io.github.bradley09roberts.hardcorefriends.ai.role.farm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Finds a flat 9×9 patch of grass or dirt inside the camp for Fern's farm plot: level ground, nothing player-built
 * nearby, clear of other reserved sites and the supply chest, and at least five blocks from the camp centre.
 */
public final class FarmPlotFinder {
	public static final int HALF = 4;
	private static final int STEP = 2;
	private static final int MAX_CANDIDATES = 100;
	private static final int SITE_SPACING = 12;

	private FarmPlotFinder() {
	}

	/** The centre block (ground level) of a suitable plot, or null if the camp has no room. */
	public static @Nullable BlockPos find(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		BlockPos home = c.homePos();
		int maxDist = Camp.radius(data) - HALF - 1;
		List<int[]> offsets = new ArrayList<>();
		for (int dx = -maxDist; dx <= maxDist; dx += STEP) {
			for (int dz = -maxDist; dz <= maxDist; dz += STEP) {
				// The plot's nearest edge stays at least five blocks from the centre.
				if (Math.max(Math.abs(dx), Math.abs(dz)) < HALF + 5 || dx * dx + dz * dz > maxDist * maxDist) {
					continue;
				}
				offsets.add(new int[] {dx, dz});
			}
		}
		// Nearest the camp centre first: the farm should be handy, just clear of the fire and paths.
		offsets.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
		int checked = 0;
		for (int[] o : offsets) {
			if (++checked > MAX_CANDIDATES) {
				break;
			}
			int x = home.getX() + o[0];
			int z = home.getZ() + o[1];
			if (!level.hasChunkAt(x, z)) {
				continue;
			}
			int y = Ground.surfaceY(level, x, z, home.getY());
			if (y == Ground.NONE) {
				continue;
			}
			BlockPos centre = new BlockPos(x, y, z);
			if (isSuitable(c, level, data, centre)) {
				return centre;
			}
		}
		return null;
	}

	/** Checks one candidate centre (the ground block that becomes the water hole). */
	public static boolean isSuitable(CompanionEntity c, ServerLevel level, CampData data, BlockPos centre) {
		if (Math.abs(centre.getY() - c.homePos().getY()) > 16) {
			return false;
		}
		for (Map.Entry<String, CampData.Site> e : data.sites().entrySet()) {
			if (!e.getKey().equals(Structures.FARM_PLOT)
				&& Camp.horizontalDistSqr(e.getValue().origin, centre) < SITE_SPACING * SITE_SPACING) {
				return false;
			}
		}
		Optional<BlockPos> chest = data.chestPos();
		if (chest.isPresent() && Camp.horizontalDistSqr(chest.get(), centre) < 8 * 8) {
			return false;
		}
		int y = centre.getY();
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				int x = centre.getX() + dx;
				int z = centre.getZ() + dz;
				BlockPos ground = new BlockPos(x, y, z);
				if (!level.hasChunkAt(x, z) || !WorldEditGuard.inCamp(c, ground)
					|| Ground.surfaceY(level, x, z, y + 1) != y) {
					return false;
				}
				if (!Crops.isTillable(level.getBlockState(ground))) {
					return false;
				}
				BlockState above = level.getBlockState(ground.above());
				if (!above.isAir() && !(WorldEditGuard.isClearablePlant(above) && !above.is(Blocks.SNOW))) {
					return false;
				}
			}
		}
		BlockState below = level.getBlockState(centre.below());
		if (!below.isFaceSturdy(level, centre.below(), Direction.UP) || !below.getFluidState().isEmpty()) {
			return false; // the water must sit in a sealed hole
		}
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-HALF - 2, -1, -HALF - 2), centre.offset(HALF + 2, 3, HALF + 2))) {
			BlockState s = level.getBlockState(p);
			if ((s.is(ModTags.BUILD_MARKERS) || s.hasBlockEntity()) && !data.isPlacedByFriends(level, p)) {
				return false;
			}
			if (!s.getFluidState().isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
