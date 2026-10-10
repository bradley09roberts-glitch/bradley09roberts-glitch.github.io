package io.github.bradley09roberts.hardcorefriends.architecture;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.BuildJob;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SiteClearing;
import io.github.bradley09roberts.hardcorefriends.camp.SiteFinder;
import io.github.bradley09roberts.hardcorefriends.camp.SiteGrading;
import io.github.bradley09roberts.hardcorefriends.camp.build.Part;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Building library plans on chosen spots, for the packages that decide where buildings go (the village's houses and
 * civic buildings, the market's shops and workplaces). A building is a "site" with a key of the caller's choosing
 * ({@code village.house.3}): reserve it on a spot that {@link #check} approves, then give a friend a job that runs
 * {@link #job} until {@link #isFinished}; the builders fetch, craft and place everything with real materials through
 * the edit guard, exactly as for the camp's own buildings. {@link #markers} gives the world positions of a plan's
 * named spots (its beds, its door, its counter) once built.
 *
 * <p>Site keys must not clash with the camp's own structure ids ("cabin", "storehouse"...).
 */
public final class Construction {
	/** Called on the server thread when a library building is finished: (level, site key, plan). */
	public interface Finished {
		void finished(ServerLevel level, String siteKey, Blueprint plan);
	}

	/** Listeners told when a library site's building is finished. */
	public static final List<Finished> FINISHED = new CopyOnWriteArrayList<>();

	private Construction() {
	}

	/**
	 * Why a plan cannot go here, or empty if it can: every footprint column must be loaded firm natural ground (or the
	 * friends' own foundations and paths) at most one block below {@code origin}'s height, the building's space must
	 * be empty but for plants, snow and natural leaves, and nothing player-built may be within two blocks.
	 * {@code origin} is the plan's local (0, 0, 0) on the ground floor (one above the ground).
	 */
	public static Optional<String> check(ServerLevel level, Blueprint plan, BlockPos origin, int rotation) {
		CampData data = Camp.data(level.getServer());
		int[] box = plan.footprint(origin, rotation);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = box[0]; x <= box[2]; x++) {
			for (int z = box[1]; z <= box[3]; z++) {
				m.set(x, origin.getY() - 1, z);
				if (!level.isLoaded(m)) {
					return Optional.of("part of the spot is not loaded");
				}
				BlockState ground = level.getBlockState(m);
				BlockState dip = level.getBlockState(m.below());
				boolean firm = SiteFinder.isNaturalGround(ground) || data.isPlacedByFriends(level, m) && !ground.isAir();
				boolean oneDip = (ground.isAir() || WorldEditGuard.isClearablePlant(ground)) && ground.getFluidState().isEmpty()
					&& (SiteFinder.isNaturalGround(dip) || data.isPlacedByFriends(level, m.below()));
				if (!firm && !(plan.hasFoundations() && oneDip)) {
					return Optional.of("the ground is not firm and level at " + x + " " + z);
				}
				for (int y = origin.getY(); y < origin.getY() + plan.height(); y++) {
					m.set(x, y, z);
					BlockState s = level.getBlockState(m);
					if (!(s.isAir() || WorldEditGuard.isClearablePlant(s) || SiteClearing.isNaturalLeaves(s)) || !s.getFluidState().isEmpty()) {
						return Optional.of("something is in the way at " + x + " " + y + " " + z);
					}
				}
			}
		}
		for (int x = box[0] - 2; x <= box[2] + 2; x++) {
			for (int z = box[1] - 2; z <= box[3] + 2; z++) {
				for (int y = origin.getY() - 2; y <= origin.getY() + plan.height() + 1; y++) {
					m.set(x, y, z);
					if (!level.isLoaded(m)) {
						continue;
					}
					BlockState s = level.getBlockState(m);
					if ((s.is(ModTags.BUILD_MARKERS) || s.hasBlockEntity()) && !data.isPlacedByFriends(level, m)) {
						return Optional.of("too close to something a player built");
					}
				}
			}
		}
		for (int[] other : SiteFinder.reservedBoxes(data, "")) {
			if (box[0] - 1 <= other[2] && box[2] + 1 >= other[0] && box[1] - 1 <= other[3] && box[3] + 1 >= other[1]) {
				return Optional.of("it overlaps another building's site");
			}
		}
		return Optional.empty();
	}

	/**
	 * Reserves a site for a library plan under {@code siteKey}, remembering the plan and the wood its wooden parts
	 * should prefer (null: the plan's own, else the wood the camp has most of). Returns false if the key is a camp
	 * structure's or already in use.
	 */
	public static boolean reserve(ServerLevel level, String siteKey, Blueprint plan, BlockPos origin, int rotation, @Nullable String wood) {
		CampData data = Camp.data(level.getServer());
		if (Blueprints.isCampStructure(siteKey) || data.site(siteKey).isPresent()) {
			return false;
		}
		String chosen = wood != null ? wood : Styles.woodFor(level, origin, plan);
		SiteFinder.reserve(data, siteKey, plan, List.of(new Part(origin.immutable(), Math.floorMod(rotation, 4))), chosen);
		return true;
	}

	/**
	 * A friend's next run at building (or, with {@code repair}, mending) the site, or null if the site or its plan is
	 * gone. Tick it until it returns success or failure, and {@code stop()} it when done, exactly as the camp's own
	 * building job does ({@code ai.role.build.BlueprintTask} shows how).
	 */
	public static @Nullable BuildJob job(CompanionEntity c, String siteKey, WorldEditGuard.Reason reason, boolean repair) {
		if (!(c.level() instanceof ServerLevel level)) {
			return null;
		}
		CampData data = Camp.data(level.getServer());
		Optional<Blueprint> plan = Blueprints.forSite(data, siteKey);
		if (plan.isEmpty() || data.site(siteKey).isEmpty()) {
			return null;
		}
		return new BuildJob(c, plan.get(), siteKey, reason, repair);
	}

	/** The plan a site is built from, if it still exists. */
	public static Optional<Blueprint> planOf(ServerLevel level, String siteKey) {
		return Blueprints.forSite(Camp.data(level.getServer()), siteKey);
	}

	/** True once the site's building has been finished (it may have been damaged since: see {@link BuildJob#missing}). */
	public static boolean isFinished(ServerLevel level, String siteKey) {
		return Blueprints.isFinished(Camp.data(level.getServer()), siteKey);
	}

	/** How far the building has got, 0 to 1 (by the entries placed in build order). */
	public static double progress(ServerLevel level, String siteKey) {
		CampData data = Camp.data(level.getServer());
		Optional<CampData.Site> site = data.site(siteKey);
		Optional<Blueprint> plan = Blueprints.forSite(data, siteKey);
		if (site.isEmpty() || plan.isEmpty()) {
			return 0;
		}
		if (Blueprints.isFinished(data, siteKey)) {
			return 1;
		}
		int total = Blueprints.placements(plan.get(), SiteFinder.parts(data, siteKey, plan.get())).size();
		return total == 0 ? 1 : Math.min(1.0, site.get().progress / (double) total);
	}

	/** World positions of one of the plan's markers on this site ({@code bed}, {@code door}, {@code chest}...). */
	public static List<BlockPos> markers(ServerLevel level, String siteKey, String marker) {
		CampData data = Camp.data(level.getServer());
		Optional<Blueprint> plan = Blueprints.forSite(data, siteKey);
		List<BlockPos> list = new ArrayList<>();
		if (plan.isEmpty()) {
			return list;
		}
		for (Part part : SiteFinder.parts(data, siteKey, plan.get())) {
			list.addAll(plan.get().markerPositions(marker, part.origin(), part.rotation()));
		}
		return list;
	}

	/**
	 * Lets a site go: forgets the reservation, the plan record, any felling or levelling planned for it and anything it
	 * was asking for. Blocks already built stay as they are (they are still the friends' own, so they can be built on or
	 * taken down later).
	 */
	public static void release(ServerLevel level, String siteKey) {
		CampData data = Camp.data(level.getServer());
		if (Blueprints.isCampStructure(siteKey)) {
			return;
		}
		data.removeSite(siteKey);
		Blueprints.forgetRecord(data, siteKey);
		SiteClearing.forget(data, siteKey);
		SiteGrading.forget(data, siteKey);
		MaterialDemand.clear(siteKey);
		CampNeeds.clearBuildShortage(siteKey);
	}

	/** Every library site in the camp (built or not), by key. */
	public static List<String> sites(ServerLevel level) {
		return Blueprints.librarySites(Camp.data(level.getServer()));
	}

	/** Tells the listeners a library building is finished. */
	public static void fireFinished(ServerLevel level, String siteKey, Blueprint plan) {
		for (Finished f : FINISHED) {
			try {
				f.finished(level, siteKey, plan);
			} catch (RuntimeException e) {
				HardcoreFriends.LOGGER.error("A building-finished listener failed for {}", siteKey, e);
			}
		}
	}
}
