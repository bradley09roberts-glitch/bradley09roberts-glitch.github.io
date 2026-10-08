package io.github.bradley09roberts.hardcorefriends.town;

/**
 * Several players: the camp's owner and the players they trust, each friend's bond with each player, a job board
 * of what the camp needs, mourning a fallen player and keeping their things safe, notes left for the camp, deliveries
 * to players' mailboxes, fair limits on a server, and opt-in siege nights.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS}, sub-commands through
 * {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and block-edit rules through
 * {@code WorldEditGuard.POLICIES}.
 */
public final class Town {
	private Town() {
	}

	public static void init() {
	}
}
