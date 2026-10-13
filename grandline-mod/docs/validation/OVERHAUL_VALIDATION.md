# Validation report — 0.3.0 gameplay overhaul

Everything below was run on this machine (4 shared cores, 15 GB) against the code on branch
`claude/friendly-darwin-gv73pq`. Nothing here is a human playtest: **no person has played 0.3.0 yet**. "Passed"
means an automated check passed; "client capture" means a real Minecraft client was driven by the test harness and the
screenshot was looked at.

## Passed

### Self-tests on the final merged code

| Suite | Result | What it covers |
|---|---|---|
| Full regression, phase 1 (`GL_SELFTEST=1`, fresh world) | 706 / 706 | every system of the mod, the overhaul checks included |
| Full regression, phase 2 (`GL_SELFTEST=2`, after a restart) | 21 / 21 (one more check runs only when a random market raid in phase 1 leaves a second good missing; this run it did not) | persistence after a real server restart |
| `overhaul` | 17 / 17 | line of effect with real blocks (open ground, stone wall, closed and open door, corner, waist-high cover, glass pane, floor between levels), sight for lock-on, blasts, beams, projectiles (wall, fast shot at a pane, piercing then a wall), an NPC never hurting a target it cannot reach and giving up, at most two melee attackers, no stun-lock, the per-blow cap, the target frame's identity/relationship/threat, guarding counts for the lesson, sparring, the measured balance table, the registries, the cost of the cover check |
| `crew` | 34 / 34 | roles, active limit and reserve, swaps (refused at sea and mid-fight, accepted in town), follow/hold/focus/regroup, no attack on neutrals, kill credit once, XP sharing, downed and revived, getting up alone, healing limit, no friendly fire, no hit through a wall, stuck recovery, old-format crew data, bond saved, dismiss and re-recruit, logout and return, Nether and back, recall without duplicates, disband |
| `story_arcs` | 19 / 19 | 40 chapters (20 at full standard), 445 scenes, 974 lines, 20 decisions, 106 consequences; every full chapter played to the end on each choice (40 playthroughs, 316 steps); early completion; idempotent rewards; journal fields; save migration (32 changed quests) |
| `stories` | 78 / 78 | the earlier story, island-story and job checks |
| `quests` | 5 / 5 | every quest (3518) has a description and readable objectives; routes; shipwrights |
| `careers` | 38 / 38 | faction careers (re-run after the conversation fix below) |

### Real-client captures (screenshots in `docs/captures/overhaul/`)

- Target frame on a bandit (`· name, Bandit · Wild, 15/15, ⚔ Hostile, = Matched`) and on a tier-10 Marine captain
  (`◆ ..., Marine Captain · Marine, 511/511, ⚔ Hostile, ▲▲ Overwhelming`); the first-fight card.
- The controls page with the bound keys, the fight loop and the tells.
- A sparring bout (partner with name tag, frame showing "Sparring").
- The crew roster: two active (Petra marksman, Carmen support) with Hold / Rest, Kenji (fighter) in reserve with swap
  buttons, "In combat: no swaps now"; companions following.
- The opening chapter's first scene ("Fumio Tsunatori 1/7") and the journal (chapter, next step, where, danger, who).

### Upgrades of existing saves (clean NeoForge 21.1.259 server, release jars)

- A 0.2.0 world: starts, runs and saves with 0.3.0 — 0 errors, 0 exceptions.
- A 0.1.0 world (copy): starts, runs and saves with 0.3.0 — 0 errors, 0 exceptions.
- The same 0.1.0 world joined by a real client as its old character (level 60 Fish-Man): the character loads intact,
  the story migration runs (`story_v2` set), the journal opens, the server logs no exception. That character had no
  quest in progress, so the quest mapping itself is covered by the `story_arcs` migration checks rather than by this
  save.

### The Modrinth modpack (`dist/Grand-Line-Odyssey-0.3.0.mrpack`)

- Installed the way a launcher does (`tools/check_mrpack.py`): GeckoLib 4.9.3, Sodium 0.6.13 and Iris 1.8.12
  downloaded from `cdn.modrinth.com` and matched their size, SHA-1 and SHA-512; the mod, the shader pack and the Iris
  setting unpacked from the overrides.
- A real client with that instance's Sodium, Iris, shader pack and the pack's own `iris.properties` logged
  `Using shaderpack: GrandLineShaders-1.0.0.zip` (Balanced profile) and rendered with the shaders on
  (`docs/captures/overhaul/modpack_shaders_on.jpg`). The mod itself ran from source in that client (same code as the
  jar in the pack).
- Not run: an import through the Modrinth App or Prism Launcher themselves (neither runs here).

### Measured balance (Standard difficulty)

See [BALANCE.md](../BALANCE.md): at every tier a minion falls to 3 light blows of the reference character, a standard
foe to 5, an elite to 11–12; their light blows take 7–12 % (minion/standard) and 13–17 % (elite) of that character's
health; no unannounced blow takes more than the rank's cap.

## Failed, then fixed (no failure remains in the final runs)

| Run | Failures | Cause | Fix |
|---|---|---|---|
| Regression after the combat changes | 4 (fruit sweep, three Logia checks) | the tests fired ~400 abilities in a row in the same arena; walls, ice and sand left by earlier abilities now (correctly) sheltered the dummy | the tests revert temporary terrain between abilities |
| Regression after merging story and crew | 3 career checks | the new "no new fight during a conversation" rule was in `isHostileTo`, so a player who had just talked read as not hostile | the rule moved to the player-target goal; careers 38/38 |
| `overhaul`, early runs | cover, balance probe | test harness: the arena chunk was not loaded on the first tick; a stock fake player ignores all damage; a random Logia captain needed Haki | load the arena first; a probe player that takes damage; probe foes without fruit |
| Client captures, early runs | HUD overlaps; no target frame | vanilla only picks entities within reach (3 blocks); the card overlapped the hearts and chat | own visible pick up to 24 blocks; layout fixes |

## Not run / not verified

- **No human playtest.** Feel, difficulty and pacing are untested by a person.
- **Story beyond Sabaody** (chapters 20–37) keeps its earlier quest shape; Skypiea and Fish-Man Island have scenes but
  no decisions. Dimension travel in the middle of a story step is not tested.
- **Crew:** companions reviving a downed captain and crew persistence through a captain's knockdown or a real server
  restart were not exercised by the crew tests (the earlier phase-2 persistence checks still pass); door opening,
  fighting at a hold spot, the rank-3 techniques and the 8-second technique rule are untested; V/N keys and the
  kneel animation were seen only on the roster screen capture.
- **Multiplayer** (two clients) was not re-run for the overhaul.
- **Server load test** was not re-run for 0.3.0 (the shared machine makes it too noisy to compare); the cost of the
  cover check itself is measured below.

## Cost of line of effect

Measured in the `overhaul` suite (20 000 checks each, after warm-up): **2.0 µs** for one exposure check in the open
(one ray) and **7.4 µs** behind full cover (every ray tried). An NPC re-checks its target four to five times a second,
so a hundred NPCs in fights at once cost well under 0.2 ms per server tick.
