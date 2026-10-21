// How current the data behind a prediction was, from the record's own data_freshness.

import type { DataFreshness } from "../api";
import { daysBetween, formatDate, formatLondon, formatShortDay, parseUtc } from "../format";

/** A gap longer than this between the newest result used and the prediction is flagged. */
export const STALE_AFTER_DAYS = 21;

export type FreshnessFact = { label: string; date: string };

/** Every "latest result" date in the record, most relevant first. */
export function freshnessFacts(f: DataFreshness, competition?: string): FreshnessFact[] {
  const out: FreshnessFact[] = [];
  const byDivision = f.latest_result_by_division;
  if (byDivision && typeof byDivision === "object") {
    const entries = Object.entries(byDivision).filter(([, d]) => typeof d === "string");
    entries.sort(([a], [b]) => (a === competition ? -1 : b === competition ? 1 : a.localeCompare(b)));
    for (const [division, date] of entries) out.push({ label: `Latest ${division} result`, date });
  }
  if (typeof f.latest_uefa_result === "string") {
    out.push({ label: "Latest UEFA result", date: f.latest_uefa_result });
  }
  if (typeof f.latest_domestic_result === "string") {
    out.push({ label: "Latest domestic result", date: f.latest_domestic_result });
  }
  return out.filter((x) => /^\d{4}-\d{2}-\d{2}/.test(x.date));
}

/** The newest result date behind the prediction: the fixture's own division when the record
 * lists divisions, otherwise the newest date recorded. */
export function latestResultDate(f: DataFreshness, competition?: string): string | null {
  const own = competition ? f.latest_result_by_division?.[competition] : undefined;
  if (typeof own === "string" && /^\d{4}-\d{2}-\d{2}/.test(own)) return own.slice(0, 10);
  const dates = freshnessFacts(f).map((x) => x.date.slice(0, 10));
  return dates.length ? (dates.sort().at(-1) ?? null) : null;
}

export function FreshnessBadge({
  freshness,
  createdAt,
  competition,
}: {
  freshness: DataFreshness;
  /** When the prediction was saved (UTC ISO). */
  createdAt: string;
  /** The fixture's competition code, to pick its own division's date. */
  competition?: string;
}) {
  const latest = latestResultDate(freshness, competition);
  const created = parseUtc(createdAt);
  const gap = latest ? daysBetween(new Date(`${latest}T23:59:59Z`), created) : null;
  const stale = gap !== null && gap > STALE_AFTER_DAYS;
  return (
    <span
      className={`freshness${stale ? " is-stale" : ""}`}
      title={`Saved ${formatLondon(createdAt)}${latest ? `; newest result used ${formatDate(latest)}` : ""}`}
    >
      <span className="freshness-dot" aria-hidden="true" />
      {latest ? <>Results to {formatShortDay(latest)}</> : <>Result dates not recorded</>}
      <span aria-hidden="true"> · </span>
      Saved {formatLondon(createdAt)}
      {stale && <strong> · newest result is {gap} days older than the prediction</strong>}
    </span>
  );
}
