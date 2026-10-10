package io.github.bradley09roberts.hardcorefriends.life;

import java.util.List;

import net.minecraft.util.RandomSource;

/**
 * The tunes a musician knows: old folk songs and nursery tunes, everyone's to play, written for a note block. A note
 * is a note block's own number (0 is F sharp, 12 the F sharp an octave up, 24 the top) and a length in half beats.
 */
final class Tunes {
	/** Ticks a half beat lasts (a beat is about 0.4 seconds). */
	static final int HALF_BEAT = 4;

	/** One tune: its name and its notes, each {note, half beats}. */
	record Tune(String name, int[][] notes) {
	}

	// Note numbers: G3 1, A3 3, B3 5, C4 6, D4 8, E4 10, F4 11, F#4 12, G4 13, G#4 14, A4 15, B4 17, C5 18, D5 20, E5 22, F5 23.
	private static final Tune TWINKLE = new Tune("Twinkle, Twinkle, Little Star", new int[][] {
		{6, 2}, {6, 2}, {13, 2}, {13, 2}, {15, 2}, {15, 2}, {13, 4},
		{11, 2}, {11, 2}, {10, 2}, {10, 2}, {8, 2}, {8, 2}, {6, 4},
		{13, 2}, {13, 2}, {11, 2}, {11, 2}, {10, 2}, {10, 2}, {8, 4},
		{13, 2}, {13, 2}, {11, 2}, {11, 2}, {10, 2}, {10, 2}, {8, 4},
		{6, 2}, {6, 2}, {13, 2}, {13, 2}, {15, 2}, {15, 2}, {13, 4},
		{11, 2}, {11, 2}, {10, 2}, {10, 2}, {8, 2}, {8, 2}, {6, 4}});
	private static final Tune FRERE_JACQUES = new Tune("Frere Jacques", new int[][] {
		{6, 2}, {8, 2}, {10, 2}, {6, 2}, {6, 2}, {8, 2}, {10, 2}, {6, 2},
		{10, 2}, {11, 2}, {13, 4}, {10, 2}, {11, 2}, {13, 4},
		{13, 1}, {15, 1}, {13, 1}, {11, 1}, {10, 2}, {6, 2}, {13, 1}, {15, 1}, {13, 1}, {11, 1}, {10, 2}, {6, 2},
		{6, 2}, {1, 2}, {6, 4}, {6, 2}, {1, 2}, {6, 4}});
	private static final Tune ODE_TO_JOY = new Tune("Ode to Joy", new int[][] {
		{10, 2}, {10, 2}, {11, 2}, {13, 2}, {13, 2}, {11, 2}, {10, 2}, {8, 2},
		{6, 2}, {6, 2}, {8, 2}, {10, 2}, {10, 3}, {8, 1}, {8, 4},
		{10, 2}, {10, 2}, {11, 2}, {13, 2}, {13, 2}, {11, 2}, {10, 2}, {8, 2},
		{6, 2}, {6, 2}, {8, 2}, {10, 2}, {8, 3}, {6, 1}, {6, 4}});
	private static final Tune GREENSLEEVES = new Tune("Greensleeves", new int[][] {
		{15, 2}, {18, 4}, {20, 2}, {22, 3}, {23, 1}, {22, 2}, {20, 4}, {17, 2},
		{13, 3}, {15, 1}, {17, 2}, {18, 4}, {15, 2}, {15, 3}, {14, 1}, {15, 2}, {17, 4}, {14, 2}, {10, 4},
		{15, 2}, {18, 4}, {20, 2}, {22, 3}, {23, 1}, {22, 2}, {20, 4}, {17, 2},
		{13, 3}, {15, 1}, {17, 2}, {18, 3}, {17, 1}, {15, 2}, {14, 3}, {12, 1}, {14, 2}, {15, 6}});

	static final List<Tune> ALL = List.of(TWINKLE, FRERE_JACQUES, ODE_TO_JOY, GREENSLEEVES);

	private Tunes() {
	}

	static Tune pick(RandomSource random) {
		return ALL.get(random.nextInt(ALL.size()));
	}
}
