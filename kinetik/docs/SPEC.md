# Kinetik — Technical Specification

> Status: **Draft v1, awaiting approval** · Last updated: 2026-09-25
>
> Kinetik is an original desktop motion-graphics and video editor for short-form,
> beat-synced football edits (9:16, 20–45 s, speed ramps, shakes, zooms, grading).
> It is not affiliated with or modelled on any commercial product's assets,
> branding, icons or file formats.

---

## 1. Decisions from the kickoff questions

| # | Question | Decision |
|---|----------|----------|
| 1 | Tech stack | Electron + React + TypeScript; WebGL2 for GPU compositing and effects; FFmpeg/ffprobe for probing, proxies, audio extraction, full-res decode and encoding. |
| 2 | Hardware | Not specified, so the app targets **Windows 10/11 x64, macOS 12+ (Apple Silicon or Intel) and Linux x64**. Baseline: 4-core CPU, 16 GB RAM, a GPU with WebGL2 and `EXT_color_buffer_float`. Hardware encoders (NVENC, QuickSync, AMF, VideoToolbox) are **detected at runtime**, with a fallback to software libx264/libx265. The target OS must be confirmed before Phase 9 (export) and Phase 10 (packaging). |
| 3 | Scope | **Football-edit features only** for v1. The architecture (composition model, effect registry, frame sources) is general-purpose, so it can grow later without a rewrite. |
| 4 | Footage | Mixed **1080p and 4K** at **25, 30, 50 or 60 fps**, including 29.97/59.94. Variable-frame-rate (VFR) sources such as phone clips and screen recordings are **conformed to a constant frame rate** on import. HDR (HLG/PQ) sources are tone-mapped to SDR Rec.709, because v1 is SDR-only. |
| 5 | Extras | **Beat detection:** yes, written in-house in TypeScript with no downloads. **Optical flow:** yes, as its own phase (4b), using `rife-ncnn-vulkan`. The binary and models (roughly 10–50 MB) are downloaded **only when the user clicks "Enable optical flow"** in the app, never automatically. Scope is confirmed again before 4b starts. |
| 6 | Location | The app lives in the `kinetik/` subfolder of this repo. The root `index.html` (a GitHub Pages site) is left untouched. |
| 7 | Name | **Kinetik** |

---

## 2. Feature summary (v1)

**Project and media**
- Uses a versioned JSON project file (`.kinetik`) with migrations, atomic saves and a backup of the previous save.
- Imports mp4/mov video, png/jpg images and mp3/wav audio, probed with ffprobe.
- Has a media bin with poster thumbnails, filmstrip hover-scrub and metadata badges (resolution, fps, VFR, HDR, proxy status).
- Generates proxies automatically in the background. A job queue shows progress.
- Relinks missing media using a stored fingerprint.

**Composition**
- The default composition is 1080×1920, 60 fps, 30 s (1800 frames), with a black background. All of these can be edited.
- Supports multiple compositions per project. Nested compositions are out of scope for v1 (see §13).

**Timeline**
- Layer types: video, image, audio, solid, text, adjustment and null.
- Editing: move, trim in/out, split at the playhead, reorder, slip, and ripple delete.
- Switches: solo, mute (audio), hide (video) and lock.
- Blend modes: normal, add, screen, multiply, overlay, soft light, lighten, darken and difference.
- Snapping to the playhead, layer edges, in/out points, markers and beat markers.
- Undo/redo for every mutation, built on a command pattern with transactions for drags.

**Animation**
- Keyframable transform: anchor point, position, scale (linked or unlinked), rotation and opacity.
- Easing presets: linear, ease in, ease out, easy ease and hold.
- A bezier graph editor with value and speed views.
- Copy and paste of keyframes between layers, with relative timing kept.

**Time**
- Constant speed (stretch) per layer.
- Time remapping with a speed graph.
- Speed-ramp presets.
- Frame blending: none, frame mix, or optical flow (Phase 4b).

**Audio and beats**
- Waveforms on audio layers.
- Automatic beat detection: kick onsets and a beat grid are turned into markers.
- Tap markers with the M key during playback.
- Snap cuts and keyframes to beat markers.

**Effects (GPU)**
- A reorderable, keyframable effect stack on each layer.
- Effects: camera shake, zoom punch, white flash, directional motion blur, glow, film grain, chromatic aberration, sharpen and vignette.
- Effect presets can be saved and loaded.

**Colour**
- Lift/gamma/gain, curves, HSL by hue range, saturation, contrast, temperature and tint.
- `.cube` LUT import, both 1D and 3D.
- Adjustment layers that grade everything below them.

**Text and overlays**
- Text layers using system fonts, with fill, stroke and shadow.
- Animation presets: pop-in, typewriter and beat flash.
- Overlay clips such as light leaks and grain, using screen or add blend modes.

**Export**
- A render queue that encodes through FFmpeg to H.264 or H.265.
- Bitrate control and a choice of hardware or software encoder.
- A "TikTok 1080×1920 60fps High" preset.
- A progress bar, a cancel button and an ETA.

**Polish**
- Autosave and crash recovery.
- A shortcut map.
- Templates structured as hook, build, drop and outro.
- A recent-projects list.

---

## 3. Architecture

### 3.1 Process and thread model

```
┌──────────────────────────── Electron main process ─────────────────────────────┐
│ app lifecycle · windows · menus · file dialogs · project I/O (atomic save)     │
│ logger (JSON lines) · IPC router · kinetik-media:// protocol (range requests)  │
│                                                                                │
│   ┌──────────── utilityProcess: "media-service" ─────────────┐                 │
│   │ job queue: ffprobe · thumbnails · proxies · audio→PCM    │                 │
│   │ full-res decoder sessions (ffmpeg → raw YUV)             │                 │
│   │ export encoder (raw RGBA/YUV + WAV → ffmpeg → mp4)       │                 │
│   │ rife-ncnn-vulkan runner (Phase 4b)                       │                 │
│   └──────────────────────────────────────────────────────────┘                 │
└───────────────▲──────────────────────────────▲─────────────────────────────────┘
                │ typed IPC (contextBridge)    │ MessagePort (frames, bulk data)
┌───────────────┴───────── Editor window (renderer) ──────────┐  ┌─ Export window ──┐
│ UI thread: React UI · project store · command history       │  │ (hidden)         │
│            Web Audio preview engine (audio thread)          │  │ same render      │
│  ├─ Render worker: OffscreenCanvas + WebGL2 compositor      │  │ worker code,     │
│  │    frame sources (WebCodecs proxy decode / ffmpeg YUV)   │  │ full-res sources,│
│  │    texture + frame caches, effect shaders                │  │ OfflineAudio mix │
│  └─ Analysis worker: waveform peaks · beat detection        │  └──────────────────┘
└─────────────────────────────────────────────────────────────┘
```

**Rules**
- The UI thread never decodes, composites or analyses audio. It only renders React and holds the project state.
- The main process only routes requests and does light I/O. Everything heavy that uses FFmpeg runs in the `media-service` utility process, so a stalled FFmpeg pipe can never freeze window management.
- Export runs in a **hidden export window** that loads the **same render-worker bundle** as the editor. The user can keep editing while an export runs, because the export works from a frozen project snapshot.

### 3.2 The single render pipeline (preview = export)

Everything visible is produced by two pure functions and one executor:

```
evaluateFrame(project, compId, frame, quality) → RenderPlan     (src/shared/eval, pure TS)
evaluateAudio(project, compId, range)          → AudioMixPlan   (src/shared/eval, pure TS)
Compositor.execute(RenderPlan)                 → pixels         (render worker, WebGL2)
```

- A **RenderPlan** is plain data. It lists each draw op in stacking order with fully resolved parameters: the transform matrix, opacity, blend mode, and effect instances with their evaluated parameters (including the camera-shake seed and frame). It also lists **source frame requests** as `{mediaId, frameIndex, blend: {nextIndex, weight} | null}`.
- The preview and export paths call the **same `evaluateFrame`** and the **same `Compositor`** code. They differ only in:
  1. **Frame source:** preview uses proxies decoded with WebCodecs; export uses originals decoded by FFmpeg. Both deliver YUV planes plus colour metadata, and **Kinetik's own shader** converts them to RGB, so the matrix and range handling is identical.
  2. **Output size:** preview draws at viewer resolution, and export at comp resolution. Effects express sizes in **comp pixels** and are scaled by `renderScale`, so a blur of radius 20 looks the same at any viewer zoom.
- **Determinism rule:** the render path never calls `Math.random`, `Date.now`, `performance.now` or anything that depends on wall-clock time. All procedural effects use seeded hash noise that is a pure function of `(seed, frame, subframe)`.
- **Preview may drop frames, but never renders them differently.** If playback falls behind, frames are skipped and a dropped-frame counter is shown. There is no hidden low-quality mode. A draft-quality toggle exists (it halves the resolution) and is shown clearly in the viewer when active.
- **Parity tests:** a headless test renders the same frames through both paths and compares them. Solids, images and text must match exactly. Video must match within a tolerance of ≤1/255 mean absolute difference, because proxy and original differ in resolution. Test fixtures carry a frame-index barcode, so the check confirms the *same source frame* was chosen.

### 3.3 Frame sources and decoding

- `FrameSource` interface: `getFrame(mediaId, frameIndex): Promise<YuvFrame>`, plus `prefetch(range)` and `release`.
- **`ProxyFrameSource`** (preview):
  - Demuxes the proxy MP4 with `mp4box.js`, reading it through the `kinetik-media://` protocol with HTTP range support.
  - Decodes with the WebCodecs `VideoDecoder`, hardware-accelerated where available.
  - Proxies use a short GOP with no B-frames, so a random seek decodes at most 9 frames.
  - Decoded `VideoFrame`s are copied to planar textures (`copyTo`) and cached in an LRU cache with a configurable budget (default 1.5 GB).
- **`FfmpegFrameSource`** (export, and the optional "Full-res preview" toggle):
  - Runs a decoder session in `media-service`: `ffmpeg -ss <t> -i original <conform chain> -f rawvideo -pix_fmt yuv420p|yuv420p10le pipe:`.
  - Frames stream over a MessagePort. The session reads sequentially and reseeks only on backward or long forward jumps (time-remapped layers).
- **Conform chain.** One function builds the FFmpeg filter chain for both proxy generation and full-res decode, so frame *n* means the same picture on both paths:
  1. Apply rotation metadata.
  2. Tone-map HDR to SDR Rec.709 (`zscale` + `tonemap=hable`), for HDR sources only.
  3. Resample to constant frame rate (`fps=<conformRate>:round=near`). For VFR sources the conform rate is the nominal rate, rounded to the nearest standard rate.
  4. Proxy only: scale so the long edge is 1920 px, using Lanczos.
- **Colour:** YUV→RGB happens in `shaders/yuv.glsl`. It supports BT.709 and BT.601 matrices and limited or full range, read from the ffprobe tags. It defaults to BT.709 limited for HD and above, and BT.601 for SD.
- **Working space:** display-referred Rec.709/sRGB values held in **RGBA16F** framebuffers, giving headroom for glow and add blends without clipping mid-chain. Values are clamped only at the final output. v1 does not use a linear-light working space. Blend and grading formulas are defined on display-referred values, which is what editors expect for screen and add overlays.

### 3.4 Timing model (Engineering Rule 1)

No time value is ever stored or passed between modules as floating-point seconds.

| Quantity | Type | Notes |
|----------|------|-------|
| Composition time | `Frame`: branded integer | Frame index in the comp's frame rate. |
| Frame rates, speeds | `Rational {num, den}` | Normalised with gcd, `den > 0`, safe-integer checked. Examples: 60/1, 30000/1001, speed 3/1. |
| Media/source time | `Flicks`: branded integer | 705,600,000 per second. Divides evenly by 24, 25, 30, 48, 50, 60, 90 and 120, by 23.976, 29.97 and 59.94, and by 44.1k and 48k sample rates. 2^53 flicks is about 147 days. |
| Audio position | integer sample index | 48,000 Hz internally. |
| Keyframe times | `Frame` | Always whole comp frames. Sub-frame keys are not allowed in v1. |

**Constant-speed mapping is exact:**
```
srcFrameExact = (f − layer.startFrame) · speed · srcFps / compFps     (Rational arithmetic)
srcFrame      = floor(srcFrameExact)                                  (integer floor, no epsilon)
blendWeight   = frac(srcFrameExact)                                   (only if frame blending ≠ none)
```

**Time remap:**
- Keyframe *values* are source times stored as integer `Flicks`.
- Interpolation happens in float64 inside `evaluateFrame`, where it is immediately converted to a source frame position:
  ```
  pos = srcFlicks · srcFps / FLICKS_PER_SECOND
  idx = floor(pos + 1e-9)
  ```
  The 1e-9 guards against 4.9999999 turning into 4.
- The float value is never stored. It is covered by tests at 25, 30, 50 and 60 fps, and at 29.97.

**Frame rate conversion:**
- A 25 fps source in a 60 fps comp uses sample-and-hold (`floor`) by default, or frame mix or optical flow when enabled.
- The frame-rate difference is always explicit in the math, never implied.

### 3.5 State management and undo (Engineering Rule 2, Phase 2)

- The project state is an **immutable tree** held in a Zustand store. Mutations are **commands** applied with Immer, which yields forward and inverse patches.
  ```ts
  interface Command { label: string; apply(draft: Project): void; mergeKey?: string }
  history.execute(cmd)           // applies, records {label, patches, inversePatches}
  history.begin(label) / commit() / cancel()   // transactions for drags and scrubs
  history.undo() / redo()
  ```
- **Every** project mutation goes through `history.execute`. A lint rule and a test enforce this by exporting only readonly types to UI code.
- Transient UI state (selection, zoom, playhead, panel sizes) lives in a separate store and is **not** part of undo. Selection is restored on undo as a courtesy.
- **Coalescing:** consecutive commands with the same `mergeKey` within a transaction become one undo step. For example, dragging a position value creates one step.
- **History limit:** 500 steps, stored as patches, so it uses little memory.
- The render worker receives a **structurally shared snapshot** after each commit. It is posted as a patch stream, so the worker keeps an identical copy without re-sending the whole project.

### 3.6 IPC and security

- `contextIsolation: true`, `sandbox: true` and `nodeIntegration: false`. The app loads no remote content, and a CSP is set.
- The preload script exposes one typed `window.kinetik` API. Channel names and payload types live in `src/shared/ipc.ts`, and payloads are validated with zod in main.
- Bulk data (frames, PCM, peaks) travels over transferable `MessagePort`s, not `ipcRenderer.invoke`.
- **`kinetik-media://` protocol:** serves only files that are registered media, proxies or cache entries, by opaque ID. There is no arbitrary path access. It supports `Range`.

### 3.7 Logging

- Main writes JSON-lines logs to `<userData>/logs/kinetik-YYYY-MM-DD.log` and keeps 7 days.
- The renderer and workers forward log lines through IPC.
- Levels are `debug`, `info`, `warn` and `error`. The default is `info` in production and `debug` in development.
- Every FFmpeg job logs its full command line, exit code and the last 50 lines of stderr.
- A "Reveal logs" menu item opens the log folder.

### 3.8 Caches

- Location: `<userData>/cache/<mediaFingerprint>/`, holding `proxy.mp4`, `poster.jpg`, `filmstrip.jpg`, `audio.f32` (48k stereo float PCM), `peaks.bin`, `beats.json` and `flow/<segment>/…`.
- Fingerprint: `sha256(size ‖ mtime ‖ first 1 MiB ‖ last 1 MiB)`, truncated to 16 hex chars.
- Caches are shared across projects, can be cleared from Preferences, and are rebuilt on demand.

---

## 4. Project file format

- The extension is `.kinetik`. The file is UTF-8 JSON, pretty-printed with 2-space indents, which makes diffs readable.
- **Save sequence:**
  1. Serialise the project.
  2. Validate it with the zod schema.
  3. Write it to `<file>.tmp`.
  4. `fsync` the temp file.
  5. Rename the previous file to `<file>.bak`.
  6. Rename `.tmp` to `<file>`.
- **Load sequence:**
  1. Parse the JSON.
  2. Read `format` and `version`.
  3. Run migrations in order: `migrations[v](json) → json(v+1)`.
  4. Validate against the current schema.
- A file whose version is newer than the app supports is refused with a clear message and is never modified.

### 4.1 Top-level shape (version 1)

```jsonc
{
  "format": "kinetik.project",
  "version": 1,
  "id": "5f0c…",                       // uuid v4
  "name": "Haaland — Derby Edit",
  "createdAt": "2026-09-25T18:00:00.000Z",
  "modifiedAt": "2026-09-25T18:42:10.000Z",
  "appVersion": "0.1.0",
  "settings": { "defaultCompId": "c1", "snapping": true },
  "media": [ /* MediaItem */ ],
  "compositions": [ /* Composition */ ],
  "presets": { "effects": [], "text": [] } // project-local presets (global ones live in userData)
}
```

### 4.2 MediaItem

```jsonc
{
  "id": "m1",
  "kind": "video",                      // video | image | audio
  "name": "derby_goal.mp4",
  "path": "/Users/me/Clips/derby_goal.mp4",   // absolute
  "relPath": "../Clips/derby_goal.mp4",       // relative to project file, for relinking
  "fingerprint": "a1b2c3d4e5f60718",
  "probe": {
    "width": 3840, "height": 2160, "rotation": 0,
    "sourceFrameRate": { "num": 60000, "den": 1001 },
    "conformFrameRate": { "num": 60000, "den": 1001 },
    "isVfr": false, "isHdr": false,
    "frameCount": 1438, "durationFlicks": 16931952000,
    "colour": { "matrix": "bt709", "range": "limited", "transfer": "bt709" },
    "codec": "hevc",
    "audio": { "channels": 2, "sampleRate": 48000 }   // or null
  }
}
```
Proxy and cache state is **not** stored in the project. It is derived from the fingerprint at runtime.

### 4.3 Composition, layer, property

```jsonc
{
  "id": "c1", "name": "Main",
  "width": 1080, "height": 1920,
  "frameRate": { "num": 60, "den": 1 },
  "durationFrames": 1800,
  "background": [0, 0, 0, 1],
  "markers": [ { "id": "k1", "frame": 120, "kind": "beat", "label": "", "strength": 0.92 } ],
  "workArea": { "inFrame": 0, "outFrame": 1800 },
  "layers": [ /* top-most first */ ]
}
```

```jsonc
{
  "id": "L1", "name": "derby_goal", "type": "video",   // video|image|audio|solid|text|adjustment|null
  "mediaId": "m1",
  "startFrame": -30,        // comp frame where source frame 0 sits
  "inFrame": 0, "outFrame": 240,   // visible comp-frame range [in, out)
  "speed": { "num": 1, "den": 1 },
  "switches": { "visible": true, "audible": true, "solo": false, "locked": false },
  "blendMode": "normal",
  "parentId": null,
  "transform": {
    "anchor":   { "value": [1920, 1080] },
    "position": { "value": [540, 960], "keyframes": [] },
    "scale":    { "value": [100, 100], "linked": true, "keyframes": [] },
    "rotation": { "value": 0 },
    "opacity":  { "value": 100 }
  },
  "timeRemap": null,        // or { "enabled": true, "blending": "none|mix|flow", "keyframes": [...] }
  "audio": { "gainDb": 0, "keyframes": [] },
  "effects": [ { "id": "e1", "type": "kinetik.shake", "enabled": true, "params": { "intensity": { "value": 20 }, "seed": { "value": 1337 } } } ],
  "text": null              // TextSpec for text layers
}
```

**Keyframe (all animatable properties):**
```jsonc
{
  "frame": 120,                      // integer comp frame, relative to the comp (not the layer)
  "value": [540, 960],               // number | number[]; for timeRemap: integer Flicks
  "interp": { "in": "bezier", "out": "bezier" },   // linear | bezier | hold
  "ease":   { "in":  [{ "speed": 0, "influence": 0.333 }],   // one entry per dimension
              "out": [{ "speed": 0, "influence": 0.333 }] }
}
```
- `speed` is in value units per comp frame, and `influence` is between 0.01 and 1.
- Each segment between two keyframes becomes a cubic bezier on the value-against-time graph (see §6).
- Easing presets write these fields:
  - linear: `interp: linear`
  - easy ease: speed 0, influence ⅓ on both sides
  - ease in: the incoming side only
  - ease out: the outgoing side only

---

## 5. Timeline behaviour

**Editing**
- **Move:** changes `startFrame`, `inFrame` and `outFrame` by the same delta. Keyframes move with the layer. They are stored in comp time, so the command shifts them too.
- **Trim:** changes `inFrame` or `outFrame` only, clamped to the available source frames (unless time remapping is on).
- **Split:** duplicates the layer at the playhead. The left copy keeps `[in, P)` and the right copy keeps `[P, out)`. Keyframes are copied to both halves, and interpolated keys are inserted at the split point where needed.
- **Slip:** changes `startFrame` while `in` and `out` stay fixed.

**Snapping**
- Targets: the playhead, all layer in/out points, comp markers, beat markers and keyframes (when dragging keyframes).
- The threshold is 8 screen pixels. The highest-priority target within the threshold wins, in this order: playhead, beat marker, layer edge, other.
- Holding Shift (or the toggle in the timeline toolbar) turns snapping off for the current drag.

**Switches**
- **Solo:** if any layer is soloed, only soloed visual layers render.
- **Lock:** blocks selection and editing of the layer.

**Adjustment layers**
- Everything below the adjustment layer is rendered into a buffer.
- The adjustment layer's effect stack is applied to that buffer.
- The result is mixed back over the original by the adjustment layer's opacity, only within its `[in, out)` range.

---

## 6. Keyframe interpolation (Phase 3)

**Segment shape**
- For keyframes `k0` (at frame t0, value v0) and `k1` (at t1, value v1), with `Δt = t1 − t0`:
  - `P0 = (t0, v0)`
  - `P1 = (t0 + out.influence·Δt, v0 + out.speed·out.influence·Δt)`
  - `P2 = (t1 − in.influence·Δt, v1 − in.speed·in.influence·Δt)`
  - `P3 = (t1, v1)`
- If the influences sum to more than 1, they are scaled down proportionally.

**Evaluation**
- To evaluate at frame f, solve `x(u) = f` for u using Newton–Raphson with a bisection fallback, to a tolerance of 1e-7 frames. The result is `y(u)`.
- `hold` returns v0 until t1. `linear` is ordinary linear interpolation.

**Dimensions**
- Multi-dimensional values are interpolated **per dimension**, using separate easing per dimension.
- Spatial (curved) motion paths are out of scope for v1. Position moves in straight lines, with easing.

**Graph editor**
- **Value graph:** shows the bezier curves directly, with draggable handles.
- **Speed graph:** shows the derivative `dv/df`. Dragging a handle changes speed or influence.
- **Time remap:** the speed graph is shown as a percentage, where 100% means 1 source second per comp second.

**Unit tests**
- Endpoints are exact.
- Monotonic `x(u)` is maintained.
- Easy ease is symmetric.
- Hold returns the correct values.
- Keys pasted onto another layer keep their relative timing.
- Results match reference values computed independently.

---

## 7. Time remapping and speed ramps (Phase 4)

**Model**
- `timeRemap.keyframes` store source time (`Flicks`) at comp frames.
- Enabling time remapping adds a key at `inFrame` (source time at in) and a key at `outFrame` (source time at out).

**Speed-ramp tool**
- The user picks a preset and a beat marker.
- The tool **solves** for keyframe values and easing so the speed curve matches the preset while the source frame at the beat stays where the user placed it. For example, the ball-strike frame stays pinned to the beat.

**Presets (speeds in %)**

| Preset | Speed shape |
|--------|-------------|
| Fast into slow on beat | 300% eased to 20%, reaching 20% at the beat |
| Slow into fast | 20% to 300% |
| Punch | 100% to 400% to 100% over 6 frames |
| Freeze on beat | 100%, then 0% (hold) for N frames, then 100% |
| Smooth ramp | 100% to 30% to 100% |

**Frame blending**
- **none:** shows `floor(pos)`. At 20% speed from a 60 fps source in a 60 fps comp, each source frame is shown for 5 comp frames. This duplication is **inherent** to having no blending.
- **mix:** cross-fades between `floor(pos)` and `floor(pos)+1` by the fractional part.
- **flow (Phase 4b):**
  - Uses intermediate frames generated by RIFE and cached per `(media, srcFrame, phase)`, with the phase quantised to 1/16.
  - Preview falls back to *mix* for any frames not yet generated, and the viewer shows a "flow pending" badge.
  - Export waits for flow frames, so the output is always true flow.
  - A "Build flow cache" button renders ahead.

**Audio on remapped layers**
- Muted by default in v1. Football edits use a music bed.
- Layers at a constant speed other than 100% get resampled audio with the pitch shifted.

---

## 8. Audio and beat detection (Phase 5)

**Preview engine**
- Web Audio `AudioContext` at 48 kHz.
- Clip PCM (`audio.f32`) is loaded into `AudioBuffer`s.
- Mix graph: per-layer gain (keyframed), then the master bus.

**Clock and sync**
- **The audio clock is master** during playback. The current frame is `floor(playedSamples · fps.num / (48000 · fps.den))`, computed with integer maths.
- The render worker renders whichever frame is current and skips any it has fallen behind on.

**Export**
- Export uses `OfflineAudioContext`, built by the **same graph builder** from the same `AudioMixPlan`. It writes a 48 kHz float WAV that FFmpeg then muxes.

**Waveform**
- The analysis worker builds min/max peak pyramids (256, 1024 and 4096 samples per bucket) and stores them in `peaks.bin`.

**Beat detection (analysis worker, pure TS in `src/shared/audio/`)**
1. Mix down to mono at 48 kHz. Run an STFT with a 2048-sample window and a 256-sample hop (5.3 ms).
2. **Kick onset function:** half-wave-rectified spectral flux in the 40–150 Hz band, log-compressed, with a small weight for full-band flux.
3. **Peak picking:** use an adaptive threshold (moving median plus k·MAD) and a minimum spacing of 100 ms. Refine each peak to sub-hop precision with parabolic interpolation, then correct for window latency.
4. **Tempo and grid:** estimate BPM (60–200) by autocorrelating the onset function. Then find the beat phase with dynamic-programming beat tracking.
5. **Output:**
   - `kick` markers (onsets above a strength cutoff the user can adjust).
   - `beat` markers (the grid).
   - Downbeat guesses every 4 beats.
   - All markers are rounded to the **nearest** comp frame, and their strength is stored.
6. A global "beat offset (ms)" setting corrects any remaining latency.

**Tap markers**
- Pressing M during playback creates a marker at the audio-clock frame, minus a tap-latency compensation (default 0 ms, adjustable).

**Tests**
- Synthetic tracks with kicks at known sample positions, at 140 and 160 BPM (phonk range), with and without hi-hat and 808 noise.
- Pass condition: every detected kick lies within ±1 frame at 60 fps (±16.7 ms) of the truth, with at least 95% recall.

---

## 9. Effects (Phase 6)

- Each effect is a module: `{ type, version, params: ParamSpec[], glsl, uniforms(evalCtx) → UniformValues, passes }`.
- Params are keyframable properties that use the same keyframe model as §6.
- Effect-stack execution: `layerTexture → pass → pass → … → composite`. Passes ping-pong between RGBA16F framebuffers from a pool.
- Sizes are in comp pixels and multiplied by `renderScale`.
- **Presets:** saved as JSON (`.kfx`) holding the effect list and params, including keyframes relative to the first key. They are stored in `<userData>/presets/effects/` or in the project.

| Effect | Key params | Notes |
|--------|------------|-------|
| Camera shake | intensity (px), rotation (°), frequency (Hz), seed, octaves, motion-blur tie-in | 2-D fractal value noise hashed from `(seed, t)`. `t` comes from the comp frame via rational→float at eval time. Deterministic. |
| Zoom punch | amount (%), duration (frames), ease, anchor | Can also be generated as scale keyframes on a beat marker ("punch on beat"). |
| White flash | colour, peak opacity, attack, decay (frames) | Added to the layer or the whole comp. There is also a beat-triggered variant. |
| Directional motion blur | shutter angle (°), samples | Velocity comes from evaluating the layer transform at `f ± shutter/2` (a pure function), then a line blur along the screen-space vector. Combines with shake. |
| Glow | threshold, radius, intensity, tint | Bright pass, then separable Gaussian blur down a mip chain, then an add. |
| Film grain | amount, size, softness, seed, animated | Hash noise per frame, applied in luma. |
| Chromatic aberration | amount (px), direction, radial falloff | Samples R, G and B at offsets. |
| Sharpen | amount, radius | Unsharp mask. |
| Vignette | amount, midpoint, roundness, feather | |

**Parity acceptance:** a headless test renders each effect with fixed params through the preview and export paths and requires bit-identical output on the same machine (§3.2).

**RAM preview:** a render-ahead cache stores composited frames of the work area as `ImageBitmap`s, within a budget, for real-time playback of heavy stacks. It is invalidated by project patches that touch the affected layers or frames.

---

## 10. Colour grading (Phase 7)

Each tool is an effect in the same effect stack, with a fixed internal order when it is added as the "Kinetik Grade" bundle.

- **White balance:**
  - **Temperature/tint:** a von Kries-style scale of R, G and B derived from Planckian-locus offsets, normalised to keep luma.
- **Primaries:**
  - **Lift/gamma/gain:** per channel and master, using `out = pow(clamp(gain·(x + lift·(1−x)), 0, ∞), 1/gamma)`.
  - **Contrast:** around a pivot of 0.435 (18% grey in Rec.709 gamma).
  - **Saturation:** Rec.709 luma-preserving.
- **Curves:** master, R, G and B curves as monotone cubic splines (Fritsch–Carlson), baked into a 1024-entry float 1D LUT texture.
- **HSL:** 8 hue ranges, each with hue shift, saturation and luminance. Weights are smooth (raised cosine) in the HSL domain.
- **LUT:**
  - `.cube` parser supporting `TITLE`, `LUT_1D_SIZE`, `LUT_3D_SIZE` (≤ 65), `DOMAIN_MIN` and `DOMAIN_MAX`.
  - 3D LUTs are stored as `TEXTURE_3D` RGBA16F and sampled trilinearly (tetrahedral interpolation is a stretch goal). There is an intensity mix.
  - Parser tests cover the identity LUT, domain scaling and malformed files.
- **Scopes** (a stretch goal inside Phase 7, or 7b): luma waveform and a vectorscope computed on the GPU. These help with "matching clips from different matches by eye".

---

## 11. Text and overlays (Phase 8)

**Rendering**
- Text is rasterised in the render worker with an `OffscreenCanvas` 2D context at comp resolution × `renderScale`, then uploaded as a texture.
- Static text is cached. Animated text is laid out per glyph: glyph positions are measured once, and each glyph is drawn with its own evaluated transform and opacity.

**Fonts**
- The font list comes from the Local Font Access API (`queryLocalFonts`). Electron grants the permission in main.
- Rendering uses the family name. A missing font falls back to a bundled open-licence font and shows a warning.

**TextSpec**
- Text, font family and weight, size, tracking, line height, alignment and fill.
- Stroke: width, colour, and whether it sits over or under the fill.
- Shadow: offset, blur, colour and opacity.
- Uppercase toggle.

**Animation presets** (these generate keyframes or per-glyph params)
- **Pop-in:** scale 0→115→100% with an overshoot ease and a per-glyph stagger.
- **Typewriter:** glyph opacity reveal at N characters per frame, with an optional cursor.
- **Beat flash:** opacity or fill brightness pulses on each beat marker inside the layer's range, with decay.

**Overlays**
- Ordinary video or image layers with screen or add blend modes. The media bin has an "Overlay" tag and a one-click "add as overlay (screen, full comp)" action.

---

## 12. Export (Phase 9)

**Flow**
1. The render queue item captures `{projectSnapshot, compId, range, preset, outputPath}`.
2. The hidden export window renders frames in order through the shared pipeline at comp resolution.
3. Frames are read back with PBO double-buffering and sent over a MessagePort to `media-service`.
4. `media-service` writes them to FFmpeg's stdin as `rawvideo rgba`, with backpressure.
5. Audio is rendered through `OfflineAudioContext` into a WAV file and muxed in by FFmpeg.

**FFmpeg output settings**
- Scaling uses `out_color_matrix=bt709:out_range=tv` into `yuv420p`, tagged `-colorspace bt709 -color_primaries bt709 -color_trc bt709`.
- `-r` is set to the comp rational, e.g. `60/1`, for exact CFR.
- `-movflags +faststart`.
- H.265 uses the `-tag:v hvc1` tag.

**Encoders**
- `libx264`, `libx265`, `h264_nvenc`, `hevc_nvenc`, `h264_qsv`, `h264_amf` and `h264_videotoolbox`.
- They are probed at startup with a one-frame test encode, and only the working ones are offered.

**"TikTok 1080×1920 60fps High" preset**
- Video: H.264 High profile, level 4.2, 1080×1920, 60/1 fps, VBR with a 16 Mb/s target and a 24 Mb/s max, GOP of 60, `yuv420p`, BT.709.
- Encoder: libx264 `-preset slow`.
- Audio: AAC-LC, 48 kHz stereo, 320 kb/s.

**Progress, cancel and errors**
- Progress is shown as frames done out of the total.
- The ETA is an exponential moving average of the frame rate over the last 120 frames.
- Cancel kills FFmpeg, deletes the partial file and leaves the queue item marked "Cancelled".

**Verification** (automated in a test)
- ffprobe confirms the frame count, frame rate and duration.
- Per-frame barcode fixtures confirm frame-for-frame order.
- Cross-correlating the exported audio against the mix confirms A/V offset < 1 ms.
- Rendered frames are compared with preview-path frames.

---

## 13. Out of scope for v1

- Nested or pre-composed compositions.
- Masks and rotoscoping.
- 3D layers and cameras.
- Motion tracking.
- Expressions or scripting.
- Spatial motion-path curves.
- HDR output.
- ProRes or other intermediate export.
- Plugin SDK.
- Collaboration.
- Proxy editing for remote or cloud media.

These are recorded so the architecture doesn't block them. In particular, `RenderPlan` can later hold nested plans for pre-comps.

---

## 14. UI and visual identity (original design)

**Look**
- The **Kinetik** brand is a lowercase wordmark with a slanted "k" monogram, drawn as original SVG.
- **Palette:**
  - Graphite surfaces: `#0D0F12`, `#15181D`, `#1D2128`.
  - Hairline borders: `#2A2F38`.
  - Text: `#E8ECF1` and `#8A93A3`.
  - **Accent "pitch lime"** `#B8FF3C` for the playhead, selection and primary actions.
  - Beat markers `#FF3CA6`, keyframes `#FFC53C`, audio `#3CC8FF`.
- Typography: the system UI font, plus a monospaced font for timecode.
- Icons: an original minimal SVG set in `src/renderer/ui/icons/` (or `lucide-react` if approved; see §15).

**Layout**
- Left: the media bin.
- Centre: the 9:16 viewer with safe-zone guides for TikTok's UI overlays.
- Right: the inspector, with tabs for Transform, Effects, Grade and Text.
- Bottom: the timeline, with a toggle to show the graph editor.
- Panels can be resized. Docking layouts are out of scope.

**Timecode**
- The default display is `SS:FF`, plus the frame number (short edits don't need hours).
- It can be switched to frames only.

---

## 15. Dependencies (approval requested)

| Package | Why | Size / risk |
|---------|-----|-------------|
| `electron` | App runtime (the approved stack) | Large, as expected |
| `react`, `react-dom` | UI | Small |
| `typescript` | Language | Dev only |
| `electron-vite`, `vite`, `@vitejs/plugin-react` | Builds main, preload, renderer and workers with HMR | Dev only |
| `zustand` | Store (about 1 kB) | Tiny |
| `immer` | Immutable updates plus patches for undo | Small |
| `zod` | Project schema validation and migrations; IPC payload checks | Small |
| `mp4box` | MP4 demux for WebCodecs decode of proxies | Medium (~200 kB) |
| `ffmpeg-static`, `ffprobe-static` | Bundled FFmpeg binaries. A system FFmpeg can be used instead through Preferences. | **Large (~70–80 MB per platform)**. GPL build. See the note below. |
| `vitest` | Unit tests | Dev only |
| `eslint`, `typescript-eslint`, `eslint-plugin-react-hooks`, `prettier` | Lint and format | Dev only |
| `@playwright/test` | Electron end-to-end smoke tests (`_electron.launch`) | Dev only |
| `electron-builder` | Packaging installers (Phase 10) | Dev only |
| `lucide-react` *(optional)* | Icon set (ISC licence) | Small. The alternative is hand-made SVGs. |

**On-demand download (not an npm dependency, Phase 4b):** the `rife-ncnn-vulkan` release binary (MIT) and a RIFE model. The user consents in the app before the download, and the file is checked against a SHA-256 hash.

**FFmpeg licensing note:** the `ffmpeg-static` builds include GPL components (libx264 and libx265). That is fine for personal use. If Kinetik is ever distributed, it must meet the GPL, or be switched to an LGPL FFmpeg build with hardware-only encoders. This is flagged now so it isn't a surprise later.

---

## 16. Testing strategy

| Layer | Tooling | What |
|-------|---------|------|
| Pure logic (`src/shared`) | Vitest | Rational/flicks maths, frame mapping, keyframe interpolation, time remapping, snapping, commands and undo, project schema and migrations, save/load round-trip, `.cube` parser, beat detection on synthetic audio, noise determinism |
| Media service | Vitest + a real FFmpeg | Probe parsing, the conform chain, proxy frame-index parity using barcode fixtures generated during the test (25, 30, 50 and 60 fps, 29.97, a VFR variant) |
| Render | Headless Electron (Playwright), WebGL2 via SwiftShader in CI | Parity between preview and export paths, golden images with tolerance for effects |
| E2E | Playwright `_electron` | Launch, create/save/reopen a project, import, build a small timeline, export |
| Manual | Per-phase checklist in PLAN.md | Smoothness and feel on the user's own hardware |

**Barcode fixtures:** a test helper writes raw RGBA frames with the frame index encoded as 16 large black or white blocks, plus a label for humans. It pipes them into FFmpeg at each frame rate. Decoded frames are read back to an index by thresholding each block's mean, which survives H.264 compression.
