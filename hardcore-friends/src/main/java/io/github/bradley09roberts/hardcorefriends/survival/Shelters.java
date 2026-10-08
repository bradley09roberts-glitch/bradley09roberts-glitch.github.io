package io.github.bradley09roberts.hardcorefriends.survival;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * What the ways of staying alive away from camp share: the blocks a friend carries for them (dirt and cobblestone),
 * and a record, kept with the friend, of the exact blocks they placed for a shelter, a pillar or a water landing, and
 * of the few natural blocks they mean to dig to get under cover. The {@code SURVIVAL} edit rules only let a friend
 * take back their own pieces and dig the blocks they planned to, so these jobs can never touch anything else.
 */
public final class Shelters {
	private static final String PIECES = "survival.pieces";
	private static final String DIGS = "survival.digs";

	private Shelters() {
	}

	/** Dirt, coarse dirt, cobblestone, cobbled deepslate or stone: blocks a friend walls up with. */
	public static boolean isShelterBlock(ItemStack s) {
		return s.is(Items.DIRT) || s.is(Items.COARSE_DIRT) || s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE)
			|| s.is(Items.STONE);
	}

	/** The block state a shelter block item places. */
	public static BlockState stateOf(ItemStack s) {
		return Block.byItem(s.getItem()).defaultBlockState();
	}

	/** True for the blocks shelter blocks place (so the edit rules can check what is being placed). */
	public static boolean isShelterState(BlockState s) {
		return s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.COBBLED_DEEPSLATE)
			|| s.is(Blocks.STONE);
	}

	/** How many shelter blocks the friend carries. */
	public static int blocksCarried(CompanionEntity c) {
		return c.backpack().count(Shelters::isShelterBlock);
	}

	// ----------------------------------------------------------------- pieces

	/** The blocks this friend placed to stay alive and has not taken back yet. */
	public static LongSet pieces(CompanionEntity c) {
		return read(c, PIECES);
	}

	public static void addPiece(CompanionEntity c, BlockPos pos) {
		LongSet set = pieces(c);
		set.add(pos.asLong());
		write(c, PIECES, set);
	}

	public static void removePiece(CompanionEntity c, BlockPos pos) {
		LongSet set = pieces(c);
		if (set.remove(pos.asLong())) {
			write(c, PIECES, set);
		}
	}

	public static boolean isPiece(CompanionEntity c, BlockPos pos) {
		return pieces(c).contains(pos.asLong());
	}

	/** The natural blocks this friend planned to dig to get under cover (at most three). */
	public static boolean mayDig(CompanionEntity c, BlockPos pos) {
		return read(c, DIGS).contains(pos.asLong());
	}

	public static void planDigs(CompanionEntity c, Iterable<BlockPos> digs) {
		LongSet set = new LongOpenHashSet();
		for (BlockPos p : digs) {
			set.add(p.asLong());
		}
		write(c, DIGS, set);
	}

	public static void clearDigs(CompanionEntity c) {
		c.extra().remove(DIGS);
	}

	private static LongSet read(CompanionEntity c, String key) {
		long[] packed = c.extra().getLongArray(key).orElse(new long[0]);
		LongSet set = new LongOpenHashSet(packed.length);
		for (long l : packed) {
			set.add(l);
		}
		return set;
	}

	private static void write(CompanionEntity c, String key, LongSet set) {
		if (set.isEmpty()) {
			c.extra().remove(key);
		} else {
			c.extra().putLongArray(key, set.toLongArray());
		}
	}

	// ----------------------------------------------------------------- places

	/** True if a friend could stand here: firm ground below, room for the body, no fluid. */
	public static boolean standable(ServerLevel level, BlockPos feet) {
		if (!level.isLoaded(feet)) {
			return false;
		}
		BlockState below = level.getBlockState(feet.below());
		return below.isFaceSturdy(level, feet.below(), Direction.UP)
			&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
			&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
			&& level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty();
	}

	/** A spot two or three blocks from {@code pos} where a friend can stand clear of it, nearest to {@code from}. */
	public static @Nullable BlockPos standableNear(ServerLevel level, BlockPos pos, BlockPos from) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) < 2) {
					continue;
				}
				for (int dy = -1; dy <= 1; dy++) {
					m.set(pos.getX() + dx, from.getY() + dy, pos.getZ() + dz);
					if (standable(level, m)) {
						double d = m.distSqr(from);
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

	/** True for a block that closes a side: a full, solid block with no fluid in it. */
	public static boolean closes(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return s.getFluidState().isEmpty() && s.isCollisionShapeFullBlock(level, pos);
	}
}
