import { decisionLogSchema, type DecisionLog, type ReviewStamps, type ScenarioFixture, type Snapshot } from "@countercoach/engine";
import type { DataStatusEvent, OverlayModel } from "../shared/ipc";
import { DEFAULT_SETTINGS, patchSettings, sanitizeSettings, type Settings } from "../shared/settings";

export interface Bootstrap {
  snapshot: Snapshot;
  stamps: ReviewStamps | null;
  source: string;
  latest: { clientVersion: number | null; checkedAt: string } | null;
  settings: Settings;
  fixtures: unknown[];
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
    const [snapshot, stamps, index] = await Promise.all([
      fetch("./data/snapshot.json").then((r) => r.json() as Promise<Snapshot>),
      fetch("./data/review-stamps.json").then((r) => (r.ok ? (r.json() as Promise<ReviewStamps>) : null)).catch(() => null),
      fetch("./data/fixtures/index.json").then((r) => (r.ok ? (r.json() as Promise<string[]>) : [])).catch(() => [] as string[]),
    ]);
    const fixtures = await Promise.all(index.map((f) => fetch(`./data/fixtures/${f}`).then((r) => r.json() as Promise<ScenarioFixture>).catch(() => null)));
    return { snapshot, stamps, source: "bundled (web preview)", latest: null, settings: this.settings, fixtures: fixtures.filter(Boolean), platform: "web", version: "web" };
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
