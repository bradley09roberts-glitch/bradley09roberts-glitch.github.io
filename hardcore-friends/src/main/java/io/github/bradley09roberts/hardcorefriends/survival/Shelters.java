package io.github.bradley09roberts.hardcorefriends.survival;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * What the ways of staying alive away from camp share: the blocks a friend carries for them (dirt and cobblestone),
 * and a record, kept with the friend, of the exact blocks they placed for a shelter, a pillar or a water landing (and
 * what each was), and of the few natural blocks they mean to dig to get under cover or out again. The {@code SURVIVAL}
 * edit rules only let a friend take back their own pieces and dig the blocks they planned to, so these jobs can never
 * touch anything else.
 */
public final class Shelters {
	private static final String PIECES = "survival.pieces";
	/** What each piece was placed as, by position: still known as the friend's own when the camp's record is not. */
	private static final String PIECE_BLOCKS = "survival.piece_blocks";
	private static final String DIGS = "survival.digs";
	private static final String DIRT = BuiltInRegistries.BLOCK.getKey(Blocks.DIRT).toString();

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

	/** Records a block the friend has just placed to stay alive, and what it is. */
	public static void addPiece(CompanionEntity c, BlockPos pos) {
		LongSet set = pieces(c);
		set.add(pos.asLong());
		write(c, PIECES, set);
		CompoundTag blocks = c.extra().getCompoundOrEmpty(PIECE_BLOCKS);
		Block placed = c.level().getBlockState(pos).getBlock();
		blocks.putString(Long.toString(pos.asLong()), BuiltInRegistries.BLOCK.getKey(placed).toString());
		c.extra().put(PIECE_BLOCKS, blocks);
	}

	public static void removePiece(CompanionEntity c, BlockPos pos) {
		LongSet set = pieces(c);
		if (set.remove(pos.asLong())) {
			write(c, PIECES, set);
		}
		CompoundTag blocks = c.extra().getCompoundOrEmpty(PIECE_BLOCKS);
		if (blocks.contains(Long.toString(pos.asLong()))) {
			blocks.remove(Long.toString(pos.asLong()));
			if (blocks.isEmpty()) {
				c.extra().remove(PIECE_BLOCKS);
			}
		}
	}

	public static boolean isPiece(CompanionEntity c, BlockPos pos) {
		return pieces(c).contains(pos.asLong());
	}

	/**
	 * True while one of this friend's pieces is still the block they put there, or plain dirt they put there that has
	 * since grown grass or mycelium (or turned to podzol) by itself overnight. The camp's own record of placed blocks
	 * no longer counts a block that changed, and may be full, so without this a friend could be shut in for good by
	 * their own dirt roof.
	 */
	public static boolean stillOurs(CompanionEntity c, BlockPos pos, BlockState now) {
		String placed = c.extra().getCompoundOrEmpty(PIECE_BLOCKS).getStringOr(Long.toString(pos.asLong()), "");
		if (placed.isEmpty() || !isPiece(c, pos)) {
			return false;
		}
		if (placed.equals(BuiltInRegistries.BLOCK.getKey(now.getBlock()).toString())) {
			return true;
		}
		return placed.equals(DIRT) && (now.is(Blocks.GRASS_BLOCK) || now.is(Blocks.MYCELIUM) || now.is(Blocks.PODZOL));
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

	// ---------------------------------------------------------------- way out

	/**
	 * True when a friend standing at {@code feet} is not shut in: on some side there is a gap two blocks high to walk
	 * (or drop) through, or a block to jump up onto with room above it and above their own head.
	 */
	public static boolean wayOut(ServerLevel level, BlockPos feet) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (canStep(level, feet, d, Set.of())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True when a friend at {@code feet} can take one step towards {@code d}, walking or jumping up a block, with the
	 * blocks in {@code dug} counted as dug away already.
	 */
	public static boolean canStep(ServerLevel level, BlockPos feet, Direction d, Set<BlockPos> dug) {
		BlockPos next = feet.relative(d);
		if (!open(level, next.above(), dug)) {
			return false;
		}
		return open(level, next, dug) || open(level, feet.above(2), dug) && open(level, next.above(2), dug);
	}

	/** True when a friend can pass through this block: nothing to bump into, and no lava. */
	public static boolean open(ServerLevel level, BlockPos pos, Set<BlockPos> dug) {
		if (dug.contains(pos)) {
			return true;
		}
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState s = level.getBlockState(pos);
		return s.getCollisionShape(level, pos).isEmpty() && !s.getFluidState().is(FluidTags.LAVA);
	}
}
