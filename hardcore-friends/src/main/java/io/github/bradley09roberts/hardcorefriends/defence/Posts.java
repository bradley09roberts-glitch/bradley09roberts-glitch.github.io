package io.github.bradley09roberts.hardcorefriends.defence;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.village.VillagePlan;

/**
 * The village's guard posts, read from the buildings the village has put up ({@code VillagePlan}): the lookout on top
 * of each watchtower ({@code civic:watchtower}'s {@code lookout} spots), each town gate (beside the way through, its
 * {@code gate} spot), and each length of town wall (on the village side of its middle, half way between its two
 * {@code join} ends). Each post has a short patrol: a gate's runs up the street into the village, a wall's along the
 * inside of the wall to its ends; the lookout has none (the guard stays up there). Worked out at most once a minute,
 * or again a few seconds later when a post's spot was not loaded yet (the world just opened), so the guards are not
 * stood down for a minute for want of a look.
 *
 * <p>When the alarm rings, fighters go to the post nearest the danger that is not right on top of it, or, before the
 * village has any, to the bell or the camp centre ({@link #alarmPost}); never within {@value Alarm#CREEPER_CLEARANCE}
 * blocks of a creeper or past one on the way.
 */
final class Posts {
	/** One post: what it is ("the watchtower lookout"), where the guard stands, and the patrol from it. */
	record Post(String what, BlockPos stand, boolean high, List<BlockPos> patrol) {
	}

	private static final int REFRESH = 20 * 60;
	/** A look taken while a post's spot was not loaded is taken again this soon. */
	private static final int RETRY = 20 * 2;
	/** A fighter is not sent to a post closer than this to the danger itself. */
	private static final double FRONT = 4;
	/** Nor to one whose straight way there passes this close to a creeper. */
	private static final double CREEPER_PATH = 6;

	private static List<Post> cache = List.of();
	/** "Never" is half of {@code Long.MIN_VALUE}, so {@code now - cachedAt} cannot overflow. */
	private static long cachedAt = Long.MIN_VALUE / 2;
	/** Set by {@link #find} when a post's spot was not loaded, so the list may be short. */
	private static boolean partial;

	private Posts() {
	}

	/** The guard posts (empty before the village has a watchtower, a gate or walls). */
	static List<Post> guardPosts(ServerLevel level) {
		long now = level.getGameTime();
		if (now - cachedAt < REFRESH && now >= cachedAt) {
			return cache;
		}
		cachedAt = now;
		partial = false;
		cache = find(level);
		if (partial) {
			cachedAt = now - REFRESH + RETRY; // a post's spot was not loaded yet: look again soon
		}
		return cache;
	}

	private static List<Post> find(ServerLevel level) {
		MinecraftServer server = level.getServer();
		CampData data = Camp.data(server);
		List<Post> posts = new ArrayList<>();
		if (!Camp.isCampLevel(level, data)) {
			return posts;
		}
		BlockPos centre = data.campPos().orElseThrow();
		for (VillagePlan.Building tower : VillagePlan.buildingsOfKind(server, "civic:watchtower")) {
			for (BlockPos p : tower.marker("lookout")) {
				partial |= !level.isLoaded(p);
				if (level.isLoaded(p) && Spots.isStandable(level, p)) {
					posts.add(new Post("the watchtower lookout", p.immutable(), true, List.of()));
					break;
				}
			}
		}
		for (VillagePlan.Building gate : VillagePlan.buildingsOfKind(server, "civic:gate")) {
			BlockPos way = gate.first("gate").orElse(null);
			List<BlockPos> ends = gate.marker("join");
			if (way == null || ends.size() < 2) {
				continue;
			}
			partial |= !level.isLoaded(way);
			int[] along = axis(ends.get(0), ends.get(1));
			int[] in = inward(way, along, centre);
			BlockPos stand = standable(level, way.offset(2 * along[0], 0, 2 * along[1]));
			if (stand == null) {
				stand = standable(level, way.offset(2 * in[0], 0, 2 * in[1]));
			}
			if (stand == null) {
				continue;
			}
			List<BlockPos> patrol = new ArrayList<>();
			for (int step : new int[] {6, 12}) {
				BlockPos p = standable(level, way.offset(step * in[0], 0, step * in[1]));
				if (p != null) {
					patrol.add(p);
				}
			}
			posts.add(new Post("the town gate", stand, false, List.copyOf(patrol)));
		}
		for (VillagePlan.Building wall : VillagePlan.buildingsOfKind(server, "civic:wall")) {
			List<BlockPos> ends = wall.marker("join");
			if (ends.size() < 2) {
				continue;
			}
			BlockPos a = ends.get(0);
			BlockPos b = ends.get(1);
			BlockPos mid = new BlockPos((a.getX() + b.getX()) / 2, (a.getY() + b.getY()) / 2, (a.getZ() + b.getZ()) / 2);
			partial |= !level.isLoaded(mid);
			int[] in = inward(mid, axis(a, b), centre);
			BlockPos stand = standable(level, mid.offset(2 * in[0], 0, 2 * in[1]));
			if (stand == null) {
				continue;
			}
			List<BlockPos> patrol = new ArrayList<>();
			for (BlockPos end : new BlockPos[] {a, b}) {
				BlockPos p = standable(level, end.offset(2 * in[0], 0, 2 * in[1]));
				if (p != null) {
					patrol.add(p);
				}
			}
			posts.add(new Post("the town wall", stand, false, List.copyOf(patrol)));
		}
		return List.copyOf(posts);
	}

	/** The unit step (x, z) along the line from a to b, on the longer axis. */
	private static int[] axis(BlockPos a, BlockPos b) {
		int dx = b.getX() - a.getX();
		int dz = b.getZ() - a.getZ();
		return Math.abs(dx) >= Math.abs(dz) ? new int[] {Integer.signum(dx), 0} : new int[] {0, Integer.signum(dz)};
	}

	/** The unit step (x, z) across {@code along}, on the side facing the camp centre (into the village). */
	private static int[] inward(BlockPos from, int[] along, BlockPos centre) {
		int[] across = {along[1], along[0]};
		double plus = Camp.horizontalDistSqr(from.offset(across[0] * 4, 0, across[1] * 4), centre);
		double minus = Camp.horizontalDistSqr(from.offset(-across[0] * 4, 0, -across[1] * 4), centre);
		return plus <= minus ? across : new int[] {-across[0], -across[1]};
	}

	private static @Nullable BlockPos standable(ServerLevel level, BlockPos p) {
		return level.isLoaded(p) ? Spots.standable(level, p) : null;
	}

	/** A spot to stand one or two blocks beside a block (a bell, the campfire), or null. */
	static @Nullable BlockPos beside(ServerLevel level, BlockPos pos) {
		for (int distance = 1; distance <= 2; distance++) {
			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				BlockPos p = standable(level, pos.offset(d[0] * distance, 0, d[1] * distance));
				if (p != null) {
					return p;
				}
			}
		}
		return null;
	}

	/**
	 * Where a fighter goes when the alarm rings: of the ground-level guard posts, the bells and the camp centre, the one
	 * nearest the danger that is at least {@value #FRONT} blocks from it and not within
	 * {@value Alarm#CREEPER_CLEARANCE} of a creeper; with no danger in sight (a player rang the bell), the nearest of
	 * them to the fighter. Null when nowhere is safe to go: they hold where they are.
	 */
	static @Nullable BlockPos alarmPost(ServerLevel level, LivingEntity fighter) {
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return null;
		}
		List<BlockPos> candidates = new ArrayList<>();
		for (Post p : guardPosts(level)) {
			if (!p.high()) {
				candidates.add(p.stand());
			}
		}
		for (Bells.Bell bell : Bells.known(level)) {
			BlockPos near = beside(level, bell.pos());
			if (near != null) {
				candidates.add(near);
			}
		}
		BlockPos centre = beside(level, data.campPos().orElseThrow());
		if (centre != null) {
			candidates.add(centre);
		}
		List<LivingEntity> creepers = Alarm.creepers(level);
		Vec3 focus = Alarm.focus(level);
		Vec3 from = focus != null ? focus : fighter.position();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos c : candidates) {
			Vec3 at = Vec3.atBottomCenterOf(c);
			double d = at.distanceToSqr(from);
			if (focus != null && d < FRONT * FRONT || d >= bestDist || nearCreeper(at, creepers)
				|| creeperInTheWay(fighter.position(), at, creepers)) {
				continue;
			}
			bestDist = d;
			best = c;
		}
		return best;
	}

	/** True if a creeper stands near the straight way from the fighter to the post: they are never sent past one. */
	private static boolean creeperInTheWay(Vec3 from, Vec3 to, List<LivingEntity> creepers) {
		for (LivingEntity creeper : creepers) {
			if (Area.inTheWay(creeper.position(), from, to, CREEPER_PATH)) {
				return true;
			}
		}
		return false;
	}

	private static boolean nearCreeper(Vec3 at, List<LivingEntity> creepers) {
		for (LivingEntity creeper : creepers) {
			if (creeper.position().distanceToSqr(at) <= Alarm.CREEPER_CLEARANCE * Alarm.CREEPER_CLEARANCE) {
				return true;
			}
		}
		return false;
	}

	static void clear() {
		cache = List.of();
		cachedAt = Long.MIN_VALUE / 2;
		partial = false;
	}
}
