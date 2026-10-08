package io.github.bradley09roberts.hardcorefriends.ai.role.guard;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;

/** How Aegis judges weapons, armour and shields. All comparisons use the items' real stats. */
public final class Gear {
	/** The four armour slots, head to feet. */
	public static final EquipmentSlot[] ARMOUR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** A replacement must beat the worn piece by at least this much to be worth swapping. */
	private static final double ARMOUR_MARGIN = 0.02;

	private Gear() {
	}

	/**
	 * Weapon preference, matching the friend's own weapon choice in combat: any sword beats any axe, and within a
	 * kind the sturdier (higher tier) one wins. Zero for anything that is not a weapon.
	 */
	public static int weaponRank(ItemStack s) {
		if (s.isEmpty()) {
			return 0;
		}
		if (s.is(ItemTags.SWORDS)) {
			return 2000 + s.getMaxDamage();
		}
		if (s.is(ItemTags.AXES)) {
			return 1000 + s.getMaxDamage();
		}
		return 0;
	}

	public static boolean isWeapon(ItemStack s) {
		return weaponRank(s) > 0;
	}

	public static boolean isShield(ItemStack s) {
		return s.is(Items.SHIELD);
	}

	/** The armour slot an item is worn in (head, chest, legs or feet), or null if it is not protective armour. */
	public static @Nullable EquipmentSlot armourSlot(ItemStack s) {
		if (s.isEmpty()) {
			return null;
		}
		Equippable equippable = s.get(DataComponents.EQUIPPABLE);
		if (equippable == null) {
			return null;
		}
		EquipmentSlot slot = equippable.slot();
		for (EquipmentSlot armour : ARMOUR_SLOTS) {
			if (armour == slot) {
				return protection(s, slot) > 0 ? slot : null;
			}
		}
		return null;
	}

	/** Armour points plus half the toughness that the item gives when worn in the slot. */
	public static double protection(ItemStack s, EquipmentSlot slot) {
		if (s.isEmpty()) {
			return 0;
		}
		ItemAttributeModifiers mods = s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		return mods.compute(Attributes.ARMOR, 0.0, slot) + 0.5 * mods.compute(Attributes.ARMOR_TOUGHNESS, 0.0, slot);
	}

	/** Protection with remaining durability as a small tie-breaker; -1 for an empty slot. */
	public static double armourScore(ItemStack s, EquipmentSlot slot) {
		if (s.isEmpty()) {
			return -1;
		}
		double wear = s.isDamageableItem() && s.getMaxDamage() > 0
			? (s.getMaxDamage() - s.getDamageValue()) / (double) s.getMaxDamage() : 1.0;
		return protection(s, slot) + 0.1 * wear;
	}

	/** True if the candidate piece is clearly better than what is worn in its slot. */
	public static boolean betterArmour(ItemStack candidate, ItemStack worn, EquipmentSlot slot) {
		return armourSlot(candidate) == slot && armourScore(candidate, slot) > armourScore(worn, slot) + ARMOUR_MARGIN;
	}
}
