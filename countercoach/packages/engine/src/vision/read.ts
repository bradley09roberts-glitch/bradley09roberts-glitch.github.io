import { z } from "zod";
import type { Confidence, SlotType } from "../types.js";
import type { EnemyItemsValue, MatchEvent } from "../state/types.js";
import { thumbnail, toPixels, type FracRect, type Rect, type RgbaImage } from "./image.js";
import { groupRows, hasContrast, readBox, scanIcons, type IconDetection } from "./scan.js";
import { ICON_THUMB, rankTemplates, scoreThumb, type Candidate, type PreparedTemplate, type TemplateSet } from "./templates.js";

/**
 * Reads item icons (and optionally hero portraits) from a user-triggered capture of a screen the
 * player can already see, such as the Tab scoreboard. Nothing here touches the game: it only
 * looks at pixels. The layout comes from a one-time calibration on the user's own screenshot.
 *
 * Two layouts are supported. In Deadlock's Tab view (seen on a real capture) each player is a
 * *column*: a portrait card on top with that player's items underneath. A *rows* layout (portrait
 * to the left of a line of items) is kept for other screens. The calibration decides which.
 */

const fracRect = z.object({ x: z.number().min(0).max(1), y: z.number().min(0).max(1), w: z.number().min(0).max(1), h: z.number().min(0).max(1) });

export const screenLayoutSchema = z.object({
  version: z.literal(1),
  /** width / height of the capture used for calibration. */
  aspect: z.number().positive(),
  /** Region containing every item icon to read. */
  itemArea: fracRect,
  /** Icon side as a fraction of the capture height. */
  iconSize: z.number().positive().max(0.5),
  /** "columns": portrait above each player's items (Deadlock Tab view). "rows": portrait to the left. */
  orientation: z.enum(["rows", "columns"]).default("rows"),
  /** Optional hero portraits: one per player group. */
  portrait: z
    .object({
      /** Left edge and width of the calibrated portrait, as fractions of the capture width. */
      x: z.number().min(0).max(1),
      w: z.number().min(0).max(1),
      /** Portrait height as a fraction of the capture height. */
      size: z.number().positive().max(0.5),
      /** Rows: portrait centre minus item-row centre (fraction of height). */
      offsetY: z.number().min(-0.5).max(0.5),
      /** Columns: portrait centre (fraction of height); every card's portrait sits at this height. */
      centerY: z.number().min(0).max(1).optional(),
    })
    .nullable(),
  /**
   * Tier badges seen on this screen: whether the calibrated icon had one, and the badge hue per
   * item slot learned from icons the user confirmed (overrides the defaults in BADGE_HUES).
   */
  badges: z
    .object({
      present: z.boolean(),
      hues: z.object({ weapon: z.number().min(0).max(360).optional(), vitality: z.number().min(0).max(360).optional(), spirit: z.number().min(0).max(360).optional() }),
    })
    .optional(),
  /**
   * Item slot grid learned from a calibration capture where players had many items: every place an
   * item can appear, grouped by player card. When present, reads check only these slots.
   */
  slots: z
    .object({
      cards: z
        .array(
          z.object({
            /** Card centre along the player axis (fraction of width for columns, height for rows). */
            centre: z.number().min(0).max(1),
            slots: z.array(fracRect).max(48),
          }),
        )
        .max(24),
    })
    .optional(),
  calibratedAt: z.string(),
});
export type ScreenLayout = z.infer<typeof screenLayoutSchema>;
export type SlotGrid = NonNullable<ScreenLayout["slots"]>;

/**
 * Thresholds (normalised cross-correlation). On a real Deadlock capture, true item icons scored
 * 0.85–0.93 with the runner-up 0.27–0.51 behind; scenery that slipped through scored up to ~0.85
 * but almost never with a wide margin, so a wide margin is required for an automatic read.
 */
export const VISION_THRESHOLDS = {
  itemConfident: 0.85,
  itemMargin: 0.2,
  /** With a matching tier badge in the corner, slightly lower bars apply. */
  itemConfidentBadge: 0.72,
  itemMarginBadge: 0.1,
  itemMin: 0.72,
  heroConfident: 0.75,
  heroMargin: 0.05,
  heroMin: 0.5,
} as const;

/**
 * Default hue ranges (degrees) of the tier badge drawn in the top-right corner of HUD item icons,
 * by item slot. Vitality (yellow-green, ~75°) was measured on a real capture; weapon (orange) and
 * spirit (purple) are assumptions until the user confirms an icon of that slot, after which the
 * learned hue (± BADGE_HUE_TOLERANCE) is used. A badge only ever adds evidence.
 */
export const BADGE_HUES: Record<SlotType, [number, number]> = {
  vitality: [55, 110],
  weapon: [15, 50],
  spirit: [250, 310],
};

export const BADGE_HUE_TOLERANCE = 25;

export type ReadStatus = "confident" | "uncertain";

export interface ScreenItemRead {
  box: Rect;
  candidates: Candidate[];
  status: ReadStatus;
  /** A tier badge of the expected colour was seen in the icon's corner. */
  badge: boolean;
}

export interface ScreenRowRead {
  index: number;
  box: Rect;
  /**
   * Position of this player group along the player axis, as a fraction of the capture (row:
   * vertical centre / height; column: horizontal centre / width). Used to remember assignments.
   */
  posFrac: number;
  hero: { box: Rect; candidates: Candidate[]; status: ReadStatus | "none" } | null;
  items: ScreenItemRead[];
}

export interface ScreenRead {
  width: number;
  height: number;
  orientation: "rows" | "columns";
  rows: ScreenRowRead[];
  warnings: string[];
  ms: number;
}

export interface ReadOptions {
  allowItem?: (className: string) => boolean;
  allowHero?: (heroId: number) => boolean;
}

function classify(c: Candidate[], confident: number, margin: number): ReadStatus {
  const a = c[0]?.score ?? 0;
  const b = c[1]?.score ?? -1;
  return a >= confident && a - b >= margin ? "confident" : "uncertain";
}

function classifyItem(c: Candidate[], badge: boolean, badgeScreen: boolean): ReadStatus {
  const T = VISION_THRESHOLDS;
  // On a screen whose icons carry tier badges, a badge-less "icon" is not trusted however well
  // it correlates: on a real capture such matches were scenery.
  if (!badgeScreen && classify(c, T.itemConfident, T.itemMargin) === "confident") return "confident";
  return badge ? classify(c, T.itemConfidentBadge, T.itemMarginBadge) : "uncertain";
}

/**
 * Measure the icon's top-right corner: is there a bright, saturated badge that stands out from
 * the art beside it, and what hue is it? (A uniformly coloured patch, such as a team-coloured
 * card behind a false match, fails the stand-out test.)
 */
export function measureBadge(img: RgbaImage, box: Rect): { present: boolean; hue: number } {
  const th = thumbnail(img, box, 16);
  const mean = (inside: (u: number, v: number) => boolean): [number, number, number] => {
    let r = 0;
    let g = 0;
    let b = 0;
    let n = 0;
    for (let i = 0; i < 256; i++) {
      const u = ((i % 16) + 0.5) / 16;
      const v = (Math.floor(i / 16) + 0.5) / 16;
      if (!inside(u, v)) continue;
      r += th[i * 4]!;
      g += th[i * 4 + 1]!;
      b += th[i * 4 + 2]!;
      n++;
    }
    return [r / n, g / n, b / n];
  };
  // The badge's tip (well inside its triangle), and art on three sides of it: diagonally
  // below-left, to its left along the top edge, and below it along the right edge. A real badge
  // differs from all three; a coloured card or scenery behind a false match usually does not.
  const tip = mean((u, v) => u - v > 0.7);
  const sides = [mean((u, v) => u - v > 0.15 && u - v < 0.45), mean((u, v) => u > 0.2 && u < 0.45 && v < 0.12), mean((u, v) => u > 0.88 && v > 0.4 && v < 0.6)];
  const [r, g, b] = tip;
  const mx = Math.max(r, g, b);
  const mn = Math.min(r, g, b);
  const d = mx - mn;
  let hue = d === 0 ? 0 : mx === r ? ((g - b) / d) % 6 : mx === g ? (b - r) / d + 2 : (r - g) / d + 4;
  hue = (hue * 60 + 360) % 360;
  const standsOut = sides.every((c) => Math.hypot(r - c[0], g - c[1], b - c[2]) >= 0.15);
  return { present: standsOut && mx >= 0.55 && d / mx >= 0.3, hue };
}

function hueDistance(a: number, b: number): number {
  const d = Math.abs(a - b) % 360;
  return d > 180 ? 360 - d : d;
}

/**
 * Does the icon carry a tier badge of the hue expected for this slot on this screen? On a screen
 * calibrated as having badges, a slot whose hue has not been learned yet accepts any badge hue
 * (the assumed defaults could be wrong); otherwise the default ranges apply.
 */
export function hasTierBadge(img: RgbaImage, box: Rect, slot: SlotType | null, badges?: ScreenLayout["badges"]): boolean {
  if (!slot || badges?.present === false) return false;
  const m = measureBadge(img, box);
  if (!m.present) return false;
  const learned = badges?.hues[slot];
  if (learned != null) return hueDistance(m.hue, learned) <= BADGE_HUE_TOLERANCE;
  if (badges?.present) return true;
  const [lo, hi] = BADGE_HUES[slot];
  return m.hue >= lo && m.hue <= hi;
}

/**
 * Learn badge hues per slot from icons the user confirmed (median per slot). Returns only slots
 * with at least one badge seen.
 */
export function learnBadgeHues(img: RgbaImage, confirmed: { box: Rect; slot: SlotType }[]): Partial<Record<SlotType, number>> {
  const by: Partial<Record<SlotType, number[]>> = {};
  for (const c of confirmed) {
    const m = measureBadge(img, c.box);
    if (m.present) (by[c.slot] ??= []).push(m.hue);
  }
  const out: Partial<Record<SlotType, number>> = {};
  for (const [slot, hs] of Object.entries(by) as [SlotType, number[]][]) {
    const sorted = [...hs].sort((a, b) => a - b);
    out[slot] = Math.round(sorted[Math.floor(sorted.length / 2)]!);
  }
  return out;
}

interface Group {
  members: number[];
  box: Rect;
  /** Centre along the player axis, in pixels. */
  centre: number;
}

/** Players as columns: cluster icons by horizontal position (a gap wider than ~0.7 icon starts a new player). */
function groupColumns(dets: IconDetection[], iconPx: number, maxWidth: number | null): Group[] {
  const order = dets.map((_, i) => i).sort((a, b) => dets[a]!.box.x - dets[b]!.box.x);
  const groups: number[][] = [];
  let cur: number[] | null = null;
  let left = 0;
  let right = -Infinity;
  for (const i of order) {
    const b = dets[i]!.box;
    const split = !cur || b.x - right > iconPx * 0.7 || (maxWidth != null && b.x + b.w - left > maxWidth);
    if (split) {
      cur = [i];
      groups.push(cur);
      left = b.x;
      right = b.x + b.w;
    } else {
      cur!.push(i);
      right = Math.max(right, b.x + b.w);
    }
  }
  return groups.map((members) => {
    members.sort((a, b) => dets[a]!.box.y - dets[b]!.box.y || dets[a]!.box.x - dets[b]!.box.x);
    const bs = members.map((m) => dets[m]!.box);
    const x0 = Math.min(...bs.map((b) => b.x));
    const y0 = Math.min(...bs.map((b) => b.y));
    const x1 = Math.max(...bs.map((b) => b.x + b.w));
    const y1 = Math.max(...bs.map((b) => b.y + b.h));
    return { members, box: { x: x0, y: y0, w: x1 - x0, h: y1 - y0 }, centre: (x0 + x1) / 2 };
  });
}

/** Read one capture using a calibrated layout. */
export function readScreen(img: RgbaImage, layout: ScreenLayout, templates: TemplateSet, opts: ReadOptions = {}): ScreenRead {
  const t0 = Date.now();
  const warnings: string[] = [];
  const orientation = layout.orientation ?? "rows";
  const aspect = img.width / img.height;
  if (Math.abs(aspect - layout.aspect) / layout.aspect > 0.02) {
    warnings.push(
      `This capture is ${img.width}×${img.height} (aspect ${aspect.toFixed(2)}) but the layout was calibrated at aspect ${layout.aspect.toFixed(2)}. Recalibrate for this resolution.`,
    );
  }
  if (layout.slots?.cards.length) return readSlots(img, layout, templates, opts, warnings, t0);
  const T = VISION_THRESHOLDS;
  const itemTemplates = templates.items.filter((t) => !opts.allowItem || opts.allowItem(t.className));
  const slotOf = new Map(itemTemplates.map((t) => [t.className, t.slot]));
  const iconPx = layout.iconSize * img.height;
  const raw = scanIcons(img, toPixels(layout.itemArea, img), itemTemplates, { iconPx, minScore: T.itemMin });
  const badgeScreen = layout.badges?.present === true;
  const judged = raw.map((d) => {
    const badge = hasTierBadge(img, d.box, slotOf.get(d.candidates[0]!.className) ?? null, layout.badges);
    return { d, badge, status: classifyItem(d.candidates, badge, badgeScreen) };
  });
  // Keep an uncertain icon only if it carries a tier badge, or (on screens without badges) lines
  // up with a confident icon in the same row or column: stray matches in scenery do neither.
  const confident = judged.filter((j) => j.status === "confident").map((j) => j.d.box);
  const aligned = (b: Rect) =>
    confident.some((c) => {
      const dx = Math.abs(c.x - b.x);
      const dy = Math.abs(c.y - b.y);
      return (dy < iconPx * 0.25 && dx < iconPx * 4) || (dx < iconPx * 0.25 && dy < iconPx * 4);
    });
  const keep = judged.filter((j) => j.status === "confident" || j.badge || (!badgeScreen && aligned(j.d.box)));
  const dets = keep.map((j) => j.d);
  const info = keep.map((j) => ({ status: j.status, badge: j.badge }));

  const portraitW = layout.portrait ? layout.portrait.w * img.width : null;
  const groups: Group[] =
    orientation === "columns"
      ? groupColumns(dets, iconPx, portraitW != null ? portraitW * 1.3 : null)
      : groupRows(dets, iconPx).map((r) => ({ members: r.members, box: r.box, centre: r.cy }));
  // A real player group has at least one clearly readable icon.
  const players = groups.filter((g) => g.members.some((m) => info[m]!.status === "confident"));
  const heroTemplates = templates.heroes.filter((t) => t.heroId != null && (!opts.allowHero || opts.allowHero(t.heroId)));
  const out: ScreenRowRead[] = players.map((g, index) => {
    const items: ScreenItemRead[] = g.members.map((m) => ({ box: dets[m]!.box, candidates: dets[m]!.candidates, status: info[m]!.status, badge: info[m]!.badge }));
    const hero = layout.portrait && heroTemplates.length ? readPortrait(img, layout, orientation, g, heroTemplates) : null;
    return { index, box: g.box, posFrac: g.centre / (orientation === "columns" ? img.width : img.height), hero, items };
  });
  if (raw.length === 0) warnings.push("No item icons were found in the calibrated area. Was the scoreboard open when the capture was taken?");
  return { width: img.width, height: img.height, orientation, rows: out, warnings, ms: Date.now() - t0 };
}

// ---------------------------------------------------------------------------------------------
// Calibrate once, then read fixed slots
// ---------------------------------------------------------------------------------------------

function bboxOf(boxes: Rect[]): Rect {
  const x0 = Math.min(...boxes.map((b) => b.x));
  const y0 = Math.min(...boxes.map((b) => b.y));
  const x1 = Math.max(...boxes.map((b) => b.x + b.w));
  const y1 = Math.max(...boxes.map((b) => b.y + b.h));
  return { x: x0, y: y0, w: x1 - x0, h: y1 - y0 };
}

/**
 * Read one known slot: the best of a few one-pixel shifts and a small inset, then a full ranking.
 * Returns null for an empty slot (flat, low-contrast).
 */
function readSlot(img: RgbaImage, box: Rect, templates: PreparedTemplate[], byKey: Map<string, PreparedTemplate>): { box: Rect; candidates: Candidate[] } | null {
  const short = rankTemplates(thumbnail(img, box, ICON_THUMB), templates, 8)
    .map((c) => byKey.get(c.key))
    .filter((t): t is PreparedTemplate => !!t);
  if (!short.length) return null;
  let best: { box: Rect; score: number } | null = null;
  for (const inset of [0, 0.06]) {
    for (const [dx, dy] of [[0, 0], [-1, 0], [1, 0], [0, -1], [0, 1]] as const) {
      const m = box.w * inset;
      const b = { x: box.x + dx + m, y: box.y + dy + m, w: box.w - 2 * m, h: box.h - 2 * m };
      const th = thumbnail(img, b, ICON_THUMB);
      for (const t of short) {
        const sc = scoreThumb(th, t);
        if (!best || sc > best.score) best = { box: b, score: sc };
      }
    }
  }
  const th = thumbnail(img, best!.box, ICON_THUMB);
  const candidates = rankTemplates(th, templates, 3);
  const top = candidates[0] ? byKey.get(candidates[0].key) : undefined;
  if (!top || !hasContrast(th, top)) return null;
  return { box: best!.box, candidates };
}

/** Read every calibrated slot; cards with no readable item are left out (never cleared). */
function readSlots(img: RgbaImage, layout: ScreenLayout, templates: TemplateSet, opts: ReadOptions, warnings: string[], t0: number): ScreenRead {
  const T = VISION_THRESHOLDS;
  const orientation = layout.orientation ?? "rows";
  const itemTemplates = templates.items.filter((t) => !opts.allowItem || opts.allowItem(t.className));
  const byKey = new Map(itemTemplates.map((t) => [t.key, t]));
  const slotOf = new Map(itemTemplates.map((t) => [t.className, t.slot]));
  const badgeScreen = layout.badges?.present === true;
  const heroTemplates = templates.heroes.filter((t) => t.heroId != null && (!opts.allowHero || opts.allowHero(t.heroId)));
  const out: ScreenRowRead[] = [];
  layout.slots!.cards.forEach((card, index) => {
    const items: ScreenItemRead[] = [];
    for (const fr of card.slots) {
      const r = readSlot(img, toPixels(fr, img), itemTemplates, byKey);
      if (!r || r.candidates[0]!.score < T.itemMin) continue;
      const badge = hasTierBadge(img, r.box, slotOf.get(r.candidates[0]!.className) ?? null, layout.badges);
      // On a badge screen every real item shows its badge; a badge-less slot is empty or covered.
      if (badgeScreen && !badge) continue;
      // A known slot holding something item-like but unclear is asked about, not dropped.
      const status = classifyItem(r.candidates, badge, badgeScreen);
      items.push({ box: r.box, candidates: r.candidates, status, badge });
    }
    if (!items.length) return;
    const box = bboxOf(items.map((i) => i.box));
    const centre = card.centre * (orientation === "columns" ? img.width : img.height);
    const hero = layout.portrait && heroTemplates.length ? readPortrait(img, layout, orientation, { members: [], box, centre }, heroTemplates) : null;
    out.push({ index, box, posFrac: card.centre, hero, items });
  });
  if (!out.length) warnings.push("No items were found in the calibrated slots. Was the scoreboard open when the capture was taken?");
  return { width: img.width, height: img.height, orientation, rows: out, warnings, ms: Date.now() - t0 };
}

/** Cluster 1-D values (sorted) with a tolerance; returns cluster means. */
function cluster(values: number[], tol: number): number[] {
  const v = [...values].sort((a, b) => a - b);
  const out: number[][] = [];
  for (const x of v) {
    const last = out[out.length - 1];
    if (last && x - last[last.length - 1]! <= tol) last.push(x);
    else out.push([x]);
  }
  return out.map((c) => c.reduce((s, x) => s + x, 0) / c.length);
}

/** Fill evenly spaced gaps in sorted positions (a missing row or card); optionally extend by one step. */
function fillGaps(pos: number[], tolFrac: number, extend: boolean): number[] {
  if (pos.length < 2) return pos;
  const diffs = pos.slice(1).map((p, i) => p - pos[i]!);
  const pitch = Math.min(...diffs);
  const out = [pos[0]!];
  for (let i = 1; i < pos.length; i++) {
    const d = pos[i]! - pos[i - 1]!;
    const k = Math.round(d / pitch);
    if (k >= 2 && Math.abs(d - k * pitch) <= tolFrac * pitch) for (let j = 1; j < k; j++) out.push(pos[i - 1]! + j * pitch);
    out.push(pos[i]!);
  }
  if (extend) out.push(pos[pos.length - 1]! + pitch);
  return out;
}

/**
 * Learn the slot grid from a read of a well-filled scoreboard (calibrate when players have many
 * items). Slots are the same for every card: positions within a card (across the player axis)
 * and positions along it are pooled over all cards, gaps are filled, and one extra slot line is
 * added for items bought later.
 */
export function buildSlotGrid(read: ScreenRead, layout: ScreenLayout): SlotGrid | null {
  if (!read.rows.length) return null;
  const W = read.width;
  const H = read.height;
  const s = layout.iconSize * H;
  const cols = (layout.orientation ?? "rows") === "columns";
  // "u" runs along the player axis (where cards are), "v" across it (where a card's items stack).
  const u = (b: Rect) => (cols ? b.x + b.w / 2 : b.y + b.h / 2);
  const v = (b: Rect) => (cols ? b.y + b.h / 2 : b.x + b.w / 2);
  const centres = read.rows.map((r) => r.posFrac * (cols ? W : H));
  const offsets = cluster(read.rows.flatMap((r, i) => r.items.map((it) => u(it.box) - centres[i]!)), s * 0.35);
  // Re-centre each card on the offset pattern (a card whose items sat in one column only).
  const fitted = read.rows.map((r, i) => {
    const us = r.items.map((it) => u(it.box));
    let best = centres[i]!;
    let bestErr = Infinity;
    for (const x of us) {
      for (const o of offsets) {
        const c = x - o;
        const err = us.reduce((e, y) => e + Math.min(...offsets.map((k) => Math.abs(y - (c + k)))), 0);
        if (err < bestErr) {
          bestErr = err;
          best = c;
        }
      }
    }
    return best;
  });
  const lines = fillGaps(cluster(read.rows.flatMap((r) => r.items.map((it) => v(it.box))), s * 0.35), 0.15, true);
  const cards = fillGaps(cluster(fitted, s * 0.35), 0.1, false);
  return {
    cards: cards.map((c) => ({
      centre: c / (cols ? W : H),
      slots: lines.flatMap((line) =>
        offsets.map((o) => {
          const cu = c + o;
          const cx = cols ? cu : line;
          const cy = cols ? line : cu;
          return { x: (cx - s / 2) / W, y: (cy - s / 2) / H, w: s / W, h: s / H };
        }),
      ).filter((r) => r.x >= 0 && r.y >= 0 && r.x + r.w <= 1 && r.y + r.h <= 1),
    })),
  };
}

/** Merge a newly learned grid into an existing one (cards and slots matched by position). */
export function mergeSlotGrids(a: SlotGrid, b: SlotGrid, tol = 0.004): SlotGrid {
  const cards = a.cards.map((c) => ({ centre: c.centre, slots: [...c.slots] }));
  for (const nb of b.cards) {
    const hit = cards.find((c) => Math.abs(c.centre - nb.centre) <= tol * 3);
    if (!hit) {
      cards.push({ centre: nb.centre, slots: [...nb.slots] });
      continue;
    }
    for (const sl of nb.slots) {
      if (!hit.slots.some((x) => Math.abs(x.x - sl.x) <= tol && Math.abs(x.y - sl.y) <= tol * 2)) hit.slots.push(sl);
    }
  }
  return { cards: cards.sort((x, y) => x.centre - y.centre).slice(0, 24).map((c) => ({ centre: c.centre, slots: c.slots.slice(0, 48) })) };
}

/** Find the portrait for a player group: small search around where the calibration says it is. */
function readPortrait(img: RgbaImage, layout: ScreenLayout, orientation: "rows" | "columns", g: Group, heroTemplates: TemplateSet["heroes"]): NonNullable<ScreenRowRead["hero"]> {
  const p = layout.portrait!;
  // Portrait art is square (API art and the in-game card head shot); compare a square at the
  // centre of the calibrated box rather than squashing a tall card into the template.
  const side = Math.min(p.w * img.width, p.size * img.height);
  const w = side;
  const h = side;
  const cx = orientation === "columns" ? g.centre : (p.x + p.w / 2) * img.width;
  const cy = orientation === "columns" ? (p.centerY ?? 0) * img.height : g.centre + p.offsetY * img.height;
  const jx = w / 4;
  const jy = h / 4;
  const step = Math.max(1, Math.min(w, h) / 8);
  let best: { box: Rect; candidates: Candidate[] } | null = null;
  for (let dy = -jy; dy <= jy + 1e-6; dy += step) {
    for (let dx = -jx; dx <= jx + 1e-6; dx += step) {
      const box = { x: cx - w / 2 + dx, y: cy - h / 2 + dy, w, h };
      const c = readBox(img, box, heroTemplates, 3);
      if (c[0] && (!best || c[0].score > best.candidates[0]!.score)) best = { box, candidates: c };
    }
  }
  const T = VISION_THRESHOLDS;
  const fallback = { x: cx - w / 2, y: cy - h / 2, w, h };
  if (!best || best.candidates[0]!.score < T.heroMin) return { box: best?.box ?? fallback, candidates: best?.candidates ?? [], status: "none" };
  return { ...best, status: classify(best.candidates, T.heroConfident, T.heroMargin) };
}

// ---------------------------------------------------------------------------------------------
// Turning a read into match observations
// ---------------------------------------------------------------------------------------------

export type RowTarget = { kind: "me" } | { kind: "enemy"; heroId: number } | { kind: "ally"; heroId: number } | { kind: "ignore" };

export interface RowProposal {
  row: number;
  target: RowTarget | null;
  /** Hero recognised in the portrait but missing from the roster. */
  unknownHeroId: number | null;
  reason: string;
}

export interface RosterContext {
  meHero: number | null;
  enemies: number[];
  allies: number[];
}

export function sameTarget(a: RowTarget | null, b: RowTarget | null): boolean {
  if (!a || !b || a.kind !== b.kind) return false;
  return a.kind === "me" || a.kind === "ignore" || (a as { heroId: number }).heroId === (b as { heroId: number }).heroId;
}

function targetValid(t: RowTarget, ctx: RosterContext): boolean {
  if (t.kind === "me") return ctx.meHero != null;
  if (t.kind === "enemy") return ctx.enemies.includes(t.heroId);
  if (t.kind === "ally") return ctx.allies.includes(t.heroId);
  return true;
}

/**
 * Suggest who each row belongs to: a confident portrait wins; otherwise a row at the same height
 * as an earlier confirmed read reuses that assignment. Anything else is left for the user.
 */
export function proposeTargets(read: ScreenRead, ctx: RosterContext, remembered: { posFrac: number; target: RowTarget }[] = []): RowProposal[] {
  const props: RowProposal[] = read.rows.map((r) => {
    const h = r.hero;
    if (h && h.status === "confident") {
      const id = h.candidates[0]!.heroId!;
      if (id === ctx.meHero) return { row: r.index, target: { kind: "me" }, unknownHeroId: null, reason: "portrait matches your hero" };
      if (ctx.enemies.includes(id)) return { row: r.index, target: { kind: "enemy", heroId: id }, unknownHeroId: null, reason: "portrait matches an enemy" };
      if (ctx.allies.includes(id)) return { row: r.index, target: { kind: "ally", heroId: id }, unknownHeroId: null, reason: "portrait matches an ally" };
      return { row: r.index, target: null, unknownHeroId: id, reason: "portrait hero is not in your roster" };
    }
    const mem = remembered
      .filter((m) => Math.abs(m.posFrac - r.posFrac) <= 0.015 && targetValid(m.target, ctx))
      .sort((a, b) => Math.abs(a.posFrac - r.posFrac) - Math.abs(b.posFrac - r.posFrac))[0];
    if (mem) return { row: r.index, target: mem.target, unknownHeroId: null, reason: "same place as your last confirmed read" };
    return { row: r.index, target: null, unknownHeroId: null, reason: "choose who this row belongs to" };
  });
  // A player can only be one row: on duplicates keep the first and ask about the rest.
  for (let i = 0; i < props.length; i++) {
    const t = props[i]!.target;
    if (!t || t.kind === "ignore") continue;
    for (let j = i + 1; j < props.length; j++) {
      if (sameTarget(t, props[j]!.target)) props[j] = { ...props[j]!, target: null, reason: "another row already matched this player" };
    }
  }
  return props;
}

/** True when a read can be applied without review: every row assigned and every icon confident. */
export function canAutoApply(read: ScreenRead, props: RowProposal[]): boolean {
  if (read.warnings.length > 0 || read.rows.length === 0) return false;
  return read.rows.every((r, i) => {
    const p = props[i];
    if (!p?.target) return false;
    if (p.target.kind === "ignore") return true;
    return r.items.every((it) => it.status === "confident");
  });
}

export interface ConfirmedRow {
  target: RowTarget;
  items: string[];
}

/**
 * Convert confirmed rows into match events. Rows for the same player are merged. `complete`
 * says whether the screen shows full inventories (scoreboard) or only some items.
 */
export function screenReadEvents(
  rows: ConfirmedRow[],
  opts: { observedAt: number; gameTime: number | null; complete: boolean; confidence: Confidence },
): MatchEvent[] {
  const merged = new Map<string, { target: RowTarget; items: string[] }>();
  for (const r of rows) {
    const t = r.target;
    if (t.kind === "ignore") continue;
    const key = t.kind === "me" ? "me" : `${t.kind}:${t.heroId}`;
    const cur = merged.get(key) ?? { target: t, items: [] };
    for (const it of r.items) if (!cur.items.includes(it)) cur.items.push(it);
    merged.set(key, cur);
  }
  const base = { type: "observe" as const, source: "screen" as const, observedAt: opts.observedAt, gameTime: opts.gameTime, confidence: opts.confidence };
  const events: MatchEvent[] = [];
  for (const { target, items } of merged.values()) {
    if (target.kind === "me") events.push({ ...base, field: "me.items", value: items });
    else if (target.kind === "enemy" || target.kind === "ally") {
      const value: EnemyItemsValue = { items, complete: opts.complete };
      events.push({ ...base, field: `${target.kind === "enemy" ? "enemyItems" : "allyItems"}:${target.heroId}`, value });
    }
  }
  return events;
}

/**
 * Build a layout from rectangles the user drew on a capture (all in pixels). With a portrait, the
 * orientation follows from where it sits relative to the sample icon: above or below means each
 * player is a column (Deadlock's Tab view), left or right means rows.
 */
export function layoutFromCalibration(
  img: { width: number; height: number },
  itemArea: Rect,
  sampleIcon: Rect,
  portrait: { box: Rect } | null,
  at: string,
  badges?: ScreenLayout["badges"],
): ScreenLayout {
  const frac = (r: Rect): FracRect => ({ x: r.x / img.width, y: r.y / img.height, w: r.w / img.width, h: r.h / img.height });
  const side = (r: Rect) => (r.w + r.h) / 2;
  let orientation: "rows" | "columns" = "rows";
  let p: ScreenLayout["portrait"] = null;
  if (portrait) {
    const b = portrait.box;
    const pcx = b.x + b.w / 2;
    const pcy = b.y + b.h / 2;
    const icx = sampleIcon.x + sampleIcon.w / 2;
    const icy = sampleIcon.y + sampleIcon.h / 2;
    orientation = Math.abs(pcy - icy) > Math.abs(pcx - icx) ? "columns" : "rows";
    p = {
      x: b.x / img.width,
      w: b.w / img.width,
      size: b.h / img.height,
      offsetY: orientation === "rows" ? (pcy - icy) / img.height : 0,
      ...(orientation === "columns" ? { centerY: pcy / img.height } : {}),
    };
  }
  return {
    version: 1,
    aspect: img.width / img.height,
    itemArea: frac(itemArea),
    iconSize: side(sampleIcon) / img.height,
    orientation,
    portrait: p,
    ...(badges ? { badges } : {}),
    calibratedAt: at,
  };
}

/**
 * Inspect the icon the user marked during calibration: which item it is, and whether this screen
 * draws tier badges (and in which hue for that item's slot).
 */
export function calibrationBadges(img: RgbaImage, sampleIcon: Rect, templates: TemplateSet): ScreenLayout["badges"] {
  const top = readBox(img, sampleIcon, templates.items, 2);
  const sure = !!top[0] && top[0].score >= 0.6 && top[0].score - (top[1]?.score ?? 0) >= 0.15;
  const slot = sure ? (templates.items.find((t) => t.className === top[0]!.className)?.slot ?? null) : null;
  const m = measureBadge(img, sampleIcon);
  return { present: m.present, hues: m.present && slot ? { [slot]: Math.round(m.hue) } : {} };
}
