// The lab's Machine learning tab (D-028): what the learners are, in plain words; the honest
// verdict and why; the month-by-month learning curve of each learner against the model in use;
// and the validation and test comparison with its intervals.
//
// Every number is read from /api/ml/summary and the two saved reports
// (/api/reports/ml_learning_curve, /api/reports/ml_selection); docs/research/machine-learning.md
// has the full write-up. Lower composite scores are better; the frequency baseline scores 1.

import { useState } from "react";
import {
  endpoints,
  useApi,
  type MlCandidate,
  type MlCurveSplit,
  type MlLearningCurve,
  type MlSelection,
  type MlSelectionRow,
  type MlSummary,
} from "../api";
import { Badge } from "../components/Badge";
import { CiStrip } from "../components/CiStrip";
import { Segmented, SelectField, TableScroll } from "../components/Controls";
import { LineChart, type LineBand, type LinePoint } from "../components/LineChart";
import { ApiView, Empty, ErrorView, Loading } from "../components/StateViews";
import { formatLondon, formatNumber, formatPct } from "../format";

/** Plain names for the four learners; an unknown one is shown by its id. */
const LEARNERS: Record<string, string> = {
  ml_online_market: "Online mixture, with prices",
  ml_stack_market: "Learned correction, with prices",
  ml_online_pure: "Online mixture, no prices",
  ml_stack_pure: "Learned correction, no prices",
};

function learnerName(id: string): string {
  return LEARNERS[id] ?? id;
}

const SPLITS: Record<string, string> = { validation: "Validation 2023/24", test: "Test 2024/25 + 2025/26" };
const SPLITS_SHORT: Record<string, string> = { validation: "Validation", test: "Test" };

const fmt = (v: number | null | undefined, digits = 4) =>
  v === null || v === undefined || !Number.isFinite(v) ? "—" : formatNumber(v, digits);

/** "+0.00028", "−0.00033". */
function diff(v: number, digits = 5): string {
  const s = formatNumber(Math.abs(v), digits);
  return v > 0 ? `+${s}` : v < 0 ? `−${s}` : s;
}

const monthFmt = new Intl.DateTimeFormat("en-GB", { month: "short", year: "2-digit", timeZone: "UTC" });
const monthLong = new Intl.DateTimeFormat("en-GB", { month: "long", year: "numeric", timeZone: "UTC" });
const monthOf = (ym: string, long = false) => {
  const d = new Date(`${ym}-15T12:00:00Z`);
  return Number.isNaN(d.getTime()) ? ym : (long ? monthLong : monthFmt).format(d);
};

export function MachineLearningPanel() {
  const summary = useApi(endpoints.mlSummary(), { freshMs: 600_000 });
  return (
    <>
      <Intro />
      <ApiView result={summary} what="the machine-learning summary" loadingLines={2}>
        {(s) => (
          <>
            <Verdict s={s} />
            <Curve s={s} />
            <Candidates s={s} />
            <MetricsTable />
          </>
        )}
      </ApiView>
    </>
  );
}

function Intro() {
  return (
    <section className="card notice mlx-intro" aria-labelledby="ml-what">
      <h2 id="ml-what">Models that learn from the models</h2>
      <p>
        Every model here already learns: before each round of predictions it is fitted again on
        every result so far. The four <strong>learners</strong> go a step further. They watch the
        other models' track record (which one has been right more often lately, and by how much)
        and keep learning after every result.
      </p>
      <ul className="facts mlx-kinds">
        <li>
          <strong>Online mixture.</strong> Blends the models' score forecasts, and after every match
          moves weight towards whichever has been doing better.
        </li>
        <li>
          <strong>Learned correction.</strong> Learns a small, cautious adjustment to the model in
          use from how its past forecasts turned out.
        </li>
      </ul>
      <p>
        Each comes in two versions: <em>with prices</em> (for fixtures with bookmakers' prices,
        against the model in use there) and <em>without</em>. They were tuned on 2021/22 and
        2022/23 only, checked once on 2023/24 (validation), and the best was run once on 2024/25
        and 2025/26 (test). A learner replaces a model in use only if it is also at least as good
        on test.
      </p>
    </section>
  );
}

function Verdict({ s }: { s: MlSummary }) {
  const noSwitch = s.switch_to.length === 0;
  return (
    <section className="card mlx-verdict" aria-labelledby="ml-verdict">
      <p className="eyebrow">
        Verdict · {s.decision} {s.status === "proposed" ? "(proposed)" : ""}
      </p>
      <h2 id="ml-verdict" className="mlx-verdict-title">
        {noSwitch ? "No switch yet" : `Switch to ${s.switch_to.map(learnerName).join(", ")}`}
      </h2>
      <p className="mlx-verdict-lede">
        {noSwitch ? (
          <>
            The models in use stay: <code>{s.models_of_record.with_prices}</code> where a fixture has
            prices, <code>{s.models_of_record.without_prices}</code> where it hasn't.
          </>
        ) : (
          <>The rule's conditions were met on validation and on test.</>
        )}
      </p>
      <h3>Why</h3>
      <ul className="facts">
        {s.why.map((w) => (
          <li key={w}>{w}</li>
        ))}
      </ul>
      <p className="muted small">
        The rule: {s.rule}. Lower is better.
        {s.report_generated_utc && <> Summary made {formatLondon(s.report_generated_utc)} UK time.</>}
      </p>
    </section>
  );
}

// ---------------------------------------------------------------------------------------------
// Learning curve

type View = "monthly" | "cumulative";

function Curve({ s }: { s: MlSummary }) {
  const curve = useApi(endpoints.report<MlLearningCurve>("ml_learning_curve"), { freshMs: 600_000 });
  const panels = curve.data?.panels ?? [];
  const ids = s.candidates.map((c) => c.model).filter((m) => panels.some((p) => p.candidate === m));
  const [picked, setPicked] = useState<string | null>(null);
  const [view, setView] = useState<View>("monthly");
  const id = picked && ids.includes(picked) ? picked : (ids[0] ?? null);
  const panel = panels.find((p) => p.candidate === id);

  return (
    <section className="card mlx-curve" aria-labelledby="ml-curve">
      <h2 id="ml-curve">Learning curve, month by month</h2>
      <p className="muted small">
        Each learner against the model in use, on the same fixtures, five leagues together. Lower
        is better; the frequency baseline scores 1.
      </p>
      {curve.data === undefined ? (
        curve.error ? (
          <ErrorView error={curve.error} what="the learning curve" onRetry={curve.reload} retrying={curve.loading} />
        ) : (
          <Loading label="Loading the learning curve…" lines={1} />
        )
      ) : !panel || !id ? (
        <Empty title="No learning curve saved">
          <p>The report appears once the learners' month-by-month scores have been written.</p>
        </Empty>
      ) : (
        <>
          <div className="mlx-controls">
            <SelectField
              id="ml-learner"
              label="Learner"
              value={id}
              options={ids.map((m) => ({ value: m, label: learnerName(m) }))}
              onChange={setPicked}
            />
            <Segmented
              label="Score"
              value={view}
              options={[
                { value: "monthly", label: "Each month" },
                { value: "cumulative", label: "Running total" },
              ]}
              onChange={setView}
            />
          </div>
          <CurveChart candidate={id} target={panel.target} splits={panel.splits} view={view} />
          {curve.data.note && <p className="muted small">{curve.data.note}</p>}
        </>
      )}
    </section>
  );
}

function CurveChart({
  candidate,
  target,
  splits,
  view,
}: {
  candidate: string;
  target: string;
  splits: Record<string, MlCurveSplit>;
  view: View;
}) {
  // Validation then test along one axis, a one-month gap between them. Test months are drawn
  // only where the learner had its test run.
  const order = ["validation", "test"].filter((k) => splits[k]?.models[candidate]);
  const learner: LinePoint[] = [];
  const inUse: LinePoint[] = [];
  const bands: LineBand[] = [];
  const ticks: { x: number; label: string }[] = [];
  const rows: { split: string; month: string; fixtures: number; a: number | null; b: number | null }[] = [];
  const labels = new Map<number, string>();
  let x = 0;
  for (const key of order) {
    const sp = splits[key];
    if (!sp) continue;
    const a = sp.models[candidate]?.[view] ?? [];
    const b = sp.models[target]?.[view] ?? [];
    const x0 = x;
    sp.months.forEach((m, i) => {
      const n = sp.fixtures[i] ?? 0;
      learner.push({ x, y: a[i] ?? null, label: `${n} fixtures` });
      inUse.push({ x, y: b[i] ?? null });
      labels.set(x, monthOf(m, true));
      if (i % 3 === 0) ticks.push({ x, label: monthOf(m) });
      rows.push({ split: key, month: m, fixtures: n, a: a[i] ?? null, b: b[i] ?? null });
      x += 1;
    });
    bands.push({ x0: x0 - 0.5, x1: x - 0.5, label: SPLITS[key] ?? key, short: SPLITS_SHORT[key] });
    x += 1;
  }
  const untested = !splits.test?.models[candidate];
  return (
    <>
      <LineChart
        series={[
          { key: "learner", label: learnerName(candidate), tone: "accent", points: learner, dots: true },
          { key: "target", label: `${target} (in use)`, tone: "target", points: inUse },
        ]}
        xTicks={ticks}
        xLabel={(v) => labels.get(v) ?? ""}
        yFormat={(v) => formatNumber(v, 3)}
        bands={bands}
        height={250}
        label={`${view === "monthly" ? "Monthly" : "Running"} composite score of ${learnerName(candidate)} and ${target}, ${order.map((k) => SPLITS[k]).join(" and ")}. The numbers are in the table below.`}
      />
      <ul className="lc-legend">
        <li className="lc-s-accent">
          <span className="lc-legend-key lc-s-accent" aria-hidden="true" />
          {learnerName(candidate)} <code>{candidate}</code>
        </li>
        <li className="lc-s-target">
          <span className="lc-legend-key lc-s-target" aria-hidden="true" />
          In use: <code>{target}</code>
        </li>
      </ul>
      {untested && (
        <p className="note-line">
          No test months: only the learner with the lowest validation score that also beat its
          model was run on test (D-025).
        </p>
      )}
      <details className="cal-table">
        <summary>The numbers, month by month</summary>
        <TableScroll label="Composite score by month">
          <table className="data-table">
            <thead>
              <tr>
                <th scope="col">Month</th>
                <th scope="col" className="num">
                  Fixtures
                </th>
                <th scope="col" className="num">
                  Learner
                </th>
                <th scope="col" className="num">
                  In use
                </th>
                <th scope="col" className="num">
                  Difference
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => (
                <tr key={`${r.split}-${r.month}`} className={i > 0 && rows[i - 1]?.split !== r.split ? "group-start" : undefined}>
                  <th scope="row">
                    {monthOf(r.month, true)}
                    {(i === 0 || rows[i - 1]?.split !== r.split) && <span className="cell-sub">{SPLITS[r.split]}</span>}
                  </th>
                  <td className="num">{r.fixtures}</td>
                  <td className="num">{fmt(r.a, 4)}</td>
                  <td className="num">{fmt(r.b, 4)}</td>
                  <td className="num">{r.a !== null && r.b !== null ? diff(r.a - r.b, 4) : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </TableScroll>
        <p className="muted small">A negative difference means the learner did better that month.</p>
      </details>
    </>
  );
}

// ---------------------------------------------------------------------------------------------
// Validation and test

function Candidates({ s }: { s: MlSummary }) {
  return (
    <section className="card mlx-cands" aria-labelledby="ml-cands">
      <h2 id="ml-cands">Validation and test</h2>
      <p className="muted small">
        Composite score against the model in use, with the 95% interval of the difference (block
        bootstrap over decision batches). Left of zero, the learner was better.
      </p>
      <ol className="mlx-list">
        {s.candidates.map((c) => (
          <CandidateRow key={c.model} c={c} />
        ))}
      </ol>
    </section>
  );
}

function Split({
  title,
  learner,
  target,
  fixtures,
  difference,
  ci,
  good,
  goodText,
  badText,
}: {
  title: string;
  learner: number;
  target: number;
  fixtures: number;
  difference: number;
  ci: [number, number];
  good: boolean;
  goodText: string;
  badText: string;
}) {
  return (
    <div className="mlx-split">
      <p className="mlx-split-head">
        <strong>{title}</strong>
        <span className="muted small">{formatNumber(fixtures, 0)} fixtures</span>
      </p>
      <dl className="mlx-nums">
        <div>
          <dt>Learner</dt>
          <dd>{fmt(learner, 5)}</dd>
        </div>
        <div>
          <dt>In use</dt>
          <dd>{fmt(target, 5)}</dd>
        </div>
        <div>
          <dt>Difference</dt>
          <dd>
            {diff(difference)}
            <span className="cell-sub">
              {diff(ci[0])} to {diff(ci[1])}
            </span>
          </dd>
        </div>
      </dl>
      <CiStrip value={difference} lo={ci[0]} hi={ci[1]} digits={5} label={`${title}: learner minus model in use`} better={["learner better", "in use better"]} />
      <p className={`mlx-outcome ${good ? "good" : "bad"}`}>
        <span aria-hidden="true">{good ? "✓" : "✗"}</span> {good ? goodText : badText}
      </p>
    </div>
  );
}

function CandidateRow({ c }: { c: MlCandidate }) {
  const v = c.validation;
  const t = c.test;
  return (
    <li className="mlx-cand">
      <div className="mlx-cand-head">
        <h3>{learnerName(c.model)}</h3>
        <span className="badges">
          <Badge tone={c.fixtures_with_prices ? "market" : "neutral"}>
            {c.fixtures_with_prices ? "Fixtures with prices" : "Fixtures without prices"}
          </Badge>
        </span>
        <p className="muted small">
          <code>{c.model}</code> · {c.what} · against <code>{c.target}</code>
        </p>
      </div>
      <div className="mlx-splits">
        <Split
          title={SPLITS.validation ?? "Validation"}
          learner={v.composite}
          target={v.target_composite}
          fixtures={v.fixtures}
          difference={v.difference}
          ci={v.difference_ci95}
          good={v.beat_target}
          goodText="Better than the model in use"
          badText="Worse than the model in use"
        />
        {t.run ? (
          <Split
            title={SPLITS.test ?? "Test"}
            learner={t.composite}
            target={t.target_composite}
            fixtures={t.fixtures}
            difference={t.difference}
            ci={t.difference_ci95}
            good={t.at_least_as_good}
            goodText="At least as good as the model in use"
            badText="Not as good as the model in use"
          />
        ) : (
          <div className="mlx-split mlx-split-none">
            <p className="mlx-split-head">
              <strong>{SPLITS.test}</strong>
            </p>
            <p className="muted small">Not run.</p>
          </div>
        )}
      </div>
      <p className="mlx-cand-outcome">
        <strong>Outcome:</strong> {c.outcome}
      </p>
    </li>
  );
}

// ---------------------------------------------------------------------------------------------
// Every metric

const METRICS: { key: string; label: string; pct?: boolean }[] = [
  { key: "rps", label: "RPS" },
  { key: "ll_ou_1.5", label: "LL O/U 1.5" },
  { key: "ll_ou_2.5", label: "LL O/U 2.5" },
  { key: "ll_ou_3.5", label: "LL O/U 3.5" },
  { key: "ll_btts", label: "LL BTTS" },
  { key: "composite", label: "Composite" },
  { key: "acc_1x2", label: "Right 1X2", pct: true },
];

function MetricsTable() {
  const sel = useApi(endpoints.report<MlSelection>("ml_selection"), { freshMs: 600_000 });
  const tables = [
    { key: "validation", title: SPLITS.validation ?? "Validation", rows: sel.data?.validation_2324?.table ?? [] },
    { key: "test", title: SPLITS.test ?? "Test", rows: sel.data?.test_2425_2526?.table ?? [] },
  ].filter((t) => t.rows.length > 0);
  if (sel.data === undefined) return null;
  if (tables.length === 0) return null;
  const num = (r: MlSelectionRow, k: string) => (typeof r[k] === "number" ? (r[k] as number) : null);
  return (
    <details className="card details mlx-metrics">
      <summary>Every metric, validation and test</summary>
      {tables.map((t) => (
        <div key={t.key} className="mlx-metric-table">
          <h3>{t.title}</h3>
          <TableScroll label={`Metrics, ${t.title}`}>
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Model</th>
                  <th scope="col" className="num">
                    Fixtures
                  </th>
                  {METRICS.map((m) => (
                    <th key={m.key} scope="col" className="num">
                      {m.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {t.rows.map((r) => (
                  <tr key={r.model}>
                    <th scope="row">
                      <code>{r.model}</code>
                    </th>
                    <td className="num">{formatNumber(r.fixtures, 0)}</td>
                    {METRICS.map((m) => {
                      const v = num(r, m.key);
                      return (
                        <td key={m.key} className="num">
                          {m.pct ? formatPct(v, 1) : fmt(v, 4)}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </TableScroll>
        </div>
      ))}
      <p className="muted small">
        RPS: ranked probability score of the match result. LL: log loss. Lower is better for both;
        the composite averages them, each divided by the frequency baseline's.
      </p>
    </details>
  );
}
