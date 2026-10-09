package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;

/**
 * What the expeditions remember, stored in {@code data/hardcorefriends_expedition.dat}: each player's party, the
 * portal crossings players have made (both ends of each, so a friend left behind or going home knows a way through),
 * and the places found on expeditions (the Nether arrival portals, fortresses, the stronghold, the camp's own portal),
 * each with its dimension, since the camp's points of interest have none. Kept apart from the camp's own data so this
 * package's saves never touch it.
 */
public final class ExpeditionData extends SavedData {
	public static final Codec<ExpeditionData> CODEC = CompoundTag.CODEC.xmap(ExpeditionData::fromTag, ExpeditionData::toTag);
	public static final SavedDataType<ExpeditionData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "expedition"), ExpeditionData::new, CODEC, null);

	/** A place kind: the portal players came out of in the Nether. */
	public static final String NETHER_PORTAL = "nether_portal";
	/** A place kind: a Nether fortress seen on an expedition. */
	public static final String FORTRESS = "fortress";
	/** A place kind: the stronghold Scout found (or the one a player led the friends into). */
	public static final String STRONGHOLD = "stronghold";
	/** A place kind: an End portal the friends saw open. */
	public static final String END_PORTAL = "end_portal";
	/** A place kind: the Nether portal the friends built at the camp. */
	public static final String CAMP_PORTAL = "camp_portal";

	private static final int MAX_CROSSINGS = 32;
	private static final int MAX_PLACES = 64;
	/** Two crossings starting this close together (same dimensions) are the same portal. */
	private static final int SAME_PORTAL = 8;
	/** Two places of a kind this close together are the same place (a fortress sprawls). */
	private static final int SAME_PLACE = 48;

	/** One crossing between dimensions: where it starts, where it comes out, and when it was last used. */
	public record Crossing(String fromDim, BlockPos from, String toDim, BlockPos to, long at) {
	}

	/** A place found on an expedition, in its own dimension. */
	public record Place(String type, String dim, BlockPos pos, long at) {
	}

	/** A party member: the friend's id and the name they had when added (for showing them when they are far away). */
	public record Member(UUID id, String name) {
	}

	private final Map<UUID, List<Member>> parties = new LinkedHashMap<>();
	private final List<Crossing> crossings = new ArrayList<>();
	private final List<Place> places = new ArrayList<>();
	private int dragonsDefeated;
	private boolean campPortalLit;

	public ExpeditionData() {
	}

	public static ExpeditionData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	// ----------------------------------------------------------------- parties

	/** The members of a player's party, in the order they were added (empty if none). */
	public List<Member> party(UUID leader) {
		return List.copyOf(parties.getOrDefault(leader, List.of()));
	}

	/** The player whose party this friend is in, if any. */
	public Optional<UUID> partyOf(UUID friend) {
		for (Map.Entry<UUID, List<Member>> e : parties.entrySet()) {
			for (Member m : e.getValue()) {
				if (m.id().equals(friend)) {
					return Optional.of(e.getKey());
				}
			}
		}
		return Optional.empty();
	}

	/** Puts a friend in a player's party, taking them out of any other (a friend is in one party at most). */
	public void join(UUID leader, UUID friend, String name) {
		leave(friend);
		parties.computeIfAbsent(leader, k -> new ArrayList<>()).add(new Member(friend, name));
		setDirty();
	}

	/** Takes a friend out of whatever party they are in. Returns true if they were in one. */
	public boolean leave(UUID friend) {
		boolean removed = false;
		for (List<Member> members : parties.values()) {
			removed |= members.removeIf(m -> m.id().equals(friend));
		}
		parties.values().removeIf(List::isEmpty);
		if (removed) {
			setDirty();
		}
		return removed;
	}

	// --------------------------------------------------------------- crossings

	/** Remembers a crossing (the newest use of a portal replaces the older record of it). */
	public void addCrossing(String fromDim, BlockPos from, String toDim, BlockPos to, long now) {
		crossings.removeIf(c -> c.fromDim().equals(fromDim) && c.toDim().equals(toDim)
			&& c.from().distSqr(from) <= SAME_PORTAL * SAME_PORTAL);
		if (crossings.size() >= MAX_CROSSINGS) {
			crossings.removeFirst();
		}
		crossings.add(new Crossing(fromDim, from.immutable(), toDim, to.immutable(), now));
		setDirty();
	}

	/**
	 * The nearest known way from {@code dim} into {@code toDim} within {@code range} blocks of {@code near}: a crossing
	 * made in that direction, or one made the other way (a Nether portal works both ways). Null if none is known.
	 */
	public Travel.@Nullable Way wayBetween(String dim, BlockPos near, String toDim, int range) {
		Travel.Way best = null;
		double bestDist = (double) range * range;
		for (Crossing c : crossings) {
			BlockPos here;
			BlockPos there;
			if (c.fromDim().equals(dim) && c.toDim().equals(toDim)) {
				here = c.from();
				there = c.to();
			} else if (c.toDim().equals(dim) && c.fromDim().equals(toDim)) {
				here = c.to();
				there = c.from();
			} else {
				continue;
			}
			double d = here.distSqr(near);
			if (d <= bestDist) {
				bestDist = d;
				best = new Travel.Way(here, toDim, there);
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ places

	/** Records a place. Returns false if one of its kind is already known close by in that dimension. */
	public boolean addPlace(String type, String dim, BlockPos pos, long now) {
		for (Place p : places) {
			if (p.type().equals(type) && p.dim().equals(dim) && p.pos().distSqr(pos) <= SAME_PLACE * SAME_PLACE) {
				return false;
			}
		}
		if (places.size() >= MAX_PLACES) {
			places.removeFirst();
		}
		places.add(new Place(type, dim, pos.immutable(), now));
		setDirty();
		return true;
	}

	/** Every place known, oldest first. */
	public List<Place> places() {
		return List.copyOf(places);
	}

	/** The newest known place of a kind, if any. */
	public Optional<Place> latest(String type) {
		for (int i = places.size() - 1; i >= 0; i--) {
			if (places.get(i).type().equals(type)) {
				return Optional.of(places.get(i));
			}
		}
		return Optional.empty();
	}

	// ------------------------------------------------------------------- flags

	public boolean campPortalLit() {
		return campPortalLit;
	}

	public void setCampPortalLit(boolean lit) {
		if (campPortalLit != lit) {
			campPortalLit = lit;
			setDirty();
		}
	}

	public int dragonsDefeated() {
		return dragonsDefeated;
	}

	public void addDragonDefeated() {
		dragonsDefeated++;
		setDirty();
	}

	// ------------------------------------------------------------- persistence

	private static ExpeditionData fromTag(CompoundTag tag) {
		ExpeditionData data = new ExpeditionData();
		CompoundTag partiesTag = tag.getCompoundOrEmpty("parties");
		for (String key : partiesTag.keySet()) {
			UUID leader = uuid(key);
			if (leader == null) {
				continue;
			}
			List<Member> members = new ArrayList<>();
			for (Tag t : partiesTag.getListOrEmpty(key)) {
				if (t instanceof CompoundTag m) {
					UUID id = uuid(m.getStringOr("id", ""));
					if (id != null) {
						members.add(new Member(id, m.getStringOr("name", "a friend")));
					}
				}
			}
			if (!members.isEmpty()) {
				data.parties.put(leader, members);
			}
		}
		for (Tag t : tag.getListOrEmpty("crossings")) {
			if (t instanceof CompoundTag c) {
				data.crossings.add(new Crossing(c.getStringOr("fromDim", ""), BlockPos.of(c.getLongOr("from", 0L)),
					c.getStringOr("toDim", ""), BlockPos.of(c.getLongOr("to", 0L)), c.getLongOr("at", 0L)));
			}
		}
		for (Tag t : tag.getListOrEmpty("places")) {
			if (t instanceof CompoundTag p) {
				data.places.add(new Place(p.getStringOr("type", ""), p.getStringOr("dim", ""), BlockPos.of(p.getLongOr("pos", 0L)),
					p.getLongOr("at", 0L)));
			}
		}
		data.dragonsDefeated = tag.getIntOr("dragons", 0);
		data.campPortalLit = tag.getBooleanOr("campPortalLit", false);
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		CompoundTag partiesTag = new CompoundTag();
		parties.forEach((leader, members) -> {
			ListTag list = new ListTag();
			for (Member m : members) {
				CompoundTag mt = new CompoundTag();
				mt.putString("id", m.id().toString());
				mt.putString("name", m.name());
				list.add(mt);
			}
			partiesTag.put(leader.toString(), list);
		});
		tag.put("parties", partiesTag);
		ListTag crossingTag = new ListTag();
		for (Crossing c : crossings) {
			CompoundTag ct = new CompoundTag();
			ct.putString("fromDim", c.fromDim());
			ct.putLong("from", c.from().asLong());
			ct.putString("toDim", c.toDim());
			ct.putLong("to", c.to().asLong());
			ct.putLong("at", c.at());
			crossingTag.add(ct);
		}
		tag.put("crossings", crossingTag);
		ListTag placeTag = new ListTag();
		for (Place p : places) {
			CompoundTag pt = new CompoundTag();
			pt.putString("type", p.type());
			pt.putString("dim", p.dim());
			pt.putLong("pos", p.pos().asLong());
			pt.putLong("at", p.at());
			placeTag.add(pt);
		}
		tag.put("places", placeTag);
		tag.putInt("dragons", dragonsDefeated);
		tag.putBoolean("campPortalLit", campPortalLit);
		return tag;
	}

	private static @Nullable UUID uuid(String text) {
		try {
			return text.isEmpty() ? null : UUID.fromString(text);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
