# A proper village: streets, houses of their own, real beds, a daily routine, and a Town and a City

Once the camp becomes a **Village** (the fourth stage, Unity 250), the friends stop living round a campfire and start
living in a village. They draw up a town plan, lay streets, and build a house for every household: a single friend, or
a married couple with their children. They sleep in their own beds, go home in the evening, ask each other round, have
breakfast and lunch at their own table, and the village keeps growing: a well, a town hall, a tavern, a market, a
school, a chapel, farms, lamp posts, benches and gardens, and in the end a town gate with walls. Two new stages follow
the Settlement: the **Town** and the **City**.

Nothing here has been run in game yet. See **Honest limits** at the end.

## The town plan

When the camp reaches the Village stage, the friends lay out a town plan round the camp centre (everyone is told,
and `/friends village` shows it):

- **Streets.** A grid of six streets: the **High Street** runs east to west through the camp centre, **Market Street**
  north to south, and four lanes 34 blocks out (**North Lane**, **South Lane**, **West Lane**, **East Lane**). The two
  main streets cross at the **square**, the open ground round the camp's campfire.
- **Plots** line both sides of the streets. Every building faces the street it stands on, its front door opening onto a
  one-block verge beside the three-block-wide street. Plots are sized from each building's own plan, so a cottage takes a
  small plot and the town hall a big one, and plots never overlap each other, the camp's own buildings, the mines, the
  square or a street. A street only opens (and gets laid) as far as its plots reach, so the village grows outwards from
  the square, street by street. Everyone is told when a new street opens.
- **The ground.** The streets follow the land as it lies. A plot is only taken on firm natural ground, never on water, a
  field, a player's floor or under trees, and never within two blocks of anything you built (three where the ground has
  to be levelled). Gently uneven ground is levelled first by the levelling job (the survival part of 2.0: bumps dug,
  dips filled, at most `maxGradeDepth` blocks); one-block dips are filled with foundations. The ground in front of every
  door must be within a step of the floor, so everyone can walk in. Hills, lakes and your builds simply leave gaps in
  the rows.
- **The camp grows with its village.** The camp's radius (the area the friends work in, and the edit rules' camp zone)
  grows to cover every plot and street, up to `villageRadius` (64 blocks by default).

## Houses of their own

- **Every household gets a house.** A single friend gets a small one (the oak hut, a cottage); a married couple one
  with a spare bed for a baby; a family one with a bed for everyone. The plan is picked from the building library's 19
  houses by the beds it needs and the style that suits the land and the wood in the camp's chest (spruce cabins in the
  taiga, sandstone houses in the desert, oak cottages on the plains...).
- **Built with real materials** by the friends themselves, through the same building job as the camp's own buildings
  (planks, logs, stone, glass, beds, doors and the rest fetched, crafted and placed through the edit guard, scaffolding
  for the roofs). **Everyone builds their own home in their spare time**; Oak (the builder) puts up everyone else's
  buildings and anyone's house. Up to `villageBuildsAtOnce` buildings (3) go up at once, one builder on each.
- **Moving in.** As soon as a house stands its household moves in, each to a bed of their own, children in their
  parents' house. A house counts as standing once it is finished, or as good as: 95% of it built with every light, door,
  bed and chest in (a ridge slab just out of reach never keeps anyone out, but nobody sleeps in a house that is dark
  inside or has no door); the builder's repair job adds the rest later. Torches the village's unfinished buildings
  still need stay in the chest: the landscaper lights the camp only with the ones to spare.
- **Growing families.** A wedding brings two households together in one of their two houses with a bed for each of
  them, the bigger one when both have room (the other goes to whoever needs one next). When neither has (two friends
  from one-bed huts, say), everyone keeps their own bed and a house with room for them all is built; they move in
  together as soon as it stands. A baby takes the spare bed; a family that has outgrown its house gets a bigger one
  built, moves in as soon as it stands, and its old house goes to someone else. Someone whose marriage ends gets a home
  of their own again (a child who lives with them keeps their bed in the old house until it stands, then moves in with
  them). Someone who dies or is dismissed moves out, and a house hardly begun (less than a tenth of its blocks placed)
  for a household that is gone is given up.
- **Newcomers and grown-up children** count as households of their own and get houses too. A child who grows up keeps
  their bed in their parents' house until a house of their own stands (an empty one, or one built for them), then moves
  out; their parents have room for another baby again once they have gone. A grown-up child who marries and still
  lives at their parents' only moves to a house with a spare bed for a baby (built for them, or an empty one).
- **Never a player's house.** Friends only live in the houses they built on their own plots.

## Real beds

A friend with a home sleeps in **their own bed** there: they walk home, lie down in it as villagers do (the bed is
marked taken while they sleep in it), and get up at dawn, when hurt or when the watch raises the alarm, exactly as
before (hunger pangs, which the game counts as a hurt, wake a starving friend only for a moment: they lie straight
back down). Without a home (or while their bed is missing, or a player is asleep in it) they sleep as before: in the
cabin, or round the camp centre. Players can still sleep through the night as usual: only players count for that, and
a friend never takes a bed a player is lying in. The night watch is kept as before; the friend on watch stays up.

## The day

- **Up at dawn**, and **breakfast** (until time 1500) and **lunch** (around midday, 5500 to 7000) at their own table
  (or, now and then, at the tavern, once the village has one), when a little peckish and carrying food. A friend who is
  hungry still eats wherever they are, as before.
- **Work** through the day: the camp's jobs, the village's buildings, the streets.
- **The evening** (from time 11000 until nightfall): everyone puts the day's work down and goes home, or to the square
  by the well if they have no house yet, to be with their family and neighbours (not under a thunderstorm's dark sky,
  when monsters can spawn in the open). Now and then a friend asks someone round for the evening, and the guest comes
  to theirs until sunset (time 12000), then walks home while it is still light. Building in a hurry, the night watch,
  a hungry friend's meal and every danger come first.
- **Bed** in their own house at nightfall.

Children keep their own day (the people part of 3.0): play, lessons, an early night at home in their own bed.

## Streets, water, fields and the square

- **Terra lays the streets** with a shovel: the open stretch of every street becomes a trodden path. From the
  Settlement on the paths are surfaced with gravel (when the camp has some), and from the Town on with cobblestone, from
  the chest's stock. Only grass, dirt and the friends' own path blocks are touched, never under a building, never by
  water, never within two blocks of anything you built (a lawn by your house, a garden, a yard), never over a hollow (the roof of a cave, an overhang: gravel would fall through and leave a hole), and not
  within three blocks of the friends' mines, whose stairways run just under the ground (the street has a gap there).
  **Lamp posts** go up on the verges every 12 blocks, beside the stretches with something built.
- **Water.** Building plans cannot pour water, so once a well, the fountain or the wheat field stands, the farmer pours
  it: into a dry `water` spot of the basin, one bucket at a time, and the water runs on along the basin by itself; spots
  it cannot reach get a bucket of their own. Only into a basin that holds water (solid under every spot and solid or
  more basin round it), so not a drop can run out. The bucket is filled at a pool that tops itself up at once, or the
  camp's water bucket is used. The camp needs a bucket (three iron) for this. A basin the farmer cannot fill (no such
  pool nearby) is left for five minutes while the rest of the grounds (the field, the orchard) are tended.
- **The wheat field**'s soil is tilled where water is near, sown (wheat first) and harvested by the farmer; the
  **orchard** gets a sapling on each of its tree spots.

## What the village builds

| Stage | What goes up besides the houses |
|---|---|
| Village | the town plan, the streets, the **well** in the square, two benches, lamp posts |
| Settlement | the **town hall** (weddings move there), the **market**, the **tavern**, a flower garden, a signpost |
| Town | the **school** (the people part's children go there when the village has a teacher), the **chapel**, the **wheat field**, the **orchard**, the **watchtower**, a fountain |
| City | the **town gate** across a main street at the edge of the village, with two lengths of wall each side of it, and the **barn** |

Shops and workplaces (the bakery, the smithy, the fishing hut, the general store...) are built when the market part of
3.0 asks for them; the fishing hut goes on the bank with its deck facing the water. Kinds the building library has no
plan for are skipped.

## Growth: the Town and the City

Two new stages after the Settlement:

| Stage | Unity | People | Houses standing | Civic buildings standing |
|---|---|---|---|---|
| Town | 650 | 14 | 5 | town hall, well, and one of tavern, market, school or chapel |
| City | 800 | 22 | 9 | town hall, well, tavern, market, and two of school, chapel, watchtower or town gate |

People means everyone on the team: the named friends, newcomers, children and people grown up in the camp. Everyone
is told when the village grows, and a friend celebrates. Finished civic buildings, shops and workplaces give the camp
Unity (+15, the town hall +30), and new houses a little (+10, at most 40 a day).

**Population cap.** The village never grows beyond `maxPopulation` people (30 by default, a setting of the people part):
no baby is born and no newcomer joins beyond it. Babies on the way count towards it, so a newcomer never takes the
place of a baby already expected.

## Commands

All work at permission level 0 with cheats off and change nothing.

| Command | What it shows |
|---|---|
| `/friends village` | the village: people, babies on the way (and the cap), homes and other buildings, the streets and their surface, the plots (what stands on each, who lives there, how far along a building is by the blocks placed, and who it is for), what is being looked for, what other parts asked for, and what the next stage still needs |
| `/friends village plots` | every plot, lamp posts included |
| `/friends home` | where everyone lives, household by household (or why someone has no home yet) |
| `/friends home <name>` | one person: their house and street, their bed, and who they live with |

`/friends camp` also shows a line about the village.

## Settings (`config/hardcorefriends.json`)

| Setting | Default | What it does |
|---|---|---|
| `villageHomes` | `true` | Lay out the town plan at the Village stage and build homes; `false` keeps the camp as it was (no new plots, no Town or City). Houses already built are still lived in. |
| `villageRadius` | `64` | How far from the camp centre the village's streets and plots may spread (from `campRadius` to 96). The camp grows with them. |
| `villageBuildsAtOnce` | `3` | Most village buildings under way at once, besides two pieces of decoration (1 to 8). |

`maxPopulation` (people part) is the village's population cap; `maxGradeDepth` and `allowTerraforming` (survival part)
govern levelling plots; `allowScaffolding` (architecture part) high building.

## For the other packages

`village.VillagePlan`:

- `requestBuilding(server, kind, reason)` and `requestBuilding(server, kind, reason, count)`: ask for one (or `count`)
  buildings of a library kind (`shop:bakery`, `workplace:fisher`, `civic:school`...; a kind without a colon means any of
  its sub-kinds). Asking again changes nothing. Returns false only if the library has no plan of that kind. Requests are
  built after houses for households with none and the well, and before the stage's own civic buildings. A request that
  finds no plot waits five minutes under the kind asked for (`shop` as a whole, for a kind without a colon), so the
  civic buildings and the rest go ahead meanwhile.
- `buildingsOfKind(server, kind)`: the standing ones, oldest first, as `Building` records: site key, kind, plan id, name,
  dimension, origin, rotation, `built`, and every marker in world positions (`marker("counter")`, `first("job")`).
  `allOfKind` includes those still being built; `building(server, siteKey)` one by key.
- `isBuilt(server, kind)`, `isSiteBuilt(server, siteKey)`, `isRequested(server, kind)`.
- `population(server)` (the people living) and `populationFull(server)` (the cap reached, babies on the way included,
  through `civic.Families.babiesOnTheWay`).

`civic.Homes` is implemented: `homeOf`, `bedFor` (the foot of the friend's bed), `homes` (standing houses),
`roomForOneMore`, `moveIn` and `moveOut`. Village site keys start with `village.` (the town hall's contains
`town_hall` and is marked completed in the camp's records, for the wedding venue).

## Honest limits

- **Nothing here has been run in game.** It compiles. The plot search, the levelling, the street laying and above all
  the friends' handling of big buildings on real ground are untested; numbers (scores, how many surveys a tick, the
  stage requirements) are first guesses.
- **The grid is square to the compass.** Streets run east-west and north-south only, and "follow the terrain" means the
  path is laid on the ground as it lies and plots are skipped where the ground is wrong; streets are not curved round
  hills, and a street can run up and down steep ground (the path is laid wherever there is grass or dirt).
- **Slow.** A house is 150 to 400 blocks of real materials, a town hall about 600. With one builder and everyone else
  building their own home in their spare time, a full village takes many in-game days, and the gatherers will be busy.
- **Big plots are hard to find** on rough ground: a building that finds no plot waits five minutes and tries again
  (`/friends village` says why), and after three searches in a row find nothing the planner rests a minute before
  looking again. Clearing or levelling ground along the streets, or a larger `villageRadius`, helps. Trees on a plot
  rule it out (no felling for village plots).
- **A house waits for its lights.** A house is not lived in until every light and the door are in, so a camp with no
  coal (or charcoal) for torches keeps its households in the cabin until the gatherers bring some.
- **Streets and your ground.** A street keeps two blocks clear of anything you built, but grass further than that from
  your blocks looks natural: a big lawn or the middle of a large fenced field that a street runs through can still be
  made a path there. Keep such ground off the street lines (or put something of yours on it).
- **The camp grows to the village's size** (up to 64 blocks): the friends' camp jobs (lighting, paths, the gathering
  ring, quarries, hunting, which all stay outside or inside the camp) work over a larger area, so gathering trips get
  longer.
- **Moving the camp** (`/friends camp set` elsewhere, more than three blocks from the plan's centre or to another
  dimension) lets the whole town plan go (everyone is told): unfinished buildings stop, the houses' blocks stay, and
  nobody lives in them any more. A new plan is laid at the new camp. Setting the camp again within three blocks of
  where it was (standing by the campfire, say) keeps the village as it is.
- **Turning `villageHomes` off** stops new plots and building, but plots already reserved stay reserved.
- **Water** only goes into basins that hold it; a fountain or channel whose rim is not solid blocks all round stays dry.
  Water that flows in from the first bucket fills the rest of a small basin as running water rather than still water. A
  friend who carried a water bucket for a fall may come back with it empty.
- **Beds.** The bed is marked taken while a friend sleeps in it, so a player cannot lie down in a friend's bed then
  ("this bed is occupied"), as with villagers. A world saved while a friend slept leaves the bed marked until they wake
  on loading. Players' respawn points are not looked at.
- **The routine** is simple: friends walk to a spot at home, the square or a friend's house and stand there; there is no
  sitting on chairs. Meals are eaten from what the friend carries. The chests in a house are part of the building only:
  friends do not keep their own things in them yet (everything still goes to the camp's supply chest).
- **Streets** are surfaced block by block (the path dug up, the new block put in); if someone keeps standing on the spot
  the earth goes back, and in rare cases a one-block dip may be left, which the next pass paths over. Stretches over
  a hollow or near the mines are left as grass, so a street can have gaps.
- **Walls** are only two lengths each side of the gate, not a ring round the village. The gate goes at the end of the
  High Street or Market Street, and the village may later grow past it.
- **The wheat field** is the village's: the farmer tills, sows and harvests it with the camp's seeds, and it counts
  towards the camp's farmland, so Fern may till less elsewhere.
- Old 2.x camps that reach the Village stage get a plan at once; houses go up over their existing ground, and the camp's
  cabins stay as they are (friends without a house still sleep there).
