// Small inline-SVG helpers: a sparkline, W/D/L form pips and the navigation icons.
// No chart library: each is a few elements drawn from the numbers passed in.

import type { ReactNode } from "react";

export type SparkPoint = { value: number; label?: string };

/**
 * A single-series line, oldest on the left. `baseline` (e.g. 0) draws a hairline at that
 * value. The last point gets an end dot. Each point carries its label as a hover title.
 */
export function Sparkline({
  points,
  width = 120,
  height = 32,
  baseline,
  label,
}: {
  points: SparkPoint[];
  width?: number;
  height?: number;
  baseline?: number;
  /** Accessible description of what the line shows. */
  label: string;
}) {
  if (points.length === 0) return null;
  const pad = 5;
  const values = points.map((p) => p.value);
  if (baseline !== undefined) values.push(baseline);
  let lo = Math.min(...values);
  let hi = Math.max(...values);
  if (lo === hi) {
    lo -= 1;
    hi += 1;
  }
  const x = (i: number) =>
    points.length === 1 ? width / 2 : pad + (i * (width - 2 * pad)) / (points.length - 1);
  const y = (v: number) => pad + ((hi - v) * (height - 2 * pad)) / (hi - lo);
  const path = points.map((p, i) => `${i === 0 ? "M" : "L"}${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join(" ");
  const last = points.length - 1;
  const lastPoint = points[last];
  return (
    <svg
      className="sparkline"
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      role="img"
      aria-label={label}
    >
      {baseline !== undefined && (
        <line className="spark-base" x1={0} x2={width} y1={y(baseline)} y2={y(baseline)} />
      )}
      <path className="spark-line" d={path} />
      {points.map((p, i) => (
        <circle key={i} className="spark-hit" cx={x(i)} cy={y(p.value)} r={8}>
          {p.label && <title>{p.label}</title>}
        </circle>
      ))}
      {lastPoint && <circle className="spark-dot" cx={x(last)} cy={y(lastPoint.value)} r={4} />}
    </svg>
  );
}

const RESULT_NAMES = { W: "win", D: "draw", L: "loss" } as const;

/** Results as lettered pips, oldest first. The letter is the label; colour only supports it. */
export function FormPips({ results }: { results: ("W" | "D" | "L")[] }) {
  if (results.length === 0) return null;
  return (
    <span
      className="form-pips"
      role="img"
      aria-label={`Form, oldest first: ${results.map((r) => RESULT_NAMES[r]).join(", ")}`}
    >
      {results.map((r, i) => (
        <span key={i} className={`pip pip-${r}`} aria-hidden="true">
          {r}
        </span>
      ))}
    </span>
  );
}

export type IconName = "fixtures" | "past" | "performance" | "lab" | "data" | "dot";

const ICONS: Record<IconName, ReactNode> = {
  fixtures: (
    <>
      <rect x="3.5" y="5" width="17" height="15" rx="2" />
      <path d="M3.5 9.5h17M8 3v4M16 3v4" />
    </>
  ),
  past: (
    <>
      <path d="M4 12a8 8 0 1 0 2.3-5.6" />
      <path d="M4 4v4h4M12 8v4.5l3 2" />
    </>
  ),
  performance: (
    <>
      <path d="M4 20V10M10 20V4M16 20v-7M21 20H3" />
    </>
  ),
  lab: (
    <>
      <path d="M9 3h6M10 3v6l-5.5 9.5A1.7 1.7 0 0 0 6 21h12a1.7 1.7 0 0 0 1.5-2.5L14 9V3" />
      <path d="M7.5 15h9" />
    </>
  ),
  data: (
    <>
      <ellipse cx="12" cy="5.5" rx="7.5" ry="2.5" />
      <path d="M4.5 5.5v13c0 1.4 3.4 2.5 7.5 2.5s7.5-1.1 7.5-2.5v-13M4.5 12c0 1.4 3.4 2.5 7.5 2.5s7.5-1.1 7.5-2.5" />
    </>
  ),
  dot: <circle cx="12" cy="12" r="4" />,
};

/** The wordmark's emblem: a centre circle and halfway line, in chalk with a floodlit spot. */
export function BrandMark({ size = 26 }: { size?: number }) {
  return (
    <svg className="brand-mark" width={size} height={size} viewBox="0 0 28 28" aria-hidden="true" focusable="false">
      <rect className="brand-mark-pitch" x="1" y="1" width="26" height="26" rx="7" />
      <path className="brand-mark-line" d="M14 3.5v21" />
      <circle className="brand-mark-line" cx="14" cy="14" r="6.2" />
      <circle className="brand-mark-spot" cx="14" cy="14" r="2.1" />
    </svg>
  );
}

export function Icon({ name, size = 22 }: { name: IconName; size?: number }) {
  return (
    <svg
      className="icon"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {ICONS[name]}
    </svg>
  );
}
