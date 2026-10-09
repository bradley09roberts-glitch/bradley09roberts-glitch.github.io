package io.github.bradley09roberts.hardcorefriends.navigation;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Out of the water: swim to the best shore {@link Ways#shore} finds (quick to reach, towards where the friend was going,
 * across the current rather than with it, and leading somewhere), working against the current so it does not carry
 * them off. The water's own push is cancelled and a little pull towards the shore added, about what a player swimming
 * hard manages. Done once they stand on dry ground; given up after {@value #GIVE_UP} ticks.
 */
final class ShorePlan implements Plan {
	private static final int GIVE_UP = 600;
	/** How much the water pushes a body each tick (vanilla), cancelled while swimming out. */
	private static final double WATER_PUSH = 0.014;
	private static final double SWIM_PULL = 0.012;

	private final @Nullable BlockPos towards;
	private @Nullable BlockPos landing;
	private int ticks;
	private int repath;

	ShorePlan(@Nullable BlockPos towards) {
		this.towards = towards;
	}

	@Override
	public Kind kind() {
		return Kind.SHORE;
	}

	@Override
	public boolean start(CompanionEntity c, ServerLevel level) {
		landing = Ways.shore(level, c.blockPosition(), towards, Senses.read(c).current(), 16, 900);
		if (landing == null) {
			return false;
		}
		Speech.say(c, Line.SWIMMING_OUT);
		return true;
	}

	@Override
	public Status tick(CompanionEntity c, ServerLevel level) {
		BlockPos to = landing;
		if (to == null || ++ticks > GIVE_UP) {
			return Status.FAILED;
		}
		if (!c.isInWater() && c.onGround()) {
			return Status.DONE;
		}
		if (!Terrain.standable(level, to)) {
			return Status.FAILED; // the landing has gone (a block placed, the water risen): look again
		}
		if (--repath <= 0 || c.getNavigation().isDone()) {
			repath = 20;
			if (!c.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.0)) {
				c.getMoveControl().setWantedPosition(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.0);
			}
		}
		if (c.isInWater()) {
			Vec3 push = Vec3.ZERO;
			Vec3 flow = Terrain.current(level, c.blockPosition());
			if (flow.lengthSqr() > 1.0E-6) {
				push = push.add(flow.normalize().scale(-WATER_PUSH)); // swim across the current, not with it
			}
			Vec3 toShore = new Vec3(to.getX() + 0.5 - c.getX(), 0, to.getZ() + 0.5 - c.getZ());
			if (toShore.lengthSqr() > 0.25) {
				push = push.add(toShore.normalize().scale(SWIM_PULL));
			}
			c.addDeltaMovement(push);
			c.getLookControl().setLookAt(Vec3.atCenterOf(to));
		}
		return Status.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.getNavigation().stop();
	}
}
