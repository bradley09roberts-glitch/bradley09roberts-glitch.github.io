package io.github.bradley09roberts.hardcorefriends.defence;

/**
 * Defending the village: the alarm bell, children and non-fighters taking cover at home, guards at the walls,
 * gate and watchtower, standing up to raids, and a fire watch.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS} (and {@code TaskScheduler.JOB_FILTERS} to keep a
 * friend off jobs), sub-commands through {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and
 * block-edit rules through {@code WorldEditGuard.POLICIES}.
 */
public final class Defence {
	private Defence() {
	}

	public static void init() {
	}
}
