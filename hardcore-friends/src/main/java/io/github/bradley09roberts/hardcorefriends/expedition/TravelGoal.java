package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.EnumSet;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.survival.ChunkLoader;

/**
 * Finding the way through a portal on their own. A friend following a player who has gone into another dimension
 * without them walks to the portal the player used (or the way they came in, or another crossing a player has made)
 * and goes through after them. A friend in another dimension than the camp's with nobody to follow waits by the portal
 * they came in by; once {@link Travel#WAIT_LIMIT} has passed (counted in {@link Expeditions}) or they are told to go
 * back to work, they walk to it and go home. Work never runs away from the camp's dimension: this goal outranks the
 * job scheduler there, so no job tries to walk to a camp in another world.
 *
 * <p>Never stranded: a portal they cannot get to (no path, the way blocked) is given up after
 * {@value #GIVE_UP_TICKS} ticks without progress, and they find their way across from where they stand. While no
 * player is near, a roaming ticket keeps their land running so they do not freeze half way.
 */
public class TravelGoal extends Goal {
	private enum Plan {
		NONE,
		/** Off after the leader through the portal they used. */
		FOLLOW_THROUGH,
		/** Left on their own away from home: waiting by the portal they came in by. */
		WAIT,
		/** Going home through the portal they came in by. */
		GO_HOME
	}

	/** Close enough to the portal to step through. */
	private static final double AT_PORTAL = 2.5;
	/** Close enough to step through even when the last bit of the way is blocked. */
	private static final double NEAR_PORTAL = 8;
	/** No progress towards the portal for this long: they find their way across from where they are. */
	private static final int GIVE_UP_TICKS = 20 * 90;
	/** The far side is not loaded: try again after this long. */
	private static final int DEFER_TICKS = 200;
	/** With a player this close their land keeps running anyway: no roaming ticket needed. */
	private static final double PLAYER_NEAR = 96;

	private final CompanionEntity c;
	private Plan plan = Plan.NONE;
	private Travel.@Nullable Way way;
	private int recheck;
	private int deferred;
	private int noProgress;
	private double bestDist = Double.MAX_VALUE;
	private boolean announced;
	private int landCheck;

	public TravelGoal(CompanionEntity companion) {
		this.c = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (--recheck > 0) {
			return false;
		}
		recheck = 20;
		plan = decide();
		if (plan == Plan.NONE) {
			letLandGo();
		}
		return plan != Plan.NONE;
	}

	@Override
	public boolean canContinueToUse() {
		return plan != Plan.NONE && c.isAlive();
	}

	@Override
	public void start() {
		noProgress = 0;
		deferred = 0;
		bestDist = Double.MAX_VALUE;
		announced = false;
		recheck = 20;
		holdLand();
	}

	@Override
	public void stop() {
		// A fight or falling back interrupts the walk: the roaming ticket stays (their land must keep running to finish
		// the fight), and is let go only once there is nothing left to do (see canUse).
		c.actions().stopWalking();
		plan = Plan.NONE;
		way = null;
		recheck = 0; // look again at once afterwards, before any job gets going in the wrong world
	}

	/** Lets go of the roaming ticket the expedition asked for, once this friend has nowhere to go. */
	private void letLandGo() {
		if (Travel.roaming(c) && !c.isRemoved()) {
			Travel.setRoaming(c, false);
			ChunkLoader.stopRoaming(c);
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	/** What to do now, and which way (see the class description). */
	private Plan decide() {
		if (!c.isTeamMember() || !(c.level() instanceof ServerLevel level)) {
			return Plan.NONE;
		}
		boolean abroad = Travel.abroad(c);
		switch (c.mode()) {
			case FOLLOW -> {
				ServerPlayer leader = c.leader();
				boolean present = leader != null && leader.isAlive() && !leader.isSpectator();
				if (present && leader.level() == level) {
					Travel.clearChase(c);
					return Plan.NONE;
				}
				if (present && FriendsConfig.get().friendsFollowThroughPortals) {
					way = Travel.wayTo(c, leader);
					if (way != null) {
						return Plan.FOLLOW_THROUGH;
					}
				}
				way = abroad ? Travel.arrival(c) : null;
				return abroad ? Plan.WAIT : Plan.NONE;
			}
			case WORK -> {
				if (!abroad) {
					return Plan.NONE;
				}
				way = Travel.wayHome(c);
				return Plan.GO_HOME;
			}
			default -> {
				return Plan.NONE;
			}
		}
	}

	@Override
	public void tick() {
		if (--recheck <= 0) {
			recheck = 20;
			Plan next = decide();
			if (next != plan) {
				plan = next;
				noProgress = 0;
				bestDist = Double.MAX_VALUE;
				c.actions().stopWalking();
				if (plan == Plan.NONE) {
					letLandGo();
					return;
				}
			}
			if (++landCheck >= 5) {
				landCheck = 0;
				holdLand(); // every few seconds: a player may have come or gone
			}
		}
		if (c.tickCount % 100 == 0 && c.mode() == CompanionMode.WORK) {
			snack();
		}
		if (deferred > 0) {
			deferred--;
			return;
		}
		switch (plan) {
			case FOLLOW_THROUGH, GO_HOME -> travel();
			case WAIT -> waitByPortal();
			case NONE -> {
			}
		}
	}

	/** Waits a few blocks from the portal they came in by (or where they are, if they do not know one). */
	private void waitByPortal() {
		Travel.Way w = way;
		if (w == null || c.blockPosition().distSqr(w.portal()) <= 4 * 4) {
			c.actions().stopWalking();
			return;
		}
		c.actions().walkTo(w.portal(), 3.0);
		if (c.actions().isStuck()) {
			c.actions().stopWalking();
			way = null; // cannot get there: wait here
		}
	}

	/** Walks to the portal and goes through it (or, failing that, finds their way across from here). */
	private void travel() {
		ServerLevel level = (ServerLevel) c.level();
		if (plan == Plan.GO_HOME && !announced) {
			announced = true;
			Speech.say(c, Line.PORTAL_HOME);
		}
		Travel.Way w = way;
		if (w == null) {
			if (plan == Plan.GO_HOME && ++noProgress > 100) {
				crossFrom(level, null); // no portal known on this side: they find their way home from here
			}
			return;
		}
		BlockPos portal = w.portal();
		double flat = Math.sqrt(Camp.horizontalDistSqr(c.blockPosition(), portal));
		double dy = Math.abs(c.getY() - portal.getY());
		if (flat <= AT_PORTAL && dy <= 3) {
			crossFrom(level, w);
			return;
		}
		if (c.actions().walkTo(portal, 1.5)) {
			crossFrom(level, w);
			return;
		}
		double dist = flat + dy;
		if (dist < bestDist - 1.0) {
			bestDist = dist;
			noProgress = 0;
		} else {
			noProgress++;
		}
		if (c.actions().isStuck() || noProgress > GIVE_UP_TICKS) {
			if (flat <= NEAR_PORTAL || noProgress > GIVE_UP_TICKS) {
				crossFrom(level, w);
			} else {
				c.actions().stopWalking(); // try another way round next tick
			}
		}
	}

	/**
	 * Goes across now: to the far side of the way (or near the leader when following), or home to the camp when no
	 * way is known. Waits a little when the far side is not loaded.
	 */
	private void crossFrom(ServerLevel level, Travel.@Nullable Way w) {
		var server = level.getServer();
		ServerLevel to = w != null ? Travel.level(server, w.toDim()) : server.getLevel(Travel.homeDimension(server));
		if (to == null) {
			plan = Plan.NONE;
			return;
		}
		BlockPos target = null;
		if (w != null && to.isPositionEntityTicking(w.toPos()) && Travel.safeSpot(to, w.toPos(), 4) != null) {
			target = w.toPos();
		}
		if (target == null && plan == Plan.FOLLOW_THROUGH) {
			ServerPlayer leader = c.leader();
			if (leader != null && leader.level() == to && leader.isAlive()) {
				target = leader.blockPosition();
			}
		}
		if (target == null && plan == Plan.GO_HOME) {
			Optional<BlockPos> camp = Camp.center(to);
			if (camp.isPresent() && to.isPositionEntityTicking(camp.get())) {
				target = camp.get();
			}
		}
		if (target == null) {
			deferred = DEFER_TICKS; // nowhere loaded to come out yet: try again in a while
			return;
		}
		BlockPos here = w != null ? w.portal() : c.blockPosition();
		BlockPos there = w != null ? w.toPos() : target;
		PortalFollow.request(c, to, target, here, there, plan == Plan.FOLLOW_THROUGH ? Line.PORTAL_THROUGH : null);
		deferred = 40; // the crossing happens at the end of the tick; nothing more to do meanwhile
	}

	/** Asks for a roaming ticket while no player is near (so they keep moving), and lets it go once one is. */
	private void holdLand() {
		if (!(c.level() instanceof ServerLevel level)) {
			return;
		}
		boolean playerNear = false;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(c) <= PLAYER_NEAR * PLAYER_NEAR) {
				playerNear = true;
				break;
			}
		}
		if (!playerNear && !Travel.roaming(c)) {
			Travel.setRoaming(c, true);
			if (!ChunkLoader.startRoaming(c, plan == Plan.GO_HOME ? "making their way home through a portal"
				: "making their way to a portal")) {
				Travel.setRoaming(c, false);
			}
		}
	}

	/** A bite to eat on the way: the meal job does not run away from home. */
	private void snack() {
		if (c.needs().get(Needs.Need.HUNGER) >= 25) {
			return;
		}
		ItemStack food = c.backpack().take(CompanionEntity::isEdible, 1);
		if (!food.isEmpty()) {
			String name = food.getHoverName().getString();
			c.eat(food);
			Speech.say(c, Line.ATE, name);
		}
	}
}
