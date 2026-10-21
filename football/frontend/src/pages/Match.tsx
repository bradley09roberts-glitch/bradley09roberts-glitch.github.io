// Brief item 2: one saved prediction in full: expected goals, score grid, recent evidence,
// availability, uncertainty, saved editions and the API's explanation.

import type { ReactNode } from "react";
import { endpoints, useApi, type JsonValue, type PredictionDetail, type RecentMatch } from "../api";
import { EditionBadge, RecordTypeBadge, editionLabel, recordTypeLabel } from "../components/Badge";
import { FreshnessBadge, freshnessFacts } from "../components/FreshnessBadge";
import { MarketGrid, totalsIn } from "../components/MarketGrid";
import { BackLink } from "../components/Nav";
import { ProbBar } from "../components/ProbBar";
import { ScoreTopList } from "../components/ScoreTopList";
import { Signals, fieldName, missingInputs } from "../components/Signals";
import { ApiView, Empty } from "../components/StateViews";
import { FormPips, Sparkline } from "../components/Svg";
import { EUROPEAN_CUPS } from "../competitions";
import {
  formatDate,
  formatKickoff,
  formatLondon,
  formatNumber,
  formatPct,
  formatShortDay,
  marketLabel,
  outcomeLabel,
  seasonLabel,
  totalLine,
} from "../format";
import { useDocumentTitle } from "../hooks";
import { canGoBack, href } from "../router";
import type { PageProps } from ".";

export function Match({ params }: PageProps) {
  const id = params.id ?? "";
  const valid = /^\d+$/.test(id);
  const result = useApi(valid ? endpoints.prediction(id) : null);
  useDocumentTitle(result.data ? `${result.data.home_name} v ${result.data.away_name}` : "Match");

  const fallback = result.data?.settlement ? "/past" : "/fixtures";
  const back = (
    <BackLink to={fallback}>{canGoBack() ? "Back" : fallback === "/past" ? "Past matches" : "Fixtures"}</BackLink>
  );

  if (!valid || (result.error?.notFound && !result.data)) {
    return (
      <>
        {back}
        <h1 className="visually-hidden">Match</h1>
        <Empty title="No such prediction">
          <p>
            There's no saved prediction {valid ? `number ${id}` : `called “${id}”`}. It may have
            been mistyped. <a href={href("/fixtures")}>See the fixtures</a>.
          </p>
        </Empty>
      </>
    );
  }

  return (
    <>
      {back}
      {/* The match title is the page heading once loaded; until then (loading or error) the
          page still has one. */}
      {!result.data && <h1 className="visually-hidden">Match</h1>}
      <ApiView result={result} what="this prediction" loadingLines={4}>
        {(d) => <MatchDetail d={d} />}
      </ApiView>
    </>
  );
}

function Section({ title, id, children, className }: { title: string; id: string; children: ReactNode; className?: string }) {
  return (
    <section className={`card${className ? ` ${className}` : ""}`} aria-labelledby={id}>
      <h2 id={id}>{title}</h2>
      {children}
    </section>
  );
}

function MatchDetail({ d }: { d: PredictionDetail }) {
  const p = d.probabilities;
  const x = p["1X2"];
  const s = d.settlement;
  const scored = s && s.home_goals !== null && s.away_goals !== null;

  return (
    <>
      <header className="card match-head">
        <p className="eyebrow">
          {d.competition_name} · {seasonLabel(d.season)}
        </p>
        <h1 className="match-title">
          <span>{d.home_name}</span> <span className="vs">v</span> <span>{d.away_name}</span>
        </h1>
        <p className="match-when">
          {formatKickoff(d.kickoff_utc, d.match_date)}
          {d.kickoff_utc && " (UK time)"}
        </p>
        <div className="badges">
          <RecordTypeBadge type={d.record_type} />
          <EditionBadge edition={d.edition} />
          {scored && (
            <span className="result-tag">
              Full time {s.home_goals}–{s.away_goals}
            </span>
          )}
          {s && !scored && <span className="result-tag">{s.status}</span>}
        </div>
        <FreshnessBadge
          freshness={d.data_freshness}
          createdAt={d.created_at_utc}
          competition={d.competition}
        />
      </header>

      {(d.explanation || d.notes) && (
        <section className="card notice" aria-labelledby="summary">
          <h2 id="summary">In brief</h2>
          {d.explanation && <p>{d.explanation}</p>}
          {d.notes && <p className="muted small note-gap">Note: {d.notes}</p>}
        </section>
      )}

      <Signals card={d} features={d.features} variant="full" />

      {s && <ResultSection d={d} />}

      {p.expected_goals && (
        <Section title="Expected goals" id="xg">
          <ExpectedGoals home={d.home_name} away={d.away_name} xg={p.expected_goals} />
        </Section>
      )}

      <Section title="Match result" id="one-x-two">
        {x ? (
          <ProbBar probs={x} homeName={d.home_name} awayName={d.away_name} size="large" />
        ) : (
          <p className="muted">No match-result probabilities in this record.</p>
        )}
      </Section>

      <Section title="Goals" id="goals">
        <MarketGrid probabilities={p} variant="full" />
      </Section>

      <Section title="Most likely scores" id="scores">
        <ScoreTopList scores={p.top_scores ?? []} homeName={d.home_name} awayName={d.away_name} />
      </Section>

      <Section title="Uncertainty" id="uncertainty">
        <Uncertainty d={d} />
      </Section>

      <Section title="Availability and team news" id="availability">
        <Availability d={d} />
      </Section>

      <Section title="Recent form" id="recent">
        <p className="muted small">
          Each side's last results before the prediction's data cut-off (
          {formatLondon(d.training_cutoff_utc)}), so nothing that happened later is used.
        </p>
        <div className="recent-grid">
          <RecentForm team={d.home_name} matches={d.recent.home} />
          <RecentForm team={d.away_name} matches={d.recent.away} />
        </div>
      </Section>

      <Section title="Saved editions" id="editions">
        <Editions d={d} />
      </Section>

      <RecordDetails d={d} />
    </>
  );
}

function ExpectedGoals({ home, away, xg }: { home: string; away: string; xg: { home: number; away: number } }) {
  const scale = Math.max(3, xg.home, xg.away);
  const rows = [
    { name: home, v: xg.home, cls: "home" },
    { name: away, v: xg.away, cls: "away" },
  ];
  return (
    <>
      <ul className="xg">
        {rows.map((r) => (
          <li key={r.cls}>
            <span className="xg-team">
              <span className={`swatch swatch-${r.cls}`} aria-hidden="true" />
              {r.name}
            </span>
            <span className="xg-value">{formatNumber(r.v, 2)}</span>
            <span className="bar-track" aria-hidden="true">
              <span className={`bar-fill seg-${r.cls}`} style={{ width: `${(r.v / scale) * 100}%` }} />
            </span>
          </li>
        ))}
      </ul>
      <p className="muted small">
        The average number of goals the model expects each side to score: {formatNumber(xg.home + xg.away, 2)}{" "}
        in total. Model estimates, not shot-based xG.
      </p>
    </>
  );
}

function ResultSection({ d }: { d: PredictionDetail }) {
  const s = d.settlement;
  if (!s) return null;
  const order = (m: string) => (m === "1X2" ? 0 : m === "BTTS" ? 100 : m.startsWith("OU_") ? 10 + totalLine(m) : 200);
  const markets = Object.entries(s.outcomes).sort(([a], [b]) => order(a) - order(b));
  const scored = s.home_goals !== null && s.away_goals !== null;
  return (
    <Section title="Result" id="result">
      <p>
        {scored ? (
          <>
            Final score{" "}
            <strong>
              {d.home_name} {s.home_goals}–{s.away_goals} {d.away_name}
            </strong>
          </>
        ) : (
          <>Status: {s.status}</>
        )}
        <span className="muted small"> · settled {formatLondon(s.settled_at_utc)}</span>
      </p>
      {markets.length > 0 && (
        <ul className="outcomes">
          {markets.map(([m, o]) => (
            <li key={m} className={o.correct ? "is-correct" : "is-incorrect"}>
              <span className="outcome-market">{marketLabel(m)}</span>
              <span className="outcome-pick">
                Pick: {outcomeLabel(m, o.pick)} ({formatPct(d.selections[m]?.p)})
              </span>
              <span className="outcome-actual muted small">
                Actual: {outcomeLabel(m, o.actual)}, which the model gave {formatPct(o.p_actual)}
              </span>
              <span className="outcome-mark">
                <span aria-hidden="true">{o.correct ? "✓" : "✗"}</span> {o.correct ? "Correct" : "Incorrect"}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Section>
  );
}

function num(v: JsonValue | undefined): number | null {
  return typeof v === "number" && Number.isFinite(v) ? v : null;
}

function str(v: JsonValue | undefined): string | null {
  return typeof v === "string" && v ? v : null;
}

function Uncertainty({ d }: { d: PredictionDetail }) {
  const p = d.probabilities;
  const items: ReactNode[] = [];
  const x = p["1X2"];
  if (x) {
    const ranked = (
      [
        ["H", `${d.home_name} win`],
        ["D", "a draw"],
        ["A", `${d.away_name} win`],
      ] as const
    )
      .map(([k, label]) => ({ label, p: x[k] }))
      .sort((a, b) => b.p - a.p);
    const [first, second] = ranked;
    if (first && second) {
      const gap = first.p - second.p;
      items.push(
        <>
          The most likely result, {first.label}, has {formatPct(first.p)}; next is {second.label} at{" "}
          {formatPct(second.p)}.{" "}
          {gap < 0.1
            ? "That is a close call."
            : `The model still expects the most likely result not to happen ${formatPct(1 - first.p)} of the time.`}
        </>,
      );
    }
  }
  const top = p.top_scores?.[0];
  if (top) {
    items.push(
      <>
        Even the single most likely score, {top.score.replace("-", "–")}, has only {formatPct(top.p)}: exact
        scores are always uncertain.
      </>,
    );
  }
  const ou = totalsIn(p).find((t) => t.line === 2.5);
  if (ou && Math.abs(ou.probs.O - 0.5) < 0.08) {
    items.push(<>Over or under 2.5 goals is close to a coin toss here ({formatPct(ou.probs.O)} over).</>);
  }
  const homeN = num(d.features.home_matches_in_window);
  const awayN = num(d.features.away_matches_in_window);
  if (homeN !== null && awayN !== null) {
    const thin = Math.min(homeN, awayN) < 20;
    items.push(
      <>
        Ratings rest on {homeN} recent matches for {d.home_name} and {awayN} for {d.away_name}
        {thin ? ", a small sample, so these numbers are less certain than usual." : "."}
      </>,
    );
  }
  const homeLeague = str(d.features.home_league_for_rating);
  const awayLeague = str(d.features.away_league_for_rating);
  if (EUROPEAN_CUPS.has(d.competition) && (homeLeague || awayLeague)) {
    items.push(
      <>
        Clubs from different leagues are compared through results between leagues (home side rated
        in {homeLeague ?? "unknown"}, away side in {awayLeague ?? "unknown"}), which adds uncertainty.
      </>,
    );
  }
  items.push(<>The probabilities come from past results only and have no error bars of their own.</>);
  return (
    <>
      <ul className="facts">
        {items.map((it, i) => (
          <li key={i}>{it}</li>
        ))}
      </ul>
      <p className="small">
        How often probabilities like these came true in past seasons is in the{" "}
        <a href={href("/lab")}>backtest lab</a>.
      </p>
    </>
  );
}

function Availability({ d }: { d: PredictionDetail }) {
  const missing = missingInputs(d);
  const lineupMissing = d.missing_fields.includes("lineups");
  const lineupEdition = d.editions.find((e) => e.edition === "lineup");
  return (
    <>
      {d.edition === "early" || lineupMissing ? (
        <p>
          <strong>Lineups and injuries are not used in the early edition.</strong> These
          probabilities don't reflect team news, rotation or absences.
        </p>
      ) : (
        <p>This edition was made after the lineups were announced.</p>
      )}
      {missing.length > 0 && (
        <p className="chip-row">
          <span className="muted small">Missing from this record:</span>{" "}
          {missing.map((f) => (
            <span key={f} className="badge badge-neutral">
              {fieldName(f)}
            </span>
          ))}
        </p>
      )}
      {lineupEdition && lineupEdition.record_type === "unavailable" && (
        <p className="muted small">
          No lineup edition was produced{lineupEdition.reason ? `: ${lineupEdition.reason}` : "."}
        </p>
      )}
    </>
  );
}

function teamGoals(m: RecentMatch): { gf: number; ga: number } | null {
  const parts = m.score.split("-").map((n) => Number.parseInt(n, 10));
  const [hg, ag] = parts;
  if (hg === undefined || ag === undefined || Number.isNaN(hg) || Number.isNaN(ag)) return null;
  return m.venue === "H" ? { gf: hg, ga: ag } : { gf: ag, ga: hg };
}

function RecentForm({ team, matches }: { team: string; matches: RecentMatch[] }) {
  if (matches.length === 0) {
    return (
      <div className="recent">
        <h3>{team}</h3>
        <p className="muted small">No earlier results in the data.</p>
      </div>
    );
  }
  const oldestFirst = [...matches].reverse();
  const gd = oldestFirst
    .map((m) => ({ m, g: teamGoals(m) }))
    .filter((x): x is { m: RecentMatch; g: { gf: number; ga: number } } => x.g !== null)
    .map(({ m, g }) => ({
      value: g.gf - g.ga,
      label: `${formatShortDay(m.date)} v ${m.opponent}: ${g.gf}–${g.ga}`,
    }));
  const signed = (n: number) => (n > 0 ? `+${n}` : String(n));
  return (
    <div className="recent">
      <h3>{team}</h3>
      <div className="recent-summary">
        <FormPips results={oldestFirst.map((m) => m.result)} />
        {gd.length > 1 && (
          <Sparkline
            points={gd}
            baseline={0}
            label={`Goal difference in each match, oldest first: ${gd.map((g) => signed(g.value)).join(", ")}`}
          />
        )}
      </div>
      <p className="muted small">Oldest on the left. Line: goal difference per match (zero marked).</p>
      <ol className="recent-list">
        {matches.map((m, i) => {
          const g = teamGoals(m);
          return (
            <li key={`${m.date}-${i}`}>
              <span className="recent-date">{formatShortDay(m.date)}</span>
              <span className="recent-opp">
                <span className="muted">{m.venue === "H" ? "v" : "at"}</span> {m.opponent}
                <span className="muted small"> · {m.competition}</span>
              </span>
              <span className="recent-score">{g ? `${g.gf}–${g.ga}` : m.score}</span>
              <span className={`pip pip-${m.result}`} aria-label={m.result === "W" ? "Win" : m.result === "D" ? "Draw" : "Loss"}>
                {m.result}
              </span>
            </li>
          );
        })}
      </ol>
      <p className="muted small">Scores show {team}'s goals first.</p>
    </div>
  );
}

function Editions({ d }: { d: PredictionDetail }) {
  if (d.editions.length === 0) return <p className="muted">No editions recorded.</p>;
  return (
    <>
      <ol className="editions">
        {d.editions.map((e) => {
          const current = e.id === d.id;
          const label = `${editionLabel(e.edition)} · ${e.model_name}`;
          return (
            <li key={e.id} className={current ? "is-current" : undefined}>
              <div className="edition-line">
                {current ? (
                  <strong>
                    {label} <span className="muted">(this page)</span>
                  </strong>
                ) : (
                  <a href={href(`/match/${e.id}`)}>{label}</a>
                )}
              </div>
              <div className="muted small">
                Saved {formatLondon(e.created_at_utc)} · {recordTypeLabel(e.record_type)}
                {e.reason ? ` · ${e.reason}` : ""}
              </div>
            </li>
          );
        })}
      </ol>
      <p className="muted small">
        Saved predictions are never changed. A correction or a later edition is a new record that
        says what it replaces.
      </p>
    </>
  );
}

function RecordDetails({ d }: { d: PredictionDetail }) {
  const facts = freshnessFacts(d.data_freshness, d.competition);
  const { model_params: modelParams, ...otherFeatures } = d.features;
  return (
    <details className="card details">
      <summary>Record details</summary>
      <dl className="status">
        <dt>Prediction</dt>
        <dd>#{d.id}</dd>
        <dt>Model</dt>
        <dd>{d.model_name}</dd>
        <dt>Version</dt>
        <dd className="mono">{d.model_version}</dd>
        <dt>Saved</dt>
        <dd>{formatLondon(d.created_at_utc)}</dd>
        <dt>Data cut-off</dt>
        <dd>{formatLondon(d.training_cutoff_utc)}</dd>
        {facts.map((f) => (
          <FactRow key={f.label} label={f.label} value={formatDate(f.date)} />
        ))}
        <dt>Fixture</dt>
        <dd className="mono">{d.fixture_ref}</dd>
      </dl>
      {Object.keys(otherFeatures).length > 0 && (
        <>
          <h3>Inputs saved with the prediction</h3>
          <pre className="json">{JSON.stringify(otherFeatures, null, 2)}</pre>
        </>
      )}
      {modelParams !== undefined && (
        <>
          <h3>Model parameters</h3>
          <pre className="json">{JSON.stringify(modelParams, null, 2)}</pre>
        </>
      )}
      <p className="small">
        Sources and methods: <a href={href("/data")}>Data and methodology</a>.
      </p>
    </details>
  );
}

function FactRow({ label, value }: { label: string; value: string }) {
  return (
    <>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </>
  );
}
