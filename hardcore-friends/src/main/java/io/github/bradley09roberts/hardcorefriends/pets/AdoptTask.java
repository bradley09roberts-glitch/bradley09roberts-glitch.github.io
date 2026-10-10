package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.Wildlife;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.EntityApproach;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.civic.Families;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Adopting a pet. A friend with no pet (a child most of all), or a parent finding one for their child, takes a stray
 * cat's fish or a wild wolf's bones from the camp's stock, walks up to the animal and feeds it, as a player tames one:
 * each fish or bone has a one in three chance, and is eaten either way. Once tamed it is named and belongs to them (or
 * their child). A pet of the whole camp (one whose owner died with nobody to take it) is simply taken in, no food needed.
 *
 * <p>Only wild animals: never one that is named, tamed or owned, on a lead, ridden, saddled or armoured, near anything
 * a player built or inside a player's fences ({@link Wildlife}), an angry or young wolf, or one where a friend died
 * lately. A child only takes in a cat (or the camp's own pet) inside the camp. Nobody adopts unless pets are on, the
 * person has none, the camp keeps fewer than {@code maxPets}, and the camp has a home for the pet ({@link Pets#hasHomeFor}).
 * Children score this above their everyday play (only being asked to play comes first); a parent finding a pet for
 * their child, in the spare time between jobs; a grown-up for themselves only when they have little else to do. An
 * animal that cannot be reached is left alone for a few minutes, so nobody keeps walking at it.
 */
final class AdoptTask implements CompanionTask {
	static final String ID = "pets.adopt";
	private static final double CHILD_SCORE = 58;
	private static final double FOR_CHILD_SCORE = 30;
	private static final double GROWN_UP_SCORE = 12;
	private static final int PLAN_INTERVAL = 60;
	private static final int SCAN_INTERVAL = 200;
	private static final int FEED_GAP = 25;
	private static final double FEED_REACH = 2.0;
	private static final int FOOD_TAKEN = 5;
	private static final int IGNORE_TICKS = 20 * 300;

	/** The team's look at the strays round the camp, per dimension, refreshed every {@value #SCAN_INTERVAL} ticks. */
	private static final class Seen {
		long at = Long.MIN_VALUE / 2;
		List<TamableAnimal> animals = List.of();
	}

	private static final Map<String, Seen> SEEN = new HashMap<>();
	/** Which animal each adopter is after, so two friends never go for the same one. */
	private static final Map<UUID, UUID> CLAIMS = new ConcurrentHashMap<>();

	private enum Phase {
		CHEST,
		APPROACH,
		FEED
	}

	private record Plan(TamableAnimal animal, PetKind kind, CompanionEntity forWhom, boolean campPet) {
	}

	private @Nullable Plan planned;
	private long plannedAt = Long.MIN_VALUE / 2;
	private @Nullable Plan plan;
	private Phase phase = Phase.CHEST;
	private int wait;
	private int sinceCheck;
	private final EntityApproach approach = new EntityApproach();
	private final Map<UUID, Long> ignored = new HashMap<>();

	static void clear() {
		SEEN.clear();
		CLAIMS.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Plan p = plan;
		return p == null ? "making friends with a stray" : "making friends with a " + (p.kind() == PetKind.WOLF ? "wolf" : "cat");
	}

	// ----------------------------------------------------------------- score

	@Override
	public double score(CompanionEntity c) {
		if (!FriendsConfig.get().pets || !(c.level() instanceof ServerLevel level) || c.mode() != CompanionMode.WORK
			|| !c.isTeamMember() || c.isAsleep() || c.getTarget() != null || c.isRetreating() || !c.isHealthy()
			|| c.tooWeakToWork() || Camp.isNight(level) || Camp.isDusk(level)) {
			return 0;
		}
		Optional<BlockPos> centre = Camp.center(level);
		if (centre.isEmpty() || !WorldEditGuard.inCampHorizontally(c, c.blockPosition())) {
			return 0;
		}
		long now = level.getGameTime();
		if (now - plannedAt >= PLAN_INTERVAL || now < plannedAt || planned != null && !planned.animal().isAlive()) {
			plannedAt = now;
			ignored.values().removeIf(until -> until <= now);
			planned = plan(c, level);
		}
		Plan p = planned;
		if (p == null) {
			return 0;
		}
		return c.isChild() ? CHILD_SCORE : p.forWhom() != c ? FOR_CHILD_SCORE : GROWN_UP_SCORE;
	}

	/** Who this friend would adopt for (themselves, or one of their children), and the animal, or null. */
	private @Nullable Plan plan(CompanionEntity c, ServerLevel level) {
		MinecraftServer server = level.getServer();
		CompanionEntity forWhom = null;
		if (Pets.mayHavePet(server, c)) {
			forWhom = c;
		} else if (!c.isChild()) {
			for (UUID kid : Families.get().childrenOf(server, c.getUUID())) {
				CompanionEntity child = Pets.companion(server, kid);
				if (child != null && child.isChild() && child.level() == level && Pets.mayHavePet(server, child)) {
					forWhom = child;
					break;
				}
			}
		}
		if (forWhom == null) {
			return null;
		}
		return choose(c, level, forWhom);
	}

	/** The nearest animal this friend may adopt for {@code forWhom}, with the food for it carried or in the chest. */
	private @Nullable Plan choose(CompanionEntity c, ServerLevel level, CompanionEntity forWhom) {
		PetsData data = PetsData.get(level.getServer());
		boolean full = Pets.atCap(level.getServer());
		Plan best = null;
		double bestDist = Double.MAX_VALUE;
		for (TamableAnimal a : seen(c, level)) {
			if (ignored.containsKey(a.getUUID()) || claimedByOther(a, c)) {
				continue;
			}
			PetKind kind = PetKind.of(a);
			if (kind == null) {
				continue;
			}
			boolean campPet = Pets.isPet(a) && data.pet(a.getUUID()).map(p -> p.owner == null).orElse(false);
			if (campPet ? !campPetMayGo(c, a) : full || !mayTame(c, a, kind)) {
				continue;
			}
			if (!campPet && !hasFood(c, level, kind)) {
				continue;
			}
			double d = a.distanceToSqr(c);
			if (d < bestDist) {
				bestDist = d;
				best = new Plan(a, kind, forWhom, campPet);
			}
		}
		return best;
	}

	private static boolean claimedByOther(TamableAnimal a, CompanionEntity c) {
		for (Map.Entry<UUID, UUID> e : CLAIMS.entrySet()) {
			if (e.getValue().equals(a.getUUID()) && !e.getKey().equals(c.getUUID())) {
				return true;
			}
		}
		return false;
	}

	/** The cats and wolves round the camp and its gathering ring, from the team's last look. */
	private static List<TamableAnimal> seen(CompanionEntity c, ServerLevel level) {
		Seen seen = SEEN.computeIfAbsent(Camp.dimensionId(level), k -> new Seen());
		long now = level.getGameTime();
		if (now - seen.at >= SCAN_INTERVAL || now < seen.at) {
			seen.at = now;
			BlockPos centre = c.homePos();
			int r = WorldEditGuard.campRadius(c) + Math.min(FriendsConfig.get().resourceRadius, Wildlife.MAX_RING);
			AABB box = new AABB(centre).inflate(r, 24, r);
			seen.animals = new ArrayList<>(level.getEntitiesOfClass(TamableAnimal.class, box,
				a -> a.isAlive() && PetKind.of(a) != null && (!a.isTame() || Pets.isPet(a))));
		}
		return seen.animals;
	}

	/**
	 * True if this wild animal may be tamed (the cheap checks; the player-build look is made once, when the job
	 * starts, and again before every feeding).
	 */
	private static boolean mayTame(CompanionEntity c, TamableAnimal a, PetKind kind) {
		if (!a.isAlive() || a.isRemoved() || a.level() != c.level() || a.isTame() || a.getOwnerReference() != null
			|| Wildlife.isSomebodys(a) || Pets.isPet(a)) {
			return false;
		}
		if (kind == PetKind.WOLF && (a.isBaby() || a instanceof NeutralMob angry && angry.isAngry())) {
			return false;
		}
		BlockPos pos = a.blockPosition();
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		if (data.nearDanger(pos, level.getGameTime())) {
			return false;
		}
		BlockPos home = c.homePos();
		// A child only takes in a cat, and only inside the camp; a grown-up goes as far as the gathering ring.
		if (c.isChild() && (kind != PetKind.CAT || !WorldEditGuard.inCampHorizontally(c, pos) || Math.abs(pos.getY() - home.getY()) > 6)) {
			return false;
		}
		return WorldEditGuard.inResourceZone(c, pos) && Math.abs(pos.getY() - home.getY()) <= 24;
	}

	/** A pet of the whole camp is taken in where it is: inside the camp, loaded beside the friend. */
	private static boolean campPetMayGo(CompanionEntity c, TamableAnimal a) {
		return a.isAlive() && !a.isLeashed() && a.level() == c.level() && WorldEditGuard.inCampHorizontally(c, a.blockPosition());
	}

	/** True if the friend carries, or the chest holds, what tames this kind. */
	private static boolean hasFood(CompanionEntity c, ServerLevel level, PetKind kind) {
		return c.backpack().count(kind.tamingFood()) >= 1 || Workbench.stock(level, "tame." + kind.key(), kind.tamingFood()) >= 1;
	}

	// ------------------------------------------------------------------ run

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Plan p = planned;
		planned = null;
		plannedAt = Long.MIN_VALUE / 2;
		if (p == null || !p.animal().isAlive()) {
			return false;
		}
		// The block checks, once, before setting off: nothing a player built near it, not inside a player's fences.
		if (!p.campPet() && Wildlife.looksOwnedNow(level, p.animal())) {
			ignored.put(p.animal().getUUID(), level.getGameTime() + IGNORE_TICKS);
			return false;
		}
		plan = p;
		CLAIMS.put(c.getUUID(), p.animal().getUUID());
		approach.reset();
		wait = 0;
		sinceCheck = 0;
		phase = p.campPet() || c.backpack().count(p.kind().tamingFood()) >= 1 ? Phase.APPROACH : Phase.CHEST;
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Plan p = plan;
		if (p == null) {
			return TaskStatus.FAILURE;
		}
		TamableAnimal a = p.animal();
		if (++sinceCheck >= 20) {
			sinceCheck = 0;
			if (!stillOk(c, level, p)) {
				return TaskStatus.FAILURE;
			}
		}
		switch (phase) {
			case CHEST -> {
				ChestWalk.State walk = ChestWalk.tick(c);
				if (walk == ChestWalk.State.FAILED) {
					return TaskStatus.FAILURE;
				}
				if (walk == ChestWalk.State.ARRIVED) {
					Optional<Container> chest = ChestWalk.chest(c);
					Workbench.forgetStock(level);
					if (chest.isEmpty() || SupplyChest.withdraw(chest.get(), c.backpack(), p.kind().tamingFood(), FOOD_TAKEN) < 1) {
						return TaskStatus.FAILURE;
					}
					phase = Phase.APPROACH;
				}
				return TaskStatus.RUNNING;
			}
			case APPROACH -> {
				if (!approach.walk(c, a, FEED_REACH)) {
					if (approach.isStuck()) {
						ignored.put(a.getUUID(), level.getGameTime() + IGNORE_TICKS); // out of reach: leave it a while
						return TaskStatus.FAILURE;
					}
					return TaskStatus.RUNNING;
				}
				if (p.campPet()) {
					return adopt(c, level, p) ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
				}
				phase = Phase.FEED;
				wait = 0;
				return TaskStatus.RUNNING;
			}
			case FEED -> {
				c.getLookControl().setLookAt(a);
				if (c.distanceTo(a) > FEED_REACH + 1.0) {
					phase = Phase.APPROACH;
					return TaskStatus.RUNNING;
				}
				a.getNavigation().stop();
				if (--wait > 0) {
					return TaskStatus.RUNNING;
				}
				wait = FEED_GAP;
				// Every rule once more, worked out afresh, before the food is offered.
				if (!mayTame(c, a, p.kind()) || Wildlife.looksOwnedNow(level, a)) {
					ignored.put(a.getUUID(), level.getGameTime() + IGNORE_TICKS);
					return TaskStatus.FAILURE;
				}
				if (c.backpack().remove(p.kind().tamingFood(), 1) < 1) {
					return TaskStatus.FAILURE; // out of fish or bones: another day
				}
				c.swingArm();
				a.playSound(SoundEvents.GENERIC_EAT.value(), 0.8F, 1.0F);
				if (a.getRandom().nextInt(3) == 0) {
					return adopt(c, level, p) ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
				}
				level.broadcastEntityEvent(a, (byte) 6); // smoke: not yet
				return c.backpack().count(p.kind().tamingFood()) >= 1 ? TaskStatus.RUNNING : TaskStatus.FAILURE;
			}
		}
		return TaskStatus.FAILURE;
	}

	/** The adoption may go on: the person it is for may still have a pet, and the animal is still there to adopt. */
	private static boolean stillOk(CompanionEntity c, ServerLevel level, Plan p) {
		TamableAnimal a = p.animal();
		if (!a.isAlive() || a.isRemoved() || a.level() != level) {
			return false;
		}
		MinecraftServer server = level.getServer();
		CompanionEntity who = p.forWhom();
		if (!who.isAlive() || PetsData.get(server).petOf(who.getUUID()).isPresent() || !Pets.hasHomeFor(server, who.getUUID())
			|| !FriendsConfig.get().pets) {
			return false;
		}
		if (p.campPet()) {
			return PetsData.get(server).pet(a.getUUID()).map(r -> r.owner == null).orElse(false) && campPetMayGo(c, a);
		}
		return !Pets.atCap(server) && mayTame(c, a, p.kind());
	}

	/** Makes the animal this person's pet, names it, and tells everyone. */
	private static boolean adopt(CompanionEntity adopter, ServerLevel level, Plan p) {
		TamableAnimal a = p.animal();
		CompanionEntity owner = p.forWhom();
		MinecraftServer server = level.getServer();
		PetsData data = PetsData.get(server);
		if (!owner.isAlive() || data.petOf(owner.getUUID()).isPresent()) {
			return false;
		}
		PetsData.Pet record;
		if (p.campPet()) {
			Optional<PetsData.Pet> existing = data.pet(a.getUUID());
			if (existing.isEmpty() || existing.get().owner != null) {
				return false;
			}
			record = existing.get();
		} else {
			if (Pets.atCap(server)) {
				return false;
			}
			a.setTame(true, true);
			a.addTag(Pets.PET_TAG);
			a.setPersistenceRequired();
			record = data.newPet(a.getUUID(), p.kind(), PetNames.pick(p.kind(), level.getRandom(), data.petNames()));
			a.setCustomName(Component.literal(record.name));
			record.adoptedDay = Camp.day(level);
		}
		a.setOwnerReference(EntityReference.of(owner.getUUID()));
		a.setOrderedToSit(false);
		a.setTarget(null);
		a.getNavigation().stop();
		level.broadcastEntityEvent(a, (byte) 7); // hearts
		record.owner = owner.getUUID();
		record.ownerName = owner.displayName();
		record.dimension = Camp.dimensionId(level);
		record.lastPos = a.blockPosition();
		record.lastSeen = level.getGameTime();
		data.setDirty();
		PetBrain.install(a);

		Speech.say(owner, Line.PET_ADOPTED, record.name, p.kind().word());
		String who = Pets.fullName(server, owner);
		String text = adopter == owner
			? who + " has a new " + p.kind().word() + " and has named it " + record.name + "."
			: adopter.displayName() + " found a " + p.kind().word() + " for " + who + ", who has named it " + record.name + ".";
		if (p.campPet()) {
			text = record.name + " the " + p.kind().word() + " has a home with " + who + " now.";
		}
		Speech.announce(server, Component.literal(text).withStyle(ChatFormatting.GRAY));
		Unity.add(level, "pets", 3, 6);
		Camp.data(server).addStat("pets_adopted", 1);
		return true;
	}

	@Override
	public void stop(CompanionEntity c) {
		CLAIMS.remove(c.getUUID());
		plan = null;
		phase = Phase.CHEST;
		approach.reset();
	}

	@Override
	public int successCooldown() {
		return 20 * 120;
	}

	@Override
	public int failureCooldown() {
		return 20 * 60;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
