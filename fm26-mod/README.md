# FM26 Moneyball (BepInEx plugin)

A BepInEx plugin for Football Manager 26, aimed at transfer and wage negotiations: how much to bid, what wage to offer.

## Status: step 1 of 2

| Part | State |
|---|---|
| Fee and wage advisors (`FeeAdvisor`, `WageAdvisor`) | Done, unit-tested. Same rules of thumb as the web tool in `/fm26`. |
| Discovery scanner | Done, unit-tested. Lists the game's transfer/contract/wage classes and methods. |
| Tracer | Done, unit-tested on stand-in classes. Logs live offer values from any method you name. |
| Plugin shell | Compiles against BepInEx 5.4.21 and Unity references. **Never run inside FM26.** |
| Wiring the advisors into FM26's negotiation screens | **Not done.** Needs the game's real class and method names, which I can't see from here. |

Step 2 starts once you send me `discovery.txt` and a `trace.log` from a real negotiation (below).

The advisors are community rules of thumb, not FM's own formulas. Nothing here reads or changes the game's AI yet.

## Before you start

1. **Back up your saves** (`Documents\Sports Interactive\Football Manager 2026`).
2. Check whether FM26 is **Mono** or **IL2CPP**. In the game folder (Steam: right-click the game, Manage, Browse local files):
   - `<game>_Data\Managed\Assembly-CSharp.dll` exists: **Mono**. This plugin applies.
   - `GameAssembly.dll` plus `<game>_Data\il2cpp_data\` exists: **IL2CPP**. This BepInEx 5 build will not load. Tell me; the core logic is reusable on BepInEx 6, but the plugin shell has to be redone.

## Install

1. Download BepInEx 5 (x64, the 5.4.x release) from <https://github.com/BepInEx/BepInEx/releases> and unzip it into the folder that contains the game's `.exe`.
2. Run the game once to the main menu, then quit. That creates `BepInEx\config`.
3. Unzip `dist/Fm26Moneyball-0.1.0.zip` into the same folder, so you get `BepInEx\plugins\Fm26Moneyball\Fm26Moneyball.Plugin.dll`.
4. Start the game and sit at the main menu for about a minute.
5. Open `BepInEx\fm26-moneyball\discovery.txt`. Also check `BepInEx\LogOutput.log` for a line starting `FM26 Moneyball loaded`.

If step 5 shows nothing, send me `BepInEx\LogOutput.log`; it says whether BepInEx started at all.

## What to send me

- `BepInEx\fm26-moneyball\discovery.txt`
- `BepInEx\LogOutput.log`

Commit them to this branch under `fm26-mod/discovery/`, or paste them in the chat.

## Then trace a real negotiation

1. Edit `BepInEx\config\com.bradley.fm26moneyball.cfg`.
2. Set `[Trace] Enabled = true`.
3. Under `Targets`, list `Namespace.Type::Method` entries from `discovery.txt`, separated by `;`. Every overload is traced.
4. Restart, open a transfer negotiation, make an offer, counter, and accept.
5. Send me `BepInEx\fm26-moneyball\trace.log`. Each line shows the arguments, the result, and simple field values of offer objects, which tells me which field is the fee and which is the wage.

## What step 2 will do

Once I can see the real names, the plugin can show or log the advisor's opening bid, expected price and ceiling next to FM's own offer, and the wage band against your squad's structure. Whether it can write values straight into FM26's negotiation UI depends on what the game exposes; I'll say so after reading the discovery output.

## Build from source

Needs the .NET SDK (8 or later).

```bash
dotnet build -c Release
dotnet run -c Release --project tests/Fm26Moneyball.Tests   # 32 checks
./scripts/package.sh                                        # writes dist/Fm26Moneyball-0.1.0.zip
```

The test project pins HarmonyX 2.16.1 because HarmonyX 2.10.2 segfaulted when I ran the tests on .NET 8. In the game, BepInEx supplies its own Harmony, so the plugin is compiled against 2.10.2 but runs against whatever BepInEx ships.

## Layout

```
src/Fm26Moneyball.Core     advisors, discovery, tracer, object dumper (net472 + netstandard2.0)
src/Fm26Moneyball.Plugin   BepInEx entry point and config (net472)
tests/                     console test runner
scripts/package.sh         builds the drop-in zip
```

## Risks

Plugins modify the running game. A game update can break them, and a bad patch can corrupt a save, so keep backups. Single-player only.
