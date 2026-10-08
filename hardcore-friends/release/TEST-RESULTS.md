# Hardcore Friends 1.0.0: test results

These results are honest: they include what went wrong, what was fixed, and what is still not perfect. Everything ran on Minecraft Java 26.3 with Fabric Loader 0.19.5 and Fabric API 0.162.0+26.3. **None of your own saves were opened or touched.** Every test world was created fresh inside the build folder.

## 1. Automated game tests: 143 of 143 pass

The final code passed the full server suite twice in a row (`test-logs/server-gametests-summary.txt`). Each test runs in its own small plot with a fresh camp.

| Area | What is covered |
|---|---|
| Core rules | The world-edit guard keeps player builds safe and stays in its zones; only natural trees count. A death drops a backpack with everything. Friends fight zombies and plain drowned together and heal out of combat. They leave a skeleton's line of fire, a badly hurt friend falls back at once even while dodging, and places where a friend died are avoided for three days. |
| Teamwork and supplies | Depositing, restocking and crafting tools; shares and deliveries to a stuck builder; feeding a hurt friend first. |
| Everyone pitches in | Any friend can do any job, but specialists keep theirs. A stand-in builds or farms when the specialist is missing, shared jobs are never doubled up, and claims are released when a friend leaves or dies. Stand-ins carry the tools for work nobody covers. |
| Needs and mood | Friends eat real food from the backpack or the chest. A starving friend drops work to eat, and one at one heart rests at camp. They sleep at night (a skipped night counts), wake when hurt, chat (raising Unity), take pastimes that change no blocks, and warm up by the fire. Mood lines, the `/friends needs` command, and starving that never kills on its own are also covered. |
| Farming and foraging | Harvesting and replanting (carrots first), tilling, the farm plot on bumpy ground, baking and bone meal. Felling only natural trees and replanting them; bounded quarries. |
| Building and redstone | The whole cabin and its repairs, the watchtower, and sites that avoid player builds. A forest camp has the trees on the cabin site cleared first. The automatic door, drop-off hopper and auto-smelter. |
| Livestock | Terra builds the pen. Friends lure or lead animals home, breed them, butcher only the surplus, and cook meat on the campfire or in the furnace. Hunting is only allowed by day, outside camp, on wild animals. Your named, leashed, saddled, fenced or baby animals are never touched, and the last pair is always kept. |
| Mining, exploring, guarding, landscaping | Ores need the right pickaxe, and the staircase mine stays in its box. Scout's finds, Aegis's gear and guard duty, Sage's store reviews, and Terra's paths, lights, plants and fences. |

**How the suite was kept honest.** Each fix comes with a test that fails on the old code. Whenever a test failed only some of the time, I traced the root cause instead of re-running it. Those causes included:
- a thunderstorm darkening the test world;
- a farm field with no water;
- a mock player left behind by another test;
- a skeleton chasing a friend into the edge of the test area;
- a real scoring bug where quarrying beat delivering logs to a stuck builder.

## 2. In-game Hardcore test (real client, fresh world): 12 of 12

A real Minecraft client created a new **Hardcore** world, which forces cheats off.

- The vanilla `/time set` was refused, while `/friends` commands worked.
- All nine friends were recruited for exactly 18 common food, and a duplicate recruit was refused.
- Fern's death dropped her backpack, and she could not be recruited straight back.
- The player's own death showed **"Game Over!"** and then spectator mode, with no extra life.

Screenshots are in `screenshots/`. They show your nine skins in game, front and back, with name tags.

## 3. Two in-game days on real terrain: recording 2

This was a fresh Hardcore world on a hilly birch forest by a river, with all nine friends and a starter chest. It ran two full in-game days with the camera recording. The video is `videos/hardcore-two-days-timelapse.mp4`; the panel under the picture shows each friend's mood, what they are doing, and what they say.

- **7 of 9 survived.** Sage and Aegis were killed by zombies in the camp on the first night, and nobody died after that.
- **The camp reached Village**: supply chest, crafting table, campfire, furnace, farm plot, torch posts, paths, cabin, automatic door and farm fence. Unity ended at 274 (Companions).
- **All 45 of the test's player-built blocks were untouched**, and all 614 block changes by friends stayed inside their zones.
- This run used the code just before livestock was added (`5a65fea`). Livestock is covered by the automated tests above but was not in this recording.

**What the first night showed.** Aegis guards until midnight and then sleeps, so the camp had no watch after that. He fought a zombie alone, and Sage, the one friend with no weapon, could only run. This is written up as a known limit in GUIDE.md, section 13. A night-watch rota, a bedtime routine, a camp-wide rally and a sword for Sage are built on a separate branch (`wf/night-safety`) but not in this release: they still need their review fixes and testing with livestock.

## 4. What went wrong on the way, and was fixed

An earlier recording (`videos/first-recording-before-drowned-fix.mp4`) lost Scout and Flint to drowned in a river, and then most of the camp on the first night. The cause was a bug of mine: drowned without a trident counted as archers, so friends ran from them instead of fighting together, and a dodging friend never fell back to recover. Both are fixed and tested. The second recording above is the result.

A code review of the needs and generalist systems confirmed 13 problems, all now fixed. They included:
- friends eating three times more than intended, and a farm that could never feed nine;
- urgent needs losing to busy work;
- stale job claims after a reload.

A review of livestock confirmed 14 more, all fixed. The most important: a friend could have butchered your named or saddled animals in the pen, and a full pen could stall for good.

## 5. Production install check

This used the official Fabric server launcher with this release's JAR and Fabric API 0.162.0+26.3 (CurseForge file 9078180), on a fresh Hardcore world. It loaded with no errors, and `/friends help`, `/friends list`, `/friends camp`, `/friends unity` and `/friends needs` all worked from the console (`test-logs/production-server-check.txt`). The JAR contains no test code.

## 6. Skin checks

All nine supplied skins are packed into the mod byte for byte, unchanged. They were validated with your saved make-minecraft-skins skill's own bundled validator and renderer. Each is a 64×64 RGBA PNG on the Classic (Steve) model with all 36 base faces filled. 3D previews are in `skin-previews/`, and in-game views are in `screenshots/`.

## 7. Not tested

- Multiplayer with several real players at once.
- Weeks of in-game time, the Nether and the End, and every kind of terrain. Livestock was not tested over a long real-world run.
- Real graphics cards: the client ran on software rendering, so frame rate was not measured.
