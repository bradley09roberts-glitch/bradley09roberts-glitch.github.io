package io.github.bradley09roberts.hardcorefriends.civic;

import java.util.Optional;

import net.minecraft.core.BlockPos;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * The village trades a friend may hold besides their speciality (baker, fisher, shopkeeper, teacher...), owned by the
 * {@code market} package (which registers the real {@link Provider}). A profession is tied to a workplace building
 * with its job block. With no provider nobody holds a profession.
 */
public final class Professions {
	/** The market package's answers. */
	public interface Provider {
		/** This friend's village trade id, such as {@code "baker"}, if they hold one. */
		Optional<String> professionOf(CompanionEntity companion);

		/** Where they work: the job block of their workplace. */
		Optional<BlockPos> workplaceOf(CompanionEntity companion);

		/** A short title for status lines, such as "Baker". */
		String title(String profession);
	}

	private static final Provider NONE = new Provider() {
		@Override
		public Optional<String> professionOf(CompanionEntity companion) {
			return Optional.empty();
		}

		@Override
		public Optional<BlockPos> workplaceOf(CompanionEntity companion) {
			return Optional.empty();
		}

		@Override
		public String title(String profession) {
			return profession;
		}
	};

	private static volatile Provider provider = NONE;

	private Professions() {
	}

	/** Called once by the market package. */
	public static void provide(Provider p) {
		provider = p;
	}

	public static Provider get() {
		return provider;
	}
}
