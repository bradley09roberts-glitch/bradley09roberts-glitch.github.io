package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;

/**
 * What the camp is short of, recomputed every 30 seconds from the supply chest and every loaded backpack. Tasks
 * multiply their scores by these needs, so friends naturally drift towards whatever the team lacks. Sage's
 * planning adds a 10% boost to the top need while Sage is around.
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
	private static final EnumMap<Need, Double> NEEDS = new EnumMap<>(Need.class);
	private static final EnumMap<Need, Integer> STOCK = new EnumMap<>(Need.class);
	private static final EnumMap<Need, Integer> BUILD_SHORTAGE = new EnumMap<>(Need.class);
	private static String shortageText = "";
	private static long lastShortageReport = -100_000;
	private static String siteProblem = "";
	private static long lastSiteProblem = -100_000;

	private CampNeeds() {
	}

	public static void clear() {
		NEEDS.clear();
		STOCK.clear();
		BUILD_SHORTAGE.clear();
		shortageText = "";
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

	/** The builder reports what the current blueprint still lacks so gatherers can prioritise it. */
	public static void reportBuildShortage(ServerLevel level, Map<Need, Integer> shortage, String description) {
		BUILD_SHORTAGE.clear();
		BUILD_SHORTAGE.putAll(shortage);
		shortageText = description;
		lastShortageReport = level.getGameTime();
		NEEDS.put(Need.BUILD, shortage.isEmpty() ? 0.0 : 1.0);
		for (Map.Entry<Need, Integer> e : shortage.entrySet()) {
			NEEDS.merge(e.getKey(), 0.8, Math::max);
		}
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

	public static Map<Need, Integer> buildShortage() {
		return BUILD_SHORTAGE;
	}

	public static String shortageText(long gameTime) {
		return gameTime - lastShortageReport < 20 * 300 ? shortageText : "";
	}

	public static void recompute(MinecraftServer server) {
		CampData data = Camp.data(server);
		int stage = data.stage();
		EnumMap<Need, Integer> stock = new EnumMap<>(Need.class);
		for (Need n : Need.values()) {
			stock.put(n, 0);
		}
		for (ServerLevel level : server.getAllLevels()) {
			SupplyChest.of(level).ifPresent(chest -> count(chest, stock));
		}
		for (CompanionEntity c : Companions.all()) {
			count(c.backpack().container(), stock);
		}
		STOCK.clear();
		STOCK.putAll(stock);
		NEEDS.put(Need.FOOD, shortfall(stock.get(Need.FOOD), 24 + 8 * stage));
		NEEDS.put(Need.WOOD, shortfall(stock.get(Need.WOOD), 64 + 32 * stage));
		NEEDS.put(Need.STONE, shortfall(stock.get(Need.STONE), 48 + 32 * stage));
		NEEDS.put(Need.DIRT, shortfall(stock.get(Need.DIRT), 16 + 8 * stage));
		NEEDS.put(Need.TORCHES, shortfall(stock.get(Need.TORCHES), 8 + 4 * stage));
		NEEDS.put(Need.ORE, shortfall(stock.get(Need.ORE), 6 + 6 * stage));
		NEEDS.put(Need.SEEDS, shortfall(stock.get(Need.SEEDS), 8));
		NEEDS.put(Need.BUILD, BUILD_SHORTAGE.isEmpty() ? 0.0 : 1.0);
		for (Map.Entry<Need, Integer> e : BUILD_SHORTAGE.entrySet()) {
			NEEDS.merge(e.getKey(), 0.8, Math::max);
		}
	}

	private static double shortfall(int have, int target) {
		return Math.clamp(1.0 - have / (double) target, 0.0, 1.0);
	}

	private static void count(Container container, EnumMap<Need, Integer> stock) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack s = container.getItem(i);
			if (s.isEmpty()) {
				continue;
			}
			int n = s.getCount();
			if (s.is(ModTags.COMPANION_FOOD) || s.is(Items.WHEAT) || s.is(Items.POTATO)) {
				stock.merge(Need.FOOD, n, Integer::sum);
			}
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
