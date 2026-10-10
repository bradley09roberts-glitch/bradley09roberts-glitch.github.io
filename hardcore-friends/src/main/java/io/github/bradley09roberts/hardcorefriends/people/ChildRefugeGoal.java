package io.github.bradley09roberts.hardcorefriends.people;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Reach;
import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * A child who sees a monster within {@value #SEE} blocks that could get at them runs to a grown-up: a parent nearby
 * first, then a fighter or a player, otherwise home: into their own house in the village, or without one to the
 * resting place (the cabin); a child already indoors at the camp stays put. It sits below the danger reflexes (falling
 * back when hurt, dodging a monster right beside them), which keep running as for anyone, and above every job. A child
 * never fights back.
 *
 * <p><b>Which monsters.</b> One right beside them ({@value #BESIDE} blocks, wall or no wall, as for a sleeper), or one
 * in plain sight that is going for them or that a whole path leads to ({@link Reach}). A zombie outside the cabin
 * wall, a drowned in the river or a skeleton in a cave under the camp does not send a child running. A sleeping child
 * is left to the sleep job, which already wakes a sleeper for a monster that could get at them and not for one that
 * could not.
 *
 * <p><b>Whom to run to.</b> Only a grown-up who is safe to run to ({@link #safeToRunTo}): about level with the child
 * (never down into a mine), not falling back hurt, not fighting anything and not on watch, further from the monster
 * than the child is (never towards it) and, after dark, under a roof: a child never opens the cabin door and runs out
 * into the night to a parent on watch or Aegis at the camp edge. A child already indoors after dark does not go out
 * at all: only a grown-up in the same room ({@value #INDOORS_RANGE} blocks, nothing in between) will do, so a parent
 * under another roof (visiting a neighbour, asleep in the cabin) is not run to across the village in the dark. Home,
 * too, only when it lies further from the monster than the child is.
 *
 * <p>Held for at least {@value #MIN_TICKS} ticks and at most {@value #MAX_TICKS}; after the longest hold it rests
 * {@value #COOLDOWN} ticks, so a monster lingering in sight cannot keep a child from eating or sleeping for good.
 */
final class ChildRefugeGoal extends Goal {
	/** A monster this close sends a child running... */
	private static final double SEE = 12;
	/** ...and they keep going until nothing that could get at them is this close... */
	private static final double CLEAR = 16;
	/** ...and stay at least this long. */
	private static final int MIN_TICKS = 60;
	private static final int MAX_TICKS = 20 * 20;
	/** After the longest hold, the goal waits this long before it starts again. */
	private static final int COOLDOWN = 20 * 10;
	/**
	 * With nowhere better to go (already home or indoors, or home lies towards the monster), the goal looks again after
	 * this long.
	 */
	private static final int NOWHERE_TO_GO = 40;
	/** A monster this close counts whatever lies between (the sleep job's own rule for waking). */
	private static final double BESIDE = 3;
	/** Parents this far away are run to... */
	private static final double PARENT_RANGE = 32;
	/** ...fighters and players this far. */
	private static final double PROTECTOR_RANGE = 24;
	/** Indoors after dark, only a grown-up this close and in plain sight (the same room) is run to. */
	private static final double INDOORS_RANGE = 8;
	/** A grown-up more than this far above or below the child is not run to (down a mine, up a cliff). */
	private static final double LEVEL = 6;
	private static final double RUN = 1.35;

	private final CompanionEntity child;
	private @Nullable LivingEntity threat;
	private @Nullable Entity refuge;
	private @Nullable BlockPos home;
	private int ticks;
	/** Game time before which the goal does not start (after the longest hold, or with nowhere to go). */
	private long calmUntil;

	ChildRefugeGoal(CompanionEntity child) {
		this.child = child;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!child.isChild() || !child.isTeamMember() || child.isAsleep() || (child.tickCount + child.getId()) % 10 != 0
			|| child.level().getGameTime() < calmUntil) {
			return false;
		}
		threat = danger(SEE);
		if (threat == null) {
			return false;
		}
		pickRefuge();
		if (refuge == null && home == null) {
			// Already home, indoors at the camp, or home lies towards the monster: they stay put and carry on (a
			// monster right beside them is the danger reflexes' to dodge).
			threat = null;
			calmUntil = child.level().getGameTime() + NOWHERE_TO_GO;
			return false;
		}
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		if (!child.isChild() || ticks > MAX_TICKS) {
			return false;
		}
		if (ticks >= MIN_TICKS && refuge == null && home == null) {
			return false; // home, or indoors: safe enough
		}
		return ticks < MIN_TICKS || threat != null;
	}

	@Override
	public void start() {
		ticks = 0;
		Speech.say(child, Line.CHILD_SCARED);
		if (child.scheduler().current() != null) {
			child.scheduler().interrupt(); // whatever game or lesson it was, it stops
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ticks++;
		if (ticks % 10 == 1) {
			if (ticks > 1) {
				LivingEntity seen = danger(CLEAR);
				if (seen != null || ticks >= MIN_TICKS) {
					threat = seen; // early on, the last one seen still counts for where to run
				}
			}
			pickRefuge();
			Entity to = refuge;
			if (to != null) {
				if (child.distanceToSqr(to) > 2.5 * 2.5) {
					child.getNavigation().moveTo(to, RUN);
				} else {
					child.getNavigation().stop();
				}
			} else if (home != null) {
				child.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, RUN);
			} else {
				child.getNavigation().stop();
			}
		}
		LivingEntity seen = threat;
		if (seen != null && seen.isAlive()) {
			child.getLookControl().setLookAt(seen);
		}
	}

	/**
	 * The nearest monster within {@code radius} that could get at this child: one right beside them, or one in plain
	 * sight that is going for them or that a whole path leads to. Nearest first, so paths are only worked out until one
	 * is found, and {@link Reach} remembers each answer a while.
	 */
	private @Nullable LivingEntity danger(double radius) {
		List<LivingEntity> near = new ArrayList<>(Threats.around(child, radius));
		near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(child)));
		for (LivingEntity e : near) {
			double d = e.distanceToSqr(child);
			if (d >= radius * radius) {
				break;
			}
			if (d <= BESIDE * BESIDE) {
				return e;
			}
			if (child.hasLineOfSight(e) && (Threats.isTargeting(e, child) || Reach.check(child, e) != Reach.Answer.NO)) {
				return e;
			}
		}
		return null;
	}

	/**
	 * Somewhere safe to run: a parent within {@value #PARENT_RANGE} blocks first, else a fighter or a player within
	 * {@value #PROTECTOR_RANGE}, each only if {@link #safeToRunTo}. Indoors at the camp (under a roof, up at ground
	 * level) after dark, only one in the same room. Failing those, home ({@link #homeToRunTo}), unless the child is
	 * already indoors at the camp, where they stay put rather than go out.
	 */
	private void pickRefuge() {
		refuge = null;
		home = null;
		if (!(child.level() instanceof ServerLevel level)) {
			return;
		}
		boolean dark = Camp.isNight(level);
		BlockPos here = child.blockPosition();
		boolean indoors = !level.canSeeSky(here.above()) && Spots.inCamp(child, here) && !Children.belowGround(child, level, here);
		boolean stayIn = dark && indoors;
		double parentRange = stayIn ? INDOORS_RANGE : PARENT_RANGE;
		double protectorRange = stayIn ? INDOORS_RANGE : PROTECTOR_RANGE;
		double best = parentRange * parentRange;
		for (UUID id : PeopleData.get(level.getServer()).person(child.getUUID()).map(p -> p.parents).orElse(List.of())) {
			CompanionEntity parent = PeopleEvents.loaded(level.getServer(), id);
			if (parent != null && parent.level() == level && parent.distanceToSqr(child) < best
				&& safeToRunTo(level, parent, dark, stayIn)) {
				best = parent.distanceToSqr(child);
				refuge = parent;
			}
		}
		if (refuge == null) {
			best = protectorRange * protectorRange;
			for (CompanionEntity c : Companions.near(level, child.getBoundingBox().inflate(protectorRange))) {
				double d = c.distanceToSqr(child);
				if (c != child && c.isFighter() && d < best && safeToRunTo(level, c, dark, stayIn)) {
					best = d;
					refuge = c;
				}
			}
		}
		if (refuge == null) {
			refuge = level.getNearestPlayer(child.getX(), child.getY(), child.getZ(), protectorRange,
				e -> e instanceof Player p && !p.isSpectator() && safeToRunTo(level, p, dark, stayIn));
		}
		if (refuge == null && !indoors) {
			home = homeToRunTo(level, here);
		}
	}

	/**
	 * Home, with no grown-up to run to: into their own house in the village (beside their bed, or just inside the door)
	 * if they have one, otherwise the resting place (the cabin), and the other of the two if the first lies towards the
	 * monster. Never one that is no further from the monster than the child is (the same rule as for a grown-up), so a
	 * child never runs past it to get home; null then, or when they are already there (a step away, and under the same
	 * sky: just outside the wall of an indoor spot is not there yet).
	 */
	private @Nullable BlockPos homeToRunTo(ServerLevel level, BlockPos here) {
		BlockPos house = ChildHomeTask.houseSpot(child);
		BlockPos rest = child.restPos();
		for (BlockPos to : house != null ? new BlockPos[] {house, rest} : new BlockPos[] {rest}) {
			if (here.closerThan(to, 3) && level.canSeeSky(here.above()) == level.canSeeSky(to.above())) {
				return null; // already home
			}
			LivingEntity danger = threat;
			if (danger == null || danger.distanceToSqr(Vec3.atBottomCenterOf(to)) > child.distanceToSqr(danger) + 9) {
				return to;
			}
		}
		return null;
	}

	/**
	 * True if this grown-up is safe to run to: alive and about level with the child (never down a mine or up a cliff),
	 * not falling back hurt, not fighting anything or keeping watch, further from the monster than the child is (never
	 * towards it, the same rule as for anyone fleeing to a protector), and after dark under a roof (never out into the
	 * night); for a child indoors after dark ({@code stayIn}), in plain sight of them too (the same room).
	 */
	private boolean safeToRunTo(ServerLevel level, LivingEntity grownUp, boolean dark, boolean stayIn) {
		if (!grownUp.isAlive() || Math.abs(grownUp.getY() - child.getY()) > LEVEL) {
			return false;
		}
		if (grownUp instanceof CompanionEntity c
			&& (c.isChild() || c.isRetreating() || c.getTarget() != null || NightWatch.isOnWatch(c))) {
			return false;
		}
		if (dark && level.canSeeSky(grownUp.blockPosition().above())) {
			return false;
		}
		LivingEntity danger = threat;
		if (danger != null && grownUp.distanceToSqr(danger) <= child.distanceToSqr(danger) + 9) {
			return false;
		}
		return !stayIn || child.hasLineOfSight(grownUp);
	}

	@Override
	public void stop() {
		if (ticks > MAX_TICKS) {
			calmUntil = child.level().getGameTime() + COOLDOWN;
		}
		child.getNavigation().stop();
		threat = null;
		refuge = null;
		home = null;
		ticks = 0;
	}
}
