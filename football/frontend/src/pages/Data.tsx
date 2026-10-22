// Brief item 6: data and methodology. Sources, how predictions are made and which model
// versions are live, coverage by league and season, exclusions (import issues), the full
// refresh history including failures, and the system status.
//
// Descriptive text is fixed; every count, percentage and date comes from the API.

import { useMemo, useState } from "react";
import {
  endpoints,
  useApi,
  type CoverageRow,
  type FixturesResponse,
  type ImportIssues,
  type JobRun,
  type JsonValue,
} from "../api";
import { Badge, type Tone } from "../components/Badge";
import { Segmented, SelectField, TableScroll } from "../components/Controls";
import { ApiView, Empty, StaleNotice } from "../components/StateViews";
import {
  formatDate,
  formatLondon,
  formatRelative,
  formatTime,
  londonDayKey,
  parseUtc,
  seasonLabel,
} from "../format";
import { useDocumentTitle } from "../hooks";
import { href, setQuery } from "../router";
import { SystemStatus } from "./Status";
import type { PageProps } from ".";
import "./Data.css";

const SECTIONS = [
  { id: "data-status", label: "Status" },
  { id: "data-sources", label: "Sources" },
  { id: "data-method", label: "Method" },
  { id: "data-coverage", label: "Coverage" },
  { id: "data-exclusions", label: "Exclusions" },
  { id: "data-refresh", label: "Refresh history" },
] as const;

export function Data({ query }: PageProps) {
  useDocumentTitle("Data and methodology");
  return (
    <>
      <header className="page-head">
        <h1 className="page-title">Data and methodology</h1>
        <p className="lede">
          Where the data comes from, what is in it, what was left out and why, and every data
          refresh, failures included.
        </p>
      </header>

      <nav className="data-jump" aria-label="On this page">
        {SECTIONS.map((s) => (
          <button
            key={s.id}
            type="button"
            className="chip"
            onClick={() => document.getElementById(s.id)?.scrollIntoView({ behavior: "smooth", block: "start" })}
          >
            {s.label}
          </button>
        ))}
      </nav>

      <div id="data-status" className="data-anchor">
        <SystemStatus />
      </div>
      <Sources />
      <Method />
      <Coverage query={query} />
      <Exclusions />
      <RefreshHistory query={query} />
    </>
  );
}

// ---------------------------------------------------------------------------------------------
// Sources

function Sources() {
  const health = useApi(endpoints.health(), { freshMs: 15_000 });
  const h = health.data;
  const provider = h
    ? h.live_provider === "none"
      ? { tone: "neutral" as Tone, text: "Not configured" }
      : h.live_provider_configured
        ? { tone: "good" as Tone, text: `Connected: ${h.live_provider}` }
        : { tone: "warn" as Tone, text: `${h.live_provider}: no key` }
    : null;
  return (
    <section className="card data-anchor" id="data-sources" aria-labelledby="data-sources-h">
      <h2 id="data-sources-h">Sources</h2>
      <ul className="sources">
        <li>
          <div className="source-head">
            <h3>football-data.co.uk</h3>
            <Badge>Personal, non-commercial use</Badge>
          </div>
          <p>
            Historical results, match statistics and bookmaker prices from 2018/19: the big five
            leagues and their second tiers, plus other European top divisions for rating clubs in
            UEFA competitions. Prices are read for every division (closing 1X2 only for the
            one-file-per-league countries). Its fixtures file gives this week's pre-match prices,
            used by model + market records.
          </p>
          <p className="muted small">
            Fetched politely (one request every 2 seconds, cached; the current season at most
            twice a day). Raw files and prices are never published. Historical files may include
            corrections made after the matches.
          </p>
        </li>
        <li>
          <div className="source-head">
            <h3>openfootball</h3>
            <Badge>CC0</Badge>
          </div>
          <p>
            Fixture lists for the current domestic seasons, with kick-off times, used to find this
            week's matches. Team names are matched to ours and checked against the results already
            played. Also UEFA club competition results up to July 2026.
          </p>
        </li>
        <li>
          <div className="source-head">
            <h3>Wikipedia</h3>
            <Badge>CC BY-SA 4.0</Badge>
          </div>
          <p>
            2026/27 Champions League, Europa League and Conference League fixtures and results,
            qualifying and league phase, read from saved page revisions. UEFA.com is not used: its
            terms forbid scripted collection.
          </p>
        </li>
        <li>
          <div className="source-head">
            <h3>Sportmonks (free plan)</h3>
            {provider && <Badge tone={provider.tone}>{provider.text}</Badge>}
          </div>
          <p>
            The live provider, for bookmaker prices captured before kickoff (48 hours, 24 hours, 2
            hours and 15 minutes before). The free plan covers the Scottish Premiership and Danish
            Superliga only, so most fixtures have no live prices yet and no betting selection.
          </p>
          <p className="muted small">
            Lineups and injuries are not used: the early edition is made without them.
          </p>
        </li>
      </ul>
      <p className="muted small">
        Not used: BBC Sport (its terms forbid scraping), Understat (its robots.txt disallows
        automated access) and expected-goals data in general, which no free source covers for
        past seasons.
      </p>
    </section>
  );
}

// ---------------------------------------------------------------------------------------------
// Method and model versions

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}

function Method() {
  const selection = useApi(endpoints.report<JsonValue>("model_selection"));
  const fixtures = useApi(endpoints.fixtures(14));
  const runs = useApi(endpoints.backtests());
  const sel = isRecord(selection.data) ? selection.data : undefined;
  const selected = typeof sel?.selected === "string" ? sel.selected : null;
  const rule = typeof sel?.rule === "string" ? sel.rule : null;
  const generated = typeof sel?.generated_at_utc === "string" ? sel.generated_at_utc : null;

  return (
    <section className="card data-anchor" id="data-method" aria-labelledby="data-method-h">
      <h2 id="data-method-h">How predictions are made</h2>
      <ul className="facts">
        <li>
          <strong>Decision time.</strong> The early edition is made at 00:00 UK time on the decision
          day, from results final before then. No lineup or injury information is used.
        </li>
        <li>
          <strong>Saved, never changed.</strong> A genuine pre-match prediction is saved before
          kickoff and can't be edited. Backtests replay past seasons in the same way, a matchweek
          at a time, and are labelled retrospective.
        </li>
        <li>
          <strong>Choosing a model.</strong> Models are compared on 2023/24 (validation); the chosen
          one is then run once on 2024/25 and 2025/26 (test). See the{" "}
          <a href={href("/lab")}>backtest lab</a>.
        </li>
        <li>
          <strong>Model + market.</strong> Where football-data has published bookmakers' prices
          for a fixture (collected on Tuesdays and Fridays), a second record blends them in. It
          keeps the de-vigged probabilities it used and the price file's fingerprint, never the
          prices. The pure model's record continues alongside, and the two are counted
          separately on <a href={href("/performance")}>Performance</a>.
        </li>
        <li>
          <strong>Market benchmark.</strong> The model is compared with de-vigged closing prices on
          the same fixtures: Pinnacle before 23 July 2025, Betfair Exchange after (Pinnacle's prices
          went stale from that date).
        </li>
      </ul>

      <h3>Three separate signals</h3>
      <dl className="data-signals">
        <div>
          <dt>Model confidence</dt>
          <dd>
            How likely the model thinks its most likely outcome is. On a model + market record it
            is called forecast confidence (bookmakers + model), because the prices are blended in.
          </dd>
        </div>
        <div>
          <dt>Data completeness</dt>
          <dd>Which inputs the prediction had and which it lacked.</dd>
        </div>
        <div>
          <dt>Betting edge</dt>
          <dd>
            Whether a bookmaker price beats the model's probability. Needs odds: a model-only
            record whose match also has a model + market record points to that one.
          </dd>
        </div>
      </dl>
      <p className="muted small">
        They are never blended into one score: a confident prediction can have no edge, and a
        complete record can still be uncertain.
      </p>

      <h3>Model versions</h3>
      <dl className="status data-models">
        <dt>Selected model</dt>
        <dd>
          {selected ? (
            <>
              <code>{selected}</code>
              {generated && <span className="muted"> · chosen {formatLondon(generated)}</span>}
              {rule && <span className="cell-sub">{rule}</span>}
            </>
          ) : selection.error?.notFound ? (
            <span className="muted">Not chosen yet: the evaluation report hasn't been saved.</span>
          ) : selection.error ? (
            <span className="bad">Couldn't load: {selection.error.message}</span>
          ) : selection.data !== undefined ? (
            <span className="muted">The saved report names no model.</span>
          ) : (
            <span className="muted">Loading…</span>
          )}
        </dd>
        <dt>Published now</dt>
        <dd>
          <PublishedModels result={fixtures.data} error={fixtures.error?.message} />
        </dd>
        <dt>Backtest runs</dt>
        <dd>
          {runs.data ? (
            runs.data.runs.length ? (
              <>
                {runs.data.runs.length} saved ({summariseKinds(runs.data.runs.map((r) => r.kind))}).{" "}
                <a href={href("/lab")}>Open the lab</a>
              </>
            ) : (
              <span className="muted">None saved yet</span>
            )
          ) : runs.error ? (
            <span className="bad">Couldn't load: {runs.error.message}</span>
          ) : (
            <span className="muted">Loading…</span>
          )}
        </dd>
      </dl>
    </section>
  );
}

function summariseKinds(kinds: string[]): string {
  const counts = new Map<string, number>();
  for (const k of kinds) counts.set(k, (counts.get(k) ?? 0) + 1);
  return [...counts.entries()]
    .map(([k, n]) => `${n} ${k === "backtests" ? "domestic" : k === "backtests_euro" ? "UEFA" : k}`)
    .join(", ");
}

function PublishedModels({ result, error }: { result: FixturesResponse | undefined; error?: string }) {
  if (!result) return error ? <span className="bad">Couldn't load: {error}</span> : <span className="muted">Loading…</span>;
  const byModel = new Map<string, { name: string; version: string; n: number; latest: string }>();
  for (const c of result.competitions.flatMap((x) => x.fixtures)) {
    const key = `${c.model_name}@${c.model_version}`;
    const e = byModel.get(key);
    if (e) {
      e.n += 1;
      if (c.created_at_utc > e.latest) e.latest = c.created_at_utc;
    } else {
      byModel.set(key, { name: c.model_name, version: c.model_version, n: 1, latest: c.created_at_utc });
    }
  }
  if (byModel.size === 0) return <span className="muted">No predictions published for the next 14 days.</span>;
  // The fixtures list carries each fixture's record of record and its partner; the other
  // models' predictions are counted (`models_for_fixture`) and shown on each match page.
  const carried = new Map<string, { cards: number; models: number }>();
  for (const c of result.competitions.flatMap((x) => x.fixtures)) {
    const e = carried.get(c.fixture_ref) ?? { cards: 0, models: 0 };
    e.cards += 1;
    e.models = Math.max(e.models, c.models_for_fixture ?? 0);
    carried.set(c.fixture_ref, e);
  }
  const more = [...carried.values()].reduce((n, e) => n + Math.max(0, e.models - e.cards), 0);
  return (
    <>
      <ul className="data-model-list">
        {[...byModel.values()].map((m) => (
          <li key={`${m.name}@${m.version}`}>
            <code>{m.name}</code> version <code>{m.version}</code>
            <span className="muted">
              {" "}
              · {m.n} {m.n === 1 ? "prediction" : "predictions"}, latest saved {formatLondon(m.latest)}
            </span>
          </li>
        ))}
      </ul>
      {more > 0 && (
        <span className="cell-sub">
          Above: each fixture's record of record and, where there is one, its model + market partner. Other models
          saved {more.toLocaleString("en-GB")} more predictions for the same fixtures: each match page compares every
          model, and <a href={href("/performance")}>Performance</a> has the live leaderboard.
        </span>
      )}
    </>
  );
}

// ---------------------------------------------------------------------------------------------
// Coverage

type Scope = "core" | "europe" | "all";
type ColumnSet = "results" | "stats" | "prices";

const STAT_LABELS: Record<string, string> = {
  pct_kickoff_time: "Kick-off time",
  pct_ht_score: "Half-time score",
  pct_shots: "Shots",
  pct_shots_on_target: "Shots on target",
  pct_corners: "Corners",
  pct_cards: "Cards",
  pct_referee: "Referee",
  pct_fd_xg: "xG (not used)",
};

const BOOKS: Record<string, string> = {
  B365: "Bet365",
  PS: "Pinnacle",
  BFE: "Betfair Exchange",
  Avg: "Market average",
};

function statLabel(key: string): string {
  return STAT_LABELS[key] ?? key.replace(/^pct_/, "").replace(/_/g, " ");
}

/** "odds::PS close 1X2" -> ["Pinnacle", "closing 1X2"]. */
function oddsLabel(key: string): [string, string] {
  const [book = "", phase = "", ...rest] = key.replace(/^odds::/, "").split(" ");
  const when = phase === "pre" ? "pre-closing" : phase === "close" ? "closing" : phase;
  return [BOOKS[book] ?? book, `${when} ${rest.join(" ")}`.trim()];
}

function pct(v: number | null | undefined): { text: string; muted: boolean } {
  if (v === null || v === undefined || !Number.isFinite(v)) return { text: "n/a", muted: true };
  if (v === 0) return { text: "0%", muted: true };
  return { text: v === 100 ? "100%" : `${v.toFixed(1)}%`, muted: false };
}

function Coverage({ query }: { query: URLSearchParams }) {
  const result = useApi(endpoints.coverage());
  const scope: Scope = query.get("scope") === "europe" ? "europe" : query.get("scope") === "all" ? "all" : "core";
  const cols: ColumnSet = query.get("cols") === "stats" ? "stats" : query.get("cols") === "prices" ? "prices" : "results";
  const division = query.get("division");

  return (
    <section className="card data-anchor" id="data-coverage" aria-labelledby="data-coverage-h">
      <h2 id="data-coverage-h">Coverage</h2>
      <p className="muted small">
        Matches stored for each league and season, and how complete each field is. On a phone the
        table scrolls sideways inside its box.
      </p>
      <ApiView
        result={result}
        what="coverage"
        loadingLines={2}
        isEmpty={(d) => d.rows.length === 0}
        empty={<p className="muted">No match data has been imported yet.</p>}
      >
        {(d) => <CoverageTable rows={d.rows} scope={scope} cols={cols} division={division} />}
      </ApiView>
    </section>
  );
}

function CoverageTable({
  rows,
  scope,
  cols,
  division,
}: {
  rows: CoverageRow[];
  scope: Scope;
  cols: ColumnSet;
  division: string | null;
}) {
  const inScope = useMemo(() => rows.filter((r) => scope === "all" || r.scope === scope), [rows, scope]);
  const divisions = useMemo(() => {
    const seen = new Map<string, string>();
    for (const r of inScope) if (!seen.has(r.division)) seen.set(r.division, r.division_name);
    return [...seen.entries()];
  }, [inScope]);
  const div = division && divisions.some(([c]) => c === division) ? division : null;
  const shown = div ? inScope.filter((r) => r.division === div) : inScope;

  // Group by division (in the API's order), seasons oldest first.
  const groups = useMemo(() => {
    const m = new Map<string, CoverageRow[]>();
    for (const r of shown) {
      const list = m.get(r.division);
      if (list) list.push(r);
      else m.set(r.division, [r]);
    }
    return [...m.entries()].map(([code, list]) => ({
      code,
      name: list[0]?.division_name ?? code,
      rows: list.slice().sort((a, b) => a.season.localeCompare(b.season)),
    }));
  }, [shown]);

  const keys = useMemo(() => {
    const stat = new Set<string>();
    const odds = new Set<string>();
    for (const r of rows)
      for (const k of Object.keys(r)) {
        if (k.startsWith("pct_")) stat.add(k);
        else if (k.startsWith("odds::")) odds.add(k);
      }
    return { stat: [...stat], odds: [...odds] };
  }, [rows]);

  const totals = shown.reduce(
    (t, r) => ({
      files: t.files + 1,
      matches: t.matches + r.matches,
      held: t.held + (r.held_back ?? 0),
      excluded: t.excluded + (r.model_excluded ?? 0),
      errors: t.errors + r.issues_error,
    }),
    { files: 0, matches: 0, held: 0, excluded: 0, errors: 0 },
  );
  const hasEurope = shown.some((r) => r.held_back !== undefined || r.model_excluded !== undefined);
  const n = (v: number) => v.toLocaleString("en-GB");

  const header =
    cols === "results"
      ? ["Matches", "Teams", "First match", "Last match", "Issues", ...(hasEurope ? ["Held back", "Excluded"] : []), "Downloaded"]
      : cols === "stats"
        ? keys.stat.map(statLabel)
        : keys.odds.map((k) => oddsLabel(k).join(" "));
  const colCount = header.length + 1;

  return (
    <>
      <div className="ctl-row">
        <Segmented<Scope>
          label="Leagues"
          value={scope}
          options={[
            { value: "core", label: "Big five + second tiers" },
            { value: "europe", label: "Other European" },
            { value: "all", label: "All" },
          ]}
          onChange={(v) => setQuery({ scope: v === "core" ? null : v, division: null })}
        />
        <SelectField
          id="coverage-division"
          label="League"
          value={div ?? ""}
          options={[
            { value: "", label: `All (${divisions.length})` },
            ...divisions.map(([code, name]) => ({ value: code, label: `${name} (${code})` })),
          ]}
          onChange={(v) => setQuery({ division: v || null })}
        />
        <Segmented<ColumnSet>
          label="Columns"
          value={cols}
          options={[
            { value: "results", label: "Results" },
            { value: "stats", label: "Statistics" },
            { value: "prices", label: "Prices" },
          ]}
          onChange={(v) => setQuery({ cols: v === "results" ? null : v })}
        />
      </div>

      <p className="small data-totals">
        <strong>{n(totals.matches)}</strong> matches in <strong>{n(totals.files)}</strong> league
        seasons
        {totals.held > 0 && (
          <>
            {" "}
            · <strong>{n(totals.held)}</strong> repeat meetings held back
          </>
        )}
        {totals.excluded > 0 && (
          <>
            {" "}
            · <strong>{n(totals.excluded)}</strong> excluded from models
          </>
        )}
        {totals.errors > 0 && (
          <>
            {" "}
            · <strong>{n(totals.errors)}</strong> import errors
          </>
        )}
      </p>

      <TableScroll label="Coverage by league and season" className="coverage-scroll">
        <table className="data-table coverage-table">
          <thead>
            <tr>
              <th scope="col" className="sticky-col">
                Season
              </th>
              {header.map((h) => (
                <th key={h} scope="col" className={cols === "results" && /First|Last|Downloaded/.test(h) ? undefined : "num"}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {groups.map((g) => (
              <CoverageGroup key={g.code} code={g.code} name={g.name} rows={g.rows} cols={cols} keys={keys} hasEurope={hasEurope} colCount={colCount} />
            ))}
          </tbody>
        </table>
      </TableScroll>
      <p className="muted small">
        Expected matches is a full double round robin for the teams seen, so the current season and
        leagues with split or multi-round formats fall short by design. Held back: repeat meetings
        of the same teams in one season, which can't be stored yet. Excluded: kept for the record
        but left out of models (awarded results, play-offs against lower-division clubs). Prices:
        share of matches with a complete price set; n/a outside the big five.
      </p>
    </>
  );
}

function CoverageGroup({
  code,
  name,
  rows,
  cols,
  keys,
  hasEurope,
  colCount,
}: {
  code: string;
  name: string;
  rows: CoverageRow[];
  cols: ColumnSet;
  keys: { stat: string[]; odds: string[] };
  hasEurope: boolean;
  colCount: number;
}) {
  const n = (v: number) => v.toLocaleString("en-GB");
  return (
    <>
      <tr className="coverage-group">
        <th scope="colgroup" colSpan={colCount}>
          <span className="coverage-group-name">
            {name} <span className="muted">({code})</span>
          </span>
        </th>
      </tr>
      {rows.map((r) => {
        const short = r.matches < r.expected_matches;
        return (
          <tr key={`${r.division}-${r.season}`}>
            <th scope="row" className="sticky-col">
              {r.season_label || seasonLabel(r.season)}
            </th>
            {cols === "results" && (
              <>
                <td className="num">
                  {n(r.matches)}
                  <span className={`cell-sub${short ? " warn-text" : ""}`}>of {n(r.expected_matches)}</span>
                </td>
                <td className="num">{r.teams}</td>
                <td>{formatDate(r.first_date)}</td>
                <td>{formatDate(r.last_date)}</td>
                <td className="num">
                  {r.issues_error + r.issues_warning === 0 ? (
                    <span className="muted">None</span>
                  ) : (
                    <>
                      {r.issues_error > 0 && <span className="bad">{n(r.issues_error)} err</span>}
                      {r.issues_error > 0 && r.issues_warning > 0 && " · "}
                      {r.issues_warning > 0 && <span className="warn-text">{n(r.issues_warning)} warn</span>}
                    </>
                  )}
                </td>
                {hasEurope && (
                  <>
                    <td className="num">{r.held_back === undefined ? <span className="muted">—</span> : n(r.held_back)}</td>
                    <td className="num">{r.model_excluded === undefined ? <span className="muted">—</span> : n(r.model_excluded)}</td>
                  </>
                )}
                <td>{r.retrieved_at ? formatDate(r.retrieved_at.slice(0, 10)) : <span className="muted">—</span>}</td>
              </>
            )}
            {cols === "stats" &&
              keys.stat.map((k) => {
                const p = pct(r[k as `pct_${string}`]);
                return (
                  <td key={k} className={`num${p.muted ? " muted" : ""}`}>
                    {p.text}
                  </td>
                );
              })}
            {cols === "prices" &&
              keys.odds.map((k) => {
                const p = pct(r[k as `odds::${string}`]);
                return (
                  <td key={k} className={`num${p.muted ? " muted" : ""}`}>
                    {p.text}
                  </td>
                );
              })}
          </tr>
        );
      })}
    </>
  );
}

// ---------------------------------------------------------------------------------------------
// Exclusions (import issues)

/** What each import rule means and what happens to the row (DECISIONS D-017, D-018, D-022). */
const ISSUE_INFO: Record<string, { what: string; action: string }> = {
  no_result: { what: "Row has no score", action: "Row dropped" },
  bad_date: { what: "Date can't be read", action: "Row dropped" },
  bad_time: { what: "Kick-off time can't be read", action: "Time dropped" },
  bad_season: { what: "Season can't be read", action: "Row dropped" },
  missing_team: { what: "A team name is missing", action: "Row dropped" },
  unmapped_teams: { what: "Team names not in the mapping table", action: "Row dropped" },
  wrong_division: { what: "Row belongs to another division", action: "Row dropped" },
  wrong_country: { what: "Row belongs to another country", action: "Row dropped" },
  result_mismatch: { what: "Full-time result disagrees with the goals", action: "Row dropped" },
  ht_result_mismatch: { what: "Half-time result disagrees with the goals", action: "Half-time values dropped" },
  ht_exceeds_ft: { what: "More goals at half-time than full-time", action: "Half-time values dropped" },
  duplicate_fixture: { what: "Same match twice on the same date", action: "Second row dropped" },
  repeat_pairing: {
    what: "Same home and away teams again in one season (split or multi-round leagues)",
    action: "Held back until the database can store repeat meetings",
  },
  invalid_price: { what: "Price missing, zero, 1.0 or less, or unreadable", action: "That price dropped" },
  implausible_overround: {
    what: "Implied probabilities of a price set sum outside 0.95–1.30",
    action: "Whole price set dropped",
  },
  bad_stat: { what: "A match statistic can't be read", action: "That value dropped" },
  sot_exceeds_shots: { what: "More shots on target than shots", action: "Kept, flagged" },
  kickoff_before_collection: {
    what: "Kick-off before the documented price collection time",
    action: "Kept; never used for staking",
  },
  result_without_stats: {
    what: "A score with no half-time score or statistics (awarded or bare results)",
    action: "Kept for the record; excluded from models",
  },
  playoff_vs_outside_club: {
    what: "Play-off against a lower-division club",
    action: "Kept; excluded from models",
  },
  other_league_label: { what: "File holds rows labelled with another league", action: "File-level warning only" },
};

function issueLabel(code: string): string {
  return code.replace(/_/g, " ").replace(/^./, (c) => c.toUpperCase());
}

function Exclusions() {
  const result = useApi(endpoints.importIssues());
  return (
    <section className="card data-anchor" id="data-exclusions" aria-labelledby="data-exclusions-h">
      <h2 id="data-exclusions-h">Exclusions</h2>
      <p className="muted small">
        Every row or value the importer rejected or flagged, by reason. Errors drop or hold back
        data; warnings keep it with a flag.
      </p>
      <ApiView
        result={result}
        what="import issues"
        loadingLines={1}
        isEmpty={(d) => d.summary.length === 0}
        empty={<p className="muted">No import issues recorded.</p>}
      >
        {(d) => <IssuesTable data={d} />}
      </ApiView>
    </section>
  );
}

function IssuesTable({ data }: { data: ImportIssues }) {
  const [open, setOpen] = useState<ReadonlySet<string>>(() => new Set());
  const rows = data.summary
    .slice()
    .sort((a, b) => (a.severity === b.severity ? b.count - a.count : a.severity === "error" ? -1 : 1));
  const examples = useMemo(() => {
    const m = new Map<string, ImportIssues["issues"]>();
    for (const i of data.issues) {
      const k = `${i.severity}|${i.code}`;
      const list = m.get(k);
      if (!list) m.set(k, [i]);
      else if (list.length < 5) list.push(i);
    }
    return m;
  }, [data.issues]);
  const total = (sev: string) => rows.filter((r) => r.severity === sev).reduce((n, r) => n + r.count, 0);
  const toggle = (k: string) =>
    setOpen((s) => {
      const next = new Set(s);
      if (next.has(k)) next.delete(k);
      else next.add(k);
      return next;
    });

  return (
    <>
      <p className="small data-totals">
        <strong>{total("error").toLocaleString("en-GB")}</strong> errors ·{" "}
        <strong>{total("warning").toLocaleString("en-GB")}</strong> warnings
      </p>
      <TableScroll label="Import issues by reason">
        <table className="data-table issues-table">
          <thead>
            <tr>
              <th scope="col">Reason and what happens</th>
              <th scope="col" className="num">
                Count
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => {
              const k = `${r.severity}|${r.code}`;
              const info = ISSUE_INFO[r.code];
              const ex = examples.get(k) ?? [];
              const isOpen = open.has(k);
              return (
                <IssueRows
                  key={k}
                  rowKey={k}
                  severity={r.severity}
                  code={r.code}
                  count={r.count}
                  info={info}
                  examples={ex}
                  open={isOpen}
                  onToggle={() => toggle(k)}
                />
              );
            })}
          </tbody>
        </table>
      </TableScroll>
    </>
  );
}

function IssueRows({
  rowKey,
  severity,
  code,
  count,
  info,
  examples,
  open,
  onToggle,
}: {
  rowKey: string;
  severity: string;
  code: string;
  count: number;
  info: { what: string; action: string } | undefined;
  examples: ImportIssues["issues"];
  open: boolean;
  onToggle: () => void;
}) {
  const exId = `issue-ex-${rowKey.replace(/[^a-z0-9]+/gi, "-")}`;
  return (
    <>
      <tr>
        <th scope="row" className="wrap">
          <Badge tone={severity === "error" ? "bad" : "warn"}>{severity === "error" ? "Error" : "Warning"}</Badge>{" "}
          {info?.what ?? issueLabel(code)}
          <span className="issue-action">
            <span aria-hidden="true">→ </span>
            {info?.action ?? "See the import log"}
          </span>
          <span className="cell-sub">
            <code>{code}</code>
            {examples.length > 0 && (
              <>
                {" · "}
                <button type="button" className="link-button" aria-expanded={open} aria-controls={exId} onClick={onToggle}>
                  {open ? "Hide examples" : `Show ${examples.length === 1 ? "example" : `${examples.length} examples`}`}
                </button>
              </>
            )}
          </span>
        </th>
        <td className="num">{count.toLocaleString("en-GB")}</td>
      </tr>
      {open && (
        <tr id={exId} className="issue-examples">
          <td colSpan={2}>
            <ul>
              {examples.map((e, i) => (
                <li key={i}>
                  <code>{e.file}</code>
                  {e.row !== null && <span className="muted"> row {e.row}</span>}
                  {e.detail && <span>: {e.detail}</span>}
                </li>
              ))}
            </ul>
            {count > examples.length && (
              <p className="muted small">First {examples.length} of {count.toLocaleString("en-GB")}.</p>
            )}
          </td>
        </tr>
      )}
    </>
  );
}

// ---------------------------------------------------------------------------------------------
// Refresh history

const JOB_NAMES: Record<string, string> = {
  fd_update: "football-data update",
  fd_download_initial: "football-data first download",
  fd_download_europe: "European leagues download",
  odds_capture: "Odds capture",
};

function jobName(job: string): string {
  return JOB_NAMES[job] ?? job.replace(/_/g, " ").replace(/^./, (c) => c.toUpperCase());
}

const STATUS_INFO: Record<string, { label: string; tone: Tone; icon: string }> = {
  ok: { label: "OK", tone: "good", icon: "✓" },
  partial: { label: "Partial", tone: "warn", icon: "!" },
  failed: { label: "Failed", tone: "bad", icon: "✕" },
  running: { label: "Running", tone: "info", icon: "…" },
};

function statusInfo(status: string) {
  return STATUS_INFO[status] ?? { label: status, tone: "neutral" as Tone, icon: "?" };
}

function isProblem(r: JobRun): boolean {
  return r.status !== "ok" && r.status !== "running";
}

function duration(r: JobRun): string | null {
  if (!r.started_at || !r.finished_at) return null;
  const s = Math.max(0, Math.round((parseUtc(r.finished_at).getTime() - parseUtc(r.started_at).getTime()) / 1000));
  if (s < 60) return `${s} s`;
  if (s < 3600) return `${Math.floor(s / 60)} min ${s % 60} s`;
  return `${Math.floor(s / 3600)} h ${Math.floor((s % 3600) / 60)} min`;
}

/** "21 Oct 2026, 01:25 to 02:01" (the date once when both are on the same UK day). */
function formatSpan(fromIso: string, toIso: string): string {
  const sameDay = londonDayKey(fromIso, fromIso) === londonDayKey(toIso, toIso);
  return `${formatLondon(fromIso)} to ${sameDay ? formatTime(toIso) : formatLondon(toIso)}`;
}

/** Consecutive OK runs of the same job fold into one entry; problems never fold. */
type Entry = { runs: JobRun[] };

function fold(runs: JobRun[]): Entry[] {
  const out: Entry[] = [];
  for (const r of runs) {
    const last = out.at(-1);
    const head = last?.runs[0];
    if (last && head && r.status === "ok" && head.status === "ok" && head.job === r.job) last.runs.push(r);
    else out.push({ runs: [r] });
  }
  return out;
}

const HISTORY_LIMIT = 200;

function RefreshHistory({ query }: { query: URLSearchParams }) {
  const result = useApi(endpoints.refreshHistory(HISTORY_LIMIT), { freshMs: 15_000 });
  const job = query.get("job");
  const problemsOnly = query.get("runs") === "problems";
  return (
    <section className="card data-anchor" id="data-refresh" aria-labelledby="data-refresh-h">
      <h2 id="data-refresh-h">Refresh history</h2>
      <p className="muted small">Every data job, newest first, failures included.</p>
      <ApiView
        result={result}
        what="the refresh history"
        loadingLines={2}
        isEmpty={(d) => d.runs.length === 0}
        empty={<p className="muted">No job has run yet.</p>}
      >
        {(d) => <HistoryList runs={d.runs} job={job} problemsOnly={problemsOnly} />}
      </ApiView>
    </section>
  );
}

function HistoryList({ runs, job, problemsOnly }: { runs: JobRun[]; job: string | null; problemsOnly: boolean }) {
  const jobs = useMemo(() => [...new Set(runs.map((r) => r.job))], [runs]);
  const activeJob = job && jobs.includes(job) ? job : null;
  const filtered = runs.filter((r) => (!activeJob || r.job === activeJob) && (!problemsOnly || isProblem(r)));
  const entries = fold(filtered);
  const problems = runs.filter(isProblem);
  const latestProblem = problems[0];

  return (
    <>
      {latestProblem ? (
        <StaleNotice>
          Latest problem: <strong>{jobName(latestProblem.job)}</strong>{" "}
          {statusInfo(latestProblem.status).label.toLowerCase()} {latestProblem.started_at ? formatRelative(latestProblem.started_at) : ""}
          {latestProblem.error ? `: ${latestProblem.error}` : "."}
        </StaleNotice>
      ) : (
        <p className="small good">
          <span aria-hidden="true">✓ </span>No failed or partial runs in the last {runs.length} jobs.
        </p>
      )}

      <div className="ctl-row">
        <SelectField
          id="refresh-job"
          label="Job"
          value={activeJob ?? ""}
          options={[{ value: "", label: "All jobs" }, ...jobs.map((j) => ({ value: j, label: jobName(j) }))]}
          onChange={(v) => setQuery({ job: v || null })}
        />
        <Segmented<"all" | "problems">
          label="Show"
          value={problemsOnly ? "problems" : "all"}
          options={[
            { value: "all", label: "All runs" },
            { value: "problems", label: `Problems (${problems.length})` },
          ]}
          onChange={(v) => setQuery({ runs: v === "all" ? null : v })}
        />
      </div>

      {entries.length === 0 ? (
        <Empty title={problemsOnly ? "No problems to show" : "No runs for this job"}>
          <button type="button" onClick={() => setQuery({ job: null, runs: null })}>
            Show every run
          </button>
        </Empty>
      ) : (
        <ol className="runs">
          {entries.map((e) => (
            <RunEntry key={e.runs[0]?.id} entry={e} />
          ))}
        </ol>
      )}
      <p className="muted small">
        The latest {runs.length} runs. Repeated successful runs of the same job are folded into one
        line.
      </p>
    </>
  );
}

function RunEntry({ entry }: { entry: Entry }) {
  const first = entry.runs[0];
  const last = entry.runs.at(-1);
  if (!first || !last) return null;
  const info = statusInfo(first.status);
  const many = entry.runs.length > 1;
  const dur = duration(first);
  return (
    <li className={`run run-${info.tone}`}>
      <span className={`run-status badge badge-${info.tone}`}>
        <span aria-hidden="true">{info.icon}</span>
        {info.label}
      </span>
      <div className="run-body">
        <p className="run-head">
          <strong>{jobName(first.job)}</strong>
          {many && <span className="muted"> × {entry.runs.length}</span>}
        </p>
        <p className="muted small">
          {many && last.started_at && first.started_at
            ? formatSpan(last.started_at, first.started_at)
            : first.started_at
              ? formatLondon(first.started_at)
              : "Start time unknown"}
          {!many && dur && ` · took ${dur}`}
          {!many && !first.finished_at && first.status !== "running" && " · never finished"}
        </p>
        {first.error && <p className="run-error">{first.error}</p>}
        {!many && first.detail !== null && (
          <details className="run-detail">
            <summary>Details</summary>
            <pre className="json">{JSON.stringify(first.detail, null, 2)}</pre>
          </details>
        )}
      </div>
    </li>
  );
}
