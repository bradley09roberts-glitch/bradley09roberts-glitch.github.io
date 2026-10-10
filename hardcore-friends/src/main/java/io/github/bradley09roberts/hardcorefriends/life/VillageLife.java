package io.github.bradley09roberts.hardcorefriends.life;

/**
 * Village life: a calendar, market days, feasts and festivals, birthdays, music in the evenings, funerals
 * and graves for those who die, and the Village Chronicle, a book of everything that happens.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS} (and {@code TaskScheduler.JOB_FILTERS} to keep a
 * friend off jobs), sub-commands through {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and
 * block-edit rules through {@code WorldEditGuard.POLICIES}.
 */
public final class VillageLife {
	private VillageLife() {
	}

	public static void init() {
	}
}
