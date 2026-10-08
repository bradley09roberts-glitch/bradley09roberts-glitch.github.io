import { z } from "zod";
import { SNAPSHOT_SCHEMA_VERSION, snapshotContentHash, validateSnapshot, type Snapshot } from "@countercoach/engine";
import { fetchJson, type FetchOptions } from "./http.js";
import { rawAbilityOrderStat, rawBuild, rawGeneric, rawHero, rawItem, rawPatch, rawSteamInfo } from "./rawSchemas.js";
import type { RawAbilityOrderStat, RawBuild, RawHero, RawItem } from "./rawSchemas.js";
import { deriveAbilityCosts, transformHeroes, transformItems } from "./transform.js";

export const API = "https://api.deadlock-api.com";

export interface IngestOptions {
  fetch: FetchOptions;
  statsDays: number;
  buildHeroes: number;
  log: (m: string) => void;
}

export type IngestResult =
  | { ok: true; snapshot: Snapshot; warnings: string[] }
  | { ok: false; errors: string[]; warnings: string[] };

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

/** Latest client build only (cheap check used by the app before a full ingest). */
export async function fetchLatestBuild(fo: FetchOptions): Promise<number | null> {
  const r = rawSteamInfo.safeParse((await fetchJson(`${API}/v1/assets/steam-info`, { ...fo, maxAgeMs: 0 })).data);
  return r.success ? r.data.client_version : null;
}

/**
 * Fetch, validate and normalise a snapshot. Never writes files and never throws for data
 * problems: invalid data returns { ok: false } so callers keep their last-known-good snapshot.
 */
export async function buildSnapshot(opts: IngestOptions): Promise<IngestResult> {
  const { log } = opts;
  const endpoints: string[] = [];
  const errors: string[] = [];
  const warnings: string[] = [];
  const get = async <T>(p: string): Promise<T> => {
    endpoints.push(p.split("?")[0]!);
    const r = await fetchJson<T>(`${API}${p}`, opts.fetch);
    log(`${r.fromCache ? "cache" : "net  "} ${p.slice(0, 110)}`);
    return r.data;
  };
  try {
    const steam = rawSteamInfo.safeParse(await get("/v1/assets/steam-info"));
    if (!steam.success) errors.push("steam-info invalid");
    const rawHeroes = validateArray<RawHero>("heroes", rawHero, await get("/v1/assets/heroes"), errors);
    const rawItems = validateArray<RawItem>("items", rawItem, await get("/v1/assets/items"), errors);
    const generic = rawGeneric.safeParse(await get("/v1/assets/generic-data"));
    if (!generic.success) errors.push("generic-data invalid");
    const patches = validateArray("patches", rawPatch, await get("/v2/patches"), warnings);
    if (errors.length) return { ok: false, errors, warnings };

    // Day-aligned window so cached statistics remain addressable for the whole day.
    const since = Math.floor(Date.now() / 86_400_000) * 86400 - opts.statsDays * 86400;
    const playable = rawHeroes.filter((h) => h.player_selectable === true && h.disabled !== true);
    const orderStats = new Map<number, { since: number; orders: RawAbilityOrderStat[] }>();
    for (const h of playable) {
      try {
        const data = await get<unknown>(`/v1/analytics/ability-order-stats?hero_id=${h.id}&min_unix_timestamp=${since}&min_matches=20`);
        orderStats.set(h.id, { since, orders: validateArray("ability-order-stats", rawAbilityOrderStat, data, warnings) });
      } catch (e) {
        warnings.push(`ability-order-stats for ${h.name} unavailable: ${String(e)}`);
      }
    }
    const builds: RawBuild[] = [];
    for (const h of playable.slice(0, opts.buildHeroes)) {
      try {
        const data = await get<unknown>(`/v1/builds?hero_id=${h.id}&limit=50&sort_by=updated_at&sort_direction=desc&only_latest=true`);
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
    const latestPatch = patches.slice().sort((a, b) => b.pub_date.localeCompare(a.pub_date))[0];
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
    if (!v.ok) return { ok: false, errors: v.errors, warnings };
    return { ok: true, snapshot: v.snapshot, warnings };
  } catch (e) {
    return { ok: false, errors: [e instanceof Error ? e.message : String(e)], warnings };
  }
}

export interface QualityReport {
  acceptable: boolean;
  problems: string[];
}

/**
 * Compare a freshly built snapshot with the last-known-good one. Schema validity is not enough:
 * a partial outage can produce a valid but degraded snapshot (e.g. statistics missing), which
 * must not replace a better one.
 */
export function compareQuality(prev: Snapshot | null, next: Snapshot): QualityReport {
  const problems: string[] = [];
  if (!next.abilityCosts.crossCheck.consistent) problems.push("ability cost cross-check failed");
  if (prev) {
    const cov = (s: Snapshot) => s.heroes.filter((h) => h.playable && h.abilityOrders.orders.length).length;
    if (cov(next) < cov(prev) * 0.9) problems.push(`ability-order statistics coverage dropped (${cov(prev)} → ${cov(next)} heroes)`);
    if (next.meta.counts.shopItems < prev.meta.counts.shopItems * 0.85) problems.push(`shop item count dropped sharply (${prev.meta.counts.shopItems} → ${next.meta.counts.shopItems})`);
    if (next.meta.counts.playableHeroes < prev.meta.counts.playableHeroes - 3) problems.push(`playable hero count dropped (${prev.meta.counts.playableHeroes} → ${next.meta.counts.playableHeroes})`);
    if ((next.meta.clientVersion ?? 0) < (prev.meta.clientVersion ?? 0)) problems.push("client build is older than the active snapshot");
  }
  return { acceptable: problems.length === 0, problems };
}
