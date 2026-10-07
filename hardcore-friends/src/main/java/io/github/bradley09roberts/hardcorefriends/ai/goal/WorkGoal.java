package io.github.bradley09roberts.hardcorefriends.ai.goal;

import java.util.EnumSet;

import net.minecraft.world.entity.ai.goal.Goal;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;

/** Runs the friend's job scheduler whenever no reflex (fleeing, fighting, following) needs the body. */
public class WorkGoal extends Goal {
	private final CompanionEntity companion;

	public WorkGoal(CompanionEntity companion) {
		this.companion = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return companion.mode() == CompanionMode.WORK && companion.getTarget() == null;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		companion.scheduler().tick(companion.level().getGameTime());
	}

	@Override
	public void stop() {
		companion.scheduler().interrupt();
	}
}
