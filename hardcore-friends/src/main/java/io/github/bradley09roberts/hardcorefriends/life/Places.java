package io.github.bradley09roberts.hardcorefriends.life;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.village.VillagePlan;

/**
 * Where village life happens: the camp's world, the square (the open ground round the camp centre, where the feasts
 * are), the tavern (music on ordinary evenings), a spot to stand in a ring round a gathering, and who is about to take
 * part. Lookups are bounded (a handful of block reads per spot) and change nothing.
 */
final class Places {
	/** How near a monster must be to a gathering, a grave or a musician to spoil it. */
	static final double SPOIL_RANGE = 12;

	private Places() {
	}

	/** The camp's world, if the camp is set and that world is loaded. */
	static @Nullable ServerLevel campLevel(MinecraftServer server) {
		CampData camp = Camp.data(server);
		if (camp.campPos().isEmpty()) {
			return null;
		}
		Identifier id = Identifier.tryParse(camp.campDimension());
		return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	/** True when this friend is in the camp's world. */
	static boolean inCampLevel(CompanionEntity c) {
		return c.level() instanceof ServerLevel level && Camp.isCampLevel(level, Camp.data(level.getServer()));
	}

	/** Within the camp (horizontally) and not far above or below its centre: not down the mine or a cave. */
	static boolean inCamp(ServerLevel level, BlockPos pos) {
		CampData camp = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, camp) || camp.campPos().isEmpty()) {
			return false;
		}
		BlockPos centre = camp.campPos().get();
		int r = Camp.radius(camp);
		return Camp.horizontalDistSqr(centre, pos) <= (double) r * r && Math.abs(pos.getY() - centre.getY()) <= 16;
	}

	/**
	 * A grown-up or child of the team who could join in with village life now: at work with the team (not following
	 * anyone off, not holding a spot), in the camp, awake and not keeping the night watch.
	 */
	static boolean free(CompanionEntity c) {
		return c.isAlive() && c.isTeamMember() && c.mode() == CompanionMode.WORK && !c.isAsleep() && c.level() instanceof ServerLevel level
			&& inCamp(level, c.blockPosition()) && !NightWatch.isOnWatch(c);
	}

	/** Everyone in the camp's world who could join in now. */
	static List<CompanionEntity> freePeople(ServerLevel level) {
		List<CompanionEntity> list = new ArrayList<>();
		for (CompanionEntity c : Companions.in(level)) {
			if (free(c)) {
				list.add(c);
			}
		}
		return list;
	}

	/** The loaded, living team member with this id in this world, if any. */
	static @Nullable CompanionEntity loaded(ServerLevel level, @Nullable UUID id) {
		if (id == null) {
			return null;
		}
		return level.getEntity(id) instanceof CompanionEntity c && c.isAlive() && !c.isRemoved() && c.isTeamMember() ? c : null;
	}

	/** The village square: the camp centre, where the campfire and the well are. */
	static @Nullable BlockPos square(ServerLevel level) {
		return Camp.center(level).orElse(null);
	}

	/** A standing spot inside the village's tavern, if one stands; null otherwise. */
	static @Nullable BlockPos tavern(ServerLevel level) {
		for (VillagePlan.Building b : VillagePlan.buildingsOfKind(level.getServer(), "civic:tavern")) {
			for (String marker : new String[] {"inside", "sit", "door"}) {
				for (BlockPos p : b.marker(marker)) {
					BlockPos s = standableNear(level, p, 1);
					if (s != null) {
						return s;
					}
				}
			}
		}
		return null;
	}

	/** A spot a friend can stand on within {@code radius} blocks (square rings) of {@code around}; null if none. */
	static @Nullable BlockPos standableNear(ServerLevel level, BlockPos around, int radius) {
		if (!level.isLoaded(around)) {
			return null;
		}
		for (int r = 0; r <= radius; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					BlockPos s = Spots.standable(level, around.offset(dx, 0, dz));
					if (s != null && Math.abs(s.getY() - around.getY()) <= 2) {
						return s;
					}
				}
			}
		}
		return null;
	}

	/**
	 * A place to stand in a ring of {@code radius} round a centre, the {@code index}th of {@code count}; for a funeral,
	 * {@code half} keeps the ring to the side the grave's sign faces ({@code facingX}, {@code facingZ}). Falls back to
	 * any standable spot nearby.
	 */
	static @Nullable BlockPos ringSpot(ServerLevel level, BlockPos centre, int index, int count, double radius, boolean half, int facingX,
			int facingZ) {
		int n = Math.max(1, count);
		double base = half ? Math.atan2(facingZ, facingX) - Math.PI / 2 : 0;
		double span = half ? Math.PI : 2 * Math.PI;
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = base + span * ((index + attempt * 0.37) % n + 0.5) / n;
			double r = radius + (attempt % 3 == 2 ? 1 : 0);
			BlockPos target = centre.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r));
			if (!level.isLoaded(target)) {
				continue;
			}
			BlockPos s = Spots.standable(level, target);
			if (s != null && Math.abs(s.getY() - centre.getY()) <= 3 && !s.equals(centre)) {
				return s;
			}
		}
		return standableNear(level, centre.offset(facingX * 2, 0, facingZ * 2), 3);
	}

	/** True when a monster that could spoil things is within {@code range} of a spot. */
	static boolean hostileNear(ServerLevel level, BlockPos pos, double range) {
		AABB box = new AABB(pos).inflate(range, range / 2, range);
		return !level.getEntitiesOfClass(LivingEntity.class, box, Threats::isThreat).isEmpty();
	}

	/** The first standing building of a library kind, if any. */
	static Optional<VillagePlan.Building> building(ServerLevel level, String kind) {
		List<VillagePlan.Building> list = VillagePlan.buildingsOfKind(level.getServer(), kind);
		return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
	}
}
