# Defending the village: the alarm bell, guards on the walls, raids and the fire watch

The friends now defend their village by themselves. When danger comes, somebody rings the bell; children and anyone
not fit to fight go indoors and shut the door; the fighters take their posts; and when it is over somebody gives the
all-clear. Once the village has a watchtower, a gate or walls, guards stand there at night. A raid on the village is
fought off together, and fires near the houses are put out. You do not need to give any orders; you can join in (ring
the bell yourself, fight beside them).

Nothing here has been run in game yet. See **Honest limits** at the end.

## The alarm bell

**What rings it:**

- **Monsters closing in at night**: at least `alarmHordeSize` hostiles (3 by default) seen in the village, or just
  beyond its edge, at the same time, once one of them that has not rung it before has come into the village itself.
  Monsters only prowling round the edge do not ring it.
- **A creeper inside the village**, by day or night.
- **A raid**: a pillager raid on the village, or on a game village near the camp.
- **A bad omen**: a player in the village with the Raid Omen (a raid is about to start), so everyone is ready before
  the raiders arrive.
- **You**: ring a bell in the village yourself (right-click it) and the village treats it as an alarm. On a shared
  world, only the camp's owner and the players they trust can sound it this way (as with giving orders); anyone may
  still ring a bell for the sound.

**Who notices.** The friends who are awake in the village keep an eye out: the one on the night watch and the guards
always, and the others in turn. A hostile counts when one of them can see it, or it is right beside them (within 6
blocks), and it is in the village at ground level: a zombie in a cave under the houses does not ring the bell. Each
hostile rings it once while it stays about, and one first seen while the alarm was already on does not ring it again
after the all-clear (a creeper still does, if it comes into the village).

**The bell.** The nearest friend who is awake, grown up and fit runs to the bell and rings it three times, with the
game's own bell, so you hear it and raiders near it glow just as when a villager rings it. They put down whatever
work they had in hand for it. Nobody is ever sent to a bell with a creeper near it, nor past a creeper on the way (nor
anyone with a creeper close by them), and whoever is asked is let off if a creeper comes into their way; with other
monsters beside the bell only someone fit to fight is sent. The bell rung is, in this order:

1. the **town hall's bell** (once a bell hangs there: the builders hang it once the camp has a bell in the chest);
2. the **friends' own bell**: if the village has no bell at all and no town hall yet (planned or standing: the town
   hall takes the chest's bell for its own bell spot), and the supply chest holds a bell, the builder (or anyone in
   their place) stands it on the ground at the square, a few blocks from the campfire, on a free spot that is no
   building's site, nowhere near your builds, and off the line of the village's two main streets (which cross at the
   campfire once the village lays them out). The friends cannot make bells: put one in the chest (villagers sell
   them, and game villages have one). If you move the camp, the bell at the old square is no longer the friends'
   own: still within the village, it is rung as any other bell; otherwise they put up another at the new square
   when the chest has a bell;
3. the **school bell**;
4. **any other bell in the village**, such as the old bell of a game village the camp grew up in, or one you hung.

With no bell (or nobody near it), the alarm is simply shouted. During a raid the bell is rung again for each new wave.

## When the bell rings

- **Children and everyone not fit to fight go indoors**: to their own home if it is safe to get to, otherwise the
  nearest other house, the town hall (or the tavern, chapel or school), or the camp's cabin. They never run towards a
  creeper, or past a monster standing between them and the door; if there is nowhere safe to go, they stay where they
  are and the usual reflexes keep them away from danger. Heavy work is put down for it. Once in, they **shut the door behind them** (only the
  friends' own wooden doors, which you can always open) and stand at a spot inside, away from the windows where the
  house allows. Someone already indoors stays put. At night, anyone indoors whose bed is there simply goes to bed.
  Someone found asleep out in the open (their own bed taken or out of reach, so they lay down the old way) is got up
  and kept indoors like anyone whose bed is outdoors, until the all-clear.
- **Fighters take their posts**: everyone grown up and fit to fight (healthy, not too tired, not falling back) who is a
  warrior, holds a guard trade, or carries a sword, an axe or a bow with arrows. They go to the post nearest the
  danger (a gate, a stretch of wall, the bell, the camp centre), never right on top of it, never near a creeper and
  never past one on the way, and stand ready there. The alarm gets sleeping fighters up, as the night watch's alarm
  does, if they have had enough sleep (40 energy or more); one with less is left asleep for this alarm. A fighter
  who wears out below 20 energy goes to bed and sits out the rest of this alarm, even once a nap has lifted them back
  over 20, so nobody is got up and sent back to bed over and over. The friend on the night
  watch and the guards on duty keep their own posts (the friend on watch does so even with only a pick or a hoe:
  they are not sent indoors).
- **Who the defenders go for first**: a monster at a door (zombies trying to break doors in on Hard, vindicators),
  then one going for someone in the village (a villager, an iron golem, you, a friend; a skeleton shooting in from
  outside), then a spider climbing a wall, then any other raider. They never fire with you, a villager, a friend or an
  animal in the line of fire (the combat rules of 2.0), and never hurt villagers or golems.
- **The all-clear**: once nothing has been seen inside the village for 30 seconds (and the alarm has been on for at
  least 30 seconds), or at dawn for an alarm at night, a friend calls the all-clear and everyone comes out and goes
  back to what they were doing. A raid keeps the alarm on until the raid is over. Any other alarm ends after five
  minutes at most, so a monster stuck in sight somewhere cannot keep everyone indoors for good.

## Guards

Once the village has a **watchtower**, a **town gate** or **town walls** (the Town and City stages of the village),
guards stand there at night, besides the night watch. Before that the night watch is all there is, as in 2.0.

- **Two shifts**, like the night watch: dusk to midnight, and midnight to dawn. Each shift has up to
  `guardsPerShift` guards (2 by default), no more than there are posts. Nobody stands both shifts: everyone who stood
  any part of the first shift sleeps the second, and is not picked for the night watch's second half either. The
  friend on the night watch is never a guard too.
- **Who**: grown-ups at work in the village who are healthy, rested (35 energy or more) and armed (a sword, an axe or
  a bow). Warriors first, then whoever did not stand guard last night, then the best armed. A guard who gets hurt,
  worn out (below 20 energy) or stuck is relieved and someone else takes the post; the one relieved is not picked
  again that night.
- **On duty from dusk**: a guard picked at dusk goes straight to their post. They leave the evening at home and a feast
  at the square to the others, and a guard napping after last night's shift gets up for it.
- **The posts**: the lookout at the top of the watchtower (a guard with a bow goes up there and **shoots from the
  top** rather than climbing down to fight), the town gate (beside the way through, with a sword), and each stretch of
  wall (on the village side). A guard at the gate or a wall now and then walks a short patrol up the street or along
  the wall and back. A dark post gets a torch put down beside it, if the guard carries one.
- **Paid in respect**: in the morning the camp gains 2 Unity for each guard who stood the night (at most 8 a day), and
  guards may **sleep during the day** (a nap at home, below 60 energy) to make up for it.

## Raids

When a pillager raid starts on the village (a player walked in with Bad Omen, which becomes the Raid Omen in a
village), or on a game village near the camp, the alarm rings, the children and non-fighters go indoors, and the
fighters and guards fight the raiders, helping the villagers and iron golems (they never hurt them). The bell rings
again for each wave. **A raid beaten off earns the camp 40 Unity** (at most 80 a day), everyone is told, and a friend
celebrates. A raid lost or abandoned earns nothing.

Note: the friends' village has beds and bells, so the game may count it as a village for Bad Omen. Walking into it with
Bad Omen starts a raid on the friends.

## The fire watch

Fire near the houses or the camp (lightning, lava, a burning creeper's work) is **put out by hand**: a fit grown-up
nearby (at most two at once) walks up to it, out of the flames, and puts it out flame by flame until the blaze is
out, as you would. Only the fire itself is touched, through the edit guard's rules (fire counts as a clearable block
inside the camp); the builder's repair job then puts back what burnt in the friends' own buildings, as it does for
any missing block. Left alone:

- soul fire, and fire burning for good on netherrack or another everlasting base (a fireplace);
- fire within two blocks of lava (the lava would light it again, and nobody goes near lava);
- fire beside water, which the edit guard does not let them break;
- fire outside the camp, or that they cannot reach or see (left for a minute, then tried again).

A blaze put out earns 1 Unity (at most 10 a day).

## Commands

All at permission level 0, with cheats off, and changing nothing.

| Command | What it shows |
|---|---|
| `/friends defence` | the alarm (quiet, or ringing: why, for how long, whether the bell was rung, how many at their posts and taking cover), the last alarm, the village's bells, the guard posts and who stands each tonight, who is on duty now (night watch and guards), fires to put out, and how many alarms, raids beaten off, fires put out and nights on guard so far |

`/friends camp` also says when the alarm is ringing.

## Settings (`config/hardcorefriends.json`, under "Defending the village")

| Setting | Default | What it does |
|---|---|---|
| `villageDefence` | `true` | The alarm bell, taking cover, fighters at their posts, guards and raids. `false` leaves only the night watch. |
| `guardsPerShift` | `2` | Guards each half of the night once the village has a watchtower, gate or walls (0 to 6; 0: no guards). |
| `alarmHordeSize` | `3` | How many hostiles seen in the village at once at night ring the bell (1 to 20). |
| `fireWatch` | `true` | Friends put out fires near the houses and the camp. |

## For the other packages

- Job ids start with `defence.`: `defence.ring_bell`, `defence.take_cover` (children may do it), `defence.to_posts`,
  `defence.guard`, `defence.guard_rest`, `defence.fire`, `defence.put_up_bell`.
- `civic.Professions`: a trade called `guard`, if the market package ever adds one, puts its holder first in the guard
  rota and counts them as a fighter. Nothing needs it.
- `combat.Archery.HOLDS_POST` (new): a predicate list; a friend for whom one says true shoots whatever they would fight
  rather than closing in. The defence package uses it for the guard at the watchtower lookout.
- `Alarm.isActive()` is public, for any package that wants to know the bell is ringing.
- `camp.NightWatch.EXCUSED` (new): a list of rules; a friend one of them answers true for (with the watch) is not
  chosen for that watch. The defence package uses it to keep the first shift's guards off the second watch.
- The defence job filter keeps a guard on duty off `needs.evening` (the village's evening at home) and
  `needs.festival` (a feast or funeral), as those packages already do for the friend on the night watch.

## Honest limits

- **Nothing here has been run in game.** It compiles. The numbers (how many hostiles ring the bell, how long until
  the all-clear, the scores) are first guesses.
- **The village's bell needs a bell.** The friends cannot make one; the town hall's bell spot stays empty until the
  supply chest has a bell, and the friends' own square bell needs one too. Without any bell the alarm is shouted.
- **Who rings it.** Only a friend awake, at work in the village and within 48 blocks of the bell is asked; at night
  that is usually the night watch or a guard. A shopkeeper serving a player is not asked. A friend busy with a need
  more urgent than the bell (a starving friend's meal) is let off after 40 seconds and the next nearest is asked; with
  nobody left to ask, the alarm is shouted. During a raid everyone may be asked again at each new wave. The way to the
  bell is judged in a straight line, as for taking cover (below).
- **The last alarm** shown by `/friends defence` reads "over without an all-clear" when the game was closed (or the
  defence switched off) while it rang.
- **Taking cover is simple.** People walk to a spot inside and stand there; a shelter's "inside" is the plan's marked
  spot, and "away from the windows" only avoids standing right beside glass. Shutting the door is tried once, a moment
  after they are in, and only on the friends' own wooden doors (not on anyone in the doorway). Before the village has
  houses, only the cabin is a shelter; with no shelter at all, nobody takes cover and the old reflexes apply.
- **The way home is judged in a straight line**: a shelter is refused when a hostile stands near the straight line to
  it, but the path the friend actually walks can bend past danger the check did not see (the danger reflexes still
  pull them away).
- **Posts.** The watchtower's lookout is up a spiral stair, and the friends' path finding over stairs has not been
  tried in game; a guard who cannot get up three times is relieved. The walls have no walkway, so wall guards stand
  on the ground on the village side and cannot shoot over the wall. A guard at the lookout who chases a target out of
  bow range walks down after it, and goes back up when the fight is over.
- **Fighters at their posts** stand there for the whole alarm, sleep included; a long night of alarms leaves them
  tired (below 20 energy they count as non-fighters and go to bed for the rest of that alarm). A fighter asleep with
  less than 40 energy is not got up at all, so a village of tired people may have few at their posts.
- **Asleep in the open**: whether a sleeper is indoors is judged by the sky above them, as elsewhere in the mod; a bed
  under a glass roof counts as out in the open, and its sleeper is got up and sent to stand inside at the alarm.
- **Raids**: the friends fight raiders with their usual combat; there is no special tactic against ravagers or
  evokers' fangs, and a raid at night means nobody sleeps much. The raid has to be within 96 blocks of the camp centre
  to count (the game's own raid distance).
- **The bad omen alarm** only sounds for the Raid Omen (the game gives it on entering a village with Bad Omen); a
  player carrying Bad Omen into a camp the game does not count as a village starts no raid and rings no bell.
- **Fire** is put out by hand only: no water buckets. A big blaze spreads faster than two friends can put it out, and
  fire on a roof out of reach is left alone. The village is looked over for fire about every 10 seconds, so a fire can
  burn a few blocks before anyone comes. Where a fire has burnt before, the game keeps a trace of it until the chunk
  is next loaded, so such a spot is searched block by block at most every 10 seconds: a new fire there can take a
  few seconds longer to be noticed. Fire in a player's build inside the camp is put out too (fire is never a
  build), but never a fireplace on netherrack.
- **The Village Chronicle**: the village life package (built at the same time) had no way in for other packages yet,
  so a raid won is not written in the Chronicle.
- **Your own bell rings the alarm**, even one you ring for fun: the children go indoors for at least half a minute.
