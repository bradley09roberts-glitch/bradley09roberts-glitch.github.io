package io.github.bradley09roberts.hardcorefriends.people;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;

/**
 * Parents and fighters protect children first: a grown-up who could stand and fight it ({@link
 * CompanionEntity#canStandAndFight}: healthy, armed, within their reach) goes for a monster that is after a child,
 * their own child if they are a parent, any child if they are a fighter (Aegis and the warriors among the newcomers).
 * A target goal, above fighting back for themselves; it looks at the camp's endangered children, worked out once every
 * 10 ticks for the whole world ({@link Children#inDanger}).
 */
final class ProtectChildGoal extends Goal {
	private static final double REACH = 24;

	private final CompanionEntity adult;
	private @Nullable Mob threat;

	ProtectChildGoal(CompanionEntity adult) {
		this.adult = adult;
		this.setFlags(EnumSet.of(Goal.Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		if (adult.isChild() || !adult.isTeamMember() || adult.mode() == CompanionMode.STAY
			|| (adult.tickCount + adult.getId()) % 10 != 0 || !(adult.level() instanceof ServerLevel level)) {
			return false;
		}
		threat = null;
		double best = REACH * REACH;
		for (Children.Danger danger : Children.inDanger(level)) {
			Mob mob = danger.threat();
			if (!mob.isAlive() || !danger.child().isAlive()) {
				continue;
			}
			double d = adult.distanceToSqr(mob);
			if (d >= best || adult.hasGivenUpOn(mob) || !adult.canStandAndFight(mob)) {
				continue;
			}
			if (adult.isFighter() || Children.isParent(level.getServer(), adult, danger.child())) {
				best = d;
				threat = mob;
			}
		}
		return threat != null && adult.getTarget() != threat;
	}

	@Override
	public void start() {
		adult.setTarget(threat);
	}

	@Override
	public boolean canContinueToUse() {
		Mob mob = threat;
		return mob != null && mob.isAlive() && adult.getTarget() == mob && adult.canStandAndFight(mob);
	}

	@Override
	public void stop() {
		threat = null;
	}
}
