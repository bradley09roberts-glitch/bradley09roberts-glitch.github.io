package io.github.bradley09roberts.hardcorefriends.people;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Lines;

/**
 * What the friends say about living together: becoming good friends, asking someone out, a date, proposing, wedding
 * vows and toasts, baby news and a new baby, mourning a child or a partner, a quarrel and a break-up; and the lines only
 * children say (first words, playing, learning, bedtime, a fright, growing up), written as each trade's child would
 * say them, since a child otherwise speaks in their trade's grown-up voice. Each line is written plainly once and twice
 * in each of the nine voices. The usual rules for wording apply (see {@link Lines}).
 */
final class PeopleLines {
	private PeopleLines() {
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
		grownUps();
		family();
		children();
	}

	private static void grownUps() {
		line(Line.BECAME_FRIENDS,
			v("%1$s, I'm really glad we're friends.", "You know, %1$s, you're a proper friend.", "%1$s and me? Friends. Good ones."),
			v("Oh, %1$s, I'm so glad we've become such good friends.", "%1$s, dear, you've made this camp feel like home."),
			v("%1$s, you're a good friend. Solid as oak.", "We work well together, %1$s. Friends, then."),
			v("%1$s, I've decided you're my friend. Don't let it go to your head.", "Friends, %1$s? Fine. Yes. Friends."),
			v("%1$s! We're proper friends now! Best news all day!", "You're one of my favourite people, %1$s!"),
			v("%1$s! Friendship level: maximum! Well, nearly!", "We make a great team, %1$s! Best friends?"),
			v("%1$s, I'd stand by you in any fight. You're a friend.", "I trust you, %1$s. That's not a small thing."),
			v("A good friend is rare, %1$s. I'm glad I found you.", "%1$s, friendship grows slowly, and ours has grown well."),
			v("%1$s, you make every day a little brighter. Friends?", "We fit together nicely, %1$s, like flowers in a bed."),
			v("%1$s, you're a true friend. What's mine is yours.", "I'd share my last apple with you, %1$s. Friends!"));
		line(Line.ASK_OUT,
			v("%1$s, would you like to go out with me?", "I like you a lot, %1$s. Would you go out with me?",
				"%1$s... could we be more than friends?"),
			v("%1$s, I've grown very fond of you. Would you go out with me?", "Would you share a sunset with me, %1$s? Just us two?"),
			v("%1$s, I'll be plain: I like you. Will you go out with me?", "I've thought it through, %1$s. Would you go out with me?"),
			v("%1$s, this is scarier than a cave. Would you go out with me?", "So, %1$s. Would you, maybe, go out with me? No pressure."),
			v("%1$s! Want to go on adventures together? Like, together together?", "I've found something amazing, %1$s. It's you! Go out with me?"),
			v("%1$s, my heart's ticking like a redstone clock! Go out with me?", "Theory: we'd be brilliant together, %1$s. Shall we test it?"),
			v("%1$s, I'd like to walk beside you, not just guard you. Go out with me?", "Will you go out with me, %1$s? I'll keep you safe."),
			v("%1$s, I've listened to my heart closely. Would you go out with me?", "Some things are worth the risk, %1$s. Go out with me?"),
			v("%1$s, would you like to walk among the flowers with me? Just us?", "You make everything lovelier, %1$s. Will you go out with me?"),
			v("%1$s, I'd love to share more than supper with you. Go out with me?", "Would you go out with me, %1$s? I'll bring the berries."));
		line(Line.ACCEPT,
			v("Yes, %1$s! Of course!", "Oh, %1$s, yes!", "I'd love to, %1$s."),
			v("Oh, %1$s, yes! My heart's all aflutter.", "Yes, dear %1$s, with all my heart."),
			v("Yes, %1$s. That's settled, then. Happily.", "I'd like that very much, %1$s. Yes."),
			v("Yes, %1$s. Even better than finding diamonds.", "Oh. Yes, %1$s. Definitely yes. Wow."),
			v("Yes! Yes, %1$s! Best adventure ever!", "%1$s! Of course I will! Yes!"),
			v("Yes, %1$s! Every circuit in me says yes!", "Experiment approved, %1$s! Yes!"),
			v("Yes, %1$s. Always.", "I'd be honoured, %1$s. Yes."),
			v("Yes, %1$s. I had hoped you would ask.", "My answer is yes, %1$s. It was never in doubt."),
			v("Oh, %1$s, yes! How perfectly lovely.", "Yes, %1$s! I'm blushing like a rose."),
			v("Yes, %1$s! I'm the luckiest forager alive.", "Of course, %1$s! Yes, yes, yes!"));
		line(Line.DATE,
			v("Come on, %1$s, let's have a little time together.", "%1$s, shall we go for a walk?", "Just you and me for a while, %1$s."),
			v("Come and sit with me a while, %1$s. The day's work can wait.", "%1$s, let's watch the evening come in together."),
			v("Let's down tools for a bit, %1$s. Just us.", "%1$s, I've set aside some time for us. Come on."),
			v("%1$s, let's go somewhere with no creepers. Just us.", "Quiet evening, %1$s? My idea of a perfect date."),
			v("%1$s! I know the best spot! Follow me!", "Evening adventure, %1$s? Just the two of us!"),
			v("%1$s! Date time! I've scheduled it and everything!", "Let's take a break together, %1$s! The gadgets can wait!"),
			v("Walk with me, %1$s. I'll keep watch over us both.", "Let's take some time, %1$s. The camp is safe for now."),
			v("Let us share the evening, %1$s. Time well spent.", "%1$s, the light is lovely tonight. Come and see it with me."),
			v("%1$s, the sky's turning such pretty colours. Come with me?", "Let's find somewhere lovely, %1$s. Just us."),
			v("%1$s, I saved us something nice. Come and sit with me.", "Let's have a little time together, %1$s. Just us two."));
		line(Line.PROPOSE,
			v("%1$s, will you marry me?", "I want to spend every day with you, %1$s. Marry me?",
				"%1$s, would you make me the happiest person in the camp and marry me?"),
			v("%1$s, you're the sunshine on my fields. Will you marry me?", "Will you marry me, dear %1$s, and grow old with me?"),
			v("%1$s, I want to build a life with you. Will you marry me?", "I've never been surer of anything, %1$s. Marry me?"),
			v("%1$s, I've checked twice and I'm sure. Will you marry me?", "This is the bravest thing I've ever done. %1$s, marry me?"),
			v("%1$s, let's make every day an adventure together. Marry me?", "I've been all over the world, %1$s, and I choose you. Marry me?"),
			v("%1$s, you light me up like redstone! Will you marry me?", "I've invented lots of things, %1$s, but never this. Marry me?"),
			v("%1$s, I'll protect you all my days. Will you marry me?", "Will you marry me, %1$s? My heart is yours to keep."),
			v("%1$s, I've thought about it carefully, and I know. Marry me?", "Of all my plans, %1$s, this is the best. Will you marry me?"),
			v("%1$s, let's plant a garden that lasts forever. Marry me?", "You make my world beautiful, %1$s. Will you marry me?"),
			v("%1$s, I want to share everything with you. Will you marry me?", "Will you marry me, %1$s? I've never wanted anything more."));
		line(Line.WEDDING_VOWS,
			v("%1$s, I promise to love you and look after you, always.", "I promise you my whole heart, %1$s.",
				"Whatever comes, %1$s, we'll face it together."),
			v("%1$s, I'll care for you in every season, come rain or shine.", "I promise to keep you fed, warm and loved, %1$s. Always."),
			v("%1$s, I'll stand by you, steady as a good beam. Always.", "I promise to build every day with you, %1$s."),
			v("%1$s, I promise to keep you safe and make you laugh. Mostly safe.", "I'll face any cave with you, %1$s. Any cave at all."),
			v("%1$s, every adventure from now on is ours together. I promise.", "I promise to always come home to you, %1$s."),
			v("%1$s, I promise you a lifetime of love and lovely inventions.", "My heart's wired to yours forever, %1$s. I promise."),
			v("%1$s, I promise to guard your heart as I guard this camp.", "I will stand beside you all my days, %1$s. I promise."),
			v("%1$s, I promise to listen, to learn, and to love you always.", "Whatever the years bring, %1$s, I choose you. I promise."),
			v("%1$s, I promise to make our life as lovely as a garden.", "I'll tend our love like my favourite flowers, %1$s. Always."),
			v("%1$s, all I have is yours, and all my love too. I promise.", "I promise to share every day with you, %1$s."));
		line(Line.WEDDING_TOAST,
			v("To %1$s! Long life and happiness!", "Here's to %1$s! What a lovely day!", "Everyone, a cheer for %1$s!"),
			v("To %1$s! May your love grow like the best harvest.", "Here's to %1$s, dears. I'm so happy I could cry."),
			v("To %1$s. A good match, built to last.", "Here's to %1$s. Strong foundations, both of them."),
			v("To %1$s! May your caves be shallow and your torches bright.", "Here's to %1$s. Nobody blew up. Lovely day."),
			v("To %1$s! Here's to all your adventures together!", "Hooray for %1$s! Best day in the whole world!"),
			v("To %1$s! The best invention of all: true love!", "Three cheers for %1$s! Hip hip, hooray!"),
			v("To %1$s. May you always be safe and always together.", "Raise your cups for %1$s. A good day for us all."),
			v("To %1$s. A wise choice, made with the heart.", "Here's to %1$s. Love, patience and many happy years."),
			v("To %1$s! A love as lovely as spring blossom.", "Here's to %1$s! What a beautiful day for it."),
			v("To %1$s! May you never be short of anything.", "Here's to %1$s! Plenty of love and plenty of pie!"));
		line(Line.QUARREL,
			v("Oh, leave it, %1$s. I'm not in the mood.", "Must you, %1$s? Really?", "Not now, %1$s. Honestly."),
			v("%1$s, please, I'm too tired to argue.", "That's not fair, %1$s, and you know it."),
			v("That's not how it's done, %1$s, and you know it.", "Leave it, %1$s. I'm not in the mood."),
			v("Wonderful, %1$s. Another argument. My favourite.", "%1$s, I'm grumpy enough without your help."),
			v("Ugh, %1$s! You're being so annoying today!", "Fine, %1$s! Be like that!"),
			v("%1$s, that's completely wrong and you know it!", "Argh, %1$s! You never listen!"),
			v("Enough, %1$s. We're all tired.", "Let's not do this, %1$s. Not today."),
			v("That was unkind, %1$s. We should both rest.", "Let's not quarrel, %1$s. We'll regret it later."),
			v("%1$s, you've made a terrible muddle of my mood.", "Honestly, %1$s, could you not today?"),
			v("%1$s, I share everything and this is my thanks?", "Not now, %1$s. I'm cross and hungry."));
		line(Line.BREAK_UP,
			v("%1$s, I think we're better off as friends.", "I'm sorry, %1$s. This isn't working.", "Let's just be friends, %1$s."),
			v("%1$s, dear, I think we were happier as friends.", "I'm sorry, %1$s. My heart isn't in it any more."),
			v("%1$s, it's not working. Let's call it a day, as friends.", "I've thought it over, %1$s. We're better as friends."),
			v("%1$s, this isn't working. Like a pickaxe made of wool.", "Let's just be friends, %1$s. Safer for everyone."),
			v("%1$s, I think our paths go different ways now.", "Sorry, %1$s. Friends is better for us. Promise."),
			v("%1$s, the experiment didn't work. Friends again?", "Sorry, %1$s. We don't quite connect any more."),
			v("%1$s, I'll always look out for you. But as a friend.", "It's over, %1$s. No hard feelings."),
			v("%1$s, we've grown apart. Let's part kindly.", "I think we both know it, %1$s. Friends is wiser."),
			v("%1$s, we just don't bloom together any more.", "Sorry, %1$s. Let's be friends instead."),
			v("%1$s, I'm sorry. Friends, then? We'll still share supper.", "It's not working, %1$s. But I'm still here for you."));
	}

	private static void family() {
		line(Line.BABY_NEWS,
			v("%1$s and I are expecting a baby!", "Everyone, %1$s and I have news: a baby's on the way!",
				"A little one is coming! %1$s and I are so happy!"),
			v("Oh, everyone! %1$s and I are expecting a little one!", "A baby's coming, dears! %1$s and I are over the moon."),
			v("News, everyone: %1$s and I are expecting. I'll build a cot.", "%1$s and I are having a baby. Time to plan ahead."),
			v("%1$s and I are having a baby. I'm already worrying. Happily.", "A baby! %1$s and I are... yes. Happy. Very happy."),
			v("Guess what! %1$s and I are having a baby!", "%1$s and I have the best news ever! A baby's coming!"),
			v("%1$s and I are having a baby! Best project ever!", "Big news! %1$s and I are expecting! I'm so excited!"),
			v("%1$s and I are expecting. I'll keep you both safe.", "A child is coming. %1$s and I are very happy."),
			v("%1$s and I are expecting a child. A new chapter begins.", "News for the camp: %1$s and I are to have a baby."),
			v("%1$s and I are expecting! I'll make the nursery lovely.", "Oh, %1$s and I are having a baby! How wonderful!"),
			v("%1$s and I are expecting! One more to share with!", "A baby's coming! %1$s and I couldn't be happier!"));
		line(Line.BABY_ARRIVED,
			v("Everyone, meet little %1$s!", "Our baby's here! Say hello to %1$s!", "Welcome to the world, little %1$s."),
			v("Look, everyone! This is our little %1$s. So precious.", "Welcome home, %1$s, dear. You're safe with us."),
			v("This is %1$s. Our family's newest member.", "Meet %1$s, everyone. Well made, if I say so myself."),
			v("This is %1$s. Small, loud, and perfect.", "Meet %1$s. Already braver than me."),
			v("Everyone! Come and meet %1$s! Our baby's here!", "Hello, %1$s! You've got a whole world to explore!"),
			v("Introducing %1$s! Our greatest creation!", "%1$s is here! Look at those tiny hands!"),
			v("This is %1$s. I'll keep you safe always, little one.", "Meet %1$s. The camp has one more to protect."),
			v("This is %1$s. A small person with a big future.", "Welcome, %1$s. There is so much to teach you."),
			v("Meet little %1$s, everyone! Prettier than any flower.", "Welcome, %1$s! We'll plant a tree just for you."),
			v("Meet %1$s, everyone! One more to share with!", "Our baby %1$s is here! Come and say hello!"));
		line(Line.MOURN_CHILD,
			v("Oh, %1$s... my little one. Goodbye.", "We've lost %1$s. I'll never forget you.", "Rest now, %1$s. We love you always."),
			v("Oh, %1$s, my darling. I'll plant flowers for you every spring.", "My little %1$s... I'll love you always."),
			v("%1$s... I'll carve your name where everyone can see it.", "We've lost %1$s. I don't know how to mend this."),
			v("%1$s... I should have kept you safer. I'm so sorry.", "Goodbye, little %1$s. The camp is too quiet now."),
			v("%1$s... we had so many places left to see.", "Goodbye, %1$s. I'll name the brightest star after you."),
			v("%1$s... I'll build a lamp that never goes out, for you.", "Oh, %1$s. The camp's lights feel dimmer without you."),
			v("%1$s... I was meant to protect you. Rest now.", "I'll guard your memory, %1$s, all my days."),
			v("%1$s... some losses are too heavy for words.", "We will remember %1$s always. Every single day."),
			v("%1$s... I'll keep your favourite flowers growing forever.", "Oh, %1$s, my little bud. Goodbye."),
			v("%1$s... I'd give everything to have you back.", "Goodbye, little %1$s. We'll always keep a place for you."));
		line(Line.MOURN_PARTNER,
			v("%1$s... I can't believe you're gone.", "Goodbye, %1$s, my love.", "I'll carry on for both of us, %1$s."),
			v("Oh, %1$s, my love. The fields will never look the same.", "Goodbye, %1$s. I'll keep our home warm."),
			v("%1$s... we built so much together. I'll keep it standing.", "Goodbye, %1$s. I'll look after the family."),
			v("%1$s... you were the bravest person I knew.", "Goodbye, %1$s. It's very dark without you."),
			v("%1$s... no adventure will feel the same now.", "Goodbye, %1$s. You'll be in every sunset I see."),
			v("%1$s... I can't fix this. I wish I could.", "Goodbye, %1$s. You were my brightest spark."),
			v("%1$s... I'll keep watch for both of us now.", "Rest, %1$s. I'll look after everyone you loved."),
			v("%1$s... I'll hold on to every moment we had.", "Goodbye, %1$s. Love outlasts everything."),
			v("%1$s... I'll plant your favourite flowers by our door.", "Goodbye, my love. Our garden will remember you, %1$s."),
			v("%1$s... I'd have shared all my days with you.", "Goodbye, %1$s. I'll look after our family."));
	}

	/** Lines only children say: each trade's child, in a child's words. */
	private static void children() {
		line(Line.CHILD_FIRST_WORDS,
			v("Hello! Hello! Is this my home?", "Ooh! Everything's so big!", "Hi, everyone! I'm here!"),
			v("Hello! Can I help water the plants?", "Ooh, it smells like bread here!"),
			v("Hello! What are we building today?", "Is that a house? Can I build one too?"),
			v("Hello. Is it safe here? It looks safe.", "Hi. Are there any caves? I'm not going in them."),
			v("Hello! What's over there? And there? And there?", "Wow! Can I explore everything?"),
			v("Hello! What does that do? And that?", "Ooh, shiny! Can I press the button?"),
			v("Hello! I'll protect everyone! I'm very strong!", "Hi! Don't worry, I'm here now!"),
			v("Hello. Why is the sky blue?", "Hello! I have lots of questions."),
			v("Hello! Look at all the pretty flowers!", "Ooh, colours! Everything's so pretty!"),
			v("Hello! Does anyone want a cuddle?", "Hi! Can I share my snack with you?"));
		line(Line.CHILD_PLAY,
			v("Let's play %1$s!", "I love %1$s!", "Again! Let's play %1$s again!"),
			v("Come and play %1$s with me, please!", "%1$s is my favourite! After gardening!"),
			v("Let's play %1$s! I'll make the rules!", "Proper %1$s, everyone. Fair and square!"),
			v("%1$s. Fine. But nobody go near the caves.", "I'm good at %1$s. I'm careful, see."),
			v("%1$s! Bet you can't catch me!", "Wheee! %1$s is the best!"),
			v("%1$s! I've got a clever plan to win!", "Ooh, %1$s! Let's go, go, go!"),
			v("%1$s! I'm the guard, nobody gets past me!", "I'll win at %1$s! I'm the bravest!"),
			v("%1$s! I've worked out a winning plan.", "Let's play %1$s. I'm thinking very hard."),
			v("%1$s! But mind the flowers, everyone!", "Let's play %1$s by the pretty flowers!"),
			v("%1$s! Everyone can play, everyone!", "Come on, let's all play %1$s together!"));
		line(Line.CHILD_LEARN,
			v("Can I watch? I want to learn %1$s!", "Show me how %1$s works!", "When I'm big I'll be great at %1$s!"),
			v("Teach me %1$s, please! I'll be gentle.", "Is this how %1$s works? Ooh!"),
			v("Show me %1$s properly, step by step!", "I'm learning %1$s! Look how careful I am!"),
			v("I'm watching %1$s. From a safe distance.", "%1$s looks tricky. I'll watch very carefully."),
			v("Ooh, %1$s! Can I try? Can I? Please?", "I'm learning %1$s! This is so exciting!"),
			v("How does %1$s work? Why? What if...?", "%1$s! I'm taking notes in my head!"),
			v("I'll learn %1$s and help keep everyone safe!", "Watching %1$s. I'll be strong and helpful one day!"),
			v("I'm watching %1$s very closely. I notice things.", "Why do we do %1$s that way? I want to know."),
			v("%1$s looks so neat! Can I help tidy?", "I'm learning %1$s! I'll make it pretty."),
			v("I'm learning %1$s so I can help everyone!", "%1$s! When I'm big I'll share what I make!"));
		line(Line.CHILD_BEDTIME,
			v("Aww, already? Just five more minutes!", "Going home now. Night night!", "Is it bedtime? I'm not even tired."),
			v("Going home. I'll say goodnight to the flowers.", "Night night, everyone! Sweet dreams!"),
			v("Home time. Tidy up first, then bed.", "Going home now. Early to bed, early to build!"),
			v("Home before dark. That's the rule. Good rule.", "Going inside now. The dark is not my friend."),
			v("Aww, I wasn't finished exploring!", "Just one more look around? No? Fine. Home."),
			v("But I was just about to invent something!", "Bedtime? My brain's still buzzing!"),
			v("Going home. I'll guard my bed tonight!", "Home time. Everyone stay safe!"),
			v("Going home. I'll count the stars from the window.", "Bedtime already? The day went so quickly."),
			v("Home time. I'll dream about gardens.", "Night night, flowers! See you tomorrow!"),
			v("Going home. Does anyone want my last biscuit?", "Night night, everyone! Love you all!"));
		line(Line.CHILD_SCARED,
			v("Help! A monster!", "Eek! Wait for me!", "Monster! I'm scared!"),
			v("Help! Somebody, please!", "Eek! Where's my family?"),
			v("Help! It's getting closer!", "Monster! Grown-ups, help!"),
			v("Nope! Nope! Running away now!", "I knew it wasn't safe! Help!"),
			v("Whoa! Run, run, run!", "That's not a good adventure! Help!"),
			v("Aaah! Monster! Emergency!", "Help! That's not a friendly invention!"),
			v("I'm brave, but I'm getting help!", "Grown-ups! Monster! Over here!"),
			v("That's a monster. Running is the wise choice!", "Help! I'm going to find a grown-up!"),
			v("Eek! Keep it away from me!", "Help! It's horrible and scary!"),
			v("Help, everyone! A monster!", "Eek! Wait for me, please!"));
		line(Line.GROWN_UP,
			v("I'm all grown up! The camp's newest %1$s, at your service.", "Look at me! I'm the camp's new %1$s!",
				"Grown up at last! Time to work as the new %1$s."),
			v("I'm grown up! The camp's newest %1$s. I'll look after everyone.", "All grown up! I'll be a kind %1$s, I promise."),
			v("Grown up! Time to get to work as the new %1$s.", "I'm ready. One properly trained %1$s, reporting."),
			v("Grown up. Still careful. The camp's newest %1$s.", "I'm an adult now. A cautious %1$s. The best kind."),
			v("I'm grown up! The world's waiting for its newest %1$s!", "All grown up! Best %1$s ever, you'll see!"),
			v("Upgrade complete! Grown-up %1$s, online!", "I'm grown up! The camp's cleverest new %1$s!"),
			v("I'm grown now. I'll serve as the camp's %1$s and keep it safe.", "Grown up and ready. The camp's newest %1$s."),
			v("I'm grown now. I'll be a thoughtful %1$s.", "Childhood's lessons, put to use. The new %1$s is ready."),
			v("I'm all grown up! I'll be a tidy %1$s.", "Grown up at last! The camp's newest %1$s, neat and ready."),
			v("I'm grown up! The camp's newest %1$s, here to help.", "All grown up! What I make, I'll share. A generous %1$s!"));
	}
}
