# Game 1 - Red Light, Green Light

Package names: `core/redlight/` (`RedLightRules`, `DollCycle`), `game/redlight/` (`RedLightGreenLightGame`,
`RedLightNpcBehavior`), arena `build/arena/RedLightBuilder` (`ArenaId.RED_LIGHT`), entities `DollEntity` + `GuardEntity`.
Test it alone with `/squid debug play red_light 40`.

## Rules (as shown to the players in the instructions phase)
* Cross the field (130 blocks) to the finish line before the time runs out (Normal 190 s, Hard 165 s, Extreme 145 s).
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
