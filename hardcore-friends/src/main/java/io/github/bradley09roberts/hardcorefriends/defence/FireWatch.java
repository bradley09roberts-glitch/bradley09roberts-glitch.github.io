package io.github.bradley09roberts.hardcorefriends.defence;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * Spotting fires in the village and the camp: lightning, lava, a creeper's work. Once a second a slice of the camp's
 * loaded chunk sections is looked over, cheaply: a section's palette says whether it could hold fire at all
 * ({@code LevelChunkSection.maybeHas}), and only one that could is searched block by block, at most
 * {@value #FULL_SCANS} a round, so the whole village is gone over every few seconds without loading a chunk.
 *
 * <p>A fire is put out ({@link PutOutFireTask}) when it is ordinary fire (never soul fire, and never a fire burning for
 * good on netherrack or another everlasting base: that is somebody's fireplace) inside the camp, at most
 * {@value #HEIGHT} blocks above or below its centre, with no lava within {@value #LAVA_CLEARANCE} blocks (lava lights it
 * again at once, and nobody goes near). Fire is never a player's build; breaking it changes nothing else. A fire nobody
 * could get to is left alone for {@value #GIVE_UP} ticks.
 */
final class FireWatch {
	private static final int PALETTES_PER_ROUND = 48;
	private static final int FULL_SCANS = 2;
	private static final int MAX_FIRES = 64;
	private static final int HEIGHT = 24;
	private static final int LAVA_CLEARANCE = 2;
	private static final int GIVE_UP = 20 * 60;
	/** At most this many friends put out fires at once. */
	private static final int CREW = 2;

	private static final Set<BlockPos> FIRES = new LinkedHashSet<>();
	private static final Map<BlockPos, Long> GIVEN_UP = new HashMap<>();
	private static final Map<UUID, BlockPos> CLAIMS = new HashMap<>();
	private static int cursor;

	private FireWatch() {
	}

	/** Once a second: checks the fires known and looks over the next slice of the village for new ones. */
	static void tick(ServerLevel level, CampData data) {
		if (!FriendsConfig.get().fireWatch) {
			FIRES.clear();
			CLAIMS.clear();
			return;
		}
		long now = level.getGameTime();
		GIVEN_UP.values().removeIf(until -> until <= now);
		BlockPos centre = data.campPos().orElseThrow();
		int radius = Camp.radius(data);
		FIRES.removeIf(p -> !level.isLoaded(p) || !isFire(level.getBlockState(p)));
		CLAIMS.values().removeIf(p -> !FIRES.contains(p));
		// A claim whose friend is gone (unloaded, died) is let go, so the fire is not left to burn.
		CLAIMS.keySet().removeIf(id -> !(level.getEntity(id) instanceof CompanionEntity c) || !c.isAlive());
		int minCx = (centre.getX() - radius) >> 4;
		int maxCx = (centre.getX() + radius) >> 4;
		int minCz = (centre.getZ() - radius) >> 4;
		int maxCz = (centre.getZ() + radius) >> 4;
		int minSy = Math.max(level.getMinSectionY(), (centre.getY() - HEIGHT) >> 4);
		int maxSy = Math.min(level.getMaxSectionY(), (centre.getY() + HEIGHT) >> 4);
		int width = maxCx - minCx + 1;
		int depth = maxCz - minCz + 1;
		int tall = maxSy - minSy + 1;
		int total = width * depth * tall;
		if (total <= 0) {
			return;
		}
		int full = 0;
		for (int n = 0; n < Math.min(PALETTES_PER_ROUND, total) && full < FULL_SCANS; n++) {
			int index = Math.floorMod(cursor++, total);
			int sy = minSy + index % tall;
			int cz = minCz + (index / tall) % depth;
			int cx = minCx + index / (tall * depth);
			LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
			if (chunk == null) {
				continue;
			}
			int sectionIndex = level.getSectionIndexFromSectionY(sy);
			if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
				continue;
			}
			LevelChunkSection section = chunk.getSection(sectionIndex);
			if (section.hasOnlyAir() || !section.maybeHas(FireWatch::isFire)) {
				continue;
			}
			full++;
			scanSection(level, data, section, cx, sy, cz, centre, radius, now);
		}
		cursor = Math.floorMod(cursor, total);
	}

	private static void scanSection(ServerLevel level, CampData data, LevelChunkSection section, int cx, int sy, int cz,
		BlockPos centre, int radius, long now) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					BlockState state = section.getBlockState(x, y, z);
					if (!isFire(state)) {
						continue;
					}
					m.set((cx << 4) + x, (sy << 4) + y, (cz << 4) + z);
					if (FIRES.size() < MAX_FIRES && worthPuttingOut(level, m, centre, radius, now)) {
						FIRES.add(m.immutable());
					}
				}
			}
		}
	}

	/** Ordinary fire; soul fire burns blue on purpose, and is left alone. */
	static boolean isFire(BlockState state) {
		return state.is(BlockTags.FIRE) && !state.is(Blocks.SOUL_FIRE);
	}

	/** See the class description: in the camp, not everlasting, clear of lava, not given up on. */
	private static boolean worthPuttingOut(ServerLevel level, BlockPos pos, BlockPos centre, int radius, long now) {
		if (Camp.horizontalDistSqr(pos, centre) > (double) radius * radius || Math.abs(pos.getY() - centre.getY()) > HEIGHT) {
			return false;
		}
		Long until = GIVEN_UP.get(pos);
		if (until != null && until > now) {
			return false;
		}
		BlockPos below = pos.below();
		if (!level.isLoaded(below) || level.getBlockState(below).is(level.dimensionType().infiniburn())) {
			return false; // a fireplace: it burns for good on purpose
		}
		return !lavaNear(level, pos);
	}

	private static boolean lavaNear(ServerLevel level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-LAVA_CLEARANCE, -LAVA_CLEARANCE, -LAVA_CLEARANCE),
			pos.offset(LAVA_CLEARANCE, LAVA_CLEARANCE, LAVA_CLEARANCE))) {
			if (!level.isLoaded(p)) {
				return true;
			}
			if (level.getFluidState(p).is(FluidTags.LAVA)) {
				return true;
			}
		}
		return false;
	}

	/** True if there are fires to put out. */
	static boolean any() {
		return !FIRES.isEmpty();
	}

	static int count() {
		return FIRES.size();
	}

	/**
	 * The fire this friend should go to: the one they are on already, or the nearest one nobody else is on within
	 * {@code range} blocks, while fewer than {@value #CREW} friends are at it. Null if none.
	 */
	static @Nullable BlockPos nearestFor(CompanionEntity c, double range) {
		BlockPos own = CLAIMS.get(c.getUUID());
		if (own != null && FIRES.contains(own)) {
			return own;
		}
		if (CLAIMS.size() >= CREW) {
			return null;
		}
		BlockPos best = null;
		double bestDist = range * range;
		for (BlockPos p : FIRES) {
			if (CLAIMS.containsValue(p) || !level(c).isLoaded(p)) {
				continue;
			}
			double d = c.distanceToSqr(Vec3.atCenterOf(p));
			if (d < bestDist) {
				bestDist = d;
				best = p;
			}
		}
		return best;
	}

	private static ServerLevel level(CompanionEntity c) {
		return (ServerLevel) c.level();
	}

	/** Takes on a fire (see {@link #nearestFor}); null if there is none for them. */
	static @Nullable BlockPos claim(CompanionEntity c, double range) {
		BlockPos fire = nearestFor(c, range);
		if (fire != null) {
			CLAIMS.put(c.getUUID(), fire);
		}
		return fire;
	}

	/** The next fire close to the last one put out (the same blaze), taken on by the same friend; null when it is out. */
	static @Nullable BlockPos next(CompanionEntity c, BlockPos last, double near) {
		CLAIMS.remove(c.getUUID());
		ServerLevel level = level(c);
		BlockPos best = null;
		double bestDist = near * near;
		for (BlockPos p : FIRES) {
			if (CLAIMS.containsValue(p) || p.equals(last)) {
				continue;
			}
			double d = p.distSqr(last);
			if (d < bestDist && level.isLoaded(p) && isFire(level.getBlockState(p))) {
				bestDist = d;
				best = p;
			}
		}
		if (best == null) {
			// Fire spreads faster than a scan round: look right round the last one too.
			for (BlockPos p : BlockPos.betweenClosed(last.offset(-3, -2, -3), last.offset(3, 3, 3))) {
				if (level.isLoaded(p) && isFire(level.getBlockState(p)) && !CLAIMS.containsValue(p)) {
					BlockPos q = p.immutable();
					CampData data = Camp.data(level.getServer());
					if (data.campPos().isPresent()
						&& worthPuttingOut(level, q, data.campPos().get(), Camp.radius(data), level.getGameTime())) {
						FIRES.add(q);
						best = q;
						break;
					}
				}
			}
		}
		if (best != null) {
			CLAIMS.put(c.getUUID(), best);
		}
		return best;
	}

	/** A fire nobody could get to: left alone a while. */
	static void giveUp(BlockPos fire, long now) {
		FIRES.remove(fire);
		GIVEN_UP.put(fire.immutable(), now + GIVE_UP);
		CLAIMS.values().removeIf(fire::equals);
	}

	/** The fire is out (by this friend's hand, or it burnt out). */
	static void out(BlockPos fire) {
		FIRES.remove(fire);
	}

	static void release(CompanionEntity c) {
		CLAIMS.remove(c.getUUID());
	}

	static void clear() {
		FIRES.clear();
		GIVEN_UP.clear();
		CLAIMS.clear();
		cursor = 0;
	}

	/** For a friend who died or left: their claim goes. */
	static void forget(UUID friend) {
		CLAIMS.remove(friend);
	}
}
