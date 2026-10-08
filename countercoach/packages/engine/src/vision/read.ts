import { z } from "zod";
import type { Confidence } from "../types.js";
import type { EnemyItemsValue, MatchEvent } from "../state/types.js";
import { toPixels, type FracRect, type Rect, type RgbaImage } from "./image.js";
import { groupRows, readBox, scanIcons } from "./scan.js";
import type { Candidate, TemplateSet } from "./templates.js";

/**
 * Reads item icons (and optionally hero portraits) from a user-triggered capture of a screen the
 * player can already see, such as the Tab scoreboard. Nothing here touches the game: it only
 * looks at pixels. The layout comes from a one-time calibration on the user's own screenshot,
 * because the real scoreboard geometry has not been verified by this project.
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
  /** Optional hero-portrait column: one portrait per row, found at the row's height. */
  portrait: z
    .object({
      x: z.number().min(0).max(1),
      w: z.number().min(0).max(1),
      /** Portrait side as a fraction of the capture height. */
      size: z.number().positive().max(0.5),
      /** Portrait centre minus item-row centre, as a fraction of the capture height. */
      offsetY: z.number().min(-0.5).max(0.5),
    })
    .nullable(),
  calibratedAt: z.string(),
});
export type ScreenLayout = z.infer<typeof screenLayoutSchema>;

/** Thresholds (normalised cross-correlation). Tuned on synthetic renders; see scripts/vision-bench.ts. */
export const VISION_THRESHOLDS = {
  itemConfident: 0.8,
  itemMargin: 0.03,
  itemMin: 0.72,
  heroConfident: 0.75,
  heroMargin: 0.05,
  heroMin: 0.5,
} as const;

export type ReadStatus = "confident" | "uncertain";

export interface ScreenItemRead {
  box: Rect;
  candidates: Candidate[];
  status: ReadStatus;
}

export interface ScreenRowRead {
  index: number;
  box: Rect;
  /** Row centre as a fraction of the capture height (used to remember assignments). */
  yFrac: number;
  hero: { box: Rect; candidates: Candidate[]; status: ReadStatus | "none" } | null;
  items: ScreenItemRead[];
}

export interface ScreenRead {
  width: number;
  height: number;
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

/** Read one capture using a calibrated layout. */
export function readScreen(img: RgbaImage, layout: ScreenLayout, templates: TemplateSet, opts: ReadOptions = {}): ScreenRead {
  const t0 = Date.now();
  const warnings: string[] = [];
  const aspect = img.width / img.height;
  if (Math.abs(aspect - layout.aspect) / layout.aspect > 0.02) {
    warnings.push(
      `This capture is ${img.width}×${img.height} (aspect ${aspect.toFixed(2)}) but the layout was calibrated at aspect ${layout.aspect.toFixed(2)}. Recalibrate for this resolution.`,
    );
  }
  const T = VISION_THRESHOLDS;
  const itemTemplates = templates.items.filter((t) => !opts.allowItem || opts.allowItem(t.className));
  const iconPx = layout.iconSize * img.height;
  const dets = scanIcons(img, toPixels(layout.itemArea, img), itemTemplates, { iconPx, minScore: T.itemMin });
  // A real scoreboard row has at least one clearly readable icon; rows made only of weak matches
  // are usually panel decoration or patches between icons.
  const rows = groupRows(dets, iconPx).filter((r) =>
    r.members.some((m) => classify(dets[m]!.candidates, T.itemConfident, T.itemMargin) === "confident"),
  );
  const heroTemplates = templates.heroes.filter((t) => t.heroId != null && (!opts.allowHero || opts.allowHero(t.heroId)));
  const out: ScreenRowRead[] = rows.map((r, index) => {
    const items: ScreenItemRead[] = r.members.map((m) => {
      const d = dets[m]!;
      return { box: d.box, candidates: d.candidates, status: classify(d.candidates, T.itemConfident, T.itemMargin) };
    });
    let hero: ScreenRowRead["hero"] = null;
    if (layout.portrait && heroTemplates.length) hero = readPortrait(img, layout, r.cy, heroTemplates);
    return { index, box: r.box, yFrac: r.cy / img.height, hero, items };
  });
  if (dets.length === 0) warnings.push("No item icons were found in the calibrated area. Was the scoreboard open when the capture was taken?");
  return { width: img.width, height: img.height, rows: out, warnings, ms: Date.now() - t0 };
}

/** Find the portrait for a row: small search around the expected position. */
function readPortrait(img: RgbaImage, layout: ScreenLayout, rowCy: number, heroTemplates: TemplateSet["heroes"]): NonNullable<ScreenRowRead["hero"]> {
  const p = layout.portrait!;
  const size = p.size * img.height;
  const cy = rowCy + p.offsetY * img.height;
  const xCentre = (p.x + p.w / 2) * img.width;
  const jitter = size / 4;
  const step = Math.max(1, size / 8);
  let best: { box: Rect; candidates: Candidate[] } | null = null;
  for (let dy = -jitter; dy <= jitter + 1e-6; dy += step) {
    for (let dx = -jitter; dx <= jitter + 1e-6; dx += step) {
      const box = { x: xCentre - size / 2 + dx, y: cy - size / 2 + dy, w: size, h: size };
      const c = readBox(img, box, heroTemplates, 3);
      if (c[0] && (!best || c[0].score > best.candidates[0]!.score)) best = { box, candidates: c };
    }
  }
  const T = VISION_THRESHOLDS;
  if (!best || best.candidates[0]!.score < T.heroMin) return { box: best?.box ?? { x: xCentre - size / 2, y: cy - size / 2, w: size, h: size }, candidates: best?.candidates ?? [], status: "none" };
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
export function proposeTargets(read: ScreenRead, ctx: RosterContext, remembered: { yFrac: number; target: RowTarget }[] = []): RowProposal[] {
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
      .filter((m) => Math.abs(m.yFrac - r.yFrac) <= 0.015 && targetValid(m.target, ctx))
      .sort((a, b) => Math.abs(a.yFrac - r.yFrac) - Math.abs(b.yFrac - r.yFrac))[0];
    if (mem) return { row: r.index, target: mem.target, unknownHeroId: null, reason: "same row as your last confirmed read" };
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

/** Build a layout from rectangles the user drew on a capture (all in pixels). */
export function layoutFromCalibration(
  img: { width: number; height: number },
  itemArea: Rect,
  sampleIcon: Rect,
  portrait: { box: Rect; rowCy: number | null } | null,
  at: string,
): ScreenLayout {
  const frac = (r: Rect): FracRect => ({ x: r.x / img.width, y: r.y / img.height, w: r.w / img.width, h: r.h / img.height });
  const side = (r: Rect) => (r.w + r.h) / 2;
  const portraitSize = portrait ? side(portrait.box) : 0;
  return {
    version: 1,
    aspect: img.width / img.height,
    itemArea: frac(itemArea),
    iconSize: side(sampleIcon) / img.height,
    portrait: portrait
      ? {
          x: portrait.box.x / img.width,
          w: portrait.box.w / img.width,
          size: portraitSize / img.height,
          offsetY: portrait.rowCy == null ? 0 : (portrait.box.y + portrait.box.h / 2 - portrait.rowCy) / img.height,
        }
      : null,
    calibratedAt: at,
  };
}
