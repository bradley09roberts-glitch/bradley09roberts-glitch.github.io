package io.github.bradley09roberts.hardcorefriends.ai.role.ranch;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
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
 * The farmer keeps the pen to a sensible size: when a kind has more grown animals than are kept for breeding
 * ({@link Livestock#keepAdults}: {@value Livestock#KEEP_ADULTS}, fewer when the pen is full), she takes a sword or an
 * axe (her own, or one from the supply chest), goes into the paddock and butchers the surplus, up to two a visit, then
 * gathers the drops (meat, leather, wool, feathers) into her backpack. She never takes a kind below its breeding
 * adults, never a young one, never an animal in love, and never one that is plainly somebody's (named, on a lead,
 * saddled, owned: {@link Wildlife#isSomebodys}); those count among the animals kept. She only strikes with the gate
 * shut, and does not start while a player is in or right by the pen (the gate could not be shut).
 */
public final class ButcherTask implements CompanionTask {
	public static final String ID = "fern.butcher";
	private static final double BASE = 42;
	private static final int PER_VISIT = 2;
	private static final Predicate<ItemStack> WEAPON = s -> s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES);

	private enum Phase {
		FETCH,
		ENTER,
		KILL,
		GATHER,
		LEAVE
	}

	private Livestock.@Nullable Kind planned;
	private Livestock.@Nullable Kind kind;
	private @Nullable Phase phase;
	private @Nullable Animal target;
	private int killed;
	private int swing;
	private Carcass carcass = Carcass.none();
	private final Pen.GateWalk walk = new Pen.GateWalk();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Livestock.Kind k = kind;
		return k == null ? "butchering" : "butchering a " + k.singular();
	}

	/**
	 * How many grown animals of the kind may be butchered: those beyond the ones the pen keeps, and no more than there
	 * are animals she may take (somebody's animals and ones in love count among the kept, never the taken).
	 */
	static int surplus(List<Animal> animals, Livestock.Kind k) {
		int extra = Livestock.count(animals, k, true) - Livestock.keepAdults(animals, k);
		int takeable = 0;
		for (Animal a : animals) {
			if (mayTake(a, k)) {
				takeable++;
			}
		}
		return Math.min(extra, takeable);
	}

	/** A grown animal of the kind, not in love (breeding comes first), and nobody's but the camp's. */
	private static boolean mayTake(Animal a, Livestock.Kind k) {
		return a.isAlive() && Livestock.kind(a) == k && !a.isBaby() && !a.isInLove() && !Wildlife.isSomebodys(a);
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Pen pen = Pen.of(level).orElse(null);
		planned = null;
		if (pen == null || Pen.otherAtWork(c) || pen.playerNear(level)) {
			return 0; // with a player by the pen the gate cannot be shut, and nothing is struck with it open
		}
		if (!c.isArmed() && Stores.inChest(c, WEAPON) == 0) {
			return 0;
		}
		List<Animal> animals = pen.animals(level);
		int most = 0;
		for (Livestock.Kind k : Livestock.Kind.values()) {
			int extra = k.penned() ? surplus(animals, k) : 0;
			if (extra > most) {
				most = extra;
				planned = k;
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
		killed = 0;
		swing = 0;
		target = null;
		carcass = Carcass.none();
		walk.reset();
		phase = c.isArmed() ? Phase.ENTER : Phase.FETCH;
		Speech.say(c, Line.BUTCHERING, kind.singular());
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
					// A sword if there is one, else an axe: borrowed, and put back with the rest at the next deposit.
					if (SupplyChest.withdraw(chest.get(), c.backpack(), s -> s.is(ItemTags.SWORDS), 1) == 0) {
						SupplyChest.withdraw(chest.get(), c.backpack(), s -> s.is(ItemTags.AXES), 1);
					}
					if (!c.isArmed()) {
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
					// In, but the gate would not shut (someone by it): a struck animal could run out, so out again.
					phase = pen.get().gateShut(level) ? Phase.KILL : Phase.LEAVE;
					walk.reset();
				}
			}
			case KILL -> kill(c, pen.get(), k);
			case GATHER -> {
				if (carcass.gather(c)) {
					phase = killed < PER_VISIT ? Phase.KILL : Phase.LEAVE;
				}
			}
			case LEAVE -> {
				Pen.Step step = pen.get().leave(c, walk, true);
				if (step != Pen.Step.RUNNING) {
					return killed > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
				}
			}
		}
		return TaskStatus.RUNNING;
	}

	private void kill(CompanionEntity c, Pen pen, Livestock.Kind k) {
		ServerLevel level = (ServerLevel) c.level();
		if (!pen.gateShut(level)) {
			target = null; // someone opened the gate (or broke it): never strike with it open
			phase = Phase.LEAVE;
			return;
		}
		Animal a = target;
		if (a == null || !mayTake(a, k) || !pen.holds(a)) {
			List<Animal> animals = pen.animals(level);
			if (surplus(animals, k) <= 0) {
				phase = Phase.LEAVE;
				return;
			}
			target = a = pick(c, animals, k);
			if (a == null) {
				phase = Phase.LEAVE;
				return;
			}
		}
		c.equipBestWeapon();
		if (--swing > 0) {
			c.getLookControl().setLookAt(a);
			if (!Carcass.inReach(c, a)) {
				c.actions().walkToEntity(a, 1.5);
			}
			return;
		}
		if (!Carcass.inReach(c, a)) {
			c.actions().walkToEntity(a, 1.5);
			if (c.actions().isStuck()) {
				phase = Phase.LEAVE;
			}
			return;
		}
		if (!mayTake(a, k) || surplus(pen.animals(level), k) <= 0) {
			target = null; // never a young one, nobody's own animal, never below the breeding adults
			phase = Phase.LEAVE;
			return;
		}
		Carcass.strike(c, a);
		swing = Carcass.SWING_TICKS;
		if (!a.isAlive()) {
			killed++;
			target = null;
			carcass.died(a.position());
			Camp.data(level.getServer()).addStat("animals_butchered", 1);
			phase = Phase.GATHER;
		}
	}

	/** The nearest grown animal of the kind she may take (not in love, not somebody's). */
	private static @Nullable Animal pick(CompanionEntity c, List<Animal> animals, Livestock.Kind k) {
		Animal best = null;
		double bestScore = Double.MAX_VALUE;
		for (Animal a : animals) {
			if (!mayTake(a, k)) {
				continue;
			}
			double score = a.distanceToSqr(c);
			if (score < bestScore) {
				bestScore = score;
				best = a;
			}
		}
		return best;
	}

	@Override
	public void stop(CompanionEntity c) {
		phase = null;
		kind = null;
		target = null;
	}

	@Override
	public int successCooldown() {
		return 200;
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
