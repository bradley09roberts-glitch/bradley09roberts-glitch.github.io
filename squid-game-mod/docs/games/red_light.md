# Game 1 - Red Light, Green Light

Package names: `core/redlight/` (`RedLightRules`, `DollCycle`), `game/redlight/` (`RedLightGreenLightGame`,
`RedLightNpcBehavior`), arena `build/arena/RedLightBuilder` (`ArenaId.RED_LIGHT`), entities `DollEntity` + `GuardEntity`.
Test it alone with `/squid debug play red_light 40`.

## Rules (as shown to the players in the instructions phase)
* Cross the field (130 blocks) to the finish line before the time runs out (Normal 200 s, Hard 185 s, Extreme 165 s).
* The giant doll chants "mu-gung-hwa kko-chi pi-eot-seum-ni-da" (ten syllables, a different tempo every cycle) with her back to
  the field: **green light, move freely**.
* When the last syllable ends she turns. You have a **stopping allowance** to come to a halt:
  Normal 1.1 s, Hard 0.65 s, Extreme 0.3 s. Her eyes light up the moment the allowance ends: **eyes lit = red light**.
* While the eyes are lit, **any movement you cause eliminates you**: horizontal motion above a small threshold while you hold a
  movement key (0.035 / 0.025 / 0.018 blocks per tick), or leaving the ground with your own jump. Turning the camera never counts.
  Standing against a wall while holding a key is fine (you do not move). Knock-back, being shoved by a neighbour, the
  momentum slide after releasing the keys are tolerated while you are not pressing a movement key and the displacement stays
  small (< 0.32 blocks/tick); an unexplained large displacement (teleport, speed hack) is flagged illegal. If you are in mid-air
  when the eyes light you may land; leaving the ground again by jumping is a violation.
* A guard takes aim at the first contestant who breaks the rule and shoots (staggered when several break it together).
  Everyone across the line is safe; anyone who has not crossed when the time is up is eliminated.
* The same `RedLightRules` judge humans and NPCs; the chant length distribution depends on the difficulty (shorter and faster
  chants on Hard / Extreme) and gets slightly faster as the field advances.

## NPC contestants
`RedLightNpcBehavior` only uses what a human could perceive (`PublicView`: the chant they hear, the doll turning, the eyes lit,
shots they witness):
* **reaction speed** sets the delay between seeing the turn and braking;
* **caution / patience / risk tolerance** set the safety margin: cautious runners brake before the chant ends (estimating the end
  from the tempo of the syllables heard so far), reckless ones run to the last syllable;
* **skill** sharpens the estimate of the chant end (error of a few ticks, smaller with skill) and lowers the chance of a mistake;
* **courage** decides how much a nearby elimination shakes them: hesitation after green (resume delay), fear that lengthens
  every margin, flinches (`shocked`), panic freezes;
* human-like imperfections: attention lapses (a turn noticed late, rarer for skilled/brave runners, doubled while frightened),
  stumbles while running, involuntary wobbles while frozen;
* after crossing they celebrate or cry according to their personality (`CELEBRATE_FIST`, `SOB`, `CELEBRATE`).
Crowding is handled by lanes (each NPC keeps its own lane x), vanilla collision pushes and the entity's stuck recovery.

## World
The doll stands on her plinth at the far end (`redlight.doll`, yaw 0: she faces the tree, back to the field); she turns her
head (not her body), her eyes glow while they are lit, the chant is sung from her position (audible across the whole field),
servos and lock-on sounds accompany the turn. Guards on towers and along the walls (`guard.post`, triangle rank = armed). HUD:
big traffic light (green / amber / red), banner, counter "Across the line", timer.

## Balance and verification (integration)
NPC-only games with 100 contestants (`/squid debug play red_light 100 <difficulty>` at `/tick rate 100`, final build), share of the field
that crosses the line in time:

| | Normal | Hard | Extreme |
|--|--------|------|---------|
| survivors (runs) | 77, 83 (and 75 - 91 in full tournaments) | 61, 65 (and 63 - 66) | 41, 42 (and 34 - 50) |
| typical share | about 80 % | about 63 % | about 42 % |

Deaths are mostly misjudged stops: on Extreme the 0.3 s allowance is shorter than most reaction times, so only runners who anticipate the
end of the chant (and guess its tempo right) stop in time; on Hard and Normal attention lapses (a turn noticed late, 8 - 26 ticks, more
frequent for unskilled and timid contestants, scaled 1.3 / 2.4 / 1.5 by difficulty) and involuntary wobbles do most of the damage; a
few contestants run out of time only when they hesitate a lot after witnessing shots. Runs with 456 NPCs, a reset in the middle of the
game, a server restart and two human contestants (one moved on red and was shot, the other stayed alive) behaved as described above.
Two tuning notes from the verification: far NPCs used to move at a third of their speed (their movement is now renewed every tick,
whatever the behaviour's decision rate), and vanilla's `Mob.setSpeed` made the pace quadratic in the attribute (overridden).

