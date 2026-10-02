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
