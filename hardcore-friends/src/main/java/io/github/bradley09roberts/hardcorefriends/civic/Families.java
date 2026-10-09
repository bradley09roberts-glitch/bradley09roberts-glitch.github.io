package io.github.bradley09roberts.hardcorefriends.civic;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

/**
 * Who belongs with whom: couples, parents and children, owned by the {@code people} package (which registers the real
 * {@link Provider}). Friends are identified by entity UUID, which stays the same when a friend crosses dimensions. With
 * no provider everyone is single and nobody is anyone's child.
 */
public final class Families {
	/** The people package's answers. */
	public interface Provider {
		Optional<UUID> partnerOf(MinecraftServer server, UUID person);

		List<UUID> parentsOf(MinecraftServer server, UUID person);

		List<UUID> childrenOf(MinecraftServer server, UUID person);

		/** The people who live together with this person: their partner and their children who are still young. */
		Set<UUID> householdOf(MinecraftServer server, UUID person);

		/** A family name for display ("Hart"), or empty. */
		Optional<String> familyName(MinecraftServer server, UUID person);
	}

	private static final Provider NONE = new Provider() {
		@Override
		public Optional<UUID> partnerOf(MinecraftServer server, UUID person) {
			return Optional.empty();
		}

		@Override
		public List<UUID> parentsOf(MinecraftServer server, UUID person) {
			return List.of();
		}

		@Override
		public List<UUID> childrenOf(MinecraftServer server, UUID person) {
			return List.of();
		}

		@Override
		public Set<UUID> householdOf(MinecraftServer server, UUID person) {
			return Set.of(person);
		}

		@Override
		public Optional<String> familyName(MinecraftServer server, UUID person) {
			return Optional.empty();
		}
	};

	private static volatile Provider provider = NONE;

	private Families() {
	}

	/** Called once by the people package. */
	public static void provide(Provider p) {
		provider = p;
	}

	public static Provider get() {
		return provider;
	}
}
