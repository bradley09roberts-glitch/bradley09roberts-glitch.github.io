package io.github.bradley09roberts.hardcorefriends.ai.goal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.combat.Archery;
import io.github.bradley09roberts.hardcorefriends.combat.Tactics;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;

/**
 * Friends look out for each other. A healthy friend holding a tool joins in against a hostile mob that is going for
 * them, another friend or a player within {@value #RANGE} blocks, if they can see it. Inside the camp they stand
 * together more widely (the rally): an armed friend at work in the camp joins in against a hostile going for anyone
 * within {@value #RALLY_RANGE} blocks, and goes for any hostile the night watch raised the alarm about within
 * {@value NightWatch#ALARM_RANGE} blocks (not one at the far end of a village); the friend on watch takes on any hostile
 * that comes into the camp. A friend with a bow and arrows covers anyone within bow range
 * ({@value Archery#BOW_RANGE} blocks) against what they would shoot.
 *
 * <p>In the camp nobody needs to see the hostile first: they find their way to it. Out of sight, or further than
 * {@value #RANGE} blocks, a hostile is only chosen when a whole path leads to it ({@link Reach}; a partial path to
 * the foot of a ledge or the outside of a fence ring does not count), so a mob nobody can get at in the camp does not
 * keep the friends (and, through them, the sleepers) up all night. Among several threats, one a teammate is already
 * fighting is favoured ({@link Tactics#rankDistance}). Aegis has his own wider guard duty; friends told to stay put
 * only defend themselves.
 */
public class MutualDefenceTargetGoal extends Goal {
	/** Friends join a fight this close, anywhere. */
	private static final double RANGE = 8;
	/** Armed friends inside the camp join a fight this close. */
	public static final double RALLY_RANGE = 16;
	/** At most this many paths are worked out per look round (each answer is remembered a while). */
	private static final int PATH_CHECKS = 2;
	private final CompanionEntity companion;
	private @Nullable LivingEntity chosen;
	private int cooldown;

	/** One threat worth going for, with how it ranks and whether a path must lead to it. */
	private record Candidate(LivingEntity threat, double rank, boolean needsPath) {
	}

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
		boolean archer = Archery.canShoot(companion);
		double scan = onWatch ? CompanionEntity.CAMP_REACH : rallies ? RALLY_RANGE : RANGE;
		if (archer) {
			scan = Math.max(scan, Archery.BOW_RANGE);
		}
		List<Candidate> candidates = new ArrayList<>();
		for (LivingEntity threat : Threats.around(companion, scan)) {
			if (!(threat instanceof Mob mob) || !worthGoingFor(threat, inCamp)) {
				continue;
			}
			boolean campFight = inCamp && NightWatch.insideCamp(threat);
			double range = campFight && rallies ? RALLY_RANGE : RANGE;
			if (archer && Archery.wouldShoot(companion, threat)) {
				range = Math.max(range, Archery.BOW_RANGE);
			}
			double d = threat.distanceToSqr(companion);
			LivingEntity victim = mob.getTarget();
			boolean defending = d <= range * range && (victim == companion
				|| (victim instanceof CompanionEntity && victim.distanceToSqr(companion) <= range * range)
				|| (victim instanceof Player p && !p.isSpectator() && victim.distanceToSqr(companion) <= range * range));
			boolean guarding = onWatch && campFight;
			if (defending || guarding) {
				candidates.add(candidate(threat, d));
			}
		}
		// The watch raised the alarm: armed friends in the camp go for what it was raised about, near them.
		if (candidates.isEmpty() && rallies) {
			for (LivingEntity threat : NightWatch.alarmed((ServerLevel) companion.level())) {
				if (threat.distanceToSqr(companion) <= NightWatch.ALARM_RANGE * NightWatch.ALARM_RANGE
					&& worthGoingFor(threat, true)) {
					candidates.add(candidate(threat, threat.distanceToSqr(companion)));
				}
			}
		}
		candidates.sort(Comparator.comparingDouble(Candidate::rank));
		int paths = 0;
		for (Candidate c : candidates) {
			if (!c.needsPath()) {
				return c.threat();
			}
			Reach.Answer answer = Reach.known(companion, c.threat());
			if (answer == null) {
				if (paths++ >= PATH_CHECKS) {
					return null; // enough paths for one look round: look again shortly
				}
				answer = Reach.check(companion, c.threat());
			}
			if (answer == Reach.Answer.YES) {
				return c.threat();
			}
			if (answer == Reach.Answer.UNKNOWN) {
				return null; // mid-jump: no path can be worked out; look again shortly
			}
		}
		return null;
	}

	/**
	 * A candidate threat. One further than {@value #RANGE} blocks or out of sight needs a whole path to it, unless this
	 * friend would shoot it and can see it.
	 */
	private Candidate candidate(LivingEntity threat, double distSqr) {
		double dist = Math.sqrt(distSqr);
		boolean sight = companion.hasLineOfSight(threat);
		boolean shoots = sight && Archery.wouldShoot(companion, threat);
		boolean needsPath = !shoots && (!sight || dist > RANGE);
		return new Candidate(threat, Tactics.rankDistance(companion, threat, dist), needsPath);
	}

	/**
	 * A hostile this friend is fit to fight and has not given up on. Out of the camp it must be in sight; in the camp it
	 * need not be, as long as it is on the same level of ground (not in a cave below).
	 */
	private boolean worthGoingFor(LivingEntity threat, boolean inCamp) {
		if (!companion.canStandAndFight(threat) || companion.hasGivenUpOn(threat)) {
			return false;
		}
		if (companion.hasLineOfSight(threat)) {
			return true;
		}
		return inCamp && NightWatch.insideCamp(threat) && Math.abs(threat.getY() - companion.getY()) <= 4;
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
