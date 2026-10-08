import { decisionLogSchema, type DecisionLog, type ReviewStamps, type ScenarioFixture, type Snapshot } from "@countercoach/engine";
import type { CaptureResult, SavedCaptures } from "../shared/capture";
import type { DataStatusEvent, OverlayModel } from "../shared/ipc";
import { DEFAULT_SETTINGS, patchSettings, sanitizeSettings, type Settings } from "../shared/settings";

export interface Bootstrap {
  snapshot: Snapshot;
  stamps: ReviewStamps | null;
  source: string;
  latest: { clientVersion: number | null; checkedAt: string } | null;
  settings: Settings;
  fixtures: unknown[];
  /** Screen-reader icon templates if already built on this machine, else null (built on first use). */
  icons: unknown;
  platform: string;
  version: string;
}

export interface OverlayStateMsg {
  editMode: boolean;
  expanded: boolean;
  scale: number;
  clickThrough: boolean;
}

export interface Host {
  kind: "electron" | "web";
  getBootstrap(): Promise<Bootstrap>;
  setSettings(patch: unknown): Promise<Settings>;
  checkData(): Promise<DataStatusEvent>;
  refreshData(): Promise<{ event: DataStatusEvent; bootstrap: Omit<Bootstrap, "settings" | "fixtures" | "platform" | "version"> | null }>;
  overlayUpdate(m: OverlayModel): void;
  setEditMode(on: boolean): Promise<boolean>;
  overlayResize(height: number): void;
  logAppend(matchId: string, entry: unknown): Promise<boolean>;
  logList(): Promise<{ matchId: string; entries: number; heroId: number | null }[]>;
  logRead(id: string): Promise<DecisionLog | null>;
  logDeleteAll(): Promise<boolean>;
  importReplay(): Promise<{ log?: DecisionLog; error?: string } | null>;
  /** Capture the screen under the cursor after `delayMs` (desktop app only). */
  captureScreen(delayMs: number): Promise<CaptureResult>;
  /** Pick a screenshot file to read. */
  loadImage(): Promise<CaptureResult | null>;
  savedCaptures(): Promise<SavedCaptures>;
  deleteSavedCaptures(): Promise<SavedCaptures>;
  openSavedCaptures(): Promise<boolean>;
  /** Captures triggered by the global hotkey. */
  onScreenCaptured(fn: (r: CaptureResult) => void): () => void;
  /** Icon templates for the screen reader, built locally on first use (downloads item art once). */
  iconTemplates(): Promise<unknown>;
  onTemplatesProgress(fn: (p: { done: number; total: number }) => void): () => void;
  onOverlayModel(fn: (m: OverlayModel) => void): () => void;
  onOverlayState(fn: (m: OverlayStateMsg) => void): () => void;
  onDataEvent(fn: (e: DataStatusEvent) => void): () => void;
  onSettings(fn: (s: Settings) => void): () => void;
}

declare global {
  interface Window {
    countercoach?: Omit<Host, "kind">;
  }
}

// ---------------------------------------------------------------------------------------------
// Browser host (web preview / screenshots). Settings and logs live in localStorage; every access
// is wrapped because storage can be unavailable.
// ---------------------------------------------------------------------------------------------

function lsGet<T>(k: string, fallback: T): T {
  try {
    const v = localStorage.getItem(k);
    return v ? (JSON.parse(v) as T) : fallback;
  } catch {
    return fallback;
  }
}
function lsSet(k: string, v: unknown): void {
  try {
    localStorage.setItem(k, JSON.stringify(v));
  } catch {
    /* storage unavailable */
  }
}

class WebHost implements Host {
  kind = "web" as const;
  private settings: Settings = sanitizeSettings(lsGet("cc.settings", DEFAULT_SETTINGS));
  private listeners = { overlay: new Set<(m: OverlayModel) => void>(), settings: new Set<(s: Settings) => void>() };

  async getBootstrap(): Promise<Bootstrap> {
    const [snapshot, stamps, index, icons] = await Promise.all([
      fetch("./data/snapshot.json").then((r) => r.json() as Promise<Snapshot>),
      fetch("./data/review-stamps.json").then((r) => (r.ok ? (r.json() as Promise<ReviewStamps>) : null)).catch(() => null),
      fetch("./data/fixtures/index.json").then((r) => (r.ok ? (r.json() as Promise<string[]>) : [])).catch(() => [] as string[]),
      Promise.resolve(null),
    ]);
    const fixtures = await Promise.all(index.map((f) => fetch(`./data/fixtures/${f}`).then((r) => r.json() as Promise<ScenarioFixture>).catch(() => null)));
    return { snapshot, stamps, source: "bundled (web preview)", latest: null, settings: this.settings, fixtures: fixtures.filter(Boolean), icons, platform: "web", version: "web" };
  }
  async setSettings(patch: unknown): Promise<Settings> {
    this.settings = patchSettings(this.settings, patch);
    lsSet("cc.settings", this.settings);
    this.listeners.settings.forEach((f) => f(this.settings));
    return this.settings;
  }
  async checkData(): Promise<DataStatusEvent> {
    return { kind: "offline", message: "Web preview: live data checks run in the desktop app.", latestBuild: null, snapshotBuild: null, at: new Date().toISOString() };
  }
  async refreshData() {
    return { event: await this.checkData(), bootstrap: null };
  }
  overlayUpdate(m: OverlayModel): void {
    this.listeners.overlay.forEach((f) => f(m));
  }
  async setEditMode(on: boolean): Promise<boolean> {
    return on;
  }
  overlayResize(): void {
    /* web preview renders the overlay inline */
  }
  async logAppend(matchId: string, entry: unknown): Promise<boolean> {
    if (!this.settings.logging.enabled) return false;
    const all = lsGet<Record<string, DecisionLog>>("cc.logs", {});
    const log = all[matchId] ?? { version: 1, matchId, heroId: null, build: null, entries: [] };
    log.entries = [...log.entries, entry as DecisionLog["entries"][number]].slice(-500);
    all[matchId] = log;
    lsSet("cc.logs", all);
    return true;
  }
  async logList() {
    const all = lsGet<Record<string, DecisionLog>>("cc.logs", {});
    return Object.values(all).map((l) => ({ matchId: l.matchId, entries: l.entries.length, heroId: l.heroId }));
  }
  async logRead(id: string) {
    const all = lsGet<Record<string, DecisionLog>>("cc.logs", {});
    const p = decisionLogSchema.safeParse(all[id]);
    return p.success ? p.data : null;
  }
  async logDeleteAll() {
    lsSet("cc.logs", {});
    return true;
  }
  importReplay(): Promise<{ log?: DecisionLog; error?: string } | null> {
    return new Promise((resolve) => {
      const input = document.createElement("input");
      input.type = "file";
      input.accept = "application/json,.json";
      input.onchange = async () => {
        const f = input.files?.[0];
        if (!f) return resolve(null);
        try {
          const p = decisionLogSchema.safeParse(JSON.parse(await f.text()));
          resolve(p.success ? { log: p.data } : { error: "Not a valid CounterCoach decision log" });
        } catch {
          resolve({ error: "Could not read file" });
        }
      };
      input.click();
    });
  }
  async captureScreen(): Promise<CaptureResult> {
    return { error: "Screen capture runs in the desktop app. In this preview, use Load screenshot." };
  }
  loadImage(): Promise<CaptureResult | null> {
    return new Promise((resolve) => {
      const input = document.createElement("input");
      input.type = "file";
      input.accept = "image/png,image/jpeg";
      input.onchange = async () => {
        const f = input.files?.[0];
        if (!f) return resolve(null);
        try {
          const bmp = await createImageBitmap(f);
          const canvas = document.createElement("canvas");
          canvas.width = bmp.width;
          canvas.height = bmp.height;
          const ctx = canvas.getContext("2d");
          if (!ctx) return resolve({ error: "Canvas unavailable" });
          ctx.drawImage(bmp, 0, 0);
          const d = ctx.getImageData(0, 0, bmp.width, bmp.height);
          resolve({ width: d.width, height: d.height, data: new Uint8Array(d.data.buffer), order: "rgba", source: f.name, at: Date.now() });
        } catch {
          resolve({ error: "Could not read that image" });
        }
      };
      input.click();
    });
  }
  async savedCaptures() {
    return { count: 0, bytes: 0, dir: "" };
  }
  async deleteSavedCaptures() {
    return { count: 0, bytes: 0, dir: "" };
  }
  async openSavedCaptures() {
    return false;
  }
  onScreenCaptured() {
    return () => {};
  }
  async iconTemplates(): Promise<unknown> {
    // Web preview only: a development fixture copied in by `build:web`.
    const r = await fetch("./data/icons.json").catch(() => null);
    return r && r.ok ? r.json() : { error: "Icon templates are not available in this preview." };
  }
  onTemplatesProgress() {
    return () => {};
  }
  onOverlayModel(fn: (m: OverlayModel) => void) {
    this.listeners.overlay.add(fn);
    return () => void this.listeners.overlay.delete(fn);
  }
  onOverlayState() {
    return () => {};
  }
  onDataEvent() {
    return () => {};
  }
  onSettings(fn: (s: Settings) => void) {
    this.listeners.settings.add(fn);
    return () => void this.listeners.settings.delete(fn);
  }
}

let host: Host | null = null;
export function getHost(): Host {
  if (host) return host;
  host = window.countercoach ? ({ kind: "electron", ...window.countercoach } as Host) : new WebHost();
  return host;
}
