package io.github.bradley09roberts.hardcorefriends.life;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.village.VillagePlan;

/**
 * Looking round the market on market day: in their spare time, once each market day, a friend (or a child) wanders
 * over to a shopkeeper at their stall, the market square's stalls, or else the square, and looks round for a little
 * while, chatting to whoever is there: more company and fun, and a busier square. A needs job ({@code needs.market}),
 * time off, at {@value #SCORE}: a pastime, so it waits for work to be done.
 */
final class MarketVisitTask implements CompanionTask {
	static final String ID = "needs.market";
	private static final double SCORE = 32;
	private static final int STAY = 20 * 30;

	/** The market day each friend last went round the market. */
	private static final Map<UUID, Long> VISITED = new HashMap<>();

	private @Nullable BlockPos spot;
	private boolean arrived;
	private int ticks;

	static void clear() {
		VISITED.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "looking round the market";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!Places.free(c) || !(c.level() instanceof ServerLevel level) || !MarketDay.open(level) || c.getTarget() != null
			|| MarketDay.stall(c).isPresent()) {
			return 0;
		}
		Long last = VISITED.get(c.getUUID());
		return last != null && last == Calendar.today(level.getServer()) ? 0 : SCORE;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		VISITED.put(c.getUUID(), Calendar.today(level.getServer()));
		if (VISITED.size() > 256) {
			VISITED.clear();
			VISITED.put(c.getUUID(), Calendar.today(level.getServer()));
		}
		List<BlockPos> choices = new ArrayList<>();
		for (CompanionEntity keeper : Places.freePeople(level)) {
			MarketDay.stall(keeper).ifPresent(choices::add);
		}
		for (VillagePlan.Building market : VillagePlan.buildingsOfKind(level.getServer(), "civic:market")) {
			choices.addAll(market.marker("customer"));
		}
		BlockPos square = Places.square(level);
		if (choices.isEmpty() && square != null) {
			choices.add(square);
		}
		if (choices.isEmpty()) {
			return false;
		}
		BlockPos target = choices.get(c.getRandom().nextInt(choices.size()));
		spot = Places.standableNear(level, target, 3);
		arrived = false;
		ticks = 0;
		return spot != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos to = spot;
		if (to == null) {
			return TaskStatus.FAILURE;
		}
		ticks++;
		if (ticks % 20 == 0 && Places.hostileNear(level, c.blockPosition(), Places.SPOIL_RANGE)) {
			return TaskStatus.FAILURE;
		}
		if (!arrived) {
			if (c.actions().walkTo(to, 1.5)) {
				arrived = true;
				ticks = 0;
				Speech.say(c, Line.MARKET_BROWSE);
			} else if (c.actions().isStuck()) {
				return TaskStatus.FAILURE;
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		if (ticks % 20 == 0) {
			c.needs().add(Needs.Need.FUN, 0.4);
			if (Needs.hasCompany(c)) {
				c.needs().add(Needs.Need.SOCIAL, 0.3);
			}
		}
		if (ticks % 80 == 0) {
			c.getLookControl().setLookAt(c.getX() + c.getRandom().nextInt(7) - 3, c.getEyeY(), c.getZ() + c.getRandom().nextInt(7) - 3);
		}
		return ticks >= STAY ? TaskStatus.SUCCESS : TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		spot = null;
		arrived = false;
	}

	@Override
	public int failureCooldown() {
		return 20 * 60;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}
}
