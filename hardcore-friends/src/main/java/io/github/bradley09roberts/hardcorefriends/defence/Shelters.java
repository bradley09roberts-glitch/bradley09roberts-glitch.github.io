package io.github.bradley09roberts.hardcorefriends.defence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.civic.Homes;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.village.VillagePlan;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Where people take cover when the alarm rings: their own home ({@code civic.Homes}), else the nearest other house of
 * the village, the town hall or another public building (the tavern, the chapel, the school), else the camp's cabin.
 * Each shelter has a spot to stand inside it (the plan's {@code inside} spot, else a seat or a table), away from the
 * windows where there is a choice, and the door in front of it. The list is worked out at most every
 * {@value #REFRESH} ticks, and only while it is wanted (an alarm, a command).
 *
 * <p>A shelter is never chosen towards danger: not one with a hostile seen lately within {@value #KEEP_OFF} blocks of
 * it (a creeper within {@value Alarm#CREEPER_CLEARANCE}), nor one whose straight way there passes a hostile standing
 * between them and it within {@value #PATH} blocks of the way (a creeper within {@value #CREEPER_PATH}): the people
 * taking cover are those not fit to fight, children above all.
 *
 * <p>Shutting the door behind them goes through the edit guard (the friends' own wooden doors only: never a player's,
 * never an iron door, never on anyone standing in the doorway), so a player can always open it again.
 */
final class Shelters {
	/** A place to take cover: its key, its name, a spot inside, its door (the step outside it), and who lives there. */
	record Shelter(String key, String name, BlockPos spot, @Nullable BlockPos door, Set<UUID> residents) {
	}

	private static final int REFRESH = 20 * 30;
	/** Shelters further than this from a friend are too far to run to. */
	private static final double MAX_DISTANCE = 64;
	private static final double KEEP_OFF = 6;
	private static final double CREEPER_PATH = 6;
	private static final double PATH = 4;
	/** Standing this close to a shelter's spot, under a roof, counts as in it. */
	private static final double INSIDE = 7;

	private static List<Shelter> cache = List.of();
	/** "Never" is half of {@code Long.MIN_VALUE}, so {@code now - cachedAt} cannot overflow. */
	private static long cachedAt = Long.MIN_VALUE / 2;

	private Shelters() {
	}

	/** Every shelter in the village (worked out again at most every {@value #REFRESH} ticks). */
	static List<Shelter> all(ServerLevel level) {
		long now = level.getGameTime();
		if (now - cachedAt < REFRESH && now >= cachedAt) {
			return cache;
		}
		cachedAt = now;
		cache = find(level);
		return cache;
	}

	private static List<Shelter> find(ServerLevel level) {
		MinecraftServer server = level.getServer();
		CampData data = Camp.data(server);
		List<Shelter> list = new ArrayList<>();
		if (!Camp.isCampLevel(level, data)) {
			return list;
		}
		String dimension = Camp.dimensionId(level);
		for (Homes.Home home : Homes.get().homes(server)) {
			if (!home.dimension().equals(dimension)) {
				continue;
			}
			Optional<VillagePlan.Building> building = VillagePlan.building(server, home.id());
			BlockPos spot = building.map(b -> spotIn(level, b, "inside", "sit", "table")).orElse(null);
			if (spot != null) {
				list.add(new Shelter(home.id(), building.get().name(), spot, home.door(), Set.copyOf(home.residents())));
			}
		}
		for (String kind : new String[] {"civic:town_hall", "civic:tavern", "civic:chapel", "civic:school"}) {
			for (VillagePlan.Building b : VillagePlan.buildingsOfKind(server, kind)) {
				BlockPos spot = spotIn(level, b, "inside", "sit", "seat", "table");
				if (spot != null) {
					list.add(new Shelter(b.siteKey(), b.name(), spot, b.first("door").orElse(null), Set.of()));
				}
			}
		}
		for (String cabin : new String[] {Structures.CABIN, Structures.CABIN_2}) {
			if (!data.isCompleted(cabin)) {
				continue;
			}
			Optional<CampData.Site> site = data.site(cabin);
			if (site.isEmpty()) {
				continue;
			}
			// The cabin's middle, as the friends' resting place has it (CompanionEntity.restPos).
			BlockPos inside = Blueprint.worldPos(site.get().origin, site.get().rotation, 3, 1, 4);
			if (Spots.isStandable(level, inside) && !level.canSeeSky(inside.above())) {
				list.add(new Shelter(cabin, "the cabin", inside, null, Set.of()));
			}
		}
		return List.copyOf(list);
	}

	/** A spot inside a building: the first of its marked spots with room to stand under its roof, away from windows if possible. */
	private static @Nullable BlockPos spotIn(ServerLevel level, VillagePlan.Building b, String... markers) {
		BlockPos fallback = null;
		for (String marker : markers) {
			for (BlockPos p : b.marker(marker)) {
				for (BlockPos q : new BlockPos[] {p, p.north(), p.south(), p.east(), p.west(), p.above()}) {
					if (!level.isLoaded(q) || !Spots.isStandable(level, q) || level.canSeeSky(q.above())) {
						continue;
					}
					if (!besideWindow(level, q)) {
						return q.immutable();
					}
					if (fallback == null) {
						fallback = q.immutable();
					}
				}
			}
		}
		return fallback;
	}

	/** True if glass or a pane is right beside where someone would stand (at their feet or head). */
	private static boolean besideWindow(ServerLevel level, BlockPos feet) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (BlockPos p : new BlockPos[] {feet.relative(d), feet.above().relative(d)}) {
				if (!level.isLoaded(p)) {
					continue;
				}
				Block block = level.getBlockState(p).getBlock();
				if (block instanceof TransparentBlock || block instanceof IronBarsBlock) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The shelter for this friend now: their own home if it is safe to go to, else the nearest safe one (see the class
	 * description), or the one they are already in. Null if there is none.
	 */
	static @Nullable Shelter choose(CompanionEntity c, ServerLevel level) {
		List<Shelter> shelters = all(level);
		if (shelters.isEmpty()) {
			return null;
		}
		Shelter here = containing(level, c);
		if (here != null) {
			return here; // already indoors in one: no going out into it
		}
		List<LivingEntity> threats = Alarm.present(level);
		for (Shelter s : shelters) {
			if (s.residents().contains(c.getUUID()) && safe(c, s, threats)) {
				return s;
			}
		}
		Shelter best = null;
		double bestDist = MAX_DISTANCE * MAX_DISTANCE;
		for (Shelter s : shelters) {
			double d = c.distanceToSqr(Vec3.atBottomCenterOf(s.spot()));
			if (d < bestDist && safe(c, s, threats)) {
				bestDist = d;
				best = s;
			}
		}
		return best;
	}

	/** True if going to this shelter does not take this friend towards danger (see the class description). */
	static boolean safe(CompanionEntity c, Shelter s, List<LivingEntity> threats) {
		Vec3 to = Vec3.atBottomCenterOf(s.spot());
		Vec3 from = c.position();
		for (LivingEntity t : threats) {
			boolean creeper = t instanceof Creeper;
			double keepOff = creeper ? Alarm.CREEPER_CLEARANCE : KEEP_OFF;
			if (t.position().distanceToSqr(to) <= keepOff * keepOff) {
				return false;
			}
			if (Area.inTheWay(t.position(), from, to, creeper ? CREEPER_PATH : PATH)) {
				return false; // it stands between them and the shelter
			}
		}
		return true;
	}

	/** The shelter this friend is in (under a roof, close by its spot), or null. */
	static @Nullable Shelter containing(ServerLevel level, CompanionEntity c) {
		BlockPos feet = c.blockPosition();
		if (level.canSeeSky(feet.above())) {
			return null;
		}
		Shelter best = null;
		double bestDist = INSIDE * INSIDE;
		for (Shelter s : all(level)) {
			BlockPos spot = s.spot();
			double d = Camp.horizontalDistSqr(spot, feet);
			if (Math.abs(spot.getY() - feet.getY()) <= 3 && d <= bestDist) {
				bestDist = d;
				best = s; // the nearest, where two houses stand close together
			}
		}
		return best;
	}

	/** True if this friend is indoors in one of the village's shelters. */
	static boolean indoors(ServerLevel level, CompanionEntity c) {
		return containing(level, c) != null;
	}

	/**
	 * Shuts the open door of this shelter if the friends hung it and it is wooden, nobody stands in the doorway, and it
	 * is within reach. Through the edit guard (the friends' own blocks only), so it is never a player's door.
	 */
	static boolean shutDoor(CompanionEntity c, Shelter s) {
		BlockPos step = s.door();
		if (step == null || c.isChild() || !(c.level() instanceof ServerLevel level)) {
			return false;
		}
		CampData data = Camp.data(level.getServer());
		for (BlockPos p : BlockPos.betweenClosed(step.offset(-2, -1, -2), step.offset(2, 1, 2))) {
			if (!level.isLoaded(p)) {
				continue;
			}
			BlockState state = level.getBlockState(p);
			if (!(state.getBlock() instanceof DoorBlock door) || !DoorBlock.isWoodenDoor(state) || !door.isOpen(state)
				|| state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER || !data.isPlacedByFriends(level, p)) {
				continue;
			}
			BlockPos lower = p.immutable();
			AABB doorway = new AABB(lower).expandTowards(0, 1, 0);
			if (!level.getEntitiesOfClass(LivingEntity.class, doorway, LivingEntity::isAlive).isEmpty() || !c.actions().canReach(lower)) {
				continue;
			}
			if (c.actions().transform(lower, state.setValue(DoorBlock.OPEN, false), WorldEditGuard.Reason.INVENT, null)) {
				level.playSound(null, lower, door.type().doorClose(), SoundSource.BLOCKS, 1.0F, 0.95F);
				return true;
			}
		}
		return false;
	}

	static void clear() {
		cache = List.of();
		cachedAt = Long.MIN_VALUE / 2;
	}
}
