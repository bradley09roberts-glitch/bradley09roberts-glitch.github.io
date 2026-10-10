package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import io.github.bradley09roberts.hardcorefriends.ai.role.scout.Compass;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.pets.PetsData.MapRecord;
import io.github.bradley09roberts.hardcorefriends.pets.PetsData.MapState;
import io.github.bradley09roberts.hardcorefriends.survival.Places;
import io.github.bradley09roberts.hardcorefriends.survival.Trips;

/**
 * Scout's maps. The camp's explorer (Scout, or a newcomer who explores like her; one map maker at a time) makes empty
 * maps from the camp's paper and a compass ({@link MakeMapTask}), turns the first into the map of the camp (1:2, the
 * land 128 blocks round the camp centre) and carries a spare on her trips: once she is well away from the camp and off
 * every map already drawn, it becomes a map of where she is (1:4, 512 blocks across). Whatever she carries is drawn in
 * as she walks ({@link MapPainter}, only land already loaded). A map is finished when enough of it is drawn (the camp's
 * when 60% is, or a quarter after three days; a trip's when she is home with any of it drawn, or after six days); she
 * marks the camp and the places found on trips on it, says so, and takes it to hang in the town hall or library
 * ({@link HangMapTask}) or puts it in the chest. Players get copies ({@link CopyMapTask} and right-clicking her with an
 * empty map).
 */
final class Maps {
	/** The camp map's scale (1:2): 256 blocks across, 128 each way from the camp centre. */
	static final int CAMP_SCALE = 1;
	/** A trip map's scale (1:4): 512 blocks across. */
	static final int TRIP_SCALE = 2;
	/** How far round the map maker each slice of drawing reaches, in blocks. */
	static final int DRAW_RADIUS = 64;
	/** A trip map is only started this far beyond the camp's edge. */
	static final int TRIP_START = 64;
	private static final float CAMP_DONE = 0.6F;
	private static final float CAMP_DONE_LATE = 0.25F;
	private static final int CAMP_LATE_DAYS = 3;
	private static final float TRIP_DONE = 0.05F;
	private static final int TRIP_LATE_DAYS = 6;
	private static final int MAKER_CACHE = 100;
	/** How many places found on trips are marked on one map at most. */
	private static final int MAX_MARKS = 12;

	private static @Nullable UUID makerId;
	private static long makerAt = Long.MIN_VALUE / 2;
	/**
	 * The map numbers being drawn or carried home just now, so the map maker keeps those maps in their backpack (a keep
	 * rule) while any other map a friend picks up (one fallen from its frame, say) is tidied into the chest as usual.
	 */
	private static volatile Set<Integer> carriedIds = Set.of();

	private Maps() {
	}

	static void clear() {
		makerId = null;
		makerAt = Long.MIN_VALUE / 2;
		carriedIds = Set.of();
	}

	/** Brings the list of maps being carried up to date (on starting, and whenever a map is started or seen to). */
	static void refreshCarried(MinecraftServer server) {
		Set<Integer> ids = new HashSet<>();
		for (MapRecord m : PetsData.get(server).maps()) {
			if (m.state == MapState.DRAWING || m.state == MapState.FINISHED) {
				ids.add(m.id);
			}
		}
		carriedIds = Set.copyOf(ids);
	}

	/** For the keep rule: a map being drawn or carried home, or an empty map. */
	static boolean keeps(ItemStack s) {
		if (s.is(Items.MAP)) {
			return true;
		}
		MapId id = s.is(Items.FILLED_MAP) ? s.get(DataComponents.MAP_ID) : null;
		return id != null && carriedIds.contains(id.id());
	}

	// ---------------------------------------------------------------- makers

	/** An explorer by trade (Scout, or a newcomer who explores like her), grown up and on the team. */
	static boolean isExplorer(CompanionEntity c) {
		return c.isTeamMember() && !c.isChild() && c.friendId().role() == Role.EXPLORER && c.isAlive();
	}

	/**
	 * The camp's one map maker just now: Scout herself if she is about, otherwise the explorer newcomer first on the
	 * roster. Worked out at most every {@value #MAKER_CACHE} ticks.
	 */
	static boolean isMaker(CompanionEntity c) {
		if (!isExplorer(c) || !FriendsConfig.get().scoutMaps || !(c.level() instanceof ServerLevel level)) {
			return false;
		}
		long now = level.getGameTime();
		if (now - makerAt >= MAKER_CACHE || now < makerAt) {
			makerAt = now;
			CompanionEntity best = null;
			for (CompanionEntity other : Companions.all()) {
				if (!isExplorer(other)) {
					continue;
				}
				boolean scout = other.friendId() == FriendId.SCOUT && !other.isSettler();
				boolean bestScout = best != null && best.friendId() == FriendId.SCOUT && !best.isSettler();
				if (best == null || scout && !bestScout || scout == bestScout && other.rosterIndex() < best.rosterIndex()) {
					best = other;
				}
			}
			makerId = best == null ? null : best.getUUID();
		}
		return c.getUUID().equals(makerId);
	}

	// ------------------------------------------------------------- creation

	/**
	 * A fresh map's data centred exactly on the spot (the game snaps a new map to a grid, which could put the camp at
	 * its very edge): made the game's way, then given the wanted centre through the map data's own saved form.
	 */
	private static MapItemSavedData centred(ServerLevel level, int x, int z, int scale) {
		MapItemSavedData fresh = MapItemSavedData.createFresh(x, z, (byte) scale, true, false, level.dimension());
		try {
			Tag tag = MapItemSavedData.CODEC.encodeStart(NbtOps.INSTANCE, fresh).result().orElse(null);
			if (tag instanceof CompoundTag c) {
				c.putInt("xCenter", x);
				c.putInt("zCenter", z);
				return MapItemSavedData.CODEC.parse(NbtOps.INSTANCE, c).result().orElse(fresh);
			}
		} catch (RuntimeException e) {
			// fall back to the game's own snapped map
		}
		return fresh;
	}

	/**
	 * Turns one of the maker's empty maps into a new map of {@code shows}, centred on the spot, and records it. Returns
	 * the record, or null if they carry no empty map.
	 */
	static @Nullable MapRecord start(CompanionEntity c, BlockPos centre, int scale, String shows, boolean camp) {
		if (!(c.level() instanceof ServerLevel level) || c.backpack().remove(Workbench.EMPTY_MAP, 1) < 1) {
			return null;
		}
		MapItemSavedData data = centred(level, centre.getX(), centre.getZ(), scale);
		MapId id = level.getFreeMapId();
		level.setMapData(id, data);
		PetsData pets = PetsData.get(level.getServer());
		MapRecord rec = pets.newMap(id.id(), shows, Camp.dimensionId(level), data.centerX, data.centerZ, scale, camp);
		rec.holder = c.getUUID();
		rec.maker = c.displayName();
		rec.startedDay = Camp.day(level);
		pets.setDirty();
		ItemStack stack = new ItemStack(Items.FILLED_MAP);
		stack.set(DataComponents.MAP_ID, id);
		label(stack, rec);
		refreshCarried(level.getServer());
		Workbench.give(c, stack);
		Camp.data(level.getServer()).addStat("maps_started", 1);
		return rec;
	}

	/** Names the map item after what it shows, with who drew it. */
	private static void label(ItemStack stack, MapRecord rec) {
		stack.set(DataComponents.ITEM_NAME, Component.literal(rec.title()));
		List<Component> lore = new ArrayList<>();
		lore.add(Component.literal("Drawn by " + (rec.maker.isEmpty() ? "the camp's explorer" : rec.maker)).withStyle(ChatFormatting.GRAY));
		stack.set(DataComponents.LORE, new ItemLore(lore));
	}

	/**
	 * Marks the map: the camp, and the places found on trips that lie on it (villages, survivor camps, temples,
	 * ruined portals and pillager outposts), with the game's own map markers.
	 */
	static void decorate(ServerLevel level, ItemStack stack, MapRecord rec) {
		CampData data = Camp.data(level.getServer());
		Optional<BlockPos> centre = data.campPos();
		if (centre.isPresent() && rec.covers(data.campDimension(), centre.get().getX(), centre.get().getZ())) {
			MapItemSavedData.addTargetDecoration(stack, centre.get(), "hardcorefriends_camp", MapDecorationTypes.TARGET_POINT);
		}
		// The village, once it has a town hall: a village mark on the hall.
		Optional<BlockPos> hall = MapFrames.building(level).filter(key -> key.contains("town_hall"))
			.flatMap(data::site).map(site -> site.origin);
		if (hall.isPresent() && rec.covers(data.campDimension(), hall.get().getX(), hall.get().getZ())) {
			MapItemSavedData.addTargetDecoration(stack, hall.get(), "hardcorefriends_village", MapDecorationTypes.PLAINS_VILLAGE);
		}
		if (!rec.dimension.equals(data.campDimension())) {
			return;
		}
		int marks = 0;
		for (Places.Place place : Places.all(data)) {
			Holder<MapDecorationType> mark = mark(place.type());
			if (mark == null || !rec.covers(rec.dimension, place.pos().getX(), place.pos().getZ())) {
				continue;
			}
			MapItemSavedData.addTargetDecoration(stack, place.pos(), "hardcorefriends_" + place.type() + "_" + place.pos().asLong(), mark);
			if (++marks >= MAX_MARKS) {
				break;
			}
		}
	}

	private static @Nullable Holder<MapDecorationType> mark(String type) {
		return switch (type) {
			case Places.VILLAGE -> MapDecorationTypes.PLAINS_VILLAGE;
			case Places.SURVIVOR_CAMP -> MapDecorationTypes.ABANDONED_CAMP;
			case Places.TEMPLE -> MapDecorationTypes.DESERT_PYRAMID;
			case Places.RUINED_PORTAL -> MapDecorationTypes.RED_X;
			case Places.OUTPOST -> MapDecorationTypes.RED_MARKER;
			default -> null;
		};
	}

	/**
	 * A copy of a map for a player: the same map (it shares the original's drawing, as a map copied at a table does),
	 * with its name and marks. The caller pays for it with an empty map.
	 */
	static ItemStack copyOf(ServerLevel level, MapRecord rec) {
		ItemStack stack = new ItemStack(Items.FILLED_MAP);
		stack.set(DataComponents.MAP_ID, new MapId(rec.id));
		label(stack, rec);
		decorate(level, stack, rec);
		return stack;
	}

	/** The map item for this record in the friend's backpack, or empty. */
	static ItemStack carried(CompanionEntity c, int mapId) {
		return c.backpack().find(s -> isMap(s, mapId));
	}

	static boolean isMap(ItemStack s, int mapId) {
		if (!s.is(Items.FILLED_MAP)) {
			return false;
		}
		MapId id = s.get(DataComponents.MAP_ID);
		return id != null && id.id() == mapId;
	}

	/** Which finished map a player gets a copy of: one showing where they stand, else the camp's, else the newest. */
	static Optional<MapRecord> bestFor(ServerLevel level, BlockPos at) {
		PetsData data = PetsData.get(level.getServer());
		String dim = Camp.dimensionId(level);
		MapRecord here = null;
		MapRecord camp = null;
		MapRecord newest = null;
		for (MapRecord m : data.maps()) {
			if (!done(m)) {
				continue;
			}
			if (here == null && m.covers(dim, at.getX(), at.getZ())) {
				here = m;
			}
			if (m.camp && camp == null) {
				camp = m;
			}
			newest = m;
		}
		return Optional.ofNullable(here != null ? here : camp != null ? camp : newest);
	}

	/** Finished, and still the camp's (being carried home, hanging or in the chest). */
	static boolean done(MapRecord m) {
		return m.state == MapState.FINISHED || m.state == MapState.HUNG || m.state == MapState.STORED;
	}

	// --------------------------------------------------------------- drawing

	/**
	 * Every tick of every friend: the map maker draws in what they carry, and sees to their maps now and then. So does an
	 * explorer who was the map maker a while (Scout away when they came) and still carries a map: they finish it and
	 * bring it home, or it would never be done and keep the camp from getting another.
	 */
	static void tick(CompanionEntity c, ServerLevel level) {
		if (!isExplorer(c) || !FriendsConfig.get().scoutMaps) {
			return;
		}
		int phase = c.tickCount + c.getId();
		if (phase % 4 == 0) {
			draw(c, level, phase / 4);
		}
		if (phase % 100 == 0) {
			boolean maker = isMaker(c);
			if (maker || !PetsData.get(level.getServer()).carriedBy(c.getUUID()).isEmpty()) {
				upkeep(c, level, maker);
			}
		}
	}

	/** One slice of drawing on one of the maps the friend is drawing (taking turns when there are several). */
	private static void draw(CompanionEntity c, ServerLevel level, int step) {
		PetsData data = PetsData.get(level.getServer());
		List<MapRecord> drawing = new ArrayList<>();
		String dim = Camp.dimensionId(level);
		BlockPos at = c.blockPosition();
		for (MapRecord m : data.carriedBy(c.getUUID())) {
			if (m.state == MapState.DRAWING && m.dimension.equals(dim) && near(m, at)) {
				drawing.add(m);
			}
		}
		if (drawing.isEmpty()) {
			return;
		}
		MapRecord m = drawing.get(Math.floorMod(step / 16, drawing.size()));
		MapItemSavedData map = level.getMapData(new MapId(m.id));
		if (map == null || carried(c, m.id).isEmpty()) {
			return;
		}
		MapPainter.paint(level, map, c, step, DRAW_RADIUS);
	}

	/** True if the spot is on the map or close enough to its edge to draw some of it. */
	private static boolean near(MapRecord m, BlockPos at) {
		int reach = m.halfWidth() + DRAW_RADIUS;
		return Math.abs(at.getX() - m.centreX) < reach && Math.abs(at.getZ() - m.centreZ) < reach;
	}

	/**
	 * Every five seconds for the map maker (or an explorer still carrying maps): maps that left their backpack are
	 * written off, drawing is counted, maps that are drawn enough are finished, and the map maker starts a trip map when
	 * they are out beyond every map.
	 */
	private static void upkeep(CompanionEntity c, ServerLevel level, boolean maker) {
		PetsData data = PetsData.get(level.getServer());
		long day = Camp.day(level);
		for (MapRecord m : data.carriedBy(c.getUUID())) {
			if (carried(c, m.id).isEmpty()) {
				m.state = MapState.GONE; // taken out of their backpack by someone
				m.holder = null;
				data.setDirty();
				continue;
			}
			if (m.state != MapState.DRAWING) {
				continue;
			}
			MapItemSavedData map = level.getServer().overworld().getMapData(new MapId(m.id));
			if (map != null) {
				m.coverage = MapPainter.coverage(map);
				data.setDirty();
			}
			boolean finished = m.camp
				? m.coverage >= CAMP_DONE || day - m.startedDay >= CAMP_LATE_DAYS && m.coverage >= CAMP_DONE_LATE
				: Trips.home(c) && m.coverage >= TRIP_DONE || day - m.startedDay >= TRIP_LATE_DAYS && m.coverage > 0;
			if (finished) {
				finish(c, level, m);
			}
		}
		if (maker) {
			maybeStartTripMap(c, level, data);
		}
		refreshCarried(level.getServer());
	}

	/** The map is drawn: marked, named, and announced by its maker, ready to be hung up. */
	private static void finish(CompanionEntity c, ServerLevel level, MapRecord m) {
		m.state = MapState.FINISHED;
		m.finishedDay = Camp.day(level);
		PetsData.get(level.getServer()).setDirty();
		ItemStack stack = carried(c, m.id);
		if (!stack.isEmpty()) {
			decorate(level, stack, m);
		}
		Speech.say(c, Line.MAP_FINISHED, m.shows);
		Camp.data(level.getServer()).addStat("maps_drawn", 1);
	}

	/**
	 * Out on a trip, beyond the camp's edge and off every map already drawn or being drawn, with a spare empty map:
	 * start a map of the land here. Only in the camp's own dimension, and never one with a ceiling.
	 */
	private static void maybeStartTripMap(CompanionEntity c, ServerLevel level, PetsData data) {
		CampData camp = Camp.data(level.getServer());
		Optional<BlockPos> centre = Camp.center(level);
		if (centre.isEmpty() || level.dimensionType().hasCeiling() || c.backpack().count(Workbench.EMPTY_MAP) < 1) {
			return;
		}
		BlockPos at = c.blockPosition();
		int beyond = Camp.radius(camp) + TRIP_START;
		if (Camp.horizontalDistSqr(at, centre.get()) <= (double) beyond * beyond) {
			return;
		}
		String dim = Camp.dimensionId(level);
		for (MapRecord m : data.maps()) {
			if (m.state != MapState.GONE && m.covers(dim, at.getX(), at.getZ())) {
				return;
			}
		}
		int dx = at.getX() - centre.get().getX();
		int dz = at.getZ() - centre.get().getZ();
		int distance = (int) Math.round(Math.sqrt((double) dx * dx + dz * dz) / 50.0) * 50;
		String shows = "the land " + distance + " blocks " + Compass.direction(dx, dz) + " of the camp";
		start(c, at, TRIP_SCALE, shows, false);
	}

	/**
	 * The map of the camp where it is now, unless it was lost. A camp moved elsewhere ({@code /friends camp set}) is
	 * off its old map, so it gets a new one.
	 */
	static Optional<MapRecord> campMapHere(ServerLevel level) {
		Optional<BlockPos> centre = Camp.center(level);
		if (centre.isEmpty()) {
			return Optional.empty();
		}
		String dim = Camp.dimensionId(level);
		for (MapRecord m : PetsData.get(level.getServer()).maps()) {
			if (m.camp && m.state != MapState.GONE && m.covers(dim, centre.get().getX(), centre.get().getZ())) {
				return Optional.of(m);
			}
		}
		return Optional.empty();
	}

	/** Starts the map of the camp from one of the maker's empty maps. */
	static boolean startCampMap(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return false;
		}
		Optional<BlockPos> centre = Camp.center(level);
		return centre.isPresent() && start(c, centre.get(), CAMP_SCALE, "the camp", true) != null;
	}

	// --------------------------------------------------------------- checks

	/**
	 * Every so often for the maps not carried: a hung map whose frame is loaded but no longer holds it, or a map put
	 * away whose chest is loaded but no longer holds it, was taken by someone and is written off. A frame is only looked
	 * for once the entities of its chunk have loaded too (they load a moment after the blocks).
	 */
	static void checkPlaced(MinecraftServer server) {
		PetsData data = PetsData.get(server);
		ServerLevel campLevel = campLevel(server);
		for (MapRecord m : data.maps()) {
			if (m.state == MapState.HUNG && m.frame != null && campLevel != null && campLevel.isLoaded(m.frame)
				&& campLevel.areEntitiesLoaded(ChunkPos.pack(m.frame)) && MapFrames.frameWith(campLevel, m.frame, m.id).isEmpty()) {
				m.state = MapState.GONE;
				m.frame = null;
				data.setDirty();
			} else if (m.state == MapState.STORED && campLevel != null) {
				Optional<Container> chest = SupplyChest.of(campLevel);
				if (chest.isPresent() && SupplyChest.count(chest.get(), s -> isMap(s, m.id)) == 0) {
					m.state = MapState.GONE;
					data.setDirty();
				}
			}
		}
	}

	/** The level the camp is in, if it is loaded. */
	static @Nullable ServerLevel campLevel(MinecraftServer server) {
		CampData data = Camp.data(server);
		if (data.campPos().isEmpty()) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (Camp.isCampLevel(level, data)) {
				return level;
			}
		}
		return null;
	}

	/** The friend was lost (died or dismissed): the maps they carried went with their backpack. */
	static void holderLeft(CompanionEntity c, ServerLevel level) {
		PetsData data = PetsData.get(level.getServer());
		for (MapRecord m : data.carriedBy(c.getUUID())) {
			m.state = MapState.GONE;
			m.holder = null;
			data.setDirty();
		}
	}

	/** The level a map record belongs to, for its data (maps live in the server's storage, so any level reads them). */
	static @Nullable MapItemSavedData dataOf(MinecraftServer server, MapRecord m) {
		Level overworld = server.overworld();
		return overworld.getMapData(new MapId(m.id));
	}
}
