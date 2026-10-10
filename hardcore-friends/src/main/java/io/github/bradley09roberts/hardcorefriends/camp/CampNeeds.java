package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.task.common.KeepList;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Role;

/**
 * What the camp is short of, recomputed every 30 seconds from the supply chest and every loaded backpack. Tasks
 * multiply their scores by these needs, so friends naturally drift towards whatever the team lacks. Sage's
 * planning adds a 10% boost to the top need while Sage is around.
 *
 * <p>Food is counted as food: in loaves' worth of hunger ({@link #stock} of {@code FOOD}), wheat as the bread it
 * makes, seeds and seed crops kept for planting not at all, against four days of food for the team.
 *
 * <p>Building shortages are kept per building ({@link #reportBuildShortage(ServerLevel, String, CompanionEntity, Map,
 * String)}), since several friends build at once (the builder, households on their own homes, the landscaper): one
 * builder's report never hides another's, a building's shortage is dropped once its builder is no longer short, the
 * building is finished or its site let go, and any report not renewed for {@value #SHORTAGE_STALE} ticks lapses.
 */
public final class CampNeeds {
	public enum Need {
		FOOD("food"),
		WOOD("wood"),
		STONE("stone"),
		DIRT("dirt"),
		TORCHES("torches"),
		ORE("ore"),
		SEEDS("seeds"),
		BUILD("building materials");

		private final String label;

		Need(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	private static final int INTERVAL = 600;
	/** Food is counted in loaves of bread: the hunger one loaf satisfies. */
	private static final double LOAF = 30;
	private static final double WHEAT_PER_LOAF = 3;
	/** Days of food the camp likes to have in store for the team; with less, farming and foraging speed up. */
	private static final double FOOD_DAYS = 4;
	private static final EnumMap<Need, Double> NEEDS = new EnumMap<>(Need.class);
	private static final EnumMap<Need, Integer> STOCK = new EnumMap<>(Need.class);
	/** A building's shortage lapses when its builder has not reported it again for this long (ten minutes). */
	private static final long SHORTAGE_STALE = 20 * 60 * 10;
	/** Key for reports that belong to no building in particular (levelling a site, tests). */
	private static final String ANY_SITE = "";

	/** One building's report: what it is short of, in words, when, and who is building it (null if nobody). */
	private record Shortage(Map<Need, Integer> amounts, String text, long at, @Nullable UUID builder) {
	}

	/** Each building's latest shortage report, by site key, oldest report first. */
	private static final Map<String, Shortage> BUILD_SHORTAGES = new LinkedHashMap<>();
	/** Every live report added up, kept ready for the many callers ({@link #buildShortage()}). */
	private static Map<Need, Integer> buildShortage = Map.of();
	private static String siteProblem = "";
	private static long lastSiteProblem = -100_000;
	/**
	 * Needs raised by the feature packages (Sage's long-term plan wanting iron or diamonds, for one): each is asked after
	 * every recompute and its values (0 to 1) are merged in by the larger, so they can raise a need but never hide one.
	 */
	public static final List<Function<MinecraftServer, Map<Need, Double>>> EXTRA = new CopyOnWriteArrayList<>();

	private CampNeeds() {
	}

	public static void clear() {
		NEEDS.clear();
		STOCK.clear();
		BUILD_SHORTAGES.clear();
		buildShortage = Map.of();
		siteProblem = "";
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL == 7) {
			recompute(server);
		}
	}

	/** 0 (plenty) to 1 (none at all). */
	public static double need(Need need) {
		return NEEDS.getOrDefault(need, 0.5);
	}

	public static int stock(Need need) {
		return STOCK.getOrDefault(need, 0);
	}

	/** The single most pressing need, or null when the camp is comfortable. */
	public static @Nullable Need focus() {
		Need best = null;
		double bestValue = 0.3;
		for (Map.Entry<Need, Double> e : NEEDS.entrySet()) {
			if (e.getValue() > bestValue) {
				bestValue = e.getValue();
				best = e.getKey();
			}
		}
		return best;
	}

	/** Score multiplier for a task that addresses this need: 1 + need, plus Sage's 10% planning bonus. */
	public static double weight(Need need) {
		double w = 1.0 + need(need);
		if (focus() == need && Companions.find(FriendId.SAGE).isPresent()) {
			w *= 1.1;
		}
		return w;
	}

	/**
	 * A shortage that belongs to no building in particular (levelling a site): replaces the last such report. It lapses
	 * like any other; an empty map withdraws it.
	 */
	public static void reportBuildShortage(ServerLevel level, Map<Need, Integer> shortage, String description) {
		reportBuildShortage(level, ANY_SITE, null, shortage, description);
	}

	/**
	 * A builder reports what this building's batch still lacks, so gatherers can prioritise it (and bring it to them).
	 * Replaces the building's earlier report; an empty map means it is short of nothing now.
	 */
	public static void reportBuildShortage(ServerLevel level, String siteKey, @Nullable CompanionEntity builder,
		Map<Need, Integer> shortage, String description) {
		if (shortage.isEmpty()) {
			clearBuildShortage(siteKey);
			return;
		}
		BUILD_SHORTAGES.remove(siteKey); // re-added at the end: the newest report comes last
		BUILD_SHORTAGES.put(siteKey, new Shortage(Map.copyOf(shortage), description, level.getGameTime(),
			builder == null ? null : builder.getUUID()));
		refreshBuildShortage(level.getGameTime());
		for (Map.Entry<Need, Integer> e : shortage.entrySet()) {
			NEEDS.merge(e.getKey(), 0.8, Math::max);
		}
	}

	/**
	 * Adds to what this building is reported short of (keeping the larger amount of each, and the words already given:
	 * "6 dirt or cobblestone" is not added again to a report that already asks for some), for a need that turns up
	 * mid-batch, such as blocks for a scaffold pillar.
	 */
	public static void addBuildShortage(ServerLevel level, String siteKey, @Nullable CompanionEntity builder,
		Map<Need, Integer> extra, String description) {
		Shortage before = live(siteKey, level.getGameTime());
		Map<Need, Integer> merged = new EnumMap<>(Need.class);
		if (before != null) {
			merged.putAll(before.amounts());
		}
		extra.forEach((need, n) -> merged.merge(need, n, Math::max));
		String words = description.replaceFirst("^\\d+ ", "");
		String text = before == null || before.text().isEmpty() ? description
			: before.text().contains(words) ? before.text() : before.text() + ", " + description;
		reportBuildShortage(level, siteKey, builder, merged, text);
	}

	/** This building is short of nothing any more: it was supplied, finished, or its site was let go. */
	public static void clearBuildShortage(String siteKey) {
		if (BUILD_SHORTAGES.remove(siteKey) != null) {
			buildShortage = merge();
			if (BUILD_SHORTAGES.isEmpty()) {
				NEEDS.put(Need.BUILD, 0.0);
			}
		}
	}

	/** One building's report, if it has not lapsed. */
	private static @Nullable Shortage live(String siteKey, long gameTime) {
		Shortage s = BUILD_SHORTAGES.get(siteKey);
		return s != null && !stale(s, gameTime) ? s : null;
	}

	private static boolean stale(Shortage s, long gameTime) {
		return gameTime - s.at() > SHORTAGE_STALE || gameTime < s.at();
	}

	/** Drops lapsed reports and adds up the rest; the building need follows whether anything is short. */
	private static void refreshBuildShortage(long gameTime) {
		BUILD_SHORTAGES.values().removeIf(s -> stale(s, gameTime));
		buildShortage = merge();
		NEEDS.put(Need.BUILD, BUILD_SHORTAGES.isEmpty() ? 0.0 : 1.0);
	}

	private static Map<Need, Integer> merge() {
		if (BUILD_SHORTAGES.isEmpty()) {
			return Map.of();
		}
		EnumMap<Need, Integer> total = new EnumMap<>(Need.class);
		for (Shortage s : BUILD_SHORTAGES.values()) {
			s.amounts().forEach((need, n) -> total.merge(need, n, Integer::sum));
		}
		return Collections.unmodifiableMap(total);
	}

	/** A builder could not find anywhere to put a building; shown by {@code /friends camp} for a while. */
	public static void reportSiteProblem(ServerLevel level, String text) {
		siteProblem = text;
		lastSiteProblem = level.getGameTime();
	}

	public static void clearSiteProblem() {
		siteProblem = "";
	}

	/** The latest site problem, if reported within the last five minutes, else "". */
	public static String siteProblem(long gameTime) {
		return gameTime - lastSiteProblem < 20 * 300 ? siteProblem : "";
	}

	/** What the buildings under way are short of, all their live reports added up (read-only). */
	public static Map<Need, Integer> buildShortage() {
		return buildShortage;
	}

	/**
	 * The friend whose building most recently reported a shortage that still stands, if they are still at work: the
	 * one to bring materials to.
	 */
	public static Optional<CompanionEntity> shortageBuilder() {
		UUID who = null;
		for (Shortage s : BUILD_SHORTAGES.values()) {
			if (s.builder() != null) {
				who = s.builder(); // the newest report comes last
			}
		}
		if (who == null) {
			return Optional.empty();
		}
		for (CompanionEntity c : Companions.all()) {
			if (c.getUUID().equals(who)) {
				return c.isAlive() && !c.isRemoved() && c.mode() == CompanionMode.WORK ? Optional.of(c) : Optional.empty();
			}
		}
		return Optional.empty();
	}

	/** The words of the latest shortage report, if made within the last five minutes, else "". */
	public static String shortageText(long gameTime) {
		Shortage latest = null;
		for (Shortage s : BUILD_SHORTAGES.values()) {
			latest = s;
		}
		return latest != null && gameTime - latest.at() < 20 * 300 && gameTime >= latest.at() ? latest.text() : "";
	}

	public static void recompute(MinecraftServer server) {
		CampData data = Camp.data(server);
		int stage = data.stage();
		EnumMap<Need, Integer> stock = new EnumMap<>(Need.class);
		for (Need n : Need.values()) {
			stock.put(n, 0);
		}
		double food = 0;
		for (ServerLevel level : server.getAllLevels()) {
			Optional<Container> chest = SupplyChest.of(level);
			if (chest.isPresent()) {
				count(chest.get(), stock);
				food += food(chest.get(), null);
			}
		}
		List<CompanionEntity> friends = Companions.all();
		for (CompanionEntity c : friends) {
			count(c.backpack().container(), stock);
			food += food(c.backpack().container(), c.friendId().role());
		}
		double loaves = food / LOAF;
		stock.put(Need.FOOD, (int) Math.round(loaves));
		STOCK.clear();
		STOCK.putAll(stock);
		// Enough food in store for the team's next few days: about half a loaf a friend a day.
		double foodTarget = Math.max(1, friends.size()) * Needs.typicalDailyHunger() / LOAF * FOOD_DAYS;
		NEEDS.put(Need.FOOD, Math.clamp(1.0 - loaves / foodTarget, 0.0, 1.0));
		NEEDS.put(Need.WOOD, shortfall(stock.get(Need.WOOD), 64 + 32 * stage));
		NEEDS.put(Need.STONE, shortfall(stock.get(Need.STONE), 48 + 32 * stage));
		NEEDS.put(Need.DIRT, shortfall(stock.get(Need.DIRT), 16 + 8 * stage));
		NEEDS.put(Need.TORCHES, shortfall(stock.get(Need.TORCHES), 8 + 4 * stage));
		NEEDS.put(Need.ORE, shortfall(stock.get(Need.ORE), 6 + 6 * stage));
		NEEDS.put(Need.SEEDS, shortfall(stock.get(Need.SEEDS), 8));
		refreshBuildShortage(server.overworld().getGameTime());
		for (Need n : buildShortage.keySet()) {
			NEEDS.merge(n, 0.8, Math::max);
		}
		for (Function<MinecraftServer, Map<Need, Double>> extra : EXTRA) {
			for (Map.Entry<Need, Double> e : extra.apply(server).entrySet()) {
				NEEDS.merge(e.getKey(), Math.clamp(e.getValue(), 0.0, 1.0), Math::max);
			}
		}
	}

	private static double shortfall(int have, int target) {
		return Math.clamp(1.0 - have / (double) target, 0.0, 1.0);
	}

	/**
	 * The food in a container, in hunger points: everything a friend can eat, by how filling it is, and wheat as the
	 * bread it bakes into (three to a loaf). Seeds and raw potatoes are not food (nobody in camp bakes potatoes). In a
	 * friend's backpack ({@code role} given), crops their work keeps for planting (a farmer's seed carrots) are seed,
	 * not food.
	 */
	private static double food(Container container, @Nullable Role role) {
		Map<Item, Integer> forPlanting = new HashMap<>();
		double total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack s = container.getItem(i);
			if (s.isEmpty()) {
				continue;
			}
			int n = s.getCount();
			if (CompanionEntity.isEdible(s)) {
				if (role != null) {
					int keep = forPlanting.computeIfAbsent(s.getItem(), item -> KeepList.workKeep(role, s));
					int kept = Math.min(keep, n);
					forPlanting.put(s.getItem(), keep - kept);
					n -= kept;
				}
				total += n * CompanionEntity.hungerValue(s);
			} else if (s.is(Items.WHEAT)) {
				total += n * LOAF / WHEAT_PER_LOAF;
			}
		}
		return total;
	}

	private static void count(Container container, EnumMap<Need, Integer> stock) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack s = container.getItem(i);
			if (s.isEmpty()) {
				continue;
			}
			int n = s.getCount();
			if (s.is(ItemTags.LOGS)) {
				stock.merge(Need.WOOD, n * 4, Integer::sum);
			} else if (s.is(ItemTags.PLANKS)) {
				stock.merge(Need.WOOD, n, Integer::sum);
			}
			if (s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE) || s.is(Items.STONE)) {
				stock.merge(Need.STONE, n, Integer::sum);
			}
			if (s.is(Items.DIRT)) {
				stock.merge(Need.DIRT, n, Integer::sum);
			}
			if (s.is(Items.TORCH)) {
				stock.merge(Need.TORCHES, n, Integer::sum);
			} else if (s.is(ItemTags.COALS)) {
				stock.merge(Need.TORCHES, n * 4, Integer::sum);
			}
			if (s.is(Items.RAW_IRON) || s.is(Items.IRON_INGOT)) {
				stock.merge(Need.ORE, n, Integer::sum);
			}
			if (s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS) || s.is(Items.CARROT) || s.is(Items.POTATO)) {
				stock.merge(Need.SEEDS, n, Integer::sum);
			}
		}
	}

	/** One-line summary for Sage and {@code /friends plan}. */
	public static String summary() {
		StringBuilder sb = new StringBuilder();
		for (Need n : Need.values()) {
			double v = need(n);
			if (v >= 0.5) {
				if (!sb.isEmpty()) {
					sb.append(", ");
				}
				sb.append(n.label()).append(String.format(Locale.ROOT, " (%d%% short)", Math.round(v * 100)));
			}
		}
		return sb.isEmpty() ? "nothing urgent" : sb.toString();
	}

	/** Helper for tasks: does the supply chest or this friend's backpack hold something matching? */
	public static boolean available(CompanionEntity c, Predicate<ItemStack> filter) {
		if (c.backpack().has(filter)) {
			return true;
		}
		return SupplyChest.of((ServerLevel) c.level()).map(chest -> SupplyChest.count(chest, filter) > 0).orElse(false);
	}
}
