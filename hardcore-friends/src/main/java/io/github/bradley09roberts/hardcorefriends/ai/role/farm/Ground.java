package io.github.bradley09roberts.hardcorefriends.ai.role.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds the ground surface of a column near a known height. Unlike a heightmap lookup this ignores roofs, overhangs
 * and ceilings more than a few blocks up, and it reads at most about twenty blocks per column.
 */
public final class Ground {
	/** Returned when no ground was found in the searched range. */
	public static final int NONE = Integer.MIN_VALUE;
	private static final int ABOVE = 4;
	private static final int BELOW = 12;
	private static final int CLIMB = 8;

	private Ground() {
	}

	/** Blocks you stand on (or water), the same rule the motion-blocking heightmap uses, leaves excepted. */
	public static boolean isGround(BlockState state) {
		return !state.isAir() && (state.is(BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP_NO_LEAVES) || !state.getFluidState().isEmpty());
	}

	/**
	 * The y of the top ground block of column (x, z) near {@code refY} (usually where a friend stands), searching from
	 * four blocks above it down to twelve below, or climbing up to eight more if that start is buried in a hill.
	 * Returns {@link #NONE} when the column is not loaded or has no ground in range.
	 */
	public static int surfaceY(Level level, int x, int z, int refY) {
		if (!level.hasChunkAt(x, z)) {
			return NONE;
		}
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, refY + ABOVE, z);
		if (isGround(level.getBlockState(m))) {
			for (int i = 0; i < CLIMB; i++) {
				m.move(0, 1, 0);
				if (!isGround(level.getBlockState(m))) {
					return m.getY() - 1;
				}
			}
			return NONE;
		}
		for (int i = 0; i < ABOVE + BELOW; i++) {
			m.move(0, -1, 0);
			if (isGround(level.getBlockState(m))) {
				return m.getY();
			}
		}
		return NONE;
	}
}
