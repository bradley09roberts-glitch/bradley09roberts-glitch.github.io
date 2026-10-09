# Hardcore Friends: installation and gameplay guide

**For:** Minecraft Java **26.3** with **Fabric**. Single-player or a small server. Works in Hardcore without enabling cheats.

## 1. Install

### Option A: CurseForge app (easiest)

1. Open the CurseForge app and go to **Minecraft → Create Custom Profile → Import**.
2. Choose `Hardcore-Friends-2.0.0-CurseForge.zip`.
3. CurseForge installs Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3 and the mod.
4. Press **Play**. This creates a separate profile with its own saves folder, so your existing worlds are not touched.

### Option B: Manual install

1. Install the Fabric Loader for Minecraft 26.3 with the official installer from fabricmc.net (choose loader 0.19.5 or newer).
2. Put two files in your `.minecraft/mods` folder:
   - `hardcore-friends-2.0.0.jar`
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
| **Energy** | While awake, so they are tired by nightfall | They go to bed at nightfall and sleep until dawn, inside the cabin once it is built, otherwise around the camp centre, unless it is their turn on watch (section 6). A night you sleep through counts as a whole night's sleep for them too. A friend who is exhausted naps by day, and one who kept a watch naps sooner |
| **Social** | While alone. Being near a friend or you fills it | They walk over to another friend for a chat |
| **Fun** | While working | They take a short break for a pastime that suits them: Flint skips stones, Spark tinkers with a gadget, Sage watches the clouds or the stars, and so on. Pastimes never change your world |
| **Comfort** | In rain or darkness out in the open, and while badly hurt | They warm up by a lit campfire or go indoors. A roof overhead and a lit campfire nearby are cosy |

The lower a need, the more urgent it is. A mild need waits until the job in hand is done; a desperate one comes before any work, however pressing: a starving friend drops everything to eat when there is food. At night work waits for the morning, so friends go to bed (section 6). Work never wakes a sleeping friend. Friends only see to their needs while working on their own: a friend who is following you or holding position puts them off until you send them back to work.

**Mood.** Together the needs make a mood: miserable, low, okay, good or great. Hunger and energy count the most.

- Mood changes how fast friends work: 80% when miserable, up to 110% when every need is met.
- Friends say how they feel now and then. A friend in a low mood names their worst need ("Worst need: fun").
- When the whole team is in high spirits (a good average mood of 75 or more, and nobody feeling low), the Unity bond grows by 1 every in-game hour (at most 12 a day). A fed, rested camp that spends time together gets there. A low mood never costs Unity.

**Food.** Keep food in the supply chest. Bread, baked potatoes, carrots, apples, berries, cookies, dried kelp, pumpkin pie, stews and cooked meat or fish all count; filling food such as cooked beef satisfies more hunger. Friends do not cook, so raw potatoes, meat and fish only count once you cook them.

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

**The gate.** Friends open and shut the pen gate themselves. They never shut it while you are in or right next to the pen, or while anyone stands in the gateway, so while you are there they do not start pen jobs that open it. A friend who ends up inside the pen with nothing to do there walks out and shuts the gate behind them. Eggs laid in the pen and the ground inside it are left alone: nobody tries to tidy up behind the fence or plant flowers there.

## 6b. Nights: bedtime, the watch and standing together

Nights are when Hardcore friends die, so the camp keeps a night routine.

- **Bedtime.** At nightfall friends put their work down (it waits for the morning) and go to bed: inside the cabin once it is built, otherwise in a ring around the camp centre. Only the jobs that belong to the night go on: a hungry friend has supper first, a chilly one warms up by the campfire first, anyone still out comes home, Terra lights a dark camp before she turns in, and a badly hurt friend with no food is still fed. Pastimes and chats wait for the morning too. Once asleep, nothing but trouble gets them up: they sleep until dawn unless they are hurt, a monster that could get at them comes within 8 blocks, a friend within 16 blocks is trading blows (or has a hostile within 4 blocks), the watch raises the alarm, or their own watch begins.
- **The watch rota.** Someone always stays up. The night has two watches: the **first** from dusk to midnight, the **second** from midnight to dawn. Aegis keeps the first watch whenever he is alive and working at the camp. The second goes to a healthy friend with something to fight with: the best armed (any sword before any axe before any other tool), then the healthiest, but never whoever kept a watch the night before if anyone else can, so the duty rotates and nobody loses sleep every night. If Aegis has died, or is away following you, someone else keeps the first watch as well. A watcher who dies, is sent to follow you or stay, gets too weak, runs out of energy, or is more than 24 blocks beyond the camp's edge for over 30 seconds is relieved by the next friend in line, and friends at the camp are chosen before friends away from it. The second watcher sleeps until midnight and is then woken for their watch; whoever kept a watch naps sooner the next day. A friend alone at camp (other than Aegis) has nobody to watch over and simply sleeps.
- **Keeping watch.** The watcher stands by the campfire (or the camp centre), stepping round it now and then and looking out over the camp, and goes for any hostile that comes into the camp. Aegis keeps his watch walking his posts round the camp, or from the watchtower once it stands; on his watch he only stays beside players inside the camp or within 8 blocks of its edge.
- **The alarm.** When the watcher spots a hostile inside the camp (one they can see, or one out of sight on the camp's own ground with a way to it, not one in a cave beneath), they raise the alarm ("Zombie in the camp! Everyone up!"). Everyone asleep in the camp wakes, and every armed friend in the camp (holding a sword or an axe) goes to fight it. When it is dealt with they go back to bed. Each hostile raises the alarm once a night.
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

None of these commands give items, teleport you, or change time, weather, game mode or difficulty.

## 9. Unity bond

The bond grows when you spend time near your friends, when they deliver resources and share with each other, chat with each other (1 point a chat, at most 30 a day), defend one another, and finish camp buildings, and while the whole team is in high spirits (1 point an in-game hour, at most 12 a day). Losing a friend costs 80 points. A low mood costs nothing.

| Level | Points | Bonus |
|---|---|---|
| Strangers | 0 | none |
| Acquaintances | 100 | backpacks hold 18 stacks; friends share food with hungry players |
| Companions | 250 | **work rhythm**: +15% work speed beside another friend; **careful hands**: 20% less tool wear |
| Close Friends | 500 | backpacks hold 27 stacks; faster healing at camp (not while starving); Scout's warnings make threats glow |
| Family | 800 | **rally**: below 3 hearts with 2+ friends nearby, you get 5 s of Regeneration I and they target your attacker (10-minute cooldown) |

None of these bonuses can stop you dying.

## 10. The camp grows into a settlement

**Campsite → Camp → Hamlet → Village → Settlement.** Each stage needs that stage's buildings finished. The later stages also need Unity: 100 for Hamlet, 250 for Village and 500 for Settlement. Use `/friends camp` to see what is built and what Oak is still waiting for. Everything is built from what is in the supply chest, so keep it stocked or let Rowan and Flint fill it. Cabin windows use glass panes when the chest has glass (6 glass make 16 panes); otherwise Oak closes them with planks. If the supply chest is broken, friends forget it: Oak builds a new one, or you can link another with `/friends chest`. If a friend is not on your team, the others stand in and build their improvements too, just more slowly. At the Hamlet stage Terra also builds the animal pen (section 6); it is optional and never holds the camp back.

## 11. Hardcore rules

- Your own Hardcore death is unchanged: one life, then spectator. There are no extra lives.
- Friends need food. A starving friend loses health down to one heart and cannot heal. Starving never kills them on its own, but it leaves them one hit from death.
- Friends are mortal. They retreat when badly hurt, eat from their backpacks, and run from creepers. Healthy friends holding a tool stand together and fight off zombies and other close attackers, so nobody is picked off alone; in the camp armed friends come from further away, and at night the watch raises the alarm (section 6). Friends with a bow shoot skeletons and other archers; everyone else gets out of their line of fire, and a badly hurt friend stays well out of bow range until they recover. In a fight a badly hurt friend eats a golden apple or drinks a healing potion if they carry one. Out of combat (no damage for 10 s, no target, not burning) they recover 1 health every 4 s. If they die, they drop a **backpack item** holding everything they carried and wore. It never despawns, and its coordinates go to chat. Use it (right-click) to unpack.
- A fallen friend's name returns as a newcomer after 3 in-game days. Set `deadFriendsReturnAfterDays` to `-1` in `config/hardcorefriends.json` for permanent loss.
- Zombies, skeletons, spiders, illagers and witches hunt friends just as they hunt villagers.
- Newcomers (section 17) are mortal too: a newcomer who dies is gone for good.

## 12. How friends treat your world

- **Building and landscaping** happen only inside the camp radius: 24 blocks, growing to 40.
- **Tree felling, quarrying and the staircase mine** happen only in a ring up to 48 blocks beyond the camp. Flint also mines ores that are already exposed, inside the camp or in that ring.
- Friends never break chests, furnaces, signs, beds or any other block entity, or anything crafted-looking: planks, doors, glass, torches, slabs, stairs, fences and so on. They also stay clear of blocks next to such things.
- Trees are felled only if they grew naturally (natural leaves), never log walls or trees inside camp. Quarry pits are 5×5 and at most 2 blocks deep. Mines stay inside one 24×24 area.
- **Animals:** friends only lead away or hunt wild animals, never yours (named, tamed, on a lead, saddled, near anything you built or inside your fences), never butcher yours in their pen, and only hunt by day in the gathering ring outside the camp (section 6). The only blocks they open and shut are the gate of their own pen.
- **New in 2.0**, each with its own strict rules (details in `docs/v2/`): Terra levels building sites inside the camp (natural ground only, never within 3 blocks of your builds, at most 3 blocks up or down); friends caught out at night build a small shelter or pillar up out of reach and take it all back afterwards (never within 6 blocks of your builds); Flint's deep mine stays in its own box and seals caves only inside it; obsidian is only made from natural lava (never lava open to the sky at y = 40 or above); on expeditions Scout builds a small marker over the stronghold and friends pillar up to the End crystals. Survivor camps (section 17) are new world generation in newly explored chunks only.
- Every change is logged (`/friends log`). Turn editing off entirely with `allowWorldEditing: false`, or individually with `allowTreeFelling`, `allowQuarrying` and `allowMining`.

## 13. Honest limits

- **2.0 has not been played yet.** Everything new in this version compiles and was reviewed by reading it against the game's own code, but none of it has been tried in game. Expect rough edges, and test in a copy of your world first. Each part's own honest limits are in `docs/v2/`.
- **Nights are the dangerous time.** The night watch, the alarm and standing together are new in 2.0 and untested in game. In 1.0's two-day test run, before the watch existed, 2 of 9 friends died to zombies on the first night and none after. Stay near camp after dark, light it well, and help in fights.
- The camp keeps running while you (the owner or a trusted player) are online anywhere, but not while nobody is online. Monsters only spawn near players, so an empty camp gets no new ones, but those already there keep fighting.
- Every friend can do every job, but a stand-in only works on someone else's speciality in their spare time, and more slowly. A camp missing several specialists grows more slowly.
- Needs are five simple numbers, not a full life simulation. Friends meet them with a fixed set of jobs: eating, sleeping, chatting, a pastime, warming up and, when too weak to work, resting. They sleep where they lie down (in the cabin or around the camp centre), not in beds.
- Friends see to their needs only while working on their own. A friend following you or holding position does not eat, sleep or rest; they eat from their backpack only when hurt or very hungry. Hand them food, or send them back to work now and then.
- Chats between friends are an exchange of pre-written lines, not a real conversation.
- The night watch looks out over the camp from the campfire: it notices a hostile it can see, or one out of sight on the camp's own ground with a way to it, not one in a cave beneath the camp. The alarm wakes friends inside the camp, not those out in the gathering ring.
- They build from fixed blueprints, adapted to the wood you have. They do not invent new buildings.
- Pathfinding is vanilla mob pathfinding. On rough terrain a friend can get stuck. Stuck jobs time out and are retried later.
- Mining is one staircase mine plus exposed ores near camp, and (for Sage's plan) one deep branch mine at a time. Only trees they can fully reach (about 6 blocks tall) are felled.
- The farm plot needs a fairly level spot inside camp: a level hole for the water with about 20 level grass or dirt blocks around it. Bumps are fine, but on a camp that is all slopes or rock Fern says she needs "a flat patch of grass", and the camp cannot grow past stage 2 until she gets one. Help her by levelling a patch, or simply pour water on level ground in camp: she farms around any water inside the camp.
- When the next camp step is waiting on something nobody can fetch (for example nether materials for Spark's lamp posts), friends with nothing useful left to do take a break near camp.
- Contraptions are a fixed set of vanilla redstone builds.
- Livestock is cows, pigs, sheep and chickens in one pen; rabbits are only hunted. Friends do not shear sheep, milk cows or collect eggs. An animal following its food can wander off before it reaches the pen; it is fetched again later. An unnamed animal of yours standing loose in the open, with nothing you built nearby, looks wild to the friends: name it or fence it in.
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

Besides the nine friends, the world now has other people in it, each with their own name, look (one of the game's
default skins) and trade:

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
  food, put a friend on a lead, move the camp or ask for deliveries, or hurt a friend at all. Anyone may look and anyone
  may feed a friend. The single-player host and operators are always allowed, and someone playing alone is never
  limited or refused.
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
