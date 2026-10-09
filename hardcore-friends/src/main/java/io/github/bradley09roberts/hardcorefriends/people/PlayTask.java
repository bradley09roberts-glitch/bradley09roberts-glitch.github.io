package io.github.bradley09roberts.hardcorefriends.people;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Children at play, by day inside the camp. With another child free nearby they play tag (whoever is "it" chases,
 * the other runs off, a touch swaps them over) or hide-and-seek (one counts, the other hides a little way off, then is
 * found); on their own they chase the chickens about (never touching them) or play explorers between a few spots.
 * Fun and company needs fill and the two get to be better friends. Changes no block and harms nothing.
 */
final class PlayTask implements CompanionTask {
	static final String ID = People.JOB_PREFIX + "play";
	private static final double PLAYMATE_RANGE = 24;
	private static final int GAME_TICKS = 20 * 35;
	private static final int COUNT_TICKS = 20 * 5;
	private static final double RUN = 1.25;

	enum Game {
		TAG("tag"), HIDE("hide-and-seek"), CHICKENS("chicken chase"), EXPLORERS("explorers");

		final String word;

		Game(String word) {
			this.word = word;
		}
	}

	/** A game being played: by one child alone, or by two (a starts, b is asked). */
	private static final class Match {
		final Game game;
		final UUID a;
		final @Nullable UUID b;
		/** Tag: who is "it". Hide-and-seek: who seeks. */
		UUID it;
		final long until;
		@Nullable BlockPos hideSpot;
		long tagAt;
		boolean over;

		Match(Game game, UUID a, @Nullable UUID b, UUID it, long until) {
			this.game = game;
			this.a = a;
			this.b = b;
			this.it = it;
			this.until = until;
		}
	}

	private static final Map<UUID, Match> MATCHES = new HashMap<>();

	private @Nullable Match match;
	private @Nullable BlockPos waypoint;
	private int legs;
	private int ticks;

	static void clear() {
		MATCHES.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Match m = match;
		return m == null ? "playing" : "playing " + m.game.word;
	}

	@Override
	public double score(CompanionEntity c) {
		if (!c.isChild() || !(c.level() instanceof ServerLevel level) || Camp.isNight(level) || !Spots.inCamp(c, c.blockPosition())) {
			return 0;
		}
		Match m = MATCHES.get(c.getUUID());
		if (m != null) {
			if (m.over || level.getGameTime() > m.until) {
				MATCHES.remove(c.getUUID(), m);
			} else if (c.getUUID().equals(m.b)) {
				return 62; // asked to play
			}
		}
		return 34 + (100 - c.needs().get(Needs.Need.FUN)) * 0.2;
	}

	@Override
	public boolean start(CompanionEntity c) {
		match = null;
		waypoint = null;
		legs = 0;
		ticks = 0;
		ServerLevel level = (ServerLevel) c.level();
		long until = level.getGameTime() + GAME_TICKS;
		Match asked = MATCHES.get(c.getUUID());
		if (asked != null && !asked.over && c.getUUID().equals(asked.b)) {
			match = asked;
			return true;
		}
		CompanionEntity mate = playmate(c, level);
		Match m;
		if (mate != null) {
			Game game = c.getRandom().nextBoolean() ? Game.TAG : Game.HIDE;
			UUID it = c.getRandom().nextBoolean() ? c.getUUID() : mate.getUUID();
			m = new Match(game, c.getUUID(), mate.getUUID(), it, until);
			if (game == Game.HIDE) {
				CompanionEntity hider = it.equals(c.getUUID()) ? mate : c;
				m.hideSpot = spotAround(hider, hider.blockPosition(), 6, 12);
				if (m.hideSpot == null) {
					return false;
				}
			}
			MATCHES.put(mate.getUUID(), m);
		} else {
			boolean chickens = !level.getEntitiesOfClass(Chicken.class, c.getBoundingBox().inflate(16, 4, 16),
				ch -> ch.isAlive() && Spots.inCamp(c, ch.blockPosition())).isEmpty();
			m = new Match(chickens ? Game.CHICKENS : Game.EXPLORERS, c.getUUID(), null, c.getUUID(), until);
		}
		MATCHES.put(c.getUUID(), m);
		match = m;
		Speech.say(c, Line.CHILD_PLAY, m.game.word);
		return true;
	}

	/** Another child close by who is free to play: awake, at home in the camp, and idling or at a pastime. */
	private static @Nullable CompanionEntity playmate(CompanionEntity c, ServerLevel level) {
		CompanionEntity best = null;
		double bestDist = PLAYMATE_RANGE * PLAYMATE_RANGE;
		for (CompanionEntity other : level.getEntitiesOfClass(CompanionEntity.class, c.getBoundingBox().inflate(PLAYMATE_RANGE, 8, PLAYMATE_RANGE),
			o -> o != c && o.isChild() && o.isAlive() && o.isTeamMember())) {
			double d = other.distanceToSqr(c);
			if (d < bestDist && free(other)) {
				bestDist = d;
				best = other;
			}
		}
		return best;
	}

	private static boolean free(CompanionEntity child) {
		if (MATCHES.containsKey(child.getUUID()) || !Relationships.awake(child) || child.mode() != CompanionMode.WORK
			|| !Spots.inCamp(child, child.blockPosition())) {
			return false;
		}
		String job = Relationships.job(child);
		return job == null || job.equals("common.idle") || job.equals("needs.leisure") || job.equals(ID);
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		Match m = match;
		if (m == null || m.over || !(c.level() instanceof ServerLevel level)) {
			return TaskStatus.SUCCESS;
		}
		ticks++;
		CompanionEntity mate = null;
		if (m.b != null) {
			mate = PeopleEvents.loaded(level.getServer(), c.getUUID().equals(m.a) ? m.b : m.a);
			if (mate == null || mate.level() != level || mate.distanceToSqr(c) > 32 * 32 || mate.isAsleep()) {
				m.over = true;
				return TaskStatus.SUCCESS;
			}
		}
		if (level.getGameTime() > m.until || Camp.isNight(level)) {
			end(c, mate, m);
			return TaskStatus.SUCCESS;
		}
		switch (m.game) {
			case TAG -> tag(c, level, m, mate);
			case HIDE -> {
				if (hide(c, m, mate)) {
					end(c, mate, m);
					return TaskStatus.SUCCESS;
				}
			}
			case CHICKENS -> chickens(c, level);
			case EXPLORERS -> {
				if (explore(c)) {
					end(c, mate, m);
					return TaskStatus.SUCCESS;
				}
			}
		}
		return TaskStatus.RUNNING;
	}

	private void tag(CompanionEntity c, ServerLevel level, Match m, @Nullable CompanionEntity mate) {
		if (mate == null) {
			return;
		}
		long now = level.getGameTime();
		if (m.it.equals(c.getUUID())) {
			if (ticks % 10 == 0) {
				c.getNavigation().moveTo(mate, RUN);
			}
			if (c.distanceToSqr(mate) <= 1.4 * 1.4 && now - m.tagAt > 40) {
				m.it = mate.getUUID();
				m.tagAt = now;
				c.swingArm();
				Speech.say(c, Line.CHILD_PLAY, m.game.word);
			}
		} else if (ticks % 15 == 0) {
			// Run off away from whoever is "it", staying in the camp.
			Vec3 away = c.position().subtract(mate.position()).multiply(1, 0, 1);
			Vec3 dir = away.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : away.normalize();
			BlockPos spot = Spots.standable(level, BlockPos.containing(c.position().add(dir.scale(6))));
			if (spot == null || !Spots.inCamp(c, spot)) {
				spot = spotAround(c, c.homePos(), 2, 8);
			}
			if (spot != null) {
				c.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, RUN);
			}
		}
	}

	/** True once the seeker has found the hider. */
	private boolean hide(CompanionEntity c, Match m, @Nullable CompanionEntity mate) {
		if (mate == null || m.hideSpot == null) {
			return true;
		}
		boolean seeker = m.it.equals(c.getUUID());
		if (ticks < COUNT_TICKS) {
			if (seeker) {
				c.getNavigation().stop();
				c.getLookControl().setLookAt(c.getX(), c.getEyeY() - 2, c.getZ() + 0.1); // eyes covered, counting
			} else {
				c.actions().walkTo(m.hideSpot, 0.8);
			}
			return false;
		}
		if (!seeker) {
			if (!c.actions().walkTo(m.hideSpot, 0.8)) {
				return false;
			}
			c.setShiftKeyDown(true); // crouched down, hiding
			return mate.distanceToSqr(c) <= 2 * 2;
		}
		if (ticks % 10 == 0) {
			c.getNavigation().moveTo(mate, 1.0);
		}
		if (c.distanceToSqr(mate) <= 2 * 2) {
			Speech.say(c, Line.CHILD_PLAY, m.game.word);
			return true;
		}
		return false;
	}

	private void chickens(CompanionEntity c, ServerLevel level) {
		if (ticks % 20 != 1) {
			return;
		}
		List<Chicken> chickens = level.getEntitiesOfClass(Chicken.class, c.getBoundingBox().inflate(16, 4, 16),
			ch -> ch.isAlive() && Spots.inCamp(c, ch.blockPosition()));
		Chicken nearest = null;
		for (Chicken ch : chickens) {
			if (nearest == null || ch.distanceToSqr(c) < nearest.distanceToSqr(c)) {
				nearest = ch;
			}
		}
		if (nearest != null) {
			c.getNavigation().moveTo(nearest, RUN); // chasing them about is the game; they are never touched
		}
	}

	/** True once the explorer has been to a few spots. */
	private boolean explore(CompanionEntity c) {
		if (waypoint == null) {
			waypoint = spotAround(c, c.homePos(), 3, 10);
			if (waypoint == null) {
				return true;
			}
		}
		if (c.actions().walkTo(waypoint, 1.0) || c.actions().isStuck()) {
			waypoint = null;
			return ++legs >= 4;
		}
		return false;
	}

	/** The game is over: both feel better, and the two are better friends. */
	private static void end(CompanionEntity c, @Nullable CompanionEntity mate, Match m) {
		if (m.over) {
			return;
		}
		m.over = true;
		for (CompanionEntity child : mate == null ? new CompanionEntity[] {c} : new CompanionEntity[] {c, mate}) {
			child.needs().add(Needs.Need.FUN, 40);
			child.needs().add(Needs.Need.SOCIAL, mate == null ? 5 : 20);
		}
		if (mate != null && c.level() instanceof ServerLevel level) {
			Relationships.change(level.getServer(), c, mate, 2, 0);
		}
	}

	/** A standable spot in the camp {@code min} to {@code max} blocks from a centre, or null after a few tries. */
	static @Nullable BlockPos spotAround(CompanionEntity c, BlockPos centre, int min, int max) {
		ServerLevel level = (ServerLevel) c.level();
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = c.getRandom().nextDouble() * Math.PI * 2;
			double r = min + c.getRandom().nextDouble() * (max - min);
			BlockPos p = Spots.standable(level, centre.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r)));
			if (p != null && Spots.inCamp(c, p)) {
				return p;
			}
		}
		return null;
	}

	@Override
	public void stop(CompanionEntity c) {
		Match m = match;
		if (m != null) {
			m.over = true; // either child leaving ends the game for both
			MATCHES.remove(c.getUUID(), m);
		}
		c.setShiftKeyDown(false);
		c.getNavigation().stop();
		match = null;
		waypoint = null;
		legs = 0;
		ticks = 0;
	}

	@Override
	public int failureCooldown() {
		return 20 * 20;
	}

	@Override
	public int successCooldown() {
		return 20 * 40;
	}

	@Override
	public int maxTicks() {
		return GAME_TICKS + 20 * 10;
	}
}
