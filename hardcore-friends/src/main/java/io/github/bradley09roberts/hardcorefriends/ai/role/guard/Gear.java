package io.github.bradley09roberts.hardcorefriends.ai.role.guard;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * How every friend judges weapons, armour, shields, bows and emergency healing (Aegis's gear rules, used for the whole
 * team). All comparisons use the items' real stats.
 */
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

	// ------------------------------------------------------------ every friend

	/** The best piece for an armour slot this friend has: worn, or carried in the backpack. Empty if none. */
	public static ItemStack bestOwned(CompanionEntity c, EquipmentSlot slot) {
		ItemStack best = c.getItemBySlot(slot);
		for (ItemStack s : c.backpack().stacks()) {
			if (betterArmour(s, best, slot)) {
				best = s;
			}
		}
		return best;
	}

	/** The rank of the best sword or axe this friend holds or carries ({@link #weaponRank}); 0 if none. */
	public static int bestWeaponRank(CompanionEntity c) {
		int best = weaponRank(c.getMainHandItem());
		for (ItemStack s : c.backpack().stacks()) {
			best = Math.max(best, weaponRank(s));
		}
		return best;
	}

	/** True for a shield (anything that blocks attacks when raised). */
	public static boolean blocks(ItemStack s) {
		return !s.isEmpty() && s.has(DataComponents.BLOCKS_ATTACKS);
	}

	/** True when the friend has a shield in the off hand or the backpack. */
	public static boolean hasShield(CompanionEntity c) {
		return blocks(c.getOffhandItem()) || c.backpack().has(Gear::blocks);
	}

	/** True when the friend holds or carries a bow. */
	public static boolean hasBow(CompanionEntity c) {
		return c.getMainHandItem().is(Items.BOW) || c.backpack().has(s -> s.is(Items.BOW));
	}

	/** Arrows of any kind carried in the backpack. */
	public static int arrows(CompanionEntity c) {
		return c.backpack().count(ItemTags.ARROWS);
	}

	/**
	 * Emergency healing: a golden apple (the enchanted kind too), or a drinkable potion of healing or regeneration.
	 * Never normal food: friends eat that for hunger.
	 */
	public static boolean isHealing(ItemStack s) {
		if (s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE)) {
			return true;
		}
		return potionWith(s, MobEffects.INSTANT_HEALTH) || potionWith(s, MobEffects.REGENERATION);
	}

	/** A drinkable potion of fire resistance. */
	public static boolean isFireResistance(ItemStack s) {
		return potionWith(s, MobEffects.FIRE_RESISTANCE);
	}

	/** A drinkable potion (not a splash or lingering one) one of whose effects is {@code effect}. */
	public static boolean potionWith(ItemStack s, Holder<MobEffect> effect) {
		if (!s.is(Items.POTION)) {
			return false;
		}
		PotionContents contents = s.get(DataComponents.POTION_CONTENTS);
		if (contents == null) {
			return false;
		}
		for (MobEffectInstance e : contents.getAllEffects()) {
			if (e.is(effect)) {
				return true;
			}
		}
		return false;
	}

	/** Golden apples and healing or regeneration potions carried. */
	public static int healingItems(CompanionEntity c) {
		return c.backpack().count(Gear::isHealing);
	}
}
