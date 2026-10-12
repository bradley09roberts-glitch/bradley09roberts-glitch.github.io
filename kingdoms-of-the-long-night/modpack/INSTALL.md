# Installing Kingdoms of the Long Night

The pack ships as two Modrinth modpack files built from one manifest:

| File | Preset | What it is | Recommended RAM for the game |
|---|---|---|---|
| `dist/KingdomsOfTheLongNight-<version>-performance.mrpack` | **Performance** (default) | Everything except shaders, Distant Horizons and Sound Physics. Start here. | **6 GB** |
| `dist/KingdomsOfTheLongNight-<version>-rich.mrpack` | **Rich** | Same world/gameplay mods + Iris with Complementary Reimagined shaders, Distant Horizons (far terrain) and Sound Physics Remastered. Needs a decent GPU. | **8 GB** |

Both presets contain exactly the same world-affecting mods at the same versions, so a world made in one
opens in the other (see "Switching presets").

A `.mrpack` is small: it contains only our configs, default `options.txt` and our own `longnight` mod. The
launcher downloads every other mod from cdn.modrinth.com and checks each file's hash.

Requirements: Minecraft Java Edition account, **Java 21** (64-bit), Minecraft **1.21.1**, NeoForge
**21.1.259** (the launcher installs Minecraft and NeoForge for you).

RAM rule of thumb: give the game the amount in the table, never more than about half of your PC's RAM,
and leave at least 4 GB for the operating system. More is not better: oversized heaps cause longer
garbage-collection pauses.

---

## Option A - Prism Launcher (recommended)

1. Install Prism Launcher from https://prismlauncher.org and sign in with your Microsoft account
   (Settings -> Accounts -> Add Microsoft).
2. **Java 21**: Settings -> Java -> *Auto-detect* (or *Download Java* -> pick a Java 21 runtime, e.g.
   Eclipse Temurin 21 or Microsoft OpenJDK 21). Prism can also pick the right Java automatically per
   instance; Java 21 is required for Minecraft 1.21.1.
3. Click **Add Instance** -> **Import** (left side) -> **Browse** -> choose
   `KingdomsOfTheLongNight-<version>-performance.mrpack` -> name it e.g.
   `KotLN Performance` -> **OK**. Prism downloads Minecraft 1.21.1, NeoForge 21.1.259 and all mods.
4. Select the new instance -> **Edit** -> **Settings** -> **Java** tab:
   - tick *Java installation* and choose your **Java 21** path,
   - tick *Memory*: **Minimum 2048 MiB, Maximum 6144 MiB** (Rich preset: **8192 MiB**).
5. Click **Launch**. The first start takes a few minutes (mod setup, shader/LOD caches on Rich).

## Option B - Modrinth App

1. Install the Modrinth App from https://modrinth.com/app and sign in with your Microsoft account.
2. **Java 21**: Settings (gear) -> *Java and memory* -> make sure a **Java 21** installation is set
   (use *Install recommended* / *Auto detect* if it is empty).
3. In the left bar click **+ (Create new instance)** -> **Import from file** -> choose the `.mrpack`
   -> confirm. The app creates the instance and downloads everything.
4. Open the instance -> **... / Options** -> **Java and memory** -> enable custom memory ->
   **6144 MB** (Performance) or **8192 MB** (Rich). Make sure it uses Java 21.
5. Press **Play**.

## Creating a fresh Hardcore world

1. **Singleplayer** -> **Create New World**.
2. Game tab: **Game Mode: Hardcore** (difficulty is locked to Hard; one life).
3. World tab: World Type **Default** (Tectonic + Terralith replace the default generator - do *not*
   pick Superflat/Amplified/Large Biomes), **Generate Structures: ON**, Bonus Chest OFF.
   Leave *Allow Commands* OFF for a fair hardcore run (you can still use `/locate` etc. only with commands on).
4. Optional: type a seed. Create. The first chunks take longer than vanilla because the terrain is
   continent-scale; that is normal.
   If Minecraft shows **"Worlds using Experimental Settings are not supported"** when creating or
   opening the world, choose **"I know what I'm doing!"** (or *Create Backup and Load*). It is a vanilla
   notice triggered by the custom terrain data of Tectonic/Terralith, not an error, and it is shown only
   once per world.
5. Tips: AppleSkin shows saturation in the HUD; Xaero's World Map opens with **M** and a new
   waypoint is **B**; EMI (recipe viewer) appears beside the inventory; the Ars Nouveau magic path
   starts with the *Worn Notebook* - search for it in EMI to see its recipe. Check all keybinds under
   Options -> Controls -> Key Binds.

## Keeping it separate from your existing Minecraft

- Each Prism instance / Modrinth App instance has its **own game folder** (mods, config, saves,
  options, screenshots). Nothing is shared with the official launcher's `.minecraft` or with other
  instances, so your existing installs and worlds are untouched.
- Do **not** copy old worlds into this pack and do not open this pack's worlds in other installs:
  Tectonic/Terralith/YUNG's would generate new chunks next to old ones (ugly seams), and modded
  blocks/items would be lost in a vanilla install.
- Folder locations: Prism -> right-click instance -> *Folder*; Modrinth App -> instance -> *Open folder*.
  Your worlds are in `saves/`. Back them up by copying that folder while the game is closed.

## Switching presets

The recommended way is to have **both presets installed as two instances** (import both `.mrpack` files)
and move the world between them:

1. Close the game.
2. Copy `saves/<your world>` from one instance folder to the other's `saves/`.
3. Launch the other instance and open the world.

This is safe because both presets contain the same server-side/world mods at the same versions; the
Rich preset only adds client-side visuals (Iris, shader, Sound Physics) and Distant Horizons, which
keeps its LOD cache in the world folder (`data/DistantHorizons*.sqlite`) and is simply ignored by the
Performance preset.

In-game you can also toggle the shader on Rich: **Options -> Video Settings -> Shader Packs**
(Iris default keys: **O** opens the shader menu, **K** toggles shaders) to turn shaders off temporarily, and Distant Horizons' quality in its settings
(the "DH" button in Video Settings / its options screen).

## Updating the pack

Import the new `.mrpack` as a **new instance**, then copy your `saves/` folder over (after backing it
up). Note that importing replaces `options.txt` with the pack defaults (keybinds and video settings);
copy your old `options.txt` over as well if you want to keep yours.
Never downgrade a world to an older pack version.

## Troubleshooting

- *"Incompatible mod set"/crash on start*: make sure Java 21 is used and that you did not add other
  mods to the instance.
- *Low FPS on Rich*: switch shaders off (K), lower Distant Horizons quality, or use the Performance preset.
- *Out of memory*: raise max memory to the recommended value (not higher than half your RAM).
- Logs are in the instance folder under `logs/latest.log`; crash reports in `crash-reports/`.
