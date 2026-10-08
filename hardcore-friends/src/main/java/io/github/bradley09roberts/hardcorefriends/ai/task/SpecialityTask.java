package io.github.bradley09roberts.hardcorefriends.ai.task;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speciality;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Wraps one kind of work so any friend can do it, specialists first. The rule, for a job scoring {@code s}:
 * <ul>
 * <li><b>Their own speciality:</b> {@code s}, unchanged. A specialist does their own work exactly as they would
 * alone, so whenever there is some, they do it.</li>
 * <li><b>Standing in</b> (nobody of that speciality is working in this world: dead, not recruited, following a
 * player or told to stay): {@code keenness × min(s, 90) / 2}, where keenness is 0.8 for the friend's interest and
 * 0.6 for anything else ({@link Speciality#affinity}). That is at most 36 for their interest and 27 for other work:
 * below a friend's own main work (40 and up) and nearly all their secondary work, but above idling. So standing in is
 * what friends do with their free time, their interest first, and the more the camp needs a job (its score carries
 * the camp's need for it) the sooner it gets picked up.</li>
 * <li><b>Lending a hand</b> (the specialist is around): a quarter of the stand-in score, at most 9. Helpers only
 * pick it up when they have nothing else at all to do, and the specialist, who scores it in full, gets there
 * first.</li>
 * <li><b>Personal jobs</b> ({@link #PERSONAL}: dealing with what this friend carries, such as taking building
 * materials to a stuck builder or smelting carried ore) are nobody else's to do, so they score {@code s × keenness}
 * whoever is around: a delivery to a builder who is stuck stays urgent.</li>
 * </ul>
 * Jobs that work on one shared thing ({@link #EXCLUSIVE}: a building job, the mine, the quarry, the farm's
 * layout...) are run by one friend at a time; the others score them 0 while someone is on it. When the specialist
 * comes back to work and finds someone else on their shared job, the other friend hands it over at once.
 *
 * <p>When a friend starts work outside their speciality they may say so ({@link Line#HELPING_OUT}).
 */
public final class SpecialityTask implements CompanionTask {
	/** Above this a job's score counts no further for a stand-in: the camp's need still shows, within bounds. */
	public static final double STAND_IN_BASE_CAP = 90;
	/** How much of their keenness-weighted score a stand-in gives a job. */
	public static final double STAND_IN_SHARE = 0.5;
	/** How much of the stand-in score a helper gives a job while its specialist is around. */
	public static final double HELP_SHARE = 0.25;

	/**
	 * Jobs that work on one shared thing and must never run twice at once: one builder per building job (so never
	 * two on one site), one farm layout and one tiller (the farmland cap), one digger in the mine, one quarry worker,
	 * one feller (the part-felled tree is one record), one path layer, one fencer, one gardener (tree spacing), one
	 * explorer (the scout log), and one friend at the furnace.
	 */
	public static final Set<String> EXCLUSIVE = Set.of(
		"oak.build", "oak.repair", "spark.contraption", "fern.farm_plot", "fern.till", "flint.dig_mine", "rowan.quarry",
		"rowan.chop", "terra.paths", "terra.fence", "terra.plant", "scout.explore", "flint.smelt", "flint.collect_smelted");

	/** Jobs about what the friend themself carries: nobody else can do them, so whoever carries the load does. */
	public static final Set<String> PERSONAL = Set.of("rowan.deliver", "flint.smelt");

	/** The idle job: a specialist doing only this is free to take their work back. */
	private static final String IDLE = "common.idle";

	/** Who is running each exclusive job right now. */
	private static final Map<String, UUID> RUNNING = new HashMap<>();
	/** Who last started each exclusive job, so others can find the friend who has been doing it (the builder). */
	private static final Map<String, UUID> LAST = new HashMap<>();
	/** How long a specialist's request to have their shared job handed back stands (keeping others off it), in ticks. */
	private static final int HANDOVER_TICKS = 60;
	/** Specialists waiting for their shared job back from whoever holds it: job id to who asked, and when. */
	private static final Map<String, Handover> HANDOVER = new HashMap<>();

	private record Handover(UUID to, long at) {
	}

	/** The working specialists of each kind of work, refreshed once per game tick. */
	private static final Map<Role, CompanionEntity> ON_HAND = new EnumMap<>(Role.class);
	private static long onHandAt = Long.MIN_VALUE;

	private final CompanionTask inner;
	private final Role role;
	private final boolean exclusive;
	private final boolean personal;

	public SpecialityTask(CompanionTask inner, Role role) {
		this.inner = inner;
		this.role = role;
		this.exclusive = EXCLUSIVE.contains(inner.id());
		this.personal = PERSONAL.contains(inner.id());
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

	/** True when this is the friend's own speciality. */
	private boolean own(CompanionEntity c) {
		return c.friendId().role() == role;
	}

	@Override
	public double score(CompanionEntity c) {
		if (exclusive) {
			Optional<CompanionEntity> holder = holder(c);
			if (holder.isPresent()) {
				askForHandover(c, holder.get());
				return 0;
			}
			if (handedToAnother(c)) {
				return 0;
			}
		}
		double base = inner.score(c);
		if (base <= 0 || own(c)) {
			return Math.max(0, base);
		}
		double keen = Speciality.affinity(c.friendId(), role);
		if (personal) {
			return base * keen;
		}
		double standIn = keen * Math.min(base, STAND_IN_BASE_CAP) * STAND_IN_SHARE;
		return specialistOnHand(c) ? standIn * HELP_SHARE : standIn;
	}

	/**
	 * The most this job could score for this friend right now, without looking at the world: the scheduler skips the
	 * (sometimes costly) scoring of jobs that could not win anyway. Infinite for their own work.
	 */
	public double ceiling(CompanionEntity c) {
		if (own(c) || personal) {
			return Double.POSITIVE_INFINITY;
		}
		if (exclusive && (holder(c).isPresent() || handedToAnother(c))) {
			return 0;
		}
		double standIn = Speciality.affinity(c.friendId(), role) * STAND_IN_BASE_CAP * STAND_IN_SHARE;
		return specialistOnHand(c) ? standIn * HELP_SHARE : standIn;
	}

	/** True when a friend of this job's speciality (not this one) is working in the same world. */
	private boolean specialistOnHand(CompanionEntity c) {
		CompanionEntity specialist = onHand(c.level().getGameTime()).get(role);
		return specialist != null && specialist != c && specialist.level() == c.level();
	}

	/** The friends in WORK mode, by speciality; worked out once per game tick for everyone. */
	private static Map<Role, CompanionEntity> onHand(long gameTime) {
		if (onHandAt != gameTime) {
			onHandAt = gameTime;
			ON_HAND.clear();
			for (CompanionEntity friend : Companions.all()) {
				if (friend.mode() == CompanionMode.WORK) {
					ON_HAND.put(friend.friendId().role(), friend);
				}
			}
		}
		return ON_HAND;
	}

	/** The friend other than this one running this exclusive job, if they are still alive and loaded. */
	private Optional<CompanionEntity> holder(CompanionEntity c) {
		UUID holder = RUNNING.get(inner.id());
		if (holder == null || holder.equals(c.getUUID())) {
			return Optional.empty();
		}
		Optional<CompanionEntity> found = find(holder);
		if (found.isEmpty()) {
			RUNNING.remove(inner.id()); // they died or left: the job is free again
		}
		return found;
	}

	/** True while this exclusive job is being handed back to its specialist, who is someone else. */
	private boolean handedToAnother(CompanionEntity c) {
		Handover h = HANDOVER.get(inner.id());
		return h != null && !h.to().equals(c.getUUID()) && c.level().getGameTime() - h.at() < HANDOVER_TICKS;
	}

	/**
	 * A specialist with nothing on finds a helper or stand-in on their own shared job: they ask for it back if they
	 * have work there.
	 */
	private void askForHandover(CompanionEntity c, CompanionEntity holder) {
		if (!own(c) || holder.friendId().role() == role) {
			return;
		}
		CompanionTask doing = c.scheduler().current();
		if ((doing == null || doing.id().equals(IDLE)) && inner.score(c) > 0) {
			HANDOVER.put(inner.id(), new Handover(c.getUUID(), c.level().getGameTime()));
		}
	}

	private static Optional<CompanionEntity> find(UUID id) {
		for (CompanionEntity other : Companions.all()) {
			if (other.getUUID().equals(id)) {
				return Optional.of(other);
			}
		}
		return Optional.empty();
	}

	@Override
	public boolean start(CompanionEntity c) {
		if (exclusive && (holder(c).isPresent() || handedToAnother(c))) {
			return false;
		}
		boolean started = inner.start(c);
		if (started && exclusive) {
			RUNNING.put(inner.id(), c.getUUID());
			LAST.put(inner.id(), c.getUUID());
			HANDOVER.remove(inner.id());
		}
		if (started && !own(c)) {
			Speech.say(c, Line.HELPING_OUT, inner.describe());
		}
		return started;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (exclusive && !own(c) && handedToAnother(c)) {
			return TaskStatus.FAILURE; // the specialist is back for it: hand it over (and leave it be for a while)
		}
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

	/** The living, loaded friend running this exclusive job right now, if any. */
	public static Optional<CompanionEntity> runner(String jobId) {
		UUID holder = RUNNING.get(jobId);
		return holder == null ? Optional.empty() : find(holder);
	}

	/** The living, loaded friend who last started this exclusive job (running it now or not), if any. */
	public static Optional<CompanionEntity> lastRunner(String jobId) {
		Optional<CompanionEntity> now = runner(jobId);
		if (now.isPresent()) {
			return now;
		}
		UUID last = LAST.get(jobId);
		return last == null ? Optional.empty() : find(last);
	}

	/** Forgets every claim (tests and server restarts). */
	public static void clearClaims() {
		RUNNING.clear();
		LAST.clear();
		HANDOVER.clear();
		onHandAt = Long.MIN_VALUE;
	}
}
