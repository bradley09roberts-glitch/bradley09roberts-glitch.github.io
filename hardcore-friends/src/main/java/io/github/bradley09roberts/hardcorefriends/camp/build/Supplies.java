package io.github.bradley09roberts.hardcorefriends.camp.build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.Container;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds.Need;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * One trip's worth of gathering building materials into a friend's backpack: finished items come out of the supply
 * chest first, then the raw ingredients for whatever must still be crafted, and the crafting itself uses the real
 * recipes in {@code Crafting}. Items already promised to an earlier part of the same request are never counted
 * twice. When no crafting table is in reach, the ingredients are still collected and {@link #needsTable()} says
 * that the crafting has to wait until the friend stands by a table.
 */
public final class Supplies {
	private final CompanionEntity c;
	private final @Nullable Container chest;
	private final boolean table;
	private final Map<Stock, Integer> committed = new EnumMap<>(Stock.class);
	private boolean needsTable;

	public Supplies(CompanionEntity c, @Nullable Container chest, boolean nearTable) {
		this.c = c;
		this.chest = chest;
		this.table = nearTable;
	}

	/** True if something could not be crafted because no crafting table was within reach. */
	public boolean needsTable() {
		return needsTable;
	}

	/**
	 * Gathers and crafts everything in {@code wanted}, crafted things first so their ingredients are not taken from
	 * blocks that are placed as they are (logs for cabin corners). Returns what is still missing.
	 */
	public Map<Stock, Integer> gather(Map<Stock, Integer> wanted) {
		List<Map.Entry<Stock, Integer>> order = new ArrayList<>(wanted.entrySet());
		order.sort(Comparator.comparingInt(e -> rank(e.getKey())));
		Map<Stock, Integer> missing = new LinkedHashMap<>();
		for (Map.Entry<Stock, Integer> e : order) {
			obtain(e.getKey(), e.getValue());
		}
		// Check at the end: a later craft may have used up something an earlier entry relied on.
		Backpack bp = c.backpack();
		Map<Stock, Integer> stillNeeded = new EnumMap<>(Stock.class);
		for (Map.Entry<Stock, Integer> e : wanted.entrySet()) {
			stillNeeded.merge(e.getKey(), e.getValue(), Integer::sum);
		}
		for (Map.Entry<Stock, Integer> e : stillNeeded.entrySet()) {
			int have = bp.count(e.getKey().item());
			if (have < e.getValue()) {
				missing.put(e.getKey(), e.getValue() - have);
			}
		}
		return missing;
	}

	/** Makes sure {@code count} more of a kind are carried on top of what this trip already promised. */
	private boolean obtain(Stock s, int count) {
		if (count <= 0) {
			return true;
		}
		Backpack bp = c.backpack();
		int need = committed.getOrDefault(s, 0) + count;
		int have = bp.count(s.item());
		if (have < need && chest != null) {
			if (s == Stock.FILL) {
				SupplyChest.withdraw(chest, bp, st -> st.is(Items.DIRT), need - have);
				have = bp.count(s.item());
			}
			if (have < need) {
				SupplyChest.withdraw(chest, bp, s.item(), need - have);
			}
			have = bp.count(s.item());
		}
		Stock.Recipe recipe = s.recipe();
		if (have < need && recipe != null) {
			int crafts = (need - have + recipe.yield() - 1) / recipe.yield();
			boolean inputs = true;
			for (Map.Entry<Stock, Integer> in : recipe.inputs().entrySet()) {
				inputs &= obtain(in.getKey(), crafts * in.getValue());
			}
			if (inputs && recipe.needsTable() && !table) {
				needsTable = true; // ingredients stay promised for crafting at the table
			} else if (inputs) {
				s.craft(c, need);
				for (Map.Entry<Stock, Integer> in : recipe.inputs().entrySet()) {
					committed.merge(in.getKey(), -crafts * in.getValue(), Integer::sum);
				}
			}
			have = bp.count(s.item());
		}
		committed.merge(s, count, Integer::sum);
		return have >= need;
	}

	/** Crafted things first (most complex first), then planks and sticks, then raw materials. */
	private static int rank(Stock s) {
		Stock.Recipe r = s.recipe();
		if (r == null) {
			return 100;
		}
		if (s == Stock.PLANKS) {
			return 60;
		}
		if (s == Stock.STICK) {
			return 50;
		}
		return 10 - Math.min(9, r.inputs().size() + (r.needsTable() ? 1 : 0));
	}

	// ------------------------------------------------------------- reporting

	/** "12 planks, 2 glass panes". */
	public static String describe(Map<Stock, Integer> missing) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Stock, Integer> e : missing.entrySet()) {
			if (!sb.isEmpty()) {
				sb.append(", ");
			}
			sb.append(e.getKey().describe(e.getValue()));
		}
		return sb.toString();
	}

	/** The camp needs a shortage feeds, for gatherers to prioritise. */
	public static Map<Need, Integer> needs(Map<Stock, Integer> missing) {
		Map<Need, Integer> map = new EnumMap<>(Need.class);
		for (Map.Entry<Stock, Integer> e : missing.entrySet()) {
			map.merge(e.getKey().need(), e.getValue(), Integer::sum);
		}
		return map;
	}

	/** Total of a kind carried plus in the chest, without moving anything. */
	public static int available(CompanionEntity c, @Nullable Container chest, Stock s) {
		int n = c.backpack().count(s.item());
		if (chest != null) {
			n += SupplyChest.count(chest, s.item());
		}
		return n;
	}

	/** True if at least {@code count} of a kind are carried or stored, or could be crafted from what is. */
	public static boolean canMake(CompanionEntity c, @Nullable Container chest, Stock s, int count) {
		return canMake(c, chest, s, count, 0);
	}

	private static boolean canMake(CompanionEntity c, @Nullable Container chest, Stock s, int count, int depth) {
		int have = available(c, chest, s);
		if (have >= count) {
			return true;
		}
		Stock.Recipe recipe = s.recipe();
		if (recipe == null || depth > 3) {
			return false;
		}
		int crafts = (count - have + recipe.yield() - 1) / recipe.yield();
		for (Map.Entry<Stock, Integer> in : recipe.inputs().entrySet()) {
			if (!canMake(c, chest, in.getKey(), crafts * in.getValue(), depth + 1)) {
				return false;
			}
		}
		return true;
	}
}
