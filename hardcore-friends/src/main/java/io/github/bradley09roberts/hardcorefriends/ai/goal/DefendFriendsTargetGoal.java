package io.github.bradley09roberts.hardcorefriends.ai.goal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.combat.Archery;
import io.github.bradley09roberts.hardcorefriends.combat.Tactics;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;

/**
 * Picks hostile mobs that threaten players, friends or the camp. Aegis always guards; other armed friends only
 * defend their leader in FOLLOW mode. Creepers are left alone unless the defender is healthy and the creeper is
 * not yet hissing, or the defender can shoot it from beyond its blast. A hostile must be in sight, except inside the
 * camp, where the guard finds his way to it when a whole path leads there ({@link Reach}); one he could not get at is
 * left alone for a while. Among several, one a teammate is already fighting is favoured ({@link Tactics}).
 */
public class DefendFriendsTargetGoal extends Goal {
	private static final double GUARD_RADIUS = 16;
	/** At most this many new paths are worked out per look round. */
	private static final int PATH_CHECKS = 2;
	private final CompanionEntity companion;
	private @Nullable LivingEntity chosen;
	private int cooldown;

	public DefendFriendsTargetGoal(CompanionEntity companion) {
		this.companion = companion;
		this.setFlags(EnumSet.of(Goal.Flag.TARGET));
	}

	private boolean active() {
		if (companion.isRetreating()) {
			return false;
		}
		if (companion.isFighter()) {
			return companion.mode() != CompanionMode.STAY;
		}
		return companion.mode() == CompanionMode.FOLLOW && companion.isArmed();
	}

	@Override
	public boolean canUse() {
		if (!active() || --cooldown > 0) {
			return false;
		}
		cooldown = 10;
		chosen = pick();
		return chosen != null;
	}

	private @Nullable LivingEntity pick() {
		// First: whoever just attacked a nearby player or friend.
		for (ServerPlayer player : ((net.minecraft.server.level.ServerLevel) companion.level()).players()) {
			if (player.distanceToSqr(companion) < GUARD_RADIUS * GUARD_RADIUS * 4) {
				LivingEntity attacker = player.getLastHurtByMob();
				if (attacker != null && valid(attacker) && player.tickCount - player.getLastHurtByMobTimestamp() < 100
					&& (companion.hasLineOfSight(attacker) || Reach.check(companion, attacker) != Reach.Answer.NO)) {
					return attacker;
				}
			}
		}
		List<LivingEntity> candidates = new ArrayList<>();
		Map<LivingEntity, Double> scores = new HashMap<>();
		for (LivingEntity threat : Threats.around(companion, GUARD_RADIUS + 8)) {
			if (!valid(threat)) {
				continue;
			}
			double toProtected = distanceToProtected(threat.position());
			if (toProtected > GUARD_RADIUS) {
				continue;
			}
			double toMe = Tactics.rankDistance(companion, threat, threat.distanceTo(companion));
			candidates.add(threat);
			scores.put(threat, toProtected + toMe * 0.5);
		}
		candidates.sort(Comparator.comparingDouble(scores::get));
		// Out of sight (inside the camp), only one a whole path leads to: a partial path to the outside of a fence ring
		// or the foot of a ledge does not count. At most a couple of new paths per look round.
		int paths = 0;
		for (LivingEntity threat : candidates) {
			if (companion.hasLineOfSight(threat)) {
				return threat;
			}
			Reach.Answer answer = Reach.known(companion, threat);
			if (answer == null) {
				if (paths++ >= PATH_CHECKS) {
					return null;
				}
				answer = Reach.check(companion, threat);
			}
			if (answer == Reach.Answer.YES) {
				return threat;
			}
			if (answer == Reach.Answer.UNKNOWN) {
				return null;
			}
		}
		return null;
	}

	private boolean valid(LivingEntity e) {
		if (!Threats.isThreat(e) || companion.hasGivenUpOn(e) || !inSightOrInCamp(e)) {
			return false;
		}
		if (e instanceof Creeper creeper) {
			return Archery.standsWithBow(companion, creeper) || companion.isFighter() && creeper.getSwellDir() <= 0
				&& companion.getHealth() > companion.getMaxHealth() * 0.6F;
		}
		return true;
	}

	/**
	 * In sight, or, for a guard at work in the camp, anywhere on the camp's own ground: inside the camp nobody needs
	 * to see a hostile before going for it (a cave below the camp is not its ground). Out of sight it is only chosen
	 * when a whole path leads to it (see {@link #pick}).
	 */
	private boolean inSightOrInCamp(LivingEntity e) {
		if (companion.hasLineOfSight(e)) {
			return true;
		}
		return companion.mode() == CompanionMode.WORK && NightWatch.insideCamp(companion) && NightWatch.insideCamp(e)
			&& Math.abs(e.getY() - companion.getY()) <= 4;
	}

	private double distanceToProtected(Vec3 pos) {
		double best = Double.MAX_VALUE;
		for (ServerPlayer player : ((net.minecraft.server.level.ServerLevel) companion.level()).players()) {
			if (!player.isSpectator()) {
				best = Math.min(best, player.position().distanceTo(pos));
			}
		}
		for (CompanionEntity friend : Companions.in((net.minecraft.server.level.ServerLevel) companion.level())) {
			best = Math.min(best, friend.position().distanceTo(pos));
		}
		BlockPos camp = Camp.center((net.minecraft.server.level.ServerLevel) companion.level()).orElse(null);
		if (camp != null) {
			best = Math.min(best, Vec3.atCenterOf(camp).distanceTo(pos));
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
