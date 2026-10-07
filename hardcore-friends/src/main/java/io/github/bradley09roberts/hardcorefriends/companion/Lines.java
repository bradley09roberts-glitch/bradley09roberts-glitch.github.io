package io.github.bradley09roberts.hardcorefriends.companion;

/** Placeholder; replaced by the full dialogue table. */
public final class Lines {
	private Lines() {
	}

	public static String[] get(FriendId friend, Line line) {
		return new String[] {line.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')};
	}
}
