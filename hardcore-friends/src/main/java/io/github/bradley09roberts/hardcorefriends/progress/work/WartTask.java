package io.github.bradley09roberts.hardcorefriends.progress.work;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
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
import io.github.bradley09roberts.hardcorefriends.progress.Stations;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * A small nether wart bed for the brewer, once the chest holds soul sand and nether wart: the farmer sets soul sand
 * down in a short row on level ground near the farm (a raised bed, on top of the grass, so no ground is dug), plants
 * wart on it, harvests it when fully grown and replants one wart from each harvest. Up to {@code
 * ProgressData.maxWartBeds()} beds, remembered in {@link ProgressData}. Placing and harvesting go through the guard's
 * {@code FARM} rules (inside the camp; only ripe wart).
 */
public final class WartTask implements CompanionTask {
	private static final int CAMP_CORE = 3;

	private enum Mode {
		HARVEST,
		PLANT,
		BED
	}

	private Mode mode = Mode.HARVEST;
	private int done;
	private @Nullable BlockPos target;

	@Override
	public String id() {
		return "fern.nether_wart";
	}

	@Override
	public String describe() {
		return "tending the nether wart";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || !FriendsConfig.get().allowWorldEditing) {
			return 0;
		}
		MinecraftServer server = level.getServer();
		if (!Camp.isCampLevel(level, Camp.data(server)) || Camp.isNight(level)) {
			return 0;
		}
		List<BlockPos> beds = beds(level);
		if (beds.stream().anyMatch(b -> ripe(level, b))) {
			return 44;
		}
		boolean haveWart = c.backpack().has(s -> s.is(Items.NETHER_WART)) || Trips.inChest(c, s -> s.is(Items.NETHER_WART)) > 0;
		if (!haveWart) {
			return 0;
		}
		if (beds.stream().anyMatch(b -> level.getBlockState(b.above()).isAir())) {
			return 40;
		}
		boolean soulSand = c.backpack().has(s -> s.is(Items.SOUL_SAND)) || Trips.inChest(c, s -> s.is(Items.SOUL_SAND)) > 0;
		boolean wanted = ProgressPlan.wants(server, Items.NETHER_WART) || Stations.brewingStand(level) != null;
		return soulSand && wanted && beds.size() < ProgressData.get(server).maxWartBeds() ? 34 : 0;
	}

	/** The friends' wart beds that are still soul sand (others are forgotten). */
	private static List<BlockPos> beds(ServerLevel level) {
		ProgressData data = ProgressData.get(level.getServer());
		List<BlockPos> beds = data.wartBeds();
		for (BlockPos b : beds) {
			if (level.isLoaded(b) && !level.getBlockState(b).is(Blocks.SOUL_SAND)) {
				data.removeWartBed(b);
			}
		}
		return data.wartBeds().stream().filter(level::isLoaded).toList();
	}

	private static boolean ripe(ServerLevel level, BlockPos bed) {
		BlockState s = level.getBlockState(bed.above());
		return s.is(Blocks.NETHER_WART) && s.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		done = 0;
		target = null;
		List<BlockPos> beds = beds(level);
		if (beds.stream().anyMatch(b -> ripe(level, b))) {
			mode = Mode.HARVEST;
		} else if (beds.stream().anyMatch(b -> level.getBlockState(b.above()).isAir())) {
			mode = Mode.PLANT;
		} else {
			target = newBedSpot(c, level, beds);
			if (target == null) {
				return false;
			}
			mode = Mode.BED;
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
		// Wart and soul sand come from the chest when not carried.
		boolean needWart = mode != Mode.HARVEST && !c.backpack().has(s -> s.is(Items.NETHER_WART));
		boolean needSand = mode == Mode.BED && !c.backpack().has(s -> s.is(Items.SOUL_SAND));
		if (needWart || needSand) {
			ChestWalk.State walk = ChestWalk.tick(c);
			if (walk == ChestWalk.State.FAILED) {
				return finish();
			}
			if (walk == ChestWalk.State.WALKING) {
				return TaskStatus.RUNNING;
			}
			Optional<Container> chest = ChestWalk.chest(c);
			if (chest.isEmpty()) {
				return finish();
			}
			if (needWart && Trips.take(chest.get(), c, s -> s.is(Items.NETHER_WART), 4) == 0
				|| needSand && Trips.take(chest.get(), c, s -> s.is(Items.SOUL_SAND), 1) == 0) {
				return finish();
			}
		}
		return switch (mode) {
			case HARVEST -> harvest(c, level);
			case PLANT -> plant(c, level);
			case BED -> bed(c, level);
		};
	}

	private TaskStatus finish() {
		return done > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
	}

	/** Breaks ripe wart (drops go in the backpack) and puts one carried wart straight back. */
	private TaskStatus harvest(CompanionEntity c, ServerLevel level) {
		BlockPos bed = null;
		for (BlockPos b : beds(level)) {
			if (ripe(level, b) || level.getBlockState(b.above()).isAir() && c.backpack().has(s -> s.is(Items.NETHER_WART))) {
				bed = b;
				break;
			}
		}
		if (bed == null || done >= 12) {
			return finish();
		}
		BlockPos crop = bed.above();
		if (!c.actions().canReach(crop)) {
			c.actions().walkTo(crop, 2.0);
			return c.actions().isStuck() ? finish() : TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		if (ripe(level, bed)) {
			Actions.Result r = c.actions().mine(crop, WorldEditGuard.Reason.FARM);
			if (r == Actions.Result.FAILED) {
				return finish();
			}
			if (r == Actions.Result.DONE) {
				done++;
				Camp.data(level.getServer()).addStat("nether_wart_harvested", 1);
			}
			return TaskStatus.RUNNING;
		}
		return replant(c, crop) ? TaskStatus.RUNNING : finish();
	}

	private TaskStatus plant(CompanionEntity c, ServerLevel level) {
		BlockPos bed = null;
		for (BlockPos b : beds(level)) {
			if (level.getBlockState(b.above()).isAir()) {
				bed = b;
				break;
			}
		}
		if (bed == null || !c.backpack().has(s -> s.is(Items.NETHER_WART))) {
			return finish();
		}
		BlockPos crop = bed.above();
		if (!c.actions().canReach(crop)) {
			c.actions().walkTo(crop, 2.0);
			return c.actions().isStuck() ? finish() : TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		return replant(c, crop) ? TaskStatus.RUNNING : finish();
	}

	/** Plants one carried wart; false if refused. Waits (true) while the guard paces edits. */
	private boolean replant(CompanionEntity c, BlockPos crop) {
		EditSteps.Step step = EditSteps.place(c, crop, Blocks.NETHER_WART.defaultBlockState(), s -> s.is(Items.NETHER_WART),
			WorldEditGuard.Reason.FARM);
		if (step == EditSteps.Step.DONE) {
			done++;
		}
		return step != EditSteps.Step.FAILED;
	}

	/** Sets one carried soul sand down on the chosen spot; the next run plants it. */
	private TaskStatus bed(CompanionEntity c, ServerLevel level) {
		BlockPos spot = target;
		if (spot == null) {
			return finish();
		}
		if (!c.actions().canReach(spot)) {
			c.actions().walkTo(spot, 2.0);
			return c.actions().isStuck() ? finish() : TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		return switch (EditSteps.place(c, spot, Blocks.SOUL_SAND.defaultBlockState(), s -> s.is(Items.SOUL_SAND), WorldEditGuard.Reason.FARM)) {
			case DONE -> {
				ProgressData.get(level.getServer()).addWartBed(spot);
				done++;
				mode = Mode.PLANT;
				yield TaskStatus.RUNNING;
			}
			case WAIT -> TaskStatus.RUNNING;
			case FAILED -> finish();
		};
	}

	/**
	 * Where the next bed goes: beside the last bed if there is room, else near the farm (or the camp centre). The spot
	 * must be air with air above, on solid, level natural ground inside the camp, away from the camp's middle, the
	 * pen, water and anything player-built.
	 */
	private static @Nullable BlockPos newBedSpot(CompanionEntity c, ServerLevel level, List<BlockPos> beds) {
		CampData data = Camp.data(level.getServer());
		if (!beds.isEmpty()) {
			BlockPos last = beds.getLast();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos next = last.relative(d);
				if (bedSpot(c, level, data, next)) {
					return next;
				}
			}
		}
		BlockPos anchor = data.site(Structures.FARM_PLOT).map(s -> s.origin).orElse(c.homePos());
		List<BlockPos> candidates = new java.util.ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(anchor.offset(-8, -2, -8), anchor.offset(8, 2, 8))) {
			if (p.distSqr(anchor) >= 9) {
				candidates.add(p.immutable());
			}
		}
		candidates.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(anchor)));
		for (BlockPos p : candidates) {
			if (bedSpot(c, level, data, p)) {
				return p; // the nearest fitting spot: checking stops here
			}
		}
		return null;
	}

	private static boolean bedSpot(CompanionEntity c, ServerLevel level, CampData data, BlockPos spot) {
		if (!Trips.loaded(level, spot, 5) || !level.getBlockState(spot).isAir() || !level.getBlockState(spot.above()).isAir()) {
			return false;
		}
		BlockState ground = level.getBlockState(spot.below());
		if (!(ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT) || ground.is(Blocks.COARSE_DIRT))
			|| !ground.isFaceSturdy(level, spot.below(), Direction.UP) || data.isPlacedByFriends(level, spot.below())) {
			return false;
		}
		if (!WorldEditGuard.inCamp(c, spot) || Camp.horizontalDistSqr(c.homePos(), spot) <= (CAMP_CORE + 2) * (CAMP_CORE + 2)) {
			return false;
		}
		if (Pen.site(level).map(p -> p.covers(spot)).orElse(false) || WorldEditGuard.touchesFluid(level, spot)
			|| Crops.nearWater(level, spot.below())) {
			return false; // the pen, or ground the farmer could till
		}
		return !WorldEditGuard.looksPlayerBuilt(level, spot, 2, data);
	}

	@Override
	public void stop(CompanionEntity c) {
		target = null;
	}

	@Override
	public int failureCooldown() {
		return 1200;
	}

	@Override
	public int successCooldown() {
		return 100;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
