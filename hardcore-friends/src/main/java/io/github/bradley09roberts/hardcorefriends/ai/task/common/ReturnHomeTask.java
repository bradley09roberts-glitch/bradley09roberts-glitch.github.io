package io.github.bradley09roberts.hardcorefriends.ai.task.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Brings a friend back inside the camp at dusk and at night, when wandering far from the fire gets dangerous.
 * Aegis is the exception: the guard patrols at night instead.
 */
public final class ReturnHomeTask implements CompanionTask {
	/** How close to the camp centre a friend walks before the trip counts as done. */
	private static final double ARRIVE = 6.0;

	private boolean announced;

	@Override
	public String id() {
		return "common.return_home";
	}

	@Override
	public String describe() {
		return "heading home for the night";
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.friendId() == FriendId.AEGIS || c.mode() != CompanionMode.WORK) {
			return 0;
		}
		return shouldBeHome(c) ? 85 : 0;
	}

	/** True when it is getting dark and the friend is outside the comfortable part of the camp. */
	public static boolean shouldBeHome(CompanionEntity c) {
		return sendsHome(c, c.blockPosition());
	}

	/**
	 * True when this task would call the friend home from {@code pos}: it is dusk or night and the spot lies outside
	 * the comfortable part of the camp. Work outside the camp checks its targets with this, so a friend never walks
	 * out to a job only to be sent straight back.
	 */
	public static boolean sendsHome(CompanionEntity c, BlockPos pos) {
		ServerLevel level = (ServerLevel) c.level();
		int limit = Math.max(4, WorldEditGuard.campRadius(c) - 4);
		return (Camp.isNight(level) || Camp.isDusk(level))
			&& Camp.horizontalDistSqr(pos, c.homePos()) > (double) limit * limit;
	}

	@Override
	public boolean start(CompanionEntity c) {
		announced = false;
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (!announced) {
			Speech.say(c, Line.NIGHT_RETURN);
			announced = true;
		}
		BlockPos home = c.homePos();
		if (c.actions().walkTo(home, ARRIVE)) {
			return TaskStatus.SUCCESS;
		}
		return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		announced = false;
	}

	@Override
	public int failureCooldown() {
		return 100;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}
}
