package io.github.bradley09roberts.hardcorefriends.people;

import java.util.EnumSet;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * A child who sees a monster within {@value #SEE} blocks runs to a grown-up: a parent nearby first, then a fighter or
 * a player ({@link CompanionEntity#nearestProtector}), otherwise home to the resting place. It sits below the danger
 * reflexes (falling back when hurt, dodging a monster right beside them), which keep running as for anyone, and above
 * every job. A child never fights back.
 */
final class ChildRefugeGoal extends Goal {
	/** A monster this close sends a child running. */
	private static final double SEE = 12;
	/** They keep going until nothing is this close... */
	private static final double CLEAR = 16;
	/** ...and stay at least this long. */
	private static final int MIN_TICKS = 60;
	private static final int MAX_TICKS = 20 * 20;
	private static final double RUN = 1.35;

	private final CompanionEntity child;
	private @Nullable Entity refuge;
	private @Nullable BlockPos home;
	private int ticks;

	ChildRefugeGoal(CompanionEntity child) {
		this.child = child;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!child.isChild() || !child.isTeamMember() || (child.tickCount + child.getId()) % 10 != 0) {
			return false;
		}
		return Threats.nearest(child, SEE) != null;
	}

	@Override
	public boolean canContinueToUse() {
		if (!child.isChild() || ticks > MAX_TICKS) {
			return false;
		}
		return ticks < MIN_TICKS || ticks % 10 != 0 || Threats.nearest(child, CLEAR) != null;
	}

	@Override
	public void start() {
		ticks = 0;
		pickRefuge();
		Speech.say(child, Line.CHILD_SCARED);
		if (child.scheduler().current() != null) {
			child.scheduler().interrupt(); // whatever game or lesson it was, it stops
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ticks++;
		if (ticks % 10 == 1) {
			pickRefuge();
			Entity to = refuge;
			if (to != null) {
				if (child.distanceToSqr(to) > 2.5 * 2.5) {
					child.getNavigation().moveTo(to, RUN);
				} else {
					child.getNavigation().stop();
				}
			} else if (home != null) {
				child.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, RUN);
			}
		}
		LivingEntity threat = Threats.nearest(child, CLEAR);
		if (threat != null) {
			child.getLookControl().setLookAt(threat);
		}
	}

	/** A parent within 32 blocks, else a fighter or player within 24, else home. */
	private void pickRefuge() {
		refuge = null;
		home = null;
		if (child.level() instanceof ServerLevel level) {
			double best = 32 * 32;
			for (UUID id : PeopleData.get(level.getServer()).person(child.getUUID()).map(p -> p.parents).orElse(java.util.List.of())) {
				CompanionEntity parent = PeopleEvents.loaded(level.getServer(), id);
				if (parent != null && parent.level() == level && !parent.isRetreating() && parent.distanceToSqr(child) < best) {
					best = parent.distanceToSqr(child);
					refuge = parent;
				}
			}
		}
		if (refuge == null) {
			refuge = child.nearestProtector(24);
		}
		if (refuge == null) {
			BlockPos rest = child.restPos();
			home = child.blockPosition().closerThan(rest, 3) ? null : rest;
		}
	}

	@Override
	public void stop() {
		child.getNavigation().stop();
		refuge = null;
		home = null;
		ticks = 0;
	}
}
