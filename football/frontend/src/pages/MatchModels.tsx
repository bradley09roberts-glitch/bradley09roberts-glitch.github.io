// The match page's "Every model" section: every model's genuine pre-match record for this
// fixture (/api/fixtures/{ref}/models), side by side. The model of record first, then the
// machine-learning learners, challengers and baselines, each with its role, its match-result
// split, over 2.5 goals, both teams to score and expected goals; once the match is settled, a
// tick or cross per market and the match's RPS, with the best score highlighted. On a phone
// the first few show, with "Show all N models".
//
// Only genuine pre-match records are here (the response's `kind`); backtests are in the lab.
// Every number is the record's own; nothing is computed except which RPS is lowest.

import { useState } from "react";
import { endpoints, useApi, type FixtureModel, type FixtureModels, type PredictionDetail } from "../api";
import { BasisBadge, editionLabel } from "../components/Badge";
import { Mark, RoleTag, SplitBar, SplitKey, outcomeWord, resultOf, roleRank } from "../components/ModelBits";
import { notInSnapshot } from "../components/StateViews";
import { formatKickoff, formatLondon, formatNumber, formatPct } from "../format";
import { useMediaQuery } from "../hooks";
import { href } from "../router";
import "./MatchModels.css";

/** Rows shown on a phone before "Show all". */
const PHONE_ROWS = 5;

/** Two RPS values this close are a tie. */
const TIE = 1e-9;

export function EveryModel({ d }: { d: PredictionDetail }) {
  // Only fixtures with a genuine pre-match record have a list (the API answers 404 otherwise).
  const genuine = d.record_type === "genuine_prematch";
  const res = useApi(genuine ? endpoints.fixtureModels(d.fixture_ref) : null, { freshMs: 60_000 });
  if (!genuine) return null;
  const { data, error, loading, reload } = res;
  // A fixture the API (or this copy) has no list for: nothing to show, so no section.
  if (!data && error && (error.notFound || notInSnapshot(error))) return null;

  return (
    <section className="card every-model" aria-labelledby="every-model">
      <div className="em-head">
        <h2 id="every-model">Every model</h2>
        <span className="badge badge-good" title="Saved before kickoff and never changed">
          Genuine pre-match
        </span>
      </div>
      {data ? (
        <ModelList f={data} currentId={d.id} />
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

function ModelList({ f, currentId }: { f: FixtureModels; currentId: number }) {
  const phone = useMediaQuery("(max-width: 719px)");
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
  const collapsed = phone && !expanded && models.length > PHONE_ROWS + 1;
  const shown = collapsed ? models.slice(0, PHONE_ROWS) : models;
  const anyBeforePrices = models.some((m) => m.expected_goals_before_prices && m.probabilities.expected_goals);

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
      ) : (
        <p className="muted small">
          Not played yet: ticks, crosses and each model's score appear once the match is settled.
        </p>
      )}
      <SplitKey home={f.home_name} away={f.away_name} />

      <ol className={`em-list${scored ? " is-settled" : ""}`} aria-label="Every model's record, model of record first">
        <li className="em-row em-colhead" aria-hidden="true">
          <span>Model</span>
          <span>Match result</span>
          <span className="em-num">Over 2.5</span>
          <span className="em-num">BTTS</span>
          <span className="em-num">xG</span>
          {scored && <span className="em-num">RPS</span>}
        </li>
        {shown.map((m) => (
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

      {phone && models.length > PHONE_ROWS + 1 && (
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
            * A model + market record keeps its pure model's expected goals; the bookmakers' prices move the score
            probabilities, not these averages.
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
