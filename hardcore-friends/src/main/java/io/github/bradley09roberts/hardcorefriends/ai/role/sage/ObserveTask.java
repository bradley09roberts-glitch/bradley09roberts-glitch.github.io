package io.github.bradley09roberts.hardcorefriends.ai.role.sage;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Sage keeps an eye on things: stands a few blocks from a player in camp (or from the friend covering the team's top
 * need) and watches the surroundings for a while. Sage's advice comes from what is seen, so staying near people
 * matters.
 */
public final class ObserveTask implements CompanionTask {
	private static final int WATCH_TICKS = 20 * 15;
	private static final double NEAR = 6;
	private static final double APPROACH = 4;

	private @Nullable LivingEntity subject;
	private int watched;
	private int lookTimer;

	@Override
	public String id() {
		return "sage.observe";
	}

	@Override
	public String describe() {
		return "observing the camp";
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (Camp.isNight(level) && !WorldEditGuard.inCamp(c, c.blockPosition())) {
			return 0;
		}
		return 40;
	}

	@Override
	public boolean start(CompanionEntity c) {
		subject = pickSubject(c);
		watched = 0;
		lookTimer = 0;
		return true;
	}

	/** A player in camp first, then the friend covering the top need, then the nearest friend; null = the camp itself. */
	private static @Nullable LivingEntity pickSubject(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		ServerPlayer nearestPlayer = null;
		double best = 48 * 48;
		for (ServerPlayer p : level.players()) {
			double d = p.distanceToSqr(c);
			if (!p.isSpectator() && p.isAlive() && d < best && WorldEditGuard.inCamp(c, p.blockPosition())) {
				best = d;
				nearestPlayer = p;
			}
		}
		if (nearestPlayer != null) {
			return nearestPlayer;
		}
		CampNeeds.Need focus = CampNeeds.focus();
		if (focus != null) {
			CompanionEntity owner = TeamPlan.ownerPresent(focus).orElse(null);
			if (owner != null && owner != c && owner.level() == level && WorldEditGuard.inCamp(c, owner.blockPosition())) {
				return owner;
			}
		}
		CompanionEntity nearestFriend = null;
		best = 32 * 32;
		for (CompanionEntity other : Companions.in(level)) {
			double d = other.distanceToSqr(c);
			if (other != c && d < best) {
				best = d;
				nearestFriend = other;
			}
		}
		return nearestFriend;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (subject != null && (!subject.isAlive() || subject.isRemoved() || subject.level() != c.level())) {
			subject = null;
		}
		boolean close;
		if (subject != null) {
			close = c.distanceTo(subject) <= NEAR;
			if (!close) {
				c.actions().walkToEntity(subject, APPROACH);
			}
		} else {
			BlockPos home = c.homePos();
			close = c.actions().walkTo(home, NEAR);
			if (c.actions().isStuck()) {
				return TaskStatus.FAILURE;
			}
		}
		if (!close) {
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		lookAround(c);
		return ++watched >= WATCH_TICKS ? TaskStatus.SUCCESS : TaskStatus.RUNNING;
	}

	private void lookAround(CompanionEntity c) {
		if (--lookTimer > 0) {
			return;
		}
		lookTimer = 30 + c.getRandom().nextInt(40);
		if (subject != null && c.getRandom().nextBoolean()) {
			c.getLookControl().setLookAt(subject);
			return;
		}
		double angle = c.getRandom().nextDouble() * Math.PI * 2;
		c.getLookControl().setLookAt(c.getX() + Math.cos(angle) * 8, c.getEyeY() + c.getRandom().nextDouble() * 2 - 1,
			c.getZ() + Math.sin(angle) * 8);
	}

	@Override
	public void stop(CompanionEntity c) {
		subject = null;
		watched = 0;
	}

	@Override
	public int successCooldown() {
		return 20 * 15;
	}

	@Override
	public int maxTicks() {
		return 20 * 60;
	}
}
