// The backtest lab's Hit rates tab: how often each model of record's pick was right, per
// league or competition and per market, on the two test seasons, with the frequency
// baseline's rate on the same games beneath each one. Then the confidence buckets, then the
// genuine pre-match record (kept in its own card; the Performance page is its home).
//
// Every number comes from /api/hit-rates, which counts the saved test ledgers and the settled
// pre-match records (footy.modeling.hit_rates). Nothing is worked out here except labels.

import {
  endpoints,
  useApi,
  type Basis,
  type ConfidenceBucket,
  type HitRate,
  type HitRateCell,
  type HitRateTable,
  type HitRates,
  type PrematchHitTable,
} from "../api";
import { BasisBadge, basisLabel } from "../components/Badge";
import { Segmented, TableScroll } from "../components/Controls";
import { ApiView } from "../components/StateViews";
import { formatLondon, formatNumber, formatPct, keepNumberWithName, seasonLabel } from "../format";
import { href, setQuery } from "../router";
import "./HitRates.css";

const MARKET_HEAD: Record<string, [string, string]> = {
  "1X2": ["Result", "1X2"],
  "OU_1.5": ["O/U", "1.5"],
  "OU_2.5": ["O/U", "2.5"],
  "OU_3.5": ["O/U", "3.5"],
  BTTS: ["BTTS", "both score"],
};

const BASES: Basis[] = ["model_and_market", "model_only"];

function parseBasis(raw: string | null): Basis {
  return raw === "model_only" ? "model_only" : "model_and_market";
}

function seasonsText(seasons: readonly string[]): string {
  const labels = seasons.map(seasonLabel);
  if (labels.length <= 1) return labels.join("");
  return `${labels.slice(0, -1).join(", ")} and ${labels[labels.length - 1]}`;
}

function listText(names: readonly string[]): string {
  const kept = names.map(keepNumberWithName);
  if (kept.length <= 1) return kept.join("");
  return `${kept.slice(0, -1).join(", ")} and ${kept[kept.length - 1]}`;
}

function pct(r: HitRate | null | undefined): string {
  return formatPct(r?.hit_rate, 1);
}

/** A rate in a table cell, with its % sign (set a little smaller). */
function Rate({ r }: { r: HitRate | null | undefined }) {
  const v = r?.hit_rate;
  if (v === null || v === undefined || !Number.isFinite(v)) return <>—</>;
  return (
    <>
      {(v * 100).toFixed(1)}
      <span className="hit-pct">%</span>
    </>
  );
}

/** "Champions League, Europa League and Conference League, with their qualifying rounds". */
function groupsText(names: { code: string; name: string }[]): string {
  const main = names.filter((g) => !g.code.endsWith("Q"));
  const qualifying = main.length < names.length && main.length > 0;
  return `${listText(main.map((g) => g.name))}${qualifying ? ", with their qualifying rounds" : ""}`;
}

function ciText(ci: [number, number] | null | undefined): string {
  if (!ci) return "—";
  return `${(ci[0] * 100).toFixed(1)}–${(ci[1] * 100).toFixed(1)}`;
}

export function HitRatesPanel({ query }: { query: URLSearchParams }) {
  const res = useApi(endpoints.hitRates());
  const showCi = query.get("ci") === "1";
  const basis = parseBasis(query.get("basis"));
  return (
    <ApiView result={res} what="the hit rates" loadingLines={3}>
      {(d) => <HitRatesView d={d} showCi={showCi} basis={basis} />}
    </ApiView>
  );
}

function HitRatesView({ d, showCi, basis }: { d: HitRates; showCi: boolean; basis: Basis }) {
  const tables = d.backtest.tables;
  const families: string[] = [];
  for (const t of tables) if (!families.includes(t.family)) families.push(t.family);
  const seasons = [...new Set(tables.flatMap((t) => t.seasons))].sort();

  return (
    <>
      <section className="card hit-intro" aria-labelledby="hits-title">
        <h2 id="hits-title">How often the pick was right</h2>
        <p>
          The pick is the most likely outcome: the likeliest match result (a draw can be the pick),
          the likelier side of each goal line, and yes or no for both teams to score. These are the
          test seasons{seasons.length > 0 && `, ${seasonsText(seasons)}`}, replayed using only
          what was known before each match. Each model was chosen on 2023/24 and then run once on
          them.
        </p>
        <p className="muted small">
          Beneath each rate, in grey, is the <strong>baseline</strong> on the same games: always
          picking the outcome that had been most common so far. A model is only useful where it
          beats that.
        </p>
        <label className="hit-ci-toggle">
          <input
            type="checkbox"
            checked={showCi}
            onChange={(e) => setQuery({ ci: e.target.checked ? 1 : null })}
          />
          Show 95% intervals
        </label>
      </section>

      {families.map((f) => (
        <FamilyCard
          key={f}
          tables={tables.filter((t) => t.family === f)}
          markets={d.markets}
          showCi={showCi}
          basis={basis}
        />
      ))}

      {d.backtest.missing.length > 0 && (
        <p className="muted small hit-missing">
          Not shown, because a test ledger isn't saved or doesn't fit its baseline's:{" "}
          {d.backtest.missing.map((m, i) => (
            <span key={`${m.family}-${m.model}`}>
              {i > 0 && "; "}
              <code>{m.model}</code> ({m.reason})
            </span>
          ))}
          .
        </p>
      )}

      <PrematchCard pre={d.prematch} markets={d.markets} />
    </>
  );
}

function FamilyCard({
  tables,
  markets,
  showCi,
  basis,
}: {
  tables: HitRateTable[];
  markets: HitRates["markets"];
  showCi: boolean;
  basis: Basis;
}) {
  const first = tables[0];
  if (!first) return null;
  const table = tables.find((t) => t.basis === basis) ?? first;
  const bases = BASES.filter((b) => tables.some((t) => t.basis === b));
  const groups = table.rows.filter((r) => r.code !== "ALL");
  const all = table.rows.find((r) => r.code === "ALL");
  const id = `hits-${table.family}`;
  const uefa = table.family === "uefa";

  return (
    <section className="card hit-family" aria-labelledby={id}>
      <h2 id={id}>{table.family_name}</h2>
      {bases.length > 1 && (
        <Segmented
          label="Model"
          value={table.basis}
          options={bases.map((b) => ({ value: b, label: basisLabel(b) }))}
          onChange={(b) => setQuery({ basis: b === "model_and_market" ? null : b })}
        />
      )}
      <p className="muted small hit-facts">
        {groupsText(groups)} · {seasonsText(table.seasons)} · {formatNumber(table.games, 0)} games.
        <br />
        Model <code>{table.model}</code>
        {bases.length < 2 && (
          <>
            {" "}
            <BasisBadge basis={table.basis} />
          </>
        )}
        ; baseline{" "}
        <code>{table.baseline_model}</code>.
      </p>

      <p className="muted small hit-legend" aria-hidden="true">
        <span className="hit-legend-model">Hit rate</span>
        <span className="hit-legend-base">baseline</span>
        {showCi && <span className="hit-legend-ci">95% interval</span>}
      </p>
      <TableScroll className="hit-scroll" label={`${table.family_name}: hit rates by ${uefa ? "competition" : "league"} and market`}>
        <table className="data-table hit-table">
          <caption className="visually-hidden">
            Share of games where the pick was right, by {uefa ? "competition" : "league"} and
            market, with the baseline's share on the same games
            {showCi ? " and the 95% interval" : ""}
          </caption>
          <thead>
            <tr>
              <th scope="col" className="sticky-col">
                {uefa ? "Competition" : "League"}
              </th>
              {markets.map((m) => {
                const [a, b] = MARKET_HEAD[m.key] ?? [m.key, ""];
                return (
                  <th key={m.key} scope="col" className="num" title={m.label}>
                    <span aria-hidden="true">
                      {a}
                      <span className="cell-sub">{b}</span>
                    </span>
                    <span className="visually-hidden">{m.label}</span>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {table.rows.map((r) => (
              <tr key={r.code} className={r.code === "ALL" ? "hit-all" : undefined}>
                <th scope="row" className="sticky-col">
                  {r.name}
                  <span className="cell-sub">{formatNumber(r.games, 0)} games</span>
                </th>
                {markets.map((m) => (
                  <td key={m.key} className="num">
                    <Cell cell={r.markets[m.key]} showCi={showCi} />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </TableScroll>
      {all && <LineNote all={all.markets} />}

      {all && <Confidence buckets={all.confidence} games={all.games} id={`${id}-conf`} />}

      <p className="muted small hit-source">
        Counted from the saved ledgers <code>{table.run_id}</code>
        {table.run_created_at_utc && ` (run ${formatLondon(table.run_created_at_utc)})`} and{" "}
        <code>{table.baseline_run_id}</code>. Intervals are 95% Wilson intervals for each rate.
      </p>
    </section>
  );
}

function Cell({ cell, showCi }: { cell: HitRateCell | undefined; showCi: boolean }) {
  if (!cell || cell.n === 0) return <span className="muted">—</span>;
  return (
    <>
      <span className="hit-value">
        <Rate r={cell} />
      </span>
      <span className="cell-sub hit-base">
        <span className="visually-hidden">baseline </span>
        <Rate r={cell.baseline} />
      </span>
      {showCi && (
        <span className="cell-sub hit-ci">
          <span className="visually-hidden">95% interval </span>
          {ciText(cell.ci95)}
        </span>
      )}
    </>
  );
}

/** The plain warning about the lopsided goal lines, with this family's baseline rates. */
function LineNote({ all }: { all: Record<string, HitRateCell> }) {
  const o15 = all["OU_1.5"]?.baseline;
  const o35 = all["OU_3.5"]?.baseline;
  if (!o15 || !o35) return null;
  return (
    <p className="small hit-note">
      <strong>Over/under 1.5 and 3.5 look high mostly because one side happens most of the time
      anyway</strong>: always picking the usual outcome scores {pct(o15)} and {pct(o35)} on them
      here. Judge every rate against the baseline beneath it.
    </p>
  );
}

const CONF_LABEL: Record<string, (t: number) => string> = {
  "1X2": (t) => `Result, favourite ${Math.round(t * 100)}% or more`,
  "OU_2.5": (t) => `O/U 2.5, ${Math.round(t * 100)}% or more either way`,
  BTTS: (t) => `BTTS, ${Math.round(t * 100)}% or more either way`,
};

function Confidence({
  buckets,
  games,
  id,
}: {
  buckets: Record<string, ConfidenceBucket[]>;
  games: number;
  id: string;
}) {
  const rows = Object.entries(buckets).flatMap(([m, bs]) => bs.map((b) => ({ m, b })));
  if (rows.length === 0) return null;
  return (
    <div className="hit-conf">
      <h3 id={id}>When the model was confident</h3>
      <p className="muted small">
        The same {formatNumber(games, 0)} games, kept only where the model's pick had at least the
        probability shown.
      </p>
      <TableScroll className="hit-scroll" label="Hit rate when the model was confident">
        <table className="data-table hit-conf-table" aria-labelledby={id}>
          <thead>
            <tr>
              <th scope="col" className="sticky-col">
                Games where
              </th>
              <th scope="col" className="num">
                Hit rate
              </th>
              <th scope="col" className="num">
                Games
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map(({ m, b }) => (
              <tr key={`${m}-${b.threshold}`}>
                <th scope="row" className="sticky-col">
                  {CONF_LABEL[m]?.(b.threshold) ?? `${m}, ${Math.round(b.threshold * 100)}% or more`}
                </th>
                <td className="num">
                  <span className="hit-value">{formatPct(b.hit_rate, 1)}</span>
                  <span className="cell-sub">{ciText(b.ci95)}</span>
                </td>
                <td className="num">
                  {formatNumber(b.games, 0)}
                  <span className="cell-sub">{formatPct(b.share, 0)} of games</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </TableScroll>
      <p className="muted small">
        Beneath each hit rate is its 95% interval. The surer the model, the more often it was
        right, but the fewer games there are.
      </p>
    </div>
  );
}

// ---------------------------------------------------------------------------------------------
// The genuine pre-match record

function PrematchCard({ pre, markets }: { pre: HitRates["prematch"]; markets: HitRates["markets"] }) {
  return (
    <section className="card hit-prematch" aria-labelledby="hits-prematch">
      <h2 id="hits-prematch">Genuine pre-match record</h2>
      {pre.settled === 0 || pre.tables.length === 0 ? (
        <p>
          <strong>None settled yet.</strong>{" "}
          {pre.published > 0
            ? `${formatNumber(pre.published, 0)} ${pre.published === 1 ? "fixture has" : "fixtures have"} a prediction saved before kickoff; their hit rates appear here as the matches are played and settled.`
            : "Hit rates from predictions saved before kickoff appear here as the matches are played and settled."}{" "}
          The full record is on the <a href={href("/performance")}>Performance</a> page.
        </p>
      ) : (
        <>
          <p className="muted small">
            Predictions saved before kickoff, each fixture once (its record of record, first
            edition): {formatNumber(pre.settled, 0)} settled of {formatNumber(pre.published, 0)}
            {pre.last_settled_at_utc && `, last settled ${formatLondon(pre.last_settled_at_utc)}`}.
            Live records have no baseline. The full record is on the{" "}
            <a href={href("/performance")}>Performance</a> page.
          </p>
          {pre.tables.map((t) => (
            <PrematchTable key={t.family} t={t} markets={markets} />
          ))}
        </>
      )}
    </section>
  );
}

function PrematchTable({ t, markets }: { t: PrematchHitTable; markets: HitRates["markets"] }) {
  const models = Object.entries(t.models);
  return (
    <div className="hit-prematch-family">
      <h3>{t.family_name}</h3>
      <p className="muted small">
        {formatNumber(t.games, 0)} settled {t.games === 1 ? "fixture" : "fixtures"} ·{" "}
        {models.map(([m, n], i) => (
          <span key={m}>
            {i > 0 && ", "}
            <code>{m}</code> {formatNumber(n, 0)}
          </span>
        ))}
      </p>
      <p className="muted small hit-legend" aria-hidden="true">
        <span className="hit-legend-model">Hit rate</span>
        <span className="hit-legend-base">right of settled</span>
      </p>
      <TableScroll className="hit-scroll" label={`${t.family_name}: pre-match hit rates`}>
        <table className="data-table hit-table">
          <thead>
            <tr>
              <th scope="col" className="sticky-col">
                {t.family === "uefa" ? "Competition" : "League"}
              </th>
              {markets.map((m) => {
                const [a, b] = MARKET_HEAD[m.key] ?? [m.key, ""];
                return (
                  <th key={m.key} scope="col" className="num" title={m.label}>
                    <span aria-hidden="true">
                      {a}
                      <span className="cell-sub">{b}</span>
                    </span>
                    <span className="visually-hidden">{m.label}</span>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {t.rows.map((r) => (
              <tr key={r.code} className={r.code === "ALL" ? "hit-all" : undefined}>
                <th scope="row" className="sticky-col">
                  {r.name}
                  <span className="cell-sub">{formatNumber(r.games, 0)} settled</span>
                </th>
                {markets.map((m) => {
                  const x = r.markets[m.key];
                  return (
                    <td key={m.key} className="num">
                      {x && x.n > 0 ? (
                        <>
                          <span className="hit-value">
                            <Rate r={x} />
                          </span>
                          <span className="cell-sub">
                            {formatNumber(x.correct, 0)} of {formatNumber(x.n, 0)}
                          </span>
                        </>
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </TableScroll>
    </div>
  );
}
