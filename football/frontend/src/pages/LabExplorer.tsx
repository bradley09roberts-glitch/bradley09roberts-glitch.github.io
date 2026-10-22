// The lab's Backtest explorer: what each model predicted for every past match it was run on.
// Filters for league or competition, season (validation 2023/24, test 2024/25 and 2025/26),
// team and models; a summary per chosen model over the filtered matches (matches, results
// right with its 95% interval, mean RPS); then the matches, a page at a time, each with the
// score and every chosen model's home/draw/away split, its pick marked right or wrong.
//
// Backtests only (`kind: "backtest"`): predictions computed after the fact, out of sample,
// never genuine pre-match records. A banner says so above everything else.
//
// Data: /api/backtest-models (the models and what the ledgers cover) and one
// /api/backtest-matches?division=&season=&limit=1000 per division and season, which is exactly
// what the static snapshot holds (one shard file per league and split, fetched on first use).
// Team and model filters are applied here. With no team chosen the summary is the API's; with a
// team it is worked out here from the per-match figures, as labelled.

import { useEffect, useMemo, useRef, useState } from "react";
import {
  endpoints,
  useApi,
  type BacktestCell,
  type BacktestGroup,
  type BacktestMatch,
  type BacktestMatches,
  type BacktestModel,
  type BacktestModels,
} from "../api";
import { BasisBadge } from "../components/Badge";
import { SelectField } from "../components/Controls";
import { RangeBar, ROLE_GROUPS, RoleTag, SplitBar, SplitKey, roleRank, wilson } from "../components/ModelBits";
import { ErrorView, Loading, StaleNotice } from "../components/StateViews";
import { TeamBadge } from "../components/TeamBadge";
import { TeamLink } from "../components/TeamLink";
import { formatNumber, formatPct, seasonLabel } from "../format";
import { useMediaQuery } from "../hooks";
import { setQuery } from "../router";
import "./LabExplorer.css";

const PAGE_SIZE = 20;

const WAY_ON = { to: "/lab", label: "Back to the lab" };

const SPLIT_WORD: Record<string, string> = { validation: "validation", test: "test" };

export function BacktestExplorer({ query }: { query: URLSearchParams }) {
  const res = useApi(endpoints.backtestModels(), { freshMs: 600_000 });
  return (
    <>
      <Banner explanation={res.data?.explanation} />
      {res.data === undefined ? (
        res.error ? (
          <ErrorView
            error={res.error}
            what="the backtest explorer's models"
            onRetry={res.reload}
            retrying={res.loading}
            wayOn={WAY_ON}
          />
        ) : (
          <Loading label="Loading the backtest explorer…" lines={2} />
        )
      ) : (
        <Explorer m={res.data} query={query} />
      )}
    </>
  );
}

// ---------------------------------------------------------------------------------------------
// Banner

const PREFIX = /^Backtest, not a record\.\s*/;

function Banner({ explanation }: { explanation?: string }) {
  return (
    <section className="bx-banner" aria-labelledby="bx-banner-title">
      <p className="bx-banner-eyebrow">Backtest · computed after the fact · out of sample</p>
      <h2 id="bx-banner-title" className="bx-banner-title">
        Backtest predictions, not pre-match records
      </h2>
      <p>
        {explanation
          ? explanation.replace(PREFIX, "")
          : "These predictions were computed after the fact by replaying past seasons: each match was predicted only from results before it, so no model had seen it. They were never published before kickoff, so they are not genuine pre-match records and are never counted with them."}
      </p>
      <p className="bx-banner-foot">
        Genuine pre-match records, saved before kickoff, are on each match page ("Every model") and on{" "}
        <a href="#/performance">Performance</a>.
      </p>
    </section>
  );
}

// ---------------------------------------------------------------------------------------------
// Choices from the address

type Order = "old" | "new";

/** The default models: the model of record, the best machine-learning learner on validation,
 * Dixon-Coles and the frequency baseline; topped up to four from the curated order. */
function defaultModels(models: BacktestModel[]): string[] {
  const out: string[] = [];
  const add = (m: BacktestModel | undefined) => {
    if (m && !out.includes(m.model)) out.push(m.model);
  };
  add(models.find((m) => m.role === "model_of_record"));
  const validationRps = (m: BacktestModel) => m.splits.find((s) => s.split === "validation")?.mean_rps ?? Infinity;
  add(models.filter((m) => m.role === "machine_learning").sort((a, b) => validationRps(a) - validationRps(b))[0]);
  add(models.find((m) => m.model === "dixon_coles"));
  add(models.find((m) => m.role === "baseline" && m.model.includes("frequency")));
  for (const m of models) {
    if (out.length >= 4) break;
    add(m);
  }
  return out;
}

function parseModels(raw: string | null, models: BacktestModel[]): string[] | null {
  if (!raw) return null;
  const known = new Set(models.map((m) => m.model));
  const picked = raw.split(",").filter((m) => known.has(m));
  return picked.length ? picked : null;
}

/** Lower case without accents, for team search. */
function norm(s: string): string {
  return s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().trim();
}

// ---------------------------------------------------------------------------------------------
// Explorer

function Explorer({ m, query }: { m: BacktestModels; query: URLSearchParams }) {
  const groups = m.groups.filter((g) => g.divisions.length > 0 && g.coverage.length > 0);
  const divParam = query.get("division");
  const group: BacktestGroup | undefined =
    groups.find((g) => g.divisions.some((d) => d.code === divParam)) ?? groups[0];
  const division = group?.divisions.some((d) => d.code === divParam) ? divParam : (group?.divisions[0]?.code ?? null);
  const seasons = useMemo(
    () =>
      (group?.coverage ?? []).filter((c) => c.division === division).sort((a, b) => b.season.localeCompare(a.season)),
    [group, division],
  );
  const seasonParam = query.get("season");
  const season = seasons.find((s) => s.season === seasonParam)?.season ?? seasons[0]?.season ?? null;
  const asked = seasonParam !== null && season !== seasonParam ? seasonParam : null;
  const askedDivision = divParam !== null && divParam !== division ? divParam : null;

  const groupModels = useMemo(() => m.models.filter((x) => x.group === group?.group), [m.models, group]);
  const chosen = parseModels(query.get("models"), groupModels) ?? defaultModels(groupModels);
  const isDefault = query.get("models") === null;
  const teamParam = query.get("team") ?? "";
  const order: Order = query.get("order") === "new" ? "new" : "old";
  const pageParam = Number.parseInt(query.get("page") ?? "1", 10);

  const res = useApi(division && season ? endpoints.backtestMatches(division, season) : null, { freshMs: 600_000 });

  if (!group || !division || !season) {
    return (
      <section className="card">
        <h2>No backtests to explore</h2>
        <p>No saved backtest ledger covers a league or competition yet.</p>
      </section>
    );
  }

  const divisionName = group.divisions.find((d) => d.code === division)?.name ?? division;
  const split = seasons.find((s) => s.season === season)?.split ?? "";
  const where = `${divisionName} ${seasonLabel(season)}`;

  return (
    <>
      <Filters
        m={m}
        groups={groups}
        group={group}
        division={division}
        season={season}
        seasons={seasons}
        groupModels={groupModels}
        chosen={chosen}
        isDefault={isDefault}
        teamParam={teamParam}
        order={order}
        data={
          res.data && res.data.filters.division === division && res.data.filters.season === season
            ? res.data
            : undefined
        }
      />
      {askedDivision && (
        <StaleNotice>
          There are no backtest predictions for “{askedDivision}”. Showing{" "}
          {group.divisions.find((d) => d.code === division)?.name ?? division} instead.
        </StaleNotice>
      )}
      {asked && !askedDivision && (
        <StaleNotice>
          There are no backtest predictions for {divisionName} {seasonLabel(asked)}. Showing {seasonLabel(season)}{" "}
          instead.
        </StaleNotice>
      )}
      {res.data === undefined ? (
        res.error ? (
          <ErrorView
            error={res.error}
            what={`the ${where} backtest`}
            onRetry={res.reload}
            retrying={res.loading}
            wayOn={WAY_ON}
          />
        ) : (
          <Loading label={`Loading the ${where} backtest…`} lines={2} />
        )
      ) : (
        <Results
          key={`${division}-${season}`}
          data={res.data}
          m={m}
          where={where}
          split={split}
          splitNote={m.splits[split] ?? null}
          chosen={chosen}
          teamParam={teamParam}
          order={order}
          pageParam={pageParam}
        />
      )}
    </>
  );
}

// ---------------------------------------------------------------------------------------------
// Filters

function Filters({
  m,
  groups,
  group,
  division,
  season,
  seasons,
  groupModels,
  chosen,
  isDefault,
  teamParam,
  order,
  data,
}: {
  m: BacktestModels;
  groups: BacktestGroup[];
  group: BacktestGroup;
  division: string;
  season: string;
  seasons: BacktestGroup["coverage"];
  groupModels: BacktestModel[];
  chosen: string[];
  isDefault: boolean;
  teamParam: string;
  order: Order;
  data: BacktestMatches | undefined;
}) {
  const phone = useMediaQuery("(max-width: 719px)");
  const [pickerOpen, setPickerOpen] = useState<boolean | null>(null);
  const open = pickerOpen ?? !phone;
  const teams = useMemo(
    () => Object.entries(data?.teams ?? {}).sort(([, a], [, b]) => a.name.localeCompare(b.name)),
    [data],
  );
  const teamName = data?.teams[teamParam]?.name;
  const available = (model: string) => {
    if (data) return (data.summary.find((s) => s.model === model)?.matches ?? 0) > 0;
    const x = groupModels.find((g) => g.model === model);
    return !!x?.splits.some((s) => s.seasons.includes(season));
  };
  const unavailable = groupModels.filter((x) => !available(x.model));
  const chosenLabels = groupModels.filter((x) => chosen.includes(x.model) && available(x.model)).map((x) => x.label);

  const setModels = (next: string[]) => setQuery({ models: next.length ? next.join(",") : null, page: null });
  const toggle = (model: string) => {
    const next = chosen.includes(model) ? chosen.filter((x) => x !== model) : [...chosen, model];
    // Keep the curated order in the address.
    setModels(groupModels.map((x) => x.model).filter((x) => next.includes(x)));
  };

  const byRole = new Map<string, BacktestModel[]>();
  for (const x of [...groupModels].sort((a, b) => roleRank(a.role) - roleRank(b.role))) {
    const key = ROLE_GROUPS[x.role] ?? ROLE_GROUPS.other ?? "Other models";
    const list = byRole.get(key);
    if (list) list.push(x);
    else byRole.set(key, [x]);
  }

  const onTeam = (text: string) => {
    const exact = teams.find(([, t]) => norm(t.name) === norm(text));
    setQuery({ team: exact ? exact[0] : text || null, page: null });
  };

  return (
    <section className="card bx-filters" aria-label="Backtest explorer filters">
      <div className="ctl-row">
        <SelectField
          id="bx-division"
          label="League or competition"
          value={division}
          groups={groups.map((g) => ({
            label: g.name,
            options: g.divisions
              .filter((d) => g.coverage.some((c) => c.division === d.code))
              .map((d) => ({ value: d.code, label: d.name })),
          }))}
          onChange={(v) => {
            const next = groups.find((g) => g.divisions.some((d) => d.code === v));
            // The same season if the new choice has it (else its latest); a change of group
            // (league games ↔ UEFA) has other models, so back to that group's defaults.
            const keepSeason = next?.coverage.some((c) => c.division === v && c.season === season);
            setQuery({
              division: v,
              season: keepSeason ? season : null,
              page: null,
              ...(next && next.group !== group.group ? { models: null } : {}),
            });
          }}
        />
        <SelectField
          id="bx-season"
          label="Season"
          value={season}
          options={seasons.map((s) => ({
            value: s.season,
            label: `${seasonLabel(s.season)} (${SPLIT_WORD[s.split] ?? s.split})`,
          }))}
          onChange={(v) => setQuery({ season: v, page: null })}
        />
        <div className="ctl-field bx-team-field">
          <label htmlFor="bx-team" className="ctl-label">
            Team
          </label>
          <input
            id="bx-team"
            className="bx-team-input"
            type="search"
            list="bx-team-list"
            autoComplete="off"
            placeholder="All teams"
            value={teamName ?? teamParam}
            onChange={(e) => onTeam(e.target.value)}
          />
          <datalist id="bx-team-list">
            {teams.map(([key, t]) => (
              <option key={key} value={t.name} />
            ))}
          </datalist>
        </div>
        <div className="ctl-field ctl-seg" role="group" aria-label="Order">
          <span className="ctl-label" aria-hidden="true">
            Order
          </span>
          <div className="segmented segmented-scroll">
            {(
              [
                ["old", "Oldest first"],
                ["new", "Newest first"],
              ] as const
            ).map(([v, label]) => (
              <button
                key={v}
                type="button"
                aria-pressed={order === v}
                onClick={() => setQuery({ order: v === "old" ? null : v, page: null })}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="bx-picker">
        <div className="bx-picker-head">
          <span className="ctl-label" id="bx-models-label">
            Models · {formatNumber(chosenLabels.length, 0)} chosen
          </span>
          <button
            type="button"
            className="bx-picker-toggle"
            aria-expanded={open}
            aria-controls="bx-models"
            onClick={() => setPickerOpen(!open)}
          >
            {open ? "Hide the list" : "Change models"}
          </button>
        </div>
        {!open && <p className="bx-chosen small">{chosenLabels.join(" · ") || "None chosen"}</p>}
        <div id="bx-models" className="bx-model-groups" role="group" aria-labelledby="bx-models-label" hidden={!open}>
          {[...byRole.entries()].map(([name, list]) => (
            <div key={name} className="bx-model-group">
              <span className="bx-model-group-name">{name}</span>
              <div className="chips">
                {list.map((x) => {
                  const ok = available(x.model);
                  return (
                    <button
                      key={x.model}
                      type="button"
                      className="chip bx-chip"
                      aria-pressed={ok && chosen.includes(x.model)}
                      disabled={!ok}
                      title={ok ? (x.description ?? undefined) : `Not run on ${seasonLabel(season)}`}
                      onClick={() => toggle(x.model)}
                    >
                      {x.basis === "model_and_market" && (
                        <span className="bx-chip-market" aria-hidden="true" title="Model + market" />
                      )}
                      {x.label}
                      {x.basis === "model_and_market" && <span className="visually-hidden"> (model + market)</span>}
                    </button>
                  );
                })}
              </div>
            </div>
          ))}
          <div className="bx-picker-actions">
            <button type="button" onClick={() => setModels([])} disabled={isDefault}>
              Default models
            </button>
            <button
              type="button"
              onClick={() => setModels(groupModels.filter((x) => available(x.model)).map((x) => x.model))}
            >
              Every model run on {seasonLabel(season)}
            </button>
          </div>
          {unavailable.length > 0 && (
            <p className="muted small">
              Greyed out: not run on {seasonLabel(season)}.{" "}
              {seasons.find((s) => s.season === season)?.split === "test"
                ? "Only the models of record, the leading learner and challenger and the baselines were run once on the test seasons."
                : ""}
            </p>
          )}
          <p className="muted small">
            <span className="bx-chip-market bx-chip-market-key" aria-hidden="true" /> marks model + market forecasts,
            which use bookmakers' prices collected before each match.
          </p>
        </div>
      </div>
      {m.groups.some((g) => g.skipped_runs.length > 0) && (
        <p className="muted small">
          Some saved runs were left out of the explorer because they are not model forecasts.
        </p>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------------------------
// Results: summary strip and matches

type Col = { index: number; model: string; label: string; role: string; basis: string; info?: BacktestModel };

function Results({
  data,
  m,
  where,
  split,
  splitNote,
  chosen,
  teamParam,
  order,
  pageParam,
}: {
  data: BacktestMatches;
  m: BacktestModels;
  where: string;
  split: string;
  splitNote: string | null;
  chosen: string[];
  teamParam: string;
  order: Order;
  pageParam: number;
}) {
  const listTop = useRef<HTMLHeadingElement>(null);
  const cols: Col[] = data.models
    .map((x, index) => ({ ...x, index, info: m.models.find((y) => y.model === x.model && y.group === data.group) }))
    .filter((c) => chosen.includes(c.model) && (data.summary[c.index]?.matches ?? 0) > 0);

  // Team: a key from a link or the list, or text matched against names.
  const teamKey = teamParam && data.teams[teamParam] ? teamParam : null;
  const needle = teamKey ? "" : norm(teamParam);
  const teamText = teamKey ? (data.teams[teamKey]?.name ?? teamKey) : teamParam.trim();
  const rows = useMemo(() => {
    const filtered = data.matches.filter(
      (r) =>
        (teamKey
          ? r.home === teamKey || r.away === teamKey
          : !needle || norm(r.home_name).includes(needle) || norm(r.away_name).includes(needle)) &&
        cols.some((c) => r.p[c.index]),
    );
    return order === "new" ? filtered.reverse() : filtered;
    // `cols` is rebuilt on every render; the models in it are what matters.
  }, [data, teamKey, needle, order, cols.map((c) => c.model).join(",")]);

  const pages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  const page = Number.isFinite(pageParam) ? Math.min(Math.max(1, pageParam), pages) : 1;
  const start = (page - 1) * PAGE_SIZE;
  const shown = rows.slice(start, start + PAGE_SIZE);
  const filteredByTeam = teamParam.trim() !== "";

  // A page change brings the top of the list into view.
  const lastPage = useRef(page);
  useEffect(() => {
    if (lastPage.current === page) return;
    lastPage.current = page;
    const el = listTop.current;
    if (el && el.getBoundingClientRect().top < 0) {
      const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      el.scrollIntoView({ block: "start", behavior: reduce ? "auto" : "smooth" });
    }
  }, [page]);

  if (cols.length === 0) {
    return (
      <section className="card bx-none">
        <h2>None of the chosen models was run on {where}</h2>
        <p>Only some models were run on the test seasons. Choose others above, or go back to the defaults.</p>
        <button type="button" className="button-primary" onClick={() => setQuery({ models: null, page: null })}>
          Use the default models
        </button>
      </section>
    );
  }

  return (
    <>
      {rows.length > 0 && (
        <Summary
          data={data}
          cols={cols}
          rows={rows}
          where={where}
          split={split}
          splitNote={splitNote}
          teamText={filteredByTeam ? teamText : null}
        />
      )}

      <section className="card bx-list-card" aria-labelledby="bx-list-title">
        <div className="bx-list-head">
          <h2 id="bx-list-title" ref={listTop} tabIndex={-1}>
            Match by match
          </h2>
          <span className="badge badge-warn" title="Computed after the fact; not a pre-match record">
            Backtest
          </span>
        </div>
        {rows.length === 0 ? (
          <div className="bx-empty">
            <p>
              <strong>
                No {where} matches for “{teamText}”.
              </strong>
            </p>
            <p className="muted small">
              Check the spelling, or try another season or competition: the team may not have played in this one.
            </p>
            <button type="button" onClick={() => setQuery({ team: null, page: null })}>
              Show every team
            </button>
          </div>
        ) : (
          <>
            <p className="muted small bx-count">
              {rows.length > PAGE_SIZE
                ? `Matches ${formatNumber(start + 1, 0)}–${formatNumber(start + shown.length, 0)} of ${formatNumber(rows.length, 0)}`
                : `${formatNumber(rows.length, 0)} ${rows.length === 1 ? "match" : "matches"}`}
              {filteredByTeam ? ` with ${teamText}` : ""}, {order === "new" ? "newest" : "oldest"} first.
            </p>
            <SplitKey>
              <span className="muted">Bold: the model's pick, ✓ right or ✗ wrong</span>
            </SplitKey>
            <ol className="bx-matches">
              {shown.map((r) => (
                <MatchCard key={`${r.date}-${r.home}-${r.away}`} r={r} cols={cols} data={data} />
              ))}
            </ol>
            {pages > 1 && <Pager page={page} pages={pages} />}
          </>
        )}
      </section>
    </>
  );
}

function Pager({ page, pages }: { page: number; pages: number }) {
  return (
    <nav className="bx-pager" aria-label="Pages of matches">
      <button type="button" disabled={page <= 1} onClick={() => setQuery({ page: page - 1 > 1 ? page - 1 : null })}>
        <span aria-hidden="true">←</span> Previous
      </button>
      <span className="bx-page-of" aria-live="polite">
        Page {formatNumber(page, 0)} of {formatNumber(pages, 0)}
      </span>
      <button type="button" disabled={page >= pages} onClick={() => setQuery({ page: page + 1 })}>
        Next <span aria-hidden="true">→</span>
      </button>
    </nav>
  );
}

type Tile = {
  col: Col;
  matches: number;
  right: number;
  hit: number | null;
  ci: [number, number] | null;
  rps: number | null;
};

function Summary({
  data,
  cols,
  rows,
  where,
  split,
  splitNote,
  teamText,
}: {
  data: BacktestMatches;
  cols: Col[];
  rows: BacktestMatch[];
  where: string;
  split: string;
  splitNote: string | null;
  teamText: string | null;
}) {
  const tiles: Tile[] = cols.map((col) => {
    if (teamText === null) {
      const s = data.summary[col.index];
      return {
        col,
        matches: s?.matches ?? 0,
        right: s?.results_right ?? 0,
        hit: s?.hit_rate ?? null,
        ci: s?.ci95 ?? null,
        rps: s?.mean_rps ?? null,
      };
    }
    let n = 0;
    let k = 0;
    let sum = 0;
    for (const r of rows) {
      const c: BacktestCell | null | undefined = r.p[col.index];
      if (!c) continue;
      n += 1;
      k += c[5];
      sum += c[6];
    }
    return { col, matches: n, right: k, hit: n ? k / n : null, ci: wilson(k, n), rps: n ? sum / n : null };
  });
  const rpsList = tiles.map((t) => t.rps).filter((v): v is number => v !== null);
  const best = rpsList.length > 1 && Math.max(...rpsList) - Math.min(...rpsList) > 1e-9 ? Math.min(...rpsList) : null;
  const notes = cols.filter((c) => c.info?.note);
  const market = cols.filter((c) => c.basis === "model_and_market");
  const fewest = Math.min(...tiles.map((t) => t.matches));

  return (
    <section className="card bx-summary" aria-labelledby="bx-summary-title">
      <div className="bx-list-head">
        <h2 id="bx-summary-title">
          {where}
          {teamText ? `: ${teamText}` : ""}
        </h2>
        <span className="badge badge-warn">Backtest · {SPLIT_WORD[split] ?? split}</span>
      </div>
      {splitNote && <p className="muted small">{splitNote}</p>}
      <ul className="bx-strip">
        {tiles.map((t) => {
          const isBest = best !== null && t.rps !== null && t.rps - best < 1e-9;
          return (
            <li key={t.col.model} className={`bx-tile${isBest ? " is-best" : ""}`}>
              <span className="bx-tile-name" title={t.col.info?.description ?? undefined}>
                {t.col.label}
              </span>
              <span className="bx-tile-tags">
                <RoleTag role={t.col.role} short />
                {t.col.basis === "model_and_market" && <BasisBadge basis="model_and_market" />}
                {isBest && <span className="best-tag">Best RPS</span>}
              </span>
              <dl className="bx-tile-figs">
                <div>
                  <dt>Results right</dt>
                  <dd>
                    <span className="bx-tile-value">{formatPct(t.hit)}</span>
                    <span className="bx-tile-sub">
                      {formatNumber(t.right, 0)} of {formatNumber(t.matches, 0)}
                      {t.ci && (
                        <>
                          {" "}
                          · 95%: {formatPct(t.ci[0])}–{formatPct(t.ci[1])}
                        </>
                      )}
                    </span>
                    <RangeBar value={t.hit} ci={t.ci} />
                  </dd>
                </div>
                <div>
                  <dt>Mean RPS</dt>
                  <dd>
                    <span className={`bx-tile-value${isBest ? " is-strong" : ""}`}>
                      {t.rps !== null ? formatNumber(t.rps, 4) : "—"}
                    </span>
                    <span className="bx-tile-sub">lower is better</span>
                  </dd>
                </div>
                <div>
                  <dt>Matches</dt>
                  <dd>
                    <span className="bx-tile-value bx-tile-value-sm">{formatNumber(t.matches, 0)}</span>
                  </dd>
                </div>
              </dl>
            </li>
          );
        })}
      </ul>
      <ul className="facts small bx-summary-notes">
        {teamText !== null && (
          <li>
            Worked out on this page from the matches listed below (RPS from the per-match figures, rounded to four
            places).
            {fewest < 100 && " Fewer than 100 matches: the intervals are wide, and small differences mean little."}
          </li>
        )}
        <li>
          Results right: how often the most likely match result (home, draw or away) happened, with its 95% interval.
          RPS: ranked probability score of the match-result forecast, lower is better.
        </li>
        {market.length > 0 && (
          <li>
            Model + market forecasts move a model's score grid onto the bookmakers' consensus for the match result and
            over/under 2.5 goals, so where prices were available they often show the same figures as each other.
          </li>
        )}
        {notes.map((c) => (
          <li key={c.model}>
            {c.label}: {c.info?.note}
          </li>
        ))}
      </ul>
    </section>
  );
}

const dayFmt = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Europe/London",
  weekday: "short",
  day: "numeric",
  month: "short",
  year: "numeric",
});

function matchDay(isoDate: string): string {
  const d = new Date(`${isoDate.slice(0, 10)}T12:00:00Z`);
  return Number.isNaN(d.getTime()) ? isoDate : dayFmt.format(d).replace(",", "");
}

function MatchCard({ r, cols, data }: { r: BacktestMatch; cols: Col[]; data: BacktestMatches }) {
  const [hg, ag] = r.score;
  const result = hg > ag ? "Home win" : hg < ag ? "Away win" : "Draw";
  return (
    <li className="bx-match">
      <div className="bx-meta">
        <span>{matchDay(r.date)}</span>
        {r.stage && <span className="bx-stage">{r.stage}</span>}
        <span className="bx-result">{result}</span>
      </div>
      <p className="bx-teams">
        <span className="bx-team bx-home">
          <TeamBadge teamKey={r.home} name={r.home_name} media={data.teams[r.home]?.media} size={22} />
          <TeamLink teamKey={r.home}>{r.home_name}</TeamLink>
        </span>
        <span className="bx-score">
          {hg}–{ag}
        </span>
        <span className="bx-team bx-away">
          <TeamLink teamKey={r.away}>{r.away_name}</TeamLink>
          <TeamBadge teamKey={r.away} name={r.away_name} media={data.teams[r.away]?.media} size={22} />
        </span>
      </p>
      <ul className="bx-preds" aria-label="Each chosen model's backtest prediction">
        {cols.map((c) => {
          const cell = r.p[c.index];
          return (
            <li key={c.model} className={`bx-pred${cell ? "" : " is-missing"}`}>
              <span className="bx-pred-name">{c.label}</span>
              {cell ? (
                <>
                  <SplitBar
                    className="bx-pred-split"
                    probs={{ H: cell[0], D: cell[1], A: cell[2] }}
                    homeName={r.home_name}
                    awayName={r.away_name}
                    correct={cell[5] === 1}
                  />
                  <span className="bx-pred-extra">
                    <span>
                      <span className="bx-k">O2.5</span> {formatPct(cell[3])}
                    </span>
                    <span>
                      <span className="bx-k">BTTS</span> {formatPct(cell[4])}
                    </span>
                    <span>
                      <span className="bx-k">RPS</span> {formatNumber(cell[6], 3)}
                    </span>
                  </span>
                </>
              ) : (
                <span className="muted small">No prediction for this match</span>
              )}
            </li>
          );
        })}
      </ul>
    </li>
  );
}
