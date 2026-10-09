package io.github.bradley09roberts.hardcorefriends.navigation;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Friends sprint like players: on a walk with more than {@value #START_AT} blocks of path left (to work, home, on a
 * trip, catching up with their leader, running to help), and whenever they are running away or hurrying after a
 * sprinting leader ({@link #hurry}). They use the vanilla sprint (the shared sprint flag, so players see it, the 30%
 * speed bonus and the dust kicked up) and stop {@value #STOP_AT} blocks short of where they are going, in water (they
 * swim), when hungry (hunger {@value #MIN_HUNGER} or less), sneaking, asleep or carried, and, unless running from
 * something, beside a long drop or near lava, where they stand or a few steps ahead (hurrying after a sprinting leader
 * along a bridge over the void or a lava lake's shore, they walk). Running costs a little more hunger. Children sprint
 * too, but tire after a few seconds and must get their breath back.
 */
public final class Sprint {
	/** A walk with more path than this left (in blocks) is worth a sprint. */
	public static final double START_AT = 12;
	/** Once sprinting, they slow to a walk this close to the end. */
	static final double STOP_AT = 5;
	/** Hurrying (running away, keeping up), they sprint until this close. */
	static final double HURRY_STOP_AT = 3;
	/** At or below this hunger a friend does not sprint (as a player cannot when starving). */
	public static final double MIN_HUNGER = 25;
	/**
	 * Extra hunger a second of sprinting costs: an ordinary working second costs about 0.014, so a run costs about
	 * two thirds as much again (a whole day's work is about 15.5; ten minutes of running about 6 more).
	 */
	static final double HUNGER_PER_SECOND = 0.01;
	/** A child can sprint this many ticks before tiring. */
	private static final int CHILD_STAMINA = 200;
	/** A tired child sprints again once this much breath is back. */
	private static final int CHILD_RECOVERED = 120;
	/** A long run (more path than this) may get a cheerful word. */
	private static final double LONG_RUN = 24;
	/** How many spots of the path ahead are looked at for a drop or lava before running on (a run covers two between looks). */
	private static final int LOOK_AHEAD = 3;

	private static final class State {
		long hurryUntil;
		boolean fleeing;
		int stamina = CHILD_STAMINA;
		boolean tired;
	}

	private static final Map<CompanionEntity, State> STATES = new WeakHashMap<>();

	private Sprint() {
	}

	private static State state(CompanionEntity c) {
		return STATES.computeIfAbsent(c, k -> new State());
	}

	/**
	 * Asks the friend to hurry for the next {@code ticks}: they sprint on any path longer than a few blocks.
	 * {@code fleeing} is for running away from danger (the stuck watcher leaves a friend alone while they run).
	 */
	public static void hurry(CompanionEntity c, int ticks, boolean fleeing) {
		State s = state(c);
		s.hurryUntil = c.level().getGameTime() + ticks;
		s.fleeing = fleeing;
	}

	/** True while the friend is running away from something (asked through {@link #hurry}). */
	public static boolean fleeing(CompanionEntity c) {
		State s = STATES.get(c);
		return s != null && s.fleeing && c.level().getGameTime() < s.hurryUntil;
	}

	/** Forgets everything (a server starting or stopping). */
	static void clear() {
		STATES.clear();
	}

	/** A {@code CompanionEvents.TICK} listener. */
	static void tick(CompanionEntity c, ServerLevel level) {
		State s = state(c);
		if (c.isChild()) {
			if (c.isSprinting()) {
				if (--s.stamina <= 0) {
					s.tired = true;
				}
			} else if (s.stamina < CHILD_STAMINA && c.tickCount % 2 == 0) {
				s.stamina++;
				if (s.stamina >= CHILD_RECOVERED) {
					s.tired = false;
				}
			}
		}
		if (c.tickCount % 5 != 0) {
			return;
		}
		long now = level.getGameTime();
		boolean hurrying = now < s.hurryUntil;
		double left = pathLeft(c);
		// Keeping up with a leader is no reason to run along a cliff or past lava; only running from something is.
		boolean want = mayRun(c, s) && left > (hurrying ? HURRY_STOP_AT : c.isSprinting() ? STOP_AT : START_AT)
			&& (hurrying && s.fleeing || carefulGround(c, level));
		if (want != c.isSprinting()) {
			c.setSprinting(want);
			if (want && left > LONG_RUN && !hurrying) {
				Speech.say(c, Line.SPRINTING);
			}
		}
		if (c.isSprinting() && c.tickCount % 20 == 0 && c.isTeamMember()) {
			c.needs().add(Needs.Need.HUNGER, -HUNGER_PER_SECOND);
		}
	}

	private static boolean mayRun(CompanionEntity c, State s) {
		if (!c.isAlive() || c.isPassenger() || c.isInWater() || c.isInLava() || c.isShiftKeyDown() || c.isAsleep()
			|| c.isSleeping() || c.isLeashed()) {
			return false;
		}
		if (c.isChild() && s.tired) {
			return false;
		}
		return !c.isTeamMember() || c.needs().get(Needs.Need.HUNGER) > MIN_HUNGER;
	}

	/**
	 * True unless the friend stands beside a long drop or near lava, or the next {@value #LOOK_AHEAD} spots of their path
	 * do: a sprinting jump there could go wrong. Looked at afresh each time (not the senses' reading, which can be half a
	 * second old), as a run covers a couple of blocks between two looks.
	 */
	private static boolean carefulGround(CompanionEntity c, ServerLevel level) {
		if (riskyFooting(level, c.blockPosition())) {
			return false;
		}
		Path path = c.getNavigation().getPath();
		if (path != null && !path.isDone()) {
			int end = Math.min(path.getNodeCount(), path.getNextNodeIndex() + LOOK_AHEAD);
			for (int i = path.getNextNodeIndex(); i < end; i++) {
				if (riskyFooting(level, path.getNodePos(i))) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean riskyFooting(ServerLevel level, BlockPos feet) {
		return Senses.nearDrop(level, feet) || Terrain.nearLava(level, feet);
	}

	/** How many blocks of path are left (0 with no path): along the path, from where the friend stands. */
	static double pathLeft(CompanionEntity c) {
		PathNavigation nav = c.getNavigation();
		Path path = nav.getPath();
		if (path == null || path.isDone()) {
			return 0;
		}
		double left = 0;
		Vec3 at = c.position();
		for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
			Node node = path.getNode(i);
			Vec3 next = new Vec3(node.x + 0.5, node.y, node.z + 0.5);
			left += at.distanceTo(next);
			at = next;
			if (left > LONG_RUN * 2) {
				break; // far enough to know
			}
		}
		return left;
	}
}
