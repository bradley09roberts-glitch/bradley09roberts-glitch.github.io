package io.github.bradley09roberts.hardcorefriends.ai.task.common;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Passes the night quietly near the camp centre: strolling a few steps, then pausing to look at the others. Says
 * good morning once when the night is over. Aegis patrols instead.
 */
public final class NightRestTask implements CompanionTask {
	/** Friends stay within this many blocks of the camp centre while resting. */
	private static final int STAY = 6;
	private static final int LEGS_PER_RUN = 4;

	private boolean restedTonight;
	private @Nullable BlockPos spot;
	private int pause;
	private int legs;

	@Override
	public String id() {
		return "common.night_rest";
	}

	@Override
	public String describe() {
		return "resting at camp";
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.friendId() == FriendId.AEGIS) {
			return 0;
		}
		ServerLevel level = (ServerLevel) c.level();
		if (!Camp.isNight(level)) {
			greetMorning(c);
			return 0;
		}
		int radius = WorldEditGuard.campRadius(c);
		return Camp.horizontalDistSqr(c.blockPosition(), c.homePos()) <= (double) radius * radius ? 15 : 0;
	}

	private void greetMorning(CompanionEntity c) {
		if (restedTonight) {
			restedTonight = false;
			Speech.say(c, Line.MORNING);
		}
	}

	@Override
	public boolean start(CompanionEntity c) {
		spot = null;
		pause = 0;
		legs = 0;
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (!Camp.isNight(level)) {
			greetMorning(c);
			return TaskStatus.SUCCESS;
		}
		restedTonight = true;
		if (pause > 0) {
			pause--;
			lookAtFriend(c);
			return TaskStatus.RUNNING;
		}
		if (spot == null) {
			if (legs >= LEGS_PER_RUN) {
				return TaskStatus.SUCCESS;
			}
			spot = Upkeep.randomSpotNear(c, c.restPos(), c.restPos().equals(c.homePos()) ? STAY - 2 : 2);
			if (spot == null) {
				legs++;
				pause = 100;
				return TaskStatus.RUNNING;
			}
		}
		if (c.actions().walkTo(spot, 1.5) || c.actions().isStuck()) {
			c.actions().stopWalking();
			spot = null;
			legs++;
			pause = 60 + c.getRandom().nextInt(100);
		}
		return TaskStatus.RUNNING;
	}

	private static void lookAtFriend(CompanionEntity c) {
		CompanionEntity nearest = null;
		double best = 8 * 8;
		for (CompanionEntity other : Companions.near((ServerLevel) c.level(), c.getBoundingBox().inflate(8))) {
			double d = other.distanceToSqr(c);
			if (other != c && d < best) {
				best = d;
				nearest = other;
			}
		}
		if (nearest != null) {
			c.getLookControl().setLookAt(nearest);
		}
	}

	@Override
	public void stop(CompanionEntity c) {
		spot = null;
		pause = 0;
		legs = 0;
	}

	@Override
	public int maxTicks() {
		return 20 * 60;
	}
}
