package io.github.bradley09roberts.hardcorefriends.ai.role.farm;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import io.github.bradley09roberts.hardcorefriends.companion.Backpack;

/**
 * Crop knowledge shared by Fern's routines: which seed grows which crop, what counts as ripe, which soil can be
 * tilled and where the water is. Everything here only reads the world.
 */
public final class Crops {
	/** Plantable seeds, in the order Fern prefers them when stocks are equal. */
	public static final List<Item> SEEDS = List.of(Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT_SEEDS);
	public static final Predicate<ItemStack> IS_SEED = Crops::isSeed;
	/**
	 * How close Fern walks to a crop before working from where she stands. Stopping short keeps her off the
	 * farmland itself, where a stumble or a jump could trample it.
	 */
	public static final double FIELD_REACH = 2.5;

	/** Most farmland Fern will keep in the camp at each settlement stage. */
	private static final int[] FARMLAND_CAP = {24, 48, 80, 120, 160};

	private Crops() {
	}

	public static boolean isSeed(ItemStack stack) {
		for (Item seed : SEEDS) {
			if (stack.is(seed)) {
				return true;
			}
		}
		return false;
	}

	/** Maximum farmland in camp for a settlement stage. */
	public static int farmlandCap(int stage) {
		return FARMLAND_CAP[Math.clamp(stage, 0, FARMLAND_CAP.length - 1)];
	}

	/** The seed item that replants this crop, or null if it is not one of the four field crops. */
	public static @Nullable Item seedFor(BlockState crop) {
		Block b = crop.getBlock();
		if (b == Blocks.WHEAT) {
			return Items.WHEAT_SEEDS;
		}
		if (b == Blocks.CARROTS) {
			return Items.CARROT;
		}
		if (b == Blocks.POTATOES) {
			return Items.POTATO;
		}
		if (b == Blocks.BEETROOTS) {
			return Items.BEETROOT_SEEDS;
		}
		return null;
	}

	/** The freshly planted (age 0) crop for a seed item, or null if the item is not a seed. */
	public static @Nullable BlockState cropFor(Item seed) {
		if (seed == Items.WHEAT_SEEDS) {
			return Blocks.WHEAT.defaultBlockState();
		}
		if (seed == Items.CARROT) {
			return Blocks.CARROTS.defaultBlockState();
		}
		if (seed == Items.POTATO) {
			return Blocks.POTATOES.defaultBlockState();
		}
		if (seed == Items.BEETROOT_SEEDS) {
			return Blocks.BEETROOTS.defaultBlockState();
		}
		return null;
	}

	/** The seed Fern carries most of, or null when she has none. */
	public static @Nullable Item bestSeed(Backpack backpack) {
		Item best = null;
		int bestCount = 0;
		for (Item seed : SEEDS) {
			int n = backpack.count(seed);
			if (n > bestCount) {
				best = seed;
				bestCount = n;
			}
		}
		return best;
	}

	/** A fully grown field crop, or a melon or pumpkin that grew from an attached stem. */
	public static boolean isRipe(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockState stem = level.getBlockState(pos.relative(d));
				if (stem.getBlock() instanceof AttachedStemBlock && stem.getValue(AttachedStemBlock.FACING) == d.getOpposite()) {
					return true;
				}
			}
		}
		return false;
	}

	/** A field crop that can still grow. */
	public static boolean isGrowing(BlockState state) {
		return state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state);
	}

	/** Grass, dirt or coarse dirt: what a hoe turns into farmland. */
	public static boolean isTillable(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT);
	}

	public static boolean isWater(BlockState state) {
		FluidState fluid = state.getFluidState();
		return !fluid.isEmpty() && fluid.is(FluidTags.WATER);
	}

	/** A still water source block (not flowing water and not a waterlogged block). */
	public static boolean isWaterSource(BlockState state) {
		return state.is(Blocks.WATER) && state.getFluidState().isSource();
	}

	/** True if a stem grows next to this farmland, so its free neighbours are kept for the fruit. */
	public static boolean besideStem(ServerLevel level, BlockPos farmland) {
		BlockPos above = farmland.above();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			Block b = level.getBlockState(above.relative(d)).getBlock();
			if (b instanceof StemBlock || b instanceof AttachedStemBlock) {
				return true;
			}
		}
		return false;
	}

	/** Same rule farmland uses to stay moist: water within 4 blocks sideways, level with it or one higher. */
	public static boolean nearWater(ServerLevel level, BlockPos soil) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				for (int dy = 0; dy <= 1; dy++) {
					m.set(soil.getX() + dx, soil.getY() + dy, soil.getZ() + dz);
					if (isWater(level.getBlockState(m))) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
