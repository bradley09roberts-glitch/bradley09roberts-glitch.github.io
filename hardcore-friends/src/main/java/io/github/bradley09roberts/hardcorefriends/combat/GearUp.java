package io.github.bradley09roberts.hardcorefriends.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.ai.role.guard.Gear;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Wears the best armour and shield a friend carries: a better piece in the backpack (a gift from a player, or the
 * spoils of a trip) goes on, and the worn one goes into the backpack in its place; a shield goes into an empty off
 * hand. Instant (no walk to the chest), in any mode, checked every {@value #CHECK_TICKS} ticks per friend. Fetching
 * gear from the chest is {@link io.github.bradley09roberts.hardcorefriends.ai.role.guard.EquipGearTask}.
 */
public final class GearUp {
	private static final int CHECK_TICKS = 100;

	private GearUp() {
	}

	/** Called every tick for every friend (a {@code CompanionEvents.TICK} hook); works every five seconds. */
	static void tick(CompanionEntity c, ServerLevel level) {
		if ((c.tickCount + c.getId()) % CHECK_TICKS != 3 || !c.isAlive() || !c.isTeamMember() || c.isUsingItem()) {
			return;
		}
		wearFromBackpack(c, level);
	}

	/** Puts on any better armour and a shield from the backpack. Returns true if anything changed. */
	public static boolean wearFromBackpack(CompanionEntity c, ServerLevel level) {
		boolean changed = false;
		Backpack bp = c.backpack();
		for (EquipmentSlot slot : Gear.ARMOUR_SLOTS) {
			ItemStack worn = c.getItemBySlot(slot);
			int best = -1;
			ItemStack bestPiece = worn;
			for (int i = 0; i < Backpack.MAX_SLOTS; i++) {
				ItemStack s = bp.get(i);
				if (Gear.betterArmour(s, bestPiece, slot)) {
					best = i;
					bestPiece = s;
				}
			}
			if (best >= 0) {
				swapIn(c, level, slot, best);
				changed = true;
			}
		}
		if (c.getOffhandItem().isEmpty()) {
			int shield = bp.slotOf(Gear::blocks);
			if (shield >= 0) {
				swapIn(c, level, EquipmentSlot.OFFHAND, shield);
				changed = true;
			}
		}
		return changed;
	}

	/** Swaps the backpack item in {@code packSlot} with what is worn in the equipment slot (the old piece takes its place). */
	private static void swapIn(CompanionEntity c, ServerLevel level, EquipmentSlot slot, int packSlot) {
		ItemStack piece = c.backpack().removeSlot(packSlot);
		if (piece.isEmpty()) {
			return;
		}
		ItemStack old = c.getItemBySlot(slot);
		c.setItemSlot(slot, piece);
		if (!old.isEmpty()) {
			c.backpack().container().setItem(packSlot, old); // the very slot just emptied, so nothing is ever dropped
			c.backpack().container().setChanged();
		}
	}
}
