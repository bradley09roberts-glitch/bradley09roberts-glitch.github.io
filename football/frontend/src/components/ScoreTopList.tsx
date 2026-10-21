// The most likely exact scores from the saved score grid.

import { formatPct } from "../format";

export function ScoreTopList({
  scores,
  homeName,
  awayName,
}: {
  scores: { score: string; p: number }[];
  homeName: string;
  awayName: string;
}) {
  if (scores.length === 0) return <p className="muted">No score grid in this record.</p>;
  const max = Math.max(...scores.map((s) => s.p));
  const covered = scores.reduce((sum, s) => sum + s.p, 0);
  return (
    <figure className="scores">
      <ol className="score-list">
        {scores.map((s, i) => (
          <li key={s.score} className={i === 0 ? "is-pick" : undefined}>
            <span className="score-label">{s.score.replace("-", "–")}</span>
            <span className="bar-track" aria-hidden="true">
              <span className="bar-fill" style={{ width: `${max > 0 ? (s.p / max) * 100 : 0}%` }} />
            </span>
            <span className="score-value">{formatPct(s.p, 1)}</span>
          </li>
        ))}
      </ol>
      <figcaption className="muted small">
        Scores are {homeName} first, {awayName} second. These {scores.length} scores together
        cover {formatPct(covered)} of the model's probability; every other score shares the
        remaining {formatPct(Math.max(0, 1 - covered))}.
      </figcaption>
    </figure>
  );
}
