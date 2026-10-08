#!/usr/bin/env node
/**
 * CounterCoach data ingest.
 *
 *   pnpm ingest                 # fetch (with cache), validate, write snapshot if changed
 *   pnpm ingest -- --offline    # rebuild from cached responses only
 *
 * The active snapshot is only replaced after the new one passes schema validation and
 * structural checks. The previous snapshot is kept as last-known-good.
 */
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { diffSnapshots, type Snapshot } from "@countercoach/engine";
import { buildSnapshot, compareQuality } from "./core.js";
import type { FetchOptions } from "./http.js";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../../..");

interface Args {
  out: string;
  cache: string;
  offline: boolean;
  maxAgeHours: number;
  statsDays: number;
  buildHeroes: number;
}

function parseArgs(argv: string[]): Args {
  const a: Args = {
    out: path.join(root, "data/snapshots"),
    cache: path.join(root, "data/cache"),
    offline: false,
    maxAgeHours: 6,
    statsDays: 14,
    buildHeroes: 6,
  };
  for (let i = 0; i < argv.length; i++) {
    const k = argv[i];
    const v = argv[i + 1];
    if (k === "--offline") a.offline = true;
    else if (k === "--out" && v) (a.out = path.resolve(v), i++);
    else if (k === "--cache" && v) (a.cache = path.resolve(v), i++);
    else if (k === "--max-age-hours" && v) (a.maxAgeHours = Number(v), i++);
    else if (k === "--stats-days" && v) (a.statsDays = Number(v), i++);
    else if (k === "--build-heroes" && v) (a.buildHeroes = Number(v), i++);
  }
  return a;
}

export interface Manifest {
  active: { file: string; clientVersion: number | null; contentHash: string; retrievedAt: string } | null;
  history: { file: string; clientVersion: number | null; contentHash: string; retrievedAt: string; replacedAt: string }[];
  lastCheck: { at: string; clientVersion: number | null; result: string } | null;
}

async function readManifest(dir: string): Promise<Manifest> {
  try {
    return JSON.parse(await readFile(path.join(dir, "manifest.json"), "utf8")) as Manifest;
  } catch {
    return { active: null, history: [], lastCheck: null };
  }
}

async function main(): Promise<void> {
  const args = parseArgs(process.argv.slice(2));
  const log = (m: string) => console.log(`[ingest] ${m}`);
  const fo: FetchOptions = { cacheDir: args.cache, maxAgeMs: args.maxAgeHours * 3600_000, offline: args.offline, retries: 4, timeoutMs: 60_000, log };
  const result = await buildSnapshot({ fetch: fo, statsDays: args.statsDays, buildHeroes: args.buildHeroes, log });
  if (!result.ok) {
    console.error(`[ingest] validation failed; active snapshot NOT replaced:\n  ${result.errors.slice(0, 20).join("\n  ")}`);
    process.exitCode = 2;
    return;
  }
  const { snapshot, warnings } = result;
  await mkdir(args.out, { recursive: true });
  const manifest = await readManifest(args.out);
  const now = new Date().toISOString();
  let prev: Snapshot | null = null;
  if (manifest.active) {
    try {
      prev = JSON.parse(await readFile(path.join(args.out, manifest.active.file), "utf8")) as Snapshot;
    } catch {
      prev = null;
    }
  }
  const quality = compareQuality(prev, snapshot);
  if (!quality.acceptable && manifest.active?.contentHash !== snapshot.meta.contentHash) {
    manifest.lastCheck = { at: now, clientVersion: snapshot.meta.clientVersion, result: `rejected: ${quality.problems.join("; ")}` };
    await writeFile(path.join(args.out, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n");
    console.error(`[ingest] new snapshot is degraded; last-known-good kept:\n  ${quality.problems.join("\n  ")}`);
    process.exitCode = 4;
    return;
  }
  if (manifest.active?.contentHash === snapshot.meta.contentHash) {
    manifest.lastCheck = { at: now, clientVersion: snapshot.meta.clientVersion, result: "unchanged" };
    await writeFile(path.join(args.out, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n");
    log(`content unchanged (hash ${snapshot.meta.contentHash.slice(0, 12)}); active snapshot kept`);
  } else {
    const file = `snapshot-${snapshot.meta.clientVersion ?? "unknown"}-${snapshot.meta.contentHash.slice(0, 12)}.json`;
    await writeFile(path.join(args.out, file), JSON.stringify(snapshot) + "\n");
    if (manifest.active && prev) {
      try {
        const diff = diffSnapshots(prev, snapshot);
        const diffFile = `diff-${prev.meta.contentHash.slice(0, 8)}-to-${snapshot.meta.contentHash.slice(0, 8)}.json`;
        await writeFile(path.join(args.out, diffFile), JSON.stringify(diff, null, 2) + "\n");
        log(`diff vs last-known-good: ${diff.items.length} item, ${diff.abilities.length} ability, ${diff.heroes.length} hero changes -> ${diffFile}`);
      } catch (e) {
        warnings.push(`could not diff against previous snapshot: ${String(e)}`);
      }
      manifest.history.unshift({ ...manifest.active, replacedAt: now });
      manifest.history = manifest.history.slice(0, 10);
    }
    manifest.active = { file, clientVersion: snapshot.meta.clientVersion, contentHash: snapshot.meta.contentHash, retrievedAt: snapshot.meta.retrievedAt };
    manifest.lastCheck = { at: now, clientVersion: snapshot.meta.clientVersion, result: "replaced" };
    await writeFile(path.join(args.out, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n");
    log(`wrote ${file}`);
  }
  await writeFile(path.join(args.out, "ingest-warnings.txt"), warnings.join("\n") + "\n");
  log(
    `build ${snapshot.meta.clientVersion} | heroes ${snapshot.meta.counts.playableHeroes}/${snapshot.meta.counts.heroes} playable | items ${snapshot.meta.counts.items} (${snapshot.meta.counts.shopItems} tier<=4) | tier costs ${snapshot.abilityCosts.tierCosts.join("/")} (${snapshot.abilityCosts.provenance.confidence}) | ${warnings.length} warnings`,
  );
}

main().catch((e) => {
  console.error(`[ingest] failed; active snapshot NOT replaced: ${e instanceof Error ? e.stack : String(e)}`);
  process.exitCode = 1;
});
