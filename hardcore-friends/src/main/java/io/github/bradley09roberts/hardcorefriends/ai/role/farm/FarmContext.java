package io.github.bradley09roberts.hardcorefriends.ai.role.farm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.role.TeamCache;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * What a farmer knows about the fields, shared by one friend's farming routines (Fern's, or anyone helping out).
 * Two cached surveys keep the cost bounded:
 * <ul>
 * <li>a local 3D scan up to 16 blocks around the friend (and around the farm when they are away from it), refreshed
 * every 5 seconds, which lists ripe and growing crops, empty farmland, shore water and crafting tables;</li>
 * <li>a camp-wide surface survey ({@link CampSurvey}, one for the whole team), spread over many calls (a few hundred
 * columns at a time), which counts farmland and finds water anywhere inside the camp.</li>
 * </ul>
 */
public final class FarmContext {
	private static final int LOCAL_RADIUS = 16;
	private static final int LOCAL_DY = 4;
	private static final int LOCAL_INTERVAL = 100;
	private static final int MAX_WATER = 64;
	/** Fern does not till right in the middle of the camp, where the fire and paths go. */
	private static final int CAMP_CORE = 3;

	// local scan
	private long localScanAt = Long.MIN_VALUE / 2;
	private final List<BlockPos> ripe = new ArrayList<>();
	private final List<BlockPos> growing = new ArrayList<>();
	private final List<BlockPos> emptyFarmland = new ArrayList<>();
	private final List<BlockPos> shoreWater = new ArrayList<>();
	private @Nullable BlockPos table;
	private @Nullable List<BlockPos> tillCache;
	private long copiedAt = Long.MIN_VALUE;

	/** The latest local scan anyone made, which friends other than the farmer reuse instead of scanning again. */
	private static final class Snapshot {
		private long at = Long.MIN_VALUE / 2;
		private List<BlockPos> ripe = List.of();
		private List<BlockPos> growing = List.of();
		private List<BlockPos> emptyFarmland = List.of();
		private List<BlockPos> shoreWater = List.of();
		private @Nullable BlockPos table;
	}

	private static Snapshot snapshot(CompanionEntity c) {
		return TeamCache.get((ServerLevel) c.level(), "farm.local", Snapshot::new);
	}

	// ------------------------------------------------------------ local scan

	/**
	 * Rescans around the farm if the last scan is older than 5 seconds. The farmer keeps her own schedule, exactly as
	 * when she farmed alone; anyone else reuses the team's latest scan while it is fresh, so a field is not scanned
	 * once for every friend who might lend a hand.
	 */
	public void refresh(CompanionEntity c) {
		long now = c.level().getGameTime();
		Snapshot shared = snapshot(c);
		if (c.friendId().role() == Role.FARMER) {
			if (now - localScanAt >= LOCAL_INTERVAL) {
				localScanAt = now;
				scanLocal(c);
				publish(shared, now);
			}
			return;
		}
		if (now - shared.at >= LOCAL_INTERVAL) {
			localScanAt = now;
			scanLocal(c);
			publish(shared, now);
		} else if (copiedAt != shared.at) {
			copiedAt = shared.at;
			copy(ripe, shared.ripe);
			copy(growing, shared.growing);
			copy(emptyFarmland, shared.emptyFarmland);
			copy(shoreWater, shared.shoreWater);
			if (shared.table != null) {
				table = shared.table;
			}
			tillCache = null;
		}
	}

	private void publish(Snapshot shared, long now) {
		shared.at = now;
		shared.ripe = List.copyOf(ripe);
		shared.growing = List.copyOf(growing);
		shared.emptyFarmland = List.copyOf(emptyFarmland);
		shared.shoreWater = List.copyOf(shoreWater);
		shared.table = table;
		copiedAt = now;
	}

	private static void copy(List<BlockPos> into, List<BlockPos> from) {
		into.clear();
		into.addAll(from);
	}

	/** Forces a rescan on the next {@link #refresh}, after the fields changed (for everyone sharing the scan too). */
	public void invalidate(CompanionEntity c) {
		localScanAt = Long.MIN_VALUE / 2;
		snapshot(c).at = Long.MIN_VALUE / 2;
	}

	public List<BlockPos> ripe() {
		return ripe;
	}

	public List<BlockPos> growing() {
		return growing;
	}

	public List<BlockPos> emptyFarmland() {
		return emptyFarmland;
	}

	/** A crafting table inside the camp that Fern has seen or Oak built, if any. */
	public Optional<BlockPos> craftingTable(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (table != null && level.isLoaded(table) && level.getBlockState(table).is(Blocks.CRAFTING_TABLE)) {
			return Optional.of(table);
		}
		CampData data = Camp.data(level.getServer());
		Optional<CampData.Site> site = data.site(Structures.CRAFTING_TABLE);
		if (site.isPresent()) {
			for (BlockPos p : BlockPos.betweenClosed(site.get().origin.offset(-1, -1, -1), site.get().origin.offset(1, 1, 1))) {
				if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.CRAFTING_TABLE)) {
					table = p.immutable();
					return Optional.of(table);
				}
			}
		}
		return Optional.empty();
	}

	/** Where Fern's fields are: the farm plot, else the middle of the farmland in camp, else null. */
	public @Nullable BlockPos farmAnchor(CompanionEntity c) {
		CampData data = Camp.data(c.level().getServer());
		Optional<CampData.Site> plot = data.site(Structures.FARM_PLOT);
		if (plot.isPresent()) {
			return plot.get().origin;
		}
		CampSurvey survey = survey(c);
		if (survey.farmlandCentre() != null) {
			return survey.farmlandCentre();
		}
		BlockPos home = c.homePos();
		return survey.water().stream().min(Comparator.comparingDouble(w -> w.distSqr(home))).orElse(null);
	}

	private void scanLocal(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		ripe.clear();
		growing.clear();
		emptyFarmland.clear();
		shoreWater.clear();
		tillCache = null;
		BlockPos here = c.blockPosition();
		BlockPos anchor = farmAnchor(c);
		// Around Fern, and around the farm too when she is away from it (skipping the overlap).
		scanBox(c, level, here, null);
		if (anchor != null && Camp.horizontalDistSqr(anchor, here) > 8 * 8) {
			scanBox(c, level, anchor, here);
		}
		BlockPos sortFrom = here;
		ripe.sort(Comparator.comparingDouble(p -> p.distSqr(sortFrom)));
		growing.sort(Comparator.comparingDouble(p -> p.distSqr(sortFrom)));
		emptyFarmland.sort(Comparator.comparingDouble(p -> p.distSqr(sortFrom)));
		BlockPos waterFrom = anchor != null ? anchor : here;
		shoreWater.sort(Comparator.comparingDouble(p -> p.distSqr(waterFrom)));
	}

	/** Scans one 33×9×33 box, leaving out blocks inside the box around {@code skip} (already scanned). */
	private void scanBox(CompanionEntity c, ServerLevel level, BlockPos centre, @Nullable BlockPos skip) {
		BlockPos here = c.blockPosition();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -LOCAL_RADIUS; dx <= LOCAL_RADIUS; dx++) {
			for (int dz = -LOCAL_RADIUS; dz <= LOCAL_RADIUS; dz++) {
				int x = centre.getX() + dx;
				int z = centre.getZ() + dz;
				if (!level.hasChunkAt(x, z)) {
					continue;
				}
				for (int dy = -LOCAL_DY; dy <= LOCAL_DY; dy++) {
					m.set(x, centre.getY() + dy, z);
					if (skip != null && Math.abs(x - skip.getX()) <= LOCAL_RADIUS && Math.abs(z - skip.getZ()) <= LOCAL_RADIUS
						&& Math.abs(m.getY() - skip.getY()) <= LOCAL_DY) {
						continue;
					}
					BlockState state = level.getBlockState(m);
					if (state.isAir()) {
						continue;
					}
					if (Crops.isRipe(level, m, state)) {
						addIfInCamp(c, ripe, m);
					} else if (Crops.isGrowing(state)) {
						addIfInCamp(c, growing, m);
					} else if (state.is(Blocks.FARMLAND)) {
						if (level.getBlockState(m.above()).isAir() && !Crops.besideStem(level, m)) {
							addIfInCamp(c, emptyFarmland, m);
						}
					} else if (Crops.isWaterSource(state)) {
						if (shoreWater.size() < MAX_WATER && isShore(level, m)) {
							addIfInCamp(c, shoreWater, m);
						}
					} else if (state.is(Blocks.CRAFTING_TABLE) && WorldEditGuard.inCamp(c, m)) {
						if (table == null || table.distSqr(here) > m.distSqr(here)) {
							table = m.immutable();
						}
					}
				}
			}
		}
	}

	private static void addIfInCamp(CompanionEntity c, List<BlockPos> list, BlockPos pos) {
		if (WorldEditGuard.inCamp(c, pos)) {
			list.add(pos.immutable());
		}
	}

	private static boolean isShore(ServerLevel level, BlockPos water) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (!Crops.isWater(level.getBlockState(water.relative(d)))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Grass or dirt near shore water that Fern may turn into farmland, nearest the farm first. Cached until the next
	 * local scan. Each candidate has air or a clearable plant above, is inside the camp but outside its core, was not
	 * placed by the friends (paths, foundations) and is not next to anything player-built.
	 */
	public List<BlockPos> tillCandidates(CompanionEntity c, int limit) {
		refresh(c);
		if (tillCache != null) {
			return tillCache;
		}
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		BlockPos home = c.homePos();
		Set<BlockPos> found = new LinkedHashSet<>();
		int checked = 0;
		for (BlockPos water : shoreWater) {
			if (found.size() >= limit || ++checked > 24) {
				break;
			}
			List<BlockPos> ring = new ArrayList<>();
			for (int dy = 0; dy >= -1; dy--) {
				for (int dx = -4; dx <= 4; dx++) {
					for (int dz = -4; dz <= 4; dz++) {
						ring.add(water.offset(dx, dy, dz));
					}
				}
			}
			ring.sort(Comparator.comparingDouble(p -> p.distSqr(water)));
			for (BlockPos p : ring) {
				if (found.size() >= limit) {
					break;
				}
				if (!found.contains(p) && isTillSpot(c, level, data, home, p, false)) {
					found.add(p);
				}
			}
		}
		tillCache = new ArrayList<>(found);
		return tillCache;
	}

	/**
	 * Whether Fern may till this block now (see {@link #tillCandidates}). Pass {@code checkWater = false} when the
	 * block is already known to lie within reach of water.
	 */
	public static boolean isTillSpot(CompanionEntity c, ServerLevel level, CampData data, BlockPos home, BlockPos p,
			boolean checkWater) {
		if (!level.isLoaded(p) || !Crops.isTillable(level.getBlockState(p))) {
			return false;
		}
		BlockState above = level.getBlockState(p.above());
		if (!above.isAir() && !(WorldEditGuard.isClearablePlant(above) && !above.is(Blocks.SNOW))) {
			return false;
		}
		if (!WorldEditGuard.inCamp(c, p) || Camp.horizontalDistSqr(home, p) <= CAMP_CORE * CAMP_CORE) {
			return false;
		}
		if (data.isPlacedByFriends(level, p) || nearBuild(level, data, p)) {
			return false;
		}
		return !checkWater || Crops.nearWater(level, p);
	}

	/** Anything player-made (other than farmland and paths) or a block entity right next to this block. */
	private static boolean nearBuild(ServerLevel level, CampData data, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 2, 1))) {
			BlockState s = level.getBlockState(p);
			if ((s.is(ModTags.BUILD_MARKERS) || s.hasBlockEntity()) && !s.is(Blocks.FARMLAND) && !s.is(Blocks.DIRT_PATH)
				&& !data.isPlacedByFriends(level, p)) {
				return true;
			}
		}
		return false;
	}

	// ----------------------------------------------------------- camp survey

	/** The camp survey for this friend's dimension, shared by every friend (see {@link CampSurvey}). */
	private static CampSurvey survey(CompanionEntity c) {
		return CampSurvey.of((ServerLevel) c.level());
	}

	/**
	 * Surveys the next few hundred surface columns of the camp. Call once per scoring round; a full pass over a
	 * radius-24 camp takes about three calls, a radius-40 camp about nine. The survey is shared, so however many
	 * friends call this it moves on at most once a second.
	 */
	public void stepCampSurvey(CompanionEntity c) {
		survey(c).step(c);
	}

	/** Farmland counted in the camp by the last complete survey, or -1 before the first pass finishes. */
	public int campFarmland(CompanionEntity c) {
		return survey(c).farmland();
	}

	/** Surface water sources found in the camp by the last complete survey. */
	public List<BlockPos> campWater(CompanionEntity c) {
		return survey(c).water();
	}

	public boolean campSurveyed(CompanionEntity c) {
		return survey(c).farmland() >= 0;
	}

	/** Counts newly tilled farmland straight away, so the stage cap holds between survey passes. */
	public void noteTilled(CompanionEntity c, int n) {
		survey(c).noteTilled(n);
	}

	/** Records water placed for the farm, so the farm plot is known before the next survey pass. */
	public void noteWater(CompanionEntity c, BlockPos pos) {
		survey(c).noteWater(pos);
	}
}
