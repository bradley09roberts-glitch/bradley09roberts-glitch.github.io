// The most likely exact scores from the saved record, laid out as a small heat map: home goals
// down, away goals across. Only the goal counts that appear among the saved scores get a row or
// a column, so there are no empty rows. Within the grid, a score the record didn't save is
// marked as not in the record (never as zero). Every value is printed in its cell; the shading
// only supports it.

import { formatPct } from "../format";

type Score = { score: string; p: number };

function parse(s: Score): { h: number; a: number; p: number } | null {
  const [h, a] = s.score.split("-").map((n) => Number.parseInt(n, 10));
  if (h === undefined || a === undefined || !Number.isInteger(h) || !Number.isInteger(a) || h < 0 || a < 0) return null;
  return { h, a, p: s.p };
}

export function ScoreGrid({ scores, homeName, awayName }: { scores: Score[]; homeName: string; awayName: string }) {
  if (scores.length === 0) return <p className="muted">No score grid in this record.</p>;
  const cells = scores.map(parse).filter((c): c is { h: number; a: number; p: number } => c !== null);
  const max = Math.max(...scores.map((s) => s.p));
  const covered = scores.reduce((sum, s) => sum + s.p, 0);
  const top = scores.reduce<Score | undefined>((best, s) => (!best || s.p > best.p ? s : best), undefined);
  // Goal counts up to nine get a row (home) or column (away) when a saved score has them.
  const inGrid = cells.filter((c) => c.h <= 9 && c.a <= 9);
  const rows = [...new Set(inGrid.map((c) => c.h))].sort((a, b) => a - b);
  const cols = [...new Set(inGrid.map((c) => c.a))].sort((a, b) => a - b);
  const at = new Map(cells.map((c) => [`${c.h}-${c.a}`, c.p]));
  // Scores outside the drawn grid (more than nine goals) would be lost: list them instead.
  const outside = cells.filter((c) => c.h > 9 || c.a > 9);
  const hasGaps = rows.some((h) => cols.some((a) => !at.has(`${h}-${a}`)));

  return (
    <figure className="scoregrid">
      <div className="scoregrid-wrap">
        <span className="scoregrid-axis scoregrid-axis-away" aria-hidden="true">
          {awayName} goals <span className="scoregrid-arrow">→</span>
        </span>
        <span className="scoregrid-axis scoregrid-axis-home" aria-hidden="true">
          {homeName} goals <span className="scoregrid-arrow">↓</span>
        </span>
        <table className="scoregrid-table" style={{ ["--cols" as string]: cols.length }}>
          <caption className="visually-hidden">
            Most likely scores: {homeName} goals in rows, {awayName} goals in columns. Cells
            without a value are not among the saved scores.
          </caption>
          <thead>
            <tr>
              <td className="scoregrid-corner" />
              {cols.map((a) => (
                <th key={a} scope="col">
                  <span className="visually-hidden">{awayName} </span>
                  {a}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((h) => (
              <tr key={h}>
                <th scope="row">
                  <span className="visually-hidden">{homeName} </span>
                  {h}
                </th>
                {cols.map((a) => {
                  const p = at.get(`${h}-${a}`);
                  if (p === undefined) {
                    return (
                      <td key={a} className="scoregrid-cell is-empty">
                        <span aria-hidden="true">·</span>
                        <span className="visually-hidden">not among the saved scores</span>
                      </td>
                    );
                  }
                  const isTop = top !== undefined && top.score === `${h}-${a}`;
                  return (
                    <td
                      key={a}
                      className={`scoregrid-cell${isTop ? " is-top" : ""}`}
                      style={{ ["--heat-p" as string]: max > 0 ? (p / max).toFixed(3) : "0" }}
                    >
                      <span className="scoregrid-value">{formatPct(p, 1)}</span>
                      {isTop && <span className="visually-hidden"> (most likely score)</span>}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {outside.length > 0 && (
        <p className="small">
          Also saved:{" "}
          {outside.map((c, i) => (
            <span key={`${c.h}-${c.a}`}>
              {i > 0 && ", "}
              {c.h}–{c.a} {formatPct(c.p, 1)}
            </span>
          ))}
        </p>
      )}
      <figcaption className="muted small">
        {top && (
          <>
            Most likely: <strong className="scoregrid-top">{top.score.replace("-", "–")}</strong> ({formatPct(top.p, 1)}),
            outlined.{" "}
          </>
        )}
        Scores are {homeName} first, {awayName} second. These {scores.length} scores together cover{" "}
        {formatPct(covered)} of the model's probability; every other score shares the remaining{" "}
        {formatPct(Math.max(0, 1 - covered))}.{hasGaps && " A dot marks a score that isn't among them."}
      </figcaption>
    </figure>
  );
}
