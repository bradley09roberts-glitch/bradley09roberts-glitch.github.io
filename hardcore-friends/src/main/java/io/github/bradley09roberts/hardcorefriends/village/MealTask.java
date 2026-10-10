package io.github.bradley09roberts.hardcorefriends.village;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Meals at the table: breakfast just after dawn and lunch around midday, a friend of the village who is a little
 * peckish (hunger below {@value #PECKISH}) and carries food sits down to eat at home (at their house's table) or, now
 * and then and always without a home, at the tavern when the village has one. One meal each time a day, from their own
 * backpack (friends keep a few pieces of food: what the camp's eating job tops up), so the camp's stores are used just as
 * before; the table only changes where it is eaten.
 *
 * <p>A needs job scoring {@value #SCORE}: between jobs it comes before ordinary work, but it never interrupts work in
 * hand (that would need 25 more), and a hungry friend's meal anywhere (the eating job) always comes first.
 */
final class MealTask implements CompanionTask {
	static final String ID = "needs.meal";
	private static final double SCORE = 62;
	private static final double PECKISH = 80;
	private static final double REACH = 1.5;
	private static final int SIT_TICKS = 20 * 6;
	private static final int WALK_TICKS = 20 * 40;

	/** Which meal each friend last had: day × 2 + 0 for breakfast, + 1 for lunch. */
	private static final Map<UUID, Long> LAST_MEAL = new HashMap<>();

	private @Nullable BlockPos seat;
	private boolean seated;
	private boolean atTavern;
	private boolean lunch;
	private int ticks;

	static void clear() {
		LAST_MEAL.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		String where = atTavern ? "at the tavern" : "at home";
		return (lunch ? "having lunch " : "having breakfast ") + where;
	}

	/** This meal's number (day × 2 + 0 breakfast or 1 lunch), or -1 outside mealtimes. */
	private static long mealIndex(ServerLevel level) {
		long time = Camp.timeOfDay(level);
		long day = Camp.day(level);
		if (time < 1500) {
			return day * 2;
		}
		if (time >= 5500 && time < 7000) {
			return day * 2 + 1;
		}
		return -1;
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || level.dimensionType().hasFixedTime()) {
			return 0;
		}
		long meal = mealIndex(level);
		if (meal < 0 || LAST_MEAL.getOrDefault(c.getUUID(), -1L) == meal) {
			return 0;
		}
		if (c.needs().get(Needs.Need.HUNGER) >= PECKISH || !c.hasFood() || !Routine.inVillage(c) || NightWatch.isOnWatch(c)) {
			return 0;
		}
		if (Routine.home(c).isEmpty() && Routine.tavern(level) == null) {
			return 0;
		}
		return SCORE;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		seated = false;
		ticks = 0;
		BlockPos tavern = Routine.tavern(level);
		BlockPos table = Routine.home(c).map(p -> Routine.spotIn(level, p, "sit", "table", "inside")).orElse(null);
		atTavern = tavern != null && (table == null || c.getRandom().nextInt(4) == 0);
		seat = atTavern ? tavern : table;
		long meal = mealIndex(level);
		lunch = meal % 2 == 1;
		LAST_MEAL.put(c.getUUID(), meal); // one go at each meal, eaten or not
		return seat != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		BlockPos to = seat;
		if (to == null) {
			return TaskStatus.FAILURE;
		}
		ticks++;
		if (!seated) {
			if (c.actions().walkTo(to, REACH)) {
				seated = true;
				ticks = 0;
				eat(c);
			} else if (c.actions().isStuck() || ticks > WALK_TICKS) {
				return TaskStatus.FAILURE;
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		return ticks >= SIT_TICKS ? TaskStatus.SUCCESS : TaskStatus.RUNNING;
	}

	/** One piece of food from the backpack, eaten at the table. */
	private static void eat(CompanionEntity c) {
		ItemStack food = c.backpack().take(CompanionEntity::isEdible, 1);
		if (food.isEmpty()) {
			return;
		}
		String name = food.getHoverName().getString();
		c.eat(food);
		Speech.say(c, Line.ATE, name);
	}

	@Override
	public void stop(CompanionEntity c) {
		seat = null;
		seated = false;
		ticks = 0;
	}

	@Override
	public int failureCooldown() {
		return 20 * 30;
	}

	@Override
	public int maxTicks() {
		return WALK_TICKS + SIT_TICKS + 20 * 5;
	}
}
