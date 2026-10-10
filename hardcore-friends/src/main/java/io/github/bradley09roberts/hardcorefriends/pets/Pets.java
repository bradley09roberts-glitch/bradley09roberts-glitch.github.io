package io.github.bradley09roberts.hardcorefriends.pets;

/**
 * Pets and maps: friends and children adopt cats and dogs that follow them and sleep at home, and Scout draws
 * maps of the land round the camp and where trips went.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS} (and {@code TaskScheduler.JOB_FILTERS} to keep a
 * friend off jobs), sub-commands through {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and
 * block-edit rules through {@code WorldEditGuard.POLICIES}.
 */
public final class Pets {
	private Pets() {
	}

	public static void init() {
	}
}
