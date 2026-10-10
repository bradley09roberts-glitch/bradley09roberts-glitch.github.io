package io.github.bradley09roberts.hardcorefriends.ai.role.terra;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;

import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.Pen;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;

/**
 * Terra's path layout: a straight L-shaped path from the camp centre to every building site of the camp (not the
 * village's, which front its own streets) and to the supply chest (first along x, then along z). Pure geometry from the camp data, so other routines can keep clear of planned paths.
 */
public final class PathPlan {
	private PathPlan() {
	}

	/** Where paths lead: every building site origin and the supply chest. */
	public static List<BlockPos> targets(CampData data) {
		List<BlockPos> targets = new ArrayList<>();
		BlockPos centre = data.campPos().orElse(null);
		if (centre == null) {
			return targets;
		}
		int maxLength = 2 * Camp.radius(data);
		for (Map.Entry<String, CampData.Site> e : data.sites().entrySet()) {
			if (e.getKey().startsWith(io.github.bradley09roberts.hardcorefriends.village.Planner.KEY_PREFIX)) {
				continue; // the village's buildings front its own streets (the village package lays those)
			}
			CampData.Site site = e.getValue();
			// The animal pen's path leads to its gate, not along its fence to a corner.
			BlockPos to = e.getKey().equals(Structures.ANIMAL_PEN) ? new Pen(site.origin, site.rotation).outside() : site.origin;
			addTarget(targets, centre, to, maxLength);
		}
		data.chestPos().ifPresent(chest -> addTarget(targets, centre, chest, maxLength));
		return targets;
	}

	private static void addTarget(List<BlockPos> targets, BlockPos centre, BlockPos target, int maxLength) {
		int length = Math.abs(target.getX() - centre.getX()) + Math.abs(target.getZ() - centre.getZ());
		if (length >= 3 && length <= maxLength) {
			targets.add(target);
		}
	}

	/** Every column on a planned path (y is the camp centre's), from the centre outwards, without duplicates. */
	public static List<BlockPos> columns(CampData data) {
		BlockPos centre = data.campPos().orElse(null);
		if (centre == null) {
			return List.of();
		}
		Set<Long> seen = new LinkedHashSet<>();
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos target : targets(data)) {
			int stepX = Integer.signum(target.getX() - centre.getX());
			int stepZ = Integer.signum(target.getZ() - centre.getZ());
			int x = centre.getX();
			int z = centre.getZ();
			while (true) {
				if (x == target.getX() && z == target.getZ()) {
					break; // the target itself is the building or chest
				}
				BlockPos column = new BlockPos(x, centre.getY(), z);
				if (seen.add(column.asLong())) {
					out.add(column);
				}
				if (x != target.getX()) {
					x += stepX;
				} else {
					z += stepZ;
				}
			}
		}
		return out;
	}

	/** True if the column lies on a planned path. Cheap: no world access. */
	public static boolean onPath(CampData data, BlockPos pos) {
		BlockPos centre = data.campPos().orElse(null);
		if (centre == null) {
			return false;
		}
		for (BlockPos target : targets(data)) {
			if (pos.getZ() == centre.getZ() && between(pos.getX(), centre.getX(), target.getX())) {
				return true;
			}
			if (pos.getX() == target.getX() && between(pos.getZ(), centre.getZ(), target.getZ())) {
				return true;
			}
		}
		return false;
	}

	/** True if the column is on or right beside a planned path. */
	public static boolean nearPath(CampData data, BlockPos pos, int margin) {
		for (int dx = -margin; dx <= margin; dx++) {
			for (int dz = -margin; dz <= margin; dz++) {
				if (onPath(data, pos.offset(dx, 0, dz))) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean between(int v, int a, int b) {
		return v >= Math.min(a, b) && v <= Math.max(a, b);
	}
}
