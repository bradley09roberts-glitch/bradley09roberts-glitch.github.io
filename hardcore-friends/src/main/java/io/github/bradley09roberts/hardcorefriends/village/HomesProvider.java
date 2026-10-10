package io.github.bradley09roberts.hardcorefriends.village;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.architecture.Construction;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.civic.Homes;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * The village's answers to {@code civic.Homes}, read from {@link VillageData}: a home is a standing house plot, its
 * beds are the plan's {@code bed} spots (the foot of each bed) in the world, and its residents are who the village
 * moved in. A newborn or a partner joining a household takes a free bed in the household's house (or waits for a
 * bigger one, which the village then builds); someone leaving a marriage is moved out and housed again on their own;
 * someone who died or was dismissed is moved out. Everything else is worked out again by the planner every few
 * seconds, so a missed call never leaves anyone homeless for long.
 */
final class HomesProvider implements Homes.Provider {
	@Override
	public Optional<Homes.Home> homeOf(MinecraftServer server, UUID resident) {
		VillageData v = VillageData.get(server);
		return v.homeOf(resident).filter(VillageData.Plot::standing).map(p -> home(server, v, p));
	}

	@Override
	public Optional<BlockPos> bedFor(CompanionEntity companion) {
		if (!(companion.level() instanceof ServerLevel level)) {
			return Optional.empty();
		}
		MinecraftServer server = level.getServer();
		VillageData v = VillageData.get(server);
		if (!v.dimension().equals(Camp.dimensionId(level)) || v.centre().isEmpty()) {
			return Optional.empty();
		}
		Optional<VillageData.Plot> home = v.homeOf(companion.getUUID());
		if (home.isEmpty() || !home.get().standing()) {
			return Optional.empty();
		}
		int bed = home.get().residents.getOrDefault(companion.getUUID(), -1);
		if (bed < 0) {
			return Optional.empty();
		}
		List<BlockPos> beds = Construction.markers(level, home.get().siteKey, "bed");
		return bed < beds.size() ? Optional.of(beds.get(bed)) : Optional.empty();
	}

	@Override
	public List<Homes.Home> homes(MinecraftServer server) {
		VillageData v = VillageData.get(server);
		List<Homes.Home> list = new ArrayList<>();
		for (VillageData.Plot p : v.plots()) {
			if (p.isHouse() && p.standing()) {
				list.add(home(server, v, p));
			}
		}
		return list;
	}

	@Override
	public boolean roomForOneMore(MinecraftServer server, Collection<UUID> parents) {
		VillageData v = VillageData.get(server);
		for (UUID parent : parents) {
			Optional<VillageData.Plot> home = v.homeOf(parent).filter(VillageData.Plot::standing);
			if (home.isPresent() && Housing.capacity(home.get()) > home.get().residents.size()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void moveIn(MinecraftServer server, UUID resident, Collection<UUID> household) {
		VillageData v = VillageData.get(server);
		Set<UUID> others = new java.util.HashSet<>(household);
		others.remove(resident);
		if (others.isEmpty()) {
			// A household of one leaving a shared house (a marriage that ended): out of it, to be housed on their own.
			Optional<VillageData.Plot> current = v.homeOf(resident);
			if (current.isPresent() && current.get().residents.size() > 1) {
				current.get().residents.remove(resident);
				v.touch();
			}
		} else {
			for (UUID other : others) {
				Optional<VillageData.Plot> home = v.homeOf(other).filter(VillageData.Plot::standing);
				if (home.isPresent()) {
					Housing.moveInto(v, List.of(), home.get(), resident);
					Housing.assignBeds(v, home.get());
					break;
				}
			}
		}
		Planner.reconcileSoon();
	}

	@Override
	public void moveOut(MinecraftServer server, UUID resident) {
		VillageData v = VillageData.get(server);
		for (VillageData.Plot p : v.plots()) {
			if (p.residents.remove(resident) != null) {
				v.touch();
			}
		}
		Planner.reconcileSoon();
	}

	private static Homes.Home home(MinecraftServer server, VillageData v, VillageData.Plot p) {
		ServerLevel level = Planner.campLevel(server, Camp.data(server));
		List<BlockPos> beds = level == null ? List.of() : Construction.markers(level, p.siteKey, "bed");
		List<BlockPos> doors = level == null ? List.of() : Construction.markers(level, p.siteKey, "door");
		BlockPos door = doors.isEmpty() ? p.middle() : doors.getFirst();
		return new Homes.Home(p.siteKey, v.dimension(), door, List.copyOf(beds), Set.copyOf(p.residents.keySet()));
	}
}
