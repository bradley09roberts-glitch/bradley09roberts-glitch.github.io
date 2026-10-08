# Beating the game: Sage's plan, deep mining, obsidian, enchanting, the anvil and brewing

In 2.0 the friends work towards beating the game, one step at a time. Sage keeps the plan, tells everyone the next
goal and cheers when a step is reached; the rest of the camp gets on with what the step needs: iron, diamonds from a
deep branch mine, obsidian, sugar cane, books, a library with an enchanting table, enchanting, mending gear at an anvil
and brewing potions.

## Sage's plan

The plan has eleven steps. The camp works on one at a time, in this order:

| Step | What it needs |
|---|---|
| 1. Settled | The camp has grown into a **Village** (stage 3). |
| 2. Iron age | An iron (or better) pickaxe for every miner, an iron sword for every warrior (at least one for the camp), and 10 iron ingots in stock. |
| 3. Diamonds | A diamond pickaxe in the camp. |
| 4. Enchanting | An enchanting table with **15 bookshelves** round it (the friends' library, or one you built near the supply chest). |
| 5. Nether ready | 10 obsidian (for a portal) and a flint and steel. Fire resistance potions if the camp can brew them (not required). |
| 6. Blaze rods | At least 7 blaze rods (powder counts as half a rod). |
| 7. Ender pearls | At least 12 ender pearls (eyes of ender count too). |
| 8. Eyes of ender | At least 12 eyes of ender (the friends aim for 15, for spares). |
| 9. Stronghold found | Finished by the expedition work. |
| 10. End portal open | Finished by the expedition work. |
| 11. Dragon defeated | Finished by the expedition work. |

- **What counts:** everything in the supply chest, plus everything the friends near you carry or hold.
- **Checks:** every 10 seconds. When a step is met, Sage (or, without her, the first friend in the roster) says so, a
  gold message tells everyone on the server, and the Unity bond grows by **40**. Then the next goal is announced.
- **`/friends goals`** shows the whole plan: which steps are done (and on which day), a checklist for the step in hand
  ("iron swords for the fighters: 1/1", "bookshelves round it: 9/15"...), the camp's experience level, and what the
  plan wants gathered right now.
- **Steering:** the plan says what the camp wants (diamonds, lapis, obsidian, books, paper, sugar cane, leather, a
  bucket, a flint and steel, bottles, nether wart, eyes of ender). The jobs below score higher the more is missing. In
  the iron age the camp's ore need rises, so the miners mine more iron.
- **Diamonds:** the camp keeps up to **7 diamonds** back (3 for the diamond pickaxe, 2 for the enchanting table, 2
  spare). Other spending (better armour or swords) only uses diamonds beyond that.
- **Setting:** `progressionGoals` in `config/hardcorefriends.json` (on by default). Off: no plan, no announcements and
  nothing wanted, so the friends do not go after diamonds, obsidian or books on their own. They still enchant, mend and
  brew if the stations and materials are there.

## The camp's experience

Friends have no experience levels of their own, so the camp shares one pool that works exactly like a player's
experience bar:

- **It grows** by the vanilla amounts when a friend mines an ore that gives experience (coal, diamond, emerald, lapis,
  redstone, nether quartz), takes smelted items out of the camp's own furnace (0.7 an iron or copper ingot, 1 a gold
  ingot, 0.35 cooked food, 0.1 glass), or lands the killing blow on a monster.
- **Levels** follow the player's formula: level 30 needs 1,395 points.
- **It is spent** like a player's: enchanting at the top of the table needs the pool at the offer's level (30 with 15
  bookshelves) and costs 3 levels; a repair costs its price in levels.

## Deep mining (Flint first; anyone who covers mining)

While the plan wants diamonds (or lapis for enchanting), and the miner carries an **iron or better pickaxe**, he digs a
**branch mine at diamond level** (about y = -58):

- **Where:** if his staircase mine near camp has reached its bottom, the deep stairs carry on down from its last step
  (at right angles to its corridor, so they cut through none of it). Otherwise a new entrance in the gathering ring.
  Everything stays inside the mine's 24×24 box.
- **Layout:** a 1-wide staircase with 3 blocks of head room, spiralling down one step at a time (never straight down),
  then a 2-high corridor with 2-high branches every 3 blocks, alternating sides, up to 16 long.
- **Safety, before every block:** the block and everything touching it is looked at. Water or lava there: the wall is
  left standing (the friends never open a block that touches either) and the work goes round: a stair turns, a branch
  ends. Lava found this way is remembered as a camp point of interest ("lava"), and the friend says so. Loose gravel or
  sand overhead is gone round, never dug under. Gravel that falls in is dug out again (a few times at most).
- **Holes into caves** round each new block are sealed with carried cobblestone or cobbled deepslate (bring some, or
  it comes from the digging). A block that opens into a cave too big to wall in is gone round instead. Missing floors
  are filled the same way. The miners may later dig their own seals back out, only deep inside their mine boxes.
- **Light:** a torch every 8 blocks.
- **Ores** showing in the new walls and ceiling (never the floor) are dug out on the way: diamond, redstone, lapis,
  gold, iron, coal, copper, emerald. They go into the backpack and from there to the chest.
- **Home again:** the miner stops and heads home below **60% health**, when hungry, with no food, no torches (or coal
  to make them) or a full pack, and in the late afternoon (the way back up is long). No new deep trip starts after
  mid-afternoon.
- If the stairs cannot get deep enough at a spot (lava or water all round), that mine is given up and a new one is
  started elsewhere. A finished deep mine is followed by a new one a day later, if diamonds are still wanted.

## Better pickaxes

The miner (or whoever covers mining) fetches a better pickaxe from the chest when there is one. Otherwise, once the
plan has got that far, he makes one at the camp's crafting table: an **iron pickaxe** from 3 iron ingots (from the
iron age on), and the camp's single **diamond pickaxe** from 3 diamonds (from the diamonds step on). In the iron age
Sage's workshop also makes an **iron sword** for the warriors (Aegis takes it from the chest).

## Obsidian

With the diamond pickaxe and a **water bucket**, the miner makes obsidian (the plan wants 14: 4 for the enchanting
table and 10 for a portal):

- **Where:** at still lava the camp knows of inside the gathering ring: lava Scout reported near camp, and lava the
  deep mine found. Lava in a cave the friends cannot walk to is left alone for a day.
- **How:** standing on dry ground with no lava touching their feet or head, they pour water on a lava source and scoop
  it back, so the source turns into obsidian and the bucket stays full. Then they mine the obsidian (about ten seconds a
  block), but only once nothing round it is liquid: lava sources touching it are turned to obsidian first; obsidian next
  to flowing lava or water is left where it is. Never the block under their own feet.
- **Rules (the `CAST` world-edit rule):** only a still source block of plain lava (never a cauldron, never lava pouring
  down into something), only in the camp's dimension and inside the gathering ring, never within 4 blocks of anything
  player-built. Only obsidian the friends made themselves is ever mined (if you break it, it is no longer theirs).
- **Buckets:** with no water bucket in the camp, Sage makes a bucket (3 iron). It is filled at a pool that tops itself
  up (two or more water sources beside it, solid ground or water under it), so the pool never changes.

## Sugar cane, paper and books

From the diamonds step on, while the library is not built, the plan wants **46 books** (3 for each of 15 shelves, 1 for
the table), so it wants paper, sugar cane and **leather**:

- **Fern grows sugar cane** on dirt, grass or sand right beside still water in the camp (beside the farm's water or a
  pond): she plants it, harvests the stalks above the bottom segment (the root grows again) and replants. Natural cane
  in the camp is harvested the same way.
- **The cane patch:** with nowhere to plant, she makes one: a one-block pool in a level patch of grass near the farm
  (tilled, dug out and filled from a water bucket, as for the farm's own pond; she keeps the empty bucket), with cane on
  its four sides. That ground is kept out of the tilling. At most two patches.
- **Sage's workshop** turns cane into paper (3 into 3) and paper and leather into books (3 paper + 1 leather), at the
  camp's crafting table, from the chest and back into it.
- **You provide:** at least **one sugar cane** to start (the friends never take cane from outside the camp), and help
  with **leather** if you can: books need 46, and leather only comes from the cows Fern butchers for food.

## The library and enchanting

All three work stations (the library, the anvil and the brewing stand) are only built once the camp is a Village.

- **The library** is a new camp building: an enchanting table in the middle of a 5×5 square ringed by 15 bookshelves
  (every edge block but the doorway), with the ring of air the table needs, and a torch on each corner shelf. Oak builds
  it through the ordinary building system with real materials (bookshelves are crafted from planks and books, the table
  from a book, 2 diamonds and 4 obsidian), once the plan has reached enchanting (or the camp is a Settlement) **and**
  everything it still needs is in the camp or can be crafted. If you already set up a table with 15 shelves near the
  chest, he does not build one.
- **Enchanting** is Sage's speciality. When the camp's experience is at the table's top offer (level 30 with 15
  shelves) and the chest holds **3 lapis**, she takes the best unenchanted gear from the chest (swords, armour,
  pickaxes, bows; diamond before iron; nothing worn past half its durability; **never anything you named**), and uses
  the top offer exactly as the enchanting screen would work it out (the table's shelves, the camp's enchanting seed,
  vanilla's enchanting rules). It costs 3 lapis and 3 levels; the gear goes back in the chest. She also clears any
  flower or torch the friends put inside the library's ring (it would block the shelves).

## The anvil

From the diamonds step on (or as a Settlement), once the camp has **41 iron ingots** (31 for the anvil, 10 kept), Oak
builds an **anvil** near the chest. The builder then mends worn iron and diamond gear from the chest (at least a
quarter worn) the way an anvil does for a player: each ingot or diamond mends a quarter of the item's durability, the
price in levels is its repair cost so far plus one per ingot or diamond, anything costing 40 levels or more is "too
expensive", and the item's repair cost rises the vanilla way. The camp's experience pays. Iron is only used beyond the
plan's 10, diamonds only beyond the ones kept back. The anvil wears like yours: a 12% chance each use to become
chipped, then damaged, then break; a broken anvil is built again when there is iron to spare. Named gear is never
touched.

## Brewing

- **The brewing stand** is built near the chest once there is a blaze rod to spare (after the eyes of ender the plan
  still needs).
- **Glass bottles:** Sage puts sand in the camp's own furnace (with coal) to make glass and makes 3 bottles from 3 glass.
  Bottles are filled at still water (that never uses the water up).
- **Nether wart:** with soul sand and nether wart in the chest, Fern sets a short row of soul sand on the grass near
  the farm (up to 6), plants wart on it, harvests it when ripe and replants.
- **Spark brews** at the friends' own stand with its real workings: water bottles in, nether wart to make awkward
  potions, then the reagent for what the camp wants most and has: **fire resistance** (magma cream, made from blaze
  powder and a slime ball) first for the Nether, then **healing** (glistering melon, made from a melon slice and gold),
  **regeneration** (ghast tear) and **strength** (spare blaze powder). Every step is checked against the game's own
  brewing recipes first. Blaze powder fuels the stand. Finished potions go to the chest on the next visit.

## Other crafting in Sage's workshop

Flint and steel (iron ingot and flint) for the Nether step, eyes of ender (ender pearl and blaze powder) for the eyes
step. Blaze rods are only ground into powder beyond what the eyes of ender and a brewing stand still need.

## Who does what

| Job | Speciality | Notes |
|---|---|---|
| `flint.deep_mine` | Miner | One friend at a time |
| `flint.obsidian` | Miner | One friend at a time |
| `flint.better_pickaxe` | Miner | Only the miner, or whoever covers mining |
| `fern.sugar_cane`, `fern.nether_wart` | Farmer | One friend at a time each |
| `oak.stations` (library, anvil, brewing stand), `oak.anvil` (mending) | Builder | One friend at a time each |
| `sage.workshop`, `sage.enchant` | Strategist | One friend at a time each |
| `spark.brew` | Redstone inventor | One friend at a time |

As with all work, the specialist does it first and others stand in when nobody of that speciality is working. None of
it happens at night.

## Commands

- `/friends goals`: the plan, the checklist of the step in hand, the camp's experience and what is wanted.

## Honest limits

- Nothing here has been tried in a game yet. Expect rough edges in timing and pathfinding.
- **Deep mining is slow.** The staircase from the surface to y = -58 is well over a hundred steps; a deep trip spends a
  lot of the day walking, and the miner turns back in the late afternoon. Diamonds come a few at a time.
- **Pathfinding into deep caves:** the friends seal holes into caves round their tunnels, but a cave opened by digging
  out an ore in the wall is not sealed, and the vanilla pathfinder may still route through a cave on the way to the
  work. Lava never flows in (the friends never open a block that touches it), but monsters can.
- **Obsidian needs known lava.** The friends only use lava Scout reported near camp or the deep mine found, and only
  lava they can walk up to on dry ground. Without such lava, the obsidian has to come from you.
- **Leather is the slow part of books.** 46 books need 46 leather; only Fern's butchering (for food) and your gifts
  provide it. Rabbit hide is not turned into leather.
- **Sugar cane needs a start:** at least one piece in the chest or growing in the camp. Cane growing inside the camp is
  treated like the camp's crops (its tops are harvested), as for any crop in the camp.
- **Flint** (for the flint and steel) is not mined on purpose; it comes from gravel the friends happen to dig, or from
  you.
- **Blaze rods, ender pearls, ghast tears, magma cream ingredients, soul sand and nether wart** come from the Nether
  and the End: from the expedition work or from you.
- **Experience from the furnace:** the camp's pool counts the friends' own smelting when they take it out of their own
  furnace; the furnace still stores its usual experience for a player who empties it later.
- **The library's ring** is only cleared of blocks the friends placed (a planted flower, a torch); anything a player
  puts there is left alone, and the table then has less power.
- **One enchant at a time:** Sage always uses the top offer of the table, so with 15 shelves the camp needs level 30
  in its pool before each enchant (3 levels are spent each time).
- **Potions** are brewed to drink (not splash or lingering), and not extended or strengthened with redstone or
  glowstone.
- The plan's iron sword and iron pickaxe crafting only covers the iron age; better armour and weapons belong to the
  combat work.
