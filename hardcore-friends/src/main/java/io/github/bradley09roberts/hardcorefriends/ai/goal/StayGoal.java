package io.github.bradley09roberts.hardcorefriends.ai.goal;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;

/** STAY mode: hold the ordered spot, walking back if pushed away. */
public class StayGoal extends Goal {
	private final CompanionEntity companion;

	public StayGoal(CompanionEntity companion) {
		this.companion = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return companion.mode() == CompanionMode.STAY && companion.stayPos() != null
			&& companion.blockPosition().distSqr(companion.stayPos()) > 4;
	}

	@Override
	public boolean canContinueToUse() {
		return companion.mode() == CompanionMode.STAY && companion.stayPos() != null
			&& !companion.getNavigation().isDone();
	}

	@Override
	public void start() {
		BlockPos p = companion.stayPos();
		if (p != null) {
			companion.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 1.0);
		}
	}
}
