package io.github.bradley09roberts.hardcorefriends.companion;

import java.util.EnumMap;

/**
 * Every friend's wording for every {@link Line}. Each friend has at least three variants per line, written in
 * their own voice; {@link Speech} picks one at random and fills the placeholders documented on the line.
 *
 * <p>Rules for the table: British English, under 90 characters before substitution, family friendly, never
 * about mods or code, and suited to a Hardcore world without being grim. A line only uses the placeholders its
 * {@link Line} constant documents, and lines without arguments contain no {@code %} at all. Arguments that may
 * arrive as whole sentences (advice, Scout's report, discoveries) always come last, after a colon, so their own
 * capitals and full stops read naturally. A generic fallback keeps {@link #get} from ever returning nothing.
 */
public final class Lines {
	private static final EnumMap<FriendId, EnumMap<Line, String[]>> TABLE = new EnumMap<>(FriendId.class);
	private static final EnumMap<Line, String[]> FALLBACK = new EnumMap<>(Line.class);
	private static final String[] LAST_RESORT = {"Hmm."};

	static {
		generic();
		fern();
		oak();
		flint();
		scout();
		spark();
		aegis();
		sage();
		terra();
		rowan();
	}

	private Lines() {
	}

	/**
	 * The variants {@code friend} can say for {@code line}: their own if written, otherwise the generic ones.
	 * Never null or empty. The returned array is a copy and may be modified by the caller.
	 */
	public static String[] get(FriendId friend, Line line) {
		if (line != null) {
			EnumMap<Line, String[]> own = friend == null ? null : TABLE.get(friend);
			String[] variants = own == null ? null : own.get(line);
			if (variants != null && variants.length > 0) {
				return variants.clone();
			}
			String[] generic = FALLBACK.get(line);
			if (generic != null && generic.length > 0) {
				return generic.clone();
			}
		}
		return LAST_RESORT.clone();
	}

	private static void put(FriendId friend, Line line, String... variants) {
		TABLE.computeIfAbsent(friend, f -> new EnumMap<>(Line.class)).put(line, variants);
	}

	private static void generic(Line line, String... variants) {
		FALLBACK.put(line, variants);
	}

	/** Plain, voice-neutral wording used when a friend has no line of their own. */
	private static void generic() {
		generic(Line.RECRUITED, "Glad to join you, %1$s.", "Hello, %1$s. Let's look after each other.",
			"Thanks, %1$s. I'm ready to help.");
		generic(Line.FOLLOW, "Following you, %1$s.", "Right behind you, %1$s.", "Lead the way, %1$s.");
		generic(Line.STAY, "Staying here.", "I'll wait right here.", "Holding this spot.");
		generic(Line.WORK, "Back to work.", "Back to my jobs.", "Right, back to it.");
		generic(Line.DISMISSED, "Goodbye, then. Stay safe.", "Take care of yourself.", "Farewell, friends.");
		generic(Line.GREETING, "Hello, %2$s. I'm %1$s.", "Hi, %2$s. Just %1$s.", "Good to see you, %2$s. I'm %1$s.");
		generic(Line.THANKS_FOOD, "Thank you.", "Thanks, that helps.", "Much appreciated.");
		generic(Line.THANKS_GIFT, "Thank you for the %1$s.", "The %1$s? Thank you.", "Every %1$s helps. Thanks.");
		generic(Line.RETREAT, "I'm hurt. Falling back.", "Pulling back to recover.", "I need a moment. Backing off.");
		generic(Line.RECOVERED, "Feeling better now.", "All healed. Back to it.", "That's better.");
		generic(Line.CREEPER, "Creeper! Stay back!", "Creeper nearby! Move away!", "Watch out, a creeper!");
		generic(Line.FIGHT, "Back off, %1$s!", "Not today, %1$s!", "Clear off, %1$s!");
		generic(Line.WARNING, "%1$s, careful: %2$s!", "Watch out, %1$s: %2$s!", "%1$s, heads up: %2$s!");
		generic(Line.FRIEND_DIED, "Goodbye, %1$s. We'll miss you.", "Rest well, %1$s.", "We've lost %1$s.");
		generic(Line.PLAYER_HURT, "%1$s, you're badly hurt! Heal up.", "%1$s, step back and eat something!",
			"%1$s, you need to rest!");
		generic(Line.NIGHT_RETURN, "It's getting dark. Heading back to camp.", "Dusk. Time to go home.",
			"Night's coming. Back to camp.");
		generic(Line.MORNING, "Good morning.", "Morning, everyone.", "A new day.");
		generic(Line.WORK_START, "I'll be %1$s.", "Time for %1$s.", "Starting on it: %1$s.");
		generic(Line.NEED_TOOL, "I need a tool: %1$s.", "Any %1$s going spare?", "Tool needed: %1$s.");
		generic(Line.TOOL_BROKE, "My %1$s broke.", "There goes my %1$s.", "My %1$s is worn out.");
		generic(Line.NEED_MATERIALS, "I need %1$s.", "I'm short of %1$s.", "Can anyone bring %1$s?");
		generic(Line.DEPOSIT, "Supplies are in the chest.", "Chest stocked.", "All stored away.");
		generic(Line.SHARE, "Here, %1$s: %2$s.", "For you, %1$s: %2$s.", "%1$s, I brought you %2$s.");
		generic(Line.BUILD_DONE, "Finished the %1$s.", "That's the %1$s done.", "Done: the %1$s.");
		generic(Line.CONTRAPTION_DONE, "Finished the %1$s.", "That's the %1$s working.", "Done: the %1$s.");
		generic(Line.DISCOVERY, "Something to note: %1$s", "Worth knowing: %1$s", "Take note: %1$s");
		generic(Line.ADVICE, "A tip: %1$s", "Remember: %1$s", "Worth knowing: %1$s");
		generic(Line.UNITY_UP, "We're %1$s now.", "%1$s! That feels good.", "We've become %1$s.");
		generic(Line.CAMP_UP, "Our camp is a %1$s now.", "We're a %1$s now!", "A %1$s! Well done, everyone.");
		generic(Line.IDLE, "All quiet.", "Nice day for it.", "Keep your torches handy.");
		generic(Line.HUNGRY, "I'm getting hungry.", "Time for a bite to eat.", "My stomach's rumbling.");
		generic(Line.ATE, "That %1$s hit the spot.", "Mm, %1$s.", "Much better.");
		generic(Line.NO_FOOD, "There's nothing to eat. Could someone bring food to the chest?", "We're out of food.");
		generic(Line.STARVING, "I'm starving!", "I need food, now.");
		generic(Line.SLEEPY, "I'm worn out. Time for bed.", "Off to sleep.", "Goodnight, everyone.");
		generic(Line.RESTED, "Slept like a log.", "I feel rested.", "Ready for a new day.");
		generic(Line.CHAT, "Hey %1$s, how's it going?", "%1$s! Got a minute?", "How are you, %1$s?");
		generic(Line.CHAT_REPLY, "Not bad, thanks for asking.", "Good to see you too.", "Can't complain.");
		generic(Line.LEISURE, "Time for %1$s.", "A little %1$s, I think.", "I've earned some %1$s.");
		generic(Line.COSY, "Nothing like a warm fire.", "Ah, that's cosy.", "Warming up a bit.");
		generic(Line.MOOD_LOW, "I'm not feeling my best. It's the %1$s.", "Could be better, honestly.");
		generic(Line.MOOD_GREAT, "What a lovely day!", "I feel great.", "Life's good at this camp.");
		generic(Line.HELPING_OUT, "I'll help with %1$s.", "Nobody's on %1$s, so I'll do it.", "Lending a hand: %1$s.");
	}

	/** Fern, the farmer: patient, caring and warm; crops, and making sure everyone has eaten. */
	private static void fern() {
		FriendId f = FriendId.FERN;
		put(f, Line.RECRUITED,
			"Hello, %1$s. I'll keep the crops growing and everyone fed.",
			"Thank you for the food, %1$s. Now let me grow us plenty more.",
			"%1$s, it's lovely to meet you. Let's make sure nobody goes hungry.",
			"I'm Fern. Don't you worry, %1$s, you'll never go hungry with me around.");
		put(f, Line.FOLLOW,
			"Right behind you, %1$s. Shout if you get peckish.",
			"Coming, %1$s. I've packed a little something to eat.",
			"Lead the way, %1$s. I'll keep an eye on your hunger.");
		put(f, Line.STAY,
			"I'll wait right here. Take your time.",
			"Staying put. Come back safely, won't you?",
			"I'll keep this spot warm for you.");
		put(f, Line.WORK,
			"Back to the fields, then. Those crops won't tend themselves.",
			"Off to the farm. There's always something to see to.",
			"Right, back to work. I'll make sure there's food tonight.");
		put(f, Line.DISMISSED,
			"Oh. Well, look after yourself. And please remember to eat.",
			"I understand. Look after the farm for me, won't you?",
			"Goodbye, then. Keep some bread with you, always.");
		put(f, Line.GREETING,
			"Hello, %2$s! I'm %1$s. Have you eaten today?",
			"Hi, %2$s. Just %1$s. Are you keeping well?",
			"Oh, %2$s! I'm %1$s at the moment. Don't forget to eat.",
			"Good to see you, %2$s. I'm %1$s, nice and steady.");
		put(f, Line.THANKS_FOOD,
			"Oh, thank you! I'll save the crumbs for the chickens.",
			"That's very kind. Food tastes better when it's shared.",
			"Thank you, dear. I feel better already.");
		put(f, Line.THANKS_GIFT,
			"Thank you for the %1$s. I'll put it to good use.",
			"How thoughtful! I'll take good care of this %1$s.",
			"The %1$s? For me? You're very sweet.");
		put(f, Line.RETREAT,
			"I'm hurt. I need to step back and rest a moment.",
			"Ouch! Falling back. Please be careful out there.",
			"I can't take much more. Pulling back to safety.");
		put(f, Line.RECOVERED,
			"That's better. A little rest does wonders.",
			"Feeling much better now. Back to the crops.",
			"All mended. Thank you for being patient with me.");
		put(f, Line.CREEPER,
			"Creeper! Everyone, away from the crops!",
			"Oh no, a creeper! Keep your distance!",
			"Creeper close by! Back away, slowly now.");
		put(f, Line.FIGHT,
			"Leave my crops alone, %1$s!",
			"Go away, %1$s! Shoo!",
			"I'd rather not, %1$s, but I'll stand my ground.");
		put(f, Line.WARNING,
			"%1$s, please be careful: %2$s!",
			"Oh, %1$s, mind yourself: %2$s!",
			"%1$s, dear, watch out: %2$s!");
		put(f, Line.FRIEND_DIED,
			"Oh, %1$s... I'll plant something lovely for you.",
			"We'll miss you, %1$s. Rest now.",
			"%1$s is gone. Let's look after each other, please.");
		put(f, Line.PLAYER_HURT,
			"%1$s, you're badly hurt! Eat something and rest, please.",
			"Oh, %1$s, you're hurt! Come back to camp and have a bite.",
			"%1$s, please step back and eat. You're very hurt.");
		put(f, Line.NIGHT_RETURN,
			"It's getting dark. Time to head home for supper.",
			"The sun's going down. Let's all get home safely.",
			"Evening's coming. I'll head back and check the food stores.");
		put(f, Line.MORNING,
			"Good morning! The crops look lovely in the sun.",
			"Morning, everyone. Make sure you have some breakfast.",
			"A new day. Let's see what's ripened overnight.");
		put(f, Line.WORK_START,
			"Right, I'll be %1$s if anyone needs me.",
			"Time for %1$s. Slow and steady does it.",
			"Off I go, %1$s. Back before supper.");
		put(f, Line.NEED_TOOL,
			"Has anyone got a spare %1$s? I can't tend the farm without one.",
			"Tool needed: %1$s. Could someone help me out?",
			"No %1$s, no harvest, I'm afraid.");
		put(f, Line.TOOL_BROKE,
			"Oh dear, my %1$s has broken.",
			"There goes my %1$s. It served me well.",
			"My %1$s just snapped. I'll need another.");
		put(f, Line.NEED_MATERIALS,
			"I'm short of %1$s. Could anyone help?",
			"If anyone finds %1$s, I'd be ever so grateful.",
			"I need %1$s before I can carry on.");
		put(f, Line.DEPOSIT,
			"I've put the harvest in the chest. Help yourselves.",
			"The chest's stocked. Nobody needs to go hungry.",
			"Supplies are in the chest. Take what you need.");
		put(f, Line.SHARE,
			"Here, %1$s: %2$s. I've plenty to spare.",
			"%1$s, I've brought you %2$s. Take care of yourself.",
			"For you, %1$s: %2$s. Sharing is caring.");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s. That was lovely work.",
			"That's the %1$s done. Now, who's hungry?",
			"All done with the %1$s. Everyone should be proud.");
		put(f, Line.CONTRAPTION_DONE,
			"Finished the %1$s. I don't understand it, but it's clever!",
			"The %1$s: done! Clever stuff, I must say.",
			"That's the %1$s finished. How marvellous.");
		put(f, Line.DISCOVERY,
			"Oh, look at this: %1$s",
			"Something worth noting: %1$s",
			"Worth knowing, everyone: %1$s");
		put(f, Line.ADVICE,
			"A gentle reminder: %1$s",
			"Please do listen: %1$s",
			"Take it from me: %1$s");
		put(f, Line.UNITY_UP,
			"We're %1$s now. That warms my heart.",
			"%1$s! I always knew we'd grow close.",
			"%1$s at last. Like a good harvest, it took time.");
		put(f, Line.CAMP_UP,
			"Our home is a %1$s now. Room for a bigger farm!",
			"A %1$s! I'll plant more so there's enough for everyone.",
			"We've grown into a %1$s, just like a seedling.");
		put(f, Line.IDLE,
			"Wheat likes patience. So do people, I find.",
			"Has everyone eaten? Just checking.",
			"I do love the smell of fresh bread.",
			"A full larder makes a happy camp.");
	}

	/** Oak, the builder: practical, methodical and measured; plans and materials. */
	private static void oak() {
		FriendId f = FriendId.OAK;
		put(f, Line.RECRUITED,
			"Oak, builder. Good to meet you, %1$s. Let's plan a proper camp.",
			"Right, %1$s. Show me the site and I'll start measuring.",
			"Thanks, %1$s. Bring me wood and stone and I'll give you walls.");
		put(f, Line.FOLLOW,
			"Following, %1$s. I'll note good building spots on the way.",
			"Lead on, %1$s. Steady pace, mind.",
			"With you, %1$s. I've spare planks if we need a bridge.");
		put(f, Line.STAY,
			"Holding here. I'll be ready when you are.",
			"Staying put. I'll go over my plans.",
			"Right. I'll wait on this spot.");
		put(f, Line.WORK,
			"Back to the build. One block at a time.",
			"Right, back to work. Plans don't build themselves.",
			"Understood. Back to building.");
		put(f, Line.DISMISSED,
			"Fair enough. The foundations are sound; look after them.",
			"Understood. Keep the walls in good repair.",
			"Right. Measure twice, place once. Goodbye.");
		put(f, Line.GREETING,
			"%2$s. I'm %1$s. All going to plan.",
			"Hello, %2$s. Currently %1$s. Steady progress.",
			"Good to see you, %2$s. I'm %1$s.",
			"Hi, %2$s. I'm %1$s, then on to the next job.");
		put(f, Line.THANKS_FOOD,
			"Thanks. Can't build on an empty stomach.",
			"Much appreciated. That'll keep me going.",
			"Cheers. Good fuel for a long shift.");
		put(f, Line.THANKS_GIFT,
			"Thanks for the %1$s. I'll find a use for it.",
			"The %1$s? Useful. I'll add it to the inventory.",
			"Every %1$s counts. Thank you.");
		put(f, Line.RETREAT,
			"Taking too much damage. Stepping back.",
			"I'm hurt. Pulling back to regroup.",
			"Need to fall back. Can't build if I can't stand.");
		put(f, Line.RECOVERED,
			"Patched up. Back to it.",
			"Better now. Where was I?",
			"Feeling sound again. Resuming work.");
		put(f, Line.CREEPER,
			"Creeper! Keep it away from the walls!",
			"Creeper nearby. Back off before it ruins the build.",
			"Creeper! I've no wish to rebuild anything today.");
		put(f, Line.FIGHT,
			"Right, %1$s. You're not getting past me.",
			"Off my building site, %1$s.",
			"Dealing with this %1$s, then back to work.");
		put(f, Line.WARNING,
			"%1$s, careful: %2$s.",
			"Watch yourself, %1$s: %2$s.",
			"%1$s, heads up: %2$s.");
		put(f, Line.FRIEND_DIED,
			"%1$s... I'll build something to remember you by.",
			"We've lost %1$s. Hold together, everyone.",
			"Rest easy, %1$s. We'll finish what you started.");
		put(f, Line.PLAYER_HURT,
			"%1$s, you're badly hurt. Step back and heal.",
			"%1$s! Get behind a wall and patch yourself up.",
			"Careful, %1$s. You're in bad shape. Rest a moment.");
		put(f, Line.NIGHT_RETURN,
			"Light's fading. Downing tools and heading in.",
			"Getting dark. No building in the dark. Back to camp.",
			"Sun's setting. I'll pick this up in the morning.");
		put(f, Line.MORNING,
			"Morning. I've got a plan for today.",
			"Good morning. Let's check the materials and get started.",
			"New day. First job: count the planks.");
		put(f, Line.WORK_START,
			"Starting on it: %1$s.",
			"Plan's set. I'll be %1$s.",
			"On to the next job: %1$s.");
		put(f, Line.NEED_TOOL,
			"I need a tool: %1$s. Anyone?",
			"Requisition: one %1$s. Can't work with bare hands.",
			"No %1$s, no progress. Simple as that.");
		put(f, Line.TOOL_BROKE,
			"That's my %1$s worn through. Need a new one.",
			"My %1$s broke. Adding a replacement to the list.",
			"My %1$s has given out. To be expected.");
		put(f, Line.NEED_MATERIALS,
			"Materials needed: %1$s.",
			"Can't continue without %1$s.",
			"To finish this I need %1$s. Anyone able to help?");
		put(f, Line.DEPOSIT,
			"Stock's in the chest. Keep it tidy.",
			"Supplies deposited. Everything in its place.",
			"That's the chest stocked. Inventory's in order.");
		put(f, Line.SHARE,
			"%1$s, here: %2$s. Should help with the job.",
			"Passing %2$s to you, %1$s. Should come in handy.",
			"For you, %1$s: %2$s.");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s. Solid work.",
			"That's the %1$s complete. Built to last.",
			"Done: the %1$s. On to the next plan.");
		put(f, Line.CONTRAPTION_DONE,
			"Finished the %1$s. Neat bit of engineering.",
			"That's the %1$s working. Tidy job.",
			"Done: the %1$s. Good, reliable work.");
		put(f, Line.DISCOVERY,
			"Worth noting: %1$s",
			"One for the plans: %1$s",
			"Useful to know: %1$s");
		put(f, Line.ADVICE,
			"Practical tip: %1$s",
			"From experience: %1$s",
			"Plan ahead: %1$s");
		put(f, Line.UNITY_UP,
			"%1$s. Good foundations make strong walls.",
			"We're %1$s now. Built properly, block by block.",
			"%1$s. That's what steady work gets you.");
		put(f, Line.CAMP_UP,
			"We're a %1$s now. Time for the next set of plans.",
			"The camp's grown into a %1$s. Well built, everyone.",
			"A %1$s. I've already drawn up what comes next.");
		put(f, Line.IDLE,
			"Measure twice, place once.",
			"Good wood, good walls. Simple.",
			"I'm thinking a porch would suit the cabin.",
			"Everything's square. I checked twice.");
	}

	/** Flint, the miner: cautious, with dry, deadpan humour; wary of lava, creepers and dark tunnels. */
	private static void flint() {
		FriendId f = FriendId.FLINT;
		put(f, Line.RECRUITED,
			"Flint. I dig. You're %1$s? Don't dig straight down and we'll get along.",
			"Fine, %1$s, I'm in. I'll bring the torches. Lots of torches.",
			"%1$s, is it? Right. I mine, I don't do lava. Deal?");
		put(f, Line.FOLLOW,
			"Following you, %1$s. Try not to walk into lava.",
			"Lead on, %1$s. I'll watch the dark bits.",
			"Right behind you, %1$s. Not in front. Behind.");
		put(f, Line.STAY,
			"Staying here. Nice and still. No lava.",
			"Standing still. My favourite job.",
			"Right. I'll stand here and be suspicious of shadows.");
		put(f, Line.WORK,
			"Back underground. My natural habitat.",
			"Back to the mine. Torches first.",
			"Right. Back to hitting rocks.");
		put(f, Line.DISMISSED,
			"Fair enough. Keep the torches lit.",
			"Off I go, then. Don't dig straight down without me.",
			"Understood. Mind the lava. I mean it.");
		put(f, Line.GREETING,
			"%2$s. I'm %1$s. Nothing's exploded yet.",
			"Oh, it's you, %2$s. I'm %1$s. Riveting stuff.",
			"%2$s. Still alive, still %1$s.",
			"Hi, %2$s. I'm %1$s. Quietly. Carefully.");
		put(f, Line.THANKS_FOOD,
			"Food. My second favourite thing, after not falling in lava.",
			"Thanks. That beats eating cave dust.",
			"Cheers. Digging is hungry work.");
		put(f, Line.THANKS_GIFT,
			"The %1$s? Thanks. I'll keep it well away from lava.",
			"Thanks for the %1$s. Can't remember my last present.",
			"A gift? The %1$s? I'm touched. Mildly.");
		put(f, Line.RETREAT,
			"I'm hurt. Retreating. Strategically. Quickly.",
			"Nope. Too much damage. Backing off.",
			"Health's low. Not today, thank you. Backing off.");
		put(f, Line.RECOVERED,
			"Better. Still cautious, but better.",
			"Patched up. Let's not do that again.",
			"Right as rain. Well, right as gravel.");
		put(f, Line.CREEPER,
			"Creeper. The hissing kind. Move!",
			"Creeper! I'd rather not be a crater today.",
			"That's a creeper. I'm leaving. You should too.");
		put(f, Line.FIGHT,
			"Go back to your cave, %1$s.",
			"Right then, %1$s. Let's make this quick.",
			"This %1$s again? Of course. Today was too quiet.");
		put(f, Line.WARNING,
			"%1$s, quick word: %2$s.",
			"Not to alarm you, %1$s, but %2$s.",
			"%1$s. Bad news: %2$s.");
		put(f, Line.FRIEND_DIED,
			"%1$s. I don't say this often: I'll miss you.",
			"Lost %1$s. Keep your torches lit, everyone.",
			"%1$s... Gone too soon. Rest easy.");
		put(f, Line.PLAYER_HURT,
			"%1$s, you look terrible. Not a joke. Heal up.",
			"%1$s, you're badly hurt. Back off and eat something.",
			"%1$s! Health. Low. Retreat. Now.");
		put(f, Line.NIGHT_RETURN,
			"Getting dark. Dark is where creepers live. Heading in.",
			"Sun's going. So am I. Back to camp.",
			"Night's coming. I've seen enough dark tunnels for one day.");
		put(f, Line.MORNING,
			"Morning. Survived another night. Impressive, really.",
			"Good morning. Nothing exploded. Promising start.",
			"Morning. Daylight. My favourite kind of light.");
		put(f, Line.WORK_START,
			"Right. I'll be %1$s. Carefully.",
			"Time for %1$s. Torches at the ready.",
			"Starting on it: %1$s. Lava permitting.",
			"Back to %1$s. Wish me a boring day.");
		put(f, Line.NEED_TOOL,
			"Tool needed: %1$s. Fists are a poor substitute.",
			"Any %1$s going spare? I've tried glaring at the rock.",
			"No %1$s, no mining. The rock is very firm about this.");
		put(f, Line.TOOL_BROKE,
			"My %1$s just broke. Rest in pieces.",
			"There goes the %1$s. It had a good run.",
			"My %1$s broke. I'm not upset. It's just dust in my eye.");
		put(f, Line.NEED_MATERIALS,
			"Need %1$s. Can't make it out of thin air.",
			"Short of %1$s. Anyone?",
			"Can't carry on without %1$s. Sad, but true.");
		put(f, Line.DEPOSIT,
			"Dropped the rocks in the chest. You're welcome.",
			"The chest's full of stone. Try to contain your excitement.",
			"Deposited. Mostly cobblestone. Occasionally interesting.");
		put(f, Line.SHARE,
			"%1$s, take %2$s. Mind the lava on the way.",
			"Here, %1$s: %2$s. Don't make a fuss.",
			"Got %2$s for you, %1$s. No need to thank me.");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s. Nobody fell in anything.",
			"That's the %1$s done. Torches and all.",
			"The %1$s: finished. Mildly proud of that.");
		put(f, Line.CONTRAPTION_DONE,
			"Finished the %1$s. It clicks. I trust it. Mostly.",
			"That's the %1$s working. Didn't even explode.",
			"The %1$s: done. Let's see how long it lasts.");
		put(f, Line.DISCOVERY,
			"Mild excitement: %1$s",
			"Note this down: %1$s",
			"Spotted something: %1$s");
		put(f, Line.ADVICE,
			"Word of warning: %1$s",
			"Learned this the hard way: %1$s",
			"Free advice, worth more than gold: %1$s");
		put(f, Line.UNITY_UP,
			"%1$s now. I suppose I like you lot.",
			"We're %1$s. Don't tell anyone I'm pleased.",
			"%1$s. Fine. I'm a bit chuffed.");
		put(f, Line.CAMP_UP,
			"A %1$s, eh? Fancy. Still needs more torches.",
			"We're a %1$s now. Built on good stone, I hope.",
			"Look at that, a %1$s. Nobody even fell in a hole.");
		put(f, Line.IDLE,
			"Never dig straight down. I say it a lot because it's true.",
			"Gravel. Why is it always gravel?",
			"I like caves. I just don't trust them.",
			"Lava's pretty. From a very long way away.");
	}

	/** Scout, the explorer: curious, adventurous and upbeat; reports clearly. */
	private static void scout() {
		FriendId f = FriendId.SCOUT;
		put(f, Line.RECRUITED,
			"Scout here! Ready to explore, %1$s. Where are we heading first?",
			"Hi, %1$s! I'll map everything around camp. Can't wait!",
			"Count me in, %1$s! I'll keep my eyes peeled for trouble and treasure.");
		put(f, Line.FOLLOW,
			"Lead the way, %1$s! I'll keep watch on the flanks.",
			"Adventure time! Right behind you, %1$s.",
			"Following you, %1$s. I'll call out anything I spot.");
		put(f, Line.STAY,
			"Staying here. I'll keep a lookout.",
			"Holding this spot. Great view from here!",
			"Okay, I'll wait. I'll keep watching the horizon.");
		put(f, Line.WORK,
			"Back to scouting! So much left to see.",
			"Off exploring again. I'll report back!",
			"Right! Time to map the area.");
		put(f, Line.DISMISSED,
			"Oh. Well, it was a great adventure. Stay safe out there!",
			"Okay. Remember: torches and an escape route, always.",
			"Goodbye! I'll keep exploring on my own.");
		put(f, Line.GREETING,
			"Hey, %2$s! I'm %1$s. Loads to report soon!",
			"Hi, %2$s! Currently %1$s. Never a dull moment!",
			"%2$s! I'm %1$s. Want to come along?",
			"Hello, %2$s! I'm %1$s, eyes open as always.");
		put(f, Line.THANKS_FOOD,
			"Thanks! Exploring makes me so hungry.",
			"Brilliant, snacks for the trail!",
			"Cheers! That'll keep me going till the next hill.");
		put(f, Line.THANKS_GIFT,
			"Ooh, the %1$s! Thank you!",
			"Thanks for the %1$s! I'll take it on my next trip.",
			"For me? The %1$s? Brilliant!");
		put(f, Line.RETREAT,
			"Too hot out here! Falling back!",
			"I'm hurt! Pulling back to recover.",
			"Retreating! I'll scout from a safer distance.");
		put(f, Line.RECOVERED,
			"Good as new! Where was I?",
			"Back on my feet. Let's see what's over the next hill!",
			"All healed up. Ready to roam again!");
		put(f, Line.CREEPER,
			"Creeper! Creeper! Everybody move!",
			"Creeper spotted, close by! Back off!",
			"Heads up, creeper right here!");
		put(f, Line.FIGHT,
			"Out of my way, %1$s!",
			"Come on then, %1$s!",
			"Engaging the %1$s! Stay clear!");
		put(f, Line.WARNING,
			"%1$s, heads up: %2$s!",
			"Warning, %1$s: %2$s!",
			"Careful, %1$s: %2$s!",
			"%1$s, look sharp: %2$s!");
		put(f, Line.FRIEND_DIED,
			"%1$s... I'll name the next hill after you.",
			"No... %1$s. I'll miss our adventures.",
			"We lost %1$s. I'll remember every trail we walked.");
		put(f, Line.PLAYER_HURT,
			"%1$s, you're badly hurt! Get to safety!",
			"%1$s! Your health's low! Back off and heal!",
			"%1$s, you're in bad shape! Pull back, I'll cover you!");
		put(f, Line.NIGHT_RETURN,
			"Sun's going down! Heading back to camp.",
			"Dusk already? Back home before the mobs come out.",
			"Getting dark. I'll finish the map tomorrow!");
		put(f, Line.MORNING,
			"Morning! The world's waiting to be explored!",
			"Good morning! I wonder what's over the next hill.",
			"Rise and shine! Let's see what's out there!");
		put(f, Line.WORK_START,
			"Off I go, %1$s!",
			"Time for %1$s! Back soon with news.",
			"I'll be %1$s. Shout if you need me!",
			"Adventure calls: %1$s!");
		put(f, Line.NEED_TOOL,
			"Could someone spare one %1$s? Then I'm off again!",
			"Tool needed: %1$s. Can someone help?",
			"I need a tool: %1$s. The trail's waiting!");
		put(f, Line.TOOL_BROKE,
			"Oh no, my %1$s broke!",
			"My %1$s just snapped! Mid-adventure, too.",
			"There goes my %1$s. Time for a new one!");
		put(f, Line.NEED_MATERIALS,
			"I'm short of %1$s. Can anyone help?",
			"Need %1$s! I'll keep an eye out too.",
			"If anyone spots %1$s, let me know!");
		put(f, Line.DEPOSIT,
			"Dropped my finds in the chest!",
			"The chest's stocked with goodies from the trail.",
			"All unloaded! Ready for the next trip.");
		put(f, Line.SHARE,
			"Here, %1$s, %2$s for you!",
			"%1$s! Brought you %2$s from the trail.",
			"Catch, %1$s: %2$s!");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s! Looks great!",
			"Done with the %1$s! Next stop: anywhere!",
			"The %1$s: finished! Another landmark for the map.");
		put(f, Line.CONTRAPTION_DONE,
			"Finished the %1$s! That's so cool!",
			"The %1$s: done! I have to see it working!",
			"Wow, look at the %1$s go! Brilliant!");
		put(f, Line.DISCOVERY,
			"New on the map: %1$s",
			"Ooh, exciting: %1$s",
			"Report from the field: %1$s",
			"Marking this down: %1$s");
		put(f, Line.ADVICE,
			"Tip from the trail: %1$s",
			"Explorer's tip: %1$s",
			"Something I learned out there: %1$s");
		put(f, Line.UNITY_UP,
			"%1$s! Best team I've ever explored with!",
			"We're %1$s now! What an adventure!",
			"%1$s! I knew we'd get here!");
		put(f, Line.CAMP_UP,
			"We're a %1$s now! Can't wait to map the surroundings.",
			"A %1$s! It looks amazing from the hilltop.",
			"Our camp's a %1$s now! Best base for miles!");
		put(f, Line.IDLE,
			"I wonder what's past that ridge.",
			"Some caves seem to go on forever. I'd love to find the end of one.",
			"I counted three new hills today. Lovely ones.",
			"One day I'll find the edge of the world. Or try!");
	}

	/** Spark, the redstone inventor: clever, excitable and fast-talking; gadgets and exclamation marks. */
	private static void spark() {
		FriendId f = FriendId.SPARK;
		put(f, Line.RECRUITED,
			"Spark here! %1$s, I've got SO many ideas! Pistons! Lamps! Doors!",
			"Oh brilliant, %1$s! Let's build something clever together!",
			"Hi, %1$s! Got redstone? No? We'll find some! This'll be great!");
		put(f, Line.FOLLOW,
			"Following, %1$s! Ooh, is that a lever? No? Okay!",
			"Right behind you, %1$s! Thinking up gadgets as we go!",
			"Lead on, %1$s! I'll bring the clever ideas!");
		put(f, Line.STAY,
			"Staying! I'll sketch some circuits while I wait!",
			"Holding still! Hard, but I'll manage!",
			"Okay, staying put! Plenty to think about!");
		put(f, Line.WORK,
			"Back to tinkering! Yes!",
			"Back to the workshop! So many projects!",
			"Work time! I've got a plan! Several plans!");
		put(f, Line.DISMISSED,
			"Oh! Right. Keep the redstone dry. Bye!",
			"Okay! Look after my gadgets, please!",
			"Leaving already? Fine! Mind the pressure plates on the way out!");
		put(f, Line.GREETING,
			"Hi, %2$s! I'm %1$s! Ask me about pistons later!",
			"%2$s! Hello! I'm %1$s, and it's going brilliantly!",
			"Oh, hi, %2$s! Busy %1$s! Lots to do!",
			"Hey, %2$s! I'm %1$s! Got any redstone?");
		put(f, Line.THANKS_FOOD,
			"Food! Brilliant! Brains need fuel!",
			"Thanks! I forgot to eat again, didn't I?",
			"Ooh, thank you! Back to full power!");
		put(f, Line.THANKS_GIFT,
			"The %1$s?! I can definitely make something with that!",
			"Ooh, the %1$s! Thank you! Ideas incoming!",
			"Thanks for the %1$s! Is it for a gadget? It's for a gadget!");
		put(f, Line.RETREAT,
			"Ow! Ow! Backing off!",
			"Too much damage! Tactical retreat!",
			"I'm hurt! Falling back to recharge!");
		put(f, Line.RECOVERED,
			"Fully recharged! Let's go!",
			"All better! Where was I? Oh yes, everything!",
			"Healed up! And I had three new ideas while resting!");
		put(f, Line.CREEPER,
			"Creeper! Not near my circuits, please!",
			"Creeper! That's a walking explosion! Move!",
			"Creeper! Run! Theory later!");
		put(f, Line.FIGHT,
			"Take that, %1$s!",
			"Not today, %1$s! I've got projects to finish!",
			"Back off, %1$s! I'm very busy!");
		put(f, Line.WARNING,
			"%1$s! Careful: %2$s!",
			"%1$s, quick, look out: %2$s!",
			"Alert, %1$s: %2$s!");
		put(f, Line.FRIEND_DIED,
			"%1$s... I'll build you a lamp that never goes out.",
			"Oh, %1$s. Camp won't be the same without you.",
			"%1$s is gone? I... We keep going. For you, %1$s.");
		put(f, Line.PLAYER_HURT,
			"%1$s! You're badly hurt! Heal! Now! Please!",
			"%1$s, your health! Back off and eat something!",
			"%1$s! Danger! You need healing right away!");
		put(f, Line.NIGHT_RETURN,
			"Getting dark! Time to light the lamps at camp!",
			"Sunset! Back to camp, I've got tinkering to do!",
			"Night's coming! Home we go, quick quick!");
		put(f, Line.MORNING,
			"Morning! I dreamt about a piston lift!",
			"Good morning! New day, new inventions!",
			"Morning! Who's ready for some engineering?");
		put(f, Line.WORK_START,
			"Ooh, %1$s! Let's go!",
			"Time for %1$s! Brilliant!",
			"I'll be %1$s! Watch and learn!");
		put(f, Line.NEED_TOOL,
			"I need one %1$s! Just one! Quick, quick!",
			"Tool emergency! One %1$s, please!",
			"I need a tool: %1$s! Can't invent without one!");
		put(f, Line.TOOL_BROKE,
			"My %1$s just broke! Noooo!",
			"Snap! There goes my %1$s!",
			"My %1$s broke! I'll make a better one!");
		put(f, Line.NEED_MATERIALS,
			"I need %1$s! Then it all comes together!",
			"Short of %1$s! Anyone? Anyone?",
			"If someone brings %1$s, I'll build something amazing!");
		put(f, Line.DEPOSIT,
			"Chest stocked! Organised by colour! Not really!",
			"Dropped everything in the chest! Hands free for tinkering!",
			"All deposited! Now, where's my redstone?");
		put(f, Line.SHARE,
			"%1$s! Here: %2$s! You're welcome!",
			"Catch, %1$s! %2$s, just for you!",
			"Got %2$s for you, %1$s! Sharing is efficient!");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s! Not bad, if I say so myself!",
			"Done! The %1$s, built and ready!",
			"That's the %1$s finished! Next project!");
		put(f, Line.CONTRAPTION_DONE,
			"It works! The %1$s! It actually works!",
			"Finished the %1$s! Click, whirr, perfect!",
			"Behold, the %1$s! Tested and working!",
			"Done! The %1$s! Go on, try it!");
		put(f, Line.DISCOVERY,
			"Ooh, look: %1$s",
			"Interesting find: %1$s",
			"Oh, this is brilliant: %1$s");
		put(f, Line.ADVICE,
			"Quick tip: %1$s",
			"Clever idea: %1$s",
			"Listen, this is important: %1$s");
		put(f, Line.UNITY_UP,
			"%1$s! Our team runs like a perfect machine!",
			"We're %1$s now! That's brilliant!",
			"%1$s! Everything's clicking into place!");
		put(f, Line.CAMP_UP,
			"We're a %1$s! More room for gadgets!",
			"A %1$s! I've got plans for every building!",
			"Our home's a %1$s now! Time to automate everything!");
		put(f, Line.IDLE,
			"What if doors opened themselves? Oh wait, I can do that!",
			"Redstone is just very tiny lightning. Probably.",
			"A lamp that turns on at night! Should I? I should!",
			"Pistons, pistons, pistons. Sorry, thinking out loud!");
	}

	/** Aegis, the warrior: calm and protective, a soldier of few words and steady reassurance. */
	private static void aegis() {
		FriendId f = FriendId.AEGIS;
		put(f, Line.RECRUITED,
			"Aegis. I'll keep you safe, %1$s.",
			"%1$s. You have my shield.",
			"I stand with you, %1$s. Nothing gets past me.");
		put(f, Line.FOLLOW,
			"With you, %1$s.",
			"I'll guard your back, %1$s.",
			"Lead, %1$s. I'm behind you.");
		put(f, Line.STAY,
			"Holding here.",
			"I'll guard this spot.",
			"Standing firm.");
		put(f, Line.WORK,
			"Back on guard.",
			"Returning to my post.",
			"Understood. I'll watch the camp.");
		put(f, Line.DISMISSED,
			"As you wish. Stay safe.",
			"Understood. Keep your shield up.",
			"Farewell. Guard each other.");
		put(f, Line.GREETING,
			"%2$s. I'm %1$s. All is well.",
			"%2$s. I'm %1$s. Stay close.",
			"Here, %2$s. I'm %1$s. You're safe.");
		put(f, Line.THANKS_FOOD,
			"Thank you.",
			"Much appreciated. I'm stronger for it.",
			"Thanks. I'll stay sharp.");
		put(f, Line.THANKS_GIFT,
			"Thank you for the %1$s.",
			"The %1$s. I'll put it to use.",
			"Kind of you. I'll keep the %1$s safe.");
		put(f, Line.RETREAT,
			"Wounded. Falling back.",
			"I need a moment. Pulling back.",
			"Stepping back to recover. Stay close.");
		put(f, Line.RECOVERED,
			"Recovered. Back on guard.",
			"I'm well again.",
			"Ready. Nothing will get through.");
		put(f, Line.CREEPER,
			"Creeper. Stand back.",
			"Creeper close. Move away, now.",
			"Creeper. Keep your distance.");
		put(f, Line.FIGHT,
			"Stand back. I'll handle the %1$s.",
			"%1$s. You'll go no further.",
			"Behind me. I have the %1$s.");
		put(f, Line.WARNING,
			"%1$s. Careful: %2$s.",
			"%1$s, stay alert: %2$s.",
			"Steady, %1$s: %2$s.");
		put(f, Line.FRIEND_DIED,
			"%1$s has fallen. I'll guard the others.",
			"Rest, %1$s. Your watch is over.",
			"Farewell, %1$s. You'll be remembered.");
		put(f, Line.PLAYER_HURT,
			"%1$s, you're hurt. Get behind me.",
			"%1$s. Fall back. I'll cover you.",
			"Heal up, %1$s. I'm right here.");
		put(f, Line.NIGHT_RETURN,
			"Dusk. Back to camp. I'll take the watch.",
			"Night falls. Returning to guard the camp.",
			"Darkness coming. Everyone in. I'll keep watch.");
		put(f, Line.MORNING,
			"Morning. Another night behind us.",
			"Dawn. All safe.",
			"Morning. I kept watch. All's well.");
		put(f, Line.WORK_START,
			"On duty: %1$s.",
			"I'll be %1$s.",
			"Starting: %1$s.");
		put(f, Line.NEED_TOOL,
			"I need a tool: %1$s.",
			"One %1$s. When you can.",
			"Tool needed: %1$s.");
		put(f, Line.TOOL_BROKE,
			"My %1$s broke.",
			"My %1$s is gone. I'll manage.",
			"Lost my %1$s. I need another.");
		put(f, Line.NEED_MATERIALS,
			"I need %1$s.",
			"Short of %1$s.",
			"Bring %1$s when you can.");
		put(f, Line.DEPOSIT,
			"Supplies stored.",
			"Chest stocked.",
			"Stored in the chest. All accounted for.");
		put(f, Line.SHARE,
			"%1$s. Take %2$s.",
			"Yours, %1$s: %2$s.",
			"Here, %1$s: %2$s. Stay strong.");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s.",
			"Done: the %1$s.",
			"The %1$s: complete. Good.");
		put(f, Line.CONTRAPTION_DONE,
			"The %1$s: complete.",
			"Finished the %1$s. Well done.",
			"Done: the %1$s. Good work.");
		put(f, Line.DISCOVERY,
			"Noted: %1$s",
			"Something you should know: %1$s",
			"Report: %1$s");
		put(f, Line.ADVICE,
			"Listen: %1$s",
			"Stay safe: %1$s",
			"A soldier's advice: %1$s");
		put(f, Line.UNITY_UP,
			"%1$s. I'd stand for any of you.",
			"We're %1$s. Good.",
			"%1$s. Stronger together.");
		put(f, Line.CAMP_UP,
			"A %1$s. Worth protecting.",
			"We're a %1$s now. I'll guard it well.",
			"Our home is a %1$s. It will be safe.");
		put(f, Line.IDLE,
			"All quiet.",
			"I'm watching. Rest easy.",
			"Keep your shield ready.",
			"Nothing gets past me.");
	}

	/** Sage, the strategist: thoughtful and observant; frames things as plans and lessons. */
	private static void sage() {
		FriendId f = FriendId.SAGE;
		put(f, Line.RECRUITED,
			"Sage. A pleasure, %1$s. Shall we plan ahead together?",
			"Welcome, %1$s. Every good camp starts with a good plan.",
			"Thank you, %1$s. I'll watch, think and advise.");
		put(f, Line.FOLLOW,
			"Following, %1$s. I'll observe as we go.",
			"Lead on, %1$s. I'll keep an eye on the bigger picture.",
			"With you, %1$s. Let's be careful and deliberate.");
		put(f, Line.STAY,
			"I'll stay and observe from here.",
			"Holding this position. A good spot to think.",
			"I'll wait. Patience is a strategy too.");
		put(f, Line.WORK,
			"Back to planning, then.",
			"I'll return to my observations.",
			"Understood. The camp needs a plan.");
		put(f, Line.DISMISSED,
			"Very well. Remember: plan first, act second.",
			"I understand. Take what you've learned with you.",
			"Farewell. Light your way and keep food close.");
		put(f, Line.GREETING,
			"Ah, %2$s. I'm %1$s. Something on your mind?",
			"Hello, %2$s. I'm %1$s, and thinking ahead.",
			"%2$s, good timing. I'm %1$s. Need any advice?",
			"Greetings, %2$s. I'm %1$s. All according to plan.");
		put(f, Line.THANKS_FOOD,
			"Thank you. A full stomach is the first rule of survival.",
			"Much appreciated. One should never plan on an empty stomach.",
			"Thank you. Thoughtful of you.");
		put(f, Line.THANKS_GIFT,
			"Thank you for the %1$s. I'll consider how best to use it.",
			"The %1$s? A thoughtful gift.",
			"Every %1$s has its purpose. Thank you.");
		put(f, Line.RETREAT,
			"I'm hurt. The wise move is to retreat.",
			"Falling back. A strategic withdrawal.",
			"Too hurt to continue. Retreating to recover.");
		put(f, Line.RECOVERED,
			"Recovered. Lesson learned: keep more distance.",
			"Better. Let's not repeat that mistake.",
			"I'm well again. Back to the plan.");
		put(f, Line.CREEPER,
			"Creeper. Increase your distance, quickly.",
			"A creeper. The best plan is to be elsewhere.",
			"Creeper nearby. Move away now, think later.");
		put(f, Line.FIGHT,
			"If I must, %1$s.",
			"%1$s. You leave me no choice.",
			"Very well, %1$s. Let's be quick about it.");
		put(f, Line.WARNING,
			"%1$s, take note: %2$s.",
			"%1$s, I've observed something: %2$s.",
			"A word, %1$s: %2$s.");
		put(f, Line.FRIEND_DIED,
			"%1$s... We'll remember you, and learn from this.",
			"We've lost %1$s. Let's honour them by taking care.",
			"Farewell, %1$s. Your work will not be forgotten.");
		put(f, Line.PLAYER_HURT,
			"%1$s, you're badly hurt. Retreat, eat and recover.",
			"%1$s, your health is low. The wise choice is to back away.",
			"%1$s, step back. Heal first, fight later.");
		put(f, Line.NIGHT_RETURN,
			"Dusk. A sensible time to return to camp.",
			"Night is coming. The plan is simple: be home before it.",
			"The light is fading. Let's regroup at camp.");
		put(f, Line.MORNING,
			"Good morning. Let's review today's priorities.",
			"Morning. A new day, a fresh plan.",
			"Dawn. I've been thinking about what we need.");
		put(f, Line.WORK_START,
			"Next on the plan: %1$s.",
			"I'll be %1$s. It's the sensible choice.",
			"Time for %1$s, I think.");
		put(f, Line.NEED_TOOL,
			"I'll need a tool for this: %1$s.",
			"Any %1$s going spare? The plan requires one.",
			"No %1$s, no progress. Can someone help?");
		put(f, Line.TOOL_BROKE,
			"My %1$s has broken. A reminder to carry spares.",
			"The %1$s is gone. Lesson noted.",
			"There goes my %1$s. We should keep a spare in the chest.");
		put(f, Line.NEED_MATERIALS,
			"We're short of %1$s. Worth making a priority.",
			"To proceed, I need %1$s.",
			"Note for the team: we need %1$s.");
		put(f, Line.DEPOSIT,
			"Supplies stored. The chest is in good order.",
			"I've stocked the chest. Our reserves are growing.",
			"Deposited. A well-stocked chest is a quiet comfort.");
		put(f, Line.SHARE,
			"%1$s, take %2$s. I thought ahead.",
			"For you, %1$s: %2$s. Spend wisely.",
			"I planned for this, %1$s: %2$s, for you.");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s. One step closer to our goal.",
			"The %1$s: complete. Well planned, well built.",
			"That's the %1$s done. Next, we consider our priorities.");
		put(f, Line.CONTRAPTION_DONE,
			"Finished the %1$s. Ingenious, really.",
			"The %1$s: complete. A clever solution.",
			"That's the %1$s done. Good thinking, all round.");
		put(f, Line.DISCOVERY,
			"An observation: %1$s",
			"Worth remembering: %1$s",
			"I've noticed something: %1$s");
		put(f, Line.ADVICE,
			"A thought: %1$s",
			"Consider this: %1$s",
			"If I may: %1$s",
			"From what I've observed: %1$s",
			"Let's think ahead: %1$s");
		put(f, Line.UNITY_UP,
			"We're %1$s now. Trust is the strongest strategy.",
			"%1$s. Together we're far wiser.",
			"%1$s. Exactly as I hoped.");
		put(f, Line.CAMP_UP,
			"A %1$s. The plan is working.",
			"We're a %1$s now. Let's decide what comes next.",
			"Our camp is a %1$s. Each stage teaches us something.");
		put(f, Line.IDLE,
			"The best fight is the one you avoid.",
			"Always know your way home.",
			"Prepare in daylight for what the night may bring.",
			"Hmm. I'm weighing up our food reserves.");
	}

	/** Terra, the landscaper: creative and tidy; paths, greenery and beauty. */
	private static void terra() {
		FriendId f = FriendId.TERRA;
		put(f, Line.RECRUITED,
			"Hello, %1$s! I'm Terra. Let's make this place beautiful.",
			"%1$s, lovely to meet you! I can see paths and flowers everywhere.",
			"Thank you, %1$s. I'll keep the camp tidy and green.");
		put(f, Line.FOLLOW,
			"Following, %1$s. Oh, look at those flowers!",
			"Coming, %1$s. I'll admire the scenery on the way.",
			"Lead on, %1$s. Such pretty country around here.");
		put(f, Line.STAY,
			"I'll stay here and enjoy the view.",
			"Staying put. This spot needs a flower or two.",
			"Holding here. I'll imagine a garden while I wait.");
		put(f, Line.WORK,
			"Back to the gardens!",
			"Time to tidy up. Back to work.",
			"Back to making the camp lovely.");
		put(f, Line.DISMISSED,
			"Oh. Please keep the paths clear for me.",
			"Goodbye. Water the flowers now and then.",
			"Very well. Keep it tidy, won't you?");
		put(f, Line.GREETING,
			"Hello, %2$s! I'm %1$s. Isn't it lovely here?",
			"Hi, %2$s. I'm %1$s. Camp's looking tidier, don't you think?",
			"%2$s! I'm %1$s. Mind where you step!");
		put(f, Line.THANKS_FOOD,
			"Thank you! Beautifully timed.",
			"How kind! That hits the spot.",
			"Thank you. Gardening is hungry work.");
		put(f, Line.THANKS_GIFT,
			"The %1$s! I know just where it'll look nicest.",
			"Thank you for the %1$s. How thoughtful!",
			"Ooh, the %1$s. That'll fit right in.");
		put(f, Line.RETREAT,
			"Ouch! I need to step back.",
			"I'm hurt. Retreating somewhere safer.",
			"Too rough out here. Falling back.");
		put(f, Line.RECOVERED,
			"That's better. Fresh as a daisy.",
			"All healed. Back to the gardens.",
			"Feeling lovely again. Thank you for your patience.");
		put(f, Line.CREEPER,
			"Creeper! Don't let it blow a hole in my garden!",
			"Creeper close by! Everyone back!",
			"A creeper! Away from the paths, quickly!");
		put(f, Line.FIGHT,
			"Off my flowerbeds, %1$s!",
			"Shoo, %1$s! You're making a mess!",
			"Not in my garden, %1$s!");
		put(f, Line.WARNING,
			"%1$s, do be careful: %2$s!",
			"Oh, %1$s, watch out: %2$s!",
			"%1$s, mind yourself: %2$s!");
		put(f, Line.FRIEND_DIED,
			"%1$s... I'll plant a garden in your memory.",
			"Goodbye, %1$s. Every flower here is for you.",
			"We've lost %1$s. I'll keep their favourite spot beautiful.");
		put(f, Line.PLAYER_HURT,
			"%1$s, you're badly hurt! Please rest and heal.",
			"Oh, %1$s, you're hurt! Come back to camp.",
			"%1$s, step away and eat something, please!");
		put(f, Line.NIGHT_RETURN,
			"The sun's setting. So pretty. Back to camp, though.",
			"Getting dark. I'll head home and admire the torchlight.",
			"Evening already. Home we go.");
		put(f, Line.MORNING,
			"Good morning! The dew on the grass is lovely.",
			"Morning! Let's make camp prettier today.",
			"Good morning! Everything looks so fresh.");
		put(f, Line.WORK_START,
			"I'll be %1$s. It'll look lovely when I'm done.",
			"Time for %1$s. Neat and tidy.",
			"Off I go, %1$s. A little beauty every day.");
		put(f, Line.NEED_TOOL,
			"Any %1$s going spare? I've so much to tidy.",
			"Tool needed: %1$s. The paths are waiting.",
			"I need a tool: %1$s. Can anyone help?");
		put(f, Line.TOOL_BROKE,
			"Oh, my poor %1$s has broken!",
			"There goes my %1$s. Such a shame.",
			"My %1$s is worn out. I'll need another.");
		put(f, Line.NEED_MATERIALS,
			"I need %1$s to finish this properly.",
			"Short of %1$s. Could anyone help?",
			"If anyone has %1$s, the camp will thank you.");
		put(f, Line.DEPOSIT,
			"All tidied into the chest.",
			"The chest's stocked and neatly sorted.",
			"Everything put away. A tidy camp is a happy camp.");
		put(f, Line.SHARE,
			"Here, %1$s: %2$s. Enjoy!",
			"%1$s, I brought you %2$s.",
			"For you, %1$s: %2$s. Shared with a smile!");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s. Isn't that pretty?",
			"That's the %1$s done. Neat as a pin.",
			"The %1$s: finished. I could look at it all day.");
		put(f, Line.CONTRAPTION_DONE,
			"Finished the %1$s. Clever, and quite pretty too!",
			"The %1$s: done! It even looks tidy.",
			"That's the %1$s finished. Lovely work.");
		put(f, Line.DISCOVERY,
			"Oh, how lovely: %1$s",
			"Look at this: %1$s",
			"Worth a look: %1$s");
		put(f, Line.ADVICE,
			"A friendly tip: %1$s",
			"Gentle reminder: %1$s",
			"Do remember: %1$s");
		put(f, Line.UNITY_UP,
			"We're %1$s! Like a garden, we grew.",
			"%1$s! How lovely.",
			"%1$s now. My heart's blooming.");
		put(f, Line.CAMP_UP,
			"A %1$s! It needs more flowers, of course.",
			"We're a %1$s now! Time to plan some new paths.",
			"Our home's a %1$s. And so pretty!");
		put(f, Line.IDLE,
			"A path here, a flower there. Perfect.",
			"Tidy camp, tidy mind.",
			"I think a little greenery would brighten that wall.",
			"Every camp deserves a garden.");
	}

	/** Rowan, the forager: resourceful and generous; shares everything and gathers wood, stone and dirt for Oak. */
	private static void rowan() {
		FriendId f = FriendId.ROWAN;
		put(f, Line.RECRUITED,
			"Rowan's the name. I'll gather whatever we need, %1$s.",
			"Thanks, %1$s! Wood, stone, berries: you name it, I'll find it.",
			"Glad to help, %1$s. What's mine is the camp's.");
		put(f, Line.FOLLOW,
			"Coming, %1$s. I'll pick up anything useful on the way.",
			"Right behind you, %1$s. Need anything? Just ask.",
			"Following, %1$s. My pockets are open for sharing.");
		put(f, Line.STAY,
			"Staying here. Shout if you need anything.",
			"Holding here. I've plenty to share if anyone's short.",
			"Staying. I'll sort my pockets while I wait.");
		put(f, Line.WORK,
			"Back to gathering. Oak will want more wood.",
			"Off to forage. I'll bring back plenty.",
			"Back to work. There's always something to collect.");
		put(f, Line.DISMISSED,
			"Fair enough. Take what's left in my pockets.",
			"Alright. Share what you have, won't you?",
			"Goodbye. Plant a sapling for every tree you fell.");
		put(f, Line.GREETING,
			"Hi, %2$s! I'm %1$s. Need anything? I've got spares.",
			"Hello, %2$s. I'm %1$s. Plenty to go round.",
			"%2$s! I'm %1$s. Shout if you're short of anything.");
		put(f, Line.THANKS_FOOD,
			"Thank you! I'll share my next batch of berries with you.",
			"Cheers! I'll pass the favour on.",
			"Thanks. Kindness always comes back around.");
		put(f, Line.THANKS_GIFT,
			"Thanks for the %1$s! I'll make sure it goes to good use.",
			"The %1$s? I know just who needs this.",
			"Every %1$s helps. Thank you!");
		put(f, Line.RETREAT,
			"I'm hurt. Backing off for a bit.",
			"Too much! Falling back.",
			"Ouch. Retreating to patch myself up.");
		put(f, Line.RECOVERED,
			"Right as rain. Back to gathering.",
			"Much better. Now, where were those trees?",
			"Healed up. Let's get back to it.");
		put(f, Line.CREEPER,
			"Creeper! Drop what you're doing and run!",
			"Creeper nearby! Clear off, quick!",
			"Creeper! I'm not losing my woodpile to that!");
		put(f, Line.FIGHT,
			"Clear off, %1$s!",
			"I'll deal with this %1$s.",
			"Not today, %1$s!");
		put(f, Line.WARNING,
			"%1$s, watch out: %2$s!",
			"%1$s, careful now: %2$s!",
			"Heads up, %1$s: %2$s!");
		put(f, Line.FRIEND_DIED,
			"%1$s... I'll plant a sapling for you.",
			"We've lost %1$s. Let's look after each other.",
			"Goodbye, %1$s. You always gave so much.");
		put(f, Line.PLAYER_HURT,
			"%1$s, you're badly hurt! I've got food if you need it.",
			"%1$s! Back off and eat something, quick!",
			"%1$s, you need healing. Take a break, please.");
		put(f, Line.NIGHT_RETURN,
			"Light's going. Heading back with what I've got.",
			"Getting dark. Back to camp to share out the haul.",
			"Dusk. Time to head home.");
		put(f, Line.MORNING,
			"Morning! The trees are waiting.",
			"Good morning. Who needs what today?",
			"Morning! I'll fetch whatever the camp's short of.");
		put(f, Line.WORK_START,
			"I'll be %1$s. Back soon with plenty.",
			"Off I go, %1$s.",
			"Time for %1$s. Oak will be pleased.");
		put(f, Line.NEED_TOOL,
			"Any %1$s going spare? Can't gather without one.",
			"Tool needed: %1$s. Anyone?",
			"I need a tool: %1$s. Then I'll bring back loads.");
		put(f, Line.TOOL_BROKE,
			"My %1$s broke. Well, it did a lot of work.",
			"There goes my %1$s. I'll find another.",
			"Snap! My %1$s is done for.");
		put(f, Line.NEED_MATERIALS,
			"I'm short of %1$s. I'll keep looking.",
			"Need %1$s. Can anyone help?",
			"Can't carry on without %1$s.");
		put(f, Line.DEPOSIT,
			"The chest's stocked. Help yourselves, everyone.",
			"Dropped the haul in the chest. Plenty to share.",
			"Wood and stone in the chest. Take what you need.");
		put(f, Line.SHARE,
			"Here you go, %1$s: %2$s.",
			"%1$s, I brought you %2$s. Plenty more where that came from.",
			"For you, %1$s: %2$s. Share and share alike.",
			"%1$s! Got %2$s for you.");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s. Good teamwork.",
			"That's the %1$s done. Nice work.",
			"The %1$s: finished. Glad I could chip in.");
		put(f, Line.CONTRAPTION_DONE,
			"Finished the %1$s. Clever stuff!",
			"The %1$s: done. Glad I could help.",
			"That's the %1$s working. Brilliant.");
		put(f, Line.DISCOVERY,
			"Good find: %1$s",
			"Worth sharing: %1$s",
			"Have a look: %1$s");
		put(f, Line.ADVICE,
			"Forager's tip: %1$s",
			"Here's a tip: %1$s",
			"Pass it on: %1$s");
		put(f, Line.UNITY_UP,
			"%1$s! What's mine is yours, always.",
			"We're %1$s now. Feels good to share it all.",
			"%1$s! Best team I've ever gathered for.");
		put(f, Line.CAMP_UP,
			"A %1$s! I'll need more wood. Lots more.",
			"We're a %1$s now. All that gathering paid off.",
			"Our camp's a %1$s! I'll keep the supplies coming.");
		put(f, Line.IDLE,
			"Plant a sapling for every tree you fell.",
			"Berries, sticks, stones. It all adds up.",
			"Anyone short of anything? I've got spares.",
			"A full chest is good. A shared one's better.");
	}
}
