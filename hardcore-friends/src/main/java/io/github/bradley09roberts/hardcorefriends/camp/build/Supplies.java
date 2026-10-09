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

import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds.Need;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * One trip's worth of gathering building materials into a friend's backpack: finished items come out of the supply
 * chest first, then the raw ingredients for whatever must still be crafted, and the crafting itself uses the real
 * recipes in {@code Crafting} and {@link WoodWork}. Items already promised to an earlier part of the same request are
 * never counted twice. When no crafting table is in reach, the ingredients are still collected and
 * {@link #needsTable()} says that the crafting has to wait until the friend stands by a table.
 *
 * <p>A request may name a preferred wood or colour per kind ({@link #prefer}): those items come out of the chest
 * first and are crafted from first, so a spruce house gets spruce stairs when the camp has spruce, and any wood when
 * it has not. A kind with two recipes (a bed from wool, else a straw bed from hay) uses the first one whose
 * ingredients the camp has.
 */
public final class Supplies {
	/** Above this food need, wheat is not used to make anything (hay bales for straw beds). */
	private static final double FOOD_SHORT = 0.4;

	private final CompanionEntity c;
	private final @Nullable Container chest;
	private final boolean table;
	private final Map<Stock, Integer> committed = new EnumMap<>(Stock.class);
	private final Map<Stock, String> preferred = new EnumMap<>(Stock.class);
	private boolean needsTable;

	public Supplies(CompanionEntity c, @Nullable Container chest, boolean nearTable) {
		this.c = c;
		this.chest = chest;
		this.table = nearTable;
	}

	/** Prefers this wood or colour for a kind (ignored when null). Returns this, for chaining. */
	public Supplies prefer(Map<Stock, String> preferences) {
		preferred.putAll(preferences);
		return this;
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
			obtain(e.getKey(), e.getValue(), 0);
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
	private boolean obtain(Stock s, int count, int depth) {
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
			String wish = preferred.get(s);
			if (have < need && wish != null) {
				SupplyChest.withdraw(chest, bp, st -> s.matches(st) && MaterialSpec.matchesVariant(st, wish), need - have);
				have = bp.count(s.item());
			}
			if (have < need) {
				SupplyChest.withdraw(chest, bp, s.item(), need - have);
			}
			have = bp.count(s.item());
		}
		if (have < need && depth <= 4) {
			Stock.Recipe recipe = chooseRecipe(s, need - have);
			if (recipe != null) {
				int crafts = (need - have + recipe.yield() - 1) / recipe.yield();
				boolean inputs = true;
				for (Map.Entry<Stock, Integer> in : recipe.inputs().entrySet()) {
					inputs &= obtain(in.getKey(), crafts * in.getValue(), depth + 1);
				}
				if (inputs && recipe.needsTable() && !table) {
					needsTable = true; // ingredients stay promised for crafting at the table
				} else if (inputs) {
					s.craft(c, need, preferred.get(s));
					for (Map.Entry<Stock, Integer> in : recipe.inputs().entrySet()) {
						committed.merge(in.getKey(), -crafts * in.getValue(), Integer::sum);
					}
				}
				have = bp.count(s.item());
			}
		}
		committed.merge(s, count, Integer::sum);
		return have >= need;
	}

	/** The first recipe whose ingredients are carried, stored or makeable; the first one if none are. */
	private Stock.@Nullable Recipe chooseRecipe(Stock s, int missing) {
		List<Stock.Recipe> recipes = s.recipes();
		if (recipes.size() <= 1) {
			return recipes.isEmpty() ? null : recipes.getFirst();
		}
		for (Stock.Recipe r : recipes) {
			int crafts = (missing + r.yield() - 1) / r.yield();
			boolean ok = true;
			for (Map.Entry<Stock, Integer> in : r.inputs().entrySet()) {
				if (!canMake(c, chest, in.getKey(), crafts * in.getValue())) {
					ok = false;
					break;
				}
			}
			if (ok) {
				return r;
			}
		}
		return recipes.getFirst();
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
		if (s == Stock.WHEAT && depth > 0 && CampNeeds.need(Need.FOOD) > FOOD_SHORT) {
			return false; // wheat is bread first: no straw beds while the camp is short of food
		}
		int have = available(c, chest, s);
		if (have >= count) {
			return true;
		}
		if (depth > 3) {
			return false;
		}
		for (Stock.Recipe recipe : s.recipes()) {
			int crafts = (count - have + recipe.yield() - 1) / recipe.yield();
			boolean ok = true;
			for (Map.Entry<Stock, Integer> in : recipe.inputs().entrySet()) {
				if (!canMake(c, chest, in.getKey(), crafts * in.getValue(), depth + 1)) {
					ok = false;
					break;
				}
			}
			if (ok) {
				return true;
			}
		}
		return false;
	}
}
