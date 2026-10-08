package io.github.bradley09roberts.hardcorefriends.progress;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;

import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * How good a piece of gear is, judged by what it can do rather than by its name, so copper or any later material
 * slots in by itself: a pickaxe's tier is the hardest block it gets drops from (iron ore, diamond ore, obsidian).
 */
public final class Tiers {
	/** Gets drops from iron ore: stone (or copper) and up. */
	public static final int STONE = 1;
	/** Gets drops from diamond ore: iron and up. */
	public static final int IRON = 2;
	/** Gets drops from obsidian: diamond and up. */
	public static final int DIAMOND = 3;

	private Tiers() {
	}

	/** -1 for anything that is not a pickaxe, 0 for wood or gold, then {@link #STONE}, {@link #IRON}, {@link #DIAMOND}. */
	public static int pickaxe(ItemStack s) {
		if (s.isEmpty() || !s.is(ItemTags.PICKAXES)) {
			return -1;
		}
		if (s.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())) {
			return DIAMOND;
		}
		if (s.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState())) {
			return IRON;
		}
		return s.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState()) ? STONE : 0;
	}

	/** The best pickaxe tier a friend holds or carries, -1 for none. */
	public static int bestPickaxe(CompanionEntity c) {
		int best = pickaxe(c.getMainHandItem());
		Backpack bp = c.backpack();
		for (int i = 0; i < Backpack.MAX_SLOTS; i++) {
			best = Math.max(best, pickaxe(bp.get(i)));
		}
		return best;
	}

	/** An iron, diamond or netherite sword. */
	public static boolean ironSword(ItemStack s) {
		return s.is(Items.IRON_SWORD) || s.is(Items.DIAMOND_SWORD) || s.is(Items.NETHERITE_SWORD);
	}

	/** Gear worth enchanting: swords, armour, pickaxes and bows. */
	public static boolean enchantable(ItemStack s) {
		return !s.isEmpty() && (s.is(ItemTags.SWORDS) || s.is(ItemTags.PICKAXES) || s.is(Items.BOW)
			|| s.is(ItemTags.HEAD_ARMOR) || s.is(ItemTags.CHEST_ARMOR) || s.is(ItemTags.LEG_ARMOR) || s.is(ItemTags.FOOT_ARMOR));
	}

	/** True for gear nobody should have touched: named by a player. */
	public static boolean named(ItemStack s) {
		return s.has(DataComponents.CUSTOM_NAME);
	}

	/** A rough rank of how good a piece of gear is: its full durability (diamond above iron above stone...). */
	public static int rank(ItemStack s) {
		return s.isDamageableItem() ? s.getMaxDamage() : 0;
	}

	/** A potion of fire resistance, short or long, drinkable or splash. */
	public static boolean fireResistance(ItemStack s) {
		PotionContents contents = s.get(DataComponents.POTION_CONTENTS);
		return contents != null && (contents.is(Potions.FIRE_RESISTANCE) || contents.is(Potions.LONG_FIRE_RESISTANCE));
	}
}
