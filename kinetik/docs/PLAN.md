# Kinetik — Build Plan

> Status: **Awaiting approval of SPEC + PLAN** · Last updated: 2026-09-25
>
> **Workflow:**
> - Work happens one phase at a time. A phase is done only when its **acceptance criteria** pass **and** you have confirmed it works on your machine.
> - Every phase ends with a git commit (`kinetik: phase N — <summary>`) and a report covering what was built, the files changed, test steps, known issues and the next phase.
> - Any phase that turns out too large is split into lettered sub-phases rather than trimmed.

## Status

| Phase | Name | Status |
|-------|------|--------|
| 0 | Scaffold | ⏳ Not started |
| 1a | Import, probe, media bin, proxies | ⏳ |
| 1b | Preview engine | ⏳ |
| 2 | Timeline, layers, undo | ⏳ |
| 3 | Keyframes and graph editor | ⏳ |
| 4a | Time remapping, speed ramps, frame mix | ⏳ |
| 4b | Optical-flow slow motion (RIFE) | ⏳ (scope is confirmed again first) |
| 5 | Audio, waveforms, beat sync | ⏳ |
| 6a | Effect stack + shake, zoom punch, flash, vignette, presets | ⏳ |
| 6b | Motion blur, glow, grain, chromatic aberration, sharpen, RAM preview | ⏳ |
| 7 | Colour grading and LUTs | ⏳ |
| 8 | Text and overlays | ⏳ |
| 9 | Export and render queue | ⏳ |
| 10 | Polish, autosave, templates, packaging | ⏳ |

**How this differs from your original outline (Engineering Rule 8):**
- **Phase 1** is split into 1a and 1b. Media ingestion and the GPU preview engine are each a full phase of work.
- **Phase 4** is split into 4a and 4b. Optical flow needs a native binary, a download flow and a cache, and should not hold up time remapping.
- **Phase 6** is split into 6a and 6b. The effect framework and parity harness come first, then the heavier multi-pass effects and the RAM preview.
- A minimal **"render frame N to PNG through the export path"** command arrives in Phase 1b, long before the full exporter in Phase 9. Every phase after it can then *prove* that preview and export match (Rule 2), instead of finding out in Phase 9.

**About where testing happens:** I build in a cloud Linux container with no GPU and no screen. I can run unit tests, lint, typecheck, FFmpeg tests and headless Electron tests (software WebGL). Checks marked **[You]** need you to run the app on your own machine.

---

## Phase 0 — Scaffold

**Build**
- A `kinetik/` npm project using electron-vite. It has main, preload and renderer targets, plus a worker entry. TypeScript is strict.
- The folder structure described in CLAUDE.md.
- `src/shared/project/`: a zod schema for project **version 1**, the `migrations` registry (empty, with the v0→v1 test harness in place), and `createEmptyProject()`.
- `src/main/project-io.ts`: atomic save (tmp, fsync, `.bak`, rename) and load (parse, migrate, validate, with a friendly error on failure).
- An app shell with the Kinetik branding, a welcome screen (New / Open), and a File menu with New, Open…, Save, Save As… and Close. The window title shows the project name and a dirty marker (•).
- A logger with JSON lines under `userData/logs`, renderer log forwarding, and a "Reveal Logs" menu item.
- A typed IPC contract in `src/shared/ipc.ts`, and preload `window.kinetik`.
- Tooling: Vitest, ESLint (flat config), Prettier, `npm run check` (typecheck, lint, test), and a Playwright Electron smoke test.
- Setup for `ffmpeg-static`/`ffprobe-static` and path resolution. Phase 0 only checks `ffmpeg -version` and reports the result in the About dialog.

**Acceptance**
- [ ] `npm install && npm run dev` launches the app window **[You]**
- [ ] New project → Save As `test.kinetik` → Close → Open `test.kinetik` gives the same project (name, id, comp settings) **[You]**
- [ ] Unit tests: save/load round-trip, schema rejects bad files, a newer-version file is refused, the `.bak` is created, and an interrupted write leaves the original intact
- [ ] E2E smoke test (headless): the app launches, a project is created, saved and reopened
- [ ] `npm run check` is clean

---

## Phase 1a — Import, probe, media bin, proxies

**Build**
- `media-service` utility process with a job queue (concurrency 2), progress events and cancellation.
- Import through the dialog and drag-and-drop. Handles mp4, mov, png, jpg, mp3 and wav.
- The ffprobe parser fills `MediaItem.probe`. It detects VFR by comparing `r_frame_rate` with `avg_frame_rate` and scanning packet timestamps, detects HDR from the transfer characteristics, and reads the rotation.
- Media fingerprinting and the cache layout from SPEC §3.8.
- A shared **conform chain** builder (SPEC §3.3), used for proxies now and for full-res decode later.
- Jobs: poster thumbnail, 10-frame filmstrip, proxy (H.264, long edge ≤ 1920, GOP 10, no B-frames, CFR), and audio extraction to 48k float PCM.
- A media bin panel with poster thumbnails, hover-scrub on the filmstrip, badges (4K, 60, VFR, HDR, proxy ●/progress), and a missing-media relink dialog.
- The `kinetik-media://` protocol with Range support, restricted to registered IDs.
- Composition settings dialog (default 1080×1920, 60 fps, 1800 frames).

**Acceptance**
- [ ] Importing each supported type adds it to the bin with the correct metadata **[You]**
- [ ] A 4K clip's proxy is generated in the background with visible progress, and the UI stays responsive (no frozen window) **[You]**
- [ ] Automated: probe parsing fixtures cover CFR, VFR, rotated and HDR-tagged files; the conform chain is snapshot-tested
- [ ] Automated: proxy frame-index parity. For barcode fixtures at 25, 30, 50 and 60 fps, 29.97 and VFR, proxy frame *n* decodes to barcode *n* for every frame
- [ ] Save, then reopen: media is still linked. Move a file: the relink dialog finds it by fingerprint

---

## Phase 1b — Preview engine

**Build**
- A render worker with an OffscreenCanvas and WebGL2 context, the YUV→RGB shader, a texture pool and an RGBA16F framebuffer pool.
- `ProxyFrameSource` (mp4box demux, WebCodecs decode, LRU frame cache, decode-ahead) and `FfmpegFrameSource` (full-res raw YUV sessions).
- `evaluateFrame()` v0: one layer per media item that can be dropped onto the comp, a fit/fill transform, and a RenderPlan.
- A viewer panel with a 9:16 canvas, zoom (fit/50/100/200%), TikTok safe-zone overlay, and draft/full resolution toggle.
- Transport: play/pause (Space), frame step (←/→), ±10 frames (Shift+←/→), Home/End, and a scrub bar. Timecode shows `SS:FF` and the frame number.
- Playback clock, temporary until the audio clock arrives in Phase 5: `requestAnimationFrame` with an integer frame counter derived from elapsed audio-context samples. A dropped-frame counter is shown.
- Debug command **"Render current frame via export path → PNG"**, plus a headless `renderFrame` test hook.

**Acceptance**
- [ ] A 4K 60 fps clip (played from its proxy) plays at 60 fps in a 1080×1920 comp with **0 dropped frames over 20 s**, shown by the on-screen counter **[You]**
- [ ] Scrubbing and arrow-key stepping show the exact frame. Automated: for barcode fixtures, the preview-path frame at comp frame *f* equals the expected source frame from the rational mapping, for sources at 25, 30, 50 and 60 fps and 29.97 in a 60 fps comp
- [ ] Automated: preview path vs export path (`FfmpegFrameSource`) choose the same source frame (barcode match), and pixels differ by at most the tolerance
- [ ] The UI thread stays under 8 ms of work per frame during playback (DevTools performance trace) **[You]**

---

## Phase 2 — Timeline, layers, undo

**Build**
- `src/shared/history/`: `Command`, `History` (execute, undo, redo, transactions, merge keys, limit 500) using Immer patches. Project patches are streamed to the render worker.
- A timeline panel with a ruler, zoom and scroll, and layer rows with a clip bar showing the poster or filmstrip.
- Operations, each a command: add layer (drag from the bin), move, trim in/out, split at playhead (S), slip (Alt-drag), reorder (drag rows), delete, ripple delete, duplicate, and set work area.
- Switches: visible, audible, solo and lock. Blend mode dropdown. Solid and null layers.
- Snapping (SPEC §5) with a visual snap line. Shift temporarily disables it.
- Comp markers: add (M when stopped), move and delete. All undoable.
- Multi-select (click, Shift-click, marquee) and moving several layers at once.
- The Edit menu shows "Undo <label>" / "Redo <label>". Shortcuts are Ctrl/Cmd+Z and Ctrl/Cmd+Shift+Z.

**Acceptance**
- [ ] Build a rough cut of 10 clips with moves, trims, splits and reorders **[You]**
- [ ] Undo each action back to the empty timeline, then redo all of it. The final state matches the pre-undo state exactly. Automated: a property-based test runs random command sequences and checks that undo-all gives the initial state and redo-all gives the final state
- [ ] A 1-second drag creates **one** undo step
- [ ] Blend modes match reference formulas (automated golden 2-layer tests)
- [ ] Snapping to the playhead, edges and markers works, and Shift overrides it **[You]**

---

## Phase 3 — Keyframes and graph editor

**Build**
- `src/shared/anim/`: bezier segment construction, a Newton + bisection solver, and linear, hold and bezier interpolation per dimension (SPEC §6).
- Transform properties become keyframable: a stopwatch toggle, keyframe add/remove at the playhead, a keyframe lane in the timeline, and "go to previous/next key" (J/K).
- Easing presets: linear, ease in, ease out, easy ease and hold (F9-style shortcut, remappable).
- Graph editor panel with value and speed graphs, draggable handles, multi-key selection, box select, and fit-to-view.
- Copy and paste of keyframes (Ctrl/Cmd+C/V) between layers and properties, with relative timing kept and pasted at the playhead.
- A transform gizmo in the viewer: drag position, scale handles, rotate and move the anchor.

**Acceptance**
- [ ] An easy-ease zoom (scale 100→140% over 30 frames) looks smooth, with no jump at either end and no linear feel **[You]**
- [ ] Unit tests: exact endpoints, symmetric easy ease, hold behaviour, influence clamping, the solver converges for extreme handles, reference values, and paste-timing preservation
- [ ] Every keyframe edit is undoable

---

## Phase 4a — Time remapping, speed ramps, frame mix

**Build**
- `src/shared/time/remap.ts`: remap evaluation (SPEC §7), speed-to-source solving, and conversion between the speed graph and keyframe values.
- Constant speed per layer (stretch, which changes the layer duration), with exact rational mapping.
- Enable time remap per layer. Its graph editor shows a speed graph in %.
- Speed-ramp preset tool (SPEC §7 table) that pins a chosen source frame to a chosen beat or marker.
- Frame blending: none and mix. Export frame decoding reuses `FfmpegFrameSource` with reseek.
- Freeze frame at the playhead.

**Acceptance**
- [ ] A ramp from 300% to 20% plays smoothly. **With blending "mix"**, no two consecutive output frames are identical. **With "none"**, repeats appear only where speed < 100%, as SPEC §7 explains **[You]**
- [ ] Automated: the source-frame sequence for the preset is monotonic and has no backwards steps. Velocity is continuous (no speed jumps larger than the tolerance between frames). The pinned source frame lands exactly on the beat frame
- [ ] Unit tests for the remap at 25, 30 and 60 fps and 29.97 sources in a 60 fps comp, including the float→frame rounding edge cases

## Phase 4b — Optical flow (RIFE) *(scope is confirmed with you before starting)*

**Build**
- In-app "Enable optical flow" prompt. It downloads `rife-ncnn-vulkan` and a model after your consent, verifies SHA-256, and stores them in `userData/tools/`.
- A flow-cache builder in `media-service`: extracts the needed source frame pairs, runs RIFE at the required phases (quantised to 1/16), and caches the results.
- Frame blending mode "flow". Preview falls back to mix with a "flow pending" badge. Export blocks until flow frames exist.

**Acceptance**
- [ ] A 20% slow-motion section with flow shows no duplicated frames and no mix ghosting **[You]**
- [ ] With no GPU/Vulkan, flow is disabled cleanly with an explanation and the app does not crash
- [ ] Preview (once cached) and export produce identical flow frames

---

## Phase 5 — Audio and beat sync

**Build**
- A Web Audio preview engine. **The audio clock becomes the playback master.** Per-layer gain keyframes. Scrub audio (short grains) is optional and off by default.
- An analysis worker that builds peak pyramids and draws the waveform on audio (and video-with-audio) layers.
- Beat detection (SPEC §8): kick onsets and a beat grid, run from "Detect beats" on an audio layer. A dialog offers sensitivity, BPM range, offset, and "kicks only / grid / both".
- Tap markers (M during playback) with latency compensation.
- Snapping to beat markers for cuts, layer edges and keyframes. "Cut on beats" helper: split the selected layer at every beat marker in range.

**Acceptance**
- [ ] On your phonk track, detected kick markers line up with the kicks to within 1 frame at 60 fps. You check this by stepping frame by frame over the waveform **[You]**
- [ ] Automated: synthetic 140 and 160 BPM tracks (with hats and 808 noise) have every kick within ±1 frame and at least 95% recall
- [ ] A/V sync: automated clap-and-flash fixture, where the flash frame and the audio transient are within 1 frame during preview

---

## Phase 6a — Effect framework + first effects

**Build**
- An effect registry, effect stack UI (add, reorder by drag, enable/disable, delete, duplicate) and keyframable params that reuse the Phase 3 UI.
- A multi-pass executor with an FBO pool and `renderScale` handling.
- Effects: camera shake (seeded), zoom punch (plus "punch on beat" generator), white flash (plus "flash on beat"), vignette.
- Effect presets: save and load `.kfx` in userData and in the project, with a presets browser.
- **Parity harness:** a headless test renders every effect through the preview and export paths and compares them.

**Acceptance**
- [ ] Every effect in 6a renders bit-identically in preview and export paths (automated)
- [ ] Shake with the same seed gives the same result every time. Different seeds differ (automated)
- [ ] A preset can be saved, reloaded and applied to another layer **[You]**

## Phase 6b — Heavy effects + RAM preview

**Build**
- Directional motion blur driven by transform velocity (including shake), glow (mip-chain blur), film grain, chromatic aberration and sharpen.
- RAM preview (render-ahead cache of the work area) with a cache bar in the timeline ruler.

**Acceptance**
- [ ] All effects render identically in preview and export (automated parity, bit-exact on the same machine)
- [ ] A heavy stack (shake + motion blur + glow + grain) plays in real time after RAM preview **[You]**

---

## Phase 7 — Colour grading

**Build**
- Grade effects: temperature and tint, lift/gamma/gain (colour wheels + master sliders), contrast, saturation, curves (master/R/G/B editor), HSL (8 ranges) and LUT (with intensity).
- A `.cube` parser (1D and 3D, domain handling) and a LUT browser.
- Adjustment layers that apply their stack to everything below.
- A "Kinetik Grade" bundle, and copy/paste of a grade between layers.
- Stretch goal: luma waveform and vectorscope (if they grow large, they move to 7b).

**Acceptance**
- [ ] Clips from two different matches can be graded to match by eye using the tools (and scopes if present) **[You]**
- [ ] LUTs load correctly. Automated: the identity LUT is a no-op within 1/1023, a known reference LUT matches expected outputs, and malformed files give a clear error
- [ ] Grades have preview/export parity (automated)

---

## Phase 8 — Text and overlays

**Build**
- Text layers: system fonts (Local Font Access), fill, stroke and shadow, with an inspector Text tab and on-canvas editing (double-click).
- Per-glyph layout and animation presets: pop-in, typewriter and beat flash (driven by beat markers).
- Overlay workflow: tagging overlays in the bin, and "Add as overlay" (screen or add, full comp).

**Acceptance**
- [ ] Beat-flash and pop-in text lines up with beat markers exactly (automated: the frame of peak intensity equals the marker frame) **[You]**
- [ ] Text renders with preview/export parity (automated)
- [ ] A light-leak overlay with screen blend looks correct over footage **[You]**

---

## Phase 9 — Export

**Build**
- A hidden export window running the shared render worker and `OfflineAudioContext` mixdown.
- The `media-service` encoder: raw RGBA stdin with backpressure, WAV mux, and colour tagging (SPEC §12).
- Encoder capability probe (hardware and software).
- A render queue panel: add the current comp, choose a preset, set the output path, and see progress, ETA and cancel. Multiple items run in sequence.
- Presets: "TikTok 1080×1920 60fps High" (H.264) plus an H.265 variant, with custom bitrate and encoder.

**Acceptance**
- [ ] The exported file plays correctly in VLC and QuickTime/Windows Media Player, and uploads to TikTok **[You]**
- [ ] Automated: ffprobe reports 1080×1920, 60/1 fps and the expected frame count. Barcode fixture frames come out in the exact order. Exported frames match preview-path frames within the encode tolerance
- [ ] Automated: A/V offset < 1 ms (clap/flash cross-correlation)
- [ ] Cancel stops within 1 s and removes the partial file

---

## Phase 10 — Polish

**Build**
- Autosave every 60 s when there are changes (to `userData/autosave/`) and crash recovery on next launch ("Recover unsaved changes?").
- A shortcut map and a shortcuts editor/cheat sheet.
- Project templates: **Hook (0–3 s) / Build / Drop / Outro**, with pre-placed markers, placeholder layers, a text hook and a grade adjustment layer, sized to BPM.
- A recent-projects list on the welcome screen and in the File menu.
- Packaging with electron-builder for your OS, plus crash-reporting logs.

**Acceptance**
- [ ] A full 30-second edit (import, cut to beats, ramps, shakes, grade, text, export) is made start to finish without leaving the app **[You]**
- [ ] Killing the app mid-edit (`kill -9`) and relaunching offers recovery with at most 60 s of work lost
