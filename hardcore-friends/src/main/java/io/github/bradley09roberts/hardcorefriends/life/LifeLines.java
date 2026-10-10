package io.github.bradley09roberts.hardcorefriends.life;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Lines;

/**
 * What the friends say about village life: the feasts (the morning's news, a few words, cheering, the cook's call, the
 * end), the winter lights, market day, birthdays, music, funerals, mourning and visiting a grave, making a grave, and
 * writing the Chronicle. Each line is written plainly once and twice in each of the nine voices; newcomers speak with
 * their archetype's. Gentle and family friendly, even at a funeral. The usual rules for wording apply (see
 * {@link Lines}).
 */
final class LifeLines {
	private LifeLines() {
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
		feasts();
		market();
		birthdays();
		funerals();
	}

	private static void feasts() {
		line(Line.FESTIVAL_BEGINS,
			v("It's %1$s today! See you this evening.", "Don't forget, it's %1$s today!", "Today's %1$s. Everyone's invited!"),
			v("It's %1$s today! I'll make sure there's plenty to eat.", "Oh, it's %1$s today, dear! Come along this evening."),
			v("It's %1$s today. Finish your jobs in good time, everyone.", "Today's %1$s. I'll get my work done early."),
			v("It's %1$s today. I might even smile.", "Today's %1$s. Don't eat it all before I get there."),
			v("It's %1$s today! Best day of the year!", "Guess what? It's %1$s today! Don't be late!"),
			v("It's %1$s today! I've been counting down the days!", "Today's %1$s! I wonder what we'll have to eat!"),
			v("It's %1$s today. I'll keep the village safe for it.", "Today's %1$s. Enjoy yourselves; I'll watch the edges."),
			v("It's %1$s today. Good to mark the turning of the year.", "Today is %1$s. Days like these hold us together."),
			v("It's %1$s today! I'll make the square look lovely.", "Today's %1$s! Everything should look its best."),
			v("It's %1$s today! I'll bring something to share.", "Today's %1$s! There'll be plenty to go round."));
		line(Line.FESTIVAL_SPEECH,
			v("Welcome, everyone, to %1$s. Here's to all of us!", "Thank you all for coming to %1$s. Let's enjoy it together."),
			v("Welcome to %1$s, everyone. Eat well, there's plenty for all.", "Thank you for coming to %1$s. Look how far we've come."),
			v("Welcome to %1$s. We built all this together. Well done, everyone.", "Thanks for coming to %1$s. Good work this year, all."),
			v("Welcome to %1$s. We're all still here, and that's worth a cheer.", "A short speech for %1$s: well done, everyone. Now eat."),
			v("Welcome to %1$s! Here's to every adventure still to come!", "Thanks for coming to %1$s! What a year it's been!"),
			v("Welcome to %1$s! Here's to all our bright ideas!", "Thanks for coming to %1$s! This village runs on all of you!"),
			v("Welcome to %1$s. Rest easy tonight; you're among friends.", "Thank you for coming to %1$s. Every one of you matters."),
			v("Welcome to %1$s. Let's remember all we've built, and those we've lost.",
				"At %1$s we give thanks: for the harvest, and for each other."),
			v("Welcome to %1$s! Doesn't the village look beautiful tonight?", "Thank you for coming to %1$s. We made this place lovely."),
			v("Welcome to %1$s! Share what you have and enjoy every bite!", "Thanks for coming to %1$s! Nobody goes home hungry tonight."));
		line(Line.FESTIVAL_CHEER,
			v("What a lovely evening!", "This is wonderful.", "I love a good feast."),
			v("Have you tried the bread? I baked it with love.", "Everyone's eating well tonight. That makes me happy."),
			v("Good to put the tools down for an evening.", "Now this is a proper village gathering."),
			v("Not bad, this. Not bad at all.", "I'll admit it: I'm enjoying myself."),
			v("Best night ever! Who wants to hear a story?", "I could dance all night!"),
			v("This is brilliant! We should do this every week!", "Look at everyone! Isn't it amazing?"),
			v("It's good to see everyone happy and safe.", "A peaceful evening. Just what we needed."),
			v("Evenings like this are what we work for.", "Look around. This is what a village is."),
			v("Everything looks so pretty tonight.", "What a lovely, lovely evening."),
			v("There's plenty! Have some more!", "Sharing a meal with friends. Nothing better."));
		line(Line.FESTIVAL_END,
			v("Thank you all for %1$s. Good night!", "That's the end of %1$s. Sleep well, everyone.", "What a day. Thank you for %1$s!"),
			v("Thank you all for %1$s. Sleep well, my dears.", "That's the end of %1$s. I hope everyone ate their fill."),
			v("That's the end of %1$s. Early start tomorrow, everyone.", "Thanks for %1$s, all. Good work. Good night."),
			v("That's the end of %1$s. Back to the grindstone tomorrow.", "Thanks for %1$s. I'm off before someone makes me dance."),
			v("That's the end of %1$s! Same time next year!", "Thanks for %1$s, everyone! Sweet dreams!"),
			v("That's the end of %1$s! Already planning the next one!", "Thanks for %1$s! Best night ever, officially!"),
			v("That's the end of %1$s. Go home safely, everyone.", "Thank you all for %1$s. I'll see everyone home."),
			v("That's the end of %1$s. Remember tonight.", "Thank you all for %1$s. Rest well."),
			v("That's the end of %1$s. Wasn't it beautiful?", "Thank you for %1$s, everyone. Sleep sweetly."),
			v("That's the end of %1$s! Take the leftovers home!", "Thanks for %1$s, everyone. Good night, friends!"));
		line(Line.FEAST_SERVING,
			v("Come and get it! There's food for everyone.", "Here you go, eat up!", "Dinner's served!"),
			v("Here you are, dear. Eat up, there's plenty.", "Come and eat! I made sure there's enough for all."),
			v("Food's ready. One each, then seconds.", "Dinner's up. Help yourselves."),
			v("Food's here. Try not to fight over it.", "Dinner. Eat it before it gets cold."),
			v("Food! Come and get it while it's hot!", "Dig in, everyone! It smells amazing!"),
			v("Dinner is served! Perfectly cooked, if I say so myself!", "Food's ready! Come and try it!"),
			v("Here, eat. You've all earned it.", "Food for everyone. Take your share."),
			v("Eat well. A shared meal is a shared strength.", "Here you are. There's enough for all of us."),
			v("Dinner's served, nicely laid out!", "Come and eat! Doesn't it look lovely?"),
			v("Here, take some! There's plenty to share!", "Eat up, everyone! Seconds for anyone who wants them!"));
		line(Line.LIGHTS_UP,
			v("The winter lights are lit!", "There, the lights are up. Isn't it pretty?"),
			v("The winter lights are lit. Doesn't it feel cosy?", "There we are, lights all round the square."),
			v("Lights are up. Neat and evenly spaced.", "That's the winter lights in place."),
			v("Lights are up. Less dark, fewer surprises.", "The winter lights are lit. Nothing's sneaking up on us tonight."),
			v("The lights are up! It looks magical!", "Look at the square glowing! Amazing!"),
			v("Winter lights: on! Look at that glow!", "Lights are up! Brilliant, literally!"),
			v("The lights are lit. The square is safe and bright.", "Winter lights are up. A bright night is a safe night."),
			v("The winter lights are lit. Light in the longest night.", "There. A little light against the dark."),
			v("The winter lights are up! Isn't it beautiful?", "Lights all round the square. So pretty!"),
			v("The lights are up! Light for everyone!", "The square's all lit up for the winter lights!"));
		line(Line.MUSIC_PLAY,
			v("A tune for the evening!", "Time for a little music.", "Here's a song for everyone."),
			v("A gentle tune for the evening, my dears.", "Let me play you something soothing."),
			v("A bit of music after a day's work.", "Here's a tune. Simple and steady."),
			v("Fine. One song. Maybe two.", "Here's a tune. Don't sing along."),
			v("A traveller's tune, coming up!", "Here's a song I picked up on the road!"),
			v("Note block ready! Time for music!", "Listen to this tune I worked out!"),
			v("A quiet tune for a quiet evening.", "Some music, while I keep an eye out."),
			v("Music, to end the day well.", "An old tune. Listen closely."),
			v("A pretty tune for a pretty evening.", "Let me play something lovely."),
			v("A song to share with everyone!", "Gather round, here's a tune!"));
		line(Line.CHRONICLE_WRITING,
			v("Let me write today's news in the Chronicle.", "Time to update the Village Chronicle.", "Writing it all down for the Chronicle."),
			v("Let me write the news in the Chronicle, so we remember.", "A few lines for the Village Chronicle."),
			v("Updating the Chronicle. Every day on record.", "Writing up the Chronicle. Neat and tidy."),
			v("Writing it all down. Someone has to.", "The Chronicle. Facts only."),
			v("Writing our latest adventures in the Chronicle!", "The Chronicle needs today's story!"),
			v("Updating the Chronicle! Recording history!", "Time to write it all down. For the record!"),
			v("Recording the day in the Chronicle.", "A few lines in the Chronicle, for those who come after."),
			v("Let me record the village's story.", "History matters. Writing in the Chronicle."),
			v("Writing in the Chronicle in my neatest hand.", "A pretty page for the Village Chronicle."),
			v("Writing the news in the Chronicle, for everyone.", "The Chronicle! Stories to share for years."));
	}

	private static void market() {
		line(Line.MARKET_DAY,
			v("Market day! Come to %1$s!", "It's market day at %1$s. Have a look!", "Step up to %1$s, it's market day!"),
			v("Market day! Fresh things at %1$s, dear.", "Come and see %1$s. Everything's lovely and fresh!"),
			v("Market day. Good, honest goods at %1$s.", "Open for market day at %1$s. Fair prices."),
			v("Market day at %1$s. No haggling, please.", "It's market day. Come to %1$s. Or don't."),
			v("Market day! Come and see %1$s!", "Roll up! It's market day at %1$s!"),
			v("Market day! Brilliant bargains at %1$s!", "Come to %1$s! Market day deals!"),
			v("Market day at %1$s. Trade in peace.", "Come to %1$s. It's market day."),
			v("Market day at %1$s. A fair trade serves us all.", "It's market day. Come and see %1$s."),
			v("Come and see %1$s, all nicely laid out!", "Market day at %1$s! Doesn't it look lovely?"),
			v("Market day at %1$s! There's plenty to share!", "Come to %1$s! Market day, good things for all!"));
		line(Line.MARKET_BROWSE,
			v("Let's see what's on the stalls today.", "I love market day.", "Anything good at the market?"),
			v("Let's see if there's anything nice at the market.", "Market day! I might find some seeds."),
			v("Market day. I'll take a look round.", "Let's see what the stalls have."),
			v("Market day. I'll just look, thanks.", "Let's see what everyone's selling. Probably bread."),
			v("Market day! Let's see what's new!", "Ooh, look at all the stalls!"),
			v("Market day! Anything I could tinker with?", "Let's see what's on offer today!"),
			v("Market day. A busy, happy square.", "I'll take a stroll round the stalls."),
			v("Market day. A good way to see how the village is doing.", "Let's see what the stalls tell us about our stores."),
			v("Market day! The stalls look so pretty.", "Let's browse the stalls for a while."),
			v("Market day! Let's see what everyone's sharing.", "I love market day. So many good things!"));
	}

	private static void birthdays() {
		line(Line.BIRTHDAY_WISH,
			v("Happy birthday, %1$s!", "Many happy returns, %1$s!", "Happy birthday to you, %1$s!"),
			v("Happy birthday, %1$s! I hope your day is lovely, dear.", "Many happy returns, %1$s! You deserve a treat."),
			v("Happy birthday, %1$s. Another good year built.", "Many happy returns, %1$s."),
			v("Happy birthday, %1$s. Another year survived. Well done.", "Happy birthday, %1$s. Don't let it go to your head."),
			v("Happy birthday, %1$s! Here's to more adventures!", "It's %1$s's birthday? Hooray!"),
			v("Happy birthday, %1$s! Best birthday ever, I calculate!", "Happy birthday, %1$s! I'll invent you a present!"),
			v("Happy birthday, %1$s. I'm glad you're with us.", "Many happy returns, %1$s. Stay safe this year."),
			v("Happy birthday, %1$s. Another year wiser.", "Many happy returns, %1$s. You make us stronger."),
			v("Happy birthday, %1$s! You look lovely today!", "Happy birthday, %1$s! I'll pick you some flowers."),
			v("Happy birthday, %1$s! Let's share something sweet!", "Many happy returns, %1$s! What a treat to have you."));
		line(Line.BIRTHDAY_THANKS,
			v("Thank you, everyone!", "You remembered! Thank you.", "What a lovely birthday."),
			v("Oh, thank you, my dears. What a lovely surprise.", "You remembered! Thank you, everyone."),
			v("Thanks, everyone. Means a lot.", "Another year. Thank you, all."),
			v("Thanks. I suppose birthdays aren't so bad.", "You remembered. That's... nice. Thank you."),
			v("Thank you! Best birthday ever!", "You're all the best! Thank you!"),
			v("Thank you! This is brilliant!", "You remembered! I'm so happy!"),
			v("Thank you, friends. I'm glad to be here.", "Thank you. It's good to have you all."),
			v("Thank you. Another year, and every one a gift.", "Thank you, all. I'm grateful for each of you."),
			v("Thank you! What a lovely birthday!", "Oh, you're all so sweet. Thank you!"),
			v("Thank you! Let's all share the day together!", "You're all so kind. Thank you, everyone!"));
	}

	private static void funerals() {
		line(Line.FUNERAL_WORDS,
			v("We're here to remember %1$s. We will never forget.", "Today we say goodbye to %1$s. Rest well."),
			v("We're here to remember %1$s. Such kind hearts. We'll miss you.", "Goodbye, %1$s. We'll keep your memory growing."),
			v("We're here for %1$s. They helped build all of this.", "We remember %1$s today. What we built, we built together."),
			v("We're here for %1$s. Hard to find the words. We'll miss you.", "Goodbye, %1$s. It won't be the same without you."),
			v("We remember %1$s today. What a journey we shared.", "Goodbye, %1$s. Every road I walk, I'll think of you."),
			v("We remember %1$s today. You made everything brighter.", "Goodbye, %1$s. Some things can't be fixed. We'll miss you."),
			v("We're here for %1$s. I'll carry their memory with me.", "Goodbye, %1$s. Rest now. We'll keep watch."),
			v("We gather to remember %1$s. They live on in all we do.", "Goodbye, %1$s. A life well lived is never lost."),
			v("We remember %1$s. I'll keep flowers here always.", "Goodbye, %1$s. You made this place more beautiful."),
			v("We remember %1$s. They gave so much to all of us.", "Goodbye, %1$s. We'll share our stories of you for ever."));
		line(Line.FUNERAL_FAREWELL,
			v("Goodbye, %1$s.", "Rest well, %1$s.", "We'll miss you, %1$s."),
			v("Rest well, %1$s. I'll bring you flowers.", "Goodbye, %1$s. Sleep peacefully."),
			v("Goodbye, %1$s. Your work stands all around us.", "Rest easy, %1$s."),
			v("Goodbye, %1$s. You were one of the good ones.", "Rest well, %1$s. I'll keep the mine safe."),
			v("Goodbye, %1$s. See you over the next hill.", "Rest well, %1$s. I'll tell your stories."),
			v("Goodbye, %1$s. I'll miss your ideas.", "Rest well, %1$s. The village shines because of you."),
			v("Rest now, %1$s. Your watch is over.", "Goodbye, %1$s. I'll guard what you loved."),
			v("Goodbye, %1$s. You'll be remembered.", "Rest in peace, %1$s."),
			v("Goodbye, %1$s. I'll plant something beautiful for you.", "Rest well, %1$s. I'll keep this place tidy for you."),
			v("Goodbye, %1$s. Thank you for everything you shared.", "Rest well, %1$s. We'll always set a place for you."));
		line(Line.FUNERAL_CARE,
			v("We'll all look after %1$s.", "%1$s won't ever be alone."),
			v("Don't you worry, %1$s. We'll all look after you.", "%1$s, you have a whole village to love you."),
			v("We'll look after %1$s. That's a promise.", "%1$s will have a home here, always."),
			v("Nobody's leaving %1$s on their own. Not while I'm here.", "We'll look after %1$s. All of us."),
			v("%1$s, you've got all of us now.", "We'll look after %1$s, every single day."),
			v("%1$s, we're your family too. Always.", "We'll take care of %1$s. I promise."),
			v("I'll keep %1$s safe. You have my word.", "%1$s is under all our protection now."),
			v("%1$s will be raised by all of us, with love.", "We'll look after %1$s together."),
			v("%1$s, you're never alone here.", "We'll make sure %1$s is always cared for."),
			v("%1$s, everything we have is yours too.", "We'll all look after %1$s. Always."));
		line(Line.MOURNING,
			v("I keep thinking about %1$s.", "I miss %1$s.", "It's quiet without %1$s."),
			v("I keep setting a place for %1$s.", "I miss %1$s. The fields feel emptier."),
			v("I keep expecting %1$s to come round the corner.", "I miss %1$s. Work feels heavier."),
			v("Still can't believe %1$s is gone.", "Miss you, %1$s. Quietly."),
			v("I keep looking for %1$s on the road.", "I miss %1$s. Adventures aren't the same."),
			v("I wanted to show %1$s my new idea.", "I miss %1$s. Nothing feels as bright."),
			v("I wish I'd been there for %1$s.", "I miss %1$s. I'll keep everyone safe for them."),
			v("Grief takes time. I miss %1$s.", "%1$s would want us to carry on."),
			v("I planted something for %1$s today.", "I miss %1$s. Everything reminds me of them."),
			v("I saved some berries for %1$s, out of habit.", "I miss %1$s. I'd share anything to have them back."));
		line(Line.GRAVE_VISIT,
			v("Hello, %1$s. Just came to see you.", "We miss you, %1$s.", "Thinking of you, %1$s."),
			v("Hello, %1$s. The crops are doing well. I thought you'd like to know.", "I came to sit with you a while, %1$s."),
			v("Hello, %1$s. The village is growing. You'd be proud.", "Came to see you, %1$s. All's well."),
			v("Hello, %1$s. Just checking in.", "It's me, %1$s. Still here. Still miss you."),
			v("Hello, %1$s! I've got so many stories for you.", "Came back to see you, %1$s."),
			v("Hello, %1$s. I finished that idea we talked about.", "Came to tell you the news, %1$s."),
			v("Hello, %1$s. Everyone's safe. I promise.", "I'm here, %1$s. Keeping watch, as always."),
			v("Hello, %1$s. We remember you every day.", "A quiet moment with you, %1$s."),
			v("Hello, %1$s. I've come to tidy your flowers.", "I came to make things pretty for you, %1$s."),
			v("Hello, %1$s. I wish I could share this day with you.", "Came to see you, %1$s. We're all thinking of you."));
		line(Line.GRAVE_MADE,
			v("I've made a resting place for %1$s.", "There. %1$s's grave is ready.", "A stone for %1$s, so we never forget."),
			v("I've made a resting place for %1$s. I'll keep flowers by it.", "There. A quiet place for %1$s, under the sky."),
			v("Made a headstone for %1$s. Built to last.", "There. %1$s's grave is ready. Solid stone."),
			v("A stone for %1$s. Good, sturdy stone.", "There. %1$s has a resting place now."),
			v("I've made a resting place for %1$s, with a view.", "There. %1$s's grave is ready."),
			v("I've made a stone for %1$s. Simple, but true.", "There. %1$s's name is written for all to see."),
			v("A resting place for %1$s. I'll keep it safe.", "There. %1$s's grave is ready. Rest now."),
			v("A stone for %1$s, so the village remembers.", "There. %1$s's name will be read for years to come."),
			v("I've made %1$s a lovely resting place.", "There. %1$s's grave, with flowers, just so."),
			v("I've made a resting place for %1$s.", "There. A place for %1$s, for all of us to visit."));
	}
}
