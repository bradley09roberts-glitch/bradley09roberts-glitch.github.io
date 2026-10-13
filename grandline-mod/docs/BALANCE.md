# Balance

All combat numbers for foes come from one file: `src/main/java/com/grandline/balance/Tuning.java`. This page explains
the rules it follows and gives the table measured by the self-test (`GL_SELFTEST=overhaul`, which writes
`balance-table.md` next to the server).

## The rules

**Two inputs only.** A foe's health and the size of its blows depend on its *encounter tier* (1–10, from the sea it
lives in) and its *rank*. Nothing depends on the player who happens to fight it, so levelling up, better weapons,
Haki and a crew always make fights easier. There is no auto-scaling.

| Rank | Who | Light blows of the reference character to fell it | Its light blow (share of the reference character's health) | Most one unannounced blow can take from a player |
|---|---|---|---|---|
| Minion | bandits, deckhands, cowardly rabble | about 2.5 (2–3) | 9 % | 22 % |
| Standard | Marines, guards, Fish-Man soldiers, bounty hunters | about 4.5 (4–5) | 11 % | 25 % |
| Elite | officers, captains, named characters, giants | about 10 | 12 % | 32 % |
| Boss | story bosses (phases, ground-marked attacks, boss bar) | about 45 | 13 % (ordinary blows) | 40 % (55 % for an attack announced by a ground marker) |

A weapon adds 10 % to a foe's blows; a heavy blow is about 2.2× a light one and is always announced (red flash, 0.6 s).
Foes' techniques are scaled so a typical one lands for about two light blows; big ones still meet the cap.

**The reference character** (a yardstick, never read from a live player): the level each sea is meant for, a human
who put a third of their points into Endurance and a third into Strength, a common weapon of that sea, and weapon
mastery of half their level.

| Tier | Seas | Level | Health | Weapon | Light blow |
|---|---|---|---|---|---|
| 1 | East Blue (starter isles) | 1 | 21 | cutlass (6) | 6.3 |
| 2 | the Blues | 8 | 30 | cutlass (6) | 8.2 |
| 3 | Paradise | 16 | 42 | fine blade (7.5) | 12.1 |
| 4 | Paradise | 26 | 56 | fine blade (7.5) | 15.0 |
| 5 | Paradise | 36 | 70 | fine blade (7.5) | 18.3 |
| 6 | New World | 46 | 84 | skilful blade (9) | 23.3 |
| 7 | New World | 56 | 98 | skilful blade (9) | 26.9 |
| 8 | New World | 68 | 114 | skilful blade (9) | 31.3 |
| 9 | New World | 80 | 132 | supreme blade (12) | 39.7 |
| 10 | New World | 92 | 148 | supreme blade (12) | 44.7 |

**Story bosses fight one tier above their island**, whatever their lore rank, so each arc's boss can be beaten by a
character who reached that arc (Gallowgate is a Shells Town boss, not a Paradise one). Named characters who are not
bosses keep their own tier: a Vice Admiral visiting the Baratie is meant to look and be overwhelming, and the target
frame and a warning say so before the first blow.

**Difficulty.** `serverconfig/grandline-server.toml`, `[balance] difficulty`:

| Setting | Foes' blows | Foes' health | Foes pressing you in melee at once | Per-blow caps |
|---|---|---|---|---|
| auto (default) | follows the world's difficulty: Peaceful/Easy = Adventure, Normal = Standard, Hard = Hard | | | |
| adventure | ×0.70 | ×0.85 | 1 | ×0.85 |
| standard | ×1.00 | ×1.00 | 2 | ×1.00 |
| hard | ×1.30 | ×1.15 | 3 | ×1.15 |

The mod's damage types no longer scale with the vanilla difficulty as well, so this is the only multiplier.

**Fairness rules** (combat/Hits, npc/NpcCombatGoal, npc/AttackSlots):
- At most two foes (one on Adventure, three on Hard) press a melee attack on you at once; the rest circle a few steps
  out and wait, and a foe steps back after three swings so the pressure rotates. Bosses ignore this.
- A stagger holds a player at most 1.5 s (2.5 s for an announced boss attack) and is followed by 2 s in which no new
  stagger can land: no stun-locks.
- A foe only swings, shoots or uses a technique when its blow can actually reach you (line of effect). It searches
  where it last saw you and gives up after 8 s; settlement folk chase no further than 64 blocks from home; a foe that
  makes no progress towards you gives up.
- Foes that are dangerous or overwhelming for you warn you over the hotbar before their first blow.

**Players.** Trained fists (an unarmed fighting style with empty hands) count as a common blade (+5), so unarmed
fighters keep pace with sword users.

## Measured table

The table below is produced by the self-test (Standard difficulty): a real reference character (a test player with
the stats above and the weapon of the sea) fells each foe with light blows through the full damage pipeline (armour,
modifiers, line of effect), and takes one light and one heavy blow from it.

| Tier | Ref. level | Rank | Role | NPC health | Light blows to fell | Ref. light blow | Ref. health | Foe light blow | Foe heavy blow |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 1 | MINION | bandit | 14 | 3 | 6.3 | 21 | 10 % | 22 % |
| 1 | 1 | STANDARD | marine | 26 | 5 | 6.3 | 21 | 12 % | 25 % |
| 1 | 1 | ELITE | pirate_captain | 63 | 11 | 6.3 | 21 | 18 % | 32 % |
| 2 | 8 | MINION | bandit | 19 | 3 | 8.2 | 30 | 10 % | 22 % |
| 2 | 8 | STANDARD | marine | 34 | 5 | 8.2 | 30 | 12 % | 16 % |
| 2 | 8 | ELITE | pirate_captain | 82 | 11 | 8.2 | 30 | 13 % | 32 % |
| 3 | 16 | MINION | bandit | 28 | 3 | 12.1 | 42 | 8 % | 22 % |
| 3 | 16 | STANDARD | marine | 51 | 5 | 12.1 | 42 | 12 % | 16 % |
| 3 | 16 | ELITE | pirate_captain | 121 | 11 | 12.1 | 42 | 17 % | 32 % |
| 5 | 36 | MINION | bandit | 42 | 3 | 18.3 | 70 | 7 % | 22 % |
| 5 | 36 | STANDARD | marine | 77 | 5 | 18.3 | 70 | 12 % | 25 % |
| 5 | 36 | ELITE | pirate_captain | 183 | 11 | 18.3 | 70 | 17 % | 32 % |
| 7 | 56 | MINION | bandit | 62 | 3 | 26.9 | 98 | 8 % | 22 % |
| 7 | 56 | STANDARD | marine | 113 | 5 | 26.9 | 98 | 12 % | 16 % |
| 7 | 56 | ELITE | pirate_captain | 269 | 11 | 26.9 | 98 | 17 % | 32 % |
| 10 | 92 | MINION | bandit | 102 | 3 | 44.7 | 148 | 8 % | 22 % |
| 10 | 92 | STANDARD | marine | 188 | 5 | 44.7 | 148 | 12 % | 16 % |
| 10 | 92 | ELITE | pirate_captain | 447 | 12 | 44.7 | 148 | 17 % | 32 % |

Boss estimate (computed, not measured): a story boss has 45.0 light blows of the reference character of its encounter tier; at one landed blow per 1.5-3 s with its guard and phases that is roughly 1-2.5 minutes for a prepared character.

The two foe-blow columns vary by a few points between runs: each foe rolls a weapon from its role's list, and weapons
have different strike strengths (the probe takes one light and one heavy blow). Heavy blows show the rank's cap where
the raw blow would be larger.

## Before the overhaul

Computed from the previous formulas (not measured in play): a tier-1 bandit had 22 health and hit with
`2 + 1.2 × tier` plus its weapon (a cutlass added 5), about 8 per light blow — 40 % of a new character's 20 health,
and a heavy blow could take more than all of it. The stop-gap in 0.2.0 scaled tier-1 foes' damage by 0.45 (about 18 %
per light blow); several bandits at once could still chain a new character down. A story boss's health was
`40 + 18 × tier²` from its lore tier: Gallowgate (lore tier 3) had 202 health and hit with weapon damage on top of
`2 + 1.4 × tier`.
