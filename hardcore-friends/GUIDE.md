# Hardcore Friends: installation and gameplay guide

**For:** Minecraft Java **26.3** with **Fabric**. Single-player or a small server. Works in Hardcore without enabling cheats.

## 1. Install

### Option A: CurseForge app (easiest)

1. Open the CurseForge app and go to **Minecraft → Create Custom Profile → Import**.
2. Choose `Hardcore-Friends-1.0.0-CurseForge.zip`.
3. CurseForge installs Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3 and the mod.
4. Press **Play**. This creates a separate profile with its own saves folder, so your existing worlds are not touched.

### Option B: Manual install

1. Install the Fabric Loader for Minecraft 26.3 with the official installer from fabricmc.net (choose loader 0.19.5 or newer).
2. Put two files in your `.minecraft/mods` folder:
   - `hardcore-friends-1.0.0.jar`
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

## 3. The nine friends

Each friend's role is their speciality: the work they choose first and do best. Any friend can do any of these jobs, though (see section 4).

| Friend | Role | Personality | Their speciality |
|---|---|---|---|
| **Fern** | Farmer | patient, caring | Harvests ripe crops and replants straight away, tills new farmland next to water, lays out a farm plot (with a water bucket from the chest), bakes bread, uses bone meal, shares food with hungry players. Keeps livestock: brings wild animals home to the pen, breeds them, butchers the surplus, cooks the meat, and hunts when food is short (section 6) |
| **Oak** | Builder | practical, methodical | Builds the camp step by step from real materials: chest, campfire, crafting table, furnace, torch posts, cabin, storehouse, watchtower, lantern posts, second cabin. Saws planks and repairs damage |
| **Flint** | Miner | cautious, dry humour | Mines exposed ores (with the right pickaxe), digs one tidy staircase mine with branch tunnels outside camp, lights it with torches, avoids lava and water, smelts ore in the camp furnace |
| **Scout** | Explorer | curious, adventurous | Scouts widening rings around camp by day, records ores, trees, lava and villages for the team, and warns you about creepers, mobs closing in, nightfall and storms |
| **Spark** | Redstone inventor | clever, excitable | Builds working vanilla contraptions: automatic cabin door (pressure plates), drop-off hopper on the supply chest, auto-smelter, and night lamp posts (needs nether materials). Keeps the torch supply up |
| **Aegis** | Warrior | calm, protective | Guards you and the camp, attacks hostile mobs near players and friends, patrols at night (on the watchtower once it exists), and takes the best sword, armour and shield from the chest |
| **Sage** | Strategist | thoughtful, observant | Gives Hardcore survival advice for your situation (health, hunger, darkness, phantoms, night, tools, mining depth), works out what the camp is short of, and sets the team's focus |
| **Terra** | Landscaper | creative, tidy | Lays dirt paths between camp buildings, plants saplings and flowers, puts torches in dark spots (fewer mob spawns), tidies dropped items, fills small holes, fences the farm, builds the animal pen |
| **Rowan** | Forager | resourceful, generous | Fells natural trees outside camp and replants them, quarries dirt and stone from small shallow pits outside camp, picks berries, and delivers building materials to Oak first |

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
- **Skill.** Specialists mine, chop and dig 20% faster at their own work. Their interest goes at normal speed, and anything else 15% slower.
- **Duties that stay with one friend:** Aegis's guard duty and gear, Sage's observations and stores review, and Scout's reports.

A friend starting work outside their speciality may say so, for example "I'll lend a hand with harvesting crops."

## 5. Needs and mood

Like characters in The Sims, every friend has five everyday needs, each from 0 (desperate) to 100 (fully met). Friends look after them on their own.

| Need | Goes down | How friends meet it |
|---|---|---|
| **Hunger** | All the time, a little faster while working. About half a loaf of bread's worth a day: a loaf every two days, so nine friends eat four or five loaves a day | They eat real food: from their backpack first, otherwise one item from the supply chest. Bowls go back in the backpack |
| **Energy** | While awake, so they are tired by nightfall | They sleep at night, inside the cabin once it is built, otherwise around the camp centre. A night you sleep through counts as a whole night's sleep for them too. A friend who is exhausted naps by day. Aegis keeps the first watch |
| **Social** | While alone. Being near a friend or you fills it | They walk over to another friend for a chat |
| **Fun** | While working | They take a short break for a pastime that suits them: Flint skips stones, Spark tinkers with a gadget, Sage watches the clouds or the stars, and so on. Pastimes never change your world |
| **Comfort** | In rain or darkness out in the open, and while badly hurt | They warm up by a lit campfire or go indoors. A roof overhead and a lit campfire nearby are cosy |

The lower a need, the more urgent it is. A mild need waits until the job in hand is done; a desperate one comes before any work, however pressing: a starving friend drops everything to eat when there is food, and an exhausted one goes to bed at night. Work never wakes a sleeping friend. Friends only see to their needs while working on their own: a friend who is following you or holding position puts them off until you send them back to work.

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
- Friends are mortal. They retreat when badly hurt, eat from their backpacks, and run from creepers. Healthy friends holding a tool stand together and fight off zombies and other close attackers, so nobody is picked off alone. Only Aegis goes after skeletons and other archers; everyone else gets out of their line of fire, and a badly hurt friend stays well out of bow range until they recover. Out of combat (no damage for 10 s, no target, not burning) they recover 1 health every 4 s. If they die, they drop a **backpack item** holding everything they carried and wore. It never despawns, and its coordinates go to chat. Use it (right-click) to unpack.
- A fallen friend's name returns as a newcomer after 3 in-game days. Set `deadFriendsReturnAfterDays` to `-1` in `config/hardcorefriends.json` for permanent loss.
- Zombies, skeletons, spiders, illagers and witches hunt friends just as they hunt villagers.

## 12. How friends treat your world

- **Building and landscaping** happen only inside the camp radius: 24 blocks, growing to 40.
- **Tree felling, quarrying and the staircase mine** happen only in a ring up to 48 blocks beyond the camp. Flint also mines ores that are already exposed, inside the camp or in that ring.
- Friends never break chests, furnaces, signs, beds or any other block entity, or anything crafted-looking: planks, doors, glass, torches, slabs, stairs, fences and so on. They also stay clear of blocks next to such things.
- Trees are felled only if they grew naturally (natural leaves), never log walls or trees inside camp. Quarry pits are 5×5 and at most 2 blocks deep. Mines stay inside one 24×24 area.
- **Animals:** friends only lead away or hunt wild animals, never yours (named, tamed, on a lead, saddled, near anything you built or inside your fences), never butcher yours in their pen, and only hunt by day in the gathering ring outside the camp (section 6). The only blocks they open and shut are the gate of their own pen.
- Every change is logged (`/friends log`). Turn editing off entirely with `allowWorldEditing: false`, or individually with `allowTreeFelling`, `allowQuarrying` and `allowMining`.

## 13. Honest limits

- Friends only act while their area is loaded, which means near a player. They do not work while you are far away, and their needs do not change then either.
- Every friend can do every job, but a stand-in only works on someone else's speciality in their spare time, and more slowly. A camp missing several specialists grows more slowly.
- Needs are five simple numbers, not a full life simulation. Friends meet them with a fixed set of jobs: eating, sleeping, chatting, a pastime, warming up and, when too weak to work, resting. They sleep where they lie down (in the cabin or around the camp centre), not in beds.
- Friends see to their needs only while working on their own. A friend following you or holding position does not eat, sleep or rest; they eat from their backpack only when hurt or very hungry. Hand them food, or send them back to work now and then.
- Chats between friends are an exchange of pre-written lines, not a real conversation.
- They build from fixed blueprints, adapted to the wood you have. They do not invent new buildings.
- Pathfinding is vanilla mob pathfinding. On rough terrain a friend can get stuck. Stuck jobs time out and are retried later.
- Mining is one staircase mine plus exposed ores near camp. Only trees they can fully reach (about 6 blocks tall) are felled.
- The farm plot needs a fairly level spot inside camp: a level hole for the water with about 20 level grass or dirt blocks around it. Bumps are fine, but on a camp that is all slopes or rock Fern says she needs "a flat patch of grass", and the camp cannot grow past stage 2 until she gets one. Help her by levelling a patch, or simply pour water on level ground in camp: she farms around any water inside the camp.
- When the next camp step is waiting on something nobody can fetch (for example nether materials for Spark's lamp posts), friends with nothing useful left to do take a break near camp.
- Contraptions are a fixed set of vanilla redstone builds.
- Livestock is cows, pigs, sheep and chickens in one pen; rabbits are only hunted. Friends do not shear sheep, milk cows or collect eggs. An animal following its food can wander off before it reaches the pen; it is fetched again later. An unnamed animal of yours standing loose in the open, with nothing you built nearby, looks wild to the friends: name it or fence it in.
- Dialogue is pre-written and chosen by situation and personality. It is not free conversation.
- Purely natural-looking player builds (for example a hut made only of dirt or stone) cannot be told apart from terrain if they sit in the gathering ring outside your camp. Keep such builds inside the camp radius, or add any crafted block nearby.

## 14. Config (`config/hardcorefriends.json`)

`chatter`, `campRadius` (24), `maxCampRadius` (40), `resourceRadius` (48), `allowWorldEditing`, `allowTreeFelling`, `allowQuarrying`, `allowMining`, `deadFriendsReturnAfterDays` (3, or −1 for permanent), `followTeleportDistance` (48, 0 = off), `monstersTargetCompanions` (true).
