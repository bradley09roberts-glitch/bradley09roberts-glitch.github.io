package io.github.bradley09roberts.hardcorefriends.ai.task.common;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Keeping the night watch, for whoever the rota has put on it ({@link NightWatch}) other than Aegis, whose guard duty
 * is his watch. The watcher stands by the camp's lit campfire (or the camp centre), stepping round it now and then
 * and looking out over the camp. Spotting hostiles and raising the alarm is {@link NightWatch}'s lookout, and fighting
 * is the combat reflexes': the watcher goes for any hostile that comes into the camp
 * ({@link io.github.bradley09roberts.hardcorefriends.ai.goal.MutualDefenceTargetGoal}). The watch outranks every
 * pastime, chat and warm-up, and a meal unless the watcher is hungry; it runs in half-minute rounds so a pressing need
 * gets a look in between them.
 */
public final class WatchTask implements CompanionTask {
	/** The watch's score: urgent upkeep, above bedtime's pastimes and below a hungry friend's meal (80). */
	public static final double SCORE = 75;
	private static final int ROUND_TICKS = 20 * 30;
	/** How long the watcher stands at one spot before stepping round the fire. */
	private static final int STAND_TICKS = 20 * 10;
	private static final int POSTS = 6;
	private static final int POST_DISTANCE = 3;

	private @Nullable BlockPos centre;
	private @Nullable BlockPos post;
	private int postIndex;
	private int ticks;
	private int standing;
	private boolean arrived;

	@Override
	public String id() {
		return "common.watch";
	}

	@Override
	public String describe() {
		return "keeping watch";
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.isFighter()) {
			return 0; // Aegis keeps his watch on guard duty
		}
		return NightWatch.isOnWatch(c) ? SCORE : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		BlockPos fire = Spots.litCampfire(c);
		centre = fire != null ? fire : c.homePos();
		ticks = 0;
		standing = 0;
		arrived = false;
		postIndex = c.rosterIndex() % POSTS;
		post = postNear(c, centre, postIndex);
		Speech.say(c, Line.NIGHT_WATCH);
		return post != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (!NightWatch.isOnWatch(c)) {
			return TaskStatus.SUCCESS; // the watch is over (or has passed to someone else)
		}
		if (post == null || centre == null) {
			return TaskStatus.FAILURE;
		}
		if (++ticks > ROUND_TICKS) {
			return TaskStatus.SUCCESS;
		}
		if (!arrived) {
			boolean there = c.actions().walkTo(post, 1.0);
			if (!there && c.actions().isStuck()) {
				if (c.blockPosition().distSqr(centre) > 8 * 8) {
					return TaskStatus.FAILURE;
				}
				there = true; // near enough to the fire to keep watch from here
			}
			if (there) {
				c.actions().stopWalking();
				arrived = true;
				standing = 0;
			}
			return TaskStatus.RUNNING;
		}
		c.getNavigation().stop();
		LivingEntity threat = Threats.nearest(c, NightWatch.LOOKOUT_RANGE);
		if (threat != null) {
			c.getLookControl().setLookAt(threat);
		} else {
			// Look out over the camp, turning slowly from one side to the next.
			double angle = (c.tickCount / 60) * (Math.PI / 3);
			c.getLookControl().setLookAt(c.getX() + Math.cos(angle) * 8, c.getEyeY(), c.getZ() + Math.sin(angle) * 8);
		}
		if (++standing >= STAND_TICKS) {
			// A step round the fire to another side.
			postIndex = (postIndex + 1) % POSTS;
			BlockPos next = postNear(c, centre, postIndex);
			if (next != null) {
				post = next;
				arrived = false;
			}
			standing = 0;
		}
		return TaskStatus.RUNNING;
	}

	/** A standing spot a few blocks from the fire (or camp centre) on one of its sides; null if none is free. */
	private static @Nullable BlockPos postNear(CompanionEntity c, BlockPos centre, int index) {
		ServerLevel level = (ServerLevel) c.level();
		for (int k = 0; k < POSTS; k++) {
			double angle = (index + k) * 2 * Math.PI / POSTS;
			BlockPos p = Spots.standable(level, centre.offset((int) Math.round(Math.cos(angle) * POST_DISTANCE), 0,
				(int) Math.round(Math.sin(angle) * POST_DISTANCE)));
			if (p != null) {
				return p;
			}
		}
		return Spots.standable(level, centre);
	}

	@Override
	public void stop(CompanionEntity c) {
		post = null;
		centre = null;
		arrived = false;
		ticks = 0;
		standing = 0;
	}

	@Override
	public int failureCooldown() {
		return 100;
	}

	@Override
	public int maxTicks() {
		return ROUND_TICKS + 20 * 30;
	}
}
