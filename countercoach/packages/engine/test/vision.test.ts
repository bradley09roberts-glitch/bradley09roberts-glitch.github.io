import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import {
  ICON_THUMB,
  applyEvents,
  canAutoApply,
  createMatchState,
  decodeBase64,
  encodeBase64,
  iconTemplateFileSchema,
  layoutFromCalibration,
  loadTemplates,
  proposeTargets,
  rankTemplates,
  readField,
  readScreen,
  screenReadEvents,
  thumbnail,
  toPixels,
  type EnemyItemsValue,
  type RgbaImage,
  type ScreenLayout,
  type TemplateSet,
} from "../src/index.js";
import { T0, loadSnapshot } from "./helpers.js";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");
const iconsRaw = JSON.parse(readFileSync(path.join(root, "fixtures/vision/icons.json"), "utf8"));
const templates: TemplateSet = loadTemplates(iconsRaw);
const snap = loadSnapshot();

/** Decode a stored template back to an RGBA float thumbnail. */
function templatePixels(kind: "items" | "heroes", pred: (t: { className: string; id?: number; variant?: string }) => boolean): Float32Array {
  const t = (iconsRaw[kind] as { className: string; id?: number; variant?: string; rgba: string }[]).find(pred)!;
  return Float32Array.from(decodeBase64(t.rgba), (b) => b / 255);
}

/** Bilinear upscale of a 16×16 RGBA float thumbnail onto an opaque image at (x, y), size n. */
function draw(img: RgbaImage, src: Float32Array, x: number, y: number, n: number): void {
  const N = ICON_THUMB;
  for (let j = 0; j < n; j++) {
    for (let i = 0; i < n; i++) {
      const fx = Math.min(N - 1.001, Math.max(0, ((i + 0.5) / n) * N - 0.5));
      const fy = Math.min(N - 1.001, Math.max(0, ((j + 0.5) / n) * N - 0.5));
      const x0 = Math.floor(fx);
      const y0 = Math.floor(fy);
      const ax = fx - x0;
      const ay = fy - y0;
      const p = ((y + j) * img.width + (x + i)) * 4;
      const at = (xx: number, yy: number, c: number) => src[(yy * N + xx) * 4 + c]!;
      const a = (1 - ax) * (1 - ay) * at(x0, y0, 3) + ax * (1 - ay) * at(x0 + 1, y0, 3) + (1 - ax) * ay * at(x0, y0 + 1, 3) + ax * ay * at(x0 + 1, y0 + 1, 3);
      for (let c = 0; c < 3; c++) {
        const v = (1 - ax) * (1 - ay) * at(x0, y0, c) + ax * (1 - ay) * at(x0 + 1, y0, c) + (1 - ax) * ay * at(x0, y0 + 1, c) + ax * ay * at(x0 + 1, y0 + 1, c);
        img.data[p + c] = a * v * 255 + (1 - a) * img.data[p + c]!;
      }
    }
  }
}

function blank(w: number, h: number, seed = 1): RgbaImage {
  let s = seed;
  const rnd = () => ((s = (s * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
  const data = new Uint8ClampedArray(w * h * 4);
  for (let i = 0; i < w * h; i++) {
    data[i * 4] = 22 + rnd() * 6;
    data[i * 4 + 1] = 24 + rnd() * 6;
    data[i * 4 + 2] = 30 + rnd() * 6;
    data[i * 4 + 3] = 255;
  }
  return { width: w, height: h, data, order: "rgba" };
}

const heroId = (name: string) => snap.heroes.find((h) => h.name === name)!.id;

/** A fake scoreboard: one portrait plus a row of items per player. */
function scoreboard(rows: { hero: number; items: string[] }[], s = 32) {
  const img = blank(960, 540);
  const x0 = 200;
  const y0 = 60;
  const pitch = s + 14;
  rows.forEach((r, k) => {
    const y = y0 + k * pitch;
    draw(img, templatePixels("heroes", (t) => t.id === r.hero && t.variant === "small"), x0 - s - 12, y, s);
    r.items.forEach((cls, i) => draw(img, templatePixels("items", (t) => t.className === cls), x0 + i * (s + 4), y, s));
  });
  const layout: ScreenLayout = {
    version: 1,
    aspect: 960 / 540,
    itemArea: { x: (x0 - 8) / 960, y: (y0 - 8) / 540, w: (12 * (s + 4)) / 960, h: (rows.length * pitch + 8) / 540 },
    iconSize: s / 540,
    portrait: { x: (x0 - s - 12) / 960, w: s / 960, size: s / 540, offsetY: 0 },
    calibratedAt: "test",
  };
  return { img, layout };
}

describe("vision: primitives", () => {
  it("base64 round-trips arbitrary bytes", () => {
    const b = Uint8Array.from({ length: 1000 }, (_, i) => (i * 37) & 255);
    for (const n of [0, 1, 2, 3, 998, 1000]) expect([...decodeBase64(encodeBase64(b.subarray(0, n)))]).toEqual([...b.subarray(0, n)]);
  });

  it("thumbnail averages by area and marks off-image cells transparent", () => {
    const img: RgbaImage = { width: 2, height: 2, data: Uint8ClampedArray.from([0, 0, 0, 255, 255, 255, 255, 255, 0, 0, 0, 255, 255, 255, 255, 255]), order: "rgba" };
    const t = thumbnail(img, { x: 0, y: 0, w: 2, h: 2 }, 1);
    expect(t[0]).toBeCloseTo(0.5, 5);
    expect(t[3]).toBeCloseTo(1, 5);
    const off = thumbnail(img, { x: 1, y: 0, w: 2, h: 2 }, 1);
    expect(off[3]).toBeCloseTo(0.5, 5);
    // BGRA input reads the same colours.
    const bgra: RgbaImage = { width: 1, height: 1, data: Uint8ClampedArray.from([10, 20, 30, 255]), order: "bgra" };
    const p = thumbnail(bgra, { x: 0, y: 0, w: 1, h: 1 }, 1);
    expect(Math.round(p[0]! * 255)).toBe(30);
    expect(Math.round(p[2]! * 255)).toBe(10);
  });
});

describe("vision: templates", () => {
  it("template file is valid and covers every snapshot item and playable hero", () => {
    expect(iconTemplateFileSchema.safeParse(iconsRaw).success).toBe(true);
    const items = new Set(templates.items.map((t) => t.className));
    for (const it of snap.items) expect(items.has(it.className), it.className).toBe(true);
    const heroes = new Set(templates.heroes.map((t) => t.heroId));
    for (const h of snap.heroes.filter((x) => x.playable)) expect(heroes.has(h.id), h.name).toBe(true);
  });

  it("every item template identifies itself, and no two items are near-identical", () => {
    let closest = { score: -1, pair: "" };
    for (const t of iconsRaw.items as { className: string }[]) {
      const px = templatePixels("items", (x) => x.className === t.className);
      const [a, b] = rankTemplates(px, templates.items, 2);
      expect(a!.className).toBe(t.className);
      if (b!.score > closest.score) closest = { score: b!.score, pair: `${t.className} ~ ${b!.className}` };
    }
    // Item art is unique per item (unlike the older shared HUD glyphs); keep it that way.
    expect(closest.score, closest.pair).toBeLessThan(0.9);
  });
});

describe("vision: reading a scoreboard", () => {
  const me = heroId("Abrams");
  const haze = heroId("Haze");
  const seven = heroId("Seven");
  const dynamo = heroId("Dynamo");
  const rows = [
    { hero: me, items: ["upgrade_close_range", "upgrade_endurance", "upgrade_lifestrike_gauntlets", "upgrade_melee_charge"] },
    { hero: haze, items: ["upgrade_headshot_booster", "upgrade_rapid_rounds", "upgrade_ricochet", "upgrade_toxic_bullets", "upgrade_improved_stamina"] },
    { hero: seven, items: ["upgrade_improved_spirit", "upgrade_magic_burst", "upgrade_magic_reach"] },
    { hero: dynamo, items: ["upgrade_magic_tempo", "upgrade_health"] },
  ];
  const { img, layout } = scoreboard(rows);
  const read = readScreen(img, layout, templates);

  it("finds every row, portrait and item confidently", () => {
    expect(read.warnings).toEqual([]);
    expect(read.rows).toHaveLength(rows.length);
    read.rows.forEach((r, i) => {
      expect(r.hero?.status).toBe("confident");
      expect(r.hero?.candidates[0]?.heroId).toBe(rows[i]!.hero);
      expect(r.items.map((it) => it.candidates[0]!.className)).toEqual(rows[i]!.items);
      for (const it of r.items) expect(it.status).toBe("confident");
    });
  });

  it("assigns rows from portraits and the roster, then applies as screen observations", () => {
    const ctx = { meHero: me, enemies: [haze, seven], allies: [dynamo] };
    const props = proposeTargets(read, ctx);
    expect(props.map((p) => p.target)).toEqual([{ kind: "me" }, { kind: "enemy", heroId: haze }, { kind: "enemy", heroId: seven }, { kind: "ally", heroId: dynamo }]);
    expect(canAutoApply(read, props)).toBe(true);
    const events = screenReadEvents(
      read.rows.map((r, i) => ({ target: props[i]!.target!, items: r.items.map((it) => it.candidates[0]!.className) })),
      { observedAt: T0 + 5000, gameTime: 600, complete: true, confidence: "high" },
    );
    const state = applyEvents(createMatchState("live"), [{ type: "match.start", matchId: "m1", mode: "standard", at: T0 }, ...events]);
    const hz = readField(state, `enemyItems:${haze}`, T0 + 6000)!;
    expect(hz.obs.source).toBe("screen");
    expect((hz.value as EnemyItemsValue).items).toEqual(rows[1]!.items);
    expect((hz.value as EnemyItemsValue).complete).toBe(true);
    expect(readField(state, "me.items", T0 + 6000)!.value).toEqual(rows[0]!.items);
  });

  it("asks instead of guessing when a portrait hero is not in the roster", () => {
    const props = proposeTargets(read, { meHero: me, enemies: [haze], allies: [] });
    expect(props[2]).toMatchObject({ target: null, unknownHeroId: seven });
    expect(canAutoApply(read, props)).toBe(false);
  });

  it("falls back to remembered row positions when portraits are not calibrated", () => {
    const noPortrait = readScreen(img, { ...layout, portrait: null }, templates);
    expect(noPortrait.rows.every((r) => r.hero === null)).toBe(true);
    const remembered = [{ yFrac: noPortrait.rows[1]!.yFrac + 0.004, target: { kind: "enemy" as const, heroId: haze } }];
    const props = proposeTargets(noPortrait, { meHero: me, enemies: [haze], allies: [] }, remembered);
    expect(props[1]!.target).toEqual({ kind: "enemy", heroId: haze });
    expect(props[0]!.target).toBeNull();
    // A remembered hero that left the roster is not reused.
    expect(proposeTargets(noPortrait, { meHero: me, enemies: [], allies: [] }, remembered)[1]!.target).toBeNull();
  });

  it("merges several rows for one player and drops ignored rows", () => {
    const ev = screenReadEvents(
      [
        { target: { kind: "enemy", heroId: haze }, items: ["a", "b"] },
        { target: { kind: "enemy", heroId: haze }, items: ["b", "c"] },
        { target: { kind: "ignore" }, items: ["x"] },
      ],
      { observedAt: T0, gameTime: null, complete: false, confidence: "medium" },
    );
    expect(ev).toHaveLength(1);
    expect(ev[0]).toMatchObject({ field: `enemyItems:${haze}`, value: { items: ["a", "b", "c"], complete: false }, source: "screen", confidence: "medium" });
  });
});

describe("vision: refusing bad input", () => {
  const { layout } = scoreboard([{ hero: heroId("Haze"), items: ["upgrade_close_range"] }]);

  it("an empty panel yields no rows and an explanation", () => {
    const r = readScreen(blank(960, 540, 7), layout, templates);
    expect(r.rows).toEqual([]);
    expect(r.warnings.join(" ")).toMatch(/No item icons/);
  });

  it("random noise is not read as items", () => {
    const img = blank(960, 540, 3);
    let s = 99;
    for (let i = 0; i < img.data.length; i++) if (i % 4 !== 3) img.data[i] = (s = (s * 1103515245 + 12345) & 0x7fffffff) & 255;
    const r = readScreen(img, layout, templates);
    expect(r.rows.flatMap((x) => x.items).filter((it) => it.status === "confident")).toEqual([]);
  });

  it("warns when the capture's aspect ratio differs from the calibration", () => {
    const r = readScreen(blank(800, 600), layout, templates);
    expect(r.warnings[0]).toMatch(/Recalibrate/);
  });

  it("calibration rectangles round-trip through fractions", () => {
    const img = { width: 2560, height: 1440 };
    const l = layoutFromCalibration(img, { x: 100, y: 200, w: 900, h: 700 }, { x: 120, y: 210, w: 40, h: 42 }, { box: { x: 40, y: 205, w: 50, h: 50 }, rowCy: 231 }, "t");
    expect(toPixels(l.itemArea, img)).toEqual({ x: 100, y: 200, w: 900, h: 700 });
    expect(l.iconSize * 1440).toBeCloseTo(41, 6);
    expect(l.portrait!.offsetY * 1440).toBeCloseTo(-1, 6);
  });
});
