package io.github.bradley09roberts.hardcorefriends.expedition;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The End portal in a stronghold's portal room: twelve frame blocks round a 3×3 opening. Friends only ever set an
 * eye in an empty frame, and only open the portal (fill the opening with portal blocks, as setting the last eye does
 * for a player) inside a ring whose twelve frames all hold an eye.
 */
final class EndPortal {
	private EndPortal() {
	}

	/** True for an end portal frame without an eye. */
	static boolean emptyFrame(BlockState state) {
		return state.is(Blocks.END_PORTAL_FRAME) && !state.getValue(EndPortalFrameBlock.HAS_EYE);
	}

	/** True for an end portal frame with an eye. */
	static boolean fullFrame(BlockState state) {
		return state.is(Blocks.END_PORTAL_FRAME) && state.getValue(EndPortalFrameBlock.HAS_EYE);
	}

	/**
	 * The lowest corner of the 3×3 opening that {@code pos} lies in, when all twelve frames round it hold an eye
	 * (the opening's corners are not part of the ring). Null otherwise. Reads only loaded blocks.
	 */
	static @Nullable BlockPos openingAt(ServerLevel level, BlockPos pos) {
		for (int ox = pos.getX() - 2; ox <= pos.getX(); ox++) {
			for (int oz = pos.getZ() - 2; oz <= pos.getZ(); oz++) {
				BlockPos origin = new BlockPos(ox, pos.getY(), oz);
				if (ringFull(level, origin)) {
					return origin;
				}
			}
		}
		return null;
	}

	/** True when the twelve frames round the opening at {@code origin} (its lowest corner) all hold an eye. */
	static boolean ringFull(ServerLevel level, BlockPos origin) {
		for (int i = 0; i < 3; i++) {
			if (!frameWithEye(level, origin.offset(i, 0, -1)) || !frameWithEye(level, origin.offset(i, 0, 3))
				|| !frameWithEye(level, origin.offset(-1, 0, i)) || !frameWithEye(level, origin.offset(3, 0, i))) {
				return false;
			}
		}
		return true;
	}

	private static boolean frameWithEye(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && fullFrame(level.getBlockState(pos));
	}

	/**
	 * Near a full ring whose opening is not open yet: the first empty place in the opening, or null. Looks round
	 * {@code around} (within 6 blocks, 3 up or down) only for frames that hold an eye.
	 */
	static @Nullable BlockPos unopened(ServerLevel level, BlockPos around) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dy = -3; dy <= 3; dy++) {
			for (int dx = -6; dx <= 6; dx++) {
				for (int dz = -6; dz <= 6; dz++) {
					m.set(around.getX() + dx, around.getY() + dy, around.getZ() + dz);
					if (!level.isLoaded(m) || !fullFrame(level.getBlockState(m))) {
						continue;
					}
					// The opening lies beside one of this frame's sides: try the openings it could belong to.
					for (int ox = m.getX() - 3; ox <= m.getX() + 1; ox++) {
						for (int oz = m.getZ() - 3; oz <= m.getZ() + 1; oz++) {
							BlockPos origin = new BlockPos(ox, m.getY(), oz);
							if (!ringFull(level, origin)) {
								continue;
							}
							for (int i = 0; i < 3; i++) {
								for (int k = 0; k < 3; k++) {
									BlockPos p = origin.offset(i, 0, k);
									if (level.getBlockState(p).isAir()) {
										return p;
									}
								}
							}
							return null; // the full ring's opening is already open (or blocked)
						}
					}
				}
			}
		}
		return null;
	}

	/** The nearest empty frame within 8 blocks of {@code around} (3 up or down), or null. */
	static @Nullable BlockPos nearestEmptyFrame(ServerLevel level, BlockPos around) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dy = -3; dy <= 3; dy++) {
			for (int dx = -8; dx <= 8; dx++) {
				for (int dz = -8; dz <= 8; dz++) {
					m.set(around.getX() + dx, around.getY() + dy, around.getZ() + dz);
					if (level.isLoaded(m) && emptyFrame(level.getBlockState(m))) {
						double d = m.distSqr(around);
						if (d < bestDist) {
							bestDist = d;
							best = m.immutable();
						}
					}
				}
			}
		}
		return best;
	}

	/** True when {@code pos} is inside a stronghold (any of its rooms or corridors). */
	static boolean inStronghold(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && level.structureManager().getStructureWithPieceAt(pos, StructureTags.EYE_OF_ENDER_LOCATED).isValid();
	}
}
