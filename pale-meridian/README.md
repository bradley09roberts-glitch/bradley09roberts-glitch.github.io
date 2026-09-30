# Pale Meridian

*Chart what the fog forgot.*

A story campaign for **Minecraft: Java Edition 26.2** (Fabric). Solo, or 2–4 friends in co-op.

> Forty years ago the Vale of Vell vanished from every map under a grey fog the locals call the Pall.
> You are a surveyor. A year-late letter from your old mentor, who walked into the fog and never
> came out, asks you to come yourself, and to bring light.
>
> Relight the valley's lamps, help its people remember who they are, and find out what the fog
> is hiding, and why.

**This page and `QUICKSTART.md` are spoiler-free.** Anything under `docs/spoilers/` is not.

---

## What's in it

- **A hand-built valley** at the centre of every new world: a waystation on the rim, a lakeside
  village with a bell tower, terraced orchards and a windmill, a glassworks under the cliffs, a mine,
  an island observatory, a drowned chapel in the fen — linked by lamp-lit roads. Beyond the rim the
  world is ordinary Minecraft.
- **A complete campaign**: a prologue, four chapters and an epilogue with a real ending (the last
  decision is yours, and the valley remembers it). About 35 quests, 150+ conversations, puzzles,
  building and a handful of tense encounters. Design target: 6–10 hours for a first playthrough.
- **Places that change**: when a district remembers itself its fog lifts, colour, music and animals
  come back, lamps light, and its people change.
- **Built for co-op**: story progress is shared by the whole world, encounters scale with the group,
  late joiners get a recap, and each player keeps their own journal.
- **A Field Journal** (press **G**, Minecraft's Quick Actions key, or use the pause menu): current
  objective, the story so far, the people you've met, and comfort settings.
- **Comfort options**: turn off screen-darkening effects, the objective bar or waypoint marker; a
  gentler "Story" encounter setting; keep-inventory; the fog's cold can be switched off.
- **Fair by design**: every step can be recovered if you lose an item or break something, and
  nothing is ever lost for good.

## Get playing

- **Play on your own PC:** see **[QUICKSTART.md](QUICKSTART.md)** (Windows first; about 10 minutes).
- **Host a private server for friends:** see **[docs/SERVER_GUIDE.md](docs/SERVER_GUIDE.md)**.

Release files are in `dist/`:

| File | What it is |
|---|---|
| `PaleMeridian-1.0.0.mrpack` | Client pack. Import it into the Modrinth App or Prism Launcher. |
| `PaleMeridian-Server-1.0.0.zip` | Private server package (Windows and Linux installers). |
| `SHA256SUMS.txt` | Checksums of the files above. |

You need your own Minecraft: Java Edition account. Minecraft and the third-party mods are
downloaded from their official sources by your launcher or the server installer (see
[docs/ATTRIBUTION.md](docs/ATTRIBUTION.md)); nothing restricted is bundled here.

## Project status

Everything described above is implemented and passes the project's offline checks (the game's own
data loaders parse every file; world generation is probed on several seeds; installers are tested).
**It has not yet been played in a running game**: starting Minecraft requires accepting the
Minecraft EULA, which is left to you. See [docs/TEST_REPORT.md](docs/TEST_REPORT.md) and
[docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md) before your first session, and please report anything odd.

## For developers

| | |
|---|---|
| [docs/TECHNICAL.md](docs/TECHNICAL.md) | Architecture, save-state model, and a feature → file map |
| [docs/STACK_AND_LOCK.md](docs/STACK_AND_LOCK.md) | Locked versions, compatibility and how to re-verify the lock |
| [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) | What is implemented, what is not |
| [docs/TEST_REPORT.md](docs/TEST_REPORT.md) | Every check with PASS / FAIL / NOT RUN and evidence |
| `docs/spoilers/` | ⚠️ Story bible, quest graph, full walkthrough |

```bash
python3 tools/gen_all.py                         # regenerate the data pack, sites and art (Python 3.11+, Pillow)
cd mod && ./gradlew build                        # build the mod jar (JDK 25); never launches the game
./gradlew offlineCheck -PcheckMode=validate      # load everything through the game's own loaders, offline
cd .. && python3 tools/build_dist.py             # client .mrpack, server zip, checksums
```

Code and original content: MIT (see `LICENSE`). Not an official Minecraft product; not approved by
or associated with Mojang or Microsoft.
