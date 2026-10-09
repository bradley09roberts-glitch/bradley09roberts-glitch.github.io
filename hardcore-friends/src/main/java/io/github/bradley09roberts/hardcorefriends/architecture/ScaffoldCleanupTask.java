package io.github.bradley09roberts.hardcorefriends.architecture;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Takes down scaffolding left standing (a builder interrupted and gone, a world closed mid-climb): walks up to the
 * pillar and digs it out from the top down, from the ground beside it, so nothing is left behind. Never a pillar a
 * friend is standing on (they bring themselves down). The builder's job (a {@code SpecialityTask} for the builder),
 * one friend at a time.
 */
public final class ScaffoldCleanupTask implements CompanionTask {
	public static final String ID = "oak.scaffold";
	private static final int PER_RUN = 12;

	private @Nullable BlockPos current;
	private int dug;
	private int ticksOnBlock;
	/** Pillars that could not be reached lately, by column, with the game time to try again. */
	private final Map<Long, Long> unreachable = new HashMap<>();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "taking down scaffolding";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || !Scaffold.any(level)) {
			return 0;
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return 0;
		}
		return next(c, level) != null ? 50 : 0;
	}

	/** The top block of the nearest pillar nobody stands on and that is not set aside, or null. */
	private @Nullable BlockPos next(CompanionEntity c, ServerLevel level) {
		List<BlockPos> all = Scaffold.all(level);
		long now = level.getGameTime();
		unreachable.values().removeIf(until -> until <= now);
		Map<Long, BlockPos> tops = new HashMap<>();
		for (BlockPos p : all) {
			long column = BlockPos.asLong(p.getX(), 0, p.getZ());
			if (unreachable.containsKey(column) || !level.isLoaded(p)) {
				continue;
			}
			BlockPos top = tops.get(column);
			if (top == null || p.getY() > top.getY()) {
				tops.put(column, p);
			}
		}
		return tops.values().stream()
			.filter(p -> Scaffold.isScaffold(level, p) && !someoneOn(level, p))
			.min(Comparator.comparingDouble(p -> p.distSqr(c.blockPosition())))
			.orElse(null);
	}

	/** True if a friend stands on (or right above) this pillar. */
	private static boolean someoneOn(ServerLevel level, BlockPos top) {
		return !level.getEntitiesOfClass(CompanionEntity.class, new AABB(top.above()).inflate(0.3, 1.0, 0.3)).isEmpty();
	}

	@Override
	public boolean start(CompanionEntity c) {
		current = next(c, (ServerLevel) c.level());
		dug = 0;
		ticksOnBlock = 0;
		return current != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos target = current;
		if (target == null) {
			return dug > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
		}
		if (!Scaffold.isScaffold(level, target) || someoneOn(level, target)) {
			return moveOn(c, level);
		}
		Actions actions = c.actions();
		if (++ticksOnBlock > 20 * 25) {
			setAside(level, target);
			return moveOn(c, level);
		}
		if (!actions.canReach(target)) {
			actions.walkTo(target, 2.0);
			if (actions.isStuck()) {
				actions.stopWalking();
				setAside(level, target);
				return moveOn(c, level);
			}
			return TaskStatus.RUNNING;
		}
		actions.stopWalking();
		switch (actions.mine(target, WorldEditGuard.Reason.BUILD)) {
			case DONE -> {
				Scaffold.remove(level, target);
				dug++;
				return moveOn(c, level);
			}
			case FAILED -> {
				setAside(level, target);
				return moveOn(c, level);
			}
			case RUNNING -> {
				return TaskStatus.RUNNING;
			}
		}
		return TaskStatus.RUNNING;
	}

	private void setAside(ServerLevel level, BlockPos p) {
		unreachable.put(BlockPos.asLong(p.getX(), 0, p.getZ()), level.getGameTime() + 20 * 60 * 2);
	}

	private TaskStatus moveOn(CompanionEntity c, ServerLevel level) {
		ticksOnBlock = 0;
		if (dug >= PER_RUN) {
			current = null;
			return TaskStatus.SUCCESS;
		}
		current = next(c, level);
		if (current == null) {
			return dug > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
		}
		return TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().reset();
		current = null;
	}

	@Override
	public int failureCooldown() {
		return 600;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}
}
