package io.github.bradley09roberts.hardcorefriends.progress;

/**
 * Beating the game, step by step: Sage's long-term plan (iron, diamonds, enchanting, the Nether, blaze rods and
 * ender pearls, eyes of ender, the stronghold, the End), and the work it needs: deep mining at diamond level with lava
 * safety, making obsidian, books and bookshelves, enchanting, repairs at the anvil, brewing, and a sugar cane farm.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS}, sub-commands through
 * {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and block-edit rules through
 * {@code WorldEditGuard.POLICIES}.
 */
public final class Progression {
	private Progression() {
	}

	public static void init() {
	}
}
