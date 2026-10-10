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
  beyond its edge, at the same time.
- **A creeper inside the village**, by day or night.
- **A raid**: a pillager raid on the village, or on a game village near the camp.
- **A bad omen**: a player in the village with the Raid Omen (a raid is about to start), so everyone is ready before
  the raiders arrive.
- **You**: ring a bell in the village yourself (right-click it) and the village treats it as an alarm.

**Who notices.** The friends who are awake in the village keep an eye out: the one on the night watch and the guards
always, and the others in turn. A hostile counts when one of them can see it, or it is right beside them (within 6
blocks), and it is in the village at ground level: a zombie in a cave under the houses does not ring the bell. Each
hostile rings it once while it stays about.

**The bell.** The nearest friend who is awake, grown up and fit runs to the bell and rings it three times, with the
game's own bell, so you hear it and raiders near it glow just as when a villager rings it. Nobody is ever sent to a
bell with a creeper near it. The bell rung is, in this order:

1. the **town hall's bell** (once a bell hangs there: the builders hang it once the camp has a bell in the chest);
2. the **friends' own bell**: if the village has no bell at all and no town hall yet (planned or standing: the town
   hall takes the chest's bell for its own bell spot), and the supply chest holds a bell, the builder (or anyone in
   their place) stands it on the ground at the square, a few blocks from the campfire, on a free spot that is no
   building's site and nowhere near your builds. The friends cannot make bells: put one in the chest (villagers sell
   them, and game villages have one);
3. the **school bell**;
4. **any other bell in the village**, such as the old bell of a game village the camp grew up in, or one you hung.

With no bell (or nobody near it), the alarm is simply shouted. During a raid the bell is rung again for each new wave.

## When the bell rings

- **Children and everyone not fit to fight go indoors**: to their own home if it is safe to get to, otherwise the
  nearest other house, the town hall (or the tavern, chapel or school), or the camp's cabin. They never run towards a
  creeper, or past a monster standing between them and the door; if there is nowhere safe to go, they stay where they
  are and the usual reflexes keep them away from danger. Once in, they **shut the door behind them** (only the
  friends' own wooden doors, which you can always open) and stand at a spot inside, away from the windows where the
  house allows. Someone already indoors stays put. At night, anyone indoors whose bed is there simply goes to bed.
- **Fighters take their posts**: everyone grown up and fit to fight (healthy, not too tired, not falling back) who is a
  warrior, holds a guard trade, or carries a sword, an axe or a bow with arrows. They go to the post nearest the
  danger (a gate, a stretch of wall, the bell, the camp centre), never right on top of it and never near a creeper, and
  stand ready there. The alarm gets sleeping fighters up, as the night watch's alarm does. The friend on the night
  watch and the guards on duty keep their own posts.
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
  `guardsPerShift` guards (2 by default), no more than there are posts. Nobody stands both shifts, and the friend on
  the night watch is never a guard too.
- **Who**: grown-ups at work in the village who are healthy, not too tired and armed (a sword, an axe or a bow).
  Warriors first, then whoever did not stand guard last night, then the best armed. A guard who gets hurt, worn out or
  stuck is relieved and someone else takes the post.
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

## Honest limits

- **Nothing here has been run in game.** It compiles. The numbers (how many hostiles ring the bell, how long until
  the all-clear, the scores) are first guesses.
- **The village's bell needs a bell.** The friends cannot make one; the town hall's bell spot stays empty until the
  supply chest has a bell, and the friends' own square bell needs one too. Without any bell the alarm is shouted.
- **Who rings it.** Only a friend awake, at work in the village and within 48 blocks of the bell is asked; at night
  that is usually the night watch or a guard. A friend busy with something more urgent than the bell (a starving
  friend's meal) is let off after 40 seconds and the alarm goes on without the bell.
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
  tired (below 20 energy they count as non-fighters and go to bed).
- **Raids**: the friends fight raiders with their usual combat; there is no special tactic against ravagers or
  evokers' fangs, and a raid at night means nobody sleeps much. The raid has to be within 96 blocks of the camp centre
  to count (the game's own raid distance).
- **The bad omen alarm** only sounds for the Raid Omen (the game gives it on entering a village with Bad Omen); a
  player carrying Bad Omen into a camp the game does not count as a village starts no raid and rings no bell.
- **Fire** is put out by hand only: no water buckets. A big blaze spreads faster than two friends can put it out, and
  fire on a roof out of reach is left alone. The village is looked over for fire about every 10 seconds, so a fire can
  burn a few blocks before anyone comes. Fire in a player's build inside the camp is put out too (fire is never a
  build), but never a fireplace on netherrack.
- **The Village Chronicle**: the village life package (built at the same time) had no way in for other packages yet,
  so a raid won is not written in the Chronicle.
- **Your own bell rings the alarm**, even one you ring for fun: the children go indoors for at least half a minute.
