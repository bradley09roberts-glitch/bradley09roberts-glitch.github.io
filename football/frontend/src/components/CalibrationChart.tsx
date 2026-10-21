// Reliability diagram in inline SVG: mean predicted probability (x) against the observed
// frequency (y) per bin, with the diagonal for perfect calibration and, underneath, the
// number of fixtures in each bin. Every value is also in the table under the chart.
//
// Two things are worked out here from the API's own bin numbers (n, mean_predicted,
// observed), and labelled as such: a 95% Wilson interval for each bin's observed rate, and
// the fixture-weighted average gap between observed and predicted.

import { useLayoutEffect, useRef, useState, type CSSProperties, type RefObject } from "react";
import type { CalibrationBin } from "../api";
import { formatPct } from "../format";
import "./chart.css";

export type SeriesTone = "home" | "draw" | "away" | "single";

export type CalibrationSeries = {
  key: string;
  /** What the observed rate counts, e.g. "Home win" or "Over 2.5 goals". */
  label: string;
  tone: SeriesTone;
  bins: CalibrationBin[];
};

/** Bins with fewer fixtures than this are drawn hollow: their observed rate is noisy. */
export const SMALL_BIN = 20;

/** 95% Wilson score interval for an observed proportion p over n trials. */
export function wilson(p: number, n: number, z = 1.96): [number, number] {
  if (n <= 0 || !Number.isFinite(p)) return [0, 1];
  const z2 = z * z;
  const denom = 1 + z2 / n;
  const centre = (p + z2 / (2 * n)) / denom;
  const half = (z * Math.sqrt((p * (1 - p)) / n + z2 / (4 * n * n))) / denom;
  return [Math.max(0, centre - half), Math.min(1, centre + half)];
}

/** Fixture-weighted mean of |observed − predicted| over the bins, or null without bins. */
export function calibrationGap(bins: readonly CalibrationBin[]): number | null {
  let n = 0;
  let total = 0;
  for (const b of bins) {
    n += b.n;
    total += b.n * Math.abs(b.observed - b.mean_predicted);
  }
  return n > 0 ? total / n : null;
}

export function binLabel(b: CalibrationBin): string {
  return `${Math.round(b.bin_low * 100)}–${Math.round(b.bin_high * 100)}%`;
}

function compact(n: number): string {
  if (n < 1000) return String(n);
  if (n < 10_000) return `${(n / 1000).toFixed(1).replace(/\.0$/, "")}k`;
  return `${Math.round(n / 1000)}k`;
}

function points(pp: number): string {
  return `${(pp * 100).toFixed(1)} points`;
}

function useWidth(): [RefObject<HTMLDivElement | null>, number] {
  const ref = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    setWidth(Math.round(el.getBoundingClientRect().width));
    if (typeof ResizeObserver === "undefined") return;
    const ro = new ResizeObserver((entries) => {
      const e = entries[0];
      if (e) setWidth(Math.round(e.contentRect.width));
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  return [ref, width];
}

const MAX_WIDTH = 480;
const M = { left: 42, right: 12, top: 10 };
const HIST_GAP = 18;
const HIST_LABEL = 14;
const HIST_H = 44;
const AXIS_H = 38;
const TICKS = [0, 0.25, 0.5, 0.75, 1];

/** A bar with a 4px rounded top and a square base. */
function barPath(x: number, w: number, base: number, h: number): string {
  if (h <= 0 || w <= 0) return "";
  const r = Math.min(4, w / 2, h);
  const top = base - h;
  return `M${x},${base} V${top + r} Q${x},${top} ${x + r},${top} H${x + w - r} Q${x + w},${top} ${x + w},${top + r} V${base} Z`;
}

type Hover = { s: number; b: number } | null;

export function CalibrationChart({
  series,
  title,
  showIntervals = true,
}: {
  series: CalibrationSeries[];
  /** Accessible name, e.g. "Calibration: home win, draw and away win". */
  title: string;
  showIntervals?: boolean;
}) {
  const [ref, measured] = useWidth();
  const [hover, setHover] = useState<Hover>(null);
  const width = Math.max(260, Math.min(measured || 320, MAX_WIDTH));
  const plot = width - M.left - M.right;
  const histTop = M.top + plot + HIST_GAP;
  const base = histTop + HIST_LABEL + HIST_H;
  const height = base + AXIS_H;
  const x = (p: number) => M.left + p * plot;
  const y = (p: number) => M.top + (1 - p) * plot;

  const visible = series.filter((s) => s.bins.length > 0);
  const maxN = Math.max(1, ...visible.flatMap((s) => s.bins.map((b) => b.n)));
  const single = visible.length === 1;

  // Histogram: each 10%-wide bin slot holds one bar per series, separated by 2px.
  const slotOf = (b: CalibrationBin) => ({ x0: x(b.bin_low), x1: x(b.bin_high) });
  const barGeom = (b: CalibrationBin, si: number) => {
    const { x0, x1 } = slotOf(b);
    const inner = x1 - x0 - 4;
    const k = visible.length;
    const bw = Math.min(24, (inner - 2 * (k - 1)) / k);
    const groupW = bw * k + 2 * (k - 1);
    const start = x0 + 2 + (inner - groupW) / 2;
    const bx = start + si * (bw + 2);
    const h = Math.max(1, (b.n / maxN) * HIST_H);
    return { bx, bw, h };
  };

  const hovered = hover ? visible[hover.s]?.bins[hover.b] : undefined;
  const hoveredSeries = hover ? visible[hover.s] : undefined;

  let tip: { style: CSSProperties; bin: CalibrationBin; s: CalibrationSeries } | null = null;
  if (hovered && hoveredSeries) {
    const px = x(hovered.mean_predicted);
    const py = y(hovered.observed);
    const right = px > width / 2;
    tip = {
      bin: hovered,
      s: hoveredSeries,
      style: {
        top: Math.max(0, Math.min(py - 20, M.top + plot - 120)),
        ...(right ? { right: width - px + 14 } : { left: px + 14 }),
      },
    };
  }

  const describe = (s: CalibrationSeries, b: CalibrationBin) => {
    const [lo, hi] = wilson(b.observed, b.n);
    return `${s.label}, predicted ${binLabel(b)}: mean predicted ${formatPct(b.mean_predicted, 1)}, observed ${formatPct(b.observed, 1)}, ${b.n} fixtures, 95% interval ${formatPct(lo, 0)} to ${formatPct(hi, 0)}`;
  };

  return (
    <figure className="cal">
      <div className="cal-chart" ref={ref}>
        <svg
          width={width}
          height={height}
          viewBox={`0 0 ${width} ${height}`}
          role="group"
          aria-label={title}
          onPointerLeave={() => setHover(null)}
        >
          {/* Grid and axes */}
          <g className="cal-grid" aria-hidden="true">
            {TICKS.map((t) => (
              <g key={t}>
                <line x1={x(0)} x2={x(1)} y1={y(t)} y2={y(t)} />
                <line x1={x(t)} x2={x(t)} y1={y(0)} y2={y(1)} />
                <text className="cal-tick" x={M.left - 6} y={y(t)} dy="0.32em" textAnchor="end">
                  {Math.round(t * 100)}%
                </text>
                <text className="cal-tick" x={x(t)} y={base + 14} textAnchor={t === 0 ? "start" : t === 1 ? "end" : "middle"}>
                  {Math.round(t * 100)}%
                </text>
              </g>
            ))}
            <line className="cal-axis" x1={x(0)} x2={x(1)} y1={base} y2={base} />
            <text className="cal-axis-title" x={x(0.5)} y={base + 32} textAnchor="middle">
              Predicted probability
            </text>
            <text
              className="cal-axis-title"
              transform={`translate(11 ${y(0.5)}) rotate(-90)`}
              textAnchor="middle"
            >
              Observed frequency
            </text>
            <text className="cal-tick" x={M.left - 6} y={histTop + HIST_LABEL + HIST_H / 2} dy="0.32em" textAnchor="end">
              n
            </text>
          </g>

          {/* Perfect calibration */}
          <line className="cal-diagonal" x1={x(0)} y1={y(0)} x2={x(1)} y2={y(1)} aria-hidden="true" />

          {/* Fixtures per bin */}
          <g aria-hidden="true">
            {visible.map((s, si) =>
              s.bins.map((b, bi) => {
                const { bx, bw, h } = barGeom(b, si);
                const active = hover?.s === si && hover.b === bi;
                return (
                  <g key={`${s.key}-${bi}`} className={`cal-s-${s.tone}`}>
                    <path className={`cal-bar${active ? " is-active" : ""}`} d={barPath(bx, bw, base, h)} />
                    {single && bw >= 18 && (
                      <text className="cal-bar-label" x={bx + bw / 2} y={base - h - 3} textAnchor="middle">
                        {compact(b.n)}
                      </text>
                    )}
                    <rect
                      className="cal-hit"
                      x={bx - 1}
                      y={histTop}
                      width={bw + 2}
                      height={HIST_LABEL + HIST_H}
                      onPointerEnter={() => setHover({ s: si, b: bi })}
                    />
                  </g>
                );
              }),
            )}
          </g>

          {/* Series: line, intervals, points */}
          {visible.map((s, si) => {
            const sorted = [...s.bins].sort((a, b) => a.mean_predicted - b.mean_predicted);
            const d = sorted
              .map((b, i) => `${i === 0 ? "M" : "L"}${x(b.mean_predicted).toFixed(1)},${y(b.observed).toFixed(1)}`)
              .join(" ");
            return (
              <g key={s.key} className={`cal-s-${s.tone}`}>
                <path className="cal-line" d={d} aria-hidden="true" />
                {showIntervals &&
                  s.bins.map((b, bi) => {
                    const [lo, hi] = wilson(b.observed, b.n);
                    const cx = x(b.mean_predicted);
                    return (
                      <g key={bi} className="cal-ci" aria-hidden="true">
                        <line x1={cx} x2={cx} y1={y(lo)} y2={y(hi)} />
                        <line x1={cx - 3} x2={cx + 3} y1={y(lo)} y2={y(lo)} />
                        <line x1={cx - 3} x2={cx + 3} y1={y(hi)} y2={y(hi)} />
                      </g>
                    );
                  })}
                {s.bins.map((b, bi) => {
                  const active = hover?.s === si && hover.b === bi;
                  return (
                    <g key={bi}>
                      <circle
                        className={`cal-dot${b.n < SMALL_BIN ? " is-small" : ""}${active ? " is-active" : ""}`}
                        cx={x(b.mean_predicted)}
                        cy={y(b.observed)}
                        r={active ? 5.5 : 4.5}
                        aria-hidden="true"
                      />
                      <circle
                        className="cal-hit"
                        cx={x(b.mean_predicted)}
                        cy={y(b.observed)}
                        r={13}
                        tabIndex={0}
                        role="img"
                        aria-label={describe(s, b)}
                        onPointerEnter={() => setHover({ s: si, b: bi })}
                        onFocus={() => setHover({ s: si, b: bi })}
                        onBlur={() => setHover(null)}
                      />
                    </g>
                  );
                })}
              </g>
            );
          })}
        </svg>

        {tip && (
          <div className="cal-tip" style={tip.style} aria-hidden="true">
            <div className="cal-tip-head">
              <span className={`cal-key cal-s-${tip.s.tone}`} />
              {tip.s.label} · predicted {binLabel(tip.bin)}
            </div>
            <div>
              <strong>{formatPct(tip.bin.observed, 1)}</strong> observed
            </div>
            <div>
              <strong>{formatPct(tip.bin.mean_predicted, 1)}</strong> mean predicted
            </div>
            <div>
              <strong>{tip.bin.n.toLocaleString("en-GB")}</strong> fixtures
            </div>
            {(() => {
              const [lo, hi] = wilson(tip.bin.observed, tip.bin.n);
              return (
                <div className="cal-tip-sub">
                  95% interval {formatPct(lo, 0)} to {formatPct(hi, 0)}
                </div>
              );
            })()}
          </div>
        )}
      </div>

      <figcaption className="cal-legend">
        <ul>
          {visible.map((s) => {
            const gap = calibrationGap(s.bins);
            return (
              <li key={s.key}>
                <span className={`cal-key cal-s-${s.tone}`} aria-hidden="true" />
                <span>
                  {s.label}
                  {gap !== null && <span className="muted"> · average gap {points(gap)}</span>}
                </span>
              </li>
            );
          })}
          <li>
            <span className="cal-key cal-key-diagonal" aria-hidden="true" />
            <span>Perfect calibration</span>
          </li>
          <li>
            <span className="cal-key cal-key-small" aria-hidden="true" />
            <span>Hollow: fewer than {SMALL_BIN} fixtures</span>
          </li>
        </ul>
        <p className="muted small">
          Bars under the chart count the fixtures in each 10% bin.
          {showIntervals && " Whiskers are 95% intervals for the observed rate (Wilson), worked out here from each bin's count."}{" "}
          The average gap is weighted by fixtures and also worked out here from the bins.
        </p>
      </figcaption>

      <details className="cal-table">
        <summary>Show the numbers</summary>
        {visible.map((s) => (
          <div key={s.key} className="table-scroll" role="region" aria-label={`${s.label} calibration bins`} tabIndex={0}>
            <table className="data-table">
              <caption>{s.label}</caption>
              <thead>
                <tr>
                  <th scope="col">Predicted bin</th>
                  <th scope="col" className="num">
                    Fixtures
                  </th>
                  <th scope="col" className="num">
                    Mean predicted
                  </th>
                  <th scope="col" className="num">
                    Observed
                  </th>
                  <th scope="col" className="num">
                    Gap
                  </th>
                </tr>
              </thead>
              <tbody>
                {s.bins.map((b) => {
                  const gap = (b.observed - b.mean_predicted) * 100;
                  return (
                    <tr key={b.bin_low}>
                      <th scope="row">{binLabel(b)}</th>
                      <td className="num">{b.n.toLocaleString("en-GB")}</td>
                      <td className="num">{formatPct(b.mean_predicted, 1)}</td>
                      <td className="num">{formatPct(b.observed, 1)}</td>
                      <td className="num">
                        {gap > 0 ? "+" : gap < 0 ? "−" : ""}
                        {Math.abs(gap).toFixed(1)} pts
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ))}
        <p className="muted small">Gap is observed minus mean predicted, in percentage points.</p>
      </details>
    </figure>
  );
}
