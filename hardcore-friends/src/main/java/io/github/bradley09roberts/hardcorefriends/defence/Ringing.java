package io.github.bradley09roberts.hardcorefriends.defence;

import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;

/**
 * Who rings the bell. When the alarm wants the bell rung, the nearest friend who is awake, grown up, at work in the
 * village and fit (not falling back, not badly hurt, not on fire) within {@value #RANGE} blocks of the bell is asked;
 * they put down what they were doing (a need such as a meal, or getting unstuck, aside: the scores sort those out) and
 * run to it and ring it ({@link RingBellTask}).
 * Nobody is ever sent to a bell with a creeper within {@value Alarm#CREEPER_CLEARANCE} blocks of it, nor past a creeper
 * on the way (one within {@value #CREEPER_PATH} blocks of them or of the straight way there), and with any other hostile
 * within {@value #CONTESTED} blocks of it only a friend fit to fight ({@link Duty#fighter}) is. Nor is anyone another
 * package keeps off the job (a shopkeeper serving a player). A ringer who has not rung it {@value #TIMEOUT} ticks after
 * being asked (or who is no longer fit to go, or finds a creeper in the way) is let off and someone else asked; with no
 * bell, or nobody to ring it, the alarm is shouted instead by the friend nearest the danger.
 */
final class Ringing {
	private static final double RANGE = 48;
	/** A ringer asked this long ago who has not rung it is let off. */
	private static final int TIMEOUT = 20 * 40;
	/** With a hostile this close to the bell, only a fighter is sent to ring it. */
	private static final double CONTESTED = 8;
	/** Nobody is sent to the bell with a creeper this close to them or to the straight way there. */
	private static final double CREEPER_PATH = 6;

	private Ringing() {
	}

	/** The alarm's bell: ask someone to ring it, keep an eye on whoever was asked. */
	static void tick(ServerLevel level, CampData data, Alarm.State s, long now) {
		if (!s.ringWanted || s.rung) {
			return;
		}
		if (s.ringer != null) {
			CompanionEntity ringer = byId(level, s.ringer);
			if (ringer != null && fit(ringer) && now - s.ringerSince <= TIMEOUT && !creeperNear(level, s.bell)
				&& !creeperOnTheWay(ringer.position(), s.bell, Alarm.creepers(level))) {
				return; // on their way
			}
			s.failedRingers.add(s.ringer);
			s.ringer = null;
		}
		Bells.Bell bell = Bells.main(level);
		if (bell == null || creeperNear(level, bell.pos())) {
			s.ringWanted = false;
			shout(level, s);
			return;
		}
		s.bell = bell.pos();
		Vec3 at = Vec3.atCenterOf(bell.pos());
		boolean contested = hostileNear(level, at);
		List<LivingEntity> creepers = Alarm.creepers(level);
		CompanionEntity best = null;
		double bestDist = RANGE * RANGE;
		for (CompanionEntity c : Companions.in(level)) {
			if (s.failedRingers.contains(c.getUUID()) || !fit(c) || contested && !Duty.fighter(c)) {
				continue; // with monsters by the bell, only someone fit to fight goes to it
			}
			double d = c.distanceToSqr(at);
			if (d < bestDist && mayTakeTheJob(c) && !creeperOnTheWay(c.position(), bell.pos(), creepers)) {
				bestDist = d;
				best = c;
			}
		}
		if (best == null) {
			s.ringWanted = false;
			shout(level, s);
			return;
		}
		s.ringer = best.getUUID();
		s.ringerSince = now;
		CompanionTask current = best.scheduler().current();
		if (current != null && !current.id().equals(RingBellTask.ID) && !current.id().startsWith("needs.")
			&& !current.id().startsWith("navigation.")) {
			best.scheduler().interrupt(); // the bell comes first: heavy work, the watch, taking cover or a post
		}
	}

	/** False if another package keeps this friend off ringing the bell now (a shopkeeper serving a player, say). */
	private static boolean mayTakeTheJob(CompanionEntity c) {
		for (BiPredicate<CompanionEntity, String> filter : TaskScheduler.JOB_FILTERS) {
			if (!filter.test(c, RingBellTask.ID)) {
				return false;
			}
		}
		return true;
	}

	/** The bell was rung: nobody else need go. */
	static void rung(Alarm.State s) {
		s.rung = true;
		s.ringWanted = false;
		s.ringer = null;
	}

	/** The asked ringer could not get there: someone else is asked next time round. */
	static void failed(Alarm.State s, CompanionEntity ringer) {
		if (ringer.getUUID().equals(s.ringer)) {
			s.failedRingers.add(ringer.getUUID());
			s.ringer = null;
		}
	}

	/** Able to run and ring the bell: grown up, awake, at work in the village, and in a fit state to go. */
	static boolean fit(CompanionEntity c) {
		return c.isAlive() && !c.isChild() && !c.isAsleep() && c.mode() == CompanionMode.WORK && !c.isRetreating()
			&& !c.isOnFire() && !c.tooWeakToWork() && c.getHealth() > c.getMaxHealth() * 0.4F && Area.atHome(c);
	}

	/** True if a hostile seen lately is within {@value #CONTESTED} blocks of the bell. */
	private static boolean hostileNear(ServerLevel level, Vec3 bell) {
		for (LivingEntity t : Alarm.present(level)) {
			if (t.distanceToSqr(bell) <= CONTESTED * CONTESTED) {
				return true;
			}
		}
		return false;
	}

	/** True if a creeper seen lately is within {@value Alarm#CREEPER_CLEARANCE} blocks of the bell. */
	static boolean creeperNear(ServerLevel level, @Nullable BlockPos bell) {
		if (bell == null) {
			return false;
		}
		Vec3 at = Vec3.atCenterOf(bell);
		for (LivingEntity creeper : Alarm.creepers(level)) {
			if (creeper.distanceToSqr(at) <= Alarm.CREEPER_CLEARANCE * Alarm.CREEPER_CLEARANCE) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True if going from {@code from} to the bell would take a friend past a creeper: one within {@value #CREEPER_PATH}
	 * blocks of them, or standing near the straight way there, nearer the bell than they are.
	 */
	static boolean creeperOnTheWay(Vec3 from, @Nullable BlockPos bell, List<LivingEntity> creepers) {
		if (bell == null) {
			return false;
		}
		Vec3 to = Vec3.atCenterOf(bell);
		for (LivingEntity creeper : creepers) {
			if (creeper.position().distanceToSqr(from) <= CREEPER_PATH * CREEPER_PATH
				|| Area.inTheWay(creeper.position(), from, to, CREEPER_PATH)) {
				return true;
			}
		}
		return false;
	}

	/** With no bell rung, the alarm is shouted by the friend awake in the village nearest the danger. */
	private static void shout(ServerLevel level, Alarm.State s) {
		Vec3 focus = Alarm.focus(level);
		CompanionEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (CompanionEntity c : Companions.in(level)) {
			if (c.isAsleep() || c.isChild() || c.mode() != CompanionMode.WORK || !Area.atHome(c)) {
				continue;
			}
			double d = focus == null ? 0 : c.distanceToSqr(focus);
			if (d < bestDist) {
				bestDist = d;
				best = c;
			}
		}
		if (best != null) {
			Alarm.say(best, Line.ALARM_BELL, s.cause.danger);
		}
	}

	private static @Nullable CompanionEntity byId(ServerLevel level, UUID id) {
		return level.getEntity(id) instanceof CompanionEntity c ? c : null;
	}
}
