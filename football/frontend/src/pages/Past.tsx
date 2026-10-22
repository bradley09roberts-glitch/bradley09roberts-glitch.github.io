// Brief item 3: settled predictions, newest first, with the pick, its probability, the score
// and whether each market was right. Genuine pre-match and retrospective records are two
// separate lists, never mixed in one table (the API returns one record type per request).
//
// By default each match appears once, with the record /api/performance counts for it (the
// first edition of its record of record), so this page and Performance agree. "All models"
// lists every model's settled record of each match, grouped by match; the tally still counts
// only the records of record. Each row opens the saved record and its evidence on the match
// page.

import { useMemo, type ChangeEvent } from "react";
import {
  endpoints,
  useApi,
  type PastCard,
  type PastResponse,
  type RecordType,
} from "../api";
import { BasisBadge, EditionBadge, RecordTypeBadge, isConsensus } from "../components/Badge";
import { CompetitionChip } from "../components/CompetitionMark";
import { MatchLink, useMatchPageCheck } from "../components/MatchLink";
import { ApiView, Empty } from "../components/StateViews";
import { TeamBadge } from "../components/TeamBadge";
import { TeamLink } from "../components/TeamLink";
import { sortByCompetition } from "../competitions";
import {
  formatDayHeading,
  formatDayHeadingWithYear,
  formatLondon,
  formatNumber,
  formatPct,
  formatTime,
  londonDayKey,
  outcomeLabel,
  parseUtc,
  settlementStatusLabel,
} from "../format";
import { useDocumentTitle } from "../hooks";
import { href, setQuery } from "../router";
import type { PageProps } from ".";
import "./Past.css";

/** Matches requested per list, one card each (the API allows up to 2,000). */
const LIMIT = 500;
/** "All models": the newest matches, every model's card for each (footy/snapshot.py,
 * FRONTEND_PAST_ALL_MODELS holds exactly this request). */
const ALL_MODELS_LIMIT = 60;
/** Matches drawn at a time; "Show more" adds another page. */
const PAGE = 40;
const PAGE_ALL = 8;
const LINES = [0.5, 1.5, 2.5, 3.5, 4.5] as const;
const DEFAULT_LINE = 2.5;

type Kind = "genuine" | "retrospective";
type Mode = "record" | "all";

const KINDS: Record<Kind, { recordType: RecordType; label: string }> = {
  genuine: { recordType: "genuine_prematch", label: "Genuine pre-match" },
  retrospective: { recordType: "retrospective_backtest", label: "Retrospective" },
};

function parseKind(raw: string | null): Kind {
  return raw === "retrospective" ? "retrospective" : "genuine";
}

function parseMode(raw: string | null): Mode {
  return raw === "all" ? "all" : "record";
}

function parseLine(raw: string | null): number {
  const n = raw === null ? NaN : Number(raw);
  return (LINES as readonly number[]).includes(n) ? n : DEFAULT_LINE;
}

function parseShown(raw: string | null, page: number, limit: number): number {
  const n = raw === null ? NaN : Number(raw);
  return Number.isInteger(n) && n > page ? Math.min(n, limit) : page;
}

export function Past({ query }: PageProps) {
  useDocumentTitle("Past matches");
  const kind = parseKind(query.get("type"));
  const mode = parseMode(query.get("models"));
  const recordType = KINDS[kind].recordType;
  const competition = query.get("competition") || null;
  const line = parseLine(query.get("line"));
  const page = mode === "all" ? PAGE_ALL : PAGE;
  const limit = mode === "all" ? ALL_MODELS_LIMIT : LIMIT;
  const shown = parseShown(query.get("shown"), page, limit);

  // The one-card-per-match list for every competition gives the competition choices. With
  // no competition chosen it is also the list shown, so there is one request, not two.
  const all = useApi(endpoints.past({ record_type: recordType, limit: LIMIT }));
  const filtered = useApi(
    mode === "record" && competition ? endpoints.past({ record_type: recordType, competition, limit: LIMIT }) : null,
  );
  const everyModel = useApi(
    mode === "all"
      ? endpoints.past({ record_type: recordType, models: "all", limit: ALL_MODELS_LIMIT, competition: competition ?? undefined })
      : null,
  );
  const result = mode === "all" ? everyModel : competition ? filtered : all;
  const options = useMemo(
    () => competitionOptions(all.data, competition, result.data),
    [all.data, competition, result.data],
  );
  const optionsTruncated = all.data?.truncated ?? (all.data?.matches.length ?? 0) >= LIMIT;
  const competitionName = options.find((o) => o.code === competition)?.name ?? competition;
  const filtersChanged = kind !== "genuine" || mode !== "record" || competition !== null || line !== DEFAULT_LINE;

  const onCompetition = (e: ChangeEvent<HTMLSelectElement>) =>
    setQuery({ competition: e.target.value || null, shown: null });
  const onLine = (e: ChangeEvent<HTMLSelectElement>) =>
    setQuery({ line: Number(e.target.value) === DEFAULT_LINE ? null : e.target.value });

  return (
    <>
      <header className="page-head">
        <h1 className="page-title">Past matches</h1>
        <p className="lede">
          Settled predictions, newest first: the pick in each market, its probability, the score
          and whether it was right. Each match is listed once, with its record of record, unless
          you choose every model. Open a match to see the evidence saved with it.
        </p>
      </header>

      <div className="filters past-switches">
        <div className="past-switch" role="group" aria-label="Record type">
          <span className="filter-label">Record</span>
          <div className="segmented">
            {(Object.keys(KINDS) as Kind[]).map((k) => (
              <button
                key={k}
                type="button"
                aria-pressed={k === kind}
                onClick={() => setQuery({ type: k === "genuine" ? null : k, shown: null })}
              >
                {KINDS[k].label}
              </button>
            ))}
          </div>
        </div>
        <div className="past-switch" role="group" aria-label="Models">
          <span className="filter-label">Models</span>
          <div className="segmented">
            <button
              type="button"
              aria-pressed={mode === "record"}
              onClick={() => setQuery({ models: null, shown: null })}
            >
              Record of record
            </button>
            <button
              type="button"
              aria-pressed={mode === "all"}
              onClick={() => setQuery({ models: "all", shown: null })}
            >
              All models
            </button>
          </div>
        </div>
      </div>

      <KindNote kind={kind} mode={mode} />

      <div className="filters filter-fields">
        <label className="field">
          <span className="filter-label">Competition</span>
          <select value={competition ?? ""} onChange={onCompetition}>
            <option value="">All competitions</option>
            {options.map((o) => (
              <option key={o.code} value={o.code}>
                {o.name}
                {o.count !== null && !optionsTruncated && mode === "record" ? ` (${o.count})` : ""}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="filter-label">Goal line</span>
          <select value={String(line)} onChange={onLine}>
            {LINES.map((l) => (
              <option key={l} value={String(l)}>
                Over/under {l}
              </option>
            ))}
          </select>
        </label>
        {filtersChanged && (
          <button
            type="button"
            className="reset"
            onClick={() => setQuery({ type: null, models: null, competition: null, line: null, shown: null })}
          >
            Reset filters
          </button>
        )}
      </div>

      <ApiView
        result={result}
        what="past matches"
        loadingLines={4}
        isEmpty={(d) => d.matches.length === 0}
        empty={<NoRecords kind={kind} competition={competition} competitionName={competitionName} />}
      >
        {(data) => (
          <PastList
            data={data}
            kind={kind}
            mode={mode}
            line={line}
            shown={shown}
            page={page}
            limit={limit}
            showCompetition={competition === null}
          />
        )}
      </ApiView>
    </>
  );
}

function KindNote({ kind, mode }: { kind: Kind; mode: Mode }) {
  if (kind === "genuine") {
    return (
      <div className="notice-info" role="note">
        <strong>Genuine pre-match record.</strong> Every prediction here was saved before kickoff
        and never changed, then scored once the result was in.{" "}
        {mode === "record"
          ? "Each match appears once, with the record Performance counts: the first edition of its record of record."
          : "Every model's record of each match is listed, the record of record first; the tally still counts the records of record only."}{" "}
        Totals are on <a href={href("/performance")}>Performance</a>.
      </div>
    );
  }
  return (
    <div className="notice-stale notice-top" role="note">
      <span className="notice-icon" aria-hidden="true">
        !
      </span>
      <div className="notice-text">
        <strong>Retrospective, not a pre-match record.</strong> These were made after the event by
        replaying the model over matches already played, using only data from before each one.
        They show how the method would have done; they were not published in advance and are
        never counted in the pre-match record.
      </div>
    </div>
  );
}

function NoRecords({
  kind,
  competition,
  competitionName,
}: {
  kind: Kind;
  competition: string | null;
  competitionName: string | null;
}) {
  if (competition) {
    return (
      <Empty title={`No settled ${KINDS[kind].label.toLowerCase()} records for ${competitionName ?? competition}`}>
        <p>Nothing of this record type has been settled for this competition yet.</p>
        <button type="button" onClick={() => setQuery({ competition: null, shown: null })}>
          Show every competition
        </button>
      </Empty>
    );
  }
  if (kind === "genuine") {
    return (
      <Empty
        title={
          <>
            No settled <span className="nowrap">pre-match</span> predictions yet
          </>
        }
      >
        <p>
          The genuine record is made only of predictions saved before kickoff. A match appears here
          once its result is in and the prediction has been settled. Upcoming predictions are on
          the <a href={href("/fixtures")}>Fixtures</a> page.
        </p>
        <button type="button" onClick={() => setQuery({ type: "retrospective", shown: null })}>
          See retrospective records
        </button>
      </Empty>
    );
  }
  return (
    <Empty title="No retrospective records saved">
      <p>
        Retrospective records are made by replaying the model over matches already played. None
        has been saved as a match record yet. Season-by-season test results are in the{" "}
        <a href={href("/lab")}>backtest lab</a>.
      </p>
    </Empty>
  );
}

// ---------------------------------------------------------------------------------------------
// Competition choices

type CompetitionOption = { code: string; name: string; count: number | null };

function competitionOptions(
  all: PastResponse | undefined,
  selected: string | null,
  shown: PastResponse | undefined,
): CompetitionOption[] {
  const byCode = new Map<string, CompetitionOption>();
  for (const c of all?.matches ?? []) {
    const o = byCode.get(c.competition);
    if (o) o.count = (o.count ?? 0) + 1;
    else byCode.set(c.competition, { code: c.competition, name: c.competition_name, count: 1 });
  }
  // A competition in the address but not among the newest matches still gets its option.
  if (selected && !byCode.has(selected)) {
    const name = shown?.matches[0]?.competition_name ?? selected;
    byCode.set(selected, { code: selected, name, count: null });
  }
  return sortByCompetition([...byCode.values()], (o) => o.code);
}

// ---------------------------------------------------------------------------------------------
// The list

/** The three markets in each row: match result, the chosen goal line, both teams to score. */
function rowMarkets(line: number): string[] {
  return ["1X2", `OU_${line}`, "BTTS"];
}

function shortMarket(market: string): string {
  if (market === "1X2") return "Result";
  if (market === "BTTS") return "Both score";
  if (market.startsWith("OU_")) return `Goals ${market.slice(3)}`;
  return market;
}

function shortPick(market: string, code: string): string {
  if (market === "BTTS") return code === "Y" ? "Yes" : code === "N" ? "No" : code;
  return outcomeLabel(market, code);
}

/** The saved probability of the pick: from the card's selections, or (older copies) its
 * probabilities. */
function pickProb(card: PastCard, market: string, code: string): number | null {
  const sel = card.selections[market];
  if (sel && sel.pick === code) return sel.p;
  const m: unknown = (card.probabilities as Record<string, unknown> | undefined)?.[market];
  if (typeof m !== "object" || m === null) return null;
  const v = (m as Record<string, unknown>)[code];
  return typeof v === "number" && Number.isFinite(v) ? v : null;
}

/** One match: its cards, the counted one first (the API's order). */
type FixtureGroup = { ref: string; cards: PastCard[] };
type DayGroup = { day: string; fixtures: FixtureGroup[] };

/** "Sunday 25 October", as on Fixtures; the year only for a day in an earlier year. */
function dayHeading(day: string): string {
  return day.slice(0, 4) === londonDayKey(new Date().toISOString(), day).slice(0, 4)
    ? formatDayHeading(day)
    : formatDayHeadingWithYear(day);
}

function byFixture(rows: PastCard[]): FixtureGroup[] {
  const out = new Map<string, PastCard[]>();
  for (const r of rows) {
    const list = out.get(r.fixture_ref);
    if (list) list.push(r);
    else out.set(r.fixture_ref, [r]);
  }
  return [...out.entries()].map(([ref, cards]) => ({ ref, cards }));
}

function groupByDay(fixtures: FixtureGroup[]): DayGroup[] {
  const byDay = new Map<string, FixtureGroup[]>();
  for (const f of fixtures) {
    const lead = f.cards[0];
    if (!lead) continue;
    const day = londonDayKey(lead.kickoff_utc, lead.match_date);
    const list = byDay.get(day);
    if (list) list.push(f);
    else byDay.set(day, [f]);
  }
  const kickoff = (f: FixtureGroup) => f.cards[0]?.kickoff_utc ?? "";
  const id = (f: FixtureGroup) => f.cards[0]?.id ?? 0;
  return [...byDay.entries()]
    .sort(([a], [b]) => b.localeCompare(a))
    .map(([day, list]) => ({
      day,
      fixtures: list.sort((a, b) => kickoff(b).localeCompare(kickoff(a)) || id(b) - id(a)),
    }));
}

function latestSettlement(rows: PastCard[]): string | null {
  let best: string | null = null;
  let bestAt = -Infinity;
  for (const r of rows) {
    const at = r.settlement ? parseUtc(r.settlement.settled_at_utc).getTime() : NaN;
    if (r.settlement && Number.isFinite(at) && at > bestAt) {
      bestAt = at;
      best = r.settlement.settled_at_utc;
    }
  }
  return best;
}

/** The cards the tally counts: the records of record (every card in an older copy, which
 * didn't mark them). */
function countedRows(rows: PastCard[]): PastCard[] {
  return rows.filter((r) => r.counted !== false);
}

function PastList({
  data,
  kind,
  mode,
  line,
  shown,
  page,
  limit,
  showCompetition,
}: {
  data: PastResponse;
  kind: Kind;
  mode: Mode;
  line: number;
  shown: number;
  page: number;
  limit: number;
  showCompetition: boolean;
}) {
  const rows = data.matches;
  const markets = rowMarkets(line);
  const fixtures = useMemo(() => byFixture(rows), [rows]);
  const days = useMemo(() => groupByDay(fixtures.slice(0, shown)), [fixtures, shown]);
  const counted = useMemo(() => countedRows(rows), [rows]);
  const truncated = data.truncated ?? fixtures.length >= limit;
  const lastSettled = latestSettlement(rows);
  const remaining = fixtures.length - shown;
  const models = mode === "all" ? new Set(rows.map((r) => r.model_name)).size : 0;

  return (
    <>
      <p className="muted small updated">
        {formatNumber(fixtures.length, 0)} settled {fixtures.length === 1 ? "match" : "matches"}
        {mode === "all" && (
          <>
            {" "}
            · {formatNumber(rows.length, 0)} records from {formatNumber(models, 0)}{" "}
            {models === 1 ? "model" : "models"}
          </>
        )}
        {truncated && " (the newest only)"}
        {lastSettled && <> · latest settled {formatLondon(lastSettled)}</>}
      </p>

      <Tally rows={counted} markets={markets} kind={kind} mode={mode} truncated={truncated} />

      {days.map((d) => (
        <section key={d.day} className="past-day" aria-labelledby={`day-${d.day}`}>
          <h2 id={`day-${d.day}`} className="day-head">
            <span className="day-name">{dayHeading(d.day)}</span>
            <span className="day-count">
              {d.fixtures.length} {d.fixtures.length === 1 ? "match" : "matches"}
            </span>
          </h2>
          <ol className={`past-list${mode === "all" ? " past-list-all" : ""}`}>
            {d.fixtures.map((f) =>
              mode === "all" ? (
                <FixtureModels key={f.ref} cards={f.cards} markets={markets} showCompetition={showCompetition} />
              ) : (
                f.cards[0] && (
                  <PastRow key={f.ref} card={f.cards[0]} markets={markets} showCompetition={showCompetition} />
                )
              ),
            )}
          </ol>
        </section>
      ))}

      {remaining > 0 && (
        <button type="button" className="show-more" onClick={() => setQuery({ shown: shown + page })}>
          Show {Math.min(page, remaining)} more of {formatNumber(remaining, 0)}
        </button>
      )}
      {truncated && remaining <= 0 && (
        <p className="muted small">
          {mode === "all"
            ? `Every model's records are listed for the newest ${formatNumber(limit, 0)} settled matches only. Each match's page lists every model's record of it.`
            : `Only the newest ${formatNumber(limit, 0)} settled matches are listed. Choose a competition to see further back in one league.`}
        </p>
      )}
    </>
  );
}

/** Counts of the correct/incorrect markers of the records of record in this list: no new
 * numbers, just a tally, so it agrees with Performance. */
function Tally({
  rows,
  markets,
  kind,
  mode,
  truncated,
}: {
  rows: PastCard[];
  markets: string[];
  kind: Kind;
  mode: Mode;
  truncated: boolean;
}) {
  const items = markets.map((m) => {
    let n = 0;
    let correct = 0;
    for (const r of rows) {
      const o = r.settlement?.outcomes[m];
      if (!o) continue;
      n += 1;
      if (o.correct) correct += 1;
    }
    return { market: m, n, correct };
  });
  return (
    <section className="card tally" aria-labelledby="tally-title">
      <div className="tally-head">
        <h2 id="tally-title">
          Picks right{kind === "genuine" ? ": records of record" : " in this list"}
          {truncated ? " (newest matches)" : ""}
        </h2>
        <RecordTypeBadge type={KINDS[kind].recordType} />
      </div>
      <ul className="tally-cells">
        {items.map((i) => (
          <li key={i.market}>
            <span className="tally-label">{shortMarket(i.market)}</span>
            {i.n > 0 ? (
              <>
                <span className="tally-value">
                  {formatNumber(i.correct, 0)} <span className="tally-of">of {formatNumber(i.n, 0)}</span>
                </span>
                <span className="muted small">{formatPct(i.correct / i.n)} correct</span>
              </>
            ) : (
              <span className="muted small">Not settled</span>
            )}
          </li>
        ))}
      </ul>
      <p className="muted small">
        {kind === "genuine" ? (
          <>
            Each match counted once, with its record of record, as Performance counts it
            {mode === "all" ? "; the other models' records below are not in this tally" : ""}. The
            full pre-match record, every market, is on <a href={href("/performance")}>Performance</a>.
          </>
        ) : (
          "Replayed after the event, so this says how the method would have done, not how it did."
        )}
      </p>
    </section>
  );
}

function scoreText(card: PastCard): { scored: boolean; text: string; label: string } {
  const s = card.settlement;
  const scored = s !== null && s.home_goals !== null && s.away_goals !== null;
  return {
    scored,
    text: scored ? `${s.home_goals}–${s.away_goals}` : s ? settlementStatusLabel(s.status) : "—",
    label: scored
      ? `${card.home_name} ${s.home_goals}, ${card.away_name} ${s.away_goals}`
      : `${card.home_name} versus ${card.away_name}, ${s?.status ?? "not settled"}`,
  };
}

function Teams({ card, scoreLink }: { card: PastCard; scoreLink: "stretched" | "plain" }) {
  const score = scoreText(card);
  const held = useMatchPageCheck()(card.id);
  const cls = `past-score${score.scored ? "" : " is-status"}`;
  return (
    <h3 className="past-teams">
      <span className="past-home">
        <TeamLink teamKey={card.home_team} className="past-name">
          {card.home_name}
        </TeamLink>
        <TeamBadge teamKey={card.home_team} name={card.home_name} media={card.home_media} size={28} />
      </span>
      {held ? (
        <a
          className={`${scoreLink === "stretched" ? "stretched " : ""}${cls}`}
          href={href(`/match/${card.id}`)}
          aria-label={`${score.label}. Open the saved prediction`}
        >
          {score.text}
        </a>
      ) : (
        <span className={cls} aria-label={score.label}>
          {score.text}
        </span>
      )}
      <span className="past-away">
        <TeamBadge teamKey={card.away_team} name={card.away_name} media={card.away_media} size={28} />
        <TeamLink teamKey={card.away_team} className="past-name">
          {card.away_name}
        </TeamLink>
      </span>
    </h3>
  );
}

/** One match, its record of record: the default list. */
function PastRow({
  card,
  markets,
  showCompetition,
}: {
  card: PastCard;
  markets: string[];
  showCompetition: boolean;
}) {
  const s = card.settlement;
  const settledMarkets = s ? Object.keys(s.outcomes).length : 0;
  const others = Math.max(0, (card.models_for_fixture ?? 1) - 1);

  return (
    <li>
      <article className="card past-row">
        <div className="past-meta">
          {card.kickoff_utc && <span className="kickoff">{formatTime(card.kickoff_utc)}</span>}
          {showCompetition && <CompetitionChip code={card.competition} name={card.competition_name} />}
          {card.edition !== "early" && <EditionBadge edition={card.edition} />}
          {card.basis === "model_and_market" && <BasisBadge basis={card.basis} consensus={isConsensus(card)} />}
        </div>
        {/* Each name opens its team's page; anywhere else on the row opens the saved
            prediction (the score is the link, stretched over the row). */}
        <Teams card={card} scoreLink="stretched" />
        {settledMarkets === 0 ? (
          <p className="muted small">
            {s && s.status !== "settled"
              ? `No markets settled: the match was ${s.status}.`
              : "No markets were settled for this record."}
          </p>
        ) : (
          <ul className="pm-cells">
            {markets.map((m) => (
              <MarketCell key={m} card={card} market={m} />
            ))}
          </ul>
        )}
        <p className="past-by small">
          <span className="muted">{card.counted === false ? "Shown: " : "Record of record: "}</span>
          <span className="past-by-model">{card.model_label ?? card.model_name}</span>
          {others > 0 && (
            <span className="above-stretch">
              <span className="muted">
                {" "}
                · {others} other {others === 1 ? "model" : "models"} ·{" "}
              </span>
              <MatchLink id={card.id} query={{ at: "models" }} className="past-compare">
                Compare every model
                <span className="visually-hidden">
                  : {card.home_name} versus {card.away_name}
                </span>
              </MatchLink>
            </span>
          )}
        </p>
      </article>
    </li>
  );
}

function MarketCell({ card, market }: { card: PastCard; market: string }) {
  const o = card.settlement?.outcomes[market];
  const label = shortMarket(market);
  if (!o) {
    return (
      <li className="pm-cell pm-none">
        <span className="pm-label">{label}</span>
        <span className="pm-pick muted">Not predicted</span>
      </li>
    );
  }
  const p = pickProb(card, market, o.pick);
  return (
    <li className={`pm-cell ${o.correct ? "pm-correct" : "pm-incorrect"}`}>
      <span className="pm-label">{label}</span>
      <span className="pm-pick">{shortPick(market, o.pick)}</span>
      <span className="pm-foot">
        <span className="pm-prob">{formatPct(p)}</span>
        <span className="pm-mark" aria-hidden="true">
          {o.correct ? "✓" : "✗"}
        </span>
      </span>
      <span className="visually-hidden">
        {o.correct ? "Correct" : `Incorrect: it was ${outcomeLabel(market, o.actual)}`}
      </span>
    </li>
  );
}

/** One match with every model's settled record: "All models". */
function FixtureModels({
  cards,
  markets,
  showCompetition,
}: {
  cards: PastCard[];
  markets: string[];
  showCompetition: boolean;
}) {
  const lead = cards[0];
  if (!lead) return null;
  return (
    <li>
      <article className="card past-row past-fixture">
        <div className="past-meta">
          {lead.kickoff_utc && <span className="kickoff">{formatTime(lead.kickoff_utc)}</span>}
          {showCompetition && <CompetitionChip code={lead.competition} name={lead.competition_name} />}
          <span>
            {cards.length} {cards.length === 1 ? "model" : "models"}
          </span>
        </div>
        <Teams card={lead} scoreLink="plain" />
        <table className="pmr-table">
          <caption className="visually-hidden">
            Every model's pick for {lead.home_name} versus {lead.away_name}, and whether it was right
          </caption>
          <thead>
            <tr>
              <th scope="col">Model</th>
              {markets.map((m) => (
                <th scope="col" key={m}>
                  {shortMarket(m)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {cards.map((c) => (
              <tr key={c.id} className={c.counted ? "is-counted" : undefined}>
                <th scope="row">
                  <MatchLink id={c.id} className="pmr-model">
                    {c.model_label ?? c.model_name}
                  </MatchLink>
                  {c.counted && <span className="pmr-tag">Record of record</span>}
                  {isConsensus(c) && <span className="pmr-tag pmr-tag-market">Bookmakers' consensus</span>}
                  {c.edition !== "early" && <span className="pmr-tag">{c.edition} edition</span>}
                </th>
                {markets.map((m) => (
                  <ModelCell key={m} card={c} market={m} />
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </article>
    </li>
  );
}

function ModelCell({ card, market }: { card: PastCard; market: string }) {
  const o = card.settlement?.outcomes[market];
  if (!o) {
    return (
      <td className="pmr-cell pmr-none">
        <span className="muted">—</span>
        <span className="visually-hidden">Not predicted</span>
      </td>
    );
  }
  return (
    <td className={`pmr-cell ${o.correct ? "pm-correct" : "pm-incorrect"}`}>
      <span className="pmr-pick">{shortPick(market, o.pick)}</span>{" "}
      <span className="pmr-prob">{formatPct(pickProb(card, market, o.pick))}</span>{" "}
      <span className="pm-mark" aria-hidden="true">
        {o.correct ? "✓" : "✗"}
      </span>
      <span className="visually-hidden">{o.correct ? "Correct" : `Incorrect: it was ${outcomeLabel(market, o.actual)}`}</span>
    </td>
  );
}
