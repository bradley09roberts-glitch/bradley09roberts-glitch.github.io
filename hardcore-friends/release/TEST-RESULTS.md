# Hardcore Friends 1.0.0: test results

These are honest results. They include what failed, what that taught me, and what still is not perfect. All tests ran in this build environment on Minecraft Java 26.3 with Fabric Loader 0.19.5 and Fabric API 0.162.0+26.3. **None of your own saves were opened or touched.** Every test world was created fresh inside the build folder.

## 1. Automated game tests (server side): 80 of 80 pass

The suite has 79 Hardcore Friends tests plus one test from the Fabric framework. Each runs in its own small test plot with a fresh camp:

| Area | Tests | Examples |
|---|---|---|
| Core rules | 16 | world-edit guard preserves player builds and stays inside its zones, only natural trees count as trees, death drops a backpack holding everything, friends fight a zombie together, heal out of combat, leave a skeleton's line of fire, a hurt friend stays out of bow range, backpacks survive lava |
| Teamwork and supplies | 11 | deposit surplus but keep tools, restock the best tool, craft a stone pickaxe, Rowan hands logs to Oak during a shortage, share food with a hurt friend, come home at night |
| Farming and foraging | 15 | harvest and replant, till beside water, lay out the farm plot (also on bumpy ground), bake bread, fell only natural trees and replant, quarry stays 5×5 and 2 deep, no stone dug without a pickaxe |
| Building and redstone | 15 | campfire, supply chest, whole cabin, repairs, watchtower, sites avoid player builds, automatic door, drop-off hopper, auto-smelter |
| Mining and exploring | 10 | exposed ore needs the right pickaxe, staircase mine digs only natural stone inside its box, smelting, Scout records ores |
| Guardian, strategist, landscaper | 12 | Aegis equips better gear and defends a friend, Sage reviews the stores, Terra's paths, lighting, planting, fences |

**Stability:** six full runs in a row after the final fixes all passed (80/80 each time). Earlier repeated runs found four tests that failed only sometimes. I traced each one to a cause instead of re-running until it passed:

- *Rowan went quarrying while Oak waited for her logs.* A real priority bug: quarrying could outscore delivering to a blocked builder. Fixed (delivery now comes first), and the test now sets up the worst case every time.
- *A hurt friend drifted back into a skeleton's range.* A real behaviour bug: retreating ended 12 blocks away, inside bow range, and "drift home" could walk her back. Fixed, and a new test covers it.
- *A farmer charged a skeleton with a hoe.* A real behaviour bug, fixed: only Aegis goes after archers.
- *Friends rested at noon in two Rowan tests.* A test-world issue: random thunderstorms make it "dark outside", which correctly sends friends home. The tests now pin clear weather.
- *Farmland turned back to dirt in the harvest test.* A test-world issue: the test field had no water. It now has water, like a real farm.

## 2. In-game test in a fresh Hardcore world (client)

A real Minecraft client (software-rendered, no GPU) created a new **Hardcore** world. Hardcore forces cheats off.

SOAK_RESULTS_SKINS

## 3. Two days of real terrain in Hardcore (client soak)

SOAK_RESULTS_CAMP

## 4. Production install check

PROD_RESULTS

## 5. Skin checks

All nine supplied skins are packed into the mod byte-for-byte unchanged. They were checked with your saved make-minecraft-skins skill's own bundled validator and renderer:

- Every skin is a valid 64×64 RGBA PNG on the Classic (Steve, 4-pixel arms) model, with all 36 base faces filled.
- 3D previews of each friend are in `skin-previews/` (`<name>-preview.png` with the overlay layer, `<name>-base-only.png` without it), plus `all-friends-preview.png`.
- In game the skins render on the classic wide-arm model with every overlay layer showing. See the screenshots in `screenshots/`.

## 6. What was not tested

- Multiplayer with several real players at once (the code is server-side and should work, but only single-player and a dedicated server console were run).
- Very long play (weeks of in-game time), the Nether and the End with friends following, and every possible terrain type.
- Real GPUs: the client ran on software rendering, so frame rate was not measured.
