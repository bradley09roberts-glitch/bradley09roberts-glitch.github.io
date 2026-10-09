package io.github.bradley09roberts.hardcorefriends.people;

/**
 * Living together: friendships and romance between friends, couples, weddings, children who grow up, family names,
 * and the skins people wear (more skins can be added as data). Provides {@code civic.Families}.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS} (and {@code TaskScheduler.JOB_FILTERS} to keep a
 * friend off jobs), sub-commands through {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and
 * block-edit rules through {@code WorldEditGuard.POLICIES}.
 */
public final class People {
	private People() {
	}

	public static void init() {
	}
}
