package io.github.bradley09roberts.hardcorefriends.ai.role.scout;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Plain-English compass directions and coordinates for Scout's messages. */
public final class Compass {
	private static final String[] POINTS = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};

	private Compass() {
	}

	/** Eight-point compass direction of a horizontal offset (Minecraft north is −Z, east is +X). */
	public static String direction(double dx, double dz) {
		if (dx * dx + dz * dz < 1.0E-6) {
			return "here";
		}
		// Angle measured like entity yaw: 0 = south (+Z), 90 = west (−X).
		double yaw = Math.toDegrees(Math.atan2(-dx, dz));
		int index = (int) Math.floorMod(Math.round(yaw / 45.0), 8);
		return POINTS[index];
	}

	/** Direction from one entity to a point. */
	public static String direction(Entity from, Vec3 to) {
		return direction(to.x - from.getX(), to.z - from.getZ());
	}

	/** True if a point is behind the entity's facing (more than 90 degrees from where it looks). */
	public static boolean isBehind(Entity viewer, Vec3 point) {
		Vec3 look = viewer.getViewVector(1.0F);
		double dx = point.x - viewer.getX();
		double dz = point.z - viewer.getZ();
		return look.x * dx + look.z * dz < 0;
	}

	public static String coords(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}
}
