/**
 * Render a SYNTHETIC scoreboard image for end-to-end tests and documentation.
 *
 *   pnpm tsx scripts/make-test-scoreboard.ts [outDir]
 *
 * It is built from the community API's item art and hero portraits (cached by `pnpm icons`), laid
 * out like Deadlock's Tab view as seen on one real capture: a card per player across the top,
 * items in a grid under each card, a tier badge on every icon, busy scenery behind. It is NOT a
 * Deadlock screenshot.
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

/**
 * A second Tab press with different builds (items sold, swapped, or not bought yet), for the
 * "calibrate once, then read on every Tab" flow: the slot grid learned on the full board must
 * read this one without recalibrating.
 */
const NEXT_BUILDS: Record<number, string[]> = Object.fromEntries(
  Object.entries(BUILDS).map(([id, items]) => [Number(id), items.slice(0, Math.max(1, items.length - (Number(id) % 3)))]),
);
NEXT_BUILDS[4] = ["upgrade_rapid_rounds", "upgrade_resonant_healing", "upgrade_improved_spirit"];
NEXT_BUILDS[20] = ["upgrade_improved_spirit", "upgrade_headshot_booster"];

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
  const left = Math.round((W - (12 * 74 + 11 * 8 + 60)) / 2);
  const geometry = { itemArea: { x: left - 10, y: 168 - 8, w: 12 * 74 + 11 * 8 + 80, h: 3 * (30 + 4) + 16 }, icon: 30 };
  const draw = async (builds: Record<number, string[]>, sceneSeed: number) => {
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

    // Busy "game world" behind the HUD: blocks of varied colour and contrast.
    let seed = sceneSeed;
    const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
    for (let k = 0; k < 900; k++) {
      const c: [number, number, number] = [40 + rnd() * 200, 30 + rnd() * 160, 20 + rnd() * 140];
      fill(Math.floor(rnd() * W), Math.floor(rnd() * H), 6 + Math.floor(rnd() * 90), 6 + Math.floor(rnd() * 90), c, 0.35);
    }
    // Tab view modelled on a real capture: one card per player across the top (your team amber on
    // the left, enemies blue on the right), portrait on top, items under the card in a 2-wide grid,
    // each icon with a tier badge in its top-right corner.
    const icon = 30;
    const gap = 4;
    const cardW = 74;
    const cardGap = 8;
    const portrait = { w: 62, h: 70 };
    const cardTop = 12;
    const itemsTop = 168;
    const BADGE: Record<string, [number, number, number]> = { vitality: [174, 210, 71], weapon: [230, 150, 50], spirit: [175, 120, 235] };
    const badge = (x: number, y: number, n: number, slot: string) => {
      for (let j = 0; j < n; j++) for (let i = 0; i < n; i++) if ((i + 0.5) / n - (j + 0.5) / n > 0.65) fill(x + i, y + j, 1, 1, BADGE[slot]!);
    };
    const order = [
      { id: ME, team: "me" as const },
      ...ALLIES.map((id) => ({ id, team: "ally" as const })),
      ...ENEMIES.map((id) => ({ id, team: "enemy" as const })),
    ];
    const rows: { heroId: number; team: "ally" | "enemy" | "me"; x: number; portrait: { x: number; y: number; w: number; h: number }; items: { className: string; x: number; y: number }[] }[] = [];
    for (let k = 0; k < order.length; k++) {
      const { id, team } = order[k]!;
      const x0 = left + k * (cardW + cardGap) + (k >= 6 ? 60 : 0);
      fill(x0, cardTop, cardW, itemsTop - cardTop - 8, team === "enemy" ? [52, 92, 190] : [196, 150, 46], 0.9);
      // Ability circles near the bottom of the card (HUD parts that are not items).
      for (let a = 0; a < 4; a++) fill(x0 + 6 + a * 17, itemsTop - 30, 12, 12, [235, 235, 235], 0.9);
      const pr = { x: x0 + Math.round((cardW - portrait.w) / 2), y: cardTop + 4, w: portrait.w, h: portrait.h };
      blit(await art(heroUrl.get(id)!), pr.x, pr.y + Math.round((pr.h - pr.w) / 2), pr.w);
      const placed: { className: string; x: number; y: number }[] = [];
      const items = builds[id]!;
      for (let i = 0; i < items.length; i++) {
        const cls = items[i]!;
        const slot = snap.items.find((it) => it.className === cls)!.slot;
        const x = x0 + Math.round((cardW - 2 * icon - gap) / 2) + (i % 2) * (icon + gap);
        const y = itemsTop + Math.floor(i / 2) * (icon + gap);
        blit(await art(itemUrl.get(cls)!), x, y, icon);
        badge(x, y, icon, slot);
        placed.push({ className: cls, x, y });
      }
      rows.push({ heroId: id, team, x: x0 + cardW / 2, portrait: pr, items: placed });
    }
    return { img, rows };
  };

  await mkdir(outDir, { recursive: true });
  for (const [name, builds, sceneSeed] of [
    ["synthetic-scoreboard", BUILDS, 7],
    ["synthetic-scoreboard-next", NEXT_BUILDS, 23],
  ] as const) {
    const { img, rows } = await draw(builds, sceneSeed);
    await writeFile(path.join(outDir, `${name}.png`), encodePng(img));
    const truth = {
      note: "SYNTHETIC test image built from community-API art, laid out like Deadlock's Tab view as seen on one real capture; not a Deadlock screenshot.",
      width: W,
      height: H,
      geometry,
      rows,
    };
    await writeFile(path.join(outDir, `${name}.json`), JSON.stringify(truth, null, 1) + "\n");
    console.log(`wrote ${path.join(outDir, `${name}.png`)} (${rows.length} players, ${rows.reduce((n, r) => n + r.items.length, 0)} items)`);
  }
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
