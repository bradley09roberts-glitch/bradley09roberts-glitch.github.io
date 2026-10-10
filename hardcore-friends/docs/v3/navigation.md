# Finding the way: better paths, sharper senses, getting unstuck and sprinting (3.0)

In 2.0 friends found their way with ordinary mob pathfinding. Too many of them wandered into caves on the way to
something on the surface, dropped down holes they could not climb back out of, or stood stuck behind running water,
and some died down there on their own. This part of the update fixes the causes first (how friends choose their way),
then teaches them to notice when they are stuck and get themselves out. As a last resort, a friend who is stuck for a
long time is brought home. Friends also sprint now.

Nothing here has been run in the game yet. See **Honest limits** at the end.

## Why friends got stuck

- When a place could not be reached, the pathfinding walked them to the nearest point it could find. That was often
  inside a cave mouth, a ravine or a hole.
- They happily dropped two or three blocks, and in a fight even more. A drop like that cannot be walked back up.
- Running water and still water looked the same to them. A stream or a waterfall could carry them off.
- Getting stuck only made the job give up. The next job started from the same hole.

## Better paths

Every friend now finds their way with paths of their own:

- **No cave detours.** On a walk from somewhere on the surface to somewhere on the surface, a path through a cave
  costs a lot. A friend fetching wood goes round the hill, not through the cave under it. If the only way on leads
  underground, they stop at the cave mouth and their job picks something else, instead of following a dead end. Jobs
  whose target is underground, such as Flint's mine, are not affected.
- **Water.** Running water costs much more than still water, and deep water more than shallow. Friends never step into
  a waterfall or onto the lip of one. Walking along a riverbank is no longer avoided. A path that cannot reach its goal
  never ends out in the water.
- **Drops.** A step down of two blocks or more costs extra, because it cannot be walked back up. Friends never drop
  more than three blocks, even when chasing a monster. Standing beside a drop of more than three blocks costs a little,
  so paths keep back from cliff edges.
- **Danger underfoot.** Lava and fire anywhere round a spot are avoided. Before, a spot next to both water and lava
  could count only the water. Friends also avoid walking on powder snow, past pointed dripstone and wither roses, and
  (as before) magma, cactus and berry bushes.
- **Longer reach.** Paths now reach 48 blocks instead of 32. The 20-block legs of trips fit easily, with room for
  detours. Each path still looks at a limited number of spots, so it stays cheap.
- **Running away** picks dry ground under the open sky where it can, not water or a cave.

## Sharper senses

- **Hearing.** Friends hear monsters within 16 blocks through walls, not just the ones they can see. Their paths keep
  away from a creeper they can hear, and, for friends who are not fighters, from other monsters too. So they do not
  walk round a corner into one. A creeper hissing within 12 blocks sends anyone running, seen or not.
- Friends also sense whether they are underground or in the dark, short of air under water, in a current, near lava,
  or beside a long drop. These feed into how they choose their way, when they sprint, and how they get unstuck.

## Getting unstuck

Every friend is watched in every mode: working, following, staying, and newcomers who have not joined yet. If they
are trying to get somewhere and getting nowhere, they work through these steps:

1. **A hop and a step aside.** The spot they were stuck on counts as blocked for half a minute, so their next path
   goes round it. They try this up to three times.
2. **Air.** Under water and short of air, they swim straight up, and across to open water if there is a roof over
   them. Then they say so.
3. **Out of the water.** Stuck in the water, caught in a current or being carried away from where they were going,
   they swim to the best shore. That is the nearest one, towards where they were going, across the current rather
   than with it, and one that leads somewhere (not a ledge with nowhere to go). They work against the current while
   they swim. In a hole full of water with steep sides, they cut a step out just above the waterline.
4. **Shut in.** In a pit or a hole they cannot walk out of, they put a block of dirt or cobblestone from their backpack
   underfoot, once or twice, the way players do. Then they step out onto the edge and take the blocks back. With no
   blocks, or if the hole is deeper, they dig a staircase up. A friend shut in on a job underground (a hole in the
   mine) only digs until they can get about again, not all the way to the surface. A friend whose walks keep failing
   away from camp checks whether they can get 24 blocks away on foot or swimming (the bottom of a ravine, say). If
   not, they dig a staircase towards home until they can. Open water counts as a way out, so a riverbank or a lake
   shore never looks like a pit.
5. **Lost in a cave.** Underground, trying to get somewhere that is not, and getting no nearer: they look for the
   nearest spot under open sky they can walk to, up to 48 blocks away, and walk there. Along a dark way, they put a
   torch down every so often if they carry any. If there is no way out on foot, they dig a staircase up.
6. **Brought home (last resort).** A friend on the team who is still in trouble after two in-game minutes, trapped
   underground while badly hurt or starving, or about to drown, is brought home safely. Time lost underground is added
   up until they are out, so a friend who can wander about a big cave but never find the way out is brought home too,
   however many times they try in between. A friend who is following you is brought back to you instead. Everyone is
   told, for example "Rowan got lost in a cave and found the way home."

A friend is never brought home out of a fight, out of a job that is still getting on with things (a mine that is
being dug, for example), or when you told them to stay put. A friend who is following you and gets stuck catches up
with you after a few seconds (when catching up is on, `followTeleportDistance` above 0), instead of waiting until you
are 48 blocks away. That comes first: they only start walking out of a cave or digging if the catch-up does not
happen. Walking after you while you walk on counts as getting somewhere, so a friend following you out of a cave is
not taken for lost. A follower only counts as heading for you while following you is what moves them, so one who
stands at their spot on purpose (an archer in the dragon fight, say) is never "stuck" short of you. When you are up a
pillar in the End, or next to an end crystal, a friend is never brought back to you there.

**Digging out** follows the edit rules friends use to stay alive away from camp. A staircase digs at most three
blocks a step, and only natural ground: dirt, grass, sand, gravel, clay, and plain stone, granite, diorite, andesite
and tuff. It never digs a block with anything stored in it, a block touching water or lava, under loose sand or
gravel, near anything you built, anything the friends built, or on the camp's own ground. Friends only dig or place
blocks to get out in the Overworld. In the Nether and the End they are with you, so they catch up with you instead.
On the camp's own ground, a friend who is shut in (in a building, or in the pen) never digs or places blocks. They
hop, try the doors, and in the end are brought out. The camp's own ground is the heart of the camp (its size before
the village, at any depth) and, in the village round it, the sites of the village's buildings near the camp's height.
The rest of a grown village, its gardens and verges, and the caves, ravines and mines under it, are dug out of like
anywhere else. Children never change a block. They swim, walk out of caves and get brought home like everyone else.

## Sprinting

Friends sprint like players:

- when more than 12 blocks of path are left: to work, home, on trips, catching up with you, or running to help;
- when running away from danger, or keeping up with you while you sprint.

They use the real sprint. You see them run, with dust at their feet, and they move 30% faster. They slow to a walk 5
blocks from where they are going. They do not sprint in water (they swim), when hungry (hunger 25 or less), when
sneaking, or (unless running from something) beside a long drop or near lava, where they stand or a few steps ahead.
That holds when they keep up with you too: sprint along a bridge over the void or past a lava lake and they walk
after you. Running costs a little more hunger: a second of running costs about two thirds as much again as a second
of work, so ten minutes of running adds about 6 to a day's 15 or so.
Children sprint too, but tire after about ten seconds and need a while to get their breath back. Now and then a friend
says something cheerful as they set off on a long run.

## Command

| Command | What it shows |
|---|---|
| `/friends senses` | For each friend: above or below ground, light, water, current, lava, drops, monsters heard (and the nearest), danger, whether they are stuck or getting out, and whether they are running |
| `/friends senses <name>` | The same for one friend |

It works without cheats and changes nothing.

## Settings (`config/hardcorefriends.json`)

| Setting | Default | What it does |
|---|---|---|
| `rescueStuckFriends` | `true` | Brings a friend home (or back to their leader) after two in-game minutes stuck or lost, sooner when trapped underground hurt or starving, or about to drown. Turn it off for pure Hardcore. Friends still try everything else to get out. |

Digging and placing blocks to get out also needs `allowWorldEditing` to be on.

## For other parts of the mod

`navigation/Senses` answers questions about a friend: `Senses.underground(c)`, `Senses.dark(c)`, `Senses.danger(c)`
(none, caution or serious), `Senses.heard(c)` (monsters heard, nearest first), `Senses.lowOnAir(c)` and
`Senses.inCurrent(c)`. `Routes.reachable(c, pos, reach)` says whether a friend can walk somewhere along a whole path,
without ducking underground on a walk between two places on the surface (cached; meant for the one target a job is
about to choose; safe to ask while the friend walks somewhere else, as the path is worked out on the side). `Wayfinder.troubleTicks(c)` says how long a friend has been stuck. `Sprint.hurry(c, ticks, fleeing)`
makes a friend run.

## Honest limits

Nothing here has been run in the game. Everything below is untested.

- **No bridges over water.** A friend never puts a block over running water to cross it. The edit rules forbid taking
  a block back from beside water, so it would be left behind. They go round, swim across if they must (working against
  the current), and are brought home if a river truly cuts them off.
- **"Underground" is a guess.** It means sky-less and at least three blocks under the top of the ground there. A
  windowless room or a deep overhang can count as underground. Paths through one cost more, and a friend inside one,
  heading outside, may think they are lost. A cave right under a thin roof can count as the surface.
- **Searches are bounded.** The way out of a cave is looked for up to 48 blocks away and 3,000 spots. A friend deep in
  a big cave system may find nothing and start digging up instead, or, below the deepslate line, wait to be brought
  home (about two minutes of being lost, added up). Long, winding ways out of the water can be missed in the same
  way. The searches swim across and up, never down, so a way out that means diving under a wall is not found.
- **Jobs do not check their targets yet.** `Routes.reachable` exists, but the jobs do not ask it before choosing a
  target. A job whose target can only be reached through a cave is still chosen. The walk then stops at the cave
  mouth and gives up, and the job picks again later, perhaps the same target.
- **A staircase out of a cave under the village** comes up through the village's open ground (a garden, a verge)
  and is left there, as it would be in the wilds; it never comes up through a building's site or the camp's heart.
- **Digging up is slow** without a pickaxe (about 7 seconds a stone block), and it stops at sand or gravel overhead,
  at water or lava, at blocks you or the friends built, and at the camp's own ground. It cannot dig deepslate, cobblestone
  or sandstone (the shared edit rules for staying alive do not allow them), so deep down below the deepslate line, or
  in a desert, a friend can only walk out. A friend who cannot dig any further waits to be brought home.
- **The night shelter** looks after itself: while a friend is building, sleeping in or leaving their shelter, they
  are not watched for being stuck.
- **The rescue is a teleport.** It puts the friend on a safe, loaded spot near the camp centre (or near you), and only
  when that spot is loaded. A friend stuck while the camp is not loaded waits until it is. Purists can turn it off.
- **Nudges** push a friend a little sideways and make them hop. In a crowd at the chest this can look odd.
- **Children** cannot dig or place blocks, so they rely on swimming and walking out, and on the rescue.
- **Sprinting** is decided every quarter of a second from the path ahead. A friend may sprint round a corner into
  something the path did not foresee. They do not sprint beside a long drop or near lava unless they are fleeing.
- **Hearing** only changes paths for monsters within 12 blocks, and only when the path is worked out. A monster that
  moves afterwards is noticed at the next path.
- **Performance.** Paths look at up to 768 spots, against 512 before, and each spot is checked a little more. A walk
  whose path stops short of the target (a cave mouth, the last dry step) is worked out again once a second, not every
  tick. Searches for a way out run only for a friend in trouble, at most every 10 seconds. With many friends stuck at
  once, there may be short spikes.
