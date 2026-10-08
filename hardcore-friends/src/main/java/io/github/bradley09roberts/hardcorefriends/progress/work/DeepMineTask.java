package io.github.bradley09roberts.hardcorefriends.progress.work;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.MinePlan;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.MineSite;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.MiningHelper;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;
import io.github.bradley09roberts.hardcorefriends.progress.Tiers;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Flint's branch mine at diamond level, worked while Sage's plan wants diamonds or lapis. It uses the same layout as
 * his staircase mine ({@link MinePlan}, kept under {@link MinePlan#DEEP_KEY}): a 1-wide staircase with three blocks
 * of head room, spiralling down inside its 24×24 box to about y = {@value #DEEP_Y}, then a 2-high corridor with 2-high
 * branches every 3 blocks. Where the staircase mine near camp already reaches its bottom, the deep stairs carry on down
 * from its last step; otherwise they start from a new entrance in the gathering ring.
 *
 * <p>Safety, before every block: the block and everything round it is looked at, and if water or lava is there it
 * is left standing as a wall (the guard never opens a block that touches either) and the work goes round it: a stair
 * turns, a branch ends. Lava found this way is remembered as a camp point of interest. Loose gravel or sand overhead is
 * also gone round, never dug under. Stairs only ever go down one step at a time beside the friend, never straight
 * down. Once a step or tunnel block is open, any hole it broke into (a cave beside, above or below) is sealed with
 * carried cobblestone or cobbled deepslate, so nothing flows or walks in; a block that opens into a cave too wide to
 * seal is gone round instead. A torch goes down every 8 blocks. Ores in the walls and ceiling of the new tunnel are
 * dug out on the way (diamond needs the iron pickaxe this job requires), the floor never.
 *
 * <p>The friend stops and heads home below 60% health, when hungry, out of food or torches, with a full pack, or as
 * the afternoon ends (the way back up is long).
 */
public final class DeepMineTask implements CompanionTask {
	/** The level the branches are dug at: where diamonds are most common. */
	public static final int DEEP_Y = -58;
	private static final int BLOCKS_PER_RUN = 24;
	private static final int SEARCH_RETRY = 20 * 120;
	private static final int NEW_MINE_AFTER = 24000;
	private static final int MAX_STUCK = 5;
	/** A staircase that has to stop more than this far above its bottom is no use for diamonds: it is given up. */
	private static final int GIVE_UP_ABOVE_BOTTOM = 18;
	/** More openings than this round one new block means a big cave: go round rather than wall it all in. */
	private static final int MAX_SEALS_PER_JOB = 4;
	/** Gravel that keeps falling into a step is dug out at most this often before the step is given up. */
	private static final int MAX_REDIG = 12;
	private static final int MAX_WALL_ORES = 6;
	/** No new deep trip after this time of day (late afternoon): the way back up takes a while. */
	private static final long LAST_START = 9000;
	/** Turn back at this time of day, whatever is left to dig. */
	private static final long TURN_BACK = 10500;
	private static final String POI_LAVA = "lava";

	private long searchFailedUntil;
	private MinePlan.@Nullable Job job;
	private int clearIndex;
	private int minedThisRun;
	private int redig;
	private boolean sealed;
	private boolean oresQueued;
	private boolean torchTried;
	private final Deque<BlockPos> wallOres = new ArrayDeque<>();
	private int checkTimer;

	@Override
	public String id() {
		return "flint.deep_mine";
	}

	@Override
	public String describe() {
		return "mining deep for diamonds";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || !FriendsConfig.get().allowMining || level.dimension() != Level.OVERWORLD) {
			return 0;
		}
		if (Camp.isNight(level) || Camp.isDusk(level) || Camp.timeOfDay(level) > LAST_START) {
			return 0;
		}
		MinecraftServer server = level.getServer();
		if (!wantsDeepOres(server) || Tiers.bestPickaxe(c) < Tiers.IRON || !Trips.fitForDeepWork(c, 3)) {
			return 0;
		}
		CampData data = Camp.data(server);
		if (data.campPos().isPresent() && !Camp.isCampLevel(level, data)) {
			return 0;
		}
		MinePlan plan = MinePlan.of(data, MinePlan.DEEP_KEY);
		if (plan.exists() && !plan.isIn(level)) {
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
						return 0;
					}
					BlockState state = level.getBlockState(blocked);
					if (!state.isAir() && !MiningHelper.canHarvest(c, state)) {
						return 0;
					}
					plan.setToolBlocked(null);
				}
			}
		}
		return 50 * Math.max(ProgressPlan.weight(server, Items.DIAMOND), ProgressPlan.weight(server, Items.LAPIS_LAZULI));
	}

	/** The plan wants what only deep mining finds: diamonds, or lapis for enchanting. */
	static boolean wantsDeepOres(MinecraftServer server) {
		return ProgressPlan.wants(server, Items.DIAMOND) || ProgressPlan.wants(server, Items.LAPIS_LAZULI);
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		MinePlan plan = MinePlan.of(data, MinePlan.DEEP_KEY);
		if (plan.exists() && !plan.isIn(level)) {
			return false;
		}
		if (plan.phase() == MinePlan.Phase.DONE) {
			plan.abandon();
		}
		if (!plan.exists() && !begin(c, level, data, plan)) {
			searchFailedUntil = level.getGameTime() + SEARCH_RETRY;
			return false;
		}
		resetJob();
		minedThisRun = 0;
		checkTimer = 0;
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	/**
	 * Starts the deep mine: below the staircase mine near camp when that has reached its bottom (and was not given up
	 * on before), else from a new entrance in the gathering ring.
	 */
	private static boolean begin(CompanionEntity c, ServerLevel level, CampData data, MinePlan plan) {
		int bottom = Math.max(level.getMinY() + 6, DEEP_Y);
		MinePlan shallow = MinePlan.of(data);
		boolean shallowReady = shallow.exists() && shallow.isIn(level)
			&& (shallow.phase() == MinePlan.Phase.BRANCHES || shallow.phase() == MinePlan.Phase.DONE)
			&& shallow.stairEnd().getY() > bottom + 8 && WorldEditGuard.inResourceZone(c, shallow.entrance());
		if (shallowReady && !near(shallow.entrance(), plan.oldEntrances())) {
			plan.beginBelow(shallow, bottom);
			return true;
		}
		List<Long> avoid = new ArrayList<>();
		for (long l : plan.oldEntrances()) {
			avoid.add(l);
		}
		for (long l : shallow.oldEntrances()) {
			avoid.add(l);
		}
		if (shallow.exists()) {
			avoid.add(shallow.entrance().asLong());
		}
		MineSite.Site site = MineSite.find(c, avoid.stream().mapToLong(Long::longValue).toArray());
		if (site == null) {
			return false;
		}
		plan.begin(site.entrance(), site.dir(), bottom, Camp.dimensionId(level));
		return true;
	}

	private static boolean near(BlockPos pos, long[] entrances) {
		for (long l : entrances) {
			if (Camp.horizontalDistSqr(BlockPos.of(l), pos) < 16 * 16) {
				return true;
			}
		}
		return false;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (Camp.isNight(level) || Camp.isDusk(level) || Camp.timeOfDay(level) > TURN_BACK) {
			return done();
		}
		if (--checkTimer <= 0) {
			checkTimer = 20;
			if (!Trips.fitForDeepWork(c, 2)) {
				return done(); // hurt, hungry, no food or light, or full: home first
			}
		}
		CampData data = Camp.data(level.getServer());
		MinePlan plan = MinePlan.of(data, MinePlan.DEEP_KEY);
		if (!plan.exists() || !plan.isIn(level)) {
			return done();
		}
		long now = level.getGameTime();
		if (job == null) {
			job = plan.nextJob(now);
			resetJobProgress();
			if (job == null) {
				Speech.say(c, Line.BUILD_DONE, "deep mine");
				return done();
			}
			if (plan.phase() == MinePlan.Phase.BRANCHES && plan.stairEnd().getY() > plan.bottomY() + GIVE_UP_ABOVE_BOTTOM) {
				plan.abandon(); // the stairs could not get deep enough here: start afresh somewhere else
				job = null;
				return done();
			}
		}
		MinePlan.Job current = job;
		Actions actions = c.actions();
		if (!actions.walkTo(current.stand(), 1.0)) {
			return actions.isStuck() ? stuck(plan) : TaskStatus.RUNNING;
		}
		// 1. Clear the step or tunnel block, top down, looking at everything round each block first.
		while (clearIndex < current.clear().size()) {
			BlockPos pos = current.clear().get(clearIndex);
			if (!plan.inBox(pos)) {
				return refuse(c, plan, now);
			}
			BlockState state = level.getBlockState(pos);
			if (fluidAround(level, pos)) {
				noteFluid(c, level, data, pos);
				return refuse(c, plan, now);
			}
			if (MiningHelper.isPassable(level, pos)) {
				clearIndex++;
				continue;
			}
			if (clearIndex == 0 && looseAbove(level, pos)) {
				return refuse(c, plan, now); // gravel or sand overhead would pour in: go round
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
				return refuse(c, plan, now);
			}
			minedThisRun++;
			data.addStat("blocks_mined", 1);
			clearIndex++;
			if (minedThisRun >= BLOCKS_PER_RUN && clearIndex < current.clear().size()) {
				return done();
			}
		}
		// 2. Anything that fell back in (gravel from a pocket) is dug out again, a few times at most.
		for (int i = 0; i < current.clear().size(); i++) {
			if (!MiningHelper.isPassable(level, current.clear().get(i))) {
				if (++redig > MAX_REDIG) {
					return refuse(c, plan, now);
				}
				clearIndex = i;
				return TaskStatus.RUNNING;
			}
		}
		// 3. A floor to stand on.
		BlockPos floor = current.cell().below();
		if (!MiningHelper.isSolidFloor(level, floor)) {
			if (paced(c, floor)) {
				return TaskStatus.RUNNING;
			}
			if (!seal(c, level, floor)) {
				return refuse(c, plan, now);
			}
		}
		// 4. Seal any hole into a cave round the new blocks.
		if (!sealed) {
			List<BlockPos> openings = openings(level, data, current);
			if (openings.size() > MAX_SEALS_PER_JOB) {
				return refuse(c, plan, now);
			}
			for (BlockPos hole : openings) {
				if (paced(c, hole)) {
					return TaskStatus.RUNNING;
				}
				if (!seal(c, level, hole)) {
					return refuse(c, plan, now);
				}
			}
			sealed = true;
		}
		// 5. Ores showing in the new walls and ceiling.
		if (!oresQueued) {
			oresQueued = true;
			queueWallOres(c, level, current);
		}
		TaskStatus ores = mineWallOres(c, level, data);
		if (ores != null) {
			return ores;
		}
		// 6. Light.
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
		return minedThisRun >= BLOCKS_PER_RUN ? TaskStatus.SUCCESS : TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		resetJob();
	}

	@Override
	public int maxTicks() {
		return 20 * 300;
	}

	@Override
	public int failureCooldown() {
		return 400;
	}

	// ------------------------------------------------------------------ helpers

	private void resetJob() {
		job = null;
		resetJobProgress();
	}

	private void resetJobProgress() {
		clearIndex = 0;
		redig = 0;
		sealed = false;
		oresQueued = false;
		torchTried = false;
		wallOres.clear();
	}

	private TaskStatus done() {
		return minedThisRun > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
	}

	private TaskStatus stuck(MinePlan plan) {
		plan.setStuckCount(plan.stuckCount() + 1);
		if (plan.stuckCount() >= MAX_STUCK) {
			plan.abandon();
		}
		return TaskStatus.FAILURE;
	}

	/** This piece cannot be dug safely: the stairs turn, a branch ends, or the corridor (and the mine) is finished. */
	private TaskStatus refuse(CompanionEntity c, MinePlan plan, long now) {
		MinePlan.Job refused = job;
		resetJob();
		c.actions().cancelMining();
		if (refused == null) {
			return TaskStatus.RUNNING;
		}
		if (refused.kind() == MinePlan.Kind.STAIR) {
			plan.turnStair(refused);
		} else {
			plan.refuse(refused, now);
		}
		return TaskStatus.RUNNING;
	}

	/** Water or lava in the block or any block touching it (unloaded counts as wet). */
	private static boolean fluidAround(ServerLevel level, BlockPos pos) {
		return WorldEditGuard.touchesFluid(level, pos);
	}

	/** Remembers lava found beside the work (once per spot) and says so; water is simply gone round. */
	private static void noteFluid(CompanionEntity c, ServerLevel level, CampData data, BlockPos pos) {
		BlockPos lava = null;
		for (Direction d : Direction.values()) {
			BlockPos n = pos.relative(d);
			if (level.isLoaded(n) && level.getFluidState(n).is(FluidTags.LAVA)) {
				lava = n;
				break;
			}
		}
		if (lava == null && level.getFluidState(pos).is(FluidTags.LAVA)) {
			lava = pos;
		}
		if (lava == null) {
			return;
		}
		for (CampData.Poi poi : data.pois()) {
			if (POI_LAVA.equals(poi.type) && poi.pos.distSqr(lava) <= 12 * 12) {
				Speech.say(c, Line.LAVA_SEALED);
				return;
			}
		}
		data.addPoi(POI_LAVA, lava, level.getGameTime());
		data.addStat("lava_found", 1);
		Speech.say(c, Line.LAVA_SEALED);
	}

	/** Gravel, sand or any other falling block resting on top of this block. */
	private static boolean looseAbove(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos.above()).getBlock() instanceof Fallable;
	}

	/** Natural stone and ores (and the friends' own seals) are mined; topsoil is dug as earth, outside the camp only. */
	private static WorldEditGuard.Reason reasonFor(CompanionEntity c, BlockState state, BlockPos pos) {
		if (!state.is(ModTags.MINEABLE_NATURAL) && state.is(ModTags.EARTH_GATHERABLE) && !WorldEditGuard.inCamp(c, pos)) {
			return WorldEditGuard.Reason.GATHER_EARTH;
		}
		return WorldEditGuard.Reason.MINE;
	}

	/**
	 * Open blocks touching the newly dug ones that are not part of the tunnel: the way back (the step or tunnel block
	 * the friend stands in) and the floor are left out, and so are the staircase mine's own tunnels. Each is a hole into
	 * a cave.
	 */
	private static List<BlockPos> openings(ServerLevel level, CampData data, MinePlan.Job job) {
		Set<BlockPos> tunnel = new HashSet<>(job.clear());
		BlockPos stand = job.stand();
		tunnel.add(stand);
		tunnel.add(stand.above());
		tunnel.add(stand.above(2));
		tunnel.add(job.cell().below());
		MinePlan shallow = MinePlan.of(data);
		List<BlockPos> holes = new ArrayList<>();
		for (BlockPos p : job.clear()) {
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				if (tunnel.contains(n) || holes.contains(n) || !level.isLoaded(n)) {
					continue;
				}
				if (shallow.exists() && shallow.isIn(level) && shallow.inBox(n) && n.getY() >= shallow.bottomY() - 1) {
					continue; // the staircase mine's own stairs and tunnels
				}
				BlockState s = level.getBlockState(n);
				if (MiningHelper.isPassable(level, n) && !s.is(Blocks.TORCH) && !s.is(Blocks.WALL_TORCH)) {
					holes.add(n.immutable());
				}
			}
		}
		return holes;
	}

	/** Fills an open space with one carried cobblestone or cobbled deepslate (the friends may take it back later). */
	private static boolean seal(CompanionEntity c, ServerLevel level, BlockPos pos) {
		if (!c.actions().canReach(pos)) {
			return false;
		}
		BlockState current = level.getBlockState(pos);
		if (!current.isAir() && !current.canBeReplaced()) {
			return false;
		}
		if (c.backpack().has(s -> s.is(Items.COBBLESTONE))) {
			return c.actions().place(pos, Blocks.COBBLESTONE.defaultBlockState(), s -> s.is(Items.COBBLESTONE), WorldEditGuard.Reason.MINE);
		}
		if (c.backpack().has(s -> s.is(Items.COBBLED_DEEPSLATE))) {
			return c.actions().place(pos, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), s -> s.is(Items.COBBLED_DEEPSLATE),
				WorldEditGuard.Reason.MINE);
		}
		return false;
	}

	/** True while the guard's per-friend edit pacing would refuse a placement this tick. */
	private static boolean paced(CompanionEntity c, BlockPos pos) {
		WorldEditGuard.Verdict v = WorldEditGuard.canPlace(c, pos, Blocks.TORCH.defaultBlockState(), WorldEditGuard.Reason.MINE);
		return !v.allowed() && "pacing".equals(v.why());
	}

	/** Wanted ores showing in the walls and ceiling of the new blocks (never the floor), that this friend can dig. */
	private void queueWallOres(CompanionEntity c, ServerLevel level, MinePlan.Job job) {
		wallOres.clear();
		boolean diamonds = false;
		BlockPos floor = job.cell().below();
		for (BlockPos p : job.clear()) {
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				if (wallOres.size() >= MAX_WALL_ORES || n.equals(floor) || n.getY() < job.cell().getY() || wallOres.contains(n)) {
					continue;
				}
				BlockState s = level.getBlockState(n);
				if (MiningHelper.isWantedOre(s) && MiningHelper.hasPickaxeFor(c, s) && !WorldEditGuard.touchesFluid(level, n)) {
					wallOres.add(n.immutable());
					diamonds |= s.is(BlockItemTags.DIAMOND_ORES.block());
				}
			}
		}
		if (diamonds) {
			Speech.say(c, Line.FOUND_DIAMONDS);
			Camp.data(level.getServer()).addStat("diamond_veins_found", 1);
		}
	}

	/** Digs the queued wall ores one by one. Null when there are none left to dig. */
	private @Nullable TaskStatus mineWallOres(CompanionEntity c, ServerLevel level, CampData data) {
		while (!wallOres.isEmpty()) {
			BlockPos ore = wallOres.peekFirst();
			BlockState s = level.getBlockState(ore);
			if (!MiningHelper.isWantedOre(s) || !c.actions().canReach(ore) || c.backpack().freeSlots() == 0) {
				wallOres.pollFirst();
				continue;
			}
			Actions.Result result = c.actions().mine(ore, WorldEditGuard.Reason.MINE);
			if (result == Actions.Result.RUNNING) {
				return TaskStatus.RUNNING;
			}
			wallOres.pollFirst();
			if (result == Actions.Result.DONE) {
				data.addStat("ores_mined", 1);
				if (s.is(BlockItemTags.DIAMOND_ORES.block())) {
					data.addStat("diamond_ore_mined", 1);
				}
			}
		}
		return null;
	}
}
