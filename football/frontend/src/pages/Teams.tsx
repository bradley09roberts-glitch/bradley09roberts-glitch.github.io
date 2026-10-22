// Teams: every club with a stored match, to search and filter by this season's competitions.
// Each card shows the badge, name, country and league, this season's competitions, the last
// five results and the descriptive strength rank, and opens the team's page.
//
// Everything comes from GET /api/teams, asked for without parameters (so a static snapshot
// can answer it); the search, filters and order are applied here. In a snapshot only the teams
// it holds pages for are listed.

import { useEffect, useLayoutEffect, useMemo, useRef, useState, type FocusEvent } from "react";
import { endpoints, useApi, type MediaSummary, type TeamListEntry, type TeamsResponse } from "../api";
import { CompetitionMark } from "../components/CompetitionMark";
import { ApiView, Empty } from "../components/StateViews";
import { FormPips } from "../components/Svg";
import { TeamBadge } from "../components/TeamBadge";
import { teamHref, useTeamPageCheck } from "../components/TeamLink";
import { formItems, ordinal } from "../components/TeamStats";
import { competitionRank, countryName, sortByCompetition } from "../competitions";
import { formatDate, formatNumber } from "../format";
import { useDocumentTitle } from "../hooks";
import { setQuery } from "../router";
import { SNAPSHOT } from "../snapshot";
import type { PageProps } from ".";
import "./Teams.css";

/** Cards drawn at a time; "Show more" adds another page. */
const PAGE = 48;
/** Competitions with their own chip; the rest are in the "Other competitions" list. */
const CHIP_CODES = ["UCL", "UEL", "UECL", "E0", "SP1", "I1", "D1", "F1", "E1"];

type Sort = "strength" | "name";
type Scope = "season" | "all";

/** Case, accents and punctuation ignored: "Atlético" matches "atletico". */
export function fold(s: string): string {
  return s
    .normalize("NFD")
    .replace(/\p{M}+/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

export function Teams({ query }: PageProps) {
  useDocumentTitle("Teams");
  const result = useApi(endpoints.teams(), { freshMs: 300_000 });

  return (
    <>
      <header className="page-head">
        <h1 className="page-title">Teams</h1>
        <p className="lede">
          Every club with a league or UEFA match in the data: how each does in its league and in
          Europe, its form, its matches and a descriptive strength rating.
        </p>
      </header>
      <ApiView result={result} what="the teams" loadingLines={3}>
        {(data) => <TeamIndex data={data} query={query} />}
      </ApiView>
    </>
  );
}

function TeamIndex({ data, query }: { data: TeamsResponse; query: URLSearchParams }) {
  const hasPage = useTeamPageCheck();
  const q = query.get("q") ?? "";
  const competition = query.get("competition");
  const sort: Sort = query.get("sort") === "name" ? "name" : "strength";
  const scope: Scope = query.get("scope") === "all" ? "all" : "season";
  const shown = Math.max(PAGE, Number.parseInt(query.get("shown") ?? "", 10) || PAGE);
  // The box follows typing at once; the address follows it (replacing the entry).
  const [text, setText] = useState(q);
  // Back and Forward change the address: the box follows (unless it already says the same).
  useEffect(() => {
    setText((t) => (fold(t) === fold(q) ? t : q));
  }, [q]);

  // In a snapshot, only the teams it has pages for.
  const listed = useMemo(() => data.teams.filter((t) => hasPage(t.key)), [data.teams, hasPage]);

  const competitions = useMemo(() => {
    const counts = new Map<string, { code: string; name: string; count: number }>();
    for (const t of listed) {
      for (const c of t.competitions_this_season) {
        const e = counts.get(c.code);
        if (e) e.count += 1;
        else counts.set(c.code, { code: c.code, name: c.name, count: 1 });
      }
    }
    return sortByCompetition([...counts.values()], (c) => c.code);
  }, [listed]);
  const chips = competitions.filter((c) => CHIP_CODES.includes(c.code));
  const others = competitions.filter((c) => !CHIP_CODES.includes(c.code));

  const teams = useMemo(() => {
    const needle = fold(q);
    const rows = listed.filter((t) => {
      if (competition) {
        if (!t.competitions_this_season.some((c) => c.code === competition)) return false;
      } else if (scope === "season" && !needle && t.competitions_this_season.length === 0) {
        return false;
      }
      return !needle || fold(t.name).includes(needle) || t.key.includes(needle.replace(/ /g, "-"));
    });
    const byName = (a: TeamListEntry, b: TeamListEntry) => a.name.localeCompare(b.name, "en-GB", { sensitivity: "base" });
    return rows.sort(
      sort === "name"
        ? byName
        : (a, b) => (a.elo_rank ?? Infinity) - (b.elo_rank ?? Infinity) || byName(a, b),
    );
  }, [listed, q, competition, scope, sort]);

  const active = competition ? competitions.find((c) => c.code === competition) : undefined;

  // On a phone the competition chips scroll sideways: keep the chosen one in view.
  const chipsRef = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    const row = chipsRef.current;
    const chip = row?.querySelector<HTMLElement>('[aria-pressed="true"]');
    if (!row || !chip || row.scrollWidth <= row.clientWidth) return;
    row.scrollLeft = chip.offsetLeft - (row.clientWidth - chip.offsetWidth) / 2;
  }, [competition]);
  const onChipFocus = (e: FocusEvent<HTMLDivElement>) => {
    const row = e.currentTarget;
    const chip = e.target;
    if (chip === row || row.scrollWidth <= row.clientWidth) return;
    const left = chip.offsetLeft - 28;
    const right = chip.offsetLeft + chip.offsetWidth + 28 - row.clientWidth;
    if (row.scrollLeft > left) row.scrollLeft = left;
    else if (row.scrollLeft < right) row.scrollLeft = right;
  };

  const choose = (code: string | null) => setQuery({ competition: code, shown: null });

  return (
    <>
      <section className="card tm-tools" aria-label="Find a team">
        <div className="tm-search">
          <label htmlFor="team-search" className="ctl-label">
            Search
          </label>
          <input
            id="team-search"
            type="search"
            className="tm-input"
            placeholder="A club's name, e.g. Villa"
            autoComplete="off"
            spellCheck={false}
            value={text}
            onChange={(e) => {
              setText(e.target.value);
              setQuery({ q: e.target.value.trim() || null, shown: null });
            }}
          />
        </div>
        <div className="tm-tool-row">
          <div className="filters" role="group" aria-label="Order">
            <span className="filter-label">Order</span>
            <div className="segmented">
              <button type="button" aria-pressed={sort === "strength"} onClick={() => setQuery({ sort: null })}>
                Strength
              </button>
              <button type="button" aria-pressed={sort === "name"} onClick={() => setQuery({ sort: "name" })}>
                Name
              </button>
            </div>
          </div>
          {!competition && (
            <div className="filters" role="group" aria-label="Which teams">
              <span className="filter-label">Show</span>
              <div className="segmented">
                <button type="button" aria-pressed={scope === "season"} onClick={() => setQuery({ scope: null, shown: null })}>
                  This season
                </button>
                <button type="button" aria-pressed={scope === "all"} onClick={() => setQuery({ scope: "all", shown: null })}>
                  Every team
                </button>
              </div>
            </div>
          )}
        </div>
      </section>

      <div className="chips chips-scroll" role="group" aria-label="Competition this season" ref={chipsRef} onFocus={onChipFocus}>
        <button type="button" className="chip" aria-pressed={competition === null} onClick={() => choose(null)}>
          All
        </button>
        {chips.map((c) => (
          <button
            key={c.code}
            type="button"
            className="chip"
            aria-pressed={competition === c.code}
            onClick={() => choose(c.code)}
          >
            <CompetitionMark code={c.code} />
            {c.name} <span className="chip-count">{c.count}</span>
          </button>
        ))}
        {others.length > 0 && (
          <label className={`chip chip-select${active && !CHIP_CODES.includes(active.code) ? " is-on" : ""}`}>
            <span className="visually-hidden">Other competitions</span>
            <select
              value={active && !CHIP_CODES.includes(active.code) ? active.code : ""}
              onChange={(e) => choose(e.target.value || null)}
            >
              <option value="">Other competitions…</option>
              {others
                .slice()
                .sort((a, b) => competitionRank(a.code) - competitionRank(b.code) || a.name.localeCompare(b.name))
                .map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.name} ({c.count})
                  </option>
                ))}
            </select>
          </label>
        )}
      </div>

      <p className="muted small updated">
        {formatNumber(teams.length, 0)} {teams.length === 1 ? "team" : "teams"}
        {active ? ` in ${active.name} this season` : scope === "season" && !q ? " with a match this season" : ""}
        {data.latest_match_held && <> · results to {formatDate(data.latest_match_held)}</>}
        {SNAPSHOT && (
          <>
            {" "}
            · this snapshot holds pages for {formatNumber(listed.length, 0)} of the {formatNumber(data.teams.length, 0)}{" "}
            teams
          </>
        )}
      </p>

      {teams.length === 0 ? (
        <Empty
          title={q ? `No team matches “${q}”` : "No teams here"}
          actions={
            <button
              type="button"
              onClick={() => {
                setText("");
                setQuery(q ? { q: null, shown: null } : { competition: null, scope: "all", shown: null });
              }}
            >
              {q ? "Clear the search" : "Show every team"}
            </button>
          }
        >
          <p>
            {q
              ? "Try part of the name, without the club's initials (e.g. “Villa” rather than “AVFC”)."
              : "No team with a page here plays in this competition this season."}
          </p>
        </Empty>
      ) : (
        <>
          <ol className="tm-grid">
            {teams.slice(0, shown).map((t) => (
              <TeamCard key={t.key} team={t} media={data.media[t.key]} />
            ))}
          </ol>
          {teams.length > shown && (
            <button type="button" className="show-more" onClick={() => setQuery({ shown: shown + PAGE })}>
              Show {Math.min(PAGE, teams.length - shown)} more of {formatNumber(teams.length - shown, 0)}
            </button>
          )}
        </>
      )}
    </>
  );
}

function TeamCard({ team: t, media }: { team: TeamListEntry; media: MediaSummary | undefined }) {
  const country = countryName(t.country);
  const comps = sortByCompetition(t.competitions_this_season, (c) => c.code);
  const form = formItems(t.form_last_5).map((f) => f.result);
  const division = t.division;
  return (
    <li className="card tm-card">
      <TeamBadge teamKey={t.key} name={t.name} media={media} size={44} className="tm-badge" />
      <div className="tm-main">
        <h2 className="tm-name">
          <a className="stretched" href={teamHref(t.key)}>
            {t.name}
          </a>
        </h2>
        <p className="tm-meta">
          {[country, division ? `${division.name}${division.current ? "" : ` (to ${division.season_label})`}` : null]
            .filter(Boolean)
            .join(" · ") || "No league held"}
        </p>
        {comps.length > 0 && (
          <ul className="tm-comps" aria-label="This season">
            {comps.map((c) => (
              <li key={c.code} title={c.name}>
                <CompetitionMark code={c.code} />
                <span className="visually-hidden">{c.name}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="tm-side">
        {t.elo_rank !== null ? (
          <span className="tm-rank">
            <span className="tm-rank-label">Rank</span>
            <span className="tm-rank-value">{ordinal(t.elo_rank)}</span>
          </span>
        ) : (
          <span className="tm-rank tm-unranked">Not ranked</span>
        )}
        {t.elo !== null && <span className="tm-elo">Strength {formatNumber(Math.round(t.elo), 0)}</span>}
        {form.length > 0 ? <FormPips results={form} /> : <span className="tm-elo">No recent matches</span>}
      </div>
    </li>
  );
}
