package io.github.bradley09roberts.hardcorefriends.ai.goal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.pathfinder.Path;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Whether a friend can walk all the way to a mob. Vanilla path finding hands back the best partial path when the mob
 * cannot be reached (a zombie inside a fence ring, a skeleton on a ledge, a spider on a roof), so "a path exists" says
 * nothing: only a path that {@linkplain Path#canReach() reaches} the mob counts. Paths are costly to work out, so each
 * answer is remembered for a while per friend and mob: a way there for {@value #KEEP_REACHABLE} ticks, none for
 * {@value #KEEP_UNREACHABLE}.
 */
public final class Reach {
	/** The answer to "can this friend get there?". */
	public enum Answer {
		/** A whole path leads to it. */
		YES,
		/** Only a partial path: it is out of reach (for now). */
		NO,
		/** The friend is in mid-air, so no path can be worked out this tick; ask again shortly. */
		UNKNOWN
	}

	private static final int KEEP_REACHABLE = 40;
	private static final int KEEP_UNREACHABLE = 200;
	/** Per friend: mob id to {until game time, 1 reachable / 0 not}. Weak, so unloaded friends are forgotten. */
	private static final Map<CompanionEntity, Map<UUID, long[]>> CACHE = new WeakHashMap<>();

	private Reach() {
	}

	/** The remembered answer for this friend and mob, or null when it would have to be worked out. */
	public static @Nullable Answer known(CompanionEntity c, LivingEntity target) {
		Map<UUID, long[]> known = CACHE.get(c);
		long[] entry = known == null ? null : known.get(target.getUUID());
		if (entry == null || c.level().getGameTime() >= entry[0]) {
			return null;
		}
		return entry[1] == 1 ? Answer.YES : Answer.NO;
	}

	/** Whether a whole path leads from the friend to the mob (cached; see the class description). */
	public static Answer check(CompanionEntity c, LivingEntity target) {
		if (target.level() != c.level() || !target.isAlive()) {
			return Answer.NO;
		}
		long now = c.level().getGameTime();
		Map<UUID, long[]> known = CACHE.computeIfAbsent(c, k -> new HashMap<>());
		long[] entry = known.get(target.getUUID());
		if (entry != null && now < entry[0]) {
			return entry[1] == 1 ? Answer.YES : Answer.NO;
		}
		if (!c.onGround() && !c.isInLiquid() && !c.isPassenger()) {
			return Answer.UNKNOWN; // mid-jump (or not yet landed) no path can be worked out at all
		}
		Path path = c.getNavigation().createPath(target, 1);
		boolean ok = path != null && path.canReach();
		if (known.size() > 32) {
			known.values().removeIf(e -> e[0] <= now);
		}
		known.put(target.getUUID(), new long[] {now + (ok ? KEEP_REACHABLE : KEEP_UNREACHABLE), ok ? 1 : 0});
		return ok ? Answer.YES : Answer.NO;
	}

	/** Forgets every answer: a server starting or stopping. */
	public static void clear() {
		CACHE.clear();
	}
}
