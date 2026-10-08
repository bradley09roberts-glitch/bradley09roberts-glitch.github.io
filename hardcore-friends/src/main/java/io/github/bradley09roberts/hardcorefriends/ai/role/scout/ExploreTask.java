package io.github.bradley09roberts.hardcorefriends.ai.role.scout;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * By day Scout walks to the next waypoint on growing rings around home (radius 24, 40, 56 and so on up to
 * 24 + Scout's roam distance, {@value #POINTS_PER_RING} points per ring) while surveying the area with
 * {@link AreaSurvey}. Each run visits one waypoint; progress is kept in {@link ScoutLog}. Scout stops at dusk.
 */
public final class ExploreTask implements CompanionTask {
	public static final int FIRST_RING = 24;
	public static final int RING_STEP = 16;
	public static final int POINTS_PER_RING = 8;
	private static final double ARRIVE = 3.0;

	private final AreaSurvey survey = new AreaSurvey();
	private @Nullable BlockPos waypoint;

	@Override
	public String id() {
		return "scout.explore";
	}

	@Override
	public String describe() {
		return "scouting the area";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || Camp.isNight(level) || Camp.isDusk(level)) {
			return 0;
		}
		return 45;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		ScoutLog log = ScoutLog.of(Camp.data(level.getServer()));
		waypoint = waypoint(level, c.homePos(), log.ring(), log.point(), c.getBlockY());
		survey.reset();
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (waypoint == null || Camp.isNight(level) || Camp.isDusk(level)) {
			return TaskStatus.SUCCESS;
		}
		survey.tick(c);
		ScoutLog log = ScoutLog.of(Camp.data(level.getServer()));
		if (c.actions().walkTo(waypoint, ARRIVE)) {
			log.advance(rings(), POINTS_PER_RING, true);
			return TaskStatus.SUCCESS;
		}
		if (c.actions().isStuck()) {
			log.advance(rings(), POINTS_PER_RING, false);
			return TaskStatus.FAILURE;
		}
		return TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		waypoint = null;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}

	@Override
	public int successCooldown() {
		return 100;
	}

	@Override
	public int failureCooldown() {
		return 60;
	}

	/** Number of rings: radius 24, 40, 56, ... up to 24 + Scout's roam distance. */
	public static int rings() {
		return FriendId.SCOUT.roam() / RING_STEP + 1;
	}

	public static int ringRadius(int ring) {
		return FIRST_RING + RING_STEP * ring;
	}

	/**
	 * The waypoint for a ring and point: evenly spaced around home, with odd rings rotated half a step so the rings
	 * cover the gaps between each other. Its height is the ground there when loaded, otherwise Scout's own.
	 */
	public static BlockPos waypoint(ServerLevel level, BlockPos home, int ring, int point, int fallbackY) {
		int r = ringRadius(Math.clamp(ring, 0, rings() - 1));
		double angle = (point + (ring % 2) * 0.5) * 2 * Math.PI / POINTS_PER_RING;
		int x = home.getX() + (int) Math.round(Math.cos(angle) * r);
		int z = home.getZ() + (int) Math.round(Math.sin(angle) * r);
		BlockPos column = new BlockPos(x, fallbackY, z);
		int y = level.isLoaded(column) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : fallbackY;
		return new BlockPos(x, y, z);
	}

	/** The survey this task runs, exposed for tests. */
	public AreaSurvey survey() {
		return survey;
	}
}
