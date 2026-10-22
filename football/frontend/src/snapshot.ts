// Snapshot mode: a read-only static copy of the site with no API server behind it.
//
// Off unless the app is built with `npm run build:snapshot` (Vite mode "snapshot", or
// VITE_SNAPSHOT=1). When off, SNAPSHOT is the constant false and the bundler drops everything
// below, so the normal build is unchanged.
//
// When on, every API request is answered from the files `uv run footy export-snapshot` writes
// next to the page. They hold the API's real responses at one moment, keyed by request path
// plus a canonical query string (see canonicalKey, and footy/snapshot.py, which builds the same
// keys). snapshot.json holds what the first screen needs and lists the shard files
// (snapshot-matches-1.json, snapshot-lab-1.json, snapshot-teams-1.json, ...) with the keys each
// one answers; a shard is fetched only when a page asks for one of its keys, then kept. A
// few requests are answered by filtering another stored response (see `derived`). A request
// that is in no file fails the way an unreachable API does, with the message
// "Not in this snapshot".

export const SNAPSHOT: boolean = import.meta.env.VITE_SNAPSHOT === "1";

/** The snapshot file, relative to where the page was first loaded (the hash router changes
 * only the fragment, but the address is captured once, at startup, regardless). */
const SNAPSHOT_URL = SNAPSHOT ? new URL("snapshot.json", document.baseURI).href : "";

export const NOT_IN_SNAPSHOT =
  "Not in this snapshot. This read-only copy holds only what the site could show when it was taken.";

/** A request the snapshot can't answer. api.ts turns it into an ApiError with no status,
 * the same shape as an unreachable API. */
export class SnapshotError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "SnapshotError";
  }
}

type SnapshotFile = {
  format: string;
  taken_at_utc: string;
  /** Distinct response bodies; entries point into this list. */
  bodies: unknown[];
  /** Canonical request key -> [HTTP status, index into bodies]. */
  entries: Record<string, [number, number]>;
  /** snapshot.json only: the shard files, each with the keys it answers. Absent in a
   * snapshot written before shards existed. */
  shards?: { file: string; bytes: number | null; keys: string[] }[];
};

let file: Promise<SnapshotFile> | null = null;
/** snapshot.json once it has loaded (for snapshotHolds). */
let loadedFile: SnapshotFile | null = null;
/** Shard file name -> its load, once started; a failed load is forgotten so it is retried. */
const shardLoads = new Map<string, Promise<SnapshotFile>>();
/** Key -> the shard file that answers it, built once from snapshot.json. */
let shardOf: Map<string, string> | null = null;

function isSnapshotFile(body: unknown): body is SnapshotFile {
  const b = body as SnapshotFile | null;
  return !!b && typeof b.entries === "object" && b.entries !== null && Array.isArray(b.bodies);
}

/** How loading snapshot.json is going, for the banner. It follows every attempt, so a
 * "Try again" that succeeds after a failed load clears the banner's failure note too. */
export type SnapshotStatus =
  | { state: "loading" }
  | { state: "failed" }
  | { state: "loaded"; takenAt: string };

let status: SnapshotStatus = { state: "loading" };
const listeners = new Set<() => void>();

function setStatus(next: SnapshotStatus): void {
  status = next;
  for (const listener of listeners) listener();
}

/** For useSyncExternalStore: subscribe to status changes. */
export function subscribeSnapshotStatus(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** For useSyncExternalStore: the current status (the same object until it changes). */
export function snapshotStatus(): SnapshotStatus {
  return status;
}

/** Fetch snapshot.json once. A failed load is forgotten, so "Try again" fetches it again. */
function loadSnapshot(): Promise<SnapshotFile> {
  if (!file) {
    if (status.state === "failed") setStatus({ state: "loading" });
    const pending = fetch(SNAPSHOT_URL, { headers: { Accept: "application/json" } })
      .then(async (res) => {
        if (!res.ok) throw new SnapshotError(`Couldn't load this snapshot's data (error ${res.status}).`);
        const body: unknown = await res.json();
        if (!isSnapshotFile(body)) {
          throw new SnapshotError("This snapshot's data file is not in the expected format.");
        }
        if (file === pending) {
          loadedFile = body;
          setStatus({ state: "loaded", takenAt: body.taken_at_utc });
        }
        return body;
      })
      .catch((err: unknown) => {
        if (file === pending) {
          file = null;
          setStatus({ state: "failed" });
        }
        throw err instanceof SnapshotError ? err : new SnapshotError("Couldn't load this snapshot's data.");
      });
    file = pending;
  }
  return file;
}

function compare(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

/**
 * The key a request is stored under: the path with each segment percent-encoded as
 * encodeURIComponent does, then the query parameters sorted by name (then value), each name
 * and value encoded the same way. "/api/past?record_type=x&limit=500" and
 * "/api/past?limit=500&record_type=x" share the key "/api/past?limit=500&record_type=x".
 */
export function canonicalKey(url: string): string {
  const q = url.indexOf("?");
  const rawPath = q >= 0 ? url.slice(0, q) : url;
  const path = rawPath
    .split("/")
    .map((s) => {
      try {
        return encodeURIComponent(decodeURIComponent(s));
      } catch {
        return s;
      }
    })
    .join("/");
  const params = [...new URLSearchParams(q >= 0 ? url.slice(q + 1) : "")].sort(
    ([ak, av], [bk, bv]) => compare(ak, bk) || compare(av, bv),
  );
  const qs = params.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join("&");
  return qs ? `${path}?${qs}` : path;
}

/** The shard file that answers `key`, if snapshot.json lists one. */
function shardFor(snap: SnapshotFile, key: string): string | undefined {
  if (!shardOf) {
    shardOf = new Map();
    for (const shard of snap.shards ?? []) {
      // Only a plain file name next to the page: never a path or another site.
      if (!/^snapshot-[a-z]+-\d+\.json$/.test(shard.file)) continue;
      for (const k of shard.keys) shardOf.set(k, shard.file);
    }
  }
  return shardOf.get(key);
}

/** Fetch one shard file once; a failed load is forgotten, so "Try again" fetches it again. */
function loadShard(name: string): Promise<SnapshotFile> {
  let pending = shardLoads.get(name);
  if (!pending) {
    pending = fetch(new URL(name, SNAPSHOT_URL).href, { headers: { Accept: "application/json" } })
      .then(async (res) => {
        if (!res.ok) throw new SnapshotError(`Couldn't load this part of the snapshot (error ${res.status}).`);
        const body: unknown = await res.json();
        if (!isSnapshotFile(body)) throw new SnapshotError("This snapshot's data file is not in the expected format.");
        return body;
      })
      .catch((err: unknown) => {
        shardLoads.delete(name);
        throw err instanceof SnapshotError ? err : new SnapshotError("Couldn't load this part of the snapshot.");
      });
    shardLoads.set(name, pending);
  }
  return pending;
}

/** The fixtures window the snapshot stores (footy/snapshot.py, SNAPSHOT_FIXTURE_DAYS). */
const FIXTURE_DAYS_HELD = 30;
/** The API's window when none is asked for. */
const FIXTURE_DAYS_DEFAULT = 8;

type FixturesBody = { generated_at_utc?: string; competitions?: { fixtures?: { match_date?: string }[] }[] };

/** The fixtures for the next `days` days from the stored 30-day response, as the API answers
 * them: the cards dated up to today + `days` (from yesterday on, which the stored response
 * already starts at), `today` being the UTC date it answered on; a competition left without
 * cards is dropped, as the API never lists an empty one. */
function fixturesWithin(body: unknown, days: number): unknown {
  const b = body as FixturesBody | null;
  const day = b?.generated_at_utc?.slice(0, 10);
  if (!b || !day || !Array.isArray(b.competitions)) return body;
  const last = new Date(`${day}T00:00:00Z`);
  last.setUTCDate(last.getUTCDate() + days);
  const until = last.toISOString().slice(0, 10);
  return {
    ...b,
    competitions: b.competitions
      .map((c) => ({ ...c, fixtures: (c.fixtures ?? []).filter((f) => (f.match_date ?? "") <= until) }))
      .filter((c) => c.fixtures.length > 0),
  };
}

/** A request answered by filtering another stored response: which key, and how. Only the
 * fixtures windows (every N from 1 to 30 comes from the 30-day one). */
function derived(key: string): { from: string; make: (body: unknown) => unknown } | null {
  const m = /^\/api\/fixtures(?:\?days=(\d+))?$/.exec(key);
  if (!m) return null;
  const days = m[1] === undefined ? FIXTURE_DAYS_DEFAULT : Number(m[1]);
  if (!Number.isInteger(days) || days < 1 || days > FIXTURE_DAYS_HELD) return null;
  return { from: canonicalKey(`/api/fixtures?days=${FIXTURE_DAYS_HELD}`), make: (body) => fixturesWithin(body, days) };
}

/** The stored [status, body] for a key, from snapshot.json or its shard; null if neither has it. */
async function lookup(snap: SnapshotFile, key: string, signal: AbortSignal): Promise<[number, unknown] | null> {
  let source = snap;
  let hit = snap.entries[key];
  if (!hit) {
    const shard = shardFor(snap, key);
    if (!shard) return null;
    source = await loadShard(shard);
    if (signal.aborted) throw new DOMException("Aborted", "AbortError");
    hit = source.entries[key];
    if (!hit) return null;
  }
  const [status, index] = hit;
  return [status, source.bodies[index] ?? null];
}

/** Answer an API request from the snapshot, as the Response the API sent when it was taken:
 * from snapshot.json, or from the shard file that holds it (fetched the first time a page
 * needs it), or derived from another stored response. Rejects with SnapshotError when it
 * isn't there, or an AbortError when `signal` aborts. */
export async function snapshotFetch(url: string, signal: AbortSignal): Promise<Response> {
  const snap = await loadSnapshot();
  if (signal.aborted) throw new DOMException("Aborted", "AbortError");
  const key = canonicalKey(url);
  let found = await lookup(snap, key, signal);
  if (!found) {
    const rule = derived(key);
    const from = rule ? await lookup(snap, rule.from, signal) : null;
    if (rule && from) found = [from[0], from[0] === 200 ? rule.make(from[1]) : from[1]];
  }
  if (!found) throw new SnapshotError(NOT_IN_SNAPSHOT);
  const [status, body] = found;
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

/** Start loading snapshot.json if nothing has yet (the banner does this, so a page that
 * makes no request of its own still learns when the snapshot was taken). Progress and
 * failure are reported through snapshotStatus(). */
export function startSnapshotLoad(): void {
  loadSnapshot().catch(() => {
    // Reported through snapshotStatus(); the pages show their own error and "Try again".
  });
}

/** Whether the snapshot answers this request (in snapshot.json or a shard): true or false once
 * snapshot.json has loaded, null before then. Lets a page leave out links to pages the copy
 * doesn't hold, such as a team without a page. Re-check after snapshotStatus() changes. */
export function snapshotHolds(url: string): boolean | null {
  if (!loadedFile) return null;
  const file = loadedFile;
  const held = (key: string) => key in file.entries || shardFor(file, key) !== undefined;
  const key = canonicalKey(url);
  const rule = derived(key);
  return held(key) || (rule !== null && held(rule.from));
}
