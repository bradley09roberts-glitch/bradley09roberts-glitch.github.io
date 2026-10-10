# Living together: friendships, romance, weddings and children

The people of your camp now have lives of their own together. They become friends with the people they work, eat
and chat with; some fall in love, go on evening dates, get engaged and marry at the camp in front of everyone; married
couples with room at home have babies; and the children play, learn from their parents, go home early, run to a
grown-up when a monster comes, and grow up into new members of the team who work at a trade. Everything is gentle
and family friendly: hand holding, hearts, weddings and babies arriving, nothing more.

None of it changes a block. It all happens on its own; you can watch it with a few commands.

## Friendships

Every pair of people on the team (the nine friends, the newcomers and the children) has a **friendship** score from
0 to 100. Two people who have just met start at 10. It grows when they:

- **chat** (a friend who feels lonely walks over for a chat): +3
- **share** something (handing over materials, or food to a hurt friend): +2
- **fight side by side** against monsters: +1.5, at most every 30 seconds
- **work near each other** (within 12 blocks): +0.5 a minute
- **eat, rest, play or sit by the fire together** (within 6 blocks): +1 a minute
- for a parent and child, simply **being near each other**: +1 a minute

How much each of these counts depends on how well the two get on by nature. Two equally chatty people talk easily;
generous people are easy company; and someone whose interest is another's trade (Sage and Fern share a love of farming)
has plenty to talk about. Every pair also has a little chemistry of its own that never changes. Two people both in a
good mood gain a quarter more.

Two people who are both in a **low mood** and close together may **quarrel** now and then (friendship -4). That is
the only way a friendship falls, so a well-fed, well-rested camp gets on well.

At friendship 50 two people are **good friends**, and one of them says so. At 75 they are close friends, at 90 best
friends.

## Romance

Two **grown-ups** who are already friendly (friendship 45 or more), who are **not family**, and who are each single,
may also grow a **romance** (0 to 100). About four pairs in ten simply have no spark: they can be the best of friends
but never more. Romance grows from chats (+2), from time at leisure together (+0.5 a minute) and above all from dates.

When two single grown-ups reach friendship 60 and romance 25, one of them, sooner or later, **asks the other out**,
the other says yes, and everyone is told ("Fern and Oak are going out together. How lovely!"). Any two grown-ups may
pair up. Nobody goes out with two people at once, and family (parents, children, brothers and sisters, cousins, aunts
and uncles, step-parents) never court each other.

### Dates

Once a day, in the late afternoon or evening (time of day 9000 to 12500), a couple spends a little time together inside
the camp: watching the **sunset** from the highest natural ground about, sitting by the **campfire**, or an **evening
walk**. One asks; the other stops what they are doing to join them unless they are busy with something pressing.
Hearts float up, both feel happier (fun and company), and the romance grows (+8, friendship +3). A date never changes
a block and ends at nightfall. Whoever keeps the night watch (it starts at dusk) is neither asked out nor asks, and a
date ends when one of the two goes on watch. In a village, a date comes before the evening at home: from time of day
11000 a couple who have not had their date yet go out first, and the one asked leaves home to join them.

### Engagement and the wedding

A couple who have been going out for at least **two days**, have been on **three or more dates** and are deeply in
love (friendship and romance both 70 or more) may get engaged at the end of a date: one proposes, the other says yes,
and the **wedding is set for the next morning**.

On the wedding day, in the late morning (time of day 5000 to 10000), once both are at the camp and no monster is
about, the wedding begins at the **camp centre** (or at the **town hall**, once the village has built one). Everyone
free at the camp, children included, gathers in a ring round the couple; the couple say their vows; they are married;
a guest raises a toast; the bell rings; Unity rises (+30); and the couple take **one family name**. A wedding the couple
cannot get to within two minutes, or that a monster or nightfall interrupts, is put off until the next day.

"A monster about" means one within 12 blocks of the spot that could really spoil it: one going for a player or
anyone on the team, an archer in plain sight, or a monster in plain sight, about level with the spot and out of the
water, that the couple could walk up to. A drowned in the river, a zombie shut in the animal pen or a skeleton in a
cave under the camp does not hold a wedding up. If a monster about (or a camp centre too cramped to stand in) keeps
the wedding from starting all morning, it is put off until the next day and everyone is told why.

A married couple are one **household**. Once the village builds homes, the village gives the household a home together.

### When it goes wrong

A couple going out or engaged whose friendship falls below 25 (after quarrels in low moods) call it off and stay
friends. A married couple only part below 10, which is rare; their children stay at home with the parent they live
with. People who have parted can find each other again, or someone new, in time.

## Family names

Everyone is given a family name the first time they are seen (Hart, Ashby, Fletcher, Nightingale and about a hundred
more), so "Fern Hart" and "Oak Ashby" become "the Hart family" when they marry. Children take their family's name.
Name tags still show first names only; family names appear in the commands and announcements.

## Babies

Once a minute, each married couple who could start a family has a small chance (about once in fifteen minutes) of
finding they are **expecting**; everyone is told, and the baby **arrives about an in-game day later**, beside a parent
at the camp and up at ground level there (never down the camp mine or in a cave under the camp: the baby waits until
a parent is back up). A couple can have a baby when:

- children are switched on (`children` in the settings);
- both are alive, grown up, **at the camp together** and at work there (not following anyone off);
- they married at least a day ago;
- their youngest is at least `daysBetweenChildren` days old (5 by default) and they have fewer than four children;
- neither is mourning a child;
- the team (named friends, newcomers, children and babies on the way) is under `maxPopulation` (30 by default);
- the camp has **food in store** (the camp's food need at most 0.4: well stocked, not just enough for today);
- both are at least in an **okay mood**;
- and there is **room at home**: a free bed in their village home; or, while the village has no homes at all, a spare
  place in the camp's finished cabin (nine places, for everyone on the team; the second cabin does not count, as
  nobody sleeps in it).

`/friends couples` says, for every married couple, what is still missing.

A baby arrives as a **child of the camp**: their own first name (from a list of their own, so no stranger you meet
later shares it), the family name, a child's skin, and a trade taken from a parent's trade or a parent's interest.

## Childhood

Children are drawn at a little over half size. For `childhoodDays` in-game days (10 by default) a child:

- **never works**, never fights and never changes a block. They do not follow players off adventuring (`/friends
  follow` leaves children out, with a note that they are too young, so a child is never fetched to you across the
  map), cannot join an expedition party, cannot be dismissed, and do not go through portals;
- **eats** when hungry, from the camp chest like anyone; a parent who has food brings some to a hungry child who has
  none on them;
- **plays** by day inside the camp: tag and hide-and-seek with another child, or chasing the chickens about (never
  touching them) and playing explorers on their own;
- **learns**: they follow a parent who is at work inside the camp, above ground (never down a mine), and watch from a
  few steps away, picking up skill in that work; if the village has a **teacher** at work, they go to school instead
  and learn their own trade;
- **goes home early**: from the end of the afternoon (time of day 11000) they go home, to beside their own bed in the
  family's village house (or just inside its front door), or to the cabin if the family has no house yet, and stay
  in; at nightfall they go to bed;
- **keeps near home**: a child who strays outside the camp, or ends up below ground under it (down a cave or the
  mine), comes straight back;
- **runs to a grown-up** when a monster within 12 blocks could get at them: one right beside them, or one in plain
  sight that is going for them or that they could walk up to (not one behind the cabin wall, in a fenced pen, in the
  river or in a cave below). They run to a parent about level with them first (never down into a mine after one),
  otherwise a fighter or a player, but only to someone further from the monster than they are, who is not fighting
  or on watch, and, after dark, who is indoors: a child never runs out of the cabin into the night. A child already
  indoors after dark does not go out at all, even to a parent under another roof: only a grown-up in the same room
  (a few blocks away, nothing in between) will do. With nobody like that, they go home, into their own house in the
  village (or the cabin, without a house), but only if home is further from the monster than they are; they stay
  put if they are already indoors at the camp. A sleeping child is woken only by the
  same rule as anyone asleep (a monster that could get at them). **Parents and Aegis** (and any warrior) go for a
  monster that is after a child, or right beside one with nothing in between, before anything else, if they are fit
  to fight; one who is asleep only gets up for a monster that is going for a child.

Children take food from you (right-click with food: they eat it if hungry, or keep it), but nothing else: tools,
weapons and building materials are for grown-ups.

## Growing up

After `childhoodDays` days a child **grows up** (once they are awake and standing somewhere with room for a grown-up):
full size, a grown-up's skin if theirs was a child's, a short celebration for everyone ("Pip Hart has grown up and
starts work as a farmer, and is already good at farming."), Unity +15, and from then on they are a full member of the
team who works at their trade with whatever skill they picked up as a child. They appear in `/friends newcomers` as
"born in the camp". **People born in the camp never count against the newcomer limit** (`maxSettlers`); only
`maxPopulation` limits births.

A grown-up born here has no tools at first: like any friend whose tool broke, they fetch or make one from the camp's
supplies.

## Hardcore

- A child who dies is gone for good, like anyone. Their parents mourn (and have no other baby for three days), and the
  whole camp mourns with them.
- A husband or wife who dies is mourned by their partner; the household stays together with the surviving parent.
  A widowed friend may, in time, love again.
- A sweetheart, fiancé or spouse who is dismissed leaves the romance behind them.
- A wedding or a baby on the way ends quietly if one of the couple dies.

## Skins

Everyone's skin now comes from a **skin list**, `assets/hardcorefriends/skins.json` inside the mod. Each skin has a
**number** (saved with the person), a **texture**, an **arm model** (`wide` for the classic 4-pixel arms, `slim` for
the 3-pixel arms) and **who may wear it** (`adult`, `child` or `any`; the nine friends' own skins are `named` and never
given to anyone else). Slim skins are drawn on the slim player model and wide ones on the wide model, for grown-ups
and children alike (armour and held items follow the arm model too).

Built in:

| Numbers | Skins |
|---|---|
| 0 to 8 | the nine friends' own (Fern, Oak, Flint, Scout, Spark, Aegis, Sage, Terra, Rowan) |
| 100 to 108 | the game's default skins with wide arms: Steve, Alex, Ari, Efe, Kai, Makena, Noor, Sunny, Zuri |
| 109 to 117 | the same nine with slim arms |
| 1000 to 1081 | the medieval village set: 62 grown-ups and 20 children, half wide and half slim (below) |
| 1082 and up | skins you add |

### Who wears what

- Newcomers (survivors, wanderers, settlers) get an `adult` or `any` skin; babies a `child` or `any` skin; a child who
  grows up in a `child`-only skin is given a grown-up's one.
- **Fitting skins.** A skin can carry **tags**: a trade or a place. A newcomer is more likely to wear a skin of a trade
  that goes with their work (a builder: carpenter or mason; a farmer: farmer, farmhand, shepherd or beekeeper; a
  warrior: guard or hunter; an explorer: traveller, wanderer, bard, hunter or fisher...). Someone met in a desert,
  snowy, jungle, swamp, savanna or dark forest biome has about a 2 in 5 chance of being dressed for it, while one of
  that land's skins is still free. A child growing up gets a skin to suit their trade the same way.
- **Everyone looks different.** A skin nobody in the loaded world is wearing is chosen first; only when every
  suitable skin is taken are skins shared. People in the same survivor camp get different skins. A camp is made as
  the world is generated, before its people can be checked against anyone else, so each camp stranger is checked
  when they first wake: if someone else in the loaded world already wears their skin, they are given a free one
  (and a new name, if someone living has theirs).
- **The default skins step back.** Once at least six added skins suit someone (true with the village set), the
  game's own Steve, Alex and friends are only used if nothing else suits. Anyone already wearing one keeps it.

### The medieval village set (1000 to 1081)

| Group | Skins | Tags |
|---|---|---|
| Tradespeople (30) | two each of baker, butcher, fisher, shepherd, beekeeper, mason, carpenter, blacksmith, tailor, teacher, doctor, shopkeeper, innkeeper, farmer, guard | the trade |
| Everyday villagers (20) | farmhands, travellers, a wanderer, a hunter, a miner, a scholar, a weaver, a bard, parents, grandparents, young adults | the trade where there is one |
| Children (20) | smocks, dungarees, jumpers, scarves, dresses (`child_` skins, worn only by children) | none |
| Biome villagers (12) | two each for desert, snowy, jungle, swamp, savanna and dark forest | the place |

Every one was checked before going in: 64x64, the right arm width, no see-through gaps in the body, no duplicates.

### Adding skins

Each skin is a normal **64x64 Minecraft player skin PNG** (the modern layout with the second layer: hat, jacket,
sleeves, trousers). Draw it for wide arms (4 pixels) or slim arms (3 pixels).

1. Put the PNGs in the mod's source folder:
   - `hardcore-friends/src/main/resources/assets/hardcorefriends/textures/entity/people/wide/` for wide-armed skins
   - `hardcore-friends/src/main/resources/assets/hardcorefriends/textures/entity/people/slim/` for slim-armed skins

   File names use only lower case letters, digits and underscores (`baker_girl.png`). Start the name with `child_`
   for a skin only children wear, or `adult_` for one only grown-ups wear; any other name may be worn by anyone.
2. Run `python3 tools/add_skins.py` in `hardcore-friends/`. It gives every new PNG an entry with the next free number
   from 1000 up (and skips, with a message, any file that is not a 64x64 PNG or has a bad name). The first word
   after `adult_` or `child_` (or the first word of a name with neither) becomes the skin's tag when it is a trade or
   place the mod knows: `adult_baker_rosa.png` and `adult_baker.png` are tagged `baker`, `adult_snowy_astrid.png` is
   tagged `snowy`, but `adult_rosa_baker.png` gets no tag, so put the trade or place straight after the prefix. Tags
   only matter on `adult` and `any` skins: children's skins are picked without them. Known trades: baker, butcher,
   fisher, shepherd, beekeeper, mason, carpenter, blacksmith, tailor, teacher, doctor, shopkeeper, innkeeper, farmer,
   guard, farmhand, hunter, miner, scholar, traveller, wanderer, weaver, bard. Places: desert, snowy, jungle, swamp,
   savanna, dark_forest.
3. Rebuild the mod (`./gradlew build`) and install the new JAR on the server and every player's game.

Or add an entry by hand, in the `skins` list of `skins.json`:

```json
{"id": 1082, "texture": "hardcorefriends:textures/entity/people/wide/adult_potter_june.png", "model": "wide", "for": "adult", "tags": ["mason"]}
```

`tags` is optional.

Rules: every `id` is used once; **never change or reuse a number** a world already uses (people remember their skin
by number); a mistake in the file never stops the game: a broken entry is skipped with a warning in the log, and a
person whose skin number is unknown is drawn in one of the default skins.

## Commands

All work at permission level 0 with cheats off and change nothing.

| Command | What it shows |
|---|---|
| `/friends family` | every family in the camp, by family name |
| `/friends family <name>` | one person: their partner, parents, children (with ages) and who they live with |
| `/friends couples` | every couple going out, engaged or married; the wedding day; and for the married, a baby on the way or what is still missing for one |
| `/friends relationships` | the closest friendships in the camp |
| `/friends relationships <name>` | one person's partner and closest friends, with friendship and romance |

Names are first names only (`pip`): a full name has a space in it, which the command will not take. The nine friends go by their own names.

## Settings

In `config/hardcorefriends.json`, under "Living together":

| Setting | Default | What it does |
|---|---|---|
| `romance` | true | Friends may fall for each other, go on dates, get engaged and marry |
| `children` | true | Married couples may have children |
| `childhoodDays` | 10 | In-game days a child takes to grow up (1 to 100) |
| `maxPopulation` | 30 | Most people on the team at once, children and babies on the way included (0 to 200) |
| `daysBetweenChildren` | 5 | Days a couple waits after one baby before another (1 to 60) |
| `relationshipSpeed` | 1.0 | How fast friendships and romances grow (0.25 to 4) |

## For the other packages

- `civic.Families` answers: the partner is the living **husband or wife** (couples only going out live apart);
  parents as recorded; children while alive; the household is a married couple and their children still young (a
  child's household is that of the parent they live with); the family name as carried; and the babies on the way
  (`babiesOnTheWay`), which the village counts towards `maxPopulation` before letting a newcomer join.
- The people package calls `Homes.roomForOneMore` before a baby is planned, `Homes.moveIn` for a newborn and for the
  household at a wedding, and `Homes.moveOut` only when someone dies or is dismissed. The spouse who moves out of a
  marriage that ended is still on the team: they get `Homes.moveIn(server, them, Set.of(them))`, a household of one
  that needs a bed in a home of its own, so the village should take them out of the old home and find them another.
- A child only does `needs.*` jobs, `navigation.*` jobs (getting unstuck must never be kept from a child; the edit
  guard refuses all their block changes anyway) and the jobs in `People.childJobs()` (play, learn, home time, stay
  close, weddings, idling, coming home, leaving the pen). A job that should be open to children too (going to their
  own bed) needs an id starting with `needs.`, or `People.allowChildJob(id)` at integration.
- The wedding uses a camp site whose id contains `town_hall` once it is completed; the school uses the profession
  `teacher` (through `civic.Professions`) at its workplace.

## Honest limits

- **Nothing here has been run in game yet.** It compiles; the numbers (how fast friendships grow, how often babies
  come) are first guesses and may need tuning once you have watched a few days.
- The pace is slow on purpose: a couple typically needs a week or two of in-game days from meeting to marrying, and
  a baby takes a day to arrive and `childhoodDays` to grow up. `relationshipSpeed` and `childhoodDays` speed it up.
- Relationships only change while the people are loaded together. Friends far away on trips, or a camp unloaded while
  you are away, make no progress.
- Without a village (no homes yet), babies need spare places in the finished cabin (nine places) for the whole team,
  which a camp with all nine friends alive does not have: the second cabin adds none, as the sleep job only uses the
  first. The village package's homes are the real way to make room.
- Dates and games are simple: walking to a spot, standing together, looking at each other or the sunset, with hearts.
  Tag and hide-and-seek are rough (children run about on the camp's own paths; hiding is crouching a few blocks away).
- Children speak their own lines for playing, learning, bedtime, fright, first words and growing up, but other
  everyday lines (eating, chatting, sleeping) are in their trade's grown-up voice.
- A wedding happens only while both of the couple are loaded at the camp in the late morning; with nobody at the camp
  it simply waits.
- A grown-up born here starts with no tool and has to fetch or make one.
- The skin list is read from the mod's own JAR: new skins mean rebuilding the mod and giving every player the new JAR.
  An entry whose texture path has a typo draws the game's purple-and-black "missing texture" (the tool writes the
  paths from the files themselves, so it cannot make that mistake).
- "Below ground" (for a child kept near home, and where a baby arrives) is a simple test: more than six blocks under
  the surface there and below home. A child in a very tall building on low ground may be walked home for nothing.
- A child is a smaller body and can slip under ledges and through gaps a grown-up cannot. They only grow up where a
  grown-up fits, but a parent may not always be able to follow them.
- If both of a child's parents die, the child stays on the team and is looked after by the camp as a whole (their
  needs jobs and the chest), with no one to learn from but a teacher.
