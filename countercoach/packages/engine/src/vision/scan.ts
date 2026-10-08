import { IntegralImage, clampRect, iou, thumbnail, type Rect, type RgbaImage } from "./image.js";
import { ICON_THUMB, MID, PREFILTER, PREFILTER_INSET, normaliseInPlace, opaqueVector, rankTemplates, scoreThumb, type Candidate, type PreparedTemplate } from "./templates.js";

/**
 * Sliding-window icon search at a known icon size. A coarse 4×4 colour pre-filter over a
 * summed-area table ranks every grid position; positions are then re-scored greedily with full
 * 16×16 templates (with small insets for frames and a 1 px hill climb) and overlapping hits are
 * suppressed.
 */

/** Fractions of the icon side trimmed from each edge when refining (frames around artwork). */
const INSETS = [0, 0.06, 0.12];
/** Coarse patches with less colour variation than this (0..1 scale) are skipped as background. */
const FLAT_STD = 0.02;

export interface IconDetection {
  box: Rect;
  /** Best candidates, highest first (distinct entities). */
  candidates: Candidate[];
}

export interface ScanOptions {
  /** Icon side in pixels. */
  iconPx: number;
  /** Grid step as a fraction of the icon side (default 1/8). */
  stepFrac?: number;
  /** Minimum pre-filter correlation for a position to be considered (default 0.72). */
  preMin?: number;
  /** Minimum full-template score for a detection to be kept (default 0.5). */
  minScore?: number;
  /** How many pre-filter templates to re-score per position (default 20). */
  shortlist?: number;
  /** Upper bound on positions re-scored (default 1500). */
  maxAttempts?: number;
  /** A detection at least this good blocks further attempts inside it (default 0.85). */
  strongScore?: number;
}

/** Search `area` for icons from `templates`. */
export function scanIcons(img: RgbaImage, area: Rect, templates: PreparedTemplate[], opts: ScanOptions): IconDetection[] {
  const s = opts.iconPx;
  if (!(s >= 8) || templates.length === 0) return [];
  const region = clampRect(area, img);
  if (region.w < s || region.h < s) return [];
  const step = Math.max(1, s * (opts.stepFrac ?? 1 / 8));
  const preMin = opts.preMin ?? 0.72;
  const minScore = opts.minScore ?? 0.5;
  const shortlist = opts.shortlist ?? 20;
  const ii = new IntegralImage(img, region);
  const cols = Math.floor((region.w - s) / step) + 1;
  const rows = Math.floor((region.h - s) / step) + 1;
  const inner = s * PREFILTER_INSET;
  const cell = (s - 2 * inner) / PREFILTER;
  const v = new Float32Array(PREFILTER * PREFILTER * 3);
  const dims = v.length;

  /** Coarse 4×4 vector at (x, y); null when the patch is nearly flat (empty panel). */
  const coarse = (x: number, y: number): Float32Array | null => {
    for (let cy = 0; cy < PREFILTER; cy++) {
      for (let cx = 0; cx < PREFILTER; cx++) ii.mean(x + inner + cx * cell, y + inner + cy * cell, cell, cell, v, (cy * PREFILTER + cx) * 3);
    }
    let mean = 0;
    for (let k = 0; k < dims; k++) mean += v[k]!;
    mean /= dims;
    let varSum = 0;
    for (let k = 0; k < dims; k++) varSum += (v[k]! - mean) ** 2;
    if (Math.sqrt(varSum / dims) < FLAT_STD) return null;
    return normaliseInPlace(v);
  };

  const shortlistAt = (x: number, y: number): { t: PreparedTemplate; d: number }[] => {
    const q = coarse(x, y);
    if (!q) return [];
    const scored: { t: PreparedTemplate; d: number }[] = [];
    for (const t of templates) {
      let d = 0;
      for (let k = 0; k < dims; k++) d += t.pre[k]! * q[k]!;
      scored.push({ t, d });
    }
    return scored.sort((a, b) => b.d - a.d).slice(0, shortlist);
  };

  const positions: { x: number; y: number; score: number }[] = [];
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      const x = region.x + c * step;
      const y = region.y + r * step;
      const q = coarse(x, y);
      if (!q) continue;
      let best = -1;
      for (const t of templates) {
        const p = t.pre;
        let d = 0;
        for (let k = 0; k < dims; k++) d += p[k]! * q[k]!;
        if (d > best) best = d;
      }
      if (best >= preMin) positions.push({ x, y, score: best });
    }
  }
  positions.sort((a, b) => b.score - a.score);

  /**
   * Pick the template and inset at one position using shift-tolerant 8×8 vectors (a 16×16
   * comparison drops sharply when the grid is off by a pixel or two on detailed artwork).
   */
  const bestAt = (x: number, y: number, short: PreparedTemplate[]) => {
    let best: { box: Rect; score: number; t: PreparedTemplate } | null = null;
    for (const inset of INSETS) {
      const m = s * inset;
      const box = { x: x + m, y: y + m, w: s - 2 * m, h: s - 2 * m };
      const q = opaqueVector(thumbnail(img, box, MID), MID);
      for (const t of short) {
        let d = 0;
        for (let k = 0; k < q.length; k++) d += t.mid[k]! * q[k]!;
        if (!best || d > best.score) best = { box, score: d, t };
      }
    }
    return best;
  };

  // Greedy: visit positions from the strongest coarse match down. A position is only skipped
  // when it sits inside an icon already read with a strong score, so a weak or wrong hit next to
  // a real icon cannot hide it (non-maximum suppression at the end settles any overlaps).
  const found: IconDetection[] = [];
  const strong: Rect[] = [];
  const strongScore = opts.strongScore ?? 0.85;
  const maxAttempts = opts.maxAttempts ?? 1500;
  let attempts = 0;
  for (const pos of positions) {
    if (attempts >= maxAttempts) break;
    const cx = pos.x + s / 2;
    const cy = pos.y + s / 2;
    const near = (b: Rect, r: number) => Math.abs(b.x + b.w / 2 - cx) < s * r && Math.abs(b.y + b.h / 2 - cy) < s * r;
    if (strong.some((b) => near(b, 0.45)) || found.some((d) => near(d.box, 0.2))) continue;
    attempts++;
    const short = shortlistAt(pos.x, pos.y).map((e) => e.t);
    let best = bestAt(pos.x, pos.y, short);
    // The 8×8 pick is a coarse gate; detailed art can score low here until the hill climb aligns it.
    if (!best || best.score < minScore - 0.27) continue;
    best = { ...best, score: scoreThumb(thumbnail(img, best.box, ICON_THUMB), best.t) };
    // Pixel-level hill climb on the best template: frames make the score sensitive to 1 px.
    for (let iter = 0; iter < 10; iter++) {
      const b: Rect = best.box;
      const moves: Rect[] = [
        { ...b, x: b.x - 1 },
        { ...b, x: b.x + 1 },
        { ...b, y: b.y - 1 },
        { ...b, y: b.y + 1 },
        { x: b.x + 1, y: b.y + 1, w: b.w - 2, h: b.h - 2 },
        { x: b.x - 1, y: b.y - 1, w: b.w + 2, h: b.h + 2 },
      ];
      let moved = false;
      for (const box of moves) {
        if (box.w < s * 0.74 || box.w > s * 1.04) continue;
        const sc = scoreThumb(thumbnail(img, box, ICON_THUMB), best.t);
        if (sc > best.score + 1e-4) {
          best = { box, score: sc, t: best.t };
          moved = true;
        }
      }
      if (!moved) break;
    }
    if (best.score < minScore) continue;
    // Final ranking against every template so the runner-up (and the margin) is meaningful.
    const candidates = rankTemplates(thumbnail(img, best.box, ICON_THUMB), templates, 3);
    const bx = best.box;
    if (found.some((d) => Math.abs(d.box.x - bx.x) < s * 0.15 && Math.abs(d.box.y - bx.y) < s * 0.15)) continue;
    found.push({ box: best.box, candidates });
    if (candidates[0]!.score >= strongScore) strong.push(best.box);
  }

  // Non-maximum suppression. Icons in a grid never overlap, so any real overlap is a conflict
  // (typically a patch straddling two icons) and the weaker detection goes.
  found.sort((a, b) => b.candidates[0]!.score - a.candidates[0]!.score);
  const kept: IconDetection[] = [];
  for (const d of found) if (!kept.some((k) => iou(k.box, d.box) > 0.08)) kept.push(d);
  return kept;
}

/** Re-score a single known box against all templates (used for portraits and corrections). */
export function readBox(img: RgbaImage, box: Rect, templates: PreparedTemplate[], limit = 3): Candidate[] {
  return rankTemplates(thumbnail(img, box, ICON_THUMB), templates, limit);
}

export interface IconRow {
  /** Vertical centre of the row (pixels). */
  cy: number;
  /** Indices into the detections array, left to right. */
  members: number[];
  box: Rect;
}

/** Group detections into horizontal rows (a scoreboard line per player). */
export function groupRows(dets: IconDetection[], iconPx: number): IconRow[] {
  const order = dets.map((_, i) => i).sort((a, b) => dets[a]!.box.y - dets[b]!.box.y);
  const rows: IconRow[] = [];
  for (const i of order) {
    const b = dets[i]!.box;
    const cy = b.y + b.h / 2;
    const row = rows.find((r) => Math.abs(r.cy - cy) <= iconPx * 0.5);
    if (row) {
      row.members.push(i);
      row.cy = row.members.reduce((s, m) => s + dets[m]!.box.y + dets[m]!.box.h / 2, 0) / row.members.length;
    } else rows.push({ cy, members: [i], box: b });
  }
  for (const r of rows) {
    r.members.sort((a, b) => dets[a]!.box.x - dets[b]!.box.x);
    const xs = r.members.map((m) => dets[m]!.box);
    const x0 = Math.min(...xs.map((b) => b.x));
    const y0 = Math.min(...xs.map((b) => b.y));
    const x1 = Math.max(...xs.map((b) => b.x + b.w));
    const y1 = Math.max(...xs.map((b) => b.y + b.h));
    r.box = { x: x0, y: y0, w: x1 - x0, h: y1 - y0 };
  }
  return rows.sort((a, b) => a.cy - b.cy);
}
