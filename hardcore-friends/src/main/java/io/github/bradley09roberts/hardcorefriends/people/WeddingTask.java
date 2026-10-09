package io.github.bradley09roberts.hardcorefriends.people;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;

/**
 * Going to a wedding ({@link Weddings}): the couple walk to their places either side of the venue and face each
 * other; everyone else free at the camp (children too) stands in a ring round them and watches. The couple's own
 * wedding outranks most work (110); for a guest it is a short break (72) that ordinary work in hand finishes first.
 * Changes no block.
 */
final class WeddingTask implements CompanionTask {
	static final String ID = People.JOB_PREFIX + "wedding";
	private static final double COUPLE = 110;
	private static final double GUEST = 72;
	/** Guests come from this far beyond the ring. */
	private static final double GUEST_RANGE = 40;

	private Weddings.@Nullable Ceremony ceremony;
	private @Nullable BlockPos spot;
	private boolean arrived;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Weddings.Ceremony w = ceremony;
		return w != null && w.married ? "celebrating a wedding" : "at a wedding";
	}

	@Override
	public double score(CompanionEntity c) {
		Weddings.Ceremony w = Weddings.active();
		if (w == null || c.level().dimension() != w.dimension || !c.isTeamMember() || c.isAsleep()) {
			return 0;
		}
		if (w.isCouple(c.getUUID())) {
			return COUPLE;
		}
		return c.blockPosition().closerThan(w.venue, GUEST_RANGE) ? GUEST : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ceremony = Weddings.active();
		arrived = false;
		Weddings.Ceremony w = ceremony;
		if (w == null) {
			return false;
		}
		ServerLevel level = (ServerLevel) c.level();
		if (w.isCouple(c.getUUID())) {
			spot = Spots.standable(level, w.spotFor(c.getUUID()));
			if (spot == null) {
				spot = w.venue;
			}
			return true;
		}
		// A place in the ring, spread round by each guest's own number.
		double angle = (c.rosterIndex() * 2.39996) % (2 * Math.PI);
		for (int attempt = 0; attempt < 6; attempt++) {
			double a = angle + attempt * 0.5;
			BlockPos p = w.venue.offset((int) Math.round(Math.cos(a) * Weddings.RING), 0, (int) Math.round(Math.sin(a) * Weddings.RING));
			spot = Spots.standable(level, p);
			if (spot != null) {
				return true;
			}
		}
		return false;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		Weddings.Ceremony w = ceremony;
		BlockPos to = spot;
		if (w == null || to == null || Weddings.active() != w) {
			return TaskStatus.SUCCESS; // over, or put off
		}
		if (!arrived) {
			if (c.actions().walkTo(to, w.isCouple(c.getUUID()) ? 0.8 : 1.5)) {
				arrived = true;
			} else if (c.actions().isStuck()) {
				if (!c.blockPosition().closerThan(w.venue, Weddings.RING + 4)) {
					return TaskStatus.FAILURE;
				}
				arrived = true; // close enough to see
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		if (w.isCouple(c.getUUID())) {
			BlockPos other = w.spotFor(w.a.equals(c.getUUID()) ? w.b : w.a);
			c.getLookControl().setLookAt(Vec3.atCenterOf(other).add(0, 0.6, 0));
		} else {
			c.getLookControl().setLookAt(Vec3.atCenterOf(w.venue).add(0, 0.6, 0));
		}
		if (w.married && c.tickCount % 20 == 0) {
			c.needs().add(Needs.Need.FUN, 1.5);
			c.needs().add(Needs.Need.SOCIAL, 1.5);
		}
		return TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		ceremony = null;
		spot = null;
		arrived = false;
	}

	@Override
	public int failureCooldown() {
		return 20 * 30;
	}

	@Override
	public int successCooldown() {
		return 20 * 30;
	}

	@Override
	public int maxTicks() {
		return 20 * 150;
	}
}
