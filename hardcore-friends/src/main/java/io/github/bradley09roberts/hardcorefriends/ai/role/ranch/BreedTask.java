package io.github.bradley09roberts.hardcorefriends.ai.role.ranch;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * The farmer breeds the pen's animals: when two grown animals of a kind are ready (not young, not resting after
 * breeding) and the pen has room for a young one ({@link Livestock#MAX_PER_KIND} of a kind, {@link Livestock#MAX_TOTAL}
 * in all), she takes two of their food from her backpack or the supply chest, goes into the paddock (shutting the
 * gate behind her), feeds each of them one (the food is used up) so they fall in love, and leaves. Vanilla breeding
 * does the rest: the pair finds each other and a young one is born.
 */
public final class BreedTask implements CompanionTask {
	public static final String ID = "fern.breed";
	private static final double BASE = 44;
	private static final int FEED_REACH = 2;

	private enum Phase {
		FETCH,
		ENTER,
		FEED,
		LEAVE
	}

	private Livestock.@Nullable Kind planned;
	private Livestock.@Nullable Kind kind;
	private @Nullable Phase phase;
	private @Nullable Animal current;
	private final Set<UUID> fed = new HashSet<>();
	private final Pen.GateWalk walk = new Pen.GateWalk();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Livestock.Kind k = kind;
		return k == null ? "breeding the animals" : "breeding the " + k.plural();
	}

	/** Grown, not resting after breeding, not in love already. */
	static boolean ready(Animal a) {
		return a.isAlive() && !a.isBaby() && a.getAge() == 0 && a.canFallInLove() && !a.isInLove();
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Pen pen = Pen.of(level).orElse(null);
		planned = null;
		if (pen == null || Pen.otherAtWork(c) || pen.playerNear(level)) {
			return 0; // with a player by the pen the gate could not be shut behind her
		}
		List<Animal> animals = pen.animals(level);
		if (animals.size() + 1 > Livestock.MAX_TOTAL) {
			return 0;
		}
		int fewest = Integer.MAX_VALUE;
		for (Livestock.Kind k : Livestock.Kind.values()) {
			int all = Livestock.count(animals, k, false);
			if (!k.penned() || all + 1 > Livestock.MAX_PER_KIND || all >= fewest) {
				continue;
			}
			int ready = 0;
			for (Animal a : animals) {
				if (Livestock.kind(a) == k && ready(a)) {
					ready++;
				}
			}
			if (ready >= 2 && Stores.available(c, k.food()) >= 2) {
				planned = k;
				fewest = all;
			}
		}
		return planned == null ? 0 : Math.min(69, BASE * CampNeeds.weight(CampNeeds.Need.FOOD));
	}

	@Override
	public boolean start(CompanionEntity c) {
		kind = planned;
		if (kind == null) {
			return false;
		}
		fed.clear();
		current = null;
		walk.reset();
		phase = Stores.carried(c, kind.food()) >= 2 ? Phase.ENTER : Phase.FETCH;
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Optional<Pen> pen = Pen.of(level);
		Livestock.Kind k = kind;
		if (pen.isEmpty() || k == null || phase == null) {
			return TaskStatus.FAILURE;
		}
		switch (phase) {
			case FETCH -> {
				boolean[] failed = {false};
				Optional<Container> chest = Stores.atChest(c, failed);
				if (failed[0]) {
					return TaskStatus.FAILURE;
				}
				if (chest.isPresent()) {
					SupplyChest.withdraw(chest.get(), c.backpack(), k.food(), 2 - Stores.carried(c, k.food()));
					if (Stores.carried(c, k.food()) < 2) {
						return TaskStatus.FAILURE;
					}
					phase = Phase.ENTER;
				}
			}
			case ENTER -> {
				Pen.Step step = pen.get().enter(c, walk, true);
				if (step == Pen.Step.FAILED) {
					return TaskStatus.FAILURE;
				}
				if (step == Pen.Step.DONE) {
					phase = Phase.FEED;
					walk.reset();
				}
			}
			case FEED -> feed(c, pen.get(), k);
			case LEAVE -> {
				Pen.Step step = pen.get().leave(c, walk, true);
				if (step != Pen.Step.RUNNING) {
					return fed.size() >= 2 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
				}
			}
		}
		return TaskStatus.RUNNING;
	}

	/** Feeds two ready animals of the kind, one at a time. */
	private void feed(CompanionEntity c, Pen pen, Livestock.Kind k) {
		ServerLevel level = (ServerLevel) c.level();
		if (fed.size() >= 2) {
			Speech.say(c, Line.BRED_ANIMALS, k.plural());
			Camp.data(level.getServer()).addStat("animals_bred", 1);
			phase = Phase.LEAVE;
			return;
		}
		Animal a = current;
		if (a == null || !ready(a) || !pen.holds(a)) {
			current = a = pick(c, pen, k);
			if (a == null) {
				phase = Phase.LEAVE; // the other one is not ready after all: try again another time
				return;
			}
		}
		if (!c.actions().walkToEntity(a, FEED_REACH)) {
			if (c.actions().isStuck()) {
				phase = Phase.LEAVE;
			}
			return;
		}
		ItemStack food = takeFood(c, k);
		if (food.isEmpty()) {
			phase = Phase.LEAVE;
			return;
		}
		c.getLookControl().setLookAt(a);
		c.swingArm();
		a.setInLove(null);
		level.playSound(null, a.getX(), a.getY(), a.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
		fed.add(a.getUUID());
		current = null;
	}

	/** One of the kind's food, from the hand or the backpack: it is eaten. */
	private static ItemStack takeFood(CompanionEntity c, Livestock.Kind k) {
		ItemStack hand = c.getMainHandItem();
		if (!hand.isEmpty() && k.food().test(hand)) {
			return hand.split(1);
		}
		return c.backpack().take(k.food(), 1);
	}

	private @Nullable Animal pick(CompanionEntity c, Pen pen, Livestock.Kind k) {
		Animal best = null;
		double bestDist = Double.MAX_VALUE;
		for (Animal a : pen.animals(c.level())) {
			if (Livestock.kind(a) == k && ready(a) && !fed.contains(a.getUUID())) {
				double d = a.distanceToSqr(c);
				if (d < bestDist) {
					bestDist = d;
					best = a;
				}
			}
		}
		return best;
	}

	@Override
	public void stop(CompanionEntity c) {
		phase = null;
		kind = null;
		current = null;
		fed.clear();
	}

	@Override
	public int successCooldown() {
		return 600;
	}

	@Override
	public int failureCooldown() {
		return 400;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
