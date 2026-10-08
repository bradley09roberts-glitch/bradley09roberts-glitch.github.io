import path from "node:path";

/**
 * Opt-in "read when I hold Tab". Uses a passive global keyboard listener (uiohook-napi: a
 * low-level keyboard hook, the same mechanism push-to-talk apps use). It only observes: the Tab
 * key still reaches the game, nothing is blocked or sent, and no key other than Tab is acted on
 * or stored. When Tab has been held for the configured delay (long enough for the scoreboard to
 * appear), `onTabHeld` runs once per press.
 */

interface HookEvent {
  keycode: number;
  altKey: boolean;
  ctrlKey: boolean;
  metaKey: boolean;
}
interface UiohookModule {
  uIOhook: { on(ev: "keydown" | "keyup", fn: (e: HookEvent) => void): void; removeAllListeners(): void; start(): void; stop(): void };
  UiohookKey: { Tab: number };
}

/** Minimum time between two automatic captures. */
const MIN_INTERVAL_MS = 1500;

export interface TabWatcherStatus {
  running: boolean;
  error: string | null;
  /** Tab presses seen since start (for diagnostics only; no other keys are counted). */
  tabPresses: number;
}

export class TabWatcher {
  private mod: UiohookModule | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private held = false;
  private last = 0;
  private status: TabWatcherStatus = { running: false, error: null, tabPresses: 0 };

  constructor(
    private readonly onTabHeld: () => void,
    private readonly delayMs: () => number,
    /** Skip when CounterCoach itself has focus (Tab there is just moving between fields). */
    private readonly ownWindowFocused: () => boolean,
  ) {}

  getStatus(): TabWatcherStatus {
    return { ...this.status };
  }

  private load(): UiohookModule {
    if (this.mod) return this.mod;
    // Shipped next to main.cjs (scripts/build-main.mjs); falls back to the workspace copy in dev.
    try {
      this.mod = require(path.join(__dirname, "vendor", "uiohook-napi")) as UiohookModule;
    } catch {
      this.mod = require("uiohook-napi") as UiohookModule;
    }
    return this.mod;
  }

  start(): TabWatcherStatus {
    if (this.status.running) return this.getStatus();
    try {
      const { uIOhook, UiohookKey } = this.load();
      uIOhook.removeAllListeners();
      uIOhook.on("keydown", (e) => {
        if (e.keycode !== UiohookKey.Tab) return;
        // Holding a key repeats keydown; only the first one counts. Alt+Tab is window switching.
        if (this.held || e.altKey || e.ctrlKey || e.metaKey) return;
        this.held = true;
        this.status.tabPresses++;
        if (this.ownWindowFocused() || Date.now() - this.last < MIN_INTERVAL_MS) return;
        this.timer = setTimeout(() => {
          this.timer = null;
          if (!this.held) return;
          this.last = Date.now();
          this.onTabHeld();
        }, this.delayMs());
      });
      uIOhook.on("keyup", (e) => {
        if (e.keycode !== UiohookKey.Tab) return;
        this.held = false;
        // Released before the scoreboard had time to appear: no capture.
        if (this.timer) {
          clearTimeout(this.timer);
          this.timer = null;
        }
      });
      uIOhook.start();
      this.status = { running: true, error: null, tabPresses: 0 };
    } catch (e) {
      this.status = { running: false, error: e instanceof Error ? e.message : String(e), tabPresses: 0 };
    }
    return this.getStatus();
  }

  stop(): TabWatcherStatus {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    this.held = false;
    if (this.mod && this.status.running) {
      try {
        this.mod.uIOhook.removeAllListeners();
        this.mod.uIOhook.stop();
      } catch {
        /* already stopped */
      }
    }
    this.status = { ...this.status, running: false };
    return this.getStatus();
  }
}
