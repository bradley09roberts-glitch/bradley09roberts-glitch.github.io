package io.github.bradley09roberts.hardcorefriends.progress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * Sage's plan to beat the game: the steps in {@link Milestone}, worked through in order. Every
 * {@value #INTERVAL} ticks the step in hand is checked against what the camp owns ({@link CampStock}); when it is met
 * Sage (or whoever plans in her place) announces it, the bond grows, and the next step becomes the goal. The plan also
 * says what the camp wants for the step in hand and the ones just after ({@link #wanted}): the plan's jobs score from
 * that, and the camp's ore need rises while iron is wanted, so Flint mines deeper when diamonds are wanted, mines more
 * iron in the iron age and the farm grows sugar cane when books are.
 *
 * <p>Other packages use this as the plan's public face: {@link #current}, {@link #complete} (the expedition work
 * finishes the stronghold, End portal and dragon steps), {@link #wants}/{@link #wanted}, {@link #stockTargets} and
 * {@link #DIAMOND_RESERVE}. With {@code FriendsConfig.progressionGoals} off nothing is tracked, announced or wanted.
 */
public final class ProgressPlan {
	/** Diamonds the camp keeps back for a diamond pickaxe (3), an enchanting table (2) and spares (2). Others spend only above it. */
	public static final int DIAMOND_RESERVE = 7;
	public static final int IRON_STOCK = 10;
	public static final int PORTAL_OBSIDIAN = 10;
	public static final int TABLE_OBSIDIAN = 4;
	public static final int BLAZE_RODS = 7;
	public static final int ENDER_PEARLS = 12;
	public static final int EYES = 12;
	/** Eyes wanted in all: twelve and a few spare, since some break on the way. */
	public static final int EYES_WANTED = 15;
	/** Books for the library: three for each of 15 bookshelves and one for the table. */
	public static final int LIBRARY_BOOKS = 46;
	public static final int LAPIS_WANTED = 9;
	/** Water bottles and potions the brewer likes to have to hand. */
	public static final int BOTTLES_WANTED = 6;
	/** Unity for each step of the plan reached. */
	public static final int STEP_UNITY = 40;
	public static final String UNITY_CATEGORY = "plan";

	private static final int INTERVAL = 200;
	private static final int OFFSET = 31;

	/** What the plan wants more of right now, worked out at each check. Read by scoring; never null. */
	private static Map<Item, Integer> wanted = Map.of();
	private static Map<Item, Integer> targets = Map.of();

	private ProgressPlan() {
	}

	public static boolean enabled() {
		return FriendsConfig.get().progressionGoals;
	}

	// ------------------------------------------------------------ public API

	/** The step the camp is working on, or null when the plan is finished (or switched off). */
	public static @Nullable Milestone current(MinecraftServer server) {
		if (!enabled()) {
			return null;
		}
		ProgressData data = ProgressData.get(server);
		for (Milestone m : Milestone.values()) {
			if (!data.isDone(m)) {
				return m;
			}
		}
		return null;
	}

	public static boolean isDone(MinecraftServer server, Milestone m) {
		return ProgressData.get(server).isDone(m);
	}

	/** True when this step is done or is the one in hand: the plan has got that far. */
	public static boolean reached(MinecraftServer server, Milestone m) {
		Milestone now = current(server);
		return enabled() && (now == null || now.ordinal() >= m.ordinal());
	}

	/**
	 * Marks a step done, announces it and rewards the team (once; doing it again changes nothing). The expedition work
	 * calls this for the stronghold, the End portal and the dragon; the plan calls it itself for the rest.
	 */
	public static void complete(MinecraftServer server, Milestone m) {
		ProgressData data = ProgressData.get(server);
		ServerLevel overworld = server.overworld();
		if (!data.markDone(m, Camp.day(overworld))) {
			return;
		}
		Camp.data(server).addStat("plan_steps", 1);
		if (!enabled()) {
			return;
		}
		Unity.add(overworld, UNITY_CATEGORY, STEP_UNITY, 0);
		CompanionEntity planner = planner();
		if (planner != null) {
			Speech.say(planner, Line.GOAL_REACHED, m.title());
		}
		Milestone next = current(server);
		String after = next == null ? " That was the last step: the plan is complete!" : " Next: " + next.goal() + ".";
		Speech.announce(server, Component.literal("Sage's plan: " + m.title() + " reached!" + after).withStyle(ChatFormatting.GOLD));
		data.setAnnounced(next);
		if (next != null && planner != null) {
			Speech.say(planner, Line.GOAL_NEW, next.goal());
		}
	}

	/** True if the plan wants more of this item now. */
	public static boolean wants(MinecraftServer server, Item item) {
		return wanted(server, item) > 0;
	}

	/** How many more of this item the plan wants now (0 when it has enough or does not need it). */
	public static int wanted(MinecraftServer server, Item item) {
		return wanted.getOrDefault(item, 0);
	}

	/** How many of each item the plan wants the camp to own in all, for the step in hand. */
	public static Map<Item, Integer> stockTargets(MinecraftServer server) {
		return Collections.unmodifiableMap(targets);
	}

	/** A score multiplier for a job that gets the plan what it wants: 1 + the share still missing (1 to 2). */
	public static double weight(MinecraftServer server, Item item) {
		int target = targets.getOrDefault(item, 0);
		int missing = wanted(server, item);
		return target <= 0 ? 1.0 : 1.0 + Math.clamp(missing / (double) target, 0.0, 1.0);
	}

	/**
	 * Diamonds the camp keeps back: 3 for a diamond pickaxe until one exists, 2 for the enchanting table until it is
	 * built, and 2 spare. Never more than {@link #DIAMOND_RESERVE}.
	 */
	public static int diamondReserve(MinecraftServer server) {
		CampStock.Snapshot s = CampStock.get(server);
		int reserve = 2;
		if (s.diamondPickaxes() == 0) {
			reserve += 3;
		}
		if (!Camp.data(server).isCompleted(Structures.LIBRARY) && s.total(Items.ENCHANTING_TABLE) == 0) {
			reserve += 2;
		}
		return Math.min(DIAMOND_RESERVE, reserve);
	}

	/** Forgets what was wanted (a world closing). */
	public static void clear() {
		wanted = Map.of();
		targets = Map.of();
	}

	// ------------------------------------------------------------------ check

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL == OFFSET) {
			evaluate(server);
		}
	}

	/** Checks the step in hand (and any already met after it), announces a new goal, and works out what is wanted. */
	public static void evaluate(MinecraftServer server) {
		CampData camp = Camp.data(server);
		if (!enabled() || camp.campPos().isEmpty()) {
			clear();
			return;
		}
		CampStock.Snapshot stock = CampStock.get(server);
		ProgressData data = ProgressData.get(server);
		for (int guard = 0; guard < 3; guard++) {
			Milestone m = current(server);
			if (m == null || m.byExpedition() || !met(server, m, stock)) {
				break;
			}
			complete(server, m);
		}
		Milestone now = current(server);
		if (now != null && data.announced() != now) {
			CompanionEntity planner = planner();
			if (planner != null && Speech.say(planner, Line.GOAL_NEW, now.goal())) {
				data.setAnnounced(now);
			}
		}
		workOutWants(server, stock);
	}

	/** True when the camp has what this step asks for. */
	static boolean met(MinecraftServer server, Milestone m, CampStock.Snapshot s) {
		return switch (m) {
			case SETTLED -> Camp.data(server).stage() >= 3;
			case IRON_AGE -> ironPickaxesNeeded(s) == 0 && ironSwordsNeeded(s) == 0 && s.total(Items.IRON_INGOT) >= IRON_STOCK;
			case DIAMONDS -> s.diamondPickaxes() >= 1;
			case ENCHANTING -> enchantingPower(server) >= Stations.MAX_SHELVES;
			case NETHER_READY -> s.total(Items.OBSIDIAN) >= PORTAL_OBSIDIAN && s.total(Items.FLINT_AND_STEEL) >= 1;
			case BLAZE_RODS -> blazeRods(s) >= BLAZE_RODS;
			case ENDER_PEARLS -> s.total(Items.ENDER_PEARL) + s.total(Items.ENDER_EYE) >= ENDER_PEARLS;
			case EYES_OF_ENDER -> s.total(Items.ENDER_EYE) >= EYES;
			default -> false;
		};
	}

	/** Iron pickaxes still missing: one for each miner on the team (or one for the camp with no miner about). */
	static int ironPickaxesNeeded(CampStock.Snapshot s) {
		return s.miners() > 0 ? s.miners() - s.minersWithIron() : Math.max(0, 1 - s.ironPickaxes());
	}

	/** Iron swords still missing: one for each warrior on the team, and at least one for the camp. */
	static int ironSwordsNeeded(CampStock.Snapshot s) {
		return Math.max(0, Math.max(1, s.warriors()) - s.ironSwords());
	}

	/** Blaze rods, counting powder already ground from them (two to a rod). */
	static int blazeRods(CampStock.Snapshot s) {
		return s.total(Items.BLAZE_ROD) + s.total(Items.BLAZE_POWDER) / 2;
	}

	/** The bookshelves powering the camp's best enchanting table (0 without one). */
	static int enchantingPower(MinecraftServer server) {
		ServerLevel level = campLevel(server);
		if (level == null) {
			return 0;
		}
		BlockPos table = Stations.enchantingTable(level);
		return table == null ? 0 : Stations.power(level, table);
	}

	/** The camp's level, if a camp is set and its level exists. */
	public static @Nullable ServerLevel campLevel(MinecraftServer server) {
		CampData data = Camp.data(server);
		for (ServerLevel level : server.getAllLevels()) {
			if (Camp.isCampLevel(level, data)) {
				return level;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ wants

	private static void workOutWants(MinecraftServer server, CampStock.Snapshot s) {
		Map<Item, Integer> target = new LinkedHashMap<>();
		Milestone now = current(server);
		int step = now == null ? Milestone.values().length : now.ordinal();
		CampData camp = Camp.data(server);
		boolean libraryDone = camp.isCompleted(Structures.LIBRARY) || enchantingPower(server) >= Stations.MAX_SHELVES;
		ServerLevel level = campLevel(server);
		boolean tableMade = libraryDone || s.total(Items.ENCHANTING_TABLE) > 0 || level != null && Stations.enchantingTable(level) != null;
		if (now == Milestone.IRON_AGE) {
			target.put(Items.IRON_INGOT, IRON_STOCK + 3 * ironPickaxesNeeded(s) + 2 * ironSwordsNeeded(s));
		}
		if (step >= Milestone.DIAMONDS.ordinal()) {
			target.put(Items.DIAMOND, diamondReserve(server));
			if (!libraryDone) {
				int books = LIBRARY_BOOKS - 3 * s.total(Items.BOOKSHELF) - 3 * libraryShelvesBuilt(server) - (tableMade ? 1 : 0);
				if (books > 0) {
					target.put(Items.BOOK, books);
					int stillBooks = Math.max(0, books - s.total(Items.BOOK));
					target.put(Items.PAPER, 3 * stillBooks);
					target.put(Items.SUGAR_CANE, Math.max(0, 3 * stillBooks - s.total(Items.PAPER)));
					target.put(Items.LEATHER, stillBooks);
				}
			}
			int obsidian = (tableMade ? 0 : TABLE_OBSIDIAN) + (step <= Milestone.NETHER_READY.ordinal() ? PORTAL_OBSIDIAN : 0);
			if (obsidian > 0) {
				target.put(Items.OBSIDIAN, obsidian);
				if (s.total(Items.WATER_BUCKET) == 0) {
					target.put(Items.BUCKET, 1);
				}
			}
		}
		if (step >= Milestone.ENCHANTING.ordinal() || tableMade) {
			target.put(Items.LAPIS_LAZULI, LAPIS_WANTED);
		}
		if (step >= Milestone.ENCHANTING.ordinal() && step <= Milestone.NETHER_READY.ordinal()) {
			target.put(Items.FLINT_AND_STEEL, 1);
			if (s.total(Items.FLINT_AND_STEEL) == 0) {
				target.put(Items.FLINT, 1);
			}
		}
		if (step >= Milestone.NETHER_READY.ordinal() && (s.total(Items.BLAZE_ROD) > 0 || s.total(Items.BREWING_STAND) > 0
			|| camp.isCompleted(Structures.BREWING_STAND))) {
			target.put(Items.GLASS_BOTTLE, BOTTLES_WANTED);
			target.put(Items.NETHER_WART, 3);
		}
		if (now == Milestone.BLAZE_RODS) {
			target.put(Items.BLAZE_ROD, BLAZE_RODS);
		}
		if (now == Milestone.ENDER_PEARLS) {
			target.put(Items.ENDER_PEARL, ENDER_PEARLS);
		}
		if (now == Milestone.EYES_OF_ENDER || step > Milestone.EYES_OF_ENDER.ordinal() && step <= Milestone.END_PORTAL.ordinal()) {
			target.put(Items.ENDER_EYE, EYES_WANTED);
		}
		Map<Item, Integer> more = new LinkedHashMap<>();
		target.forEach((item, want) -> {
			int have = s.total(item);
			if (item == Items.BUCKET) {
				have += s.total(Items.WATER_BUCKET);
			}
			if (item == Items.GLASS_BOTTLE) {
				have += s.total(Items.POTION);
			}
			if (want > have) {
				more.put(item, want - have);
			}
		});
		targets = target;
		wanted = more;
	}

	/** Bookshelves already standing in the library (0 without a library table). */
	private static int libraryShelvesBuilt(MinecraftServer server) {
		return enchantingPower(server);
	}

	/** For the camp's needs: the ore need rises while the plan wants iron (the iron age). */
	public static Map<CampNeeds.Need, Double> extraNeeds(MinecraftServer server) {
		if (!enabled()) {
			return Map.of();
		}
		Map<CampNeeds.Need, Double> map = new EnumMap<>(CampNeeds.Need.class);
		if (wants(server, Items.IRON_INGOT)) {
			map.put(CampNeeds.Need.ORE, 0.8); // diamonds are the deep mine's work, which the plan steers itself
		}
		return map;
	}

	// ----------------------------------------------------------------- people

	/**
	 * Who speaks for the plan: Sage when she is about, otherwise the friend first in the roster (whoever plans in her
	 * place). Null with nobody loaded.
	 */
	public static @Nullable CompanionEntity planner() {
		Optional<CompanionEntity> sage = Companions.find(FriendId.SAGE);
		if (sage.isPresent() && sage.get().isAlive()) {
			return sage.get();
		}
		CompanionEntity best = null;
		for (CompanionEntity c : Companions.all()) {
			if (c.isAlive() && (best == null || c.rosterIndex() < best.rosterIndex())) {
				best = c;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- display

	/** The plan as lines for {@code /friends goals}: each step, the checklist of the one in hand, and what is wanted. */
	public static List<Component> describe(MinecraftServer server) {
		List<Component> lines = new ArrayList<>();
		if (!enabled()) {
			lines.add(Component.literal("Sage's plan is switched off (progressionGoals in the config).").withStyle(ChatFormatting.GRAY));
			return lines;
		}
		CampData camp = Camp.data(server);
		if (camp.campPos().isEmpty()) {
			lines.add(Component.literal("Set up a camp first (/friends camp set): Sage's plan starts from there.").withStyle(ChatFormatting.GRAY));
			return lines;
		}
		CampStock.Snapshot s = CampStock.get(server);
		ProgressData data = ProgressData.get(server);
		int xp = data.experience();
		int level = Experience.levelOf(xp);
		lines.add(Component.literal("Sage's plan to beat the game (camp experience: level " + level + ", " + xp + " points)")
			.withStyle(ChatFormatting.GOLD));
		Milestone now = current(server);
		for (Milestone m : Milestone.values()) {
			if (data.isDone(m)) {
				lines.add(Component.literal("  done  ").withStyle(ChatFormatting.GREEN)
					.append(Component.literal(m.title() + " (day " + data.doneOn(m) + ")").withStyle(ChatFormatting.GRAY)));
			} else if (m == now) {
				lines.add(Component.literal("  now   ").withStyle(ChatFormatting.YELLOW)
					.append(Component.literal(m.title() + ": " + m.goal()).withStyle(ChatFormatting.WHITE)));
				for (String check : checklist(server, m, s)) {
					lines.add(Component.literal("          " + check).withStyle(ChatFormatting.WHITE));
				}
			} else {
				lines.add(Component.literal("  later ").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.literal(m.title()).withStyle(ChatFormatting.GRAY)));
			}
		}
		if (!wanted.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			wanted.forEach((item, n) -> {
				if (!sb.isEmpty()) {
					sb.append(", ");
				}
				sb.append(n).append(' ').append(item.getDefaultInstance().getHoverName().getString().toLowerCase(java.util.Locale.ROOT));
			});
			lines.add(Component.literal("Wanted for the plan: " + sb).withStyle(ChatFormatting.AQUA));
		}
		return lines;
	}

	/** What a step needs and what the camp has towards it, one "have/need" item a line. */
	static List<String> checklist(MinecraftServer server, Milestone m, CampStock.Snapshot s) {
		List<String> list = new ArrayList<>();
		switch (m) {
			case SETTLED -> list.add(tick(Camp.data(server).stage() >= 3) + "camp stage: " + Camp.stageName(Camp.data(server).stage())
				+ " (a Village is needed)");
			case IRON_AGE -> {
				int miners = Math.max(1, s.miners());
				list.add(tick(ironPickaxesNeeded(s) == 0) + "iron pickaxes for the miners: "
					+ (s.miners() > 0 ? s.minersWithIron() : Math.min(1, s.ironPickaxes())) + "/" + miners);
				int swords = Math.max(1, s.warriors());
				list.add(tick(ironSwordsNeeded(s) == 0) + "iron swords for the fighters: " + Math.min(swords, s.ironSwords()) + "/" + swords);
				list.add(tick(s.total(Items.IRON_INGOT) >= IRON_STOCK) + "iron ingots in stock: " + s.total(Items.IRON_INGOT) + "/" + IRON_STOCK);
			}
			case DIAMONDS -> {
				list.add(tick(s.diamondPickaxes() >= 1) + "diamond pickaxe: " + Math.min(1, s.diamondPickaxes()) + "/1");
				list.add(tick(s.total(Items.DIAMOND) >= 3 || s.diamondPickaxes() >= 1) + "diamonds: " + s.total(Items.DIAMOND)
					+ "/3 (Flint mines deep, at diamond level, once he has an iron pickaxe)");
			}
			case ENCHANTING -> {
				int power = enchantingPower(server);
				boolean table = campLevel(server) != null && Stations.enchantingTable(campLevel(server)) != null;
				list.add(tick(table) + "enchanting table: " + (table ? "built" : "not yet (book, 2 diamonds, 4 obsidian)"));
				list.add(tick(power >= Stations.MAX_SHELVES) + "bookshelves round it: " + power + "/" + Stations.MAX_SHELVES);
				list.add("   books: " + s.total(Items.BOOK) + ", paper: " + s.total(Items.PAPER) + ", sugar cane: "
					+ s.total(Items.SUGAR_CANE) + ", leather: " + s.total(Items.LEATHER) + ", obsidian: " + s.total(Items.OBSIDIAN));
			}
			case NETHER_READY -> {
				list.add(tick(s.total(Items.OBSIDIAN) >= PORTAL_OBSIDIAN) + "obsidian: " + s.total(Items.OBSIDIAN) + "/" + PORTAL_OBSIDIAN);
				list.add(tick(s.total(Items.FLINT_AND_STEEL) >= 1) + "flint and steel: " + Math.min(1, s.total(Items.FLINT_AND_STEEL)) + "/1");
				list.add("   fire resistance potions (if we can brew them): " + s.fireResistance());
			}
			case BLAZE_RODS -> list.add(tick(blazeRods(s) >= BLAZE_RODS) + "blaze rods: " + blazeRods(s) + "/" + BLAZE_RODS);
			case ENDER_PEARLS -> list.add(tick(s.total(Items.ENDER_PEARL) + s.total(Items.ENDER_EYE) >= ENDER_PEARLS)
				+ "ender pearls: " + (s.total(Items.ENDER_PEARL) + s.total(Items.ENDER_EYE)) + "/" + ENDER_PEARLS);
			case EYES_OF_ENDER -> list.add(tick(s.total(Items.ENDER_EYE) >= EYES) + "eyes of ender: " + s.total(Items.ENDER_EYE) + "/"
				+ EYES + " (" + EYES_WANTED + " with spares)");
			case STRONGHOLD -> list.add("[ ] follow the eyes of ender to the stronghold");
			case END_PORTAL -> list.add("[ ] fill the End portal frame with eyes of ender");
			case DRAGON -> list.add("[ ] defeat the ender dragon");
		}
		return list;
	}

	private static String tick(boolean ok) {
		return ok ? "[x] " : "[ ] ";
	}
}
