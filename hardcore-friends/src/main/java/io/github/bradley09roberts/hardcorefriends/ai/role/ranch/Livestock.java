package io.github.bradley09roberts.hardcorefriends.ai.role.ranch;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The animals the farmer keeps and hunts, and the numbers she keeps them to. Cows, pigs, sheep and chickens live in
 * the pen; rabbits are only ever hunted. Every kind is lured with its own vanilla food (wheat for cows and sheep,
 * carrots, potatoes or beetroot for pigs, seeds for chickens), which is also what makes two of them fall in love.
 */
public final class Livestock {
	/** A kind of animal the friends know how to keep or hunt. */
	public enum Kind {
		COW("cow", "cows", ItemTags.COW_FOOD, true),
		PIG("pig", "pigs", ItemTags.PIG_FOOD, true),
		SHEEP("sheep", "sheep", ItemTags.SHEEP_FOOD, true),
		CHICKEN("chicken", "chickens", ItemTags.CHICKEN_FOOD, true),
		RABBIT("rabbit", "rabbits", null, false);

		private final String singular;
		private final String plural;
		private final @Nullable TagKey<Item> food;
		private final boolean penned;

		Kind(String singular, String plural, @Nullable TagKey<Item> food, boolean penned) {
			this.singular = singular;
			this.plural = plural;
			this.food = food;
			this.penned = penned;
		}

		/** "cow". */
		public String singular() {
			return singular;
		}

		/** "cows". */
		public String plural() {
			return plural;
		}

		/** True for the kinds kept in the pen (rabbits are only hunted). */
		public boolean penned() {
			return penned;
		}

		/** The food this kind follows and breeds on (its vanilla tempt food). Nothing for rabbits. */
		public Predicate<ItemStack> food() {
			TagKey<Item> tag = food;
			return tag == null ? s -> false : s -> !s.isEmpty() && s.is(tag);
		}
	}

	/** Two of a kind make a breeding pair: the pen is stocked until each kind has a pair. */
	public static final int PAIR = 2;
	/**
	 * Adults of each kind kept for breeding while the pen has room; the rest are butchered. A full pen keeps fewer
	 * ({@link #keepAdults}), never fewer than a {@link #PAIR}.
	 */
	public static final int KEEP_ADULTS = 4;
	/** At most this many of one kind in the pen, young ones included (three pairs). */
	public static final int MAX_PER_KIND = 6;
	/** At most this many animals in the pen altogether. */
	public static final int MAX_TOTAL = 12;

	/** Raw meats and fish, each at the same place as what it cooks into in {@link #COOKED_MEAT}. */
	private static final List<Item> RAW_MEAT = List.of(Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.RABBIT,
		Items.COD, Items.SALMON);
	private static final List<Item> COOKED_MEAT = List.of(Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON,
		Items.COOKED_CHICKEN, Items.COOKED_RABBIT, Items.COOKED_COD, Items.COOKED_SALMON);
	private static final List<Item> OTHER_DROPS = List.of(Items.LEATHER, Items.FEATHER, Items.RABBIT_HIDE, Items.RABBIT_FOOT);

	private Livestock() {
	}

	/** The kind of this animal, or null if it is not one the friends keep or hunt (mooshrooms are left alone). */
	public static @Nullable Kind kind(Entity e) {
		EntityType<?> type = e.getType();
		if (type == EntityTypes.COW) {
			return Kind.COW;
		}
		if (type == EntityTypes.PIG) {
			return Kind.PIG;
		}
		if (type == EntityTypes.SHEEP) {
			return Kind.SHEEP;
		}
		if (type == EntityTypes.CHICKEN) {
			return Kind.CHICKEN;
		}
		if (type == EntityTypes.RABBIT) {
			return Kind.RABBIT;
		}
		return null;
	}

	/** Adults of this kind among the animals (or every one of the kind with {@code adultsOnly} false). */
	public static int count(List<? extends Animal> animals, Kind kind, boolean adultsOnly) {
		int n = 0;
		for (Animal a : animals) {
			if (kind(a) == kind && (!adultsOnly || !a.isBaby())) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Grown animals of a kind the pen keeps for breeding, given the animals in it now: {@value #KEEP_ADULTS} while there
	 * is room for a young one. A full pen ({@value #MAX_TOTAL}) shares its room out among the kinds in it, keeping
	 * {@code MAX_TOTAL / kinds - 1} of each (2 with four kinds, 3 with three, at most {@value #KEEP_ADULTS}), so there is
	 * always a surplus to butcher and the room it frees lets breeding go on. Never fewer than a {@value #PAIR}.
	 */
	public static int keepAdults(List<? extends Animal> penned, Kind kind) {
		if (penned.size() + 1 <= MAX_TOTAL) {
			return Math.max(PAIR, KEEP_ADULTS);
		}
		int kinds = 0;
		for (Kind k : Kind.values()) {
			if (k.penned() && count(penned, k, false) > 0) {
				kinds++;
			}
		}
		return Math.max(PAIR, Math.min(KEEP_ADULTS, MAX_TOTAL / Math.max(1, kinds) - 1));
	}

	/** Raw beef, pork, mutton, chicken, rabbit, cod or salmon: what the campfire turns into a meal. */
	public static boolean isRawMeat(ItemStack s) {
		return !s.isEmpty() && RAW_MEAT.contains(s.getItem());
	}

	/** What a raw meat or fish cooks into ({@link Items#AIR} for anything else). */
	public static Item cookedFrom(Item raw) {
		int i = RAW_MEAT.indexOf(raw);
		return i < 0 ? Items.AIR : COOKED_MEAT.get(i);
	}

	/** Cooked meat or fish. */
	public static boolean isCookedMeat(ItemStack s) {
		return !s.isEmpty() && COOKED_MEAT.contains(s.getItem());
	}

	/** Anything an animal drops when butchered or hunted: meat, leather, wool, feathers, rabbit hide and feet. */
	public static boolean isAnimalDrop(ItemStack s) {
		return isRawMeat(s) || isCookedMeat(s) || OTHER_DROPS.contains(s.getItem()) || s.is(ItemTags.WOOL);
	}
}
