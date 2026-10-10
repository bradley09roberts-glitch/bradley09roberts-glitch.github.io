package io.github.bradley09roberts.hardcorefriends.market;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.companion.Line;

/**
 * What the station trades make, with the game's own recipes and real ingredients from the supply chest: the baker's
 * bread, cookies, pies, cakes and baked potatoes; the cook's (innkeeper's) cooked meat and fish, stews and soups; the
 * butcher's cooked meat; and, for the smith's shop, plain iron tools and armour when the camp has iron to spare. Each
 * product is made only while the village has fewer than its target (in the workplace's chests and the supply chest
 * together), and its ingredients leave the supply chest only above what the camp keeps back (seed potatoes, sugar cane
 * for Sage's paper, planks for building). Cooking needs heat at the work station (a smoker, furnace or campfire) and
 * fuel, as the game's furnaces do: a piece of coal cooks eight, a plank one and a half.
 */
final class Products {
	/** One ingredient: matching items, how many a craft uses, and how many the supply chest keeps back. */
	record Input(Predicate<ItemStack> match, int count, int keep) {
	}

	/**
	 * One product: what it is, what making it is called ("baking bread"), the item and how many a craft gives, the
	 * ingredients, whether it needs heat (and fuel), an empty container each craft hands back (the cake's three
	 * buckets), how many the village wants, and whether it may be made at all just now.
	 */
	record Product(String doing, Item output, int yield, List<Input> inputs, boolean heat, @Nullable Item returns, int returnsCount,
		int target, Predicate<ServerLevel> allowed, @Nullable Line line) {
	}

	/** Coal or charcoal cooks this many items; planks and logs this many tenths... see {@link #fuelValue}. */
	static final int COAL_SMELTS = 8;

	private static Input in(Item item, int count) {
		return new Input(s -> s.is(item), count, 0);
	}

	private static Input in(Item item, int count, int keep) {
		return new Input(s -> s.is(item), count, keep);
	}

	private static Product make(String doing, Item output, int yield, int target, Line line, Input... inputs) {
		return new Product(doing, output, yield, List.of(inputs), false, null, 0, target, level -> true, line);
	}

	private static Product cook(Item raw, Item cooked, int target, Line line) {
		return new Product("cooking " + name(cooked), cooked, 1, List.of(in(raw, 1)), true, null, 0, target, level -> true, line);
	}

	private static String name(Item item) {
		return new ItemStack(item).getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
	}

	private static final Predicate<ServerLevel> FOOD_TO_SPARE = level -> CampNeeds.need(CampNeeds.Need.FOOD) <= 0.4;

	static final List<Product> BAKER = List.of(
		make("baking bread", Items.BREAD, 1, 32, Line.BAKING, in(Items.WHEAT, 3)),
		new Product("baking potatoes", Items.BAKED_POTATO, 1, List.of(in(Items.POTATO, 1, 16)), true, null, 0, 48, level -> true, Line.BAKING),
		make("baking cookies", Items.COOKIE, 8, 32, Line.BAKING, in(Items.WHEAT, 2), in(Items.COCOA_BEANS, 1)),
		new Product("baking pumpkin pies", Items.PUMPKIN_PIE, 1,
			List.of(in(Items.PUMPKIN, 1), in(Items.SUGAR, 1), new Input(s -> s.is(ItemTags.EGGS), 1, 0)), false, null, 0, 8, level -> true, Line.BAKING),
		new Product("baking a cake", Items.CAKE, 1,
			List.of(in(Items.MILK_BUCKET, 3), in(Items.SUGAR, 2), new Input(s -> s.is(ItemTags.EGGS), 1, 0), in(Items.WHEAT, 3)),
			false, Items.BUCKET, 3, 2, FOOD_TO_SPARE, Line.BAKING),
		make("grinding sugar", Items.SUGAR, 1, 6, Line.TRADE_WORK, in(Items.SUGAR_CANE, 1, 24)));

	private static final List<Product> MEAT = List.of(
		cook(Items.BEEF, Items.COOKED_BEEF, 16, Line.COOKING),
		cook(Items.PORKCHOP, Items.COOKED_PORKCHOP, 16, Line.COOKING),
		cook(Items.MUTTON, Items.COOKED_MUTTON, 16, Line.COOKING),
		cook(Items.CHICKEN, Items.COOKED_CHICKEN, 16, Line.COOKING),
		cook(Items.RABBIT, Items.COOKED_RABBIT, 16, Line.COOKING));

	private static final List<Product> FISH = List.of(
		cook(Items.COD, Items.COOKED_COD, 16, Line.COOKING),
		cook(Items.SALMON, Items.COOKED_SALMON, 16, Line.COOKING));

	static final List<Product> BUTCHER = MEAT;

	static final List<Product> COOK = concat(MEAT, FISH, List.of(
		new Product("baking potatoes", Items.BAKED_POTATO, 1, List.of(in(Items.POTATO, 1, 16)), true, null, 0, 24, level -> true, Line.TRADE_WORK),
		make("making mushroom stew", Items.MUSHROOM_STEW, 1, 4, Line.TRADE_WORK,
			in(Items.BOWL, 1), in(Items.BROWN_MUSHROOM, 1), in(Items.RED_MUSHROOM, 1)),
		make("making beetroot soup", Items.BEETROOT_SOUP, 1, 4, Line.TRADE_WORK, in(Items.BOWL, 1), in(Items.BEETROOT, 6)),
		make("making bowls", Items.BOWL, 4, 4, Line.TRADE_WORK, new Input(s -> s.is(ItemTags.PLANKS), 3, 32))));

	private static final Predicate<ServerLevel> IRON_TO_SPARE = level -> Stores.supplyCount(level, s -> s.is(Items.IRON_INGOT)) >= 40;

	private static Product forge(Item output, int iron, int sticks) {
		List<Input> inputs = new ArrayList<>();
		inputs.add(in(Items.IRON_INGOT, iron, 32));
		if (sticks > 0) {
			inputs.add(in(Items.STICK, sticks));
		}
		return new Product("making " + name(output), output, 1, inputs, false, null, 0, 1, IRON_TO_SPARE, Line.TRADE_WORK);
	}

	static final List<Product> SMITH = List.of(
		forge(Items.IRON_PICKAXE, 3, 2), forge(Items.IRON_SWORD, 2, 1), forge(Items.IRON_AXE, 3, 2), forge(Items.IRON_SHOVEL, 1, 2),
		forge(Items.IRON_HELMET, 5, 0), forge(Items.IRON_BOOTS, 4, 0), forge(Items.SHEARS, 2, 0), forge(Items.BUCKET, 3, 0));

	private Products() {
	}

	/** The products a trade makes at its station (none for the trades whose work is elsewhere). */
	static List<Product> of(Trade trade, @Nullable Workplace w) {
		return switch (trade) {
			case BAKER -> BAKER;
			case INNKEEPER -> COOK;
			case BUTCHER -> BUTCHER;
			// The smith forges for sale only at the smith's shop; at the smithy the camp's own smith work is theirs.
			case BLACKSMITH -> w != null && w.isShop() ? SMITH : List.of();
			default -> List.of();
		};
	}

	/** How many items a fuel cooks, in tenths (coal and charcoal 80, a plank or log 15), or 0 if it is not fuel here. */
	static int fuelValue(ItemStack s) {
		if (s.is(Items.COAL) || s.is(Items.CHARCOAL)) {
			return COAL_SMELTS * 10;
		}
		if (s.is(ItemTags.PLANKS) || s.is(ItemTags.LOGS_THAT_BURN)) {
			return 15;
		}
		return 0;
	}

	/** The sound of a trade's station at work. */
	static SoundEvent workSound(Trade trade) {
		return switch (trade) {
			case BAKER, INNKEEPER, BUTCHER -> SoundEvents.VILLAGER_WORK_BUTCHER;
			case BLACKSMITH -> SoundEvents.VILLAGER_WORK_TOOLSMITH;
			case MASON -> SoundEvents.VILLAGER_WORK_MASON;
			case CARPENTER -> SoundEvents.VILLAGER_WORK_FLETCHER;
			case TAILOR, SHEPHERD -> SoundEvents.VILLAGER_WORK_SHEPHERD;
			case FISHER, FISHMONGER -> SoundEvents.VILLAGER_WORK_FISHERMAN;
			case FARMER -> SoundEvents.VILLAGER_WORK_FARMER;
			case DOCTOR -> SoundEvents.VILLAGER_WORK_CLERIC;
			case TEACHER -> SoundEvents.VILLAGER_WORK_LIBRARIAN;
			default -> SoundEvents.VILLAGER_WORK_ARMORER;
		};
	}

	@SafeVarargs
	private static <T> List<T> concat(List<T>... lists) {
		List<T> all = new ArrayList<>();
		for (List<T> l : lists) {
			all.addAll(l);
		}
		return List.copyOf(all);
	}
}
