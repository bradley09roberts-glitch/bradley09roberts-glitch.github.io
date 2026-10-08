package io.github.bradley09roberts.hardcorefriends.progress.work;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.role.TeamCache;
import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.Crops;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.EditSteps;
import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.Pen;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressData;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Sugar cane for paper and books, grown while Sage's plan wants books. Cane grows on dirt, grass or sand right beside
 * water: the farmer plants it on such ground inside the camp (beside the farm's water or other still water), harvests
 * the stalks above the bottom segment (the root stays and grows again) and replants empty spots. With nowhere to plant,
 * she makes a small cane patch: a one-block pool in a level patch of grass near the farm (tilled, dug out and filled
 * from a water bucket, which she keeps empty afterwards, like the farm's own pond), with cane on the four sides. The
 * ground round a patch is kept out of the tilling ({@link #reservedForCane}). Everything goes through the guard's
 * {@code FARM} rules (only the tops of cane, only inside the camp). Known spots are looked for at most every
 * {@value #SCAN_INTERVAL} ticks for the whole team.
 */
public final class CaneTask implements CompanionTask {
	private static final int SCAN_INTERVAL = 600;
	private static final int SCAN_RADIUS = 12;
	private static final int MAX_SPOTS = 12;
	private static final int MAX_HARVEST = 16;
	private static final int MAX_PLANT = 8;
	private static final int CAMP_CORE = 3;

	private enum Mode {
		HARVEST,
		PLANT,
		PATCH
	}

	private enum PatchStep {
		FETCH,
		TILL,
		DIG,
		POUR
	}

	/** The team's list of cane ground: where cane stands or can be planted (the block under the cane). */
	private static final class Spots {
		private List<BlockPos> ground = List.of();
		private long at = Long.MIN_VALUE / 2;
	}

	private Mode mode = Mode.HARVEST;
	private PatchStep patchStep = PatchStep.FETCH;
	private @Nullable BlockPos patch;
	private int done;

	@Override
	public String id() {
		return "fern.sugar_cane";
	}

	@Override
	public String describe() {
		return "tending the sugar cane";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || !FriendsConfig.get().allowWorldEditing) {
			return 0;
		}
		MinecraftServer server = level.getServer();
		CampData data = Camp.data(server);
		if (!Camp.isCampLevel(level, data) || Camp.isNight(level)) {
			return 0;
		}
		boolean wanted = ProgressPlan.wants(server, Items.SUGAR_CANE);
		List<BlockPos> spots = spots(level);
		double weight = ProgressPlan.weight(server, Items.SUGAR_CANE);
		int tall = 0;
		int empty = 0;
		for (BlockPos g : spots) {
			if (!level.isLoaded(g)) {
				continue;
			}
			BlockState above = level.getBlockState(g.above());
			if (above.is(Blocks.SUGAR_CANE) && level.getBlockState(g.above(2)).is(Blocks.SUGAR_CANE)) {
				tall++;
			} else if (above.isAir()) {
				empty++;
			}
		}
		if (tall > 0 && (wanted || tall >= 4)) {
			return 42 * weight;
		}
		if (!wanted) {
			return 0;
		}
		if (empty > 0 && hasCane(c)) {
			return 40 * weight;
		}
		if (spots.size() < 2 && ProgressData.get(server).canAddCanePatch() && hasCane(c) && waterBucket(c)) {
			return 34 * weight;
		}
		return 0;
	}

	private static boolean hasCane(CompanionEntity c) {
		return c.backpack().has(s -> s.is(Items.SUGAR_CANE)) || Trips.inChest(c, s -> s.is(Items.SUGAR_CANE)) > 0;
	}

	private static boolean waterBucket(CompanionEntity c) {
		return c.backpack().has(s -> s.is(Items.WATER_BUCKET)) || Trips.inChest(c, s -> s.is(Items.WATER_BUCKET)) > 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		done = 0;
		patch = null;
		patchStep = PatchStep.FETCH;
		List<BlockPos> spots = spots(level);
		boolean anyTall = spots.stream().anyMatch(g -> level.isLoaded(g) && level.getBlockState(g.above(2)).is(Blocks.SUGAR_CANE));
		boolean anyEmpty = spots.stream().anyMatch(g -> level.isLoaded(g) && level.getBlockState(g.above()).isAir());
		if (anyTall) {
			mode = Mode.HARVEST;
		} else if (anyEmpty && hasCane(c)) {
			mode = Mode.PLANT;
		} else {
			patch = findPatchSite(c, level);
			if (patch == null) {
				Speech.say(c, Line.NEED_MATERIALS, "a level patch of grass to grow sugar cane on");
				return false;
			}
			mode = Mode.PATCH;
		}
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (Camp.isNight(level)) {
			return finish();
		}
		return switch (mode) {
			case HARVEST -> harvest(c, level);
			case PLANT -> plant(c, level);
			case PATCH -> patch(c, level);
		};
	}

	private TaskStatus finish() {
		return done > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
	}

	// ---------------------------------------------------------------- harvest

	/** Breaks the tallest cane from the top down, leaving each root. */
	private TaskStatus harvest(CompanionEntity c, ServerLevel level) {
		if (done >= MAX_HARVEST || c.backpack().freeSlots() == 0 && !c.backpack().canFit(new ItemStack(Items.SUGAR_CANE))) {
			return finish();
		}
		BlockPos top = nextTop(c, level);
		if (top == null) {
			return finish();
		}
		if (!c.actions().canReach(top)) {
			c.actions().walkTo(top.below(), 2.0);
			return c.actions().isStuck() ? finish() : TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		Actions.Result result = c.actions().mine(top, WorldEditGuard.Reason.FARM);
		if (result == Actions.Result.RUNNING) {
			return TaskStatus.RUNNING;
		}
		if (result == Actions.Result.FAILED) {
			return finish();
		}
		done++;
		Camp.data(level.getServer()).addStat("sugar_cane_harvested", 1);
		return TaskStatus.RUNNING;
	}

	/** The top segment of the nearest cane at least two high. */
	private static @Nullable BlockPos nextTop(CompanionEntity c, ServerLevel level) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos g : spots(level)) {
			if (!level.isLoaded(g) || !level.getBlockState(g.above(2)).is(Blocks.SUGAR_CANE)) {
				continue;
			}
			BlockPos top = g.above(2);
			while (level.getBlockState(top.above()).is(Blocks.SUGAR_CANE) && top.getY() < g.getY() + 4) {
				top = top.above();
			}
			double d = top.distSqr(c.blockPosition());
			if (d < bestDist) {
				bestDist = d;
				best = top;
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ plant

	private TaskStatus plant(CompanionEntity c, ServerLevel level) {
		if (!c.backpack().has(s -> s.is(Items.SUGAR_CANE))) {
			ChestWalk.State walk = ChestWalk.tick(c);
			if (walk == ChestWalk.State.FAILED) {
				return finish();
			}
			if (walk == ChestWalk.State.WALKING) {
				return TaskStatus.RUNNING;
			}
			Optional<Container> chest = ChestWalk.chest(c);
			if (chest.isEmpty() || Trips.take(chest.get(), c, s -> s.is(Items.SUGAR_CANE), MAX_PLANT) == 0) {
				return finish();
			}
		}
		if (done >= MAX_PLANT) {
			return finish();
		}
		BlockPos spot = nextEmpty(c, level);
		if (spot == null) {
			return finish();
		}
		if (!c.actions().canReach(spot)) {
			c.actions().walkTo(spot, 2.0);
			return c.actions().isStuck() ? finish() : TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		return switch (EditSteps.place(c, spot, Blocks.SUGAR_CANE.defaultBlockState(), s -> s.is(Items.SUGAR_CANE), WorldEditGuard.Reason.FARM)) {
			case DONE -> {
				done++;
				yield TaskStatus.RUNNING;
			}
			case WAIT -> TaskStatus.RUNNING;
			case FAILED -> {
				invalidate(level);
				yield finish();
			}
		};
	}

	/** The nearest empty spot where cane would grow now. */
	private static @Nullable BlockPos nextEmpty(CompanionEntity c, ServerLevel level) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos g : spots(level)) {
			BlockPos spot = g.above();
			if (!level.isLoaded(g) || !level.getBlockState(spot).isAir() || !Blocks.SUGAR_CANE.defaultBlockState().canSurvive(level, spot)) {
				continue;
			}
			double d = spot.distSqr(c.blockPosition());
			if (d < bestDist) {
				bestDist = d;
				best = spot;
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ patch

	/** Makes a one-block pool in the chosen grass: water bucket, till, dig out own farmland, pour. */
	private TaskStatus patch(CompanionEntity c, ServerLevel level) {
		BlockPos centre = patch;
		if (centre == null) {
			return finish();
		}
		switch (patchStep) {
			case FETCH -> {
				if (c.backpack().has(s -> s.is(Items.WATER_BUCKET))) {
					patchStep = PatchStep.TILL;
					return TaskStatus.RUNNING;
				}
				ChestWalk.State walk = ChestWalk.tick(c);
				if (walk == ChestWalk.State.FAILED) {
					return finish();
				}
				if (walk == ChestWalk.State.WALKING) {
					return TaskStatus.RUNNING;
				}
				Optional<Container> chest = ChestWalk.chest(c);
				if (chest.isEmpty() || Trips.take(chest.get(), c, s -> s.is(Items.WATER_BUCKET), 1) == 0) {
					return finish();
				}
				patchStep = PatchStep.TILL;
				return TaskStatus.RUNNING;
			}
			case TILL -> {
				if (!standBeside(c, centre)) {
					return c.actions().isStuck() ? finish() : TaskStatus.RUNNING;
				}
				BlockState state = level.getBlockState(centre);
				if (state.is(Blocks.FARMLAND)) {
					patchStep = PatchStep.DIG;
					return TaskStatus.RUNNING;
				}
				if (!Crops.isTillable(state) || !level.getBlockState(centre.above()).isAir()) {
					return finish();
				}
				EditSteps.Step step = EditSteps.transform(c, centre, Blocks.FARMLAND.defaultBlockState(), WorldEditGuard.Reason.FARM,
					c.actions().hasTool(ItemTags.HOES) ? ItemTags.HOES : null);
				return step == EditSteps.Step.FAILED ? finish() : TaskStatus.RUNNING;
			}
			case DIG -> {
				if (level.getBlockState(centre).isAir()) {
					patchStep = PatchStep.POUR;
					return TaskStatus.RUNNING;
				}
				if (!standBeside(c, centre)) {
					return c.actions().isStuck() ? finish() : TaskStatus.RUNNING;
				}
				if (!level.getBlockState(centre).is(Blocks.FARMLAND)) {
					return finish();
				}
				// The farmland is Fern's own block now, which the guard lets her dig out.
				return c.actions().mine(centre, WorldEditGuard.Reason.BUILD) == Actions.Result.FAILED ? finish() : TaskStatus.RUNNING;
			}
			case POUR -> {
				if (!standBeside(c, centre)) {
					return c.actions().isStuck() ? finish() : TaskStatus.RUNNING;
				}
				if (!level.getBlockState(centre).isAir() || !sealedBelowAndAround(level, centre)) {
					return finish();
				}
				return switch (EditSteps.place(c, centre, Blocks.WATER.defaultBlockState(), s -> s.is(Items.WATER_BUCKET), WorldEditGuard.Reason.FARM)) {
					case DONE -> {
						ItemStack left = c.backpack().insert(new ItemStack(Items.BUCKET));
						if (!left.isEmpty()) {
							c.spawnAtLocation(level, left);
						}
						ProgressData.get(level.getServer()).addCanePatch(centre);
						invalidate(level);
						done++;
						yield TaskStatus.SUCCESS;
					}
					case WAIT -> TaskStatus.RUNNING;
					case FAILED -> finish();
				};
			}
		}
		return finish();
	}

	/** Stands beside the patch centre (never on it) within reach. True once in place. */
	private static boolean standBeside(CompanionEntity c, BlockPos centre) {
		BlockPos feet = c.blockPosition();
		boolean onCentre = feet.getX() == centre.getX() && feet.getZ() == centre.getZ();
		if (!onCentre && c.actions().canReach(centre)) {
			c.actions().stopWalking();
			return true;
		}
		c.actions().walkTo(centre.offset(2, 1, 0), 0.8);
		return false;
	}

	/** Solid ground under the hole and on all four sides, so the water stays a single still pool. */
	private static boolean sealedBelowAndAround(ServerLevel level, BlockPos hole) {
		if (!level.getBlockState(hole.below()).isFaceSturdy(level, hole.below(), Direction.UP)) {
			return false;
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos n = hole.relative(d);
			BlockState s = level.getBlockState(n);
			if (!s.isFaceSturdy(level, n, d.getOpposite())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A grass or dirt block near the farm, inside the camp, with level grass, dirt or sand on all four sides (each with
	 * nothing on top) where cane could stand round a pool: away from the camp's middle, any build, the animal pen, paths
	 * and other water. Bounded: a 21×21 square, three heights.
	 */
	private static @Nullable BlockPos findPatchSite(CompanionEntity c, ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		BlockPos anchor = data.site(Structures.FARM_PLOT).map(s -> s.origin).orElse(c.homePos());
		BlockPos home = c.homePos();
		Optional<Pen> pen = Pen.site(level);
		List<BlockPos> candidates = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(anchor.offset(-10, -2, -10), anchor.offset(10, 2, 10))) {
			candidates.add(p.immutable());
		}
		candidates.sort(Comparator.comparingDouble(p -> p.distSqr(anchor)));
		int checked = 0;
		for (BlockPos t : candidates) {
			if (!Trips.loaded(level, t, 5) || !Crops.isTillable(level.getBlockState(t)) || !level.getBlockState(t.above()).isAir()) {
				continue;
			}
			if (++checked > 200) {
				break;
			}
			if (!WorldEditGuard.inCamp(c, t) || Camp.horizontalDistSqr(home, t) <= (CAMP_CORE + 2) * (CAMP_CORE + 2)
				|| data.isPlacedByFriends(level, t) || pen.map(p -> p.covers(t.above())).orElse(false)) {
				continue;
			}
			if (!level.getBlockState(t.below()).isFaceSturdy(level, t.below(), Direction.UP)) {
				continue;
			}
			boolean ok = true;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos g = t.relative(d);
				BlockState gs = level.getBlockState(g);
				if (!gs.is(BlockTags.SUPPORTS_SUGAR_CANE) || !level.getBlockState(g.above()).isAir() || data.isPlacedByFriends(level, g)
					|| !WorldEditGuard.inCamp(c, g) || pen.map(p -> p.covers(g.above())).orElse(false)) {
					ok = false;
					break;
				}
			}
			if (ok && !WorldEditGuard.looksPlayerBuilt(level, t, 2, data) && !WorldEditGuard.touchesFluid(level, t)
				&& !Crops.nearWater(level, t)) {
				return t;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ spots

	/** The ground of every cane spot in the camp, worked out at most every {@value #SCAN_INTERVAL} ticks. */
	static List<BlockPos> spots(ServerLevel level) {
		Spots spots = TeamCache.get(level, "progress.cane", Spots::new);
		long now = level.getGameTime();
		if (now - spots.at >= SCAN_INTERVAL || now < spots.at) {
			spots.at = now;
			spots.ground = scan(level);
		}
		return spots.ground;
	}

	private static void invalidate(ServerLevel level) {
		TeamCache.get(level, "progress.cane", Spots::new).at = Long.MIN_VALUE / 2;
	}

	/** Ground beside the friends' cane patches, then any other dirt, grass or sand beside still water near the farm. */
	private static List<BlockPos> scan(ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data) || data.campPos().isEmpty()) {
			return List.of();
		}
		List<BlockPos> found = new ArrayList<>();
		ProgressData progress = ProgressData.get(level.getServer());
		for (BlockPos water : progress.canePatches()) {
			if (!level.isLoaded(water)) {
				continue;
			}
			if (!Crops.isWaterSource(level.getBlockState(water))) {
				progress.removeCanePatch(water); // the pool is gone
				continue;
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				addIfCaneGround(level, water.relative(d), found);
			}
		}
		BlockPos centre = data.campPos().get();
		BlockPos anchor = data.site(Structures.FARM_PLOT).map(s -> s.origin).orElse(centre);
		int r = Math.min(SCAN_RADIUS, Camp.radius(data));
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r && found.size() < MAX_SPOTS; dx++) {
			for (int dz = -r; dz <= r && found.size() < MAX_SPOTS; dz++) {
				if (!level.hasChunkAt(anchor.getX() + dx, anchor.getZ() + dz)) {
					continue;
				}
				for (int dy = -3; dy <= 3; dy++) {
					m.set(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
					if (!Crops.isWaterSource(level.getBlockState(m))) {
						continue;
					}
					for (Direction d : Direction.Plane.HORIZONTAL) {
						addIfCaneGround(level, m.relative(d), found);
					}
				}
			}
		}
		return found.size() > MAX_SPOTS ? List.copyOf(found.subList(0, MAX_SPOTS)) : List.copyOf(found);
	}

	/**
	 * Adds the block if cane could stand on it inside the camp: dirt, grass or sand (never farmland), with air or cane on
	 * top, not by the camp's middle and nothing player-built right beside it.
	 */
	private static void addIfCaneGround(ServerLevel level, BlockPos ground, List<BlockPos> found) {
		if (found.contains(ground) || !level.isLoaded(ground)) {
			return;
		}
		BlockState g = level.getBlockState(ground);
		BlockState above = level.getBlockState(ground.above());
		if (!g.is(BlockTags.SUPPORTS_SUGAR_CANE) || !(above.isAir() || above.is(Blocks.SUGAR_CANE))) {
			return;
		}
		CampData data = Camp.data(level.getServer());
		BlockPos centre = data.campPos().orElse(ground);
		int r = Camp.radius(data);
		if (Camp.horizontalDistSqr(centre, ground) > (double) r * r || Camp.horizontalDistSqr(centre, ground) <= CAMP_CORE * CAMP_CORE
			|| Math.abs(ground.getY() - centre.getY()) > 24) {
			return;
		}
		if (WorldEditGuard.looksPlayerBuilt(level, ground.above(), 1, data)) {
			return;
		}
		found.add(ground.immutable());
	}

	/**
	 * True for ground right beside one of the friends' cane patches: it is kept for cane, so the farmer does not till it
	 * into farmland. Cheap: compares against the (at most two) recorded pools.
	 */
	public static boolean reservedForCane(ServerLevel level, BlockPos ground) {
		for (BlockPos water : ProgressData.get(level.getServer()).canePatches()) {
			if (water.getY() == ground.getY() && Math.abs(water.getX() - ground.getX()) + Math.abs(water.getZ() - ground.getZ()) == 1) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void stop(CompanionEntity c) {
		patch = null;
	}

	@Override
	public int failureCooldown() {
		return 1200;
	}

	@Override
	public int successCooldown() {
		return 200;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}
}
