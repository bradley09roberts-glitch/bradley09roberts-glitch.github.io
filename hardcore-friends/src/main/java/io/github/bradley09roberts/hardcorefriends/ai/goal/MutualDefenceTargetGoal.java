package io.github.bradley09roberts.hardcorefriends.ai.goal;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;

/**
 * Friends look out for each other: a healthy friend holding a tool joins in against a hostile mob that is going for
 * them, another friend or a player close by. Aegis has its own wider guard duty; friends told to stay put only
 * defend themselves.
 */
public class MutualDefenceTargetGoal extends Goal {
	private static final double RANGE = 8;
	private final CompanionEntity companion;
	private @Nullable LivingEntity chosen;
	private int cooldown;

	public MutualDefenceTargetGoal(CompanionEntity companion) {
		this.companion = companion;
		this.setFlags(EnumSet.of(Goal.Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		if (companion.isFighter() || companion.mode() == CompanionMode.STAY || companion.getTarget() != null
			|| --cooldown > 0) {
			return false;
		}
		cooldown = 10;
		chosen = pick();
		return chosen != null;
	}

	private @Nullable LivingEntity pick() {
		LivingEntity best = null;
		double bestDist = RANGE * RANGE;
		for (LivingEntity threat : Threats.around(companion, RANGE)) {
			if (!(threat instanceof Mob mob) || !companion.canStandAndFight(threat) || !companion.hasLineOfSight(threat)) {
				continue;
			}
			LivingEntity victim = mob.getTarget();
			boolean defending = victim == companion
				|| (victim instanceof CompanionEntity && victim.distanceToSqr(companion) <= RANGE * RANGE)
				|| (victim instanceof Player p && !p.isSpectator() && victim.distanceToSqr(companion) <= RANGE * RANGE);
			double d = threat.distanceToSqr(companion);
			if (defending && d < bestDist) {
				bestDist = d;
				best = threat;
			}
		}
		return best;
	}

	@Override
	public void start() {
		companion.setTarget(chosen);
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}
}
