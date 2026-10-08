package io.github.bradley09roberts.hardcorefriends.ai.role.mine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Flint mines ores that are already exposed to air: ones he can see within 16 blocks, plus ore spots Scout reported
 * inside the resource zone. He only digs an ore when he carries a pickaxe that actually gets drops from it, lights
 * dark spots with a torch, and follows a vein for up to {@value #MAX_PER_RUN} blocks.
 */
public final class MineExposedOreTask implements CompanionTask {
	public static final String POI_ORE = "ore";
	private static final int SCAN_RADIUS = 16;
	private static final int SCAN_HEIGHT = 10;
	private static final int SCAN_INTERVAL = 100;
	private static final int MAX_CANDIDATES = 24;
	private static final int MAX_PER_RUN = 8;
	private static final int SKIP_TICKS = 20 * 60;

	private final List<BlockPos> candidates = new ArrayList<>();
	private final Map<BlockPos, Long> skipUntil = new HashMap<>();
	private long lastScan = -SCAN_INTERVAL;
	private @Nullable BlockPos chosen;
	private @Nullable BlockPos chosenStand;

	private @Nullable BlockPos target;
	private @Nullable BlockPos stand;
	private int minedThisRun;
	private boolean litThisTarget;

	@Override
	public String id() {
		return "flint.mine_ore";
	}

	@Override
	public String describe() {
		return "mining ore";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || !FriendsConfig.get().allowMining || !MiningHelper.hasAnyPickaxe(c)) {
			return 0;
		}
		if (c.backpack().freeSlots() == 0) {
			return 0;
		}
		long now = level.getGameTime();
		if (now - lastScan >= SCAN_INTERVAL || (chosen != null && !isTargetable(c, level, chosen))) {
			lastScan = now;
			rescan(c, level);
		}
		return chosen == null ? 0 : 55 * CampNeeds.weight(CampNeeds.Need.ORE);
	}

	@Override
	public boolean start(CompanionEntity c) {
		if (chosen == null || chosenStand == null) {
			return false;
		}
		target = chosen;
		stand = chosenStand;
		minedThisRun = 0;
		litThisTarget = false;
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (target == null || stand == null) {
			return finish();
		}
		Actions actions = c.actions();
		BlockState state = level.getBlockState(target);
		if (!MiningHelper.isWantedOre(state)) {
			return next(c, level);
		}
		boolean near = c.position().distanceToSqr(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5) <= 2.25;
		if (!near || !actions.canReach(target)) {
			if (actions.walkTo(stand, 1.0) && !actions.canReach(target)) {
				skip(level, target);
				return finish();
			}
			if (actions.isStuck()) {
				skip(level, target);
				return finish();
			}
			return TaskStatus.RUNNING;
		}
		if (!litThisTarget) {
			litThisTarget = MiningHelper.lightIfDark(c);
			if (!litThisTarget) {
				return TaskStatus.RUNNING;
			}
		}
		if (!MiningHelper.hasPickaxeFor(c, state)) {
			Speech.say(c, Line.NEED_TOOL, "better pickaxe");
			skip(level, target);
			return finish();
		}
		Actions.Result result = actions.mine(target, WorldEditGuard.Reason.MINE);
		if (result == Actions.Result.RUNNING) {
			return TaskStatus.RUNNING;
		}
		if (result == Actions.Result.FAILED) {
			skip(level, target);
			return finish();
		}
		CampData data = Camp.data(level.getServer());
		data.addStat("ores_mined", 1);
		minedThisRun++;
		forgetMinedPois(level, data, target);
		return next(c, level);
	}

	@Override
	public void stop(CompanionEntity c) {
		target = null;
		stand = null;
		chosen = null;
		chosenStand = null;
		lastScan = -SCAN_INTERVAL;
	}

	@Override
	public int failureCooldown() {
		return 100;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}

	// ------------------------------------------------------------------ helpers

	private TaskStatus finish() {
		return minedThisRun > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
	}

	/** Follows the vein: picks the next exposed ore close to the last one, or ends the run. */
	private TaskStatus next(CompanionEntity c, ServerLevel level) {
		if (minedThisRun >= MAX_PER_RUN || c.backpack().freeSlots() == 0 || target == null) {
			return finish();
		}
		BlockPos from = target;
		BlockPos best = null;
		BlockPos bestStand = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(from.offset(-3, -3, -3), from.offset(3, 3, 3))) {
			if (!isTargetable(c, level, p)) {
				continue;
			}
			double d = p.distSqr(c.blockPosition());
			if (d >= bestDist) {
				continue;
			}
			BlockPos spot = c.actions().canReach(p) ? c.blockPosition() : MiningHelper.standSpotFor(level, p, c.blockPosition());
			if (spot != null) {
				best = p.immutable();
				bestStand = spot;
				bestDist = d;
			}
		}
		if (best == null) {
			return finish();
		}
		target = best;
		stand = bestStand;
		litThisTarget = false;
		return TaskStatus.RUNNING;
	}

	/** Exposed, wanted, allowed by the guard, not skipped, and Flint has the right pickaxe. */
	private boolean isTargetable(CompanionEntity c, ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!MiningHelper.isWantedOre(state) || !MiningHelper.touchesAir(level, pos) || isSkipped(level, pos)) {
			return false;
		}
		if (Camp.isNight(level) && !WorldEditGuard.inCamp(c, pos)) {
			return false;
		}
		if (isMineFloor(level, pos)) {
			return false;
		}
		WorldEditGuard.Verdict verdict = WorldEditGuard.canBreak(c, pos, WorldEditGuard.Reason.MINE);
		if (!verdict.allowed() && !"pacing".equals(verdict.why())) {
			return false;
		}
		return MiningHelper.hasPickaxeFor(c, state);
	}

	private void rescan(CompanionEntity c, ServerLevel level) {
		chosen = null;
		chosenStand = null;
		candidates.clear();
		long now = level.getGameTime();
		skipUntil.values().removeIf(until -> until <= now);
		BlockPos centre = c.blockPosition();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
			for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
				m.set(centre.getX() + dx, centre.getY(), centre.getZ() + dz);
				if (!level.isLoaded(m)) {
					continue;
				}
				for (int dy = -SCAN_HEIGHT; dy <= SCAN_HEIGHT; dy++) {
					m.setY(centre.getY() + dy);
					if (MiningHelper.isWantedOre(level.getBlockState(m)) && MiningHelper.touchesAir(level, m)) {
						candidates.add(m.immutable());
					}
				}
			}
		}
		addReportedOres(c, level);
		candidates.sort(Comparator.comparingDouble(p -> p.distSqr(centre)));
		boolean lackedTool = false;
		int checked = 0;
		for (BlockPos p : candidates) {
			if (checked++ >= MAX_CANDIDATES) {
				break;
			}
			BlockState state = level.getBlockState(p);
			if (!MiningHelper.hasPickaxeFor(c, state)) {
				lackedTool |= !isSkipped(level, p);
				continue;
			}
			if (!isTargetable(c, level, p)) {
				continue;
			}
			BlockPos spot = MiningHelper.standSpotFor(level, p, centre);
			if (spot != null) {
				chosen = p;
				chosenStand = spot;
				return;
			}
		}
		if (lackedTool) {
			Speech.say(c, Line.NEED_TOOL, "better pickaxe");
		}
	}

	/** Ore spots Scout reported inside the resource zone. Spots with no ore left are forgotten. */
	private void addReportedOres(CompanionEntity c, ServerLevel level) {
		if (!Camp.isCampLevel(level, Camp.data(level.getServer()))) {
			return;
		}
		CampData data = Camp.data(level.getServer());
		for (CampData.Poi poi : List.copyOf(data.pois())) {
			if (!POI_ORE.equals(poi.type) || !level.isLoaded(poi.pos) || !WorldEditGuard.inResourceZone(c, poi.pos)) {
				continue;
			}
			BlockPos exposed = null;
			boolean anyOre = false;
			for (BlockPos p : BlockPos.betweenClosed(poi.pos.offset(-2, -2, -2), poi.pos.offset(2, 2, 2))) {
				if (MiningHelper.isWantedOre(level.getBlockState(p))) {
					anyOre = true;
					if (MiningHelper.touchesAir(level, p)) {
						exposed = p.immutable();
						break;
					}
				}
			}
			if (!anyOre) {
				data.removePoi(poi);
			} else if (exposed != null && !candidates.contains(exposed)) {
				candidates.add(exposed);
			}
		}
	}

	/** Removes ore spots whose ore is now all mined out. */
	private static void forgetMinedPois(ServerLevel level, CampData data, BlockPos mined) {
		for (CampData.Poi poi : List.copyOf(data.pois())) {
			if (!POI_ORE.equals(poi.type) || poi.pos.distSqr(mined) > 16) {
				continue;
			}
			boolean left = false;
			for (BlockPos p : BlockPos.betweenClosed(poi.pos.offset(-2, -2, -2), poi.pos.offset(2, 2, 2))) {
				if (MiningHelper.isWantedOre(level.getBlockState(p))) {
					left = true;
					break;
				}
			}
			if (!left) {
				data.removePoi(poi);
			}
		}
	}

	/**
	 * An ore that is the floor of a walkway in Flint's mine (stairs or tunnels). Digging it would leave a hole that
	 * could trap him on the way back up, so it stays.
	 */
	private static boolean isMineFloor(ServerLevel level, BlockPos pos) {
		MinePlan plan = MinePlan.of(Camp.data(level.getServer()));
		return plan.inBox(pos) && MiningHelper.isPassable(level, pos.above()) && MiningHelper.isPassable(level, pos.above(2));
	}

	private boolean isSkipped(ServerLevel level, BlockPos pos) {
		Long until = skipUntil.get(pos);
		return until != null && until > level.getGameTime();
	}

	private void skip(ServerLevel level, BlockPos pos) {
		skipUntil.put(pos.immutable(), level.getGameTime() + SKIP_TICKS);
	}
}
