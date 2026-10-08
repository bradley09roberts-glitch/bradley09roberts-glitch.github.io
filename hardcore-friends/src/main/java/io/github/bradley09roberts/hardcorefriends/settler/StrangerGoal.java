package io.github.bradley09roberts.hardcorefriends.settler;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;

/**
 * A stranger's everyday life, while they are not on the team: by day they potter about within a short walk of their
 * home spot (a village's meeting place, their survivor camp, or the edge of the team's camp for a traveller), looking
 * round; at night they go to their night spot (by a bed in a village, inside their tent at a camp) or home, and keep
 * still. Someone who has strayed too far walks back. When a player stands close they stop to talk.
 *
 * <p>Only moves them about: they never touch a block. Danger comes first, because the reflexes (falling back, getting
 * away from creepers, fighting back) have a higher priority, and so does any fight they are in. A traveller who has
 * outstayed their day walks away from the camp instead (see {@link SettlerEvents}).
 */
final class StrangerGoal extends Goal {
	/** How far a stranger may stray from home by day before walking back. */
	static final double TETHER = 16;
	/** How far from home they choose a spot to wander to. */
	private static final int WANDER = 8;
	private static final double CLOSE = 4;
	private final CompanionEntity c;
	private int nextWander;
	private int nextPath;
	/** Counts goal ticks: the goal selector ticks this goal every other game tick, on odd or even ones by entity. */
	private int beat;

	StrangerGoal(CompanionEntity companion) {
		this.c = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return c.mode() == CompanionMode.STRANGER && c.getTarget() == null && !c.isRetreating() && c.level() instanceof ServerLevel;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		nextPath = 0;
	}

	@Override
	public void stop() {
		c.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (++beat < 5 || !(c.level() instanceof ServerLevel level)) {
			return; // about twice a second is plenty for pottering about
		}
		beat = 0;
		CompoundTag tag = Strangers.state(c);
		if (tag.getBooleanOr(Strangers.LEAVING, false)) {
			leave(level);
			return;
		}
		BlockPos home = c.homePos();
		boolean night = Camp.isNight(level);
		BlockPos anchor = home;
		if (night) {
			BlockPos spot = nightSpot(tag);
			if (spot != null && spot.distSqr(home) <= 40 * 40) {
				anchor = spot;
			}
		}
		double away = Camp.horizontalDistSqr(c.blockPosition(), anchor);
		// A traveller still on their way in walks straight up to the camp's edge before pottering about.
		boolean onTheirWay = Personas.Origin.ROAD.key().equals(tag.getStringOr(Strangers.ORIGIN, ""))
			&& !tag.getBooleanOr(Strangers.ARRIVED, false);
		double tether = onTheirWay ? 3 : TETHER;
		Player near = level.getNearestPlayer(c, CLOSE);
		if (near != null && !near.isSpectator() && away <= TETHER * TETHER) {
			c.getNavigation().stop(); // someone wants a word: stand and talk
			return;
		}
		if (night) {
			if (away > 2.25) {
				walkTo(anchor, 0.8);
			} else {
				c.getNavigation().stop();
			}
			return;
		}
		if (away > tether * tether) {
			walkTo(anchor, 0.9);
			return;
		}
		if (c.getNavigation().isDone() && c.tickCount >= nextWander) {
			nextWander = c.tickCount + 100 + c.getRandom().nextInt(200);
			Vec3 spot = LandRandomPos.getPos(c, WANDER, 4);
			if (spot == null || Camp.horizontalDistSqr(BlockPos.containing(spot), home) > (double) (WANDER + 2) * (WANDER + 2)) {
				spot = LandRandomPos.getPosTowards(c, WANDER, 4, Vec3.atBottomCenterOf(home));
			}
			if (spot != null) {
				c.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.6);
			}
		}
	}

	/** A traveller on their way out: keeps walking away from the camp until nobody is watching (then they go). */
	private void leave(ServerLevel level) {
		if (c.tickCount < nextPath) {
			return;
		}
		if (!c.getNavigation().isDone()) {
			nextPath = c.tickCount + 20; // still on their way: look again shortly
			return;
		}
		nextPath = c.tickCount + 40; // at most one new path every two seconds, even when none can be found
		Vec3 from = Camp.center(level).map(Vec3::atBottomCenterOf).orElse(Vec3.atBottomCenterOf(c.homePos()));
		Vec3 away = LandRandomPos.getPosAway(c, 16, 6, from);
		if (away != null) {
			c.getNavigation().moveTo(away.x, away.y, away.z, 0.9);
		}
	}

	/** Heads for a spot, working the way out again at most every two seconds (so an unreachable spot costs little). */
	private void walkTo(BlockPos target, double speed) {
		if (c.tickCount >= nextPath) {
			nextPath = c.tickCount + 40;
			c.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
		}
	}

	private static @Nullable BlockPos nightSpot(CompoundTag tag) {
		return tag.getLong(Strangers.NIGHT_SPOT).map(BlockPos::of).orElse(null);
	}
}
