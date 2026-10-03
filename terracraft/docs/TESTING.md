# Testing TerraCraft

## Compile

```bash
./gradlew compileJava      # fast check
./gradlew build            # full jar
```

## Dedicated server smoke test (no display needed)

1. `cp tools/test/server.properties run/server.properties && echo eula=true > run/eula.txt`
   (offline mode, RCON on port 25575 with password `terracraft`).
2. `./gradlew runServer > server.log 2>&1 &` and wait for `Done (`.
3. Drive it with RCON: `python3 tools/test/rcon.py "terraria worldstate" "terraria recipecheck minecraft:diamond_sword"`.
4. Check `server.log` for `ERROR`/exceptions, and the TerraCraft load lines
   (`Loaded N Terraria recipes`, `Loaded N mining power rules`).

## Headless client (real rendering, screenshots, input)

The development container has Xvfb and Mesa (llvmpipe, OpenGL 4.5), which run the client.

```bash
Xvfb :99 -screen 0 1280x720x24 &
# First run only: start the client once, then in run-client/options.txt set
# onboardAccessibility:false, skipMultiplayerWarning:true, joinedFirstServer:true, pauseOnLostFocus:false
DISPLAY=:99 ./gradlew runClient -Pautojoin > client.log 2>&1 &   # joins localhost:25565 as "Dev"
```

* Screenshots: `DISPLAY=:99 import -window root shot.png` (ImageMagick).
* Input: `python3 tools/test/xinput.py click left 640 360 key r sleep 1 hold left 1.0 shiftclick 700 400`
  (needs `pip install python-xlib`). The game window is 854x480 centred on the 1280x720 screen.
* Give items / set state with RCON (`give Dev terracraft:hermes_boots`, `op Dev`, `execute at Dev run summon ...`).

## What was verified for Stage 1 (in the live client)

Terraria HUD (hearts, Life text, defense, mana stars), Life Crystals, crafting screen with station detection
and server-side crafting, equipment screen (armor + accessories, shift-click equip), set bonus and stat
totals, Wand of Sparking / Magic Missile mana use and On Fire!, bow ammo consumption, broadsword melee damage
with variance, vanilla mob damage scaling (zombie: 3 x 5 - 9 x 0.5 = 10.5), environmental damage scaling,
Terraria regeneration delay, coin drops / conversion / softcore death penalty, healing potion + Potion
Sickness, pickaxe power (Copper vs Platinum on Obsidian), double jump, villager/trader removal, Nether
blocking, no natural vanilla hostile spawns, disabled diamond/netherite/enchanting/brewing recipes.

## What was verified for Stage 2 (in the live client)

Cloud in a Bottle fix (logic), all enemy sprites rendering and animating, slime hopping, zombie chasing and
contact damage, health bars, coin/gel drops, Mother Slime splitting, natural night spawning (6 zombies in
40 s within the cap), ore pairs in fresh chunks (`/terraria worldgen scan 3`: chosen ores dominate, others
~25%), Life Crystals (18) and chests (9) per 49 chunks, chest loot tables, Fallen Stars falling and vanishing
at noon, King Slime (boss bar, life scaling 2000 -> 1000 HP x2, shrinking, slime shedding, defeat message,
progression flag, loot), Eye of Cthulhu (servants, charges, phase 2 at 50%, Crimtane drop in a Crimson world),
Guide arriving on first join, housing validation, Merchant arriving with 60 silver into a built house, chat
window, shop with 12 items and buying (coins deducted by the item value), Nurse arriving after a Life Crystal
and healing for 44 copper, Guide shooting enemies, the personal Terraria sprite pack loading in the client.

Tips: freeze test mobs with `{NoAI:1b}`; town NPCs walk away quickly, so teleport them and right-click in the
same command batch (`xinput.py hold right 0.05` clicks without moving the pointer, which would turn the camera).

## What was verified for Stage 4a (in the live client)

Dungeon generated at the seeded spot (seed 12345: 104 93 -819, pink bricks, door facing spawn) with tower,
porch, ladder shafts, rooms, corridors, Locked Gold Chests, bookshelves, lanterns and cobwebs; Old Man at the
door with the Curse button only at night; Dungeon Guardian killing a survival player who entered before
Skeletron; Skeletron awakening from the curse (Old Man removed, chat closed), head + two hands rendering,
defeat message, curse-lifted announcement and progression flag; Golden Key opening (and being used up by) a
Locked Gold Chest with dungeon loot (Muramasa); Angry Bones and Dark Casters spawning inside after Skeletron;
Water Bolt casting. Tip: bosses despawn without a valid survival target, so freeze them with `{NoAI:1b}` while
a survival player stands nearby to frame screenshots.

## What was verified for Stage 4b (in the live client)

Seed 12345 jungle near -640 -410: mud/jungle grass/spores conversion (`/terraria worldgen scan 4`: 444k mud,
18.8k jungle grass, 353 spores, 3 larvae, 4.1k hive blocks), hive interior (honey floor, Larva), breaking the
Larva awakening the Queen Bee (she attacked and killed the test player), Queen Bee, Hornet, bee and Man Eater
models, Queen Bee defeat (flag, The Bee's Knees, Bee Wax, honey, potions), The Bee's Knees firing bee arrows,
Jungle Bats spawning underground. Not observed: natural Hornet/Man Eater spawns in a 40 s window.

## What was verified for Stage 4c (in the live client)

Underworld below y=-40 in fresh chunks (ash floor/ceiling, lava sea, `/terraria worldgen scan 4` near 40 -50 40:
262k ash, 2.4k hellstone, 1.1k obsidian brick, 1 Hellforge, 3 Shadow Chests), no cave vegetation inside it,
ruined houses, a Guide Voodoo Doll in lava killing the Guide and awakening the Wall of Flesh, the wall sheet with
eyes, mouth and The Hungry rendering, defeat setting `boss_wall_of_flesh_defeated` and `hardmode_active` with the
Terraria announcement, loot (Pwnhammer, emblem, Breaker Blade) delivered to the player. Underworld enemies spawn
fast enough to kill an unequipped test character within seconds.

## What was verified for Stages 4d and 4e (in the live client)

`/terraria event start goblin_army`: progress bar, only goblins spawning on the surface (14 within 25 s), goblin
models, kills counted toward the goal, "The Goblin Army has been defeated!" and the progression flag; the Bound
Goblin appearing in a cave near the underground player, talking to him freeing the Goblin Tinkerer (arrival
message, Shop + Reforge buttons), reforging the held Copper Broadsword (Pointy, Ruthless, Light, Unpleasant;
coins deducted; name and green tooltip lines updated); `/terraria meteor` landing a crater lined with Meteorite
("A meteorite has landed!") and Meteor Heads spawning around it.

## Showcase world

`tools/showcase_world.py <world folder>` writes the `showcase` datapack: a hub at y=210 above spawn (seed 12345)
with command-block buttons for time/weather, Hardmode, events, town NPCs, biome teleports, gear kits, a boss
arena, an enemy zoo and chests holding every item. In the world run `/reload`, `/function showcase:build`,
`/function showcase:build_zoo`, then `/function showcase:build_underworld` from inside the Underworld.
`/function showcase:hub` returns to the hub. To open a singleplayer save directly:
`./gradlew runClient "-Pworld=<save name>"`. `xinput.py type <text>` types into chat.
