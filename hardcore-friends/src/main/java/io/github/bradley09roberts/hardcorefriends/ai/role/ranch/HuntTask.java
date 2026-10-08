package io.github.bradley09roberts.hardcorefriends.ai.role.ranch;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * When the camp is short of food, the farmer goes hunting: by day only, while she is healthy (well above the health
 * at which she falls back) and armed with a sword or an axe, she hunts a wild cow, pig, sheep, chicken or rabbit in
 * the gathering ring outside the camp and gathers what it drops. The strict rules of {@link Wildlife#mayHunt} keep the
 * player's animals safe (never a named, tamed, leashed or riding animal, never one near anything a player built or
 * inside a player's fences, never a young one, never the last two of a kind nearby, never in the camp or the pen,
 * never where a friend died lately, never at dusk or night); they are checked again before every blow, and the hunt
 * is called off the moment any of them fails.
 */
public final class HuntTask implements CompanionTask {
	public static final String ID = "fern.hunt";
	private static final double BASE = 40;
	/** Hunting only starts when the camp is at least this short of food (0 plenty, 1 none at all). */
	public static final double FOOD_SHORT = 0.4;
	private static final int PLAN_INTERVAL = 60;
	private static final int CHECK_INTERVAL = 20;
	private static final int IGNORE_TICKS = 2400;

	private @Nullable Animal planned;
	private long plannedAt = Long.MIN_VALUE / 2;
	private @Nullable Animal target;
	private Livestock.@Nullable Kind kind;
	private int swing;
	private int sinceCheck;
	private boolean gathering;
	private Carcass carcass = Carcass.none();
	private final Map<UUID, Long> ignored = new HashMap<>();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Livestock.Kind k = kind;
		return k == null ? "hunting" : "hunting a " + k.singular();
	}

	/** Well enough to hunt: above the health at which this friend would fall back, with a margin. */
	static boolean healthy(CompanionEntity c) {
		double enough = Math.max(0.5, c.friendId().retreatFraction() + 0.1);
		return !c.isRetreating() && c.getHealth() > c.getMaxHealth() * enough;
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (Camp.isNight(level) || Camp.isDusk(level) || CampNeeds.need(CampNeeds.Need.FOOD) < FOOD_SHORT) {
			return 0;
		}
		if (!healthy(c) || !c.isArmed() || !Camp.isCampLevel(level, Camp.data(level.getServer()))) {
			return 0;
		}
		long now = level.getGameTime();
		if (now - plannedAt >= PLAN_INTERVAL || now < plannedAt || planned == null || !planned.isAlive()) {
			plannedAt = now;
			ignored.values().removeIf(until -> until <= now);
			Pen pen = Pen.site(level).orElse(null);
			planned = Wildlife.nearest(c, a -> !ignored.containsKey(a.getUUID()) && Wildlife.mayHunt(c, a, pen));
		}
		return planned == null ? 0 : Math.min(69, BASE * CampNeeds.weight(CampNeeds.Need.FOOD));
	}

	@Override
	public boolean start(CompanionEntity c) {
		target = planned;
		planned = null;
		if (target == null || !target.isAlive()) {
			return false;
		}
		kind = Livestock.kind(target);
		swing = 0;
		sinceCheck = 0;
		gathering = false;
		carcass = Carcass.none();
		Speech.say(c, Line.HUNTING, kind == null ? "animal" : kind.singular());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (gathering) {
			return carcass.gather(c) ? TaskStatus.SUCCESS : TaskStatus.RUNNING;
		}
		Animal a = target;
		if (a == null || !a.isAlive() || a.isRemoved()) {
			return TaskStatus.FAILURE;
		}
		if (++sinceCheck >= CHECK_INTERVAL) {
			sinceCheck = 0;
			if (!stillAllowed(c, a)) {
				return callOff(c, a);
			}
		}
		c.equipBestWeapon();
		if (--swing > 0) {
			c.getLookControl().setLookAt(a);
			if (!Carcass.inReach(c, a)) {
				c.actions().walkToEntity(a, 1.5);
			}
			return TaskStatus.RUNNING;
		}
		if (!Carcass.inReach(c, a)) {
			c.actions().walkToEntity(a, 1.5);
			return c.actions().isStuck() ? callOff(c, a) : TaskStatus.RUNNING;
		}
		// Every rule once more, worked out afresh, before the blow.
		if (!stillAllowed(c, a) || Wildlife.looksOwnedNow(level, a)) {
			return callOff(c, a);
		}
		Carcass.strike(c, a);
		swing = Carcass.SWING_TICKS;
		if (!a.isAlive()) {
			carcass.died(a.position());
			gathering = true;
			Camp.data(level.getServer()).addStat("animals_hunted", 1);
		}
		return TaskStatus.RUNNING;
	}

	/** The hunt may go on: still day, still healthy and armed, and the animal still one the rules allow. */
	private boolean stillAllowed(CompanionEntity c, Animal a) {
		return healthy(c) && c.isArmed() && Wildlife.mayHunt(c, a, Pen.site((ServerLevel) c.level()).orElse(null));
	}

	private TaskStatus callOff(CompanionEntity c, Animal a) {
		ignored.put(a.getUUID(), c.level().getGameTime() + IGNORE_TICKS);
		return TaskStatus.FAILURE;
	}

	@Override
	public void stop(CompanionEntity c) {
		target = null;
		kind = null;
		gathering = false;
	}

	@Override
	public int successCooldown() {
		return 200;
	}

	@Override
	public int failureCooldown() {
		return 300;
	}

	@Override
	public int maxTicks() {
		return 20 * 60;
	}
}
