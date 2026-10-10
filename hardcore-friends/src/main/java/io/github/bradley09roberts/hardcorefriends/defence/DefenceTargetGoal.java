package io.github.bradley09roberts.hardcorefriends.defence;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.DoorBlock;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Reach;
import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.combat.Archery;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;

/**
 * Who the village's defenders go for first. For a guard on duty, or a fighter at their post while the alarm rings, it
 * picks among the hostiles within {@value #RANGE} blocks, in this order:
 * <ol>
 * <li>one at a door (a zombie or vindicator trying to break it in, on Hard, or just beating on it);</li>
 * <li>one going for someone in the village: a villager, an iron golem, a player, a friend (a skeleton shooting in from
 * outside included);</li>
 * <li>a spider climbing a wall;</li>
 * <li>any other raider of a raid.</li>
 * </ol>
 * Within a rank the nearest comes first. Only what this friend would stand up to anyway ({@code canStandAndFight}: so
 * never a creeper close up, and a child never), that they have not given up on, and that they can see or walk to (at
 * most one new path a look round). A guard up at the watchtower lookout only takes what they would shoot from there.
 * Villagers, golems, players and friends are never targets: the friends' attacks on them are cancelled outright
 * ({@code combat.FriendlyFire}) and they are never threats. Everything else is left to the usual target goals.
 */
final class DefenceTargetGoal extends Goal {
	private static final double RANGE = 24;
	private static final int LOOK_EVERY = 10;
	/** Ranks: lower goes first. */
	private static final int AT_DOOR = 0;
	private static final int ATTACKING = 1;
	private static final int CLIMBING = 2;
	private static final int RAIDER = 3;
	private static final int NONE = -1;

	private final CompanionEntity defender;
	private @Nullable LivingEntity chosen;

	DefenceTargetGoal(CompanionEntity defender) {
		this.defender = defender;
		this.setFlags(EnumSet.of(Goal.Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		if ((defender.tickCount + defender.getId()) % LOOK_EVERY != 0 || defender.isChild() || defender.isAsleep()
			|| defender.mode() != CompanionMode.WORK || !(defender.level() instanceof ServerLevel level)) {
			return false;
		}
		boolean guard = GuardRota.isOnDuty(defender);
		if (!guard && !Duty.atAlarm(defender)) {
			return false;
		}
		LivingEntity current = defender.getTarget();
		if (current != null && current.isAlive() && rank(level, current) >= 0) {
			return false; // already on one of these
		}
		chosen = pick(level, GuardRota.holdsPost(defender));
		return chosen != null && chosen != current;
	}

	private @Nullable LivingEntity pick(ServerLevel level, boolean onLookout) {
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		int paths = 0;
		for (LivingEntity t : Threats.around(defender, RANGE)) {
			int rank = rank(level, t);
			if (rank < 0 || !Area.inside(t, Area.EDGE)) {
				continue;
			}
			double d = Math.sqrt(t.distanceToSqr(defender));
			double score = rank * 100 + d;
			if (score >= bestScore || !defender.canStandAndFight(t) || defender.hasGivenUpOn(t)) {
				continue;
			}
			boolean sight = defender.hasLineOfSight(t);
			if (onLookout && !(sight && Archery.wouldShoot(defender, t) && d <= Archery.BOW_RANGE)) {
				continue; // up on the lookout: only what can be shot from there
			}
			if (!sight) {
				Reach.Answer known = Reach.known(defender, t);
				if (known == null) {
					if (paths++ >= 1) {
						continue;
					}
					known = Reach.check(defender, t);
				}
				if (known == Reach.Answer.NO) {
					continue;
				}
			}
			bestScore = score;
			best = t;
		}
		return best;
	}

	/** This hostile's rank (see the class description), or {@value #NONE} if it is none of these. */
	private static int rank(ServerLevel level, LivingEntity t) {
		if (!Threats.isThreat(t)) {
			return NONE;
		}
		if (atDoor(level, t)) {
			return AT_DOOR;
		}
		if (t instanceof Mob mob) {
			LivingEntity victim = mob.getTarget();
			if (victim != null && victim.isAlive() && (victim instanceof AbstractVillager || victim instanceof AbstractGolem
				|| victim instanceof Player p && !p.isSpectator() || victim instanceof CompanionEntity) && Area.inside(victim, 0)) {
				return ATTACKING;
			}
		}
		if (t instanceof Spider spider && spider.isClimbing()) {
			return CLIMBING;
		}
		if (t instanceof Raider raider && raider.hasActiveRaid()) {
			return RAIDER;
		}
		return NONE;
	}

	/** True if a wooden door is right beside this mob (at its feet or head): it is at the door, trying to get in. */
	private static boolean atDoor(ServerLevel level, LivingEntity t) {
		BlockPos feet = t.blockPosition();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 0; dy <= 1; dy++) {
					BlockPos p = feet.offset(dx, dy, dz);
					if (level.isLoaded(p) && DoorBlock.isWoodenDoor(level.getBlockState(p))
						&& t.getBoundingBox().inflate(0.6).intersects(new net.minecraft.world.phys.AABB(p))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public void start() {
		defender.setTarget(chosen);
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void stop() {
		chosen = null;
	}
}
