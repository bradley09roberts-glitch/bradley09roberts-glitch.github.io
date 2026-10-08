import { mkdtempSync, readFileSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { evaluate } from "@countercoach/engine";
import { computeOverlayBounds, offsetFromBounds, type DisplayInfo } from "../src/main/overlayPlacement";
import { LogStore, SettingsStore } from "../src/main/stores";
import { overlayModelSchema } from "../src/shared/ipc";
import { DEFAULT_SETTINGS, acceleratorSchema, patchSettings, sanitizeSettings } from "../src/shared/settings";
import { toOverlayModel } from "../src/renderer/useCoach";
import { deps, playFixture, prefs } from "../../../packages/engine/test/helpers";

const primary: DisplayInfo = { id: 1, scaleFactor: 1.5, workArea: { x: 0, y: 0, width: 2560, height: 1400 } };
const left: DisplayInfo = { id: 2, scaleFactor: 1, workArea: { x: -1920, y: 0, width: 1920, height: 1040 } };
const ultrawide: DisplayInfo = { id: 3, scaleFactor: 1, workArea: { x: 2560, y: 0, width: 3440, height: 1400 } };
const ov = (o: Partial<typeof DEFAULT_SETTINGS.overlay>) => ({ ...DEFAULT_SETTINGS.overlay, ...o });

describe("overlay placement", () => {
  it("anchors inside the work area of the chosen display, scaled", () => {
    const b = computeOverlayBounds(ov({ anchor: "top-right", scale: 1.5 }), [primary, left], 1);
    expect(b.displayId).toBe(1);
    expect(b.width).toBe(510);
    expect(b.x + b.width).toBeLessThanOrEqual(2560);
    expect(b.y).toBeGreaterThanOrEqual(0);
  });
  it("supports monitors with negative origins and falls back to primary when a display is gone", () => {
    const b = computeOverlayBounds(ov({ anchor: "bottom-left", displayId: 2 }), [primary, left], 1);
    expect(b.x).toBe(-1920 + 16);
    expect(b.y + b.height).toBe(1040 - 16);
    const gone = computeOverlayBounds(ov({ anchor: "bottom-left", displayId: 99 }), [primary], 1);
    expect(gone.displayId).toBe(1);
  });
  it("keeps custom positions resolution-independent (ultrawide)", () => {
    const custom = ov({ anchor: "custom", displayId: 3, offset: { x: 0.5, y: 0.1 } });
    const b = computeOverlayBounds(custom, [primary, ultrawide], 1);
    expect(b.x).toBe(2560 + 1720);
    const back = offsetFromBounds(b, [primary, ultrawide], 1);
    expect(back.displayId).toBe(3);
    expect(back.offset.x).toBeCloseTo(0.5, 2);
  });
  it("clamps an off-screen custom offset and sizes to content when known", () => {
    const b = computeOverlayBounds(ov({ anchor: "custom", offset: { x: 1, y: 1 } }), [primary], 1, 220);
    expect(b.x + b.width).toBeLessThanOrEqual(2560);
    expect(b.y + b.height).toBeLessThanOrEqual(1400);
    expect(b.height).toBe(220);
  });
});

describe("settings", () => {
  it("falls back field by field for invalid values", () => {
    const s = sanitizeSettings({ overlay: { scale: 99, opacity: 0.5, anchor: "nowhere" }, hotkeys: { toggleOverlay: "rm -rf /" }, logging: { enabled: true } });
    expect(s.overlay.scale).toBe(DEFAULT_SETTINGS.overlay.scale);
    expect(s.overlay.opacity).toBe(0.5);
    expect(s.overlay.anchor).toBe(DEFAULT_SETTINGS.overlay.anchor);
    expect(s.hotkeys.toggleOverlay).toBe(DEFAULT_SETTINGS.hotkeys.toggleOverlay);
    expect(s.logging.enabled).toBe(true);
  });
  it("patches nested sections without dropping siblings and ignores unknown keys", () => {
    const s = patchSettings(DEFAULT_SETTINGS, { overlay: { expanded: true }, evil: { x: 1 }, version: 9 });
    expect(s.overlay.expanded).toBe(true);
    expect(s.overlay.scale).toBe(DEFAULT_SETTINGS.overlay.scale);
    expect((s as unknown as Record<string, unknown>).evil).toBeUndefined();
    expect(s.version).toBe(1);
  });
  it("keeps older settings files working when new sections and hotkeys are added", () => {
    const old = JSON.parse(JSON.stringify(DEFAULT_SETTINGS)) as Record<string, unknown>;
    delete old.screen;
    (old.hotkeys as Record<string, unknown>).toggleOverlay = "Ctrl+Alt+P";
    delete (old.hotkeys as Record<string, unknown>).readScreen;
    const s = sanitizeSettings(old);
    expect(s.hotkeys.toggleOverlay).toBe("Ctrl+Alt+P");
    expect(s.hotkeys.readScreen).toBe(DEFAULT_SETTINGS.hotkeys.readScreen);
    expect(s.screen).toEqual(DEFAULT_SETTINGS.screen);
    // Screen captures are never kept on disk unless the user opts in.
    expect(DEFAULT_SETTINGS.screen.saveCaptures).toBe(false);
  });
  it("accepts bare F-keys and modifier chords as hotkeys, nothing else", () => {
    expect(acceleratorSchema.safeParse("F8").success).toBe(true);
    expect(acceleratorSchema.safeParse("Ctrl+Alt+R").success).toBe(true);
    for (const bad of ["R", "F25", "Tab", "Ctrl+Alt+", "rm -rf /"]) expect(acceleratorSchema.safeParse(bad).success, bad).toBe(false);
    expect(patchSettings(DEFAULT_SETTINGS, { screen: { captureDelayMs: 99999 } }).screen.captureDelayMs).toBe(DEFAULT_SETTINGS.screen.captureDelayMs);
  });
  it("accepts a valid screen calibration and rejects a malformed one", () => {
    const layout = { version: 1, aspect: 16 / 9, itemArea: { x: 0.2, y: 0.1, w: 0.4, h: 0.8 }, iconSize: 0.035, portrait: null, calibratedAt: "2026-10-08T00:00:00Z" };
    // Older calibrations without an orientation are read as rows.
    expect(patchSettings(DEFAULT_SETTINGS, { screen: { layout } }).screen.layout).toEqual({ ...layout, orientation: "rows" });
    const bad = patchSettings(DEFAULT_SETTINGS, { screen: { layout: { ...layout, itemArea: { x: 5, y: 0, w: 1, h: 1 } } } });
    expect(bad.screen.layout).toBeNull();
    expect(bad.screen.autoApply).toBe(DEFAULT_SETTINGS.screen.autoApply);
  });
  it("persists atomically and reloads", async () => {
    const dir = mkdtempSync(path.join(tmpdir(), "cc-set-"));
    const store = new SettingsStore(path.join(dir, "settings.json"));
    await store.load();
    await store.save(patchSettings(store.get(), { overlay: { opacity: 0.6 } }));
    const again = new SettingsStore(path.join(dir, "settings.json"));
    expect((await again.load()).overlay.opacity).toBe(0.6);
  });
});

describe("decision log store", () => {
  it("appends validated entries and deletes everything on request", async () => {
    const dir = mkdtempSync(path.join(tmpdir(), "cc-log-"));
    const logs = new LogStore(path.join(dir, "logs"));
    const entry = { at: 1, gameTime: 60, heroId: 6, souls: 500, owned: [], enemies: [], topThreats: [], advice: { buyNow: null, saveFor: null, alternative: null, ability: null, confidence: "low" }, informationState: "manual" };
    await logs.append("m-1", 6, 6763, entry);
    await logs.append("m-1", 6, 6763, entry);
    expect((await logs.read("m-1"))!.entries).toHaveLength(2);
    await expect(logs.append("m-1", 6, 6763, { bogus: true })).rejects.toThrow();
    await logs.deleteAll();
    expect(existsSync(path.join(dir, "logs"))).toBe(false);
    expect(await logs.list()).toEqual([]);
  });
});

describe("overlay model", () => {
  it("is schema-valid for every fixture", () => {
    const d = deps();
    const files = JSON.parse(readFileSync(path.resolve(__dirname, "../public/data/fixtures/index.json"), "utf8")) as string[];
    for (const f of files) {
      const { state, now } = playFixture(f.replace(/\.json$/, ""));
      const m = toOverlayModel(evaluate(d, state, prefs(), now), d);
      expect(overlayModelSchema.safeParse(m).success, f).toBe(true);
    }
  });
});
