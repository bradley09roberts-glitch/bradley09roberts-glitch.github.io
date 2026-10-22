// A line chart in inline SVG, no library: one or more series over a numeric x axis (days or
// month indexes), with "nice" y ticks, labelled x ticks, optional shaded bands (e.g. the
// validation and test seasons), markers (e.g. a peak) and a zero line. Hovering or touching
// the plot shows every series' value at the nearest x in a tooltip; the caller prints the same
// numbers as text, so nothing is shown by the chart alone.

import { useLayoutEffect, useRef, useState, type CSSProperties, type PointerEvent, type RefObject } from "react";
import "./chart.css";

export type LinePoint = { x: number; y: number | null; label?: string };

export type LineSeries = {
  key: string;
  label: string;
  /** Colour token: "accent" (grass), "target" (grey, dashed), "home", "away". */
  tone: "accent" | "target" | "home" | "away";
  points: LinePoint[];
  /** Draw a dot on every point (for short series). */
  dots?: boolean;
};

export type LineMarker = { x: number; y: number; label: string };
/** A shaded stretch of x (e.g. a season), labelled `label`, or `short` where that won't fit. */
export type LineBand = { x0: number; x1: number; label: string; short?: string };

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

/** About `count` round tick values covering [lo, hi]. */
export function niceTicks(lo: number, hi: number, count = 4): number[] {
  if (!(hi > lo)) return [lo];
  const raw = (hi - lo) / count;
  const mag = 10 ** Math.floor(Math.log10(raw));
  const step = ([1, 2, 2.5, 5, 10].find((m) => m * mag >= raw) ?? 10) * mag;
  const out: number[] = [];
  for (let v = Math.ceil(lo / step) * step; v <= hi + step * 1e-9; v += step) out.push(Number(v.toFixed(10)));
  return out;
}

const M = { top: 14, right: 14, bottom: 30 };

export function LineChart({
  series,
  xTicks,
  xLabel,
  yFormat,
  label,
  height = 220,
  markers = [],
  bands = [],
  yPad = 0.08,
  zero = false,
}: {
  series: LineSeries[];
  /** Tick positions and their labels along the x axis. */
  xTicks: { x: number; label: string }[];
  /** The tooltip heading for an x value, e.g. a date. */
  xLabel: (x: number) => string;
  yFormat: (y: number) => string;
  /** Accessible summary of what the chart shows (the numbers are also printed nearby). */
  label: string;
  height?: number;
  markers?: LineMarker[];
  bands?: LineBand[];
  /** Headroom above and below the data, as a share of its range. */
  yPad?: number;
  /** Draw a firm line at y = 0 (and keep 0 in range), for a series of differences. */
  zero?: boolean;
}) {
  const [ref, measured] = useWidth();
  const [hoverX, setHoverX] = useState<number | null>(null);
  const width = Math.max(280, measured || 320);

  const all = series.flatMap((s) => s.points.filter((p) => p.y !== null));
  const xs = series.flatMap((s) => s.points.map((p) => p.x));
  if (all.length === 0 || xs.length === 0) return null;
  const xMin = Math.min(...xs, ...bands.map((b) => b.x0));
  const xMax = Math.max(...xs, ...bands.map((b) => b.x1));
  const ys = [...all.map((p) => p.y as number), ...markers.map((m) => m.y), ...(zero ? [0] : [])];
  let lo = Math.min(...ys);
  let hi = Math.max(...ys);
  const pad = (hi - lo || Math.abs(hi) || 1) * yPad;
  lo -= pad;
  hi += pad;
  const ticks = niceTicks(lo, hi, height < 200 ? 3 : 4);
  lo = Math.min(lo, ticks[0] ?? lo);
  hi = Math.max(hi, ticks.at(-1) ?? hi);

  const labelW = Math.max(...ticks.map((t) => yFormat(t).length)) * 6.6 + 10;
  const left = Math.max(34, labelW);
  const plotW = width - left - M.right;
  const plotH = height - M.top - M.bottom;
  const x = (v: number) => left + (xMax === xMin ? plotW / 2 : ((v - xMin) / (xMax - xMin)) * plotW);
  const y = (v: number) => M.top + ((hi - v) / (hi - lo)) * plotH;

  // x labels that would touch the one before are left out.
  const shownTicks: { x: number; label: string }[] = [];
  let lastRight = -Infinity;
  for (const t of xTicks) {
    const half = (t.label.length * 6.2) / 2;
    const px = x(t.x);
    if (px - half < lastRight + 8 || px - half < 0 || px + half > width) continue;
    shownTicks.push(t);
    lastRight = px + half;
  }

  const path = (pts: LinePoint[]) => {
    let d = "";
    let pen = false;
    for (const p of pts) {
      if (p.y === null) {
        pen = false;
        continue;
      }
      d += `${pen ? "L" : "M"}${x(p.x).toFixed(1)},${y(p.y).toFixed(1)} `;
      pen = true;
    }
    return d.trim();
  };

  // Nearest x with data, for the tooltip.
  const uniqueXs = [...new Set(series.flatMap((s) => s.points.filter((p) => p.y !== null).map((p) => p.x)))].sort(
    (a, b) => a - b,
  );
  const onMove = (e: PointerEvent<SVGRectElement>) => {
    const box = e.currentTarget.getBoundingClientRect();
    const px = ((e.clientX - box.left) / box.width) * plotW + left;
    let best: number | null = null;
    for (const v of uniqueXs) if (best === null || Math.abs(x(v) - px) < Math.abs(x(best) - px)) best = v;
    setHoverX(best);
  };

  let tip: { style: CSSProperties; rows: { s: LineSeries; p: LinePoint }[] } | null = null;
  if (hoverX !== null) {
    const rows = series
      .map((s) => ({ s, p: s.points.find((p) => p.x === hoverX && p.y !== null) }))
      .filter((r): r is { s: LineSeries; p: LinePoint } => r.p !== undefined);
    const px = x(hoverX);
    const flip = px > width * 0.6;
    tip = {
      rows,
      style: flip ? { right: width - px + 10, top: M.top } : { left: px + 10, top: M.top },
    };
  }

  return (
    <div className="lc" ref={ref}>
      <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-label={label}>
        {bands.map((b) => {
          const w = Math.max(0, x(b.x1) - x(b.x0));
          const fits = (t: string) => t.length * 7 + 12 <= w;
          const text = fits(b.label) ? b.label : b.short && fits(b.short) ? b.short : null;
          return (
            <g key={b.label} className="lc-band">
              <rect x={x(b.x0)} y={M.top} width={w} height={plotH} />
              {text && (
                <text x={x(b.x0) + 6} y={M.top + 13} className="lc-band-label">
                  {text}
                </text>
              )}
            </g>
          );
        })}
        <g className="cal-grid">
          {ticks.map((t) => (
            <g key={t}>
              <line x1={left} x2={left + plotW} y1={y(t)} y2={y(t)} />
              <text x={left - 6} y={y(t) + 4} textAnchor="end" className="cal-tick">
                {yFormat(t)}
              </text>
            </g>
          ))}
          <line className="cal-axis" x1={left} x2={left + plotW} y1={M.top + plotH} y2={M.top + plotH} />
          {zero && <line className="lc-zero" x1={left} x2={left + plotW} y1={y(0)} y2={y(0)} />}
        </g>
        {shownTicks.map((t) => (
          <text key={t.x} x={x(t.x)} y={height - 9} textAnchor="middle" className="cal-tick">
            {t.label}
          </text>
        ))}
        {series.map((s) => (
          <g key={s.key} className={`lc-s lc-s-${s.tone}`}>
            <path className="lc-line" d={path(s.points)} />
            {s.dots &&
              s.points.map((p, i) =>
                p.y === null ? null : <circle key={i} className="lc-dot" cx={x(p.x)} cy={y(p.y)} r={3} />,
              )}
          </g>
        ))}
        {markers.map((m) => (
          <g key={m.label} className="lc-marker">
            <circle cx={x(m.x)} cy={y(m.y)} r={4.5} />
          </g>
        ))}
        {series.map((s) => {
          const last = [...s.points].reverse().find((p) => p.y !== null);
          return last && last.y !== null ? (
            <circle key={s.key} className={`lc-end lc-s-${s.tone}`} cx={x(last.x)} cy={y(last.y)} r={4} />
          ) : null;
        })}
        {hoverX !== null && (
          <line className="lc-hover" x1={x(hoverX)} x2={x(hoverX)} y1={M.top} y2={M.top + plotH} />
        )}
        {tip?.rows.map(({ s, p }) =>
          p.y === null ? null : (
            <circle key={s.key} className={`lc-hover-dot lc-s-${s.tone}`} cx={x(p.x)} cy={y(p.y)} r={4.5} />
          ),
        )}
        <rect
          className="lc-hit"
          x={left}
          y={M.top}
          width={plotW}
          height={plotH}
          onPointerMove={onMove}
          onPointerDown={onMove}
          onPointerLeave={() => setHoverX(null)}
        />
      </svg>
      {tip && hoverX !== null && tip.rows.length > 0 && (
        <div className="lc-tip" style={tip.style} aria-hidden="true">
          <strong>{xLabel(hoverX)}</strong>
          {tip.rows.map(({ s, p }) => (
            <span key={s.key} className="lc-tip-row">
              <span className={`lc-tip-key lc-s-${s.tone}`} />
              {series.length > 1 && <span className="lc-tip-name">{s.label}</span>}
              <span className="lc-tip-value">{p.y === null ? "—" : yFormat(p.y)}</span>
              {p.label && <span className="lc-tip-note">{p.label}</span>}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
