package io.github.bradley09roberts.hardcorefriends.market;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Lines;

/**
 * What the friends say at their trades: opening the shop, thanking a customer, taking up a trade, fishing, baking,
 * shearing the flock, taking honey, teaching, seeing to a patient, and the rest of the trades' work. Each line is
 * written plainly once and twice in each of the nine voices; newcomers speak with their archetype's. The usual rules
 * for wording apply (see {@link Lines}).
 */
final class MarketLines {
	private MarketLines() {
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
		shop();
		trades();
	}

	private static void shop() {
		line(Line.SHOP_OPEN,
			v("Open for trade at %1$s!", "Come and see what we have at %1$s.", "Morning! Trading at %1$s now."),
			v("Open for business at %1$s! Do come in, dear.", "Come and see what we have at %1$s today. All fresh!"),
			v("Open at %1$s. Fair prices, honest goods.", "Trading at %1$s now. Step up when you're ready."),
			v("Open at %1$s. No refunds on cave-ins.", "Trading at %1$s. Bring emeralds, not trouble."),
			v("Open at %1$s! Come and see what I've got!", "Roll up, roll up! Trading at %1$s!"),
			v("Shop's powered up at %1$s! Come and trade!", "Open for business at %1$s! Best deals in the village!"),
			v("Open at %1$s. Trade in peace, friends.", "Trading at %1$s. I'll keep an eye on things."),
			v("Open at %1$s. A fair trade serves us all.", "Trading at %1$s today. Come, let us strike a bargain."),
			v("Open at %1$s! I've tidied the shelves for you.", "Welcome to %1$s! Everything's neatly laid out."),
			v("Open at %1$s! There's plenty to go round.", "Come to %1$s! I've got good things to share."));
		line(Line.SHOP_SALE,
			v("Thank you, %1$s! Come again.", "A pleasure doing business, %1$s.", "Good trade, %1$s. Thank you!"),
			v("Thank you, %1$s! Enjoy it, dear.", "Lovely doing business with you, %1$s."),
			v("Fair trade, %1$s. Thank you.", "Done and dusted, %1$s. Come again."),
			v("Pleasure, %1$s. Mind how you go.", "Deal done, %1$s. That went suspiciously well."),
			v("Thanks, %1$s! Come back soon!", "Great trade, %1$s! Off you go on your adventures!"),
			v("Transaction complete, %1$s! Brilliant!", "Thanks, %1$s! The village coffers say thank you too!"),
			v("Thank you, %1$s. Travel safely.", "A fair exchange, %1$s. Stay safe out there."),
			v("A fair exchange, %1$s. Thank you.", "Trade builds trust, %1$s. Thank you."),
			v("Thank you, %1$s! Do come again.", "Lovely, %1$s! I'll wrap it up nicely next time."),
			v("Thanks, %1$s! Share it with a friend!", "Good trading, %1$s. What goes around comes around!"));
		line(Line.TRADE_TAKEN,
			v("I'm the village's %1$s now. I'll do my best!", "Me, the village's %1$s? Gladly!",
				"Right, I'm the new %1$s. Let's get to work."),
			v("The village's %1$s? Oh, I'd be delighted.", "I'll be the best %1$s I can be, I promise."),
			v("The new %1$s? Right. I'll do it properly.", "I'll take the %1$s's job. Steady work suits me."),
			v("The village's %1$s? Fine. Someone sensible has to.", "Me, the %1$s? I'll try not to blow anything up."),
			v("I'm the %1$s now! What an adventure!", "The village's new %1$s? Brilliant! Let's go!"),
			v("Village %1$s? I've already got ideas!", "The new %1$s! I'll invent a better way to do it!"),
			v("I'll serve as the village's %1$s, gladly.", "The %1$s's duty is mine now. I won't let you down."),
			v("The village's %1$s. A worthy calling.", "I accept the %1$s's work. It serves us all."),
			v("The village's %1$s! I'll keep it all lovely.", "Me, the new %1$s? I'll make it beautiful."),
			v("The village's %1$s! I'll share every bit.", "The new %1$s? Happy to give it a go for everyone!"));
	}

	private static void trades() {
		line(Line.FISHING,
			v("Casting a line. Here, fishy fishy.", "A bit of quiet fishing.", "Off to see what's biting."),
			v("A quiet spot of fishing. How peaceful.", "Let's see if the fish are hungry today, dear."),
			v("Line cast. Now we wait.", "Fishing. Steady hands, steady patience."),
			v("Fishing. Safer than mining, mostly.", "If I catch a boot again, I'm keeping it."),
			v("What's under the water? Let's find out!", "Casting off! Here's hoping for a big one!"),
			v("Fishing rod deployed! Waiting for a signal!", "Line in! Calculating the perfect catch!"),
			v("Fishing. I'll still watch the bank.", "A calm task. The water's quiet today."),
			v("Fishing teaches patience. Let us wait.", "The river provides, in its own time."),
			v("Such a pretty spot for fishing.", "The water's so still. Lovely."),
			v("Fish for everyone! Let's see what bites.", "A good catch feeds the whole camp."));
		line(Line.BAKING,
			v("Something lovely in the oven.", "Baking for the village.", "Fresh bread on the way."),
			v("Fresh from the oven, just how everyone likes it.", "Mmm, smell that bread. Baking's such a joy."),
			v("Baking. Measure twice, bake once.", "Loaves in. Right on schedule."),
			v("Baking. Hot work, but nothing explodes.", "Bread's in. Don't touch the oven."),
			v("Baking! The whole village will smell it!", "What shall I bake today? Something exciting!"),
			v("Oven at optimal temperature! Baking!", "Baking is just chemistry you can eat!"),
			v("Baking. A fed village is a strong one.", "Bread for the watch, and everyone else."),
			v("Baking bread. Simple work, great comfort.", "Good bread keeps a village well."),
			v("Pies with pretty crusts, coming up.", "Baking, and keeping the kitchen spotless."),
			v("Baking plenty, so everyone gets a share!", "Warm bread for all. Nothing better!"));
		line(Line.SHEARING_FLOCK,
			v("Time to shear the flock.", "Wool for the village, coming up.", "Easy now, sheep. Just a trim."),
			v("There, there, sheep. Just a little trim.", "The flock's wool is lovely and thick today."),
			v("Shearing the flock. Wool for beds.", "Steady now. Clean cuts."),
			v("Shearing. The sheep complain less than miners.", "Wool. Soft, safe, no rockfalls."),
			v("Shearing time! Fluffy sheep everywhere!", "Look at all that wool! Let's go!"),
			v("Wool harvest! Efficient snip technology!", "Sheep shearing, maximum fluff output!"),
			v("Shearing the flock. Gently does it.", "A calm flock is a well-kept flock."),
			v("The flock gives freely. Shear with care.", "Wool, grown again with time. A good harvest."),
			v("Such soft wool! It'll make lovely carpets.", "A tidy trim for every sheep."),
			v("Wool to share! Thank you, sheep!", "Plenty of wool for everyone's beds."));
		line(Line.HONEY,
			v("Honey from the hives.", "Gently, bees. Just a little honey.", "The hives are full of honey."),
			v("Sweet honey from the hives. Thank you, bees.", "Smoke's up, bees are calm. Lovely."),
			v("Fire lit underneath, bees calm. Taking the honey.", "Honey. Collected properly, no stings."),
			v("Honey. Carefully. Very carefully.", "Smoked hive, calm bees. No stings today, please."),
			v("Honey! Sticky, golden, amazing!", "The bees have been busy! Honey time!"),
			v("Hive at full capacity! Collecting honey!", "Smoke plus hive equals calm bees. Science!"),
			v("Taking the honey. The bees stay calm.", "Steady. The smoke keeps the bees peaceful."),
			v("The bees share their work with us. Gratefully.", "Honey, patiently made. Patiently gathered."),
			v("The bees love our flowers. Honey time!", "Golden honey from our lovely garden."),
			v("Honey for everyone! Bees are so generous.", "Sweet honey to share. Thank you, bees!"));
		line(Line.TEACHING,
			v("Settle down, everyone. Lesson time.", "Gather round, children. Let's learn.", "Today's lesson starts now."),
			v("Gather round, little ones. Let's learn about growing things.", "Settle down, dears. Lesson time."),
			v("Right, class. Today we learn to measure twice.", "Settle down. Good work starts with good lessons."),
			v("Lesson one: never dig straight down.", "Class, today's lesson is caution. Pay attention."),
			v("Class! Today we explore the world on a map!", "Who wants to learn about faraway places?"),
			v("Today's lesson: how redstone works! Pay attention!", "Class! Prepare to be amazed by science!"),
			v("Today we learn how to stay safe, children.", "Settle down. Lessons keep you safe."),
			v("Gather round, children. Wisdom awaits.", "A lesson today is a strength tomorrow."),
			v("Today we'll draw flowers, children!", "Neat handwriting, everyone. Let's begin."),
			v("Today we learn which berries are good to eat!", "Gather round! Sharing is today's lesson."));
		line(Line.HEALING,
			v("Hold still, %1$s. This will help.", "Let me see to you, %1$s.", "Drink this, %1$s. You'll feel better."),
			v("Oh, %1$s, let me look after you.", "There, there, %1$s. This will help, dear."),
			v("Hold still, %1$s. Let's patch you up.", "Right, %1$s. Take this. Doctor's orders."),
			v("You look terrible, %1$s. Drink this.", "Drink this, %1$s. And stop getting hurt."),
			v("Hang on, %1$s! I've got just the thing!", "Quick, %1$s, drink this! You'll be fine!"),
			v("Medical assistance for %1$s, coming up!", "Drink this, %1$s! Restorative formula!"),
			v("I've got you, %1$s. Take this.", "Rest easy, %1$s. You're safe now."),
			v("Be still, %1$s. Healing takes a moment.", "This will help you mend, %1$s."),
			v("Let me tidy you up, %1$s.", "There we go, %1$s. Good as new soon."),
			v("Here, %1$s, have this. It'll help.", "I've saved this for you, %1$s. Get well!"));
		line(Line.TRADE_WORK,
			v("Busy %1$s for the village.", "Off to work: %1$s.", "Right then, %1$s."),
			v("Busy %1$s, dear. Everyone needs a hand.", "Just %1$s. Good honest work."),
			v("Busy %1$s. Proper job.", "On with it: %1$s."),
			v("Busy %1$s. Nothing's caught fire yet.", "Just %1$s. Carefully."),
			v("Busy %1$s! Never a dull moment!", "Off I go, %1$s!"),
			v("Busy %1$s! Efficiency maximised!", "Just %1$s, but brilliantly!"),
			v("Busy %1$s. Steady work.", "On duty: %1$s."),
			v("Busy %1$s. Every task has its place.", "Now, %1$s. Patiently."),
			v("Busy %1$s, nice and neatly.", "Just %1$s. Tidy as you go!"),
			v("Busy %1$s for everyone!", "Just %1$s. It's all for sharing!"));
	}
}
