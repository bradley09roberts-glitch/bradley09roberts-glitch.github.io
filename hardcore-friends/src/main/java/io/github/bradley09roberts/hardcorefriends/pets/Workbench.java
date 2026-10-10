package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;

/**
 * The camp's stock and work tables as the map and pet jobs use them: how much of something the supply chest holds
 * (remembered for a few seconds, so nine friends scoring jobs every second cost one look), where the crafting table
 * and the cartography table stand (looked for near the chest and the camp centre, at most every 30 seconds), and the
 * real recipes for an empty map (eight paper round a compass), a compass (four iron and a redstone) and an item frame
 * (eight sticks round a piece of leather), made at a table as a player makes them. Paper, leather and iron that Sage's
 * plan is collecting are never taken (none while it still wants more, and never below its target once it has enough,
 * as for trading), and eight iron always stay in the chest.
 */
final class Workbench {
	/** How far from the chest (or the camp centre) a work table is looked for. */
	private static final int TABLE_RADIUS = 8;
	private static final int TABLE_RESCAN = 600;
	private static final int COUNT_TTL = 100;
	/** Iron the camp keeps for itself: a compass is only made from iron the chest can spare beyond this. */
	static final int IRON_KEPT = 8;
	/** How close a friend stands to a table to work at it. */
	static final double TABLE_REACH = 2.5;

	static final Predicate<ItemStack> EMPTY_MAP = s -> s.is(Items.MAP);
	static final Predicate<ItemStack> PAPER = s -> s.is(Items.PAPER);
	static final Predicate<ItemStack> COMPASS = s -> s.is(Items.COMPASS);
	static final Predicate<ItemStack> IRON = s -> s.is(Items.IRON_INGOT);
	static final Predicate<ItemStack> REDSTONE = s -> s.is(Items.REDSTONE);
	static final Predicate<ItemStack> FRAME = s -> s.is(Items.ITEM_FRAME);
	static final Predicate<ItemStack> LEATHER = s -> s.is(Items.LEATHER);
	static final Predicate<ItemStack> STICK = s -> s.is(Items.STICK);
	static final Predicate<ItemStack> PLANKS = s -> s.is(ItemTags.PLANKS);

	private static final class Tables {
		private @Nullable BlockPos crafting;
		private @Nullable BlockPos cartography;
		private long at = Long.MIN_VALUE / 2;
	}

	private record Count(long at, int count) {
	}

	private static final Map<String, Tables> TABLES = new HashMap<>();
	private static final Map<String, Count> COUNTS = new HashMap<>();

	private Workbench() {
	}

	static void clear() {
		TABLES.clear();
		COUNTS.clear();
	}

	// ---------------------------------------------------------------- stock

	/** How many matching items the supply chest holds, remembered for {@value #COUNT_TTL} ticks under {@code key}. */
	static int stock(ServerLevel level, String key, Predicate<ItemStack> filter) {
		String k = Camp.dimensionId(level) + "|" + key;
		long now = level.getGameTime();
		Count known = COUNTS.get(k);
		if (known != null && now - known.at() < COUNT_TTL && now >= known.at()) {
			return known.count();
		}
		int count = SupplyChest.of(level).map(chest -> SupplyChest.count(chest, filter)).orElse(0);
		COUNTS.put(k, new Count(now, count));
		return count;
	}

	/** Forgets a remembered count after taking from the chest, so the next look is fresh. */
	static void forgetStock(ServerLevel level) {
		String prefix = Camp.dimensionId(level) + "|";
		COUNTS.keySet().removeIf(k -> k.startsWith(prefix));
	}

	// --------------------------------------------------------------- tables

	/** The camp's crafting table, or null. */
	static @Nullable BlockPos craftingTable(ServerLevel level) {
		return tables(level).crafting;
	}

	/** Where to make a map: the cartography table if the camp has one, else the crafting table, or null. */
	static @Nullable BlockPos mapTable(ServerLevel level) {
		Tables t = tables(level);
		return t.cartography != null ? t.cartography : t.crafting;
	}

	private static Tables tables(ServerLevel level) {
		Tables t = TABLES.computeIfAbsent(Camp.dimensionId(level), k -> new Tables());
		long now = level.getGameTime();
		boolean stale = now - t.at >= TABLE_RESCAN || now < t.at
			|| t.crafting != null && (!level.isLoaded(t.crafting) || !level.getBlockState(t.crafting).is(Blocks.CRAFTING_TABLE))
			|| t.cartography != null && (!level.isLoaded(t.cartography) || !level.getBlockState(t.cartography).is(Blocks.CARTOGRAPHY_TABLE));
		if (!stale) {
			return t;
		}
		t.at = now;
		t.crafting = null;
		t.cartography = null;
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return t;
		}
		Optional<BlockPos> chest = data.chestPos();
		if (chest.isPresent()) {
			scan(level, chest.get(), t);
		}
		Optional<BlockPos> centre = data.campPos();
		if ((t.crafting == null || t.cartography == null) && centre.isPresent()) {
			scan(level, centre.get(), t);
		}
		return t;
	}

	private static void scan(ServerLevel level, BlockPos centre, Tables t) {
		int r = TABLE_RADIUS;
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -3, -r), centre.offset(r, 3, r))) {
			if (!level.isLoaded(p)) {
				continue;
			}
			var state = level.getBlockState(p);
			if (t.crafting == null && state.is(Blocks.CRAFTING_TABLE)) {
				t.crafting = p.immutable();
			} else if (t.cartography == null && state.is(Blocks.CARTOGRAPHY_TABLE)) {
				t.cartography = p.immutable();
			}
		}
	}

	/** True when the friend stands close enough to the table to work at it. */
	static boolean atTable(CompanionEntity c, BlockPos table) {
		return c.position().distanceToSqr(Vec3.atBottomCenterOf(table)) <= (TABLE_REACH + 1) * (TABLE_REACH + 1);
	}

	// ------------------------------------------------------------ materials

	/** True if the friend carries what an empty map takes: one already made, or eight paper and a compass (or its iron). */
	static boolean carriesMapMakings(Backpack bp) {
		return bp.count(EMPTY_MAP) >= 1 || bp.count(PAPER) >= 8
			&& (bp.count(COMPASS) >= 1 || bp.count(IRON) >= 4 && bp.count(REDSTONE) >= 1);
	}

	/** True if the supply chest holds what an empty map takes (counts remembered for a few seconds). */
	static boolean chestHasMapMakings(ServerLevel level) {
		if (stock(level, "map", EMPTY_MAP) >= 1) {
			return true;
		}
		return spare(level, Items.PAPER, stock(level, "paper", PAPER), 0) >= 8 && (stock(level, "compass", COMPASS) >= 1
			|| spare(level, Items.IRON_INGOT, stock(level, "iron", IRON), IRON_KEPT) >= 4 && stock(level, "redstone", REDSTONE) >= 1);
	}

	/**
	 * How many of this the camp can spare for maps and frames, of the {@code inChest} the chest holds: beyond
	 * {@code keep}, and beyond what Sage's plan is collecting (paper and leather for the library's books, iron for its
	 * stock). While the plan still wants more of it, none; once it has enough, the plan's whole amount stays, so a map
	 * never eats into what the plan has already gathered (the rule trading and the smith keep too).
	 */
	static int spare(ServerLevel level, Item item, int inChest, int keep) {
		MinecraftServer server = level.getServer();
		if (ProgressPlan.wants(server, item)) {
			return 0;
		}
		int kept = Math.max(keep, ProgressPlan.stockTargets(server).getOrDefault(item, 0));
		return Math.max(0, inChest - kept);
	}

	/**
	 * Takes the makings of one empty map from the chest: a ready one if there is one, otherwise eight paper and a
	 * compass, or the iron and redstone for one (only iron the camp can spare). Returns true if the friend now carries
	 * enough. What is taken and not used goes back with their next delivery to the chest.
	 */
	static boolean takeMapMakings(ServerLevel level, Container chest, Backpack bp) {
		forgetStock(level);
		if (bp.count(EMPTY_MAP) < 1 && SupplyChest.count(chest, EMPTY_MAP) >= 1) {
			SupplyChest.withdraw(chest, bp, EMPTY_MAP, 1);
		}
		if (carriesMapMakings(bp)) {
			return true;
		}
		if (bp.count(PAPER) < 8 && spare(level, Items.PAPER, SupplyChest.count(chest, PAPER), 0) + bp.count(PAPER) < 8) {
			return false;
		}
		boolean compass = bp.count(COMPASS) >= 1 || SupplyChest.count(chest, COMPASS) >= 1;
		boolean iron = spare(level, Items.IRON_INGOT, SupplyChest.count(chest, IRON), IRON_KEPT) >= 4 - bp.count(IRON)
			&& SupplyChest.count(chest, REDSTONE) + bp.count(REDSTONE) >= 1;
		if (!compass && !iron) {
			return false;
		}
		SupplyChest.withdraw(chest, bp, PAPER, Math.max(0, 8 - bp.count(PAPER)));
		if (bp.count(COMPASS) < 1) {
			if (compass) {
				SupplyChest.withdraw(chest, bp, COMPASS, 1);
			} else {
				SupplyChest.withdraw(chest, bp, IRON, Math.max(0, 4 - bp.count(IRON)));
				SupplyChest.withdraw(chest, bp, REDSTONE, Math.max(0, 1 - bp.count(REDSTONE)));
			}
		}
		return carriesMapMakings(bp);
	}

	/** True if the friend needs a table to turn what they carry into an empty map (none is made yet). */
	static boolean needsTableForMap(Backpack bp) {
		return bp.count(EMPTY_MAP) < 1;
	}

	/**
	 * Makes one empty map from what the friend carries, at a table: a compass first if need be (four iron round a
	 * redstone, at a crafting table), then eight paper round the compass. Real ingredients are used up. Returns true if
	 * the friend now carries an empty map.
	 */
	static boolean craftEmptyMap(CompanionEntity c, boolean craftingTable) {
		Backpack bp = c.backpack();
		if (bp.count(EMPTY_MAP) >= 1) {
			return true;
		}
		if (bp.count(PAPER) < 8) {
			return false;
		}
		if (bp.count(COMPASS) < 1) {
			if (!craftingTable || bp.count(IRON) < 4 || bp.count(REDSTONE) < 1) {
				return false;
			}
			bp.remove(IRON, 4);
			bp.remove(REDSTONE, 1);
			give(c, new ItemStack(Items.COMPASS));
		}
		if (bp.count(COMPASS) < 1) {
			return false;
		}
		bp.remove(PAPER, 8);
		bp.remove(COMPASS, 1);
		give(c, new ItemStack(Items.MAP));
		c.swingArm();
		return bp.count(EMPTY_MAP) >= 1;
	}

	/** True if the supply chest holds an item frame or what one takes (leather, and sticks or planks for them). */
	static boolean chestHasFrameMakings(ServerLevel level) {
		return stock(level, "frame", FRAME) >= 1 || spare(level, Items.LEATHER, stock(level, "leather", LEATHER), 0) >= 1
			&& (stock(level, "sticks", STICK) >= 8 || stock(level, "planks", PLANKS) >= 4);
	}

	/** Takes an item frame, or its makings, from the chest. Returns true if the friend now carries enough for one. */
	static boolean takeFrameMakings(ServerLevel level, Container chest, Backpack bp) {
		forgetStock(level);
		if (bp.count(FRAME) >= 1) {
			return true;
		}
		if (SupplyChest.count(chest, FRAME) >= 1) {
			SupplyChest.withdraw(chest, bp, FRAME, 1);
			return bp.count(FRAME) >= 1;
		}
		if (bp.count(LEATHER) < 1 && spare(level, Items.LEATHER, SupplyChest.count(chest, LEATHER), 0) < 1) {
			return false;
		}
		SupplyChest.withdraw(chest, bp, LEATHER, Math.max(0, 1 - bp.count(LEATHER)));
		if (bp.count(STICK) < 8) {
			SupplyChest.withdraw(chest, bp, STICK, 8 - bp.count(STICK));
		}
		int planksWanted = (8 - bp.count(STICK) + 1) / 2 - bp.count(PLANKS);
		if (bp.count(STICK) < 8 && planksWanted > 0) {
			SupplyChest.withdraw(chest, bp, PLANKS, planksWanted);
		}
		return carriesFrameMakings(bp);
	}

	/** True if the friend carries an item frame, or leather and the sticks for one (two sticks to a plank). */
	static boolean carriesFrameMakings(Backpack bp) {
		return bp.count(FRAME) >= 1 || bp.count(LEATHER) >= 1 && bp.count(STICK) + 2 * bp.count(PLANKS) >= 8;
	}

	/** Makes an item frame at a crafting table from carried leather and sticks (sawing planks into sticks if need be). */
	static boolean craftFrame(CompanionEntity c) {
		Backpack bp = c.backpack();
		if (bp.count(FRAME) >= 1) {
			return true;
		}
		if (bp.count(LEATHER) < 1 || !Crafting.ensureSticks(bp, 8)) {
			return false;
		}
		bp.remove(STICK, 8);
		bp.remove(LEATHER, 1);
		give(c, new ItemStack(Items.ITEM_FRAME));
		c.swingArm();
		return bp.count(FRAME) >= 1;
	}

	/** Puts an item in the friend's backpack, or at their feet if it is full. */
	static void give(CompanionEntity c, ItemStack stack) {
		ItemStack left = c.backpack().insert(stack);
		if (!left.isEmpty() && c.level() instanceof ServerLevel level) {
			c.spawnAtLocation(level, left);
		}
	}

	/** One of the recipes the friends learn here, for {@code Crafting.addRecipe}. */
	static void teachRecipes() {
		recipe(Items.COMPASS, 1, true, Crafting.of(Items.IRON_INGOT, 4), Crafting.of(Items.REDSTONE, 1));
		recipe(Items.MAP, 1, true, Crafting.of(Items.PAPER, 8), Crafting.of(Items.COMPASS, 1));
		recipe(Items.ITEM_FRAME, 1, true, Crafting.of(Items.STICK, 8), Crafting.of(Items.LEATHER, 1));
	}

	private static void recipe(Item output, int count, boolean needsTable, Crafting.Ingredient... inputs) {
		if (!Crafting.hasAddedRecipe(output)) {
			Crafting.addRecipe(output, count, needsTable, inputs);
		}
	}
}
