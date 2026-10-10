package io.github.bradley09roberts.hardcorefriends.life;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * A shopkeeper keeping their stall on market day: they go and stand by their shop's work place for a couple of
 * minutes at a time, calling out to passers-by, so a player coming to trade finds them at the counter. When a player
 * comes near, this job ends at once and the market's own shop-keeping job (which serves them) takes over. Scores
 * {@value #SCORE}: ordinary work, so needs, danger and urgent jobs always come first.
 */
final class MarketStallTask implements CompanionTask {
	static final String ID = "life.market_stall";
	private static final double SCORE = 46;
	/** A player this near the stall: leave the serving to the shop's own job. */
	private static final double CUSTOMER = 10;
	private static final int STAY = 20 * 120;
	private static final int CALL_EVERY = 20 * 30;

	private @Nullable BlockPos stand;
	private boolean arrived;
	private int ticks;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "keeping a market stall";
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.isChild() || !Places.free(c) || !(c.level() instanceof ServerLevel level) || !MarketDay.open(level) || c.getTarget() != null) {
			return 0;
		}
		BlockPos stall = MarketDay.stall(c).orElse(null);
		if (stall == null || !level.isLoaded(stall) || customerNear(level, stall)) {
			return 0;
		}
		return SCORE;
	}

	private static boolean customerNear(ServerLevel level, BlockPos stall) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(Vec3.atCenterOf(stall)) <= CUSTOMER * CUSTOMER) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos stall = MarketDay.stall(c).orElse(null);
		stand = stall == null ? null : Places.standableNear(level, stall, 2);
		arrived = false;
		ticks = 0;
		return stand != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos to = stand;
		if (to == null || !MarketDay.open(level)) {
			return TaskStatus.SUCCESS;
		}
		ticks++;
		if (ticks % 20 == 0 && customerNear(level, to)) {
			return TaskStatus.SUCCESS; // a customer: the shop's own job serves them
		}
		if (ticks % 20 == 0 && Places.hostileNear(level, c.blockPosition(), Places.SPOIL_RANGE)) {
			return TaskStatus.FAILURE;
		}
		if (!arrived) {
			if (c.actions().walkTo(to, 1.0)) {
				arrived = true;
				ticks = 0;
				Speech.say(c, Line.MARKET_DAY, MarketDay.stallName(c));
			} else if (c.actions().isStuck()) {
				return TaskStatus.FAILURE;
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		if (ticks % CALL_EVERY == 0) {
			Speech.say(c, Line.MARKET_DAY, MarketDay.stallName(c));
		}
		if (ticks % 60 == 0) {
			var near = level.getNearestPlayer(c, 8);
			if (near != null && !near.isSpectator()) {
				c.getLookControl().setLookAt(near);
			}
		}
		return ticks >= STAY ? TaskStatus.SUCCESS : TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		stand = null;
		arrived = false;
	}

	@Override
	public int failureCooldown() {
		return 20 * 60;
	}

	@Override
	public int successCooldown() {
		return 20 * 60 * 3;
	}

	@Override
	public int maxTicks() {
		return 20 * 60 * 3;
	}
}
