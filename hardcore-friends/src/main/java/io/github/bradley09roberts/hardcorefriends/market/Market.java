package io.github.bradley09roberts.hardcorefriends.market;

/**
 * Shops and trades: workplaces with their job blocks, village professions (baker, fisher, shepherd, beekeeper,
 * mason, carpenter, tailor, cook, teacher, doctor, shopkeeper...), and shops where players trade with the friends.
 * Provides {@code civic.Professions}.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS} (and {@code TaskScheduler.JOB_FILTERS} to keep a
 * friend off jobs), sub-commands through {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and
 * block-edit rules through {@code WorldEditGuard.POLICIES}.
 */
public final class Market {
	private Market() {
	}

	public static void init() {
	}
}
