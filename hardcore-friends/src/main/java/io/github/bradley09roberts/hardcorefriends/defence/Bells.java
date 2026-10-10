package io.github.bradley09roberts.hardcorefriends.defence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SiteFinder;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.village.TownPlan;
import io.github.bradley09roberts.hardcorefriends.village.VillagePlan;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * The village's bells: the town hall's and the school's (the building library's {@code bell} spots, once a bell hangs
 * there), the bell the friends put up at the square themselves ({@link PutUpBellTask}), and any other bell standing in
 * the village, such as the old bell of a game village the camp grew up in. They are found every minute from the loaded
 * chunks' block entities round the camp (never loading a chunk), and rung with the game's own bell ring. A look taken
 * while a bell's own spot was not loaded yet (the world just opened) is taken again a few seconds later; the friends'
 * own bell counts as standing until its spot is loaded and shows otherwise, so they never put up a second one for it.
 */
final class Bells {
	/** A bell in the village and what it is ("the town hall bell"). */
	record Bell(BlockPos pos, String what, int rank) {
	}

	/** Where the friends' own bell hangs, in {@link Alarm#MEMORY}. */
	static final String OWN_BELL = "bell";
	/** The camp centre their own bell was put up for, in {@link Alarm#MEMORY}: a camp moved elsewhere has none yet. */
	private static final String OWN_BELL_CAMP = "bell_camp";
	private static final int REFRESH = 20 * 60;
	/** A look taken while a bell's spot was not loaded is taken again this soon. */
	private static final int RETRY = 20 * 2;
	/** Bells this far above or below the camp centre are not the village's. */
	private static final int HEIGHT = 32;

	private static List<Bell> known = List.of();
	/** Set by {@link #find} when a bell's spot was not loaded, so the list may be short. */
	private static boolean partial;
	/** When each list was made; "never" is half of {@code Long.MIN_VALUE}, so {@code now - knownAt} cannot overflow. */
	private static long knownAt = Long.MIN_VALUE / 2;
	private static int chestBells;
	private static long chestAt = Long.MIN_VALUE / 2;

	private Bells() {
	}

	/** Every bell in the village, the best one to ring first: the town hall's, then the friends' own, then any other. */
	static List<Bell> known(ServerLevel level) {
		long now = level.getGameTime();
		if (now - knownAt < REFRESH && now >= knownAt) {
			return known;
		}
		knownAt = now;
		partial = false;
		known = find(level);
		if (partial) {
			knownAt = now - REFRESH + RETRY; // a bell's spot was not loaded yet: look again soon
		}
		return known;
	}

	/** The bell to ring: the best ranked, then the nearest the camp centre. */
	static @Nullable Bell main(ServerLevel level) {
		List<Bell> bells = known(level);
		Bell best = null;
		for (Bell b : bells) {
			if (isBell(level, b.pos()) && (best == null || b.rank() < best.rank())) {
				best = b;
			}
		}
		return best;
	}

	/** Forgets the bells found, so the next look finds them afresh (a bell was just put up). */
	static void invalidate() {
		knownAt = Long.MIN_VALUE / 2;
		chestAt = Long.MIN_VALUE / 2;
	}

	static boolean isBell(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof BellBlock;
	}

	/** Rings the bell at {@code pos} as the game does (sound, swing, raiders near it glow). False if it is no bell. */
	static boolean ring(@Nullable Entity who, ServerLevel level, BlockPos pos) {
		if (!isBell(level, pos) || !(level.getBlockState(pos).getBlock() instanceof BellBlock bell)) {
			return false;
		}
		return bell.attemptToRing(who, level, pos, null);
	}

	private static List<Bell> find(ServerLevel level) {
		MinecraftServer server = level.getServer();
		CampData data = Camp.data(server);
		List<Bell> list = new ArrayList<>();
		if (!Camp.isCampLevel(level, data)) {
			return list;
		}
		BlockPos centre = data.campPos().orElseThrow();
		int radius = Camp.radius(data) + Area.EDGE;
		Set<BlockPos> seen = new HashSet<>();
		for (VillagePlan.Building b : VillagePlan.buildingsOfKind(server, "civic:town_hall")) {
			for (BlockPos p : b.marker("bell")) {
				partial |= !level.isLoaded(p);
				if (isBell(level, p) && seen.add(p)) {
					list.add(new Bell(p.immutable(), "the town hall bell", 0));
				}
			}
		}
		Optional<BlockPos> own = ownBell(data);
		if (own.isPresent() && inVillage(own.get(), centre, radius)) {
			// Not loaded yet, it is taken to stand still: the friends never put up a second bell for want of a look at it.
			boolean unseen = !level.isLoaded(own.get());
			partial |= unseen;
			if ((unseen || isBell(level, own.get())) && seen.add(own.get())) {
				list.add(new Bell(own.get(), "the village bell", 1));
			}
		}
		for (VillagePlan.Building b : VillagePlan.buildingsOfKind(server, "civic:school")) {
			for (BlockPos p : b.marker("bell")) {
				partial |= !level.isLoaded(p);
				if (isBell(level, p) && seen.add(p)) {
					list.add(new Bell(p.immutable(), "the school bell", 2));
				}
			}
		}
		// Any other bell in the village (an old game village's, one a player hung): from the loaded chunks' block
		// entities, which costs a look through a short list per chunk and never loads one.
		int minCx = (centre.getX() - radius) >> 4;
		int maxCx = (centre.getX() + radius) >> 4;
		int minCz = (centre.getZ() - radius) >> 4;
		int maxCz = (centre.getZ() + radius) >> 4;
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					BlockPos p = be.getBlockPos();
					if (be instanceof BellBlockEntity && inVillage(p, centre, radius) && seen.add(p)) {
						list.add(new Bell(p.immutable(), "the old village bell", 3));
					}
				}
			}
		}
		list.sort((a, b) -> a.rank() != b.rank() ? Integer.compare(a.rank(), b.rank())
			: Double.compare(Camp.horizontalDistSqr(a.pos(), centre), Camp.horizontalDistSqr(b.pos(), centre)));
		return List.copyOf(list);
	}

	/** Within the village's reach of the camp centre, and not far above or below it. */
	private static boolean inVillage(BlockPos p, BlockPos centre, int radius) {
		return Math.abs(p.getY() - centre.getY()) <= HEIGHT && Camp.horizontalDistSqr(p, centre) <= (double) radius * radius;
	}

	/**
	 * True once the village has a town hall planned, going up or standing: its plan has a bell spot, which the builders fill
	 * from the chest themselves, so the friends do not take the chest's bell to the square.
	 */
	static boolean townHallPlanned(ServerLevel level) {
		return VillagePlan.isRequested(level.getServer(), "civic:town_hall");
	}

	/**
	 * Where the friends hung their own bell for this camp, if they did: a bell put up for a camp since moved elsewhere
	 * ({@code /friends camp set}) is not this one's.
	 */
	static Optional<BlockPos> ownBell(CampData data) {
		CompoundTag mem = data.memory(Alarm.MEMORY);
		if (!mem.contains(OWN_BELL)) {
			return Optional.empty();
		}
		if (mem.contains(OWN_BELL_CAMP)
			&& !data.campPos().map(c -> c.asLong() == mem.getLongOr(OWN_BELL_CAMP, 0L)).orElse(false)) {
			return Optional.empty();
		}
		return Optional.of(BlockPos.of(mem.getLongOr(OWN_BELL, 0L)));
	}

	static void rememberOwnBell(CampData data, BlockPos pos) {
		CompoundTag mem = data.memory(Alarm.MEMORY);
		mem.putLong(OWN_BELL, pos.asLong());
		data.campPos().ifPresent(c -> mem.putLong(OWN_BELL_CAMP, c.asLong()));
		data.setDirty();
		invalidate();
	}

	/** How many bells the supply chest holds (looked at once a minute). */
	static int inChest(ServerLevel level) {
		long now = level.getGameTime();
		if (now - chestAt < REFRESH && now >= chestAt) {
			return chestBells;
		}
		chestAt = now;
		chestBells = SupplyChest.of(level).map(chest -> SupplyChest.count(chest, s -> s.is(Items.BELL))).orElse(0);
		return chestBells;
	}

	/**
	 * Where the friends hang a bell of their own: on open ground at the square, a few blocks from the camp centre
	 * (the campfire), never on a reserved building site, beside a player's build or a block entity, nor on or beside
	 * the line of the main streets the town plan runs through the square (a bell there would stand in the street for
	 * good once it is laid), so it is in nobody's way. Null if there is no such spot.
	 */
	static @Nullable BlockPos squareSpot(ServerLevel level, CampData data) {
		Optional<BlockPos> camp = data.campPos();
		if (camp.isEmpty() || !Camp.isCampLevel(level, data)) {
			return null;
		}
		BlockPos centre = camp.get();
		List<int[]> sites = SiteFinder.reservedBoxes(data, "");
		// Corners of the square first: the village's two main streets cross at the campfire, east-west and north-south.
		for (int distance = 3; distance <= 6; distance++) {
			for (int[] d : new int[][] {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
				int step = Math.max(3, (int) Math.round(distance / Math.sqrt(2)));
				BlockPos p = squareCandidate(level, data, sites, centre, centre.offset(d[0] * step, 0, d[1] * step));
				if (p != null) {
					return p;
				}
			}
		}
		for (int distance = 3; distance <= 6; distance++) {
			for (int k = 0; k < 16; k++) {
				double angle = k * Math.PI / 8;
				BlockPos column = centre.offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
				BlockPos p = squareCandidate(level, data, sites, centre, column);
				if (p != null) {
					return p;
				}
			}
		}
		return null;
	}

	/** A spot in this column for the bell, if it is free, open ground and nobody's: see {@link #squareSpot}. */
	private static @Nullable BlockPos squareCandidate(ServerLevel level, CampData data, List<int[]> sites, BlockPos centre,
		BlockPos column) {
		if (!level.isLoaded(column) || TownPlan.onStreet(centre, column.getX(), column.getZ(), 1)) {
			return null; // on the line of a street to come, or its verge
		}
		BlockPos p = Spots.standable(level, column);
		if (p == null || !level.canSeeSky(p) || onSite(p, sites) || nearBlockEntity(level, p)
			|| WorldEditGuard.looksPlayerBuilt(level, p, 2, data)) {
			return null;
		}
		BlockState floor = level.getBlockState(p.below());
		if (!floor.isFaceSturdy(level, p.below(), Direction.UP) || floor.is(Blocks.DIRT_PATH) || floor.is(Blocks.GRAVEL)) {
			return null; // not on a street or a path
		}
		return p;
	}

	private static boolean onSite(BlockPos p, List<int[]> boxes) {
		for (int[] box : boxes) {
			if (p.getX() >= box[0] - 2 && p.getX() <= box[2] + 2 && p.getZ() >= box[1] - 2 && p.getZ() <= box[3] + 2) {
				return true;
			}
		}
		return false;
	}

	/** True if a block entity (the campfire, a chest, a door's neighbour bell...) is within a block of the spot. */
	private static boolean nearBlockEntity(ServerLevel level, BlockPos p) {
		for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
			if (!level.isLoaded(q) || level.getBlockState(q).hasBlockEntity()) {
				return true;
			}
		}
		return false;
	}

	static void clear() {
		known = List.of();
		knownAt = Long.MIN_VALUE / 2;
		partial = false;
		chestBells = 0;
		chestAt = Long.MIN_VALUE / 2;
	}
}
