package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.companion.Backpack;

/** Helpers for the shared supply chest (any chest, double chest or barrel linked with {@code /friends chest}). */
public final class SupplyChest {
	private SupplyChest() {
	}

	/** The container at a position if it is a loaded chest or barrel. Double chests return both halves. */
	public static Optional<Container> at(ServerLevel level, BlockPos pos) {
		if (pos == null || !level.isLoaded(pos)) {
			return Optional.empty();
		}
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock chest) {
			return Optional.ofNullable(ChestBlock.getContainer(chest, state, level, pos, true));
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof BarrelBlockEntity barrel) {
			return Optional.of(barrel);
		}
		return Optional.empty();
	}

	public static boolean isValidStorage(ServerLevel level, BlockPos pos) {
		return at(level, pos).isPresent();
	}

	/** The linked supply chest, if the camp is in this level and the chest still exists. */
	public static Optional<Container> of(ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return Optional.empty();
		}
		return data.chestPos().flatMap(pos -> at(level, pos));
	}

	public static int count(Container container, Predicate<ItemStack> filter) {
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && filter.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** Inserts a stack into the container. Returns what did not fit. */
	public static ItemStack insert(Container container, ItemStack stack) {
		ItemStack remainder = HopperBlockEntity.addItem(null, container, stack.copy(), null);
		container.setChanged();
		return remainder;
	}

	/** Moves up to {@code max} matching items from the container into the backpack. Returns the amount moved. */
	public static int withdraw(Container container, Backpack backpack, Predicate<ItemStack> filter, int max) {
		int moved = 0;
		for (int i = 0; i < container.getContainerSize() && moved < max; i++) {
			ItemStack stack = container.getItem(i);
			if (stack.isEmpty() || !filter.test(stack)) {
				continue;
			}
			int want = Math.min(max - moved, stack.getCount());
			ItemStack offer = stack.copyWithCount(want);
			ItemStack left = backpack.insert(offer);
			int taken = want - left.getCount();
			if (taken > 0) {
				stack.shrink(taken);
				if (stack.isEmpty()) {
					container.setItem(i, ItemStack.EMPTY);
				}
				moved += taken;
			}
			if (!left.isEmpty()) {
				break; // backpack full
			}
		}
		if (moved > 0) {
			container.setChanged();
		}
		return moved;
	}

	/** Moves up to {@code max} matching items from the backpack into the container. Returns the amount moved. */
	public static int deposit(Backpack backpack, Container container, Predicate<ItemStack> filter, int max) {
		int moved = 0;
		for (ItemStack stack : backpack.stacks()) {
			if (moved >= max || !filter.test(stack)) {
				continue;
			}
			int give = Math.min(max - moved, stack.getCount());
			ItemStack left = insert(container, stack.copyWithCount(give));
			int put = give - left.getCount();
			if (put > 0) {
				stack.shrink(put);
				moved += put;
			}
			if (!left.isEmpty()) {
				break; // chest full
			}
		}
		if (moved > 0) {
			backpack.container().setChanged();
			// Clear emptied stacks
			for (int i = 0; i < Backpack.MAX_SLOTS; i++) {
				if (backpack.get(i).isEmpty()) {
					backpack.container().setItem(i, ItemStack.EMPTY);
				}
			}
		}
		return moved;
	}
}
