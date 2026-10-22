// Building blocks for team statistics, shared by the team pages and the match page's "Form and
// stats" section: comparison bars (for and against, or one side against the other), a
// won-drawn-lost split, rate meters and form pips that name each opponent.
//
// Every figure is the API's (footy.teams); nothing is filled in. A statistic the source lacks
// is shown as missing, and each average says how many matches it covers. Every value a colour
// marks is also printed as text.

import type { ReactNode } from "react";
import type { CompetitionType, MediaSummary, Rate, Result, ShortSummary, StatAverage } from "../api";
import { formatDate, formatNumber, formatPct, formatShortDay } from "../format";
import { TeamBadge, teamAbbr } from "./TeamBadge";
import { TeamLink } from "./TeamLink";
import "./teamstats.css";

// ---------------------------------------------------------------------------------------------
// Names

/** Per-match statistics in display order, with labels. `fd_xg` is football-data's own
 * expected-goals figure, not this project's, and is always labelled so. */
export const STATS: readonly { key: string; label: string; digits: number }[] = [
  { key: "goals", label: "Goals", digits: 2 },
  { key: "half_time_goals", label: "Half-time goals", digits: 2 },
  { key: "shots", label: "Shots", digits: 1 },
  { key: "shots_on_target", label: "Shots on target", digits: 1 },
  { key: "corners", label: "Corners", digits: 1 },
  { key: "fouls", label: "Fouls", digits: 1 },
  { key: "yellow_cards", label: "Yellow cards", digits: 2 },
  { key: "red_cards", label: "Red cards", digits: 2 },
  { key: "fd_xg", label: "xG (football-data's figure)", digits: 2 },
];

export const RATES: readonly { key: string; label: string; hint: string }[] = [
  { key: "clean_sheets", label: "Clean sheets", hint: "conceded none" },
  { key: "failed_to_score", label: "Failed to score", hint: "scored none" },
  { key: "both_teams_scored", label: "Both teams scored", hint: "each side scored" },
  { key: "over_2_5_goals", label: "Over 2.5 goals", hint: "three or more in the match" },
];

export const RESULT_WORD: Record<Result, string> = { W: "Win", D: "Draw", L: "Loss" };

/** The match sources as their publishers write them; an unknown one is shown as sent. */
const SOURCE_NAME: Record<string, string> = {
  "football-data": "football-data.co.uk",
  openfootball: "openfootball",
  wikipedia: "Wikipedia",
};

export function sourceName(source: string): string {
  return SOURCE_NAME[source] ?? source;
}

/** "1st", "2nd", "23rd". */
export function ordinal(n: number): string {
  const tens = n % 100;
  if (tens >= 11 && tens <= 13) return `${n}th`;
  return `${n}${n % 10 === 1 ? "st" : n % 10 === 2 ? "nd" : n % 10 === 3 ? "rd" : "th"}`;
}

/** "+3", "−2", "0". */
export function signed(n: number, digits = 0): string {
  const s = formatNumber(Math.abs(n), digits);
  return n > 0 ? `+${s}` : n < 0 ? `−${s}` : formatNumber(0, digits);
}

export function plural(n: number, one: string, many = `${one}s`): string {
  return `${formatNumber(n, 0)} ${n === 1 ? one : many}`;
}

// ---------------------------------------------------------------------------------------------
// Comparison bars

export type CompareRow = {
  key: string;
  label: ReactNode;
  left: number | null;
  right: number | null;
  digits?: number;
  /** Under the label, e.g. "311 matches". */
  note?: ReactNode;
};

/**
 * Two figures per row as bars growing out from a centre line: the left side's to the left,
 * the right side's to the right, each scaled to the larger of the two, so a row shows which
 * is bigger and by how much. Both figures are printed at the ends. A row where either side
 * has no figure draws no bars (one bar against "—" would read as one side dominating).
 */
export function CompareBars({
  rows,
  left,
  right,
  tone,
  caption,
}: {
  rows: CompareRow[];
  /** Column names, e.g. "For" and "Against", or the two teams. */
  left: ReactNode;
  right: ReactNode;
  tone: "for-against" | "home-away";
  /** A short accessible description of what the rows compare. */
  caption: string;
}) {
  return (
    <div className={`cmp cmp-${tone}`}>
      <div className="cmp-key" aria-hidden="true">
        <span className="cmp-key-left">
          <span className="cmp-swatch cmp-swatch-left" />
          {left}
        </span>
        <span className="cmp-key-right">
          {right}
          <span className="cmp-swatch cmp-swatch-right" />
        </span>
      </div>
      <ul className="cmp-rows" aria-label={caption}>
        {rows.map((r) => {
          const both = r.left !== null && r.right !== null;
          const scale = both ? Math.max(r.left ?? 0, r.right ?? 0) : 0;
          const w = (v: number | null) => (v === null || scale <= 0 ? 0 : (v / scale) * 100);
          const lv = r.left === null ? "—" : formatNumber(r.left, r.digits ?? 2);
          const rv = r.right === null ? "—" : formatNumber(r.right, r.digits ?? 2);
          return (
            <li key={r.key} className={`cmp-row${both ? "" : " cmp-row-partial"}`}>
              <span className="cmp-label">
                <span className="cmp-name">{r.label}</span>
                {r.note && <span className="cmp-note">{r.note}</span>}
              </span>
              <span className="cmp-value cmp-value-left">{lv}</span>
              <span className="cmp-bars" aria-hidden="true">
                <span className="cmp-track cmp-track-left">
                  <span className="cmp-fill cmp-fill-left" style={{ width: `${w(r.left)}%` }} />
                </span>
                <span className="cmp-track cmp-track-right">
                  <span className="cmp-fill cmp-fill-right" style={{ width: `${w(r.right)}%` }} />
                </span>
              </span>
              <span className="cmp-value cmp-value-right">{rv}</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

/** Rows for the per-match averages that have figures; statistics the source lacks are left
 * out (the caller says so). `forLabel` names the side whose "for" column is on the left. */
export function averageRows(
  averages: Record<string, StatAverage> | undefined,
  options: { note?: (a: StatAverage) => ReactNode } = {},
): CompareRow[] {
  if (!averages) return [];
  const rows: CompareRow[] = [];
  for (const s of STATS) {
    const a = averages[s.key];
    if (!a || a.matches === 0 || (a.for === null && a.against === null)) continue;
    rows.push({
      key: s.key,
      label: s.label,
      left: a.for,
      right: a.against,
      digits: s.digits,
      note: options.note ? options.note(a) : plural(a.matches, "match", "matches"),
    });
  }
  return rows;
}

/**
 * Why some averages cover fewer matches than goals do, or are missing: UEFA matches carry
 * scores only, and so do football-data's files for some leagues (D-022). `goals` and `stats`
 * are the matches behind the goals and the shots averages; `europe`, whether the club has UEFA
 * matches among them; `league`, whether it has league matches among them (a club whose league
 * the data doesn't hold has none). `team` names the club where the note isn't on its own page
 * (the match page).
 */
export function statsNote(
  scope: "all" | CompetitionType,
  goals: number,
  stats: number,
  { europe, league, team }: { europe: boolean; league: boolean; team?: string },
): string | null {
  const only = team ? `Only goals for ${team}` : "Only goals";
  const uefa = "the UEFA sources give scores, not shots, corners, fouls or cards";
  if (scope === "europe") return `${only}: ${uefa}.`;
  if (stats >= goals) return null;
  if (stats === 0 && scope === "all" && !league) {
    return `${only}: the data holds no league matches for ${team ? "it" : "this club"}, only UEFA matches, and ${uefa}.`;
  }
  if (stats === 0) {
    return `${only}: football-data's files for ${team ? "its" : "this club's"} league give scores only, with no shots, corners, fouls or cards${scope === "all" && europe ? ", and so do the UEFA sources" : ""}.`;
  }
  const whose = team ? `${team}'s goals` : "Goals";
  return scope === "all"
    ? `${whose} cover all ${formatNumber(goals, 0)} counted matches; shots, corners, fouls and cards only the ${formatNumber(stats, 0)} with statistics. ${europe ? "European matches carry scores only (the permitted sources have no match statistics)" : "Some leagues' files give scores only"}.`
    : `${whose} cover all ${formatNumber(goals, 0)} league matches; shots, corners, fouls and cards only the ${formatNumber(stats, 0)} in leagues whose files have statistics.`;
}

/** The statistics a set of averages has no figures for (e.g. every one but goals in Europe). */
export function missingStats(averages: Record<string, StatAverage> | undefined): string[] {
  return STATS.filter((s) => {
    const a = averages?.[s.key];
    return !a || a.matches === 0 || (a.for === null && a.against === null);
  }).map((s) => s.label.replace(/ \(.*\)$/, ""));
}

// ---------------------------------------------------------------------------------------------
// Won, drawn, lost

/** One bar split won / drawn / lost, with the three counts printed under it. */
export function ResultSplit({
  won,
  drawn,
  lost,
  compact = false,
}: {
  won: number;
  drawn: number;
  lost: number;
  compact?: boolean;
}) {
  const total = won + drawn + lost;
  if (total === 0) return <span className="muted small">No matches</span>;
  const parts = [
    { k: "W", label: "won", n: won },
    { k: "D", label: "drawn", n: drawn },
    { k: "L", label: "lost", n: lost },
  ];
  return (
    <span className={`wdl${compact ? " wdl-compact" : ""}`}>
      <span className="wdl-bar" aria-hidden="true">
        {parts.map((p) =>
          p.n > 0 ? <span key={p.k} className={`wdl-seg wdl-${p.k}`} style={{ flexGrow: p.n }} /> : null,
        )}
      </span>
      <span className="wdl-legend">
        {parts.map((p) => (
          <span key={p.k} className="wdl-item">
            <span className={`wdl-dot wdl-${p.k}`} aria-hidden="true" />
            <strong>{formatNumber(p.n, 0)}</strong> {p.label}
          </span>
        ))}
      </span>
    </span>
  );
}

// ---------------------------------------------------------------------------------------------
// Record tiles

/** True when figures joined by dashes run to nine characters or more ("103–83–125"): those
 * are set smaller in a narrow tile. */
function long(...ns: number[]): boolean {
  return ns.map(String).join("–").length >= 9;
}

/** Played, won-drawn-lost, goals and points per game (league matches) for one summary. */
export function RecordTiles({ s }: { s: ShortSummary }) {
  return (
    <dl className="rec-tiles">
      <div className="rec-tile">
        <dt>Played</dt>
        <dd>
          <span className="rec-big">{formatNumber(s.played, 0)}</span>
          {s.left_out > 0 && <span className="rec-sub">{plural(s.left_out, "flagged match", "flagged matches")} left out</span>}
        </dd>
      </div>
      <div className="rec-tile rec-tile-wdl">
        <dt>Won · drawn · lost</dt>
        <dd>
          <span className={`rec-big${long(s.won, s.drawn, s.lost) ? " rec-long" : ""}`}>
            {s.won}
            <span className="rec-sep">–</span>
            {s.drawn}
            <span className="rec-sep">–</span>
            {s.lost}
          </span>
          <ResultSplit won={s.won} drawn={s.drawn} lost={s.lost} compact />
        </dd>
      </div>
      <div className="rec-tile">
        <dt>Goals for · against</dt>
        <dd>
          <span className={`rec-big${long(s.goals_for, s.goals_against) ? " rec-long" : ""}`}>
            {s.goals_for}
            <span className="rec-sep">–</span>
            {s.goals_against}
          </span>
          <span className="rec-sub">Difference {signed(s.goal_difference)}</span>
        </dd>
      </div>
      {s.league_matches > 0 ? (
        <div className="rec-tile">
          <dt>Points per game</dt>
          <dd>
            <span className="rec-big">{s.points_per_game === null ? "—" : formatNumber(s.points_per_game, 2)}</span>
            <span className="rec-sub">
              {formatNumber(s.league_points ?? 0, 0)} points in {plural(s.league_matches, "league match", "league matches")}
            </span>
          </dd>
        </div>
      ) : (
        // No league matches, so no points: the share of matches won instead.
        <div className="rec-tile">
          <dt>Won</dt>
          <dd>
            <span className="rec-big">{formatPct(s.played > 0 ? s.won / s.played : null)}</span>
            <span className="rec-sub">
              {s.won} of {plural(s.played, "match", "matches")} (points are counted in leagues only)
            </span>
          </dd>
        </div>
      )}
    </dl>
  );
}

// ---------------------------------------------------------------------------------------------
// Rates

export function RateMeters({ rates, played }: { rates: Record<string, Rate>; played: number }) {
  return (
    <ul className="rate-list">
      {RATES.map((r) => {
        const v = rates[r.key];
        const rate = v?.rate ?? null;
        return (
          <li key={r.key} className="rate">
            <span className="rate-label">
              {r.label}
              <span className="rate-hint">{r.hint}</span>
            </span>
            <span className="rate-value">{formatPct(rate)}</span>
            <span className="rate-meter" aria-hidden="true">
              <span className="rate-fill" style={{ width: `${(rate ?? 0) * 100}%` }} />
            </span>
            <span className="rate-of">{v ? `${formatNumber(v.matches, 0)} of ${formatNumber(played, 0)}` : "—"}</span>
          </li>
        );
      })}
    </ul>
  );
}

// ---------------------------------------------------------------------------------------------
// Form

export type FormItem = {
  result: Result;
  /** Opponent, venue, score and date, when known (from the match log). */
  opponent?: { key: string; name: string; media?: MediaSummary } | null;
  venue?: "H" | "A" | "N";
  score?: string;
  date?: string;
  competition?: string;
};

const VENUE_WORD = { H: "home", A: "away", N: "neutral ground" } as const;

function formTitle(f: FormItem): string {
  const parts = [RESULT_WORD[f.result]];
  if (f.score) parts.push(f.score);
  if (f.opponent) parts.push(`${f.venue === "A" ? "at" : "v"} ${f.opponent.name}`);
  if (f.venue === "N") parts.push("(neutral ground)");
  if (f.date) parts.push(formatDate(f.date));
  return parts.join(" ");
}

/** Form, oldest on the left. With opponents (from the match log) each pip carries the
 * opponent's badge with its three letters printed under it (a badge this small has none of
 * its own, and its colours alone don't name the club), the score and the venue; otherwise
 * lettered pips alone. */
export function FormStrip({ items, label }: { items: FormItem[]; label: string }) {
  if (items.length === 0) return <p className="muted small">No counted matches yet.</p>;
  const rich = items.some((f) => f.opponent);
  if (!rich) {
    return (
      <span
        className="form-pips"
        role="img"
        aria-label={`${label}, oldest first: ${items.map((f) => RESULT_WORD[f.result].toLowerCase()).join(", ")}`}
      >
        {items.map((f, i) => (
          <span key={i} className={`pip pip-${f.result}`} aria-hidden="true">
            {f.result}
          </span>
        ))}
      </span>
    );
  }
  return (
    <ol className="form-strip" aria-label={`${label}, oldest first`}>
      {items.map((f, i) => (
        <li key={i} className="fs-item" title={formTitle(f)}>
          <span className={`pip pip-${f.result}`} aria-hidden="true">
            {f.result}
          </span>
          {f.opponent && (
            <>
              <TeamBadge teamKey={f.opponent.key} name={f.opponent.name} media={f.opponent.media} size={22} className="fs-badge" />
              <span className="fs-opp" aria-hidden="true">
                {f.opponent.media?.badge.short?.trim().slice(0, 3).toUpperCase() || teamAbbr(f.opponent.name)}
              </span>
            </>
          )}
          <span className="fs-score" aria-hidden="true">
            {f.score ?? ""}
          </span>
          <span className="fs-venue" aria-hidden="true">
            {f.venue ?? ""}
          </span>
          <span className="visually-hidden">
            {RESULT_WORD[f.result]}
            {f.score ? ` ${f.score}` : ""}
            {f.opponent ? ` ${f.venue === "A" ? "at" : "against"} ${f.opponent.name}` : ""}
            {f.venue ? `, ${VENUE_WORD[f.venue]}` : ""}
            {f.date ? `, ${formatShortDay(f.date)}` : ""}
          </span>
        </li>
      ))}
    </ol>
  );
}

/** A form string from the API ("WDLWW", oldest first) as items without opponents. */
export function formItems(form: string | null | undefined): FormItem[] {
  return [...(form ?? "")].filter((c): c is Result => c === "W" || c === "D" || c === "L").map((result) => ({ result }));
}

/** The opponent's name as a link to its page, with its badge. */
export function OpponentName({
  teamKey,
  name,
  media,
  size = 22,
}: {
  teamKey: string | null | undefined;
  name: string;
  media?: MediaSummary | null;
  size?: number;
}) {
  return (
    <span className="opp">
      <TeamBadge teamKey={teamKey ?? name} name={name} media={media} size={size} />
      <TeamLink teamKey={teamKey} className="opp-name">
        {name}
      </TeamLink>
    </span>
  );
}
