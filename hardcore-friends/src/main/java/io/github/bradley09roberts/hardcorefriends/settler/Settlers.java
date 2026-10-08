package io.github.bradley09roberts.hardcorefriends.settler;

/**
 * Newcomers: people living in villages, at survivor camps out in the world and on the road, each with their own
 * name, look and trade (one of the nine kinds of work). Strangers mind their own home until a player earns their
 * trust (they say what they would like first), then join the team and work like any friend.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS}, sub-commands through
 * {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and block-edit rules through
 * {@code WorldEditGuard.POLICIES}.
 */
public final class Settlers {
	private Settlers() {
	}

	public static void init() {
	}
}
