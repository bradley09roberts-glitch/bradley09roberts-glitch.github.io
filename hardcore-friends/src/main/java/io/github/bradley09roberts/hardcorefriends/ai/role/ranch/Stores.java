package io.github.bradley09roberts.hardcorefriends.ai.role.ranch;

import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/** Small helpers the ranch jobs share for what is carried, held and kept in the supply chest. */
final class Stores {
	private static final double CHEST_REACH = 2.0;

	private Stores() {
	}

	/** Matching items in the supply chest. */
	static int inChest(CompanionEntity c, Predicate<ItemStack> filter) {
		return SupplyChest.of((ServerLevel) c.level()).map(chest -> SupplyChest.count(chest, filter)).orElse(0);
	}

	/** Matching items carried (backpack and hand) or in the supply chest. */
	static int available(CompanionEntity c, Predicate<ItemStack> filter) {
		return carried(c, filter) + inChest(c, filter);
	}

	/** Matching items in the backpack and the hand. */
	static int carried(CompanionEntity c, Predicate<ItemStack> filter) {
		ItemStack hand = c.getMainHandItem();
		return c.backpack().count(filter) + (!hand.isEmpty() && filter.test(hand) ? hand.getCount() : 0);
	}

	/**
	 * One tick of walking to the supply chest. Returns the chest once the friend stands at it, empty while still on
	 * the way; {@code failed[0]} is set when there is no chest or no way there.
	 */
	static Optional<Container> atChest(CompanionEntity c, boolean[] failed) {
		ServerLevel level = (ServerLevel) c.level();
		Optional<BlockPos> pos = Camp.data(level.getServer()).chestPos();
		Optional<Container> chest = SupplyChest.of(level);
		if (pos.isEmpty() || chest.isEmpty()) {
			failed[0] = true;
			return Optional.empty();
		}
		if (!c.actions().walkTo(pos.get(), CHEST_REACH)) {
			failed[0] = c.actions().isStuck();
			return Optional.empty();
		}
		c.getLookControl().setLookAt(Vec3.atCenterOf(pos.get()));
		c.swingArm();
		return chest;
	}

	/** Holds a matching item from the backpack, remembering what was held before. Returns what was held before. */
	static @Nullable Item hold(CompanionEntity c, Predicate<ItemStack> filter) {
		ItemStack hand = c.getMainHandItem();
		if (!hand.isEmpty() && filter.test(hand)) {
			return null;
		}
		Item before = hand.isEmpty() ? null : hand.getItem();
		return c.actions().equip(filter) ? before : null;
	}

	/**
	 * Puts a held item matching {@code filter} back in the backpack and takes up {@code before} again (if it is still
	 * carried). Anything that does not fit is dropped at the friend's feet, as when a backpack overflows.
	 */
	static void stow(CompanionEntity c, Predicate<ItemStack> filter, @Nullable Item before) {
		ItemStack hand = c.getMainHandItem();
		if (hand.isEmpty() || !filter.test(hand)) {
			return;
		}
		c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		ItemStack left = c.backpack().insert(hand);
		if (!left.isEmpty()) {
			c.spawnAtLocation((ServerLevel) c.level(), left);
		}
		if (before != null) {
			c.actions().equip(s -> s.is(before));
		}
	}

	/** Gives an item back to the friend's backpack, or drops it at their feet if there is no room. */
	static void giveBack(CompanionEntity c, ItemStack stack) {
		ItemStack left = c.backpack().insert(stack);
		if (!left.isEmpty()) {
			c.spawnAtLocation((ServerLevel) c.level(), left);
		}
	}
}
