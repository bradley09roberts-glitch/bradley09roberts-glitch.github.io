package io.github.bradley09roberts.hardcorefriends.town;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.Item;
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
 * first.
 *
 * <p>In a Hardcore world a player's kit is their life, so delivering is careful. It takes only what a request still
 * asks for, and only from the main inventory: never the hotbar, armour or off hand, never tools, weapons, armour,
 * buckets, totems or golden food, never anything enchanted, renamed or worn. Without a number it brings building and
 * camp materials only, never food or the plan's wants (diamonds, ender pearls...), which a player must pick by number.
 * A number means the job that player last saw under it in {@code /friends jobs}, even if the board has changed since.
 * An item that fits several jobs counts towards each of them, and a delivery counts against its job until the board
 * catches up, so the same want is never filled twice over.
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

	/** Where a request on the board comes from, which decides whether {@code /friends deliver} alone may fill it. */
	public enum Kind {
		/** What the builder (or the friend levelling a site) is short of. */
		BUILDING,
		/** Another package's want, such as Sage's plan: only by number. */
		REQUESTED,
		/** The camp's food: only by number. */
		FOOD,
		/** The camp's other everyday needs (wood, stone, dirt, light, iron, seeds). */
		EVERYDAY;

		/** True for the jobs {@code /friends deliver} without a number fills: plain building and camp materials. */
		public boolean withoutNumber() {
			return this == BUILDING || this == EVERYDAY;
		}
	}

	/**
	 * A request on the board: the request (its amount already lowered by any delivery the board has not caught up
	 * with), where it comes from, and {@code source}, the request's own text, which identifies it.
	 */
	public record Job(Request request, Kind kind, String source) {
		/** The job without its amounts ("Building: # planks"), so it is still found after amounts change. */
		public String key() {
			return keyOf(source);
		}
	}

	/** What a player last saw on the board: the jobs' keys and texts in the order shown, and when. */
	private record Shown(List<String> keys, List<String> texts, long at) {
	}

	/** What was delivered against a request (by its own text) and when, until the request itself catches up. */
	private record Recent(int amount, long at) {
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
	/** How long a player's numbers from {@code /friends jobs} stay good (five minutes). */
	private static final long SHOWN_TICKS = 20 * 60 * 5;
	/**
	 * How long a delivery counts against a request that has not changed since (five minutes, as long as the builder's
	 * shortage lasts). A request that changes (its amount, say) has caught up, and is taken as it is.
	 */
	private static final long RECENT_TICKS = 20 * 60 * 5;
	/** Never taken from a player, whatever a job asks: what keeps them alive. */
	private static final Set<Item> NEVER_TAKEN = Set.of(Items.TOTEM_OF_UNDYING, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
		Items.GOLDEN_CARROT, Items.MILK_BUCKET);

	/** Per player: the numbers they were last shown. Not saved. */
	private static final Map<UUID, Shown> SHOWN = new HashMap<>();
	/** Per request text: what was delivered against it lately. Not saved. */
	private static final Map<String, Recent> RECENT = new HashMap<>();

	private JobBoard() {
	}

	/** Every request on the board now, most pressing first (the camp's needs are worked out afresh first). */
	public static List<Request> requests(MinecraftServer server) {
		List<Request> list = new ArrayList<>();
		for (Job job : board(server)) {
			list.add(job.request());
		}
		return list;
	}

	/**
	 * Every job on the board now, most pressing first (the camp's needs are worked out afresh first), each lowered by
	 * what was delivered against it that it has not caught up with yet.
	 */
	public static List<Job> board(MinecraftServer server) {
		CampNeeds.recompute(server);
		long now = server.overworld().getGameTime();
		RECENT.values().removeIf(r -> now - r.at() >= RECENT_TICKS || now < r.at());
		List<Job> list = new ArrayList<>();
		for (Request r : building(server)) {
			add(list, r, Kind.BUILDING);
		}
		for (Function<MinecraftServer, List<Request>> supplier : SUPPLIERS) {
			try {
				for (Request r : supplier.apply(server)) {
					if (r != null && r.amount() > 0) {
						add(list, r, Kind.REQUESTED);
					}
				}
			} catch (RuntimeException e) {
				HardcoreFriends.LOGGER.warn("A job board supplier failed", e);
			}
		}
		for (Map.Entry<CampNeeds.Need, Request> e : everyday(server)) {
			add(list, e.getValue(), e.getKey() == CampNeeds.Need.FOOD ? Kind.FOOD : Kind.EVERYDAY);
		}
		return list;
	}

	/** Adds a request to the board, less what was just delivered against it (and not at all if that covers it). */
	private static void add(List<Job> list, Request r, Kind kind) {
		String source = r.text();
		Recent recent = RECENT.get(source);
		if (recent != null) {
			int left = r.amount() - recent.amount();
			if (left <= 0) {
				return;
			}
			r = new Request(source + " (" + left + " still wanted after the last delivery)", left, r.matches(), r.worth());
		}
		list.add(new Job(r, kind, source));
	}

	/** A job's text without its amounts, so it is still recognised after a delivery changes them. */
	static String keyOf(String text) {
		return text.replaceAll("\\d+", "#");
	}

	// ------------------------------------------------------------ numbers

	/** Remembers the numbers a player has just been shown by {@code /friends jobs}. */
	public static void remember(ServerPlayer player, List<Job> jobs) {
		List<String> keys = new ArrayList<>();
		List<String> texts = new ArrayList<>();
		for (Job job : jobs) {
			keys.add(job.key());
			texts.add(job.request().text());
		}
		long now = player.level().getServer().overworld().getGameTime();
		SHOWN.values().removeIf(s -> now - s.at() >= SHOWN_TICKS || now < s.at());
		SHOWN.put(player.getUUID(), new Shown(keys, texts, now));
	}

	/**
	 * The job a player means by a number: the one they were shown under it by {@code /friends jobs} in the last five
	 * minutes, as it stands on the board now. Empty, with {@code why} filled in, when there is none.
	 */
	public static Optional<Job> numbered(ServerPlayer player, List<Job> board, int number, StringBuilder why) {
		long now = player.level().getServer().overworld().getGameTime();
		Shown shown = SHOWN.get(player.getUUID());
		if (shown == null || now - shown.at() >= SHOWN_TICKS || now < shown.at()) {
			why.append("Look at the jobs first with /friends jobs, then use /friends deliver <number>.");
			return Optional.empty();
		}
		if (number < 1 || number > shown.keys().size()) {
			why.append("There was no job #" + number + " when you last looked. See /friends jobs.");
			return Optional.empty();
		}
		String key = shown.keys().get(number - 1);
		for (Job job : board) {
			if (job.key().equals(key)) {
				return Optional.of(job);
			}
		}
		why.append("Job #" + number + " (" + shown.texts().get(number - 1) + ") is no longer needed. See /friends jobs.");
		return Optional.empty();
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

	/** The camp's everyday needs, by how pressing they are (the same targets the camp itself works to), with their need. */
	private static List<Map.Entry<CampNeeds.Need, Request>> everyday(MinecraftServer server) {
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
		return entries;
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
	 * Moves what the jobs still ask for from the player's main inventory (never the hotbar, armour or off hand) into the
	 * chest, taking only {@link #deliverable} items, and food only when {@code byNumber} (a job the player picked).
	 * An item that fits several of the jobs counts towards each, so overlapping jobs ("iron" for the plan and for the
	 * camp) never take it twice over. What was delivered counts against each job until the board catches up. Stops
	 * when the chest is full.
	 */
	public static Delivered deliver(ServerPlayer player, Container chest, List<Job> jobs, boolean byNumber) {
		Inventory inv = player.getInventory();
		Map<String, Integer> moved = new LinkedHashMap<>();
		int[] remaining = new int[jobs.size()];
		int[] filled = new int[jobs.size()];
		for (int i = 0; i < jobs.size(); i++) {
			remaining[i] = jobs.get(i).request().amount();
		}
		int total = 0;
		boolean full = false;
		for (int i = 0; i < jobs.size() && !full; i++) {
			Request r = jobs.get(i).request();
			for (int slot = Inventory.SELECTION_SIZE; slot < Inventory.INVENTORY_SIZE && remaining[i] > 0 && !full; slot++) {
				ItemStack s = inv.getItem(slot);
				if (s.isEmpty() || !deliverable(s) || (!byNumber && s.has(DataComponents.FOOD)) || !r.matches().test(s)) {
					continue;
				}
				int worth = Math.max(1, r.worth().applyAsInt(s));
				int take = Math.min(s.getCount(), (remaining[i] + worth - 1) / worth);
				ItemStack left = SupplyChest.insert(chest, s.copyWithCount(take));
				int put = take - left.getCount();
				if (put > 0) {
					ItemStack one = s.copyWithCount(1);
					for (int j = 0; j < jobs.size(); j++) {
						Request o = jobs.get(j).request();
						if (o.matches().test(one)) {
							int counts = put * Math.max(1, o.worth().applyAsInt(one));
							remaining[j] -= counts;
							filled[j] += counts;
						}
					}
					String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
					s.shrink(put);
					total += put;
					moved.merge(name, put, Integer::sum);
				}
				if (!left.isEmpty()) {
					full = true;
				}
			}
		}
		if (total > 0) {
			inv.setChanged();
			long now = player.level().getServer().overworld().getGameTime();
			for (int i = 0; i < jobs.size(); i++) {
				if (filled[i] > 0) {
					String source = jobs.get(i).source();
					Recent before = RECENT.get(source);
					RECENT.put(source, new Recent((before != null ? before.amount() : 0) + filled[i], now));
				}
			}
		}
		return new Delivered(moved, total, full);
	}

	/**
	 * What may be taken from a player at all: plain items only (nothing enchanted, renamed or worn), and nothing that
	 * keeps a Hardcore player alive: no tools, weapons, armour, shields or flint and steel (anything that wears out), no
	 * buckets, no totems and no golden food.
	 */
	private static boolean deliverable(ItemStack s) {
		return !s.isEnchanted() && !s.has(DataComponents.CUSTOM_NAME) && !s.isDamaged() && !s.isDamageableItem()
			&& !(s.getItem() instanceof DispensibleContainerItem) && !NEVER_TAKEN.contains(s.getItem());
	}

	/** Forgets the numbers shown and the recent deliveries (a server stopping). */
	static void clear() {
		SHOWN.clear();
		RECENT.clear();
	}
}
