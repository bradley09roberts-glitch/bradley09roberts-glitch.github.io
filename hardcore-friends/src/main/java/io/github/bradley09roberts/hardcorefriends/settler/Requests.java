package io.github.bradley09roberts.hardcorefriends.settler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;

/**
 * What a stranger asks for before joining: something small and fitting their trade, that a player can manage in the
 * first days (a farmer wants bread and a hoe, a miner a stone pickaxe and torches, a warrior an iron sword). Each trade
 * has a couple of requests; the stranger picks one the first time they talk and keeps to it.
 *
 * <p>The player only has to carry the things: they are taken from the inventory when the stranger joins, exactly as
 * many as were asked for, all or nothing. Tools are taken cheapest first, and anything enchanted or given a name is
 * never taken, so a player's treasured gear is safe. The newcomer keeps what they were given in their backpack.
 */
public final class Requests {
	/**
	 * One thing wanted. {@code one} reads "a hoe" and {@code many} "hoes" (for counts above one: "4 bread", "8 torches").
	 */
	public record Want(String one, String many, int count, Predicate<ItemStack> matches) {
		/** "a hoe", "4 bread", or with a count of its own: "2 bread". */
		public String text(int n) {
			return n == 1 ? one : n + " " + many;
		}
	}

	/** A whole request: everything one stranger wants. */
	public record Request(List<Want> wants) {
		/** "4 bread and a hoe". */
		public String describe() {
			List<String> parts = new ArrayList<>();
			for (Want w : wants) {
				parts.add(w.text(w.count()));
			}
			return join(parts);
		}
	}

	private static final Map<FriendId, List<Request>> BY_TRADE = new EnumMap<>(FriendId.class);

	static {
		Want bread4 = item("a loaf of bread", "bread", 4, Items.BREAD);
		Want torches8 = item("a torch", "torches", 8, Items.TORCH);
		put(FriendId.FERN,
			request(bread4, tool("a hoe", "hoes", ItemTags.HOES)),
			request(item("a bundle of wheat", "wheat", 6, Items.WHEAT), tool("a hoe", "hoes", ItemTags.HOES)));
		put(FriendId.OAK,
			request(tag("a plank", "planks", 16, ItemTags.PLANKS)),
			request(item("a cobblestone", "cobblestone", 16, Items.COBBLESTONE), tag("a plank", "planks", 8, ItemTags.PLANKS)));
		put(FriendId.FLINT,
			request(item("a stone pickaxe", "stone pickaxes", 1, Items.STONE_PICKAXE), torches8),
			request(item("a torch", "torches", 12, Items.TORCH), item("a loaf of bread", "bread", 2, Items.BREAD)));
		put(FriendId.SCOUT,
			request(bread4, item("a torch", "torches", 4, Items.TORCH)),
			request(tag("a bite to eat", "bites to eat", 6, ModTags.RECRUIT_FOOD)));
		put(FriendId.SPARK,
			request(item("a copper ingot", "copper ingots", 4, Items.COPPER_INGOT)),
			request(item("a furnace", "furnaces", 1, Items.FURNACE), item("a lump of coal", "lumps of coal", 4, Items.COAL)));
		put(FriendId.AEGIS,
			request(item("an iron sword", "iron swords", 1, Items.IRON_SWORD)),
			request(item("a shield", "shields", 1, Items.SHIELD)));
		put(FriendId.SAGE,
			request(item("a book", "books", 1, Items.BOOK)),
			request(item("a loaf of bread", "bread", 3, Items.BREAD), item("a torch", "torches", 6, Items.TORCH)));
		put(FriendId.TERRA,
			request(tool("a shovel", "shovels", ItemTags.SHOVELS), tag("a sapling", "saplings", 4, ItemTags.SAPLINGS)),
			request(tag("a sapling", "saplings", 6, ItemTags.SAPLINGS), item("a torch", "torches", 6, Items.TORCH)));
		put(FriendId.ROWAN,
			request(tool("an axe", "axes", ItemTags.AXES), item("a loaf of bread", "bread", 3, Items.BREAD)),
			request(item("a torch", "torches", 6, Items.TORCH), tag("a bite to eat", "bites to eat", 4, ModTags.RECRUIT_FOOD)));
	}

	private Requests() {
	}

	/** How many requests a trade has to choose from. */
	public static int choices(FriendId trade) {
		return BY_TRADE.get(trade).size();
	}

	/** The trade's request with this number (any number works: it wraps round). */
	public static Request get(FriendId trade, int index) {
		List<Request> list = BY_TRADE.get(trade);
		return list.get(Math.floorMod(index, list.size()));
	}

	/**
	 * What is still missing from the player's inventory, as text ("2 more bread and a hoe"), or an empty string when
	 * the player carries everything.
	 */
	public static String missing(Player player, Request request) {
		List<String> parts = new ArrayList<>();
		for (Want w : request.wants()) {
			int have = count(player.getInventory(), w);
			if (have < w.count()) {
				int short_ = w.count() - have;
				parts.add(have > 0 && w.count() > 1 ? short_ + " more " + w.many() : w.text(short_));
			}
		}
		return join(parts);
	}

	/**
	 * Takes exactly what was asked for from the player, all or nothing. Returns the items taken (to go into the
	 * newcomer's backpack), or null if the player does not carry everything. Players who never run out of items
	 * (creative) keep theirs, and the newcomer gets copies.
	 */
	public static @Nullable List<ItemStack> take(Player player, Request request) {
		Inventory inv = player.getInventory();
		for (Want w : request.wants()) {
			if (count(inv, w) < w.count()) {
				return null;
			}
		}
		boolean keep = player.hasInfiniteMaterials();
		List<ItemStack> taken = new ArrayList<>();
		for (Want w : request.wants()) {
			int left = w.count();
			// Cheapest first: a stone hoe before a diamond one, a worn one before a new one.
			List<Integer> slots = new ArrayList<>();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				if (takeable(inv.getItem(i), w)) {
					slots.add(i);
				}
			}
			slots.sort(Comparator.comparingInt((Integer i) -> inv.getItem(i).getMaxDamage())
				.thenComparingInt(i -> -inv.getItem(i).getDamageValue()));
			for (int slot : slots) {
				if (left <= 0) {
					break;
				}
				ItemStack stack = inv.getItem(slot);
				int n = Math.min(left, stack.getCount());
				taken.add(stack.copyWithCount(n));
				if (!keep) {
					stack.shrink(n);
				}
				left -= n;
			}
		}
		inv.setChanged();
		return taken;
	}

	private static int count(Inventory inv, Want w) {
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (takeable(s, w)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** Matches the want, and is nothing the player has enchanted or named. */
	private static boolean takeable(ItemStack s, Want w) {
		return !s.isEmpty() && w.matches().test(s) && !s.isEnchanted() && !s.has(DataComponents.CUSTOM_NAME);
	}

	static String join(List<String> parts) {
		if (parts.isEmpty()) {
			return "";
		}
		if (parts.size() == 1) {
			return parts.get(0);
		}
		return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
	}

	private static Want item(String one, String many, int count, Item item) {
		return new Want(one, many, count, s -> s.is(item));
	}

	private static Want tag(String one, String many, int count, TagKey<Item> tag) {
		return new Want(one, many, count, s -> s.is(tag));
	}

	private static Want tool(String one, String many, TagKey<Item> tag) {
		return new Want(one, many, 1, s -> s.is(tag));
	}

	private static Request request(Want... wants) {
		return new Request(List.of(wants));
	}

	private static void put(FriendId trade, Request... requests) {
		BY_TRADE.put(trade, List.of(requests));
	}
}
