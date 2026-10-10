package io.github.bradley09roberts.hardcorefriends.market;

import java.util.EnumSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * A trade's job: only the grown-up who holds one of its trades ever takes it on, at their own workplace (or at the
 * camp for a trade held there), by day. Everyone else scores it 0, so a trade adds work for its holder without taking
 * anything from the rest. Where a trade's work overlaps a speciality's own job (the baker's bread and the farmer's
 * baking, the cook's meat and the farmer's cooking), the job stands aside while another friend is on that job, so the
 * specialist always comes first. Finished trade work trains the trade's kind of work ({@code survival.Skills}).
 */
abstract class TradeJob implements CompanionTask {
	private final String id;
	private final Set<Trade> trades;
	private final Set<String> yieldsTo;

	TradeJob(String id, Set<String> yieldsTo, Trade first, Trade... more) {
		this.id = id;
		this.trades = EnumSet.of(first, more);
		this.yieldsTo = Set.copyOf(yieldsTo);
	}

	@Override
	public final String id() {
		return id;
	}

	/** The trades whose holders do this job. */
	final Set<Trade> trades() {
		return trades;
	}

	@Override
	public final double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || c.isChild() || !c.isTeamMember() || c.mode() != CompanionMode.WORK
			|| !FriendsConfig.get().villageTrades) {
			return 0;
		}
		MarketData.Holding h = Market.holding(level.getServer(), c);
		if (h == null || !trades.contains(h.trade) || !Camp.isCampLevel(level, Camp.data(level.getServer()))) {
			return 0;
		}
		Workplace w = null;
		if (!h.atCamp()) {
			w = Workplaces.byKey(level, h.site).orElse(null);
			if (w == null) {
				return 0; // the workplace is gone, or not standing: the planner sees to it
			}
		}
		if (!yieldsTo.isEmpty() && otherOn(c, level)) {
			return 0; // a friend is on the speciality's own job of this kind: they come first
		}
		return scoreWork(c, level, h, w);
	}

	/** True if another friend in this world is working at one of the speciality jobs this job stands aside for. */
	private boolean otherOn(CompanionEntity c, ServerLevel level) {
		for (CompanionEntity other : Companions.in(level)) {
			if (other == c || other.mode() != CompanionMode.WORK) {
				continue;
			}
			CompanionTask doing = other.scheduler().current();
			if (doing != null && yieldsTo.contains(doing.id())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The score for the holder of one of this job's trades, in the camp's world. {@code w} is their workplace, or null
	 * for a trade held at the camp.
	 */
	abstract double scoreWork(CompanionEntity c, ServerLevel level, MarketData.Holding h, @Nullable Workplace w);

	/** The holder's trade and workplace again, at the start of a run (null if they no longer hold one of these trades). */
	final MarketData.@Nullable Holding holding(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return null;
		}
		MarketData.Holding h = Market.holding(level.getServer(), c);
		return h != null && trades.contains(h.trade) ? h : null;
	}

	/** The holder's workplace, or null for a trade held at the camp (or a workplace no longer standing). */
	static @Nullable Workplace workplace(CompanionEntity c, MarketData.@Nullable Holding h) {
		if (h == null || h.atCamp() || !(c.level() instanceof ServerLevel level)) {
			return null;
		}
		return Workplaces.byKey(level, h.site).orElse(null);
	}

	/** True in the working part of the day: not night, not dusk, not a dark storm. */
	static boolean workingHours(ServerLevel level) {
		return !Camp.isNight(level) && !Camp.isDusk(level);
	}

	/**
	 * True if a friend is in a fight: they have a target, or an attacker (a monster, or whoever shot at them) hurt them
	 * within the last {@code ticks}. Damage with nobody behind it (poison, withering, hunger, a fall) is no fight, so a
	 * friend taking it every second or so is still calm enough to be treated, or to fish.
	 */
	static boolean fighting(CompanionEntity c, int ticks) {
		return c.getTarget() != null || c.getLastHurtByMob() != null && c.tickCount - c.getLastHurtByMobTimestamp() < ticks;
	}
}
