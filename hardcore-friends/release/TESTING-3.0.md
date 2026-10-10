# Hardcore Friends 3.0: what to test

**Nothing in 3.0 has been played yet.** It all compiles. Every part was reviewed, then reviewed again for how the
parts work together, and the problems found were fixed (the shops were checked for duplication too). The 54 building
plans were checked by script. But you are the first to try any of it in game. Below is what is most worth watching, area by area: what to do, what should happen, and
what would be a bug worth telling me about. The item numbers (4.3 and so on) make reports quicker.

The full details are in `docs/v3/`, each file ending with its **Honest limits**. Anything listed there is known, not a
bug, but tell me anyway if it spoils the game.

## Before you start

- **Back up first.** Copy your whole `saves` folder somewhere safe, and `config/hardcorefriends.json` too. 3.0 changes
  a world as soon as it loads it: everyone gets a family name, the Chronicle starts, and a camp that is already a
  Village gets a town plan straight away.
- **Install.** Import `Hardcore-Friends-3.0.0-CurseForge.zip` in the CurseForge app (Create Custom Profile → Import;
  it gets its own profile and saves folder), or by hand: swap the 2.0.0 JAR in `.minecraft/mods` for
  `hardcore-friends-3.0.0.jar` (never both), with Fabric API `0.162.0+26.3` and Fabric Loader 0.19.5 or newer (Java 25).
- **A fresh world first.** A new Survival world (not Hardcore, so one death doesn't end the test) with **Allow
  Commands** on, so you can pop into Creative to stock the chest and use `/locate`. Pick open plains with a river or
  lake close by and a forest within reach: plots need firm ground with no trees, the fisher and the well need water,
  and the builders need a lot of wood.
- **Then a copy of your main world** (with CurseForge, copy the world folder into the profile's `saves`). Check the
  friends are there with their backpacks, buildings already standing look exactly as before (they keep their old
  plans), newcomers keep their skins, `/friends chronicle` opens with a line about the camp as it is, and a camp that is
  already a Village (or more) gets its town plan at once: the quickest way to see the village part.
- **First checks.** The world loads, `logs/latest.log` shows no errors from Hardcore Friends, `/friends builds check`
  reports nothing skipped, and `/friends village`, `/friends senses` and `/friends calendar` answer without cheats.
- **Faster settings, test world only.** In `config/hardcorefriends.json` (edit it with the game closed: it is read at
  start-up), `relationshipSpeed` 4, `childhoodDays` 2, `daysBetweenChildren` 1 and `daysPerSeason` 3 bring romance,
  children, feasts and birthdays round in days, not weeks. Every world shares the file, so put them back (1.0, 10, 5
  and 10) before playing the copy of your main world.
- **Don't use `/time set`** where you test village life: the calendar counts the world's days, and setting the time
  can move the date. Sleeping is fine; the calendar carries on through nights you sleep through.

## A quick way to the Village

Finding the way, sprinting, friendships, the calendar and the Chronicle work from day one; the new cabin goes up once
the camp is a Hamlet. Much of the rest waits for the **Village**, the fourth stage. In the fresh world:

1. `/friends camp set`, put down a chest, `/friends chest`, and recruit all nine (2 common food each).
2. Stock the chest. A trip into Creative is fine, as the friends still build only from what is in it: stacks of logs
   and cobblestone, coal, sand, some iron, wool of one colour (or hay bales) for beds, seeds, a water bucket and plenty
   of food. The new cabin alone takes close to 80 logs' worth of wood.
3. Stay close: Unity grows by 1 a minute for each friend within 24 blocks of you (up to 120 a day), besides chats,
   finished buildings and high spirits. Sleep through the nights.
4. Follow it with `/friends camp` (what is built, what Oak is waiting for), `/friends unity` and `/friends plan`;
   `/friends jobs` lists what the camp is short of, and `/friends deliver`, standing near the chest, hands over what
   matches from your main inventory.

**Camp** needs the chest and campfire; **Hamlet** the crafting table, furnace, torch posts and farm plot, and Unity
100; **Village** the cabin, the camp paths and Spark's automatic door, and Unity 250 (the animal pen is optional).
1.0's test camp got there in two in-game days, but the new cabin is a much bigger build, so allow longer. No command
adds Unity or skips a stage. After the Village comes the Settlement (Unity 500), then a good while later the Town and
the City (see 4.7).

## 1. Building

- [ ] **1.1 The new cabin.** **Expect:** a cottage on a cobblestone plinth with a log frame, plank walls, a stair roof
  with eaves, seven windows (two at the front, one in each side wall, one at the back and one in each gable end; open
  until the camp has glass), lights and two barrels; friends sleep in it and Spark's door still works. **Bug:** roof
  gaps that never fill, blocks floating in mid-air, a door nobody can get through.
- [ ] **1.2 Scaffolding.** Watch any roof go up. **Expect:** a pillar of dirt or cobblestone (6 high at most) beside or
  inside the building, climbed, then dug out again with the blocks kept; one left when you quit mid-climb is taken down
  later. **Bug:** pillars left for good, a pillar against something of yours, a friend stranded on top.
- [ ] **1.3 Making materials.** **Expect:** glass and stone fired in the friends' own furnace, the builder going to it
  only between runs of building and topping up a furnace already at work with eight or more; sand and clay dug outside
  the camp as one-block dips in every other column, never by water; wild sheep sheared for wool. When a building runs
  short, gatherers bring what it lacks to the friend building it. **Bug:** your furnace used, the builder leaving a
  building half-way through to fire one block, a hole you can't step out of, your sheep (named, on a lead, or in a pen
  of your own) sheared. The shepherd shearing the camp's own sheep in its animal pen is meant to happen (5.7).
- [ ] **1.4 Decoration never holds a building up.** **Expect:** buildings finish without the glass, flowers, carpets or
  flower pots the camp can't make yet, and the repair job adds those later. A lantern the camp had no iron for becomes a
  torch, and stays a torch even once there is iron. In a building still waiting for glass or carpets, knock a torch off
  a wall: the repair job still puts it back. **Bug:** a building stuck unfinished for days over a carpet or flower pot,
  or a torch never put back.
- [ ] **1.5 Village buildings look right.** **Expect:** roofs with eaves, shuttered windows, lit rooms, the front door
  on the street with the ground at it within a step, a style that suits the land (spruce in the taiga, sandstone in the
  desert). **Bug:** a house facing away from the street, a door you can't walk up to, mobs spawning indoors.
- [ ] **1.6 Nothing on your builds.** Before the Village stage, put up a little shed of your own (a door, a chest) in
  open ground inside the camp. **Expect:** no plot within two blocks of it (three where ground is levelled), no
  scaffolding or digging beside it, nothing of it in `/friends log`. **Bug:** any block of yours changed or built on.
- [ ] **1.7 Plans kept with their sites** (only if you write plans). Change a library plan with a data pack (the format
  is in `docs/v3/architecture.md`) while a building from it is under way, then reopen the world. **Expect:** the
  building under way and any standing ones keep their old shape, beds and doors; only buildings started afterwards use
  the change. **Bug:** a building already begun reshaped, or its repair job changing it to the new plan.

## 2. Finding the way

- [ ] **2.1 Round, not through.** Watch friends on long walks (Rowan to the trees, anyone to a far job). **Expect:**
  round hills, not through the caves under them; back from cliff edges; drops of two or three blocks less favoured but
  allowed (even though they can't be walked back up), never more than three; never into a waterfall. **Bug:** a cave
  detour on a surface errand, or a drop of more than three blocks.
- [ ] **2.2 Lost in a cave.** `/friends follow <name>` deep into a dark cave, then `/friends work <name>` and go out of
  sight. Pick a cave in the gathering ring round the camp, which keeps running while you are away; for a cave further
  out, stay within render distance, as nothing out there runs once you have gone. **Expect:** they head for open sky
  (torches down if they carry any) or dig a staircase up; lost for about two real minutes in all (sooner if they are
  badly hurt or starving down there, or about to drown), they are brought home ("Rowan got lost in a cave and found the
  way home."), but never out of a fight or a job that is getting on (Flint's mine). **Bug:** no rescue, digging through
  anything built, dying without trying.
- [ ] **2.3 Shut in a pit.** Give a friend some dirt (right-click them with it), lead them into a hole two deep that you
  dug in natural ground outside the camp, `/friends stay <name>`, climb out, wait, then `/friends work <name>`.
  **Expect:** nothing while on stay; then a hop, a block or two underfoot, out, and the blocks taken back (or a
  staircase dug). **Bug:** blocks left behind, rescue or digging while on stay, still there after two minutes.
- [ ] **2.4 Running water.** For this one, quit the game, set `followTeleportDistance` to 0 and start again; afterwards
  quit again and put it back to 48 (the file is only read at start-up). Otherwise a stuck follower just catches up with
  you, which is right but hides the rest. Cross a fast river, then `/friends follow <name>`. **Expect:** they swim
  across against the current, climb out at a bank that leads somewhere, never build a bridge, and are brought to you if
  the river truly cuts them off. **Bug:** swept downstream.
- [ ] **2.5 Sprinting.** **Expect:** a real sprint (dust at their feet, about 30% faster) for anything more than 12
  blocks off, walking the last 5; none in water, when hungry, beside a long drop or near lava unless fleeing (sprint
  past a lava lake with followers: they walk). Children tire after ten seconds or so. **Bug:** sprinting off an edge.
- [ ] **2.6 Hearing.** In the test world, put a creeper behind a wall near a friend at work. **Expect:**
  `/friends senses <name>` lists it as heard, and a creeper hissing within 12 blocks sends anyone running, seen or not.
  **Bug:** walking round the corner into a creeper they had heard.
- [ ] **2.7 Caves under the village** (test world, once the village has grown). `/friends follow <name>` into a cave
  under the outer streets, then `/friends work <name>` and go out of sight. **Expect:** they get out as in the wilds,
  digging a staircase up through a garden or verge if need be (it is left there), but never through a building's site or
  the heart of the camp; a friend shut in a building or the pen only hops, tries the doors and is brought out. **Bug:**
  digging into a house or the pen, or digging anything the friends or you built.

## 3. People

- [ ] **3.1 Friendships.** **Expect:** `/friends relationships` showing scores rising for people who work, eat and
  chat together, and a word when two become good friends; quarrels only between two in a low mood. **Bug:** scores
  falling in a well-fed, rested camp, or never moving.
- [ ] **3.2 Going out.** **Expect:** "Fern and Oak are going out together. How lovely!" (or another pair), then a date
  each evening inside the camp (a sunset from high ground, the campfire, a walk) with hearts, over by nightfall; in a
  village, the date before the evening at home. Whoever keeps the night watch is never asked out, and a date ends when
  one of the two goes on watch. **Bug:** a date outside the camp, someone going out with two people, family courting,
  a block changed, the friend on watch off on a date.
- [ ] **3.3 The wedding.** A couple deeply in love, out together two days with three dates, may get engaged;
  `/friends couples` shows the day. **Expect:** next late morning a ring of guests at the camp centre (the town hall
  once there is one), vows, a toast, the sound of a bell, one family name; a monster about all morning puts it off a
  day. **Bug:** guests stuck in the ring, a wedding never held nor put off, the bell sending children indoors.
- [ ] **3.4 Babies.** `/friends couples` says what a married couple still lacks. Before village houses there must be
  room in the finished cabin (nine places for everyone; the second cabin adds none), so a camp with all nine friends
  waits for houses. **Expect:** news they are expecting, then a baby about a day later beside a parent, above ground;
  `/friends village` counts babies on the way towards the cap, and no newcomer joins in a baby's place. **Bug:** a baby
  in a cave or the mine, or past `maxPopulation`.
- [ ] **3.5 Childhood.** **Expect:** children a little over half size who play tag and hide-and-seek, follow a parent
  at work above ground (or go to school once there is a teacher), go home late afternoon (to beside their own bed in
  the family's house, or the cabin while the family has none) and stay in, and never change a block;
  `/friends follow all` leaves them out. **Bug:** a child down the mine, off out of the camp, through a portal, or
  crossing the village to the cabin at dusk when the family has a house.
- [ ] **3.6 A child in danger** (test world). **Expect:** a monster near a child sends them to a parent, a fighter or
  you; after dark a child already indoors stays in, running only to a grown-up in the same room; with nobody to run
  to, home, but only if home is further from the monster. Parents and Aegis go for it first. **Bug:** running towards
  it, or out of a house into the night.
- [ ] **3.7 Growing up.** **Expect:** after `childhoodDays`, full size, a grown-up's skin if theirs was a child's, a
  celebration and work at their trade; `/friends newcomers` says "born in the camp". **Bug:** stuck small or in a block.
- [ ] **3.8 Skins.** Meet newcomers in a few kinds of village (`/locate structure minecraft:village_desert` and so on).
  **Expect:** skins from the medieval village set, nobody alike while free ones remain, children only in children's
  skins, often one for their trade (builders as carpenters or masons) or land (about 2 in 5 in desert or snow). **Bug:**
  purple-and-black skins, see-through gaps, arms or armour out of line (slim and wide mixed up), needless repeats.

## 4. The village

- [ ] **4.1 The town plan.** **Expect:** at the Village stage everyone is told, and `/friends village` shows the High
  Street (east to west) and Market Street (north to south) crossing at the square round the campfire, with plots only
  on firm natural ground (`/friends village plots`). **Bug:** no plan, or a plot on water, fields, under trees or yours.
- [ ] **4.2 Streets.** **Expect:** Terra treads each street into a path (gravel from the Settlement when there is some,
  cobblestone from the Town), with lamp posts every 12 blocks; gaps by water, over hollows, near the mines and within
  two blocks of anything of yours are normal. She lights the whole village by day and only the heart of the camp after
  dark, and plants saplings well back from the streets. **Bug:** a path dug under a building or right beside your
  build, gravel falling through into a cave, or a sapling on a street or a plot's front.
- [ ] **4.3 Houses of their own.** **Expect:** `/friends home` lists every household; a small house for one, a spare
  bed for a couple, a bigger one for a family; everyone builds their own in spare time and Oak the rest, three at once;
  moving in once a house is (nearly) done with every light, the door, the beds and chests in (with no coal in the chest
  for torches, waiting in the cabin is normal); a wedding brings two households into one of their houses with a bed
  for each, or, when neither has one, everyone keeps their own bed until a house for them all is built. **Bug:** anyone
  in a house of yours, two given one bed, someone sleeping in a dark or doorless house, someone homeless beside an
  empty house.
- [ ] **4.4 Real beds.** **Expect:** at nightfall friends walk home, lie down in their own bed and get up at dawn; you
  can still sleep the night through; a bed with a friend in says "this bed is occupied", as with villagers. A zombie
  at the shut front door does not wake them; one with a way in does. **Bug:** a friend in a bed you are in, sleeping
  outside with their bed free, a bed still taken once everyone is up, a house woken over and over by a zombie at the
  door.
- [ ] **4.5 The day.** **Expect:** breakfast early and lunch at midday at their own table (now and then the tavern),
  work, then from a little before sunset home (or by the well without a house), sometimes round at a friend's, the
  guest walking home at sunset; nobody waits by the well under a thunderstorm's dark sky. **Bug:** nobody going home
  in the evening, or a guest crossing the village after dark.
- [ ] **4.6 Water.** With a bucket (or three iron) in the chest. **Expect:** the farmer fills the well's basin a bucket
  at a time, none spilling. The wheat field and the orchard are Town buildings, so those checks wait for the Town: the
  field tilled, sown and harvested, saplings in the orchard. **Bug:** water running onto the street.
- [ ] **4.7 Settlement, Town and City.** **Expect:** the Settlement at Unity 500 once the Village stage's camp buildings
  stand (storehouse, watchtower, lantern posts, drop-off hopper and farm fence). The Town at Unity 650 with 14 people, 5
  houses, the town hall, the well and one of tavern, market, school or chapel, and also the Settlement stage's own camp
  buildings: the second cabin, the auto-smelter and the flower gardens (the night lamp posts are optional). The City at
  800 with 22 people, 9 houses and more. Each stage is announced, the Town and the City with a celebration. From the
  Settlement on, `/friends village` shows the Unity, people, houses and civic buildings the next stage still lacks; the
  camp buildings are only in `/friends camp` ("Built:"), so check both. **Bug:** a stage reached without what it needs,
  or stuck with everything in both met.
- [ ] **4.8 Moving the camp** (a throwaway copy only). **Expect:** `/friends camp set` well away lets the plan go
  (everyone is told), the houses stay empty and a new plan starts; within three blocks the village stays and the camp
  keeps its size. **Bug:** anything knocked down.
- [ ] **4.9 The night watch over the village** (test world). Once houses stand on the outer streets, let a zombie in
  among them at night where the watcher at the campfire can see it (down an open street from the campfire), or wait
  for one that goes for someone. **Expect:** the watcher raises the alarm, the grown-ups asleep within 40 blocks of it
  get up and the armed ones go for it, while the far end of the village and the children sleep on (unless it could get
  at them). A lone monster hidden behind houses, going for nobody and not close to the watcher, is not noticed (a
  known limit: the watcher cannot see round houses). **Bug:** a monster in the watcher's sight, or going for someone in
  the village, never noticed, or the whole village woken for one at the far end.

## 5. Trades and shops

- [ ] **5.1 First trades.** **Expect:** at the Hamlet with six or more grown-ups, a cook at the campfire, a fisher on a
  bank, a stallholder at the supply chest and a shepherd at the pen (two or more sheep), each announced, with at least
  three grown-ups left free; `/friends trades` shows it. **Bug:** a child or stranger with a trade.
- [ ] **5.2 Workplaces.** **Expect:** about one a day asked for as the village grows (bakery, smithy, fishing hut...),
  its holder moving in, the fishing hut's deck facing the water. **Bug:** a finished workplace unused for days.
- [ ] **5.3 The trading screen.** By day, right-click a shopkeeper at their counter (`/friends shops` says where).
  **Expect:** the game's trading screen (sneak and right-click with an empty hand still opens the backpack; the status
  line needs a plain right-click away from the counter); shut at dusk, in a dark thunderstorm, or when the keeper is
  busy (they say so). **Bug:** trading at night, a screen that won't close, a crash.
- [ ] **5.4 Prices.** **Expect:** fixed villager prices (six bread for an emerald, a bed for three, twenty wheat bought
  for one), the same every visit; a shop never buys what it sells that day and always buys for less. **Bug:** a price
  that changes, or any way to make emeralds trading round in circles.
- [ ] **5.5 Stock and takings.** Put a few emeralds in the supply chest for keepers' empty tills. **Expect:** only what
  is on the shelves offered (the camp stall only what the camp can spare), what you sell going to the supply chest, at
  most 32 emeralds' worth bought a visit. The camp stall, and keepers restocking their shelves from the supply chest,
  never take the planks, torches or beds the buildings under way still need. Building stock already on a shop's own
  shelves is still sold while a building waits for it (a known limit: the builders never take from a shop's chests).
  **Bug:** an offer of something the shop hasn't got, or the camp stall selling (or a keeper taking from the supply
  chest for the shelves) building stock a building waits for.
- [ ] **5.6 No duplication (important).** Count a shop's stock and your emeralds, then open the screen and close it
  every way: Esc, walking off, dusk falling, Save and Quit; on a server, log off mid-trade and try two players at once.
  **Expect:** everything not sold back on the shelves, nothing more. **Bug:** any item or emerald from nowhere, or gone
  missing (a crash with a screen open losing it is a known limit).
- [ ] **5.7 Trades at work.** **Expect:** the baker baking, the mason at the stonecutter, the carpenter making stairs,
  the doctor bringing milk to a poisoned friend, the shepherd shearing over the pen fence, all from real ingredients
  and never past what the camp keeps back. **Bug:** your named or leashed sheep sheared, anything made from nothing.
- [ ] **5.8 The blacksmith.** **Expect:** the blacksmith makes the camp's gear when that is their most useful job, and
  nobody else starts it while they are at it or for a minute after; while they are mining, too weak or away, someone
  else makes the gear, as in 2.0. **Bug:** gear never made because the blacksmith is busy with other work.

## 6. Village life

- [ ] **6.1 The calendar and market day.** **Expect:** `/friends calendar` gives today's date (day 42, for example, is
  "Sunday, day 42: the 2nd day of spring, year 2" with the usual 10-day seasons, and "Sunday, day 42: the 3rd day of
  summer, year 4" with `daysPerSeason` 3) and the next ten days. On Saturdays, once there is a market or a shopkeeper,
  keepers call out at their counters and everyone else wanders over once. **Bug:** the date jumping or going backwards.
- [ ] **6.2 A feast.** For the winter feast, put eight lanterns (or 32 torches: only those beyond 24 are used) in the
  chest. **Expect:** from late afternoon everyone free in a ring at the square, a few words from Sage (or whoever has
  been there longest), a portion each from the cook for everyone a little hungry (a well-fed friend getting nothing is
  normal; never more than half the chest's food, none if the camp is hungry), music, home at nightfall; at the winter
  lights, up to eight lights round the square, gone next morning; the night watch stays on watch. Ring a bell during the
  feast: everyone takes cover or goes to their post, and once the all-clear comes the feast carries on. **Bug:** food
  taken from a hungry camp, lights left up, a feast that never ends, or one dropped because of the alarm.
- [ ] **6.3 Music.** Tuesday, Thursday, Saturday and Sunday evenings, with a note block in the chest (or eight planks
  and a redstone dust). **Expect:** a note block put down in the tavern (or at the square), two or three tunes you
  know, picked up again. **Bug:** a note block still there the next day, or music on the evening of a funeral.
- [ ] **6.4 A birthday.** With `daysPerSeason` 3 a year is 12 days, so birthdays come 12 days after someone joined (or
  after 3.0 first saw them). **Expect:** the news, a few friends wishing them well, and with plenty of food a cake from
  the chest (put one in) shared round, or a smaller present. **Bug:** a present that didn't come from the stores.
- [ ] **6.5 A funeral and grave** (only if someone dies). **Expect:** a gentle Chronicle line; a grave, usually by next
  morning, at a cemetery on level ground towards the camp's edge, with a headstone, a waxed sign ("In memory of", the
  name, the day, "Rest well") and flowers if the chest has them; the funeral the next evening (an evening lost to the
  alarm does not count against it); mourners visiting. **Bug:** a grave on a street, a plot or your build, or crowding
  another grave and the spot before it where visitors stand; anything later built over it.
- [ ] **6.6 The Chronicle.** Put a blank book and quill (or a book, an ink sac and a feather) in the chest. Books, paper
  and leather that Sage's plan is gathering for the library are left for it, so a ready book and quill is surest.
  **Expect:** `/friends chronicle` with lines for arrivals, weddings, births, buildings, stages, feasts and raids; the
  book in the chest, then on the town hall's lectern. Before taking the book, put a second blank book and quill (or what
  makes one) in the chest: the keeper writes a new copy from it. With nothing in the stores to make one, no new copy is
  normal. **Bug:** a book of yours used or changed, or the library's books taken for it.
- [ ] **6.7 A restart mid-feast.** Once the words have been said at a feast, Save and Quit and open the world again (on
  a server, restart it). **Expect:** the feast carries on straight away with the party, and at nightfall it goes in the
  Chronicle with its Unity (naming only those who came after the restart, which is a known limit). A funeral interrupted
  the same way, after its words, counts as held and is not held a second time. **Bug:** the feast or funeral lost, its
  words said again, or no Chronicle line.

## 7. Defence

- [ ] **7.1 Ring the bell yourself.** Put a bell in the chest (friends can't make one): it goes to the square (a few
  blocks from the campfire, off the line of the main streets), or to the town hall once one is planned. By day,
  right-click it. **Expect:** children and non-fighters go indoors and shut the friends' doors, fighters go to posts,
  and about 30 seconds after nothing is about, the all-clear; `/friends defence` shows it all. **Bug:** anyone running
  towards danger, a door of yours shut, an alarm past five minutes.
- [ ] **7.2 Monsters at night.** **Expect:** three or more monsters seen in the village, or a creeper at any hour, gets
  the bell rung by the nearest friend awake (never past a creeper), rested sleeping fighters up (40 energy or more; a
  tired one sleeps on, and one who wears out below 20 goes to bed for the rest of that alarm), anyone found asleep out
  in the open got up and taken indoors, and the all-clear by dawn. **Bug:** the bell ringing all night for a mob in a
  cave or pen, a friend sent past a creeper, or a tired fighter got up and sent back to bed over and over.
- [ ] **7.3 Guards.** **Expect:** once the village's own watchtower, gate or walls stand (Town and City buildings, not
  the camp's first watchtower), `/friends defence` names tonight's guards: two shifts of up to two, armed and rested
  (35 energy or more), never the night watch, an archer shooting from the lookout; a guard picked at dusk goes straight
  to their post, missing the evening at home or a feast; a guard relieved (hurt, below 20 energy, stuck) is not picked
  again that night. **Bug:** one friend on both shifts, or on the first shift and then the night watch's second half,
  or a guard stuck on the stair.
- [ ] **7.4 A raid** (test world first). Walk into the village with Bad Omen (an ominous bottle from a raid captain, or
  `/effect give @s minecraft:bad_omen` with commands on); the game may not count the camp as a village, so a game
  village within 96 blocks of the camp centre works too. **Expect:** with the Raid Omen in the friends' own village (the
  camp and 16 blocks round it), the alarm before the raiders arrive; with the omen kept to a game village, no early
  warning: the alarm comes once the raid there has started. Then a bell each wave, 40 Unity and a Chronicle line if it
  is beaten off. **Bug:** a friend hitting a villager or golem, children left outside.
- [ ] **7.5 Fire** (test world). Light something wooden of the friends' near the houses, and a block of netherrack.
  **Expect:** one or two grown-ups soon put the first out by hand, from outside the flames, and the repair job puts back
  what burnt; the netherrack fire is left alone. **Bug:** friends walking into fire, or breaking anything but the fire.

## 8. Pets and maps

- [ ] **8.1 Adopting.** Put raw cod, bones and some rotten flesh (or raw meat, for dogs' treats in 8.2) in the chest;
  wolves live in forests and taiga, stray cats near game villages or once the village has five or more beds close
  together. **Expect:** a pet tamed, named and announced (`/friends pets`); one each, eight in all, only with a home for
  it; no cat tamed while the camp is short of food. **Bug:** a pet of yours touched or led off.
- [ ] **8.2 Day and night.** **Expect:** by day it follows its owner about the camp, at night it sits by their bed, it
  gets a treat from the chest every other day (raw fish for a cat; rotten flesh, else raw meat, for a dog, never cooked
  food; while the camp is short of food, a dog only gets rotten flesh and a cat waits), and a dog fights monsters
  attacking its owner (never a creeper, nor one its owner would shoot). A pet at the animal pen never keeps the gate
  open. **Bug:** a pet teleporting across the world, sitting on the supply chest or lying on a bed or furnace for
  minutes, hurting anything but monsters, hurt by a friend, fed the cooks' meat or fish in a hungry camp, or the pen
  gate left open over a pet.
- [ ] **8.3 Scout's maps.** Put an empty map and an item frame in the chest (or eight paper and a compass; Sage's plan
  keeps the paper, leather and iron it is collecting, and what it has gathered). **Expect:** Scout draws the camp map as
  she walks, then hangs it in a frame inside the town hall (or, before there is one, inside the cabin). Before the cabin
  stands, or with no frame to be had, the map going into the camp chest is normal: she hangs it later, once there is
  room on a wall. Later trips bring maps of land further off, marked with places found (`/friends maps`). **Bug:** a
  frame on your build or outdoors, a map that never finishes.
- [ ] **8.4 Copies.** Right-click Scout holding an empty map, or `/friends map` within 48 blocks of her. **Expect:** a
  copy of the finished map for where you stand, made from real stock (or she says what is short). `/friends map` only
  by day (at night she says to ask in the morning, while your own empty map works at any hour), and on a shared world
  only for the owner and trusted players. **Bug:** a map from nothing, your empty map taken with no copy, or an
  untrusted player getting a copy from the camp's stock.

## How to report a problem

- **The log.** `logs/latest.log` in your game folder (`.minecraft`, or right-click the CurseForge profile and choose
  Open Folder). It is replaced each time the game starts, so copy it first; after a crash, add the newest file in
  `crash-reports/`. Chat, including the answers to `/friends` commands, is written into the log.
- **The commands.** Copy what they say: always `/friends camp` and `/friends list`, then whichever fit:
  `/friends senses <name>`, `/friends village`, `/friends home <name>`, `/friends couples`, `/friends family <name>`,
  `/friends trades`, `/friends shops`, `/friends calendar`, `/friends chronicle`, `/friends defence`, `/friends pets`,
  `/friends maps`, and `/friends log` if a block of yours changed.
- **A screenshot.** F2 saves one in `screenshots/`. Open F3 first so it shows your coordinates and the day.
- **A few words.** The item number, which world (fresh or the copy), what you did, what you expected, what happened
  instead, whether it happens again, and any settings you changed.
