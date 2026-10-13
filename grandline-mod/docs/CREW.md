# Crew companions

Recruited story characters are companions. They follow you, fight beside you and come with you between islands. The
system is built to make you stronger without carrying you. It adds no new currency, no new meter and only one menu,
the roster.

Code: `npc/crew/` (CrewManager, CompanionGoal, CompanionTargetGoal, CompanionCare, CompanionOrders, CompanionCredit,
Companions, CompanionRole), `balance/CompanionTuning.java`, `client/screen/CrewScreen.java`. Tests: `GL_SELFTEST=crew`
(`test/CrewTests.java`).

## Roles

Every recruitable has a fighting role, derived from their crew job. The job keeps its ship bonus: a sniper still makes
the cannons hit harder and a musician still restores stamina at sea. As before, ship bonuses count the whole roster,
active and reserve.

| Role | Crew jobs | In a fight | Rank-3 technique |
|---|---|---|---|
| **Fighter** | swordsman, swordswoman, fighter, bodyguard, warrior, shipwright, cook, spy | Close range. Goes for whoever is attacking you, nearest to you first. Drops its current foe for one that turns on you. Holds attention: foes it hits turn on it. Stands beside and ahead of you. | **Rallying Challenge**: up to three foes going for you turn on the fighter (20 s cooldown). |
| **Marksman** (ranged) | sniper, lookout, quartermaster, helmsman, engineer, stargazer | Keeps 6–12 blocks back, behind you when it can. Shoots only with a clear line, and moves for one instead of firing into walls. Focuses your target. | **Pinning Shot**: a heavier shot that staggers and slows (15 s cooldown). |
| **Support** | musician, doctor, navigator, archaeologist | Stays at your side and fights only what comes close (a support with a ranged weapon shoots). Heals and revives. | **Encore**: once per fight, when two or more allies are below 60 %, everyone within 8 blocks recovers 10 %. |

The three early companions:

- **Petra Marlinspike** (Shells Town): a marksman with a musket.
- **Carmen Trombetta** (Orange Town): support. A musician whose songs patch people up.
- **Kenji Escoffier** (Baratie): a fighter with twin blades.

Companions also use their own style, weapon and fruit techniques, but sparingly:

- never in the first 8 seconds of a fight (Pinning Shot: 5 s), so they open with ordinary blows and a short fight stays
  yours;
- after that, at most one every 12–18 seconds;
- only in range and with a clear line.

Some weapon techniques, such as the rifle volley, deal several blows' worth at once. Fired the moment a fight opened,
one of them decided short fights on its own; the opening rule is there to stop that.

## Active and reserve

- The roster holds up to **18** members.
- At most **2** are **active**: they follow, fight and travel with you.
- The rest are in **reserve**. They wait where you left them (always a safe place), or aboard.
- A recruit joins the active pair if a slot is free; otherwise they join the reserve and wait where you recruited them.
- **Swaps** happen only at a **safe place** and **out of combat**. This covers swapping a reserve member in, sending an
  active one to rest, or exchanging the two. A safe place is:
  - inside a settlement (its streets and plots, the area town protection covers), or
  - aboard your own ship.
- Outside a safe place, or during a fight, you get a short message instead and nothing changes.
- A reserve member who joins comes to you:
  - if their body is loaded nearby, they walk over;
  - if it is loaded far away or in another dimension, they are brought over;
  - if it is unloaded, they are rebuilt next to you from their last snapshot (see Persistence).

## Commands

| Command | Where | What it does |
|---|---|---|
| **Follow** (default) | roster, dialogue | Keep formation: fighter beside and ahead, marksman behind, support close. |
| **Hold** | roster, dialogue ("Wait here for me") | Stay at this spot. Fight only foes that come within about 10 blocks of it. |
| **Focus** | roster, **N** | Every active companion attacks the foe you are looking at (within 32 blocks, never through walls). Failing that, your lock-on target or whatever you are hitting. A neutral is refused ("no foe of ours"). The order lasts 20 s or until the foe falls. |
| **Regroup** | roster, **N** | Break off at once and come back, including those holding a position. A companion more than 16 blocks away and out of your sight is brought next to you. |
| **Swap / Rest / Join** | roster, dialogue ("Wait here in reserve" / "Come with me again") | Change who is active (safe place, out of combat). |
| **Dismiss** | dialogue | They go home. They can be recruited again and remember their bond. |

Every order is answered with a short line from each companion it concerns.

### Keys

- **V**: crew roster. It lists the active pair and the reserve with role, health and bond rank. Buttons: Follow/Hold,
  Rest, Join or "⇄ name" swaps, Focus and Regroup. Swap buttons are greyed out away from a safe place or in a fight.
- **N**: quick order. If you are looking at a foe, the active companions focus it; otherwise they regroup.

At a ship's helm (riding the ship), V and N keep their gun-order meaning (fire at will, ammunition). Both keys can be
rebound.

## How companions behave

- **They only fight foes of the party**, never neutral or friendly people. A foe is:
  - whoever is attacking you or a crewmate, or hit one of you lately;
  - aggressive or lawful people who are hostile to you on sight;
  - villains;
  - monsters.

  A villager you shoved is not a foe. Neither are the downed, the surrendered or the arrested.
- **Not too far.** They engage foes within 16 blocks of you and drop any foe that gets more than 24 blocks away from you
  (36 for a focused one). In reserve, or when you are away, they only defend themselves.
- **Same rules as everyone.** Every blow, shot and technique goes through the shared combat code. Line of effect (no
  hits through walls), guard, dodge, Haki and fruit rules apply. Fighters take melee slots like everyone else.
- **No friendly fire.**
  - Their blows and area techniques never land on you or on a crewmate. Bystanders are spared too.
  - Your blows never hurt them. The knockback, slowness, fire and stagger a blocked friendly blow would leave are also
    undone.
- **Getting around.**
  - They path around obstacles and open wooden doors in their way.
  - When far away (over 20 blocks) and out of your sight, or stuck for about six seconds, they are brought next to you.
    This only happens when you stand on solid ground and they are not fighting.
  - Over 40 blocks away and idle, they always catch up.
- **Your knockdown.** If you are knocked down, a companion that is not locked in melee pulls you up: 2.5 s, or 1.25 s
  for a support.

## Healing (support)

- **A song pulse:** restores **25 %** of the most hurt ally's health. The ally can be you or a crewmate within 8 blocks
  below 75 %.
- **Cooldown:** 30 s between pulses.
- **Limit:** at most **3 per fight**.
- **Encore** (rank 3): one extra pulse per fight, described in Roles above.
- **When a fight starts and ends:** a fight starts when anyone attacks the party or the party attacks someone. It ends 5 s
  after the last of that. Each new fight gives fresh songs.
- **Rest:** out of a fight, every companion recovers 2 % of its health every 2 s.

## Downed, not dead

- A blow that would kill a companion leaves it **downed** instead. It kneels at 1 health. It takes no damage, and foes
  stop targeting it and do not pick it again.
- **Getting back up:**
  - **You** help it up by holding **[use]** on it for about 1.5 s. It stands with 35 % health.
  - A **support** companion revives a downed crewmate twice as fast (1.25 s).
  - With nobody around, it gets up **on its own about 10 s after the fight** with **30 %** health.
- **Never permanent in normal play:**
  - Only commands (/kill) end a companion.
  - The void puts it back next to you, or near where it was posted.
  - Seastone cuffs never go on your own crew or on a downed companion.

## Bond

Bond rank goes from 1 to 5. Points:

- **+1** per fight won together (the party felled at least one foe), for each active companion who was there.
- **+2** per story quest finished with them at your side. This counts every non-repeatable, non-generated quest you
  finish while they are within 48 blocks.

| Rank | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|
| Points | 0 | 3 | 8 | 15 | 25 |

Each rank above 1 adds **+4 %** to blows and **+5 %** to health. Rank 3 unlocks the role technique. Bond is saved with
the crew and kept if you dismiss a companion and recruit them again.

## Balance

Numbers come from `balance/CompanionTuning.java`, not from the enemy tables. They scale with the captain's level,
interpolated between the reference levels of the seas.

- **Blows:** 40 % of the reference character's light blow at the captain's level (`Tuning.referenceBlow`). Techniques
  scale from the same power.
- **Health:** the reference character's health (`Tuning.referenceHealth`), times 1.3 for fighters and 1.0 for marksmen
  and supports. That is enough for a standard fight: an ordinary foe's light blow takes about 11 % of it.
- **Rank:** companions are never an elite rank. They count as standard wherever a rank matters (`Tuning.rank` delegates).
- **Enemies are unchanged.** No enemy number depends on how many companions you have. Recruiting only makes fights
  easier.

`GL_SELFTEST=crew` measures the ticks to fell a foe in three set-ups and prints the companion numbers per level. One run
on the final code gave the values below.

Setup:

- The captain is level 16 and its blow is set to the reference blow, 12.6. The test captain is a fake player that
  swings whenever a blow is allowed.
- Petra and Kenji are at bond rank 1.
- The foe is pinned in place and does not fight back.

Fights vary from run to run, so treat these as one sample.

| Foe | Captain alone | Captain + Petra + Kenji | Petra + Kenji alone |
|---|---|---|---|
| Standard (tier-3 Marine, 53 health) | 13 ticks (4 blows) | 8 ticks (captain 39 damage, Petra 17) | 32 ticks |
| Sturdy (10 reference blows, 126 health) | 40 ticks (10 blows) | 28 ticks (captain 96, Petra 17, Kenji 20) | 85 ticks |

In a shared fight the companions dealt about 30 % of the damage. On their own they took about twice as long as the
captain alone.

Companion numbers per captain level (from the same run):

| Captain level | Blow (companion / reference) | Fighter health | Marksman and support health |
|---|---|---|---|
| 1 | 2.5 / 6.3 | 27 | 21 |
| 16 | 5.0 / 12.6 | 55 | 42 |
| 46 | 10.5 / 26.2 | 109 | 84 |
| 92 | 21.1 / 52.8 | 192 | 148 |

## Credit

A kill by a companion counts as the captain's own:

- quest and career objectives (`QuestEvents.onKill`), counted **once**, because the final blow decides who is credited;
- reputation, bounty, and the reward for outlaws.

Experience: all of it if you also struck the foe in the last 5 s, half if the companion felled it alone. You must be
within 64 blocks. When a companion beats a notable who yields, that counts for your "defeat" objectives (as before).

## Persistence

- **Saved per character** (`grandline_crews`). A permadeath disbands the crew and everyone goes home. A knockdown or a
  non-permanent death changes nothing: they come back to you when you respawn.
- **Travel.** When you sail more than 96 blocks away, change dimension or log out, the active pair is taken **aboard**:
  their body is saved as data. They step off next to you once you stand on solid ground again, and only where entities
  are already live. A body placed in a chunk that is still loading would stay invisible for a moment, be taken for lost
  and be rebuilt a second time.
- **Holding companions** stay where you posted them. Regroup calls them back.
- **No duplicates.** A companion lost with an unloaded chunk is rebuilt next to you from its last snapshot (taken every
  10 s) with a fresh body. The stale copy is refused when its chunk loads, through the same check as for every story
  character.
- **Dismissed** companions go home: they walk back if their home is near, otherwise they reappear there. They can be
  recruited again.

## Save compatibility

Format 2 adds the following fields. Missing fields load as defaults.

| Level | Fields |
|---|---|
| Per member | `active`, `order` (follow/hold), `hold`, `bond`, `capLevel`, `downed` |
| Per crew | `v`, `quests`, `former` |

A crew saved before this version (no `v`) loads like this:

- the members who were following become active, in recruitment order, up to two;
- if fewer than two were following, those who were told to wait fill the free slots;
- everyone else goes to reserve, staying where they were;
- `waiting: true` becomes the Hold order;
- bond starts at rank 1.

The `waiting` field is still written for older readers.
