package io.github.bradley09roberts.hardcorefriends.people;

import java.util.UUID;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Speciality;

/**
 * How well two people get on by nature, so friendships and romances differ from pair to pair instead of everyone
 * loving everyone equally. Personality comes from their archetype's numbers (two equally chatty people talk easily;
 * generous people are easy company) and their work (a farmer and someone whose interest is farming have plenty to
 * talk about), plus a little chemistry of their own that never changes for the pair (worked out from their UUIDs, so
 * it is the same after every restart). Attraction is separate: some pairs are simply good friends and never more.
 */
final class Compatibility {
	/** The lowest and highest compatibility. */
	static final double MIN = 0.6;
	static final double MAX = 1.4;
	/** About this share of pairs have no romantic spark at all. */
	private static final double NO_SPARK = 0.4;

	private Compatibility() {
	}

	/** How easily these two become friends: {@value #MIN} to {@value #MAX}, 1 for an average pair. */
	static double of(UUID a, FriendId ka, UUID b, FriendId kb) {
		double c = 1.0;
		c -= 0.3 * Math.abs(ka.chattiness() - kb.chattiness());
		c += 0.25 * ((ka.generosity() + kb.generosity()) / 2 - 0.65);
		if (Speciality.interest(ka) == kb.role() || Speciality.interest(kb) == ka.role()) {
			c += 0.15; // something in common to talk about
		} else if (ka.role() == kb.role()) {
			c += 0.05;
		}
		c += (chemistry(a, b, 0x5bd1e995L) - 0.5) * 0.3;
		return Math.clamp(c, MIN, MAX);
	}

	/**
	 * How strongly these two could fall for each other: 0 for pairs with no spark (about {@value #NO_SPARK} of them),
	 * otherwise about 0.6 to 1.6, more for a compatible pair.
	 */
	static double attraction(UUID a, FriendId ka, UUID b, FriendId kb) {
		double spark = chemistry(a, b, 0x9e3779b97f4a7c15L);
		if (spark < NO_SPARK) {
			return 0;
		}
		return of(a, ka, b, kb) * (0.5 + (spark - NO_SPARK) / (1 - NO_SPARK) * 0.7);
	}

	/** A number from 0 to 1 that is always the same for this pair (either way round) and this salt. */
	private static double chemistry(UUID a, UUID b, long salt) {
		long x = (a.getMostSignificantBits() ^ a.getLeastSignificantBits()) + (b.getMostSignificantBits() ^ b.getLeastSignificantBits());
		x ^= salt;
		x ^= x >>> 33;
		x *= 0xff51afd7ed558ccdL;
		x ^= x >>> 33;
		x *= 0xc4ceb9fe1a85ec53L;
		x ^= x >>> 33;
		return (x >>> 11) * 0x1.0p-53;
	}
}
