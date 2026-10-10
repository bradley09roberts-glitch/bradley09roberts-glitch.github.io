package io.github.bradley09roberts.hardcorefriends.ai.role.build;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;

import io.github.bradley09roberts.hardcorefriends.architecture.MaterialDemand;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.BuildJob;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.camp.build.Placement;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;
import io.github.bradley09roberts.hardcorefriends.camp.build.Supplies;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Oak puts back blocks that have gone missing from finished buildings (a torch knocked off, a wall plank taken by
 * a creeper), using the same materials as the plan. Only empty spots are refilled; nothing is ever replaced. The
 * buildings are checked every ten seconds: the camp's own each time, and the village's finished library buildings
 * (houses, shops) two at a time in turn, so a big village costs no more per check. This is also how decoration left
 * out while its material could not be had (glass in a window, a flower pot) is added once it can.
 */
public final class RepairTask extends BlueprintTask {
	private static final int RESCAN = 200;
	/** Gaps looked at in one building, in build order. */
	private static final int GAPS_LOOKED_AT = 48;

	private long scannedAt = -100_000;
	private @Nullable Blueprint damaged;
	private @Nullable String damagedKey;
	private int libraryCursor;

	@Override
	public String id() {
		return "oak.repair";
	}

	@Override
	protected WorldEditGuard.Reason reason() {
		return WorldEditGuard.Reason.BUILD;
	}

	@Override
	protected double baseScore() {
		return 40;
	}

	@Override
	protected boolean repair() {
		return true;
	}

	@Override
	public int successCooldown() {
		return 100;
	}

	@Override
	protected @Nullable Blueprint choose(CompanionEntity c, CampData data) {
		long now = c.level().getGameTime();
		if (now - scannedAt < RESCAN && now >= scannedAt) {
			return damaged;
		}
		scannedAt = now;
		damaged = null;
		damagedKey = null;
		ServerLevel level = (ServerLevel) c.level();
		Container chest = SupplyChest.of(level).orElse(null);
		for (Structures.Entry e : Structures.ALL) {
			if (e.owner() != Role.BUILDER || e.id().equals(Structures.SUPPLY_CHEST) || !data.isCompleted(e.id()) || isSetAside(c, e.id())) {
				continue;
			}
			Optional<Blueprint> bp = Blueprints.forSite(data, e.id());
			if (bp.isEmpty() || data.site(e.id()).isEmpty()) {
				continue;
			}
			if (repairable(c, chest, gaps(level, data, e.id(), bp.get()))) {
				damaged = bp.get();
				damagedKey = e.id();
				return damaged;
			}
		}
		List<String> sites = new java.util.ArrayList<>(Blueprints.librarySites(data));
		sites.sort(String::compareTo);
		for (int k = 0; k < Math.min(2, sites.size()); k++) {
			String key = sites.get(Math.floorMod(libraryCursor + k, sites.size()));
			if (!Blueprints.isFinished(data, key) || isSetAside(c, key)) {
				continue;
			}
			Optional<Blueprint> bp = Blueprints.forSite(data, key);
			if (bp.isPresent() && repairable(c, chest, gaps(level, data, key, bp.get()))) {
				damaged = bp.get();
				damagedKey = key;
				libraryCursor += k;
				return damaged;
			}
		}
		libraryCursor += 2;
		return null;
	}

	/**
	 * Gaps in a finished building, the first eight of them also reported to the camp's material demand, so what they
	 * need (glass for a window left open, bricks for a flower pot) gets made even while nothing can fill them yet. Up
	 * to {@value #GAPS_LOOKED_AT} are looked at, so a torch knocked off a wall is still seen behind a row of carpets
	 * and windows the camp cannot make yet (lights come last in the build order).
	 */
	private static List<Placement> gaps(ServerLevel level, CampData data, String key, Blueprint bp) {
		List<Placement> gaps = BuildJob.missing(level, data, key, bp, GAPS_LOOKED_AT);
		String demandKey = "repair:" + key;
		if (gaps.isEmpty()) {
			MaterialDemand.clear(demandKey);
			return gaps;
		}
		java.util.Map<Stock, Integer> wanted = new java.util.EnumMap<>(Stock.class);
		for (Placement p : gaps.subList(0, Math.min(8, gaps.size()))) {
			Stock s = p.entry().material().stock();
			Stock extra = p.entry().material().extraStock();
			if (s != null) {
				wanted.merge(s, 1, Integer::sum);
			}
			if (extra != null) {
				wanted.merge(extra, 1, Integer::sum);
			}
		}
		MaterialDemand.report(level, demandKey, wanted);
		return gaps;
	}

	/** True if one of these gaps can be filled from what is carried or stored (with its fallback, if it has one). */
	private static boolean repairable(CompanionEntity c, @Nullable Container chest, List<Placement> gaps) {
		for (Placement p : gaps) {
			Blueprint.Entry e = p.entry();
			if (fillable(c, chest, e) || e.fallback() != null && fillable(c, chest, e.withFallback())) {
				return true;
			}
		}
		return false;
	}

	private static boolean fillable(CompanionEntity c, @Nullable Container chest, Blueprint.Entry e) {
		Stock s = e.material().stock();
		Stock extra = e.material().extraStock();
		return (s == null || Supplies.canMake(c, chest, s, 1)) && (extra == null || Supplies.canMake(c, chest, extra, 1));
	}

	@Override
	protected String siteKey(Blueprint plan) {
		return plan == damaged && damagedKey != null ? damagedKey : plan.id();
	}

	@Override
	public boolean start(CompanionEntity c) {
		scannedAt = -100_000; // look again now
		return super.start(c);
	}
}
