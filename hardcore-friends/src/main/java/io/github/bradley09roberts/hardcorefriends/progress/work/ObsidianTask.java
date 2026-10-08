package io.github.bradley09roberts.hardcorefriends.progress.work;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.MiningHelper;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speciality;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.progress.CastPolicy;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressData;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;
import io.github.bradley09roberts.hardcorefriends.progress.Tiers;
import io.github.bradley09roberts.hardcorefriends.progress.Water;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Making obsidian for the enchanting table (4) and a Nether portal (10), the way a careful player does: with a
 * diamond pickaxe and a water bucket, at still lava the camp knows of (lava Scout saw near camp, lava found in the
 * mines) inside the gathering ring. The friend stands on dry ground with no lava touching their feet or head, pours
 * water over a lava source (through the guard's {@code CAST} rule: only still, natural lava, away from any build) and
 * scoops the water back, so the source turns to obsidian and the bucket stays full. Obsidian is then mined out, but
 * only once nothing round it is liquid: lava sources touching it are cast first, and obsidian next to flowing lava
 * or water is left where it is. Never the block under the friend's own feet.
 *
 * <p>With only an empty bucket the friend first fills it at a pool that tops itself up (see {@link Water}). A run casts
 * at most {@value #MAX_CASTS} sources and mines at most {@value #MAX_MINED} blocks (each takes about ten seconds).
 */
public final class ObsidianTask implements CompanionTask {
	private static final int MAX_CASTS = 16;
	private static final int MAX_MINED = 6;
	private static final int MAX_POIS = 8;
	private static final int WORK_BOX = 4;
	private static final double REACH = 4.4;
	private static final String POI_LAVA = "lava";
	private static final int SKIP_TICKS = 24000;

	private enum Phase {
		FETCH,
		FILL,
		GO,
		WORK
	}

	private Phase phase = Phase.FETCH;
	private @Nullable BlockPos stand;
	/** The obsidian being mined right now (each takes about ten seconds). */
	private @Nullable BlockPos mining;
	/** Blocks the guard refused this run. */
	private final Set<BlockPos> skip = new HashSet<>();
	/** The lava or obsidian this run went for. */
	private @Nullable BlockPos target;
	/** Places with no way to them, left alone for a day. */
	private final Map<BlockPos, Long> skipUntil = new HashMap<>();
	private @Nullable BlockPos water;
	private int casts;
	private int mined;
	private boolean announced;

	@Override
	public String id() {
		return "flint.obsidian";
	}

	@Override
	public String describe() {
		return "making obsidian";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || !FriendsConfig.get().allowMining) {
			return 0;
		}
		MinecraftServer server = level.getServer();
		CampData data = Camp.data(server);
		if (!Camp.isCampLevel(level, data) || Camp.isNight(level) || Camp.isDusk(level) || Camp.timeOfDay(level) > 10000) {
			return 0;
		}
		if (!ProgressPlan.wants(server, Items.OBSIDIAN) || !Trips.fitForDeepWork(c, 2)) {
			return 0;
		}
		if (Tiers.bestPickaxe(c) < Tiers.DIAMOND) {
			// Only the miner (or whoever covers mining) takes the camp's diamond pickaxe out of the chest for this.
			boolean miner = c.friendId().role() == Role.MINER || Speciality.covering(c) == Role.MINER;
			if (!miner || Trips.inChest(c, s -> Tiers.pickaxe(s) >= Tiers.DIAMOND) == 0) {
				return 0;
			}
		}
		boolean waterBucket = c.backpack().has(s -> s.is(Items.WATER_BUCKET)) || Trips.inChest(c, s -> s.is(Items.WATER_BUCKET)) > 0;
		boolean bucket = c.backpack().has(s -> s.is(Items.BUCKET)) || Trips.inChest(c, s -> s.is(Items.BUCKET)) > 0;
		if (!waterBucket && !bucket) {
			return 0;
		}
		if (!anyLavaKnown(c, level, data)) {
			return 0;
		}
		return 48 * ProgressPlan.weight(server, Items.OBSIDIAN);
	}

	/** Cheap: a lava spot remembered in the gathering ring, or cast obsidian left standing. */
	private static boolean anyLavaKnown(CompanionEntity c, ServerLevel level, CampData data) {
		for (CampData.Poi poi : data.pois()) {
			if (POI_LAVA.equals(poi.type) && WorldEditGuard.inResourceZone(c, poi.pos)) {
				return true;
			}
		}
		return !ProgressData.get(level.getServer()).castPositions(level, 1).isEmpty();
	}

	@Override
	public boolean start(CompanionEntity c) {
		phase = Phase.FETCH;
		stand = null;
		water = null;
		mining = null;
		target = null;
		skip.clear();
		casts = 0;
		mined = 0;
		announced = false;
		ServerLevel level = (ServerLevel) c.level();
		stand = findStand(c, level);
		if (stand == null) {
			return false;
		}
		boolean ready = Tiers.bestPickaxe(c) >= Tiers.DIAMOND && c.backpack().has(s -> s.is(Items.WATER_BUCKET));
		phase = ready ? Phase.GO : Phase.FETCH;
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (Camp.isNight(level) || Camp.isDusk(level) || stand == null) {
			return finish();
		}
		return switch (phase) {
			case FETCH -> fetch(c);
			case FILL -> fill(c, level);
			case GO -> go(c, level);
			case WORK -> work(c, level);
		};
	}

	@Override
	public void stop(CompanionEntity c) {
		stand = null;
		water = null;
		mining = null;
		skip.clear();
		c.actions().cancelMining();
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
		return 20 * 240;
	}

	private TaskStatus finish() {
		return mined > 0 || casts > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
	}

	// ------------------------------------------------------------------ phases

	private TaskStatus fetch(CompanionEntity c) {
		ChestWalk.State walk = ChestWalk.tick(c);
		if (walk == ChestWalk.State.FAILED) {
			return TaskStatus.FAILURE;
		}
		if (walk == ChestWalk.State.WALKING) {
			return TaskStatus.RUNNING;
		}
		Optional<Container> chest = ChestWalk.chest(c);
		if (chest.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		if (Tiers.bestPickaxe(c) < Tiers.DIAMOND) {
			Trips.take(chest.get(), c, s -> Tiers.pickaxe(s) >= Tiers.DIAMOND, 1);
		}
		if (!c.backpack().has(s -> s.is(Items.WATER_BUCKET))
			&& Trips.take(chest.get(), c, s -> s.is(Items.WATER_BUCKET), 1) == 0 && !c.backpack().has(s -> s.is(Items.BUCKET))) {
			Trips.take(chest.get(), c, s -> s.is(Items.BUCKET), 1);
		}
		if (Tiers.bestPickaxe(c) < Tiers.DIAMOND) {
			return TaskStatus.FAILURE;
		}
		if (c.backpack().has(s -> s.is(Items.WATER_BUCKET))) {
			phase = Phase.GO;
			return TaskStatus.RUNNING;
		}
		if (!c.backpack().has(s -> s.is(Items.BUCKET))) {
			return TaskStatus.FAILURE;
		}
		water = Water.find((ServerLevel) c.level(), c.blockPosition(), true);
		if (water == null) {
			Speech.say(c, Line.NEED_MATERIALS, "a water bucket");
			return TaskStatus.FAILURE;
		}
		phase = Phase.FILL;
		return TaskStatus.RUNNING;
	}

	private TaskStatus fill(CompanionEntity c, ServerLevel level) {
		BlockPos source = water;
		if (source == null) {
			return TaskStatus.FAILURE;
		}
		if (!Trips.reach(c, source)) {
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		if (!Water.fillBucket(c, source)) {
			return TaskStatus.FAILURE;
		}
		phase = Phase.GO;
		return TaskStatus.RUNNING;
	}

	private TaskStatus go(CompanionEntity c, ServerLevel level) {
		BlockPos spot = stand;
		if (spot == null) {
			return TaskStatus.FAILURE;
		}
		if (!c.actions().walkTo(spot, 0.6)) {
			if (c.actions().isStuck()) {
				BlockPos t = target;
				if (t != null) {
					skipUntil.put(t, level.getGameTime() + SKIP_TICKS); // no way there (lava in a cave out of reach): leave it a day
				}
				return finish();
			}
			return TaskStatus.RUNNING;
		}
		if (!lavaSafe(level, c.blockPosition())) {
			return finish(); // not where it looked safe from: leave it
		}
		phase = Phase.WORK;
		return TaskStatus.RUNNING;
	}

	/**
	 * One step at the lava: keep mining the block in hand, else mine obsidian with nothing liquid round it, else cast a
	 * lava source that holds cast obsidian in, else cast an open source. The box is looked at only between blocks, with
	 * cheap checks; the guard's full rule is asked only for the block chosen, and a refused block is skipped.
	 */
	private TaskStatus work(CompanionEntity c, ServerLevel level) {
		if (!c.backpack().has(s -> s.is(Items.WATER_BUCKET)) || mined >= MAX_MINED || casts >= MAX_CASTS
			|| !ProgressPlan.wants(level.getServer(), Items.OBSIDIAN) || c.backpack().freeSlots() == 0) {
			return finish();
		}
		if (!lavaSafe(level, c.blockPosition())) {
			return finish();
		}
		ProgressData progress = ProgressData.get(level.getServer());
		if (mining != null) {
			return mine(c, level, progress, mining);
		}
		if (level.getGameTime() - c.lastEditTick() < 5) {
			return TaskStatus.RUNNING; // the guard paces edits
		}
		BlockPos ready = null;
		BlockPos holdingIn = null;
		BlockPos open = null;
		BlockPos feet = c.blockPosition();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		double bestReady = Double.MAX_VALUE;
		double bestHolding = Double.MAX_VALUE;
		double bestOpen = Double.MAX_VALUE;
		for (int dx = -WORK_BOX; dx <= WORK_BOX; dx++) {
			for (int dy = -3; dy <= 3; dy++) {
				for (int dz = -WORK_BOX; dz <= WORK_BOX; dz++) {
					m.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
					if (!level.isLoaded(m) || skip.contains(m) || !inReach(c, m)) {
						continue;
					}
					BlockState s = level.getBlockState(m);
					double d = m.distSqr(feet);
					if (s.is(Blocks.OBSIDIAN)) {
						if (!progress.isCast(level, m) || MiningHelper.isOnTop(feet, m) || m.equals(feet.below())) {
							continue; // not ours, or the ground under their own feet
						}
						if (!WorldEditGuard.touchesFluid(level, m)) {
							if (d < bestReady) {
								bestReady = d;
								ready = m.immutable();
							}
						} else if (d < bestHolding) {
							BlockPos source = castableNeighbour(c, level, m);
							if (source != null) {
								bestHolding = d;
								holdingIn = source;
							}
						}
					} else if (d < bestOpen && s.is(Blocks.LAVA) && CastPolicy.isStillLavaSource(level, m, s) && exposed(level, m)) {
						bestOpen = d;
						open = m.immutable();
					}
				}
			}
		}
		if (ready != null) {
			mining = ready;
			return mine(c, level, progress, ready);
		}
		BlockPos cast = holdingIn != null ? holdingIn : open;
		if (cast == null) {
			return finish();
		}
		WorldEditGuard.Verdict verdict = WorldEditGuard.canTransform(c, cast, Blocks.OBSIDIAN.defaultBlockState(), WorldEditGuard.Reason.CAST);
		if (!verdict.allowed()) {
			if (!"pacing".equals(verdict.why())) {
				skip.add(cast);
			}
			return TaskStatus.RUNNING;
		}
		return cast(c, level, progress, cast);
	}

	private TaskStatus mine(CompanionEntity c, ServerLevel level, ProgressData progress, BlockPos pos) {
		Actions.Result result = c.actions().mine(pos, WorldEditGuard.Reason.CAST);
		if (result == Actions.Result.RUNNING) {
			return TaskStatus.RUNNING;
		}
		mining = null;
		if (result == Actions.Result.FAILED) {
			skip.add(pos); // refused (out of reach, liquid crept beside it, a build nearby): try another
			return TaskStatus.RUNNING;
		}
		progress.forgetCast(level, pos);
		mined++;
		Camp.data(level.getServer()).addStat("obsidian_mined", 1);
		return TaskStatus.RUNNING;
	}

	private TaskStatus cast(CompanionEntity c, ServerLevel level, ProgressData progress, BlockPos pos) {
		c.getLookControl().setLookAt(Vec3.atCenterOf(pos));
		if (!WorldEditGuard.transformBlock(c, pos, Blocks.OBSIDIAN.defaultBlockState(), WorldEditGuard.Reason.CAST)) {
			return finish();
		}
		c.swingArm();
		level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 1.0F, 1.0F);
		level.levelEvent(LevelEvent.LAVA_FIZZ, pos, 0);
		progress.recordCast(level, pos);
		casts++;
		Camp.data(level.getServer()).addStat("obsidian_made", 1);
		if (!announced) {
			announced = true;
			Speech.say(c, Line.OBSIDIAN_MADE);
		}
		return TaskStatus.RUNNING;
	}

	// ------------------------------------------------------------------ checks

	/** A lava source next to this obsidian that the friend can reach and cast, or null if any liquid there cannot be. */
	private static @Nullable BlockPos castableNeighbour(CompanionEntity c, ServerLevel level, BlockPos obsidian) {
		BlockPos found = null;
		for (Direction d : Direction.values()) {
			BlockPos n = obsidian.relative(d);
			if (level.getFluidState(n).isEmpty()) {
				continue;
			}
			BlockState state = level.getBlockState(n);
			if (!CastPolicy.isStillLavaSource(level, n, state) || !inReach(c, n) || !WorldEditGuard.inResourceZone(c, n)) {
				return null; // water, flowing lava or out of reach beside it: leave this one be
			}
			found = n.immutable();
		}
		return found;
	}

	/** A lava source the guard's cast rule allows (checked without the pacing). */
	private static boolean castable(CompanionEntity c, ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (!CastPolicy.isStillLavaSource(level, pos, s)) {
			return false;
		}
		WorldEditGuard.Verdict v = WorldEditGuard.canTransform(c, pos, Blocks.OBSIDIAN.defaultBlockState(), WorldEditGuard.Reason.CAST);
		return v.allowed() || "pacing".equals(v.why());
	}

	/** Some face of the block is open to the air, so water can be poured on it. */
	private static boolean exposed(ServerLevel level, BlockPos pos) {
		for (Direction d : Direction.values()) {
			BlockPos n = pos.relative(d);
			if (level.isLoaded(n) && level.getBlockState(n).isAir()) {
				return true;
			}
		}
		return false;
	}

	private static boolean inReach(CompanionEntity c, BlockPos pos) {
		return c.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= REACH * REACH;
	}

	/** No lava touching the friend anywhere: round their feet and head, and under them. */
	static boolean lavaSafe(ServerLevel level, BlockPos feet) {
		for (BlockPos p : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 2, 1))) {
			if (!level.isLoaded(p) || level.getFluidState(p).is(FluidTags.LAVA)) {
				return false;
			}
		}
		return true;
	}

	// --------------------------------------------------------------- planning

	/**
	 * A dry, lava-safe standing spot within reach of work: first cast obsidian left from before, then still lava at the
	 * remembered lava spots in the gathering ring. Bounded: a handful of spots, each looked at in a small box.
	 */
	private @Nullable BlockPos findStand(CompanionEntity c, ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		ProgressData progress = ProgressData.get(level.getServer());
		long now = level.getGameTime();
		skipUntil.values().removeIf(until -> until <= now);
		List<BlockPos> targets = new ArrayList<>();
		for (BlockPos p : progress.castPositions(level, 8)) {
			if (!level.isLoaded(p)) {
				continue;
			}
			if (level.getBlockState(p).is(Blocks.OBSIDIAN)) {
				targets.add(p);
			} else {
				progress.forgetCast(level, p); // mined or gone: no longer ours to mine
			}
		}
		List<CampData.Poi> lava = new ArrayList<>();
		for (CampData.Poi poi : data.pois()) {
			if (POI_LAVA.equals(poi.type) && level.isLoaded(poi.pos) && WorldEditGuard.inResourceZone(c, poi.pos)) {
				lava.add(poi);
			}
		}
		lava.sort(Comparator.comparingDouble(p -> p.pos.distSqr(c.blockPosition())));
		for (int i = 0; i < lava.size() && i < MAX_POIS; i++) {
			BlockPos source = lavaNear(c, level, lava.get(i).pos);
			if (source != null) {
				targets.add(source);
			} else if (!anyLava(level, lava.get(i).pos)) {
				data.removePoi(lava.get(i)); // the lava there is gone
			}
		}
		for (BlockPos target : targets) {
			if (skipUntil.containsKey(target)) {
				continue;
			}
			BlockPos spot = standFor(level, target, c.blockPosition());
			if (spot != null) {
				this.target = target;
				return spot;
			}
		}
		return null;
	}

	/** A castable, open lava source within 3 blocks of a remembered lava spot. */
	private static @Nullable BlockPos lavaNear(CompanionEntity c, ServerLevel level, BlockPos centre) {
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-3, -2, -3), centre.offset(3, 2, 3))) {
			if (level.isLoaded(p) && castable(c, level, p) && exposed(level, p)) {
				return p.immutable();
			}
		}
		return null;
	}

	private static boolean anyLava(ServerLevel level, BlockPos centre) {
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-3, -2, -3), centre.offset(3, 2, 3))) {
			if (!level.isLoaded(p) || level.getFluidState(p).is(FluidTags.LAVA)) {
				return true;
			}
		}
		return false;
	}

	/** The nearest standing spot (to {@code near}) within reach of the target that has no lava touching it. */
	private static @Nullable BlockPos standFor(ServerLevel level, BlockPos target, BlockPos near) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int dy = -1; dy <= 2; dy++) {
					feet.set(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
					if (feet.equals(target) || MiningHelper.isOnTop(feet, target) || !level.isLoaded(feet)
						|| !MiningHelper.reachableFrom(feet, target, MiningHelper.WORK_REACH)) {
						continue;
					}
					if (!MiningHelper.isStandable(level, feet) || !lavaSafe(level, feet)) {
						continue;
					}
					double d = feet.distSqr(near);
					if (d < bestDist) {
						bestDist = d;
						best = feet.immutable();
					}
				}
			}
		}
		return best;
	}
}
