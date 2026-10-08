package io.github.bradley09roberts.hardcorefriends.ai.role.guard;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Aegis's watch. With a player within 48 blocks, Aegis stays beside whoever is most at risk (lowest health, then
 * furthest from camp). Otherwise Aegis walks a ring of eight posts around the camp, and at night keeps watch from the
 * top of the watchtower once one stands. Fighting is left to the combat reflexes, which take over when a threat
 * comes close.
 */
public final class GuardTask implements CompanionTask {
	private static final double PLAYER_RANGE = 48;
	private static final double FOLLOW_START = 6;
	private static final double FOLLOW_STOP = 3.5;
	private static final int RUN_TICKS = 20 * 30;
	private static final int POSTS = 8;
	private static final int LEGS_PER_RUN = 2;
	private static final int TOWER_RECHECK = 20 * 60;
	private static final int CLIMB_LIMIT = 20 * 10;

	private int ticks;
	private int post = -1;
	private int legs;
	private int pause;
	private @Nullable BlockPos postPos;
	private @Nullable ServerPlayer ward;
	private boolean following;

	private Watchtower.@Nullable Lookout lookout;
	private long lookoutCheckedAt = -100_000;
	private long towerUnreachableUntil;
	private int climbTicks;

	@Override
	public String id() {
		return "aegis.guard";
	}

	@Override
	public String describe() {
		return "standing guard";
	}

	@Override
	public double score(CompanionEntity c) {
		return Camp.isNight((ServerLevel) c.level()) ? 70 : 50;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ticks = 0;
		legs = 0;
		pause = 0;
		postPos = null;
		ward = null;
		following = false;
		climbTicks = 0;
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (++ticks > RUN_TICKS) {
			return TaskStatus.SUCCESS;
		}
		if (ticks % 20 == 1) {
			ward = mostAtRisk(c, level);
		}
		if (ward != null && ward.isAlive() && !ward.isRemoved() && ward.level() == level) {
			guardPlayer(c, ward);
			return TaskStatus.RUNNING;
		}
		following = false;
		if (Camp.isNight(level) && level.getGameTime() >= towerUnreachableUntil) {
			if (level.getGameTime() - lookoutCheckedAt > TOWER_RECHECK) {
				lookoutCheckedAt = level.getGameTime();
				lookout = Watchtower.find(level, Camp.data(level.getServer()));
			}
			if (lookout != null) {
				keepLookout(c, level, lookout);
				return TaskStatus.RUNNING;
			}
		}
		return patrol(c, level);
	}

	// --------------------------------------------------------------- players

	/** The nearby player who most needs a guard: lowest health first, then furthest from camp. */
	private static @Nullable ServerPlayer mostAtRisk(CompanionEntity c, ServerLevel level) {
		BlockPos camp = c.homePos();
		ServerPlayer best = null;
		double bestHealth = Double.MAX_VALUE;
		double bestCampDist = -1;
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || !p.isAlive() || p.distanceToSqr(c) > PLAYER_RANGE * PLAYER_RANGE) {
				continue;
			}
			double health = Math.ceil(p.getHealth());
			double campDist = Camp.horizontalDistSqr(p.blockPosition(), camp);
			if (health < bestHealth || (health == bestHealth && campDist > bestCampDist)) {
				best = p;
				bestHealth = health;
				bestCampDist = campDist;
			}
		}
		return best;
	}

	private void guardPlayer(CompanionEntity c, ServerPlayer player) {
		double dist = c.distanceTo(player);
		if (dist > FOLLOW_START) {
			following = true;
		}
		if (following) {
			if (c.actions().walkToEntity(player, FOLLOW_STOP) || dist <= FOLLOW_STOP) {
				following = false;
				c.actions().stopWalking();
			}
			return;
		}
		LivingEntity threat = Threats.nearest(player, 16);
		if (threat != null) {
			c.getLookControl().setLookAt(threat);
		} else if (ticks % 60 < 30) {
			c.getLookControl().setLookAt(player);
		}
	}

	// ----------------------------------------------------------------- patrol

	private TaskStatus patrol(CompanionEntity c, ServerLevel level) {
		if (pause > 0) {
			pause--;
			return TaskStatus.RUNNING;
		}
		if (postPos == null) {
			if (legs >= LEGS_PER_RUN) {
				return TaskStatus.SUCCESS;
			}
			post = post < 0 ? nearestPost(c) : (post + 1) % POSTS;
			postPos = postPosition(c, level, post);
			if (postPos == null) {
				legs++;
				return TaskStatus.RUNNING; // that post is in an unloaded chunk; try the next one
			}
		}
		if (c.actions().walkTo(postPos, 2.0) || c.actions().isStuck()) {
			c.actions().stopWalking();
			postPos = null;
			legs++;
			pause = 40 + c.getRandom().nextInt(40);
		}
		return TaskStatus.RUNNING;
	}

	private static double ringRadius(CompanionEntity c) {
		return Math.max(4, WorldEditGuard.campRadius(c) - 4);
	}

	private static int nearestPost(CompanionEntity c) {
		BlockPos centre = c.homePos();
		double angle = Math.atan2(c.getZ() - (centre.getZ() + 0.5), c.getX() - (centre.getX() + 0.5));
		int index = (int) Math.round(angle / (2 * Math.PI / POSTS));
		return Math.floorMod(index, POSTS);
	}

	/** A standing spot on the patrol ring, on the surface; null if that part of the ring is not loaded. */
	private static @Nullable BlockPos postPosition(CompanionEntity c, ServerLevel level, int index) {
		BlockPos centre = c.homePos();
		double angle = index * 2 * Math.PI / POSTS;
		double r = ringRadius(c);
		int x = centre.getX() + (int) Math.round(Math.cos(angle) * r);
		int z = centre.getZ() + (int) Math.round(Math.sin(angle) * r);
		BlockPos probe = new BlockPos(x, centre.getY(), z);
		if (!level.isLoaded(probe)) {
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (Math.abs(y - centre.getY()) > 24) {
			return null;
		}
		return new BlockPos(x, y, z);
	}

	// ------------------------------------------------------------- watchtower

	private void keepLookout(CompanionEntity c, ServerLevel level, Watchtower.Lookout tower) {
		BlockPos stand = tower.stand();
		Vec3 standCentre = Vec3.atBottomCenterOf(stand);
		boolean onTop = c.getY() >= stand.getY() - 0.1 && flatDistSqr(c.position(), standCentre) <= 2.5 * 2.5;
		if (onTop) {
			c.actions().stopWalking();
			climbTicks = 0;
			LivingEntity threat = Threats.nearest(c, 24);
			if (threat != null) {
				c.getLookControl().setLookAt(threat);
			}
			return;
		}
		BlockPos ladder = tower.ladderFoot();
		if (ladder == null) {
			// No ladder: path straight up (stairs) or give up for tonight.
			if (c.actions().walkTo(stand, 1.0)) {
				return;
			}
			if (c.actions().isStuck()) {
				giveUpTower(level);
			}
			return;
		}
		Vec3 ladderCentre = Vec3.atBottomCenterOf(ladder);
		boolean atLadder = flatDistSqr(c.position(), ladderCentre) <= 0.8 * 0.8 && c.getY() < stand.getY();
		if (!atLadder && climbTicks == 0) {
			c.actions().walkTo(ladder, 0.6);
			if (c.actions().isStuck()) {
				giveUpTower(level);
			}
			return;
		}
		// Climbing: press towards the platform; pushing against the wall while on a ladder lifts a mob up it.
		if (++climbTicks > CLIMB_LIMIT) {
			giveUpTower(level);
			return;
		}
		c.getMoveControl().setWantedPosition(standCentre.x, stand.getY(), standCentre.z, c.actions().speed());
	}

	private void giveUpTower(ServerLevel level) {
		towerUnreachableUntil = level.getGameTime() + 20 * 60 * 2;
		climbTicks = 0;
	}

	@Override
	public void stop(CompanionEntity c) {
		postPos = null;
		ward = null;
		following = false;
		climbTicks = 0;
	}

	@Override
	public int maxTicks() {
		return RUN_TICKS + 20 * 30;
	}

	private static double flatDistSqr(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return dx * dx + dz * dz;
	}
}
