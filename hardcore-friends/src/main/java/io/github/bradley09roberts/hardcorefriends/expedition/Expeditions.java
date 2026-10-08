package io.github.bradley09roberts.hardcorefriends.expedition;

/**
 * Expeditions with a player: friends who follow go through portals with them and come back with them, build and
 * light the Nether portal, barter with piglins, hunt blazes, find the stronghold by throwing eyes of ender, fill the
 * End portal, and fight the dragon: arrows for the crystals, pillars to the caged ones, blows when it perches.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS}, sub-commands through
 * {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and block-edit rules through
 * {@code WorldEditGuard.POLICIES}.
 */
public final class Expeditions {
	private Expeditions() {
	}

	public static void init() {
	}
}
