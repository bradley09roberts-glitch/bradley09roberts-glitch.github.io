package io.github.bradley09roberts.hardcorefriends.town;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;

/**
 * The camp's job board: what the camp needs right now, as numbered requests a player can fill by bringing the items
 * to the supply chest ({@code /friends jobs}, {@code /friends deliver}). The requests come from what the builder is
 * short of, from other packages' wants ({@link #SUPPLIERS}, such as Sage's plan: "10 iron ingots for the anvil"), and
 * from the camp's everyday needs ({@link CampNeeds}: food, wood, stone, dirt, torches, iron and seeds), most pressing
 * first. Delivering takes only what a request still asks for, never anything enchanted, renamed or worn.
 */
public final class JobBoard {
	/**
	 * One request: what it says ({@code text}, with its amount already in it), how much is wanted, which items fill it,
	 * and how much each item counts for ({@code worth}: a log is four planks' worth of wood, a loaf 30 points of food).
	 */
	public record Request(String text, int amount, Predicate<ItemStack> matches, ToIntFunction<ItemStack> worth) {
		public Request {
			amount = Math.max(0, amount);
		}

		/** A request where every matching item counts once. */
		public static Request of(String text, int amount, Predicate<ItemStack> matches) {
			return new Request(text, amount, matches, s -> 1);
		}
	}

	/**
	 * Extra requests from other packages (the plan's wants), asked for whenever the board is shown or filled. They are
	 * listed after what the builder is short of and before the everyday needs. A supplier that throws is skipped.
	 */
	public static final List<Function<MinecraftServer, List<Request>>> SUPPLIERS = new CopyOnWriteArrayList<>();

	/** Only needs at least this pressing (0..1) go on the board. */
	private static final double SHOWN_FROM = 0.4;
	private static final double LOAF = 30;
	private static final double FOOD_DAYS = 4;
	/** "12 planks" or "12 dirt or cobblestone to level the cabin site", as the builder reports a shortage. */
	private static final Pattern PART = Pattern.compile("^(\\d+) (.+)$");

	private JobBoard() {
	}

	/** Every request on the board now, most pressing first (the camp's needs are worked out afresh first). */
	public static List<Request> requests(MinecraftServer server) {
		CampNeeds.recompute(server);
		List<Request> list = new ArrayList<>(building(server));
		for (Function<MinecraftServer, List<Request>> supplier : SUPPLIERS) {
			try {
				for (Request r : supplier.apply(server)) {
					if (r != null && r.amount() > 0) {
						list.add(r);
					}
				}
			} catch (RuntimeException e) {
				HardcoreFriends.LOGGER.warn("A job board supplier failed", e);
			}
		}
		list.addAll(everyday(server));
		return list;
	}

	/** What the builder (or the friend levelling a site) said they are short of, item by item. */
	private static List<Request> building(MinecraftServer server) {
		List<Request> list = new ArrayList<>();
		String text = CampNeeds.shortageText(server.overworld().getGameTime());
		if (text.isEmpty()) {
			return list;
		}
		for (String part : text.split(", ")) {
			Matcher m = PART.matcher(part.trim());
			if (!m.matches()) {
				continue;
			}
			int count;
			try {
				count = Integer.parseInt(m.group(1));
			} catch (NumberFormatException e) {
				continue;
			}
			String rest = m.group(2);
			// The longest name that fits wins: "glass panes" are panes, not glass. Kinds sharing a name ("cobblestone")
			// all count.
			Predicate<ItemStack> matches = null;
			boolean planks = false;
			int longest = 0;
			for (Stock s : Stock.values()) {
				int fit = Math.max(fit(rest, s.describe(2).substring(2)), fit(rest, s.describe(1).substring(2)));
				if (fit == 0 || fit < longest) {
					continue;
				}
				if (fit > longest) {
					longest = fit;
					matches = null;
					planks = false;
				}
				matches = matches == null ? s::matches : matches.or(s::matches);
				planks |= s == Stock.PLANKS;
			}
			if (matches == null || count <= 0) {
				continue;
			}
			if (planks) {
				// The builder saws planks from logs: a log counts as four.
				Predicate<ItemStack> plankOrLog = matches.or(s -> s.is(ItemTags.LOGS));
				list.add(new Request("Building: " + part.trim() + " (logs count as 4 planks)", count, plankOrLog,
					s -> s.is(ItemTags.LOGS) ? 4 : 1));
			} else {
				list.add(Request.of("Building: " + part.trim(), count, matches));
			}
		}
		return list;
	}

	/** How much of {@code text} the item name covers: all of it, or the start up to a space; 0 when it does not fit. */
	private static int fit(String text, String name) {
		return text.equals(name) || text.startsWith(name + " ") ? name.length() : 0;
	}

	/** The camp's everyday needs, by how pressing they are (the same targets the camp itself works to). */
	private static List<Request> everyday(MinecraftServer server) {
		int stage = Camp.data(server).stage();
		Map<CampNeeds.Need, Request> byNeed = new LinkedHashMap<>();
		double foodTarget = Math.max(1, Companions.all().size()) * Needs.typicalDailyHunger() / LOAF * FOOD_DAYS;
		int loaves = (int) Math.ceil(foodTarget - CampNeeds.stock(CampNeeds.Need.FOOD));
		if (loaves > 0) {
			byNeed.put(CampNeeds.Need.FOOD, new Request("Food: about " + loaves + " loaves' worth (bread, cooked meat or fish, "
				+ "carrots, apples, berries; wheat counts too)", (int) (loaves * LOAF),
				s -> CompanionEntity.isEdible(s) || s.is(Items.WHEAT),
				s -> CompanionEntity.isEdible(s) ? (int) Math.max(1, CompanionEntity.hungerValue(s)) : (int) (LOAF / 3)));
		}
		addShortage(byNeed, CampNeeds.Need.WOOD, 64 + 32 * stage, n -> "Wood: " + n + " planks' worth (logs count as 4)",
			s -> s.is(ItemTags.LOGS) || s.is(ItemTags.PLANKS), s -> s.is(ItemTags.LOGS) ? 4 : 1);
		addShortage(byNeed, CampNeeds.Need.STONE, 48 + 32 * stage, n -> "Stone: " + n + " cobblestone (cobbled deepslate or stone too)",
			s -> s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE) || s.is(Items.STONE), s -> 1);
		addShortage(byNeed, CampNeeds.Need.DIRT, 16 + 8 * stage, n -> "Dirt: " + n + " dirt", s -> s.is(Items.DIRT), s -> 1);
		addShortage(byNeed, CampNeeds.Need.TORCHES, 8 + 4 * stage, n -> "Light: " + n + " torches (coal or charcoal count as 4)",
			s -> s.is(Items.TORCH) || s.is(ItemTags.COALS), s -> s.is(ItemTags.COALS) ? 4 : 1);
		addShortage(byNeed, CampNeeds.Need.ORE, 6 + 6 * stage, n -> "Iron: " + n + " raw iron or iron ingots",
			s -> s.is(Items.RAW_IRON) || s.is(Items.IRON_INGOT), s -> 1);
		addShortage(byNeed, CampNeeds.Need.SEEDS, 8, n -> "Seeds: " + n + " wheat or beetroot seeds, carrots or potatoes",
			s -> s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS) || s.is(Items.CARROT) || s.is(Items.POTATO), s -> 1);
		List<Map.Entry<CampNeeds.Need, Request>> entries = new ArrayList<>(byNeed.entrySet());
		entries.removeIf(e -> CampNeeds.need(e.getKey()) < SHOWN_FROM);
		entries.sort(Comparator.comparingDouble((Map.Entry<CampNeeds.Need, Request> e) -> CampNeeds.need(e.getKey())).reversed());
		List<Request> list = new ArrayList<>();
		for (Map.Entry<CampNeeds.Need, Request> e : entries) {
			list.add(e.getValue());
		}
		return list;
	}

	private static void addShortage(Map<CampNeeds.Need, Request> into, CampNeeds.Need need, int target, Function<Integer, String> text,
			Predicate<ItemStack> matches, ToIntFunction<ItemStack> worth) {
		int missing = target - CampNeeds.stock(need);
		if (missing > 0) {
			into.put(need, new Request(text.apply(missing), missing, matches, worth));
		}
	}

	// ----------------------------------------------------------------- deliver

	/** What a delivery moved: items by name, and how many in all. */
	public record Delivered(Map<String, Integer> items, int total, boolean chestFull) {
		public String describe() {
			List<String> parts = new ArrayList<>();
			items.forEach((name, n) -> parts.add(n + " " + name));
			return String.join(", ", parts);
		}
	}

	/**
	 * Moves what the requests still ask for from the player's inventory (main slots and hotbar, never armour or the
	 * off hand) into the chest. Never takes anything enchanted, renamed or worn. Stops when the chest is full.
	 */
	public static Delivered deliver(ServerPlayer player, Container chest, List<Request> requests) {
		Inventory inv = player.getInventory();
		Map<String, Integer> moved = new LinkedHashMap<>();
		int total = 0;
		boolean full = false;
		for (Request r : requests) {
			int remaining = r.amount();
			for (int slot = 0; slot < Inventory.INVENTORY_SIZE && remaining > 0 && !full; slot++) {
				ItemStack s = inv.getItem(slot);
				if (s.isEmpty() || !deliverable(s) || !r.matches().test(s)) {
					continue;
				}
				int worth = Math.max(1, r.worth().applyAsInt(s));
				int take = Math.min(s.getCount(), (remaining + worth - 1) / worth);
				ItemStack left = SupplyChest.insert(chest, s.copyWithCount(take));
				int put = take - left.getCount();
				if (put > 0) {
					String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
					s.shrink(put);
					remaining -= put * worth;
					total += put;
					moved.merge(name, put, Integer::sum);
				}
				if (!left.isEmpty()) {
					full = true;
				}
			}
			if (full) {
				break;
			}
		}
		if (total > 0) {
			inv.setChanged();
		}
		return new Delivered(moved, total, full);
	}

	/** Plain items only: nothing enchanted, renamed or worn is ever taken from a player. */
	private static boolean deliverable(ItemStack s) {
		return !s.isEnchanted() && !s.has(DataComponents.CUSTOM_NAME) && !s.isDamaged();
	}
}
