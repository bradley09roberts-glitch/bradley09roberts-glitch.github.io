package io.github.bradley09roberts.hardcorefriends.ai.role.scout;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;

import io.github.bradley09roberts.hardcorefriends.camp.CampData;

/**
 * Scout's persistent notebook in {@code CampData.memory("scout.explore")}: how far round the exploration rings
 * Scout has got, and what was found since the last report to a player.
 */
public final class ScoutLog {
	public static final String KEY = "scout.explore";
	public static final String ORE = "ore";
	public static final String TREE = "tree";
	public static final String LAVA = "lava";
	public static final String VILLAGE = "village";
	private static final String[] KINDS = {ORE, TREE, LAVA, VILLAGE};

	private final CampData data;
	private final CompoundTag tag;

	private ScoutLog(CampData data, CompoundTag tag) {
		this.data = data;
		this.tag = tag;
	}

	public static ScoutLog of(CampData data) {
		return new ScoutLog(data, data.memory(KEY));
	}

	public int ring() {
		return tag.getIntOr("ring", 0);
	}

	public int point() {
		return tag.getIntOr("point", 0);
	}

	public int visited() {
		return tag.getIntOr("visited", 0);
	}

	/** Moves on to the next waypoint, wrapping to the first ring after the last. */
	public void advance(int rings, int pointsPerRing, boolean reached) {
		int point = point() + 1;
		int ring = ring();
		if (point >= pointsPerRing) {
			point = 0;
			ring = ring + 1 >= rings ? 0 : ring + 1;
		}
		tag.putInt("point", point);
		tag.putInt("ring", ring);
		if (reached) {
			tag.putInt("visited", visited() + 1);
			tag.putBoolean("explored", true);
		}
		data.setDirty();
	}

	/** Counts a new find for the next report. {@code highlight} replaces the headline if it is more notable. */
	public void noteFind(String kind, String highlight, int rank) {
		tag.putInt("new_" + kind, tag.getIntOr("new_" + kind, 0) + 1);
		if (rank >= tag.getIntOr("highlightRank", -1)) {
			tag.putString("highlight", highlight);
			tag.putInt("highlightRank", rank);
		}
		data.setDirty();
	}

	public int unreported(String kind) {
		return tag.getIntOr("new_" + kind, 0);
	}

	public int unreportedTotal() {
		int total = 0;
		for (String kind : KINDS) {
			total += unreported(kind);
		}
		return total;
	}

	/** True once Scout has reached at least one waypoint since the last report. */
	public boolean exploredSinceReport() {
		return tag.getBooleanOr("explored", false);
	}

	/** A short spoken summary of the unreported finds, such as "3 ore spots, a tree and lava. Best find: ...". */
	public String summary() {
		List<String> parts = new ArrayList<>();
		count(parts, unreported(ORE), "ore spot", "ore spots");
		count(parts, unreported(TREE), "good tree", "good trees");
		count(parts, unreported(VILLAGE), "village", "villages");
		count(parts, unreported(LAVA), "patch of lava near camp", "patches of lava near camp");
		if (parts.isEmpty()) {
			return "nothing new out there";
		}
		String list = parts.size() == 1 ? parts.getFirst()
			: String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
		String highlight = tag.getStringOr("highlight", "");
		return "found " + list + (highlight.isEmpty() ? "" : ". Best find: " + highlight);
	}

	private static void count(List<String> parts, int n, String one, String many) {
		if (n == 1) {
			parts.add((one.matches("^[aeiou].*") ? "an " : "a ") + one);
		} else if (n > 1) {
			parts.add(n + " " + many);
		}
	}

	/** Clears the unreported finds after telling a player. */
	public void reported() {
		for (String kind : KINDS) {
			tag.remove("new_" + kind);
		}
		tag.remove("highlight");
		tag.remove("highlightRank");
		tag.putBoolean("explored", false);
		data.setDirty();
	}
}
