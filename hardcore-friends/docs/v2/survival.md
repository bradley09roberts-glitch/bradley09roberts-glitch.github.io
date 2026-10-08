# Independence: living on, trips, room to build, staying alive and getting better

This part of Hardcore Friends 2.0 makes the friends independent. The camp keeps going while you are away. Friends go
on trips of their own, make room for a building when the camp is cramped, look after themselves when caught out at
night, and get better at what they do.

## The camp lives on while you are away

In vanilla Minecraft, the world only runs near players. Before this update, the camp froze as soon as you walked
off: crops stopped growing, nobody ate or slept, and building stopped.

Now, **while you are online anywhere**, even in the Nether or far away, the camp keeps running. That covers the camp
itself and its gathering ring (the camp radius plus `resourceRadius`, at most 10 chunks from the centre), in the
camp's own dimension. Friends work, eat, sleep and keep the night watch. Crops grow, and the furnace smelts.

- Monsters only spawn near players, so an empty camp gets no new monsters while you are away. Monsters that were
  already there keep moving.
- When nobody is online, nothing runs. The world sleeps as it always has.
- Set `keepCampLoaded` to `false` in `config/hardcorefriends.json` to turn this off.

A friend away on a trip also keeps the land around them running (a 3×3 area of chunks that moves with them). At most
`maxRoamingFriends` friends can do this at once (3 by default). This ends when they are back at camp, after a day and
a half at most, or when they die, are dismissed, follow you or change dimension. If you close the world while a
friend is out, they wake up where they were next time you play and carry on home.

## Trips

Friends go on trips only by day, when trips are on (`allowTrips`). They set off only when they are healthy, fed,
rested and carrying some food, and only if there is enough daylight to get there and back. On the way they turn back
if they get hurt, hungry or tired, if a storm starts, or if the evening is drawing in. They keep away from places
where a friend died lately and from known pillager outposts. Before leaving, they take a few dirt or cobblestone
blocks and a couple of torches from the supply chest, in case the night catches them out.

`/friends where <name>` and `/friends list` show what a friend is doing on a trip, for example "on a trip to the
village at 120 64 -340".

### Scout explores far afield

Once a day, when the camp is safe (no friend has died near it lately and there is no storm) and fed, Scout sets off on
a trip of up to 300 blocks. She picks the least explored of eight directions, and each trip in that direction goes
further than the last. She looks at the land loaded around her as she walks, and nothing is generated just for her to
look at. She records:

- villages (for trading)
- survivor camps (where newcomers live, when that part of the update is installed)
- ruined portals (obsidian)
- pillager outposts (danger: she turns back at once)
- desert and jungle temples
- new biomes

Everyone hears each find as she makes it, however far away you are, for example "New on the map: a village around
120 64 -340!". When she gets home, she sums up the trip. Finds are kept as camp points of interest and in a longer list
of their own. `/friends trips` shows them. Scout never changes a block on these trips.

Only Scout explores this far. Nobody stands in for her.

### Trading trips

When a village is known within 300 blocks of camp, a friend may take the camp's surplus there to trade. The village
can be one Scout found on a trip or one she saw near camp. Sage and Rowan are keenest, but anyone may go. One friend
goes at a time, at most once a day.

1. At the supply chest they pack up to four kinds of surplus, a stack of each at most:
   - wool, coal, sticks, string, feathers, flint, paper, clay, leather or rotten flesh
   - wheat, carrots, potatoes, beetroot, pumpkins or melons, but only when the camp has plenty of food

   They also take **up to 12 emeralds** from the chest.
2. They walk to the village and trade with each villager whose offers fit. This works exactly like your own trades:
   the villager's real offer and price, paid in full, and the offer is used up. First they sell surplus to villagers
   who buy it for emeralds. Then they buy what the camp is short of:
   - food, when the camp is short
   - glass, when the chest has little
   - arrows, when someone carries a bow
   - ender pearls, once the camp is a Village
   - enchanted books, once the camp is a Village and the chest has a plain book to pay with
   - one piece of iron gear, when the chest has little
3. They walk home and put everything they bought, and anything unsold, into the chest. They keep a little food for
   themselves.

Friends never steal and never harm a villager. They skip a village with a raid on or zombies about and keep away from
it for a day. They only trade with grown-up villagers who are awake and not already trading with a player.

**What you provide:** surplus in the supply chest, and emeralds if you want them spent. If you keep emeralds you do not
want spent, keep them somewhere other than the supply chest.

## Making room to build

Building sites still have to be on natural ground inside the camp, away from anything you built. When no flat, clear
spot exists:

1. **Felling trees** (as before): a spot with a few natural trees on it is chosen, and the trees are felled first.
2. **Levelling ground (new):** if even that fails, the friends choose the spot on uneven natural ground that needs the
   least digging and filling. A spot qualifies only if:
   - no point needs more than `maxGradeDepth` blocks (3 by default) dug down or built up;
   - everything to dig is natural earth, sand, gravel or stone, with no water or lava touching it;
   - nothing you built is within 3 blocks;
   - the ground in front of the door side ends up within a block of the new level, so friends can walk in.

   **Terra** levels the site (the builder helps, and anyone stands in if Terra is not around). She digs the bumps away
   from the top down and keeps the spoil, then fills the dips from the bottom up with dirt or cobblestone, using the
   spoil first. She takes a pickaxe and extra fill from the supply chest if she needs them. If she runs short of fill,
   she says so, and the camp's need for dirt goes up, so the quarry tops it up. The building waits until the levelling
   is done.
3. **Looking further (new):** if nothing fits even with levelling, the camp grows by 4 blocks and the search starts
   again, further out, up to `maxCampRadius` (40 by default). The friend says something like "No room for the cabin in
   camp. We'll spread out a little." The extra room belongs to this camp: moving the camp with `/friends camp set`
   starts again without it.

Set `allowTerraforming` to `false` to turn levelling off.

## Staying alive away from camp

### Night shelter

Sometimes a working friend is caught far from camp after dark: more than 48 blocks away, or getting no nearer home.
Instead of walking through the night, they get under cover:

- **into a hillside:** they dig two natural blocks out of a slope, step in and wall up the way in behind them;
- **down into the ground:** they dig two blocks down and put a roof over their head;
- **on open ground:** they build a 1×2 pillbox around themselves from the dirt or cobblestone in their backpack.

If they carry a torch, they put it inside. Then they sleep until dawn. In the morning they take back every block they
placed, put back the ground they dug (they climb out of a dug-down shelter the way players do, placing blocks under
themselves), and carry on home. Each shelter uses only dirt, cobblestone, stone and torches, right around the friend,
and digs at most two natural blocks. Friends never build a shelter within 6 blocks of anything you built.

### Out of reach when cornered

A friend might be badly hurt, with two or more zombies (or other hand-to-hand monsters) within 4 blocks, still being
hit while trying to fall back. If they carry at least 3 dirt or cobblestone blocks, they pillar up 3 blocks, the old
trick zombies cannot follow. They wait up there until the monsters have gone or the sun is up (at most a whole night),
then come down, taking the blocks back. They never do this when an archer, a spider or a creeper is about, in water,
or within 6 blocks of anything you built.

### Breaking a fall

A friend falling far while carrying a water bucket pours the water where they are about to land, just before they hit
the ground. Once down, they scoop it back up. They only do this onto firm ground in the open, never where water boils
away (the Nether), and never within 6 blocks of anything you built.

## Getting more skilled

Each friend has a level from 0 to 10 in each kind of work (farming, building, mining, exploring, redstone, guarding,
planning, landscaping and foraging) and in fighting.

- **Work** earns experience whenever a job of that kind is finished: 1 point for a quick job, up to 5 for a long one.
  Trips count as exploring.
- **Fighting** earns 1 point for each blow that lands on a monster and 5 for the blow that defeats it.
- The levels need 15, 60, 135, 240, 375, 540, 735, 960, 1215 and 1500 points.

What the levels bring:

- **Work speed:** up to 20% faster at level 10. A specialist always stays the fastest at their own work, so a
  practised stand-in gets close to the specialist's starting speed but never past it.
- **Fighting:** +0.2 attack damage and +0.4 maximum health per level (+2 damage and +4 health, two hearts, at level 10).

A friend who goes up a level says so, for example "Level 3 at farming. Slow and steady does it."

## Commands

All commands work without cheats and change nothing.

| Command | What it shows |
|---|---|
| `/friends skills` | Each friend's three best skills |
| `/friends skills <name>` | One friend's levels as bars, with points to the next level |
| `/friends trips` | Whether trips are on and the camp keeps running, who is away, and the places found |

## Settings (`config/hardcorefriends.json`)

| Setting | Default | What it does |
|---|---|---|
| `keepCampLoaded` | `true` | Keeps the camp and its gathering ring running while a player is online |
| `maxRoamingFriends` | `3` | How many friends on trips may keep the land around them running at once (0 stops trips) |
| `allowTrips` | `true` | Scout's far exploring and the trading trips |
| `allowTerraforming` | `true` | Levelling uneven ground for buildings |
| `maxGradeDepth` | `3` | The most a site may be dug down or built up, in blocks (0 turns levelling off) |
| `maxCampRadius` | `40` | Also how far the camp may grow to make room for a building |

## Honest limits

Nothing here has been run in the game yet. Everything below is untested.

- **Server load.** While you are online, about 120 chunks round the camp keep running even when you are far away,
  and up to 3 small areas round friends on trips. That is about what one extra player standing at the camp costs.
- **Monsters at camp.** An empty camp gets no new monsters, but monsters already there stay and keep fighting. Friends
  still die to them while you are away.
- **Walking far.** Friends walk using ordinary mob pathfinding, in legs of about 20 blocks. Big rivers, ravines,
  oceans or steep mountains can block a trip. The friend then turns back, or tries again later. If a trip has not
  finished after two days, it is given up.
- **Travel time.** How long a trip takes is estimated at about 2.4 blocks a second. If friends walk slower over rough
  ground, they may still be out at dusk, and then they build a shelter.
- **Trading.** Trading picks from what villagers already offer. A village without the right professions gives
  nothing useful, and friends do not cure, breed or protect villagers. Friends take up to 12 emeralds a trip from the
  supply chest.
- **Finds.** Scout only notices structures in the chunks loaded around her as she walks, so she can walk right past a
  temple 40 blocks to the side. Each biome is reported only the first time it is found. The camp's point-of-interest
  list is short (64 entries), so trip finds are also kept in a longer list of their own (96 entries).
- **Levelling.** Levelling only happens for single buildings with foundations (not torch or lantern posts). It needs
  the ground in front of the door side to be near the new level. The site search can take a minute or two on a big
  camp, and growing the camp starts the search again.
- **A bigger camp.** Growing the camp radius also moves the gathering ring outwards and lets building and
  landscaping happen in the larger area. Quarries that are now inside the camp are no longer used.
- **Shelters.** A shelter needs natural dirt or stone to dig into, or enough dirt or cobblestone in the backpack for a
  pillbox (up to 9 blocks). Trips pack 8 blocks if the chest has them. Friends who are not on a trip carry blocks only
  if they happen to have some. A friend with neither walks home in the dark as before. Climbing out of a dug-down
  shelter uses a jump-and-place trick. If a jump falls short three times, the friend is lifted the last bit.
- **Pillars.** "Cornered" is a guess: badly hurt, two monsters close and two blows taken lately. A friend may pillar up
  when running would have worked, or not pillar up when it was needed. A friend who dies on a pillar leaves its blocks
  standing.
- **Water landings.** These are rare: friends seldom fall far, and must be carrying a water bucket. If they land more
  than 5 blocks from the water (pushed sideways), the water source is left where it is.
- **Skills.** Experience counts finished jobs, not effort, so a job that is cut short earns nothing. Levels never go
  down, and a friend who dies loses theirs.
- **What friends cannot tell apart.** Purely natural-looking blocks you placed (dirt or stone) inside a site being
  levelled cannot be told from the ground, and may be dug away.
