package io.github.bradley09roberts.hardcorefriends.pets;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Lines;

/**
 * What the friends say about their pets and Scout's maps: adopting and naming a pet, playing with it, calling it over,
 * losing it, a map finished, a copy handed over and a map that cannot be made for want of paper. Each line is written
 * plainly once and twice in each of the nine voices (children speak in their trade's voice). The usual rules for
 * wording apply (see {@link Lines}): British English, at most 90 characters, only the placeholders the line documents.
 */
final class PetsLines {
	private PetsLines() {
	}

	/** One line: its plain wording, then the nine voices in {@link FriendId} order (Fern to Rowan). */
	private static void line(Line line, String[] generic, String[]... voices) {
		Lines.defineGeneric(line, generic);
		FriendId[] ids = FriendId.values();
		for (int i = 0; i < ids.length && i < voices.length; i++) {
			Lines.define(ids[i], line, voices[i]);
		}
	}

	private static String[] v(String... variants) {
		return variants;
	}

	static void register() {
		pets();
		maps();
	}

	private static void pets() {
		line(Line.PET_ADOPTED,
			v("Everyone, meet %1$s! My very own %2$s.", "I'm calling my new %2$s %1$s.", "This is %1$s. We're going to be best friends."),
			v("Oh, look at you, %1$s. You're my %2$s now, and I'll look after you.", "A %2$s of my own! I'll call you %1$s, sweetheart."),
			v("Right, %1$s, you're with me now. Good %2$s.", "A %2$s needs a proper name. %1$s it is."),
			v("I've got a %2$s now. %1$s. Don't tell the creepers.", "Meet %1$s. A %2$s with better sense than most miners."),
			v("A %2$s! My own %2$s! I'm calling you %1$s, adventurer!", "%1$s, you and me are going to explore everything!"),
			v("New team member: %1$s the %2$s! Fluffier than any circuit!", "%1$s! Brilliant name for a brilliant %2$s!"),
			v("%1$s will be my %2$s now. I'll keep you safe, little one.", "Stay close, %1$s. A good %2$s and a good guard."),
			v("I shall call this %2$s %1$s. It suits, don't you think?", "A %2$s chose me today. Welcome, %1$s."),
			v("Isn't %1$s lovely? The prettiest %2$s in the camp.", "%1$s, you'll look perfect among the flowers. My %2$s!"),
			v("I traded a snack for a friend! Meet %1$s, my %2$s.", "%1$s, my %2$s. I'll always share my supper with you."));
		line(Line.PET_PLAY,
			v("Who's a good %2$s? You are, %1$s!", "Come on, %1$s! Race you!", "%1$s, you silly %2$s. Come here."),
			v("There's my lovely %1$s. Have you been good today?", "Gently, %1$s. You're a dear %2$s, aren't you?"),
			v("Good %2$s, %1$s. Steady now.", "%1$s, sit. Good. Now we're getting somewhere."),
			v("%1$s, if you find any diamonds, they're mine.", "Best %2$s in the camp, %1$s. Low bar, mind."),
			v("%1$s! Let's go and find something amazing!", "Fetch, %1$s! Quick! Explorers never rest!"),
			v("%1$s, I timed you: fastest %2$s ever recorded!", "Good %2$s, %1$s! Your tail's like a wiggly lever!"),
			v("Easy, %1$s. Stay by me.", "Good %2$s, %1$s. Keep your ears open for trouble."),
			v("Watch %1$s, everyone. A %2$s notices everything.", "%1$s, you are a very wise %2$s."),
			v("%1$s, mind the flowerbeds, please!", "Come and sit in the sun with me, %1$s."),
			v("%1$s, I saved you a treat. Don't tell the others.", "Good %2$s, %1$s! Best friend a forager could have."));
		line(Line.PET_CALL,
			v("%1$s! Here, %1$s!", "Come on, %1$s! This way!", "%1$s! Where've you got to?"),
			v("%1$s! Come here, sweetheart!", "%1$s, dear! Come along now!"),
			v("%1$s! Here. Now, please.", "%1$s! With me!"),
			v("%1$s! Here! Before something eats you.", "%1$s! Come here. Carefully."),
			v("%1$s! Over here! Come on!", "%1$s! Adventure time! This way!"),
			v("%1$s! Here, boy! Or girl! Here!", "Calling %1$s! Come in, %1$s!"),
			v("%1$s. To me.", "%1$s! Stay close to me."),
			v("%1$s. Come, please.", "%1$s, come and sit by me."),
			v("%1$s! Come and see the garden!", "Here, %1$s! This way, lovely!"),
			v("%1$s! I've got a treat for you!", "%1$s! Here! Snack time!"));
		line(Line.PET_LOST,
			v("Oh, %1$s... I'll miss you, my %2$s.", "Goodbye, %1$s. You were the best %2$s.", "%1$s is gone. I can't believe it."),
			v("My poor %1$s. Rest now, sweet %2$s.", "Oh, %1$s. I'll never forget you, dear %2$s."),
			v("%1$s was a good %2$s. The best.", "I'll not forget you, %1$s. Not ever."),
			v("%1$s... that's not fair. Not fair at all.", "I'll miss you, %1$s. Best %2$s there was."),
			v("Oh, %1$s... no more adventures together.", "%1$s was the bravest %2$s in the world."),
			v("%1$s is gone. No invention can fix this.", "I'll miss my %2$s, %1$s. Everything's quieter now."),
			v("I couldn't protect %1$s. I'm sorry, little %2$s.", "Rest easy, %1$s. You were a loyal %2$s."),
			v("%1$s has gone. A %2$s's life is short but dear.", "Farewell, %1$s. You were well loved."),
			v("I'll plant flowers for %1$s. My sweet %2$s.", "Oh, %1$s. The garden won't be the same."),
			v("%1$s... I'll always save a treat for you.", "Goodbye, %1$s. Best %2$s a forager ever had."));
	}

	private static void maps() {
		line(Line.MAP_FINISHED,
			v("Finished! A map of %1$s.", "There: %1$s, all drawn out.", "My map of %1$s is done!"),
			v("I've finished drawing %1$s. Isn't it lovely?", "A map of %1$s, all done. Come and see, dears."),
			v("Map of %1$s: finished. Neat and square.", "That's %1$s mapped. Good, careful work."),
			v("A map of %1$s. Now we know where not to fall in.", "Done: %1$s, mapped. Every hole marked."),
			v("Done! A map of %1$s! Every hill and river!", "My map of %1$s is finished! Look at it!"),
			v("Map complete: %1$s! Accurate to the block, nearly!", "Finished mapping %1$s! Data is beautiful!"),
			v("A map of %1$s. Good for planning the watch.", "%1$s, mapped. Now I know where trouble can come from."),
			v("A map of %1$s. Knowing the land is half the battle.", "I've studied the map of %1$s. Most useful."),
			v("A map of %1$s! The colours are so pretty.", "Finished: %1$s, all the greens and blues."),
			v("A map of %1$s! I can see all the berry spots.", "Done! %1$s on one sheet. Very handy."));
		line(Line.MAP_HANDED,
			v("Here you go, %1$s: a copy of my map.", "A map for you, %1$s. Don't get lost!", "Take this copy, %1$s."),
			v("Here, %1$s, a copy of the map. Keep it safe, dear.", "A map for you, %1$s. Mind how you go."),
			v("Copy of the map, %1$s. Drawn straight and true.", "Here, %1$s. One map, as asked."),
			v("A map, %1$s. Try not to walk off a cliff.", "Here's a copy, %1$s. Holes are the dark bits."),
			v("Here, %1$s! My map! Go and explore!", "A copy for you, %1$s! Adventure awaits!"),
			v("Map copy delivered, %1$s! Zero errors!", "Here you go, %1$s! Fresh off the drawing board!"),
			v("A map for you, %1$s. Know the land, stay safe.", "Here, %1$s. Plan your way before you go."),
			v("A copy of the map, %1$s. Study it well.", "Here, %1$s. A map is a wise companion."),
			v("A map for you, %1$s. Isn't it pretty?", "Here, %1$s, a copy. I helped with the colours!"),
			v("A map for you, %1$s! I've drawn in the berry bushes.", "Here, %1$s, take a copy. Share and share alike!"));
		line(Line.MAP_NO_PAPER,
			v("I can't make a map: the chest is short of paper or a compass.", "No paper or compass to spare for a map, sorry.",
				"A map needs eight paper and a compass. We haven't got them."),
			v("I'm sorry, there's not enough paper or a compass for a map.", "We'll need more paper and a compass for a map, dear."),
			v("Short of paper or a compass. No map until we have both.", "Eight paper and a compass make a map. We're short."),
			v("No paper, no compass, no map. Simple.", "I'd draw a map, but there's no paper or compass."),
			v("I can't draw a map! We need paper and a compass!", "Out of paper or compass! Sugar cane, anyone?"),
			v("Map making halted: missing paper or a compass.", "Insufficient paper or compass for a map, sorry!"),
			v("There's no paper or compass for a map just now.", "We need paper and a compass before I can draw one."),
			v("Without paper and a compass, no map can be drawn.", "We lack the paper or compass for a map, I'm afraid."),
			v("No paper or compass for a map, I'm afraid.", "I'd love to help, but there's no paper or compass."),
			v("No paper or compass for a map. I'll look for sugar cane!", "Can't make a map, sorry: we need paper and a compass."));
	}
}
