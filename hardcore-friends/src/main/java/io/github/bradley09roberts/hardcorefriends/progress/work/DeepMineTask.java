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
import net.minecraft.world.level.levelgen.Heightmap;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.MinePlan;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.MineSite;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.MiningHelper;
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
 * <p>Safety: each step or tunnel block is looked over before any of it is dug. If water or lava is by it, loose
 * gravel or sand over it, the floor of a walkway in it (the stairs are the way back up and are never undermined), a
 * hole beside it into a cave too wide to seal, or a hole that may be a player's tunnel (a build within
 * {@value #PLAYER_GAP} blocks), it is left standing as a wall and the work goes round it: a stair turns (never back
 * the way it came), a branch ends. Lava found this way is remembered as a camp point of interest. Stairs only ever go
 * down one step at a time beside the friend, never straight down. Once a piece is open, any hole it broke into (a cave
 * beside, above or below) is sealed with carried cobblestone or cobbled deepslate, so nothing flows or walks in; a
 * piece that still cannot be made safe is filled back in before the work goes round. A torch goes down every 8 blocks.
 * Ores in the walls and ceiling of the new tunnel are dug out on the way (diamond needs the iron pickaxe this job
 * requires), never the floor nor an ore with gravel or sand resting on it.
 *
 * <p>The friend fetches seal blocks from the chest first when short of them, and stops and heads home below 60%
 * health, when hungry, out of food, torches or seal blocks, with a full pack, or as the afternoon ends (the way back
 * up is long).
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
	/** Seal blocks a friend wants to carry before a deep trip; fewer, and they are fetched from the chest. */
	private static final int MIN_SEAL_BLOCKS = 8;
	/** How many seal blocks are taken from the chest at a time. */
	private static final int SEAL_BLOCKS_TAKEN = 16;
	/** A hole within this many blocks of anything player-built may be a player's tunnel: it is never dug into. */
	private static final int PLAYER_GAP = 6;
	/** Open air this close below the entrance (or higher) that the sky shines on is the surface, not a cave. */
	private static final int SURFACE_DEPTH = 4;
	/** How long a seal waits for someone standing in the hole to move before the hole counts as unsealable. */
	private static final int MAX_SEAL_WAIT = 100;

	/** What a seal attempt came to. */
	private enum Seal {
		DONE,
		/** Not this tick (the guard's pacing, or someone standing there): try again. */
		WAIT,
		FAILED
	}

	private long searchFailedUntil;
	private MinePlan.@Nullable Job job;
	private int clearIndex;
	private int minedThisRun;
	private int redig;
	private boolean surveyed;
	private boolean sealed;
	private boolean oresQueued;
	private boolean torchTried;
	private boolean fetching;
	/** The job was given up after some of it was dug: its dug blocks are being filled back in first. */
	private boolean refilling;
	private int sealWait;
	/** The blocks this run dug for the current job, top down. */
	private final List<BlockPos> dug = new ArrayList<>();
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
		int seals = Trips.sealBlocks(c);
		if (seals < MIN_SEAL_BLOCKS && seals + Trips.inChest(c, Trips::isSealBlock) < MIN_SEAL_BLOCKS) {
			return 0; // nothing to seal holes into caves with
		}
		// At least what the staircase mine near camp scores for stone and ore (this mine yields both too), so while
		// diamonds are wanted the miner works down here rather than up there.
		double wanted = Math.max(ProgressPlan.weight(server, Items.DIAMOND), ProgressPlan.weight(server, Items.LAPIS_LAZULI));
		double camp = Math.max(CampNeeds.weight(CampNeeds.Need.STONE), CampNeeds.weight(CampNeeds.Need.ORE));
		return 50 * Math.max(wanted, camp);
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
		fetching = Trips.sealBlocks(c) < MIN_SEAL_BLOCKS;
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
		if (fetching) {
			return fetchSeals(c);
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
		if (refilling) {
			return refill(c, level, data, plan, now);
		}
		// 0. Look the piece over before any of it is dug.
		if (!surveyed) {
			TaskStatus wait = survey(c, level, data, plan, current, now);
			if (wait != null) {
				return wait;
			}
			surveyed = true;
		}
		// 1. Clear the step or tunnel block, top down, looking at everything round each block again first.
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
			if (MinePlan.isWalkwayFloor(level, data, pos)) {
				return refuse(c, plan, now); // the floor of a step or tunnel block: the way back up is never undermined
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
			dug.add(pos.immutable());
			minedThisRun++;
			data.addStat("blocks_mined", 1);
			clearIndex++;
		}
		// 2. Anything that fell back in (gravel from a pocket) is dug out again, a few times at most.
		TaskStatus fell = digOutFallen(c, plan, current, level, now);
		if (fell != null) {
			return fell;
		}
		// 3. A floor to stand on.
		BlockPos floor = current.cell().below();
		if (!MiningHelper.isSolidFloor(level, floor)) {
			Seal seal = seal(c, level, floor);
			if (seal == Seal.WAIT) {
				return TaskStatus.RUNNING;
			}
			if (seal == Seal.FAILED) {
				return refuse(c, plan, now);
			}
		}
		// 4. Seal any hole into a cave round the new blocks.
		if (!sealed) {
			List<BlockPos> openings = openings(level, data, plan, current);
			if (openings.size() > MAX_SEALS_PER_JOB) {
				return refuse(c, plan, now);
			}
			for (BlockPos hole : openings) {
				Seal seal = seal(c, level, hole);
				if (seal == Seal.WAIT) {
					return TaskStatus.RUNNING;
				}
				if (seal == Seal.FAILED) {
					return refuse(c, plan, now);
				}
			}
			sealed = true;
		}
		// 5. Ores showing in the new walls and ceiling.
		if (!oresQueued) {
			oresQueued = true;
			queueWallOres(c, level, data, current);
		}
		TaskStatus ores = mineWallOres(c, level, data);
		if (ores != null) {
			return ores;
		}
		// 6. Nothing fell in while the ores came out.
		fell = digOutFallen(c, plan, current, level, now);
		if (fell != null) {
			return fell;
		}
		// 7. Light.
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
		dug.clear();
		return minedThisRun >= BLOCKS_PER_RUN ? TaskStatus.SUCCESS : TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		resetJob();
		fetching = false;
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
		surveyed = false;
		sealed = false;
		oresQueued = false;
		torchTried = false;
		refilling = false;
		sealWait = 0;
		dug.clear();
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

	/** Takes cobblestone (or cobbled deepslate) from the chest before setting off: holes into caves are sealed with it. */
	private TaskStatus fetchSeals(CompanionEntity c) {
		ChestWalk.State walk = ChestWalk.tick(c);
		if (walk == ChestWalk.State.WALKING) {
			return TaskStatus.RUNNING;
		}
		if (walk == ChestWalk.State.ARRIVED) {
			ChestWalk.chest(c).ifPresent(chest -> {
				int want = SEAL_BLOCKS_TAKEN - Trips.sealBlocks(c);
				int got = Trips.take(chest, c, s -> s.is(Items.COBBLESTONE), want);
				Trips.take(chest, c, s -> s.is(Items.COBBLED_DEEPSLATE), want - got);
			});
		}
		fetching = false;
		if (Trips.sealBlocks(c) < MIN_SEAL_BLOCKS) {
			Speech.say(c, Line.NEED_MATERIALS, "cobblestone");
			return done();
		}
		return TaskStatus.RUNNING;
	}

	/**
	 * Step 0: everything that would make a piece be given up is looked at before any of it is dug, so nothing is opened
	 * only to be left open: water or lava by it, loose gravel over it, the floor of a walkway or a block the guard will
	 * not let them dig in it, a cave beside it too big to wall in, a hole that will not take a seal or that may be a
	 * player's tunnel. Any of those: the work goes round. Too few seal blocks for its holes: home for more first. Null
	 * when the piece may be dug.
	 */
	private @Nullable TaskStatus survey(CompanionEntity c, ServerLevel level, CampData data, MinePlan plan, MinePlan.Job job,
			long now) {
		if (paced(c, job.stand())) {
			return TaskStatus.RUNNING; // the guard's answers below only count between edits
		}
		for (int i = 0; i < job.clear().size(); i++) {
			BlockPos pos = job.clear().get(i);
			if (!plan.inBox(pos)) {
				return refuse(c, plan, now);
			}
			if (fluidAround(level, pos)) {
				noteFluid(c, level, data, pos);
				return refuse(c, plan, now);
			}
			if (MiningHelper.isPassable(level, pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if ((i == 0 && looseAbove(level, pos)) || MinePlan.isWalkwayFloor(level, data, pos)) {
				return refuse(c, plan, now); // gravel overhead, or the floor of a step or tunnel block
			}
			if (!MiningHelper.canHarvest(c, state)) {
				plan.setToolBlocked(pos);
				Speech.say(c, Line.NEED_TOOL, "better pickaxe");
				return done();
			}
			WorldEditGuard.Verdict verdict = WorldEditGuard.canBreak(c, pos, reasonFor(c, state, pos));
			if (!verdict.allowed()) {
				return passing(verdict) ? TaskStatus.RUNNING : refuse(c, plan, now);
			}
		}
		List<BlockPos> toSeal = openings(level, data, plan, job);
		if (toSeal.size() > MAX_SEALS_PER_JOB) {
			return refuse(c, plan, now); // a big cave: go round before opening anything into it
		}
		BlockPos floor = job.cell().below();
		if (!MiningHelper.isSolidFloor(level, floor)) {
			toSeal.add(floor);
		}
		for (BlockPos p : toSeal) {
			BlockState s = level.getBlockState(p);
			if (!s.isAir() && !s.canBeReplaced()) {
				return refuse(c, plan, now);
			}
			WorldEditGuard.Verdict verdict = WorldEditGuard.canPlace(c, p, Blocks.COBBLESTONE.defaultBlockState(), WorldEditGuard.Reason.MINE);
			if (!verdict.allowed()) {
				return passing(verdict) ? TaskStatus.RUNNING : refuse(c, plan, now);
			}
		}
		for (BlockPos p : toSeal) {
			if (WorldEditGuard.looksPlayerBuilt(level, p, PLAYER_GAP, data)) {
				return refuse(c, plan, now); // it may be a player's tunnel or cellar: never broken into
			}
		}
		if (Trips.sealBlocks(c) < toSeal.size()) {
			Speech.say(c, Line.NEED_MATERIALS, "cobblestone");
			return done();
		}
		return null;
	}

	/** A guard refusal that passes by itself: the edit pacing, a player right by the block, someone standing in it. */
	private static boolean passing(WorldEditGuard.Verdict verdict) {
		String why = verdict.why();
		return "pacing".equals(why) || "a player is right there".equals(why) || "someone is standing there".equals(why);
	}

	/**
	 * This piece cannot be dug safely: the stairs turn (never back the way they came), a branch ends, or the corridor
	 * (and the mine) is finished. If some of it was dug already, that is filled back in first.
	 */
	private TaskStatus refuse(CompanionEntity c, MinePlan plan, long now) {
		c.actions().cancelMining();
		if (!dug.isEmpty() && !refilling) {
			refilling = true;
			sealWait = 0;
			return TaskStatus.RUNNING;
		}
		MinePlan.Job refused = job;
		resetJob();
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

	/**
	 * Fills the blocks this piece dug back in with seal blocks, one at a time, bottom first, so a piece that is given up
	 * leaves no cave joined to the tunnel. Never fills a mine's walkway. Then the piece is given up.
	 */
	private TaskStatus refill(CompanionEntity c, ServerLevel level, CampData data, MinePlan plan, long now) {
		while (!dug.isEmpty()) {
			BlockPos pos = dug.getLast();
			if (!MiningHelper.isPassable(level, pos) || MinePlan.isWalkwaySpace(level, data, pos)) {
				dug.removeLast();
				continue;
			}
			Seal seal = seal(c, level, pos);
			if (seal == Seal.WAIT) {
				return TaskStatus.RUNNING;
			}
			dug.removeLast();
		}
		return refuse(c, plan, now);
	}

	/**
	 * Steps 2 and 6: a block of the piece that is no longer open (gravel fell in) is dug out again, a few times at most.
	 * Null when the piece is open.
	 */
	private @Nullable TaskStatus digOutFallen(CompanionEntity c, MinePlan plan, MinePlan.Job job, ServerLevel level, long now) {
		for (int i = 0; i < job.clear().size(); i++) {
			if (!MiningHelper.isPassable(level, job.clear().get(i))) {
				if (++redig > MAX_REDIG) {
					return refuse(c, plan, now);
				}
				clearIndex = i;
				return TaskStatus.RUNNING;
			}
		}
		return null;
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
	 * Open blocks touching the piece's blocks that are not part of the tunnel: the way back (the step or tunnel block
	 * the friend stands in) and the floor are left out, and so are the mines' own steps and tunnels and the open air at
	 * the top of the stairs. Each is a hole into a cave. They depend only on where the piece is, so they can be counted
	 * before it is dug.
	 */
	private static List<BlockPos> openings(ServerLevel level, CampData data, MinePlan plan, MinePlan.Job job) {
		Set<BlockPos> tunnel = new HashSet<>(job.clear());
		BlockPos stand = job.stand();
		tunnel.add(stand);
		tunnel.add(stand.above());
		tunnel.add(stand.above(2));
		tunnel.add(job.cell().below());
		MinePlan shallow = MinePlan.of(data);
		int surface = plan.entrance().getY() - SURFACE_DEPTH;
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
				if (!MiningHelper.isPassable(level, n) || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH)) {
					continue;
				}
				if (n.getY() >= surface && n.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, n.getX(), n.getZ())) {
					continue; // open to the sky by the entrance: the surface, not a cave
				}
				if (MinePlan.isWalkwaySpace(level, data, n)) {
					continue; // a step or tunnel block of the mines: never filled in
				}
				holes.add(n.immutable());
			}
		}
		return holes;
	}

	/**
	 * Fills an open space with one carried cobblestone or cobbled deepslate (the friends may take it back later). Waits
	 * a little while the guard's pacing holds or someone stands in the way.
	 */
	private Seal seal(CompanionEntity c, ServerLevel level, BlockPos pos) {
		if (!c.actions().canReach(pos)) {
			return Seal.FAILED;
		}
		BlockState current = level.getBlockState(pos);
		if (!current.isAir() && !current.canBeReplaced()) {
			return Seal.FAILED;
		}
		boolean cobble = c.backpack().has(s -> s.is(Items.COBBLESTONE));
		if (!cobble && !c.backpack().has(s -> s.is(Items.COBBLED_DEEPSLATE))) {
			return Seal.FAILED;
		}
		BlockState block = cobble ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
		WorldEditGuard.Verdict verdict = WorldEditGuard.canPlace(c, pos, block, WorldEditGuard.Reason.MINE);
		if (!verdict.allowed()) {
			return passing(verdict) && ++sealWait <= MAX_SEAL_WAIT ? Seal.WAIT : Seal.FAILED;
		}
		sealWait = 0;
		boolean placed = c.actions().place(pos, block, s -> s.is(cobble ? Items.COBBLESTONE : Items.COBBLED_DEEPSLATE),
			WorldEditGuard.Reason.MINE);
		return placed ? Seal.DONE : Seal.FAILED;
	}

	/** True while the guard's per-friend edit pacing would refuse a placement this tick. */
	private static boolean paced(CompanionEntity c, BlockPos pos) {
		WorldEditGuard.Verdict v = WorldEditGuard.canPlace(c, pos, Blocks.TORCH.defaultBlockState(), WorldEditGuard.Reason.MINE);
		return !v.allowed() && "pacing".equals(v.why());
	}

	/**
	 * Wanted ores showing in the walls and ceiling of the new blocks that this friend can dig. Never the floor (nor the
	 * floor of any walkway of the mines), and never an ore with gravel or sand resting on it, which would pour into the
	 * tunnel.
	 */
	private void queueWallOres(CompanionEntity c, ServerLevel level, CampData data, MinePlan.Job job) {
		wallOres.clear();
		boolean diamonds = false;
		BlockPos floor = job.cell().below();
		for (BlockPos p : job.clear()) {
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				if (wallOres.size() >= MAX_WALL_ORES || n.equals(floor) || n.equals(job.stand().below()) || n.getY() < job.cell().getY()
					|| MiningHelper.isOnTop(c.blockPosition(), n) || wallOres.contains(n)) {
					continue; // never a walkway's floor, nor the block under the friend
				}
				BlockState s = level.getBlockState(n);
				if (MiningHelper.isWantedOre(s) && MiningHelper.hasPickaxeFor(c, s) && !WorldEditGuard.touchesFluid(level, n)
					&& !looseAbove(level, n) && !MinePlan.isWalkwayFloor(level, data, n)) {
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
			if (!MiningHelper.isWantedOre(s) || !c.actions().canReach(ore) || c.backpack().freeSlots() == 0
				|| MiningHelper.isOnTop(c.blockPosition(), ore) || looseAbove(level, ore)) {
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
