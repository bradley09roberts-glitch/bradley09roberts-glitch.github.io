#!/usr/bin/env node
/**
 * Rebuild fixtures/vision/icons.json (screen-recognition templates used by tests, the benchmark
 * and the web preview) from the active snapshot. The desktop app does not ship this file: it
 * builds its own copy on first use.
 *
 *   pnpm icons                # fetch missing images (cached under data/cache/img)
 *   pnpm icons -- --offline   # cached images only
 */
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import type { Snapshot } from "@countercoach/engine";
import { buildIconTemplates } from "./icons.js";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../../..");

async function main(): Promise<void> {
  const offline = process.argv.includes("--offline");
  const log = (m: string) => console.log(`[icons] ${m}`);
  const manifest = JSON.parse(await readFile(path.join(root, "data/snapshots/manifest.json"), "utf8")) as { active: { file: string } | null };
  if (!manifest.active) throw new Error("no active snapshot; run pnpm ingest first");
  const snapshot = JSON.parse(await readFile(path.join(root, "data/snapshots", manifest.active.file), "utf8")) as Snapshot;
  const fo = { cacheDir: path.join(root, "data/cache/img"), maxAgeMs: 7 * 24 * 3600_000, offline, retries: 3, timeoutMs: 30_000, log };
  const { file, problems } = await buildIconTemplates(snapshot, { ...fo, cacheDir: fo.cacheDir }, log);
  const outDir = path.join(root, "fixtures/vision");
  await mkdir(outDir, { recursive: true });
  await writeFile(path.join(outDir, "icons.json"), JSON.stringify(file) + "\n");
  if (problems.length) log(`${problems.length} problems:\n  ${problems.join("\n  ")}`);
  log(`wrote fixtures/vision/icons.json (${file.items.length} items, ${file.heroes.length} portraits, hash ${file.contentHash.slice(0, 12)})`);
}

main().catch((e) => {
  console.error(`[icons] failed: ${e instanceof Error ? e.stack : String(e)}`);
  process.exitCode = 1;
});
