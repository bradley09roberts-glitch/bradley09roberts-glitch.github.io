// Brief item 2: one saved prediction in full. A hero with both teams, kick-off, competition
// and stage; a scoreboard with the match-result probabilities and expected goals; then the
// goal markets, the most likely scores as a heat map, recent evidence, availability,
// uncertainty, saved editions and the API's explanation. From 1024 px the evidence sits in a
// second column.

import type { CSSProperties, ReactNode } from "react";
import {
  endpoints,
  useApi,
  type Counterpart,
  type JsonValue,
  type MarketUsed,
  type MediaSummary,
  type PredictionDetail,
  type RecentMatch,
} from "../api";
import {
  BasisBadge,
  EditionBadge,
  RecordTypeBadge,
  basisLabel,
  editionLabel,
  recordTypeLabel,
} from "../components/Badge";
import { CompetitionChip } from "../components/CompetitionMark";
import { FreshnessBadge, freshnessFacts } from "../components/FreshnessBadge";
import { MarketGrid, totalsIn } from "../components/MarketGrid";
import { BackLink } from "../components/Nav";
import { ProbBar } from "../components/ProbBar";
import { ScoreGrid } from "../components/ScoreGrid";
import { Signals, fieldName, missingInputs } from "../components/Signals";
import { ApiView, Empty } from "../components/StateViews";
import { FormPips, Sparkline } from "../components/Svg";
import { TeamBadge, teamKeyFromName } from "../components/TeamBadge";
import { TeamLink } from "../components/TeamLink";
import { EUROPEAN_CUPS } from "../competitions";
import {
  formatDate,
  formatDayHeading,
  formatKickoff,
  formatLondon,
  formatNumber,
  formatPct,
  formatShortDay,
  formatTime,
  londonDayKey,
  marketLabel,
  outcomeLabel,
  seasonLabel,
  settlementStatusLabel,
  totalLine,
} from "../format";
import { useDocumentTitle, useMediaQuery } from "../hooks";
import { canGoBack, href } from "../router";
import type { PageProps } from ".";
import { FormAndStats } from "./MatchForm";

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
  // From 1024 px the evidence column holds the three signals too, so the two columns end
  // close together; on narrower screens they follow the explanation, as before.
  const twoColumns = useMediaQuery("(min-width: 1024px)");
  const signals = (
    <Signals
      card={d}
      features={d.features}
      variant="full"
      marketRecord={d.basis === "model_only" && d.counterpart?.basis === "model_and_market" ? d.counterpart : null}
    />
  );

  return (
    <>
      <MatchHero d={d} />

      <div className="match-layout">
        <div className="match-main">
          <div className="card scoreboard">
            <section aria-labelledby="one-x-two">
              <h2 id="one-x-two" className="sb-title">
                Match result
              </h2>
              {x ? (
                <ProbBar probs={x} homeName={d.home_name} awayName={d.away_name} size="large" />
              ) : (
                <p className="muted">No match-result probabilities in this record.</p>
              )}
            </section>
            {p.expected_goals && (
              <section className="sb-xg" aria-labelledby="xg">
                <h2 id="xg" className="sb-title">
                  Expected goals
                </h2>
                <ExpectedGoals
                  home={d.home_name}
                  away={d.away_name}
                  xg={p.expected_goals}
                  beforePrices={d.basis === "model_and_market" && sameXg(p.expected_goals, d.counterpart)}
                />
              </section>
            )}
          </div>

          {(d.explanation || d.notes) && (
            <section className="card notice brief" aria-labelledby="summary">
              <h2 id="summary">In brief</h2>
              {d.explanation && <p className="brief-text">{d.explanation}</p>}
              {d.notes && <p className="muted small note-gap">Note: {d.notes}</p>}
            </section>
          )}

          {d.basis === "model_and_market" && <MarketBlend d={d} />}
          {d.basis === "model_only" && d.counterpart?.basis === "model_and_market" && (
            <MarketVersion d={d} other={d.counterpart} />
          )}

          {!twoColumns && signals}

          {s && <ResultSection d={d} />}

          <div className="match-pair">
            <Section title="Goals" id="goals">
              <MarketGrid probabilities={p} variant="full" />
            </Section>

            <Section title="Most likely scores" id="scores">
              <ScoreGrid scores={p.top_scores ?? []} homeName={d.home_name} awayName={d.away_name} />
            </Section>
          </div>

          <Section title="Recent form" id="recent">
            <p className="muted small">
              Each side's last results before the prediction's data cut-off (
              {formatLondon(d.training_cutoff_utc)}), so nothing that happened later is used.
            </p>
            <div className="recent-grid">
              <RecentForm team={d.home_name} teamKey={d.home_team} media={d.teams?.home.media} matches={d.recent.home} />
              <RecentForm team={d.away_name} teamKey={d.away_team} media={d.teams?.away.media} matches={d.recent.away} />
            </div>
          </Section>

          {d.teams && <FormAndStats t={d.teams} />}
        </div>

        <aside className="match-aside" aria-label="Evidence and record">
          {twoColumns && signals}

          <Section title="Uncertainty" id="uncertainty">
            <Uncertainty d={d} />
          </Section>

          <Section title="Availability and team news" id="availability">
            <Availability d={d} />
          </Section>

          <Section title="Saved editions" id="editions">
            <Editions d={d} />
          </Section>

          <RecordDetails d={d} />
        </aside>
      </div>
    </>
  );
}

function stageOf(d: PredictionDetail): string | null {
  const v = d.features.stage;
  return typeof v === "string" && v.trim() ? v.trim() : null;
}

/** The longest word of a team name, for the hero's font size (styles.css, .mh-name): a long
 * word shrinks the name so it breaks only between words, never inside one. */
function nameFit(name: string): CSSProperties {
  const longest = Math.max(6, ...name.split(/[\s-]+/).map((w) => [...w].length));
  return { ["--word" as string]: longest };
}

function MatchHero({ d }: { d: PredictionDetail }) {
  const s = d.settlement;
  const scored = s !== null && s.home_goals !== null && s.away_goals !== null;
  const stage = stageOf(d);
  const day = londonDayKey(d.kickoff_utc, d.match_date);
  return (
    <header className="match-hero">
      <div className="mh-inner">
        <p className="mh-eyebrow">
          <CompetitionChip
            code={d.competition}
            name={d.competition_name}
            detail={[stage, seasonLabel(d.season)].filter(Boolean).join(" · ")}
          />
        </p>
        <div className="mh-face">
          <h1 className="mh-teams">
            <span className="mh-team mh-home">
              <TeamBadge teamKey={d.home_team} name={d.home_name} media={d.teams?.home.media} size={72} className="mh-badge" />
              <TeamLink teamKey={d.home_team} className="mh-name" style={nameFit(d.home_name)}>
                {d.home_name}
              </TeamLink>
              <span className="mh-side" aria-hidden="true">
                Home
              </span>
            </span>
            <span className="visually-hidden"> v </span>
            <span className="mh-team mh-away">
              <TeamBadge teamKey={d.away_team} name={d.away_name} media={d.teams?.away.media} size={72} className="mh-badge" />
              <TeamLink teamKey={d.away_team} className="mh-name" style={nameFit(d.away_name)}>
                {d.away_name}
              </TeamLink>
              <span className="mh-side" aria-hidden="true">
                Away
              </span>
            </span>
          </h1>
          <div className="mh-centre">
            {scored ? (
              <>
                <span className="mh-score">
                  {s.home_goals}
                  <span className="mh-score-sep">–</span>
                  {s.away_goals}
                </span>
                <span className="mh-centre-sub">Full time</span>
              </>
            ) : d.kickoff_utc ? (
              <>
                <span className="mh-time">{formatTime(d.kickoff_utc)}</span>
                <span className="mh-centre-sub">UK time</span>
              </>
            ) : (
              <>
                <span className="mh-time mh-time-tbc">TBC</span>
                <span className="mh-centre-sub">Time not yet known</span>
              </>
            )}
          </div>
        </div>
        <p className="mh-when">
          {d.kickoff_utc ? (
            <>
              {formatDayHeading(day)} · kick-off {formatTime(d.kickoff_utc)} UK time
            </>
          ) : (
            formatKickoff(d.kickoff_utc, d.match_date)
          )}
        </p>
        <div className="badges mh-badges">
          <BasisBadge basis={d.basis} />
          <RecordTypeBadge type={d.record_type} />
          <EditionBadge edition={d.edition} />
          {s && !scored && <span className="result-tag">{settlementStatusLabel(s.status)}</span>}
        </div>
        <FreshnessBadge freshness={d.data_freshness} createdAt={d.created_at_utc} competition={d.competition} />
      </div>
    </header>
  );
}

/** True when the counterpart (if any) has the same expected goals: a model + market record
 * then still carries its pure model's averages. */
function sameXg(xg: { home: number; away: number }, other: Counterpart | null): boolean {
  const o = other?.probabilities.expected_goals;
  if (!o) return other === null;
  return Math.abs(o.home - xg.home) < 1e-9 && Math.abs(o.away - xg.away) < 1e-9;
}

function ExpectedGoals({
  home,
  away,
  xg,
  beforePrices = false,
}: {
  home: string;
  away: string;
  xg: { home: number; away: number };
  beforePrices?: boolean;
}) {
  const scale = Math.max(3, xg.home, xg.away);
  return (
    <>
      <div className="xg-duel">
        <p className="xg-side xg-home">
          <span className="xg-value">{formatNumber(xg.home, 2)}</span>
          <span className="xg-team">
            <span className="swatch swatch-home" aria-hidden="true" />
            {home}
          </span>
        </p>
        <div className="xg-bars" aria-hidden="true">
          <span className="bar-track xg-track-home">
            <span className="bar-fill seg-home" style={{ width: `${(xg.home / scale) * 100}%` }} />
          </span>
          <span className="bar-track xg-track-away">
            <span className="bar-fill seg-away" style={{ width: `${(xg.away / scale) * 100}%` }} />
          </span>
        </div>
        <p className="xg-side xg-away">
          <span className="xg-value">{formatNumber(xg.away, 2)}</span>
          <span className="xg-team">
            <span className="swatch swatch-away" aria-hidden="true" />
            {away}
          </span>
        </p>
      </div>
      <p className="muted small">
        The average number of goals the model expects each side to score: {formatNumber(xg.home + xg.away, 2)}{" "}
        in total. Model estimates, not shot-based xG.
      </p>
      {beforePrices && (
        <p className="muted small">
          These are the pure model's averages, from before the bookmakers' prices were blended
          in. The blend moves the score probabilities, not these figures.
        </p>
      )}
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
          <>Status: {settlementStatusLabel(s.status)}</>
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

// ---------------------------------------------------------------------------------------------
// Model + market

const BOOK_NAMES: Record<string, string> = { B365: "Bet365", Avg: "the market average", PS: "Pinnacle" };

function bookList(books: string[]): string {
  const names = books.map((b) => BOOK_NAMES[b] ?? b);
  if (names.length <= 1) return names.join("");
  return `${names.slice(0, -1).join(", ")} and ${names.at(-1)}`;
}

/** "Levante win 37%, draw 27%, Ath Bilbao win 36%". */
function resultText(x: { H: number; D: number; A: number }, home: string, away: string): string {
  return `${home} win ${formatPct(x.H)}, draw ${formatPct(x.D)}, ${away} win ${formatPct(x.A)}`;
}

function MarketBlend({ d }: { d: PredictionDetail }) {
  const m = d.market;
  const pure = d.counterpart?.basis === "model_only" ? d.counterpart : null;
  const pureX = pure?.probabilities["1X2"];
  const when = m?.quote_time_utc ? formatLondon(m.quote_time_utc) : null;
  return (
    <section className="card market-blend" aria-labelledby="blend">
      <h2 id="blend">Model + market</h2>
      <p>
        Bookmakers' prices{" "}
        {when ? (
          <>
            available by <strong>{when}</strong> (UK time)
          </>
        ) : (
          "(collection time not recorded)"
        )}{" "}
        were blended in
        {pure && pureX ? (
          <>
            ; the pure model said {resultText(pureX, d.home_name, d.away_name)}.
          </>
        ) : (
          <>. No pure-model record was saved for this match, so there is nothing to set beside it.</>
        )}
      </p>
      {pure && <CompareTable d={d} pure={pure} />}
      {m && <MarketSource m={m} d={d} />}
    </section>
  );
}

type Row = { label: string; blended: number | undefined; pure: number | undefined };

function compareRows(d: PredictionDetail, pure: Counterpart): Row[] {
  const a = d.probabilities;
  const b = pure.probabilities;
  const rows: Row[] = [
    { label: `${d.home_name} win`, blended: a["1X2"]?.H, pure: b["1X2"]?.H },
    { label: "Draw", blended: a["1X2"]?.D, pure: b["1X2"]?.D },
    { label: `${d.away_name} win`, blended: a["1X2"]?.A, pure: b["1X2"]?.A },
  ];
  for (const line of ["1.5", "2.5", "3.5"]) {
    const key = `OU_${line}` as const;
    rows.push({ label: `Over ${line} goals`, blended: a[key]?.O, pure: b[key]?.O });
  }
  rows.push({ label: "Both teams to score", blended: a.BTTS?.Y, pure: b.BTTS?.Y });
  return rows.filter((r) => r.blended !== undefined || r.pure !== undefined);
}

/** Percentage points, signed: "+9", "−4", "0". Taken from the whole percentages the table
 * shows, so the change always matches the two figures beside it (28% v 27% is +1, not 0). */
function points(a: number | undefined, b: number | undefined): string {
  if (a === undefined || b === undefined) return "—";
  const v = Math.round(a * 100) - Math.round(b * 100);
  return v > 0 ? `+${v}` : v < 0 ? `−${Math.abs(v)}` : "0";
}

function CompareTable({ d, pure }: { d: PredictionDetail; pure: Counterpart }) {
  return (
    <div className="compare">
      <table className="compare-table">
        <caption className="visually-hidden">
          This model + market record beside the pure model's record for the same match
        </caption>
        <thead>
          <tr>
            <th scope="col">Market</th>
            <th scope="col">Model + market</th>
            <th scope="col">Pure model</th>
            <th scope="col">
              Change <span className="compare-model">points</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {compareRows(d, pure).map((r) => (
            <tr key={r.label}>
              <th scope="row">{r.label}</th>
              <td>{formatPct(r.blended)}</td>
              <td>{formatPct(r.pure)}</td>
              <td className="muted">{points(r.blended, r.pure)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="muted small">
        <a href={href(`/match/${pure.id}`)}>Open the pure model's record</a> (
        <code>{pure.model_name}</code>, saved {formatLondon(pure.created_at_utc)}).
      </p>
    </div>
  );
}

function MarketSource({ m, d }: { m: MarketUsed; d: PredictionDetail }) {
  const x = m.probabilities["1X2"];
  const ou = m.probabilities["OU_2.5"];
  const sha = m.provenance.raw_sha256;
  const parts: string[] = [];
  if (x) parts.push(resultText(x, d.home_name, d.away_name));
  if (ou) parts.push(`over 2.5 goals ${formatPct(ou.O)}`);
  return (
    <p className="muted small">
      {parts.length > 0 && (
        <>
          Bookmakers' consensus{m.books.length > 0 && <> ({bookList(m.books)}, margins removed)</>}:{" "}
          {parts.join("; ")}.{" "}
        </>
      )}
      Source: {m.provenance.source ?? "not recorded"}
      {m.provenance.retrieved_at && <>, downloaded {formatLondon(m.provenance.retrieved_at)} (UK time)</>}
      {sha && (
        <>
          , file SHA-256{" "}
          <span className="mono" title={sha}>
            {sha.slice(0, 12)}…
          </span>
        </>
      )}
      . The prices themselves are not stored or shown.
    </p>
  );
}

function MarketVersion({ d, other }: { d: PredictionDetail; other: Counterpart }) {
  return (
    <div className="notice-info" role="note">
      <strong>There is also a model + market record for this match.</strong> {other.model_name}{" "}
      blended bookmakers' prices into this model's numbers (saved {formatLondon(other.created_at_utc)}
      ).{" "}
      {other.is_primary && !d.is_primary && "It is the record of record for this match. "}
      <a href={href(`/match/${other.id}`)}>See the two side by side</a>.
    </div>
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
  items.push(
    d.basis === "model_and_market" ? (
      <>
        The probabilities come from past results and bookmakers' prices collected before the
        prediction, and have no error bars of their own.
      </>
    ) : (
      <>The probabilities come from past results only and have no error bars of their own.</>
    ),
  );
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

function RecentForm({
  team,
  teamKey,
  media,
  matches,
}: {
  team: string;
  teamKey: string;
  media?: MediaSummary;
  matches: RecentMatch[];
}) {
  const heading = (
    <h3 className="recent-head">
      <TeamBadge teamKey={teamKey} name={team} media={media} size={28} />
      <TeamLink teamKey={teamKey}>{team}</TeamLink>
    </h3>
  );
  if (matches.length === 0) {
    return (
      <div className="recent">
        {heading}
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
      {heading}
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
                <TeamBadge
                  teamKey={m.opponent_team ?? teamKeyFromName(m.opponent)}
                  name={m.opponent}
                  media={m.opponent_media}
                  size={18}
                />
                <span className="recent-opp-text">
                  <span className="muted">{m.venue === "H" ? "v" : "at"}</span>{" "}
                  <TeamLink teamKey={m.opponent_team}>{m.opponent}</TeamLink>
                  <span className="muted small"> · {m.competition}</span>
                </span>
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
          const label = `${editionLabel(e.edition)} · ${e.model_name} · ${basisLabel(e.basis)}`;
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
                {e.is_primary ? " · record of record" : ""}
                {e.reason ? ` · ${e.reason}` : ""}
              </div>
            </li>
          );
        })}
      </ol>
      <p className="muted small">
        Saved predictions are never changed. A correction or a later edition is a new record that
        says what it replaces. The record of record is the one the headline performance counts.
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
