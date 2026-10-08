package io.github.bradley09roberts.hardcorefriends.survival;

/**
 * Independence and survival skills: the camp keeps running while a player of the camp is online, friends go on
 * trips beyond the gathering ring (far exploring, foraging, trading), make room to build (search further out, level
 * uneven ground), build a night shelter when caught far from camp, get out of reach when cornered, break their falls,
 * and grow more skilled at the work they do.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS}, sub-commands through
 * {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and block-edit rules through
 * {@code WorldEditGuard.POLICIES}.
 */
public final class Survival {
	private Survival() {
	}

	public static void init() {
	}
}
