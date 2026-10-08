import { app, BrowserWindow, dialog, globalShortcut, ipcMain, screen, session, shell, type IpcMainInvokeEvent } from "electron";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { decisionLogSchema } from "@countercoach/engine";
import { IPC, logEntrySchema, logIdSchema, overlayModelSchema, type DataStatusEvent, type OverlayModel } from "../shared/ipc.js";
import { patchSettings, type Settings } from "../shared/settings.js";
import { CaptureStore, captureDisplay, loadImageFile, type CapturePayload } from "./capture.js";
import { DataStore } from "./dataStore.js";
import { IconTemplateStore } from "./iconTemplates.js";
import { TabWatcher } from "./tabWatcher.js";
import { computeOverlayBounds, offsetFromBounds, type DisplayInfo } from "./overlayPlacement.js";
import { LogStore, SettingsStore } from "./stores.js";

/**
 * CounterCoach main process. External companion only: it never reads game memory, injects into
 * the game, intercepts packets, edits game files or sends input to the game.
 */

const isSmokeTest = process.env.COUNTERCOACH_SMOKE === "1";
if (process.env.COUNTERCOACH_USER_DATA) app.setPath("userData", process.env.COUNTERCOACH_USER_DATA);

const rendererDir = path.join(app.getAppPath(), "dist", "renderer");
const dataDir = path.join(rendererDir, "data");

let mainWin: BrowserWindow | null = null;
let overlayWin: BrowserWindow | null = null;
let editMode = false;
let lastOverlayModel: OverlayModel | null = null;
let overlayContentHeight: number | null = null;
const settingsStore = new SettingsStore(path.join(app.getPath("userData"), "settings.json"));
const logStore = new LogStore(path.join(app.getPath("userData"), "logs"));
const captureStore = new CaptureStore(path.join(app.getPath("userData"), "captures"));
const templateStore = new IconTemplateStore(path.join(app.getPath("userData"), "vision"));
const tabWatcher = new TabWatcher(
  () => void captureForReader("tab").then((r) => mainWin?.webContents.send(IPC.screenCaptured, r)),
  () => settingsStore.get().screen.tabDelayMs,
  () => !!mainWin && BrowserWindow.getFocusedWindow() === mainWin,
);

/** Start or stop the opt-in Tab listener to match the settings, and tell the window. */
function syncTabWatcher(s: Settings): void {
  if (s.screen.captureOnTab || isSmokeTest) tabWatcher.start();
  else tabWatcher.stop();
  mainWin?.webContents.send(IPC.screenTabStatus, tabWatcher.getStatus());
}
const dataStore = new DataStore(dataDir, path.join(app.getPath("userData"), "data"), (e: DataStatusEvent) => {
  mainWin?.webContents.send(IPC.dataEvent, e);
});

function displays(): { list: DisplayInfo[]; primary: number } {
  return {
    list: screen.getAllDisplays().map((d) => ({ id: d.id, scaleFactor: d.scaleFactor, workArea: d.workArea })),
    primary: screen.getPrimaryDisplay().id,
  };
}

function secureWebPreferences(): Electron.WebPreferences {
  return {
    preload: path.join(app.getAppPath(), "dist-electron", "preload.cjs"),
    contextIsolation: true,
    sandbox: true,
    nodeIntegration: false,
    webSecurity: true,
    spellcheck: false,
  };
}

function lockDown(win: BrowserWindow): void {
  win.webContents.setWindowOpenHandler(({ url }) => {
    // Only patch-note / documentation links open, in the user's browser.
    if (/^https:\/\/(store\.steampowered\.com|forums\.playdeadlock\.com|api\.deadlock-api\.com)\//.test(url)) void shell.openExternal(url);
    return { action: "deny" };
  });
  win.webContents.on("will-navigate", (e) => e.preventDefault());
}

function createMainWindow(): void {
  mainWin = new BrowserWindow({
    width: 1360,
    height: 880,
    minWidth: 980,
    minHeight: 640,
    backgroundColor: "#0d0f12",
    title: "CounterCoach",
    show: false,
    webPreferences: secureWebPreferences(),
  });
  lockDown(mainWin);
  void mainWin.loadFile(path.join(rendererDir, "index.html"));
  mainWin.once("ready-to-show", () => mainWin?.show());
  mainWin.on("closed", () => {
    mainWin = null;
    app.quit();
  });
}

function applyOverlaySettings(s: Settings): void {
  if (!overlayWin) return;
  const { list, primary } = displays();
  const b = computeOverlayBounds(s.overlay, list, primary, overlayContentHeight);
  overlayWin.setBounds({ x: b.x, y: b.y, width: b.width, height: b.height });
  overlayWin.setOpacity(s.overlay.opacity);
  const clickThrough = s.overlay.clickThrough && !editMode;
  overlayWin.setIgnoreMouseEvents(clickThrough, { forward: true });
  overlayWin.setFocusable(editMode);
  if (s.overlay.enabled) {
    if (!overlayWin.isVisible()) overlayWin.showInactive();
  } else overlayWin.hide();
  overlayWin.webContents.send(IPC.overlayState, { editMode, expanded: s.overlay.expanded, scale: s.overlay.scale, clickThrough });
}

function createOverlayWindow(): void {
  overlayWin = new BrowserWindow({
    width: 340,
    height: 168,
    frame: false,
    transparent: true,
    resizable: false,
    movable: true,
    focusable: false,
    skipTaskbar: true,
    alwaysOnTop: true,
    hasShadow: false,
    show: false,
    title: "CounterCoach overlay",
    webPreferences: secureWebPreferences(),
  });
  // Above normal windows and borderless-fullscreen games. Exclusive fullscreen is not supported.
  overlayWin.setAlwaysOnTop(true, "screen-saver");
  overlayWin.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true });
  lockDown(overlayWin);
  void overlayWin.loadFile(path.join(rendererDir, "index.html"), { query: { view: "overlay" } });
  overlayWin.webContents.once("did-finish-load", () => {
    applyOverlaySettings(settingsStore.get());
    if (lastOverlayModel) overlayWin?.webContents.send(IPC.overlayModel, lastOverlayModel);
  });
  overlayWin.on("moved", () => {
    if (!overlayWin || !editMode) return;
    const { list, primary } = displays();
    const o = offsetFromBounds(overlayWin.getBounds(), list, primary);
    const s = settingsStore.get();
    void settingsStore.save({ ...s, overlay: { ...s.overlay, anchor: "custom", displayId: o.displayId, offset: o.offset } }).then((n) => broadcastSettings(n));
  });
  overlayWin.on("closed", () => (overlayWin = null));
}

function broadcastSettings(s: Settings): void {
  mainWin?.webContents.send(IPC.settingsEvent, s);
  applyOverlaySettings(s);
}

function registerHotkeys(s: Settings): string[] {
  globalShortcut.unregisterAll();
  const failed: string[] = [];
  const reg = (acc: string, fn: () => void) => {
    try {
      if (!globalShortcut.register(acc, fn)) failed.push(acc);
    } catch {
      failed.push(acc);
    }
  };
  reg(s.hotkeys.toggleOverlay, () => void updateSettings({ overlay: { enabled: !settingsStore.get().overlay.enabled } }));
  reg(s.hotkeys.toggleExpanded, () => void updateSettings({ overlay: { expanded: !settingsStore.get().overlay.expanded } }));
  reg(s.hotkeys.toggleEditMode, () => setEditMode(!editMode));
  reg(s.hotkeys.readScreen, () => {
    // A short delay lets the player press the hotkey first and then hold Tab for the scoreboard.
    setTimeout(() => void captureForReader("hotkey").then((r) => mainWin?.webContents.send(IPC.screenCaptured, r)), settingsStore.get().screen.captureDelayMs);
  });
  return failed;
}

/** Capture the screen for the reader; optionally keep a copy on disk (opt-in). */
async function captureForReader(trigger: NonNullable<CapturePayload["trigger"]>): Promise<CapturePayload | { error: string }> {
  const r = await captureDisplay(overlayWin);
  if ("error" in r) return r;
  r.payload.trigger = trigger;
  if (settingsStore.get().screen.saveCaptures) {
    try {
      await captureStore.save(r.image);
    } catch {
      /* saving is best-effort; the read still proceeds */
    }
  }
  return r.payload;
}

function setEditMode(on: boolean): void {
  editMode = on;
  applyOverlaySettings(settingsStore.get());
}

async function updateSettings(patch: unknown): Promise<Settings> {
  const before = settingsStore.get();
  const next = await settingsStore.save(patchSettings(before, patch));
  if (JSON.stringify(before.hotkeys) !== JSON.stringify(next.hotkeys)) registerHotkeys(next);
  if (before.screen.captureOnTab !== next.screen.captureOnTab) syncTabWatcher(next);
  broadcastSettings(next);
  return next;
}

/** Only accept IPC from our own windows. */
function trusted(e: IpcMainInvokeEvent | Electron.IpcMainEvent): boolean {
  const id = e.sender.id;
  return id === mainWin?.webContents.id || id === overlayWin?.webContents.id;
}

function registerIpc(): void {
  ipcMain.handle(IPC.getBootstrap, async (e) => {
    if (!trusted(e)) throw new Error("untrusted sender");
    const fixtures: unknown[] = [];
    try {
      const idx = JSON.parse(await readFile(path.join(dataDir, "fixtures", "index.json"), "utf8")) as string[];
      for (const f of idx.slice(0, 50)) fixtures.push(JSON.parse(await readFile(path.join(dataDir, "fixtures", path.basename(f)), "utf8")));
    } catch {
      /* fixtures optional */
    }
    // Screen-reader templates only if already built locally; otherwise built on first use.
    const icons = await templateStore.cached(dataStore.bootstrap().snapshot);
    return { ...dataStore.bootstrap(), settings: settingsStore.get(), fixtures, icons, platform: process.platform, version: app.getVersion() };
  });
  ipcMain.handle(IPC.getSettings, (e) => {
    if (!trusted(e)) throw new Error("untrusted sender");
    return settingsStore.get();
  });
  ipcMain.handle(IPC.setSettings, async (e, patch: unknown) => {
    if (!trusted(e)) throw new Error("untrusted sender");
    return updateSettings(patch);
  });
  ipcMain.handle(IPC.checkData, async (e) => {
    if (!trusted(e)) throw new Error("untrusted sender");
    return dataStore.check();
  });
  ipcMain.handle(IPC.refreshData, async (e) => {
    if (!trusted(e)) throw new Error("untrusted sender");
    const ev = await dataStore.refresh();
    return { event: ev, bootstrap: ev.kind === "updated" ? dataStore.bootstrap() : null };
  });
  ipcMain.on(IPC.overlayUpdate, (e, model: unknown) => {
    if (!trusted(e)) return;
    const m = overlayModelSchema.safeParse(model);
    if (!m.success) return;
    lastOverlayModel = m.data;
    overlayWin?.webContents.send(IPC.overlayModel, m.data);
  });
  ipcMain.on(IPC.overlayResize, (e, h: unknown) => {
    if (!overlayWin || e.sender.id !== overlayWin.webContents.id) return;
    if (typeof h !== "number" || !Number.isFinite(h) || h < 40 || h > 2000) return;
    const rounded = Math.ceil(h);
    if (rounded === overlayContentHeight) return;
    overlayContentHeight = rounded;
    applyOverlaySettings(settingsStore.get());
  });
  ipcMain.handle(IPC.overlaySetEdit, (e, on: unknown) => {
    if (!trusted(e) || typeof on !== "boolean") return false;
    setEditMode(on);
    return editMode;
  });
  ipcMain.handle(IPC.logAppend, async (e, payload: unknown) => {
    if (!trusted(e)) throw new Error("untrusted sender");
    if (!settingsStore.get().logging.enabled) return false;
    const p = logEntrySchema.safeParse(payload);
    if (!p.success) return false;
    const entry = p.data.entry as { heroId?: number | null };
    await logStore.append(p.data.matchId, entry.heroId ?? null, dataStore.bootstrap().snapshot.meta.clientVersion, p.data.entry);
    return true;
  });
  ipcMain.handle(IPC.logList, (e) => {
    if (!trusted(e)) throw new Error("untrusted sender");
    return logStore.list();
  });
  ipcMain.handle(IPC.logRead, (e, id: unknown) => {
    if (!trusted(e)) throw new Error("untrusted sender");
    const p = logIdSchema.safeParse(id);
    return p.success ? logStore.read(p.data) : null;
  });
  ipcMain.handle(IPC.logDeleteAll, async (e) => {
    if (!trusted(e)) throw new Error("untrusted sender");
    await logStore.deleteAll();
    return true;
  });
  ipcMain.handle(IPC.screenCapture, async (e, delayMs: unknown) => {
    if (!trusted(e)) throw new Error("untrusted sender");
    const d = typeof delayMs === "number" && Number.isFinite(delayMs) ? Math.max(0, Math.min(10_000, delayMs)) : 0;
    if (d > 0) await new Promise((r) => setTimeout(r, d));
    return captureForReader("button");
  });
  ipcMain.handle(IPC.screenTemplates, async (e) => {
    if (!trusted(e)) throw new Error("untrusted sender");
    const sender = e.sender;
    return templateStore.get(dataStore.bootstrap().snapshot, (p) => {
      if (!sender.isDestroyed()) sender.send(IPC.screenTemplatesProgress, p);
    });
  });
  ipcMain.handle(IPC.screenLoadImage, async (e) => {
    if (!trusted(e) || !mainWin) throw new Error("untrusted sender");
    const r = await dialog.showOpenDialog(mainWin, { title: "Open a screenshot", filters: [{ name: "Images", extensions: ["png", "jpg", "jpeg"] }], properties: ["openFile"] });
    if (r.canceled || !r.filePaths[0]) return null;
    const img = loadImageFile(r.filePaths[0]);
    return "error" in img ? img : { ...img, trigger: "file" };
  });
  ipcMain.handle(IPC.screenTabStatus, (e) => {
    if (!trusted(e)) throw new Error("untrusted sender");
    return tabWatcher.getStatus();
  });
  ipcMain.handle(IPC.screenSaved, (e) => {
    if (!trusted(e)) throw new Error("untrusted sender");
    return captureStore.summary();
  });
  ipcMain.handle(IPC.screenDeleteSaved, async (e) => {
    if (!trusted(e)) throw new Error("untrusted sender");
    await captureStore.deleteAll();
    return captureStore.summary();
  });
  ipcMain.handle(IPC.screenOpenSaved, async (e) => {
    if (!trusted(e)) throw new Error("untrusted sender");
    await mkdir(captureStore.dir, { recursive: true });
    return (await shell.openPath(captureStore.dir)) === "";
  });
  ipcMain.handle(IPC.importReplay, async (e) => {
    if (!trusted(e) || !mainWin) throw new Error("untrusted sender");
    const r = await dialog.showOpenDialog(mainWin, { title: "Import decision log", filters: [{ name: "CounterCoach log", extensions: ["json"] }], properties: ["openFile"] });
    if (r.canceled || !r.filePaths[0]) return null;
    const text = await readFile(r.filePaths[0], "utf8");
    if (text.length > 5_000_000) return { error: "File too large" };
    const p = decisionLogSchema.safeParse(JSON.parse(text));
    return p.success ? { log: p.data } : { error: "Not a valid CounterCoach decision log" };
  });
}

async function main(): Promise<void> {
  if (!app.requestSingleInstanceLock()) {
    app.quit();
    return;
  }
  app.on("second-instance", () => {
    if (mainWin) {
      if (mainWin.isMinimized()) mainWin.restore();
      mainWin.focus();
    }
  });
  await app.whenReady();
  // Deny every permission request (camera, notifications, etc.); none are needed.
  session.defaultSession.setPermissionRequestHandler((_wc, _perm, cb) => cb(false));
  const settings = await settingsStore.load();
  await dataStore.load();
  registerIpc();
  createMainWindow();
  createOverlayWindow();
  registerHotkeys(settings);
  syncTabWatcher(settings);
  screen.on("display-removed", () => applyOverlaySettings(settingsStore.get()));
  screen.on("display-added", () => applyOverlaySettings(settingsStore.get()));
  screen.on("display-metrics-changed", () => applyOverlaySettings(settingsStore.get()));
  if (settings.data.autoCheck && !isSmokeTest) setTimeout(() => void dataStore.check(), 3000);
  if (isSmokeTest) {
    // Used by automated launch checks: report window state then quit.
    setTimeout(async () => {
      const ov = overlayWin;
      const t0 = Date.now();
      const cap = await captureDisplay(overlayWin);
      const capture = "error" in cap ? { ok: false, error: cap.error } : { ok: true, width: cap.payload.width, height: cap.payload.height, ms: Date.now() - t0 };
      const report = JSON.stringify({
        smoke: true,
        platform: process.platform,
        electron: process.versions.electron,
        mainVisible: !!mainWin?.isVisible(),
        overlayExists: !!ov,
        overlayFocusable: ov?.isFocusable(),
        overlayAlwaysOnTop: ov?.isAlwaysOnTop(),
        overlayBounds: ov?.getBounds(),
        snapshotBuild: dataStore.bootstrap().snapshot.meta.clientVersion,
        settingsFile: path.join(app.getPath("userData"), "settings.json"),
        capture,
        overlayOpacityAfterCapture: ov?.getOpacity(),
        keyHook: tabWatcher.getStatus(),
      });
      console.log(report);
      // GUI-subsystem executables on Windows have no attached console; also write a file.
      void writeFile(path.join(app.getPath("userData"), "smoke-report.json"), report).finally(() => app.quit());
    }, Number(process.env.COUNTERCOACH_SMOKE_MS ?? 4000));
  }
}

app.on("will-quit", () => {
  globalShortcut.unregisterAll();
  tabWatcher.stop();
});
app.on("window-all-closed", () => app.quit());
void main();
