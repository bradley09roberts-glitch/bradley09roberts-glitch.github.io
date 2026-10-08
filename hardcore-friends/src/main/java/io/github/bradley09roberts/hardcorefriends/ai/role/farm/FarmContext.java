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

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * What Fern knows about the fields, shared by all her routines. Two cached surveys keep the cost bounded:
 * <ul>
 * <li>a local 3D scan up to 16 blocks around Fern (and around the farm when she is away from it), refreshed every
 * 5 seconds, which lists ripe and growing crops, empty farmland, shore water and crafting tables;</li>
 * <li>a camp-wide surface survey, spread over many calls (a few hundred columns at a time), which counts farmland
 * and finds water anywhere inside the camp.</li>
 * </ul>
 */
public final class FarmContext {
	private static final int LOCAL_RADIUS = 16;
	private static final int LOCAL_DY = 4;
	private static final int LOCAL_INTERVAL = 100;
	private static final int COLUMNS_PER_STEP = 600;
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

	// camp survey (one pass covers every column of the camp disc)
	private int surveyCursor;
	private int surveyRadius = -1;
	private @Nullable BlockPos surveyCentre;
	private int passFarmland;
	private long passSumX;
	private long passSumZ;
	private final List<BlockPos> passWater = new ArrayList<>();
	private int farmland = -1;
	private @Nullable BlockPos farmlandCentre;
	private List<BlockPos> campWater = List.of();
	private long lastSurveyStep = Long.MIN_VALUE;

	// ------------------------------------------------------------ local scan

	/** Rescans around the farm if the last scan is older than 5 seconds. */
	public void refresh(CompanionEntity c) {
		long now = c.level().getGameTime();
		if (now - localScanAt >= LOCAL_INTERVAL) {
			localScanAt = now;
			scanLocal(c);
		}
	}

	/** Forces a rescan on the next {@link #refresh}, after the fields changed. */
	public void invalidate() {
		localScanAt = Long.MIN_VALUE / 2;
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
		if (farmlandCentre != null) {
			return farmlandCentre;
		}
		BlockPos home = c.homePos();
		return campWater.stream().min(Comparator.comparingDouble(w -> w.distSqr(home))).orElse(null);
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

	/**
	 * Surveys the next few hundred surface columns of the camp. Call once per scoring round; a full pass over a
	 * radius-24 camp takes about three calls, a radius-40 camp about nine.
	 */
	public void stepCampSurvey(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (level.getGameTime() == lastSurveyStep) {
			return; // several routines may ask in the same scoring round
		}
		lastSurveyStep = level.getGameTime();
		CampData data = Camp.data(level.getServer());
		BlockPos centre = c.homePos();
		int radius = Camp.radius(data);
		if (surveyCentre == null || !surveyCentre.equals(centre) || surveyRadius != radius) {
			surveyCentre = centre;
			surveyRadius = radius;
			restartPass();
			farmland = -1;
		}
		int side = 2 * radius + 1;
		int total = side * side;
		int budget = COLUMNS_PER_STEP;
		while (budget > 0 && surveyCursor < total) {
			int dx = surveyCursor % side - radius;
			int dz = surveyCursor / side - radius;
			surveyCursor++;
			if (dx * dx + dz * dz > radius * radius) {
				continue;
			}
			budget--;
			int x = centre.getX() + dx;
			int z = centre.getZ() + dz;
			if (!level.hasChunkAt(x, z)) {
				continue;
			}
			int y = Ground.surfaceY(level, x, z, centre.getY());
			if (y == Ground.NONE) {
				continue;
			}
			BlockPos top = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(top);
			if (state.is(Blocks.FARMLAND)) {
				passFarmland++;
				passSumX += x;
				passSumZ += z;
			} else if (Crops.isWaterSource(state) && passWater.size() < MAX_WATER) {
				passWater.add(top);
			}
		}
		if (surveyCursor >= total) {
			farmland = passFarmland;
			farmlandCentre = passFarmland > 0
				? new BlockPos((int) Math.floorDiv(passSumX, passFarmland), centre.getY(), (int) Math.floorDiv(passSumZ, passFarmland))
				: null;
			if (farmlandCentre != null) {
				int fy = Ground.surfaceY(level, farmlandCentre.getX(), farmlandCentre.getZ(), centre.getY());
				farmlandCentre = new BlockPos(farmlandCentre.getX(), fy == Ground.NONE ? centre.getY() : fy + 1, farmlandCentre.getZ());
			}
			campWater = List.copyOf(passWater);
			restartPass();
		}
	}

	private void restartPass() {
		surveyCursor = 0;
		passFarmland = 0;
		passSumX = 0;
		passSumZ = 0;
		passWater.clear();
	}

	/** Farmland counted in the camp by the last complete survey, or -1 before the first pass finishes. */
	public int campFarmland() {
		return farmland;
	}

	/** Surface water sources found in the camp by the last complete survey. */
	public List<BlockPos> campWater() {
		return campWater;
	}

	public boolean campSurveyed() {
		return farmland >= 0;
	}

	/** Counts newly tilled farmland straight away, so the stage cap holds between survey passes. */
	public void noteTilled(int n) {
		if (farmland >= 0) {
			farmland += n;
		}
	}

	/** Records water Fern placed herself, so the farm plot is known before the next survey pass. */
	public void noteWater(BlockPos pos) {
		List<BlockPos> list = new ArrayList<>(campWater);
		list.add(pos.immutable());
		campWater = List.copyOf(list);
	}
}
