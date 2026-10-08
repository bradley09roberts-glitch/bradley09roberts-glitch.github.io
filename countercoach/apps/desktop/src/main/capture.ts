import { desktopCapturer, nativeImage, screen, type BrowserWindow, type NativeImage } from "electron";
import { mkdir, readdir, rm, stat, writeFile } from "node:fs/promises";
import path from "node:path";
import type { CapturePayload } from "../shared/capture.js";

export type { CapturePayload };

/**
 * User-triggered screen capture for the screen reader. Uses Electron's documented
 * desktopCapturer (the OS screen-capture API), i.e. the same pixels the player sees; it never
 * touches the game process. Captures stay in memory unless the user turns on saving.
 */

/** Largest image accepted (8K UHD); protects the renderer from absurd files. */
const MAX_PIXELS = 7680 * 4320;
/** Saved captures kept on disk when saving is on. */
const KEEP_SAVED = 20;

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

function toPayload(img: NativeImage, source: string): CapturePayload | { error: string } {
  const { width, height } = img.getSize();
  if (!width || !height) return { error: "The capture was empty." };
  if (width * height > MAX_PIXELS) return { error: `Image too large (${width}×${height}).` };
  // NativeImage bitmaps are BGRA on Windows and Linux (Skia's native order on little-endian).
  return { width, height, data: new Uint8Array(img.toBitmap()), order: "bgra", source, at: Date.now() };
}

/**
 * Capture the display under the mouse cursor (where the game is when the player presses the
 * hotkey). The overlay is made transparent for the capture so it does not cover the scoreboard.
 */
export async function captureDisplay(overlay: BrowserWindow | null): Promise<{ payload: CapturePayload; image: NativeImage } | { error: string }> {
  const display = screen.getDisplayNearestPoint(screen.getCursorScreenPoint());
  const scale = display.scaleFactor || 1;
  const size = { width: Math.round(display.size.width * scale), height: Math.round(display.size.height * scale) };
  const prevOpacity = overlay && !overlay.isDestroyed() ? overlay.getOpacity() : null;
  try {
    if (overlay && prevOpacity != null && overlay.isVisible()) {
      overlay.setOpacity(0);
      await sleep(80);
    }
    const sources = await desktopCapturer.getSources({ types: ["screen"], thumbnailSize: size, fetchWindowIcons: false });
    const src = sources.find((s) => s.display_id === String(display.id)) ?? (sources.length === 1 ? sources[0] : undefined);
    if (!src) return { error: "Could not find the screen to capture." };
    const p = toPayload(src.thumbnail, `screen ${display.id} (${size.width}×${size.height})`);
    if ("error" in p) return p;
    if (isBlack(p)) return { error: "The capture came back black. Use Borderless/Windowed mode; exclusive fullscreen cannot be captured." };
    return { payload: p, image: src.thumbnail };
  } catch (e) {
    return { error: `Screen capture failed: ${e instanceof Error ? e.message : String(e)}` };
  } finally {
    if (overlay && prevOpacity != null && !overlay.isDestroyed()) overlay.setOpacity(prevOpacity);
  }
}

/** Load a screenshot the user picked (PNG/JPEG), e.g. a Steam F12 screenshot. */
export function loadImageFile(file: string): CapturePayload | { error: string } {
  const img = nativeImage.createFromPath(file);
  if (img.isEmpty()) return { error: "Not a PNG or JPEG image." };
  return toPayload(img, path.basename(file));
}

/** True when nearly every sampled pixel is black (protected or exclusive-fullscreen content). */
function isBlack(p: CapturePayload): boolean {
  const step = Math.max(1, Math.floor((p.width * p.height) / 5000));
  let dark = 0;
  let n = 0;
  for (let i = 0; i < p.width * p.height; i += step) {
    const o = i * 4;
    if (p.data[o]! < 6 && p.data[o + 1]! < 6 && p.data[o + 2]! < 6) dark++;
    n++;
  }
  return n > 0 && dark / n > 0.995;
}

/** Opt-in on-disk copies of captures, for troubleshooting. */
export class CaptureStore {
  constructor(readonly dir: string) {}

  async save(img: NativeImage): Promise<void> {
    await mkdir(this.dir, { recursive: true });
    const name = `capture-${new Date().toISOString().replace(/[:.]/g, "-")}.png`;
    await writeFile(path.join(this.dir, name), img.toPNG());
    const files = (await this.files()).sort();
    for (const f of files.slice(0, Math.max(0, files.length - KEEP_SAVED))) await rm(path.join(this.dir, f), { force: true });
  }

  private async files(): Promise<string[]> {
    try {
      return (await readdir(this.dir)).filter((f) => /^capture-.*\.png$/.test(f));
    } catch {
      return [];
    }
  }

  async summary(): Promise<{ count: number; bytes: number; dir: string }> {
    let bytes = 0;
    const files = await this.files();
    for (const f of files) {
      try {
        bytes += (await stat(path.join(this.dir, f))).size;
      } catch {
        /* removed meanwhile */
      }
    }
    return { count: files.length, bytes, dir: this.dir };
  }

  async deleteAll(): Promise<void> {
    for (const f of await this.files()) await rm(path.join(this.dir, f), { force: true });
  }
}
