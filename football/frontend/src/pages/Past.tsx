// Brief item 3: settled predictions, newest first, with the pick, its probability, the score
// and whether each market was right. Genuine pre-match and retrospective records are two
// separate lists, never mixed in one table (the API returns one record type per request).
// Each row opens the saved record and its evidence on the match page.

import { useMemo, type ChangeEvent } from "react";
import {
  endpoints,
  useApi,
  type PastResponse,
  type PredictionCard,
  type Probabilities,
  type RecordType,
} from "../api";
import { BasisBadge, EditionBadge, RecordTypeBadge } from "../components/Badge";
import { CompetitionChip } from "../components/CompetitionMark";
import { ApiView, Empty } from "../components/StateViews";
import { TeamBadge } from "../components/TeamBadge";
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

/** Records requested per list (the API allows up to 2,000). */
const LIMIT = 500;
/** Rows drawn at a time; "Show more" adds another page. */
const PAGE = 40;
const LINES = [0.5, 1.5, 2.5, 3.5, 4.5] as const;
const DEFAULT_LINE = 2.5;

type Kind = "genuine" | "retrospective";

const KINDS: Record<Kind, { recordType: RecordType; label: string }> = {
  genuine: { recordType: "genuine_prematch", label: "Genuine pre-match" },
  retrospective: { recordType: "retrospective_backtest", label: "Retrospective" },
};

function parseKind(raw: string | null): Kind {
  return raw === "retrospective" ? "retrospective" : "genuine";
}

function parseLine(raw: string | null): number {
  const n = raw === null ? NaN : Number(raw);
  return (LINES as readonly number[]).includes(n) ? n : DEFAULT_LINE;
}

function parseShown(raw: string | null): number {
  const n = raw === null ? NaN : Number(raw);
  return Number.isInteger(n) && n > PAGE ? Math.min(n, LIMIT) : PAGE;
}

export function Past({ query }: PageProps) {
  useDocumentTitle("Past matches");
  const kind = parseKind(query.get("type"));
  const recordType = KINDS[kind].recordType;
  const competition = query.get("competition") || null;
  const line = parseLine(query.get("line"));
  const shown = parseShown(query.get("shown"));

  // The unfiltered list gives the competition choices. With no competition chosen it is
  // also the list shown, so there is one request, not two.
  const all = useApi(endpoints.past({ record_type: recordType, limit: LIMIT }));
  const filtered = useApi(
    competition ? endpoints.past({ record_type: recordType, competition, limit: LIMIT }) : null,
  );
  const result = competition ? filtered : all;
  const options = useMemo(
    () => competitionOptions(all.data, competition, filtered.data),
    [all.data, competition, filtered.data],
  );
  const optionsTruncated = (all.data?.matches.length ?? 0) >= LIMIT;
  const competitionName = options.find((o) => o.code === competition)?.name ?? competition;
  const filtersChanged = kind !== "genuine" || competition !== null || line !== DEFAULT_LINE;

  const onCompetition = (e: ChangeEvent<HTMLSelectElement>) =>
    setQuery({ competition: e.target.value || null, shown: null });
  const onLine = (e: ChangeEvent<HTMLSelectElement>) =>
    setQuery({ line: Number(e.target.value) === DEFAULT_LINE ? null : e.target.value });

  return (
    <>
      <header className="page-head">
        <h1 className="page-title">Past matches</h1>
        <p className="lede">
          Settled predictions, newest first: the pick in each market, the probability the model
          gave it, the score and whether it was right. Open a match to see the evidence saved
          with it.
        </p>
      </header>

      <div className="filters" role="group" aria-label="Record type">
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

      <KindNote kind={kind} />

      <div className="filters filter-fields">
        <label className="field">
          <span className="filter-label">Competition</span>
          <select value={competition ?? ""} onChange={onCompetition}>
            <option value="">All competitions</option>
            {options.map((o) => (
              <option key={o.code} value={o.code}>
                {o.name}
                {o.count !== null && !optionsTruncated ? ` (${o.count})` : ""}
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
            onClick={() => setQuery({ type: null, competition: null, line: null, shown: null })}
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
            line={line}
            shown={shown}
            showCompetition={competition === null}
          />
        )}
      </ApiView>
    </>
  );
}

function KindNote({ kind }: { kind: Kind }) {
  if (kind === "genuine") {
    return (
      <div className="notice-info" role="note">
        <strong>Genuine pre-match record.</strong> Every prediction here was saved before kickoff
        and never changed, then scored once the result was in. Retrospective records are a
        separate list. Totals are on <a href={href("/performance")}>Performance</a>.
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
  filtered: PastResponse | undefined,
): CompetitionOption[] {
  const byCode = new Map<string, CompetitionOption>();
  for (const c of all?.matches ?? []) {
    const o = byCode.get(c.competition);
    if (o) o.count = (o.count ?? 0) + 1;
    else byCode.set(c.competition, { code: c.competition, name: c.competition_name, count: 1 });
  }
  // A competition in the address but not among the newest records still gets its option.
  if (selected && !byCode.has(selected)) {
    const name = filtered?.matches[0]?.competition_name ?? selected;
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

/** The saved probability of an outcome, read from the record. */
function probOf(p: Probabilities, market: string, code: string): number | null {
  const m: unknown = (p as Record<string, unknown>)[market];
  if (typeof m !== "object" || m === null) return null;
  const v = (m as Record<string, unknown>)[code];
  return typeof v === "number" && Number.isFinite(v) ? v : null;
}

type DayGroup = { day: string; rows: PredictionCard[] };

/** "Sunday 25 October", as on Fixtures; the year only for a day in an earlier year. */
function dayHeading(day: string): string {
  return day.slice(0, 4) === londonDayKey(new Date().toISOString(), day).slice(0, 4)
    ? formatDayHeading(day)
    : formatDayHeadingWithYear(day);
}

function groupByDay(rows: PredictionCard[]): DayGroup[] {
  const byDay = new Map<string, PredictionCard[]>();
  for (const r of rows) {
    const day = londonDayKey(r.kickoff_utc, r.match_date);
    const list = byDay.get(day);
    if (list) list.push(r);
    else byDay.set(day, [r]);
  }
  const kickoff = (r: PredictionCard) => r.kickoff_utc ?? "";
  return [...byDay.entries()]
    .sort(([a], [b]) => b.localeCompare(a))
    .map(([day, list]) => ({
      day,
      rows: list.sort((a, b) => kickoff(b).localeCompare(kickoff(a)) || b.id - a.id),
    }));
}

function latestSettlement(rows: PredictionCard[]): string | null {
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

function PastList({
  data,
  kind,
  line,
  shown,
  showCompetition,
}: {
  data: PastResponse;
  kind: Kind;
  line: number;
  shown: number;
  showCompetition: boolean;
}) {
  const rows = data.matches;
  const markets = rowMarkets(line);
  const days = useMemo(() => groupByDay(rows.slice(0, shown)), [rows, shown]);
  const showModel = useMemo(() => new Set(rows.map((r) => r.model_name)).size > 1, [rows]);
  const truncated = rows.length >= LIMIT;
  const lastSettled = latestSettlement(rows);
  const remaining = rows.length - shown;

  return (
    <>
      <p className="muted small updated">
        {formatNumber(rows.length, 0)} settled {rows.length === 1 ? "record" : "records"}
        {truncated && " (the newest only)"}
        {lastSettled && <> · latest settled {formatLondon(lastSettled)}</>}
      </p>

      <Tally rows={rows} markets={markets} kind={kind} truncated={truncated} />

      {days.map((d) => (
        <section key={d.day} className="past-day" aria-labelledby={`day-${d.day}`}>
          <h2 id={`day-${d.day}`} className="day-head">
            <span className="day-name">{dayHeading(d.day)}</span>
            <span className="day-count">
              {d.rows.length} {d.rows.length === 1 ? "match" : "matches"}
            </span>
          </h2>
          <ol className="past-list">
            {d.rows.map((r) => (
              <PastRow
                key={r.id}
                card={r}
                markets={markets}
                showCompetition={showCompetition}
                showModel={showModel}
              />
            ))}
          </ol>
        </section>
      ))}

      {remaining > 0 && (
        <button
          type="button"
          className="show-more"
          onClick={() => setQuery({ shown: shown + PAGE })}
        >
          Show {Math.min(PAGE, remaining)} more of {formatNumber(remaining, 0)}
        </button>
      )}
      {truncated && remaining <= 0 && (
        <p className="muted small">
          Only the newest {formatNumber(LIMIT, 0)} settled records are listed. Choose a competition
          to see further back in one league.
        </p>
      )}
    </>
  );
}

/** Counts of the correct/incorrect markers in this list: no new numbers, just a tally. */
function Tally({
  rows,
  markets,
  kind,
  truncated,
}: {
  rows: PredictionCard[];
  markets: string[];
  kind: Kind;
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
          Picks right in this list{truncated ? " (newest records)" : ""}
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
            A count of the markers below. The full pre-match record, every market, is on{" "}
            <a href={href("/performance")}>Performance</a>.
          </>
        ) : (
          "Replayed after the event, so this says how the method would have done, not how it did."
        )}
      </p>
    </section>
  );
}

function PastRow({
  card,
  markets,
  showCompetition,
  showModel,
}: {
  card: PredictionCard;
  markets: string[];
  showCompetition: boolean;
  showModel: boolean;
}) {
  const s = card.settlement;
  const scored = s !== null && s.home_goals !== null && s.away_goals !== null;
  const settledMarkets = s ? Object.keys(s.outcomes).length : 0;
  const linkLabel = scored
    ? `${card.home_name} ${s.home_goals}, ${card.away_name} ${s.away_goals}. Open the saved prediction`
    : `${card.home_name} versus ${card.away_name}, ${s?.status ?? "not settled"}. Open the saved prediction`;

  return (
    <li>
      <article className="card past-row">
        <div className="past-meta">
          {card.kickoff_utc && <span className="kickoff">{formatTime(card.kickoff_utc)}</span>}
          {showCompetition && <CompetitionChip code={card.competition} name={card.competition_name} />}
          {card.edition !== "early" && <EditionBadge edition={card.edition} />}
          {card.basis === "model_and_market" && <BasisBadge basis={card.basis} />}
          {showModel && <span className="past-model">{card.model_name}</span>}
        </div>
        <h3 className="past-teams">
          <a className="stretched" href={href(`/match/${card.id}`)} aria-label={linkLabel}>
            <span className="past-home">
              <span className="past-name">{card.home_name}</span>
              <TeamBadge teamKey={card.home_team} name={card.home_name} size={28} />
            </span>
            <span className={`past-score${scored ? "" : " is-status"}`}>
              {scored ? `${s.home_goals}–${s.away_goals}` : s ? settlementStatusLabel(s.status) : "—"}
            </span>
            <span className="past-away">
              <TeamBadge teamKey={card.away_team} name={card.away_name} size={28} />
              <span className="past-name">{card.away_name}</span>
            </span>
          </a>
        </h3>
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
      </article>
    </li>
  );
}

function MarketCell({ card, market }: { card: PredictionCard; market: string }) {
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
  const p = card.selections[market]?.p ?? probOf(card.probabilities, market, o.pick);
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
