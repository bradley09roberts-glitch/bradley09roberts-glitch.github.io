package io.github.bradley09roberts.hardcorefriends.defence;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * The fire watch: a friend puts out a fire in the village ({@link FireWatch}) by hand, as a player does, going from
 * flame to flame of the same blaze until it is out. Each flame is broken through the edit guard, under its landscaping
 * rules (fire is a replaceable block like tall grass, so only inside the camp and never beside water or lava); nothing
 * but the fire itself is touched, so the builder's repair job puts back whatever burnt (the fire watch never fights
 * it). The friend only reaches for a flame they can see from within arm's reach, keeps out of the fire (the way finding
 * treats fire as a wall), and gives up on one they cannot get to.
 *
 * <p>Any grown-up at work in the village who is fit (not badly hurt, not on fire, not falling back) and within
 * {@value #RANGE} blocks; at most two at once. Scores {@value #SCORE}: above all work and ordinary needs, day or night
 * (whoever is awake), below the alarm's jobs and a desperate need.
 */
final class PutOutFireTask implements CompanionTask {
	static final String ID = "defence.fire";
	private static final double SCORE = 100;
	private static final double RANGE = 48;
	/** The flames of one blaze: fire this close to the last one put out. */
	private static final double SAME_BLAZE = 8;
	private static final int MAX_FLAMES = 32;
	private static final int FIRE_UNITY_CAP = 10;
	static final String UNITY_FIRE = "fire";

	private @Nullable BlockPos fire;
	private int flames;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "putting out a fire";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!FireWatch.any() || !fit(c)) {
			return 0;
		}
		return FireWatch.nearestFor(c, RANGE) != null ? SCORE : 0;
	}

	private static boolean fit(CompanionEntity c) {
		return FriendsConfig.get().fireWatch && !c.isChild() && c.mode() == CompanionMode.WORK && !c.isOnFire()
			&& !c.isRetreating() && !c.badlyHurt() && !c.tooWeakToWork() && Area.atHome(c);
	}

	@Override
	public boolean start(CompanionEntity c) {
		fire = FireWatch.claim(c, RANGE);
		flames = 0;
		return fire != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		BlockPos at = fire;
		if (at == null || !(c.level() instanceof ServerLevel level)) {
			return TaskStatus.FAILURE;
		}
		if (c.isOnFire() || c.badlyHurt()) {
			return TaskStatus.FAILURE; // the reflexes get them clear
		}
		if (!level.isLoaded(at) || !FireWatch.isFire(level.getBlockState(at))) {
			FireWatch.out(at);
			return nextFlame(c, level, at);
		}
		if (c.actions().canReach(at) && canSee(c, level, at)) {
			c.actions().stopWalking();
			c.getLookControl().setLookAt(Vec3.atCenterOf(at));
			if (WorldEditGuard.breakBlock(c, at, WorldEditGuard.Reason.LANDSCAPE)) {
				c.swingArm();
				level.levelEvent(null, LevelEvent.SOUND_EXTINGUISH_FIRE, at, 0);
				FireWatch.out(at);
				flames++;
				return nextFlame(c, level, at);
			}
			if (!"pacing".equals(WorldEditGuard.canBreak(c, at, WorldEditGuard.Reason.LANDSCAPE).why())) {
				FireWatch.giveUp(at, level.getGameTime()); // the guard says no (beside water, say): leave it
				return nextFlame(c, level, at);
			}
			return TaskStatus.RUNNING;
		}
		if (c.actions().walkTo(at, 2.5)) {
			if (!canSee(c, level, at)) {
				FireWatch.giveUp(at, level.getGameTime()); // right by it and still cannot see it (behind a wall)
				return nextFlame(c, level, at);
			}
			return TaskStatus.RUNNING;
		}
		if (c.actions().isStuck()) {
			FireWatch.giveUp(at, level.getGameTime());
			return nextFlame(c, level, at);
		}
		return TaskStatus.RUNNING;
	}

	/** On to the next flame of the same blaze, or done. */
	private TaskStatus nextFlame(CompanionEntity c, ServerLevel level, BlockPos last) {
		fire = flames >= MAX_FLAMES ? null : FireWatch.next(c, last, SAME_BLAZE);
		if (fire != null) {
			return TaskStatus.RUNNING;
		}
		if (flames > 0) {
			fireOut(c, level);
		}
		return flames > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
	}

	/** The blaze is out: a word, a little Unity, and the camp's count of fires put out. */
	private static void fireOut(CompanionEntity c, ServerLevel level) {
		Speech.say(c, Line.FIRE_OUT);
		Unity.add(level, UNITY_FIRE, 1, FIRE_UNITY_CAP);
		Camp.data(level.getServer()).addStat("defence.fires_out", 1);
	}

	/** True if nothing solid stands between the friend's eyes and the flame (no reaching through a wall). */
	private static boolean canSee(CompanionEntity c, ServerLevel level, BlockPos at) {
		Vec3 eyes = c.getEyePosition();
		Vec3 target = Vec3.atCenterOf(at);
		BlockHitResult hit = level.clip(new ClipContext(eyes, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(at);
	}

	@Override
	public void stop(CompanionEntity c) {
		FireWatch.release(c);
		fire = null;
		flames = 0;
	}

	@Override
	public int failureCooldown() {
		return 20 * 10;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
