package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Out of the dragon's breath. In the End, a friend standing in (or right beside) a cloud of the dragon's breath moves
 * well clear of it, to firm ground away from the island's edge. A reflex, like getting away from a creeper: it
 * outranks fighting and following. Not while up a pillar to a cage (stepping off would be a long fall).
 */
public class EndHazardGoal extends Goal {
	/** How far beyond a cloud's edge counts as too close. */
	private static final double MARGIN = 2.0;
	/** How far away a friend goes to get clear. */
	private static final int CLEAR = 8;

	private final CompanionEntity c;
	private @Nullable AreaEffectCloud cloud;
	private @Nullable BlockPos refuge;
	private int ticks;

	public EndHazardGoal(CompanionEntity companion) {
		this.c = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (c.level().dimension() != Level.END || (c.tickCount + c.getId()) % 5 != 0 || !c.isTeamMember() || !c.onGround()) {
			return false;
		}
		DragonFight.Climb climb = DragonFight.climb();
		if (climb != null && climb.friend().equals(c.getUUID())) {
			return false;
		}
		cloud = breathNear();
		if (cloud == null) {
			return false;
		}
		refuge = refuge(cloud);
		return refuge != null;
	}

	@Override
	public boolean canContinueToUse() {
		AreaEffectCloud near = cloud;
		return refuge != null && ticks < 80 && near != null && near.isAlive() && tooClose(near);
	}

	@Override
	public void start() {
		ticks = 0;
		BlockPos to = refuge;
		if (to != null) {
			c.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.4);
		}
	}

	@Override
	public void tick() {
		ticks++;
		BlockPos to = refuge;
		if (to != null && c.getNavigation().isDone()) {
			c.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.4);
		}
	}

	@Override
	public void stop() {
		c.getNavigation().stop();
		cloud = null;
		refuge = null;
	}

	/** A cloud of the dragon's breath this friend is in or right beside, if any. */
	private @Nullable AreaEffectCloud breathNear() {
		AABB box = c.getBoundingBox().inflate(10, 3, 10);
		for (AreaEffectCloud e : c.level().getEntitiesOfClass(AreaEffectCloud.class, box, AreaEffectCloud::isAlive)) {
			if (e.getOwner() instanceof EnderDragon && tooClose(e)) {
				return e;
			}
		}
		return null;
	}

	private boolean tooClose(AreaEffectCloud e) {
		double dx = c.getX() - e.getX();
		double dz = c.getZ() - e.getZ();
		double r = e.getRadius() + MARGIN;
		return dx * dx + dz * dz <= r * r && Math.abs(c.getY() - e.getY()) < 4;
	}

	/** Firm ground {@value #CLEAR} or more blocks from the cloud, away from it, not near the island's edge. */
	private @Nullable BlockPos refuge(AreaEffectCloud e) {
		ServerLevel level = (ServerLevel) c.level();
		Vec3 away = c.position().subtract(e.position());
		double base = Math.atan2(away.z, away.x);
		double distance = e.getRadius() + CLEAR;
		for (int i = 0; i < 8; i++) {
			double angle = base + (i % 2 == 0 ? 1 : -1) * (i / 2) * Math.PI / 6;
			int x = (int) Math.floor(e.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(e.getZ() + Math.sin(angle) * distance);
			if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
				continue;
			}
			BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (Math.abs(feet.getY() - c.getY()) <= 4 && Travel.standable(level, feet) && !DragonFight.nearEdge(level, feet)) {
				return feet;
			}
		}
		return null;
	}
}
