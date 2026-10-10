package io.github.bradley09roberts.hardcorefriends.architecture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds.Need;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;
import io.github.bradley09roberts.hardcorefriends.camp.build.WoodWork;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;

/**
 * What the buildings under way still need, worked back to raw materials: each builder reports the materials the rest
 * of their building calls for ({@link #report}), and from those, less what the supply chest and the team's backpacks
 * already hold, comes what is missing at every step (glass panes, so glass, so sand). The kiln smelts what is missing
 * and smeltable, the sand digger and the shearer gather what is missing and raw, and the camp's needs rise for wood,
 * stone and earth so the gatherers get ahead of the builders. Reports go stale after ten minutes, so an abandoned
 * building stops asking.
 */
public final class MaterialDemand {
	private static final long STALE = 20 * 60 * 10;
	private static final long RECOMPUTE = 20 * 30;

	private record Forecast(Map<Stock, Integer> remaining, long at) {
	}

	private static final Map<String, Forecast> FORECASTS = new ConcurrentHashMap<>();
	private static volatile Map<Stock, Integer> missing = Map.of();
	private static volatile Map<Stock, Integer> wanted = Map.of();
	private static volatile long computedAt = Long.MIN_VALUE / 2;

	private MaterialDemand() {
	}

	/** A builder's report: the materials the rest of this site's building calls for. */
	public static void report(ServerLevel level, String siteKey, Map<Stock, Integer> remaining) {
		FORECASTS.put(siteKey, new Forecast(Map.copyOf(remaining), level.getGameTime()));
	}

	/** A site is finished or gone: it needs nothing more. */
	public static void clear(String siteKey) {
		FORECASTS.remove(siteKey);
	}

	/** Forgets everything (a server stopping). */
	public static void clearAll() {
		FORECASTS.clear();
		missing = Map.of();
		wanted = Map.of();
		computedAt = Long.MIN_VALUE / 2;
	}

	/** How many of this kind the buildings under way are short of, after what is stored (worked out every 30 s). */
	public static int missing(MinecraftServer server, Stock s) {
		return missing(server).getOrDefault(s, 0);
	}

	/** Every kind the buildings under way are short of, after what is stored. */
	public static Map<Stock, Integer> missing(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now - computedAt >= RECOMPUTE || now < computedAt) {
			computedAt = now;
			missing = compute(server, now);
		}
		return missing;
	}

	/**
	 * How many of this kind the buildings under way still call for, stored or not (with what the missing parts are made
	 * from), so nothing else takes the builders' stock out of their reach (a shop stocking its shelves).
	 */
	public static int wanted(MinecraftServer server, Stock s) {
		missing(server); // worked out together, every 30 s
		return wanted.getOrDefault(s, 0);
	}

	private static Map<Stock, Integer> compute(MinecraftServer server, long now) {
		Map<Stock, Integer> demand = new EnumMap<>(Stock.class);
		FORECASTS.values().removeIf(f -> now - f.at() > STALE || now < f.at());
		for (Forecast f : FORECASTS.values()) {
			f.remaining().forEach((k, v) -> demand.merge(k, v, Integer::sum));
		}
		if (demand.isEmpty()) {
			wanted = Map.of();
			return Map.of();
		}
		Map<String, Integer> woolColours = new HashMap<>();
		Map<Stock, Integer> stored = stored(server, woolColours);
		// Work from finished goods back to raw materials, so each step only asks for what the step above still lacks.
		List<Stock> order = new ArrayList<>(List.of(Stock.values()));
		order.sort(Comparator.comparingInt((Stock s) -> depth(s, 0)).reversed());
		Map<Stock, Integer> short_ = new EnumMap<>(Stock.class);
		int woolGroup = 1; // a bed short: its wool must come three of one colour; carpets two
		for (Stock s : order) {
			int want = demand.getOrDefault(s, 0);
			int have = stored.getOrDefault(s, 0);
			if (s == Stock.WOOL && woolGroup > 1) {
				// Odd wool of mixed colours makes no bed or carpet: only whole sets of one colour count, so the
				// shearing goes on until there are enough.
				have = WoodWork.woolSets(woolColours, woolGroup) * woolGroup;
			}
			int lack = want - have;
			if (lack <= 0) {
				continue;
			}
			short_.put(s, lack);
			Stock.Recipe recipe = s.recipe();
			Stock from = s.smeltedFrom();
			if (recipe != null) {
				int crafts = (lack + recipe.yield() - 1) / recipe.yield();
				recipe.inputs().forEach((in, n) -> demand.merge(in, crafts * n, Integer::sum));
				if (s == Stock.BED || s == Stock.CARPET) {
					woolGroup = Math.max(woolGroup, recipe.inputs().getOrDefault(Stock.WOOL, 1));
				}
			} else if (from != null) {
				demand.merge(from, lack, Integer::sum);
			}
		}
		wanted = Map.copyOf(demand);
		return Map.copyOf(short_);
	}

	/** How many steps of crafting or smelting a kind is from raw materials. */
	private static int depth(Stock s, int guard) {
		if (guard > 6) {
			return 0;
		}
		Stock.Recipe recipe = s.recipe();
		Stock from = s.smeltedFrom();
		int d = 0;
		if (recipe != null) {
			for (Stock in : recipe.inputs().keySet()) {
				d = Math.max(d, 1 + depth(in, guard + 1));
			}
		} else if (from != null) {
			d = 1 + depth(from, guard + 1);
		}
		return d;
	}

	/**
	 * Everything in the supply chest and the team's backpacks, by kind (an item may count for several kinds); the wool
	 * among it is also counted by colour into {@code woolColours}.
	 */
	private static Map<Stock, Integer> stored(MinecraftServer server, Map<String, Integer> woolColours) {
		Map<Stock, Integer> stored = new EnumMap<>(Stock.class);
		List<Container> containers = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			SupplyChest.of(level).ifPresent(containers::add);
		}
		for (CompanionEntity c : Companions.all()) {
			containers.add(c.backpack().container());
		}
		for (Container container : containers) {
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack item = container.getItem(i);
				if (item.isEmpty()) {
					continue;
				}
				WoodWork.countWool(woolColours, item);
				for (Stock s : Stock.values()) {
					if (s.matches(item)) {
						stored.merge(s, item.getCount(), Integer::sum);
					}
				}
			}
		}
		return stored;
	}

	/**
	 * The camp needs the missing raw materials raise (for {@code CampNeeds.EXTRA}): wood, stone, earth (sand and clay
	 * for the quarry) and ore, more the more is missing. Never lowers a need.
	 */
	public static Map<Need, Double> extraNeeds(MinecraftServer server) {
		Map<Need, Double> needs = new EnumMap<>(Need.class);
		for (Map.Entry<Stock, Integer> e : missing(server).entrySet()) {
			Stock s = e.getKey();
			boolean raw = s.recipe() == null && s.smeltedFrom() == null;
			if (!raw || e.getValue() <= 0) {
				continue;
			}
			Need need = switch (s) {
				case LOG -> Need.WOOD;
				case COBBLESTONE, STONE_MATERIAL, FILL, FLINT -> Need.STONE;
				case SAND, CLAY_BALL, DIRT -> Need.DIRT;
				case IRON, COAL -> s == Stock.COAL ? Need.TORCHES : Need.ORE;
				default -> null;
			};
			if (need != null) {
				needs.merge(need, Math.min(0.9, 0.4 + e.getValue() / 96.0), Math::max);
			}
		}
		return needs;
	}
}
