package io.github.bradley09roberts.hardcorefriends.village;

/**
 * A proper village: a town plan of streets and plots, a house of their own for every household built with real
 * materials, real beds to sleep in, a daily routine, civic buildings, and stages beyond the Settlement. Provides
 * {@code civic.Homes}.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS} (and {@code TaskScheduler.JOB_FILTERS} to keep a
 * friend off jobs), sub-commands through {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and
 * block-edit rules through {@code WorldEditGuard.POLICIES}.
 */
public final class Village {
	private Village() {
	}

	public static void init() {
	}
}
