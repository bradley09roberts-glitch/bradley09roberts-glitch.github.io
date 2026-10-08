package io.github.bradley09roberts.hardcorefriends.ai.role.forage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.role.farm.Ground;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.MiningHelper;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Picks a 5×5 quarry site in the gathering ring: at least four blocks beyond the camp edge, flat natural ground
 * (dirt, grass, sand, gravel, or stone when Rowan carries a pickaxe for it, on top) open to the sky above, nothing
 * player-built within three blocks, no water or lava nearby, and at least 12 blocks from reserved building sites and
 * earlier quarries. Sites near Rowan are tried first, and only sites whose surroundings are loaded are judged.
 */
public final class QuarrySiteFinder {
	public static final int SIZE = 5;
	public static final int SPACING = 12;
	private static final int MARKER_MARGIN = 3;
	private static final int SEARCH = 15;
	private static final int STEP = 3;
	private static final int MAX_CANDIDATES = 80;

	private QuarrySiteFinder() {
	}

	/** Columns of a quarry must lie at least this far from the camp centre. */
	static int innerRadius(CampData data) {
		return Camp.radius(data) + 4;
	}

	/** Columns of a quarry must lie no further than this from the camp centre. */
	static int outerRadius(CampData data) {
		return Camp.radius(data) + FriendsConfig.get().resourceRadius;
	}

	/**
	 * The north-west corner of a new quarry, at the height of its top layer, or null if nothing suitable is near.
	 * {@code avoid} holds the corners of earlier quarries; {@code turn} varies the search direction when Rowan is at
	 * the camp centre.
	 */
	public static @Nullable BlockPos find(CompanionEntity c, List<BlockPos> avoid, int turn) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		BlockPos home = c.homePos();
		BlockPos here = c.blockPosition();
		int inner = innerRadius(data);
		int outer = outerRadius(data);
		double dist = Math.sqrt(Camp.horizontalDistSqr(here, home));
		BlockPos anchor;
		if (dist >= inner + 2 && dist <= outer - SIZE) {
			anchor = here;
		} else {
			double angle = dist > 1 ? Math.atan2(here.getZ() - home.getZ(), here.getX() - home.getX()) : turn * (Math.PI / 4);
			int r = inner + 8;
			anchor = new BlockPos(home.getX() + (int) Math.round(Math.cos(angle) * r), here.getY(),
				home.getZ() + (int) Math.round(Math.sin(angle) * r));
		}
		List<int[]> offsets = new ArrayList<>();
		for (int dx = -SEARCH; dx <= SEARCH; dx += STEP) {
			for (int dz = -SEARCH; dz <= SEARCH; dz += STEP) {
				offsets.add(new int[] {dx, dz});
			}
		}
		offsets.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
		int checked = 0;
		for (int[] o : offsets) {
			if (++checked > MAX_CANDIDATES) {
				break;
			}
			BlockPos corner = check(c, level, data, anchor, anchor.getX() + o[0] - SIZE / 2, anchor.getZ() + o[1] - SIZE / 2, avoid);
			if (corner != null && !data.nearDanger(corner, level.getGameTime())) {
				return corner;
			}
		}
		return null;
	}

	private static @Nullable BlockPos check(CompanionEntity c, ServerLevel level, CampData data, BlockPos anchor, int x0, int z0,
			List<BlockPos> avoid) {
		BlockPos home = c.homePos();
		double inner = innerRadius(data);
		double outer = outerRadius(data);
		int top = Ground.NONE;
		for (int dx = 0; dx < SIZE; dx++) {
			for (int dz = 0; dz < SIZE; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				double d = Camp.horizontalDistSqr(home, new BlockPos(x, home.getY(), z));
				if (d < inner * inner || d > outer * outer) {
					return null;
				}
				int y = Ground.surfaceY(level, x, z, anchor.getY());
				if (y == Ground.NONE || (top != Ground.NONE && y != top)) {
					return null; // unloaded, no ground in reach, or not flat
				}
				top = y;
				BlockPos ground = new BlockPos(x, y, z);
				BlockState groundState = level.getBlockState(ground);
				if (!groundState.is(ModTags.EARTH_GATHERABLE) || !MiningHelper.canHarvest(c, groundState)) {
					return null;
				}
				BlockState above = level.getBlockState(ground.above());
				if (!above.isAir() && !WorldEditGuard.isClearablePlant(above)) {
					return null;
				}
			}
		}
		BlockPos middle = new BlockPos(x0 + SIZE / 2, top, z0 + SIZE / 2);
		for (CampData.Site site : data.sites().values()) {
			if (Camp.horizontalDistSqr(site.origin, middle) < SPACING * SPACING) {
				return null;
			}
		}
		for (BlockPos old : avoid) {
			if (Camp.horizontalDistSqr(old.offset(SIZE / 2, 0, SIZE / 2), middle) < SPACING * SPACING) {
				return null;
			}
		}
		int lo = -MARKER_MARGIN;
		int hi = SIZE - 1 + MARKER_MARGIN;
		if (!level.hasChunkAt(x0 + lo, z0 + lo) || !level.hasChunkAt(x0 + hi, z0 + lo) || !level.hasChunkAt(x0 + lo, z0 + hi)
			|| !level.hasChunkAt(x0 + hi, z0 + hi)) {
			return null; // the margin reaches unloaded land: never load chunks just to look
		}
		for (BlockPos p : BlockPos.betweenClosed(x0 - MARKER_MARGIN, top - MARKER_MARGIN, z0 - MARKER_MARGIN,
			x0 + SIZE - 1 + MARKER_MARGIN, top + MARKER_MARGIN, z0 + SIZE - 1 + MARKER_MARGIN)) {
			BlockState s = level.getBlockState(p);
			if ((s.is(ModTags.BUILD_MARKERS) || s.hasBlockEntity()) && !data.isPlacedByFriends(level, p)) {
				return null;
			}
			boolean nearPit = p.getX() >= x0 - 1 && p.getX() <= x0 + SIZE && p.getZ() >= z0 - 1 && p.getZ() <= z0 + SIZE
				&& p.getY() >= top - 2;
			if (nearPit && !s.getFluidState().isEmpty()) {
				return null;
			}
		}
		return new BlockPos(x0, top, z0);
	}
}
