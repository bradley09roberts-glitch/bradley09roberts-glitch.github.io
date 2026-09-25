# CLAUDE.md — Kinetik

Kinetik is an original desktop motion-graphics/video editor for beat-synced 9:16 football edits.

- Full design: `docs/SPEC.md`
- Phase plan and status: `docs/PLAN.md`

Keep this file and PLAN.md up to date whenever conventions, commands or architecture change (Engineering Rule 7).

## Ground rules (from the project brief)

1. **Time is never floating-point seconds.**
   - Composition time is an integer `Frame`.
   - Media time is integer `Flicks` (705,600,000/s).
   - Rates and speeds are `Rational`.
   - Floats are allowed only transiently inside evaluation (see SPEC §3.4).
2. **One render pipeline.** Preview and export both run `evaluateFrame()` → `RenderPlan` → `Compositor`. Never add a preview-only or export-only branch that changes pixels. Frame _source_ and output _size_ are the only permitted differences.
3. **Tests are required** for timing maths, keyframe interpolation, time remapping, and project save/load, and for anything else in `src/shared`.
4. **The UI thread stays light.**
   - Decoding, compositing and audio analysis run in workers.
   - FFmpeg runs in the `media-service` utility process.
   - Main only routes requests.
5. **Ask before** adding any dependency not approved in SPEC §15, or changing the architecture.
6. **Commit at the end of every phase:** `kinetik: phase N — <summary>`.
7. **Split phases that get too big** (lettered sub-phases); don't cut corners.
8. **No Adobe names, logos, icons, UI assets or file formats.** Kinetik has its own branding (SPEC §14).

## Commands (run from `kinetik/`)

Requirements: Node 20.19+ (22 LTS recommended) and git.

| Command                           | What it does                                                                         |
| --------------------------------- | ------------------------------------------------------------------------------------ |
| `npm install`                     | Install deps, including the ffmpeg/ffprobe static binaries                           |
| `npm run dev`                     | Launch Electron with hot reload (downloads the Electron binary on first run)         |
| `npm test`                        | Vitest unit tests (`src/**/*.test.ts`)                                               |
| `npm run test:watch`              | Vitest in watch mode                                                                 |
| `npm run test:e2e`                | Builds, then runs the Playwright Electron tests in `test/e2e/`                       |
| `npm run typecheck`               | `tsc --noEmit` for the node (main/preload/shared) and web (renderer/shared) targets  |
| `npm run lint` / `npm run format` | ESLint / Prettier (write)                                                            |
| `npm run check`                   | typecheck, lint, format check, then unit tests. **Must be clean before any commit.** |
| `npm run build` / `npm start`     | Production build into `out/` / run that build                                        |

**Environment variables**

- `KINETIK_USER_DATA=<dir>`: use a separate settings/logs/cache folder (the e2e tests use this).
- `KINETIK_FFMPEG`, `KINETIK_FFPROBE`: use these binaries instead of the bundled ones.

**Headless (cloud/CI)**

- Run e2e under `xvfb-run -a npm run test:e2e`. When running as root, the tests add `--no-sandbox`.
- Playwright drives the Electron binary, so no browser download is needed. Do **not** run `playwright install`.
- E2E tests stub native dialogs in the main process (`dialog.showSaveDialog` etc.) and click menu items by id (`file.save`, …). Keep ids in `src/main/menu.ts` stable.

**Tooling versions:** Vitest is pinned to 3.2 because npm 10 crashes resolving Vitest 4's peer set. TypeScript is pinned to 5.9 for typescript-eslint.

## Folder structure

Paths marked (later) do not exist yet; they show where upcoming phases put things.

```
kinetik/
  docs/SPEC.md, docs/PLAN.md
  src/
    shared/        Pure TS: no DOM, no Node. Most unit tests live here.
      time/        rational.ts (exact Rational), units.ts (Frame, Flicks, timecode)
      project/     schema.ts (zod, versioned), migrations.ts, create.ts, serialize.ts, errors.ts
      ipc.ts       typed IPC channel contract (KinetikApi)
      log-types.ts shared logger types
      anim/        (later) keyframes, bezier solver, easing presets
      history/     (later) Command, History (Immer patches)
      eval/        (later) evaluateFrame → RenderPlan, evaluateAudio → AudioMixPlan
      audio/       (later) beat detection, peaks (pure DSP)
      effects/     (later) effect param specs (the GLSL lives in the renderer)
    main/          Electron main: index.ts (lifecycle, windows), ipc.ts, menu.ts, project-io.ts,
                   logger.ts, ffmpeg.ts (binary resolution + check)
      media-service/  (later) utilityProcess: ffprobe, jobs, proxies, decoder sessions, encoder
    preload/       index.ts: contextBridge → window.kinetik
    renderer/      index.html (CSP) + src/
      src/ui/      React components: Welcome, EditorShell, NewProjectDialog, Logo
      src/state/   Zustand stores (document-store.ts; later project history + transient UI)
      src/lib/     log.ts (forwards to main)
      src/audio/   (later) Web Audio engine / graph builder
      src/render/  (later) render worker: compositor, gl utils, frame sources, shaders/*.glsl
      src/analysis/ (later) analysis worker (waveform, beats)
      src/export/  (later) hidden export window entry
  test/e2e/        Playwright Electron tests
```

## Coding conventions

**TypeScript**

- `strict`, `noUncheckedIndexedAccess` and `exactOptionalPropertyTypes` are all on.
- No `any` (use `unknown` and narrow it).
- Prefer `type` aliases and discriminated unions.

**Time types**

- `Frame` and `Flicks` are branded numbers, created only through the helpers in `shared/time`.
- Don't do arithmetic on raw numbers across units. Use the helpers, which check `Number.isSafeInteger`.

**Purity**

- `src/shared/**` must not import from `electron`, `node:*`, the DOM or React. ESLint enforces this.
- The render path is deterministic: no `Math.random`, `Date.now` or `performance.now` inside `shared/eval`, `renderer/render` or the effects. Use the seeded hash noise in `shared/effects/noise.ts`.

**State and mutations**

- Every project mutation is a `Command` run through `history.execute`. UI code only ever sees `Readonly` project types.
- Transient UI state (selection, zoom, playhead) lives in a separate store and is never undoable.

**IPC**

- Add channels to `shared/ipc.ts` first, and validate payloads with zod in main.
- Bulk data goes over a `MessagePort` with transferables, never through `invoke`.

**Other**

- **Shaders:** `renderer/render/shaders/*.glsl`, GLSL ES 3.00. Sizes are in comp pixels multiplied by `u_renderScale`.
- **Errors:** user-facing errors are friendly and actionable. Everything else goes to the logger with context. Never swallow errors silently.
- **Logging:** use `log.debug/info/warn/error(msg, ctx)` from the logger module, not `console.*` (lint enforces this outside tests).
- **Project schema changes:** any change to `src/shared/project/schema.ts` bumps `PROJECT_VERSION` and adds a migration in `migrations.ts` with a test, even for additive changes.
- **Naming:** files use `kebab-case.ts`, React components use `PascalCase.tsx`, and tests sit next to their source as `*.test.ts`.
- **Formatting:** Prettier with 2 spaces, single quotes, no semicolons, and print width 100.
- **Comments:** explain _why_, especially any time maths or colour maths. Cite the SPEC section for non-obvious rules.

## Key architecture decisions (details in SPEC)

- **ADR-1 Stack:** Electron, React and TypeScript, WebGL2 in an OffscreenCanvas worker, and FFmpeg in a utility process.
- **ADR-2 Time:** integer frames, integer flicks and rationals. The constant-speed mapping is exact integer maths.
- **ADR-3 Frame sources:** preview decodes proxies (H.264, GOP 10, no B-frames, CFR) with WebCodecs. Export and full-res mode decode originals with FFmpeg to raw YUV. A shared **conform chain** guarantees that frame _n_ is the same picture in both. YUV→RGB is always done by Kinetik's own shader.
- **ADR-4 Colour:** display-referred Rec.709 working space in RGBA16F. SDR only in v1, and HDR sources are tone-mapped on conform.
- **ADR-5 Undo:** command pattern on Immer patches, with transactions and merge keys. The render worker is kept in sync through the patch stream.
- **ADR-6 Audio:** the Web Audio clock is master during playback. Export mixes through `OfflineAudioContext` using the same graph builder.
- **ADR-7 Export:** a hidden export window runs the same render worker, and raw RGBA is piped into FFmpeg. The user can keep editing during export.
- **ADR-8 Project file:** a `.kinetik` JSON file with `format` and an integer `version`, forward-only migrations, zod validation, atomic save with `.bak`, and files from newer versions refused.
- **ADR-9 Optical flow:** RIFE (`rife-ncnn-vulkan`) as an offline flow cache, downloaded only with the user's consent. Preview falls back to frame mix until the cache is built.

## Phase workflow

1. Before starting, re-read the phase in `docs/PLAN.md`.
2. Build it, with tests alongside the code.
3. Run `npm run check`, plus `test:e2e` where relevant.
4. Update the PLAN.md status and any spec changes, then commit `kinetik: phase N — …` and push to the working branch.
5. Report: what was built, files changed, exact test steps, known issues, and what the next phase does.
6. **Wait for the user's confirmation before starting the next phase.**

## Repo notes

- This repo is also a GitHub Pages site. The root `index.html` (FightBase) is unrelated, so **don't touch it**.
- Keep build outputs (`out/`, `dist/`, `node_modules/`, caches) gitignored inside `kinetik/`.
