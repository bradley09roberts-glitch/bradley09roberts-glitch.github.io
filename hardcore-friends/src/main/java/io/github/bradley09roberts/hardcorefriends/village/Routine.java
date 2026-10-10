package io.github.bradley09roberts.hardcorefriends.village;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.architecture.Construction;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;

/**
 * The places of a friend's day in the village, for the routine jobs ({@link EveningTask}, {@link MealTask}): the inside
 * of their own house, its table, the tavern's tables and the square. A spot is a standable block at one of the plan's
 * marked spots ({@code inside}, {@code sit}, {@code table}, {@code door}), or one right beside it. Nothing here
 * changes the world; each lookup costs a handful of block reads.
 */
final class Routine {
	private Routine() {
	}

	/** A grown-up or child at work with the team, in the camp's world and inside the camp: the routine is for them. */
	static boolean inVillage(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || c.mode() != CompanionMode.WORK || !c.isTeamMember()) {
			return false;
		}
		VillageData v = VillageData.get(level.getServer());
		return v.centre().isPresent() && Camp.isCampLevel(level, Camp.data(level.getServer())) && Spots.inCamp(c, c.blockPosition());
	}

	/** This friend's house, if it stands. */
	static Optional<VillageData.Plot> home(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return Optional.empty();
		}
		return VillageData.get(level.getServer()).homeOf(c.getUUID()).filter(VillageData.Plot::standing);
	}

	/** A spot to stand in a building, trying its markers in order: the first one with room. */
	static @Nullable BlockPos spotIn(ServerLevel level, VillageData.Plot plot, String... markers) {
		for (String marker : markers) {
			List<BlockPos> spots = Construction.markers(level, plot.siteKey, marker);
			for (BlockPos p : spots) {
				BlockPos s = standableNear(level, p);
				if (s != null) {
					return s;
				}
			}
		}
		return null;
	}

	/** The spot itself if a friend can stand there, else one of its four neighbours. */
	static @Nullable BlockPos standableNear(ServerLevel level, BlockPos p) {
		if (!level.isLoaded(p)) {
			return null;
		}
		if (Spots.isStandable(level, p)) {
			return p;
		}
		for (BlockPos n : new BlockPos[] {p.north(), p.south(), p.east(), p.west(), p.above()}) {
			if (Spots.isStandable(level, n)) {
				return n;
			}
		}
		return null;
	}

	/** The square: by the well if it stands, else by a bench, else near the camp centre. */
	static @Nullable BlockPos square(ServerLevel level, CompanionEntity c) {
		VillageData v = VillageData.get(level.getServer());
		for (String kind : new String[] {"civic:well", "decor:fountain", "decor:bench"}) {
			for (VillageData.Plot p : v.plots()) {
				if (p.standing() && p.kind.equals(kind)) {
					BlockPos s = spotIn(level, p, "sit", "door");
					if (s != null) {
						return s;
					}
				}
			}
		}
		BlockPos centre = v.centre().orElse(null);
		if (centre == null) {
			return null;
		}
		for (int r = 2; r <= 4; r++) {
			BlockPos s = Spots.standable(level, centre.offset(c.getRandom().nextInt(2 * r + 1) - r, 0, c.getRandom().nextInt(2 * r + 1) - r));
			if (s != null) {
				return s;
			}
		}
		return null;
	}

	/** A seat at a standing tavern's tables, if the village has one. */
	static @Nullable BlockPos tavern(ServerLevel level) {
		VillageData v = VillageData.get(level.getServer());
		for (VillageData.Plot p : v.plots()) {
			if (p.standing() && p.kind.equals("civic:tavern")) {
				BlockPos s = spotIn(level, p, "sit", "table", "inside");
				if (s != null) {
					return s;
				}
			}
		}
		return null;
	}
}
