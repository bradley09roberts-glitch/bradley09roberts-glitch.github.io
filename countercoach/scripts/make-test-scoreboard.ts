/**
 * Render a SYNTHETIC scoreboard image for end-to-end tests and documentation.
 *
 *   pnpm tsx scripts/make-test-scoreboard.ts [outDir]
 *
 * It is built from the community API's item art and hero portraits (cached by `pnpm icons`) in a
 * made-up layout: 12 player rows, a portrait, then item icons grouped by category with a thin
 * frame. It is NOT a Deadlock screenshot and says nothing about the real scoreboard's layout.
 * Writes synthetic-scoreboard.png and synthetic-scoreboard.json (ground truth + geometry).
 */
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { thumbnail, type RgbaImage, type Snapshot } from "../packages/engine/src/index.js";
import { fetchBinary } from "../packages/ingest/src/http.js";
import { decodePng, encodePng } from "../packages/ingest/src/png.js";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const outDir = path.resolve(process.argv[2] ?? path.join(root, "docs/screenshots"));

// Roster of the "Abrams mid-game (demo)" scenario so the read lines up with it.
const ME = 6;
const ALLIES = [3, 8, 12, 17, 20];
const ENEMIES = [13, 2, 4, 1, 11, 7];
const BUILDS: Record<number, string[]> = {
  6: ["upgrade_close_range", "upgrade_endurance", "upgrade_lifestrike_gauntlets", "upgrade_melee_charge", "upgrade_improved_stamina"],
  3: ["upgrade_improved_spirit", "upgrade_magic_burst", "upgrade_health", "upgrade_magic_reach"],
  8: ["upgrade_headshot_booster", "upgrade_rapid_rounds", "upgrade_endurance", "upgrade_sprint_booster", "upgrade_quick_silver"],
  12: ["upgrade_extra_charge", "upgrade_improved_spirit", "upgrade_health", "upgrade_magic_tempo"],
  17: ["upgrade_close_range", "upgrade_health", "upgrade_melee_charge", "upgrade_debuff_reducer"],
  20: ["upgrade_improved_spirit", "upgrade_magic_reach", "upgrade_endurance"],
  13: ["upgrade_headshot_booster", "upgrade_rapid_rounds", "upgrade_ricochet", "upgrade_toxic_bullets", "upgrade_improved_stamina", "upgrade_sprint_booster"],
  2: ["upgrade_improved_spirit", "upgrade_magic_burst", "upgrade_magic_reach", "upgrade_health", "upgrade_magic_tempo"],
  4: ["upgrade_health_stealing_magic", "upgrade_resonant_healing", "upgrade_improved_spirit", "upgrade_health"],
  1: ["upgrade_rapid_rounds", "upgrade_improved_spirit", "upgrade_toxic_bullets", "upgrade_endurance"],
  11: ["upgrade_improved_spirit", "upgrade_health", "upgrade_magic_tempo", "upgrade_debuff_reducer"],
  7: ["upgrade_headshot_booster", "upgrade_magic_burst", "upgrade_improved_spirit", "upgrade_sprint_booster", "upgrade_extra_charge"],
};
const FRAME: Record<string, [number, number, number]> = { weapon: [196, 128, 58], vitality: [88, 156, 78], spirit: [146, 98, 196] };

async function main(): Promise<void> {
  const manifest = JSON.parse(await readFile(path.join(root, "data/snapshots/manifest.json"), "utf8"));
  const snap = JSON.parse(await readFile(path.join(root, "data/snapshots", manifest.active.file), "utf8")) as Snapshot;
  const icons = JSON.parse(await readFile(path.join(root, "fixtures/vision/icons.json"), "utf8"));
  const fo = { cacheDir: path.join(root, "data/cache/img"), maxAgeMs: 0, offline: false, retries: 2, timeoutMs: 30_000, log: () => {} };
  const art = async (url: string) => decodePng((await fetchBinary(url, fo)).data);
  const itemUrl = new Map((icons.items as { className: string; url: string }[]).map((i) => [i.className, i.url]));
  const heroUrl = new Map((icons.heroes as { id: number; variant: string; url: string }[]).filter((h) => h.variant === "small").map((h) => [h.id, h.url]));

  const W = 1920;
  const H = 1080;
  const img: RgbaImage = { width: W, height: H, data: new Uint8ClampedArray(W * H * 4), order: "rgba" };
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const p = (y * W + x) * 4;
      // Muted "game world" gradient behind a dark panel.
      img.data[p] = 40 + (60 * x) / W;
      img.data[p + 1] = 52 + (30 * y) / H;
      img.data[p + 2] = 60;
      img.data[p + 3] = 255;
    }
  }
  const fill = (x: number, y: number, w: number, h: number, c: [number, number, number], a = 1) => {
    for (let j = Math.max(0, y); j < Math.min(H, y + h); j++) {
      for (let i = Math.max(0, x); i < Math.min(W, x + w); i++) {
        const p = (j * W + i) * 4;
        for (let k = 0; k < 3; k++) img.data[p + k] = a * c[k]! + (1 - a) * img.data[p + k]!;
      }
    }
  };
  const blit = (src: RgbaImage, x: number, y: number, n: number) => {
    const th = thumbnail(src, { x: 0, y: 0, w: src.width, h: src.height }, n);
    for (let j = 0; j < n; j++) {
      for (let i = 0; i < n; i++) {
        const q = (j * n + i) * 4;
        const p = ((y + j) * W + (x + i)) * 4;
        const a = th[q + 3]!;
        for (let k = 0; k < 3; k++) img.data[p + k] = a * th[q + k]! * 255 + (1 - a) * img.data[p + k]!;
      }
    }
  };

  const panel = { x: 330, y: 120, w: 1260, h: 840 };
  fill(panel.x, panel.y, panel.w, panel.h, [16, 19, 24], 0.92);
  const icon = 38;
  const frame = 2;
  const pitch = 60;
  const portrait = 46;
  const portraitX = 370;
  const itemsX = 470;
  const rows: { heroId: number; team: "ally" | "enemy" | "me"; y: number; items: { className: string; x: number; y: number }[] }[] = [];
  const order = [
    { id: ME, team: "me" as const },
    ...ALLIES.map((id) => ({ id, team: "ally" as const })),
    ...ENEMIES.map((id) => ({ id, team: "enemy" as const })),
  ];
  for (let r = 0; r < order.length; r++) {
    const { id, team } = order[r]!;
    const y = panel.y + 40 + r * pitch + (r >= 6 ? 50 : 0);
    fill(panel.x + 10, y - 8, panel.w - 20, pitch - 6, team === "enemy" ? [40, 22, 24] : [22, 32, 40], 0.7);
    blit(await art(heroUrl.get(id)!), portraitX, y + Math.round((icon - portrait) / 2), portrait);
    const items = [...BUILDS[id]!].sort((a, b) => {
      const s = ["weapon", "vitality", "spirit"];
      return s.indexOf(snap.items.find((i) => i.className === a)!.slot) - s.indexOf(snap.items.find((i) => i.className === b)!.slot);
    });
    let x = itemsX;
    let lastSlot = "";
    const placed: { className: string; x: number; y: number }[] = [];
    for (const cls of items) {
      const slot = snap.items.find((i) => i.className === cls)!.slot;
      if (lastSlot && slot !== lastSlot) x += 16;
      lastSlot = slot;
      fill(x, y, icon, icon, FRAME[slot]!);
      blit(await art(itemUrl.get(cls)!), x + frame, y + frame, icon - 2 * frame);
      placed.push({ className: cls, x, y });
      x += icon + 5;
    }
    rows.push({ heroId: id, team, y, items: placed });
  }
  await mkdir(outDir, { recursive: true });
  await writeFile(path.join(outDir, "synthetic-scoreboard.png"), encodePng(img));
  const truth = {
    note: "SYNTHETIC test image built from community-API art in a made-up layout; not a Deadlock screenshot.",
    width: W,
    height: H,
    geometry: { itemArea: { x: itemsX - 12, y: panel.y + 20, w: 640, h: panel.h - 40 }, icon, portrait: { x: portraitX, size: portrait } },
    rows,
  };
  await writeFile(path.join(outDir, "synthetic-scoreboard.json"), JSON.stringify(truth, null, 1) + "\n");
  console.log(`wrote ${path.join(outDir, "synthetic-scoreboard.png")} (${rows.length} rows, ${rows.reduce((n, r) => n + r.items.length, 0)} items)`);
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
