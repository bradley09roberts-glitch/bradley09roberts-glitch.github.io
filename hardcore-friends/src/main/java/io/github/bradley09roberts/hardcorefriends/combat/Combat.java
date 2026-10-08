package io.github.bradley09roberts.hardcorefriends.combat;

/**
 * Combat and gear for every friend: armour, shields and swords up the gear ladder (leather, iron, diamond), bows and
 * arrows for whoever has them, raising a shield against arrows and creepers, focusing on one target together, and
 * emergency healing (golden apples, healing potions). Also the night-safety fixes.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS}, sub-commands through
 * {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and block-edit rules through
 * {@code WorldEditGuard.POLICIES}.
 */
public final class Combat {
	private Combat() {
	}

	public static void init() {
	}
}
