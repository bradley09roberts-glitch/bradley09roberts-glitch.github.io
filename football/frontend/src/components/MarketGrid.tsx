// Goal totals (over/under lines) and both teams to score.

import type { OverUnder, Probabilities } from "../api";
import { formatPct, totalLine } from "../format";

type Row = { key: string; label: string; yesLabel: string; noLabel: string; yes: number; no: number };

/** The over/under markets in the record, sorted by line. */
export function totalsIn(p: Probabilities): { market: string; line: number; probs: OverUnder }[] {
  const out: { market: string; line: number; probs: OverUnder }[] = [];
  for (const [k, v] of Object.entries(p)) {
    if (!k.startsWith("OU_") || !v || typeof v !== "object" || Array.isArray(v)) continue;
    const ou = v as OverUnder;
    if (typeof ou.O !== "number") continue;
    out.push({ market: k, line: totalLine(k), probs: { O: ou.O, U: typeof ou.U === "number" ? ou.U : 1 - ou.O } });
  }
  return out.sort((a, b) => a.line - b.line);
}

function rows(p: Probabilities, lines: number[] | undefined): Row[] {
  const out: Row[] = totalsIn(p)
    .filter((t) => !lines || lines.includes(t.line))
    .map((t) => ({
      key: t.market,
      label: `${t.line} goals`,
      yesLabel: `Over ${t.line}`,
      noLabel: `Under ${t.line}`,
      yes: t.probs.O,
      no: t.probs.U,
    }));
  if (p.BTTS) {
    out.push({
      key: "BTTS",
      label: "Both score",
      yesLabel: "Yes",
      noLabel: "No",
      yes: p.BTTS.Y,
      no: p.BTTS.N,
    });
  }
  return out;
}

/**
 * compact: one chip per market (over / yes probability large, the other side small, a thin
 * meter along the bottom), for fixture cards. full: a table with both sides and a meter for
 * every line.
 */
export function MarketGrid({
  probabilities,
  lines,
  variant = "full",
}: {
  probabilities: Probabilities;
  /** Over/under lines to show; all lines in the record when omitted. */
  lines?: number[];
  variant?: "compact" | "full";
}) {
  const list = rows(probabilities, lines);
  if (list.length === 0) return <p className="muted">No goal-total or both-teams-to-score probabilities in this record.</p>;

  if (variant === "compact") {
    // One chip per market: the over (or yes) probability large, the other side beneath.
    return (
      <ul className="market-chips">
        {list.map((r) => (
          <li key={r.key} className="mchip">
            <span className="mchip-label">{r.key === "BTTS" ? "Both score" : r.yesLabel}</span>
            <span className="mchip-value">{formatPct(r.yes)}</span>
            <span className="mchip-other">
              {r.key === "BTTS" ? "No" : "Under"} {formatPct(r.no)}
            </span>
            <span className="mchip-meter" aria-hidden="true">
              <span style={{ width: `${Math.round(r.yes * 100)}%` }} />
            </span>
          </li>
        ))}
      </ul>
    );
  }

  return (
    <table className="market-table">
      <caption className="visually-hidden">Goal totals and both teams to score</caption>
      <thead>
        <tr>
          <th scope="col">Market</th>
          <th scope="col" className="num">
            Over / Yes
          </th>
          <th scope="col" aria-hidden="true">
            <span className="visually-hidden">Chart</span>
          </th>
          <th scope="col" className="num">
            Under / No
          </th>
        </tr>
      </thead>
      <tbody>
        {list.map((r) => {
          const yesPick = r.yes >= r.no;
          return (
            <tr key={r.key}>
              <th scope="row">{r.label}</th>
              <td className={`num${yesPick ? " is-pick" : ""}`}>
                <span className="visually-hidden">{r.yesLabel} </span>
                {formatPct(r.yes)}
              </td>
              <td className="meter-cell" aria-hidden="true">
                <span className="meter">
                  <span className="meter-fill" style={{ width: `${Math.round(r.yes * 100)}%` }} />
                </span>
              </td>
              <td className={`num${yesPick ? "" : " is-pick"}`}>
                <span className="visually-hidden">{r.noLabel} </span>
                {formatPct(r.no)}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
