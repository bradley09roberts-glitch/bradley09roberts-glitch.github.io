package io.github.bradley09roberts.hardcorefriends.defence;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;

/**
 * Ringing the village bell when the alarm goes up: the friend {@link Ringing} asked runs to the bell and rings it
 * {@value #STROKES} times, a second apart, with the game's own bell ring (players hear it; raiders near it glow), and
 * calls out the danger. It scores {@value #SCORE}, above every job but a desperate need, and only for the friend asked.
 * A creeper coming near the bell, near them or into their way ({@link Ringing}, twice a second), or the way there
 * failing, lets them off (someone else is asked).
 */
final class RingBellTask implements CompanionTask {
	static final String ID = "defence.ring_bell";
	/** Above taking cover and going to the posts (so the friend asked leaves those to ring it), below a desperate need. */
	static final double SCORE = 135;
	private static final int STROKES = 3;
	private static final int STROKE_GAP = 20;

	private @Nullable BlockPos bell;
	private int strokes;
	private int wait;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return strokes > 0 ? "ringing the alarm bell" : "running to ring the bell";
	}

	@Override
	public double score(CompanionEntity c) {
		return Alarm.isRinger(c) ? SCORE : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		bell = Alarm.bellToRing();
		strokes = 0;
		wait = 0;
		return bell != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		BlockPos to = bell;
		Alarm.State s = Alarm.state();
		if (to == null || s == null || !Alarm.isRinger(c) || !(c.level() instanceof ServerLevel level)) {
			return TaskStatus.SUCCESS; // the all-clear, or someone else rang it
		}
		if (!Bells.isBell(level, to) || Ringing.creeperNear(level, to)) {
			Ringing.failed(s, c);
			return TaskStatus.FAILURE;
		}
		if (!c.actions().canReach(to)) {
			if (c.actions().walkTo(to, 2.0)) {
				return TaskStatus.RUNNING;
			}
			if (c.actions().isStuck()) {
				Ringing.failed(s, c);
				return TaskStatus.FAILURE;
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		c.getLookControl().setLookAt(Vec3.atCenterOf(to));
		if (wait > 0) {
			wait--;
			return TaskStatus.RUNNING;
		}
		if (Bells.ring(c, level, to)) {
			c.swingArm();
			if (strokes == 0) {
				Alarm.say(c, Line.ALARM_BELL, s.cause.danger);
			}
		}
		strokes++;
		wait = STROKE_GAP;
		if (strokes >= STROKES) {
			Ringing.rung(s);
			return TaskStatus.SUCCESS;
		}
		return TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		bell = null;
		strokes = 0;
		wait = 0;
	}

	@Override
	public int failureCooldown() {
		return 20 * 30;
	}

	@Override
	public int maxTicks() {
		return 20 * 45;
	}
}
