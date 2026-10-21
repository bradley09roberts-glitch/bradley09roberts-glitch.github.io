// Typed client for the read-only API (src/footy/api/main.py and routes.py).
//
// Every endpoint has a typed URL builder in `endpoints` (for the `useApi` hook) and a fetch
// function in `api` that takes an AbortSignal. Nothing here invents a number: the types only
// describe what the API sends.

import { useCallback, useEffect, useRef, useState } from "react";

// ---------------------------------------------------------------------------------------------
// Shared JSON types

export type JsonValue =
  | string
  | number
  | boolean
  | null
  | JsonValue[]
  | { [key: string]: JsonValue };

// ---------------------------------------------------------------------------------------------
// /api/health

export type Health = {
  status: "ok" | "degraded";
  app_version: string;
  server_time_utc: string;
  display_timezone: string;
  database: "ok" | "error";
  schema_revision: string | null;
  schema_head: string;
  live_provider: string;
  live_provider_configured: boolean;
  milestone: string;
  data_imported: boolean;
  data: {
    matches: number;
    divisions: number;
    latest_match_date: string | null;
    last_refresh: { status: string; started_at: string | null; finished_at: string | null } | null;
  };
};

// ---------------------------------------------------------------------------------------------
// /api/coverage

export type CoverageRow = {
  division: string;
  division_name: string;
  tier: number;
  scope: string;
  layout: string;
  season: string;
  season_label: string;
  matches: number;
  teams: number;
  expected_matches: number;
  first_date: string;
  last_date: string;
  issues_error: number;
  issues_warning: number;
  raw_revision: number | null;
  retrieved_at: string | null;
  /** European leagues only: repeat meetings held back (D-022). */
  held_back?: number;
  /** European leagues only: matches excluded from models. */
  model_excluded?: number;
  /** Percentage of matches with each statistic, e.g. pct_shots. */
  [pct: `pct_${string}`]: number;
  /** Percentage of matches with a complete price set, e.g. "odds::Pinnacle closing 1X2";
   * null outside the top divisions. */
  [odds: `odds::${string}`]: number | null;
};

export type CoverageResponse = { rows: CoverageRow[] };

// ---------------------------------------------------------------------------------------------
// /api/refresh-history and /api/import-issues

export type JobRun = {
  id: number;
  job: string;
  status: string;
  started_at: string | null;
  finished_at: string | null;
  error: string | null;
  detail: JsonValue | null;
};

export type RefreshHistory = { runs: JobRun[] };

export type ImportIssue = {
  severity: string;
  code: string;
  file: string;
  row: number | null;
  detail: string | null;
};

export type ImportIssues = {
  summary: { severity: string; code: string; count: number }[];
  issues: ImportIssue[];
};

// ---------------------------------------------------------------------------------------------
// Predictions (/api/fixtures, /api/predictions/{id}, /api/past)

export type RecordType = "genuine_prematch" | "retrospective_backtest" | "exploratory" | "unavailable";

/** "early" (no lineups or injuries) or "lineup". */
export type Edition = "early" | "lineup" | (string & {});

export type OverUnder = { O: number; U: number };

/** Every market the saved record holds. Over/under lines are keyed "OU_0.5" … "OU_4.5". */
export type Probabilities = {
  "1X2"?: { H: number; D: number; A: number };
  BTTS?: { Y: number; N: number };
  expected_goals?: { home: number; away: number };
  top_scores?: { score: string; p: number }[];
  [line: `OU_${string}`]: OverUnder | undefined;
};

/** The ordinary pick per market: the most likely outcome. Not a betting selection. */
export type Selections = Record<string, { pick: string; p: number }>;

export type DataFreshness = {
  /** Domestic models: latest result in the training data, per division. */
  latest_result_by_division?: Record<string, string>;
  /** European model. */
  latest_uefa_result?: string;
  latest_domestic_result?: string;
  [key: string]: JsonValue | undefined;
};

export type MarketOutcome = { pick: string; actual: string; correct: boolean; p_actual: number };

export type Settlement = {
  home_goals: number | null;
  away_goals: number | null;
  status: "settled" | "void" | "postponed" | (string & {});
  outcomes: Record<string, MarketOutcome>;
  settled_at_utc: string;
};

/** Whether the record used bookmakers' prices ("model_and_market") or not ("model_only"). */
export type Basis = "model_and_market" | "model_only";

/** Only on records made with real bookmakers' prices. No selection rule is published yet, so
 * `selections` is always empty for now. */
export type ValueSelection = {
  status: "not_published" | (string & {});
  selections: JsonValue[];
  note: string;
};

export type PredictionCard = {
  id: number;
  /** The fixture's record of record: the first model in the group's list that has a genuine
   * pre-match record for it. */
  is_primary: boolean;
  basis: Basis;
  fixture_ref: string;
  competition: string;
  competition_name: string;
  season: string;
  match_date: string;
  kickoff_utc: string | null;
  home_team: string;
  away_team: string;
  home_name: string;
  away_name: string;
  edition: Edition;
  record_type: RecordType;
  model_name: string;
  model_version: string;
  created_at_utc: string;
  training_cutoff_utc: string;
  probabilities: Probabilities;
  selections: Selections;
  data_freshness: DataFreshness;
  missing_fields: string[];
  notes: string | null;
  settlement: Settlement | null;
  value_selection?: ValueSelection;
};

export type EditionRef = {
  id: number;
  edition: Edition;
  model_name: string;
  basis: Basis;
  is_primary: boolean;
  created_at_utc: string;
  record_type: RecordType;
  reason: string | null;
};

export type RecentMatch = {
  date: string;
  competition: string;
  venue: "H" | "A";
  opponent: string;
  score: string;
  result: "W" | "D" | "L";
};

/** What a model + market record took from the bookmakers: de-vigged consensus probabilities
 * and where the prices came from. Never the prices themselves. */
export type MarketUsed = {
  probabilities: { "1X2"?: { H: number; D: number; A: number }; "OU_2.5"?: OverUnder };
  books: string[];
  /** The prices were quoted no later than this (UTC). For a live record it is when the price
   * file was downloaded, not when the bookmakers' prices were collected. */
  quote_time_utc: string | null;
  provenance: { source?: string; raw_sha256?: string; raw_file_id?: number; retrieved_at?: string };
  devig: string | null;
  phase: string | null;
  /** Weight given to the prices, per market (1 = the prices alone). */
  weights: Partial<Record<"1X2" | "OU_2.5", number>>;
  base_model: string | null;
};

/** The same fixture's record with the other basis (pure model ↔ model + market). */
export type Counterpart = Pick<
  PredictionCard,
  | "id"
  | "is_primary"
  | "basis"
  | "edition"
  | "record_type"
  | "model_name"
  | "model_version"
  | "created_at_utc"
  | "probabilities"
  | "selections"
>;

export type PredictionDetail = PredictionCard & {
  /** The feature snapshot saved with the prediction (window sizes, model parameters, …). */
  features: Record<string, JsonValue>;
  /** Model + market records only. */
  market: MarketUsed | null;
  counterpart: Counterpart | null;
  /** Every saved edition for this fixture, oldest first. */
  editions: EditionRef[];
  /** Each side's last results before the training cutoff (no hindsight). */
  recent: { home: RecentMatch[]; away: RecentMatch[] };
  /** Plain-English summary built only from the saved record's numbers. */
  explanation: string;
};

export type FixturesResponse = {
  generated_at_utc: string;
  competitions: { code: string; name: string; fixtures: PredictionCard[] }[];
};

export type PastResponse = { record_type: RecordType; matches: PredictionCard[] };

// ---------------------------------------------------------------------------------------------
// /api/performance

export type PerformanceMarket = {
  n: number;
  correct: number;
  accuracy: number | null;
  mean_p_actual: number | null;
};

export type PerformanceRecord = {
  published: number;
  settled: number;
  pending: number;
  last_settled_at_utc: string | null;
  markets: Record<string, PerformanceMarket>;
};

export type Performance = PerformanceRecord & {
  primary_only: boolean;
  /** Ordered preference per competition group ("domestic", "uefa"). */
  primary_models: Record<string, string[]>;
  /** Model + market and model-only records, each fixture once per basis. */
  by_basis: Record<Basis, PerformanceRecord & { models: string[] }>;
  /** Both bases on the fixtures that have both. */
  paired: { fixtures: number } & Record<Basis, PerformanceRecord>;
};

// ---------------------------------------------------------------------------------------------
// Backtests

export type MarketMetrics = {
  /** 1X2 only. */
  rps?: number;
  log_loss: number;
  brier: number;
  accuracy: number;
  n: number;
  /** Block-bootstrap 95% interval for the market's primary metric (summary endpoint only). */
  primary_ci95?: [number, number];
};

export type Headline = {
  fixtures_total: number;
  predicted: number;
  excluded: number;
  ineligible: number;
  /** Per market: "1X2", "BTTS", "OU_0.5" … "OU_4.5" (only those the model predicts). */
  [market: string]: number | MarketMetrics;
};

/** A market's metrics from a headline, or undefined when the run has none for it. */
export function headlineMarket(h: Headline | null | undefined, market: string): MarketMetrics | undefined {
  const m = h?.[market];
  return typeof m === "object" && m !== null ? m : undefined;
}

export type BacktestRun = {
  run_id: string;
  kind: "backtests" | "backtests_euro" | (string & {});
  model: string | null;
  split: string | null;
  seasons: string[] | null;
  leagues: string[] | null;
  created_at_utc: string | null;
  headline: Headline | null;
};

export type BacktestsResponse = { runs: BacktestRun[] };

export type CalibrationBin = {
  bin_low: number;
  bin_high: number;
  n: number;
  mean_predicted: number;
  observed: number;
};

export type BacktestSummary = {
  run_id: string;
  headline: Headline;
  /** Keyed "1X2_home", "1X2_draw", "1X2_away", "OU_2.5", "BTTS", … */
  calibration: Record<string, CalibrationBin[]>;
};

/** Shape of footy.modeling.benchmark.benchmark(). A saved report may wrap or extend it, so
 * read it defensively. */
export type BenchmarkResult = {
  fixtures_predicted?: number;
  shin?: Record<string, BenchmarkComparison>;
  power?: Record<string, BenchmarkComparison>;
  gate?: {
    primary: string;
    model_beats_market: boolean;
    significant: boolean;
    label: string | null;
  };
};

export type BenchmarkComparison = {
  fixtures: number;
  coverage_of_predicted: number;
  model_rps?: number;
  market_rps?: number;
  model_log_loss?: number;
  market_log_loss?: number;
  difference: number;
  difference_ci95: [number, number];
  sources?: Record<string, number>;
};

// ---------------------------------------------------------------------------------------------
// Endpoints: typed URLs. `Endpoint<T>` is a plain string that remembers its response type.

declare const responseType: unique symbol;
export type Endpoint<T> = string & { readonly [responseType]?: T };

type QueryValue = string | number | boolean | null | undefined;

function endpoint<T>(path: string, query?: Record<string, QueryValue>): Endpoint<T> {
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(query ?? {})) {
    if (v !== null && v !== undefined && v !== "") params.set(k, String(v));
  }
  const qs = params.toString();
  return (qs ? `${path}?${qs}` : path) as Endpoint<T>;
}

const seg = (s: string | number) => encodeURIComponent(String(s));

export type PastQuery = { competition?: string; record_type?: RecordType; limit?: number };
export type SummaryQuery = { league?: string; season?: string };

export const endpoints = {
  health: () => endpoint<Health>("/api/health"),
  coverage: () => endpoint<CoverageResponse>("/api/coverage"),
  refreshHistory: (limit?: number) => endpoint<RefreshHistory>("/api/refresh-history", { limit }),
  importIssues: () => endpoint<ImportIssues>("/api/import-issues"),
  fixtures: (days?: number) => endpoint<FixturesResponse>("/api/fixtures", { days }),
  prediction: (id: number | string) => endpoint<PredictionDetail>(`/api/predictions/${seg(id)}`),
  past: (q: PastQuery = {}) => endpoint<PastResponse>("/api/past", q),
  performance: () => endpoint<Performance>("/api/performance"),
  backtests: () => endpoint<BacktestsResponse>("/api/backtests"),
  backtestSummary: (runId: string, q: SummaryQuery = {}) =>
    endpoint<BacktestSummary>(`/api/backtests/${seg(runId)}/summary`, q),
  /** Saved JSON reports, e.g. "benchmark". The shape depends on the report. */
  report: <T = JsonValue>(name: string) => endpoint<T>(`/api/reports/${seg(name)}`),
};

/** File downloads: plain links, not fetched by the app. */
export const downloads = {
  ledger: (runId: string, format: "csv" | "json") => `/api/backtests/${seg(runId)}/ledger.${format}`,
};

// ---------------------------------------------------------------------------------------------
// Fetching

export class ApiError extends Error {
  /** HTTP status, or null when the API couldn't be reached at all. */
  readonly status: number | null;
  readonly url: string;

  constructor(message: string, status: number | null, url: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.url = url;
  }

  get notFound(): boolean {
    return this.status === 404;
  }
}

function isAbort(err: unknown): boolean {
  return err instanceof DOMException && err.name === "AbortError";
}

async function requestJson<T>(url: string, signal: AbortSignal): Promise<T> {
  let res: Response;
  try {
    res = await fetch(url, { signal, headers: { Accept: "application/json" } });
  } catch (err) {
    if (isAbort(err)) throw err;
    throw new ApiError("Can't reach the API. Check your connection and try again.", null, url);
  }
  const type = res.headers.get("content-type") ?? "";
  if (!res.ok) {
    let detail = "";
    if (type.includes("json")) {
      try {
        const body = (await res.json()) as { detail?: unknown };
        if (typeof body.detail === "string") detail = body.detail;
      } catch {
        // keep the status-only message
      }
    }
    const message =
      res.status === 404
        ? `Not found${detail ? `: ${detail}` : ""}.`
        : res.status >= 500
          ? `The API had a problem (error ${res.status}).`
          : `The API returned ${res.status}${detail ? `: ${detail}` : ""}.`;
    throw new ApiError(message, res.status, url);
  }
  if (!type.includes("json")) {
    throw new ApiError("The API sent something other than JSON. Is it running?", res.status, url);
  }
  return (await res.json()) as T;
}

// Requests for the same URL in flight at the same time share one network request. It is
// aborted only when every caller has aborted (deferred a tick, so React's StrictMode
// mount-unmount-mount doesn't fire a second request).
type Inflight = { promise: Promise<unknown>; controller: AbortController; refs: number; timer?: number };
const inflight = new Map<string, Inflight>();

function sharedRequest<T>(url: string, signal?: AbortSignal): Promise<T> {
  let entry = inflight.get(url);
  if (!entry) {
    const controller = new AbortController();
    const created: Inflight = { controller, refs: 0, promise: Promise.resolve() };
    created.promise = requestJson<T>(url, controller.signal).finally(() => {
      if (inflight.get(url) === created) inflight.delete(url);
    });
    inflight.set(url, created);
    entry = created;
  }
  const e = entry;
  if (e.timer !== undefined) {
    window.clearTimeout(e.timer);
    e.timer = undefined;
  }
  e.refs += 1;
  return new Promise<T>((resolve, reject) => {
    let done = false;
    const onAbort = () => {
      if (done) return;
      done = true;
      e.refs -= 1;
      if (e.refs === 0) {
        e.timer = window.setTimeout(() => {
          if (e.refs === 0) {
            e.controller.abort();
            if (inflight.get(url) === e) inflight.delete(url);
          }
        }, 0);
      }
      reject(new DOMException("Aborted", "AbortError"));
    };
    if (signal?.aborted) {
      onAbort();
      return;
    }
    signal?.addEventListener("abort", onAbort, { once: true });
    e.promise.then(
      (v) => {
        if (done) return;
        done = true;
        signal?.removeEventListener("abort", onAbort);
        resolve(v as T);
      },
      (err: unknown) => {
        if (done) return;
        done = true;
        signal?.removeEventListener("abort", onAbort);
        reject(err);
      },
    );
  });
}

/** Fetch a typed endpoint. Rejects with ApiError, or an AbortError when `signal` aborts. */
export function getJson<T>(url: Endpoint<T>, signal?: AbortSignal): Promise<T> {
  return sharedRequest<T>(url, signal);
}

/** One function per endpoint. */
export const api = {
  health: (signal?: AbortSignal) => getJson(endpoints.health(), signal),
  coverage: (signal?: AbortSignal) => getJson(endpoints.coverage(), signal),
  refreshHistory: (limit?: number, signal?: AbortSignal) =>
    getJson(endpoints.refreshHistory(limit), signal),
  importIssues: (signal?: AbortSignal) => getJson(endpoints.importIssues(), signal),
  fixtures: (days?: number, signal?: AbortSignal) => getJson(endpoints.fixtures(days), signal),
  prediction: (id: number | string, signal?: AbortSignal) =>
    getJson(endpoints.prediction(id), signal),
  past: (q?: PastQuery, signal?: AbortSignal) => getJson(endpoints.past(q), signal),
  performance: (signal?: AbortSignal) => getJson(endpoints.performance(), signal),
  backtests: (signal?: AbortSignal) => getJson(endpoints.backtests(), signal),
  backtestSummary: (runId: string, q?: SummaryQuery, signal?: AbortSignal) =>
    getJson(endpoints.backtestSummary(runId, q), signal),
  report: <T = JsonValue>(name: string, signal?: AbortSignal) =>
    getJson(endpoints.report<T>(name), signal),
};

// ---------------------------------------------------------------------------------------------
// useApi: fetch on mount and whenever the URL changes.
//
// - The previous request is aborted when the URL changes or the component unmounts.
// - A response that arrives after a newer request was started is ignored.
// - Successful responses are kept for the session. A copy younger than `freshMs` is used
//   without a request (so going back is instant); an older copy is shown at once while a new
//   request runs. If that request fails, the old copy stays, with `error` set, so the page
//   can say it is showing saved data.

type CacheEntry = { data: unknown; at: number };
const cache = new Map<string, CacheEntry>();
const CACHE_LIMIT = 60;

function remember(url: string, data: unknown): CacheEntry {
  const entry = { data, at: Date.now() };
  cache.delete(url);
  cache.set(url, entry);
  while (cache.size > CACHE_LIMIT) {
    const oldest = cache.keys().next().value;
    if (oldest === undefined) break;
    cache.delete(oldest);
  }
  return entry;
}

export type ApiResult<T> = {
  /** The latest data for this URL (possibly an earlier copy; see `error` and `loading`). */
  data: T | undefined;
  /** Set when the latest request failed. With `data` also set, the data is a saved copy. */
  error: ApiError | undefined;
  /** A request for this URL is in flight. */
  loading: boolean;
  /** When `data` was received (ms since epoch). */
  receivedAt: number | undefined;
  /** Fetch again, ignoring any saved copy. */
  reload: () => void;
};

type Snapshot = {
  url: string | null;
  data: unknown;
  error: ApiError | undefined;
  loading: boolean;
  receivedAt: number | undefined;
};

function snapshotFor(url: string | null, freshMs: number): Snapshot {
  const hit = url === null ? undefined : cache.get(url);
  return {
    url,
    data: hit?.data,
    error: undefined,
    loading: url !== null && (hit === undefined || Date.now() - hit.at >= freshMs),
    receivedAt: hit?.at,
  };
}

export function useApi<T>(url: Endpoint<T> | null, options: { freshMs?: number } = {}): ApiResult<T> {
  const freshMs = options.freshMs ?? 30_000;
  const [snap, setSnap] = useState<Snapshot>(() => snapshotFor(url, freshMs));
  const [token, setToken] = useState(0);
  const force = useRef(false);
  const seq = useRef(0);

  // A new URL: show what we already hold for it (or nothing) straight away, in this render.
  let current = snap;
  if (snap.url !== url) {
    current = snapshotFor(url, freshMs);
    setSnap(current);
  }

  useEffect(() => {
    if (url === null) return;
    const hit = cache.get(url);
    if (!force.current && hit && Date.now() - hit.at < freshMs) {
      setSnap((s) => (s.url === url && s.loading ? { ...s, loading: false } : s));
      return;
    }
    force.current = false;
    const id = ++seq.current;
    const controller = new AbortController();
    setSnap((s) => (s.url === url && !s.loading ? { ...s, loading: true } : s));
    sharedRequest<T>(url, controller.signal).then(
      (data) => {
        if (id !== seq.current || controller.signal.aborted) return;
        const entry = remember(url, data);
        setSnap({ url, data, error: undefined, loading: false, receivedAt: entry.at });
      },
      (err: unknown) => {
        if (id !== seq.current || controller.signal.aborted || isAbort(err)) return;
        const error =
          err instanceof ApiError
            ? err
            : new ApiError(err instanceof Error ? err.message : String(err), null, url);
        setSnap((s) => (s.url === url ? { ...s, error, loading: false } : s));
      },
    );
    return () => controller.abort();
  }, [url, token, freshMs]);

  const reload = useCallback(() => {
    force.current = true;
    setToken((n) => n + 1);
  }, []);

  return {
    data: current.data as T | undefined,
    error: current.error,
    loading: current.loading,
    receivedAt: current.receivedAt,
    reload,
  };
}
