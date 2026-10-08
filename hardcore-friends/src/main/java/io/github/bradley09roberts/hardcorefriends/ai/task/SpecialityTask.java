package io.github.bradley09roberts.hardcorefriends.ai.task;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speciality;

/**
 * Wraps one kind of work so any friend can do it, specialists first. A friend's score for the job is multiplied by
 * how keen they are on that kind of work ({@link Speciality#affinity}), and a non-specialist holds back by
 * {@value #NON_SPECIALIST_PENALTY} points so that the specialist, who wants it more, usually gets there first.
 *
 * <p>Some jobs work on one shared thing (a building's site, the mine, the quarry, the farm's layout): only one friend
 * may run those at a time, and the others leave them alone while someone is on it.
 */
public final class SpecialityTask implements CompanionTask {
	/** Points a non-specialist's score drops by, so the specialist goes first when both are free. */
	public static final double NON_SPECIALIST_PENALTY = 8;

	/** Jobs that work on one shared thing and must never run twice at once. */
	public static final Set<String> EXCLUSIVE = Set.of(
		"oak.build", "oak.repair", "spark.contraption", "fern.farm_plot", "flint.dig_mine", "rowan.quarry",
		"terra.paths", "terra.fence", "scout.explore", "flint.smelt", "flint.collect_smelted");

	/** Who is running each exclusive job right now. */
	private static final Map<String, UUID> RUNNING = new HashMap<>();

	private final CompanionTask inner;
	private final Role role;
	private final boolean exclusive;

	public SpecialityTask(CompanionTask inner, Role role) {
		this.inner = inner;
		this.role = role;
		this.exclusive = EXCLUSIVE.contains(inner.id());
	}

	public CompanionTask inner() {
		return inner;
	}

	public Role role() {
		return role;
	}

	@Override
	public String id() {
		return inner.id();
	}

	@Override
	public String describe() {
		return inner.describe();
	}

	@Override
	public double score(CompanionEntity c) {
		if (exclusive && takenByAnother(c)) {
			return 0;
		}
		double base = inner.score(c);
		if (base <= 0) {
			return 0;
		}
		double affinity = Speciality.affinity(c.friendId(), role);
		if (affinity >= Speciality.OWN) {
			return base;
		}
		return Math.max(0.0, base * affinity - NON_SPECIALIST_PENALTY);
	}

	/** Someone else, alive and loaded, is running this exclusive job. */
	private boolean takenByAnother(CompanionEntity c) {
		UUID holder = RUNNING.get(inner.id());
		if (holder == null || holder.equals(c.getUUID())) {
			return false;
		}
		for (CompanionEntity other : Companions.all()) {
			if (other.getUUID().equals(holder) && other.isAlive() && !other.isRemoved()) {
				return true;
			}
		}
		RUNNING.remove(inner.id()); // they died or left: the job is free again
		return false;
	}

	@Override
	public boolean start(CompanionEntity c) {
		if (exclusive && takenByAnother(c)) {
			return false;
		}
		boolean started = inner.start(c);
		if (started && exclusive) {
			RUNNING.put(inner.id(), c.getUUID());
		}
		return started;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		return inner.tick(c);
	}

	@Override
	public void stop(CompanionEntity c) {
		try {
			inner.stop(c);
		} finally {
			if (exclusive) {
				RUNNING.remove(inner.id(), c.getUUID());
			}
		}
	}

	@Override
	public int failureCooldown() {
		return inner.failureCooldown();
	}

	@Override
	public int successCooldown() {
		return inner.successCooldown();
	}

	@Override
	public int maxTicks() {
		return inner.maxTicks();
	}

	/** Forgets every claim (tests and server restarts). */
	public static void clearClaims() {
		RUNNING.clear();
	}
}
