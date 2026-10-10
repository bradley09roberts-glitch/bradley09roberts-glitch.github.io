package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.EntityApproach;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * An owner feeds their pet from the camp's stock: whenever it is hurt (the food heals it, as when a player feeds their
 * pet), and otherwise as a treat every other day. A cat gets raw fish; a dog rotten flesh first, else raw meat, never
 * the cooked food the friends eat themselves. One piece at a time, fetched from the chest if not carried. Children see
 * to their own pets too: it changes no block and keeps them in the camp.
 */
final class FeedPetTask implements CompanionTask {
	static final String ID = "pets.feed";
	private static final double HURT_SCORE = 34;
	private static final double TREAT_SCORE = 14;
	/** Days between treats for a pet that is not hurt. */
	private static final int TREAT_DAYS = 2;
	private static final double RANGE = 32;
	private static final double FEED_REACH = 2.0;

	private enum Phase {
		CHEST,
		APPROACH
	}

	private Phase phase = Phase.CHEST;
	private @Nullable TamableAnimal pet;
	private final EntityApproach approach = new EntityApproach();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "feeding their pet";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!FriendsConfig.get().pets || !(c.level() instanceof ServerLevel level) || c.mode() != CompanionMode.WORK
			|| c.isAsleep() || c.getTarget() != null || c.isRetreating() || Camp.isNight(level)
			|| !WorldEditGuard.inCampHorizontally(c, c.blockPosition()) || Camp.center(level).isEmpty()) {
			return 0;
		}
		Optional<PetsData.Pet> record = PetsData.get(level.getServer()).petOf(c.getUUID());
		if (record.isEmpty()) {
			return 0;
		}
		TamableAnimal a = level.getEntity(record.get().id) instanceof TamableAnimal t && t.isAlive() ? t : null;
		if (a == null || a.distanceToSqr(c) > RANGE * RANGE || a.isLeashed()) {
			return 0;
		}
		boolean hurt = a.getHealth() < a.getMaxHealth() - 1.0F;
		boolean treat = Camp.day(level) - record.get().lastFedDay >= TREAT_DAYS;
		if (!hurt && !treat) {
			return 0;
		}
		PetKind kind = record.get().kind;
		if (c.backpack().count(kind.treat()) < 1 && Workbench.stock(level, "treat." + kind.key(), kind.treat()) < 1) {
			return 0;
		}
		return hurt ? HURT_SCORE : TREAT_SCORE;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Optional<PetsData.Pet> record = PetsData.get(level.getServer()).petOf(c.getUUID());
		pet = record.isPresent() && level.getEntity(record.get().id) instanceof TamableAnimal t && t.isAlive() ? t : null;
		if (pet == null) {
			return false;
		}
		approach.reset();
		phase = c.backpack().count(record.get().kind.treat()) >= 1 ? Phase.APPROACH : Phase.CHEST;
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		TamableAnimal a = pet;
		Optional<PetsData.Pet> record = PetsData.get(level.getServer()).petOf(c.getUUID());
		if (a == null || !a.isAlive() || record.isEmpty() || !record.get().id.equals(a.getUUID())) {
			return TaskStatus.FAILURE;
		}
		PetKind kind = record.get().kind;
		if (phase == Phase.CHEST) {
			ChestWalk.State walk = ChestWalk.tick(c);
			if (walk == ChestWalk.State.FAILED) {
				return TaskStatus.FAILURE;
			}
			if (walk == ChestWalk.State.ARRIVED) {
				Optional<Container> chest = ChestWalk.chest(c);
				Workbench.forgetStock(level);
				// Rotten flesh first for a dog: nobody else wants it.
				boolean got = chest.isPresent() && (kind == PetKind.WOLF
					&& SupplyChest.withdraw(chest.get(), c.backpack(), s -> s.is(net.minecraft.world.item.Items.ROTTEN_FLESH), 1) > 0
					|| SupplyChest.withdraw(chest.get(), c.backpack(), kind.treat(), 1) > 0);
				if (!got) {
					return TaskStatus.FAILURE;
				}
				phase = Phase.APPROACH;
			}
			return TaskStatus.RUNNING;
		}
		if (!approach.walk(c, a, FEED_REACH)) {
			return approach.isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		c.getLookControl().setLookAt(a);
		ItemStack food = c.backpack().take(kind.treat(), 1);
		if (food.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		FoodProperties props = food.get(DataComponents.FOOD);
		float heal = props != null ? props.nutrition() : 2.0F;
		a.heal(kind == PetKind.WOLF ? heal * 2.0F : heal);
		a.playSound(SoundEvents.GENERIC_EAT.value(), 0.8F, 1.0F);
		level.sendParticles(ParticleTypes.HEART, a.getX(), a.getY() + a.getBbHeight() + 0.3, a.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
		c.swingArm();
		record.get().lastFedDay = Camp.day(level);
		PetsData.get(level.getServer()).setDirty();
		Speech.say(c, Line.PET_PLAY, record.get().name, kind.word());
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		pet = null;
		phase = Phase.CHEST;
		approach.reset();
	}

	@Override
	public int successCooldown() {
		return 20 * 60;
	}

	@Override
	public int failureCooldown() {
		return 20 * 90;
	}

	@Override
	public int maxTicks() {
		return 20 * 60;
	}
}
