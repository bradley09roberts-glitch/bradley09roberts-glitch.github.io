/**
 * Synthetic accuracy benchmark for screen item recognition.
 *
 *   pnpm vision:bench [-- --trials 6 --seed 1]
 *
 * Renders fake scoreboards from the ORIGINAL full-resolution item art and hero portraits (cached
 * by `pnpm icons`), adds the HUD's tier badge to each icon, degrades them (resize, frame, rounded
 * corners, brightness/contrast, blur, noise, colour quantisation) and runs the real reader on them. This measures how well the
 * templates separate the 173 items at small sizes. It is NOT evidence about the real game's
 * scoreboard, whose layout and styling have not been verified.
 */
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  calibrationBadges,
  loadTemplates,
  readScreen,
  thumbnail,
  type RgbaImage,
  type ScreenLayout,
  type Snapshot,
} from "../packages/engine/src/index.js";
import { fetchBinary } from "../packages/ingest/src/http.js";
import { decodePng } from "../packages/ingest/src/png.js";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const arg = (k: string, d: number) => {
  const i = process.argv.indexOf(k);
  return i >= 0 ? Number(process.argv[i + 1]) : d;
};
const TRIALS = arg("--trials", 6);
let seed = arg("--seed", 1);
const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
const pick = <T>(a: T[]) => a[Math.floor(rnd() * a.length)]!;

async function main(): Promise<void> {
  const manifest = JSON.parse(await readFile(path.join(root, "data/snapshots/manifest.json"), "utf8"));
  const snap = JSON.parse(await readFile(path.join(root, "data/snapshots", manifest.active.file), "utf8")) as Snapshot;
  const iconsRaw = JSON.parse(await readFile(path.join(root, "fixtures/vision/icons.json"), "utf8"));
  const templates = loadTemplates(iconsRaw);
  const fo = { cacheDir: path.join(root, "data/cache/img"), maxAgeMs: 0, offline: true, retries: 0, timeoutMs: 1, log: () => {} };
  const art = new Map<string, RgbaImage>();
  for (const it of iconsRaw.items as { className: string; url: string }[]) art.set(it.className, decodePng((await fetchBinary(it.url, fo)).data));
  const portraits = new Map<number, RgbaImage>();
  for (const h of iconsRaw.heroes as { id: number; variant: string; url: string }[]) {
    if (h.variant === "small") portraits.set(h.id, decodePng((await fetchBinary(h.url, fo)).data));
  }
  const shop = snap.items.filter((i) => i.tier <= 4).map((i) => i.className).filter((c) => art.has(c));
  const heroIds = [...portraits.keys()];

  const totals = { icons: 0, found: 0, top1: 0, confident: 0, confidentWrong: 0, uncertain: 0, uncertainTop3: 0, falsePos: 0, heroes: 0, heroTop1: 0, heroConfident: 0, heroConfidentWrong: 0, ms: 0 };
  const confusions = new Map<string, number>();

  for (let t = 0; t < TRIALS; t++) {
    const W = 1920;
    const H = 1080;
    const s = Math.round(22 + rnd() * 22); // icon side 22..44 px
    const gap = Math.round(s * (0.08 + rnd() * 0.15));
    const img: RgbaImage = { width: W, height: H, data: new Uint8ClampedArray(W * H * 4), order: "rgba" };
    const bg = [18 + rnd() * 20, 20 + rnd() * 20, 26 + rnd() * 20];
    for (let i = 0; i < W * H; i++) {
      img.data[i * 4] = bg[0]! + rnd() * 8;
      img.data[i * 4 + 1] = bg[1]! + rnd() * 8;
      img.data[i * 4 + 2] = bg[2]! + rnd() * 8;
      img.data[i * 4 + 3] = 255;
    }
    const frame = rnd() < 0.5 ? Math.max(1, Math.round(s * 0.06)) : 0;
    const rounded = rnd() < 0.5;
    const bright = 0.8 + rnd() * 0.35;
    const contrast = 0.85 + rnd() * 0.3;
    const portraitSize = Math.round(s * (1.2 + rnd() * 0.6));
    const x0 = 300;
    const y0 = 120;
    const rowPitch = Math.max(portraitSize, s) + Math.round(s * 0.5);
    const truth: { x: number; y: number; cls: string }[] = [];
    const heroTruth: number[] = [];
    const usedHeroes = new Set<number>();
    for (let r = 0; r < 12; r++) {
      const ry = y0 + r * rowPitch;
      if (ry + rowPitch > H) break;
      let hid = pick(heroIds);
      while (usedHeroes.has(hid)) hid = pick(heroIds);
      usedHeroes.add(hid);
      heroTruth.push(hid);
      blit(img, portraits.get(hid)!, x0 - portraitSize - s, ry + (s - portraitSize) / 2, portraitSize, { frame: 0, rounded: false, bright, contrast });
      const n = 3 + Math.floor(rnd() * 10);
      const used = new Set<string>();
      for (let k = 0; k < n; k++) {
        let cls = pick(shop);
        while (used.has(cls)) cls = pick(shop);
        used.add(cls);
        const x = x0 + k * (s + gap) + (k >= 4 ? s * 0.6 : 0);
        blit(img, art.get(cls)!, x, ry, s, { frame, rounded, bright, contrast });
        badge(img, Math.round(x), ry, s, snap.items.find((i) => i.className === cls)!.slot);
        truth.push({ x, y: ry, cls });
      }
    }
    degrade(img);
    const layout: ScreenLayout = {
      version: 1,
      aspect: W / H,
      itemArea: { x: (x0 - s * 0.5) / W, y: (y0 - s * 0.5) / H, w: (16 * (s + gap)) / W, h: (12 * rowPitch + s) / H },
      iconSize: s / H,
      orientation: "rows",
      portrait: { x: (x0 - portraitSize - s) / W, w: portraitSize / W, size: portraitSize / H, offsetY: 0 },
      calibratedAt: "bench",
    };
    const t0 = truth[0]!;
    layout.badges = calibrationBadges(img, { x: t0.x, y: t0.y, w: s, h: s }, templates);
    const read = readScreen(img, layout, templates, { allowItem: (c) => shop.includes(c) });
    totals.ms += read.ms;
    const dets = read.rows.flatMap((r) => r.items);
    const matched = new Set<number>();
    for (const g of truth) {
      totals.icons++;
      const di = dets.findIndex((d, i) => !matched.has(i) && Math.abs(d.box.x - g.x) < s * 0.4 && Math.abs(d.box.y - g.y) < s * 0.4);
      if (di < 0) continue;
      matched.add(di);
      const d = dets[di]!;
      totals.found++;
      const top = d.candidates[0]!.className;
      if (top === g.cls) totals.top1++;
      else confusions.set(`${g.cls} -> ${top}`, (confusions.get(`${g.cls} -> ${top}`) ?? 0) + 1);
      if (d.status === "confident") {
        totals.confident++;
        if (top !== g.cls) totals.confidentWrong++;
      } else {
        totals.uncertain++;
        if (d.candidates.some((c) => c.className === g.cls)) totals.uncertainTop3++;
      }
    }
    totals.falsePos += dets.length - matched.size;
    read.rows.forEach((r) => {
      const ri = Math.round((r.box.y - y0) / rowPitch);
      const want = Math.abs(r.box.y - (y0 + ri * rowPitch)) < s * 0.5 ? heroTruth[ri] : undefined;
      if (want == null || !r.hero) return;
      totals.heroes++;
      if (r.hero.candidates[0]?.heroId === want) totals.heroTop1++;
      if (r.hero.status === "confident") {
        totals.heroConfident++;
        if (r.hero.candidates[0]?.heroId !== want) totals.heroConfidentWrong++;
      }
    });
    console.log(`trial ${t + 1}: icon ${s}px frame=${frame} rounded=${rounded} → ${dets.length} detections, ${read.rows.length} rows, ${read.ms} ms`);
  }
  const pct = (a: number, b: number) => (b ? `${((100 * a) / b).toFixed(1)}%` : "n/a");
  console.log("\n=== Synthetic benchmark (not the real game) ===");
  console.log(`icons rendered        ${totals.icons}`);
  console.log(`detected              ${totals.found} (${pct(totals.found, totals.icons)})`);
  console.log(`top-1 correct         ${totals.top1} (${pct(totals.top1, totals.found)} of detected)`);
  console.log(`confident             ${totals.confident} (${pct(totals.confident, totals.found)}); wrong while confident: ${totals.confidentWrong} (${pct(totals.confidentWrong, totals.confident)})`);
  console.log(`uncertain             ${totals.uncertain}; true item in top 3: ${pct(totals.uncertainTop3, totals.uncertain)}`);
  console.log(`false detections      ${totals.falsePos}`);
  console.log(`portraits             top-1 ${pct(totals.heroTop1, totals.heroes)}; confident ${pct(totals.heroConfident, totals.heroes)}; wrong while confident ${totals.heroConfidentWrong}`);
  console.log(`mean read time        ${(totals.ms / TRIALS).toFixed(0)} ms (1920×1080)`);
  const worst = [...confusions.entries()].sort((a, b) => b[1] - a[1]).slice(0, 8);
  if (worst.length) console.log(`most common confusions:\n  ${worst.map(([k, v]) => `${v}× ${k}`).join("\n  ")}`);
}

/** The HUD's tier badge: a triangle in the icon's top-right corner (vitality colour measured on a real capture; others assumed). */
function badge(img: RgbaImage, x: number, y: number, n: number, slot: string): void {
  const c = ({ vitality: [174, 210, 71], weapon: [230, 150, 50], spirit: [175, 120, 235] } as Record<string, number[]>)[slot]!;
  for (let j = 0; j < n; j++) {
    for (let i = 0; i < n; i++) {
      if ((i + 0.5) / n - (j + 0.5) / n <= 0.65) continue;
      const p = ((y + j) * img.width + (x + i)) * 4;
      img.data[p] = c[0]!;
      img.data[p + 1] = c[1]!;
      img.data[p + 2] = c[2]!;
    }
  }
}

/** Draw `src` scaled to size×size at (x, y) with optional frame and rounded corners. */
function blit(img: RgbaImage, src: RgbaImage, x: number, y: number, size: number, o: { frame: number; rounded: boolean; bright: number; contrast: number }): void {
  const ix = Math.round(x);
  const iy = Math.round(y);
  const n = Math.round(size);
  const inner = n - 2 * o.frame;
  const th = thumbnail(src, { x: 0, y: 0, w: src.width, h: src.height }, inner);
  const radius = o.rounded ? n * 0.15 : 0;
  for (let j = 0; j < n; j++) {
    for (let i = 0; i < n; i++) {
      const px = ix + i;
      const py = iy + j;
      if (px < 0 || py < 0 || px >= img.width || py >= img.height) continue;
      if (radius > 0) {
        const cx = Math.min(i, n - 1 - i);
        const cy = Math.min(j, n - 1 - j);
        if (cx < radius && cy < radius && (radius - cx) ** 2 + (radius - cy) ** 2 > radius * radius) continue;
      }
      const p = (py * img.width + px) * 4;
      const fi = i - o.frame;
      const fj = j - o.frame;
      if (fi < 0 || fj < 0 || fi >= inner || fj >= inner) {
        img.data[p] = 90;
        img.data[p + 1] = 84;
        img.data[p + 2] = 70;
        continue;
      }
      const q = (fj * inner + fi) * 4;
      const a = th[q + 3]!;
      for (let c = 0; c < 3; c++) {
        const v = ((th[q + c]! - 0.5) * o.contrast + 0.5) * 255 * o.bright;
        img.data[p + c] = a * v + (1 - a) * img.data[p + c]!;
      }
    }
  }
}

/** 2×2 blur at 40%, gaussian-ish noise and 6-bit colour quantisation. */
function degrade(img: RgbaImage): void {
  const d = img.data;
  const W = img.width;
  const copy = Uint8ClampedArray.from(d);
  for (let y = 0; y < img.height - 1; y++) {
    for (let x = 0; x < W - 1; x++) {
      const p = (y * W + x) * 4;
      for (let c = 0; c < 3; c++) {
        const avg = (copy[p + c]! + copy[p + 4 + c]! + copy[p + W * 4 + c]! + copy[p + W * 4 + 4 + c]!) / 4;
        const noise = (rnd() + rnd() + rnd() - 1.5) * 10;
        const v = 0.6 * copy[p + c]! + 0.4 * avg + noise;
        d[p + c] = Math.round(v / 4) * 4;
      }
    }
  }
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
