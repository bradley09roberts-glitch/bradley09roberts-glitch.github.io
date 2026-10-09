package io.github.bradley09roberts.hardcorefriends.civic;

import java.util.List;
import java.util.Optional;

import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;

/**
 * The village's building plans beyond the camp's own: houses, shops, workplaces, civic buildings and decorations,
 * owned by the {@code architecture} package (which registers the real {@link Provider}, loading plans from data
 * files). Plans are found by kind: {@code "house"}, {@code "shop:bakery"}, {@code "workplace:fisher"},
 * {@code "civic:town_hall"}, {@code "decor:lamp"} and so on. With no provider there are no such plans.
 */
public final class BlueprintLibrary {
	/** The architecture package's answers. */
	public interface Provider {
		Optional<Blueprint> get(String id);

		/** Every plan of this kind (any style). */
		List<Blueprint> byKind(String kind);

		/** Every plan id there is, for listing. */
		List<String> ids();
	}

	private static final Provider NONE = new Provider() {
		@Override
		public Optional<Blueprint> get(String id) {
			return Optional.empty();
		}

		@Override
		public List<Blueprint> byKind(String kind) {
			return List.of();
		}

		@Override
		public List<String> ids() {
			return List.of();
		}
	};

	private static volatile Provider provider = NONE;

	private BlueprintLibrary() {
	}

	/** Called once by the architecture package. */
	public static void provide(Provider p) {
		provider = p;
	}

	public static Provider get() {
		return provider;
	}
}
