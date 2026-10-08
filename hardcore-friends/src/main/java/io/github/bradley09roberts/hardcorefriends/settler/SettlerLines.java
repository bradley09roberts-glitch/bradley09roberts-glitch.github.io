package io.github.bradley09roberts.hardcorefriends.settler;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Lines;

/**
 * What strangers and travellers say: hellos, the request they make before joining, "not yet", joining, a full team,
 * and a traveller arriving and leaving. Every newcomer speaks in the voice of the friend whose trade they share, so
 * each line is written once for each of the nine voices (and once plainly). The usual rules for wording apply (see
 * {@link Lines}).
 */
final class SettlerLines {
	private SettlerLines() {
	}

	static void register() {
		generic();
		voice(FriendId.FERN,
			new String[] {"Hello, dear. You look like you could use a hot meal.", "Oh, a visitor! Have you eaten today?"},
			new String[] {"I'd love to help feed your camp. Could you bring me %1$s first?",
				"Bring me %1$s, dear, and I'll grow you more than you can eat."},
			new String[] {"Not yet, dear. I still need %1$s.", "Nearly! Just %1$s more and I'll pack my things."},
			new String[] {"Thank you, %1$s! Let's go and feed everyone.", "Oh, %1$s, you're a dear. I'll never let you go hungry."},
			new String[] {"You've plenty of mouths to feed already, dear. Another time.", "Your camp is full. I'll be here if things change."},
			new String[] {"Hello! I've walked so far. Might I rest by your fields?", "Oh, a camp! Is anyone cooking?"},
			new String[] {"I'd best be going. Look after each other, and eat well.", "Time to go. Thank you for letting me rest."});
		voice(FriendId.OAK,
			new String[] {"Hello there. Got a minute for a chat?", "Afternoon. You look like someone with plans."},
			new String[] {"I'll build for your camp. I'll need %1$s to make a start.", "Bring me %1$s and we'll talk foundations."},
			new String[] {"Not enough yet. Still short of %1$s.", "Close. I still need %1$s before I can start."},
			new String[] {"Thanks, %1$s. Show me the site.", "Good, %1$s. Let's build something that lasts."},
			new String[] {"No room in the plans for me yet. Another time.", "Your camp's at capacity. I'll wait."},
			new String[] {"Hello, camp. Solid work here. Mind if I stay a day?",
				"Good day. I've walked a long way. I'll rest here, if that's alright."},
			new String[] {"Right. Time I moved on. Good build, this.", "I'll be off. Keep those walls in good repair."});
		voice(FriendId.FLINT,
			new String[] {"Oh. A person. Not a zombie. Good start.", "Hello. You're not here to dig straight down, are you?"},
			new String[] {"I'll dig for you. But first, %1$s. I don't mine in the dark.",
				"Bring me %1$s and I'm in. No lava jokes, please."},
			new String[] {"Still missing %1$s. I counted. Twice.", "Not yet. I'm still waiting on %1$s. Patiently."},
			new String[] {"Right, %1$s. I'm in. Lead the way. Carefully.", "Thanks, %1$s. I'll bring the torches."},
			new String[] {"Full up? Fair enough. I'll stay here. Safely.", "No room. That's fine. I'm good at waiting."},
			new String[] {"Hello. I've been walking for ages. My feet have opinions.", "Mind if I sit down? It's been a long road."},
			new String[] {"Right. Back on the road. Wish me luck. And no lava.", "Off I go. It was nice not being eaten here."});
		voice(FriendId.SCOUT,
			new String[] {"Hey there! Where did you come from?", "Oh, hello! New faces are the best!"},
			new String[] {"Take me with you! Just bring me %1$s for the road!",
				"I'll explore for your camp! Can you find %1$s for me first?"},
			new String[] {"Almost! I still need %1$s!", "Not yet! Can you find %1$s?"},
			new String[] {"Brilliant, %1$s! Adventure starts now!", "Thanks, %1$s! I can't wait to see your camp!"},
			new String[] {"Oh, no room? I'll keep an eye out for you anyway!", "Full already? Ask me again some other time!"},
			new String[] {"Hello, camp! I followed the smoke!", "Wow, a real camp! Mind if I stay for a day?"},
			new String[] {"Time to explore somewhere new! Bye, everyone!", "Thanks for having me! Off to see what's over the hill!"});
		voice(FriendId.SPARK,
			new String[] {"Oh! Hello! Do you like gadgets? Everyone should like gadgets!", "Hi! Hi! Come and talk, I've got ideas!"},
			new String[] {"Ooh, a team! Bring me %1$s and I'll build you wonders!", "I'm in! Well, nearly! I just need %1$s first!"},
			new String[] {"So close! Just %1$s more!", "Not yet! I still need %1$s! Then off we go!"},
			new String[] {"YES! Thanks, %1$s! So many projects!", "Brilliant, %1$s! Let's go and invent things!"},
			new String[] {"Full?! Oh well! I'll keep inventing here for now!", "No room? Never mind! Maybe next time!"},
			new String[] {"Hello! Is this a camp? It IS a camp! Brilliant!", "Hi! I walked for days! Can I stay a bit?"},
			new String[] {"Off I go! So many places to tinker!", "Bye! Thanks for the rest! Must dash!"});
		voice(FriendId.AEGIS,
			new String[] {"Halt. Friend, I take it?", "Well met. You're safe here."},
			new String[] {"I'll guard your camp. Bring me %1$s.", "Find me %1$s and I'll stand with you."},
			new String[] {"Not yet. I need %1$s.", "Still short of %1$s."},
			new String[] {"Thank you, %1$s. You have my sword.", "I'm with you, %1$s. Nothing gets past me."},
			new String[] {"Your camp is full. I'll guard this place instead.", "No room. Another time, then."},
			new String[] {"Peace, friends. I only need a day's rest.", "Hello, camp. I mean no harm. I'll rest a while."},
			new String[] {"I must go. Stay safe.", "Farewell. Guard each other well."});
		voice(FriendId.SAGE,
			new String[] {"Ah, a traveller. Come, let's talk a while.", "Good day. I had a feeling someone would come."},
			new String[] {"I'd gladly join you. A small request first: %1$s.",
				"Bring me %1$s, and I'll help you plan for every danger."},
			new String[] {"Patience. I still need %1$s.", "Not quite. There's still %1$s to find."},
			new String[] {"Thank you, %1$s. Let's plan ahead together.", "A wise choice, %1$s. I'll serve your camp well."},
			new String[] {"A full camp is a busy camp. Another time, perhaps.", "No room for now. Patience will serve us both."},
			new String[] {"Greetings. I've travelled far. May I rest here a day?", "Good day. A camp, at last. May I stay a while?"},
			new String[] {"My road goes on. Farewell, and plan wisely.", "Thank you for the rest. I must be on my way."});
		voice(FriendId.TERRA,
			new String[] {"Hello! Isn't this a lovely spot?", "Oh, hello! Mind the flowers, please."},
			new String[] {"I'd love to make your camp beautiful. Could you bring me %1$s?",
				"Bring me %1$s and I'll make your camp the prettiest around."},
			new String[] {"Not yet, I'm afraid. I still need %1$s.", "Almost there! Just %1$s to go."},
			new String[] {"Thank you, %1$s! Let's make your camp lovely.", "How kind, %1$s! I'll bring a touch of beauty."},
			new String[] {"No room? That's alright. I'll tend my garden here.", "Your camp is full. Another time, perhaps."},
			new String[] {"Oh, hello! What a lovely camp. May I rest here?", "Hello! I've walked so far. This spot looks perfect."},
			new String[] {"Goodbye! Keep the camp lovely for me.", "Time I wandered on. Thank you for the rest."});
		voice(FriendId.ROWAN,
			new String[] {"Hello, stranger! Need anything? I might have spare.", "Hi there! Come and say hello."},
			new String[] {"I'll gather for your camp. Could you spare %1$s to get me started?",
				"Bring me %1$s and I'll share everything I find."},
			new String[] {"Not yet, friend. I still need %1$s.", "Nearly there! Still %1$s to find."},
			new String[] {"Thanks, %1$s! What's mine is the camp's now.", "Cheers, %1$s! I'll gather whatever we need."},
			new String[] {"No room? No matter. Take care of yourself.", "Full up? That's alright. Come back if things change."},
			new String[] {"Hello, camp! I've a few stories to share, if you'll have me.",
				"Hi there! Mind if I rest a day? I'll not be any trouble."},
			new String[] {"I'll be on my way. Share what you have, won't you?", "Thanks for the welcome! Off I go."});
	}

	private static void generic() {
		Lines.defineGeneric(Line.STRANGER_HELLO, "Oh, hello there!", "Hello, traveller. Come and talk if you like.",
			"Good day to you.");
		Lines.defineGeneric(Line.STRANGER_ASKS, "I'd like to join your camp. Could you bring me %1$s first?",
			"If you can find %1$s for me, I'll come with you.", "Bring me %1$s and you can count on me.");
		Lines.defineGeneric(Line.STRANGER_NOT_YET, "Not quite yet. I still need %1$s.", "Nearly there! Just %1$s to go.",
			"I'm still hoping for %1$s.");
		Lines.defineGeneric(Line.STRANGER_JOINS, "Thank you, %1$s! I'm with you now.", "Right, %1$s, let's go home together.",
			"Thanks, %1$s. I won't let you down.");
		Lines.defineGeneric(Line.STRANGER_TEAM_FULL, "Your camp is full up for now. Maybe another time.",
			"There's no room for me just yet. I'll wait.", "You've enough mouths to feed for now.");
		Lines.defineGeneric(Line.WANDERER_ARRIVES, "Hello, camp! Mind if I rest here a while?", "Hello there! I've walked a long way.",
			"Good day! Is there room by your fire?");
		Lines.defineGeneric(Line.WANDERER_LEAVES, "Well, I'd best be on my way. Take care.", "Time I moved on. Good luck to you all.",
			"Thanks for the rest. Farewell.");
	}

	/** One voice's wording for every settler line, in the order of the parameters. */
	private static void voice(FriendId friend, String[] hello, String[] asks, String[] notYet, String[] joins, String[] full,
			String[] arrives, String[] leaves) {
		Lines.define(friend, Line.STRANGER_HELLO, hello);
		Lines.define(friend, Line.STRANGER_ASKS, asks);
		Lines.define(friend, Line.STRANGER_NOT_YET, notYet);
		Lines.define(friend, Line.STRANGER_JOINS, joins);
		Lines.define(friend, Line.STRANGER_TEAM_FULL, full);
		Lines.define(friend, Line.WANDERER_ARRIVES, arrives);
		Lines.define(friend, Line.WANDERER_LEAVES, leaves);
	}
}
