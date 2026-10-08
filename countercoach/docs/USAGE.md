# CounterCoach — install, use, disable, uninstall

CounterCoach is an **external companion app** for Windows. It is not a game mod. It never
touches Deadlock's files, memory, network traffic or input, and it never buys items or spends
ability points for you. It is not affiliated with or endorsed by Valve. No claim is made that
third-party overlays are approved by Valve or safe from enforcement.

> **Testing status:** the Windows package was built and its installer and uninstaller were run
> under **Wine** on Linux. The app itself was run and screenshotted on Linux. It has **not** been
> run on real Windows or alongside a live Deadlock match. See [TEST_RESULTS.md](TEST_RESULTS.md).

## Install

Two packages are produced by `pnpm dist:win`:

| File | What it is |
|---|---|
| `CounterCoach-Setup-0.1.0-x64.exe` | Per-user installer (no admin). Lets you choose the folder; creates Start Menu and Desktop shortcuts. |
| `CounterCoach-0.1.0-win-x64.zip` | Portable build. Unzip anywhere and run `CounterCoach.exe`. |

Both are **unsigned**, so Windows SmartScreen will warn ("Windows protected your PC"). Choose
*More info → Run anyway* only if you built the package yourself or trust its source.

Nothing in the Deadlock install folder is modified, and no launch options are needed.

## First use (about 30 seconds)

1. Start CounterCoach. A main window and a small overlay card open.
2. In **Match**, press **New match**, then pick your hero (Ctrl+K, type, Enter).
3. Type your **unspent souls** and press Enter.
4. Add enemy heroes. Mark your lane opponents with ◎.

That's enough for the first recommendation. Each card says which extra input would improve it
(items you own, visible enemy items from the scoreboard, ahead/behind, the match clock, your
death recap, ability tiers and unspent points).

Useful keys in the main window: `/` focuses item search, `Ctrl+K` focuses hero search,
`Alt+1…5` switches tabs, and `↑/↓/Enter` work in every search list.

### Reading the advice

- **BUY NOW**: the best legal purchase you can afford now. It shows the price after any owned
  component is deducted.
- **SAVE FOR**: the next meaningful target and the souls still needed. When nothing affordable
  is worth delaying it, BUY NOW says **Save**.
- **ALTERNATIVE**: a different answer, with the condition under which it's better.
- **Why?**: the threat it answers, how that threat is known ("heroes only", "seen items",
  "your report/recap"), what it delays, and any rule it relies on that isn't verified.
- **Confidence** (high/medium/low) reflects how complete and fresh your inputs are, how
  reviewed the hero profile is, and whether the data matches the game build. It is **not** a
  win probability.
- **Information state badge**: *Manual*, *Scenario*, *Replay*, or *Last observed* when inputs
  are old.
- **Pin / Defer / Reject** steer the advice. Rejects and defers keep your reason and can be
  undone.

### Overlay

- Collapsed: next purchase, souls to go, next ability action, one reason. Expanded adds SAVE
  FOR, ALTERNATIVE and the top threats.
- Default hotkeys: **Ctrl+Alt+O** show/hide, **Ctrl+Alt+E** expand/collapse, **Ctrl+Alt+M**
  edit mode (drag to move; press again to lock). Change them in **Settings → Hotkeys**.
- While locked, the overlay is **click-through** and **cannot take keyboard focus**.
- Scale, opacity, corner or custom position, and monitor choice are saved. Custom positions are
  stored as fractions of the monitor's work area, so they survive resolution and DPI changes.
- **Use Deadlock in Borderless/Windowed mode.** External overlays cannot draw over exclusive
  fullscreen. This has not been tested with the real game.
- If you don't want the overlay, turn it off in Settings and use the main window on a second
  monitor (separate-window mode).

### Scenarios, what-if and review

- **Scenarios**: replay the bundled fixtures to see how the advice reacts. They replace the
  current match and are labelled *Scenario*.
- **What-if**: change one enemy item, your souls, your build archetype or your route, and see
  what changes and why. Your real match is not touched.
- **Review**: turn on decision logging (off by default) and review a finished match. You get at
  most three lessons, based only on what was known at each decision.

## Data updates

The app ships with a validated game-data snapshot (client build 6763, 2026-10-07). In
**Settings → Game data**:

- **Check for new build** compares the live client build with the data build. If the game is
  newer, advice is labelled low confidence until the data is refreshed.
- **Download & validate data** fetches from the community Deadlock API, validates it, compares
  its quality with the current snapshot, and only then switches. The previous snapshot is kept.
  Items that changed since the knowledge base was reviewed are flagged and their counter value
  is halved until re-checked.

The app works offline with the bundled or last downloaded snapshot.

## Where things are stored

| What | Location (Windows) |
|---|---|
| Program (installer) | `%LOCALAPPDATA%\Programs\CounterCoach` (or the folder you chose) |
| Settings | `%APPDATA%\CounterCoach\settings.json` |
| Downloaded data snapshots | `%APPDATA%\CounterCoach\data\` (`manifest.json` lists the active and previous snapshot) |
| Decision logs (opt-in) | `%APPDATA%\CounterCoach\logs\` |
| Current match and UI history | Browser storage inside `%APPDATA%\CounterCoach` |

Nothing is uploaded. The only network requests go to `api.deadlock-api.com` (data checks and
refresh) and `assets-bucket.deadlock-api.com` (item and hero icons).

## Back up and roll back

- **Back up:** copy `%APPDATA%\CounterCoach\settings.json` (and `logs\` if you keep logs).
- **Roll back data:** delete `%APPDATA%\CounterCoach\data\`. The app returns to its bundled
  snapshot on the next start.
- **Reset settings:** delete `settings.json`. Invalid or partial settings are repaired field by
  field automatically.
- **Roll back the app:** uninstall, then run an older installer. Your settings are kept.

CounterCoach makes no game-file changes, so there's nothing in Deadlock to back up or restore.

## Disable

- Hide the overlay: **Ctrl+Alt+O** or *Settings → Overlay → Show overlay*.
- Stop network checks: *Settings → Game data* (automatic checks only run at startup; untick
  `data.autoCheck` in `settings.json` to stop them).
- Stop logging: *Review → Turn logging off*. *Delete all logs* removes the files.
- Fully disable: close CounterCoach. It doesn't autostart and leaves no background service.

## Uninstall

- Installer build: *Settings → Apps → CounterCoach → Uninstall*, or run
  `Uninstall CounterCoach.exe` in the install folder (`/S` for silent). This removes the
  program, shortcuts and uninstall registry entry. Verified under Wine; not yet on Windows.
- Portable build: delete the folder.
- Remove your data as well: delete `%APPDATA%\CounterCoach`. The uninstaller keeps it on
  purpose so a reinstall keeps your settings.
