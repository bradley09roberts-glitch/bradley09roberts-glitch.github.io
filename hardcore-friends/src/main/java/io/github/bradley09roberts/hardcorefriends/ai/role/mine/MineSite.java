package io.github.bradley09roberts.hardcorefriends.ai.role.mine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Chooses where Flint's staircase mine starts: in the resource ring (at least camp radius + 6 from the centre, with
 * the whole mine box inside the gathering area), on natural ground near camp height, away from water, lava and
 * anything that looks player-built. Candidates lie on rings around the camp and are tried nearest-to-Flint first.
 */
public final class MineSite {
	/** Minimum horizontal gap between the camp edge and the entrance. */
	public static final int RING_GAP = 6;
	private static final int MAX_HEIGHT_DIFF = 12;
	private static final int RING_STEP = 6;
	private static final int ANGLES = 16;
	private static final int MAX_CHECKED = 40;

	/** An entrance (feet position), the first staircase direction and the bottom level. */
	public record Site(BlockPos entrance, Direction dir, int bottomY) {
	}

	private MineSite() {
	}

	public static @Nullable Site find(CompanionEntity c, long[] oldEntrances) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		BlockPos centre = c.homePos();
		int campRadius = WorldEditGuard.campRadius(c);
		int minR = campRadius + RING_GAP;
		int reach = Math.min(FriendsConfig.get().resourceRadius, Math.max(c.friendId().roam(), RING_GAP + MinePlan.HALF + 6));
		// The box corner furthest from camp must stay inside the gathering area (half-diagonal ≈ 17).
		int maxR = Math.max(minR, campRadius + reach - (int) Math.ceil(MinePlan.HALF * Math.sqrt(2)));
		List<BlockPos> columns = new ArrayList<>();
		for (int r = minR; r <= maxR; r += RING_STEP) {
			for (int a = 0; a < ANGLES; a++) {
				double angle = a * 2 * Math.PI / ANGLES;
				columns.add(new BlockPos(centre.getX() + (int) Math.round(Math.cos(angle) * r), centre.getY(),
					centre.getZ() + (int) Math.round(Math.sin(angle) * r)));
			}
		}
		BlockPos flint = c.blockPosition();
		columns.sort(Comparator.comparingDouble(p -> Camp.horizontalDistSqr(p, flint)));
		int checked = 0;
		for (BlockPos column : columns) {
			if (!level.isLoaded(column) || nearOld(column, oldEntrances)) {
				continue;
			}
			BlockPos feet = surface(level, column, centre.getY());
			if (feet == null || !isNaturalGround(level.getBlockState(feet.below()))) {
				continue;
			}
			int y = feet.getY();
			if (checked++ >= MAX_CHECKED) {
				break;
			}
			if (fluidNear(level, feet, 3) || WorldEditGuard.looksPlayerBuilt(level, feet, 4, data)) {
				continue;
			}
			Direction away = Direction.getApproximateNearest(feet.getX() - centre.getX(), 0, feet.getZ() - centre.getZ());
			if (away.getAxis().isVertical()) {
				away = Direction.EAST;
			}
			if (!firstStepDiggable(level, feet, away)) {
				continue;
			}
			int bottom = Math.min(MinePlan.BOTTOM_Y, y - MinePlan.MIN_DEPTH);
			bottom = Math.max(bottom, level.getMinY() + 6);
			return new Site(feet, away, bottom);
		}
		return null;
	}

	/**
	 * The highest standing spot in a column within {@value #MAX_HEIGHT_DIFF} blocks of camp height: the first
	 * non-passable block (scanning down from the surface) with two passable blocks above it. Barrier blocks, which
	 * only exist in creative and test worlds, are looked through.
	 */
	private static @Nullable BlockPos surface(ServerLevel level, BlockPos column, int campY) {
		int top = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()),
			campY + MAX_HEIGHT_DIFF + 1);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(column.getX(), top + 1, column.getZ());
		boolean headFree = open(level, m);
		for (int y = top; y >= campY - MAX_HEIGHT_DIFF; y--) {
			m.setY(y);
			boolean free = open(level, m);
			if (free && headFree && !open(level, m.below())) {
				return m.immutable();
			}
			headFree = free;
		}
		return null;
	}

	private static boolean open(ServerLevel level, BlockPos pos) {
		return MiningHelper.isPassable(level, pos) || level.getBlockState(pos).is(Blocks.BARRIER);
	}

	/**
	 * Grass, dirt or natural stone that the guard lets Flint dig: somewhere a miner would sensibly start. Never sand
	 * or gravel, which would pour into the stairs.
	 */
	public static boolean isNaturalGround(BlockState state) {
		if (state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) {
			return false;
		}
		return state.is(ModTags.MINEABLE_NATURAL) || state.is(ModTags.EARTH_GATHERABLE);
	}

	/** The first stair step (one block over and one down) is solid natural ground, with open or diggable room above. */
	private static boolean firstStepDiggable(ServerLevel level, BlockPos entrance, Direction dir) {
		BlockPos step = entrance.relative(dir).below();
		if (!isNaturalGround(level.getBlockState(step))) {
			return false;
		}
		for (BlockPos p : new BlockPos[] {step.above(), step.above(2)}) {
			BlockState state = level.getBlockState(p);
			if (!MiningHelper.isPassable(level, p) && !isNaturalGround(state)) {
				return false;
			}
		}
		return true;
	}

	private static boolean nearOld(BlockPos column, long[] oldEntrances) {
		for (long l : oldEntrances) {
			if (Camp.horizontalDistSqr(BlockPos.of(l), column) < 24 * 24) {
				return true;
			}
		}
		return false;
	}

	private static boolean fluidNear(ServerLevel level, BlockPos feet, int r) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = -2; dy <= 2; dy++) {
					m.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
					if (!level.getFluidState(m).isEmpty()) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
