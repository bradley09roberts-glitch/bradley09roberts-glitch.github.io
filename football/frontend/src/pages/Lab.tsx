// Brief item 4: the backtest lab. A Hit rates tab (how often each model of record's pick was
// right, per league or competition and market: ./HitRates.tsx), an Explorer tab (every curated
// model's backtest prediction for every match, filtered by league, season, team and model:
// ./LabExplorer.tsx), then Moneyline (1X2), Goal
// Totals and BTTS tabs, and a Machine learning tab (the learners and their verdict:
// ./LabML.tsx); filters for run (model), league, season and line; headline metrics
// with sample size and 95% interval; a calibration chart; the market benchmark from the saved
// report; ledger exports.
//
// Every number comes from /api/backtests, /api/backtests/{run}/summary or a saved report.
// The few things worked out here (Wilson intervals and the average calibration gap in the
// chart, and the difference from the frequency baseline in the run table) are computed from
// those numbers and labelled as such.

import { useEffect, useMemo, useState } from "react";
import {
  downloads,
  endpoints,
  headlineMarket,
  useApi,
  type ApiResult,
  type BacktestRun,
  type BacktestSummary,
  type BenchmarkComparison,
  type BenchmarkResult,
  type JsonValue,
  type MarketMetrics,
} from "../api";
import { Badge } from "../components/Badge";
import { CiStrip } from "../components/CiStrip";
import { CalibrationChart, type CalibrationSeries } from "../components/CalibrationChart";
import {
  Segmented,
  SelectField,
  TableScroll,
  Tabs,
  tabId,
  tabPanelId,
  type SelectGroup,
  type TabDef,
} from "../components/Controls";
import { ApiView, Empty, ErrorView, Loading, StaleNotice } from "../components/StateViews";
import { formatLondon, formatNumber, formatPct, keepNumberWithName, seasonLabel } from "../format";
import { useDocumentTitle } from "../hooks";
import { href, setQuery } from "../router";
import { SNAPSHOT } from "../snapshot";
import type { PageProps } from ".";
import { EXPLORER_KEYS } from "../explorer";
import { HitRatesPanel } from "./HitRates";
import { BacktestExplorer } from "./LabExplorer";
import { MachineLearningPanel } from "./LabML";
import "./Lab.css";

// ---------------------------------------------------------------------------------------------
// Query parameters

type LabTab = "hits" | "explorer" | MarketTab | "ml";
type MarketTab = "moneyline" | "totals" | "btts";
type Outcome = "all" | "home" | "draw" | "away";

const TABS: readonly TabDef<LabTab>[] = [
  { id: "hits", label: "Hit rates", hint: "By league" },
  { id: "explorer", label: "Explorer", hint: "Every match" },
  { id: "moneyline", label: "Moneyline", hint: "1X2" },
  { id: "totals", label: "Goal Totals", hint: "Over/under" },
  { id: "btts", label: "BTTS", hint: "Both score" },
  { id: "ml", label: "Machine learning", hint: "Learners" },
];

const LINES = [0.5, 1.5, 2.5, 3.5, 4.5] as const;
const DEFAULT_LINE = 2.5;
const FILTER_KEYS = ["run", "league", "season", "line", "outcome"] as const;

/** The tab in the address. Without one the lab opens on Hit rates, except for an address
 * with run or filter choices but no tab (written before the Hit rates tab existed, when the
 * match result was the first tab): that one keeps opening the match result. */
function parseTab(query: URLSearchParams): LabTab {
  const raw = query.get("tab");
  if (raw === "hits" || raw === "explorer" || raw === "moneyline" || raw === "totals" || raw === "btts" || raw === "ml") {
    return raw;
  }
  return FILTER_KEYS.some((k) => query.get(k) !== null) ? "moneyline" : "hits";
}

function parseLine(raw: string | null): number {
  const n = raw === null ? NaN : Number(raw);
  return (LINES as readonly number[]).includes(n) ? n : DEFAULT_LINE;
}

function parseOutcome(raw: string | null): Outcome {
  return raw === "home" || raw === "draw" || raw === "away" ? raw : "all";
}

/** Market key in the API: "1X2", "OU_2.5" (Python's f"{k:g}"), "BTTS". */
function marketFor(tab: MarketTab, line: number): string {
  if (tab === "moneyline") return "1X2";
  if (tab === "btts") return "BTTS";
  return `OU_${String(line)}`;
}

function marketTitle(tab: MarketTab, line: number): string {
  if (tab === "moneyline") return "Match result (1X2)";
  if (tab === "btts") return "Both teams to score";
  return `Over/under ${line} goals`;
}

function marketShort(tab: MarketTab, line: number): string {
  if (tab === "moneyline") return "1X2";
  if (tab === "btts") return "BTTS";
  return `O/U ${line}`;
}

// ---------------------------------------------------------------------------------------------
// Names. Reference labels only (mirroring footy.reference and the API's competition names);
// an unknown code is shown as itself.

const COMPETITION_NAMES: Record<string, string> = {
  E0: "Premier League",
  SP1: "La Liga",
  I1: "Serie A",
  D1: "Bundesliga",
  F1: "Ligue 1",
  E1: "Championship",
  SP2: "Segunda División",
  I2: "Serie B",
  D2: "2. Bundesliga",
  F2: "Ligue 2",
  SC0: "Scottish Premiership",
  N1: "Eredivisie",
  B1: "Belgian Pro League",
  P1: "Primeira Liga",
  T1: "Süper Lig",
  G1: "Super League Greece",
  AUT: "Austrian Bundesliga",
  DNK: "Danish Superliga",
  SWZ: "Swiss Super League",
  NOR: "Eliteserien",
  SWE: "Allsvenskan",
  POL: "Ekstraklasa",
  ROU: "Liga I",
  RUS: "Russian Premier League",
  FIN: "Veikkausliiga",
  IRL: "League of Ireland Premier Division",
  UCL: "Champions League",
  UEL: "Europa League",
  UECL: "Conference League",
  UCLQ: "Champions League qualifying",
  UELQ: "Europa League qualifying",
  UECLQ: "Conference League qualifying",
};

/** UEFA competition codes in the European ledgers (footy.modeling.euro.EURO_COMPS). Used
 * for the league filter when a European run's manifest doesn't list its competitions. */
const UEFA_CODES = ["UCL", "UEL", "UECL", "UCLQ", "UELQ", "UECLQ"];

function competitionName(code: string): string {
  return COMPETITION_NAMES[code] ?? code;
}

const MODEL_NAMES: Record<string, string> = {
  baseline_frequency: "League frequency",
  baseline_poisson: "Independent Poisson",
  baseline_elo: "Elo",
  dixon_coles: "Dixon–Coles",
};

export function modelName(id: string | null | undefined): string {
  if (!id) return "Unnamed model";
  const known = MODEL_NAMES[id];
  if (known) return id.startsWith("baseline_") ? `${known} (baseline)` : known;
  return id.replace(/__/g, " · ").replace(/_/g, " ");
}

const SPLIT_NAMES: Record<string, string> = {
  validation: "Validation",
  test: "Test",
  train: "Training",
  burn_in: "Burn-in",
  live_retro: "2026/27 so far",
};

function splitName(split: string | null | undefined): string {
  if (!split) return "Unknown split";
  return SPLIT_NAMES[split] ?? split.replace(/_/g, " ");
}

function kindName(kind: string): string {
  if (kind === "backtests") return "Domestic leagues";
  if (kind === "backtests_euro") return "UEFA competitions";
  return kind.replace(/_/g, " ");
}

function runSeasons(run: BacktestRun): string {
  return run.seasons?.length ? run.seasons.map(seasonLabel).join(", ") : "";
}

function runLabel(run: BacktestRun): string {
  const seasons = runSeasons(run);
  return `${modelName(run.model ?? run.run_id)} · ${splitName(run.split)}${seasons ? ` ${seasons}` : ""}`;
}

// ---------------------------------------------------------------------------------------------
// Runs

/** Market metrics for a run's saved (whole-run) headline; undefined if it has none. */
function runMarket(run: BacktestRun, market: string): MarketMetrics | undefined {
  return headlineMarket(run.headline, market);
}

const SPLIT_ORDER: Record<string, number> = { test: 0, validation: 1, live_retro: 2 };

/** Default run: the selected model's test run if there is one. Otherwise a domestic run
 * (test before validation) that predicts every market, not a baseline, with the lowest
 * saved 1X2 RPS. */
function defaultRun(runs: readonly BacktestRun[], selected: string | undefined): BacktestRun | undefined {
  const score = (r: BacktestRun): number[] => {
    const full = ["1X2", "BTTS", "OU_2.5"].every((m) => runMarket(r, m) !== undefined);
    return [
      selected && r.model === selected ? 0 : 1,
      r.kind === "backtests" ? 0 : 1,
      SPLIT_ORDER[r.split ?? ""] ?? 3,
      full ? 0 : 1,
      r.model?.startsWith("baseline_") ? 1 : 0,
      runMarket(r, "1X2")?.rps ?? 9,
    ];
  };
  const before = (a: number[], b: number[]) => {
    for (let i = 0; i < a.length; i++) {
      const x = a[i] ?? 0;
      const y = b[i] ?? 0;
      if (x !== y) return x < y;
    }
    return false;
  };
  let best: BacktestRun | undefined;
  let bestScore: number[] = [];
  for (const r of runs) {
    const s = score(r);
    if (!best || before(s, bestScore)) {
      best = r;
      bestScore = s;
    }
  }
  return best;
}

function runGroups(runs: readonly BacktestRun[], market: string, short: string): SelectGroup[] {
  const byKind = new Map<string, BacktestRun[]>();
  for (const r of runs) {
    const list = byKind.get(r.kind);
    if (list) list.push(r);
    else byKind.set(r.kind, [r]);
  }
  const kinds = [...byKind.keys()].sort((a, b) => (a === "backtests" ? -1 : b === "backtests" ? 1 : a.localeCompare(b)));
  return kinds.map((kind) => ({
    label: kindName(kind),
    options: (byKind.get(kind) ?? [])
      .slice()
      .sort(
        (a, b) =>
          (SPLIT_ORDER[a.split ?? ""] ?? 3) - (SPLIT_ORDER[b.split ?? ""] ?? 3) ||
          runLabel(a).localeCompare(runLabel(b)),
      )
      .map((r) => ({
        value: r.run_id,
        label: `${runLabel(r)}${r.headline && !runMarket(r, market) ? ` (no ${short})` : ""}`,
      })),
  }));
}

// ---------------------------------------------------------------------------------------------
// Saved reports (read defensively: a report's shape can grow)

type ModelSelection = {
  generated_at_utc?: string;
  rule?: string;
  selected?: string;
  best_composite_on_validation?: string;
  best_1x2_accuracy_on_validation?: string;
};

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}

function readSelection(v: JsonValue | undefined): ModelSelection | undefined {
  if (!isRecord(v)) return undefined;
  const str = (k: string) => (typeof v[k] === "string" ? (v[k] as string) : undefined);
  return {
    generated_at_utc: str("generated_at_utc"),
    rule: str("rule"),
    selected: str("selected"),
    best_composite_on_validation: str("best_composite_on_validation"),
    best_1x2_accuracy_on_validation: str("best_1x2_accuracy_on_validation"),
  };
}

function isBenchmark(v: unknown): v is BenchmarkResult {
  return isRecord(v) && (isRecord(v.gate) || isRecord(v.shin));
}

/** The saved benchmark report is { validation: result, test: result }; a bare result is
 * also accepted. Test first, because the gate is decided on test. */
function benchmarkSplits(v: JsonValue | undefined): { split: string | null; result: BenchmarkResult }[] {
  if (isBenchmark(v)) return [{ split: null, result: v }];
  if (!isRecord(v)) return [];
  return Object.entries(v)
    .filter((e): e is [string, BenchmarkResult] => isBenchmark(e[1]))
    .sort(([a], [b]) => (SPLIT_ORDER[a] ?? 3) - (SPLIT_ORDER[b] ?? 3))
    .map(([split, result]) => ({ split, result }));
}

// ---------------------------------------------------------------------------------------------
// Page

export function Lab({ query }: PageProps) {
  useDocumentTitle("Backtest lab");
  const tab = parseTab(query);

  return (
    <>
      <header className="page-head">
        <h1 className="page-title">Backtest lab</h1>
        <p className="lede">
          Past seasons replayed week by week: each fixture was predicted using only results final
          before its decision time. These are retrospective results, kept apart from the{" "}
          <a href={href("/performance")}>genuine pre-match record</a>.
        </p>
      </header>

      <Tabs
        tabs={TABS}
        active={tab}
        idBase="lab"
        label="Lab view"
        // Always written, so an address keeps its tab when its other choices are cleared. The
        // explorer's choices and the market tabs' share some names (season), so moving to or
        // from the explorer leaves the other side's choices behind.
        onChange={(id) => {
          const crossing = (id === "explorer") !== (tab === "explorer");
          const clear = crossing ? Object.fromEntries([...FILTER_KEYS, ...EXPLORER_KEYS].map((k) => [k, null])) : {};
          setQuery({ ...clear, tab: id }, { replace: false });
        }}
      />

      <div className="lab-panel" id={tabPanelId("lab")} role="tabpanel" aria-labelledby={tabId("lab", tab)}>
        {tab === "hits" ? (
          <HitRatesPanel query={query} />
        ) : tab === "explorer" ? (
          <BacktestExplorer query={query} />
        ) : tab === "ml" ? (
          <MachineLearningPanel />
        ) : (
          <MarketLab tab={tab} query={query} />
        )}
      </div>
    </>
  );
}

/** The Moneyline, Goal Totals and BTTS tabs: one run, filtered. */
function MarketLab({ tab, query }: { tab: MarketTab; query: URLSearchParams }) {
  const line = parseLine(query.get("line"));
  const outcome = parseOutcome(query.get("outcome"));
  const market = marketFor(tab, line);

  const runsRes = useApi(endpoints.backtests());
  const selectionRes = useApi(endpoints.report<JsonValue>("model_selection"));
  const selection = readSelection(selectionRes.data);
  const selectionSettled = selectionRes.data !== undefined || selectionRes.error !== undefined;

  const runs = useMemo(() => runsRes.data?.runs ?? [], [runsRes.data]);
  const requestedId = query.get("run");
  let run: BacktestRun | undefined;
  let missingRun = false;
  if (runsRes.data) {
    if (requestedId) {
      run = runs.find((r) => r.run_id === requestedId);
      missingRun = run === undefined;
    }
    // Wait for the selection report (or its 404) so the default is chosen once.
    if (!run && selectionSettled) run = defaultRun(runs, selection?.selected);
  }

  const leagueCodes = run ? (run.leagues ?? (run.kind === "backtests_euro" ? UEFA_CODES : [])) : [];
  const leagueParam = query.get("league");
  const league = leagueParam && leagueCodes.includes(leagueParam) ? leagueParam : null;
  const seasons = run?.seasons ?? [];
  const seasonParam = query.get("season");
  const season = seasonParam && seasons.includes(seasonParam) ? seasonParam : null;
  const filterLabel = `${league ? competitionName(league) : "All leagues"} · ${season ? seasonLabel(season) : "All seasons"}`;

  const summaryUrl = run
    ? endpoints.backtestSummary(run.run_id, { league: league ?? undefined, season: season ?? undefined })
    : null;
  const summary = useApi(summaryUrl);

  // Refetch keeps the frame: while a filter change loads, the last result for the same run
  // stays on screen, dimmed and labelled with the filters it belongs to.
  const [kept, setKept] = useState<{ url: string; runId: string; label: string; data: BacktestSummary } | null>(null);
  if (summary.data && summaryUrl && run && (kept?.url !== summaryUrl || kept.data !== summary.data)) {
    setKept({ url: summaryUrl, runId: run.run_id, label: filterLabel, data: summary.data });
  }

  const hasFilters = FILTER_KEYS.some((k) => query.get(k) !== null);
  const reset = () => setQuery({ tab, run: null, league: null, season: null, line: null, outcome: null });
  const short = marketShort(tab, line);
  const groups = useMemo(() => runGroups(runs, market, short), [runs, market, short]);

  return (
    <>
      {runsRes.data === undefined ? (
        runsRes.error ? (
          <ErrorView error={runsRes.error} what="the backtest runs" onRetry={runsRes.reload} retrying={runsRes.loading} />
        ) : (
          <Loading label="Loading backtest runs…" lines={2} />
        )
      ) : runs.length === 0 ? (
        <Empty title="No backtest runs saved yet">
          <p>Runs appear here once a walk-forward backtest has written its ledger and manifest.</p>
        </Empty>
      ) : (
        <>
          {runsRes.error && (
            <StaleNotice onRetry={runsRes.reload} retrying={runsRes.loading}>
              The run list couldn't be refreshed: {runsRes.error.message}
            </StaleNotice>
          )}

          <section className="card lab-filters" aria-label="Filters">
            <div className="ctl-row">
              <SelectField
                id="lab-run"
                label="Model run"
                wide
                value={run?.run_id ?? ""}
                groups={groups}
                options={run ? undefined : [{ value: "", label: "Choosing…", disabled: true }]}
                disabled={!run}
                onChange={(v) => setQuery({ run: v, league: null, season: null })}
              />
              <SelectField
                id="lab-league"
                label={run?.kind === "backtests_euro" ? "Competition" : "League"}
                value={league ?? ""}
                disabled={!run || leagueCodes.length < 2}
                options={[
                  { value: "", label: run?.kind === "backtests_euro" ? "All competitions" : "All leagues" },
                  ...leagueCodes.map((c) => ({ value: c, label: competitionName(c) })),
                ]}
                onChange={(v) => setQuery({ league: v || null })}
              />
              <SelectField
                id="lab-season"
                label="Season"
                value={season ?? ""}
                disabled={!run || seasons.length < 2}
                options={[
                  { value: "", label: seasons.length === 1 ? `All (${seasonLabel(seasons[0] ?? "")})` : "All seasons" },
                  ...(seasons.length > 1 ? seasons.map((s) => ({ value: s, label: seasonLabel(s) })) : []),
                ]}
                onChange={(v) => setQuery({ season: v || null })}
              />
              {tab === "totals" && (
                <Segmented
                  label="Line"
                  value={line}
                  options={LINES.map((l) => ({ value: l, label: String(l) }))}
                  onChange={(l) => setQuery({ line: l === DEFAULT_LINE ? null : l })}
                />
              )}
              {tab === "moneyline" && (
                <Segmented
                  label="Chart outcome"
                  value={outcome}
                  options={[
                    { value: "all", label: "All" },
                    { value: "home", label: "Home" },
                    { value: "draw", label: "Draw" },
                    { value: "away", label: "Away" },
                  ]}
                  onChange={(o) => setQuery({ outcome: o === "all" ? null : o })}
                />
              )}
            </div>
            <div className="toolbar lab-toolbar">
              {run ? <RunFacts run={run} selected={selection?.selected} /> : <span className="muted small">Choosing a run…</span>}
              <button type="button" onClick={reset} disabled={!hasFilters}>
                Reset filters
              </button>
            </div>
            {run && run.kind === "backtests_euro" && !run.leagues && (
              <p className="muted small">
                This run's manifest doesn't list its competitions or seasons, so the competition
                list shows every UEFA code; one the run doesn't contain returns no fixtures.
              </p>
            )}
          </section>

          {missingRun && (
            <StaleNotice>
              There's no saved run called <code>{requestedId}</code>. Showing the default run
              instead.
            </StaleNotice>
          )}

          {run && summaryUrl ? (
            <SummaryArea
              summary={summary}
              kept={kept && kept.runId === run.run_id && kept.url !== summaryUrl ? kept : null}
              filterLabel={filterLabel}
              tab={tab}
              line={line}
              outcome={outcome}
              market={market}
              run={run}
            />
          ) : (
            <Loading label="Loading the backtest summary…" lines={2} />
          )}

          {run && <RunComparison runs={runs} current={run} tab={tab} line={line} market={market} />}
        </>
      )}

      <BenchmarkPanel tab={tab} line={line} selection={selection} />

      {run && <ExportLinks run={run} />}
    </>
  );
}

function RunFacts({ run, selected }: { run: BacktestRun; selected: string | undefined }) {
  const leagues = run.leagues?.length ? run.leagues.map((c) => keepNumberWithName(competitionName(c))).join(", ") : null;
  return (
    <p className="muted small lab-facts">
      {selected && run.model === selected && (
        <>
          <Badge tone="good" title="Chosen by the evaluation report on validation 2023/24">
            Selected model
          </Badge>{" "}
        </>
      )}
      {kindName(run.kind)}
      {leagues && ` · ${leagues}`}
      {run.created_at_utc && ` · run ${formatLondon(run.created_at_utc)}`}
      <span className="visually-hidden">. Run id </span>
      <code className="lab-run-id"> {run.run_id}</code>
    </p>
  );
}

// ---------------------------------------------------------------------------------------------
// Summary: headline metrics and calibration

type Kept = { url: string; runId: string; label: string; data: BacktestSummary };

function SummaryArea({
  summary,
  kept,
  filterLabel,
  tab,
  line,
  outcome,
  market,
  run,
}: {
  summary: ApiResult<BacktestSummary>;
  kept: Kept | null;
  filterLabel: string;
  tab: MarketTab;
  line: number;
  outcome: Outcome;
  market: string;
  run: BacktestRun;
}) {
  const view = (data: BacktestSummary, label: string) => (
    <SummaryView data={data} label={label} tab={tab} line={line} outcome={outcome} market={market} run={run} />
  );
  if (summary.data === undefined && summary.loading && kept) {
    return (
      <>
        <div className="notice-info lab-updating" role="status">
          Loading <strong>{filterLabel}</strong>… still showing {kept.label} until it arrives.
        </div>
        <div className="api-view refreshing" aria-busy="true">
          {view(kept.data, kept.label)}
        </div>
      </>
    );
  }
  return (
    <ApiView result={summary} what="the backtest summary" loadingLines={2}>
      {(data) => view(data, filterLabel)}
    </ApiView>
  );
}

const fmt4 = (v: number | undefined) => formatNumber(v, 4);

function SummaryView({
  data,
  label,
  tab,
  line,
  outcome,
  market,
  run,
}: {
  data: BacktestSummary;
  label: string;
  tab: MarketTab;
  line: number;
  outcome: Outcome;
  market: string;
  run: BacktestRun;
}) {
  const h = data.headline;
  const m = headlineMarket(h, market);
  const title = marketTitle(tab, line);

  if (h.predicted === 0) {
    return (
      <Empty title={`No predicted fixtures for ${label}`}>
        <p>
          {h.fixtures_total > 0
            ? `The run has ${h.fixtures_total.toLocaleString("en-GB")} fixtures here, but none was predicted (${h.excluded} excluded, ${h.ineligible} without enough history).`
            : "This run has no fixtures for these filters."}
        </p>
        <button type="button" onClick={() => setQuery({ league: null, season: null })}>
          Show every league and season
        </button>
      </Empty>
    );
  }
  if (!m) {
    return (
      <Empty title={`${modelName(run.model)} doesn't predict ${title.toLowerCase()}`}>
        <p>This run has no {marketShort(tab, line)} probabilities. Choose another run, or another tab.</p>
      </Empty>
    );
  }

  const primary =
    tab === "moneyline"
      ? { name: "Ranked probability score", abbr: "RPS", value: m.rps }
      : { name: "Log loss", abbr: "", value: m.log_loss };
  const ci = m.primary_ci95;

  return (
    <div className="lab-summary">
      <section className="card" aria-labelledby="lab-headline">
        <h2 id="lab-headline">
          {title} <span className="muted lab-h-sub">· {label}</span>
        </h2>
        <p className="muted small">
          <strong className="lab-n">{m.n.toLocaleString("en-GB")}</strong> fixtures with a prediction
          {" · "}
          {h.predicted.toLocaleString("en-GB")} of {h.fixtures_total.toLocaleString("en-GB")} predicted
          {h.excluded > 0 && ` · ${h.excluded.toLocaleString("en-GB")} excluded (awarded or bare results)`}
          {h.ineligible > 0 && ` · ${h.ineligible.toLocaleString("en-GB")} without enough team history`}
        </p>
        <dl className="lab-metrics">
          <div className="lab-metric lab-metric-primary">
            <dt>
              {primary.name}
              {primary.abbr && <span className="muted"> ({primary.abbr})</span>}
            </dt>
            <dd>
              <span className="lab-metric-value">{fmt4(primary.value)}</span>
              {ci ? (
                <span className="lab-metric-ci">
                  95% interval {fmt4(ci[0])} to {fmt4(ci[1])}
                </span>
              ) : (
                <span className="lab-metric-ci">No interval saved</span>
              )}
              <span className="lab-metric-note">Lower is better. Interval: bootstrap over whole matchweeks.</span>
            </dd>
          </div>
          <div className="lab-metric">
            <dt>Pick accuracy</dt>
            <dd>
              <span className="lab-metric-value">{formatPct(m.accuracy, 1)}</span>
              <span className="lab-metric-note">
                {tab === "moneyline" ? "Most likely result" : "Likelier side"} was correct
              </span>
            </dd>
          </div>
          <div className="lab-metric">
            <dt>Brier score</dt>
            <dd>
              <span className="lab-metric-value">{fmt4(m.brier)}</span>
              <span className="lab-metric-note">Lower is better</span>
            </dd>
          </div>
          {tab === "moneyline" && (
            <div className="lab-metric">
              <dt>Log loss</dt>
              <dd>
                <span className="lab-metric-value">{fmt4(m.log_loss)}</span>
                <span className="lab-metric-note">Lower is better</span>
              </dd>
            </div>
          )}
        </dl>
        <p className="muted small lab-signal-note">
          These measure how good the probabilities were. They say nothing about betting edge,
          which needs prices: see the market benchmark below.
        </p>
      </section>

      <CalibrationSection data={data} tab={tab} line={line} outcome={outcome} label={label} />
    </div>
  );
}

const OUTCOME_SERIES: { key: string; outcome: Exclude<Outcome, "all">; label: string; tone: CalibrationSeries["tone"] }[] = [
  { key: "1X2_home", outcome: "home", label: "Home win", tone: "home" },
  { key: "1X2_draw", outcome: "draw", label: "Draw", tone: "draw" },
  { key: "1X2_away", outcome: "away", label: "Away win", tone: "away" },
];

function CalibrationSection({
  data,
  tab,
  line,
  outcome,
  label,
}: {
  data: BacktestSummary;
  tab: MarketTab;
  line: number;
  outcome: Outcome;
  label: string;
}) {
  let series: CalibrationSeries[];
  if (tab === "moneyline") {
    series = OUTCOME_SERIES.filter((s) => outcome === "all" || s.outcome === outcome).map((s) => ({
      key: s.key,
      label: s.label,
      tone: s.tone,
      bins: data.calibration[s.key] ?? [],
    }));
  } else if (tab === "totals") {
    const key = `OU_${String(line)}`;
    series = [{ key, label: `Over ${line} goals`, tone: "single", bins: data.calibration[key] ?? [] }];
  } else {
    series = [{ key: "BTTS", label: "Both teams score", tone: "single", bins: data.calibration.BTTS ?? [] }];
  }
  const withBins = series.filter((s) => s.bins.length > 0);
  const many = withBins.length > 1;

  return (
    <section className="card lab-cal" aria-labelledby="lab-calibration">
      <h2 id="lab-calibration">
        Calibration <span className="muted lab-h-sub">· {label}</span>
      </h2>
      <p className="muted small">
        When the model said 40%, did it happen about 40% of the time? Points on the diagonal are
        well calibrated; above it, the outcome happened more often than predicted.
      </p>
      {withBins.length === 0 ? (
        <p className="muted">No calibration bins for this market and these filters.</p>
      ) : (
        <>
          <CalibrationChart
            series={withBins}
            showIntervals={!many}
            title={`Calibration of ${withBins.map((s) => s.label.toLowerCase()).join(", ")}: observed frequency against predicted probability`}
          />
          {many && <p className="muted small">Pick one outcome above to see its 95% intervals.</p>}
        </>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------------------------
// Every run on this market (from the saved manifests: whole runs, no filters)

/** Runs listed before "Show all". */
const RUNS_SHOWN = 10;

function RunComparison({
  runs,
  current,
  tab,
  line,
  market,
}: {
  runs: readonly BacktestRun[];
  current: BacktestRun;
  tab: MarketTab;
  line: number;
  market: string;
}) {
  const [showAll, setShowAll] = useState(false);
  type Row = { run: BacktestRun; m: MarketMetrics; group: string; primary: number | undefined };
  const primaryOf = (m: MarketMetrics) => (tab === "moneyline" ? m.rps : m.log_loss);
  const rows: Row[] = [];
  const without: BacktestRun[] = [];
  for (const r of runs) {
    const m = runMarket(r, market);
    if (m) rows.push({ run: r, m, group: `${r.kind}|${r.split}|${(r.seasons ?? []).join(",")}`, primary: primaryOf(m) });
    else without.push(r);
  }
  if (rows.length === 0) return null;
  const groupOrder = [...new Set(rows.map((r) => r.group))].sort((a, b) => {
    const [ka, sa] = a.split("|");
    const [kb, sb] = b.split("|");
    return (
      (ka === "backtests" ? 0 : 1) - (kb === "backtests" ? 0 : 1) ||
      (SPLIT_ORDER[sa ?? ""] ?? 3) - (SPLIT_ORDER[sb ?? ""] ?? 3) ||
      a.localeCompare(b)
    );
  });
  const baselineIn = (group: string) =>
    rows.find((r) => r.group === group && r.run.model === "baseline_frequency")?.primary;
  // Every row in display order (group by group, best first), marking where a group starts.
  const ordered = groupOrder.flatMap((g) =>
    rows
      .filter((r) => r.group === g)
      .sort((a, b) => (a.primary ?? Infinity) - (b.primary ?? Infinity))
      .map((r, i) => ({ ...r, groupStart: i === 0, base: baselineIn(g) })),
  );
  // A long list shows its first rows (and the chosen run, wherever it is) until "Show all".
  const limited = !showAll && ordered.length > RUNS_SHOWN;
  const visible = limited
    ? ordered.filter((r, i) => i < RUNS_SHOWN || r.run.run_id === current.run_id)
    : ordered;
  const metricName = tab === "moneyline" ? "RPS" : "Log loss";
  const metricLower = tab === "moneyline" ? "RPS" : "log loss";
  const noHeadline = without.filter((r) => r.headline === null);
  const lacking = without.filter((r) => r.headline !== null);

  return (
    <section className="card" aria-labelledby="lab-runs">
      <h2 id="lab-runs">Every run on {marketShort(tab, line)}</h2>
      <p className="muted small">
        Whole runs from each run's saved manifest; the league and season filters don't apply.
        Lower {metricLower} is better. Runs in the same group share the same fixtures.
      </p>
      <TableScroll label={`Runs compared on ${marketShort(tab, line)}`}>
        <table className="data-table">
          <thead>
            <tr>
              <th scope="col" className="sticky-col lab-run-col">
                Run
              </th>
              <th scope="col" className="num">
                {metricName} (95% interval)
              </th>
              <th scope="col" className="num">
                vs frequency baseline
              </th>
              <th scope="col" className="num">
                Accuracy
              </th>
            </tr>
          </thead>
          <tbody>
            {visible.map((r, i) => {
              const base = r.base;
              const isCurrent = r.run.run_id === current.run_id;
              const ci = r.m.primary_ci95;
              const delta =
                base !== undefined && r.primary !== undefined && r.run.model !== "baseline_frequency"
                  ? r.primary - base
                  : undefined;
              return (
                <tr
                  key={r.run.run_id}
                  className={`${isCurrent ? "is-current" : ""}${r.groupStart || (i > 0 && visible[i - 1]?.group !== r.group) ? " group-start" : ""}`}
                  aria-current={isCurrent ? "true" : undefined}
                >
                  <th scope="row" className="sticky-col lab-run-col">
                    {isCurrent ? (
                      <span>{modelName(r.run.model ?? r.run.run_id)}</span>
                    ) : (
                      <button
                        type="button"
                        className="link-button"
                        onClick={() => setQuery({ run: r.run.run_id, league: null, season: null })}
                      >
                        {modelName(r.run.model ?? r.run.run_id)}
                      </button>
                    )}
                    <span className="cell-sub">
                      {r.run.kind === "backtests" ? "" : `${kindName(r.run.kind)} · `}
                      {splitName(r.run.split)} {runSeasons(r.run)} · {r.m.n.toLocaleString("en-GB")} fixtures
                    </span>
                  </th>
                  <td className="num">
                    {fmt4(r.primary)}
                    {ci && (
                      <span className="cell-sub">
                        {fmt4(ci[0])} to {fmt4(ci[1])}
                      </span>
                    )}
                  </td>
                  <td className="num">
                    {delta === undefined ? (
                      <span className="muted">—</span>
                    ) : (
                      <>
                        {delta > 0 ? "+" : delta < 0 ? "−" : ""}
                        {fmt4(Math.abs(delta))}
                        <span className="cell-sub">{delta < 0 ? "better" : delta > 0 ? "worse" : "same"}</span>
                      </>
                    )}
                  </td>
                  <td className="num">{formatPct(r.m.accuracy, 1)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </TableScroll>
      {ordered.length > RUNS_SHOWN && (
        <button type="button" className="lab-runs-more" aria-expanded={!limited} onClick={() => setShowAll(!showAll)}>
          {limited ? `Show all ${ordered.length} runs` : `Show the first ${RUNS_SHOWN}`}
        </button>
      )}
      <p className="muted small">
        "vs frequency baseline" is the run's {metricLower} minus the league-frequency
        baseline's in the same group, worked out here from the two saved headlines.
        {lacking.length > 0 &&
          ` Not listed: ${lacking.map((r) => modelName(r.model ?? r.run_id)).join(", ")} (no ${marketShort(tab, line)} predictions).`}
        {noHeadline.length > 0 &&
          ` ${noHeadline.length} ${noHeadline.length === 1 ? "run has" : "runs have"} no saved headline; choose ${noHeadline.length === 1 ? "it" : "one"} above to compute its summary.`}
      </p>
    </section>
  );
}

// ---------------------------------------------------------------------------------------------
// Market benchmark (saved report; not filtered)

const COMPARISON_NAMES: Record<string, string> = {
  bench: "De-vigged closing benchmark",
  avg: "Market-average closing (robustness check)",
};

const BOOKMAKER_NAMES: Record<string, string> = {
  PS: "Pinnacle",
  BFE: "Betfair Exchange",
  B365: "Bet365",
  Avg: "Market average",
};

function BenchmarkPanel({ tab, line, selection }: { tab: MarketTab; line: number; selection: ModelSelection | undefined }) {
  const bench = useApi(endpoints.report<JsonValue>("benchmark"));
  const who = selection?.selected ? modelName(selection.selected) : "the model chosen by the evaluation report";

  let body;
  if (bench.data === undefined && bench.error?.notFound) {
    body = (
      <div className="lab-unavailable">
        <p>
          <strong>Not available yet.</strong> The market benchmark compares the model with
          de-vigged closing prices on the same fixtures. It appears here once the evaluation report
          has been run and saved.
        </p>
      </div>
    );
  } else {
    body = (
      <ApiView result={bench} what="the market benchmark" loadingLines={1}>
        {(data) => <BenchmarkBody data={data} tab={tab} line={line} />}
      </ApiView>
    );
  }

  return (
    <section className="card" aria-labelledby="lab-benchmark">
      <h2 id="lab-benchmark">Market benchmark</h2>
      <p className="muted small">
        For {who}, on its whole validation and test runs. It doesn't change with the filters above.
      </p>
      {body}
    </section>
  );
}

function comparisonKeys(tab: MarketTab, line: number): string[] | string {
  if (tab === "moneyline") return ["bench_1X2", "avg_1X2"];
  if (tab === "btts")
    return "There is no benchmark for both teams to score: the historical data has no BTTS prices, so this market is judged on accuracy and calibration only.";
  if (line !== 2.5)
    return `The benchmark covers the 2.5 line only: the historical data has no closing prices for over/under ${line}.`;
  return ["bench_OU_2.5", "avg_OU_2.5"];
}

function BenchmarkBody({ data, tab, line }: { data: JsonValue; tab: MarketTab; line: number }) {
  const splits = benchmarkSplits(data);
  if (splits.length === 0) {
    return <p className="muted">The saved benchmark report has no results in a form this page can read.</p>;
  }
  const keys = comparisonKeys(tab, line);
  return (
    <div className="bench">
      {splits.map(({ split, result }) => {
        const gate = result.gate;
        return (
          <div key={split ?? "all"} className="bench-split">
            <h3>{split ? splitName(split) : "Result"}</h3>
            {gate && (
              <>
                <p className="badges">
                  <Badge tone={gate.model_beats_market ? "good" : "warn"}>
                    {gate.model_beats_market ? "Model beats the closing market" : "Model doesn't beat the closing market"}
                  </Badge>
                  {gate.model_beats_market && (
                    <Badge tone={gate.significant ? "good" : "neutral"}>
                      {gate.significant ? "Interval below zero" : "Interval includes zero"}
                    </Badge>
                  )}
                </p>
                <p className="muted small">Gate: {gate.primary}.</p>
                {gate.label && (
                  <div className="lab-warning" role="note">
                    <span className="notice-icon" aria-hidden="true">
                      !
                    </span>
                    <p>
                      <strong>Warning:</strong> {gate.label}
                    </p>
                  </div>
                )}
              </>
            )}
            {typeof keys === "string" ? (
              <p className="muted">{keys}</p>
            ) : (
              <>
                {keys.map((k) => {
                  const c = result.shin?.[k];
                  return c ? <Comparison key={k} name={COMPARISON_NAMES[k.split("_")[0] ?? ""] ?? k} c={c} /> : null;
                })}
                {keys.every((k) => !result.shin?.[k]) && (
                  <p className="muted">No comparison for this market in the saved report.</p>
                )}
                {keys.some((k) => result.power?.[k]) && (
                  <details className="bench-more">
                    <summary>Same comparisons with power de-vigging</summary>
                    {keys.map((k) => {
                      const c = result.power?.[k];
                      return c ? <Comparison key={k} name={COMPARISON_NAMES[k.split("_")[0] ?? ""] ?? k} c={c} /> : null;
                    })}
                  </details>
                )}
              </>
            )}
            {typeof result.fixtures_predicted === "number" && (
              <p className="muted small">{result.fixtures_predicted.toLocaleString("en-GB")} fixtures predicted in this run.</p>
            )}
          </div>
        );
      })}
    </div>
  );
}

function Comparison({ name, c }: { name: string; c: BenchmarkComparison }) {
  const isRps = c.model_rps !== undefined;
  const metric = isRps ? "RPS" : "log loss";
  const model = isRps ? c.model_rps : c.model_log_loss;
  const marketV = isRps ? c.market_rps : c.market_log_loss;
  const [lo, hi] = c.difference_ci95;
  const verdict =
    hi < 0
      ? "Model better than the market: the whole interval is below zero."
      : lo > 0
        ? "Market better than the model: the whole interval is above zero."
        : "No clear difference: the interval includes zero.";
  const sources = c.sources
    ? Object.entries(c.sources)
        .map(([k, n]) => `${BOOKMAKER_NAMES[k] ?? k} ${n.toLocaleString("en-GB")}`)
        .join(" · ")
    : null;
  const signed = (v: number) => `${v > 0 ? "+" : v < 0 ? "−" : ""}${formatNumber(Math.abs(v), 4)}`;
  return (
    <div className="bench-row">
      <p className="bench-row-head">
        <strong>{name}</strong>
        <span className="muted small">
          {c.fixtures.toLocaleString("en-GB")} fixtures ({formatPct(c.coverage_of_predicted)} of predicted)
        </span>
      </p>
      <dl className="bench-values">
        <div>
          <dt>Model {metric}</dt>
          <dd>{fmt4(model)}</dd>
        </div>
        <div>
          <dt>Market {metric}</dt>
          <dd>{fmt4(marketV)}</dd>
        </div>
        <div>
          <dt>Difference</dt>
          <dd>
            {signed(c.difference)}
            <span className="cell-sub">
              95% interval {signed(lo)} to {signed(hi)}
            </span>
          </dd>
        </div>
      </dl>
      <CiStrip value={c.difference} lo={lo} hi={hi} label={`Difference model minus market, ${metric}`} />
      <p className="small">{verdict}</p>
      {sources && <p className="muted small">Closing prices used: {sources}.</p>}
    </div>
  );
}

// ---------------------------------------------------------------------------------------------
// Exports

type FileState = "checking" | "ok" | "missing" | "unknown";
const fileStates = new Map<string, Exclude<FileState, "checking">>();

/** Checks a download exists with a one-byte range request (the API sends 206 or 404). */
function useFileState(url: string): FileState {
  const [state, setState] = useState<{ url: string; value: FileState }>(() => ({
    url,
    value: fileStates.get(url) ?? "checking",
  }));
  let current = state;
  if (state.url !== url) {
    current = { url, value: fileStates.get(url) ?? "checking" };
    setState(current);
  }
  useEffect(() => {
    if (fileStates.has(url)) return;
    const controller = new AbortController();
    // Deferred a tick, so React's StrictMode mount-unmount-mount sends one request.
    const timer = window.setTimeout(() => {
      fetch(url, { headers: { Range: "bytes=0-0" }, signal: controller.signal }).then(
        (res) => {
          const value: Exclude<FileState, "checking"> = res.ok ? "ok" : res.status === 404 ? "missing" : "unknown";
          if (value !== "unknown") fileStates.set(url, value);
          res.body?.cancel().catch(() => undefined);
          if (!controller.signal.aborted) setState({ url, value });
        },
        () => {
          if (!controller.signal.aborted) setState({ url, value: "unknown" });
        },
      );
    }, 0);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [url]);
  return current.value;
}

function FileLink({ url, label, format }: { url: string; label: string; format: string }) {
  const state = useFileState(url);
  if (state === "missing") {
    return (
      <span className="lab-export is-missing">
        <span className="lab-export-label">{label}</span>
        <span className="muted small">Not exported for this run</span>
      </span>
    );
  }
  return (
    <a className="lab-export" href={url} download>
      <span className="lab-export-label">{label}</span>
      <span className="muted small">{state === "checking" ? "Checking…" : `Download ${format}`}</span>
    </a>
  );
}

function ExportLinks({ run }: { run: BacktestRun }) {
  if (SNAPSHOT) {
    // A snapshot holds the API's JSON responses only; the ledger files are not in it.
    return (
      <section className="card" aria-labelledby="lab-export">
        <h2 id="lab-export">Export the ledger</h2>
        <p className="muted small">
          Ledger downloads for <strong>{runLabel(run)}</strong> aren't included in this read-only
          snapshot.
        </p>
      </section>
    );
  }
  if (run.per_match === false) {
    // The API refuses this run's ledger (403): match by match its figures are the
    // bookmakers' consensus, which the backtest explorer withholds too.
    return (
      <section className="card" aria-labelledby="lab-export">
        <h2 id="lab-export">Export the ledger</h2>
        <p className="muted small">
          <strong>Summary only.</strong> Match by match, the match-result and over 2.5 goals figures
          of <strong>{runLabel(run)}</strong> are the bookmakers' consensus with the margins
          removed, so its ledger isn't offered for download; its summaries above are.
        </p>
      </section>
    );
  }
  return (
    <section className="card" aria-labelledby="lab-export">
      <h2 id="lab-export">Export the ledger</h2>
      <p className="muted small">
        Every fixture in <strong>{runLabel(run)}</strong>, one row each: probabilities, result,
        per-fixture scores and status (predicted, excluded or without enough history). Downloads
        are the whole run; the filters above don't apply.
      </p>
      <div className="lab-exports">
        <FileLink url={downloads.ledger(run.run_id, "csv")} label="Ledger CSV" format="CSV" />
        <FileLink url={downloads.ledger(run.run_id, "json")} label="Ledger JSON" format="JSON" />
      </div>
    </section>
  );
}
