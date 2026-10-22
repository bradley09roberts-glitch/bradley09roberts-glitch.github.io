// Brief item 1: upcoming matches, grouped by UK day (sticky day headings) and, within a day,
// by competition then kick-off, with every market's probabilities, the data freshness and the
// edition. A competition chip on each card filters to that competition. With no odds there is
// no betting selection, but the probabilities always stay visible.

import { useLayoutEffect, useMemo, useRef, type FocusEvent } from "react";
import { endpoints, useApi, type FixturesResponse, type PredictionCard } from "../api";
import { BasisBadge, EditionBadge, RecordTypeBadge, isConsensus } from "../components/Badge";
import { CompetitionChip, CompetitionMark } from "../components/CompetitionMark";
import { FreshnessBadge } from "../components/FreshnessBadge";
import { MarketGrid } from "../components/MarketGrid";
import { ProbBar } from "../components/ProbBar";
import { ClosingPriceNote, ModelAlone, Signals, hasOdds, pickLabel } from "../components/Signals";
import { ApiView, Empty, StaleNotice } from "../components/StateViews";
import { TeamBadge } from "../components/TeamBadge";
import { TeamLink } from "../components/TeamLink";
import { competitionRank, sortByCompetition } from "../competitions";
import {
  daysBetween,
  formatDayHeading,
  formatLondon,
  formatRelative,
  formatShortDay,
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
      <ClosingPriceNote />

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

type FixtureGroup = { ref: string; competition: string; cards: PredictionCard[] };
type DayGroup = { day: string; fixtures: FixtureGroup[] };
type CompetitionCount = { code: string; name: string; count: number };

/** Fixtures by UK day; within a day, by competition (cups, then the big five, …) then kick-off.
 * Each fixture's records are kept together, the record of record first. */
function group(data: FixturesResponse, competition: string | null): { days: DayGroup[]; competitions: CompetitionCount[] } {
  const comps = sortByCompetition(data.competitions, (c) => c.code);
  const competitions: CompetitionCount[] = [];
  const byDay = new Map<string, FixtureGroup[]>();
  for (const c of comps) {
    const byRef = new Map<string, PredictionCard[]>();
    for (const card of c.fixtures) {
      const list = byRef.get(card.fixture_ref);
      if (list) list.push(card);
      else byRef.set(card.fixture_ref, [card]);
    }
    if (byRef.size > 0) competitions.push({ code: c.code, name: c.name, count: byRef.size });
    if (competition && c.code !== competition) continue;
    // The record of record leads; the others stay in the API's order.
    for (const [ref, cards] of byRef) {
      cards.sort((a, b) => Number(b.is_primary) - Number(a.is_primary));
      const first = cards[0];
      if (!first) continue;
      const day = londonDayKey(first.kickoff_utc, first.match_date);
      const g = { ref, competition: c.code, cards };
      const list = byDay.get(day);
      if (list) list.push(g);
      else byDay.set(day, [g]);
    }
  }
  const kickoff = (g: FixtureGroup) => g.cards[0]?.kickoff_utc ?? "\uffff";
  const days = [...byDay.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([day, fixtures]) => ({
      day,
      fixtures: fixtures.sort(
        (a, b) => competitionRank(a.competition) - competitionRank(b.competition) || kickoff(a).localeCompare(kickoff(b)),
      ),
    }));
  return { days, competitions };
}

function FixtureList({ data, competition }: { data: FixturesResponse; competition: string | null }) {
  const { days, competitions } = useMemo(() => group(data, competition), [data, competition]);
  const all = data.competitions.flatMap((c) => c.fixtures);
  const noOdds = all.length > 0 && all.every((c) => !hasOdds(c));
  const total = competitions.reduce((n, g) => n + g.count, 0);

  // On a phone the competition chips scroll sideways: keep the chosen one in view.
  const chipsRef = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    const row = chipsRef.current;
    const chip = row?.querySelector<HTMLElement>('[aria-pressed="true"]');
    if (!row || !chip || row.scrollWidth <= row.clientWidth) return;
    row.scrollLeft = chip.offsetLeft - (row.clientWidth - chip.offsetWidth) / 2;
  }, [competition]);

  // A chip reached with Tab is scrolled fully into view, focus ring and all (the browser
  // leaves a chip at the edge half cut off).
  const onChipFocus = (e: FocusEvent<HTMLDivElement>) => {
    const row = e.currentTarget;
    const chip = e.target;
    if (chip === row || row.scrollWidth <= row.clientWidth) return;
    const margin = 28;
    const left = chip.offsetLeft - margin;
    const right = chip.offsetLeft + chip.offsetWidth + margin - row.clientWidth;
    if (row.scrollLeft > left) row.scrollLeft = left;
    else if (row.scrollLeft < right) row.scrollLeft = right;
  };

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

      {competitions.length > 1 && (
        <div className="chips chips-scroll" role="group" aria-label="Competition" ref={chipsRef} onFocus={onChipFocus}>
          <button
            type="button"
            className="chip"
            aria-pressed={competition === null}
            onClick={() => setQuery({ competition: null })}
          >
            All <span className="chip-count">{total}</span>
          </button>
          {competitions.map((g) => (
            <button
              key={g.code}
              type="button"
              className="chip"
              aria-pressed={competition === g.code}
              onClick={() => setQuery({ competition: g.code })}
            >
              <CompetitionMark code={g.code} />
              {g.name} <span className="chip-count">{g.count}</span>
            </button>
          ))}
        </div>
      )}

      {days.length === 0 ? (
        <Empty title="No fixtures for this competition in this window">
          <button type="button" onClick={() => setQuery({ competition: null })}>
            Show every competition
          </button>
        </Empty>
      ) : (
        days.map((d) => (
          <section key={d.day} className="day" aria-labelledby={`day-${d.day}`}>
            <h2 id={`day-${d.day}`} className="day-head">
              <span className="day-name">{formatDayHeading(d.day)}</span>
              <span className="day-count">
                {d.fixtures.length} {d.fixtures.length === 1 ? "fixture" : "fixtures"}
              </span>
            </h2>
            <div className="fixture-list">
              {d.fixtures.map((f) => (
                <FixtureCard key={f.ref} cards={f.cards} />
              ))}
            </div>
          </section>
        ))
      )}
    </>
  );
}

/** Which model the card shows, and how many others predicted the match, with a link to the
 * match page's "Every model" section. The fixtures list carries only the record of record and
 * its partner with the other basis; `models_for_fixture` counts every model with a record. */
function AlsoPredicted({ card, others }: { card: PredictionCard; others: PredictionCard[] }) {
  const total = card.models_for_fixture ?? others.length + 1;
  const n = Math.max(0, total - 1);
  if (n === 0) return null;
  const label = card.model_label ?? card.model_name;
  return (
    <p className="above-stretch also">
      <span className="muted">{card.is_primary ? "Model of record: " : "Shown: "}</span>
      <span className="also-model">{label}</span>
      <span className="muted">
        {" "}
        · also predicted by {n} other {n === 1 ? "model" : "models"}
      </span>{" "}
      ·{" "}
      <a className="also-link" href={href(`/match/${card.id}`, { at: "models" })}>
        Compare every model<span className="visually-hidden">: {card.home_name} versus {card.away_name}</span>{" "}
        <span aria-hidden="true">→</span>
      </a>
    </p>
  );
}

/** The result, or that the match has kicked off, under the kick-off time. */
function KickoffStatus({ card }: { card: PredictionCard }) {
  const s = card.settlement;
  if (s && s.home_goals !== null && s.away_goals !== null) {
    return (
      <span className="fx-status">
        <span className="result-tag">
          Full time {s.home_goals}–{s.away_goals}
        </span>
      </span>
    );
  }
  if (s) {
    return (
      <span className="fx-status">
        <span className="result-tag">{settlementStatusLabel(s.status)}</span>
      </span>
    );
  }
  if (card.kickoff_utc && parseUtc(card.kickoff_utc).getTime() <= Date.now()) {
    return (
      <span className="fx-status">
        <span className="live-dot" aria-hidden="true" />
        <span>Kicked off · awaiting result</span>
      </span>
    );
  }
  return null;
}

function FixtureCard({ cards }: { cards: PredictionCard[] }) {
  const [card, ...others] = cards;
  if (!card) return null;
  const x = card.probabilities["1X2"];
  const day = londonDayKey(card.kickoff_utc, card.match_date);
  // Where the match result is the bookmakers' consensus, the pure model's own beside it.
  const consensus = isConsensus(card);
  const pure = consensus ? (others.find((o) => o.basis === "model_only") ?? null) : null;
  return (
    <article className="card fixture">
      <div className="fx-head">
        <CompetitionChip code={card.competition} name={card.competition_name} />
        <span className="fx-tags">
          <BasisBadge basis={card.basis} consensus={consensus} />
        </span>
      </div>
      <div className="fx-body">
        {/* Each name opens its team's page; anywhere else on the card opens the match (the
            "Details" link below is stretched over the card). */}
        <h3 className="fx-teams">
          <span className="fx-team">
            <TeamBadge teamKey={card.home_team} name={card.home_name} media={card.home_media} size={32} />
            <TeamLink teamKey={card.home_team} className="fx-name">
              {card.home_name}
            </TeamLink>
          </span>
          <span className="visually-hidden"> versus </span>
          <span className="fx-team">
            <TeamBadge teamKey={card.away_team} name={card.away_name} media={card.away_media} size={32} />
            <TeamLink teamKey={card.away_team} className="fx-name">
              {card.away_name}
            </TeamLink>
          </span>
        </h3>
        <p className="fx-when">
          <span className="fx-time">{card.kickoff_utc ? formatTime(card.kickoff_utc) : "TBC"}</span>
          <span className="fx-day">{formatShortDay(day)}</span>
          {!card.kickoff_utc && <span className="visually-hidden">Kick-off time to be confirmed</span>}
          <KickoffStatus card={card} />
        </p>
      </div>
      {consensus && <p className="consensus-cap small">Bookmakers' consensus (margins removed)</p>}
      {x ? (
        <ProbBar probs={x} homeName={card.home_name} awayName={card.away_name} pickLabel={pickLabel(card)} />
      ) : (
        <p className="muted small">No match-result probabilities in this record.</p>
      )}
      {pure && <ModelAlone pure={pure} homeName={card.home_name} awayName={card.away_name} />}
      <MarketGrid probabilities={card.probabilities} lines={CARD_LINES} variant="compact" />
      <Signals
        card={card}
        marketRecord={card.basis === "model_only" ? (others.find((o) => o.basis === "model_and_market") ?? null) : null}
      />
      <div className="fixture-foot">
        <span className="fx-foot-tags">
          <EditionBadge edition={card.edition} />
          {card.record_type !== "genuine_prematch" && <RecordTypeBadge type={card.record_type} />}
          <FreshnessBadge freshness={card.data_freshness} createdAt={card.created_at_utc} competition={card.competition} />
        </span>
        <a
          className="more stretched"
          href={href(`/match/${card.id}`)}
          aria-label={`${card.home_name} versus ${card.away_name}: the prediction in full`}
        >
          Details <span className="more-arrow">→</span>
        </a>
        <AlsoPredicted card={card} others={others} />
      </div>
    </article>
  );
}
