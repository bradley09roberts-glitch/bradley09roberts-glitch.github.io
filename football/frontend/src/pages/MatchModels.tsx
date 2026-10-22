// The match page's "Every model" section: every model's genuine pre-match record for this
// fixture (/api/fixtures/{ref}/models), side by side. The model of record first, then the
// machine-learning learners, challengers and baselines, each with its role, its match-result
// split, over 2.5 goals, both teams to score and expected goals; once the match is settled, a
// tick or cross per market and the match's RPS, with the best score highlighted. A long list
// has a subheading per role. Where the section is too narrow for a row per model (the same
// 640 px of width the stylesheet's container query uses), the first few show, with "Show all
// N models".
//
// Only genuine pre-match records are here (the response's `kind`); backtests are in the lab.
// Every number is the record's own; nothing is computed except which RPS is lowest.

import { useEffect, useRef, useState, type RefObject } from "react";
import { endpoints, useApi, type FixtureModel, type FixtureModels, type PredictionDetail } from "../api";
import { BasisBadge, editionLabel } from "../components/Badge";
import {
  Mark,
  ROLE_GROUPS,
  RoleTag,
  SplitBar,
  SplitKey,
  outcomeWord,
  resultOf,
  roleRank,
} from "../components/ModelBits";
import { notInSnapshot } from "../components/StateViews";
import { formatKickoff, formatLondon, formatNumber, formatPct, parseUtc, settlementStatusLabel } from "../format";
import { useNarrowerThan } from "../hooks";
import { href, savedScrollPosition } from "../router";
import "./MatchModels.css";

/** Rows shown in a narrow section before "Show all". */
const PHONE_ROWS = 5;
/** Below this section width the rows stack (MatchModels.css, its container query). */
const ROW_LAYOUT_PX = 640;
/** A list this long gets a subheading per role. */
const GROUPED_FROM = PHONE_ROWS + 2;

/** Two RPS values this close are a tie. */
const TIE = 1e-9;

export function EveryModel({ d, focus = false }: { d: PredictionDetail; focus?: boolean }) {
  // Only fixtures with a genuine pre-match record have a list (the API answers 404 otherwise).
  const genuine = d.record_type === "genuine_prematch";
  const res = useApi(genuine ? endpoints.fixtureModels(d.fixture_ref) : null, { freshMs: 60_000 });
  const section = useRef<HTMLElement>(null);
  const loaded = res.data !== undefined;
  // Opened from a fixture card's "Compare every model": once the list is in, bring it into
  // view; only on a new visit, so Back keeps the reader's own place.
  const scrolled = useRef(false);
  useEffect(() => {
    if (!focus || !loaded || scrolled.current || savedScrollPosition() !== 0) return;
    scrolled.current = true;
    const reduce = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    section.current?.scrollIntoView({ block: "start", behavior: reduce ? "auto" : "smooth" });
  }, [focus, loaded]);
  if (!genuine) return null;
  const { data, error, loading, reload } = res;
  // A fixture the API (or this copy) has no list for: nothing to show, so no section.
  if (!data && error && (error.notFound || notInSnapshot(error))) return null;

  return (
    <section className="card every-model" aria-labelledby="every-model" ref={section} id="models">
      <div className="em-head">
        <h2 id="every-model">Every model</h2>
        <span className="badge badge-good" title="Saved before kickoff and never changed">
          Genuine pre-match
        </span>
      </div>
      {data ? (
        <ModelList f={data} d={d} section={section} />
      ) : error ? (
        <div className="em-error" role="alert">
          <p>Couldn't load the other models' records: {error.message}</p>
          <button type="button" onClick={reload} disabled={loading}>
            {loading ? "Trying again…" : "Try again"}
          </button>
        </div>
      ) : (
        <div className="em-loading" role="status">
          <span className="visually-hidden">Loading every model's record…</span>
          <span className="sk sk-line" aria-hidden="true" />
          <span className="sk sk-bar" aria-hidden="true" />
          <span className="sk sk-line sk-short" aria-hidden="true" />
        </div>
      )}
    </section>
  );
}

function ordered(models: FixtureModel[]): FixtureModel[] {
  // The record of record first; then by role; the API's order within a role.
  return models
    .map((m, i) => ({ m, i }))
    .sort(
      (a, b) =>
        Number(b.m.is_record_of_record) - Number(a.m.is_record_of_record) ||
        roleRank(a.m.role) - roleRank(b.m.role) ||
        a.i - b.i,
    )
    .map((x) => x.m);
}

type Group = { name: string; models: FixtureModel[] };

/** Consecutive models of the same role group ("Models of record", "Machine learning", …). */
function byRole(models: FixtureModel[]): Group[] {
  const out: Group[] = [];
  for (const m of models) {
    const name = ROLE_GROUPS[m.role] ?? ROLE_GROUPS.other ?? "Other models";
    const last = out[out.length - 1];
    if (last && last.name === name) last.models.push(m);
    else out.push({ name, models: [m] });
  }
  return out;
}

/** True when at least two model + market records give the same match-result figures (to
 * 0.1 points): each moved its grid onto the same bookmakers' consensus. */
function sharedConsensus(models: FixtureModel[]): boolean {
  const seen = new Set<string>();
  for (const m of models) {
    const x = m.probabilities["1X2"];
    if (m.basis !== "model_and_market" || !x) continue;
    const key = [x.H, x.D, x.A].map((v) => v.toFixed(3)).join("/");
    if (seen.has(key)) return true;
    seen.add(key);
  }
  return false;
}

function ModelList({
  f,
  d,
  section,
}: {
  f: FixtureModels;
  d: PredictionDetail;
  section: RefObject<HTMLElement | null>;
}) {
  const currentId = d.id;
  // The same width the stylesheet's container query reads (the section fills that column).
  const narrow = useNarrowerThan(section, ROW_LAYOUT_PX);
  const [expanded, setExpanded] = useState(false);
  const [open, setOpen] = useState<string | null>(null);
  const models = ordered(f.models);
  const r = f.result;
  const scored = r !== null && r.home_goals !== null && r.away_goals !== null;
  const rpsValues = models.map((m) => m.settlement?.rps).filter((v): v is number => typeof v === "number");
  const best = rpsValues.length > 1 ? Math.min(...rpsValues) : null;
  // Highlight only a real winner: when every model scores the same, nobody is best.
  const isBest = (m: FixtureModel) =>
    best !== null &&
    typeof m.settlement?.rps === "number" &&
    m.settlement.rps - best < TIE &&
    rpsValues.some((v) => v - best >= TIE);
  const bestModels = models.filter(isBest);
  const collapsible = narrow && models.length > PHONE_ROWS + 1;
  const collapsed = collapsible && !expanded;
  const shown = collapsed ? models.slice(0, PHONE_ROWS) : models;
  const groups = models.length >= GROUPED_FROM ? byRole(shown) : [{ name: "", models: shown }];
  const anyBeforePrices = models.some((m) => m.expected_goals_before_prices && m.probabilities.expected_goals);
  const consensus = sharedConsensus(models);
  const own = d.settlement;
  const kickoff = f.kickoff_utc ?? d.kickoff_utc;
  const kickedOff = kickoff !== null && parseUtc(kickoff).getTime() <= Date.now();

  return (
    <>
      <p className="em-intro">
        {models.length === 1 ? (
          <>Only one model has a genuine pre-match record for this match so far.</>
        ) : (
          <>
            {formatNumber(models.length, 0)} models each saved their own forecast for this match before kickoff. Each
            record is kept as published and has its own page.
          </>
        )}
      </p>
      {scored && r ? (
        <div className="em-result">
          <p>
            <strong>
              {f.home_name} {r.home_goals}–{r.away_goals} {f.away_name}
            </strong>{" "}
            <span className="muted">
              ({outcomeWord(resultOf(r.home_goals ?? 0, r.away_goals ?? 0))},{" "}
              {(r.home_goals ?? 0) + (r.away_goals ?? 0)} goals,{" "}
              {(r.home_goals ?? 0) > 0 && (r.away_goals ?? 0) > 0 ? "both scored" : "not both scored"})
            </span>
          </p>
          {bestModels.length > 0 && best !== null && (
            <p className="small">
              Best on this match: <strong>{bestModels.map((m) => m.label).join(", ")}</strong> (RPS{" "}
              {formatNumber(best, 3)})
            </p>
          )}
        </div>
      ) : own && own.status !== "settled" ? (
        <p className="muted small">
          {settlementStatusLabel(own.status)}: no ticks, crosses or scores for this match.
        </p>
      ) : kickedOff ? (
        <p className="muted small">
          Result not in yet: ticks, crosses and each model's score appear once the match is settled.
        </p>
      ) : (
        <p className="muted small">
          Not played yet: ticks, crosses and each model's score appear once the match is settled.
        </p>
      )}
      <SplitKey home={f.home_name} away={f.away_name} />

      <div className={`em-table${scored ? " is-settled" : ""}`}>
        <div className="em-row em-colhead" aria-hidden="true">
          <span>Model</span>
          <span>Match result</span>
          <span className="em-num">Over 2.5</span>
          <span className="em-num">BTTS</span>
          <span className="em-num">xG</span>
          {scored && <span className="em-num">RPS</span>}
        </div>
        {groups.map((g) => (
          <div key={g.name || "all"} className="em-group">
            {g.name && <h3 className="em-group-name">{g.name}</h3>}
            <ol
              className="em-list"
              aria-label={g.name ? `${g.name}: each model's record` : "Every model's record, model of record first"}
            >
              {g.models.map((m) => (
                <ModelRow
                  key={m.model}
                  m={m}
                  f={f}
                  current={m.prediction_id === currentId}
                  best={isBest(m)}
                  settled={scored}
                  open={open === m.model}
                  onToggle={() => setOpen((o) => (o === m.model ? null : m.model))}
                />
              ))}
            </ol>
          </div>
        ))}
      </div>

      {collapsible && (
        <button type="button" className="em-more" aria-expanded={expanded} onClick={() => setExpanded((e) => !e)}>
          {expanded ? "Show fewer models" : `Show all ${formatNumber(models.length, 0)} models`}
        </button>
      )}

      <ul className="facts em-notes small">
        {scored && (
          <li>
            ✓ or ✗: whether the model's more likely outcome happened (for over 2.5 and both teams to score, a figure
            under 50% means it expected the opposite). RPS scores the match-result forecast: lower is better.
          </li>
        )}
        <li>
          BTTS: both teams to score. xG: the goals each side was expected to score, home first (model estimates, not
          shot-based xG). Saved times are UK time.
        </li>
        {anyBeforePrices && (
          <li>
            * The same expected goals as its pure model's own record for this match: the bookmakers' prices moved
            this record's score probabilities, not these averages.
          </li>
        )}
        {consensus && (
          <li>
            Model + market records move each model's score grid onto the same bookmakers' consensus, so several of
            them give the same match-result figures.
          </li>
        )}
        <li>
          Predictions computed after the fact, for past seasons, are in the lab's{" "}
          <a href={href("/lab", { tab: "explorer" })}>backtest explorer</a>; they are never mixed with these records.
        </li>
      </ul>
    </>
  );
}

function ModelRow({
  m,
  f,
  current,
  best,
  settled,
  open,
  onToggle,
}: {
  m: FixtureModel;
  f: FixtureModels;
  current: boolean;
  best: boolean;
  settled: boolean;
  open: boolean;
  onToggle: () => void;
}) {
  const p = m.probabilities;
  const x = p["1X2"];
  const ou = p["OU_2.5"];
  const bt = p.BTTS;
  const xg = p.expected_goals;
  const s = m.settlement;
  const descId = `em-desc-${m.model}`;
  const cls = ["em-row", best ? "is-best" : "", current ? "is-current" : ""].filter(Boolean).join(" ");
  return (
    <li className={cls}>
      <div className="em-model">
        <div className="em-name-line">
          {m.description ? (
            <button
              type="button"
              className="em-name"
              aria-expanded={open}
              aria-controls={descId}
              title={m.description}
              onClick={onToggle}
            >
              <span>{m.label}</span>
              <span className="em-info" aria-hidden="true">
                i
              </span>
              <span className="visually-hidden">{open ? " (hide description)" : " (show description)"}</span>
            </button>
          ) : (
            <span className="em-name em-name-plain">{m.label}</span>
          )}
        </div>
        <span className="em-tags">
          <RoleTag role={m.role} roles={f.roles} short />
          {m.basis === "model_and_market" && <BasisBadge basis={m.basis} />}
          {best && <span className="best-tag">Best RPS</span>}
        </span>
        {m.description && (
          <p id={descId} className="em-desc" hidden={!open}>
            {m.description}
          </p>
        )}
      </div>

      <div className="em-split">
        {x ? (
          <SplitBar
            probs={x}
            homeName={f.home_name}
            awayName={f.away_name}
            correct={s?.markets["1X2"]?.correct ?? null}
            pick={pickCode(s?.markets["1X2"]?.pick)}
          />
        ) : (
          <span className="muted small">No match result</span>
        )}
      </div>

      <Fig area="ou" label="Over 2.5" value={ou ? formatPct(ou.O) : "—"} mark={s?.markets["OU_2.5"]?.correct} />
      <Fig area="bt" label="BTTS" value={bt ? formatPct(bt.Y) : "—"} mark={s?.markets.BTTS?.correct} />
      <Fig
        area="xg"
        label="xG"
        value={
          xg
            ? `${formatNumber(xg.home, 2)}–${formatNumber(xg.away, 2)}${m.expected_goals_before_prices ? "*" : ""}`
            : "—"
        }
      />
      {settled && (
        <Fig
          area="rps"
          label="RPS"
          value={s?.rps !== null && s?.rps !== undefined ? formatNumber(s.rps, 3) : "—"}
          strong={best}
        />
      )}

      <span className="em-open">
        {current ? (
          <span className="muted small">This page</span>
        ) : (
          <a
            href={href(`/match/${m.prediction_id}`)}
            title={`${m.label}: saved ${formatLondon(m.published_at_utc)} UK time`}
          >
            Record<span className="visually-hidden"> of {m.label}</span>
          </a>
        )}
        <span className="em-saved muted">
          {m.edition === "early" ? "Saved" : `${editionLabel(m.edition)}, saved`}{" "}
          {formatKickoff(m.published_at_utc, m.published_at_utc.slice(0, 10))}
          {m.later_editions.length > 0 &&
            ` · ${m.later_editions.length} later ${m.later_editions.length === 1 ? "edition" : "editions"}`}
        </span>
      </span>
    </li>
  );
}

/** The settled pick ("H", "D" or "A"), so the mark sits on the outcome it was scored on. */
function pickCode(v: string | undefined): "H" | "D" | "A" | null {
  return v === "H" || v === "D" || v === "A" ? v : null;
}

function Fig({
  area,
  label,
  value,
  mark,
  strong = false,
}: {
  area: "ou" | "bt" | "xg" | "rps";
  label: string;
  value: string;
  mark?: boolean;
  strong?: boolean;
}) {
  return (
    <span className={`em-fig em-${area}${strong ? " is-strong" : ""}`}>
      <span className="em-fig-label">{label}</span>
      <span className="em-fig-value">
        {value}
        {mark !== undefined && <Mark ok={mark} />}
      </span>
    </span>
  );
}
