package io.github.bradley09roberts.hardcorefriends.ai.goal;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Keeps a friend away from creepers (everyone, Aegis included once one starts to hiss) and, for friends who are
 * not fighters, away from hostile mobs that come close or go for them. A non-fighter also gets out of the line of
 * fire of a skeleton or other ranged attacker that is shooting at them from beyond fighting range, instead of
 * standing still to be shot. Friends run towards a protector when one is nearby.
 */
public class AvoidDangerGoal extends Goal {
	/** How far away a ranged attacker aiming at a non-fighter is noticed (skeletons shoot from up to 15 blocks). */
	private static final double SHOOTER_RANGE = 16;
	/** A friend fleeing a shooter keeps going until this far away, beyond a skeleton's follow range. */
	private static final double SHOOTER_SAFE_DISTANCE = 20;
	private static final double SAFE_DISTANCE = 12;

	private final CompanionEntity companion;
	private final boolean avoidAllHostiles;
	private @Nullable LivingEntity danger;
	private double safeDistance = SAFE_DISTANCE;
	private int recalc;
	private int shooterScan;

	public AvoidDangerGoal(CompanionEntity companion, boolean avoidAllHostiles) {
		this.companion = companion;
		this.avoidAllHostiles = avoidAllHostiles;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		danger = findDanger();
		return danger != null;
	}

	private @Nullable LivingEntity findDanger() {
		Creeper creeper = Threats.nearestCreeper(companion, 10);
		if (creeper != null) {
			double d = creeper.distanceTo(companion);
			boolean hissing = creeper.getSwellDir() > 0 || creeper.isIgnited();
			boolean bravelyAttacking = companion.isFighter() && !hissing && companion.getHealth() > companion.getMaxHealth() * 0.6F;
			if (!bravelyAttacking && (d < 7 || (hissing && d < 10))) {
				safeDistance = SAFE_DISTANCE;
				return creeper;
			}
		}
		if (!avoidAllHostiles || companion.isFighter()) {
			return null;
		}
		LivingEntity threat = Threats.nearest(companion, 8);
		if (threat != null) {
			if (companion.canStandAndFight(threat)) {
				return null; // stand together and fight instead of being picked off alone
			}
			double d = threat.distanceTo(companion);
			if (d < 5 || Threats.isTargeting(threat, companion)) {
				safeDistance = SAFE_DISTANCE;
				return threat;
			}
		}
		// A skeleton strafing 8-15 blocks away is out of melee reach, so move out of its line of fire.
		// Scanned every 10 ticks to keep the wider search cheap.
		if (++shooterScan < 5) {
			return null;
		}
		shooterScan = 0;
		LivingEntity shooter = Threats.nearestShooter(companion, SHOOTER_RANGE);
		if (shooter != null && !companion.canStandAndFight(shooter)) {
			safeDistance = SHOOTER_SAFE_DISTANCE;
			return shooter;
		}
		return null;
	}

	@Override
	public void start() {
		recalc = 0;
		if (danger instanceof Creeper) {
			Speech.say(companion, Line.CREEPER);
		}
	}

	@Override
	public boolean canContinueToUse() {
		return danger != null && danger.isAlive() && danger.distanceTo(companion) < safeDistance;
	}

	@Override
	public void stop() {
		danger = null;
		companion.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (danger == null) {
			return;
		}
		if (--recalc <= 0 || companion.getNavigation().isDone()) {
			recalc = 15;
			Vec3 target = null;
			LivingEntity protector = companion.nearestProtector(20);
			if (protector != null && protector.distanceToSqr(danger) > companion.distanceToSqr(danger) + 9) {
				target = protector.position();
			}
			if (target == null) {
				target = DefaultRandomPos.getPosAway(companion, 12, 6, danger.position());
			}
			if (target != null) {
				companion.getNavigation().moveTo(target.x, target.y, target.z, 1.3);
			}
		}
	}
}
