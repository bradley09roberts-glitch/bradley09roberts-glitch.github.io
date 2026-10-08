package io.github.bradley09roberts.hardcorefriends.ai.task.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/** Small shared helpers for the common upkeep tasks. */
final class Upkeep {
	/** How close a friend stands to the supply chest to use it. */
	static final double CHEST_REACH = 2.5;

	private Upkeep() {
	}

	/** Position of the linked supply chest if it is in this level and still a usable container. */
	static Optional<BlockPos> chestPos(ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return Optional.empty();
		}
		return data.chestPos().filter(pos -> SupplyChest.at(level, pos).isPresent());
	}

	static int chestCount(ServerLevel level, Predicate<ItemStack> filter) {
		return SupplyChest.of(level).map(chest -> SupplyChest.count(chest, filter)).orElse(0);
	}

	/** Container slots matching a filter, best first by the given ranking. */
	static List<Integer> slotsBest(Container container, Predicate<ItemStack> filter, ToDoubleFunction<ItemStack> rank) {
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && filter.test(stack)) {
				slots.add(i);
			}
		}
		slots.sort((a, b) -> Double.compare(rank.applyAsDouble(container.getItem(b)), rank.applyAsDouble(container.getItem(a))));
		return slots;
	}

	/**
	 * Puts a tool in the friend's hand. Whatever was held before goes into the backpack, or back into the given
	 * container if the backpack is full, or on the ground as a last resort.
	 */
	static void holdInHand(CompanionEntity c, ItemStack tool, @Nullable Container fallback) {
		ItemStack previous = c.getMainHandItem();
		c.setItemSlot(EquipmentSlot.MAINHAND, tool);
		if (previous.isEmpty()) {
			return;
		}
		ItemStack left = c.backpack().insert(previous);
		if (!left.isEmpty() && fallback != null) {
			left = SupplyChest.insert(fallback, left);
		}
		if (!left.isEmpty()) {
			c.spawnAtLocation((ServerLevel) c.level(), left);
		}
	}

	/** Moves the best matching tool from the backpack into the hand. */
	static boolean equipBest(CompanionEntity c, Predicate<ItemStack> filter) {
		ItemStack best = ItemStack.EMPTY;
		double bestRank = -1;
		for (ItemStack s : c.backpack().stacks()) {
			if (filter.test(s) && toolRank(s) > bestRank) {
				bestRank = toolRank(s);
				best = s;
			}
		}
		if (best.isEmpty()) {
			return false;
		}
		ItemStack chosen = best;
		return c.actions().equip(s -> s == chosen);
	}

	/** Ranks tools by tier (maximum durability) first, then by how much durability is left. */
	static double toolRank(ItemStack stack) {
		if (!stack.isDamageableItem()) {
			return 0;
		}
		return stack.getMaxDamage() * 10_000.0 + (stack.getMaxDamage() - stack.getDamageValue());
	}

	/**
	 * A safe spot to stand at about the same height as {@code pos}: solid ground below, room for the body, no
	 * fluid. Looks up to three blocks up and down, so indoor camps work too. Null if there is none.
	 */
	static @Nullable BlockPos standable(ServerLevel level, BlockPos pos) {
		for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
			BlockPos p = pos.above(dy);
			if (level.isLoaded(p) && isStandable(level, p)) {
				return p;
			}
		}
		return null;
	}

	private static boolean isStandable(ServerLevel level, BlockPos p) {
		BlockState below = level.getBlockState(p.below());
		BlockState feet = level.getBlockState(p);
		BlockState head = level.getBlockState(p.above());
		return below.isFaceSturdy(level, p.below(), Direction.UP)
			&& feet.getCollisionShape(level, p).isEmpty() && head.getCollisionShape(level, p.above()).isEmpty()
			&& feet.getFluidState().isEmpty() && head.getFluidState().isEmpty();
	}

	/** A random standable spot within {@code range} blocks (horizontally) of a centre, or null after a few tries. */
	static @Nullable BlockPos randomSpotNear(CompanionEntity c, BlockPos centre, int range) {
		ServerLevel level = (ServerLevel) c.level();
		for (int attempt = 0; attempt < 6; attempt++) {
			int dx = c.getRandom().nextInt(range * 2 + 1) - range;
			int dz = c.getRandom().nextInt(range * 2 + 1) - range;
			BlockPos spot = standable(level, centre.offset(dx, 0, dz));
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	static void playSound(CompanionEntity c, BlockPos pos, SoundEvent sound) {
		c.level().playSound(null, pos, sound, SoundSource.BLOCKS, 0.5F, 0.9F + c.getRandom().nextFloat() * 0.1F);
	}

	/** "16 Oak Log" style description of a stack. */
	static String describe(ItemStack stack, int count) {
		return count + " " + stack.getHoverName().getString();
	}
}
