// The match page's "Form and stats" section, from the prediction's `teams` block
// (footy.teams.match_page): once played, both sides' statistics for the match; each side's
// last ten results overall and by competition type; per-match averages over those matches as
// head-to-head bars; and the last meetings between the two. Everything is from matches before
// the fixture's date, so a played match's page shows what was known before it.

import { useState } from "react";
import type { CompetitionType, HeadToHeadMeeting, MatchTeams, MatchTeamSide } from "../api";
import { CompetitionMark } from "../components/CompetitionMark";
import { Segmented } from "../components/Controls";
import { TeamBadge } from "../components/TeamBadge";
import { TeamLink } from "../components/TeamLink";
import {
  CompareBars,
  FormStrip,
  RESULT_WORD,
  STATS,
  formItems,
  plural,
  sourceName,
  statsNote,
  type CompareRow,
} from "../components/TeamStats";
import { LEAGUES_HELD, UEFA_HELD } from "../competitions";
import { formatDate } from "../format";
import "./MatchForm.css";

type Scope = "all" | CompetitionType;
type Side = "for" | "against";

const SCOPE_LABEL: Record<Scope, string> = { all: "All", league: "League", europe: "Europe", cup: "Cups" };

export function FormAndStats({ t }: { t: MatchTeams }) {
  return (
    <section className="card team-card mf" aria-labelledby="form-stats">
      <div className="team-card-head">
        <h2 id="form-stats">Form and stats</h2>
        <p className="team-card-sub">From matches before {formatDate(t.as_of)}</p>
      </div>
      {t.match_stats && <ThisMatch t={t} />}
      <FormBlock t={t} />
      <AverageBlock t={t} />
      <HeadToHead t={t} />
    </section>
  );
}

function TeamName({ side }: { side: MatchTeamSide }) {
  return (
    <span className="mf-team">
      <TeamBadge teamKey={side.team} name={side.name} media={side.media} size={24} />
      <TeamLink teamKey={side.team}>{side.name}</TeamLink>
    </span>
  );
}

function ThisMatch({ t }: { t: MatchTeams }) {
  const m = t.match_stats;
  if (!m) return null;
  const rows: CompareRow[] = [];
  for (const s of STATS) {
    const h = m.home[s.key] ?? null;
    const a = m.away[s.key] ?? null;
    if (h === null && a === null) continue;
    rows.push({ key: s.key, label: s.label, left: h, right: a, digits: s.key === "fd_xg" ? 2 : 0 });
  }
  return (
    <div className="mf-block">
      <h3>This match</h3>
      <CompareBars
        rows={rows}
        left={t.home.name}
        right={t.away.name}
        tone="home-away"
        caption={`Match statistics, ${t.home.name} against ${t.away.name}`}
      />
      <p className="muted small">
        Referee: {m.referee ?? "not recorded"} · source: {sourceName(m.source)}
        {rows.some((r) => r.key === "fd_xg") ? " · xG is football-data's own figure, not this site's" : ""}
        {m.excluded ? ` · flagged: ${m.excluded}` : ""}
      </p>
    </div>
  );
}

function FormBlock({ t }: { t: MatchTeams }) {
  const sides = [t.home, t.away];
  return (
    <div className="mf-block">
      <h3>Last 10, oldest on the left</h3>
      <div className="mf-form">
        {sides.map((side) => {
          const types = (["league", "europe", "cup"] as const).filter((k) => side.form.by_type[k]);
          return (
            <div key={side.team} className="mf-form-side">
              <TeamName side={side} />
              <dl className="form-rows">
                <div className="form-row">
                  <dt>All</dt>
                  <dd>
                    <FormStrip items={formItems(side.form.last_10)} label={`${side.name}, last 10`} />
                  </dd>
                </div>
                {types.map((k) => (
                  <div key={k} className="form-row">
                    <dt>{SCOPE_LABEL[k]}</dt>
                    <dd>
                      <FormStrip items={formItems(side.form.by_type[k])} label={`${side.name}, last 10 ${SCOPE_LABEL[k].toLowerCase()} matches`} />
                    </dd>
                  </div>
                ))}
              </dl>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function AverageBlock({ t }: { t: MatchTeams }) {
  // Scopes both sides have matches in (the home side's order: all, then by type).
  const scopes = (Object.keys(t.home.per_match) as Scope[]).filter(
    (k) => (t.home.per_match[k]?.matches ?? 0) > 0 && (t.away.per_match[k]?.matches ?? 0) > 0,
  );
  const [scope, setScope] = useState<Scope>(scopes[0] ?? "all");
  const [side, setSide] = useState<Side>("for");
  const h = t.home.per_match[scope];
  const a = t.away.per_match[scope];
  if (!h || !a) return null;
  const rows: CompareRow[] = [];
  for (const s of STATS) {
    const hs = h.averages[s.key];
    const as = a.averages[s.key];
    const hv = hs?.[side] ?? null;
    const av = as?.[side] ?? null;
    if (hv === null && av === null) continue;
    rows.push({
      key: s.key,
      label: s.label,
      left: hv,
      right: av,
      digits: s.digits,
      note: hs?.matches === as?.matches ? plural(hs?.matches ?? 0, "match", "matches") + " each" : `${hs?.matches ?? 0} and ${as?.matches ?? 0} matches`,
    });
  }
  // Why a side's statistics cover fewer matches than its goals, or are missing, in the team
  // page's words; for European matches one note covers both sides.
  const notes =
    scope === "europe"
      ? [statsNote("europe", 0, 0, true)]
      : [t.home, t.away].map((sd) => {
          const pm = sd.per_match[scope]?.averages;
          return statsNote(
            scope,
            pm?.goals?.matches ?? 0,
            pm?.shots?.matches ?? 0,
            (sd.per_match.europe?.matches ?? 0) > 0,
            sd.name,
          );
        });
  return (
    <div className="mf-block">
      <h3>Per match, last 10</h3>
      <div className="mf-controls">
        {scopes.length > 1 && (
          <Segmented
            label="Matches"
            value={scope}
            options={scopes.map((k) => ({ value: k, label: SCOPE_LABEL[k] }))}
            onChange={setScope}
          />
        )}
        <Segmented
          label="Figures"
          value={side}
          options={[
            { value: "for", label: "Made" },
            { value: "against", label: "Conceded" },
          ]}
          onChange={setSide}
        />
      </div>
      <CompareBars
        rows={rows}
        left={t.home.name}
        right={t.away.name}
        tone="home-away"
        caption={`Average per match ${side === "for" ? "made" : "conceded"}, ${t.home.name} against ${t.away.name}`}
      />
      <p className="muted small">
        {side === "for"
          ? "Each side's own goals, shots, corners, fouls and cards"
          : "What each side's opponents managed against it"}
        , per match, over its last 10 {scope === "all" ? "" : `${SCOPE_LABEL[scope].toLowerCase()} `}matches. A
        row with a figure for one side only has no bars.
      </p>
      {notes.map((n) => n && (
        <p key={n} className="note-line">
          {n}
        </p>
      ))}
    </div>
  );
}

function HeadToHead({ t }: { t: MatchTeams }) {
  const h = t.head_to_head;
  return (
    <div className="mf-block">
      <h3>Head to head</h3>
      {h.meetings.length === 0 ? (
        <p className="muted small">
          No earlier meeting in the data. {LEAGUES_HELD} {UEFA_HELD}
        </p>
      ) : (
        <>
          <p className="mf-tally">
            <span>
              <b>{h.home_side_won}</b> {t.home.name}
            </span>
            <span>
              <b>{h.drawn}</b> {h.drawn === 1 ? "draw" : "draws"}
            </span>
            <span>
              <b>{h.away_side_won}</b> {t.away.name}
            </span>
          </p>
          <ol className="mf-h2h">
            {h.meetings.map((m) => (
              <Meeting key={m.ref} m={m} home={t.home.name} />
            ))}
          </ol>
          <p className="muted small">
            The last {h.meetings.length === 1 ? "meeting" : `${h.meetings.length} meetings`}, newest first, in any
            competition; the letter is {t.home.name}'s result. Scores are after 90 minutes.
          </p>
        </>
      )}
    </div>
  );
}

function Meeting({ m, home: name }: { m: HeadToHeadMeeting; home: string }) {
  const r = m.result_for_home_side;
  return (
    <li className={`mf-meet${m.excluded ? " is-excluded" : ""}`}>
      <span className="mf-meet-when">
        {formatDate(m.date)}
        <span className="mf-meet-comp">
          <CompetitionMark code={m.competition} />
          <span className="mf-meet-comp-name">
            {m.competition_name}
            {m.stage ? ` · ${m.stage}` : ""}
          </span>
        </span>
      </span>
      <span className="mf-meet-score">
        <TeamLink teamKey={m.home_team} className="mf-meet-home">
          {m.home_name}
        </TeamLink>
        <b>
          {m.home_goals ?? "–"}–{m.away_goals ?? "–"}
        </b>
        <TeamLink teamKey={m.away_team} className="mf-meet-away">
          {m.away_name}
        </TeamLink>
        {m.neutral && <span className="muted small"> (neutral)</span>}
        {m.extra_time && <span className="muted small"> {m.penalties ? "pens" : "aet"}</span>}
      </span>
      {r ? (
        <span className={`pip pip-${r}`} title={`${RESULT_WORD[r]} for ${name}`}>
          {r}
          <span className="visually-hidden"> ({RESULT_WORD[r]} for {name})</span>
        </span>
      ) : (
        <span />
      )}
      {m.excluded && <span className="mf-meet-flag">Flagged: {m.excluded}</span>}
    </li>
  );
}
