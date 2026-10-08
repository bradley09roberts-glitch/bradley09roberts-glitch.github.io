package io.github.bradley09roberts.hardcorefriends.ai.role.ranch;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * The farmer stocks the pen: she finds a wild cow, pig, sheep or chicken in the camp or the gathering ring and brings
 * it home, until each kind has a breeding pair. With a lead from the supply chest she ties it on (the lead is used up
 * while it is on the animal and comes back when she unties it, as in vanilla); without one she holds the animal's
 * food (wheat for cows and sheep, a carrot, potato or beetroot for pigs, seeds for chickens) and it follows her, as
 * animals follow a player holding their food. She opens the gate, leads it into the paddock, lets it go and shuts the
 * gate behind her. Animals that look like somebody's are never taken ({@link Wildlife#mayLead}).
 */
public final class BringAnimalTask implements CompanionTask {
	public static final String ID = "fern.bring_animal";
	private static final double BASE = 46;
	/** Like vanilla tempting: an animal notices food held within this many blocks. */
	private static final double TEMPT_RANGE = 10;
	/** Closer than this, a tempted animal stops and waits. */
	private static final double TEMPT_STOP = 2.5;
	/** Further than this behind, the friend goes back for the animal. */
	private static final double LAG = 6;
	private static final int PLAN_INTERVAL = 40;
	private static final int IGNORE_TICKS = 1200;
	private static final int PATIENCE = 300;
	private static final Predicate<ItemStack> LEAD = s -> s.is(Items.LEAD);

	private enum Phase {
		FETCH,
		APPROACH,
		HOME,
		INTO,
		OUT
	}

	private @Nullable Animal planned;
	private long plannedAt = Long.MIN_VALUE / 2;
	private @Nullable Animal target;
	private Livestock.@Nullable Kind kind;
	private boolean lead;
	private boolean tied;
	private @Nullable Phase phase;
	private @Nullable Item heldBefore;
	private boolean holding;
	private int repath;
	private int waiting;
	private final Map<UUID, Long> ignored = new HashMap<>();
	private final Pen.GateWalk walk = new Pen.GateWalk();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Livestock.Kind k = kind;
		return k == null ? "bringing animals to the pen" : "bringing a " + k.singular() + " to the pen";
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Pen pen = Pen.of(level).orElse(null);
		if (pen == null || Camp.isNight(level) || Camp.isDusk(level) || Pen.otherAtWork(c)) {
			return 0;
		}
		long now = level.getGameTime();
		if (now - plannedAt >= PLAN_INTERVAL || now < plannedAt || planned == null || !planned.isAlive()) {
			plannedAt = now;
			planned = plan(c, pen);
		}
		return planned == null ? 0 : Math.min(69, BASE * CampNeeds.weight(CampNeeds.Need.FOOD));
	}

	/** The nearest wild animal of a kind the pen still lacks a pair of, that the friend can lead or lure. */
	private @Nullable Animal plan(CompanionEntity c, Pen pen) {
		ServerLevel level = (ServerLevel) c.level();
		List<Animal> penned = pen.animals(level);
		if (penned.size() >= Livestock.MAX_TOTAL) {
			return null;
		}
		Set<Livestock.Kind> wanted = EnumSet.noneOf(Livestock.Kind.class);
		for (Livestock.Kind k : Livestock.Kind.values()) {
			if (k.penned() && Livestock.count(penned, k, true) < Livestock.PAIR
				&& Livestock.count(penned, k, false) < Livestock.MAX_PER_KIND) {
				wanted.add(k);
			}
		}
		if (wanted.isEmpty()) {
			return null;
		}
		boolean leadAtHand = Stores.available(c, LEAD) > 0;
		Set<Livestock.Kind> means = EnumSet.noneOf(Livestock.Kind.class);
		for (Livestock.Kind k : wanted) {
			if (leadAtHand || Stores.available(c, k.food()) > 0) {
				means.add(k);
			}
		}
		if (means.isEmpty()) {
			return null;
		}
		long now = level.getGameTime();
		ignored.values().removeIf(until -> until <= now);
		return Wildlife.nearest(c, a -> means.contains(Livestock.kind(a)) && !ignored.containsKey(a.getUUID())
			&& Wildlife.mayLead(c, a, pen));
	}

	@Override
	public boolean start(CompanionEntity c) {
		target = planned;
		planned = null;
		Animal a = target;
		if (a == null || !a.isAlive()) {
			return false;
		}
		kind = Livestock.kind(a);
		if (kind == null) {
			return false;
		}
		lead = Stores.available(c, LEAD) > 0;
		tied = false;
		holding = false;
		heldBefore = null;
		waiting = 0;
		walk.reset();
		boolean carried = lead ? c.backpack().has(LEAD) : Stores.carried(c, kind.food()) > 0;
		phase = carried ? Phase.APPROACH : Phase.FETCH;
		Speech.say(c, Line.LEADING_ANIMAL, kind.singular());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Animal a = target;
		Livestock.Kind k = kind;
		Optional<Pen> pen = Pen.of(level);
		if (a == null || k == null || phase == null || pen.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		if (phase != Phase.OUT && !stillOurs(c, a)) {
			return giveUp(c, a);
		}
		if (tied && !(a.isLeashed() && a.getLeashHolder() == c)) {
			tied = false; // the lead snapped and fell off (it lies on the ground for anyone to pick up)
			return giveUp(c, a);
		}
		return switch (phase) {
			case FETCH -> fetch(c, k);
			case APPROACH -> approach(c, a, k);
			case HOME -> home(c, a, pen.get());
			case INTO -> into(c, a, pen.get());
			case OUT -> out(c, pen.get());
		};
	}

	/** The animal is still there for the taking: alive, not named meanwhile, not on someone else's lead. */
	private boolean stillOurs(CompanionEntity c, Animal a) {
		if (!a.isAlive() || a.isRemoved() || a.hasCustomName() || a.level() != c.level()) {
			return false;
		}
		return !a.isLeashed() || a.getLeashHolder() == c;
	}

	private TaskStatus fetch(CompanionEntity c, Livestock.Kind k) {
		boolean[] failed = {false};
		Optional<Container> chest = Stores.atChest(c, failed);
		if (failed[0]) {
			return TaskStatus.FAILURE;
		}
		if (chest.isEmpty()) {
			return TaskStatus.RUNNING;
		}
		if (lead && !c.backpack().has(LEAD)) {
			SupplyChest.withdraw(chest.get(), c.backpack(), LEAD, 1);
		}
		if (!c.backpack().has(LEAD)) {
			lead = false; // someone took the last lead: lure it instead
			if (Stores.carried(c, k.food()) == 0) {
				SupplyChest.withdraw(chest.get(), c.backpack(), k.food(), 8);
			}
			if (Stores.carried(c, k.food()) == 0) {
				return TaskStatus.FAILURE;
			}
		}
		phase = Phase.APPROACH;
		return TaskStatus.RUNNING;
	}

	private TaskStatus approach(CompanionEntity c, Animal a, Livestock.Kind k) {
		if (!lead) {
			hold(c, k);
			tempt(c, a);
		}
		double reach = lead ? 2.5 : 3.5;
		if (!c.actions().walkToEntity(a, reach)) {
			if (c.actions().isStuck()) {
				return giveUp(c, a);
			}
			return TaskStatus.RUNNING;
		}
		if (lead) {
			ItemStack one = c.backpack().take(LEAD, 1);
			if (one.isEmpty() || !a.canHaveALeashAttachedTo(c)) {
				if (!one.isEmpty()) {
					Stores.giveBack(c, one);
				}
				return giveUp(c, a);
			}
			a.setLeashedTo(c, true); // the lead is on the animal now; it comes back when untied
			tied = true;
			c.swingArm();
		}
		phase = Phase.HOME;
		return TaskStatus.RUNNING;
	}

	/** Walks to the pen gate with the animal following, going back for it when it lags behind, and opens the gate. */
	private TaskStatus home(CompanionEntity c, Animal a, Pen pen) {
		tempt(c, a);
		if (lagging(c, a)) {
			return comeBackFor(c, a);
		}
		if (!c.actions().walkTo(pen.outside(), 1.0)) {
			return c.actions().isStuck() ? giveUp(c, a) : TaskStatus.RUNNING;
		}
		if (a.distanceTo(c) > 4.0) {
			return waitFor(c, a); // let it catch up before opening the gate
		}
		if (pen.setGate(c, true)) {
			phase = Phase.INTO;
			waiting = 0;
		} else if (walk.waited()) {
			return giveUp(c, a);
		}
		return TaskStatus.RUNNING;
	}

	/**
	 * Leads the animal through the gate to a back corner of the paddock and, once both are well inside, lets it go and
	 * sends it to the other back corner, so it is not in the way (nor jostled out of the gate) when the friend leaves.
	 */
	private TaskStatus into(CompanionEntity c, Animal a, Pen pen) {
		tempt(c, a);
		if (lagging(c, a)) {
			return comeBackFor(c, a);
		}
		if (!c.actions().walkTo(pen.at(2, 6), 0.8)) {
			return c.actions().isStuck() ? giveUp(c, a) : TaskStatus.RUNNING;
		}
		if (pen.holds(a) && !pen.inGateway(a) && a.blockPosition().distManhattan(pen.gate()) >= 3) {
			release(c, a);
			pen.sendToBack(a);
			phase = Phase.OUT;
			walk.reset();
			return TaskStatus.RUNNING;
		}
		return waitFor(c, a);
	}

	private TaskStatus out(CompanionEntity c, Pen pen) {
		return switch (pen.leave(c, walk, true)) {
			case DONE -> {
				Animal a = target;
				if (a == null || !a.isAlive() || !pen.holds(a)) {
					yield TaskStatus.FAILURE; // it got out again: it will be fetched another time
				}
				Camp.data(((ServerLevel) c.level()).getServer()).addStat("animals_penned", 1);
				yield TaskStatus.SUCCESS;
			}
			case FAILED -> TaskStatus.FAILURE;
			case RUNNING -> TaskStatus.RUNNING;
		};
	}

	// ------------------------------------------------------------ helpers

	private boolean lagging(CompanionEntity c, Animal a) {
		return a.distanceTo(c) > LAG;
	}

	/** Goes back towards an animal that fell behind (a lured one stops following beyond its tempt range). */
	private TaskStatus comeBackFor(CompanionEntity c, Animal a) {
		if (++waiting > PATIENCE) {
			return giveUp(c, a);
		}
		c.actions().walkToEntity(a, 3.0);
		return c.actions().isStuck() ? giveUp(c, a) : TaskStatus.RUNNING;
	}

	private TaskStatus waitFor(CompanionEntity c, Animal a) {
		c.actions().stopWalking();
		c.getLookControl().setLookAt(a);
		return ++waiting > PATIENCE ? giveUp(c, a) : TaskStatus.RUNNING;
	}

	/** Holds the animal's food in hand, so it follows. */
	private void hold(CompanionEntity c, Livestock.Kind k) {
		if (!holding) {
			heldBefore = Stores.hold(c, k.food());
			holding = true;
		}
	}

	/**
	 * Like vanilla tempting, for a friend: while the friend holds its food within {@value #TEMPT_RANGE} blocks, the
	 * animal looks at them and walks after them, stopping {@value #TEMPT_STOP} blocks short. An animal on a lead is
	 * pulled by the lead instead.
	 */
	private void tempt(CompanionEntity c, Animal a) {
		if (tied || !holding || !a.isFood(c.getMainHandItem())) {
			return;
		}
		double d = a.distanceTo(c);
		if (d > TEMPT_RANGE) {
			return;
		}
		a.getLookControl().setLookAt(c, a.getMaxHeadYRot() + 20, a.getMaxHeadXRot());
		if (d < TEMPT_STOP) {
			a.getNavigation().stop();
			return;
		}
		if (--repath <= 0 || a.getNavigation().isDone()) {
			repath = 5;
			a.getNavigation().moveTo(c, 1.25);
		}
	}

	/** Lets the animal go: unties the lead (it goes back in the backpack) or puts the food away. */
	private void release(CompanionEntity c, Animal a) {
		if (tied && a.isLeashed() && a.getLeashHolder() == c) {
			a.removeLeash();
			Stores.giveBack(c, new ItemStack(Items.LEAD));
		}
		tied = false;
		if (holding && kind != null) {
			Stores.stow(c, kind.food(), heldBefore);
		}
		holding = false;
		a.getNavigation().stop();
	}

	private TaskStatus giveUp(CompanionEntity c, Animal a) {
		ignored.put(a.getUUID(), c.level().getGameTime() + IGNORE_TICKS);
		return TaskStatus.FAILURE;
	}

	@Override
	public void stop(CompanionEntity c) {
		Animal a = target;
		if (a != null) {
			release(c, a);
		} else if (holding && kind != null) {
			Stores.stow(c, kind.food(), heldBefore);
		}
		holding = false;
		tied = false;
		target = null;
		phase = null;
		kind = null;
	}

	@Override
	public int successCooldown() {
		return 100;
	}

	@Override
	public int failureCooldown() {
		return 300;
	}

	@Override
	public int maxTicks() {
		return 20 * 150;
	}
}
