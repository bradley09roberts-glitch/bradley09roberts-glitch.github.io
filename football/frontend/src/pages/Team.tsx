// One team: a hero in the club's colours (badge, name, country, this season's competitions,
// home ground with its photo and credit, descriptive strength and rank), then five tabs:
// Overview (record, form, streaks, upcoming fixtures with our published prediction, averages,
// rates, home and away, strength over time), Matches (the match log, ./TeamMatches.tsx), and
// League, Europe and Cups (the same summaries for one competition type, by competition and by
// season).
//
// Everything comes from GET /api/teams/{key}, /matches and /media, each asked for without
// parameters so a static snapshot can answer them (docs/snapshot.md). Nothing is estimated
// here: a figure the source doesn't have is shown as missing, never filled in.

import { useRef, useState, type CSSProperties, type ReactNode } from "react";
import {
  endpoints,
  useApi,
  type ApiResult,
  type CompetitionType,
  type FullSummary,
  type ShortSummary,
  type Streaks,
  type TeamMatch,
  type TeamMatchesResponse,
  type TeamMedia,
  type TeamProfile,
  type TypeSummary,
  type UpcomingFixture,
} from "../api";
import { BasisBadge } from "../components/Badge";
import { CompetitionChip, CompetitionMark } from "../components/CompetitionMark";
import { TableScroll, Tabs, tabId, tabPanelId, type TabDef } from "../components/Controls";
import { LineChart, type LinePoint } from "../components/LineChart";
import { BackLink } from "../components/Nav";
import { ApiView, Empty } from "../components/StateViews";
import { FormPips } from "../components/Svg";
import { TeamBadge } from "../components/TeamBadge";
import { teamHref } from "../components/TeamLink";
import {
  CompareBars,
  FormStrip,
  OpponentName,
  RateMeters,
  RecordTiles,
  ResultSplit,
  averageRows,
  formItems,
  missingStats,
  ordinal,
  plural,
  signed,
  statsNote,
  type FormItem,
} from "../components/TeamStats";
import { contrast, mix, parseHex, toHex, type Rgb } from "../colour";
import { UEFA_HELD, countryName, seasonCompetitions, sortByCompetition } from "../competitions";
import { formatDate, formatKickoff, formatNumber, formatPct, keepNumberWithName, parseUtc, seasonLabel } from "../format";
import { useDocumentTitle, useMediaQuery } from "../hooks";
import { canGoBack, href, setQuery } from "../router";
import type { PageProps } from ".";
import { MatchLog } from "./TeamMatches";
import "./Teams.css";

type TeamTab = "overview" | "matches" | "league" | "europe" | "cups";

const TYPE_OF_TAB: Record<"league" | "europe" | "cups", CompetitionType> = {
  league: "league",
  europe: "europe",
  cups: "cup",
};

function parseTab(raw: string | null): TeamTab {
  return raw === "matches" || raw === "league" || raw === "europe" || raw === "cups" ? raw : "overview";
}

export function Team({ params, query }: PageProps) {
  const key = params.key ?? "";
  const profile = useApi(key ? endpoints.team(key) : null, { freshMs: 120_000 });
  const log = useApi(key ? endpoints.teamMatches(key) : null, { freshMs: 120_000 });
  const media = useApi(key ? endpoints.teamMedia(key) : null, { freshMs: 600_000 });
  useDocumentTitle(profile.data ? (profile.data.team.display_name ?? profile.data.team.name) : "Team");
  const tab = parseTab(query.get("tab"));

  const back = <BackLink to="/teams">{canGoBack() ? "Back" : "Teams"}</BackLink>;

  if (!key || (profile.error?.notFound && !profile.data)) {
    return (
      <>
        {back}
        <h1 className="visually-hidden">Team</h1>
        <Empty title="No such team">
          <p>
            There's no team with the key “{key}”. It may have been mistyped.{" "}
            <a href={href("/teams")}>See every team</a>.
          </p>
        </Empty>
      </>
    );
  }

  return (
    <>
      {back}
      {!profile.data && <h1 className="visually-hidden">Team</h1>}
      <ApiView result={profile} what="this team" loadingLines={3} wayOn={{ to: "/teams", label: "See every team" }}>
        {(p) => <TeamDetail p={p} log={log} media={media} tab={tab} query={query} />}
      </ApiView>
    </>
  );
}

function TeamDetail({
  p,
  log,
  media,
  tab,
  query,
}: {
  p: TeamProfile;
  log: ApiResult<TeamMatchesResponse>;
  media: ApiResult<TeamMedia>;
  tab: TeamTab;
  query: URLSearchParams;
}) {
  const played = (t: CompetitionType) => p.by_type[t]?.played ?? 0;
  const tabs: TabDef<TeamTab>[] = [
    { id: "overview", label: "Overview", hint: "All" },
    { id: "matches", label: "Matches", hint: formatNumber(p.matches.logged, 0) },
    { id: "league", label: "League", hint: formatNumber(played("league"), 0) },
    { id: "europe", label: "Europe", hint: formatNumber(played("europe"), 0) },
  ];
  // Cups only once a club has cup matches: no cup source is held yet (docs/teams.md).
  if (played("cup") > 0) tabs.push({ id: "cups", label: "Cups", hint: formatNumber(played("cup"), 0) });
  const shown = tabs.some((t) => t.id === tab) ? tab : "overview";
  const tabsRef = useRef<HTMLDivElement>(null);
  const choose = (id: TeamTab) => {
    // A tab's own choices (the match log's filters) are left behind with it.
    setQuery({ tab: id === "overview" ? null : id, type: null, season: null }, { replace: false });
    // Bring the chosen view up under the app bar, so a tab doesn't open on the hero again.
    requestAnimationFrame(() => {
      const el = tabsRef.current;
      if (!el) return;
      const top = el.getBoundingClientRect().top;
      if (top < 0 || top > window.innerHeight / 4) {
        const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        el.scrollIntoView({ block: "start", behavior: reduce ? "auto" : "smooth" });
      }
    });
  };
  return (
    <>
      <TeamHero p={p} media={media} />
      <div className="team-tabs" ref={tabsRef}>
        <Tabs tabs={tabs} active={shown} idBase="team" label="Team view" onChange={choose} />
      </div>
      <div className="team-panel" id={tabPanelId("team")} role="tabpanel" aria-labelledby={tabId("team", shown)}>
        {shown === "overview" ? (
          <Overview p={p} log={log} />
        ) : shown === "matches" ? (
          <MatchLog p={p} log={log} query={query} />
        ) : (
          <TypeView p={p} log={log} type={TYPE_OF_TAB[shown]} />
        )}
      </div>
    </>
  );
}

// ---------------------------------------------------------------------------------------------
// Hero

const WHITE: Rgb = [255, 255, 255];
const NIGHT: Rgb = [8, 16, 11];
/** For a club whose main colour is too light to carry white text (yellow, white). */
const NEUTRAL = "#141a16";

/**
 * The hero's colours from the club's official colours (badge background first). The ground is
 * the main colour, darkened towards night only as far as white text needs for 7:1, so the
 * hero's lighter text (80% white) still has 4.5:1 (4.8:1 at worst over every club's colours on
 * 22 October 2026); a main colour too light for that leaves a neutral dark ground. The badge
 * sits on a tile in the second colour (white without one) crossed by a sash in the main one.
 * Without colours the hero keeps the site's pitch.
 */
export function clubTheme(colours: readonly string[] | undefined): CSSProperties | null {
  const cols = (colours ?? []).map(parseHex).filter((c): c is Rgb => c !== null);
  const [main, second] = cols;
  if (!main) return null;
  let base: string | null = null;
  for (let t = 0; t <= 0.6001; t += 0.05) {
    const c = mix(main, NIGHT, t);
    if (contrast(c, WHITE) >= 7) {
      base = toHex(c);
      break;
    }
  }
  return {
    ["--th-base" as string]: base ?? NEUTRAL,
    ["--th-main" as string]: toHex(main),
    ["--th-second" as string]: second ? toHex(second) : "#ffffff",
  };
}

function TeamHero({ p, media }: { p: TeamProfile; media: ApiResult<TeamMedia> }) {
  const t = p.team;
  const theme = clubTheme(t.media?.badge.colours);
  const country = countryName(t.country);
  const comps = seasonCompetitions(t.competitions_this_season);
  const s = p.strength;
  const last5 = formItems(p.form.last_5).map((f) => f.result);
  return (
    <header className={`team-hero${theme ? " has-colours" : ""}`} style={theme ?? undefined}>
      <div className="th-inner">
        <div className="th-id">
          <span className="th-tile" aria-hidden="true">
            <TeamBadge teamKey={t.key} name={t.name} media={t.media} size={76} className="th-badge" />
          </span>
          <div className="th-titles">
            <p className="th-eyebrow">
              {[country, t.division ? `${t.division.name}${t.division.current ? "" : ` (to ${t.division.season_label})`}` : null]
                .filter(Boolean)
                .join(" · ") || "Team"}
            </p>
            {/* The club's full name; football-data's short one is on cards and tables. */}
            <h1 className="th-name">{t.display_name ?? t.name}</h1>
          </div>
        </div>

        {comps.length > 0 ? (
          <ul className="th-comps" aria-label="Competitions this season">
            {comps.map((c) => (
              <li key={c.code}>
                <CompetitionChip code={c.code} name={keepNumberWithName(c.name)} detail={c.season_label} />
              </li>
            ))}
          </ul>
        ) : (
          <p className="th-quiet">No competition this season in the data.</p>
        )}

        <dl className="th-facts">
          <div className="th-fact">
            <dt>Strength</dt>
            <dd>
              <span className="th-fact-big">{s.current === null ? "—" : formatNumber(Math.round(s.current), 0)}</span>
              <span className="th-fact-sub">descriptive Elo</span>
            </dd>
          </div>
          <div className="th-fact">
            <dt>Rank</dt>
            <dd>
              <span className="th-fact-big">{s.rank === null ? "—" : ordinal(s.rank)}</span>
              <span className="th-fact-sub">
                {s.rank === null ? "no match in the last year" : `of ${formatNumber(s.ranked_teams, 0)} clubs`}
              </span>
            </dd>
          </div>
          <div className="th-fact">
            <dt>Last 5</dt>
            <dd>
              {last5.length > 0 ? <FormPips results={last5} /> : <span className="th-fact-sub">No counted match</span>}
              {last5.length > 0 && <span className="th-fact-sub">oldest first</span>}
            </dd>
          </div>
        </dl>

        <Venue media={media} />
      </div>
    </header>
  );
}

/** The home ground: its photo with a visible credit when a freely licensed one is held,
 * otherwise a pitch drawing with the ground's name. */
function Venue({ media }: { media: ApiResult<TeamMedia> }) {
  const m = media.data;
  const v = m?.venue ?? null;
  const crest = m?.crest;
  const photo = v?.url ?? null;
  const name = v?.name ?? (m ? "Not recorded" : media.error ? "Not available" : "…");
  return (
    <div className={`th-side${photo ? " has-photo" : ""}`}>
      {/* Without a photo, a phone shows the ground as one line; wider screens draw the pitch. */}
      {!photo && (
        <p className="th-venue-line">
          <span className="th-venue-label">Home ground</span> {name}
        </p>
      )}
      <figure className={`th-venue${photo ? " has-photo" : ""}`}>
        {photo ? (
          <img className="th-photo" src={photo} alt={v?.name ? `${v.name}` : "The home ground"} loading="lazy" decoding="async" />
        ) : (
          <PitchArt />
        )}
        <figcaption className="th-venue-cap">
          <span className="th-venue-label">Home ground</span>
          <span className="th-venue-name">{name}</span>
          {!photo && m && v?.name && <span className="th-venue-note">No freely licensed photo held yet</span>}
        </figcaption>
      </figure>
      {photo && v && <Credit what="Photo" pic={v} />}
      {crest && crest.kind === "crest" && crest.url && <Credit what="Crest" pic={crest} />}
      {crest && crest.kind !== "crest" && m?.colours.some((c) => c.hex) && (
        <p className="th-credit">
          Badge drawn from the club's colours (
          {m.wikidata_url ? (
            <a href={m.wikidata_url} rel="noopener noreferrer" target="_blank">
              Wikidata
            </a>
          ) : (
            "Wikidata"
          )}
          , CC0), not the club's crest.
        </p>
      )}
    </div>
  );
}

/** "Photo: Jane Doe · CC BY-SA 4.0 · Wikimedia Commons": author, the licence linked to its
 * text, and the file's page. */
function Credit({ what, pic }: { what: string; pic: TeamMedia["crest"] | NonNullable<TeamMedia["venue"]> }) {
  return (
    <p className="th-credit">
      {what}: {pic.author ?? "author not recorded"}
      {pic.licence && (
        <>
          {" · "}
          {pic.licence_url ? (
            <a href={pic.licence_url} rel="noopener noreferrer license" target="_blank">
              {pic.licence}
            </a>
          ) : (
            pic.licence
          )}
        </>
      )}
      {pic.source_url && (
        <>
          {" · "}
          <a href={pic.source_url} rel="noopener noreferrer" target="_blank">
            source
          </a>
        </>
      )}
      {pic.attribution && <span className="visually-hidden"> ({pic.attribution})</span>}
    </p>
  );
}

/** A pitch from above in chalk lines: halfway line, centre circle and both penalty areas. */
function PitchArt() {
  return (
    <svg className="th-pitch" viewBox="0 0 320 180" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false">
      <rect className="th-pitch-grass" x="0" y="0" width="320" height="180" />
      {[0, 1, 2, 3, 4, 5, 6, 7].map((i) => (
        <rect key={i} className="th-pitch-stripe" x={i * 40} y="0" width="20" height="180" />
      ))}
      <g className="th-pitch-lines">
        <rect x="14" y="12" width="292" height="156" rx="2" />
        <line x1="160" y1="12" x2="160" y2="168" />
        <circle cx="160" cy="90" r="26" />
        <rect x="14" y="52" width="40" height="76" />
        <rect x="266" y="52" width="40" height="76" />
        <rect x="14" y="72" width="14" height="36" />
        <rect x="292" y="72" width="14" height="36" />
      </g>
      <circle className="th-pitch-spot" cx="160" cy="90" r="2.5" />
    </svg>
  );
}

// ---------------------------------------------------------------------------------------------
// Shared helpers

/** Counted matches (not flagged, with a result), newest first, optionally of one type. */
function countedRows(log: TeamMatchesResponse | undefined, type?: CompetitionType): TeamMatch[] {
  return (log?.matches ?? []).filter(
    (r) => r.excluded === null && r.result !== null && (type === undefined || r.competition_type === type),
  );
}

/**
 * The last `n` results with their opponents, oldest first, taken from the loaded match log,
 * but only when they are exactly the results the profile's form string has (the log holds
 * the latest page of matches); otherwise the string's results alone.
 */
function formWithOpponents(
  form: string,
  log: TeamMatchesResponse | undefined,
  type?: CompetitionType,
): FormItem[] {
  const plain = formItems(form);
  const rows = countedRows(log, type).slice(0, plain.length).reverse();
  if (rows.length !== plain.length || rows.some((r, i) => r.result !== plain[i]?.result)) return plain;
  return rows.map((r) => ({
    result: r.result as FormItem["result"],
    opponent: { key: r.opponent, name: r.opponent_name, media: log?.media[r.opponent] },
    venue: r.venue,
    score: r.goals_for !== null && r.goals_against !== null ? `${r.goals_for}–${r.goals_against}` : undefined,
    date: r.date,
    competition: r.competition,
  }));
}

function Card({
  title,
  id,
  sub,
  children,
  className,
}: {
  title: string;
  id: string;
  sub?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`card team-card${className ? ` ${className}` : ""}`} aria-labelledby={id}>
      <div className="team-card-head">
        <h2 id={id}>{title}</h2>
        {sub && <p className="team-card-sub">{sub}</p>}
      </div>
      {children}
    </section>
  );
}

function seasonsSpan(p: TeamProfile): string {
  const seasons = p.by_season.map((s) => s.label);
  if (seasons.length === 0) return "";
  return seasons.length === 1 ? (seasons[0] ?? "") : `${seasons.at(-1)} to ${seasons[0]}`;
}

// ---------------------------------------------------------------------------------------------
// Overview

function Overview({ p, log }: { p: TeamProfile; log: ApiResult<TeamMatchesResponse> }) {
  const s = p.summary;
  // From 1024 px the fixtures head the side column; on narrower screens they follow the form.
  const twoColumns = useMediaQuery("(min-width: 1024px)");
  if (s.played === 0) {
    return (
      <Empty title="No counted matches">
        <p>
          {p.matches.logged > 0
            ? `${p.team.name} has ${plural(p.matches.logged, "match", "matches")} in the data, all flagged (awarded, cancelled or a play-off against an outside club), so there is nothing to summarise. They are listed under Matches.`
            : `There are no matches for ${p.team.name} in the data yet.`}
        </p>
      </Empty>
    );
  }
  const span = seasonsSpan(p);
  return (
    <div className="team-layout">
      <div className="team-main">
        <Card title="Record" id="record" sub={`All competitions${span ? ` · ${span}` : ""}`}>
          <RecordTiles s={s} />
          <TypeLines p={p} />
        </Card>

        <FormCard p={p} log={log.data} />

        {!twoColumns && <Upcoming p={p} />}

        <Averages s={s} id="averages" scope="all" europe={(p.by_type.europe?.played ?? 0) > 0} />

        <StrengthCard p={p} />
      </div>
      <aside className="team-aside" aria-label="Fixtures, rates and splits">
        {twoColumns && <Upcoming p={p} />}
        <RatesCard s={s} id="rates" />
        <VenueSplit summaries={p.by_venue} id="venues" />
      </aside>
    </div>
  );
}

/** One line per competition type: P W D L and points, linking to that tab. */
function TypeLines({ p }: { p: TeamProfile }) {
  const rows = (["league", "europe", "cup"] as const)
    .map((t) => ({ t, s: p.by_type[t] }))
    .filter((r): r is { t: "league" | "europe" | "cup"; s: TypeSummary } => !!r.s && r.s.played > 0);
  if (rows.length < 2) return null;
  const tabOf = { league: "league", europe: "europe", cup: "cups" } as const;
  const name = { league: "League", europe: "Europe", cup: "Cups" } as const;
  return (
    <ul className="type-lines">
      {rows.map(({ t, s }) => (
        <li key={t}>
          <a className="type-line-name" href={teamHref(p.team.key, { tab: tabOf[t] })}>
            {name[t]}
          </a>
          {/* Each figure stays on one line; the line breaks only between them. */}
          <span className="type-line-wdl">
            <span>{s.played} played</span> · <span>{s.won}–{s.drawn}–{s.lost}</span> ·{" "}
            <span>
              goals {s.goals_for}–{s.goals_against}
            </span>
          </span>
          <ResultSplit won={s.won} drawn={s.drawn} lost={s.lost} compact />
        </li>
      ))}
    </ul>
  );
}

const STREAKS: readonly { key: string; label: string; unit: [string, string] }[] = [
  { key: "wins", label: "Winning run", unit: ["win", "wins"] },
  { key: "unbeaten", label: "Unbeaten", unit: ["match", "matches"] },
  { key: "scored", label: "Scoring in", unit: ["match", "matches"] },
  { key: "clean_sheets", label: "Clean sheets", unit: ["in a row", "in a row"] },
  { key: "without_a_win", label: "Without a win", unit: ["match", "matches"] },
  { key: "losses", label: "Losing run", unit: ["loss", "losses"] },
  { key: "failed_to_score", label: "Without scoring", unit: ["match", "matches"] },
];

function StreakList({ streaks }: { streaks: Streaks | undefined }) {
  const live = STREAKS.map((d) => ({ d, s: streaks?.[d.key] })).filter((x) => x.s && x.s.matches > 0);
  if (live.length === 0) return <p className="muted small">No current run.</p>;
  return (
    <ul className="streaks">
      {live.map(({ d, s }) => (
        <li key={d.key} className={`streak streak-${d.key}`}>
          <span className="streak-label">{d.label}</span>
          <span className="streak-value">
            {s?.matches}
            <span className="streak-unit"> {s?.matches === 1 ? d.unit[0] : d.unit[1]}</span>
          </span>
          {s?.since && <span className="streak-since">since {formatDate(s.since)}</span>}
        </li>
      ))}
    </ul>
  );
}

function FormCard({ p, log, type }: { p: TeamProfile; log: TeamMatchesResponse | undefined; type?: CompetitionType }) {
  const form = type ? (p.form.by_type[type]?.last_10 ?? "") : p.form.last_10;
  const items = formWithOpponents(form, log, type);
  const byType = (["league", "europe", "cup"] as const).filter((t) => (p.form.by_type[t]?.last_5 ?? "") !== "");
  return (
    <Card title="Form" id={`form-${type ?? "all"}`} sub={`Last ${items.length || 10}, oldest on the left`}>
      <FormStrip items={items} label={`Last ${items.length} results`} />
      {!type && byType.length > 1 && (
        <dl className="form-rows">
          {byType.map((t) => (
            <div key={t} className="form-row">
              <dt>{t === "league" ? "League" : t === "europe" ? "Europe" : "Cups"}, last 5</dt>
              <dd>
                <FormPips results={formItems(p.form.by_type[t]?.last_5).map((f) => f.result)} />
              </dd>
            </div>
          ))}
        </dl>
      )}
      <h3>Current runs</h3>
      <StreakList streaks={type ? p.streaks.by_type[type] : p.streaks.all} />
    </Card>
  );
}

/** Fixtures listed before "Show all". */
const UPCOMING_SHOWN = 4;

function Upcoming({ p }: { p: TeamProfile }) {
  const list = p.upcoming;
  const [all, setAll] = useState(false);
  const shown = all ? list : list.slice(0, UPCOMING_SHOWN);
  return (
    <Card title="Coming up" id="upcoming" sub="Stored UEFA fixtures, and every fixture we have published a prediction for">
      {list.length === 0 ? (
        <p className="muted small">
          No fixture with a published prediction. League fixtures appear here once a prediction is
          saved for them.
        </p>
      ) : (
        <>
          <ol className="upcoming">
            {shown.map((f) => (
              <UpcomingRow key={f.ref} f={f} team={p.team.name} />
            ))}
          </ol>
          {list.length > shown.length && (
            <button type="button" className="show-more" onClick={() => setAll(true)}>
              Show all {list.length} fixtures
            </button>
          )}
        </>
      )}
    </Card>
  );
}

function UpcomingRow({ f, team }: { f: UpcomingFixture; team: string }) {
  const pr = f.prediction;
  const tp = pr?.team ?? null;
  const pick = pr?.pick_for_team ?? null;
  const pickText = pick === "W" ? `${team} win` : pick === "L" ? `${f.opponent_name} win` : pick === "D" ? "Draw" : null;
  const started = f.awaiting_result || (f.kickoff_utc !== null && parseUtc(f.kickoff_utc).getTime() <= Date.now());
  return (
    <li className={`up-row${pr ? "" : " is-bare"}`}>
      <div className="up-head">
        <span className="up-when">{formatKickoff(f.kickoff_utc, f.date)}</span>
        <CompetitionChip code={f.competition} name={f.competition_name} detail={f.stage} />
      </div>
      <div className="up-match">
        <span className="up-venue" title={f.venue === "H" ? "Home" : f.venue === "A" ? "Away" : "Neutral ground"}>
          {f.venue === "H" ? "v" : f.venue === "A" ? "at" : "v"}
          <span className="visually-hidden">{f.venue === "N" ? " (neutral ground)" : ""}</span>
        </span>
        <OpponentName teamKey={f.opponent} name={f.opponent_name} media={f.opponent_media} size={26} />
        {f.venue !== "N" && <span className="up-side">{f.venue === "H" ? "Home" : "Away"}</span>}
      </div>
      {pr && tp ? (
        <div className="up-pred">
          <span className="up-probs" aria-label={`Our prediction for ${team}: win ${formatPct(tp.win)}, draw ${formatPct(tp.draw)}, loss ${formatPct(tp.loss)}`}>
            <span className="up-bar" aria-hidden="true">
              <span className="wdl-seg wdl-W" style={{ flexGrow: tp.win }} />
              <span className="wdl-seg wdl-D" style={{ flexGrow: tp.draw }} />
              <span className="wdl-seg wdl-L" style={{ flexGrow: tp.loss }} />
            </span>
            <span className="up-figs" aria-hidden="true">
              <span>
                <b>Win</b> {formatPct(tp.win)}
              </span>
              <span>
                <b>Draw</b> {formatPct(tp.draw)}
              </span>
              <span>
                <b>Loss</b> {formatPct(tp.loss)}
              </span>
            </span>
          </span>
          <span className="up-foot">
            {pickText && (
              <span className="up-pick">
                <span className="pick-tag">Pick</span> {pickText}
              </span>
            )}
            {pr.basis === "model_and_market" && <BasisBadge basis={pr.basis} />}
            <a className="up-link" href={href(`/match/${pr.id}`)}>
              Match page <span aria-hidden="true">→</span>
            </a>
          </span>
          {started && <span className="up-status">Kicked off · awaiting the result</span>}
        </div>
      ) : (
        <p className="muted small up-none">
          {started ? "Kicked off · awaiting the result. " : ""}No prediction published for this fixture.
        </p>
      )}
    </li>
  );
}

function Averages({
  s,
  id,
  scope,
  europe = false,
}: {
  s: FullSummary;
  id: string;
  scope: "all" | CompetitionType;
  /** The club has UEFA matches among those summarised. */
  europe?: boolean;
}) {
  const rows = averageRows(s.per_match);
  const goals = s.per_match.goals?.matches ?? 0;
  const stats = s.per_match.shots?.matches ?? 0;
  const note = statsNote(scope, goals, stats, europe);
  // Listed only where some statistics are held; with none, the note above says why.
  const missing = stats > 0 ? missingStats(s.per_match) : [];
  return (
    <Card
      title="Per match"
      id={id}
      sub="Average for and against, with the number of matches behind each"
    >
      {rows.length === 0 ? (
        <p className="muted small">No averages: no counted matches.</p>
      ) : (
        <CompareBars rows={rows} left="For" right="Against" tone="for-against" caption="Per-match averages, for and against" />
      )}
      {note && <p className="note-line">{note}</p>}
      {missing.length > 0 && rows.length > 0 && (
        <p className="muted small">
          Not in the data for these matches: {missing.map((m) => (m === "xG" ? m : m.toLowerCase())).join(", ")}.
        </p>
      )}
      {s.per_match.fd_xg && s.per_match.fd_xg.matches > 0 && (
        <p className="muted small">
          xG is football-data's own figure (from 2026/27), not this site's; no model uses it.
        </p>
      )}
    </Card>
  );
}

function RatesCard({ s, id, europe = false }: { s: FullSummary; id: string; europe?: boolean }) {
  const h = s.half_time;
  return (
    <Card title="How matches go" id={id} sub={`Share of ${plural(s.played, "counted match", "counted matches")}`}>
      <RateMeters rates={s.rates} played={s.played} />
      <h3>From half time</h3>
      {h.matches_with_half_time_score === 0 ? (
        <p className="muted small">
          {europe
            ? "No half-time scores: the UEFA sources give the final score only."
            : "No half-time scores for these matches."}
        </p>
      ) : (
        <ul className="ht-list">
          <li className="ht">
            <span className="ht-head">
              Behind at half time <b>{h.behind_at_half_time.matches}</b>
            </span>
            <ResultSplit won={h.behind_at_half_time.won} drawn={h.behind_at_half_time.drawn} lost={h.behind_at_half_time.lost} />
            <span className="ht-note">
              Comebacks: {plural(h.behind_at_half_time.points_won, "point")} won from behind
            </span>
          </li>
          <li className="ht">
            <span className="ht-head">
              Ahead at half time <b>{h.ahead_at_half_time.matches}</b>
            </span>
            <ResultSplit won={h.ahead_at_half_time.won} drawn={h.ahead_at_half_time.drawn} lost={h.ahead_at_half_time.lost} />
            <span className="ht-note">
              Leads lost: {plural(h.ahead_at_half_time.points_dropped, "point")} dropped from ahead
            </span>
          </li>
        </ul>
      )}
      {h.matches_with_half_time_score > 0 && h.matches_with_half_time_score < s.played && (
        <p className="muted small">
          Half-time scores are known for {formatNumber(h.matches_with_half_time_score, 0)} of the{" "}
          {formatNumber(s.played, 0)} matches (league matches with full statistics).
        </p>
      )}
    </Card>
  );
}

const VENUES = [
  { k: "H", label: "Home" },
  { k: "A", label: "Away" },
  { k: "N", label: "Neutral" },
] as const;

function VenueSplit({ summaries, id }: { summaries: Partial<Record<"H" | "A" | "N", ShortSummary>>; id: string }) {
  const rows = VENUES.map((v) => ({ ...v, s: summaries[v.k] })).filter(
    (r): r is (typeof r & { s: ShortSummary }) => !!r.s && r.s.played > 0,
  );
  return (
    <Card title="Home and away" id={id}>
      {rows.length === 0 ? (
        <p className="muted small">No counted matches.</p>
      ) : (
        <ul className="venue-split">
          {rows.map((r) => (
            <li key={r.k} className="vs-row">
              <span className="vs-head">
                <span className="vs-name">{r.label}</span>
                <span className="vs-count">{plural(r.s.played, "match", "matches")}</span>
              </span>
              <ResultSplit won={r.s.won} drawn={r.s.drawn} lost={r.s.lost} />
              <span className="vs-figs">
                Goals {r.s.goals_for}–{r.s.goals_against} ({signed(r.s.goal_difference)})
                {r.s.points_per_game !== null && <> · {formatNumber(r.s.points_per_game, 2)} points per league game</>}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

// ---------------------------------------------------------------------------------------------
// Strength over time

const DAY = 86_400_000;
const dayNumber = (iso: string) => Math.round(Date.parse(`${iso.slice(0, 10)}T12:00:00Z`) / DAY);
const monthYear = new Intl.DateTimeFormat("en-GB", { month: "short", year: "numeric", timeZone: "UTC" });

function StrengthCard({ p }: { p: TeamProfile }) {
  const s = p.strength;
  const series = s.series;
  if (series.length < 2) {
    return (
      <Card title="Strength over time" id="strength">
        <p className="muted small">Not enough counted matches to draw the rating over time.</p>
      </Card>
    );
  }
  const fullFrom = s.series_points?.every_match_from ?? null;
  const points: LinePoint[] = series.map((pt) => ({
    x: dayNumber(pt.date),
    y: pt.rating,
    label: `${pt.competition}${fullFrom && pt.date < fullFrom ? " · the month's last match" : ""}`,
  }));
  const first = series[0];
  const last = series.at(-1);
  const years: { x: number; label: string }[] = [];
  const y0 = Number((first?.date ?? "").slice(0, 4));
  const y1 = Number((last?.date ?? "").slice(0, 4));
  for (let y = y0 + 1; y <= y1; y++) years.push({ x: dayNumber(`${y}-01-01`), label: String(y) });
  const ticks = years.length > 6 ? years.filter((_, i) => (years.length - 1 - i) % 2 === 0) : years;
  const low = series.reduce((a, b) => (b.rating < a.rating ? b : a), series[0] ?? { rating: 0, date: "", competition: "" });
  return (
    <Card title="Strength over time" id="strength" sub="Descriptive Elo after each counted match">
      <LineChart
        series={[{ key: "elo", label: "Strength", tone: "accent", points }]}
        xTicks={ticks}
        xLabel={(x) => formatDate(new Date(x * DAY).toISOString().slice(0, 10))}
        yFormat={(v) => formatNumber(Math.round(v), 0)}
        markers={s.peak ? [{ x: dayNumber(s.peak.date), y: s.peak.rating, label: "Peak" }] : []}
        label={`Strength rating over time for ${p.team.name}: from ${formatNumber(Math.round(first?.rating ?? 0), 0)} in ${monthYear.format(new Date(`${first?.date}T12:00:00Z`))} to ${formatNumber(Math.round(last?.rating ?? 0), 0)} on ${formatDate(last?.date)}.`}
        height={230}
      />
      <ul className="strength-facts">
        <li>
          <span className="sf-label">Now</span>
          <span className="sf-value">{s.current === null ? "—" : formatNumber(Math.round(s.current), 0)}</span>
          <span className="sf-sub">{s.rank !== null ? `${ordinal(s.rank)} of ${formatNumber(s.ranked_teams, 0)}` : "not ranked"}</span>
        </li>
        {s.peak && (
          <li>
            <span className="sf-label">
              <span className="sf-peak" aria-hidden="true" />
              Peak
            </span>
            <span className="sf-value">{formatNumber(Math.round(s.peak.rating), 0)}</span>
            <span className="sf-sub">{formatDate(s.peak.date)}</span>
          </li>
        )}
        <li>
          <span className="sf-label">Lowest shown</span>
          <span className="sf-value">{formatNumber(Math.round(low.rating), 0)}</span>
          <span className="sf-sub">{formatDate(low.date)}</span>
        </li>
      </ul>
      <p className="muted small">
        A goal-based Elo over every league and UEFA match held: it describes results and makes no
        forecast (not a model of record). Rank is among {formatNumber(s.ranked_teams, 0)} clubs with
        a match in the last year.
        {fullFrom && s.series_points
          ? ` Every match from ${formatDate(fullFrom)}; before that, one point a month (${formatNumber(s.series_points.shown, 0)} of ${formatNumber(s.series_points.counted_matches, 0)} matches drawn).`
          : ""}
      </p>
    </Card>
  );
}

// ---------------------------------------------------------------------------------------------
// League, Europe, Cups

const TYPE_TEXT: Record<CompetitionType, { name: string; matches: string }> = {
  league: { name: "League", matches: "league matches" },
  europe: { name: "Europe", matches: "UEFA matches" },
  cup: { name: "Cups", matches: "cup matches" },
};

function TypeView({ p, log, type }: { p: TeamProfile; log: ApiResult<TeamMatchesResponse>; type: CompetitionType }) {
  const s = p.by_type[type];
  if (type === "cup" && (!s || s.played === 0)) {
    return (
      <Empty title="Cup competitions will appear here">
        <p>
          Domestic cups (the FA Cup, Copa del Rey and the like) will appear here when a cup data
          source is added. Today the data holds league matches (football-data.co.uk) and the UEFA
          competitions (openfootball and Wikipedia).
        </p>
      </Empty>
    );
  }
  if (!s || s.played === 0) {
    return (
      <Empty title={`No ${TYPE_TEXT[type].matches} for ${p.team.name}`}>
        <p>
          {type === "europe"
            ? `There's no Champions League, Europa League or Conference League match for this club in the data. ${UEFA_HELD}`
            : "There's no league match for this club in the leagues held."}
        </p>
      </Empty>
    );
  }
  const comps = sortByCompetition(
    p.by_competition.filter((c) => c.type === type),
    (c) => c.competition,
  );
  const codes = new Set(comps.map((c) => c.competition));
  const seasons = p.by_season
    .map((x) => ({
      season: x.season,
      label: x.label,
      competitions: sortByCompetition(
        x.competitions.filter((c) => codes.has(c)),
        (c) => c,
      ),
      s: x.by_type[type],
    }))
    .filter((x): x is SeasonRow => !!x.s && x.s.played > 0);
  return (
    <>
      {type === "europe" && (
        <div className="notice-info" role="note">
          <strong>European matches carry scores only.</strong> The sources we may use for UEFA
          competitions give results, stages and extra-time and penalty flags, but no shots, cards,
          corners, fouls or half-time scores. Scores are after 90 minutes. {UEFA_HELD}
        </div>
      )}
      <div className="team-layout">
        <div className="team-main">
          <Card title="Record" id={`record-${type}`} sub={`${TYPE_TEXT[type].name} · ${plural(comps.length, "competition")}`}>
            <RecordTiles s={s} />
          </Card>
          <FormCard p={p} log={log.data} type={type} />
          <Averages s={s} id={`averages-${type}`} scope={type} />
          <Card title="By competition" id={`comps-${type}`}>
            <CompetitionLines rows={comps} />
          </Card>
          <Card title="By season" id={`seasons-${type}`} sub="Newest first">
            <SeasonTable
              rows={seasons}
              league={type === "league"}
              names={comps.length > 1 ? new Map(comps.map((c) => [c.competition, c.name])) : null}
            />
          </Card>
        </div>
        <aside className="team-aside" aria-label="Rates and splits">
          <RatesCard s={s} id={`rates-${type}`} europe={type === "europe"} />
          <VenueSplit summaries={s.by_venue} id={`venues-${type}`} />
        </aside>
      </div>
    </>
  );
}

/** One line per competition: its mark, name and seasons, then the record and a won-drawn-lost
 * bar (the counts are printed beside it). */
function CompetitionLines({ rows }: { rows: TeamProfile["by_competition"] }) {
  if (rows.length === 0) return <p className="muted small">None.</p>;
  return (
    <ul className="comp-lines">
      {rows.map((c) => {
        const x = c.summary;
        const first = c.seasons.at(-1);
        const last = c.seasons[0];
        return (
          <li key={c.competition}>
            <span className="cl-head">
              <CompetitionMark code={c.competition} />
              <span className="cl-name">{c.name}</span>
              <span className="cl-seasons">
                {plural(c.seasons.length, "season")}
                {first && last && ` · ${seasonLabel(first)}${first !== last ? `–${seasonLabel(last)}` : ""}`}
              </span>
            </span>
            <span className="cl-figs">
              <span>{plural(x.played, "match", "matches")}</span> ·{" "}
              <span>
                {x.won}–{x.drawn}–{x.lost}
              </span>{" "}
              · <span>goals {x.goals_for}–{x.goals_against}</span>
              {x.points_per_game !== null && (
                <>
                  {" "}
                  · <span>{formatNumber(x.points_per_game, 2)} points per game</span>
                </>
              )}
            </span>
            <ResultSplit won={x.won} drawn={x.drawn} lost={x.lost} compact />
          </li>
        );
      })}
    </ul>
  );
}

type SeasonRow = { season: string; label: string; competitions: string[]; s: ShortSummary };

/** Newest season first: played, won-drawn-lost, goals (with the difference under them) and, for
 * leagues, points. Where the club has played more than one competition of the type (a league
 * after promotion, two UEFA competitions), each season names its own. */
function SeasonTable({
  rows,
  league,
  names,
}: {
  rows: SeasonRow[];
  league: boolean;
  /** Competition code -> name, when each season should name its competitions. */
  names: Map<string, string> | null;
}) {
  if (rows.length === 0) return <p className="muted small">None.</p>;
  return (
    <TableScroll label="Record by season">
      <table className="data-table team-table">
        <thead>
          <tr>
            <th scope="col" className="sticky-col">
              Season
            </th>
            <th scope="col" className="num">
              P
            </th>
            <th scope="col" className="num">
              W–D–L
            </th>
            <th scope="col" className="num">
              Goals
            </th>
            {league && (
              <th scope="col" className="num">
                Pts
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {rows.map(({ season, label, competitions, s }) => (
            <tr key={season}>
              <th scope="row" className="sticky-col">
                <span className="tt-season">
                  <span className="tt-season-label">{label}</span>
                  {names &&
                    competitions.map((c) => (
                      <span key={c} className="tt-season-mark" title={names.get(c) ?? c}>
                        <CompetitionMark code={c} />
                        <span className="visually-hidden">{names.get(c) ?? c}</span>
                      </span>
                    ))}
                </span>
              </th>
              <td className="num">{s.played}</td>
              <td className="num">
                {s.won}–{s.drawn}–{s.lost}
              </td>
              <td className="num">
                {s.goals_for}–{s.goals_against}
                <span className="cell-sub">{signed(s.goal_difference)}</span>
              </td>
              {league && <td className="num">{s.league_points ?? "—"}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </TableScroll>
  );
}
