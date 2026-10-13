# Story overhaul: the saga as chapters

Players said the quests felt generic: "go there, beat 6 pirates, talk to someone". The saga
([STORY.md](STORY.md)) had a real plot, but the game told it one objective list at a time. This overhaul rewrites the
main story as **chapters**: each island's story is a short, authored adventure with people who talk, a problem you
uncover, a decision you make, and a town that remembers what you did.

Nothing was removed. The world, the islands, the notables, the systems, the assets and existing saves all stay; the
chapters are built on the quest system that was already there (every chapter step is an ordinary `QuestDef`).
Optional jobs (the job board, sea jobs, island jobs, generated island stories) are untouched, are shown as **Jobs**
in the journal, and never block the story. No new currency, meter or key was added.

---

## 1. How a chapter works

A chapter (`quest/arc/Arc.java`) has a number and title, an island, a cast (each with a one-line description and a
role in the story), the problem in a sentence, and its **steps**. A step is a quest chained to the next by `next`.
Steps are tagged with the beats they play, in order:

`arrival → cast → problem → discovery → escalation → decision → climax → aftermath`

**Scenes** (`quest/arc/Scenes.java`). A step can have a scene when it starts, when it ends, and when you talk to the
person it is about. A scene is a few lines, each spoken by someone with a name (a notable, you, the narrator, a role
such as "resident", or a named extra like "Old Tollan, the Tide Judge"). `DialogueScreen` shows one line at a time
with the speaker's name; click or press space to go on. A scene queued while you are fighting waits until you are
out of the fight (or half a minute), then opens on its own; talking to someone shows the queue first.

**Decisions.** Every chapter at full standard has one decision, made in a scene with the person it concerns: two
honest options, each with a cost. The option is stored as a flag on the character (`choice:<decision>=<option>`),
sends the chapter down that option's branch (a different step before the climax), and changes things you can see
afterwards (below). A decision is offered once and only real options count.

**Consequences** (`quest/arc/Consequences.java`), per player:

- *Help in the climax*: the side you chose shows up (a short effect with a line saying why: the townsfolk at your
  back, a rival's smoke, a wall of a man behind you).
- *Rewards that depend on the choice*: Beli, items, reputation with an island or a faction, paid once.
- *Shops*: a town you helped in a certain way sells to you cheaper (usually 15%), or sells something new (the
  Baratie's feast dishes).
- *Greetings*: the cast and the townsfolk/Marines of the island greet you differently during and after the chapter,
  and differently for each choice.
- *Cross-chapter*: a few choices pay off later (Whisky Peak's copied books seal the case in Alabasta; the Water 7
  blueprints hidden in your keel earn you something at Enies Lobby).

**Protected people** (`quest/arc/ArcGuard.java`). People a later scene needs cannot be lost by accident: givers,
allies, the missing and future recruits never die to stray blows and only yield to players. A chapter's villain
cannot be killed before you reach the fight with them: stray blows do nothing, and a player who is not there yet
only beats them into yielding (which still counts). Bosses with a boss fight definition keep their own rules.

**Places** (`quest/arc/ArcSites.java`). A step's objective can be bound to a place on its island (the harbour, a
notable's home, the wilds). The journal and the minimap then point at that exact spot, and for a fight the foes it
needs turn up there while you are near, so a step never waits on random encounters.

**Reliability** (`quest/Quests.java`, `quest/QuestEvents.java`). Progress is per player and saved with the
character (logout, death, knockdown, chunk unloads and dimension travel keep it). A step whose goal is already met
when it starts completes at once (an island already discovered, a boss already beaten). Rewards are paid once even
if completion is reported twice. Kills by your crew count for you, and one kill reported by several hooks counts
once. Finishing a step skips forward over steps that are already done.

**The journal** (`client/screen/QuestLogScreen.java`, `quest/QuestGuide.java`, `quest/arc/ArcJournal.java`). The
quest log separates **Story**, **Side stories** and **Jobs**. For a story step it shows the chapter ("Chapter 1 ·
East Blue — The Justice Levy"), **Next** (the one objective to do now), **Where** (a precise spot, or "Search area"
when only a region is known), **Danger** (the strongest foe of the step against you: "Dangerous — Mordecai
Gallowgate (boss)"), **Who** (the cast, the step's giver first), **Why** (the chapter's problem), the objectives and
rewards, and **The story so far** (one line per finished step, in the version of the choice you made). One objective
is tracked at a time.

---

## 2. The chapters

Twenty chapters are written to the **full standard** (scenes on every step, a decision, consequences, places,
protected cast, journal text). Eighteen later chapters are **listed**: the journal shows their chapter, cast, problem
and recap, but their quests keep their earlier shape (no scenes, decisions or consequences yet).

### Part I — the Blues (full standard)

Each of the four Ember islands opens with the same combat lesson (section 4), then leads into its sea's first
chapter. The East Blue continues for six chapters.

| # | Chapter | Island(s) | Cast | Decision: options | What changes |
|---|---|---|---|---|---|
| 1 | The Justice Levy | Shiokaze Isle → Shells Town | Old Fumio, Mio (missing), Petra (recruit), Lt. Hayashi, Capt. Gallowgate | `shells_plan`: call him out in the **square** / take the **ledger** to Hayashi | square: the town fights beside you, Shells Town 15% off, island reputation; ledger: Hayashi's Marines at your side, Marine reputation, 2 seastone cuffs. Shiokaze 10% off either way. |
| 2 | The Marmalade Siege | Orange Town | Carmen (recruit), Mayor Clementine, Rotgut | `orange_plan`: play the **festival** / cut the **fuses** first | festival: the music heals you in the fight, civilian reputation; fuses: the orchards are saved, food, Orange Town 15% off |
| 3 | The Clockwork Regatta | Syrup Village | Tomas (recruit), Gizmo | `syrup_helm`: Tomas takes the **helm** / jam the **keys** first | helm: speed in the race fight, a Log Pose; keys: Beli, Syrup Village 15% off |
| 4 | A Feast Under Siege | Baratie | Kenji (recruit), Horatio, Fosco the Gourmand | `baratie_plan`: serve a **forgery** / call the **squadron** | forgery: Kenji fights beside you, the Baratie sells two feast dishes, Saltfire curry; squadron: Marine sails on the horizon unsettle Fosco's men, Beli, Marine reputation |
| 5 | The Coral Keep | Cocoyasi | Kaimon (recruit), Gorgo | `coco_plan`: fight **side by side** with Kaimon / the villagers hold the **seawall** | side by side: Kaimon shields you, a Fish-Man Karate scroll; seawall: Beli, Cocoyasi 15% off |
| 6 | The Town of Beginnings and Endings | Loguetown | Odile (recruit), Bartolo, Capt. Valka, Tobias, Pim | `logue_plan`: stand on the **platform** / get the street to **shelter** | platform: a wanted poster, pirate reputation; shelter: Beli, Loguetown 10% off |
| 1 | The Scrap Baron (North Blue) | Fuyugane → Spider Miles → Lvneel | Sólveig, Nell, King Haakon, Rusty the Scrap Baron | `north_plan`: spike the **gunboats** / open the **pens** | gunboats: Lvneel is never raided, its king pays; pickers: they fight beside you, slingshot. Fuyugane 10% off. |
| 1 | The Swallow and the Crown (West Blue) | Kirinoha → Kano → Ilisia | Percival, Kagerou, Queen Isadora, the treasurer Corvel | `west_plan`: **pardon** the deserters / hold the **court** | pardon: they help in the fight, Ilisia 15% off; court: a fine cutlass, Ilisia reputation. Kirinoha 10% off. |
| 1 | The Ice House (South Blue) | Hinata → Karate Island → Sorbet Kingdom | Mahina, Master Kiko, King Gelato, Brinicle | `south_plan`: **storm** it with Kiko / wait for the **frost** | storm: Kiko fights with you, Tide Grappling scroll; frost: Beli, Sorbet 15% off. Hinata 10% off. |

### Part II — Paradise (full standard up to Sabaody)

| # | Chapter | Island | Cast | Decision: options | What changes |
|---|---|---|---|---|---|
| 7 | The Mountain That Flows Upward | Twin Capes | Old Wick (Wilhelmina Lanthorn), Barnacle Orrin | `capes_plan`: light the **lamp** / keep it **dark** | lamp: strength in the fight, Beli; dark: night vision, an Eternal Pose to Whisky Peak |
| 8 | The Gilded Welcome | Whisky Peak | Hugo, Ignatz (recruit), Mr. Doubloon, Miss Farthing | `whisky_plan`: give the books to the **lodge** / let Ignatz **copy** them | lodge: the hunters fight with you, cuffs; copy: Beli now, and more in Alabasta |
| 9 | The Seventy-Year Bake-Off | Little Garden | Astrid, Ylva, Hilde (recruit), Mr. Shilling | `garden_plan`: Hilde's **third dish** / **guard** the tasting | third dish: the duel ends, Sea King steaks; guard: Beli, meat |
| 10 | Panacea | Drum Island | Dr. Pippa (recruit), Raven (rival), Dr. Panacea | `drum_plan`: Raven's **smoke** / Pippa's **cure** | smoke: Raven fights beside you, but the medicine burns; cure: the village is cured, Drum 15% off |
| 11 | Salt in the Wells | Alabasta | Sefa (recruit), King Rashid, Madam Sovereign, Sir Barnaby Saltmarsh | `alabasta_plan`: hold the **spring** / hold the **stone** | spring: the capital keeps its water, Alabasta 15% off; stone: a poneglyph rubbing |
| 12 | The Debt and the Sky | Jaya | Aria (recruit), Felicity (rival), Usurio Shakkin | `jaya_plan`: **burn** every ledger / let Felicity steal them for a **deal** | burn: the whole town is free, Jaya 15% off; deal: Felicity fights beside you and pays you (Beli, an Impact Dial), but the town owes a cat now |
| 14 | Tug-of-Tides | Long Ring Long Land | Gunnar (recruit), Ayame | `ring_plan`: **expose** the stake / keep it **quiet** | expose: the herds go home, the camp cheers you on, Long Ring 15% off; quiet: Gunnar guards your back, Ayame keeps her pride and pays her bet (Beli, a whip) |
| 15 | The Three Guilds | Water 7 | Hollis, Kanna (recruit), Matthias (rival), Nimue, Brick | `water7_plan`: **burn** the blueprints / **hide** them in a keel | burn: the guilds fight with you, Water 7 15% off; hide: no help, Kanna's war hammer and Beli, and a Rokushiki scroll at Enies Lobby |
| 16 | The Gates of Justice | Enies Lobby | Nimbus (recruit), Kanna, Director Hanzaki, V-Adm. Kawaragi | `enies_plan`: the **tower** first / the **convoy** first | tower: you call off the Buster Call with Hanzaki's own den den mushi, his strongbox; convoy: the prisoners are safe first, civilian and Water 7 reputation |
| 17 | The Mirror-Monger | Thriller Bark | Mortimer (rival), Countess Kagamiya | `thriller_plan`: Mortimer's **graves** / the **front** gate | graves: surprise (strength), her sea chest; front: a hundred sailors at your back, civilian and pirate reputation |
| 18 | The Auction of Souls | Sabaody | Ume, Coralie, Edison, Gavel, Saint Pompadour, Adm. Zenith | `sabaody_plan`: **punch** through / **buy** them back | punch: rivals fight beside you, pirate and Revolutionary reputation; buy: civilian reputation, Sabaody 15% off |

Faction versions of a step ("`shells_1.marine`", "`enies_2.marine`", ...) are kept: they share the chapter's
scenes, and a few have their own (the Marine side of Enies Lobby and Sabaody).

### Listed chapters (journal only, earlier quest shape)

| # | Chapter | Where | Why it is listed only |
|---|---|---|---|
| 13 | The Storm Throne | Skypiea (White Sea) | Another dimension: the place machinery (sites, foes turning up) works on the ocean world only, and the existing Skypiea self-test checks its exact chain. |
| 19 | The Lantern in the Abyss | Fish-Man Island (deep sea) | Same as Skypiea. |
| 20–24 | The Jade Camellia, The Hermit's Silence, Descent, The Lamplighter's Execution, The Last Blade | Calm Belt, Impel Down, Marineford, Kuraigana | Not reached in this pass. |
| 25–37 | The Counterfeit Flame … The Saltfire | the New World | Not reached in this pass. |

---

## 3. The three core recruits

All three are recruited with the existing "recruit" conversation option (`CrewManager.recruit`): finishing a
chapter's recruitment step puts it on offer. Their crew roles and combat kits are unchanged; what is new is that
each has a chapter of their own.

### Petra "Pocket" Marlinspike — sniper (ranged)

- **Where / when:** Shells Town, chapter 1 (The Justice Levy). The first recruit of the game.
- **Look:** Marine dock-hand (skin `marine_1`), musket.
- **Personality:** quiet, dry, principled. "I don't take a shot unless it saves someone — that's my rule."
- **Goal:** to protect without killing; later, "A Clean Shot" at Sabaody (`personal_petra`).
- **Recruitment scene:** you meet her locked in the Marine base yard for refusing to fire on a crowd (`shells_petra`);
  she tells you where Mio is held and that Gallowgate means to march his prisoners to the gallows. After the climax
  she sits at the end of the pier with her musket and turns down her old post — differently depending on whether
  you called Iron-Collar out in the square or took the ledger to Hayashi (`recruit_petra`) — then Mio points you to
  Orange Town.

### Carmen "Brass Thunder" Trombetta — musician (support / medic)

- **Where / when:** Orange Town, chapter 2 (The Marmalade Siege).
- **Look:** town bartender-trumpeter (skin `villager_4`), Bell-Bell Fruit.
- **Personality:** loud, warm, theatrical; counts everything in fours.
- **Goal:** to play a song in every port in the world, and one at the end of it; later, a duet under the sea with
  Coralie (`personal_carmen`).
- **Recruitment scene:** her band scattered when Rotgut seized the orchards; you gather it back and get her little
  brother Pepe out of the still-house. After the climax she sits on the bandstand polishing a trumpet that's already
  gleaming and asks to come along — "a crew needs a band: somebody to keep the beat, and to put you back together
  afterwards" — in the festival or the fuses version.

### Kenji "the Carving Knife" Escoffier — swordsman (close range)

- **Where / when:** the Baratie, chapter 4 (A Feast Under Siege).
- **Look:** head waiter (skin `merchant_1`), two carving blades.
- **Personality:** impeccably polite, says please and thank you, has no patience for people who don't.
- **Goal:** to learn from Jinpachi, the Last Blade, "a cut that serves, rather than one that merely cuts" (`personal_kenji`).
- **Recruitment scene:** he fights the Gourmand's boarders beside you and presents the decision about the recipe
  book; afterwards he lays the tables, folding every napkin into a swan, and asks you to ask him.

### The other chapter recruits

Tomas (Syrup Village), Kaimon (Cocoyasi), Odile (Loguetown), Ignatz (Whisky Peak), Hilde (Little Garden), Pippa (Drum),
Sefa (Alabasta), Aria (Jaya), Gunnar (Long Ring), Kanna (Water 7) and Nimbus (Enies Lobby) each close their chapter
with a recruitment step and scene in the same way.

---

## 4. The combat lesson (opening)

The first step on each Ember island (`ember_east_1`, `ember_north_1`, `ember_west_1`, `ember_south_1`) is a lesson:
alongside the bandits, four `lesson` objectives — land a light blow, guard a blow, dodge, use a technique — reported by
`com.grandline.combat.Lessons` via `Quests.progress(sp, "lesson", target, 1)`. The objective text names the key to
press (`grandline.quest.obj.lesson.*`), the teacher comments on each part as you show it (`LessonCoach`), and elders
and masters offer a **Spar** option in conversation (`com.grandline.combat.Sparring.start`). The lesson can be done in
any order and alongside the fight.

---

## 5. Saves from before the chapters (migration)

`quest/arc/StoryMigration.java` runs once per character at login (flag `story_v2`):

- a quest under way whose objectives changed keeps the progress of every objective it still has (matched by type and
  target); lesson parts added to a step already under way count as shown;
- a fight already won in an old quest (its `defeat`/`arrest` objectives) is remembered (`beat:<who>`), and if that
  fight is now a later step of the same chapter, that step counts as done — so a player who beat Ayame in the old
  Long Ring quest is not asked to fight her again;
- steps that were inserted in front of a step the character has already reached are marked done, without rewards
  (decision branches and recruitment steps excepted: a decision skipped this way is simply not made, and recruitment
  stays on offer from the recruit as before);
- anything now complete is finished and paid the usual way.

Nobody is sent back and nobody is left without a next step. 32 changed quests are mapped (`StoryMigration.legacy`);
no quest id was removed.

---

## 6. Where things are

| What | Where |
|---|---|
| Chapter framework | `quest/arc/Arc.java`, `ArcBuilder.java`, `Arcs.java` |
| Chapters | `quest/arc/EastBlueArcs.java` (1–6), `BluesArcs.java` (North/West/South), `ParadiseArcs.java` (7–11), `SabaodyRoadArcs.java` (12, 14–18), `LaterArcs.java` (listed) |
| Scenes, decisions | `quest/arc/Scenes.java`, `client/screen/DialogueScreen.java`, `dialogue/Dialogues.java` |
| Consequences | `quest/arc/Consequences.java`, `dialogue/Shops.java`, `npc/notable/NotableHooks.java` (greetings, looks, protection) |
| Journal | `quest/arc/ArcJournal.java`, `quest/QuestGuide.java`, `client/screen/QuestLogScreen.java` |
| Text | `lang/en_us/story_overhaul*.json` (framework, east, blues, paradise, sabaody, later) and `story.json` |
| Tests | `test/StoryOverhaulTests.java` (`GL_SELFTEST=story_arcs`) |

Text keys: scenes `scene.grandline.<step>.<start|end|talk>[.<choice>].<n>` = `speaker|line`; decision options
`scene.grandline.choice.<decision>.<option>`; recaps `recap.grandline.<step>[.<option>]`; chapter title, problem and
cast `arc.grandline.<chapter>[.why|.cast.<who>]`; greetings `arc.grandline.<chapter>.<during|after>.<who>[.<option>]`
and `...after.<townsfolk|marines>.<island>[.<option>]`; places `arc.grandline.site.<name>`.

**Writing a new chapter:** add a method to a chapter file with `arc(...)`, `cast(...)`, one `step(q(...)..., beats)`
per step (objectives, rewards, `next`, `site(...)` for any fight), one `decision(...)`, and its `Consequences`
lines; write its text; run `GL_SELFTEST=story_arcs`, which checks every chapter's text, beats, decision, places and
plays it to the end on each option.

---

## 7. Tests

`GL_SELFTEST=story_arcs` (StoryOverhaulTests) checks, on a fresh world:

- every chapter's data and text (title, problem, part, cast lines, beats in order, scene speakers, decision labels,
  recaps, place labels, consequence lines) and that every kill objective of a full chapter has a place;
- the combat lesson's objective wording, and every quest's description (`questTextCheck`);
- the opening played by a fake player: lesson first in line, kills counted once each, progress through an NBT round
  trip (logout) and a knockdown, early completion and rewards paid once, the decision offered in a scene and only
  once, protected people, a boss already beaten counting at once, the journal's chapter/who/why/next/where/danger/
  recap, the town's help in the climax, the boss credited through `QuestEvents.onKill`, the town changed afterwards
  (greeting, prices, Petra's recruitment, the lead to Orange Town), and the other option's branch and Marine variant;
- every full chapter played end to end on each option, with a scene on every step and a recap afterwards;
- save migration (old Shells Town, Orange Town, Ember, Sorbet and Long Ring saves).

Last run (this branch): `story_arcs` 19 passed, 0 failed — 40 chapters (20 at full standard), 403 scenes, 905 scene
lines, 20 decisions, 106 consequences; 40 playthroughs, 316 steps; 32 changed quests mapped. `quests` 5 passed, 0
failed.

---

## 8. Known gaps

- Chapters 13 and 19 (Skypiea, Fish-Man Island) and 20–37 are listed, not written to the full standard.
- The practice bout (`com.grandline.combat.Sparring`) is a stub on this branch that always says "can't spar right
  now"; the combat branch provides the real one.
- The `stories` self-test phase (SelfTest/StoryTestsExtra, outside this work's files) predates the `lesson` and
  `choice` objective types; three of its checks need a line each to know them (see the branch report).
- Dimension travel is covered by the character data being saved on the player, but no test travels between
  dimensions mid-step.
- Consequences change greetings, prices, stock, rewards and help in fights; no notable moves to a new place because of
  a choice.
- Nothing here was play-tested by a person; the results above are the automated self-tests.
