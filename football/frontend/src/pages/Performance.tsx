// Brief item 5: the genuine pre-match record, and only that. Every number comes from
// /api/performance, which counts settled genuine_prematch predictions; retrospective results
// live in the backtest lab and in the retrospective list on Past matches.

import { endpoints, useApi, type Performance as PerformanceData, type PerformanceMarket, type PredictionCard } from "../api";
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
      </div>

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
            Each saved prediction counts once, so a fixture predicted by two models, or in two
            editions, counts more than once.
          </li>
          <li>
            With fewer than {SMALL_SAMPLE} settled matches a market's figures can move a long way
            from one weekend to the next.
          </li>
        </ul>
        <p className="small">
          Every settled prediction, with its pick and score, is on{" "}
          <a href={href("/past")}>Past matches</a>.
        </p>
      </section>
    </>
  );
}

function Record({ d, latest }: { d: PerformanceData; latest: PredictionCard | undefined }) {
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
    </>
  );
}

function LatestSettled({ card }: { card: PredictionCard }) {
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
