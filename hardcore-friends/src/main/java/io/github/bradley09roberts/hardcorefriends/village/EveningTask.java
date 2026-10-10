package io.github.bradley09roberts.hardcorefriends.village;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * The village evening: from the end of the afternoon (time of day {@value #FROM}) until nightfall, a grown-up of the
 * village puts the day's work down and goes home (or, without a home of their own yet, to the square by the well),
 * to be with their family and neighbours; now and then a friend with a home asks someone round for the evening, and
 * the guest comes to theirs. Company there fills the social need on its own (people within a few blocks of each other).
 * At nightfall the sleep job takes over: everyone is already home, a step from their own bed.
 *
 * <p>A needs job ({@code needs.*}), so it counts as time off. It scores {@value #SCORE}: urgent upkeep, so ordinary
 * work (below 45) gives way to it, but building in a hurry, the night watch, a hungry friend's meal and every danger
 * reflex come first. Children have their own early night (the people package's).
 */
final class EveningTask implements CompanionTask {
	static final String ID = "needs.evening";
	static final long FROM = 11000;
	private static final double SCORE = 70;
	/** How likely a friend with a home is to ask someone round. */
	private static final double INVITE_CHANCE = 0.3;
	private static final double INVITE_RANGE = 32;
	private static final double REACH = 1.5;

	/** An invitation for this evening: whose home, until when. */
	private record Invite(UUID host, String homeKey, long until) {
	}

	/** Guests invited round this evening. */
	private static final Map<UUID, Invite> INVITES = new HashMap<>();

	private @Nullable BlockPos spot;
	private @Nullable String visiting;
	private boolean arrived;
	private int stillTicks;

	static void clear() {
		INVITES.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		if (visiting != null) {
			return arrived ? "visiting for the evening" : "going round to visit";
		}
		return arrived ? "spending the evening at home" : "going home for the evening";
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.isChild() || !(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		long time = Camp.timeOfDay(level);
		if (time < FROM || time >= 13500 || Camp.isNightTime(level) || level.dimensionType().hasFixedTime()) {
			return 0;
		}
		if (!Routine.inVillage(c) || NightWatch.isOnWatch(c) || c.tooWeakToWork()) {
			return 0;
		}
		return SCORE;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		arrived = false;
		stillTicks = 0;
		visiting = null;
		spot = null;
		if (!retarget(c, level)) {
			return false;
		}
		if (visiting == null && Routine.home(c).isPresent()) {
			Speech.say(c, Line.GOING_HOME);
			if (c.getRandom().nextDouble() < INVITE_CHANCE) {
				invite(c, level);
			}
		}
		return spot != null;
	}

	/** Chooses where to go: a home they were invited to, else their own, else the square. */
	private boolean retarget(CompanionEntity c, ServerLevel level) {
		long now = level.getGameTime();
		Invite invite = INVITES.get(c.getUUID());
		if (invite != null && now < invite.until()) {
			Optional<VillageData.Plot> host = VillageData.get(level.getServer()).plotBySite(invite.homeKey());
			BlockPos s = host.filter(VillageData.Plot::standing).map(p -> Routine.spotIn(level, p, "sit", "inside", "door")).orElse(null);
			if (s != null) {
				spot = s;
				visiting = invite.homeKey();
				arrived = false;
				return true;
			}
		}
		INVITES.remove(c.getUUID());
		Optional<VillageData.Plot> home = Routine.home(c);
		BlockPos s = home.map(p -> Routine.spotIn(level, p, "inside", "sit", "door")).orElse(null);
		if (s == null) {
			s = Routine.square(level, c);
		}
		spot = s;
		return s != null;
	}

	/** Asks the nearest grown-up from another household, at work and free, round for the evening. */
	private static void invite(CompanionEntity host, ServerLevel level) {
		Optional<VillageData.Plot> home = Routine.home(host);
		if (home.isEmpty()) {
			return;
		}
		CompanionEntity best = null;
		double bestDist = INVITE_RANGE * INVITE_RANGE;
		for (CompanionEntity other : Companions.in(level)) {
			if (other == host || other.isChild() || other.mode() != CompanionMode.WORK || home.get().residents.containsKey(other.getUUID())
				|| INVITES.containsKey(other.getUUID()) || NightWatch.isOnWatch(other)) {
				continue;
			}
			double d = other.distanceToSqr(host);
			if (d < bestDist) {
				bestDist = d;
				best = other;
			}
		}
		if (best != null) {
			INVITES.put(best.getUUID(), new Invite(host.getUUID(), home.get().siteKey, level.getGameTime() + 2400));
			Speech.say(host, Line.INVITE_OVER, best.displayName());
		}
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (Camp.isNightTime(level) || Camp.timeOfDay(level) >= 13500) {
			return TaskStatus.SUCCESS; // bedtime: the sleep job takes over
		}
		if (visiting == null && INVITES.containsKey(c.getUUID()) && !arrived) {
			retarget(c, level); // asked round on the way home
		}
		BlockPos to = spot;
		if (to == null) {
			return TaskStatus.FAILURE;
		}
		if (!arrived) {
			if (c.actions().walkTo(to, REACH)) {
				arrived = true;
			} else if (c.actions().isStuck()) {
				return c.blockPosition().closerThan(to, 4) ? arrivedHere() : TaskStatus.FAILURE;
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		if (++stillTicks % 80 == 0) {
			lookAtCompany(c, level);
		}
		return TaskStatus.RUNNING;
	}

	private TaskStatus arrivedHere() {
		arrived = true;
		return TaskStatus.RUNNING;
	}

	/** Turns to whoever is close by (family, the guest, a player), or looks about. */
	private static void lookAtCompany(CompanionEntity c, ServerLevel level) {
		for (CompanionEntity other : Companions.in(level)) {
			if (other != c && other.distanceToSqr(c) < 36) {
				c.getLookControl().setLookAt(other);
				return;
			}
		}
		Player player = level.getNearestPlayer(c, 6);
		if (player != null && !player.isSpectator()) {
			c.getLookControl().setLookAt(player);
			return;
		}
		c.getLookControl().setLookAt(c.getX() + c.getRandom().nextInt(7) - 3, c.getEyeY(), c.getZ() + c.getRandom().nextInt(7) - 3);
	}

	@Override
	public void stop(CompanionEntity c) {
		if (visiting != null && arrived) {
			INVITES.remove(c.getUUID()); // the visit is over
		}
		spot = null;
		visiting = null;
		arrived = false;
	}

	@Override
	public int failureCooldown() {
		return 20 * 20;
	}

	@Override
	public int successCooldown() {
		return 20 * 60;
	}

	@Override
	public int maxTicks() {
		return 20 * 150;
	}
}
