// Snapshot mode: a read-only static copy of the site with no API server behind it.
//
// Off unless the app is built with `npm run build:snapshot` (Vite mode "snapshot", or
// VITE_SNAPSHOT=1). When off, SNAPSHOT is the constant false and the bundler drops everything
// below, so the normal build is unchanged.
//
// When on, every API request is answered from snapshot.json, a file written next to the page
// by `uv run footy export-snapshot`. It holds the API's real responses at one moment, keyed by
// request path plus a canonical query string (see canonicalKey, and footy/snapshot.py, which
// builds the same keys). A request that isn't in the file fails the way an unreachable API
// does, with the message "Not in this snapshot".

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
};

let file: Promise<SnapshotFile> | null = null;

/** Fetch snapshot.json once. A failed load is forgotten, so "Try again" fetches it again. */
function loadSnapshot(): Promise<SnapshotFile> {
  if (!file) {
    const pending = fetch(SNAPSHOT_URL, { headers: { Accept: "application/json" } })
      .then(async (res) => {
        if (!res.ok) throw new SnapshotError(`Couldn't load this snapshot's data (error ${res.status}).`);
        const body = (await res.json()) as SnapshotFile;
        if (!body || typeof body.entries !== "object" || !Array.isArray(body.bodies)) {
          throw new SnapshotError("This snapshot's data file is not in the expected format.");
        }
        return body;
      })
      .catch((err: unknown) => {
        if (file === pending) file = null;
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

/** Answer an API request from the snapshot, as the Response the API sent when it was taken.
 * Rejects with SnapshotError when it isn't there, or an AbortError when `signal` aborts. */
export async function snapshotFetch(url: string, signal: AbortSignal): Promise<Response> {
  const snap = await loadSnapshot();
  if (signal.aborted) throw new DOMException("Aborted", "AbortError");
  const hit = snap.entries[canonicalKey(url)];
  if (!hit) throw new SnapshotError(NOT_IN_SNAPSHOT);
  const [status, index] = hit;
  return new Response(JSON.stringify(snap.bodies[index] ?? null), {
    status,
    headers: { "content-type": "application/json" },
  });
}

/** When the snapshot was taken (UTC, ISO 8601), for the banner. */
export function snapshotTakenAt(): Promise<string> {
  return loadSnapshot().then((s) => s.taken_at_utc);
}
