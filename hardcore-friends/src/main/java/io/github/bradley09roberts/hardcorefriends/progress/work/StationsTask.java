package io.github.bradley09roberts.hardcorefriends.progress.work;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.BlueprintTask;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.BuildJob;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.camp.build.Placement;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;
import io.github.bradley09roberts.hardcorefriends.camp.build.Supplies;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.progress.CampStock;
import io.github.bradley09roberts.hardcorefriends.progress.Milestone;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;
import io.github.bradley09roberts.hardcorefriends.progress.Stations;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * The builder puts up the plan's work stations through the ordinary blueprint system (real materials, a proper site,
 * the guard's {@code BUILD} rules): the library (an enchanting table ringed by 15 bookshelves) once the plan has reached
 * enchanting (or the camp is a Settlement), an anvil once iron is plentiful, and a brewing stand once there is a blaze
 * rod to spare. Unlike Oak's general building list, a station is only started when everything it still lacks is in
 * the camp or can be crafted from what is there, so a costly library never holds up the camp's other building or
 * keeps the camp short of "building materials" for days. A station that lost blocks (an anvil worn away by use) is
 * built again the same way. Checked at most every {@value #CHECK_INTERVAL} ticks.
 */
public final class StationsTask extends BlueprintTask {
	private static final int CHECK_INTERVAL = 200;
	/** Iron the anvil may not dig into: the plan's own stock. */
	private static final int IRON_KEPT = ProgressPlan.IRON_STOCK;
	private static final int ANVIL_IRON = 31;

	private long checkedAt = Long.MIN_VALUE / 2;
	private @Nullable Blueprint cached;
	private boolean rebuilding;

	@Override
	public String id() {
		return "oak.stations";
	}

	@Override
	protected WorldEditGuard.Reason reason() {
		return WorldEditGuard.Reason.BUILD;
	}

	@Override
	protected double baseScore() {
		return 55;
	}

	@Override
	protected boolean repair() {
		return rebuilding;
	}

	@Override
	protected @Nullable Blueprint choose(CompanionEntity c, CampData data) {
		ServerLevel level = (ServerLevel) c.level();
		long now = level.getGameTime();
		if (now - checkedAt < CHECK_INTERVAL && now >= checkedAt) {
			if (cached != null && isSetAside(c, cached.id())) {
				cached = null;
			}
			return cached;
		}
		checkedAt = now;
		cached = null;
		for (Blueprint plan : List.of(Blueprints.LIBRARY, Blueprints.ANVIL, Blueprints.BREWING_STAND)) {
			if (isSetAside(c, plan.id()) || !wanted(level, data, plan)) {
				continue;
			}
			boolean completed = data.isCompleted(plan.id());
			if (completed && BuildJob.missing(level, data, plan, 1).isEmpty()) {
				continue; // standing and whole
			}
			if (makeable(c, level, data, plan)) {
				cached = plan;
				rebuilding = completed;
				break;
			}
		}
		return cached;
	}

	/** Whether the camp wants this station now. */
	private static boolean wanted(ServerLevel level, CampData data, Blueprint plan) {
		MinecraftServer server = level.getServer();
		if (data.stage() < 3) {
			return false;
		}
		CampStock.Snapshot s = CampStock.get(server);
		if (plan == Blueprints.LIBRARY) {
			boolean due = ProgressPlan.reached(server, Milestone.ENCHANTING) || data.stage() >= 4;
			if (!due) {
				return false;
			}
			if (!data.isCompleted(plan.id())) {
				// A player's own table with all 15 shelves already does the job.
				BlockPos table = Stations.enchantingTable(level);
				boolean librarys = data.site(plan.id()).map(site -> Blueprints.at(site, Blueprints.LIBRARY_TABLE).equals(table)).orElse(false);
				return table == null || librarys || Stations.power(level, table) < Stations.MAX_SHELVES;
			}
			return true;
		}
		if (plan == Blueprints.ANVIL) {
			return (ProgressPlan.reached(server, Milestone.DIAMONDS) || data.stage() >= 4)
				&& (s.total(Items.ANVIL) > 0 || s.total(Items.IRON_INGOT) >= ANVIL_IRON + IRON_KEPT);
		}
		if (plan == Blueprints.BREWING_STAND) {
			return s.total(Items.BREWING_STAND) > 0
				|| s.total(Items.BLAZE_ROD) >= 1 && WorkshopTask.sparePowder(server, s, data) >= 0;
		}
		return false;
	}

	/**
	 * True when everything the station still lacks is carried, in the chest, or can be crafted from what is, all of it
	 * together: the library's bookshelves and its table both take books, so each material is counted against what the
	 * others already use.
	 */
	private static boolean makeable(CompanionEntity c, ServerLevel level, CampData data, Blueprint plan) {
		Map<Stock, Integer> need = new EnumMap<>(Stock.class);
		if (data.site(plan.id()).isPresent()) {
			for (Placement p : BuildJob.missing(level, data, plan, 64)) {
				Stock s = p.entry().material().stock();
				if (s != null) {
					need.merge(s, 1, Integer::sum);
				}
			}
		} else {
			for (Blueprint.Entry e : plan.entries()) {
				Stock s = e.material().stock();
				if (s != null) {
					need.merge(s, 1, Integer::sum);
				}
			}
		}
		Container chest = SupplyChest.of(level).orElse(null);
		Map<Stock, Integer> used = new EnumMap<>(Stock.class);
		for (Map.Entry<Stock, Integer> e : need.entrySet()) {
			if (!reserve(c, chest, e.getKey(), e.getValue(), used, 0)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Sets aside {@code count} of a kind from what is carried and stored, less what earlier parts already set aside,
	 * crafting the rest from its ingredients (set aside the same way). False when there is not enough.
	 */
	private static boolean reserve(CompanionEntity c, @Nullable Container chest, Stock s, int count, Map<Stock, Integer> used, int depth) {
		int taken = used.getOrDefault(s, 0);
		int free = Math.max(0, Supplies.available(c, chest, s) - taken);
		int take = Math.min(free, count);
		used.put(s, taken + take);
		int missing = count - take;
		if (missing <= 0) {
			return true;
		}
		Stock.Recipe recipe = s.recipe();
		if (recipe == null || depth > 3) {
			return false;
		}
		int crafts = (missing + recipe.yield() - 1) / recipe.yield();
		for (Map.Entry<Stock, Integer> in : recipe.inputs().entrySet()) {
			if (!reserve(c, chest, in.getKey(), crafts * in.getValue(), used, depth + 1)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean start(CompanionEntity c) {
		checkedAt = Long.MIN_VALUE / 2; // look again with fresh eyes
		return super.start(c);
	}
}
