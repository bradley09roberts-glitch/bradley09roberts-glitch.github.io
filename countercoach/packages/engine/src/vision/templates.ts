import { z } from "zod";
import { canonicalJson, sha256Hex } from "../data/hash.js";
import type { Snapshot, SlotType } from "../types.js";
import { decodeBase64, encodeBase64, thumbnail, type RgbaImage } from "./image.js";

/**
 * Icon templates for local screen recognition, built from the community API's item art and hero
 * portraits (see `pnpm icons`). Each entry is a small RGBA thumbnail; matching is normalised
 * cross-correlation, so it tolerates brightness/contrast changes but not different artwork.
 */

/** Side of the stored thumbnails (pixels). */
export const ICON_THUMB = 16;
/** Side of the coarse thumbnails used by the sliding pre-filter. */
export const PREFILTER = 4;
/** Side of the shift-tolerant mid-resolution vectors used to pick a template before refining. */
export const MID = 8;

export const iconTemplateFileSchema = z.object({
  version: z.literal(1),
  thumb: z.literal(ICON_THUMB),
  generatedAt: z.string(),
  source: z.string(),
  contentHash: z.string(),
  items: z.array(
    z.object({
      className: z.string().min(1),
      slot: z.enum(["weapon", "vitality", "spirit"]),
      url: z.string(),
      rgba: z.string().min(1),
    }),
  ),
  heroes: z.array(
    z.object({
      id: z.number().int(),
      className: z.string().min(1),
      variant: z.string().min(1),
      url: z.string(),
      rgba: z.string().min(1),
    }),
  ),
});
export type IconTemplateFile = z.infer<typeof iconTemplateFileSchema>;

export interface PreparedTemplate {
  key: string;
  kind: "item" | "hero";
  /** Item className, or hero className. */
  className: string;
  heroId: number | null;
  slot: SlotType | null;
  variant: string | null;
  /** Alpha-weighted, mean-removed, unit-norm RGB (ICON_THUMB² × 3). */
  vec: Float32Array;
  /** Per-pixel alpha weights (ICON_THUMB²). */
  weight: Float32Array;
  wsum: number;
  /** Mean-removed, unit-norm PREFILTER² × 3 RGB for the coarse scan. */
  pre: Float32Array;
  /** Mean-removed, unit-norm MID² × 3 RGB (alpha-composited on mid grey). */
  mid: Float32Array;
}

export interface TemplateSet {
  items: PreparedTemplate[];
  heroes: PreparedTemplate[];
  contentHash: string;
  generatedAt: string;
}

/** Normalise an RGBA float thumbnail (n²×4, 0..1) into a prepared template. */
export function prepareTemplate(
  rgba: Float32Array,
  meta: Pick<PreparedTemplate, "key" | "kind" | "className" | "heroId" | "slot" | "variant">,
): PreparedTemplate {
  const n2 = ICON_THUMB * ICON_THUMB;
  const weight = new Float32Array(n2);
  let wsum = 0;
  let mean = 0;
  for (let i = 0; i < n2; i++) {
    const a = rgba[i * 4 + 3]!;
    weight[i] = a;
    wsum += a;
    mean += a * (rgba[i * 4]! + rgba[i * 4 + 1]! + rgba[i * 4 + 2]!);
  }
  mean = wsum > 0 ? mean / (3 * wsum) : 0;
  const vec = new Float32Array(n2 * 3);
  let norm = 0;
  for (let i = 0; i < n2; i++) {
    for (let c = 0; c < 3; c++) {
      const v = weight[i]! * (rgba[i * 4 + c]! - mean);
      vec[i * 3 + c] = v;
      norm += (rgba[i * 4 + c]! - mean) * v;
    }
  }
  norm = Math.sqrt(Math.max(norm, 1e-9));
  for (let i = 0; i < vec.length; i++) vec[i]! /= norm;
  return { ...meta, vec, weight, wsum, pre: prefilterVector(rgba), mid: midVector(rgba) };
}

/**
 * Fraction trimmed from each edge before the coarse pre-filter, so a frame drawn around the
 * artwork (or a pixel of misalignment) does not dominate the outer cells.
 */
export const PREFILTER_INSET = 0.1;

/** Coarse PREFILTER² × 3 vector of the inner region (alpha-composited on mid grey), mean-removed and unit-norm. */
export function prefilterVector(rgba: Float32Array): Float32Array {
  const bytes = new Uint8ClampedArray(rgba.length);
  for (let i = 0; i < rgba.length; i++) bytes[i] = Math.round(rgba[i]! * 255);
  const src = { width: ICON_THUMB, height: ICON_THUMB, data: bytes, order: "rgba" as const };
  const m = ICON_THUMB * PREFILTER_INSET;
  const th = thumbnail(src, { x: m, y: m, w: ICON_THUMB - 2 * m, h: ICON_THUMB - 2 * m }, PREFILTER);
  const out = new Float32Array(PREFILTER * PREFILTER * 3);
  for (let i = 0; i < PREFILTER * PREFILTER; i++) {
    const a = th[i * 4 + 3]!;
    for (let c = 0; c < 3; c++) out[i * 3 + c] = a * th[i * 4 + c]! + (1 - a) * 0.5;
  }
  return normaliseInPlace(out);
}

/** MID² × 3 vector of the whole thumbnail (alpha-composited on mid grey), mean-removed and unit-norm. */
export function midVector(rgba: Float32Array): Float32Array {
  const bytes = new Uint8ClampedArray(rgba.length);
  for (let i = 0; i < rgba.length; i++) bytes[i] = Math.round(rgba[i]! * 255);
  const th = thumbnail({ width: ICON_THUMB, height: ICON_THUMB, data: bytes, order: "rgba" }, { x: 0, y: 0, w: ICON_THUMB, h: ICON_THUMB }, MID);
  return opaqueVector(th, MID);
}

/** Composite an RGBA float thumbnail on mid grey and normalise it. */
export function opaqueVector(th: Float32Array, n: number): Float32Array {
  const out = new Float32Array(n * n * 3);
  for (let i = 0; i < n * n; i++) {
    const a = th[i * 4 + 3]!;
    for (let c = 0; c < 3; c++) out[i * 3 + c] = a * th[i * 4 + c]! + (1 - a) * 0.5;
  }
  return normaliseInPlace(out);
}

export function normaliseInPlace(v: Float32Array): Float32Array {
  let mean = 0;
  for (let i = 0; i < v.length; i++) mean += v[i]!;
  mean /= v.length;
  let norm = 0;
  for (let i = 0; i < v.length; i++) {
    v[i]! -= mean;
    norm += v[i]! * v[i]!;
  }
  norm = Math.sqrt(norm);
  if (norm < 1e-6) {
    v.fill(0);
    return v;
  }
  for (let i = 0; i < v.length; i++) v[i]! /= norm;
  return v;
}

function bytesToFloats(bytes: Uint8Array): Float32Array {
  const out = new Float32Array(bytes.length);
  for (let i = 0; i < bytes.length; i++) out[i] = bytes[i]! / 255;
  return out;
}

/** Validate and prepare a template file. Throws on a malformed file. */
export function loadTemplates(raw: unknown): TemplateSet {
  const f = iconTemplateFileSchema.parse(raw);
  const expect = ICON_THUMB * ICON_THUMB * 4;
  const decode = (s: string, what: string): Float32Array => {
    const b = decodeBase64(s);
    if (b.length !== expect) throw new Error(`icon template ${what}: expected ${expect} bytes, got ${b.length}`);
    return bytesToFloats(b);
  };
  return {
    items: f.items.map((t) =>
      prepareTemplate(decode(t.rgba, t.className), { key: `item:${t.className}`, kind: "item", className: t.className, heroId: null, slot: t.slot, variant: null }),
    ),
    heroes: f.heroes.map((t) =>
      prepareTemplate(decode(t.rgba, `${t.className}/${t.variant}`), {
        key: `hero:${t.id}:${t.variant}`,
        kind: "hero",
        className: t.className,
        heroId: t.id,
        slot: null,
        variant: t.variant,
      }),
    ),
    contentHash: f.contentHash,
    generatedAt: f.generatedAt,
  };
}

/**
 * Score an opaque screen thumbnail (n²×4 RGBA, 0..1) against a template: alpha-weighted
 * normalised cross-correlation in [-1, 1]. Brightness and contrast changes cancel out; colour
 * relationships between channels are kept because the mean is shared across channels.
 */
export function scoreThumb(x: Float32Array, t: PreparedTemplate): number {
  const n2 = ICON_THUMB * ICON_THUMB;
  let num = 0;
  let s1 = 0;
  let s2 = 0;
  for (let i = 0; i < n2; i++) {
    const r = x[i * 4]!;
    const g = x[i * 4 + 1]!;
    const b = x[i * 4 + 2]!;
    const w = t.weight[i]!;
    num += t.vec[i * 3]! * r + t.vec[i * 3 + 1]! * g + t.vec[i * 3 + 2]! * b;
    s1 += w * (r + g + b);
    s2 += w * (r * r + g * g + b * b);
  }
  const variance = s2 - (s1 * s1) / (3 * Math.max(t.wsum, 1e-9));
  if (variance < 1e-6) return 0;
  return num / Math.sqrt(variance);
}

export interface Candidate {
  key: string;
  className: string;
  heroId: number | null;
  score: number;
}

/**
 * Rank templates for a screen thumbnail. Variants of the same entity (e.g. hero portrait styles)
 * collapse to their best score so the runner-up is always a different entity.
 */
export function rankTemplates(x: Float32Array, templates: PreparedTemplate[], limit = 3): Candidate[] {
  const best = new Map<string, Candidate>();
  for (const t of templates) {
    const s = scoreThumb(x, t);
    const id = t.kind === "hero" ? `hero:${t.heroId}` : t.key;
    const cur = best.get(id);
    if (!cur || s > cur.score) best.set(id, { key: id, className: t.className, heroId: t.heroId, score: s });
  }
  return [...best.values()].sort((a, b) => b.score - a.score).slice(0, limit);
}

// ---------------------------------------------------------------------------------------------
// Building template files (shared by the `pnpm icons` CLI and the desktop app, which builds its
// own copy on the user's machine so no game art ships inside the installer).
// ---------------------------------------------------------------------------------------------

export interface TemplateRequest {
  kind: "item" | "hero";
  className: string;
  slot: SlotType | null;
  heroId: number | null;
  variant: string | null;
  url: string;
}

/** Hero portrait styles kept as templates (which one the scoreboard uses is not verified). */
export const HERO_PORTRAIT_VARIANTS = ["small", "vertical", "minimap", "card"] as const;

/**
 * Every image a template file needs for a snapshot: each item's art, and four portrait styles
 * per playable hero. `vertical` and `minimap` URLs follow the CDN's naming (`<hero>_sm.png` →
 * `_vertical.png` / `_mm.png`), checked against the API for all 40 heroes on build 6763; a URL
 * that fails to load is simply skipped.
 */
export function templateRequests(snapshot: Snapshot): TemplateRequest[] {
  const out: TemplateRequest[] = [];
  for (const it of snapshot.items) {
    if (it.image) out.push({ kind: "item", className: it.className, slot: it.slot, heroId: null, variant: null, url: it.image });
  }
  for (const h of snapshot.heroes.filter((x) => x.playable)) {
    const urls: Record<(typeof HERO_PORTRAIT_VARIANTS)[number], string | null> = {
      small: h.image,
      vertical: h.image && /_sm\.png$/.test(h.image) ? h.image.replace(/_sm\.png$/, "_vertical.png") : null,
      minimap: h.image && /_sm\.png$/.test(h.image) ? h.image.replace(/_sm\.png$/, "_mm.png") : null,
      card: h.cardImage,
    };
    for (const v of HERO_PORTRAIT_VARIANTS) {
      const url = urls[v];
      if (url) out.push({ kind: "hero", className: h.className, slot: null, heroId: h.id, variant: v, url });
    }
  }
  return out;
}

/** Thumbnail an image for a template entry (base64 of ICON_THUMB² RGBA bytes). */
export function encodeTemplateImage(img: RgbaImage): string {
  const th = thumbnail(img, { x: 0, y: 0, w: img.width, h: img.height }, ICON_THUMB);
  const b = new Uint8Array(th.length);
  for (let i = 0; i < th.length; i++) b[i] = Math.max(0, Math.min(255, Math.round(th[i]! * 255)));
  return encodeBase64(b);
}

/** Assemble and validate a template file from decoded images. */
export function buildTemplateFile(entries: { req: TemplateRequest; rgba: string }[], source: string, generatedAt: string): IconTemplateFile {
  const items = entries
    .filter((e) => e.req.kind === "item" && e.req.slot)
    .map((e) => ({ className: e.req.className, slot: e.req.slot!, url: e.req.url, rgba: e.rgba }));
  const heroes = entries
    .filter((e) => e.req.kind === "hero" && e.req.heroId != null)
    .map((e) => ({ id: e.req.heroId!, className: e.req.className, variant: e.req.variant ?? "small", url: e.req.url, rgba: e.rgba }));
  const body = { items, heroes };
  return iconTemplateFileSchema.parse({ version: 1, thumb: ICON_THUMB, generatedAt, source, contentHash: sha256Hex(canonicalJson(body)), ...body });
}

/** Convert premultiplied-alpha pixels (e.g. Electron NativeImage bitmaps) to straight alpha in place. */
export function unpremultiply(data: Uint8Array | Uint8ClampedArray): void {
  for (let i = 0; i < data.length; i += 4) {
    const a = data[i + 3]!;
    if (a === 0 || a === 255) continue;
    data[i] = Math.min(255, Math.round((data[i]! * 255) / a));
    data[i + 1] = Math.min(255, Math.round((data[i + 1]! * 255) / a));
    data[i + 2] = Math.min(255, Math.round((data[i + 2]! * 255) / a));
  }
}
