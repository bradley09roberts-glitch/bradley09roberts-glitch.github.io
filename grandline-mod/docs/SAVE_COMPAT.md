# Save compatibility (0.3.0)

Worlds made with 0.1.0 or 0.2.0 load in 0.3.0. Nothing is reset and no character is archived. **Make a copy of your
world folder before upgrading** (`.minecraft/saves/<world>` or the server's `world/`), and keep the old jar until you
are happy: 0.2.0 is in `dist/previous/` and git tag point `205b415`.

## What changes when an old world loads

| Data | Where it is saved | What happens on load |
|---|---|---|
| Foes and townsfolk already in the world | entity NBT (`Balance` tag, new) | An NPC without the tag (or with an older version) has its health, armour and blows re-tuned once, the first second it is loaded, keeping its share of health. Named characters and companions too. |
| Characters (level, stats, fruit, Haki, quests) | player data | Unchanged format. New flags are added as you play: `seen_controls` (the first-fight card was shown), `lesson_<part>` (combat tips seen), `story_v2` (the story migration ran), `choice:<decision>=<option>`, `beat:<who>`. |
| Quest progress | player data | Migrated once per character at login (`quest/arc/StoryMigration`): every objective a quest still has keeps its progress; fights already won are remembered; steps inserted before the one you had reached are marked done without paying rewards twice; nothing sends you back. 32 changed quests are mapped and no quest id was removed. |
| Crews | `data/grandline_crews.dat` (format 2) | Format 1 loads: the first two followers become active, the rest go to the reserve; holding members keep holding; bond starts at rank 1. Format 2 still writes the old `waiting` field, so an older jar can read a newer save's crew (without roles, active limit or bond). |
| Server config | `serverconfig/grandline-server.toml` | A new `[balance] difficulty = "auto"` is added with its default. Everything else is unchanged. |
| Damage types | the mod's data pack | Foes' damage no longer scales with the vanilla difficulty as well (it follows `[balance] difficulty`, which by default follows the world's difficulty). |
| Sparring partners | not saved | A bout cut short by a restart ends; a partner that loads from disk leaves at once. |

## Going back

Use the copy you made. If you must open an upgraded world with 0.2.0: characters, quests (the old ids still exist),
crews (they read the `waiting` field) and the world itself load; NPCs keep their re-tuned health until they despawn;
story flags from 0.3.0 are ignored.

## Verified

- The overhaul self-tests: NPCs loaded without the `Balance` tag are re-tuned (`GLNpc.retune`), crew format-1 tags load
  and round-trip (`GL_SELFTEST=crew`), old quest progress migrates (`GL_SELFTEST=story_arcs`: old ids in progress
  and completed land on the right step).
- A world created with 0.1.0 started on a clean NeoForge 21.1.259 server with the 0.3.0 jar: see the validation
  report.
