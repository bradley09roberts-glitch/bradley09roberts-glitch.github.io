import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import {
  ICON_THUMB,
  applyEvents,
  calibrationBadges,
  hasContrast,
  hasTierBadge,
  learnBadgeHues,
  measureBadge,
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
  scanIcons,
  screenReadEvents,
  thumbnail,
  toPixels,
  type EnemyItemsValue,
  type RgbaImage,
  type RowTarget,
  type ScreenLayout,
  type TemplateSet,
} from "../src/index.js";
import { decodePng } from "../../ingest/src/png.js";
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

/** Tier badge colours: vitality measured on a real capture; weapon and spirit assumed. */
const BADGE_RGB: Record<string, [number, number, number]> = { vitality: [174, 210, 71], weapon: [230, 150, 50], spirit: [175, 120, 235] };
const slotOf = (cls: string) => snap.items.find((i) => i.className === cls)!.slot;

/** Draw the HUD's tier badge (a triangle in the top-right corner) over an icon at (x, y), size n. */
function badge(img: RgbaImage, x: number, y: number, n: number, slot: string): void {
  const c = BADGE_RGB[slot]!;
  for (let j = 0; j < n; j++) {
    for (let i = 0; i < n; i++) {
      if ((i + 0.5) / n - (j + 0.5) / n <= 0.65) continue;
      const p = ((y + j) * img.width + (x + i)) * 4;
      img.data[p] = c[0];
      img.data[p + 1] = c[1];
      img.data[p + 2] = c[2];
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
    r.items.forEach((cls, i) => {
      draw(img, templatePixels("items", (t) => t.className === cls), x0 + i * (s + 4), y, s);
      badge(img, x0 + i * (s + 4), y, s, slotOf(cls));
    });
  });
  const layout: ScreenLayout = {
    version: 1,
    aspect: 960 / 540,
    itemArea: { x: (x0 - 8) / 960, y: (y0 - 8) / 540, w: (12 * (s + 4)) / 960, h: (rows.length * pitch + 8) / 540 },
    iconSize: s / 540,
    orientation: "rows",
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
    const remembered = [{ posFrac: noPortrait.rows[1]!.posFrac + 0.004, target: { kind: "enemy" as const, heroId: haze } }];
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
    const l = layoutFromCalibration(img, { x: 100, y: 200, w: 900, h: 700 }, { x: 120, y: 210, w: 40, h: 42 }, { box: { x: 40, y: 205, w: 50, h: 50 } }, "t");
    expect(toPixels(l.itemArea, img)).toEqual({ x: 100, y: 200, w: 900, h: 700 });
    expect(l.iconSize * 1440).toBeCloseTo(41, 6);
    // Portrait to the left of the icon → players are rows.
    expect(l.orientation).toBe("rows");
    expect(l.portrait!.offsetY * 1440).toBeCloseTo(-1, 6);
    // Portrait above the icon (Deadlock's Tab view) → players are columns.
    const c = layoutFromCalibration(img, { x: 100, y: 300, w: 900, h: 80 }, { x: 120, y: 310, w: 40, h: 40 }, { box: { x: 110, y: 20, w: 70, h: 90 } }, "t");
    expect(c.orientation).toBe("columns");
    expect(c.portrait!.centerY! * 1440).toBeCloseTo(65, 6);
  });
});

describe("vision: a real Deadlock HUD (crops of a user capture)", () => {
  // Small crops of a real in-game capture (sandbox, Tab view): only item icons on the HUD
  // background. The capture was a downscaled preview, so icons are as small as 17 px here.
  const load = (f: string) => decodePng(readFileSync(path.join(root, "fixtures/vision", f)));

  it("reads the two scoreboard items under a player card (17 px, tier badges)", () => {
    const img = load("real-card-items.png");
    const layout: ScreenLayout = { version: 1, aspect: img.width / img.height, itemArea: { x: 0, y: 0, w: 1, h: 1 }, iconSize: 17 / img.height, orientation: "columns", portrait: null, calibratedAt: "t" };
    const r = readScreen(img, layout, templates);
    expect(r.rows).toHaveLength(1);
    expect(r.rows[0]!.items.map((i) => i.candidates[0]!.className)).toEqual(["upgrade_superior_stamina", "upgrade_healbuff"]);
    for (const it of r.rows[0]!.items) {
      expect(it.status).toBe("confident");
      expect(it.badge).toBe(true);
    }
  });

  it("reads the player's own inventory icons (35 px)", () => {
    const img = load("real-inventory-items.png");
    const layout: ScreenLayout = { version: 1, aspect: img.width / img.height, itemArea: { x: 0, y: 0, w: 1, h: 1 }, iconSize: 35 / img.height, orientation: "rows", portrait: null, calibratedAt: "t" };
    const r = readScreen(img, layout, templates);
    expect(r.rows.flatMap((g) => g.items.map((i) => [i.candidates[0]!.className, i.status]))).toEqual([
      ["upgrade_superior_stamina", "confident"],
      ["upgrade_healbuff", "confident"],
    ]);
  });

  it("learns the badge hue from a confirmed icon (vitality ≈ 76°)", () => {
    const img = load("real-inventory-items.png");
    const learned = learnBadgeHues(img, [{ box: { x: 8, y: 7, w: 35, h: 35 }, slot: "vitality" }]);
    expect(learned.vitality).toBeGreaterThan(60);
    expect(learned.vitality).toBeLessThan(95);
  });
});

describe("vision: Tab-view columns over busy scenery", () => {
  const me = heroId("Abrams");
  const haze = heroId("Haze");
  const seven = heroId("Seven");
  const W = 1280;
  const H = 720;
  // Busy "game world": blocks and gradients of varied contrast, like a lit scene behind the HUD.
  const img = blank(W, H, 5);
  let seed = 11;
  const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
  for (let k = 0; k < 400; k++) {
    const x = Math.floor(rnd() * W);
    const y = Math.floor(rnd() * H);
    const w = 4 + Math.floor(rnd() * 60);
    const h = 4 + Math.floor(rnd() * 60);
    const c = [rnd() * 255, rnd() * 200, rnd() * 160];
    for (let j = y; j < Math.min(H, y + h); j++) for (let i = x; i < Math.min(W, x + w); i++) {
      const p = (j * W + i) * 4;
      for (let q = 0; q < 3; q++) img.data[p + q] = 0.6 * img.data[p + q]! + 0.4 * c[q]!;
    }
  }
  const cards = [
    { hero: me, team: [200, 150, 40], items: ["upgrade_close_range", "upgrade_endurance", "upgrade_melee_charge"] },
    { hero: haze, team: [50, 90, 200], items: ["upgrade_headshot_booster", "upgrade_rapid_rounds", "upgrade_toxic_bullets", "upgrade_improved_stamina"] },
    { hero: seven, team: [50, 90, 200], items: ["upgrade_improved_spirit", "upgrade_magic_burst"] },
  ];
  const s = 24;
  const cardW = 70;
  const truth: { hero: number; items: string[] }[] = [];
  cards.forEach((cd, k) => {
    const x0 = 420 + k * (cardW + 12);
    for (let j = 20; j < 200; j++) for (let i = x0; i < x0 + cardW; i++) {
      const p = (j * W + i) * 4;
      for (let q = 0; q < 3; q++) img.data[p + q] = 0.3 * img.data[p + q]! + 0.7 * cd.team[q]!;
    }
    draw(img, templatePixels("heroes", (t) => t.id === cd.hero && t.variant === "small"), x0 + 7, 24, 56);
    cd.items.forEach((cls, i) => {
      const x = x0 + 8 + (i % 2) * (s + 6);
      const y = 210 + Math.floor(i / 2) * (s + 6);
      draw(img, templatePixels("items", (t) => t.className === cls), x, y, s);
      badge(img, x, y, s, slotOf(cls));
    });
    truth.push({ hero: cd.hero, items: cd.items });
  });

  const sample = { x: 428, y: 210, w: s, h: s };
  const badges = calibrationBadges(img, sample, templates);

  it("calibration notices the tier badges and learns the marked icon's badge hue", () => {
    expect(badges).toMatchObject({ present: true, hues: { weapon: expect.any(Number) } });
  });

  it("groups items under each card and names players from portraits", () => {
    const layout = layoutFromCalibration(img, { x: 400, y: 200, w: 300, h: 80 }, sample, { box: { x: 427, y: 24, w: 56, h: 56 } }, "t", badges);
    expect(layout.orientation).toBe("columns");
    const r = readScreen(img, layout, templates);
    expect(r.rows.map((g) => g.hero?.candidates[0]?.heroId)).toEqual(truth.map((t) => t.hero));
    expect(r.rows.map((g) => g.items.map((i) => i.candidates[0]!.className).sort())).toEqual(truth.map((t) => [...t.items].sort()));
    // Portraits only suggest: a confident portrait proposes its player, anything less is asked.
    const ctx = { meHero: me, enemies: [haze, seven], allies: [] };
    const props = proposeTargets(r, ctx);
    const want: RowTarget[] = [{ kind: "me" }, { kind: "enemy", heroId: haze }, { kind: "enemy", heroId: seven }];
    props.forEach((p, i) => {
      if (p.target) expect(p.target).toEqual(want[i]);
      else expect(r.rows[i]!.hero?.status).not.toBe("confident");
    });
    // After the user confirms who each column is once, later reads in the match apply by position.
    const remembered = r.rows.map((g, i) => ({ posFrac: g.posFrac, target: want[i]! }));
    const again = proposeTargets(r, ctx, remembered);
    expect(again.map((p) => p.target)).toEqual(want);
    expect(canAutoApply(r, again)).toBe(true);
  });

  it("an oversized item area over the cards and scenery adds no confident junk", () => {
    const layout = layoutFromCalibration(img, { x: 0, y: 0, w: W, h: H }, sample, null, "t", badges);
    const r = readScreen(img, layout, templates);
    const read = r.rows.flatMap((g) => g.items.filter((i) => i.status === "confident").map((i) => i.candidates[0]!.className));
    const expected = truth.flatMap((t) => t.items);
    expect(read.filter((c) => !expected.includes(c))).toEqual([]);
  });
});

describe("vision: false-match guards", () => {
  it("a washed-out (low-contrast) copy of an icon is rejected", () => {
    const t = templates.items.find((x) => x.className === "upgrade_close_range")!;
    const px = templatePixels("items", (x) => x.className === "upgrade_close_range");
    const flat = Float32Array.from(px, (v, i) => (i % 4 === 3 ? v : 0.2 + (v - 0.5) * 0.05));
    expect(hasContrast(px, t)).toBe(true);
    expect(hasContrast(flat, t)).toBe(false);
  });

  it("a badge must stand out from the icon; a uniformly coloured patch is not a badge", () => {
    const yellow = blank(40, 40);
    for (let i = 0; i < 40 * 40; i++) yellow.data.set([200, 180, 40], i * 4);
    expect(measureBadge(yellow, { x: 0, y: 0, w: 40, h: 40 }).present).toBe(false);
    const icon = blank(40, 40);
    draw(icon, templatePixels("items", (x) => x.className === "upgrade_endurance"), 0, 0, 40);
    badge(icon, 0, 0, 40, "vitality");
    expect(hasTierBadge(icon, { x: 0, y: 0, w: 40, h: 40 }, "vitality")).toBe(true);
    // Wrong slot hue, and screens calibrated as badge-free, never count a badge.
    expect(hasTierBadge(icon, { x: 0, y: 0, w: 40, h: 40 }, "spirit")).toBe(false);
    expect(hasTierBadge(icon, { x: 0, y: 0, w: 40, h: 40 }, "vitality", { present: false, hues: {} })).toBe(false);
  });
});
