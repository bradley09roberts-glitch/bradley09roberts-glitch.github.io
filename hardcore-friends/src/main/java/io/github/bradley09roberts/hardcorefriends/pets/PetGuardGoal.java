package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.combat.Archery;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * A dog of the camp stands up for its owner as a player's tamed wolf does, but only ever against monsters: one going
 * for its owner, one its owner is fighting hand to hand (not one the owner would shoot: the dog would be in the way of
 * every arrow), or one that bit the dog. Never a player, a friend, a villager, a golem or
 * anyone's animal ({@link #fair}), never a creeper, and never a monster with a creeper close by (a dog would only set
 * it off beside the people it is guarding). The dog drops the chase once the monster is far from its owner. Damage a
 * pet would deal to anything but a monster is refused outright as well ({@link PetEvents#allowDamage}).
 */
final class PetGuardGoal extends Goal {
	/** A monster further than this from the dog is not taken on. */
	private static final double REACH = 16;
	/** A chase ends when the monster gets this far from the dog's owner (or the dog). */
	private static final double LEASH = 24;
	/** A monster with a creeper this close to it is left alone. */
	private static final double CREEPER_GAP = 5;
	/** "Lately", for who hurt whom. */
	private static final int RECENT = 100;
	private static final int LOOK_INTERVAL = 10;

	private final Wolf dog;
	private @Nullable LivingEntity chosen;
	private int nextLook;

	PetGuardGoal(Wolf dog) {
		this.dog = dog;
		this.setFlags(EnumSet.of(Goal.Flag.TARGET));
	}

	/** True for something a pet may go for: a living monster, never a person, a friend or an animal. */
	static boolean fair(TamableAnimal pet, LivingEntity e) {
		return e.isAlive() && !e.isRemoved() && e instanceof Enemy && Threats.isThreat(e) && !(e instanceof Player)
			&& !(e instanceof CompanionEntity) && !(e instanceof Creeper) && e.level() == pet.level();
	}

	@Override
	public boolean canUse() {
		if (--nextLook > 0 || !dog.isTame() || dog.isLeashed()) {
			return false;
		}
		nextLook = LOOK_INTERVAL;
		chosen = pick();
		return chosen != null;
	}

	private @Nullable LivingEntity pick() {
		CompanionEntity owner = PetBrain.peek(dog) == null ? null : PetBrain.peek(dog).owner;
		LivingEntity bit = dog.getLastHurtByMob();
		if (bit != null && dog.tickCount - dog.getLastHurtByMobTimestamp() < RECENT && good(bit)) {
			return bit;
		}
		if (owner == null || !owner.isAlive() || owner.level() != dog.level()) {
			return null;
		}
		LivingEntity hurtOwner = owner.getLastHurtByMob();
		if (hurtOwner != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < RECENT && good(hurtOwner)) {
			return hurtOwner;
		}
		// The owner's own target only when they will fight it hand to hand: a dog biting at a monster stands right in
		// the line of fire, so an owner who would shoot it could never loose an arrow while the dog was there.
		LivingEntity ownersTarget = owner.getTarget();
		return ownersTarget != null && good(ownersTarget) && !Archery.prefersBow(owner, ownersTarget) ? ownersTarget : null;
	}

	private boolean good(LivingEntity e) {
		return fair(dog, e) && dog.distanceToSqr(e) <= REACH * REACH && !creeperNear(e);
	}

	private boolean creeperNear(LivingEntity e) {
		return Threats.nearestCreeper(e, CREEPER_GAP) != null;
	}

	@Override
	public void start() {
		dog.setTarget(chosen);
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity t = dog.getTarget();
		if (t == null || t != chosen || !fair(dog, t)) {
			return false;
		}
		CompanionEntity owner = PetBrain.peek(dog) == null ? null : PetBrain.peek(dog).owner;
		LivingEntity anchor = owner != null && owner.isAlive() && owner.level() == dog.level() ? owner : dog;
		if (t.distanceToSqr(anchor) > LEASH * LEASH || !(dog.level() instanceof ServerLevel)) {
			return false;
		}
		return dog.tickCount % 20 != 0 || !creeperNear(t);
	}

	@Override
	public void stop() {
		if (dog.getTarget() == chosen) {
			dog.setTarget(null);
		}
		chosen = null;
	}
}
