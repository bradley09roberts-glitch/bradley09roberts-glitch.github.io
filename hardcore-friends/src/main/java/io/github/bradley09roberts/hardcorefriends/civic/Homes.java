package io.github.bradley09roberts.hardcorefriends.civic;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Who lives where: the friends' own houses in the village, owned by the {@code village} package (which registers the
 * real {@link Provider} from its {@code init()}). Other packages only ask through this facade, so they work, more
 * plainly, before or without it: with no provider nobody has a house and everyone sleeps round the camp as before.
 */
public final class Homes {
	/** A house of the village: its id, its door, the beds in it and who lives there. */
	public record Home(String id, String dimension, BlockPos door, List<BlockPos> beds, Set<UUID> residents) {
		/** Beds not yet taken by a resident. */
		public int freeBeds() {
			return Math.max(0, beds.size() - residents.size());
		}
	}

	/** The village package's answers. */
	public interface Provider {
		Optional<Home> homeOf(MinecraftServer server, UUID resident);

		/** This friend's own bed in their home, if they have one. */
		Optional<BlockPos> bedFor(CompanionEntity companion);

		List<Home> homes(MinecraftServer server);

		/**
		 * True if this household could take one more: a home where the parents live with a bed nobody has. The
		 * {@code people} package asks before a baby is born.
		 */
		boolean roomForOneMore(MinecraftServer server, Collection<UUID> parents);

		/**
		 * A newcomer to the household (a newborn, a partner moving in): give them a bed in the household's home. Also
		 * someone moving to a home of their own after a marriage ends ({@code household} is just them): they are still
		 * on the team and need a bed, in another home, after leaving their old one.
		 */
		void moveIn(MinecraftServer server, UUID resident, Collection<UUID> household);

		/** Someone left for good (died, was dismissed): their bed is free again. */
		void moveOut(MinecraftServer server, UUID resident);
	}

	private static final Provider NONE = new Provider() {
		@Override
		public Optional<Home> homeOf(MinecraftServer server, UUID resident) {
			return Optional.empty();
		}

		@Override
		public Optional<BlockPos> bedFor(CompanionEntity companion) {
			return Optional.empty();
		}

		@Override
		public List<Home> homes(MinecraftServer server) {
			return List.of();
		}

		@Override
		public boolean roomForOneMore(MinecraftServer server, Collection<UUID> parents) {
			return false;
		}

		@Override
		public void moveIn(MinecraftServer server, UUID resident, Collection<UUID> household) {
		}

		@Override
		public void moveOut(MinecraftServer server, UUID resident) {
		}
	};

	private static volatile Provider provider = NONE;

	private Homes() {
	}

	/** Called once by the village package. */
	public static void provide(Provider p) {
		provider = p;
	}

	public static Provider get() {
		return provider;
	}
}
