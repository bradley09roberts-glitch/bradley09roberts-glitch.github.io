package io.github.bradley09roberts.hardcorefriends.ai.goal;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;

/**
 * Friends look out for each other. A healthy friend holding a tool joins in against a hostile mob that is going for
 * them, another friend or a player within {@value #RANGE} blocks, if they can see it. Inside the camp they stand
 * together more widely (the rally): an armed friend at work in the camp joins in against a hostile going for anyone
 * within {@value #RALLY_RANGE} blocks, and goes for any hostile the night watch raised the alarm about; the friend on
 * watch takes on any hostile that comes into the camp. In the camp nobody needs to see the hostile first: they find
 * their way to it, and leave alone one they cannot reach. Aegis has his own wider guard duty; friends told to stay
 * put only defend themselves.
 */
public class MutualDefenceTargetGoal extends Goal {
	/** Friends join a fight this close, anywhere. */
	private static final double RANGE = 8;
	/** Armed friends inside the camp join a fight this close. */
	public static final double RALLY_RANGE = 16;
	/** A hostile out of reach of a path is not tried again for this long. */
	private static final int UNREACHABLE_FOR = 200;
	private final CompanionEntity companion;
	private final Map<UUID, Integer> unreachable = new HashMap<>();
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
		boolean inCamp = companion.mode() == CompanionMode.WORK && NightWatch.insideCamp(companion);
		boolean onWatch = inCamp && NightWatch.isOnWatch(companion);
		boolean rallies = inCamp && companion.isArmed();
		double scan = onWatch ? CompanionEntity.CAMP_REACH : rallies ? RALLY_RANGE : RANGE;
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity threat : Threats.around(companion, scan)) {
			if (!(threat instanceof Mob mob) || !worthGoingFor(threat, inCamp)) {
				continue;
			}
			boolean campFight = inCamp && NightWatch.insideCamp(threat);
			double range = campFight && rallies ? RALLY_RANGE : RANGE;
			double d = threat.distanceToSqr(companion);
			LivingEntity victim = mob.getTarget();
			boolean defending = d <= range * range && (victim == companion
				|| (victim instanceof CompanionEntity && victim.distanceToSqr(companion) <= range * range)
				|| (victim instanceof Player p && !p.isSpectator() && victim.distanceToSqr(companion) <= range * range));
			boolean guarding = onWatch && campFight;
			if ((defending || guarding) && d < bestDist) {
				bestDist = d;
				best = threat;
			}
		}
		// The watch raised the alarm: armed friends in the camp go for what it was raised about.
		if (best == null && rallies) {
			for (LivingEntity threat : NightWatch.alarmed((ServerLevel) companion.level())) {
				double d = threat.distanceToSqr(companion);
				if (d < bestDist && worthGoingFor(threat, true)) {
					bestDist = d;
					best = threat;
				}
			}
		}
		if (best != null && bestDist > RANGE * RANGE && !reachable(best)) {
			return null;
		}
		return best;
	}

	/**
	 * A hostile this friend is fit to fight and has not given up on. Out of the camp it must be in sight; in the camp it
	 * need not be, as long as it is on the same level of ground (not in a cave below).
	 */
	private boolean worthGoingFor(LivingEntity threat, boolean inCamp) {
		if (!companion.canStandAndFight(threat) || companion.hasGivenUpOn(threat)) {
			return false;
		}
		Integer until = unreachable.get(threat.getUUID());
		if (until != null && companion.tickCount < until) {
			return false;
		}
		if (companion.hasLineOfSight(threat)) {
			return true;
		}
		return inCamp && NightWatch.insideCamp(threat) && Math.abs(threat.getY() - companion.getY()) <= 4;
	}

	/** Whether a path leads to the hostile; one that cannot be reached is left alone for a while. */
	private boolean reachable(LivingEntity threat) {
		if (!companion.onGround() && !companion.isInLiquid() && !companion.isPassenger()) {
			// Mid-jump (or just spawned, not yet landed) no path can be worked out at all: look again shortly, and do not
			// count the hostile as out of reach.
			return false;
		}
		if (companion.getNavigation().createPath(threat, 1) != null) {
			return true;
		}
		unreachable.values().removeIf(until -> until <= companion.tickCount);
		unreachable.put(threat.getUUID(), companion.tickCount + UNREACHABLE_FOR);
		return false;
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
