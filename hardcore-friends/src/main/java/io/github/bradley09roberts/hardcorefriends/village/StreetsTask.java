package io.github.bradley09roberts.hardcorefriends.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.role.terra.ChestFetch;
import io.github.bradley09roberts.hardcorefriends.ai.role.terra.Landscape;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SiteFinder;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * The landscaper lays the village's streets: the open stretch of every street of the town plan, three blocks wide,
 * on the ground as it lies. Grass and dirt become a trodden path (with a shovel, as Terra's camp paths); once the camp
 * is a Settlement the paths are surfaced with gravel (when the camp has some), and from the Town on with cobblestone:
 * the friends' own path block is dug up and the new block put in its place, from the supply chest's stock. Nothing but
 * the friends' own path and natural grass or dirt is touched, nothing under a building or its site, nothing by water
 * (the edit guard's rules), and only inside the camp, which the village has grown to cover.
 *
 * <p>The street cells are surveyed for the whole team in passes of a few hundred columns at a time, once every half
 * minute; each run does up to {@value #PER_RUN} cells.
 */
final class StreetsTask implements CompanionTask {
	static final String ID = "village.streets";
	private static final int PER_RUN = 16;
	private static final int SCAN_BUDGET = 192;
	private static final int RESCAN = 600;
	private static final double WORK_REACH = 2.5;
	private static final int FETCH = 16;
	private static final int PLACE_TRIES = 40;

	/** The team's survey of the streets: which cells still want a path, which want surfacing. */
	private static final class Survey {
		private @Nullable BlockPos centre;
		private int street;
		private int along = Integer.MIN_VALUE;
		private int across = -TownPlan.HALF;
		private long doneAt = Long.MIN_VALUE / 2;
		private boolean scanning;
		private final List<BlockPos> paths = new ArrayList<>();
		private final List<BlockPos> surfaces = new ArrayList<>();
		private final List<BlockPos> nextPaths = new ArrayList<>();
		private final List<BlockPos> nextSurfaces = new ArrayList<>();
	}

	private static Survey survey = new Survey();

	private final List<BlockPos> todo = new ArrayList<>();
	private boolean surfacing;
	private @Nullable BlockPos current;
	private int worked;
	private int placeTries;
	private boolean fetched;
	private boolean dug;

	static void clear() {
		survey = new Survey();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return surfacing ? "surfacing the village streets" : "laying the village streets";
	}

	/** The street surface the village has reached: null for trodden paths, else gravel or cobblestone. */
	private static @Nullable Block surface(VillageData v) {
		return switch (v.streetLevel()) {
			case 1 -> Blocks.GRAVEL;
			case 2 -> Blocks.COBBLESTONE;
			default -> null;
		};
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.isChild() || !(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		CampData camp = Camp.data(level.getServer());
		VillageData v = VillageData.get(level.getServer());
		if (!Camp.isCampLevel(level, camp) || v.centre().isEmpty() || v.openStreets().isEmpty()) {
			return 0;
		}
		if (Camp.isNight(level) && !WorldEditGuard.inCamp(c, c.blockPosition())) {
			return 0;
		}
		scan(level, camp, v);
		if (!survey.paths.isEmpty() && c.actions().hasTool(ItemTags.SHOVELS)) {
			return 44;
		}
		if (!survey.paths.isEmpty() && c.friendId().role() == Role.LANDSCAPER) {
			Speech.say(c, Line.NEED_TOOL, "shovel");
		}
		Block surface = surface(v);
		if (surface != null && !survey.surfaces.isEmpty() && (carried(c, surface.asItem()) > 0 || ChestFetch.chestHas(c, s -> s.is(surface.asItem())))) {
			return 36;
		}
		return 0;
	}

	private static int carried(CompanionEntity c, Item item) {
		return c.backpack().count(item);
	}

	/** Carries the street survey on, a few hundred columns at a time, and starts a new pass every half minute. */
	private static void scan(ServerLevel level, CampData camp, VillageData v) {
		Survey s = survey;
		BlockPos centre = v.centre().orElseThrow();
		long now = level.getGameTime();
		if (!centre.equals(s.centre)) {
			survey = s = new Survey();
			s.centre = centre;
		}
		if (!s.scanning) {
			if (now - s.doneAt < RESCAN && now >= s.doneAt) {
				return;
			}
			s.scanning = true;
			s.street = 0;
			s.along = Integer.MIN_VALUE;
			s.across = -TownPlan.HALF;
			s.nextPaths.clear();
			s.nextSurfaces.clear();
		}
		List<int[]> sites = SiteFinder.reservedBoxes(camp, "");
		Block surface = surface(v);
		int budget = SCAN_BUDGET;
		while (budget-- > 0) {
			if (s.street >= TownPlan.STREETS.size()) {
				s.paths.clear();
				s.paths.addAll(s.nextPaths);
				s.surfaces.clear();
				s.surfaces.addAll(s.nextSurfaces);
				s.scanning = false;
				s.doneAt = now;
				return;
			}
			int[] open = v.open(s.street);
			if (open == null) {
				s.street++;
				s.along = Integer.MIN_VALUE;
				continue;
			}
			if (s.along == Integer.MIN_VALUE) {
				s.along = open[0];
				s.across = -TownPlan.HALF;
			}
			if (s.along > open[1]) {
				s.street++;
				s.along = Integer.MIN_VALUE;
				continue;
			}
			BlockPos column = TownPlan.column(TownPlan.STREETS.get(s.street), centre, s.along, s.across);
			if (++s.across > TownPlan.HALF) {
				s.across = -TownPlan.HALF;
				s.along++;
			}
			if (underSite(column, sites) || Camp.horizontalDistSqr(column, centre) <= 4) {
				continue;
			}
			BlockPos ground = Landscape.ground(level, column.getX(), column.getZ(), centre.getY() + PlotSurvey.MAX_RISE,
				centre.getY() - PlotSurvey.MAX_RISE);
			if (ground == null || !level.getFluidState(ground).isEmpty()) {
				continue;
			}
			BlockState state = level.getBlockState(ground);
			BlockState above = level.getBlockState(ground.above());
			if (!Landscape.isOpen(above)) {
				continue;
			}
			if (Landscape.isEarth(state)) {
				s.nextPaths.add(ground);
			} else if (surface != null && !state.is(surface) && (state.is(Blocks.DIRT_PATH) || state.is(Blocks.GRAVEL))
				&& camp.isPlacedByFriends(level, ground)) {
				s.nextSurfaces.add(ground);
			}
		}
	}

	private static boolean underSite(BlockPos column, List<int[]> sites) {
		for (int[] b : sites) {
			if (column.getX() >= b[0] && column.getX() <= b[2] && column.getZ() >= b[1] && column.getZ() <= b[3]) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		VillageData v = VillageData.get(level.getServer());
		todo.clear();
		current = null;
		worked = 0;
		fetched = false;
		dug = false;
		placeTries = 0;
		surfacing = survey.paths.isEmpty() || !c.actions().hasTool(ItemTags.SHOVELS);
		List<BlockPos> source = surfacing ? survey.surfaces : survey.paths;
		if (surfacing && surface(v) == null) {
			return false;
		}
		List<BlockPos> nearest = new ArrayList<>(source);
		nearest.sort(Comparator.comparingDouble(p -> p.distSqr(c.blockPosition())));
		todo.addAll(nearest.subList(0, Math.min(PER_RUN * 2, nearest.size())));
		if (todo.isEmpty()) {
			return false;
		}
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		VillageData v = VillageData.get(level.getServer());
		Block surface = surface(v);
		if (surfacing) {
			if (surface == null) {
				return finish();
			}
			Item item = surface.asItem();
			if (!fetched && carried(c, item) < Math.min(FETCH, todo.size())) {
				ChestFetch.Result r = ChestFetch.step(c, s -> s.is(item), FETCH - carried(c, item));
				if (r == ChestFetch.Result.RUNNING) {
					return TaskStatus.RUNNING;
				}
				fetched = true;
				if (carried(c, item) == 0) {
					return finish();
				}
			}
		}
		if (current == null) {
			if (worked >= PER_RUN || todo.isEmpty()) {
				return finish();
			}
			current = todo.removeFirst();
			dug = false;
			placeTries = 0;
		}
		BlockPos cell = current;
		Actions actions = c.actions();
		if (!actions.canReach(cell) || c.position().distanceTo(Vec3.atBottomCenterOf(cell.above())) > WORK_REACH + 1) {
			actions.walkTo(cell.above(), WORK_REACH);
			if (actions.isStuck()) {
				actions.stopWalking();
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		actions.stopWalking();
		BlockState above = level.getBlockState(cell.above());
		if (Landscape.isClutter(above)) {
			if (actions.mine(cell.above(), Reason.LANDSCAPE) == Actions.Result.FAILED) {
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		return surfacing ? surfaceStep(c, level, cell, surface) : pathStep(c, level, cell);
	}

	/** Grass or dirt to a trodden path, with a shovel. */
	private TaskStatus pathStep(CompanionEntity c, ServerLevel level, BlockPos cell) {
		BlockState state = level.getBlockState(cell);
		if (!Landscape.isEarth(state) || !Landscape.isOpen(level.getBlockState(cell.above()))) {
			current = null;
			return TaskStatus.RUNNING;
		}
		BlockState path = Blocks.DIRT_PATH.defaultBlockState();
		WorldEditGuard.Verdict verdict = WorldEditGuard.canTransform(c, cell, path, Reason.LANDSCAPE);
		if (!verdict.allowed()) {
			if (!"pacing".equals(verdict.why())) {
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		if (!c.actions().hasTool(ItemTags.SHOVELS)) {
			return finish();
		}
		if (c.actions().transform(cell, path, Reason.LANDSCAPE, ItemTags.SHOVELS)) {
			worked++;
		}
		current = null;
		return TaskStatus.RUNNING;
	}

	/**
	 * The friends' own path block dug up and the street surface put in its place. The surface block is carried before
	 * the path is dug, and nobody may stand on the cell, so a street is only ever left with a hole if the new block is
	 * refused for long (then the dug-up earth goes back).
	 */
	private TaskStatus surfaceStep(CompanionEntity c, ServerLevel level, BlockPos cell, Block surface) {
		Item item = surface.asItem();
		BlockState state = level.getBlockState(cell);
		CampData camp = Camp.data(level.getServer());
		if (!dug) {
			if (state.is(surface) || !(state.is(Blocks.DIRT_PATH) || state.is(Blocks.GRAVEL)) || !camp.isPlacedByFriends(level, cell)
				|| carried(c, item) == 0 || someoneOn(level, cell)) {
				current = null;
				return carried(c, item) == 0 ? finish() : TaskStatus.RUNNING;
			}
			if (WorldEditGuard.breachesFluid(level, cell)) {
				current = null;
				return TaskStatus.RUNNING;
			}
			Actions.Result r = c.actions().mine(cell, Reason.LANDSCAPE);
			if (r == Actions.Result.FAILED) {
				current = null;
			} else if (r == Actions.Result.DONE) {
				dug = true;
			}
			return TaskStatus.RUNNING;
		}
		if (!level.getBlockState(cell).isAir()) {
			current = null; // something filled it meanwhile
			return TaskStatus.RUNNING;
		}
		if (level.getGameTime() - c.lastEditTick() < 4) {
			return TaskStatus.RUNNING;
		}
		if (c.actions().place(cell, surface.defaultBlockState(), s -> s.is(item), Reason.LANDSCAPE)) {
			worked++;
			current = null;
			return TaskStatus.RUNNING;
		}
		if (++placeTries > PLACE_TRIES) {
			// Refused for long (someone keeps standing there): put the earth back rather than leave a hole.
			c.actions().place(cell, Blocks.DIRT.defaultBlockState(), s -> s.is(Items.DIRT), Reason.LANDSCAPE);
			current = null;
		}
		return TaskStatus.RUNNING;
	}

	private static boolean someoneOn(ServerLevel level, BlockPos cell) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(cell.above()).inflate(0.3)).isEmpty();
	}

	private TaskStatus finish() {
		current = null;
		return worked > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().cancelMining();
		current = null;
		todo.clear();
	}

	@Override
	public int successCooldown() {
		return 60;
	}

	@Override
	public int failureCooldown() {
		return 600;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}

	/** For {@code /friends village}: how many street cells still want a path and a surface. */
	static Map<String, Integer> progress() {
		return Map.of("paths", survey.paths.size(), "surfaces", survey.surfaces.size());
	}
}
