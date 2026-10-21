// 1X2 probabilities as one stacked bar: home (blue), draw (neutral grey), away (orange).
// Every value is printed beside its swatch, so colour never carries the meaning alone.

import { formatPct } from "../format";

export type OneXTwo = { H: number; D: number; A: number };

export function pickOf(p: OneXTwo): "H" | "D" | "A" {
  if (p.H >= p.D && p.H >= p.A) return "H";
  return p.A >= p.D ? "A" : "D";
}

export function ProbBar({
  probs,
  homeName,
  awayName,
  size = "regular",
}: {
  probs: OneXTwo;
  homeName: string;
  awayName: string;
  size?: "regular" | "large";
}) {
  const pick = pickOf(probs);
  const parts = [
    { code: "H" as const, cls: "home", label: homeName, sr: `${homeName} win`, p: probs.H },
    { code: "D" as const, cls: "draw", label: "Draw", sr: "Draw", p: probs.D },
    { code: "A" as const, cls: "away", label: awayName, sr: `${awayName} win`, p: probs.A },
  ];
  return (
    <div className={`probbar probbar-${size}`}>
      <div className="probbar-track" aria-hidden="true">
        {parts.map((x) => (
          <span
            key={x.code}
            className={`probbar-seg seg-${x.cls}`}
            style={{ flexGrow: Math.max(x.p, 0.0001) }}
            title={`${x.sr}: ${formatPct(x.p)}`}
          />
        ))}
      </div>
      <ul className="probbar-legend">
        {parts.map((x) => (
          <li key={x.code} className={x.code === pick ? "is-pick" : undefined}>
            <span className="probbar-value">
              {formatPct(x.p)}
              {x.code === pick && <span className="visually-hidden"> (most likely)</span>}
            </span>
            <span className="probbar-name">
              <span className={`swatch swatch-${x.cls}`} aria-hidden="true" />
              <span aria-hidden="true">{x.label}</span>
              <span className="visually-hidden">{x.sr}</span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
