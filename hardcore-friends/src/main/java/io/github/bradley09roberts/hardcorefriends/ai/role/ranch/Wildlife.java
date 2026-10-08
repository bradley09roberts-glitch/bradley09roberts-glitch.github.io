package io.github.bradley09roberts.hardcorefriends.ai.role.ranch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.ai.role.TeamCache;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * The wild animals around the camp, and the rules that keep the player's own animals safe from the friends.
 *
 * <p>One scan for the whole team, at most every {@value #SCAN_INTERVAL} ticks: the cows, pigs, sheep, chickens and
 * rabbits in the camp and the gathering ring around it (no further than {@value #MAX_RING} blocks beyond the camp).
 * Checks that read many blocks (is it near something a player built, is it inside a player's fences) are made at most
 * once per animal per scan, and only for animals that pass every cheap check first.
 *
 * <p>An animal counts as somebody's, and is never led away or hunted, when it has a name, is tamed or owned, is on a
 * lead, rides or is ridden, stands within {@value #PLAYER_BUILD_GAP} blocks of anything a player built
 * ({@link WorldEditGuard#looksPlayerBuilt}: fences, gates and every other build marker), or stands inside a ring of
 * a player's fences or walls.
 */
public final class Wildlife {
	/** How often the team looks for animals again, in ticks. */
	public static final int SCAN_INTERVAL = 100;
	/** Furthest beyond the camp edge the friends look for animals. */
	public static final int MAX_RING = 48;
	/** An animal this close to anything a player built is theirs. */
	public static final int PLAYER_BUILD_GAP = 4;
	/** A hunter leaves the last two of a kind within this many blocks alone. */
	public static final int KIN_RADIUS = 24;
	/** How far each way an animal is checked for a player's fence or wall round it. */
	private static final int ENCLOSURE_REACH = 24;

	/** The team's latest scan. */
	private static final class Seen {
		private long at = Long.MIN_VALUE / 2;
		private List<Animal> animals = List.of();
		private final Map<UUID, Boolean> owned = new HashMap<>();
	}

	private Wildlife() {
	}

	private static Seen seen(ServerLevel level) {
		return TeamCache.get(level, "ranch.wildlife", Seen::new);
	}

	/**
	 * The cows, pigs, sheep, chickens and rabbits in the camp and the gathering ring, as of the team's last scan (at
	 * most {@value #SCAN_INTERVAL} ticks old). Some may have died or moved since: check before use.
	 */
	public static List<Animal> around(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Seen seen = seen(level);
		long now = level.getGameTime();
		if (now - seen.at >= SCAN_INTERVAL || now < seen.at) {
			seen.at = now;
			seen.owned.clear();
			BlockPos centre = c.homePos();
			int r = WorldEditGuard.campRadius(c) + Math.min(FriendsConfig.get().resourceRadius, MAX_RING);
			AABB box = new AABB(centre).inflate(r, 24, r);
			seen.animals = level.getEntitiesOfClass(Animal.class, box, a -> a.isAlive() && Livestock.kind(a) != null);
		}
		return seen.animals;
	}

	// ---------------------------------------------------------------- rules

	/**
	 * True if this looks like somebody's animal rather than a wild one (see the class notes). The block checks are
	 * remembered for the rest of the scan.
	 */
	public static boolean looksOwned(ServerLevel level, Animal a) {
		if (ownedOnSight(a)) {
			return true;
		}
		Seen seen = seen(level);
		Boolean known = seen.owned.get(a.getUUID());
		if (known != null) {
			return known;
		}
		boolean owned = nearPlayersThings(level, a);
		seen.owned.put(a.getUUID(), owned);
		return owned;
	}

	/** {@link #looksOwned} worked out afresh, for the moment before a blow is struck. */
	public static boolean looksOwnedNow(ServerLevel level, Animal a) {
		return ownedOnSight(a) || nearPlayersThings(level, a);
	}

	/** Named, tamed or owned, on a lead, riding or ridden. */
	private static boolean ownedOnSight(Animal a) {
		if (a.hasCustomName() || a.isLeashed() || a.isPassenger() || a.isVehicle()) {
			return true;
		}
		return a instanceof OwnableEntity owned && owned.getOwnerReference() != null;
	}

	private static boolean nearPlayersThings(ServerLevel level, Animal a) {
		CampData data = Camp.data(level.getServer());
		BlockPos pos = a.blockPosition();
		return WorldEditGuard.looksPlayerBuilt(level, pos, PLAYER_BUILD_GAP, data) || enclosed(level, data, pos);
	}

	/**
	 * True if a fence, wall or other player-made block stands in each of the four directions within
	 * {@value #ENCLOSURE_REACH} blocks: the animal is inside a player's enclosure. The friends' own blocks (the pen)
	 * do not count.
	 */
	public static boolean enclosed(ServerLevel level, CampData data, BlockPos pos) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (!barrierTowards(level, data, pos, d)) {
				return false;
			}
		}
		return true;
	}

	private static boolean barrierTowards(ServerLevel level, CampData data, BlockPos pos, Direction d) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int i = 1; i <= ENCLOSURE_REACH; i++) {
			for (int dy = 0; dy <= 1; dy++) {
				m.set(pos.getX() + d.getStepX() * i, pos.getY() + dy, pos.getZ() + d.getStepZ() * i);
				if (!level.isLoaded(m)) {
					return false;
				}
				BlockState s = level.getBlockState(m);
				boolean barrier = s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS) || s.is(BlockTags.FENCE_GATES)
					|| s.is(ModTags.BUILD_MARKERS) && !s.is(Blocks.FARMLAND) && !s.is(Blocks.DIRT_PATH);
				if (barrier && !data.isPlacedByFriends(level, m)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Cheap checks for any animal the friends may take: alive, grown up, in the gathering zone, out of the pen. */
	private static boolean basicallyWild(CompanionEntity c, Animal a, @Nullable Pen pen, CampData data) {
		if (!a.isAlive() || a.isRemoved() || a.isBaby() || a.level() != c.level()) {
			return false;
		}
		BlockPos pos = a.blockPosition();
		if (pen != null && pen.covers(pos)) {
			return false; // the pen's own animals
		}
		if (!WorldEditGuard.inResourceZone(c, pos) || Math.abs(pos.getY() - c.homePos().getY()) > 24) {
			return false;
		}
		return !data.nearDanger(pos, c.level().getGameTime()) && !ownedOnSight(a);
	}

	/**
	 * True if the friend may bring this animal home to the pen: a grown cow, pig, sheep or chicken in the camp or the
	 * gathering ring, outside the pen, away from where a friend died lately, and wild (not somebody's).
	 */
	public static boolean mayLead(CompanionEntity c, Animal a, @Nullable Pen pen) {
		ServerLevel level = (ServerLevel) c.level();
		Livestock.Kind kind = Livestock.kind(a);
		if (kind == null || !kind.penned()) {
			return false;
		}
		CampData data = Camp.data(level.getServer());
		return basicallyWild(c, a, pen, data) && !looksOwned(level, a);
	}

	/**
	 * True if the friend may hunt this animal now. By day only (never at dusk or night); a grown, wild cow, pig, sheep,
	 * chicken or rabbit in the gathering ring outside the camp (never in the camp, whose animals are the pen's); never
	 * where a friend died lately; and never one of the last two of its kind within {@value #KIN_RADIUS} blocks.
	 */
	public static boolean mayHunt(CompanionEntity c, Animal a, @Nullable Pen pen) {
		ServerLevel level = (ServerLevel) c.level();
		Livestock.Kind kind = Livestock.kind(a);
		if (kind == null || Camp.isNight(level) || Camp.isDusk(level)) {
			return false;
		}
		if (WorldEditGuard.inCampHorizontally(c, a.blockPosition())) {
			return false;
		}
		CampData data = Camp.data(level.getServer());
		// The block checks are remembered for the scan, so they go before the kin count (an entity query).
		return basicallyWild(c, a, pen, data) && !looksOwned(level, a) && plenty(c, a, kind);
	}

	/** True if at least two more grown animals of this kind live within {@value #KIN_RADIUS} blocks of this one. */
	private static boolean plenty(CompanionEntity c, Animal a, Livestock.Kind kind) {
		ServerLevel level = (ServerLevel) c.level();
		List<Animal> kin = level.getEntitiesOfClass(Animal.class, a.getBoundingBox().inflate(KIN_RADIUS),
			o -> o != a && o.isAlive() && !o.isBaby() && Livestock.kind(o) == kind && o.distanceToSqr(a) <= KIN_RADIUS * KIN_RADIUS);
		return kin.size() >= 2;
	}

	/** The nearest animal to the friend from the team's scan that passes {@code test}, or null. */
	public static @Nullable Animal nearest(CompanionEntity c, Predicate<Animal> test) {
		List<Animal> sorted = new ArrayList<>(around(c));
		sorted.sort(Comparator.comparingDouble(a -> a.distanceToSqr(c)));
		for (Animal a : sorted) {
			if (test.test(a)) {
				return a;
			}
		}
		return null;
	}
}
