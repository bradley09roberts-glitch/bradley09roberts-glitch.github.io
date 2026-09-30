# Pale Meridian — Quick Start (Windows)

Spoiler-free. About 10 minutes plus download time. Linux and macOS work the same way with the same
launchers.

## You need

- A Minecraft: Java Edition account.
- A PC that runs Minecraft comfortably; 8 GB RAM or more recommended (the game gets 4 GB).
- The client pack: **`dist/PaleMeridian-1.0.0.mrpack`**.

Your existing Minecraft installation and worlds are **not touched**: the pack is installed as a
separate instance by the launcher.

---

## 1. Get a launcher that can import the pack

Either of these free launchers works. Both download the exact files listed in the pack from their
official sources and check each one against its hash.

- **Modrinth App** — https://modrinth.com/app
- **Prism Launcher** — https://prismlauncher.org

(The official Minecraft Launcher cannot import `.mrpack` files; see "Other launchers" at the end.)

## 2. Import the pack

- **Modrinth App:** create a new instance and choose the option to import it from a file, then pick
  `PaleMeridian-1.0.0.mrpack`.
- **Prism Launcher:** *Add Instance* → *Import* → browse to `PaleMeridian-1.0.0.mrpack` → *OK*.

The launcher installs Minecraft 26.2, Fabric Loader 0.19.5 and the pack's mods. Sign in with your
Microsoft account when it asks (the launcher handles this; no password is ever stored by the pack).

**Memory:** in the instance settings, give Minecraft **4 GB** (4096 MB).
**Java:** Minecraft 26.2 needs **Java 25**. The Modrinth App manages it for you; in Prism, if it
reports a Java problem, let it download Java 25 or point it at a Java 25 installation.

## 3. Create your world

Launch the instance, then **Singleplayer → Create New World**:

| Setting | Choose |
|---|---|
| Game Mode | Survival |
| Difficulty | Easy, Normal or Hard (Peaceful removes the valley's creatures and flattens the story's tension) |
| **World Type** | **Default** — required: the valley is built into the default world type |
| Allow Commands | Optional (only needed for the recovery tools in the server guide) |

Create the world. The first load takes a little longer while the valley is generated.

## 4. Your first minutes

- You arrive at a waystation on the rim of a fog-filled valley, facing the road north.
- A **letter** is pinned to the **noticeboard** next to the waystation: walk up to it and **use** it
  (right-click).
- Open your **Field Journal** with **G** (Minecraft's *Quick Actions* key; rebind it in Controls if
  you like) or from the pause menu. It always shows your current objective.
- The **glowing marker on your locator bar** points to the current objective.
- **Carry a light** in the fog: a torch or lantern in either hand.
- People and important objects are used like anything else: walk up and right-click.

## 5. Comfort and accessibility

Field Journal → **Comfort and accessibility**:

| Option | Scope |
|---|---|
| Objective bar | you |
| Screen effects (darkening pulses) | you |
| Locator-bar waypoint | you |
| The fog's cold | whole world |
| Keep inventory on death | whole world |
| Encounter difficulty: Story / Normal / Hard | whole world |

## 6. Playing with friends

- **Same house:** start your world, pause, **Open to LAN**. Friends with the same pack join from
  *Multiplayer*.
- **Different houses / always-on:** host a private server: see `docs/SERVER_GUIDE.md`.

Progress is shared: when anyone finishes a step, it's finished for everyone. Friends who join late
get a recap in their journal.

## 7. Saving and backups

The game saves automatically. To back up a singleplayer world, close the game and copy the world's
folder from the instance's `saves` folder (the launcher has an *Open folder* button) to somewhere safe.

## 8. If something goes wrong

| Problem | What to do |
|---|---|
| A red message says the world was not created with the Default world type | Create a new world with World Type **Default**. |
| The game won't start / Java error | Make sure the instance uses Java 25 (see step 2). |
| Low frame rate | Lower Render Distance (Video Settings). Shader packs are not included or supported. |
| You lost a story item | Check your Field Journal objective: every important item can be found again where it first appeared, or from the person who gave it. |
| Something seems stuck | Open the Field Journal → Current objective; the hint usually helps. Hosts can use the recovery tools in `docs/SERVER_GUIDE.md`. |
| Friends can't join | Everyone needs the same pack version (1.0.0) on Minecraft 26.2. |

## Other launchers

To use the official Minecraft Launcher instead, you would install Fabric Loader 0.19.5 for Minecraft
26.2 with the official Fabric installer (https://fabricmc.net), give it its **own game directory**
(so your normal `.minecraft` stays untouched), and copy the mods listed in `pack/lock.json` plus
`palemeridian-1.0.0.jar` into that directory's `mods` folder. The Modrinth App or Prism is much easier
and checks every file for you.
