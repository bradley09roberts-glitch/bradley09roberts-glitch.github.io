// A team's match log (the team page's Matches tab): every stored match from the club's side,
// newest first, with the competition, venue, opponent, score and result, our pre-match pick
// and whether it was right, and each row opening to every statistic for and against, the
// referee, football-data's xG (labelled as theirs) and the strength before and after.
//
// The API's default page (the latest 100 matches) is what the static snapshot holds; against
// the live API older matches load on request. The type and season filters apply to the
// matches loaded.

import { useMemo, useState } from "react";
import {
  ApiError,
  endpoints,
  getJson,
  type ApiResult,
  type CompetitionType,
  type MediaSummary,
  type TeamMatch,
  type TeamMatchesResponse,
  type TeamProfile,
} from "../api";
import { BasisBadge, Badge } from "../components/Badge";
import { CompetitionMark } from "../components/CompetitionMark";
import { SelectField } from "../components/Controls";
import { ApiView, Empty } from "../components/StateViews";
import {
  CompareBars,
  OpponentName,
  RESULT_WORD,
  STATS,
  signed,
  sourceName,
  type CompareRow,
} from "../components/TeamStats";
import { formatDate, formatNumber, formatPct, formatTime, marketLabel, seasonLabel } from "../format";
import { href, setQuery } from "../router";
import { explorerHref, teamExplorerTargets } from "../explorer";
import { SNAPSHOT } from "../snapshot";

/** Rows drawn at a time; "Show more" adds another page. */
const ROWS = 25;

type TypeFilter = "all" | CompetitionType;

function parseType(raw: string | null): TypeFilter {
  return raw === "league" || raw === "europe" || raw === "cup" ? raw : "all";
}

const VENUE = { H: "Home", A: "Away", N: "Neutral ground" } as const;

/** Older pages of the log, fetched one at a time on request (live API only). */
function useOlderPages(key: string) {
  const [rows, setRows] = useState<TeamMatch[]>([]);
  const [media, setMedia] = useState<Record<string, MediaSummary>>({});
  const [state, setState] = useState<{ loading: boolean; error: string | null }>({ loading: false, error: null });
  const load = (offset: number) => {
    setState({ loading: true, error: null });
    getJson(endpoints.teamMatches(key, offset)).then(
      (page) => {
        setRows((r) => [...r, ...page.matches]);
        setMedia((m) => ({ ...m, ...page.media }));
        setState({ loading: false, error: null });
      },
      (err: unknown) => {
        setState({ loading: false, error: err instanceof ApiError ? err.message : "Couldn't load older matches." });
      },
    );
  };
  return { rows, media, ...state, load };
}

export function MatchLog({
  p,
  log,
  query,
}: {
  p: TeamProfile;
  log: ApiResult<TeamMatchesResponse>;
  query: URLSearchParams;
}) {
  return (
    <>
      <BacktestLinks p={p} />
      <ApiView result={log} what="the match log" loadingLines={2}>
        {(data) => <LogView p={p} data={data} query={query} />}
      </ApiView>
    </>
  );
}

/** Into the lab's backtest explorer, filtered by this team: the latest league season (or UEFA
 * competition) it has backtest predictions for, then the others. Nothing when it has none. */
function BacktestLinks({ p }: { p: TeamProfile }) {
  const targets = teamExplorerTargets(p);
  const [first, ...rest] = targets;
  if (!first) return null;
  const name = p.team.display_name ?? p.team.name;
  const link = (t: { division: string; season: string }) => explorerHref({ ...t, team: p.team.key });
  return (
    <div className="ml-backtest">
      <a className="ml-backtest-link" href={link(first)}>
        What each model predicted for these games (backtest)
        <span aria-hidden="true"> →</span>
      </a>
      <p className="muted small">
        Every model's predictions for {name}'s {first.name} matches in {seasonLabel(first.season)}, computed after
        the fact: not pre-match records.
        {rest.length > 0 && (
          <>
            {" "}
            Also:{" "}
            {rest.map((t, i) => (
              <span key={`${t.division}-${t.season}`}>
                {i > 0 && " · "}
                <a href={link(t)}>
                  {t.name} {seasonLabel(t.season)}
                </a>
              </span>
            ))}
            .
          </>
        )}
      </p>
    </div>
  );
}

function LogView({ p, data, query }: { p: TeamProfile; data: TeamMatchesResponse; query: URLSearchParams }) {
  const older = useOlderPages(p.team.key);
  const [shown, setShown] = useState(ROWS);
  const [open, setOpen] = useState<string | null>(null);
  const type = parseType(query.get("type"));
  const season = query.get("season");

  const all = useMemo(() => [...data.matches, ...older.rows], [data.matches, older.rows]);
  const media = useMemo(() => ({ ...data.media, ...older.media }), [data.media, older.media]);
  const seasons = useMemo(() => [...new Set(all.map((r) => r.season))], [all]);
  const counts = useMemo(() => {
    const c: Record<TypeFilter, number> = { all: all.length, league: 0, europe: 0, cup: 0 };
    for (const r of all) c[r.competition_type] += 1;
    return c;
  }, [all]);
  const rows = all.filter((r) => (type === "all" || r.competition_type === type) && (!season || r.season === season));
  const remaining = data.total - all.length;
  // The "Our pick" column only when some loaded match has one (most clubs' logs have none).
  const picks = all.some((r) => r.prediction?.pick_for_team);

  return (
    <section className="card team-card ml" aria-labelledby="log-title">
      <div className="team-card-head">
        <h2 id="log-title">Match log</h2>
        <p className="team-card-sub">
          {all.length < data.total
            ? `The latest ${formatNumber(all.length, 0)} of ${formatNumber(data.total, 0)} matches, newest first`
            : `All ${formatNumber(data.total, 0)} matches, newest first`}
        </p>
      </div>

      <div className="ml-filters">
        <div className="filters" role="group" aria-label="Competition type">
          <div className="segmented segmented-scroll">
            {(["all", "league", "europe"] as const).map((t) => (
              <button
                key={t}
                type="button"
                aria-pressed={type === t}
                onClick={() => {
                  setShown(ROWS);
                  setQuery({ type: t === "all" ? null : t });
                }}
              >
                {t === "all" ? "All" : t === "league" ? "League" : "Europe"}{" "}
                <span className="seg-count">{counts[t]}</span>
              </button>
            ))}
          </div>
        </div>
        {seasons.length > 1 && (
          <SelectField
            id="log-season"
            label="Season"
            value={season ?? ""}
            onChange={(v) => {
              setShown(ROWS);
              setQuery({ season: v || null });
            }}
            options={[{ value: "", label: "Every season" }, ...seasons.map((s) => ({ value: s, label: seasonLabel(s) }))]}
          />
        )}
      </div>

      {rows.length === 0 ? (
        <Empty title="No matches with these filters">
          <p>{remaining > 0 ? "Older matches may have some: load them below, or clear the filters." : "Clear the filters to see every match."}</p>
        </Empty>
      ) : (
        <ol className={`ml-list${picks ? "" : " no-picks"}`} aria-label="Matches, newest first">
          <li className="ml-head" aria-hidden="true">
            <span>Date</span>
            <span>Competition</span>
            <span title="Home, away or neutral ground">H/A</span>
            <span>Opponent</span>
            <span className="ml-head-score">Score</span>
            <span />
            {picks && <span>Our pick</span>}
            <span />
          </li>
          {rows.slice(0, shown).map((r) => (
            <LogRow
              key={r.ref}
              r={r}
              team={p.team.name}
              opponentMedia={media[r.opponent]}
              open={open === r.ref}
              onToggle={() => setOpen((o) => (o === r.ref ? null : r.ref))}
            />
          ))}
        </ol>
      )}

      <div className="ml-more">
        {rows.length > shown && (
          <button type="button" onClick={() => setShown((n) => n + ROWS)}>
            Show {Math.min(ROWS, rows.length - shown)} more
          </button>
        )}
        {rows.length <= shown && remaining > 0 && !SNAPSHOT && (
          <button type="button" onClick={() => older.load(all.length)} disabled={older.loading}>
            {older.loading ? "Loading older matches…" : `Load older matches (${formatNumber(remaining, 0)} more)`}
          </button>
        )}
        {older.error && <p className="error small">{older.error}</p>}
        {remaining > 0 && SNAPSHOT && (
          <p className="muted small">
            This snapshot holds the latest {formatNumber(all.length, 0)} of the club's{" "}
            {formatNumber(data.total, 0)} matches; the live site has every one.
          </p>
        )}
      </div>
      <p className="muted small">
        Scores are after 90 minutes; extra time and penalties are marked. Flagged matches (awarded,
        cancelled, play-offs against outside clubs) are listed but left out of every summary.
        {picks ? "" : " We published no prediction for any of these matches."}
      </p>
    </section>
  );
}

function LogRow({
  r,
  team,
  opponentMedia,
  open,
  onToggle,
}: {
  r: TeamMatch;
  team: string;
  opponentMedia: MediaSummary | undefined;
  open: boolean;
  onToggle: () => void;
}) {
  const detailId = `ml-${r.ref.replace(/[^a-z0-9]/gi, "-")}`;
  const scored = r.goals_for !== null && r.goals_against !== null;
  const pr = r.prediction;
  const right = pr?.settled ? pr.correct?.["1X2"] : undefined;
  const pickP = pr?.team && pr.pick_for_team ? { W: pr.team.win, D: pr.team.draw, L: pr.team.loss }[pr.pick_for_team] : null;
  return (
    <li className={`ml-row${open ? " is-open" : ""}${r.excluded ? " is-excluded" : ""}`}>
      <div className="ml-line">
        <span className="ml-date">{formatDate(r.date)}</span>
        <span className="ml-comp" title={`${r.competition_name}${r.stage ? ` · ${r.stage}` : ""}`}>
          <CompetitionMark code={r.competition} />
          <span className="ml-comp-name">{r.competition_name}</span>
        </span>
        <span className="ml-venue" title={VENUE[r.venue]}>
          <span aria-hidden="true">{r.venue}</span>
          <span className="visually-hidden">{VENUE[r.venue]}</span>
        </span>
        <span className="ml-opp">
          <OpponentName teamKey={r.opponent} name={r.opponent_name} media={opponentMedia} size={24} />
        </span>
        <span className="ml-score">
          {scored ? `${r.goals_for}–${r.goals_against}` : r.status === "played" ? "—" : r.status}
          {r.extra_time && <span className="ml-flag">{r.penalties ? "pens" : "aet"}</span>}
        </span>
        {r.result ? (
          <span className={`pip pip-${r.result} ml-result`} title={RESULT_WORD[r.result]}>
            {r.result}
            <span className="visually-hidden"> ({RESULT_WORD[r.result]})</span>
          </span>
        ) : (
          <span className="ml-result" />
        )}
        <span className="ml-pick">
          {pr && pr.pick_for_team ? (
            <>
              <span className="ml-pick-label">Our pick</span> {RESULT_WORD[pr.pick_for_team]}
              {pickP !== null && <span className="muted"> {formatPct(pickP)}</span>}
              {right !== undefined && (
                <span className={right ? "ml-right" : "ml-wrong"}>
                  {" "}
                  <span aria-hidden="true">{right ? "✓" : "✗"}</span> {right ? "Right" : "Wrong"}
                </span>
              )}
              {pr && !pr.settled && <span className="muted"> · not settled</span>}
            </>
          ) : (
            <span className="ml-nopick">
              <span aria-hidden="true">—</span>
              <span className="visually-hidden">No prediction published</span>
            </span>
          )}
        </span>
        <button
          type="button"
          className="ml-toggle"
          aria-expanded={open}
          aria-controls={detailId}
          onClick={onToggle}
        >
          <span className="visually-hidden">
            {open ? "Hide" : "Show"} the statistics for {formatDate(r.date)} against {r.opponent_name}
          </span>
          <span aria-hidden="true">Stats</span>
          <span className="ml-chev" aria-hidden="true" />
        </button>
      </div>
      {r.excluded && (
        <p className="ml-excluded">
          <Badge tone="warn">Left out of summaries</Badge> {r.excluded}
        </p>
      )}
      {open && <LogDetail id={detailId} r={r} team={team} />}
    </li>
  );
}

/** The statistic pairs of one log row, in display order; rows without either figure left out. */
function statRows(r: TeamMatch): CompareRow[] {
  const rows: CompareRow[] = [];
  for (const s of STATS) {
    const [f, a] =
      s.key === "goals"
        ? [r.goals_for, r.goals_against]
        : s.key === "half_time_goals"
          ? [r.ht_goals_for, r.ht_goals_against]
          : [r[`${s.key}_for`], r[`${s.key}_against`]];
    if ((f ?? null) === null && (a ?? null) === null) continue;
    rows.push({ key: s.key, label: s.label, left: f ?? null, right: a ?? null, digits: s.key === "fd_xg" ? 2 : 0 });
  }
  return rows;
}

function LogDetail({ id, r, team }: { id: string; r: TeamMatch; team: string }) {
  const rows = statRows(r);
  const hasStats = rows.some((x) => x.key !== "goals");
  const pr = r.prediction;
  return (
    <div className="ml-detail" id={id}>
      {hasStats ? (
        <CompareBars rows={rows} left={team} right={r.opponent_name} tone="for-against" caption={`Statistics, ${team} against ${r.opponent_name}`} />
      ) : (
        <p className="note-line">
          {r.competition_type === "europe"
            ? "No match statistics: the UEFA sources give the score only."
            : "No match statistics for this league in the data (scores only)."}
        </p>
      )}
      {rows.some((x) => x.key === "fd_xg") && (
        <p className="muted small">xG is football-data's own figure, not this site's.</p>
      )}
      <dl className="ml-facts">
        <div>
          <dt>Competition</dt>
          <dd>
            {r.competition_name}
            {r.stage ? ` · ${r.stage}` : ""} · {r.season_label}
            {r.meeting && r.meeting > 1 ? ` · meeting ${r.meeting}` : ""}
          </dd>
        </div>
        <div>
          <dt>Venue</dt>
          <dd>
            {VENUE[r.venue]}
            {r.kickoff_utc ? ` · kick-off ${formatTime(r.kickoff_utc)} UK time` : ""}
          </dd>
        </div>
        {r.extra_time && (
          <div>
            <dt>After 90 minutes</dt>
            <dd>{r.penalties ? "Went to extra time and penalties" : "Went to extra time"}</dd>
          </div>
        )}
        <div>
          <dt>Referee</dt>
          <dd>{r.referee ?? (r.competition_type === "europe" ? "Not in the UEFA sources" : "Not recorded")}</dd>
        </div>
        {r.elo && (
          <div>
            <dt>Strength</dt>
            <dd>
              {formatNumber(Math.round(r.elo.before), 0)} → {formatNumber(Math.round(r.elo.after), 0)} (
              {signed(r.elo.after - r.elo.before, 1)})
              {r.elo.opponent_before !== null && (
                <span className="muted"> · opponent {formatNumber(Math.round(r.elo.opponent_before), 0)}</span>
              )}
            </dd>
          </div>
        )}
        <div>
          <dt>Source</dt>
          <dd>{sourceName(r.source)}</dd>
        </div>
      </dl>
      {pr && (
        <div className="ml-pred">
          <p>
            <strong>Our pre-match prediction</strong> ({pr.model_name}){" "}
            {pr.basis === "model_and_market" && <BasisBadge basis={pr.basis} />}
          </p>
          {pr.team && (
            <p className="small">
              {team} win {formatPct(pr.team.win)} · draw {formatPct(pr.team.draw)} · {r.opponent_name} win{" "}
              {formatPct(pr.team.loss)}
              {pr.pick_for_team && <> · pick: {RESULT_WORD[pr.pick_for_team].toLowerCase()}</>}
            </p>
          )}
          {pr.settled && pr.correct && (
            <ul className="ml-correct">
              {Object.entries(pr.correct).map(([m, ok]) => (
                <li key={m} className={ok ? "ml-right" : "ml-wrong"}>
                  <span aria-hidden="true">{ok ? "✓" : "✗"}</span> {marketLabel(m)}: {ok ? "right" : "wrong"}
                </li>
              ))}
            </ul>
          )}
          <a href={href(`/match/${pr.id}`)}>
            Open the saved prediction <span aria-hidden="true">→</span>
          </a>
        </div>
      )}
      {!pr && <p className="muted small">No prediction of ours was published for this match.</p>}
    </div>
  );
}
