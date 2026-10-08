package io.github.bradley09roberts.hardcorefriends.ai.goal;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.combat.Tactics;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * When badly hurt, a friend breaks off whatever they were doing, moves away from danger towards camp or a
 * protector (out of bow range when something is shooting at them), and eats from their backpack once safe. Cautious
 * friends (Flint) retreat earlier; Aegis much later. The healthiest armed friend nearby takes over the fight they
 * leave ({@link Tactics#tagOut}).
 *
 * <p>Falling back to rest only helps a friend who can heal. One too hungry to heal with nothing in the backpack falls
 * back only from danger; otherwise their jobs take care of them: they fetch food from the chest, or, with none
 * anywhere, rest at camp and keep off risky work until there is some (see
 * {@link io.github.bradley09roberts.hardcorefriends.ai.task.needs.RestTask}).
 */
public class RetreatGoal extends Goal {
	private static final double SAFE_DISTANCE = 12;
	/** An archer in sight must be left further behind: skeletons shoot from about 15 blocks. */
	private static final double SHOOTER_SAFE_DISTANCE = 20;
	/** Danger this close starts (or keeps up) a retreat. */
	private static final double THREAT_RANGE = 16;
	private final CompanionEntity companion;
	private @Nullable Vec3 fleeTo;
	private int recalc;
	private int calmTicks;
	private boolean announced;

	public RetreatGoal(CompanionEntity companion) {
		this.companion = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.TARGET));
	}

	/** True when resting can do this friend any good: they can heal, or have something in the backpack to eat. */
	private boolean restHelps() {
		return companion.needs().canHeal() || companion.hasFood();
	}

	@Override
	public boolean canUse() {
		if (companion.isOnFire() && companion.getTarget() == null) {
			return true;
		}
		if (!companion.badlyHurt()) {
			return false;
		}
		// Danger always sends a badly hurt friend back. Calm, they only fall back to rest when resting helps.
		return restHelps() || Threats.nearest(companion, THREAT_RANGE) != null;
	}

	@Override
	public boolean canContinueToUse() {
		if (companion.isOnFire()) {
			return true;
		}
		// Keep going until reasonably healed, or, for a friend who cannot heal, until the danger has been gone a while.
		return companion.getHealth() < companion.getMaxHealth() * 0.7F && !(calmTicks > 200 && !restHelps());
	}

	@Override
	public void start() {
		LivingEntity attacker = companion.getTarget();
		if (attacker != null && attacker.isAlive()) {
			Tactics.tagOut(companion, attacker); // the healthiest armed friend nearby takes the fight over
		}
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
		LivingEntity threat = Threats.nearestArcher(companion, SHOOTER_SAFE_DISTANCE);
		double safeDistance = SHOOTER_SAFE_DISTANCE;
		if (threat == null) {
			threat = Threats.nearest(companion, THREAT_RANGE);
			safeDistance = SAFE_DISTANCE;
		}
		if (threat != null && threat.distanceTo(companion) < safeDistance) {
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
		// Drift home while recovering, unless an archer stands near camp and would shoot at them there.
		BlockPos home = companion.homePos();
		if (companion.getNavigation().isDone() && companion.blockPosition().distSqr(home) > 64
			&& !Threats.archerNear((ServerLevel) companion.level(), home, SHOOTER_SAFE_DISTANCE - 2)) {
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
		Vec3 land = LandRandomPos.getPosAway(companion, 16, 7, threat.position()); // out of the water if possible
		return land != null ? land : DefaultRandomPos.getPosAway(companion, 16, 7, threat.position());
	}
}
