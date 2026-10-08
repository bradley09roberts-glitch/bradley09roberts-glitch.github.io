#!/usr/bin/env node
/**
 * CounterCoach data ingest.
 *
 *   npm run ingest                 # fetch (with cache), validate, write snapshot if changed
 *   npm run ingest -- --offline    # rebuild from cached responses only
 *
 * The active snapshot is only replaced after the new one passes schema validation and
 * structural checks. The previous snapshot is kept as last-known-good.
 */
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  SNAPSHOT_SCHEMA_VERSION,
  diffSnapshots,
  snapshotContentHash,
  validateSnapshot,
  type Snapshot,
} from "@countercoach/engine";
import { fetchJson, type FetchOptions } from "./http.js";
import { rawAbilityOrderStat, rawBuild, rawGeneric, rawHero, rawItem, rawPatch, rawSteamInfo } from "./rawSchemas.js";
import type { RawAbilityOrderStat, RawBuild, RawHero, RawItem } from "./rawSchemas.js";
import { deriveAbilityCosts, transformHeroes, transformItems } from "./transform.js";
import { z } from "zod";

const API = "https://api.deadlock-api.com";
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

function validateArray<T>(name: string, schema: z.ZodType<T>, data: unknown, errors: string[]): T[] {
  if (!Array.isArray(data)) {
    errors.push(`${name}: expected array`);
    return [];
  }
  const out: T[] = [];
  data.forEach((d, i) => {
    const r = schema.safeParse(d);
    if (r.success) out.push(r.data);
    else errors.push(`${name}[${i}]: ${r.error.issues.slice(0, 2).map((x) => `${x.path.join(".")} ${x.message}`).join("; ")}`);
  });
  return out;
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
  const fo: FetchOptions = {
    cacheDir: args.cache,
    maxAgeMs: args.maxAgeHours * 3600_000,
    offline: args.offline,
    retries: 4,
    timeoutMs: 60_000,
    log,
  };
  const endpoints: string[] = [];
  const get = async <T>(p: string): Promise<T> => {
    endpoints.push(p.split("?")[0]!);
    const r = await fetchJson<T>(`${API}${p}`, fo);
    log(`${r.fromCache ? "cache" : "net  "} ${p.slice(0, 110)}`);
    return r.data;
  };

  const errors: string[] = [];
  const warnings: string[] = [];

  const steam = rawSteamInfo.safeParse(await get("/v1/assets/steam-info"));
  if (!steam.success) errors.push("steam-info invalid");
  const rawHeroes = validateArray<RawHero>("heroes", rawHero, await get("/v1/assets/heroes"), errors);
  const rawItems = validateArray<RawItem>("items", rawItem, await get("/v1/assets/items"), errors);
  const generic = rawGeneric.safeParse(await get("/v1/assets/generic-data"));
  if (!generic.success) errors.push("generic-data invalid");
  const patches = validateArray("patches", rawPatch, await get("/v2/patches"), warnings);

  if (errors.length) {
    console.error(`[ingest] raw validation failed; active snapshot NOT replaced:\n  ${errors.slice(0, 20).join("\n  ")}`);
    process.exitCode = 2;
    return;
  }

  // Ability order statistics (weak priors) for playable heroes, current window only.
  const since = Math.floor(Date.now() / 1000) - args.statsDays * 86400;
  const playable = rawHeroes.filter((h) => h.player_selectable === true && h.disabled !== true);
  const orderStats = new Map<number, { since: number; orders: RawAbilityOrderStat[] }>();
  for (const h of playable) {
    try {
      const data = await get<unknown>(
        `/v1/analytics/ability-order-stats?hero_id=${h.id}&min_unix_timestamp=${since}&min_matches=20`,
      );
      const orders = validateArray("ability-order-stats", rawAbilityOrderStat, data, warnings);
      orderStats.set(h.id, { since, orders });
    } catch (e) {
      warnings.push(`ability-order-stats for ${h.name} unavailable: ${String(e)}`);
    }
  }

  // Builds for deriving ability point costs.
  const builds: RawBuild[] = [];
  for (const h of playable.slice(0, args.buildHeroes)) {
    try {
      const data = await get<unknown>(
        `/v1/builds?hero_id=${h.id}&limit=50&sort_by=updated_at&sort_direction=desc&only_latest=true`,
      );
      builds.push(...validateArray("builds", rawBuild, data, warnings));
    } catch (e) {
      warnings.push(`builds for ${h.name} unavailable: ${String(e)}`);
    }
  }

  const w = { warnings };
  const items = transformItems(rawItems, w);
  const heroes = transformHeroes(rawHeroes, rawItems, orderStats, w);
  const abilityCosts = deriveAbilityCosts(builds, heroes, w);
  abilityCosts.provenance.checkedBuild = steam.success ? steam.data.client_version : null;

  const latestPatch = patches
    .slice()
    .sort((a, b) => b.pub_date.localeCompare(a.pub_date))[0];
  const g = generic.success ? generic.data : {};
  const snapshot: Snapshot = {
    meta: {
      schemaVersion: SNAPSHOT_SCHEMA_VERSION,
      apiBase: API,
      endpoints: [...new Set(endpoints)].sort(),
      retrievedAt: new Date().toISOString(),
      clientVersion: steam.success ? steam.data.client_version : null,
      serverVersion: steam.success ? (steam.data.server_version ?? null) : null,
      versionDate: steam.success ? (steam.data.version_datetime ?? steam.data.version_date ?? null) : null,
      latestPatch: latestPatch ? { title: latestPatch.title.trim(), date: latestPatch.pub_date, link: latestPatch.link } : null,
      contentHash: snapshotContentHash({ heroes, items, abilityCosts }),
      counts: {
        heroes: heroes.length,
        playableHeroes: heroes.filter((h) => h.playable).length,
        items: items.length,
        shopItems: items.filter((i) => i.tier <= 4).length,
      },
    },
    heroes,
    items,
    abilityCosts,
    itemPricePerTier: (g as { item_price_per_tier?: number[] }).item_price_per_tier ?? [],
    modesInData: ["standard", ...("street_brawl" in g ? ["street_brawl"] : [])],
  };

  const v = validateSnapshot(snapshot);
  if (!v.ok) {
    console.error(`[ingest] snapshot validation failed; active snapshot NOT replaced:\n  ${v.errors.join("\n  ")}`);
    process.exitCode = 3;
    return;
  }

  await mkdir(args.out, { recursive: true });
  const manifest = await readManifest(args.out);
  const now = new Date().toISOString();
  if (manifest.active?.contentHash === snapshot.meta.contentHash) {
    manifest.lastCheck = { at: now, clientVersion: snapshot.meta.clientVersion, result: "unchanged" };
    await writeFile(path.join(args.out, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n");
    log(`content unchanged (hash ${snapshot.meta.contentHash.slice(0, 12)}); active snapshot kept`);
  } else {
    const file = `snapshot-${snapshot.meta.clientVersion ?? "unknown"}-${snapshot.meta.contentHash.slice(0, 12)}.json`;
    await writeFile(path.join(args.out, file), JSON.stringify(snapshot) + "\n");
    if (manifest.active) {
      try {
        const prev = JSON.parse(await readFile(path.join(args.out, manifest.active.file), "utf8")) as Snapshot;
        const diff = diffSnapshots(prev, snapshot);
        const diffFile = `diff-${prev.meta.contentHash.slice(0, 8)}-to-${snapshot.meta.contentHash.slice(0, 8)}.json`;
        await writeFile(path.join(args.out, diffFile), JSON.stringify(diff, null, 2) + "\n");
        log(`diff vs last-known-good: ${diff.items.length} item changes, ${diff.abilities.length} ability changes, ${diff.heroes.length} hero changes -> ${diffFile}`);
      } catch (e) {
        warnings.push(`could not diff against previous snapshot: ${String(e)}`);
      }
      manifest.history.unshift({ ...manifest.active, replacedAt: now });
      manifest.history = manifest.history.slice(0, 10);
    }
    manifest.active = {
      file,
      clientVersion: snapshot.meta.clientVersion,
      contentHash: snapshot.meta.contentHash,
      retrievedAt: snapshot.meta.retrievedAt,
    };
    manifest.lastCheck = { at: now, clientVersion: snapshot.meta.clientVersion, result: "replaced" };
    await writeFile(path.join(args.out, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n");
    log(`wrote ${file}`);
  }
  await writeFile(path.join(args.out, "ingest-warnings.txt"), warnings.join("\n") + "\n");
  log(
    `build ${snapshot.meta.clientVersion} | heroes ${snapshot.meta.counts.playableHeroes}/${snapshot.meta.counts.heroes} playable | items ${snapshot.meta.counts.items} (${snapshot.meta.counts.shopItems} tier<=4) | tier costs ${abilityCosts.tierCosts.join("/")} (${abilityCosts.provenance.confidence}) | ${warnings.length} warnings`,
  );
}

main().catch((e) => {
  console.error(`[ingest] failed; active snapshot NOT replaced: ${e instanceof Error ? e.stack : String(e)}`);
  process.exitCode = 1;
});
