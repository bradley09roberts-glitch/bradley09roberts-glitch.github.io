import { deflateSync, inflateSync } from "node:zlib";
import type { RgbaImage } from "@countercoach/engine";

/**
 * Small PNG decoder for icon templates (8-bit greyscale/RGB/palette/grey+alpha/RGBA, 16-bit
 * reduced to 8-bit, non-interlaced). Avoids a native image dependency in the ingest tool.
 */

const SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];

export function decodePng(buf: Uint8Array): RgbaImage {
  for (let i = 0; i < 8; i++) if (buf[i] !== SIGNATURE[i]) throw new Error("not a PNG file");
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  let pos = 8;
  let width = 0;
  let height = 0;
  let depth = 0;
  let colour = 0;
  let interlace = 0;
  let palette: Uint8Array | null = null;
  let trns: Uint8Array | null = null;
  const idat: Uint8Array[] = [];
  while (pos + 8 <= buf.length) {
    const len = view.getUint32(pos);
    const type = String.fromCharCode(buf[pos + 4]!, buf[pos + 5]!, buf[pos + 6]!, buf[pos + 7]!);
    const body = buf.subarray(pos + 8, pos + 8 + len);
    if (type === "IHDR") {
      width = view.getUint32(pos + 8);
      height = view.getUint32(pos + 12);
      depth = body[8]!;
      colour = body[9]!;
      interlace = body[12]!;
    } else if (type === "PLTE") palette = body;
    else if (type === "tRNS") trns = body;
    else if (type === "IDAT") idat.push(body);
    else if (type === "IEND") break;
    pos += 12 + len;
  }
  if (!width || !height) throw new Error("PNG has no IHDR");
  if (interlace !== 0) throw new Error("interlaced PNG not supported");
  if (depth !== 8 && depth !== 16) throw new Error(`PNG bit depth ${depth} not supported`);
  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[colour];
  if (!channels) throw new Error(`PNG colour type ${colour} not supported`);
  if (colour === 3 && depth !== 8) throw new Error("palette PNG must be 8-bit");
  const bpp = channels * (depth / 8);
  const stride = width * bpp;
  const total = idat.reduce((s, b) => s + b.length, 0);
  const joined = new Uint8Array(total);
  let o = 0;
  for (const b of idat) {
    joined.set(b, o);
    o += b.length;
  }
  const raw = inflateSync(joined);
  if (raw.length < (stride + 1) * height) throw new Error("PNG data truncated");
  const px = new Uint8Array(stride * height);
  let prev = new Uint8Array(stride);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)]!;
    const line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    const cur = px.subarray(y * stride, (y + 1) * stride);
    for (let x = 0; x < stride; x++) {
      const a = x >= bpp ? cur[x - bpp]! : 0;
      const b = prev[x]!;
      const c = x >= bpp ? prev[x - bpp]! : 0;
      let v = line[x]!;
      if (filter === 1) v += a;
      else if (filter === 2) v += b;
      else if (filter === 3) v += (a + b) >> 1;
      else if (filter === 4) {
        const p = a + b - c;
        const pa = Math.abs(p - a);
        const pb = Math.abs(p - b);
        const pc = Math.abs(p - c);
        v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      } else if (filter !== 0) throw new Error(`bad PNG filter ${filter}`);
      cur[x] = v & 0xff;
    }
    prev = cur;
  }
  const out = new Uint8Array(width * height * 4);
  const step = depth / 8;
  const sample = (i: number) => px[i * step]!; // 16-bit: keep the high byte
  for (let i = 0; i < width * height; i++) {
    const s = i * channels;
    let r: number;
    let g: number;
    let b: number;
    let a = 255;
    if (colour === 0) {
      r = g = b = sample(s);
      if (trns && trns.length >= 2 && depth === 8 && r === trns[1]) a = 0;
    } else if (colour === 2) {
      r = sample(s);
      g = sample(s + 1);
      b = sample(s + 2);
    } else if (colour === 3) {
      const k = px[i]!;
      r = palette?.[k * 3] ?? 0;
      g = palette?.[k * 3 + 1] ?? 0;
      b = palette?.[k * 3 + 2] ?? 0;
      a = trns && k < trns.length ? trns[k]! : 255;
    } else if (colour === 4) {
      r = g = b = sample(s);
      a = sample(s + 1);
    } else {
      r = sample(s);
      g = sample(s + 1);
      b = sample(s + 2);
      a = sample(s + 3);
    }
    out[i * 4] = r;
    out[i * 4 + 1] = g;
    out[i * 4 + 2] = b;
    out[i * 4 + 3] = a;
  }
  return { width, height, data: out, order: "rgba" };
}

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();

function crc32(bytes: Uint8Array): number {
  let c = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) c = CRC_TABLE[(c ^ bytes[i]!) & 0xff]! ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

/** Encode an RGBA image as an 8-bit RGBA PNG (used for test fixtures and docs). */
export function encodePng(img: RgbaImage): Uint8Array {
  const { width, height } = img;
  const raw = new Uint8Array((width * 4 + 1) * height);
  const bgra = img.order === "bgra";
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0;
    for (let x = 0; x < width; x++) {
      const s = (y * width + x) * 4;
      const d = y * (width * 4 + 1) + 1 + x * 4;
      raw[d] = img.data[bgra ? s + 2 : s]!;
      raw[d + 1] = img.data[s + 1]!;
      raw[d + 2] = img.data[bgra ? s : s + 2]!;
      raw[d + 3] = img.data[s + 3]!;
    }
  }
  const chunk = (type: string, body: Uint8Array): Uint8Array => {
    const out = new Uint8Array(12 + body.length);
    const v = new DataView(out.buffer);
    v.setUint32(0, body.length);
    for (let i = 0; i < 4; i++) out[4 + i] = type.charCodeAt(i);
    out.set(body, 8);
    v.setUint32(8 + body.length, crc32(out.subarray(4, 8 + body.length)));
    return out;
  };
  const ihdr = new Uint8Array(13);
  const hv = new DataView(ihdr.buffer);
  hv.setUint32(0, width);
  hv.setUint32(4, height);
  ihdr.set([8, 6, 0, 0, 0], 8);
  const parts = [Uint8Array.from(SIGNATURE), chunk("IHDR", ihdr), chunk("IDAT", deflateSync(raw, { level: 9 })), chunk("IEND", new Uint8Array(0))];
  const total = parts.reduce((n, p) => n + p.length, 0);
  const out = new Uint8Array(total);
  let o = 0;
  for (const p of parts) {
    out.set(p, o);
    o += p.length;
  }
  return out;
}
