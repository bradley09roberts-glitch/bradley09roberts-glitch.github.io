package io.github.bradley09roberts.hardcorefriends.defence;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;

/**
 * To the posts: when the alarm rings, every fighter at work in the village ({@link Duty}) goes to the post nearest the
 * danger (a gate, a wall, the bell, the camp centre: {@link Posts#alarmPost}), never within
 * {@value Alarm#CREEPER_CLEARANCE} blocks of a creeper, and stands ready there until the all-clear, looking out. The
 * fighting itself is the combat reflexes' (the rally, the guards' own target choice in {@link DefenceTargetGoal}), which
 * take over whenever a hostile comes within reach. The friend on the night watch and the guards on duty keep their own
 * posts. Scores {@value #SCORE}: above all work, below taking cover and ringing the bell.
 */
final class ToPostsTask implements CompanionTask {
	static final String ID = "defence.to_posts";
	private static final double SCORE = 120;
	private static final int RETARGET = 20 * 5;
	private static final double REACH = 2;

	private @Nullable BlockPos post;
	private boolean arrived;
	private int ticks;
	private @Nullable LivingEntity watching;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return arrived ? "standing ready at a post" : "heading to a post";
	}

	@Override
	public double score(CompanionEntity c) {
		return Duty.atAlarm(c) ? SCORE : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return false;
		}
		post = Posts.alarmPost(level, c);
		arrived = post == null;
		ticks = 0;
		Alarm.say(c, Line.TO_THE_WALLS);
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (!Duty.atAlarm(c) || !(c.level() instanceof ServerLevel level)) {
			return TaskStatus.SUCCESS; // the all-clear, or no longer fit to stand: the next job decides
		}
		if (++ticks % RETARGET == 0) {
			BlockPos next = Posts.alarmPost(level, c);
			if (next != null && !next.equals(post)) {
				post = next;
				arrived = false;
			}
		}
		BlockPos to = post;
		if (!arrived && to != null) {
			if (c.actions().walkTo(to, REACH)) {
				arrived = true;
			} else if (c.actions().isStuck()) {
				arrived = true; // as near as they can get: stand ready here
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		if (ticks % 10 == 0 || watching != null && !watching.isAlive()) {
			watching = Threats.nearest(c, 24);
		}
		LivingEntity threat = watching;
		if (threat != null) {
			c.getLookControl().setLookAt(threat);
		} else {
			Vec3 focus = Alarm.focus(level);
			if (focus != null) {
				c.getLookControl().setLookAt(focus.x, focus.y + 1, focus.z);
			}
		}
		return TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		post = null;
		watching = null;
		arrived = false;
		ticks = 0;
	}

	@Override
	public int failureCooldown() {
		return 60;
	}

	@Override
	public int successCooldown() {
		return 40;
	}

	@Override
	public int maxTicks() {
		return 20 * 60 * 6;
	}
}
