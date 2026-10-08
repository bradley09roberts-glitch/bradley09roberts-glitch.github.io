/**
 * Minimal raster helpers for local screen recognition. Pure TypeScript with no platform APIs so
 * the same code runs in the Electron renderer, a browser preview and Node tests.
 */

export interface RgbaImage {
  width: number;
  height: number;
  /** 4 bytes per pixel, row-major, no padding. */
  data: Uint8Array | Uint8ClampedArray;
  /** Channel order. Electron's NativeImage.toBitmap() is BGRA; canvas ImageData is RGBA. */
  order?: "rgba" | "bgra";
}

/** Axis-aligned rectangle in pixels (fractional values allowed). */
export interface Rect {
  x: number;
  y: number;
  w: number;
  h: number;
}

/** Rectangle expressed as fractions of the image width/height (resolution independent). */
export type FracRect = Rect;

export function toPixels(r: FracRect, img: { width: number; height: number }): Rect {
  return { x: r.x * img.width, y: r.y * img.height, w: r.w * img.width, h: r.h * img.height };
}

export function toFractions(r: Rect, img: { width: number; height: number }): FracRect {
  return { x: r.x / img.width, y: r.y / img.height, w: r.w / img.width, h: r.h / img.height };
}

export function clampRect(r: Rect, img: { width: number; height: number }): Rect {
  const x0 = Math.max(0, Math.min(img.width, r.x));
  const y0 = Math.max(0, Math.min(img.height, r.y));
  const x1 = Math.max(x0, Math.min(img.width, r.x + r.w));
  const y1 = Math.max(y0, Math.min(img.height, r.y + r.h));
  return { x: x0, y: y0, w: x1 - x0, h: y1 - y0 };
}

export function iou(a: Rect, b: Rect): number {
  const x0 = Math.max(a.x, b.x);
  const y0 = Math.max(a.y, b.y);
  const x1 = Math.min(a.x + a.w, b.x + b.w);
  const y1 = Math.min(a.y + a.h, b.y + b.h);
  const inter = Math.max(0, x1 - x0) * Math.max(0, y1 - y0);
  const union = a.w * a.h + b.w * b.h - inter;
  return union > 0 ? inter / union : 0;
}

/** Copy a region into a new RGBA image (used for thumbnails shown in the review UI). */
export function cropImage(img: RgbaImage, r: Rect): RgbaImage {
  const c = clampRect({ x: Math.floor(r.x), y: Math.floor(r.y), w: Math.ceil(r.w), h: Math.ceil(r.h) }, img);
  const out = new Uint8ClampedArray(c.w * c.h * 4);
  const bgra = img.order === "bgra";
  for (let y = 0; y < c.h; y++) {
    for (let x = 0; x < c.w; x++) {
      const s = ((c.y + y) * img.width + (c.x + x)) * 4;
      const d = (y * c.w + x) * 4;
      out[d] = img.data[bgra ? s + 2 : s]!;
      out[d + 1] = img.data[s + 1]!;
      out[d + 2] = img.data[bgra ? s : s + 2]!;
      out[d + 3] = img.data[s + 3]!;
    }
  }
  return { width: c.w, height: c.h, data: out, order: "rgba" };
}

/** Per-axis area weights: for each output cell, the source indices and coverage it averages. */
function axisWeights(start: number, length: number, n: number, limit: number): { idx: number[]; w: number[] }[] {
  const cells: { idx: number[]; w: number[] }[] = [];
  const step = length / n;
  for (let k = 0; k < n; k++) {
    const a = start + k * step;
    const b = a + step;
    const idx: number[] = [];
    const w: number[] = [];
    if (step < 1) {
      // Upsampling: sample the source pixel under the cell centre.
      const c = Math.min(limit - 1, Math.max(0, Math.floor((a + b) / 2)));
      idx.push(c);
      w.push(1);
    } else {
      for (let p = Math.floor(a); p < Math.ceil(b); p++) {
        if (p < 0 || p >= limit) continue;
        const cover = Math.min(b, p + 1) - Math.max(a, p);
        if (cover > 0) {
          idx.push(p);
          w.push(cover);
        }
      }
    }
    cells.push({ idx, w });
  }
  return cells;
}

/**
 * Area-averaged n×n thumbnail of `r` (pixels). Returns n*n*4 floats in 0..1 as RGBA. Colour is
 * alpha-weighted so transparent edges do not darken the average. Regions outside the image
 * count as transparent.
 */
export function thumbnail(img: RgbaImage, r: Rect, n: number): Float32Array {
  const xs = axisWeights(r.x, r.w, n, img.width);
  const ys = axisWeights(r.y, r.h, n, img.height);
  const out = new Float32Array(n * n * 4);
  const bgra = img.order === "bgra";
  const d = img.data;
  // Total weight a fully covered, opaque cell would have; source pixels outside the image are
  // skipped, so cells hanging off the edge come out partly transparent.
  const expected = Math.max(1, r.w / n) * Math.max(1, r.h / n);
  for (let j = 0; j < n; j++) {
    const yc = ys[j]!;
    for (let i = 0; i < n; i++) {
      const xc = xs[i]!;
      let sr = 0;
      let sg = 0;
      let sb = 0;
      let sa = 0;
      for (let yy = 0; yy < yc.idx.length; yy++) {
        const row = yc.idx[yy]! * img.width;
        const wy = yc.w[yy]!;
        for (let xx = 0; xx < xc.idx.length; xx++) {
          const w = wy * xc.w[xx]!;
          const p = (row + xc.idx[xx]!) * 4;
          const wa = (w * d[p + 3]!) / 255;
          sr += wa * d[bgra ? p + 2 : p]!;
          sg += wa * d[p + 1]!;
          sb += wa * d[bgra ? p : p + 2]!;
          sa += wa;
        }
      }
      const o = (j * n + i) * 4;
      if (sa > 0) {
        out[o] = sr / sa / 255;
        out[o + 1] = sg / sa / 255;
        out[o + 2] = sb / sa / 255;
      }
      out[o + 3] = Math.min(1, sa / expected);
    }
  }
  return out;
}

/**
 * Summed-area tables for fast box means of R, G and B over a sub-region. Used by the sliding
 * scan so each candidate position costs O(cells) instead of O(pixels).
 */
export class IntegralImage {
  readonly x0: number;
  readonly y0: number;
  readonly w: number;
  readonly h: number;
  private readonly sums: Float64Array[];

  constructor(img: RgbaImage, region: Rect) {
    const c = clampRect({ x: Math.floor(region.x), y: Math.floor(region.y), w: Math.ceil(region.w), h: Math.ceil(region.h) }, img);
    this.x0 = c.x;
    this.y0 = c.y;
    this.w = c.w;
    this.h = c.h;
    const W = c.w + 1;
    this.sums = [new Float64Array(W * (c.h + 1)), new Float64Array(W * (c.h + 1)), new Float64Array(W * (c.h + 1))];
    const bgra = img.order === "bgra";
    const [R, G, B] = this.sums as [Float64Array, Float64Array, Float64Array];
    for (let y = 0; y < c.h; y++) {
      let rr = 0;
      let rg = 0;
      let rb = 0;
      const src = (c.y + y) * img.width + c.x;
      for (let x = 0; x < c.w; x++) {
        const p = (src + x) * 4;
        rr += img.data[bgra ? p + 2 : p]!;
        rg += img.data[p + 1]!;
        rb += img.data[bgra ? p : p + 2]!;
        const o = (y + 1) * W + (x + 1);
        R[o] = R[o - W]! + rr;
        G[o] = G[o - W]! + rg;
        B[o] = B[o - W]! + rb;
      }
    }
  }

  /** Mean RGB (0..1) of the box [x, x+w) × [y, y+h) in image pixels (rounded to whole pixels). */
  mean(x: number, y: number, w: number, h: number, out: Float32Array, at: number): void {
    const W = this.w + 1;
    const ax = Math.max(0, Math.min(this.w, Math.round(x - this.x0)));
    const ay = Math.max(0, Math.min(this.h, Math.round(y - this.y0)));
    const bx = Math.max(ax + 1, Math.min(this.w, Math.round(x + w - this.x0)));
    const by = Math.max(ay + 1, Math.min(this.h, Math.round(y + h - this.y0)));
    const area = (bx - ax) * (by - ay) * 255;
    for (let c = 0; c < 3; c++) {
      const s = this.sums[c]!;
      out[at + c] = (s[by * W + bx]! - s[ay * W + bx]! - s[by * W + ax]! + s[ay * W + ax]!) / area;
    }
  }
}

/** Base64 → bytes without platform globals (atob/Buffer). */
export function decodeBase64(s: string): Uint8Array {
  const map = new Int16Array(128).fill(-1);
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  for (let i = 0; i < alphabet.length; i++) map[alphabet.charCodeAt(i)] = i;
  const clean = s.replace(/[^A-Za-z0-9+/]/g, "");
  const out = new Uint8Array(Math.floor((clean.length * 3) / 4));
  let buf = 0;
  let bits = 0;
  let o = 0;
  for (let i = 0; i < clean.length; i++) {
    buf = (buf << 6) | map[clean.charCodeAt(i)]!;
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      out[o++] = (buf >> bits) & 0xff;
    }
  }
  return out.subarray(0, o);
}

export function encodeBase64(bytes: Uint8Array): string {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  let out = "";
  for (let i = 0; i < bytes.length; i += 3) {
    const a = bytes[i]!;
    const b = bytes[i + 1];
    const c = bytes[i + 2];
    const n = (a << 16) | ((b ?? 0) << 8) | (c ?? 0);
    out += alphabet[(n >> 18) & 63]! + alphabet[(n >> 12) & 63]!;
    out += b === undefined ? "=" : alphabet[(n >> 6) & 63]!;
    out += c === undefined ? "=" : alphabet[n & 63]!;
  }
  return out;
}
