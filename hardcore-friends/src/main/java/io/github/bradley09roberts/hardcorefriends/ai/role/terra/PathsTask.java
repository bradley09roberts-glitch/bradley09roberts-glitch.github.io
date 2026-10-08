package io.github.bradley09roberts.hardcorefriends.ai.role.terra;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampProgress;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * Terra lays dirt paths with a shovel along the {@link PathPlan}: up to 16 grass or dirt blocks per run, clearing
 * grass on top first. Cells under buildings, water or anything else are left alone. Once the camp is a Hamlet and
 * nine in ten path cells are done, the camp paths count as finished.
 */
public final class PathsTask implements CompanionTask {
	private static final int PER_RUN = 16;
	private static final int SCAN_INTERVAL = 100;
	private static final double DONE_FRACTION = 0.9;
	private static final double WORK_REACH = 2.5;

	private final List<BlockPos> todo = new ArrayList<>();
	private int doneCells;
	private int pathableCells;
	private long scannedAt = -100_000;

	private @Nullable BlockPos current;
	private int converted;

	@Override
	public String id() {
		return "terra.paths";
	}

	@Override
	public String describe() {
		return "laying paths";
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		if (data.stage() < 1 || data.campPos().isEmpty() || !Camp.isCampLevel(level, data)) {
			return 0;
		}
		if (Camp.isNight(level) && !WorldEditGuard.inCamp(c, c.blockPosition())) {
			return 0;
		}
		rescanIfStale(c, level, data);
		checkFinished(c, data);
		if (todo.isEmpty()) {
			return 0;
		}
		if (!c.actions().hasTool(ItemTags.SHOVELS)) {
			Speech.say(c, Line.NEED_TOOL, "shovel");
			return 0;
		}
		return 45;
	}

	private void rescanIfStale(CompanionEntity c, ServerLevel level, CampData data) {
		if (level.getGameTime() - scannedAt < SCAN_INTERVAL) {
			return;
		}
		scannedAt = level.getGameTime();
		scan(c, level, data);
	}

	/** Sorts every planned path column into done, still to do, or blocked. */
	private void scan(CompanionEntity c, ServerLevel level, CampData data) {
		todo.clear();
		doneCells = 0;
		pathableCells = 0;
		BlockPos centre = data.campPos().orElseThrow();
		for (BlockPos column : PathPlan.columns(data)) {
			BlockPos ground = Landscape.ground(level, column.getX(), column.getZ(), centre.getY() + 3, centre.getY() - 4);
			if (ground == null || !WorldEditGuard.inCamp(c, ground)) {
				continue;
			}
			BlockState state = level.getBlockState(ground);
			if (state.is(Blocks.DIRT_PATH)) {
				doneCells++;
				pathableCells++;
			} else if (Landscape.isEarth(state) && Landscape.isOpen(level.getBlockState(ground.above()))) {
				pathableCells++;
				todo.add(ground);
			}
		}
	}

	private void checkFinished(CompanionEntity c, CampData data) {
		if (data.stage() >= 2 && !data.isCompleted(Structures.PATHS) && pathableCells >= 4
			&& doneCells >= DONE_FRACTION * pathableCells) {
			CampProgress.complete(c, Structures.PATHS);
		}
	}

	@Override
	public boolean start(CompanionEntity c) {
		current = null;
		converted = 0;
		if (todo.isEmpty() || !c.actions().hasTool(ItemTags.SHOVELS)) {
			return false;
		}
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (current == null) {
			if (converted >= PER_RUN || todo.isEmpty()) {
				return finish(c, level);
			}
			current = nearest(c);
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
		BlockState state = level.getBlockState(cell);
		BlockState path = Blocks.DIRT_PATH.defaultBlockState();
		if (state.is(Blocks.DIRT_PATH) || !Landscape.isEarth(state)) {
			current = null;
			return TaskStatus.RUNNING;
		}
		WorldEditGuard.Verdict verdict = WorldEditGuard.canTransform(c, cell, path, Reason.LANDSCAPE);
		if (!verdict.allowed()) {
			if (!"pacing".equals(verdict.why())) {
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		if (!actions.hasTool(ItemTags.SHOVELS)) {
			Speech.say(c, Line.NEED_TOOL, "shovel");
			return finish(c, level);
		}
		if (actions.transform(cell, path, Reason.LANDSCAPE, ItemTags.SHOVELS)) {
			converted++;
		}
		current = null;
		return TaskStatus.RUNNING;
	}

	private @Nullable BlockPos nearest(CompanionEntity c) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : todo) {
			double d = p.distSqr(c.blockPosition());
			if (d < bestDist) {
				bestDist = d;
				best = p;
			}
		}
		todo.remove(best);
		return best;
	}

	private TaskStatus finish(CompanionEntity c, ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		scan(c, level, data);
		scannedAt = level.getGameTime();
		checkFinished(c, data);
		if (converted > 0) {
			data.addStat("paths_laid", converted);
		}
		return converted > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
	}

	@Override
	public void stop(CompanionEntity c) {
		current = null;
		converted = 0;
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
		return 20 * 90;
	}
}
