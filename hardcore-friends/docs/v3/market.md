# Shops and trades: village professions, workplaces and trading with the friends

As the camp grows into a village, the grown-ups take up **trades** besides their speciality: a baker at the bakery, a
fisher on the deck of the fishing hut, a shepherd for the flock, a mason cutting stone for the builders, a teacher at
the school, a doctor at the clinic, a shopkeeper behind the counter of the general store, and more. Shopkeepers trade
with you across the counter through the game's own trading screen, for emeralds, at fair prices that never drift. Every
trade uses real materials from the camp's chests, and every shop sells only what the village really has.

## The trades

| Trade | Where | What they do |
|---|---|---|
| **Baker** | bakery (`shop:bakery`) | Bakes bread from wheat, cookies (wheat and cocoa), pumpkin pies (pumpkin, sugar, egg), cakes (milk, sugar, egg, wheat; the buckets come back), baked potatoes (in the oven, with fuel) and grinds sugar from spare sugar cane. Keeps the bakery's shelves and sells. |
| **Shopkeeper** | general store (`shop:general`), a market stall (`civic:market`), or a stall at the supply chest | Keeps shop: sells the village's spare bread, potatoes, apples, torches, planks, logs, cobblestone, sticks and coal, and buys what the camp is short of. |
| **Butcher** | butcher's shop (`shop:butcher`) | Cooks raw meat in the shop's smoker and sells cooked meat; buys raw meat when the camp is short of food. |
| **Fishmonger** | fish stall (`shop:fishmonger`) | Fishes like the fisher, and sells raw and cooked fish; buys fish when food is short. |
| **Tailor** | tailor's shop (`shop:tailor`) | Makes carpets and beds (wool of one colour each) for the builders, keeps a few on the shop's shelves, sells wool, carpets and beds; buys string and wool when the builders need wool. |
| **Blacksmith** | smithy (`workplace:blacksmith`) or smith's shop (`shop:smith`) | Takes over the camp's smith work (the gear the friends need): while a blacksmith is at work, nobody else does it. At the smith's shop they also make plain iron tools and armour for sale when the camp has 40 or more iron ingots, and sell them; the shop buys coal and iron. |
| **Fisher** | fishing hut (`workplace:fisher`), or the nearest bank | Fishes by day with a rod (from the chest, or made from three sticks and two string), from the hut's deck or a dry bank by open water: a bite every five to thirty seconds, the game's own fishing catch (mostly fish, now and then junk, never treasure). The fish go to the supply chest for the cooks. |
| **Mason** | mason's yard (`workplace:mason`) | Cuts what the builders are short of at the stonecutter, at its better yields (a block makes a stair or two slabs): stone bricks, and stairs, slabs and walls of cobblestone, stone, stone bricks, bricks and sandstone, and cut sandstone. |
| **Beekeeper** | apiary (`workplace:beekeeper`) | Puts beehives on the apiary's hive stands (a hive from the chest, or made from six planks and three honeycomb), leads wild bees home to them holding out a flower, and takes the honey from full hives: honeycomb with shears, or honey bottles with glass bottles. Only ever with the campfire lit beneath, so the bees stay calm. A bee is only led away if it has no home or lives in a wild nest (one a tree grew with, on a natural tree's trunk, with nothing built near it): never your hive's bees, nor those of a nest you moved with Silk Touch. |
| **Carpenter** | carpenter's workshop (`workplace:carpenter`) | Makes the wooden parts the builders are short of at the workshop's table: stairs, slabs, doors, trapdoors, fences, gates, pressure plates, ladders, chests, barrels and composters. |
| **Doctor** | clinic (`workplace:doctor`) | Gives a poisoned or withering friend a bucket of milk; gives a badly hurt friend who is not mending a potion of regeneration or healing, or (when very badly hurt) a golden apple. Only real remedies from the clinic's chests or the supply chest, and only once nothing has attacked the patient for five seconds (poison, withering and hunger do not count as attacks). |
| **Shepherd** | shepherd's hut (`workplace:shepherd`), or at the pen | Shears the camp's own sheep in the animal pen, over the fence (never going in, never opening the gate); the wool goes to the supply chest. |
| **Farmer** | farm shed (`workplace:farmer`) | Composts the camp's surplus seeds, saplings and leaves in the shed's composter and takes the bone meal to the chest for the farm. |
| **Teacher** | school (`civic:school`) | Teaches in school hours while there are children: the children go to school and learn their own trade faster than by watching a parent. |
| **Innkeeper** (the **cook** at the camp) | tavern (`civic:tavern`), or the camp's campfire | Cooks raw meat and fish (with fuel), bakes potatoes, makes mushroom stew and beetroot soup (and the bowls for them). Everything they make goes to the supply chest, where everyone eats from. |

Trades are **extra work**: a friend with a trade still does their speciality, and their speciality comes first when
the camp needs it. Trade work sits in the ordinary work band (about 40 to 60), so needs, the night watch and
emergencies always win, and nobody works a trade at night. Practising a trade trains its kind of work (a baker gets
better at farming work, a mason at building); a shopkeeper learns from the trades they make, not from waiting at the
counter.

### Who gets which trade

Sage (or, without her, the camp itself) gives each workplace to the grown-up who suits it best: their speciality and
interest, their skill in that kind of work, newcomers before the nine (a newcomer shares a speciality with one of the
nine, so their time is freest), and never the last friend of a speciality if anyone else will do. Fighters are left to
keep watch where possible. Everyone is told when someone takes up a trade. Children and strangers never hold a trade,
and at least three grown-ups (`friendsWithoutTrade`) are always left without one, so the camp's own work never runs
short of hands.

**Before there are workplaces**, a camp that has grown to a Hamlet and has six or more grown-ups gives a few trades
plainly: a **cook** at the campfire, a **fisher** on the nearest bank (if there is open water near the camp), a
**stallholder** at the supply chest and a **shepherd** at the pen (with two or more sheep in it). When the matching
workplace is built, they move into it.

A trade is given up when its holder dies, is dismissed, has not been in the camp's world for two in-game days, or their
workplace's site is let go.

## Workplaces

The workplaces are buildings from the building library (see `architecture.md`): the bakery, general store, butcher's,
fish stall, tailor's, smith's shop, fishing hut, smithy, mason's yard, apiary, carpenter's workshop, clinic, shepherd's
hut, farm shed, school, tavern and the market square's four stalls. The village's town plan decides where they go;
the market asks it for the next one about once an in-game day, as the village grows (only while there are fewer
workplaces than about half the grown-ups, counting those already asked for and not yet begun, and only what suits the
camp: a fishing hut where there is water, a school once there are children, a smithy or mason's yard once there is iron
to spare). A building counts as ready once it is
finished, or 95% built with the last few blocks left to the repair job.

Each trade works at its building's work station (the plan's `job` spot), shopkeepers stand at the `counter`, and shops
keep their stock in the building's own chests. Ingredients come from the supply chest, never more than the camp keeps
back (seed potatoes, sugar cane for Sage's paper, planks for building, iron for gear). A work station (the oven, the
stonecutter) is only stood beside, never changed.

## Shops: trading with the friends

**Right-click a shopkeeper standing at their counter by day** to open the trading screen. Anyone may trade, whether or
not the camp's owner trusts them: buying and selling at a shop only moves the shop's own stock and takings, at fixed
prices, so it is open to every visitor. (Sneak and right-click for the usual status and backpack.) A keeper serves one
customer at a time; when the shop is shut, or the keeper is busy elsewhere, they say so. Shops keep the trades' hours:
they shut at dusk, when the friends head home (an open trading screen closes then too), and while a thunderstorm makes
it dark enough for monsters.

- **What they sell** is what the shop really holds: the bakery's own bread and pies, the butcher's cooked meat, the
  tailor's carpets and beds, the smith's tools (priced by how worn they are). A stallholder at the camp sells only what
  the camp can spare: food only while the camp has plenty (and then half of it at most), building stock above what the
  camp keeps (64 planks and torches, 128 cobblestone...).
- **What they buy** is what the camp is short of right now (wheat and other crops, meat or fish when food is short, coal,
  iron, logs, cobblestone, string and wool for the builders), paid from the shop's takings, at most 32 emeralds' worth a
  visit. Shops never buy back what they are selling that day, and always buy for less than they sell.
- **Prices** are the game's own villager prices where it has them (six loaves for an emerald, a bed for three, twenty
  wheat bought for one) and never change.
- **Nothing is made from thin air.** When the screen opens, the goods on offer are taken out of the shop's chests and
  held for the sale; when it closes (you shut it, walk off, log off, night falls, the keeper is called away, the server
  stops), everything not sold goes back. What you buy is what was on the shelf; what you sell goes into the supply chest.
- A shopkeeper goes to the counter when a player comes up to it (within 8 blocks), stays while someone is that near
  (three minutes at most, unless serving, so a player busy beside the counter does not keep them from their work), and
  minds it when they have nothing else to do. They keep the shop stocked from the supply chest (only what the camp can spare), take food back to the
  supply chest when the camp is hungry, and take takings above 48 emeralds to the supply chest (for the camp's own trading
  trips), or bring a few to an empty till.

## Commands

- `/friends trades`: who holds which trade, where, and what they are doing; workplaces still waiting for someone; what
  the village has been asked to build.
- `/friends shops`: each shop, its keeper, where it is, whether it is open, and its trades so far.
- `/friends jobs` also lists the village's trades, and a friend's status line (right-click) shows their trade after
  their speciality: "Mabel (Forager, Baker)".

Both are read-only and work at permission level 0 with cheats off.

## Settings (`config/hardcorefriends.json`)

| Option | Default | What it does |
|---|---|---|
| `villageTrades` | `true` | Grown-ups take up trades. Off: nobody works a trade (trades already given are kept for when it is on again). |
| `playerShops` | `true` | Shopkeepers trade with players. Off: shops make and stock things but do not trade. |
| `friendsWithoutTrade` | `3` | Grown-ups always left without a trade (0 to 20). |
| `requestWorkplaces` | `true` | The market asks the village's town plan for workplaces as the village grows. |

## For other packages

- `civic.Professions` answers a friend's trade id (`baker`, `teacher`...), where they work (their workplace's job block;
  for the teacher the school's `teacher` spot; for a trade held at the camp, its camp spot) and a trade's title.
- The market finds workplaces through `architecture.Construction` (any finished library site of a trade's kind,
  whoever built it), and asks for new ones through `village.VillagePlan.requestBuilding(server, kind, reason)`
  (`market.VillageLink`). With the town plan off (`villageHomes: false`), nothing is asked and trades work plainly at
  the camp.
- `TaskScheduler.JOB_FILTERS`: a keeper serving a player does only `market.keep_shop` and their needs; once a blacksmith
  is at work, `combat.smith` is theirs alone.
- The edit guard's FARM rules allow two more changes, only on the friends' own blocks: filling and emptying their
  composter, and taking the honey from their beehive (same hive, same facing, less honey).

---

# Honest limits

- **Nothing here has been run in a game yet.** It compiles, and every part was re-read for crashes, duplication and
  griefing, but how it plays (paths to counters and decks, how often trades are worked, prices) is untested.
- **Trading screen.** The game's screen needs a trader entity, so each visit uses a marker entity that is never added to
  the world. The goods held for a visit live only in memory while the screen is open: if the server crashes (not a
  normal stop) with a screen open, those goods and up to 32 emeralds are lost (never duplicated). Paying a shop with a
  renamed emerald, or selling it renamed goods, turns them into plain ones.
- **Shop stock in two places.** Food on a shop's shelves is not eaten by the friends (they eat from the supply chest)
  until the shopkeeper takes it back, which they do when the camp is hungry; shelves only take food while the camp has
  plenty, and while it has not, the food on them does not count towards what the baker and butcher make, so they bake
  and cook for the supply chest. Friends' food counts (`/friends plan`) do not include the shelves. Only shops have
  shelves: the tavern's cooking all goes to the supply chest.
- **Stalls at the camp** trade straight from the supply chest; a player who is not trusted can therefore buy the camp's
  spare goods there. That is by design, and the camp keeps generous amounts back, but switch `playerShops` off on a
  server where that is unwelcome.
- **The blacksmith** takes over the camp's smith work, but it is still done at the camp's crafting table (the combat
  package's job), not at the smithy. The doctor does not brew; potions come from Sage's brewing (the progress package).
- **The farmer** composts; they do not grow pumpkins, melons or beetroot of their own.
- **The fisher** never catches treasure (it needs a real bobber in open water); their catch is fish and some junk.
- **Bees.** Beehives need honeycomb (or a hive) from you to start: the friends never take honey from a wild nest.
  Leading a bee home steers it like the game's temptation does; a bee can lose interest and wander off, and is tried
  again later. Hives fill with honey only as fast as the bees make it. Nothing records who placed a block, so a wild
  nest is told by how the game grows them (facing south, against a natural tree's trunk, nothing built near): a nest
  you moved onto a tree in the open, facing south, would pass for a wild one; one in or near your base would not.
- **The shepherd** calls sheep to the fence and shears across it; a sheep that will not come is left for later. A
  player's sheep in the pen (named, leashed or owned) is never sheared.
- **Workplaces depend on the village's town plan** to be placed. Without it, the market's buildings only appear if
  something else builds library plans of those kinds, and the trades work plainly at the camp (cook, fisher, stallholder,
  shepherd), and only from the Hamlet stage with six or more grown-ups.
- Assigning trades happens every ten seconds in the camp's world; a friend away on an expedition keeps their trade for
  two in-game days.
- The tailor makes beds and carpets only of one colour each (the camp's crafting rules), and does not dye.
