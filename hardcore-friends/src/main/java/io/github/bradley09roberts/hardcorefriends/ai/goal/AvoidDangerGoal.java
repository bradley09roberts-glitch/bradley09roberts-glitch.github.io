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
 * not fighters, away from hostile mobs that come close or go for them. Friends run towards a protector when
 * one is nearby.
 */
public class AvoidDangerGoal extends Goal {
	private final CompanionEntity companion;
	private final boolean avoidAllHostiles;
	private @Nullable LivingEntity danger;
	private int recalc;

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
				return creeper;
			}
		}
		if (!avoidAllHostiles || companion.isFighter()) {
			return null;
		}
		LivingEntity threat = Threats.nearest(companion, 8);
		if (threat == null) {
			return null;
		}
		if (companion.canStandAndFight(threat)) {
			return null; // stand together and fight instead of being picked off alone
		}
		double d = threat.distanceTo(companion);
		return d < 5 || Threats.isTargeting(threat, companion) ? threat : null;
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
		return danger != null && danger.isAlive() && danger.distanceTo(companion) < 12;
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
