package io.github.bradley09roberts.hardcorefriends.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.architecture.Construction;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;

/**
 * The village's workplaces as they stand: every finished library building of a trade's kind in the camp's world,
 * whoever put it up (the village's town plan builds them through {@code architecture.Construction}, so the market finds
 * them there and needs nothing else of the village). A building counts as standing once it is finished, or 95% built
 * with the last few blocks left to the repair job, so a trade never waits for ever on one ridge slab. Worked out at most
 * every ten seconds and on a building being finished, so asking is cheap.
 */
public final class Workplaces {
	private static final int REFRESH = 200;
	/** How much of a building must stand before its trade starts there. */
	static final double STANDING = 0.95;

	private static List<Workplace> cache = List.of();
	private static long cachedAt = Long.MIN_VALUE / 2;
	private static String cachedFor = "";

	private Workplaces() {
	}

	/** Every standing workplace in the camp's world; empty in any other world. */
	public static List<Workplace> all(ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return List.of();
		}
		long now = level.getGameTime();
		String dim = Camp.dimensionId(level);
		if (now - cachedAt < REFRESH && now >= cachedAt && dim.equals(cachedFor)) {
			return cache;
		}
		List<Workplace> found = new ArrayList<>();
		for (String site : Construction.sites(level)) {
			Optional<Blueprint> plan = Construction.planOf(level, site);
			if (plan.isEmpty()) {
				continue;
			}
			Optional<Trade> trade = Trade.forPlan(plan.get());
			if (trade.isEmpty() || !standing(level, site)) {
				continue;
			}
			addSite(level, site, plan.get(), trade.get(), found);
		}
		cache = List.copyOf(found);
		cachedAt = now;
		cachedFor = dim;
		return cache;
	}

	/** Forgets the cached list (a building was finished or let go). */
	public static void invalidate() {
		cachedAt = Long.MIN_VALUE / 2;
	}

	public static Optional<Workplace> byKey(ServerLevel level, String key) {
		if (key.isEmpty()) {
			return Optional.empty();
		}
		for (Workplace w : all(level)) {
			if (w.key().equals(key)) {
				return Optional.of(w);
			}
		}
		return Optional.empty();
	}

	/** Every standing workplace of this kind (for requests and listings). */
	public static List<Workplace> ofKind(ServerLevel level, String kind) {
		List<Workplace> list = new ArrayList<>();
		for (Workplace w : all(level)) {
			if (w.kind().equals(kind)) {
				list.add(w);
			}
		}
		return list;
	}

	/** True once the site's building is finished, or nearly ({@link #STANDING}). */
	static boolean standing(ServerLevel level, String site) {
		return Construction.isFinished(level, site) || Construction.progress(level, site) >= STANDING;
	}

	/** True if a site of this kind is reserved (built or not): the village has it in hand. */
	static boolean underWay(ServerLevel level, String kind) {
		for (String site : Construction.sites(level)) {
			Optional<Blueprint> plan = Construction.planOf(level, site);
			if (plan.isPresent() && plan.get().kind().equals(kind)) {
				return true;
			}
		}
		return false;
	}

	private static void addSite(ServerLevel level, String site, Blueprint plan, Trade trade, List<Workplace> into) {
		List<BlockPos> jobs = Construction.markers(level, site, "job");
		List<BlockPos> counters = Construction.markers(level, site, "counter");
		List<BlockPos> customers = Construction.markers(level, site, "customer");
		List<BlockPos> chests = Construction.markers(level, site, "chest");
		String name = plan.name();
		if (plan.kind().equals("civic:market")) {
			// The market square: a stall for each counter, each with its own customer spot and stock chest.
			List<BlockPos> stalls = Construction.markers(level, site, "stall");
			for (int i = 0; i < counters.size(); i++) {
				BlockPos counter = counters.get(i);
				BlockPos stall = i < stalls.size() ? stalls.get(i) : counter;
				BlockPos customer = i < customers.size() ? customers.get(i) : null;
				BlockPos chest = i < chests.size() ? chests.get(i) : nearest(chests, counter);
				into.add(new Workplace(site + "#" + i, site, i, plan.kind(), trade, name, stall, counter, customer,
					chest == null ? List.of() : List.of(chest)));
			}
			return;
		}
		BlockPos counter = counters.isEmpty() ? null : counters.getFirst();
		BlockPos job = !jobs.isEmpty() ? jobs.getFirst() : counter != null ? counter : firstOr(Construction.markers(level, site, "inside"));
		if (job == null) {
			return; // a plan with nowhere to work
		}
		into.add(new Workplace(site, site, 0, plan.kind(), trade, name, job, counter,
			customers.isEmpty() ? null : customers.getFirst(), List.copyOf(chests)));
	}

	private static @Nullable BlockPos firstOr(List<BlockPos> list) {
		return list.isEmpty() ? null : list.getFirst();
	}

	private static @Nullable BlockPos nearest(List<BlockPos> list, BlockPos to) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : list) {
			double d = p.distSqr(to);
			if (d < bestDist) {
				bestDist = d;
				best = p;
			}
		}
		return best;
	}
}
