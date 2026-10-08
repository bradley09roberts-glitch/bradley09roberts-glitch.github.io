package io.github.bradley09roberts.hardcorefriends.ai.role.mine;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Flint works one staircase mine by day (see {@link MinePlan} for the layout). Each run digs up to
 * {@value #BLOCKS_PER_RUN} blocks and then ends, so he can take the stone back to camp. Blocks are only broken
 * through the guard: natural stone and ores (plus topsoil at the entrance, outside the camp). A refused block ends
 * that part of the mine. Missing floors are sealed with carried cobblestone, and a torch goes down every
 * {@value MinePlan#TORCH_SPACING} blocks.
 */
public final class DigMineTask implements CompanionTask {
	public static final int BLOCKS_PER_RUN = 12;
	private static final int SEARCH_RETRY = 20 * 60;
	private static final int NEW_MINE_AFTER = 24000;
	private static final int MAX_STUCK = 5;
	/** A staircase refused before this many steps is abandoned instead of branching near the surface. */
	private static final int MIN_USEFUL_STEPS = 4;

	private long searchFailedUntil;
	private MinePlan.@Nullable Job job;
	private int clearIndex;
	private int minedThisRun;
	private boolean torchTried;

	@Override
	public String id() {
		return "flint.dig_mine";
	}

	@Override
	public String describe() {
		return "working the mine";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || !FriendsConfig.get().allowMining) {
			return 0;
		}
		if (Camp.isNight(level) || Camp.isDusk(level) || !MiningHelper.hasAnyPickaxe(c) || c.backpack().freeSlots() < 2) {
			return 0;
		}
		CampData data = Camp.data(level.getServer());
		MinePlan plan = MinePlan.of(data);
		if (!mayUsePlan(level, data, plan)) {
			return 0;
		}
		if (plan.exists() && !WorldEditGuard.inResourceZone(c, plan.entrance())) {
			plan.abandon(); // the camp moved
		}
		long now = level.getGameTime();
		switch (plan.phase()) {
			case NONE -> {
				if (now < searchFailedUntil) {
					return 0;
				}
			}
			case DONE -> {
				if (now - plan.finishedAt() < NEW_MINE_AFTER || now < searchFailedUntil) {
					return 0;
				}
			}
			default -> {
				BlockPos blocked = plan.toolBlocked();
				if (blocked != null) {
					if (!level.isLoaded(blocked)) {
						return 0; // never load the mine's chunk just to look at the block
					}
					BlockState state = level.getBlockState(blocked);
					if (!state.isAir() && !MiningHelper.canHarvest(c, state)) {
						return 0;
					}
					plan.setToolBlocked(null);
				}
			}
		}
		return 45 * Math.max(CampNeeds.weight(CampNeeds.Need.STONE), CampNeeds.weight(CampNeeds.Need.ORE));
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		MinePlan plan = MinePlan.of(data);
		if (!mayUsePlan(level, data, plan)) {
			return false;
		}
		if (plan.phase() == MinePlan.Phase.DONE) {
			plan.abandon();
		}
		if (!plan.exists()) {
			MineSite.Site site = MineSite.find(c, avoiding(data, plan));
			if (site == null) {
				searchFailedUntil = level.getGameTime() + SEARCH_RETRY;
				return false;
			}
			plan.begin(site.entrance(), site.dir(), site.bottomY(), Camp.dimensionId(level));
			BlockPos e = site.entrance();
			Speech.say(c, Line.DISCOVERY, "a good spot for a mine at " + e.getX() + " " + e.getY() + " " + e.getZ());
		}
		job = null;
		clearIndex = 0;
		minedThisRun = 0;
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (Camp.isNight(level) || Camp.isDusk(level)) {
			return done();
		}
		CampData data = Camp.data(level.getServer());
		MinePlan plan = MinePlan.of(data);
		long now = level.getGameTime();
		if (job == null) {
			job = plan.nextJob(now);
			clearIndex = 0;
			if (job == null) {
				Speech.say(c, Line.BUILD_DONE, "mine");
				return done();
			}
		}
		MinePlan.Job current = job;
		Actions actions = c.actions();
		if (!actions.walkTo(current.stand(), 1.0)) {
			return actions.isStuck() ? stuck(plan) : TaskStatus.RUNNING;
		}
		while (clearIndex < current.clear().size()) {
			BlockPos pos = current.clear().get(clearIndex);
			if (!plan.inBox(pos)) {
				return refuse(plan, now);
			}
			BlockState state = level.getBlockState(pos);
			if (!state.getFluidState().isEmpty()) {
				return refuse(plan, now);
			}
			if (MiningHelper.isPassable(level, pos)) {
				clearIndex++;
				continue;
			}
			if (!MiningHelper.canHarvest(c, state)) {
				plan.setToolBlocked(pos);
				Speech.say(c, Line.NEED_TOOL, "better pickaxe");
				return done();
			}
			if (!actions.canReach(pos)) {
				return stuck(plan);
			}
			Actions.Result result = actions.mine(pos, reasonFor(c, state, pos));
			if (result == Actions.Result.RUNNING) {
				return TaskStatus.RUNNING;
			}
			if (result == Actions.Result.FAILED) {
				return refuse(plan, now);
			}
			minedThisRun++;
			data.addStat("blocks_mined", 1);
			clearIndex++;
			if (minedThisRun >= BLOCKS_PER_RUN && clearIndex < current.clear().size()) {
				return done(); // the rest of this job is picked up on the next run
			}
		}
		BlockPos floor = current.cell().below();
		if (!MiningHelper.isSolidFloor(level, floor)) {
			if (paced(c, floor)) {
				return TaskStatus.RUNNING;
			}
			if (!seal(c, level, floor)) {
				return refuse(plan, now);
			}
		}
		if (plan.torchDue() && !torchTried) {
			if (paced(c, current.stand())) {
				return TaskStatus.RUNNING;
			}
			torchTried = true;
			Crafting.ensureTorches(c.backpack(), 1);
			if (MiningHelper.placeTorch(c, current.stand())) {
				plan.torchPlaced();
			}
		}
		plan.complete(current);
		job = null;
		torchTried = false;
		return minedThisRun >= BLOCKS_PER_RUN ? TaskStatus.SUCCESS : TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		job = null;
		clearIndex = 0;
		torchTried = false;
	}

	@Override
	public int maxTicks() {
		return 20 * 180;
	}

	@Override
	public int failureCooldown() {
		return 200;
	}

	// ------------------------------------------------------------------ helpers

	private TaskStatus done() {
		return minedThisRun > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
	}

	/**
	 * The mine is camp memory with camp-dimension coordinates: Flint only works (or abandons, or starts) it while he is
	 * in the camp's dimension, and never touches a mine recorded in another dimension.
	 */
	private static boolean mayUsePlan(ServerLevel level, CampData data, MinePlan plan) {
		if (data.campPos().isPresent() && !Camp.isCampLevel(level, data)) {
			return false;
		}
		return !plan.exists() || plan.isIn(level);
	}

	/** Old entrances to keep away from, and the deep mine's (package progress), so two mines never share one box. */
	private static long[] avoiding(CampData data, MinePlan plan) {
		long[] old = plan.oldEntrances();
		MinePlan deep = MinePlan.of(data, MinePlan.DEEP_KEY);
		if (!deep.exists()) {
			return old;
		}
		long[] all = java.util.Arrays.copyOf(old, old.length + 1);
		all[old.length] = deep.entrance().asLong();
		return all;
	}

	/** Could not get to the work: try again later, and give the mine up after {@value #MAX_STUCK} tries in a row. */
	private TaskStatus stuck(MinePlan plan) {
		plan.setStuckCount(plan.stuckCount() + 1);
		if (plan.stuckCount() >= MAX_STUCK) {
			plan.abandon();
		}
		return TaskStatus.FAILURE;
	}

	private TaskStatus refuse(MinePlan plan, long now) {
		MinePlan.Job refused = job;
		job = null;
		torchTried = false;
		if (refused == null) {
			return TaskStatus.RUNNING;
		}
		if (refused.kind() == MinePlan.Kind.STAIR && plan.stepsDug() < MIN_USEFUL_STEPS) {
			// Too shallow to be worth branching from: give this spot up and find a better one later.
			plan.abandon();
			return done();
		}
		plan.refuse(refused, now);
		return TaskStatus.RUNNING;
	}

	/** True while the guard's per-friend edit pacing would refuse a placement this tick. */
	private static boolean paced(CompanionEntity c, BlockPos pos) {
		WorldEditGuard.Verdict v = WorldEditGuard.canPlace(c, pos, Blocks.TORCH.defaultBlockState(), WorldEditGuard.Reason.MINE);
		return !v.allowed() && "pacing".equals(v.why());
	}

	/** Natural stone and ores are mined; topsoil (grass, sand) at the entrance is dug as earth, outside camp only. */
	private static WorldEditGuard.Reason reasonFor(CompanionEntity c, BlockState state, BlockPos pos) {
		if (!state.is(ModTags.MINEABLE_NATURAL) && state.is(ModTags.EARTH_GATHERABLE) && !WorldEditGuard.inCamp(c, pos)) {
			return WorldEditGuard.Reason.GATHER_EARTH;
		}
		return WorldEditGuard.Reason.MINE;
	}

	/** Fills a missing floor with one carried cobblestone. */
	private static boolean seal(CompanionEntity c, ServerLevel level, BlockPos floor) {
		if (!c.backpack().has(s -> s.is(Items.COBBLESTONE)) || !c.actions().canReach(floor)) {
			return false;
		}
		BlockState current = level.getBlockState(floor);
		if (!current.isAir() && !current.canBeReplaced()) {
			return false;
		}
		return c.actions().place(floor, Blocks.COBBLESTONE.defaultBlockState(), s -> s.is(Items.COBBLESTONE), WorldEditGuard.Reason.MINE);
	}
}
