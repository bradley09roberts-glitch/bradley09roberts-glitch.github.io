package io.github.bradley09roberts.hardcorefriends.camp.build;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * What one blueprint entry is made of: which carried items may be used (any planks, any log, any wooden door...),
 * how the chosen item turns into a block, and which blocks already in the world count as "this part is built".
 * Using item tags means whatever wood the team gathered is the wood the camp is built from.
 */
public enum MaterialSpec {
	PLANKS(Stock.PLANKS, s -> s.is(BlockTags.PLANKS)),
	LOG(Stock.LOG, s -> s.is(BlockTags.LOGS)),
	SLAB(Stock.SLAB, s -> s.is(BlockTags.WOODEN_SLABS)),
	DOOR(Stock.DOOR, s -> s.is(BlockTags.WOODEN_DOORS) && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER),
	/** The top half of the door below: placed with the same door item, so it needs no second item. */
	DOOR_TOP(null, s -> s.is(BlockTags.WOODEN_DOORS) && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER),
	FENCE(Stock.FENCE, s -> s.is(BlockTags.WOODEN_FENCES)),
	/** Any wooden fence gate, open or shut: the animal pen's way in. */
	FENCE_GATE(Stock.FENCE_GATE, s -> s.is(BlockTags.FENCE_GATES)),
	PRESSURE_PLATE(Stock.PRESSURE_PLATE, s -> s.is(BlockTags.WOODEN_PRESSURE_PLATES)),
	TORCH(Stock.TORCH, s -> s.is(Blocks.TORCH)),
	WALL_TORCH(Stock.TORCH, s -> s.is(Blocks.WALL_TORCH)),
	GLASS_PANE(Stock.GLASS_PANE, s -> s.is(Blocks.GLASS_PANE)),
	LADDER(Stock.LADDER, s -> s.is(Blocks.LADDER)),
	CHEST(Stock.CHEST, s -> s.is(Blocks.CHEST)),
	CRAFTING_TABLE(Stock.CRAFTING_TABLE, s -> s.is(Blocks.CRAFTING_TABLE)),
	FURNACE(Stock.FURNACE, s -> s.is(Blocks.FURNACE)),
	CAMPFIRE(Stock.CAMPFIRE, s -> s.is(Blocks.CAMPFIRE)),
	HOPPER(Stock.HOPPER, s -> s.is(Blocks.HOPPER)),
	COBBLESTONE(Stock.COBBLESTONE, s -> s.is(Blocks.COBBLESTONE)),
	/** Fills a one-block dip under a footprint: dirt first, cobblestone otherwise. Solid ground counts as done. */
	FOUNDATION(Stock.FILL, s -> !s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()),
	LANTERN(Stock.LANTERN, s -> s.is(Blocks.LANTERN)),
	REDSTONE_LAMP(Stock.REDSTONE_LAMP, s -> s.is(Blocks.REDSTONE_LAMP)),
	DAYLIGHT_DETECTOR(Stock.DAYLIGHT_DETECTOR, s -> s.is(Blocks.DAYLIGHT_DETECTOR));

	private final @Nullable Stock stock;
	private final Predicate<BlockState> built;

	MaterialSpec(@Nullable Stock stock, Predicate<BlockState> built) {
		this.stock = stock;
		this.built = built;
	}

	/** The item kind consumed when placing, or null when the entry is placed for free (the top half of a door). */
	public @Nullable Stock stock() {
		return stock;
	}

	/** True if this carried item may be used for the entry. */
	public boolean accepts(ItemStack stack) {
		return stock != null && stock.matches(stack);
	}

	/** True if the block in the world already fulfils this entry. */
	public boolean isBuilt(BlockState state) {
		return built.test(state);
	}

	/** The plain block the chosen item places, before the blueprint's facing and shape tweaks. */
	public BlockState baseState(ItemStack chosen) {
		return switch (this) {
			case WALL_TORCH -> Blocks.WALL_TORCH.defaultBlockState();
			case TORCH -> Blocks.TORCH.defaultBlockState();
			default -> {
				Block block = Block.byItem(chosen.getItem());
				yield block == Blocks.AIR ? Blocks.AIR.defaultBlockState() : block.defaultBlockState();
			}
		};
	}
}
