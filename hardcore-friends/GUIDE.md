# Hardcore Friends: installation and gameplay guide

**For:** Minecraft Java **26.3** with **Fabric**. Single-player or a small server. Works in Hardcore without enabling cheats.

## 1. Install

### Option A: CurseForge app (easiest)

1. Open the CurseForge app and go to **Minecraft → Create Custom Profile → Import**.
2. Choose `Hardcore-Friends-3.0.0-CurseForge.zip`.
3. CurseForge installs Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3 and the mod.
4. Press **Play**. This creates a separate profile with its own saves folder, so your existing worlds are not touched.

### Option B: Manual install

1. Install the Fabric Loader for Minecraft 26.3 with the official installer from fabricmc.net (choose loader 0.19.5 or newer).
2. Put two files in your `.minecraft/mods` folder:
   - `hardcore-friends-3.0.0.jar` (if you are updating, take the old `hardcore-friends-2.0.0.jar` out)
   - **Fabric API** `0.162.0+26.3` (from CurseForge or Modrinth)
3. Start the **Fabric** profile in the Minecraft Launcher.
4. **Back up your saves first.** Adding a mod to an existing world is permanent for that world.

> **Java:** Minecraft 26.3 needs Java 25. The official launcher and CurseForge provide it automatically.

## 2. Start playing

1. Create (or open) your Hardcore world. Commands work without cheats.
2. Gather a little common food. Each friend costs **2 food items**, in any mix of bread, apples, carrots, potatoes, beetroot, berries, melon slices, cookies, dried kelp, pumpkin pie, raw or cooked meat, or fish.
3. Stand where you want to live and type `/friends camp set`.
4. Place a chest or barrel nearby. Look at it and type `/friends chest`; this becomes the **shared supply chest**. If there is no chest, Oak builds one from planks.
5. Recruit: `/friends recruit fern`, `/friends recruit oak`, and so on.
6. Put useful starter supplies in the chest: logs, cobblestone, coal, seeds, a water bucket and **food**. The friends restock from it, deposit what they gather into it, build with what is in it, and eat from it when they are hungry.

### What's new in 3.0

**Nothing in 3.0 has been played yet.** It all compiles and every part was reviewed by reading it through (the building
plans were also checked by script), but you are the first to try it in game. Test in a copy of a world (or a new one)
first.

- **Proper buildings.** Oak's cabin is now a timber cottage, the storehouse and watchtower are rebuilt, and the village
  builds from a library of 54 plans in styles to suit the land. The friends make glass, bricks, stone bricks, beds and
  carpets themselves, and put up scaffolding to reach roofs (section 21).
- **Finding the way.** Paths that avoid cave detours, running water and long drops, hearing monsters through walls,
  getting themselves out of caves, holes and water, a rescue as a last resort, and sprinting (section 22).
- **Living together.** Friendships, romance, dates, weddings, babies, and children who play, learn and grow up into
  the team; family names; an 82-skin medieval village set for newcomers and children (section 23).
- **A proper village.** From the Village stage: a town plan with streets, a house for every household, their own beds,
  a daily routine, and two new stages after the Settlement, the Town and the City (section 24).
- **Trades and shops.** 15 trades at real workplaces, and shopkeepers who trade with you for emeralds (section 25).
- **Village life.** A calendar, market day, three feasts a year, music, birthdays, funerals and graves, and the
  Village Chronicle (section 26).
- **Defending the village.** An alarm bell, taking cover, guards at night, raids fought off together, and a fire watch
  (section 27).
- **Pets and maps.** Cats and dogs of their own, and Scout's maps on the town hall wall (section 28).

The friends do all of this on their own: they choose what to build and where, and you never have to give an order.
You are welcome to join in.

**Updating a 2.0 world.** Back it up first. A building started (or finished) before 3.0 keeps its old plan; new ones
use the new plans. A camp that is already a Village or more lays out its town plan straight away; the old cabins stay
as they are.

Each of those sections is a summary. The full details, settings and honest limits of each part are in `docs/v3/`
(`architecture.md`, `navigation.md`, `people.md`, `village.md`, `market.md`, `life.md`, `defence.md`, `pets.md`, and
`skin-prompt.md` for getting more skins made).

### What's new in 2.0

**Nothing in 2.0 has been played yet.** It all compiles and every part was read through and reviewed against the
game's own code, but you are the first to try it in game. Test in a copy of a world (or a new one) first.

- **They live on while you're away.** The camp keeps running while you are online anywhere (section 16).
- **They go off on their own.** Scout explores up to 300 blocks out, friends trade at villages, and a friend caught out
  at night builds a shelter (section 16).
- **They make room to build.** No flat spot? They level the ground, or spread the camp out a little (section 16).
- **They get better.** Every friend levels up in each kind of work and in fighting (section 16).
- **Gear and fighting.** Everyone wears the best armour they can get, carries a shield, shoots bows, heals in an
  emergency and fights as a team; a smith makes gear from spare materials (section 15).
- **Newcomers.** People to meet and recruit in villages, at new survivor camps in the world, and travellers who visit
  your camp (section 17).
- **Sage's plan to beat the game.** Iron, diamonds from a deep mine, obsidian, a library with an enchanting table,
  enchanting, an anvil, brewing (section 18).
- **Expeditions you lead.** Your party follows you through portals; they barter with piglins, hunt blazes, find the
  stronghold, fill the End portal and help fight the dragon (section 19).
- **Several players.** An owner and trusted players, a bond with each player, a job board, mourning and keeping a
  fallen player's things safe, notes, mailbox deliveries, opt-in siege nights (section 20).
- **Safer nights.** The night watch from 1.0's honest limits is in, with the problems found in it fixed (section 6b).

Each of those sections is a summary. The full details, settings and honest limits of each part are in `docs/v2/`
(`combat.md`, `survival.md`, `settler.md`, `progress.md`, `expedition.md`, `town.md`).

## 3. The nine friends

Each friend's role is their speciality: the work they choose first and do best. Any friend can do any of these jobs, though (see section 4).

| Friend | Role | Personality | Their speciality |
|---|---|---|---|
| **Fern** | Farmer | patient, caring | Harvests ripe crops and replants straight away, tills new farmland next to water, lays out a farm plot (with a water bucket from the chest), bakes bread, uses bone meal, shares food with hungry players. Keeps livestock: brings wild animals home to the pen, breeds them, butchers the surplus, cooks the meat, and hunts when food is short (section 6). Grows sugar cane and nether wart for Sage's plan |
| **Oak** | Builder | practical, methodical | Builds the camp step by step from real materials: chest, campfire, crafting table, furnace, torch posts, cabin, storehouse, watchtower, lantern posts, second cabin; later the library, the anvil, the brewing stand and the Nether portal. Saws planks, repairs damage and mends gear at the anvil |
| **Flint** | Miner | cautious, dry humour | Mines exposed ores (with the right pickaxe), digs one tidy staircase mine with branch tunnels outside camp, lights it with torches, avoids lava and water, smelts ore in the camp furnace. For Sage's plan he digs a branch mine down at diamond level, sealing off lava and caves, and makes obsidian |
| **Scout** | Explorer | curious, adventurous | Scouts widening rings around camp by day, records ores, trees, lava and villages for the team, and warns you about creepers, mobs closing in, nightfall and storms. Goes on day trips up to 300 blocks out, and finds the stronghold by throwing eyes of ender. Prefers the bow |
| **Spark** | Redstone inventor | clever, excitable | Builds working vanilla contraptions: automatic cabin door (pressure plates), drop-off hopper on the supply chest, auto-smelter, and night lamp posts (needs nether materials). Keeps the torch supply up, and brews potions at the brewing stand |
| **Aegis** | Warrior | calm, protective | Guards you and the camp, attacks hostile mobs near players and friends, and keeps the first night watch walking his posts round the camp (on the watchtower once it exists). Gets first pick of the armour, swords and shields in the chest |
| **Sage** | Strategist | thoughtful, observant | Gives Hardcore survival advice for your situation (health, hunger, darkness, phantoms, night, tools, mining depth), works out what the camp is short of, and sets the team's focus. Keeps the plan to beat the game, makes paper, books and eyes of ender in her workshop, and enchants gear. Carries a sword and prefers the bow |
| **Terra** | Landscaper | creative, tidy | Lays dirt paths between camp buildings, plants saplings and flowers, puts torches in dark spots (fewer mob spawns), tidies dropped items, fills small holes, fences the farm, builds the animal pen, and levels uneven ground so a building fits |
| **Rowan** | Forager | resourceful, generous | Fells natural trees outside camp and replants them, quarries dirt and stone from small shallow pits outside camp, picks berries, and delivers building materials to Oak first. Keen on trading trips to villages and on deliveries to players' mailboxes |

The friends talk in their own voices. Use `/friends chatter quiet|normal|chatty` to set how much. Danger warnings always show.

## 4. Everyone pitches in

Every friend can do every kind of work: farming, building, mining, exploring, redstone, landscaping and foraging. Their role is their **speciality**, and each also has one **interest**, the work they like next best.

| Friend | Speciality | Interest |
|---|---|---|
| Fern | Farming | Foraging |
| Oak | Building | Landscaping |
| Flint | Mining | Redstone |
| Scout | Exploring | Foraging |
| Spark | Redstone | Mining |
| Aegis | Guarding | Exploring |
| Sage | Planning | Farming |
| Terra | Landscaping | Farming |
| Rowan | Foraging | Building |

How they share the work:

- **Specialists go first.** Whenever there is work of their own kind, a friend does it; other people's work comes after.
- **Standing in.** When nobody of a speciality is working (that friend has died, has not been recruited yet, or is following you or holding position), the others pick that work up in their spare time, their interest first. A camp without Fern still gets its crops harvested and replanted, just less eagerly.
- **Lending a hand.** While the specialist is around, the others only help with that work when they have nothing else at all to do.
- **One at a time on shared jobs.** A building, the mine, the quarry, the farm's layout, the paths, a tree being felled: one friend works on each at a time, so two friends never build the same cabin. When the specialist comes back to work, a stand-in hands the job back at once.
- **Skill.** Specialists mine, chop and dig 20% faster at their own work. Their interest goes at normal speed, and anything else 15% slower. Everyone also gets faster with practice (section 16), but a stand-in never overtakes the specialist.
- **Duties that stay with one friend:** Aegis's guard duty, Sage's observations and stores review, Scout's reports and her far trips.
- **Newcomers** (section 17) work like the named friend who shares their trade: a newcomer builder is a second Oak, with their own name.

A friend starting work outside their speciality may say so, for example "I'll lend a hand with harvesting crops."

## 5. Needs and mood

Like characters in The Sims, every friend has five everyday needs, each from 0 (desperate) to 100 (fully met). Friends look after them on their own.

| Need | Goes down | How friends meet it |
|---|---|---|
| **Hunger** | All the time, a little faster while working. About half a loaf of bread's worth a day: a loaf every two days, so nine friends eat four or five loaves a day | They eat real food: from their backpack first, otherwise one item from the supply chest. Bowls go back in the backpack |
| **Energy** | While awake, so they are tired by nightfall | They go to bed at nightfall and sleep until dawn: in their own bed once they have a house in the village (section 24), otherwise inside the cabin once it is built, otherwise around the camp centre, unless it is their turn on watch (section 6). A night you sleep through counts as a whole night's sleep for them too. A friend who is exhausted naps by day, and one who kept a watch naps sooner |
| **Social** | While alone. Being near a friend or you fills it | They walk over to another friend for a chat |
| **Fun** | While working | They take a short break for a pastime that suits them: Flint skips stones, Spark tinkers with a gadget, Sage watches the clouds or the stars, and so on. Pastimes never change your world |
| **Comfort** | In rain or darkness out in the open, and while badly hurt | They warm up by a lit campfire or go indoors. A roof overhead and a lit campfire nearby are cosy |

The lower a need, the more urgent it is. A mild need waits until the job in hand is done; a desperate one comes before any work, however pressing: a starving friend drops everything to eat when there is food. At night work waits for the morning, so friends go to bed (section 6). Work never wakes a sleeping friend. Friends only see to their needs while working on their own: a friend who is following you or holding position puts them off until you send them back to work.

**Mood.** Together the needs make a mood: miserable, low, okay, good or great. Hunger and energy count the most.

- Mood changes how fast friends work: 80% when miserable, up to 110% when every need is met.
- Friends say how they feel now and then. A friend in a low mood names their worst need ("Worst need: fun").
- When the whole team is in high spirits (a good average mood of 75 or more, and nobody feeling low), the Unity bond grows by 1 every in-game hour (at most 12 a day). A fed, rested camp that spends time together gets there. A low mood never costs Unity.

**Food.** Keep food in the supply chest. Bread, baked potatoes, carrots, apples, berries, cookies, dried kelp, pumpkin pie, stews and cooked meat or fish all count; filling food such as cooked beef satisfies more hunger. Friends never eat raw potatoes, meat or fish: those only count once they are cooked, by you, by Fern on the campfire or in the furnace (section 6), or, from 3.0, by the camp's cook (section 25).

- Fern's farm feeds the camp. Wheat makes bread (three wheat a loaf); carrots feed about three times as many friends per farmland, so give Fern a few carrots and she plants them first. She keeps 16 for planting and puts the rest in the chest to eat. Her first small farm cannot feed nine friends on wheat alone, so bring some food while the camp is new; once the farm grows at stage 1 it can.
- A hungry friend who finds no food in their backpack or the chest says so and carries on working. Put some food in the chest.
- At hunger 0 a friend is **starving**: they lose half a heart every 4 seconds until they are down to one heart, just as you do on Normal difficulty, and they cannot heal, not even at camp. Starving never kills a friend on its own, but a friend on one heart dies to almost anything. A friend that weak stops all work away from camp (no mining, tree felling or exploring) and rests by the campfire until there is food, then eats at once. Keep the food coming.
- Hand food to a friend who is hurt or hungry and they eat it at once (it heals them and fills their hunger). A friend who is not hungry puts it in their backpack for later.

Use `/friends needs` to see everyone's needs as bars, their mood, and what each friend is doing about their lowest need. Right-clicking a friend also shows their mood.

## 6. Livestock

Nine friends eat a lot, and wheat alone struggles to keep up: a loaf of bread takes three wheat, while one cooked steak or porkchop fills a friend about as much as one and a half loaves. So once the camp is a Hamlet, the friends keep animals. This is Fern's work; the others help as with any job (section 4).

**The animal pen.** Terra builds a 9×9 ring of wooden fences with a gate inside the camp, on a level patch of natural ground away from your builds, with the gate facing the camp centre. It is built from real materials like any building (31 fences and a gate: about 15 logs' worth of planks and sticks, crafted at a crafting table). The pen is optional: the camp grows to the next stage without it, but the friends keep no animals until it stands. If a fence or the gate goes missing, Terra puts it back.

**Bringing animals home.** By day, Fern looks for wild cows, pigs, sheep and chickens in the camp and the gathering ring and brings them home until the pen has a pair of each. An animal of a kind the pen keeps that got out and wanders the camp is brought back too, while its kind has room.

- **With a lead** in the supply chest she ties it on and leads the animal home. The lead is used up while it is on the animal and comes back when she unties it in the pen, as in vanilla. Leads are optional.
- **Without a lead** she holds the animal's favourite food and it follows her: wheat for cows and sheep, a carrot, potato or beetroot for pigs, seeds for chickens. The food is only held, not used up.
- At the gate she first sends the pen's animals standing by it to the back, opens it, draws the animal in a few steps and shuts the gate behind them both. Then she lets it go at the back of the pen and goes out the way every pen job does. If it lags behind outside, she goes out and shuts the gate before going back for it.

**Breeding.** When two grown animals of a kind in the pen are ready, Fern takes two of their food from her backpack or the chest, goes in (shutting the gate behind her), feeds them (the food is used up), and they have a young one. The pen holds up to 6 of a kind (three pairs) and 12 animals in all.

**Butchering.** Fern keeps 4 grown animals of each kind for breeding. When the pen is full (12 animals) she keeps fewer, so there is always room to breed again: 2 of each kind with four kinds in the pen, 3 with three. She butchers the extra ones with a sword or an axe (her own, or one borrowed from the chest), up to two a visit, and gathers the meat, leather, wool and feathers. She never touches a young animal or one in love, never takes a kind below a breeding pair, and never butchers an animal of yours in the pen (named, on a lead, saddled, tamed or owned): those count among the ones she keeps. She only strikes with the gate shut, and goes out again if someone opens it.

**Cooking.** Raw beef, pork, mutton, chicken, rabbit and fish (from her backpack or the chest) go on the camp's lit campfire, up to four at a time. Fern waits by the fire and picks up each piece as it comes off a spot she filled, then puts the cooked food in the supply chest, where everyone eats from. Your own cooking on the same fire, and cooked food already lying about, is left alone (the friends do tidy up items left lying around the camp for a while, into the chest). When the campfire is full she uses the plain furnace the friends built instead, with coal, charcoal or planks fetched for exactly what goes in: she never puts in more meat than the fuel can cook, so the furnace is never left blocked with raw meat. If it ever does go out with raw meat in it, she gives it the fuel it needs. Whoever empties the furnace brings the food to the chest.

**Hunting.** When the camp is short of food, a friend who is healthy and carries a sword or an axe hunts a wild cow, pig, sheep, chicken or rabbit in the gathering ring outside the camp and brings back what it drops.

**Your animals are safe.** The friends never lead away or hunt:

- an animal with a name (from a name tag), a tamed or owned animal, one on a lead, one riding or being ridden, or one wearing a saddle or armour;
- an animal within 4 blocks of anything you built (fences, gates, walls and every other crafted block), or inside your own fences or walls, also on a hillside or in a long field (fences, walls and gates up to 48 blocks away count);
- a young animal, or one of the last two of its kind within 24 blocks;
- an animal in the camp (those belong in the pen) or in the pen itself;
- anything near where a friend died lately.

They only hunt by day: never at dusk or at night. Animals of yours with a name, a lead, a saddle or an owner are never butchered either, even in the friends' pen. To keep an animal of yours safe anywhere, give it a name or keep it fenced in.

**The gate.** Friends open and shut the pen gate themselves. They never shut it while you are in or right next to the pen, or while anyone stands in the gateway, so while you are there they do not start pen jobs that open it. The camp's own pets (3.0, section 28) do not count: a pet idling in the gateway never holds the gate open, and it is never herded to the back with the animals. A friend who ends up inside the pen with nothing to do there walks out and shuts the gate behind them. Eggs laid in the pen and the ground inside it are left alone: nobody tries to tidy up behind the fence or plant flowers there.

## 6b. Nights: bedtime, the watch and standing together

Nights are when Hardcore friends die, so the camp keeps a night routine.

- **Bedtime.** At nightfall friends put their work down (it waits for the morning) and go to bed: in their own bed once they have a house in the village (section 24), otherwise inside the cabin once it is built, otherwise in a ring around the camp centre. Only the jobs that belong to the night go on: a hungry friend has supper first, a chilly one warms up by the campfire first, anyone still out comes home, Terra lights a dark camp before she turns in, and a badly hurt friend with no food is still fed. Pastimes and chats wait for the morning too. Once asleep, nothing but trouble gets them up: they sleep until dawn unless they are hurt, a monster that could get at them comes within 8 blocks (one right beside them in plain sight, or one with a way in: monsters do not open doors, so one outside a shut door does not count), a friend within 16 blocks is trading blows (or has a hostile within 4 blocks), the watch raises the alarm about a monster near them, or their own watch begins.
- **The watch rota.** Someone always stays up. The night has two watches: the **first** from dusk to midnight, the **second** from midnight to dawn. Aegis keeps the first watch whenever he is alive and working at the camp. The second goes to a healthy friend with something to fight with: the best armed (any sword before any axe before any other tool), then the healthiest, but never whoever kept a watch the night before if anyone else can, so the duty rotates and nobody loses sleep every night. From 3.0, a guard who stood the first half of the night (section 27) is never picked for the second watch. If Aegis has died, or is away following you, someone else keeps the first watch as well. A watcher who dies, is sent to follow you or stay, gets too weak, runs out of energy, or is more than 24 blocks beyond the camp's edge for over 30 seconds is relieved by the next friend in line, and friends at the camp are chosen before friends away from it. The second watcher sleeps until midnight and is then woken for their watch; whoever kept a watch naps sooner the next day. A friend alone at camp (other than Aegis) has nobody to watch over and simply sleeps.
- **Keeping watch.** The watcher stands by the campfire (or the camp centre), stepping round it now and then and looking out over the whole camp (in 3.0, as far as the village has grown it, up to 96 blocks round them), and goes for any hostile that comes into the camp. Aegis keeps his watch walking his posts round the camp, or from the watchtower once it stands; on his watch he only stays beside players inside the camp or within 8 blocks of its edge.
- **The alarm.** When the watcher spots a hostile inside the camp (one they can see, or one out of sight on the camp's own ground with a way to it, not one in a cave beneath), they raise the alarm ("Zombie in the camp! Everyone up!"). Every grown-up asleep in the camp within 40 blocks of it wakes, and every armed friend in the camp (holding a sword or an axe) within those 40 blocks goes to fight it. Friends asleep further off (at the far end of a big village) and children sleep on, unless the monster could get at them. When it is dealt with they go back to bed. Each hostile raises the alarm once a night.
- **Standing together.** When a hostile goes for a friend or for you inside the camp, armed friends within 16 blocks join in, and any friend holding a tool joins in within 8. Inside the camp they do not need to see it first: they find their way round walls to it, but only when a whole path leads there. Outside the camp, friends join fights they can see within 8 blocks. A friend who goes 10 seconds without landing a blow, with no way through to the hostile, gives it up for 30 seconds; a second time, until dawn. Friends with a bow shoot archers and anything they cannot reach (section 15).
- **Something to fight with.** Every friend arrives with a tool they can fight back with. Sage, whose planning needs no tool, brings a wooden sword: she keeps it, and fetches or makes another when it breaks.

`/friends list` shows who is "keeping watch". A thunderstorm by day darkens the sky, so friends out in the wilds come home, but in camp they carry on working (only a tired friend naps) and no watch is kept: the watches follow the clock.

## 7. Controls

| Action | How |
|---|---|
| See a friend's status and mood | Right-click them with an empty hand |
| Open their backpack | Sneak + right-click with an empty hand |
| Give an item | Right-click them while holding it (it goes into their backpack) |
| Feed or heal a friend | Right-click with food while they are hurt or hungry; they eat it at once |
| Hurt a friend on purpose | Only while sneaking. Ordinary swings and arrows pass harmlessly |
| Trade at a shop (3.0) | Right-click a shopkeeper standing at their counter by day; sneak + right-click for their backpack instead (section 25) |
| Get a copy of a map (3.0) | Right-click Scout while holding an empty map (section 28) |

Children (3.0, section 23) take food from you, but nothing else.

## 8. Commands (no cheats needed)

| Command | Purpose |
|---|---|
| `/friends` or `/friends help` | Overview |
| `/friends recruit <name>` | Recruit a friend (2 common food) |
| `/friends list` | Everyone's status, health, mood and current job |
| `/friends needs [name\|all]` | Each friend's five needs as bars with numbers, their mood, and what they are doing about their lowest need |
| `/friends follow <name\|all>` | Come with me; they help fight and carry loot |
| `/friends stay <name\|all>` | Hold this spot |
| `/friends work <name\|all>` | Back to their own routines (default) |
| `/friends where <name>` | Location and what they are doing |
| `/friends backpack <name>` | Open (when close) or list their backpack |
| `/friends camp` / `/friends camp set` | Camp status and next steps / set the camp here |
| `/friends chest` | Link the chest or barrel you are looking at |
| `/friends unity` | Unity bond level and active bonuses |
| `/friends plan` | What the camp is short of and what everyone is doing |
| `/friends advice` | Sage's advice for you right now |
| `/friends log` | The last block changes friends made |
| `/friends dismiss <name>` | They leave and drop their backpack (costs Unity) |
| `/friends chatter <quiet\|normal\|chatty>` | How talkative they are |
| `/friends gear` | What each friend nearby wears and carries (section 15) |
| `/friends skills [name]` / `/friends trips` | Levels in each kind of work / who is away and the places found (section 16) |
| `/friends newcomers` | Newcomers on the team, and strangers near you and what they would like (section 17) |
| `/friends goals` | Sage's plan, the step in hand and what it needs (section 18) |
| `/friends party [list]` / `party add\|remove <name>` / `party go` / `party home` | Your expedition party (section 19) |
| `/friends trusted` / `trust <player>` / `untrust <player>` / `owner <player>` | Who may give orders (section 20) |
| `/friends bond [name]` | Bonds between friends and players (section 20) |
| `/friends jobs` / `/friends deliver [number]` | What the camp needs / bring it to the chest (section 20) |
| `/friends note <text>` / `/friends notes` | Leave a note for the camp / see the notes (section 20) |
| `/friends mailbox [remove]` / `/friends send <item> <count>` | Your mailbox for deliveries / ask for something (section 20) |

New in 3.0 (all work without cheats, and all but `/friends map` only show things):

| Command | Purpose |
|---|---|
| `/friends builds` / `builds <kind>` / `builds <plan id>` | The building library's plans by kind / one kind (`house`, `shop:bakery`...) / one plan (`house/oak_cottage`) with its size, styles, beds and cost (section 21) |
| `/friends builds check` | Plan files that were skipped or have warnings, and why (section 21) |
| `/friends senses [name]` | What each friend senses: underground or not, light, water, current, lava, drops, monsters heard, danger, whether they are stuck or getting out, and whether they are running (section 22) |
| `/friends family [name]` | Every family by family name / one person's partner, parents, children and who they live with (section 23) |
| `/friends couples` | Couples going out, engaged or married, the wedding day, and a baby on the way or what is still missing for one (section 23) |
| `/friends relationships [name]` | The closest friendships in the camp / one person's partner and closest friends (section 23) |
| `/friends village` / `village plots` | The village: people, babies on the way, homes, buildings, streets, plots and what the next stage still needs / every plot (section 24) |
| `/friends home [name]` | Where everyone lives, household by household / one person's house, street, bed and household (section 24) |
| `/friends trades` | Who holds which trade, where, and what they are doing; workplaces still waiting (section 25) |
| `/friends shops` | Each shop, its keeper, where it is, whether it is open, and its trades so far (section 25) |
| `/friends calendar` | Today's date, what is on today, and the next ten days (section 26) |
| `/friends chronicle [page]` | The latest page of the Village Chronicle and where the book is / another page, 1 being the oldest (section 26) |
| `/friends defence` | The alarm, the bells, the guard posts and who stands each tonight, fires to put out, and the tallies so far (section 27) |
| `/friends pets` / `/friends maps` | Every pet, whose it is and what it is doing / every map and where it is (section 28) |
| `/friends map [number]` | Ask Scout for a copy of the best map for where you stand / of one map; she makes it from the camp's own paper and brings it to you. By day, for the owner and trusted players (section 28) |

None of these commands give free items, teleport you, or change time, weather, game mode or difficulty.

## 9. Unity bond

The bond grows when you spend time near your friends, when they deliver resources and share with each other, chat with each other (1 point a chat, at most 30 a day), defend one another, and finish camp buildings, and while the whole team is in high spirits (1 point an in-game hour, at most 12 a day). Losing a friend costs 80 points. A low mood costs nothing.

New in 3.0, Unity also grows from:

- couples getting together (+5, at most 20 a day), weddings (+30), births (+10, at most 30 a day) and children
  growing up (+15) (section 23);
- finished village buildings: houses (+10, at most 40 a day), civic buildings, farms, shops and workplaces (+15) and
  the town hall (+30) (section 24);
- trading at a shop (+1, at most 10 a day, section 25);
- feasts (+10), birthdays (+2, at most 6 a day) and funerals (+5, at most 10 a day) (section 26);
- guards standing the night, raids beaten off and fires put out (section 27);
- new pets (+3, at most 6 a day, section 28).

| Level | Points | Bonus |
|---|---|---|
| Strangers | 0 | none |
| Acquaintances | 100 | backpacks hold 18 stacks; friends share food with hungry players |
| Companions | 250 | **work rhythm**: +15% work speed beside another friend; **careful hands**: 20% less tool wear |
| Close Friends | 500 | backpacks hold 27 stacks; faster healing at camp (not while starving); Scout's warnings make threats glow |
| Family | 800 | **rally**: below 3 hearts with 2+ friends nearby, you get 5 s of Regeneration I and they target your attacker (10-minute cooldown) |

None of these bonuses can stop you dying.

## 10. The camp grows into a settlement

**Campsite → Camp → Hamlet → Village → Settlement → Town → City.** Each stage needs that stage's buildings finished. The later stages also need Unity: 100 for Hamlet, 250 for Village, 500 for Settlement, 650 for Town and 800 for City. Use `/friends camp` to see what is built and what Oak is still waiting for. Everything is built from what is in the supply chest, so keep it stocked or let Rowan and Flint fill it. Cabin windows get glass panes when the camp has glass (6 glass make 16 panes); until then they are left open, and the repair job fills them in later (section 21). Only the old 2.x box cabin (with `fancyCampBuildings` off, or one started before 3.0) closes them with planks instead. If the supply chest is broken, friends forget it: Oak builds a new one, or you can link another with `/friends chest`. If a friend is not on your team, the others stand in and build their improvements too, just more slowly. At the Hamlet stage Terra also builds the animal pen (section 6); it is optional and never holds the camp back.

**New in 3.0.** The cabin, storehouse and watchtower are proper buildings now (section 21). When the camp becomes a
Village, the friends lay out a town plan and start building houses, streets and the village's own buildings
(section 24). After the Settlement come two new stages, the village's own:

| Stage | Unity | People | Houses standing | Village buildings standing |
|---|---|---|---|---|
| **Town** | 650 | 14 | 5 | the town hall, the well, and one of the tavern, market, school or chapel |
| **City** | 800 | 22 | 9 | the town hall, the well, the tavern, the market, and two of the school, chapel, watchtower or town gate |

People means everyone on the team: the named friends, newcomers, children and people grown up in the camp. The
Settlement's own buildings must be finished too. `/friends village` shows what the next stage still needs. With
`villageHomes` off the camp stays a Settlement.

## 11. Hardcore rules

- Your own Hardcore death is unchanged: one life, then spectator. There are no extra lives.
- Friends need food. A starving friend loses health down to one heart and cannot heal. Starving never kills them on its own, but it leaves them one hit from death.
- Friends are mortal. They retreat when badly hurt, eat from their backpacks, and run from creepers. Healthy friends holding a tool stand together and fight off zombies and other close attackers, so nobody is picked off alone; in the camp armed friends come from further away, and at night the watch raises the alarm (section 6). Friends with a bow shoot skeletons and other archers; everyone else gets out of their line of fire, and a badly hurt friend stays well out of bow range until they recover. In a fight a badly hurt friend eats a golden apple or drinks a healing potion if they carry one. Out of combat (no damage for 10 s, no target, not burning) they recover 1 health every 4 s. If they die, they drop a **backpack item** holding everything they carried and wore. It never despawns, and its coordinates go to chat. Use it (right-click) to unpack.
- A fallen friend's name returns as a newcomer after 3 in-game days. Set `deadFriendsReturnAfterDays` to `-1` in `config/hardcorefriends.json` for permanent loss.
- Zombies, skeletons, spiders, illagers and witches hunt friends just as they hunt villagers.
- Newcomers (section 17) are mortal too: a newcomer who dies is gone for good.

## 12. How friends treat your world

- **Building and landscaping** happen only inside the camp radius: 24 blocks, growing to 40 (and in 3.0, once the
  village's streets and plots spread, up to `villageRadius`, 64 blocks).
- **Tree felling, quarrying and the staircase mine** happen only in a ring up to 48 blocks beyond the camp. Flint also mines ores that are already exposed, inside the camp or in that ring.
- Friends never break chests, furnaces, signs, beds or any other block entity, or anything crafted-looking: planks, doors, glass, torches, slabs, stairs, fences and so on. They also stay clear of blocks next to such things.
- Trees are felled only if they grew naturally (natural leaves), never log walls or trees inside camp. Quarry pits are 5×5 and at most 2 blocks deep. Mines stay inside one 24×24 area.
- **Animals:** friends only lead away or hunt wild animals, never yours (named, tamed, on a lead, saddled, near anything you built or inside your fences), never butcher yours in their pen, and only hunt by day in the gathering ring outside the camp (section 6). Like villagers, friends open and shut wooden doors they walk through, and when the alarm goes they shut their own doors behind them (section 27). The only gate they open is their own pen's, and from 3.0 they also fill and empty their own composter and take honey from their own beehives (section 25).
- **New in 2.0**, each with its own strict rules (details in `docs/v2/`): Terra levels building sites inside the camp (natural ground only, never within 3 blocks of your builds, at most 3 blocks up or down); friends caught out at night build a small shelter or pillar up out of reach and take it all back afterwards (never within 6 blocks of your builds); Flint's deep mine stays in its own box and seals caves only inside it; obsidian is only made from natural lava (never lava open to the sky at y = 40 or above); on expeditions Scout builds a small marker over the stronghold and friends pillar up to the End crystals. Survivor camps (section 17) are new world generation in newly explored chunks only.
- **New in 3.0**, each with its own strict rules (details in `docs/v3/`):
  - Village plots go only on firm natural ground: never on water, a field, your floors or under trees, and never
    within 2 blocks of anything you built (3 where the ground is levelled). Streets are laid only on grass, dirt and
    the friends' own path blocks, never within 2 blocks of anything you built. Friends only live in houses they built
    on their own plots (section 24).
  - Builders put up temporary dirt or cobblestone pillars (at most 6 high) to reach roofs, never by your builds, and
    dig them out again (section 21).
  - The forager digs single blocks of sand and clay by day in the gathering ring, leaving one-block dips, never next
    to water or near your builds; the farmer shears wild sheep, never yours (section 21).
  - A friend shut in a hole away from camp may put a block underfoot or dig a staircase out through natural ground,
    never near anything you or the friends built and never on the camp's own ground: its heart and the sites of the
    village's buildings (section 22).
  - Graves go at a small cemetery on level natural ground away from the streets and your builds (section 26).
  - Fire near the houses and the camp is put out by hand, in your builds too, but never a fireplace on netherrack
    (section 27).
  - Pets are only ever stray cats and wild wolves, never an animal of yours (section 28). Map frames only go on walls
    the friends built (section 28).
- Every change is logged (`/friends log`). Turn editing off entirely with `allowWorldEditing: false`, or individually with `allowTreeFelling`, `allowQuarrying` and `allowMining`.

## 13. Honest limits

- **3.0 has not been played yet.** Everything new in 3.0 compiles and was reviewed by reading it through (the building
  plans were also checked by script on level ground), but none of it has been run in game. The numbers in it (how fast
  friendships grow, how often babies come, prices, alarm sizes, timings) are first guesses. Expect rough edges, and
  test in a copy of your world first. Sections 21 to 28 each end with a short list of limits; the full lists are in
  `docs/v3/`.
- **2.0 has not been played yet.** Everything new in this version compiles and was reviewed by reading it against the game's own code, but none of it has been tried in game. Expect rough edges, and test in a copy of your world first. Each part's own honest limits are in `docs/v2/`.
- **Nights are the dangerous time.** The night watch, the alarm and standing together are new in 2.0 and untested in game. In 1.0's two-day test run, before the watch existed, 2 of 9 friends died to zombies on the first night and none after. Stay near camp after dark, light it well, and help in fights.
- The camp keeps running while you (the owner or a trusted player) are online anywhere, but not while nobody is online. Monsters only spawn near players, so an empty camp gets no new ones, but those already there keep fighting.
- Every friend can do every job, but a stand-in only works on someone else's speciality in their spare time, and more slowly. A camp missing several specialists grows more slowly.
- Needs are five simple numbers, not a full life simulation. Friends meet them with a fixed set of jobs: eating, sleeping, chatting, a pastime, warming up and, when too weak to work, resting. From 3.0 a friend with a house sleeps in their own bed (section 24); without one, they sleep where they lie down (in the cabin or around the camp centre).
- Friends see to their needs only while working on their own. A friend following you or holding position does not eat, sleep or rest; they eat from their backpack only when hurt or very hungry. Hand them food, or send them back to work now and then.
- Chats between friends are an exchange of pre-written lines, not a real conversation.
- The night watch looks out over the camp from the campfire: it notices a hostile it can see, or one out of sight on the camp's own ground with a way to it, not one in a cave beneath the camp. In a grown village the watcher sees the whole village but cannot see round houses: a monster out of sight there is noticed only when it is close to them or going for someone. The alarm wakes grown-ups inside the camp within 40 blocks of the monster, not those further off or out in the gathering ring.
- They build from fixed plans (in 3.0, the building library's 54, plus any you add with a data pack), adapted to the wood the camp has. They do not invent new buildings, and they choose what to build and where themselves: there is no ordering a building.
- Since 3.0 the friends find their way with paths of their own and get themselves out of caves, holes and water (section 22), but that is untested in game, and a friend can still get stuck: stuck jobs time out and are retried later, and as a last resort a friend stuck for long is brought home (`rescueStuckFriends`).
- Mining is one staircase mine plus exposed ores near camp, and (for Sage's plan) one deep branch mine at a time. Only trees they can fully reach (about 6 blocks tall) are felled.
- The farm plot needs a fairly level spot inside camp: a level hole for the water with about 20 level grass or dirt blocks around it. Bumps are fine, but on a camp that is all slopes or rock Fern says she needs "a flat patch of grass", and the camp cannot grow past stage 2 until she gets one. Help her by levelling a patch, or simply pour water on level ground in camp: she farms around any water inside the camp.
- When the next camp step is waiting on something nobody can fetch (for example nether materials for Spark's lamp posts), friends with nothing useful left to do take a break near camp.
- Contraptions are a fixed set of vanilla redstone builds.
- Livestock is cows, pigs, sheep and chickens in one pen; rabbits are only hunted. Friends do not milk cows or collect eggs. (From 3.0 they do shear sheep: wild ones for the builders' wool, and the pen's own once there is a shepherd.) An animal following its food can wander off before it reaches the pen; it is fetched again later. An unnamed animal of yours standing loose in the open, with nothing you built nearby, looks wild to the friends: name it or fence it in.
- Dialogue is pre-written and chosen by situation and personality. It is not free conversation.
- Purely natural-looking player builds (for example a hut made only of dirt or stone) cannot be told apart from terrain if they sit in the gathering ring outside your camp. Keep such builds inside the camp radius, or add any crafted block nearby.

## 14. Config (`config/hardcorefriends.json`)

`chatter`, `campRadius` (24), `maxCampRadius` (40), `resourceRadius` (48), `allowWorldEditing`, `allowTreeFelling`, `allowQuarrying`, `allowMining`, `deadFriendsReturnAfterDays` (3, or −1 for permanent), `followTeleportDistance` (48, 0 = off), `monstersTargetCompanions` (true).

New in 2.0:

| Setting | Default | What it does |
|---|---|---|
| `keepCampLoaded` | `true` | The camp keeps running while the owner or a trusted player is online anywhere |
| `maxRoamingFriends` | `3` | How many friends away on trips keep the land around them running at once |
| `allowTrips` | `true` | Scout's far trips, trading trips, the stronghold search |
| `allowTerraforming` | `true` | Levelling uneven ground for buildings |
| `maxGradeDepth` | `3` | The most a site is dug down or built up, in blocks |
| `allowSettlers` | `true` | Strangers appear in villages, survivor camps and as travellers |
| `villageSettlerChance` | `0.75` | Chance a village has newcomers, decided once per village |
| `maxSettlers` / `maxSettlersPerPlayer` | `12` / `6` | Newcomers on the team at once / per recruiting player |
| `wanderingVisitors` | `true` | Travellers visit the camp every few days |
| `friendsUseBows` | `true` | Friends shoot, fetch and make bows and arrows |
| `progressionGoals` | `true` | Sage's plan to beat the game |
| `friendsFollowThroughPortals` | `true` | Following friends come through portals with you |
| `requireTrust` | `true` | Only the owner and trusted players give orders, open backpacks, move the camp |
| `maxFollowersPerPlayer` | `4` | Most friends following one player (never limits a player who plays alone) |
| `maxDeliveryDistance` | `400` | How far a mailbox may be (0 = no deliveries, 600 at most) |
| `siegeNights` | `false` | Opt-in waves of monsters every few nights once the camp is a Village |

New in 3.0 (numbers outside the range shown are brought back into it when the game loads the file):

| Setting | Default | What it does |
|---|---|---|
| **Better builds** (section 21) | | |
| `fancyCampBuildings` | `true` | New camp buildings use the 3.0 plans (the cottage, the timber-framed store, the roofed watchtower); `false` keeps the 2.x boxes. A building already started keeps its plan either way |
| `allowScaffolding` | `true` | Builders put up temporary pillars to reach high walls and roofs, and take them down again. Off: high parts are skipped |
| `maxScaffoldHeight` | `6` | The tallest pillar, in blocks (2 to 6) |
| `friendsShearSheep` | `true` | Friends shear wild sheep for the wool their builds need |
| **Finding the way** (section 22) | | |
| `rescueStuckFriends` | `true` | A friend stuck or lost for about two minutes (sooner when trapped underground hurt or starving, or about to drown) is brought home, or back to you. Turn it off for pure Hardcore |
| **Living together** (section 23) | | |
| `romance` | `true` | Friends may fall for each other, go on dates, get engaged and marry |
| `children` | `true` | Married couples may have children |
| `childhoodDays` | `10` | In-game days a child takes to grow up (1 to 100) |
| `maxPopulation` | `30` | Most people on the team at once, children and babies on the way included; also the village's population cap (0 to 200) |
| `daysBetweenChildren` | `5` | Days a couple waits after one baby before another (1 to 60) |
| `relationshipSpeed` | `1.0` | How fast friendships and romances grow: 2 is twice as fast, 0.5 half as fast (0.25 to 4) |
| **The village** (section 24) | | |
| `villageHomes` | `true` | Lay out the town plan at the Village stage and build homes; `false` keeps the camp as it was (no new plots, no Town or City). Houses already built are still lived in |
| `villageRadius` | `64` | How far from the camp centre the streets and plots may spread, in blocks (from `campRadius` to 96); the camp grows with them, and with `keepCampLoaded` so does the area kept running while you are online (about 15×15 chunks at 64, 19×19 at 96, against 13×13 before the village) |
| `villageBuildsAtOnce` | `3` | Most village buildings under way at once, besides two pieces of decoration (1 to 8) |
| **Trades and shops** (section 25) | | |
| `villageTrades` | `true` | Grown-ups take up trades. Off: nobody works a trade (trades already given are kept for when it is on again) |
| `playerShops` | `true` | Shopkeepers trade with players. Off: shops make and stock things but do not trade |
| `friendsWithoutTrade` | `3` | Grown-ups always left without a trade, so the camp's own work never runs short of hands (0 to 20) |
| `requestWorkplaces` | `true` | The village is asked for shops and workplaces as it grows |
| **Village life** (section 26) | | |
| `villageLife` | `true` | Feasts, market days, music and birthdays. Off: none of these (the calendar, funerals and the Chronicle still run) |
| `graves` | `true` | A grave at the cemetery for each friend lost. Off: no graves (funerals are held at the square instead) |
| `chronicleBook` | `true` | The Chronicle is also written out as a real book. Off: it is kept for `/friends chronicle` only |
| `daysPerSeason` | `10` | Days in each of the four seasons (3 to 30). Changing it moves the feasts and birthdays in an existing world |
| **Defending the village** (section 27) | | |
| `villageDefence` | `true` | The alarm bell, taking cover, fighters at their posts, guards and raids. `false` leaves only the night watch |
| `guardsPerShift` | `2` | Guards each half of the night once the village has a watchtower, gate or walls (0 to 6; 0 means no guards) |
| `alarmHordeSize` | `3` | How many hostiles seen in the village at once at night ring the bell (1 to 20) |
| `fireWatch` | `true` | Friends put out fires near the houses and the camp |
| **Pets and maps** (section 28) | | |
| `pets` | `true` | Friends and children adopt cats and dogs. Off: nobody adopts or feeds a pet (pets already kept stay) |
| `maxPets` | `8` | Most pets the camp keeps at once (0 to 50); each person has at most one |
| `scoutMaps` | `true` | Scout makes, draws and hangs maps. Off: maps already made stay where they are |

Two older settings matter more in 3.0: `allowQuarrying` also covers digging sand and clay, and `allowWorldEditing`
must be on for a friend to dig or place blocks to get out of a hole.

## 15. Gear and fighting (new in 2.0)

Full details: `docs/v2/combat.md`.

- **Everyone gears up.** Every friend wears the best armour they can get, from their backpack or the supply chest,
  carries a shield, and takes a better sword or axe when the chest has one. Aegis chooses first, then whoever fights at
  night, then everyone else. Bow users top up their arrows. Each friend keeps a golden apple or a healing potion for
  emergencies (never an enchanted golden apple from the chest).
- **The smith.** When the chest has spare materials, a friend crafts gear at the camp's crafting table: shields for
  everyone first, then swords, armour for the fighters, bows, arrows, then armour for everyone. Only spare materials
  are used: 7 diamonds, 5 iron and 16 cobblestone always stay, nothing a building is short of, and nothing Sage's plan
  is collecting.
- **Bows.** Anyone with a bow and arrows shoots archers, witches, blazes, phantoms, creepers (from 7 blocks or more)
  and anything they cannot reach. Scout and Sage prefer the bow; Aegis prefers his blade. They never shoot while you,
  a friend, a villager or any animal is in or near the line of fire, and friends' arrows and blows can never hurt
  players, friends, villagers, golems or anybody's animals.
- **Kill credit.** A mob a friend kills counts as killed by you (their leader, or the nearest player within 32
  blocks), so blaze rods and experience still drop, as with a tamed wolf's kills.
- **Shields** go up against archers drawing on them and swelling creepers. **Emergency healing:** below 40% health in a
  fight, a friend drinks a healing potion or eats a golden apple; a burning friend drinks fire resistance.
- **Teamwork.** Friends focus on a hostile a teammate is already fighting, and the healthiest armed friend takes over
  from one falling back hurt.
- **Tip:** the supply chest is shared. Armour, bows, golden apples and potions you leave in it will be used. Keep your
  own in another chest.

## 16. Independence: living on, trips, room to build, staying alive, getting better (new in 2.0)

Full details: `docs/v2/survival.md`.

- **The camp lives on.** While the owner or a trusted player is online anywhere, the camp and its gathering ring keep
  running: friends work, eat and sleep, crops grow. A friend on a trip keeps the land around them running too (at most
  3 at once). When nobody is online, nothing runs.
- **Scout's far trips.** Once a day, when the camp is safe and fed, Scout explores up to 300 blocks out and reports
  villages, survivor camps, ruined portals (obsidian), pillager outposts (she turns back), temples and new biomes, to
  everyone on the server. `/friends trips` lists the places found.
- **Trading trips.** When a village is known within 300 blocks, a friend (Sage and Rowan keenest) takes the camp's
  surplus there, trades with the villagers' real offers (never stealing, never hurting anyone), buys what the camp
  needs, and brings it home. They take up to 12 emeralds from the supply chest each trip.
- **Trips are careful.** Only by day, healthy, fed and carrying food, with daylight to get back; they turn back when
  hurt, hungry, tired, in a storm or late, and keep away from places where a friend died.
- **Room to build.** No flat spot? Trees on the site are felled first (as before). If even that fails, Terra levels
  uneven natural ground (at most 3 blocks up or down, never near your builds). If nothing fits, the camp grows a
  little and the friends look further out.
- **Caught out at night.** A friend far from camp after dark digs into a hillside, digs down or builds a small pillbox
  around themselves, sleeps, and takes it all back at dawn. A badly hurt friend cornered by zombies pillars up out of
  reach. A falling friend with a water bucket breaks their fall.
- **Getting more skilled.** Each friend has a level from 0 to 10 in every kind of work and in fighting. Levels make
  them up to 20% faster at that work, and up to +2 attack and +2 hearts in a fight. `/friends skills [name]`.

## 17. Newcomers: people to meet and recruit (new in 2.0)

Full details: `docs/v2/settler.md`.

Besides the nine friends, the world now has other people in it, each with their own name, look (a skin from the
mod's medieval village set, chosen to suit their trade or land and to differ from everyone else's where it can; see
"Skins" in `docs/v3/people.md`) and trade:

- **In villages:** the first time you walk into a village, there is a good chance one or two live there.
- **At survivor camps:** a new kind of place in the world, a small camp of tents round a campfire with a chest of
  supplies and one to three strangers, in temperate biomes. Only newly explored land gets them.
- **Travellers:** once your camp is a Camp, someone may walk up every few days and stay a day.

**To recruit one:** right-click them. They tell you who they are and ask for something small for their trade (a
farmer: 4 bread and a hoe; a miner: a stone pickaxe and 8 torches...). Bring it in your inventory and right-click
again: they take exactly what they asked for (never anything enchanted or renamed) and join. A newcomer works like the
named friend who shares their trade, under their own name: give them orders by name (`/friends follow mabel`), feed
them, open their backpack. Up to 12 newcomers on the team, 6 per player. `/friends newcomers` lists them and the
strangers near you. A newcomer who dies is gone for good.

## 18. Sage's plan: beating the game step by step (new in 2.0)

Full details: `docs/v2/progress.md`.

The camp works towards beating the game one step at a time. `/friends goals` shows the plan and what the step in hand
needs. Each step reached is announced and adds 40 Unity.

1. **Settled** (the camp is a Village) → 2. **Iron age** (iron pickaxes, an iron sword, 10 iron in stock) →
3. **Diamonds** (a diamond pickaxe) → 4. **Enchanting** (an enchanting table with 15 bookshelves) →
5. **Nether ready** (10 obsidian and a flint and steel) → 6. **Blaze rods** (7) → 7. **Ender pearls** (12) →
8. **Eyes of ender** (12) → 9. **Stronghold found** → 10. **End portal open** → 11. **Dragon defeated**.

What the friends do for it on their own:

- **Deep mining.** With an iron pickaxe, Flint digs a branch mine down at diamond level. He never digs straight down,
  never opens a block touching water or lava, walls off caves with cobblestone, lights it with torches, never digs out
  the floor of his way back up, and heads home when hurt, hungry or late.
- **Obsidian.** With the diamond pickaxe and a water bucket, the miner pours water on natural lava (cave lava, or lava
  below y = 40) and mines the obsidian. Lava open to the sky at y = 40 or above is never touched: it might be yours.
- **Books and the library.** Fern grows sugar cane, Sage makes paper and books (books need leather: help with it), and
  Oak builds a library with an enchanting table and 15 bookshelves.
- **Enchanting.** The camp shares one experience pool (from ores, smelting and defeated mobs). At level 30 Sage enchants
  the best gear in the chest with 3 lapis, using the game's own enchanting. Named gear is never touched.
- **The anvil and brewing.** Oak builds an anvil and mends worn iron and diamond gear; once blaze rods come home, a
  brewing stand, where Spark brews fire resistance, healing, regeneration and strength.

What you help with: leather (books need 46), a first sugar cane, and everything from the Nether and the End (blaze
rods, ender pearls, nether wart, soul sand): that is what expeditions are for.

## 19. Expeditions you lead: the Nether, the stronghold and the End (new in 2.0)

Full details: `docs/v2/expedition.md`.

The friends prepare everything, but the dangerous trips are **expeditions you lead**: in Hardcore, a dragon fought
without you would only make graves.

- **Your party.** `/friends party add <name>`, then `/friends party go`: your party follows you (packing food, fire
  resistance and what the plan's step needs at the chest first). `/friends party home` sends them back to work.
- **Through portals.** Friends following you within 32 blocks come through portals with you, and come home with you.
  One left behind walks to the portal and follows. A friend whose leader has gone waits by their portal for 3 minutes,
  then goes home through it. Friends never wander through a portal on their own.
- **The camp portal.** Once the plan is Nether ready, Oak builds a portal at camp and lights it.
- **In the Nether** they barter gold with calm piglins, shoot blazes (the rods drop thanks to kill credit), pick up
  useful drops and gather nether wart and soul sand. Calm piglins are left alone.
- **The stronghold.** Scout throws real eyes of ender, walks 200 blocks across and throws again, works out where the
  lines cross, walks there (sheltering at night), marks the spot with a small cobblestone pillar and a torch, and
  tells everyone the coordinates. She never digs down.
- **The End portal.** With you in the portal room, friends carrying eyes set them in the empty frames and open it.
- **The dragon.** Archers shoot the end crystals from spots with a clear shot (never while anyone is within 12 blocks of
  one), a friend pillars up to break open caged crystals, archers shoot the dragon in flight, and fighters strike it
  when it perches. They step out of its breath and keep away from the island's edge. When it dies: a big
  celebration, 100 Unity, and the plan's last step.

## 20. Several players (new in 2.0)

Full details: `docs/v2/town.md`. On a world you play alone, none of this gets in your way.

- **Owner and trust.** The first player to change anything owns the camp. While `requireTrust` is on, only the owner and
  players they trust (`/friends trust <player>`) may give orders, recruit, open backpacks, hand over anything but
  food, put a friend on a lead, move the camp or ask for deliveries (and, from 3.0, a copy of a map with
  `/friends map`), or hurt a friend at all. Anyone may look and anyone may feed a friend. The single-player host and
  operators are always allowed, and someone playing alone is never limited or refused.
- **Bonds.** Each friend has a bond with each player (-100 to 100) that grows with food and gifts, help in fights,
  deliveries and time together, and falls if you hit or dismiss them. Close friends defend you first and feed you
  first; a friend who distrusts you will not follow you. `/friends bond`.
- **The job board.** `/friends jobs` lists what the camp needs, including what Sage's plan wants. Stand near the chest
  and `/friends deliver` (or `/friends deliver <number>` for food and the plan's items): only from your main inventory,
  never your hotbar, tools, armour, buckets, golden food or anything enchanted or renamed.
- **When a player dies.** In Hardcore the friends mourn them and the camp remembers them (`/friends camp`). By day, if
  it is safe (lit, no monsters, not after lava, falls, drowning or explosions), friends gather the dropped items into a
  bag and put it in the supply chest.
- **Notes.** `/friends note <text>` (spectators too): a friend reads it out at camp. `/friends notes`.
- **Mailboxes.** `/friends mailbox` while looking at your own chest. A friend brings you a share of the camp's plenty
  every few days, and what you ask for with `/friends send <item> <count>`. They only ever put things into your mailbox.
- **Siege nights** (off by default, `siegeNights`): every five to eight nights once the camp is a Village, a wave of
  monsters gathers at the camp's edge at midnight, with a warning at dusk. Survive it together for 30 Unity.

## 21. Better builds: proper buildings, plans and scaffolding (new in 3.0)

Full details: `docs/v3/architecture.md`.

- **The camp's own buildings, rebuilt.** The cabin (Hamlet) is now a cottage on a cobblestone plinth, with a log frame,
  plank walls, a stair roof with eaves, five windows, porch lanterns and two barrels; the second cabin (Settlement) is
  the same cottage. Friends still sleep inside the first cabin (nobody sleeps in the second), and Spark's automatic
  door still works. The storehouse (Village) is
  timber-framed with two chests and six barrels. The watchtower (Village) has a stepped stone base, arrow slits, a
  ladder up the middle and a roofed lookout, where Aegis still keeps watch.
- **The building library.** The village (section 24) and the trades (section 25) build from 54 plans: 19 houses in nine
  styles, 7 shops, 8 workplaces, 10 civic buildings (town hall, wells, school, tavern, market, chapel, wall, gate,
  watchtower), 3 farms and 7 pieces of street decoration. Each is picked to suit the land and the wood in the chest:
  spruce cabins in the taiga, sandstone houses in the desert, oak cottages on the plains.
- **Nothing rare holds a building up.** Windows stay open until the camp can make glass, a lantern is built as a torch
  while the camp has no iron (and stays one), and carpets, flowers and the like are added later by the repair job.
  Only the smithy, the smith's shop and the mason's yard wait for iron, for their work blocks.
- **Building properly.** Foundations first, then the walls and floors layer by layer, the roof last, then doors,
  windows, furniture and lights. Big buildings go up in batches of 24 blocks, saved block by block.
- **Scaffolding.** A wall or roof out of reach is built from a temporary pillar of dirt or cobblestone, at most 6
  blocks high, which the builder digs out again afterwards. A friend called away comes straight down first, and a
  pillar left behind is taken down later.
- **Making materials.** The builder fires glass, stone, smooth stone, bricks and smooth sandstone in the friends'
  furnace. What is already in the furnace counts, and a furnace already at work is only topped up with eight or more,
  so the builder is not sent to it for one block at a time; a trip to the furnace waits until the run of building in
  hand is over. The forager digs sand and clay by day in the gathering ring (single blocks, leaving shallow dips anyone
  can step out of). The farmer shears wild sheep for wool. Builders tell the camp what a whole building needs, so the
  gatherers fetch it ahead of time.
- **Shortages.** When a building runs short, the gatherers bring what is missing to the friend building it, and the
  smith leaves as much iron, wood and stone in the chest as the buildings asked for (no more), so gear is still made
  while houses go up. Each building keeps its own list, so several going up at once never hide each other's.
- **Repairs.** The builder's repair job looks past gaps it cannot fill yet (carpets without wool, windows without
  glass), so a torch knocked off a wall behind them is still put back.

**How to take part.** The friends choose and build everything themselves. You can speed them up: sand, clay, coal,
iron and wool (or shears) in the supply chest all help, and dirt in the chest makes quick scaffolding. They cannot make
a bell: put one in the chest for the town hall (and the alarm, section 27). To add plans of your own, put them in a
data pack in the world's `datapacks` folder (the file format is in `docs/v3/architecture.md`), then reopen the world
(or restart the server) to load them, and check them with `/friends builds check`. `/reload` also loads them, but it
is a vanilla command that needs cheats or operator rights. A plan you add or change only applies to buildings started
afterwards: every library building already begun or standing keeps the version of its plan it was started with (a copy
is saved with the world), so it is finished, repaired and furnished from that, and its beds, doors and counters stay
where they are. That holds across `/reload`, a data pack added or removed, and updates of the mod.

**Commands** (read-only): `/friends builds` (every plan by kind), `/friends builds <kind>` (`house`, `shop`,
`shop:bakery`, `civic:tavern`...), `/friends builds <plan id>` (one plan, such as `house/oak_cottage`: size, styles,
beds and what it is built from), `/friends builds check` (plan files skipped or with warnings, and why).

**Settings:** `fancyCampBuildings`, `allowScaffolding`, `maxScaffoldHeight`, `friendsShearSheep` (section 14). Digging
sand and clay follows `allowQuarrying`.

**Limits.**

- None of the plans has been built in a game. A script that models the friends built each one on level ground in their
  order; how they look in the world, and how the friends cope with the big ones and with stairs, is untested.
- Big buildings need big, fairly level sites. A block the builders cannot reach is skipped and tried again later.
- Open buildings (the smithy, fish stall, farm shed, market stalls, mason's yard and apiary) are lit but have no door,
  so a mob can walk in at night. A window without glass is an open hole a baby zombie could squeeze through.
- A lantern that became a torch stays a torch. Clay is rare on dry land, so bricks and flower pots may never come.
  Friends cannot dye wool.
- Sand digging leaves a scatter of one-block dips that are not filled back in.
- A friend knocked off a pillar takes fall damage like anyone (six blocks at most).
- A plan bigger than 21,000 characters of JSON, not counting its spacing (the mod's largest is under 7,000), is not
  saved with its sites: those buildings follow whatever plan has that id now. The camp's own buildings (the cottage,
  storehouse and watchtower) are not saved with their sites either, so a later update of the mod that changed one
  would change it under buildings already started.
- The camp remembers up to 100,000 blocks the friends placed (a City-stage village comes to about 20,000, and an old
  village left behind by a camp move still counts). It also tells them which beds, shop chests, furnaces and street
  paths are their own, so if it ever filled, what they built afterwards would look like yours to them (beds excepted:
  those are always remembered): shops not stocked, plots along new streets turned down. The server log says so when it
  fills. Scaffolding keeps its own record, so a full one never strands a builder.


## 22. Finding the way: paths, senses, getting unstuck and sprinting (new in 3.0)

Full details: `docs/v3/navigation.md`.

In 2.0 too many friends wandered into caves, dropped into holes they could not climb out of, or got stuck behind
running water, and some died down there alone. 3.0 changes how they choose their way, then teaches them to get out.

- **Better paths.** Walking between two places on the surface, they go round the hill, not through the cave under it;
  if the only way leads underground they stop at the cave mouth and pick other work (Flint's mine is not affected).
  Running water costs much more than still water, and they never step into a waterfall. They never drop more than
  three blocks, even chasing a monster, and keep back from cliff edges. Lava, fire, powder snow, pointed dripstone and
  wither roses are avoided. Paths reach 48 blocks instead of 32.
- **Sharper senses.** They hear monsters within 16 blocks through walls, and their paths keep away from a creeper they
  can hear. A creeper hissing within 12 blocks sends anyone running, seen or not.
- **Getting unstuck.** A friend getting nowhere tries, in turn: a hop and a step aside; swimming up for air; swimming
  to the best shore, across the current rather than with it; putting a block of dirt or cobblestone from their
  backpack underfoot to climb out of a pit (and taking it back); walking out of a cave to the nearest open sky within
  48 blocks, putting torches down along a dark way if they carry any; and digging a staircase up.
- **Brought home.** As a last resort, a friend still in trouble after about two minutes (half a minute when trapped
  underground hurt or starving, a few seconds when about to drown) is brought safely home, or back to you if
  following you, and everyone is told: "Rowan got lost in a cave and found the way home." Never out of a fight, out
  of a job that is getting on, or after you told them to stay. A stuck follower catches up with you after a few
  seconds when `followTeleportDistance` is above 0.
- **Digging out** only touches natural ground (dirt, grass, sand, gravel, clay, stone, granite, diorite, andesite and
  tuff), never near anything you or the friends built, never on the camp's own ground, and only in the Overworld.
  The camp's own ground is its heart (the camp's size before the village, at any depth) and the sites of the village's
  buildings near the camp's height; there a friend shut in a building or the pen never digs or places blocks: they
  hop, try the doors, and in the end are brought out.
  The rest of a grown village (its gardens and verges, and the caves and mines under it) is dug out of like anywhere
  else. Children never change a block.
- **Sprinting.** Friends sprint like players (dust at their feet, 30% faster) when more than 12 blocks of path are
  left, when running from danger, and to keep up with you while you sprint; not in water, when hungry, or (unless
  fleeing) beside a long drop or near lava. Running costs a little more hunger. Children tire after about ten seconds.

**How to take part.** Nothing to set up. A few blocks of dirt or cobblestone and some torches in a friend's backpack
give them what they need to climb out of a hole or light a dark way.

**Commands:** `/friends senses` / `/friends senses <name>`: for everyone (or one friend), above or below ground, light,
water, current, lava, drops, monsters heard, danger, whether they are stuck or getting out, and whether they run.

**Settings:** `rescueStuckFriends` (section 14). Digging and placing blocks to get out also needs `allowWorldEditing`.

**Limits.**

- No bridges: a friend never puts blocks over running water. They go round or swim, and are brought home if a river
  truly cuts them off.
- "Underground" is a guess (no sky, at least three blocks under the ground's top): a windowless room can count.
- Jobs do not yet check whether a target can be reached: one only reachable through a cave is still chosen, the walk
  stops at the cave mouth, and the job picks again later.
- The way out is looked for up to 48 blocks away, and digging cannot go through deepslate, cobblestone or sandstone:
  deep down, or in a desert, a friend who cannot walk out waits to be brought home.
- A staircase dug out of a cave under the village comes up through its open ground (a garden, a verge) and is left
  there, as it would be in the wilds; never through a building's site or the heart of the camp.
- The rescue is a teleport to a safe, loaded spot; a friend stuck while the camp is not loaded waits until it is.

## 23. Living together: friends, couples, children and skins (new in 3.0)

Full details: `docs/v3/people.md`. All of it is gentle and family friendly (hand holding, hearts, weddings and babies
arriving), never changes a block, and happens on its own.

- **Friendships.** Every pair on the team has a friendship from 0 to 100 that grows as they chat, share, fight side by
  side, work near each other and spend time together. Two people in a low mood may quarrel, the only way a friendship
  falls, so a fed, rested camp gets on well. At 50 they are good friends, at 75 close friends, at 90 best friends.
- **Romance.** Two single, friendly grown-ups who are not family may fall for each other (about four pairs in ten
  never will). One asks the other out and everyone is told: "Fern and Oak are going out together. How lovely!" Each
  day, in the late afternoon or evening, a couple watches the sunset, sits by the campfire or goes for a walk. In a
  village the date comes before the evening at home. Whoever keeps the night watch is neither asked out nor asks, and
  a date ends when one of the two goes on watch.
- **Weddings.** After two days and three dates, deeply in love, one proposes, and the wedding is the next morning at
  the camp centre (or the town hall, once there is one). Everyone free gathers in a ring, the couple say their vows,
  the bell rings, Unity +30, and they take one family name (everyone has one: Hart, Ashby, Fletcher...). A monster
  about or nightfall puts it off a day. A couple whose friendship falls low part and stay friends (married couples
  only rarely).
- **Babies.** A married couple may find they are expecting; the baby arrives about a day later. They need to be at the
  camp together and at work there, married a day, with food in store, both in at least an okay mood, fewer than four
  children, `daysBetweenChildren` since the last, the team under `maxPopulation`, and **room at home**: a free bed in
  their village house, or before there are houses, a spare place in the finished cabin (nine places for the whole
  team; the second cabin adds none, as nobody sleeps in it). `/friends couples` says what is missing. A baby on the way
  counts towards `maxPopulation`, so a newcomer never takes the place of a baby already expected.
- **Children** stay children for `childhoodDays` (10 days). They never work, fight, follow players off, join
  expeditions or go through portals. They play tag and hide-and-seek, watch a parent at work to learn (or go to school
  once there is a teacher), and from the late afternoon go home, to beside their own bed in the family's house (or the
  cabin, while the family has no house), and stay in. When a monster could get at them they run to a grown-up; after
  dark a child already indoors stays in, and only runs to a grown-up in the same room. With nobody to run to, they go
  home, but only if home is further from the monster than they are. Parents and Aegis go for a monster after a child
  first. Children take food from you, nothing else.
- **Growing up.** A grown child joins the team with a speciality taken from a parent's speciality or interest (farmer,
  builder, miner...), and the skill they picked up (Unity +15). People born in the camp never count against
  `maxSettlers`. They start with no tools.
- **Hardcore.** A child who dies is gone for good, and the whole camp mourns. A widowed friend may in time love again.
- **Skins.** Newcomers and children wear skins from the 82-skin **medieval village set** (62 grown-ups and 20
  children, half wide-armed, half slim): tradespeople, everyday villagers, children, and villagers dressed for desert,
  snow, jungle, swamp, savanna and dark forest. They tend to wear one to suit their trade or the land they were met in,
  and a skin nobody else wears is chosen first. To add your own, see "Adding skins" in `docs/v3/people.md` (64x64
  PNGs, `python3 tools/add_skins.py`, then rebuild the mod); `docs/v3/skin-prompt.md` has a prompt for getting more.
  The script tags a skin by the first word after `adult_` or `child_` when it is a trade or place it knows, so put that
  word straight after the prefix: `adult_baker_rosa.png` is tagged baker, but `adult_rosa_baker.png` gets no tag. Tags
  only matter when choosing a grown-up's skin.

**How to take part.** Keep the camp fed and rested (moods matter), and let the village build houses: that is the real
way to make room for babies.

**Commands** (read-only; type first names only, like `pip`: a full name with a space in it will not work):
`/friends family` (every family) and `/friends family <name>` (partner, parents, children, home); `/friends couples`
(couples, wedding days, what a baby still needs); `/friends relationships` and `/friends relationships <name>` (the
closest friendships).

**Settings:** `romance`, `children`, `childhoodDays`, `maxPopulation`, `daysBetweenChildren`, `relationshipSpeed`
(section 14).

**Limits.**

- Slow on purpose: a week or two of in-game days from meeting to marrying (`relationshipSpeed` and `childhoodDays`
  speed it up). The numbers are first guesses.
- Relationships only change while the people are loaded together.
- Before there are village houses, a camp with all nine friends alive has no room for a baby: the cabin sleeps nine,
  and the second cabin adds no room.
- Dates and games are simple: walking to a spot and standing together, with hearts.
- New skins mean rebuilding the mod and giving every player the new JAR; never renumber a skin a world already uses.

## 24. The village: streets, homes, beds and the day, Town and City (new in 3.0)

Full details: `docs/v3/village.md`.

When the camp becomes a **Village** (Unity 250), the friends stop living round a campfire and lay out a town plan.

- **Streets and plots.** Six streets on a grid: the **High Street** east to west through the camp centre, **Market
  Street** north to south, and four lanes 34 blocks out, with the **square** round the campfire where the main streets
  cross. Plots line the streets, every front door facing the street, and the village grows outwards street by street.
  The camp grows to cover it, up to `villageRadius`, and the camp's own later buildings keep off the streets too. A
  friend at home on the outer plots is at the camp: nobody builds a night shelter unless they are more than 8 blocks
  beyond its edge.
- **Houses of their own.** Every household (a single friend, or a couple and their children) gets a house from the
  library, chosen by the beds it needs and the style that suits the land. Everyone builds their own home in their
  spare time; Oak (the builder) puts up everything else and anyone's house. A household moves in once its house is
  finished, or 95% built with every light, door, bed and chest in: nobody sleeps in a house that is dark inside or has
  no door. A wedding brings two households together in one of their houses with a bed for each of them (when neither
  house has room, everyone keeps their own bed until a house big enough for them all is built). A growing family gets
  a bigger house, and newcomers and grown-up children get houses of their own.
- **Real beds.** A friend with a house sleeps in their own bed as villagers do, and gets up at dawn, when hurt, when a
  monster that could get at them comes close (one outside the shut front door does not count: monsters do not open
  doors), or when the night watch raises the alarm about a monster within 40 blocks of them. A friend never takes a bed
  a player is lying in, and only players count for sleeping through the night. The night watch is still kept from the
  campfire, but the watcher looks out over the whole village (up to 96 blocks round them), so a monster among the
  outer houses raises the alarm and wakes the friends asleep near it, while the far end of the village and the
  children sleep on.
- **The day.** Breakfast and lunch at their own table (or now and then at the tavern), work, then from the late
  afternoon home (or to the square, but not under a thunderstorm's dark sky) to be with family and neighbours,
  sometimes asking someone round; a guest walks home at sunset, while it is still light.
- **Streets, water and fields.** Terra lays the streets as paths, surfaced with gravel from the Settlement and
  cobblestone from the Town, with lamp posts every 12 blocks; never within two blocks of anything you built (a lawn
  by your house, a garden, a yard). The farmer fills the well, fountain and wheat field with a bucket, and tills, sows
  and harvests the field; the orchard gets its saplings.
- **Lights and trees.** Terra lights the whole village by day, but after dark only the heart of the camp (its size
  before the village), so the outer streets never keep her out late. The torches and lanterns the village's buildings
  under way still need stay in the chest for the builders. Her saplings go at least 14 blocks back from every street
  (behind the rows of houses), never on a street, a verge or a plot's front.

| Stage | What goes up besides the houses |
|---|---|
| Village | the town plan, the streets, the well in the square, two benches, lamp posts |
| Settlement | the town hall (weddings move there), the market, the tavern, a flower garden, a signpost |
| Town | the school, the chapel, the wheat field, the orchard, the watchtower, a fountain |
| City | the town gate across a main street, with two lengths of wall each side, and the barn |

Shops and workplaces go up as the trades ask for them (section 25). The Town and the City need people, houses and
civic buildings as well as Unity (section 10). Finished civic buildings, farms, shops and workplaces give Unity (+15,
the town hall +30), new houses +10 (at most 40 a day).

**How to take part.** Keep the chest stocked: a house is 150 to 400 blocks, the town hall about 600. Coal (or
charcoal) for torches matters too, as nobody moves into a house until its lights are in. Gravel and cobblestone
surface the streets; the farmer needs a bucket (three iron) for the water. Plots avoid trees, water and your builds,
so clearing or levelling ground along the streets helps. Moving the camp with `/friends camp set` (more than three
blocks away) lets the whole town plan go: the houses stay, but nobody lives in them any more. Setting it again within
three blocks (standing by the campfire, say) keeps the village, and the camp keeps its size.

**Commands** (read-only): `/friends village` (people, babies on the way, homes, buildings, streets, plots, and what
the next stage still needs), `/friends village plots` (every plot), `/friends home` (where everyone lives),
`/friends home <name>` (one person's house, street and bed).

**Settings:** `villageHomes`, `villageRadius`, `villageBuildsAtOnce` (section 14); `maxPopulation` caps the village.

**Limits.**

- The grid is square to the compass: streets are not curved round hills, and plots are skipped where the ground is
  wrong. Big plots are hard to find on rough ground; a building with no plot tries again after five minutes, and after
  three searches in a row find nothing the planner rests a minute. Terra's saplings keep well back from the streets,
  but the deepest buildings can still find one in the way, and the saplings she planted on the ring round the camp in
  a 2.0 world stay where they are.
- A house waits for its lights: a camp with no coal or charcoal for torches keeps its households in the cabin until the
  gatherers bring some.
- A street keeps two blocks clear of anything you built, but grass further than that from your blocks looks natural: a
  big lawn, or the middle of a large fenced field, that a street runs through can still become path. Keep such ground
  off the street lines, or put something of yours on it.
- The night watch is still one friend at the campfire: they see the whole village but not round houses, and a monster
  out of their sight is noticed only when it is close to them or going for someone. The second watcher may be woken
  at midnight in a home at the edge of the village and walk to the campfire through the dark.
- Lighting looks over a big village a little at a time, so a new dark patch can take ten to twenty seconds to be
  noticed. Ground more than eight blocks above the camp's stays dark, and street surfaces never get a torch (the
  verges beside them do).
- Slow: with one builder and everyone else building in their spare time, a full village takes many in-game days.
- The routine is simple: friends walk to a spot and stand there (no sitting on chairs), and a house's chests are only
  furniture. While a friend sleeps in their bed, you cannot lie down in it.
- The walls are two lengths each side of the gate, not a ring round the village.

## 25. Trades and shops (new in 3.0)

Full details: `docs/v3/market.md`.

As the camp grows, grown-ups take up a **trade** besides their speciality, at a real workplace from the building
library, using real materials from the camp's chests. Trades are extra work: their speciality still comes first when
the camp needs it, needs and the night watch always win, and nobody works a trade at night.

| Trade | Where | What they do |
|---|---|---|
| Baker | bakery | Bakes bread, cookies, pies, cakes and baked potatoes, and sells them |
| Shopkeeper | general store, a market stall, or a stall at the supply chest | Sells the village's spare goods and buys what the camp is short of |
| Butcher | butcher's shop | Cooks raw meat in the smoker and sells it |
| Fishmonger | fish stall | Fishes, and sells raw and cooked fish |
| Tailor | tailor's shop | Makes carpets and beds for the builders; sells wool, carpets and beds |
| Blacksmith | smithy or smith's shop | Does the camp's smith work when it is their most useful job (nobody else starts it while they are at it, or for a minute after); while they are busy, too weak or away, anyone may make the gear, as before. At the shop, makes and sells plain iron tools and armour when the camp has 40 or more iron |
| Fisher | fishing hut, or a bank | Fishes by day with a rod; the catch goes to the chest for the cooks |
| Mason | mason's yard | Cuts stone bricks, stairs, slabs and walls for the builders at the stonecutter |
| Beekeeper | apiary | Puts hives on the stands, leads wild bees home, and takes the honey with the campfire lit beneath |
| Carpenter | carpenter's workshop | Makes the wooden parts the builders are short of: stairs, slabs, doors, fences, ladders, chests, barrels and more |
| Doctor | clinic | Milk for poison or withering; a regeneration or healing potion, or a golden apple, for the badly hurt |
| Shepherd | shepherd's hut, or the pen | Shears the pen's sheep over the fence |
| Farmer | farm shed | Composts spare seeds and saplings into bone meal for the farm |
| Teacher | school | Teaches the children, who learn their own trade faster there |
| Innkeeper (the camp's cook) | tavern, or the campfire | Cooks meat and fish, bakes potatoes, makes stews, all for the supply chest |

- **Who gets which.** Sage gives each workplace to the grown-up who suits it best, newcomers first, and never the last
  friend of a speciality if anyone else will do. Children and strangers never hold a trade, and at least
  `friendsWithoutTrade` grown-ups (3) always stay free. Everyone is told, and a friend's status line shows it: "Mabel
  (Forager, Baker)".
- **Before there are workplaces**, a Hamlet with six or more grown-ups has a cook at the campfire, a stallholder at the
  supply chest, a fisher on a bank (with open water near) and a shepherd at the pen (with two or more sheep). They move
  in when their workplace is built.

**How to take part: shopping.** **Right-click a shopkeeper standing at their counter by day** to open the game's
trading screen (sneak and right-click for their backpack instead). Anyone may trade, trusted or not. They **sell** only
what the shop really holds (a stallholder at the camp only what the camp can spare), and never stock the planks,
torches, beds or other things the buildings under way still need; they **buy** what the camp is short of right now,
at most 32 emeralds' worth a visit and always for less than they sell. **Prices** are the game's own villager prices
where it has them (six loaves for an emerald, a bed for three) and never change. Nothing is made from
thin air: the goods on offer come out of the shop's chests while the screen is open and go back if unsold, and what
you sell goes into the supply chest. Shops shut at dusk and in a thunderstorm dark enough for monsters, and a keeper
serves one customer at a time. The beekeeper needs honeycomb or a beehive from you to start: friends never take honey
from a wild nest.

**Commands** (read-only): `/friends trades` (who holds which trade, where, and what they are doing), `/friends shops`
(each shop, its keeper, whether it is open, and its trades so far). `/friends jobs` lists the trades too.

**Settings:** `villageTrades`, `playerShops`, `friendsWithoutTrade`, `requestWorkplaces` (section 14).

**Limits.**

- If the server crashes (not a normal stop) with a trading screen open, the goods held for that visit and up to 32
  emeralds are lost (never duplicated). Renamed emeralds or goods become plain ones.
- Stalls at the camp trade straight from the supply chest, so a player the owner does not trust can buy the camp's
  spare goods there. Switch `playerShops` off on a server where that is unwelcome.
- The blacksmith still works at the camp's crafting table, and has no extra pull towards the smith work: a miner
  blacksmith often keeps mining. The doctor does not brew, the fisher never catches treasure, and the tailor does not
  dye.
- Building stock already on a shop's shelf (put there while nothing was being built) stays there: the builders never
  take from a shop's chests.
- Workplaces need the village's town plan; without it the trades work plainly at the camp.

## 26. Village life: calendar, feasts, music, birthdays, funerals and the Chronicle (new in 3.0)

Full details: `docs/v3/life.md`. All of it happens by itself; you are welcome to join in. Before the village has a town
plan, it all happens at the camp centre.

- **The calendar.** It follows the world's own day count: seven-day weeks, and four seasons of 10 days each, so 40
  days a year. `/friends calendar` shows the date ("Sunday, day 42: the 2nd day of spring, year 2") and what is on.
- **Market day.** Every Saturday, once the village has a market or a shop, shopkeepers spend more of the day at their
  counters calling out to passers-by, and everyone else looks round the market once. Prices and stock do not change.
- **Feasts.** The **midsummer feast**, the **harvest festival** (on the last day of autumn, only if the farms did
  well) and the **winter lights**. From the late afternoon everyone free gathers in a ring at the square, someone
  (Sage, or whoever has been with the camp longest) says a few words, and it is a party until nightfall: the cook hands
  round food from the stores (never more than the camp can spare), and there is music. For the winter lights, up to
  eight lanterns glow round the square all night. Unity +10. A monster near the square holds the party up until it is
  dealt with, and so does the village's alarm (section 27): while the bell rings everyone takes cover or goes to their
  post, and the feast waits for them to come back. If you save and quit (or the server restarts) during a feast or a
  funeral, it carries on when the world is loaded again that evening; if the evening is over by then, a feast whose
  words were said still goes in the Chronicle and gives its Unity, and a funeral whose words were said counts as held.
- **Music.** Every other evening and at every feast, the village's musician (a bard, else the innkeeper, else a
  tinkerer like Spark, else the chattiest friend) puts a note block down and plays two or three old tunes (Frere
  Jacques, Greensleeves...), then picks it up again.
- **Birthdays.** Everyone has one: the day they were born in, or joined, the camp. Friends wish them well, and if food
  is plentiful there is a cake from the chest (or a smaller treat).
- **Funerals and graves.** When someone dies for good, everyone mourns for a few days. A grown-up makes a grave at a
  small **cemetery** the friends lay out on level natural ground towards the camp's edge (behind the chapel, once
  there is one), away from the streets, your builds and the graves already there: a headstone, a waxed sign reading
  "In memory of", the name, the day and "Rest well", and flowers. The next evening everyone gathers for the funeral
  and three friends say goodbye. If it cannot be held (nobody at the camp, a monster about, a storm), it moves to the
  next evening; after three evenings the one lost is remembered quietly in the Chronicle instead. An evening lost to
  the village's alarm is not one of the three. Mourners visit and leave flowers.
- **The Village Chronicle.** Everything is written down, one dated line at a time: "Day 42, spring: Mabel and Oak were
  married at the town hall, and became the Hart family." Arrivals, births, weddings, deaths, finished buildings, new
  stages, feasts and funerals, Sage's plan, the first steps into the Nether and the End, dragons and raids. The keeper
  (Sage, usually) writes it out in a real book, kept in the supply chest and later on the town hall's lectern, where
  you can read it. A full volume (100 pages) goes to the chest. A copy you take is yours: the keeper writes another.

**How to take part.** Come to the square on a feast day. Put a **blank book and quill** in the chest (or a book, or
three paper and a leather, with an ink sac and a feather) for the Chronicle; one with writing in it, or a name of its
own, is never taken. Books, paper and leather that Sage's plan is gathering for the library are left for it, so while
the library's books are still being made, a book and quill of your own is the quickest way. A note block (or eight
planks and a redstone dust) gives music; flowers in the chest go on graves.

**Commands** (read-only): `/friends calendar` (today and the next ten days: feasts, market days, birthdays, funerals),
`/friends chronicle` (the latest page and where the book is), `/friends chronicle <page>` (page 1 is the oldest).

**Settings:** `villageLife`, `graves`, `chronicleBook`, `daysPerSeason` (section 14).

**Limits.**

- Feasts are short: late afternoon to nightfall is about three minutes of real time, and sleeping through the evening
  ends one early. Friends on urgent jobs join late or not at all.
- A feast or funeral carried on after a restart forgets who had already come: the Chronicle names only those who come
  (again) afterwards, and a feast closed after its evening ended names nobody.
- The cook bakes spare wheat into bread and brings ready food; they do not cook specially.
- The cemetery needs level natural ground inside the camp, away from the streets and your builds: in a crowded or hilly
  camp a grave may not find a place (the name stays in the Chronicle).
- Friends do not set out to gather ink sacs or feathers (a fisher now and then brings up an ink sac), and do not pick
  wild flowers for graves: grave flowers only come from the chest.
- Someone away from the camp on their birthday misses it that year.

## 27. Defending the village (new in 3.0)

Full details: `docs/v3/defence.md`. The friends defend the village by themselves. **The alarm bell rings** for:

- at least `alarmHordeSize` (3) hostiles seen in or just beyond the village at night, once one of them comes into the
  village (monsters only prowling round the edge do not ring it);
- a creeper inside the village, by day or night;
- a pillager raid on the village, or on a game village near the camp;
- a player in the village with the Raid Omen, so everyone is ready before the raiders arrive;
- you, ringing a bell in the village (on a shared world, only the owner and trusted players sound the alarm this way).

The nearest friend who is awake, grown up and fit runs to the bell and rings it three times, so raiders near it glow.
Nobody is sent to a bell with a creeper near it. The bell is the town hall's, else the friends' own bell at the
square (stood a few blocks from the campfire, off the line of the main streets), else the school bell, else any other
bell in the village. With no bell, the alarm is shouted. If you move the camp, the bell at the old square is no longer
the friends' own: still within the village, it is rung like any other bell; otherwise they put up another at the new
square when the chest has a bell.

**When it rings:**

- **Taking cover.** Children and anyone not fit to fight go indoors (home, another house, the town hall, tavern, chapel
  or school, or the cabin), shut the friends' own wooden door behind them, and stand away from the windows. They never
  run towards a creeper or past a monster. At night, anyone indoors whose bed is there simply goes to bed; someone
  found asleep out in the open (their own bed taken or out of reach) is got up and kept indoors until the all-clear.
- **Fighters to their posts.** Everyone grown up, fit and armed takes the post nearest the danger (a gate, a stretch
  of wall, the bell, the camp centre), never near a creeper. They go first for a monster at a door, then one going for
  someone, then a spider climbing a wall. They never shoot with you, a villager, a friend or an animal in the way.
  The alarm gets sleeping fighters up if they have had enough sleep (40 energy or more); one with less is left asleep.
  A fighter who wears out below 20 energy goes to bed and sits out the rest of that alarm, so nobody is got up and
  sent back to bed over and over.
- **The all-clear** comes once nothing has been seen in the village for 30 seconds, or at dawn for a night alarm. A raid
  keeps the alarm on until it is over; any other alarm ends after five minutes at most.

**Guards, raids and fire:**

- **Guards.** Once the village has built its own watchtower, town gate or walls (Town and City stages; the camp's first
  watchtower does not count), up to `guardsPerShift` (2) armed grown-ups stand guard each half of the night besides
  the night watch, warriors first, taking turns. A guard must be rested (35 energy or more) to be picked. Nobody
  stands both halves: everyone who stood any part of the first shift sleeps the second, and is not picked for the
  night watch's second half either. A guard who gets hurt, worn out (below 20 energy) or stuck is relieved, and is not
  picked again that night. A guard picked at dusk goes straight to their post, leaving the evening at home and a feast
  to the others (one napping after last night's shift gets up for it). A guard with a bow shoots from the
  watchtower's lookout; others stand by the gate or the wall and walk short patrols. Each guard earns 2 Unity in the
  morning (at most 8 a day) and may nap the next day.
- **Raids.** The friends fight raiders together, helping villagers and iron golems (never hurting them). A raid beaten
  off earns 40 Unity (at most 80 a day). **Note:** the friends' village has beds and bells, so the game may count it as
  a village: walking into it with Bad Omen starts a raid on the friends.
- **The fire watch.** Fire near the houses or the camp is put out by hand, flame by flame, by up to two fit grown-ups,
  and the builder then mends what burnt in the friends' buildings. Soul fire, a fireplace on netherrack, fire within two
  blocks of lava, fire beside water and fire outside the camp are left alone. 1 Unity a blaze (at most 10 a day).

**How to take part.** Put a **bell** in the supply chest: the friends cannot make one (villagers sell them). Ring a bell
yourself when you see trouble coming, fight beside them, and keep Bad Omen out of the village unless you want a raid.

**Commands** (read-only): `/friends defence`: the alarm now and the last one, the bells, the guard posts and who stands
each tonight, who is on duty, fires to put out, and the tallies so far. `/friends camp` says when the alarm is ringing.

**Settings:** `villageDefence`, `guardsPerShift`, `alarmHordeSize`, `fireWatch` (section 14).

**Limits.**

- Taking cover is simple: walking to a spot inside and standing there. The way to a shelter or the bell is judged in a
  straight line, so the path walked can still bend past danger.
- The spiral stair up the village's own watchtower (Town stage) has not been tried in game. The walls have no
  walkway: wall guards stand on the ground and cannot shoot over. There are no special tactics against ravagers or
  evokers.
- Fire is put out by hand only, with no water: a big blaze can spread faster than two friends can put it out. Where a
  fire has burnt before, a new one can take a few seconds longer to be noticed.
- Fighters stand at their posts for the whole alarm, and a long night of alarms tires them out. As a fighter asleep
  with less than 40 energy is not got up at all, a village of tired people may have few at their posts.
- Whether a sleeper is indoors is judged by the sky above them: a bed under a glass roof counts as out in the open,
  and its sleeper is got up and sent to stand inside at the alarm.
- A bell you ring for fun still sends the children indoors for at least half a minute.

## 28. Pets and maps (new in 3.0)

Full details: `docs/v3/pets.md`.

- **Adopting.** Anyone on the team without a pet may adopt a **stray cat** (with raw cod or salmon) or a **wild wolf**
  (with bones, and then it is a dog), using food from the camp chest, with a one in three chance a feed, as for you.
  Children are keenest, and a parent finds a pet for their child before one for themselves. The pet gets a cosy name
  and everyone is told: "Pip Hart has a new cat and has named it Biscuit." Unity +3 (at most 6 a day). One pet each, at
  most `maxPets` (8) in the camp, and only when the owner has a home for it (their village house, or before then a
  finished cabin). No cat is tamed while the camp is short of food: its raw fish is what the cooks make meals from.
  Parrots are not kept.
- **Never yours.** The friends never tame, lead or touch an animal that is named, tamed or owned, on a lead, ridden,
  saddled or wearing armour, near anything you built or inside your fences, or a young or angry wolf.
- **Life with a pet.** By day a pet follows its owner about the camp, and stays near the camp centre while they are
  off. Called by name ("Biscuit! Here, Biscuit!") it comes running. At night it goes home and sits beside its owner's
  bed. The owner feeds it from the chest now and then (fish for a cat; rotten flesh, else raw meat, for a dog), and
  whenever it is hurt. While the camp is short of food, raw meat and fish are kept for the cooks: a dog only gets
  rotten flesh, and a cat waits until there is food to spare. A pet that tags along into the animal pen never holds the
  gate open and is never herded with the livestock; one shut in or out is brought to its owner (or home at night).
- **Dogs and danger.** A dog stands up for its owner like your own tamed wolf, but never goes for a creeper, nor for a
  monster its owner would shoot (it would be in the way of the arrows). Cats keep phantoms and creepers away. A pet
  of the camp only ever hurts monsters, and friends never hurt a pet. A wild wolf you hit can call a dog to help, as
  wolves call each other, but the dog drops it, anger and all, within a second.
- **When an owner is gone**, the pet goes to their family (a young child first), else to a child with no pet, else it
  stays on as the camp's own pet until someone takes it in.
- **Scout's maps.** Scout makes empty maps from eight paper round a compass (making a compass from four spare iron and
  a redstone if need be). Her first map is of the camp, 256 blocks across, drawn as she walks about; after that she maps
  where her trips take her (512 blocks across, such as "Map of the land 300 blocks north-east of the camp"). Finished
  maps are marked with the camp, the town hall and the places her trips found, and hung in an item frame on the town
  hall's wall (inside the camp's cabin before there is a town hall), or kept in the chest.
- **A copy for you.** Right-click Scout holding an **empty map** of your own and she gives you a copy of a finished map
  (the one showing where you stand, else the camp's, else the newest); that works at any hour. Or type `/friends map`:
  she makes the copy from the camp's own stock and brings it over; stay within 48 blocks. As it uses up the camp's
  stores, `/friends map` is for the camp's owner and the players they trust, as other orders are (section 20), and
  only by day: after nightfall she is off to bed, so ask in the morning (a copy still on its way at nightfall lapses
  too). If the camp is short of paper
  or a compass, she says so. A copy keeps filling in as Scout draws.

**How to take part.** Raw cod or salmon and bones in the chest let the friends adopt and feed pets, and rotten flesh
feeds dogs (the only treat a dog gets while the camp is short of food). Paper and a compass (or iron and redstone) let
Scout make maps. Sage's plan comes first: she never takes paper, leather or iron the plan is collecting (paper and
leather for the library's books, iron in the iron age), none while it still wants more and, once it has enough, only
what lies beyond its amount.

**Commands:** `/friends pets` (every pet, whose it is, what it is doing, its health), `/friends maps` (every map, what
it shows, how much is drawn, where it is), `/friends map` / `/friends map <number>` (ask for a copy of the best map for
where you stand / of one map; by day, for the owner and trusted players).

**Settings:** `pets`, `maxPets`, `scoutMaps` (section 14).

**Limits.**

- Stray cats are rare away from villages: a camp may only get cats once its village has five or more beds close
  together. Wolves live in forests and taiga.
- Pets follow only about the camp (no teleporting across the world), sit only at night, do not breed, and do not go on
  trips or expeditions. Cats do not sit on chests, or lie on beds and furnaces for minutes on end.
- You cannot give orders to a friend's pet (sit, collar, armour), but you can feed it, put it on a lead, or rename it
  with a name tag.
- Maps are drawn only from land already loaded, and the Nether is not mapped. A place found later only shows on copies
  made after it was found.
- A cartography table cannot make a map from paper alone in Java Edition: a map always costs eight paper and a compass.
