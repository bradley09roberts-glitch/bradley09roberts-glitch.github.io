import { mkdir, readFile, writeFile, stat } from "node:fs/promises";
import { createHash } from "node:crypto";
import path from "node:path";

export interface FetchOptions {
  cacheDir: string;
  /** Use cached response if younger than this many ms. */
  maxAgeMs: number;
  /** Never touch the network; cached responses only. */
  offline: boolean;
  retries: number;
  timeoutMs: number;
  log: (msg: string) => void;
}

export interface FetchResult<T> {
  url: string;
  data: T;
  fromCache: boolean;
  fetchedAt: string;
}

function cacheKey(url: string): string {
  return createHash("sha256").update(url).digest("hex").slice(0, 24);
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Minimal client-side rate limiter: the API advertises 200 requests / 60 s. */
let recent: number[] = [];
async function rateLimit(): Promise<void> {
  const now = Date.now();
  recent = recent.filter((t) => now - t < 60_000);
  if (recent.length >= 150) {
    const wait = 60_000 - (now - recent[0]!) + 50;
    await sleep(wait);
  }
  recent.push(Date.now());
}

/**
 * Fetch JSON with an on-disk cache, bounded retries and exponential backoff. Responses are
 * parsed as JSON only (never evaluated). Falls back to a stale cache entry if the network
 * fails, and reports that it did so.
 */
export async function fetchJson<T = unknown>(url: string, opts: FetchOptions): Promise<FetchResult<T>> {
  await mkdir(opts.cacheDir, { recursive: true });
  const file = path.join(opts.cacheDir, `${cacheKey(url)}.json`);
  const readCache = async (): Promise<FetchResult<T> | null> => {
    try {
      const raw = JSON.parse(await readFile(file, "utf8")) as { url: string; fetchedAt: string; data: T };
      if (raw.url !== url) return null;
      return { url, data: raw.data, fromCache: true, fetchedAt: raw.fetchedAt };
    } catch {
      return null;
    }
  };

  if (opts.offline) {
    const c = await readCache();
    if (!c) throw new Error(`offline and no cached response for ${url}`);
    return c;
  }
  try {
    const s = await stat(file);
    if (Date.now() - s.mtimeMs < opts.maxAgeMs) {
      const c = await readCache();
      if (c) return c;
    }
  } catch {
    /* no cache */
  }

  let lastErr: unknown = null;
  for (let attempt = 0; attempt <= opts.retries; attempt++) {
    if (attempt > 0) {
      const backoff = Math.min(16_000, 1000 * 2 ** (attempt - 1));
      opts.log(`retry ${attempt}/${opts.retries} in ${backoff}ms: ${url}`);
      await sleep(backoff);
    }
    try {
      await rateLimit();
      const ctrl = new AbortController();
      const timer = setTimeout(() => ctrl.abort(), opts.timeoutMs);
      const res = await fetch(url, { signal: ctrl.signal, headers: { accept: "application/json" } });
      clearTimeout(timer);
      if (res.status === 429) {
        const ra = Number(res.headers.get("retry-after") ?? "5");
        lastErr = new Error(`429 rate limited (${url})`);
        await sleep(Math.min(60_000, Math.max(1000, ra * 1000)));
        continue;
      }
      if (!res.ok) {
        lastErr = new Error(`HTTP ${res.status} for ${url}`);
        if (res.status >= 400 && res.status < 500) break;
        continue;
      }
      const text = await res.text();
      const data = JSON.parse(text) as T;
      const fetchedAt = new Date().toISOString();
      await writeFile(file, JSON.stringify({ url, fetchedAt, data }));
      return { url, data, fromCache: false, fetchedAt };
    } catch (e) {
      lastErr = e;
    }
  }
  const stale = await readCache();
  if (stale) {
    opts.log(`network failed for ${url} (${String(lastErr)}); using cached response from ${stale.fetchedAt}`);
    return stale;
  }
  throw lastErr instanceof Error ? lastErr : new Error(String(lastErr));
}

/**
 * Fetch a binary asset (icon image) with the same cache/backoff policy as fetchJson. Cached
 * files never expire: asset URLs are content-specific enough for icons, and `--refresh` callers
 * can delete the cache directory.
 */
export async function fetchBinary(url: string, opts: FetchOptions): Promise<{ url: string; data: Uint8Array; fromCache: boolean }> {
  await mkdir(opts.cacheDir, { recursive: true });
  const file = path.join(opts.cacheDir, `${cacheKey(url)}.bin`);
  try {
    return { url, data: new Uint8Array(await readFile(file)), fromCache: true };
  } catch {
    /* not cached */
  }
  if (opts.offline) throw new Error(`offline and no cached asset for ${url}`);
  let lastErr: unknown = null;
  for (let attempt = 0; attempt <= opts.retries; attempt++) {
    if (attempt > 0) await sleep(Math.min(16_000, 1000 * 2 ** (attempt - 1)));
    try {
      await rateLimit();
      const ctrl = new AbortController();
      const timer = setTimeout(() => ctrl.abort(), opts.timeoutMs);
      const res = await fetch(url, { signal: ctrl.signal });
      clearTimeout(timer);
      if (!res.ok) {
        lastErr = new Error(`HTTP ${res.status} for ${url}`);
        if (res.status >= 400 && res.status < 500 && res.status !== 429) break;
        continue;
      }
      const data = new Uint8Array(await res.arrayBuffer());
      await writeFile(file, data);
      return { url, data, fromCache: false };
    } catch (e) {
      lastErr = e;
    }
  }
  throw lastErr instanceof Error ? lastErr : new Error(String(lastErr));
}
