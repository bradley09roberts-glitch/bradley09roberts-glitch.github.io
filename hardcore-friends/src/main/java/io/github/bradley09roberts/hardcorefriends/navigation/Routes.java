package io.github.bradley09roberts.hardcorefriends.navigation;

import java.util.Map;
import java.util.WeakHashMap;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Whether a friend can walk to a place, for jobs choosing what to do: a whole path must lead there (as
 * {@code ai/goal/Reach} asks of a mob), and a walk from the surface to a place on the surface must not go underground
 * on the way. A job that checks its target with {@link #reachable} before choosing it never sends a friend into a
 * cave to fetch something that is out in the open.
 *
 * <p>Paths cost something to work out, so each answer is remembered per friend and place: a way there for
 * {@value #KEEP_YES} ticks, none for {@value #KEEP_NO}. Do not call it from a job's score for many places at once:
 * check the one place the job is about to choose.
 */
public final class Routes {
	private static final int KEEP_YES = 100;
	private static final int KEEP_NO = 400;
	/** Per friend: packed place to (until game time << 1 | reachable). */
	private static final Map<CompanionEntity, Long2LongOpenHashMap> KNOWN = new WeakHashMap<>();

	private Routes() {
	}

	/**
	 * True when the friend can walk to within {@code reach} blocks of {@code target} along a whole path, not ducking
	 * underground on a walk between two places on the surface. False in mid-air (no path can be worked out then; ask
	 * again shortly) and for a place that is not loaded.
	 */
	public static boolean reachable(CompanionEntity c, BlockPos target, int reach) {
		if (!(c.level() instanceof ServerLevel level) || !level.isLoaded(target)) {
			return false;
		}
		long now = level.getGameTime();
		Long2LongOpenHashMap known = KNOWN.computeIfAbsent(c, k -> new Long2LongOpenHashMap());
		long key = target.asLong();
		if (known.containsKey(key)) {
			long entry = known.get(key);
			if (now < entry >> 1) {
				return (entry & 1) == 1;
			}
		}
		if (!c.onGround() && !c.isInLiquid() && !c.isPassenger()) {
			return false;
		}
		Path path = c.getNavigation().createPath(target, Math.max(0, reach));
		boolean ok = path != null && path.canReach() && !throughCave(level, c, path, target);
		if (known.size() > 64) {
			known.values().removeIf(e -> e >> 1 <= now);
		}
		known.put(key, ((now + (ok ? KEEP_YES : KEEP_NO)) << 1) | (ok ? 1 : 0));
		return ok;
	}

	/** True when a walk between two places on the surface goes underground somewhere along this path. */
	private static boolean throughCave(ServerLevel level, CompanionEntity c, Path path, BlockPos target) {
		if (!Terrain.caveAware(level) || Terrain.underground(level, c.blockPosition()) || Terrain.undergroundTarget(level, target)) {
			return false;
		}
		for (int i = 0; i < path.getNodeCount(); i++) {
			if (Terrain.underground(level, path.getNodePos(i))) {
				return true;
			}
		}
		return false;
	}

	/** Forgets every answer (a server starting or stopping). */
	static void clear() {
		KNOWN.clear();
	}
}
