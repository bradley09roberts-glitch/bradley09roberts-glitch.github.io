package io.github.bradley09roberts.hardcorefriends.market;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.architecture.MaterialDemand;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;

/**
 * What each kind of shop sells and buys, and for how much. Prices are fixed (the game's own villager prices where it
 * has them: six loaves for an emerald, a bed for three, twenty wheat bought for one), so they are fair and never drift.
 * Shops sell only what they really have: a shop's own chests, all of them its stock; the camp stall only what the
 * supply chest can spare (food only while the camp has plenty, building stock above what the camp keeps). They buy only
 * what the camp is short of right now, paying from their takings, and never buy back what they are selling that day,
 * and they buy each thing for less than they would sell it, so trading round in circles never pays.
 */
final class Catalogue {
	/** The camp's food is plentiful (its food need at or below this) before any is sold from the supply chest. */
	static final double FOOD_TO_SPARE = 0.25;

	/**
	 * Something a shop sells: the items, how many make one trade ({@code unit}), the price in emeralds, how many the
	 * supply chest keeps back before any is spare, and whether it is food (spare only while the camp has plenty).
	 */
	record Good(String key, Predicate<ItemStack> match, int unit, int price, int keep, boolean food) {
		/** How many of {@code have} in the supply chest the camp can spare for sale. */
		int spare(int have) {
			if (food) {
				return CampNeeds.need(CampNeeds.Need.FOOD) <= FOOD_TO_SPARE ? have / 2 : 0;
			}
			return Math.max(0, have - keep);
		}
	}

	/** Something the village buys while it needs it: {@code unit} of the item for one emerald, at most a few times. */
	record Want(Item item, int unit, int maxTrades, Predicate<ServerLevel> needed) {
	}

	private static Good food(String key, Item item, int unit, int price) {
		return new Good(key, s -> s.is(item), unit, price, 0, true);
	}

	private static Good stock(String key, Predicate<ItemStack> match, int unit, int price, int keep) {
		return new Good(key, match, unit, price, keep, false);
	}

	private static final List<Good> BAKERY = List.of(
		food("bread", Items.BREAD, 6, 1),
		food("cookie", Items.COOKIE, 12, 1),
		food("pumpkin_pie", Items.PUMPKIN_PIE, 4, 1),
		stock("cake", s -> s.is(Items.CAKE), 1, 1, 0),
		food("baked_potato", Items.BAKED_POTATO, 8, 1));

	private static final List<Good> GENERAL = List.of(
		food("bread", Items.BREAD, 6, 1),
		food("baked_potato", Items.BAKED_POTATO, 8, 1),
		food("apple", Items.APPLE, 6, 1),
		stock("torch", s -> s.is(Items.TORCH), 16, 1, 64),
		stock("planks", s -> s.is(ItemTags.PLANKS), 16, 1, 64),
		stock("log", s -> s.is(ItemTags.LOGS), 8, 1, 32),
		stock("cobblestone", s -> s.is(Items.COBBLESTONE), 32, 1, 128),
		stock("stick", s -> s.is(Items.STICK), 32, 1, 64),
		stock("coal", s -> s.is(Items.COAL) || s.is(Items.CHARCOAL), 10, 1, 48));

	private static final List<Good> BUTCHER = List.of(
		food("cooked_porkchop", Items.COOKED_PORKCHOP, 5, 1),
		food("cooked_beef", Items.COOKED_BEEF, 5, 1),
		food("cooked_mutton", Items.COOKED_MUTTON, 5, 1),
		food("cooked_chicken", Items.COOKED_CHICKEN, 8, 1),
		food("cooked_rabbit", Items.COOKED_RABBIT, 5, 1));

	private static final List<Good> FISHMONGER = List.of(
		food("cooked_cod", Items.COOKED_COD, 6, 1),
		food("cooked_salmon", Items.COOKED_SALMON, 6, 1),
		food("cod", Items.COD, 12, 1),
		food("salmon", Items.SALMON, 12, 1));

	private static final List<Good> TAILOR = List.of(
		stock("wool", s -> s.is(ItemTags.WOOL), 1, 1, 32),
		stock("carpet", s -> s.is(ItemTags.WOOL_CARPETS), 4, 1, 16),
		stock("bed", s -> s.is(ItemTags.BEDS), 1, 3, 2));

	/** The smith's goods, sold one at a time, priced by what they are and how worn they are ({@link #toolPrice}). */
	private static final Map<Item, Integer> SMITH_PRICES = Map.ofEntries(
		Map.entry(Items.IRON_SWORD, 4), Map.entry(Items.IRON_PICKAXE, 5), Map.entry(Items.IRON_AXE, 4),
		Map.entry(Items.IRON_SHOVEL, 2), Map.entry(Items.IRON_HOE, 2), Map.entry(Items.IRON_HELMET, 5),
		Map.entry(Items.IRON_CHESTPLATE, 9), Map.entry(Items.IRON_LEGGINGS, 7), Map.entry(Items.IRON_BOOTS, 4),
		Map.entry(Items.SHEARS, 2), Map.entry(Items.SHIELD, 3), Map.entry(Items.BUCKET, 2),
		Map.entry(Items.STONE_SWORD, 1), Map.entry(Items.STONE_PICKAXE, 1), Map.entry(Items.STONE_AXE, 1),
		Map.entry(Items.STONE_SHOVEL, 1));

	private static final List<Want> FOOD_CROPS = List.of(
		new Want(Items.WHEAT, 20, 4, level -> foodShort()),
		new Want(Items.CARROT, 22, 3, level -> foodShort()),
		new Want(Items.POTATO, 26, 3, level -> foodShort()),
		new Want(Items.BEETROOT, 15, 2, level -> foodShort()));
	private static final List<Want> MEAT = List.of(
		new Want(Items.BEEF, 10, 2, level -> foodShort()),
		new Want(Items.PORKCHOP, 7, 2, level -> foodShort()),
		new Want(Items.MUTTON, 7, 2, level -> foodShort()),
		new Want(Items.CHICKEN, 14, 2, level -> foodShort()));
	private static final List<Want> FISH = List.of(
		new Want(Items.COD, 15, 3, level -> foodShort()),
		new Want(Items.SALMON, 13, 3, level -> foodShort()));
	private static final List<Want> MATERIALS = List.of(
		new Want(Items.COAL, 15, 3, level -> need(CampNeeds.Need.TORCHES) >= 0.4),
		new Want(Items.IRON_INGOT, 4, 3, level -> need(CampNeeds.Need.ORE) >= 0.4),
		new Want(Items.OAK_LOG, 16, 2, level -> need(CampNeeds.Need.WOOD) >= 0.5),
		new Want(Items.SPRUCE_LOG, 16, 2, level -> need(CampNeeds.Need.WOOD) >= 0.5),
		new Want(Items.COBBLESTONE, 40, 2, level -> need(CampNeeds.Need.STONE) >= 0.5));
	private static final List<Want> WOOL = List.of(
		new Want(Items.STRING, 14, 3, Catalogue::woolShort),
		new Want(Items.WOOL.pick(DyeColor.WHITE), 18, 2, Catalogue::woolShort));

	private Catalogue() {
	}

	/** What a kind of shop sells (the stall at the camp sells as the general store does). */
	static List<Good> sells(String type) {
		return switch (type) {
			case "bakery" -> BAKERY;
			case "butcher" -> BUTCHER;
			case "fishmonger" -> FISHMONGER;
			case "tailor" -> TAILOR;
			case "smith" -> List.of();
			default -> GENERAL;
		};
	}

	/** True for the smith, whose goods are sold one at a time ({@link #toolPrice}). */
	static boolean sellsTools(String type) {
		return type.equals("smith");
	}

	/** What a kind of shop buys, while the camp needs it. */
	static List<Want> buys(String type) {
		return switch (type) {
			case "bakery" -> List.of(FOOD_CROPS.getFirst());
			case "butcher" -> MEAT;
			case "fishmonger" -> FISH;
			case "tailor" -> WOOL;
			case "smith" -> List.of(MATERIALS.get(0), MATERIALS.get(1));
			default -> concat(FOOD_CROPS, MATERIALS);
		};
	}

	/** The smith's price for one tool or piece of armour: its full price for its wear, at least one emerald; 0 if not sold. */
	static int toolPrice(ItemStack s) {
		Integer base = SMITH_PRICES.get(s.getItem());
		if (base == null || s.isEnchanted() || s.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
			return 0; // only the smith's own plain work is for sale
		}
		if (!s.isDamageableItem() || s.getMaxDamage() <= 0) {
			return base;
		}
		double left = 1.0 - (double) s.getDamageValue() / s.getMaxDamage();
		return Math.max(1, (int) Math.round(base * left));
	}

	/** The smith's goods (for stocking the smith's shop from the chest). */
	static boolean isSmithGood(ItemStack s) {
		return SMITH_PRICES.containsKey(s.getItem());
	}

	private static boolean foodShort() {
		return need(CampNeeds.Need.FOOD) >= 0.4;
	}

	private static double need(CampNeeds.Need need) {
		return CampNeeds.need(need);
	}

	private static boolean woolShort(ServerLevel level) {
		return MaterialDemand.missing(level.getServer(), Stock.WOOL) > 0;
	}

	private static <T> List<T> concat(List<T> a, List<T> b) {
		java.util.ArrayList<T> all = new java.util.ArrayList<>(a);
		all.addAll(b);
		return List.copyOf(all);
	}
}
