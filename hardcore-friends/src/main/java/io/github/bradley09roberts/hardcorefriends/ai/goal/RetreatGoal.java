package io.github.bradley09roberts.hardcorefriends.ai.goal;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * When badly hurt, a friend breaks off whatever they were doing, moves away from danger towards camp or a
 * protector, and eats from their backpack once safe. Cautious friends (Flint) retreat earlier; Aegis much later.
 */
public class RetreatGoal extends Goal {
	private static final double SAFE_DISTANCE = 12;
	private final CompanionEntity companion;
	private @Nullable Vec3 fleeTo;
	private int recalc;
	private int calmTicks;
	private boolean announced;

	public RetreatGoal(CompanionEntity companion) {
		this.companion = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.TARGET));
	}

	private boolean lowHealth() {
		return companion.getHealth() <= companion.getMaxHealth() * companion.friendId().retreatFraction();
	}

	@Override
	public boolean canUse() {
		if (companion.isOnFire() && companion.getTarget() == null) {
			return true;
		}
		return lowHealth();
	}

	@Override
	public boolean canContinueToUse() {
		if (companion.isOnFire()) {
			return true;
		}
		// Keep going until reasonably healed, or until calm with nothing left to eat.
		return companion.getHealth() < companion.getMaxHealth() * 0.7F && !(calmTicks > 200 && !companion.hasFood());
	}

	@Override
	public void start() {
		companion.setTarget(null);
		companion.setRetreating(true);
		recalc = 0;
		calmTicks = 0;
		announced = false;
	}

	@Override
	public void stop() {
		companion.setRetreating(false);
		companion.getNavigation().stop();
		if (announced && companion.getHealth() >= companion.getMaxHealth() * 0.7F) {
			Speech.say(companion, Line.RECOVERED);
		}
		fleeTo = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity threat = Threats.nearest(companion, 16);
		if (threat != null && threat.distanceTo(companion) < SAFE_DISTANCE) {
			calmTicks = 0;
			if (!announced) {
				Speech.say(companion, Line.RETREAT);
				announced = true;
			}
			if (--recalc <= 0 || companion.getNavigation().isDone()) {
				recalc = 20;
				fleeTo = pickRefuge(threat);
				if (fleeTo != null) {
					companion.getNavigation().moveTo(fleeTo.x, fleeTo.y, fleeTo.z, 1.35);
				}
			}
			return;
		}
		calmTicks++;
		if (companion.isOnFire() && companion.getNavigation().isDone()) {
			Vec3 away = LandRandomPos.getPos(companion, 6, 3);
			if (away != null) {
				companion.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
			}
		}
		if (calmTicks % 40 == 20) {
			companion.eatFromBackpack();
		}
		// Drift home while recovering.
		BlockPos home = companion.homePos();
		if (companion.getNavigation().isDone() && companion.blockPosition().distSqr(home) > 64) {
			companion.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
		}
	}

	private @Nullable Vec3 pickRefuge(LivingEntity threat) {
		LivingEntity protector = companion.nearestProtector(24);
		if (protector != null && protector.distanceToSqr(threat) > companion.distanceToSqr(threat)) {
			return protector.position();
		}
		BlockPos home = companion.homePos();
		Vec3 homeVec = Vec3.atBottomCenterOf(home);
		if (homeVec.distanceToSqr(threat.position()) > companion.position().distanceToSqr(threat.position())
			&& homeVec.distanceToSqr(companion.position()) > 9) {
			Vec3 towards = LandRandomPos.getPosTowards(companion, 16, 7, homeVec);
			if (towards != null) {
				return towards;
			}
		}
		return DefaultRandomPos.getPosAway(companion, 16, 7, threat.position());
	}
}
