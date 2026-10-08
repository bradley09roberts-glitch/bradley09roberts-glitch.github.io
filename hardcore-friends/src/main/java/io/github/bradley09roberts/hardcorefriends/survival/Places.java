package io.github.bradley09roberts.hardcorefriends.survival;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;

/**
 * The places friends found on their trips: villages, survivor camps, ruined portals, pillager outposts, temples and
 * new biomes, in the camp's dimension. Each is also a camp point of interest ({@code CampData.addPoi}), but those
 * are few and the oldest make way for new ones, so trips keep their own longer list here, in camp memory: a trading
 * trip can always find the village Scout found last week.
 */
public final class Places {
	public static final String VILLAGE = "village";
	public static final String SURVIVOR_CAMP = "survivor_camp";
	public static final String RUINED_PORTAL = "ruined_portal";
	public static final String OUTPOST = "outpost";
	public static final String TEMPLE = "temple";
	/** Prefix of a biome find: "biome:minecraft:dark_forest". */
	public static final String BIOME = "biome:";

	private static final String MEMORY = "survival.places";
	private static final int MAX_PLACES = 96;
	/** Two finds of one kind closer than this are the same place (a village sprawls over several chunks). */
	private static final int SAME_PLACE = 64;

	/** One place found, with the day it was found and an optional "avoid until" time (a raid, zombies). */
	public record Place(String type, BlockPos pos, long foundAt, long avoidUntil) {
		/** "village", "ruined portal", or a biome's name such as "dark forest". */
		public String name() {
			return Places.name(type);
		}
	}

	private Places() {
	}

	/** Every place known in the camp's dimension, oldest first. */
	public static List<Place> all(CampData data) {
		List<Place> list = new ArrayList<>();
		for (Tag t : data.memory(MEMORY).getListOrEmpty("list")) {
			if (t instanceof CompoundTag p) {
				list.add(new Place(p.getStringOr("type", ""), BlockPos.of(p.getLongOr("pos", 0L)), p.getLongOr("at", 0L),
					p.getLongOr("avoid", 0L)));
			}
		}
		return list;
	}

	private static void save(CampData data, List<Place> places) {
		ListTag list = new ListTag();
		for (Place place : places) {
			CompoundTag p = new CompoundTag();
			p.putString("type", place.type());
			p.putLong("pos", place.pos().asLong());
			p.putLong("at", place.foundAt());
			if (place.avoidUntil() > 0) {
				p.putLong("avoid", place.avoidUntil());
			}
			list.add(p);
		}
		data.memory(MEMORY).put("list", list);
		data.setDirty();
	}

	/** True if a place of this type is already known here (any biome of the kind counts once, anywhere). */
	public static boolean known(CampData data, String type, BlockPos pos) {
		for (Place place : all(data)) {
			if (place.type().equals(type) && (type.startsWith(BIOME) || Camp.horizontalDistSqr(place.pos(), pos) <= SAME_PLACE * SAME_PLACE)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Records a find in the camp's dimension, here and as a camp point of interest. Returns false when it was already
	 * known (nothing changes then).
	 */
	public static boolean record(ServerLevel level, String type, BlockPos pos) {
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data) || known(data, type, pos)) {
			return false;
		}
		List<Place> places = all(data);
		if (places.size() >= MAX_PLACES) {
			places.removeFirst();
		}
		places.add(new Place(type, pos.immutable(), level.getGameTime(), 0L));
		save(data, places);
		data.addPoi(type, pos, level.getGameTime());
		return true;
	}

	/** Keeps friends away from a place until the given game time (a raid, monsters about). */
	public static void avoidUntil(CampData data, Place place, long until) {
		List<Place> places = all(data);
		for (int i = 0; i < places.size(); i++) {
			Place p = places.get(i);
			if (p.type().equals(place.type()) && p.pos().equals(place.pos())) {
				places.set(i, new Place(p.type(), p.pos(), p.foundAt(), until));
				save(data, places);
				return;
			}
		}
	}

	/**
	 * The nearest known place of a type within {@code maxDistance} blocks of {@code from} that is not being avoided and
	 * passes the extra test, or null.
	 */
	public static @Nullable Place nearest(CampData data, String type, BlockPos from, int maxDistance, long now,
		Predicate<Place> extra) {
		Place best = null;
		double bestDist = (double) maxDistance * maxDistance;
		for (Place place : all(data)) {
			if (!place.type().equals(type) || place.avoidUntil() > now || !extra.test(place)) {
				continue;
			}
			double d = Camp.horizontalDistSqr(place.pos(), from);
			if (d <= bestDist) {
				bestDist = d;
				best = place;
			}
		}
		return best;
	}

	/** How many places of a type are known. */
	public static int count(CampData data, Predicate<String> type) {
		int n = 0;
		for (Place place : all(data)) {
			if (type.test(place.type())) {
				n++;
			}
		}
		return n;
	}

	/** A readable name for a type of place. */
	public static String name(String type) {
		if (type.startsWith(BIOME)) {
			String id = type.substring(BIOME.length());
			int colon = id.indexOf(':');
			return (colon >= 0 ? id.substring(colon + 1) : id).replace('_', ' ').toLowerCase(Locale.ROOT);
		}
		return switch (type) {
			case VILLAGE -> "village";
			case SURVIVOR_CAMP -> "survivor camp";
			case RUINED_PORTAL -> "ruined portal";
			case OUTPOST -> "pillager outpost";
			case TEMPLE -> "temple";
			default -> type.replace('_', ' ');
		};
	}
}
