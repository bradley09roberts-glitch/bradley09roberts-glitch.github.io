// One estimate and its 95% interval on an axis centred on zero (inline SVG). Left of zero the
// first-named side is better. The values are in the accessible name and printed by the caller.

import { formatNumber } from "../format";
import "./chart.css";

export function CiStrip({
  value,
  lo,
  hi,
  label,
  better = ["model better", "market better"],
  digits = 4,
}: {
  value: number;
  lo: number;
  hi: number;
  label: string;
  /** What a value left and right of zero means. */
  better?: [string, string];
  digits?: number;
}) {
  const w = 320;
  const h = 40;
  const pad = 10;
  const extent = Math.max(Math.abs(lo), Math.abs(hi), Math.abs(value), 1e-9) * 1.15;
  const x = (v: number) => pad + ((v + extent) / (2 * extent)) * (w - 2 * pad);
  return (
    <svg
      className="ci-strip"
      viewBox={`0 0 ${w} ${h}`}
      role="img"
      aria-label={`${label}: ${formatNumber(value, digits)}, 95% interval ${formatNumber(lo, digits)} to ${formatNumber(hi, digits)}`}
    >
      <line className="ci-track" x1={pad} x2={w - pad} y1={14} y2={14} />
      <line className="ci-zero" x1={x(0)} x2={x(0)} y1={4} y2={24} />
      <line className="ci-range" x1={x(lo)} x2={x(hi)} y1={14} y2={14} />
      <circle className="ci-dot" cx={x(value)} cy={14} r={5} />
      <text x={pad} y={37}>
        ← {better[0]}
      </text>
      <text x={x(0)} y={37} textAnchor="middle">
        0
      </text>
      <text x={w - pad} y={37} textAnchor="end">
        {better[1]} →
      </text>
    </svg>
  );
}
