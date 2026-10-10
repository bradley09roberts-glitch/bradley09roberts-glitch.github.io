package io.github.bradley09roberts.hardcorefriends.life;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Taking down what was put up for an occasion: the winter lights the morning after, and a note block a musician was
 * called away from. By day, any grown-up walks over and takes it down through the edit guard (only while it is still
 * the friends' own block: one a player broke or changed is simply forgotten); it goes into their backpack and from
 * there back to the chest. One friend to a block. A block the guard keeps refusing (the camp moved away, say) is let
 * go after a few tries.
 */
final class TidyUpTask implements CompanionTask {
	static final String ID = "life.tidy_up";
	private static final double SCORE = 30;
	private static final int GIVE_UP = 5;

	/** Who is taking down which block. */
	private static final Map<BlockPos, UUID> CLAIMS = new HashMap<>();

	private LifeData.@Nullable Temp target;
	private int ticks;

	static void clear() {
		CLAIMS.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "tidying up after the festivities";
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.isChild() || !Places.free(c) || !(c.level() instanceof ServerLevel level) || Camp.isNight(level)
			|| Calendar.time(level.getServer()) > 11500) {
			return 0;
		}
		return due(c, level) != null ? SCORE : 0;
	}

	/** The nearest block due to come down in this world that nobody else is seeing to. */
	private static LifeData.@Nullable Temp due(CompanionEntity c, ServerLevel level) {
		LifeData data = LifeData.get(level.getServer());
		if (data.temps.isEmpty()) {
			return null;
		}
		long clock = level.getServer().overworld().getOverworldClockTime();
		String dim = Camp.dimensionId(level);
		LifeData.Temp best = null;
		double bestDist = Double.MAX_VALUE;
		for (LifeData.Temp t : data.temps) {
			if (!t.dimension.equals(dim) || clock < t.until || MusicTask.IN_USE.contains(t.pos)) {
				continue;
			}
			UUID holder = CLAIMS.get(t.pos);
			if (holder != null && !holder.equals(c.getUUID()) && Places.loaded(level, holder) != null) {
				continue;
			}
			double d = t.pos.distSqr(c.blockPosition());
			if (d < bestDist) {
				bestDist = d;
				best = t;
			}
		}
		return best;
	}

	@Override
	public boolean start(CompanionEntity c) {
		target = due(c, (ServerLevel) c.level());
		if (target == null) {
			return false;
		}
		CLAIMS.put(target.pos, c.getUUID());
		ticks = 0;
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		LifeData.Temp t = target;
		if (t == null) {
			return TaskStatus.FAILURE;
		}
		LifeData data = LifeData.get(level.getServer());
		if (!level.isLoaded(t.pos)) {
			return TaskStatus.FAILURE;
		}
		if (!level.getBlockState(t.pos).is(t.block) || !Camp.data(level.getServer()).isPlacedByFriends(level, t.pos)) {
			data.removeTemp(t); // gone already, or no longer ours to take
			return TaskStatus.SUCCESS;
		}
		if (!c.actions().canReach(t.pos)) {
			if (!c.actions().walkTo(t.pos, 2.0) && c.actions().isStuck()) {
				return giveUp(data, t);
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		c.getLookControl().setLookAt(t.pos.getX() + 0.5, t.pos.getY() + 0.5, t.pos.getZ() + 0.5);
		if (++ticks % 10 != 0) {
			return TaskStatus.RUNNING;
		}
		if (WorldEditGuard.breakBlock(c, t.pos, WorldEditGuard.Reason.BUILD)) {
			c.swingArm();
			data.removeTemp(t);
			return TaskStatus.SUCCESS;
		}
		return ticks > 20 * 5 ? giveUp(data, t) : TaskStatus.RUNNING;
	}

	private static TaskStatus giveUp(LifeData data, LifeData.Temp t) {
		if (++t.fails >= GIVE_UP) {
			data.removeTemp(t); // left as it is: no longer ours to take down
		}
		return TaskStatus.FAILURE;
	}

	@Override
	public void stop(CompanionEntity c) {
		if (target != null) {
			CLAIMS.remove(target.pos, c.getUUID());
		}
		target = null;
	}

	@Override
	public int failureCooldown() {
		return 20 * 60;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
