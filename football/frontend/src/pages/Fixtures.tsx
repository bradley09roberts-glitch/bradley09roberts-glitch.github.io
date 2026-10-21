// Brief item 1: upcoming matches by league, with every market's probabilities, the data
// freshness and the edition. With no odds there is no betting selection, but the
// probabilities always stay visible.

import { useMemo } from "react";
import { endpoints, useApi, type FixturesResponse, type PredictionCard } from "../api";
import { BasisBadge, EditionBadge, RecordTypeBadge, basisLabel } from "../components/Badge";
import { FreshnessBadge } from "../components/FreshnessBadge";
import { MarketGrid } from "../components/MarketGrid";
import { ProbBar } from "../components/ProbBar";
import { Signals, hasOdds } from "../components/Signals";
import { ApiView, Empty, StaleNotice } from "../components/StateViews";
import { sortByCompetition } from "../competitions";
import {
  daysBetween,
  formatDayHeading,
  formatLondon,
  formatRelative,
  formatTime,
  londonDayKey,
  parseUtc,
  settlementStatusLabel,
} from "../format";
import { useDocumentTitle } from "../hooks";
import { href, setQuery } from "../router";
import type { PageProps } from ".";

const DAY_OPTIONS = [3, 8, 14] as const;
const DEFAULT_DAYS = 8;
/** Over/under lines on the fixture cards; the match page shows every line. */
const CARD_LINES = [1.5, 2.5, 3.5];

function parseDays(raw: string | null): number {
  const n = raw === null ? NaN : Number(raw);
  return Number.isInteger(n) && n >= 1 && n <= 30 ? n : DEFAULT_DAYS;
}

export function Fixtures({ query }: PageProps) {
  useDocumentTitle("Fixtures");
  const days = parseDays(query.get("days"));
  const competition = query.get("competition");
  const result = useApi(endpoints.fixtures(days));

  return (
    <>
      <header className="page-head">
        <h1 className="page-title">Fixtures</h1>
        <p className="lede">
          Predictions saved before kickoff for the next {days} days: match result, goal totals and
          both teams to score. Times are UK time.
        </p>
      </header>

      <RefreshNotice />

      <div className="filters" role="group" aria-label="Fixture window">
        <span className="filter-label">Show</span>
        <div className="segmented">
          {DAY_OPTIONS.map((d) => (
            <button
              key={d}
              type="button"
              aria-pressed={d === days}
              onClick={() => setQuery({ days: d === DEFAULT_DAYS ? null : d })}
            >
              {d} days
            </button>
          ))}
        </div>
      </div>

      <ApiView
        result={result}
        what="fixtures"
        isEmpty={(d) => d.competitions.every((c) => c.fixtures.length === 0)}
        empty={
          <Empty title={`No predictions for the next ${days} days`}>
            <p>
              Predictions appear here once they have been saved for upcoming fixtures. Nothing is
              shown until a real prediction exists.
            </p>
            {days < 14 && (
              <button type="button" onClick={() => setQuery({ days: 14 })}>
                Look 14 days ahead
              </button>
            )}
          </Empty>
        }
      >
        {(data) => <FixtureList data={data} competition={competition} />}
      </ApiView>
    </>
  );
}

/** Warns when the latest data refresh failed or is old. Silent if the status can't be read. */
function RefreshNotice() {
  const health = useApi(endpoints.health(), { freshMs: 60_000 });
  const refresh = health.data?.data.last_refresh;
  if (!refresh?.started_at) return null;
  const failed = refresh.status !== "ok" && refresh.status !== "running";
  const age = daysBetween(parseUtc(refresh.started_at), new Date());
  if (!failed && age < 3) return null;
  return (
    <StaleNotice>
      {failed
        ? `The latest data refresh (${formatLondon(refresh.started_at)}) ended with status "${refresh.status}".`
        : `Match data was last refreshed ${formatRelative(refresh.started_at)}.`}{" "}
      Each prediction shows the newest result it used.
    </StaleNotice>
  );
}

type FixtureGroup = { ref: string; cards: PredictionCard[] };
type DayGroup = { day: string; fixtures: FixtureGroup[] };
type CompetitionGroup = { code: string; name: string; days: DayGroup[]; count: number };

function group(data: FixturesResponse): CompetitionGroup[] {
  const comps = sortByCompetition(data.competitions, (c) => c.code);
  return comps
    .map((c) => {
      const byRef = new Map<string, PredictionCard[]>();
      for (const card of c.fixtures) {
        const list = byRef.get(card.fixture_ref);
        if (list) list.push(card);
        else byRef.set(card.fixture_ref, [card]);
      }
      // The record of record leads; the others stay in the API's order.
      for (const list of byRef.values()) list.sort((a, b) => Number(b.is_primary) - Number(a.is_primary));
      const byDay = new Map<string, FixtureGroup[]>();
      for (const [ref, cards] of byRef) {
        const first = cards[0];
        if (!first) continue;
        const day = londonDayKey(first.kickoff_utc, first.match_date);
        const list = byDay.get(day);
        const g = { ref, cards };
        if (list) list.push(g);
        else byDay.set(day, [g]);
      }
      const kickoff = (g: FixtureGroup) => g.cards[0]?.kickoff_utc ?? "\uffff";
      const days = [...byDay.entries()]
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([day, fixtures]) => ({
          day,
          fixtures: fixtures.sort((a, b) => kickoff(a).localeCompare(kickoff(b))),
        }));
      return { code: c.code, name: c.name, days, count: byRef.size };
    })
    .filter((c) => c.count > 0);
}

function FixtureList({ data, competition }: { data: FixturesResponse; competition: string | null }) {
  const groups = useMemo(() => group(data), [data]);
  const shown = competition ? groups.filter((g) => g.code === competition) : groups;
  const all = data.competitions.flatMap((c) => c.fixtures);
  const noOdds = all.length > 0 && all.every((c) => !hasOdds(c));
  const total = groups.reduce((n, g) => n + g.count, 0);

  return (
    <>
      <p className="muted small updated">
        {total} {total === 1 ? "fixture" : "fixtures"} · checked {formatLondon(data.generated_at_utc)}
      </p>

      {noOdds && (
        <div className="notice-info" role="note">
          <strong>No odds available — no betting selection.</strong> There are no bookmaker prices
          for these fixtures yet, so no bet can qualify. The model's probabilities are shown in
          full.
        </div>
      )}

      {groups.length > 1 && (
        <div className="chips" role="group" aria-label="Competition">
          <button
            type="button"
            className="chip"
            aria-pressed={competition === null}
            onClick={() => setQuery({ competition: null })}
          >
            All <span className="chip-count">{total}</span>
          </button>
          {groups.map((g) => (
            <button
              key={g.code}
              type="button"
              className="chip"
              aria-pressed={competition === g.code}
              onClick={() => setQuery({ competition: g.code })}
            >
              {g.name} <span className="chip-count">{g.count}</span>
            </button>
          ))}
        </div>
      )}

      {shown.length === 0 ? (
        <Empty title="No fixtures for this competition in this window">
          <button type="button" onClick={() => setQuery({ competition: null })}>
            Show every competition
          </button>
        </Empty>
      ) : (
        shown.map((g) => (
          <section key={g.code} className="competition" aria-labelledby={`comp-${g.code}`}>
            <h2 id={`comp-${g.code}`} className="competition-title">
              {g.name}
              <span className="muted"> · {g.count}</span>
            </h2>
            {g.days.map((d) => (
              <div key={d.day} className="day">
                <h3 className="day-title">{formatDayHeading(d.day)}</h3>
                <div className="fixture-list">
                  {d.fixtures.map((f) => (
                    <FixtureCard key={f.ref} cards={f.cards} />
                  ))}
                </div>
              </div>
            ))}
          </section>
        ))
      )}
    </>
  );
}

function KickoffStatus({ card }: { card: PredictionCard }) {
  const s = card.settlement;
  if (s && s.home_goals !== null && s.away_goals !== null) {
    return (
      <span className="result-tag">
        Full time {s.home_goals}–{s.away_goals}
      </span>
    );
  }
  if (s) return <span className="result-tag">{settlementStatusLabel(s.status)}</span>;
  if (card.kickoff_utc && parseUtc(card.kickoff_utc).getTime() <= Date.now()) {
    return <span className="muted small">Kicked off · awaiting result</span>;
  }
  return null;
}

function FixtureCard({ cards }: { cards: PredictionCard[] }) {
  const [card, ...others] = cards;
  if (!card) return null;
  const x = card.probabilities["1X2"];
  return (
    <article className="card fixture">
      <div className="fixture-meta">
        <span className="kickoff">{card.kickoff_utc ? formatTime(card.kickoff_utc) : "Time TBC"}</span>
        <BasisBadge basis={card.basis} />
        <EditionBadge edition={card.edition} />
        {card.record_type !== "genuine_prematch" && <RecordTypeBadge type={card.record_type} />}
        <KickoffStatus card={card} />
      </div>
      <h4 className="fixture-teams">
        <a className="stretched" href={href(`/match/${card.id}`)}>
          <span>{card.home_name}</span>
          <span className="vs" aria-hidden="true">
            v
          </span>
          <span className="visually-hidden"> versus </span>
          <span>{card.away_name}</span>
        </a>
      </h4>
      {x ? (
        <ProbBar probs={x} homeName={card.home_name} awayName={card.away_name} />
      ) : (
        <p className="muted small">No match-result probabilities in this record.</p>
      )}
      <MarketGrid probabilities={card.probabilities} lines={CARD_LINES} variant="compact" />
      <Signals card={card} />
      <div className="fixture-foot">
        <FreshnessBadge
          freshness={card.data_freshness}
          createdAt={card.created_at_utc}
          competition={card.competition}
        />
        <span className="more" aria-hidden="true">
          Details →
        </span>
      </div>
      {others.length > 0 && (
        <p className="small above-stretch">
          Also predicted by{" "}
          {others.map((o, i) => (
            <span key={o.id}>
              {i > 0 && ", "}
              <a href={href(`/match/${o.id}`)}>{o.model_name}</a>
              <span className="muted"> ({basisLabel(o.basis).toLowerCase()})</span>
            </span>
          ))}
          <span className="muted"> (shown: {card.model_name})</span>
        </p>
      )}
    </article>
  );
}
