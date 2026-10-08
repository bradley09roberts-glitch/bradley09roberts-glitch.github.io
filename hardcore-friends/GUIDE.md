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
6. Put useful starter supplies in the chest: logs, cobblestone, coal, seeds, a water bucket and some food. The friends restock from it, deposit what they gather into it, and build with what is in it.

## 3. The nine friends

| Friend | Role | Personality | What they do on their own |
|---|---|---|---|
| **Fern** | Farmer | patient, caring | Harvests ripe crops and replants straight away, tills new farmland next to water, lays out a farm plot (with a water bucket from the chest), bakes bread, uses bone meal, shares food with hungry players |
| **Oak** | Builder | practical, methodical | Builds the camp step by step from real materials: chest, campfire, crafting table, furnace, torch posts, cabin, storehouse, watchtower, lantern posts, second cabin. Saws planks and repairs damage |
| **Flint** | Miner | cautious, dry humour | Mines exposed ores (with the right pickaxe), digs one tidy staircase mine with branch tunnels outside camp, lights it with torches, avoids lava and water, smelts ore in the camp furnace |
| **Scout** | Explorer | curious, adventurous | Scouts widening rings around camp by day, records ores, trees, lava and villages for the team, and warns you about creepers, mobs closing in, nightfall and storms |
| **Spark** | Redstone inventor | clever, excitable | Builds working vanilla contraptions: automatic cabin door (pressure plates), drop-off hopper on the supply chest, auto-smelter, and night lamp posts (needs nether materials). Keeps the torch supply up |
| **Aegis** | Warrior | calm, protective | Guards you and the camp, attacks hostile mobs near players and friends, patrols at night (on the watchtower once it exists), and takes the best sword, armour and shield from the chest |
| **Sage** | Strategist | thoughtful, observant | Gives Hardcore survival advice for your situation (health, hunger, darkness, phantoms, night, tools, mining depth), works out what the camp is short of, and sets the team's focus |
| **Terra** | Landscaper | creative, tidy | Lays dirt paths between camp buildings, plants saplings and flowers, puts torches in dark spots (fewer mob spawns), tidies dropped items, fills small holes, fences the farm |
| **Rowan** | Forager | resourceful, generous | Fells natural trees outside camp and replants them, quarries dirt and stone from small shallow pits outside camp, picks berries, and delivers building materials to Oak first |

The friends talk in their own voices. Use `/friends chatter quiet|normal|chatty` to set how much. Danger warnings always show.

## 4. Controls

| Action | How |
|---|---|
| See a friend's status | Right-click them with an empty hand |
| Open their backpack | Sneak + right-click with an empty hand |
| Give an item | Right-click them while holding it (it goes into their backpack) |
| Heal a friend | Right-click with food while they are hurt |
| Hurt a friend on purpose | Only while sneaking. Ordinary swings and arrows pass harmlessly |

## 5. Commands (no cheats needed)

| Command | Purpose |
|---|---|
| `/friends` or `/friends help` | Overview |
| `/friends recruit <name>` | Recruit a friend (2 common food) |
| `/friends list` | Everyone's status, health and current job |
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

## 6. Unity bond

The bond grows when you spend time near your friends, when they deliver resources and share with each other, defend one another, and finish camp buildings. Losing a friend costs 80 points.

| Level | Points | Bonus |
|---|---|---|
| Strangers | 0 | none |
| Acquaintances | 100 | backpacks hold 18 stacks; friends share food with hungry players |
| Companions | 250 | **work rhythm**: +15% work speed beside another friend; **careful hands**: 20% less tool wear |
| Close Friends | 500 | backpacks hold 27 stacks; faster healing at camp; Scout's warnings make threats glow |
| Family | 800 | **rally**: below 3 hearts with 2+ friends nearby, you get 5 s of Regeneration I and they target your attacker (10-minute cooldown) |

None of these bonuses can stop you dying.

## 7. The camp grows into a settlement

**Campsite → Camp → Hamlet → Village → Settlement.** Each stage needs that stage's buildings finished. The later stages also need Unity: 100 for Hamlet, 250 for Village and 500 for Settlement. Use `/friends camp` to see what is built and what Oak is still waiting for. Everything is built from what is in the supply chest, so keep it stocked or let Rowan and Flint fill it. Cabin windows use glass panes when the chest has glass (6 glass make 16 panes); otherwise Oak closes them with planks. If the supply chest is broken, friends forget it: Oak builds a new one, or you can link another with `/friends chest`. If a role's friend is not on your team, the camp grows without that improvement.

## 8. Hardcore rules

- Your own Hardcore death is unchanged: one life, then spectator. There are no extra lives.
- Friends are mortal. They retreat when badly hurt, eat from their backpacks, and run from creepers. Healthy friends holding a tool stand together and fight off zombies and other close attackers, so nobody is picked off alone. Only Aegis goes after skeletons and other archers; everyone else gets out of their line of fire, and a badly hurt friend stays well out of bow range until they recover. Out of combat (no damage for 10 s, no target, not burning) they recover 1 health every 4 s. If they die, they drop a **backpack item** holding everything they carried and wore. It never despawns, and its coordinates go to chat. Use it (right-click) to unpack.
- A fallen friend's name returns as a newcomer after 3 in-game days. Set `deadFriendsReturnAfterDays` to `-1` in `config/hardcorefriends.json` for permanent loss.
- Zombies, skeletons, spiders, illagers and witches hunt friends just as they hunt villagers.

## 9. How friends treat your world

- **Building and landscaping** happen only inside the camp radius: 24 blocks, growing to 40.
- **Tree felling, quarrying and the staircase mine** happen only in a ring up to 48 blocks beyond the camp. Flint also mines ores that are already exposed, inside the camp or in that ring.
- Friends never break chests, furnaces, signs, beds or any other block entity, or anything crafted-looking: planks, doors, glass, torches, slabs, stairs, fences and so on. They also stay clear of blocks next to such things.
- Trees are felled only if they grew naturally (natural leaves), never log walls or trees inside camp. Quarry pits are 5×5 and at most 2 blocks deep. Mines stay inside one 24×24 area.
- Every change is logged (`/friends log`). Turn editing off entirely with `allowWorldEditing: false`, or individually with `allowTreeFelling`, `allowQuarrying` and `allowMining`.

## 10. Honest limits

- Friends only act while their area is loaded, which means near a player. They do not work while you are far away.
- They build from fixed blueprints, adapted to the wood you have. They do not invent new buildings.
- Pathfinding is vanilla mob pathfinding. On rough terrain a friend can get stuck. Stuck jobs time out and are retried later.
- Mining is one staircase mine plus exposed ores near camp. Only trees they can fully reach (about 6 blocks tall) are felled.
- The farm plot needs a fairly level spot inside camp: a level hole for the water with about 20 level grass or dirt blocks around it. Bumps are fine, but on a camp that is all slopes or rock Fern says she needs "a flat patch of grass", and the camp cannot grow past stage 2 until she gets one. Help her by levelling a patch, or simply pour water on level ground in camp: she farms around any water inside the camp.
- When the next camp step is waiting on something nobody can fetch (for example nether materials for Spark's lamp posts), friends with nothing useful left to do take a break near camp.
- Contraptions are a fixed set of vanilla redstone builds.
- Dialogue is pre-written and chosen by situation and personality. It is not free conversation.
- Purely natural-looking player builds (for example a hut made only of dirt or stone) cannot be told apart from terrain if they sit in the gathering ring outside your camp. Keep such builds inside the camp radius, or add any crafted block nearby.

## 11. Config (`config/hardcorefriends.json`)

`chatter`, `campRadius` (24), `maxCampRadius` (40), `resourceRadius` (48), `allowWorldEditing`, `allowTreeFelling`, `allowQuarrying`, `allowMining`, `deadFriendsReturnAfterDays` (3, or −1 for permanent), `followTeleportDistance` (48, 0 = off), `monstersTargetCompanions` (true).
