package io.github.bradley09roberts.hardcorefriends.life;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Going to a gathering ({@link Gatherings}): a feast at the square or a funeral at the cemetery. Everyone free in the
 * camp comes, children too: they walk to a place in a ring round the square (or in a half ring in front of the grave),
 * stand together facing the middle or whoever is speaking, and stay until it ends; at a feast children run about
 * between places, playing. A needs job ({@code needs.festival}): time off, which children may take part in as well. It
 * scores {@value #FEAST} for a feast and {@value #FUNERAL} for a funeral (urgent upkeep: the day's ordinary work gives
 * way; danger, a desperate need, the night watch and a building in a hurry do not), and gives way to any monster
 * close by.
 */
final class GatherTask implements CompanionTask {
	static final String ID = "needs.festival";
	static final double FEAST = 78;
	static final double FUNERAL = 85;
	private static final double DANGER = 10;
	private static final int PLAY_EVERY = 20 * 6;

	private Gatherings.@Nullable Gathering joined;
	private @Nullable BlockPos spot;
	private int slot;
	private boolean arrived;
	private int ticks;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Gatherings.Gathering g = joined;
		if (g == null) {
			return "going to a gathering";
		}
		return "at " + g.title();
	}

	@Override
	public double score(CompanionEntity c) {
		Gatherings.Gathering g = Gatherings.activeFor(c);
		if (g == null || !Places.free(c) || c.isRetreating() || c.getTarget() != null) {
			return 0;
		}
		return g.kind == Gatherings.Kind.FUNERAL ? FUNERAL : FEAST;
	}

	@Override
	public boolean start(CompanionEntity c) {
		Gatherings.Gathering g = Gatherings.activeFor(c);
		if (g == null || !(c.level() instanceof ServerLevel level)) {
			return false;
		}
		joined = g;
		slot = Gatherings.slot(g);
		arrived = false;
		ticks = 0;
		spot = place(level, g, slot);
		return spot != null;
	}

	/** This friend's place in the ring: a full ring round the square, a half ring before a grave, wider as more come. */
	private static @Nullable BlockPos place(ServerLevel level, Gatherings.Gathering g, int slot) {
		Direction f = g.facing;
		boolean half = f != null;
		int perRing = half ? 7 : 10;
		double radius = (half ? 3 : 4) + 2 * (slot / perRing);
		BlockPos s = Places.ringSpot(level, g.centre, slot % perRing, perRing, radius, half, half ? f.getStepX() : 0, half ? f.getStepZ() : 0);
		return s != null ? s : Places.standableNear(level, g.centre, 5);
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		Gatherings.Gathering g = Gatherings.activeFor(c);
		if (g == null || g != joined || !(c.level() instanceof ServerLevel level)) {
			return TaskStatus.SUCCESS; // it is over
		}
		ticks++;
		if (ticks % 20 == 0 && Places.hostileNear(level, c.blockPosition(), DANGER)) {
			return TaskStatus.FAILURE; // a monster close by: the reflexes and the fighters see to it first
		}
		BlockPos to = spot;
		if (to == null) {
			return TaskStatus.FAILURE;
		}
		if (!arrived) {
			if (c.actions().walkTo(to, 1.0)) {
				arrived = true;
				Gatherings.arrive(g, c);
			} else if (c.actions().isStuck()) {
				if (c.blockPosition().closerThan(g.centre, 8)) {
					arrived = true; // close enough to be part of it
					Gatherings.arrive(g, c);
				} else {
					return TaskStatus.FAILURE;
				}
			}
			return TaskStatus.RUNNING;
		}
		if (c.isChild() && g.phase == Gatherings.Phase.PARTY && ticks % PLAY_EVERY == 0) {
			// Children run about between places, playing.
			BlockPos next = place(level, g, slot + 1 + c.getRandom().nextInt(9));
			if (next != null) {
				spot = next;
				arrived = false;
				return TaskStatus.RUNNING;
			}
		}
		c.actions().stopWalking();
		if (ticks % 40 == 0) {
			look(c, level, g);
		}
		return TaskStatus.RUNNING;
	}

	/** Looks at the speaker while they speak, otherwise at the middle of the gathering (the grave, the square). */
	private static void look(CompanionEntity c, ServerLevel level, Gatherings.Gathering g) {
		UUID speaker = g.speaker;
		CompanionEntity s = speaker == null || speaker.equals(c.getUUID()) ? null : Places.loaded(level, speaker);
		if (s != null && g.phase == Gatherings.Phase.WORDS) {
			c.getLookControl().setLookAt(s);
			return;
		}
		c.getLookControl().setLookAt(g.centre.getX() + 0.5, g.centre.getY() + 0.8, g.centre.getZ() + 0.5);
	}

	@Override
	public void stop(CompanionEntity c) {
		Gatherings.leave(c);
		joined = null;
		spot = null;
		arrived = false;
	}

	@Override
	public int failureCooldown() {
		return 20 * 15;
	}

	@Override
	public int successCooldown() {
		return 20 * 30;
	}

	@Override
	public int maxTicks() {
		return 20 * 60 * 6;
	}
}
