package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import io.github.bradley09roberts.hardcorefriends.companion.Role;

/**
 * The settlement plan: every camp improvement, the stage it belongs to and which role makes it. A stage is complete
 * when all of its improvements are done, except those whose maker is not on the team (the camp still grows
 * without them) and optional ones.
 */
public final class Structures {
	public record Entry(String id, int stage, Role owner, String displayName, boolean optional) {
	}

	public static final String SUPPLY_CHEST = "supply_chest";
	public static final String CAMPFIRE = "campfire";
	public static final String CRAFTING_TABLE = "crafting_table";
	public static final String FURNACE = "furnace";
	public static final String TORCH_POSTS = "torch_posts";
	public static final String FARM_PLOT = "farm_plot";
	public static final String CABIN = "cabin";
	public static final String PATHS = "paths";
	public static final String AUTO_DOOR = "auto_door";
	/** A fenced paddock with a gate where the farmer keeps and breeds cows, pigs, sheep and chickens. */
	public static final String ANIMAL_PEN = "animal_pen";
	public static final String STOREHOUSE = "storehouse";
	public static final String WATCHTOWER = "watchtower";
	public static final String LANTERN_POSTS = "lantern_posts";
	public static final String HOPPER_DROPOFF = "hopper_dropoff";
	public static final String FARM_FENCE = "farm_fence";
	public static final String CABIN_2 = "cabin_2";
	public static final String AUTO_SMELTER = "auto_smelter";
	public static final String LAMP_POSTS = "lamp_posts";
	public static final String GARDENS = "gardens";

	public static final List<Entry> ALL = List.of(
		new Entry(SUPPLY_CHEST, 0, Role.BUILDER, "supply chest", false),
		new Entry(CAMPFIRE, 0, Role.BUILDER, "campfire", false),
		new Entry(CRAFTING_TABLE, 1, Role.BUILDER, "crafting table", false),
		new Entry(FURNACE, 1, Role.BUILDER, "furnace", false),
		new Entry(TORCH_POSTS, 1, Role.BUILDER, "torch posts", false),
		new Entry(FARM_PLOT, 1, Role.FARMER, "farm plot", false),
		new Entry(CABIN, 2, Role.BUILDER, "cabin", false),
		new Entry(PATHS, 2, Role.LANDSCAPER, "camp paths", false),
		new Entry(AUTO_DOOR, 2, Role.INVENTOR, "automatic cabin door", false),
		// Optional: a camp grows without one, but it is where the farmer keeps animals for meat.
		new Entry(ANIMAL_PEN, 2, Role.LANDSCAPER, "animal pen", true),
		new Entry(STOREHOUSE, 3, Role.BUILDER, "storehouse", false),
		new Entry(WATCHTOWER, 3, Role.BUILDER, "watchtower", false),
		new Entry(LANTERN_POSTS, 3, Role.BUILDER, "lantern posts", false),
		new Entry(HOPPER_DROPOFF, 3, Role.INVENTOR, "drop-off hopper", false),
		new Entry(FARM_FENCE, 3, Role.LANDSCAPER, "farm fence", false),
		new Entry(CABIN_2, 4, Role.BUILDER, "second cabin", false),
		new Entry(AUTO_SMELTER, 4, Role.INVENTOR, "auto-smelter", false),
		new Entry(LAMP_POSTS, 4, Role.INVENTOR, "night lamp posts", true),
		new Entry(GARDENS, 4, Role.LANDSCAPER, "flower gardens", false));

	private Structures() {
	}

	public static List<Entry> forStage(int stage) {
		List<Entry> list = new ArrayList<>();
		for (Entry e : ALL) {
			if (e.stage() == stage) {
				list.add(e);
			}
		}
		return list;
	}

	public static Entry get(String id) {
		for (Entry e : ALL) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		throw new IllegalArgumentException("Unknown structure " + id);
	}

	/** Improvements of a stage still missing, ignoring optional ones and those whose maker is unavailable. */
	public static List<Entry> missing(CampData data, int stage, Predicate<Role> roleAvailable) {
		List<Entry> list = new ArrayList<>();
		for (Entry e : forStage(stage)) {
			// A fence needs a farm to enclose; without one it cannot hold the camp back.
			if (e.id().equals(FARM_FENCE) && !data.isCompleted(FARM_PLOT)) {
				continue;
			}
			if (!data.isCompleted(e.id()) && !e.optional() && roleAvailable.test(e.owner())) {
				list.add(e);
			}
		}
		return list;
	}

	/** The next improvement this role should work on at the current or an earlier stage, or null. */
	public static Entry nextFor(CampData data, Role role) {
		for (Entry e : ALL) {
			if (e.stage() <= data.stage() && e.owner() == role && !data.isCompleted(e.id())) {
				return e;
			}
		}
		return null;
	}
}
