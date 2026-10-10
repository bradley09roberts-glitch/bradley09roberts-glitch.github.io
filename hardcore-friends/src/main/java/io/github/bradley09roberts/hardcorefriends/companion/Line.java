package io.github.bradley09roberts.hardcorefriends.companion;

/**
 * Situations a friend can speak about. Each friend has their own wording in {@link Lines}. Arguments are
 * substituted with {@link String#format}; the documented placeholders say what each line receives, and
 * {@link #args()} says how many there are (the dialogue self-check, {@link Lines#problems()}, holds every
 * template to it).
 */
public enum Line {
	// Lifecycle and orders
	/** Just recruited. %1$s = player name. */
	RECRUITED(Priority.IMPORTANT, 0, 1),
	/** Ordered to follow. %1$s = player name. */
	FOLLOW(Priority.IMPORTANT, 0, 1),
	/** Ordered to hold position. */
	STAY(Priority.IMPORTANT, 0, 0),
	/** Ordered back to autonomous work. */
	WORK(Priority.IMPORTANT, 0, 0),
	/** Dismissed from the team. */
	DISMISSED(Priority.IMPORTANT, 0, 0),
	/** Player right-clicked them: a status greeting. %1$s = current activity, %2$s = player name. */
	GREETING(Priority.IMPORTANT, 0, 2),
	/** Player handed them food. */
	THANKS_FOOD(Priority.IMPORTANT, 0, 0),
	/** Player handed them a non-food item. %1$s = item name. */
	THANKS_GIFT(Priority.IMPORTANT, 0, 1),

	// Danger
	/** Health low; falling back. */
	RETREAT(Priority.DANGER, 200, 0),
	/** Recovered after retreating. */
	RECOVERED(Priority.CASUAL, 600, 0),
	/** Saw a creeper close by. */
	CREEPER(Priority.DANGER, 200, 0),
	/** Engaging a hostile mob. %1$s = mob name. */
	FIGHT(Priority.CASUAL, 400, 1),
	/** Warning a player about something. %1$s = player name, %2$s = hazard description. */
	WARNING(Priority.DANGER, 100, 2),
	/** Another friend just died. %1$s = their name. */
	FRIEND_DIED(Priority.IMPORTANT, 0, 1),
	/** A player is badly hurt nearby. %1$s = player name. */
	PLAYER_HURT(Priority.DANGER, 600, 1),

	// Day cycle
	/** Heading home because night is falling. */
	NIGHT_RETURN(Priority.CASUAL, 6000, 0),
	/** Morning greeting. */
	MORNING(Priority.CASUAL, 12000, 0),
	/** Taking the night watch while the others sleep. */
	NIGHT_WATCH(Priority.IMPORTANT, 6000, 0),
	/** On watch, raising the alarm: a hostile has come into the camp. %1$s = its name as the game shows it ("Zombie"). */
	ALARM(Priority.DANGER, 200, 1),

	// Work
	/** Starting their main job. %1$s = activity. */
	WORK_START(Priority.CASUAL, 1200, 1),
	/** Needs a tool. %1$s = tool kind, e.g. "pickaxe". */
	NEED_TOOL(Priority.CASUAL, 2400, 1),
	/** Their tool just broke. %1$s = item name. */
	TOOL_BROKE(Priority.IMPORTANT, 0, 1),
	/** Missing materials. %1$s = short list, e.g. "12 planks, 3 glass panes". */
	NEED_MATERIALS(Priority.CASUAL, 2400, 1),
	/** Dropped items into the supply chest. */
	DEPOSIT(Priority.CASUAL, 2400, 0),
	/** Gave something to another friend or player. %1$s = recipient, %2$s = item description. */
	SHARE(Priority.CASUAL, 600, 2),
	/** Finished a structure. %1$s = structure name. */
	BUILD_DONE(Priority.IMPORTANT, 0, 1),
	/** Finished a contraption. %1$s = contraption name. */
	CONTRAPTION_DONE(Priority.IMPORTANT, 0, 1),
	/** Found something interesting. %1$s = description with coordinates. */
	DISCOVERY(Priority.CASUAL, 600, 1),
	/** Strategy or survival advice. %1$s = the advice text. */
	ADVICE(Priority.CASUAL, 1200, 1),
	/** The team bond reached a new level. %1$s = level name. */
	UNITY_UP(Priority.IMPORTANT, 0, 1),
	/** The camp reached a new stage. %1$s = stage name. */
	CAMP_UP(Priority.IMPORTANT, 0, 1),
	/** Idle small talk with no arguments. */
	IDLE(Priority.CASUAL, 3600, 0),

	// Everyday needs (Sims-style)
	/** Getting hungry; going to find food. */
	HUNGRY(Priority.CASUAL, 2400, 0),
	/** Just ate. %1$s = food name as the item shows it, e.g. "Bread" or "Sweet Berries" (capitalised). */
	ATE(Priority.CASUAL, 1200, 1),
	/** Hungry and there is no food in the backpack or the supply chest. */
	NO_FOOD(Priority.IMPORTANT, 4800, 0),
	/** Very hungry: starving hurts. */
	STARVING(Priority.DANGER, 1200, 0),
	/** Tired; going to bed. */
	SLEEPY(Priority.CASUAL, 6000, 0),
	/** Woke up rested. */
	RESTED(Priority.CASUAL, 6000, 0),
	/** Opening a chat with another friend. %1$s = the other friend's name. */
	CHAT(Priority.CASUAL, 600, 1),
	/** Answering a friend who started a chat. %1$s = the other friend's name. */
	CHAT_REPLY(Priority.CASUAL, 600, 1),
	/** Starting some fun. %1$s = what they are doing, e.g. "skipping stones". */
	LEISURE(Priority.CASUAL, 1200, 1),
	/** Warming up by the fire or under a roof. */
	COSY(Priority.CASUAL, 2400, 0),
	/**
	 * Mood has dropped low. %1$s = the need that is worst, as a lower-case word: "hunger", "energy", "social",
	 * "fun" or "comfort" (templates read it as "my %1$s need" or "worst need: %1$s").
	 */
	MOOD_LOW(Priority.CASUAL, 6000, 1),
	/** Mood is great. Rare (15 minutes apart), so a happy camp of nine is not a chorus. */
	MOOD_GREAT(Priority.CASUAL, 18000, 0),
	/** Picking up work outside their speciality. %1$s = the job, e.g. "harvesting crops". */
	HELPING_OUT(Priority.CASUAL, 2400, 1),

	// Livestock
	/** Bringing a wild animal home to the pen, on a lead or following its food. %1$s = the animal, e.g. "cow". */
	LEADING_ANIMAL(Priority.CASUAL, 1200, 1),
	/** Fed two of the pen's animals so they breed. %1$s = the animals, plural, e.g. "pigs". */
	BRED_ANIMALS(Priority.CASUAL, 1200, 1),
	/** About to butcher one of the pen's surplus animals. %1$s = the animal, e.g. "pig". */
	BUTCHERING(Priority.CASUAL, 1200, 1),
	/** Putting raw meat on the campfire. %1$s = what is cooking, lower case, e.g. "beef" or "porkchop". */
	COOKING(Priority.CASUAL, 1200, 1),
	/** Setting off to hunt a wild animal for the camp. %1$s = the animal, e.g. "rabbit". */
	HUNTING(Priority.CASUAL, 1200, 1),

	// ==== Combat and gear (package combat): bows, shields, armour, healing ====
	// (add this package's lines below this comment, each ending with a comma)
	/** Drawing a bow on a hostile. %1$s = its name as the game shows it ("Skeleton"). */
	DRAW_BOW(Priority.CASUAL, 600, 1),
	/** Raising a shield against an archer drawing on them or a creeper about to blow. */
	RAISE_SHIELD(Priority.CASUAL, 600, 0),
	/** Eating or drinking something to heal in a fight. %1$s = the item as the game shows it ("Golden Apple"). */
	EMERGENCY_HEAL(Priority.DANGER, 300, 1),
	/** Put on or took up better gear from the chest. %1$s = the item as the game shows it ("Iron Chestplate"). */
	NEW_GEAR(Priority.CASUAL, 1200, 1),
	/** Made a piece of gear at the crafting table for the chest. %1$s = the item as the game shows it ("Shield"). */
	MADE_GEAR(Priority.CASUAL, 1200, 1),
	/** Taking over the fight from a friend who is falling back hurt. %1$s = that friend's name, %2$s = the mob's name. */
	TAKE_OVER(Priority.CASUAL, 400, 2),

	// ==== end of combat ====

	// ==== Independence (package survival): trips, shelters, making room to build, skills ====
	// (add this package's lines below this comment, each ending with a comma)
	/** Setting off on a trip away from camp. %1$s = where to, e.g. "the hills to the north" or "the village at 120 64 -340". */
	TRIP_START(Priority.IMPORTANT, 1200, 1),
	/** Found something worth knowing on a trip (heard by everyone). %1$s = what and where, e.g. "a village around 120 64 -340". */
	TRIP_FIND(Priority.IMPORTANT, 0, 1),
	/** Back at camp after a trip (heard by everyone). %1$s = what came of it, e.g. "a village and two new places". */
	TRIP_BACK(Priority.IMPORTANT, 0, 1),
	/** Cutting a trip short and heading home (hurt, hungry, late or blocked). */
	TRIP_TURN_BACK(Priority.CASUAL, 1200, 0),
	/** Made a trade with a villager. %1$s = what they got, e.g. "6 bread" or "3 emeralds". */
	TRADED(Priority.CASUAL, 600, 1),
	/** Caught far from camp at night: digging in or walling up for the night. */
	SHELTER(Priority.IMPORTANT, 6000, 0),
	/** Morning after a night in a shelter: taking it down and heading home. */
	SHELTER_MORNING(Priority.CASUAL, 6000, 0),
	/** Cornered and badly hurt: building a pillar up out of reach. */
	CORNERED(Priority.DANGER, 400, 0),
	/** Landed a long fall safely in water poured from a bucket. */
	CLUTCH(Priority.CASUAL, 600, 0),
	/** Got better at something. %1$s = the skill, e.g. "farming" or "fighting"; %2$s = the new level, e.g. "3". */
	LEVEL_UP(Priority.IMPORTANT, 0, 2),
	/** No room for a building in the camp, even levelled: the camp grows a little. %1$s = the building, e.g. "cabin". */
	LOOKING_FURTHER(Priority.CASUAL, 2400, 1),

	// ==== end of survival ====

	// ==== Newcomers (package settler): strangers, their requests, joining the team ====
	// (add this package's lines below this comment, each ending with a comma)
	/** A stranger (not on the team) greets a player who comes close. */
	STRANGER_HELLO(Priority.IMPORTANT, 3600, 0),
	/**
	 * A stranger, having said who they are, asks for something before joining. %1$s = what they ask for, e.g.
	 * "4 bread and a hoe" (templates read it as "could you bring me %1$s?").
	 */
	STRANGER_ASKS(Priority.IMPORTANT, 0, 1),
	/** Asked again without everything yet. %1$s = what is still missing, e.g. "2 more bread and a hoe". */
	STRANGER_NOT_YET(Priority.IMPORTANT, 100, 1),
	/** The stranger joins the team. %1$s = the player who brought what they asked for. */
	STRANGER_JOINS(Priority.IMPORTANT, 0, 1),
	/** The stranger would join, but the team (or this player's share of it) is full. */
	STRANGER_TEAM_FULL(Priority.IMPORTANT, 200, 0),
	/** A traveller arrives at the edge of the camp, hoping to be asked in. */
	WANDERER_ARRIVES(Priority.IMPORTANT, 0, 0),
	/** A traveller nobody asked in sets off again after their day at the camp. */
	WANDERER_LEAVES(Priority.IMPORTANT, 0, 0),

	// ==== end of settler ====

	// ==== Beating the game (package progress): Sage's plan, deep mining, enchanting, brewing ====
	// (add this package's lines below this comment, each ending with a comma)
	/** Sage (or whoever plans in her place) sets the camp's next goal. %1$s = the goal, e.g. "a diamond pickaxe in the camp". */
	GOAL_NEW(Priority.IMPORTANT, 0, 1),
	/** A step of Sage's plan is reached. %1$s = the step's name, e.g. "Iron age". */
	GOAL_REACHED(Priority.IMPORTANT, 0, 1),
	/** Spotted diamonds while mining. */
	FOUND_DIAMONDS(Priority.IMPORTANT, 1200, 0),
	/** Found lava while mining, left the wall in place and went round it. */
	LAVA_SEALED(Priority.IMPORTANT, 2400, 0),
	/** Poured water on lava and made obsidian. */
	OBSIDIAN_MADE(Priority.CASUAL, 1200, 0),
	/** Enchanted a piece of gear at the table. %1$s = the item's name as the game shows it, e.g. "Iron Sword". */
	ENCHANTED(Priority.IMPORTANT, 600, 1),
	/** Took finished potions off the brewing stand. %1$s = what they are, lower case, e.g. "fire resistance". */
	POTIONS_BREWED(Priority.IMPORTANT, 600, 1),
	/** Mended worn gear at the anvil. %1$s = the item's name as the game shows it, e.g. "Iron Pickaxe". */
	REPAIRED(Priority.CASUAL, 1200, 1),

	// ==== end of progress ====

	// ==== Expeditions (package expedition): portals, the Nether, the stronghold, the End ====
	// (add this package's lines below this comment, each ending with a comma)
	/** Came through a portal right behind their leader (one friend speaks for the group). */
	PORTAL_THROUGH(Priority.IMPORTANT, 1200, 0),
	/** Left on their own in another dimension: heading home through the portal they came in by. */
	PORTAL_HOME(Priority.IMPORTANT, 2400, 0),
	/** The Nether portal at the camp has just been lit. */
	PORTAL_LIT(Priority.IMPORTANT, 0, 0),
	/** Tossing a gold ingot to a piglin to barter. */
	BARTER(Priority.CASUAL, 1200, 0),
	/** Brought down a blaze in the Nether (a blaze rod for the plan, with luck). */
	BLAZE_ROD(Priority.IMPORTANT, 600, 0),
	/** Spotted a Nether fortress. */
	FORTRESS_SEEN(Priority.IMPORTANT, 6000, 0),
	/** Threw an eye of ender to find the stronghold. %1$s = the compass direction it flew, e.g. "north-east". */
	EYE_THROWN(Priority.IMPORTANT, 200, 1),
	/** Home from finding the stronghold. %1$s = where it is, e.g. "x 1204, z -388" (templates end with it). */
	STRONGHOLD_FOUND(Priority.IMPORTANT, 0, 1),
	/** Set the last eye in the End portal frame, and it opened. */
	PORTAL_FILLED(Priority.IMPORTANT, 0, 0),
	/** Setting off to pillar up beside a caged end crystal and open its cage. */
	CAGE_CLIMB(Priority.IMPORTANT, 600, 0),
	/** An end crystal they shot has gone up. */
	CRYSTAL_DOWN(Priority.IMPORTANT, 100, 0),
	/** The ender dragon has landed on the portal: time for blades. */
	DRAGON_PERCHED(Priority.DANGER, 400, 0),
	/** The ender dragon is defeated. */
	VICTORY(Priority.IMPORTANT, 0, 0),

	// ==== end of expedition ====

	// ==== Several players (package town): trust, bonds, jobs, mourning, notes, deliveries ====
	// (add this package's lines below this comment, each ending with a comma)
	/** A warm hello to a player this friend is close to (right-click). %1$s = player name. */
	GREET_WARM(Priority.IMPORTANT, 0, 1),
	/** A cool hello to a player who has hurt or let down this friend (right-click). %1$s = player name. */
	GREET_COOL(Priority.IMPORTANT, 0, 1),
	/** A player has died in a Hardcore world. %1$s = their name. */
	MOURN_PLAYER(Priority.IMPORTANT, 0, 1),
	/** About to read out a note left for the camp (the note itself follows). %1$s = who wrote it. */
	NOTE_FOUND(Priority.IMPORTANT, 0, 1),
	/** A player delivered what the camp asked for on the job board. %1$s = player name. */
	THANKS_DELIVERY(Priority.IMPORTANT, 0, 1),
	/** Left a delivery in a player's mailbox (heard by everyone). %1$s = whose mailbox. */
	MAIL_DELIVERED(Priority.IMPORTANT, 0, 1),
	/** Politely refusing an order from a player the camp does not trust. %1$s = player name. */
	REFUSE_ORDER(Priority.IMPORTANT, 100, 1),
	/** Politely refusing to follow a player they no longer trust (a very low bond). %1$s = player name. */
	REFUSE_FOLLOW(Priority.IMPORTANT, 100, 1),
	/** Gathering a fallen player's things to keep them safe in the chest. %1$s = whose things. */
	KEEPING_ITEMS(Priority.IMPORTANT, 600, 1),
	/** At dusk before a siege night: monsters will gather at the camp's edge at midnight. */
	SIEGE_DUSK(Priority.DANGER, 0, 0),
	/** Dawn after a siege night that everyone lived through. */
	SIEGE_HELD(Priority.IMPORTANT, 0, 0),

	// ==== end of town ====

	// ==== Better builds (package architecture): plans, materials, scaffolding ====
	// (add this package's lines below this comment, each ending with a comma)
	/** Putting up a scaffolding pillar to reach a high wall or roof. %1$s = the building, e.g. "cabin" or "oak cottage". */
	SCAFFOLDING(Priority.CASUAL, 3600, 1),
	/** Finished one of the village's buildings (heard by everyone near). %1$s = the building, e.g. "oak cottage". */
	BUILDING_FINISHED(Priority.IMPORTANT, 0, 1),
	/** Loading the camp furnace to make a building material. %1$s = what, e.g. "glass" or "stone". */
	FIRING_KILN(Priority.CASUAL, 2400, 1),
	/** Off to dig something the builders need from the ground. %1$s = what, "sand" or "clay". */
	DIGGING_SAND(Priority.CASUAL, 2400, 1),
	/** Shearing a wild sheep for the builders' wool. */
	SHEARING(Priority.CASUAL, 2400, 0),

	// ==== end of architecture ====

	// ==== Finding the way (package navigation): getting unstuck, caves, water, sprinting ====
	// (add this package's lines below this comment, each ending with a comma)
	/** Stuck on the spot while trying to get somewhere; trying another way. */
	STUCK(Priority.CASUAL, 1200, 0),
	/** Free again after being stuck. */
	UNSTUCK(Priority.CASUAL, 1200, 0),
	/** Lost underground: setting off to find the way out to daylight. */
	LOST_IN_CAVE(Priority.IMPORTANT, 2400, 0),
	/** Out of a cave and under the open sky again. */
	FOUND_WAY_OUT(Priority.IMPORTANT, 2400, 0),
	/** No way out on foot: digging a staircase up. */
	DIGGING_OUT(Priority.IMPORTANT, 2400, 0),
	/** Under water and running out of air. */
	GASPING(Priority.DANGER, 200, 0),
	/** Stuck in the water or caught in a current: swimming for the shore. */
	SWIMMING_OUT(Priority.CASUAL, 1200, 0),
	/** Just brought back (home, or to their leader) after being stuck or lost a long time. */
	RESCUED(Priority.IMPORTANT, 0, 0),
	/** Setting off at a run on a long way. */
	SPRINTING(Priority.CASUAL, 6000, 0),

	// ==== end of navigation ====

	// ==== Living together (package people): friendship, romance, weddings, children ====
	// (add this package's lines below this comment, each ending with a comma)
	/** Two friends have become good friends. %1$s = the other friend's name. */
	BECAME_FRIENDS(Priority.IMPORTANT, 1200, 1),
	/** Asking another friend to go out together. %1$s = their name. */
	ASK_OUT(Priority.IMPORTANT, 0, 1),
	/** Saying yes to going out, or to a proposal. %1$s = the one who asked. */
	ACCEPT(Priority.IMPORTANT, 0, 1),
	/** Setting off on a date (an evening walk, the fireside, the sunset). %1$s = their sweetheart's name. */
	DATE(Priority.IMPORTANT, 2400, 1),
	/** Asking their sweetheart to marry them. %1$s = their sweetheart's name. */
	PROPOSE(Priority.IMPORTANT, 0, 1),
	/** Their promise at their own wedding. %1$s = the one they are marrying. */
	WEDDING_VOWS(Priority.IMPORTANT, 0, 1),
	/** A guest's toast at a wedding. %1$s = the couple, e.g. "Fern and Oak". */
	WEDDING_TOAST(Priority.IMPORTANT, 0, 1),
	/** A couple are expecting a baby. %1$s = the other parent's name. */
	BABY_NEWS(Priority.IMPORTANT, 0, 1),
	/** A parent introducing the new baby. %1$s = the baby's name. */
	BABY_ARRIVED(Priority.IMPORTANT, 0, 1),
	/** A parent whose child has died. %1$s = the child's name. */
	MOURN_CHILD(Priority.IMPORTANT, 0, 1),
	/** A friend whose husband or wife has died. %1$s = their name. */
	MOURN_PARTNER(Priority.IMPORTANT, 0, 1),
	/** A cross word between two friends who are both in a low mood. %1$s = the other friend's name. */
	QUARREL(Priority.CASUAL, 6000, 1),
	/** Calling off a romance that has gone sour (they stay on the team). %1$s = the other's name. */
	BREAK_UP(Priority.IMPORTANT, 0, 1),
	/** A child's first words, just arrived in the camp. Only children say it. */
	CHILD_FIRST_WORDS(Priority.IMPORTANT, 0, 0),
	/** A child at play. %1$s = the game: "tag", "hide-and-seek", "chicken chase" or "explorers". Only children say it. */
	CHILD_PLAY(Priority.CASUAL, 600, 1),
	/** A child watching and learning. %1$s = the kind of work, e.g. "farming". Only children say it. */
	CHILD_LEARN(Priority.CASUAL, 1200, 1),
	/** A child sent home early at the end of the day. Only children say it. */
	CHILD_BEDTIME(Priority.CASUAL, 6000, 0),
	/** A child running from a monster to a grown-up. Only children say it. */
	CHILD_SCARED(Priority.DANGER, 200, 0),
	/** A child who has just grown up. %1$s = their trade, lower case, e.g. "farmer". */
	GROWN_UP(Priority.IMPORTANT, 0, 1),

	// ==== end of people ====

	// ==== A proper village (package village): homes, beds, routines, growth ====
	// (add this package's lines below this comment, each ending with a comma)

	// ==== end of village ====

	// ==== Shops and trades (package market): professions, shops, trading ====
	// (add this package's lines below this comment, each ending with a comma)
	/** Opening the shop for the day at the counter. %1$s = the shop, e.g. "the bakery" or "a market stall". */
	SHOP_OPEN(Priority.CASUAL, 12000, 1),
	/** Thanking a player for a trade across the counter. %1$s = the player's name. */
	SHOP_SALE(Priority.CASUAL, 600, 1),
	/** Taking up a village trade. %1$s = the trade, lower case, e.g. "baker" or "cook". */
	TRADE_TAKEN(Priority.IMPORTANT, 0, 1),
	/** Casting a line at the water. */
	FISHING(Priority.CASUAL, 2400, 0),
	/** At work at the bakery's oven. */
	BAKING(Priority.CASUAL, 2400, 0),
	/** Shearing the camp's own sheep over the pen fence. */
	SHEARING_FLOCK(Priority.CASUAL, 2400, 0),
	/** Taking honey or honeycomb from the hives. */
	HONEY(Priority.CASUAL, 2400, 0),
	/** Teaching the children at school. */
	TEACHING(Priority.CASUAL, 3600, 0),
	/** Seeing to a hurt or poorly friend. %1$s = the patient's name. */
	HEALING(Priority.CASUAL, 600, 1),
	/** At work in their trade. %1$s = the work, an "-ing" phrase, e.g. "cutting stone". */
	TRADE_WORK(Priority.CASUAL, 2400, 1),

	// ==== end of market ====

	// ==== Village life (package life): calendar, festivals, music, funerals, the Chronicle ====
	// (add this package's lines below this comment, each ending with a comma)
	// ==== end of life ====

	// ==== Defending the village (package defence): the bell, guards, raids, fire ====
	// (add this package's lines below this comment, each ending with a comma)
	// ==== end of defence ====

	// ==== Pets and maps (package pets) ====
	// (add this package's lines below this comment, each ending with a comma)
	/** A friend or child has just adopted a pet and named it. %1$s = the pet's name, %2$s = "cat" or "dog". */
	PET_ADOPTED(Priority.IMPORTANT, 0, 2),
	/** Playing with or making a fuss of their own pet. %1$s = the pet's name, %2$s = "cat" or "dog". */
	PET_PLAY(Priority.CASUAL, 2400, 2),
	/** Calling their pet over by name. %1$s = the pet's name. */
	PET_CALL(Priority.CASUAL, 1200, 1),
	/** Their pet has died or gone missing for good. %1$s = the pet's name, %2$s = "cat" or "dog". */
	PET_LOST(Priority.IMPORTANT, 0, 2),
	/** The map maker has finished a map. %1$s = what it shows, e.g. "the camp" or "the land to the north-east". */
	MAP_FINISHED(Priority.IMPORTANT, 0, 1),
	/** The map maker hands a player a copy of a map. %1$s = the player's name. */
	MAP_HANDED(Priority.IMPORTANT, 0, 1),
	/** The map maker cannot make a map: the camp is short of paper or a compass. */
	MAP_NO_PAPER(Priority.IMPORTANT, 0, 0),
	// ==== end of pets ====
	;

	/** How a line is rate-limited and who hears it. */
	public enum Priority {
		/** Obeys the friend's chattiness gap and the global chatter setting. Skipped entirely when quiet. */
		CASUAL,
		/** Always shown to nearby players, ignoring the chattiness gap. */
		IMPORTANT,
		/** Always shown, even when chatter is quiet; only the per-line cooldown applies. */
		DANGER
	}

	private final Priority priority;
	private final int cooldownTicks;
	private final int args;

	Line(Priority priority, int cooldownTicks, int args) {
		this.priority = priority;
		this.cooldownTicks = cooldownTicks;
		this.args = args;
	}

	public Priority priority() {
		return priority;
	}

	/** Minimum ticks between two uses of this line by the same friend. */
	public int cooldownTicks() {
		return cooldownTicks;
	}

	/** How many arguments the line receives: its templates may use {@code %1$s} up to {@code %<args>$s}, no more. */
	public int args() {
		return args;
	}
}
