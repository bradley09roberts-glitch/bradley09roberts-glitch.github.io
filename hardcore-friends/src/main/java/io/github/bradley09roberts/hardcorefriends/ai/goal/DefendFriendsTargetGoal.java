package io.github.bradley09roberts.hardcorefriends.ai.goal;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;

/**
 * Picks hostile mobs that threaten players, friends or the camp. Aegis always guards; other armed friends only
 * defend their leader in FOLLOW mode. Creepers are left alone unless the defender is healthy and the creeper is
 * not yet hissing.
 */
public class DefendFriendsTargetGoal extends Goal {
	private static final double GUARD_RADIUS = 16;
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
				if (attacker != null && valid(attacker) && player.tickCount - player.getLastHurtByMobTimestamp() < 100) {
					return attacker;
				}
			}
		}
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity threat : Threats.around(companion, GUARD_RADIUS + 8)) {
			if (!valid(threat)) {
				continue;
			}
			double toProtected = distanceToProtected(threat.position());
			if (toProtected > GUARD_RADIUS) {
				continue;
			}
			double score = toProtected + threat.distanceTo(companion) * 0.5;
			if (score < bestScore) {
				bestScore = score;
				best = threat;
			}
		}
		return best;
	}

	private boolean valid(LivingEntity e) {
		if (!Threats.isThreat(e) || !companion.hasLineOfSight(e)) {
			return false;
		}
		if (e instanceof Creeper creeper) {
			return companion.isFighter() && creeper.getSwellDir() <= 0
				&& companion.getHealth() > companion.getMaxHealth() * 0.6F;
		}
		return true;
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
