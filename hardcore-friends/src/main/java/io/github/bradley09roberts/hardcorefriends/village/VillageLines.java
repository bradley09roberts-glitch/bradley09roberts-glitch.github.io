package io.github.bradley09roberts.hardcorefriends.village;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Lines;

/**
 * What the friends say about their village: moving into a home of their own, a new street, the town hall finished,
 * going home for the evening, asking a friend round, and the village growing into a Town or a City. Each line is
 * written plainly once and twice in each of the nine voices; the usual rules for wording apply (see {@link Lines}).
 * House names arrive in lower case ("oak cottage", "acacia house"), so lines say "our" or "the" before them, never "a".
 */
final class VillageLines {
	private VillageLines() {
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
		line(Line.MOVED_IN,
			v("Home at last: our own %1$s.", "The %1$s is ours now. I could get used to this.", "Moving into the %1$s today. Home sweet home!"),
			v("Our own %1$s! I'll put flowers by the door straight away.", "Oh, the %1$s feels like home already."),
			v("The %1$s is built well. Proud to live in it.", "Moved into the %1$s. Every beam's sound. I checked."),
			v("The %1$s. Four walls, a roof, no cave-ins. Perfect.", "A home with a door I can shut. The %1$s will do nicely."),
			v("Home base: the %1$s! Best camp I've ever had!", "I've explored everywhere, and the %1$s is my favourite spot!"),
			v("The %1$s! I've got plans for a doorbell already!", "My own %1$s! Room for gadgets and everything!"),
			v("The %1$s is ours. I'll keep it safe.", "A home to guard: the %1$s. That means a great deal."),
			v("A home of one's own, the %1$s. A good season's work.", "The %1$s. Every village begins with a hearth."),
			v("The %1$s is lovely. It needs a garden, mind.", "Our %1$s! I'll plant something pretty by the step."),
			v("The %1$s! Come round any time, there'll be berries.", "Moved into the %1$s. What's mine is yours, neighbours."));
		line(Line.NEW_STREET,
			v("We've got a new street: %1$s!", "%1$s is open. The village is growing.", "Building starts on %1$s."),
			v("%1$s, how lovely. Room for gardens all along it.", "A new street, %1$s. The village grows like a good crop."),
			v("%1$s is marked out. The plots are square and level.", "New street: %1$s. Plenty of building to do."),
			v("%1$s. Above ground, as streets should be.", "They've opened %1$s. Fine, as long as it doesn't go downhill."),
			v("%1$s is open! New places to explore!", "A whole new street, %1$s! Race you to the end!"),
			v("%1$s! We should light it all the way along!", "New street: %1$s! I'm drawing up lamp plans already!"),
			v("%1$s is open. I'll walk it tonight.", "A new street, %1$s. More homes to watch over."),
			v("%1$s is laid out. A sound plan, well kept.", "With %1$s, the village takes its proper shape."),
			v("%1$s! I'll lay the path nice and straight.", "A new street, %1$s. It wants flowers along the verge."),
			v("%1$s is open! I'll bring the neighbours apples.", "New street, %1$s! More doors to knock on with treats."));
		line(Line.TOWN_HALL_DONE,
			v("The town hall is finished! We're a proper village now.", "Our town hall stands at last.", "The town hall is done. What a sight!"),
			v("The town hall is finished. It feels like a real home now.", "Oh, our town hall! A place for everyone to gather."),
			v("Town hall's finished. Best work we've done yet.", "The town hall stands. Square, level and sound."),
			v("A town hall. Next we'll be having meetings. Lovely.", "The town hall's done. Big enough to shelter everyone, at least."),
			v("The town hall's done! It's the biggest thing I've seen!", "Look at the town hall! I want to see the view from the top!"),
			v("Town hall complete! I wonder if it needs a clock tower!", "The town hall's finished! We could hold invention fairs in it!"),
			v("The town hall stands. A heart worth defending.", "Our town hall is finished. I'll keep it safe."),
			v("The town hall stands. A village needs a place to decide together.", "With the town hall finished, we are a true community."),
			v("The town hall's finished. Now it needs window boxes.", "Our town hall! It's beautiful, it really is."),
			v("The town hall's done! Let's have a feast inside!", "Town hall finished! I'll bring the first basket of food."));
		line(Line.GOING_HOME,
			v("Time to head home.", "Home for the evening.", "That's the day done. Home I go."),
			v("Home for the evening. Supper and a warm bed.", "The day's done, dear. Time to go home."),
			v("Tools down. Home for the night.", "Good day's work. Home now."),
			v("Home, before anything comes out of the dark.", "Day's done. Home has a door. Home it is."),
			v("Home time! I'll tell everyone about my day!", "Off home! Last one there's a slowcoach!"),
			v("Home! I've got a gadget waiting on the table!", "Evening already? Home I go, ideas and all!"),
			v("Heading home. Stay close to the lights, everyone.", "Home for the evening. The watch will see us through."),
			v("The day is done. Home, to think it over.", "Evening comes. A wise friend goes home."),
			v("Home for the evening. My window box needs water.", "The sun's going down. Home it is."),
			v("Home time! I've saved some berries for supper.", "Off home now. Come round if you're hungry!"));
		line(Line.INVITE_OVER,
			v("%1$s, come round to mine this evening!", "Fancy coming over tonight, %1$s?", "My door's open, %1$s. Come for the evening."),
			v("%1$s, come round for supper. I've made something nice.", "Come and sit with us tonight, %1$s."),
			v("%1$s, come and see the house. Proper job, it is.", "Come round this evening, %1$s. There's a seat for you."),
			v("%1$s, come over. My house has walls and everything.", "Fancy a quiet evening at mine, %1$s? No caves involved."),
			v("%1$s! Come over, I'll tell you all my stories!", "Come round tonight, %1$s! I've got maps to show you!"),
			v("%1$s! Come over, I'll show you my latest gadget!", "Evening at mine, %1$s? I've got something to demonstrate!"),
			v("%1$s, come round tonight. You'll be safe at mine.", "Join us this evening, %1$s. Good company is welcome."),
			v("%1$s, would you share the evening with us? There's much to discuss.", "Come round tonight, %1$s. The fire is warm."),
			v("%1$s, come and see my garden this evening!", "Come round tonight, %1$s. I've picked fresh flowers."),
			v("%1$s, come round! I've plenty of food to share.", "Supper at mine, %1$s? There's more than enough."));
		line(Line.VILLAGE_GROWS,
			v("We're a %1$s now! Look how far we've come.", "From one campfire to a %1$s. Well done, everyone!", "A %1$s! Who'd have thought it?"),
			v("A %1$s! It all grew from one little farm plot.", "We're a %1$s now. I'm so proud of us all."),
			v("A %1$s. Built block by block, by us.", "We're a %1$s now. Good foundations pay off."),
			v("A %1$s. And not one of us lives in a cave.", "We're a %1$s now. Still careful, mind."),
			v("A %1$s! I can't wait to see how big it gets!", "We're a whole %1$s! Best adventure ever!"),
			v("A %1$s! Think of the redstone we could build now!", "We're a %1$s! Progress, progress, progress!"),
			v("A %1$s now. More to protect, and more to be proud of.", "We've become a %1$s. I'll guard it with everything."),
			v("A %1$s. Patience and planning, rewarded.", "We are a %1$s now. This day will be remembered."),
			v("A %1$s! It needs flowers on every corner.", "We're a %1$s now, and a pretty one too."),
			v("A %1$s! Let's share a feast to celebrate!", "We're a %1$s! Something for everyone, always."));
	}
}
