package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * A pet of the camp walks where {@link PetBrain} says: after its owner, or to a spot (home at night, the camp centre by
 * day). A pet that makes no headway for a while (a shut door, a ledge) is brought to its owner, or its spot, like a
 * player's pet is: only inside the camp, onto loaded ground, and never across dimensions. Takes the place of vanilla's
 * following, which would teleport a pet to its owner wherever the owner was.
 */
final class PetMoveGoal extends Goal {
	/** Ticks without getting closer before a pet following its owner is brought to them. */
	private static final int FOLLOW_STUCK = 60;
	/** Ticks without getting closer before a pet going home is brought there. */
	private static final int SPOT_STUCK = 200;
	/** A pet this far behind its owner in the camp is brought along at once, as vanilla does at 12. */
	private static final double FAR_BEHIND = 20;

	private final TamableAnimal pet;
	private int repath;
	private int stuck;
	private double best = Double.MAX_VALUE;

	PetMoveGoal(TamableAnimal pet) {
		this.pet = pet;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		PetBrain.State s = PetBrain.peek(pet);
		return s != null && !pet.isOrderedToSit() && !pet.isLeashed() && distance(s) > want(s);
	}

	@Override
	public boolean canContinueToUse() {
		PetBrain.State s = PetBrain.peek(pet);
		return s != null && !pet.isOrderedToSit() && !pet.isLeashed() && distance(s) > arrived(s);
	}

	/** How far from where it should be the pet is, or 0 when there is nowhere to go. */
	private double distance(PetBrain.State s) {
		if (s.plan == PetBrain.Plan.FOLLOW) {
			CompanionEntity owner = s.owner;
			return owner == null || !owner.isAlive() || owner.level() != pet.level() ? 0 : pet.distanceTo(owner);
		}
		if (s.plan == PetBrain.Plan.GO_TO && s.spot != null) {
			BlockPos spot = s.spot;
			return Math.sqrt(pet.position().distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5));
		}
		return 0;
	}

	/** Following starts once the owner is this far off; going to a spot, once not there. */
	private static double want(PetBrain.State s) {
		return s.plan == PetBrain.Plan.FOLLOW ? s.start : s.arrive;
	}

	private static double arrived(PetBrain.State s) {
		return s.arrive;
	}

	@Override
	public void start() {
		repath = 0;
		stuck = 0;
		best = Double.MAX_VALUE;
	}

	@Override
	public void stop() {
		pet.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		PetBrain.State s = PetBrain.peek(pet);
		if (s == null || !(pet.level() instanceof ServerLevel level)) {
			return;
		}
		double dist = distance(s);
		if (dist < best - 0.5) {
			best = dist;
			stuck = 0;
		} else {
			stuck++;
		}
		if (s.plan == PetBrain.Plan.FOLLOW && s.owner != null) {
			CompanionEntity owner = s.owner;
			pet.getLookControl().setLookAt(owner, 10.0F, pet.getMaxHeadXRot());
			boolean fetch = dist > FAR_BEHIND || stuck > FOLLOW_STUCK && dist > s.start;
			if (fetch && PetBrain.inCamp(level, owner.blockPosition()) && owner.onGround()) {
				if (PetBrain.teleportNear(pet, owner.blockPosition())) {
					reset();
					return;
				}
			}
			if (--repath <= 0) {
				repath = adjustedTickDelay(10);
				pet.getNavigation().moveTo(owner, s.speed);
			}
			return;
		}
		BlockPos spot = s.spot;
		if (spot == null) {
			return;
		}
		if (stuck > SPOT_STUCK && PetBrain.inCamp(level, spot) && PetBrain.teleportNear(pet, spot)) {
			reset();
			return;
		}
		if (--repath <= 0) {
			repath = adjustedTickDelay(20);
			pet.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, s.speed);
		}
	}

	private void reset() {
		stuck = 0;
		best = Double.MAX_VALUE;
		repath = 0;
	}
}
