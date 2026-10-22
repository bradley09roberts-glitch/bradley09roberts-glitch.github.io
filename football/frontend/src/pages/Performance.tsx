// Brief item 5: the genuine pre-match record, and only that. Every number comes from
// /api/performance, which counts settled genuine_prematch predictions; retrospective results
// live in the backtest lab and in the retrospective list on Past matches.

import {
  endpoints,
  useApi,
  type Basis,
  type Performance as PerformanceData,
  type PerformanceMarket,
  type PerformanceRecord,
  type PastCard,
} from "../api";
import { BasisBadge } from "../components/Badge";
import { ClosingPriceNote } from "../components/Signals";
import { ApiView, Empty } from "../components/StateViews";
import {
  formatLondon,
  formatNumber,
  formatPct,
  formatShortDay,
  marketLabel,
  marketOrder,
} from "../format";
import { useDocumentTitle } from "../hooks";
import { href } from "../router";
import type { PageProps } from ".";
import { ModelLeaderboards } from "./Leaderboard";

/** Below this many settled predictions a market's figures can swing a long way. */
const SMALL_SAMPLE = 30;

export function Performance(_props: PageProps) {
  useDocumentTitle("Performance");
  const perf = useApi(endpoints.performance());
  // The most recently played settled match, to show how current the record is.
  const latest = useApi(endpoints.past({ record_type: "genuine_prematch", limit: 1 }));

  return (
    <>
      <header className="page-head">
        <h1 className="page-title">Performance</h1>
        <p className="lede">
          The genuine pre-match record: predictions saved before kickoff and never changed, each
          scored once its match was settled.
        </p>
      </header>

      <div className="notice-info" role="note">
        <strong>Pre-match record only.</strong> Retrospective results, made by replaying the model
        over matches already played, are not counted here. They are in the{" "}
        <a href={href("/lab")}>backtest lab</a> and in the{" "}
        <a href={href("/past", { type: "retrospective" })}>retrospective list</a> on Past matches.
        How often the models of record were right on the test seasons, by league and market, is
        on the lab's <a href={href("/lab", { tab: "hits" })}>Hit rates</a> tab.
      </div>

      <ClosingPriceNote />

      <ApiView
        result={perf}
        what="the pre-match record"
        loadingLines={2}
        isEmpty={(d) => d.published === 0}
        empty={
          <Empty title="No pre-match predictions published yet">
            <p>
              This record counts only predictions saved before kickoff. Results appear here once
              those matches have been played and settled. Upcoming predictions are on the{" "}
              <a href={href("/fixtures")}>Fixtures</a> page.
            </p>
          </Empty>
        }
      >
        {(d) => <Record d={d} latest={latest.data?.matches[0]} />}
      </ApiView>

      <ModelLeaderboards />

      <div className="perf-notes">
      <section className="card" aria-labelledby="betting-record">
        <h2 id="betting-record">Betting record</h2>
        <p>
          <strong>No betting record yet.</strong> No betting selections have been published, so
          there is no profit or loss to report.
        </p>
        <p className="muted small">
          Accuracy says how often the model's most likely outcome happened; it is not a betting
          return. When selections are published, their results will be shown here separately, in
          pounds sterling.
        </p>
      </section>

      <section className="card" aria-labelledby="how-to-read">
        <h2 id="how-to-read">How to read this</h2>
        <ul className="facts">
          <li>
            <strong>Accuracy</strong> is the share of settled predictions whose most likely
            outcome happened. In the match result, a draw can be the pick.
          </li>
          <li>
            <strong>Average probability of the result</strong> is the probability the model gave
            to what actually happened, averaged over settled matches. Higher is better, and it
            rewards being confident only when right. Giving every outcome an equal chance would
            score 33% on the match result and 50% on the two-way markets: that level is marked
            on each bar of the average probability.
          </li>
          <li>
            The headline counts each fixture once, using its <strong>record of record</strong>:
            the first model in the published order that saved a pre-match prediction for it, and
            that model's first edition.
          </li>
          <li>
            <strong>Model + market</strong> records use bookmakers' prices collected before the
            prediction (for league games the match result and over/under 2.5 are the bookmakers'
            consensus itself); <strong>model only</strong> records use past results alone. They
            are counted separately, each fixture once per kind, and neither replaces the other.
          </li>
          <li>
            With fewer than {SMALL_SAMPLE} settled matches a market's figures can move a long way
            from one weekend to the next.
          </li>
          <li>
            The <strong>model leaderboards</strong> rank every model with settled pre-match records
            by mean RPS (ranked probability score of the match-result forecast; lower is better).
            Results right is how often its most likely result happened, with a 95% interval drawn
            as a bar; log losses score the over/under 2.5 and both-teams-to-score forecasts (lower
            is better). "Against the record" compares a model with each match's record of record on
            the matches both have; negative means the model did better. Under 100 settled matches
            the order is provisional.
          </li>
          <li>
            Predictions for past seasons, computed after the fact, are in the lab's{" "}
            <a href={href("/lab", { tab: "explorer" })}>backtest explorer</a>; they are never counted
            here.
          </li>
        </ul>
        <p className="small">
          Every settled prediction, with its pick and score, is on{" "}
          <a href={href("/past")}>Past matches</a>.
        </p>
      </section>
      </div>
    </>
  );
}

function Record({ d, latest }: { d: PerformanceData; latest: PastCard | undefined }) {
  const markets = Object.entries(d.markets).sort(([a], [b]) => marketOrder(a) - marketOrder(b));
  return (
    <>
      <section className="card" aria-labelledby="totals">
        <h2 id="totals">Predictions</h2>
        <dl className="kpis">
          <div className="kpi">
            <dt>Published</dt>
            <dd>{formatNumber(d.published, 0)}</dd>
          </div>
          <div className="kpi">
            <dt>Settled</dt>
            <dd>{formatNumber(d.settled, 0)}</dd>
          </div>
          <div className="kpi">
            <dt>Awaiting result</dt>
            <dd>{formatNumber(d.pending, 0)}</dd>
          </div>
        </dl>
        <RecordOfRecord primary={d.primary_models} />
        {latest && <LatestSettled card={latest} />}
      </section>

      {d.settled === 0 || markets.length === 0 ? (
        <Empty title="No results yet">
          <p>
            Results appear here once matches are settled.{" "}
            {d.pending > 0 &&
              `${formatNumber(d.pending, 0)} ${d.pending === 1 ? "prediction is" : "predictions are"} waiting for ${d.pending === 1 ? "its match" : "their matches"} to be played and settled.`}
          </p>
          <p>
            <a href={href("/fixtures")}>See the upcoming predictions</a>.
          </p>
        </Empty>
      ) : (
        <section className="card" aria-labelledby="by-market">
          <h2 id="by-market">By market</h2>
          <MarketTable markets={markets} />
        </section>
      )}

      <ByBasis d={d} />
    </>
  );
}

const GROUP_NAMES: Record<string, string> = { domestic: "Domestic leagues", uefa: "UEFA competitions" };

/** "Domestic leagues: market_tilt_dc, otherwise dixon_coles." from primary_models. */
function RecordOfRecord({ primary }: { primary: Record<string, string[]> | undefined }) {
  const groups = Object.entries(primary ?? {}).filter(([, models]) => models.length > 0);
  if (groups.length === 0) return null;
  return (
    <p className="muted small latest-settled">
      Record of record:{" "}
      {groups.map(([group, models], i) => (
        <span key={group}>
          {i > 0 && "; "}
          {GROUP_NAMES[group] ?? group}{" "}
          {models.map((m, j) => (
            <span key={m}>
              {j > 0 && ", otherwise "}
              <code>{m}</code>
            </span>
          ))}
        </span>
      ))}
      .
    </p>
  );
}

const BASES: Basis[] = ["model_and_market", "model_only"];

const BASIS_TEXT: Record<Basis, string> = {
  model_and_market:
    "Bookmakers' prices collected before the prediction were used. For league games the match result and over/under 2.5 goals are the bookmakers' consensus with the margins removed, so these figures score the bookmakers, not the model. Prices arrive on Tuesdays and Fridays, so only fixtures priced by then have one.",
  model_only:
    "Past results alone, without prices. These continue for every fixture, so the pure model can still be compared with the market.",
};

function ByBasis({ d }: { d: PerformanceData }) {
  if (!d.by_basis) return null;
  return (
    <section className="card" aria-labelledby="by-basis">
      <h2 id="by-basis">Model + market and model only</h2>
      {BASES.map((b) => (
        <BasisBlock key={b} basis={b} r={d.by_basis[b]} />
      ))}
      {d.paired && <Paired p={d.paired} />}
    </section>
  );
}

function BasisBlock({ basis, r }: { basis: Basis; r: PerformanceRecord & { models: string[] } }) {
  const markets = Object.entries(r.markets).sort(([a], [b]) => marketOrder(a) - marketOrder(b));
  return (
    <div className="basis-block">
      <h3>
        <BasisBadge basis={basis} />
      </h3>
      <p className="muted small">{BASIS_TEXT[basis]}</p>
      <dl className="kpis">
        <div className="kpi">
          <dt>Published</dt>
          <dd>{formatNumber(r.published, 0)}</dd>
        </div>
        <div className="kpi">
          <dt>Settled</dt>
          <dd>{formatNumber(r.settled, 0)}</dd>
        </div>
        <div className="kpi">
          <dt>Awaiting result</dt>
          <dd>{formatNumber(r.pending, 0)}</dd>
        </div>
      </dl>
      <p className="muted small">
        {r.models.length > 0 ? (
          <>
            {r.models.length === 1 ? "Model" : "Models"}:{" "}
            {r.models.map((m, i) => (
              <span key={m}>
                {i > 0 && ", "}
                <code>{m}</code>
              </span>
            ))}
          </>
        ) : (
          "None published yet."
        )}
      </p>
      {r.settled > 0 && markets.length > 0 && <MarketTable markets={markets} />}
    </div>
  );
}

function Paired({ p }: { p: PerformanceData["paired"] }) {
  if (p.fixtures === 0) return null;
  const a = p.model_and_market.markets["1X2"];
  const b = p.model_only.markets["1X2"];
  return (
    <p className="small basis-paired">
      <strong>Like for like:</strong> {formatNumber(p.fixtures, 0)}{" "}
      {p.fixtures === 1 ? "fixture has" : "fixtures have"} both kinds of record.{" "}
      {a && b ? (
        <>
          On the {formatNumber(a.n, 0)} settled, the match result's average probability of the
          actual result was {formatPct(a.mean_p_actual)} with the market and{" "}
          {formatPct(b.mean_p_actual)} without.
        </>
      ) : (
        "None of them has been settled yet."
      )}
    </p>
  );
}

function LatestSettled({ card }: { card: PastCard }) {
  const s = card.settlement;
  if (!s) return null;
  const scored = s.home_goals !== null && s.away_goals !== null;
  return (
    <p className="muted small latest-settled">
      Most recent settled match:{" "}
      <a href={href(`/match/${card.id}`)}>
        {card.home_name} {scored ? `${s.home_goals}–${s.away_goals}` : "v"} {card.away_name}
      </a>{" "}
      ({card.competition_name}, {formatShortDay(card.match_date)}), settled {formatLondon(s.settled_at_utc)}.
    </p>
  );
}

/** What giving every outcome an equal chance scores on average probability of the result. */
function equalChance(market: string): number {
  return market === "1X2" ? 1 / 3 : 1 / 2;
}

function MarketTable({ markets }: { markets: [string, PerformanceMarket][] }) {
  return (
    <>
      <p className="perf-legend muted small" aria-hidden="true">
        <span className="meter-ref-key" /> marks what giving every outcome an equal chance would
        score: 33% on the match result, 50% on the two-way markets.
      </p>
      <table className="perf-table">
        <caption className="visually-hidden">
          Accuracy and average probability of the actual result, by market, for settled
          pre-match predictions
        </caption>
        <thead>
          <tr>
            <th scope="col">Market</th>
            <th scope="col">Accuracy</th>
            <th scope="col">Average probability of the result</th>
          </tr>
        </thead>
        <tbody>
          {markets.map(([m, s]) => {
            const small = s.n < SMALL_SAMPLE;
            return (
              <tr key={m}>
                <th scope="row">
                  <span className="perf-market">{marketLabel(m)}</span>
                  <span className="perf-n">
                    {formatNumber(s.n, 0)} settled
                    {small && <span className="badge badge-warn perf-small">Small sample</span>}
                  </span>
                </th>
                <td>
                  <span className="perf-value">{formatPct(s.accuracy)}</span>
                  <span className="perf-sub">
                    {formatNumber(s.correct, 0)} of {formatNumber(s.n, 0)} right
                  </span>
                  <Meter value={s.accuracy} />
                </td>
                <td>
                  <span className="perf-value">{formatPct(s.mean_p_actual)}</span>
                  <span className="visually-hidden">
                    (an equal chance for every outcome would give {formatPct(equalChance(m))})
                  </span>
                  <Meter value={s.mean_p_actual} reference={equalChance(m)} />
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </>
  );
}

/** A 0–100% bar; `reference` draws a tick at that level. Decorative: the value is printed. */
function Meter({ value, reference }: { value: number | null; reference?: number }) {
  const pct = value === null || !Number.isFinite(value) ? 0 : Math.max(0, Math.min(1, value)) * 100;
  return (
    <span className="meter perf-meter" aria-hidden="true">
      <span className="meter-fill" style={{ width: `${pct}%` }} />
      {reference !== undefined && (
        <span className="meter-ref" style={{ left: `${reference * 100}%` }} />
      )}
    </span>
  );
}
