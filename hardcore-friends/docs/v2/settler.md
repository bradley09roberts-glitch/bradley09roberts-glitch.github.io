# Newcomers: people to meet and recruit

Besides the nine friends, the world now has other people in it: **strangers** living in villages, at **survivor
camps** out in the wilds, and **travellers** who walk up to your camp. Each has their own name, look and trade, and
each will join your team if you help them out with something small first. Once on the team, a newcomer works,
talks and fights like the friend who shares their trade (a newcomer farmer works like Fern, a newcomer warrior like
Aegis), but under their own name.

## Who they are

- **Names.** About 120 different first names (Mabel, Arthur, Ivy, Hugo...). No two living newcomers in a world share
  a name, and none is called after one of the nine friends.
- **Trades.** Any of the nine kinds of work. Farmers, foragers, builders, miners and landscapers are the most common;
  warriors and strategists are rarer.
- **Looks.** One of the game's own default player skins (Steve, Alex, Ari, Efe, Kai, Makena, Noor, Sunny, Zuri), and a
  name colour of their own.
- **Stories.** When you first talk to one, they tell you who they are and a little about themselves ("I lost my village
  to pillagers last spring", "I've been walking north for weeks").

## Where to find them

### Villages

The first time you walk into a village, there is a chance (`villageSettlerChance`, 75% by default) that one or two
newcomers live there. They appear near the village bell (or the middle of the village) and each picks a bed nearby to
stand beside at night. Each village is only ever decided once: if nobody lives there the first time, nobody will.

### Survivor camps

A new kind of place in the world: a small camp of one or two wool tents round a campfire, with log benches, a crafting
table and a chest with a few supplies (bread, apples, torches, coal, sticks, seeds, a little leather and string,
sometimes a stone tool). One to three strangers live there. Survivor camps appear in plains, sunflower plains, meadows,
forests, flower forests, birch forests, dark forests, taigas, old growth taigas, snowy plains and savannas, on fairly
flat dry ground, never right next to a village. They are about as common as igloos or swamp huts (one chance in every
36 by 36 chunk area). Only new parts of the world get them: chunks you have already explored stay as they were.

### Travellers

Once your camp has grown to a **Camp** (stage 1), and as long as it is under the open sky (not in the Nether or the
End), a traveller may turn up every two to four in-game days. They arrive
in the morning, walking in from the wilds to the edge of the camp, say hello, and stay for a day. If nobody asks them
in, they say goodbye the next day and walk off, and are gone once nobody can see them. Someone has to be at or near the
camp for a traveller to come; none come while your team already has as many newcomers as it may.

## Strangers' lives

Until they join, strangers mind their own business: by day they potter about within a short walk of their home
(the village square, their camp, or the edge of your camp), and at night they go to their night spot (beside a bed in
a village, inside their tent at a survivor camp) and keep still. They step back from creepers, fall back when badly
hurt and stand up for themselves with the wooden tool they carry. They never break or place a block, never take
orders and do not use your camp's food or chest. They say hello when you come close.

A stranger who dies is simply gone; your team does not lose any Unity for them, and only players close by are told.

## Asking someone to join

1. **Right-click** a stranger. They tell you their name, their trade and their story, and ask for something small that
   suits their trade. They remember what they asked for.
2. Gather what they asked for and carry it in your inventory (anywhere in it, not just in your hand).
3. **Right-click them again.** If you have everything, they take exactly what they asked for and join the team. If not,
   they tell you what is still missing.

What they ask for (each trade has two possible requests; a stranger picks one):

| Trade | Asks for |
|---|---|
| Farmer | 4 bread and a hoe, or 6 wheat and a hoe |
| Builder | 16 planks, or 16 cobblestone and 8 planks |
| Miner | a stone pickaxe and 8 torches, or 12 torches and 2 bread |
| Explorer | 4 bread and 4 torches, or 6 pieces of food |
| Redstone inventor | 4 copper ingots, or a furnace and 4 coal |
| Warrior | an iron sword, or a shield |
| Strategist | a book, or 3 bread and 6 torches |
| Landscaper | a shovel and 4 saplings, or 6 saplings and 6 torches |
| Forager | an axe and 3 bread, or 6 torches and 4 pieces of food |

- Any kind of hoe, axe or shovel will do, and any planks, saplings or common food. The cheapest matching tool is taken
  first. **Anything enchanted or renamed is never taken**, so your good gear is safe.
- The newcomer keeps what you gave them in their backpack (and holds their own wooden tool).
- When someone joins, everyone is told, and the team's **Unity** grows by 10 (at most 30 a day from newcomers).
- **Where they go.** If you are at or near your camp, they start work there straight away. If the camp is far away
  (or in another dimension), they **follow you** home and start work as soon as they reach the camp. With no camp yet,
  they follow you; set one with `/friends camp set` and send them to work with `/friends work <name>`.
- **Limits.** The team can have at most `maxSettlers` newcomers at once (12 by default), and each player can ask in at
  most `maxSettlersPerPlayer` (6). A stranger tells you kindly when there is no room. The nine named friends never count.

## On the team

A newcomer is a full member of the team: give them orders by name (`/friends follow mabel`, `/friends stay mabel`,
`/friends work mabel`, `/friends needs mabel`, `/friends backpack mabel`, `/friends where mabel`, `/friends dismiss
mabel`; their names are offered as you type), feed them, give them gifts and open their backpack as with any friend.
They have needs, sleep, keep watch, and do every job, their own trade first. A newcomer counts for the camp's growth too:
a newcomer builder builds the camp's buildings even without Oak.

**This is Hardcore.** A newcomer who dies is gone for good, drops their backpack where they fell, and the team loses
80 Unity, as for any friend. Another stranger may turn up somewhere else in time.

## Commands

- `/friends newcomers`: every newcomer who has joined (what they are doing, their health and mood, or how they fell or
  that they left, and who asked them in), then the strangers within 128 blocks of you and what each would like.
- `/friends list` ends with a pointer to `/friends newcomers`.
- All of these work without cheats and change nothing in the world.

## Settings (`config/hardcorefriends.json`)

| Setting | Default | What it does |
|---|---|---|
| `allowSettlers` | `true` | Off: no new strangers appear anywhere (villages, survivor camps, travellers). Survivor camps are still built, just empty. Strangers already in the world stay and can still be asked in |
| `villageSettlerChance` | `0.75` | Chance a village has newcomers, decided once per village the first time a player walks in |
| `maxSettlers` | `12` | Most newcomers on the team at once |
| `maxSettlersPerPlayer` | `6` | Most living newcomers any one player may have asked in |
| `wanderingVisitors` | `true` | Travellers visiting the camp every few days |

## Honest limits

- **Nothing here has been run in the game yet.** It compiles, and was checked by reading it through, but it has not
  been played.
- **Survivor camps are new world generation.** The data files were matched field by field against the game's own
  igloo, swamp hut and pillager outpost files, but a mistake there could stop new worlds from loading, so test in a
  fresh copy first. The camp only appears in newly generated chunks.
- **The camp's ground is levelled.** The camp is only placed on dry ground that varies by at most three blocks, gaps
  under it are filled with dirt (up to four deep) and up to four blocks of air are cleared above its floor, cutting
  through grass, flowers, snow and the odd bump. A tree from a neighbouring chunk can be cut, leaving a few floating
  leaves that soon decay. A camp on a pond or a lava pool that appeared after its spot was chosen is not built at all.
- **Trees can still grow at the edges of a survivor camp.** The ground round the fire is mostly a worn dirt path, where
  nothing grows (always where the strangers stand, so no tree or bush grows round them), but the odd patch of grass by
  the fire and the grass at the edges may get a tree or flowers after the camp is built.
- **Strangers made with the world are named before the world knows who else is alive**; a duplicate name is quietly
  changed as soon as their part of the world is first loaded, before anyone could have met them.
- **Village detection** only looks at chunks already loaded: a village is noticed when you are standing in or right
  next to one of its buildings or paths and its starting chunk is loaded. With a very short view distance, you may have
  to walk further in. Zombie villages count as villages too.
- **Strangers at night** stand by a bed or in their tent but do not sleep; monsters can still find them, and they can
  die. Your friends defend strangers close to them, but nobody guards a village for you.
- **A traveller who is out of sight when their day ends** keeps going once their part of the world is loaded again;
  they are only taken out of the world when no player is within 24 blocks, or within 64 blocks and able to see them.
- **Newcomers work like their trade's named friend in every way**, including the parts that are that friend's alone: a
  newcomer warrior may take Aegis's first night watch, a newcomer explorer gives Scout's warnings, and a newcomer
  strategist gives Sage's advice. Sage's 10% planning bonus and the team plan's "who covers this" still only name the
  nine friends.
- **Requests are fixed lists**, two per trade. A stranger never changes their mind about what they asked for.
- **Records** of newcomers (in `data/hardcorefriends_settlers.dat`) are updated every few seconds while a newcomer is
  loaded, so "last seen" can be a little out of date. A newcomer who vanishes without dying or being dismissed (for
  example if their part of the world is deleted with an outside tool) still counts as alive towards the limits.
