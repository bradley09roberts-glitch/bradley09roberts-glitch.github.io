"""Coverage per division, season and field. Counts only: no source rows or prices leave here.

The core ten divisions keep the M1 report. The European leagues (scope "europe", D-022) get
their own section, with repeat meetings of a pairing (`matches.meeting`), any rows held back
beyond a division's `max_meetings`, and price coverage. Prices are imported for every division
since 21 Oct 2026 (D-022, "Prices"), so price coverage is computed for all of them.
"""

import re
from collections import Counter
from datetime import datetime

import pandas as pd
from sqlalchemy import Engine, text

from footy.ingest.fd_extra_parse import PLAYOFF_EXCLUSION
from footy.reference import DIVISIONS
from footy.seasons import season_label

STAT_FIELDS = {
    "kickoff_time": "kickoff_utc",
    "ht_score": "ht_home_goals",
    "shots": "home_shots",
    "shots_on_target": "home_sot",
    "corners": "home_corners",
    "cards": "home_yellow",
    "referee": "referee",
    "fd_xg": "fd_home_xg",
}
# (label, bookmaker, market, phase, selections needed for a complete set)
ODDS_SETS = [
    ("B365 pre 1X2", "B365", "1X2", "pre_closing", 3),
    ("B365 pre O/U2.5", "B365", "OU_2.5", "pre_closing", 2),
    ("B365 close 1X2", "B365", "1X2", "closing", 3),
    ("PS close 1X2", "PS", "1X2", "closing", 3),
    ("PS close O/U2.5", "PS", "OU_2.5", "closing", 2),
    ("BFE close 1X2", "BFE", "1X2", "closing", 3),
    ("BFE close O/U2.5", "BFE", "OU_2.5", "closing", 2),
    ("Avg close 1X2", "Avg", "1X2", "closing", 3),
    ("Avg close O/U2.5", "Avg", "OU_2.5", "closing", 2),
]


def coverage_rows(engine: Engine) -> list[dict]:
    with engine.connect() as conn:
        matches = pd.read_sql(text("SELECT * FROM matches"), conn)
        odds = pd.read_sql(
            text(
                "SELECT o.match_id, o.bookmaker, o.market_key, o.phase, count(*) AS n "
                "FROM fd_odds o GROUP BY 1, 2, 3, 4"
            ),
            conn,
        )
        issues = pd.read_sql(
            text(
                "SELECT r.logical_key, i.severity, count(*) AS n FROM import_issues i "
                "JOIN raw_files r ON r.id = i.raw_file_id GROUP BY 1, 2"
            ),
            conn,
        )
        latest_raw = pd.read_sql(
            text(
                "SELECT logical_key, max(revision) AS revision, max(retrieved_at) AS retrieved_at "
                "FROM raw_files WHERE source = 'football-data' GROUP BY 1"
            ),
            conn,
        )
        latest_issues = pd.read_sql(
            text(
                "SELECT r.source, r.logical_key, r.revision, i.severity, i.code, i.detail "
                "FROM import_issues i JOIN raw_files r ON r.id = i.raw_file_id "
                "WHERE r.revision = (SELECT max(r2.revision) FROM raw_files r2 "
                "WHERE r2.source = r.source AND r2.logical_key = r.logical_key)"
            ),
            conn,
        )
        latest_extra = pd.read_sql(
            text(
                "SELECT logical_key, max(revision) AS revision, max(retrieved_at) AS retrieved_at "
                "FROM raw_files WHERE source = 'football-data-extra' GROUP BY 1"
            ),
            conn,
        )
    if matches.empty:
        return []
    europe_issues = _europe_issue_counts(latest_issues)

    complete = {}
    for label, book, market, phase, need in ODDS_SETS:
        sel = odds[
            (odds.bookmaker == book)
            & (odds.market_key == market)
            & (odds.phase == phase)
            & (odds.n == need)
        ]
        complete[label] = set(sel.match_id)

    issue_map = {(r.logical_key, r.severity): int(r.n) for r in issues.itertuples()}
    raw_map = {r.logical_key: r for r in latest_raw.itertuples()}
    rows = []
    extra_map = {r.logical_key: r for r in latest_extra.itertuples()}
    for (division, season), g in matches.groupby(["division", "season"], sort=False):
        teams = set(g.home_team) | set(g.away_team)
        info = DIVISIONS[division]
        key = f"{season}/{division}"
        n = len(g)
        row = {
            "division": division,
            "division_name": info.name,
            "tier": info.tier,
            "scope": info.scope,
            "layout": info.layout,
            "season": season,
            "season_label": season_label(season),
            "matches": n,
            "teams": len(teams),
            "expected_matches": expected_matches(g),
            "first_date": str(g.match_date.min()),
            "last_date": str(g.match_date.max()),
            "issues_error": issue_map.get((key, "error"), 0),
            "issues_warning": issue_map.get((key, "warning"), 0),
            "raw_revision": int(raw_map[key].revision) if key in raw_map else None,
            "retrieved_at": str(raw_map[key].retrieved_at) if key in raw_map else None,
        }
        for name, col in STAT_FIELDS.items():
            row[f"pct_{name}"] = round(100 * g[col].notna().mean(), 1)
        ids = set(g.id)
        for label, *_ in ODDS_SETS:
            row[f"odds::{label}"] = round(100 * len(ids & complete[label]) / n, 1)
        if info.scope != "core":
            counts = europe_issues.get((division, season), Counter())
            row["held_back"] = counts["repeat_pairing"]
            row["max_meeting"] = int(g.meeting.max())
            row["repeat_meetings"] = int((g.meeting > 1).sum())
            row["model_excluded"] = int(g.model_exclusion.notna().sum())
            row["issues_error"] = counts["error"]
            row["issues_warning"] = counts["warning"]
            if info.layout == "extra":
                raw = extra_map.get(f"new/{division}")
                row["raw_revision"] = int(raw.revision) if raw is not None else None
                row["retrieved_at"] = str(raw.retrieved_at) if raw is not None else None
        rows.append(row)
    order = {code: i for i, code in enumerate(DIVISIONS)}
    rows.sort(key=lambda r: (order[r["division"]], r["season"]))
    return rows


def expected_matches(g: pd.DataFrame) -> int:
    """Matches expected in one division-season: every ordered pairing of its clubs once,
    plus each further meeting already stored (`meeting` > 1, D-022), plus any play-off
    matches against lower-division visitors (who are not counted as clubs). With one meeting
    per pairing (the core ten) this is teams × (teams − 1). Split and multi-round formats
    add meetings as they are played, so for a season in progress it is a lower bound."""
    playoff = g.model_exclusion == PLAYOFF_EXCLUSION
    clubs = set(g.home_team) | set(g.away_team)
    regular = set(g.home_team[~playoff]) | set(g.away_team[~playoff])
    visitors = clubs - regular
    with_visitor = g.home_team.isin(visitors) | g.away_team.isin(visitors)
    further = int((g.meeting[~with_visitor] > 1).sum())
    return len(regular) * (len(regular) - 1) + further + int(with_visitor.sum())


_SEASON_PREFIX = re.compile(r"^(\d{4}(?:/\d{2})?): ")


def _europe_issue_counts(issues: pd.DataFrame) -> dict[tuple[str, str], Counter]:
    """Issue counts per (division, season) for the latest revision of each European file.
    Main-layout files are one season each; extra-league issues carry their season as a
    "2024/25: " or "2024: " prefix (fd_extra_parse)."""
    out: dict[tuple[str, str], Counter] = {}
    for r in issues.itertuples():
        if r.source == "football-data":
            season, division = r.logical_key.split("/")
            if DIVISIONS.get(division) is None or DIVISIONS[division].scope == "core":
                continue
        elif r.source == "football-data-extra":
            division = r.logical_key.split("/")[1]
            m = _SEASON_PREFIX.match(r.detail or "")
            if m is None:
                continue  # file-level, not tied to a season
            season = _season_from_label(m.group(1))
        else:
            continue
        c = out.setdefault((division, season), Counter())
        c[r.severity] += 1
        c[r.code] += 1
    return out


def _season_from_label(label: str) -> str:
    if "/" in label:  # "2024/25"
        return f"{label[2:4]}{label[5:7]}"
    return f"{label[2:4]}{label[2:4]}"  # calendar season "2024" -> "2424"


def coverage_markdown(rows: list[dict], generated_at: datetime) -> str:
    out = [
        "# Data coverage",
        "",
        f"_Generated {generated_at:%Y-%m-%d %H:%M} UTC from the local database by "
        "`uv run footy coverage`. Counts and percentages only._",
        "",
    ]
    if not rows:
        return "\n".join(out + ["No matches imported."])
    europe = [r for r in rows if r.get("scope", "core") != "core"]
    rows = [r for r in rows if r.get("scope", "core") == "core"]

    out += [
        "## Matches",
        "",
        "Expected = teams × (teams − 1), a full double round robin. A shortfall means a",
        "curtailed season or rows excluded at import (see errors).",
        "",
        "| Division | Season | Matches | Expected | Teams | First | Last | Errors | Warnings |",
        "| --- | --- | ---: | ---: | ---: | --- | --- | ---: | ---: |",
    ]
    for r in rows:
        flag = "" if r["matches"] == r["expected_matches"] else " ⚠"
        out.append(
            f"| {r['division']} {r['division_name']} | {r['season_label']} | {r['matches']}{flag} "
            f"| {r['expected_matches']} | {r['teams']} | {r['first_date']} | {r['last_date']} "
            f"| {r['issues_error']} | {r['issues_warning']} |"
        )

    out += ["", "## Fields (% of matches with a value)", ""]
    names = list(STAT_FIELDS)
    out.append("| Division | Season | " + " | ".join(n.replace("_", " ") for n in names) + " |")
    out.append("| --- | --- | " + " | ".join("---:" for _ in names) + " |")
    for r in rows:
        cells = " | ".join(_pct(r[f"pct_{n}"]) for n in names)
        out.append(f"| {r['division']} | {r['season_label']} | {cells} |")

    out += [
        "",
        "## Prices (% of matches with a complete set)",
        "",
        "pre = pre-closing (collected by Fri 17:00 / Tue 13:00 UK, D-012); close = closing.",
        "PS = Pinnacle (unreliable from 23 Jul 2025, D-014); BFE = Betfair Exchange;",
        "Avg = market average. Sets with implausible margins are dropped at import. The",
        'second tiers\' prices, every season, were imported on 21 Oct 2026 (D-022, "Prices").',
        "",
    ]
    labels = [s[0] for s in ODDS_SETS]
    out.append("| Division | Season | " + " | ".join(labels) + " |")
    out.append("| --- | --- | " + " | ".join("---:" for _ in labels) + " |")
    for r in rows:
        cells = " | ".join(_pct(r[f"odds::{lab}"]) for lab in labels)
        out.append(f"| {r['division']} | {r['season_label']} | {cells} |")
    out.append("")
    if europe:
        out += _europe_markdown(europe)
    return "\n".join(out)


def _europe_markdown(rows: list[dict]) -> list[str]:
    out = [
        "## European leagues",
        "",
        "Extra domestic leagues for rating clubs in UEFA competitions (D-022). Results and",
        "kick-off times, plus half-time scores and statistics in the main-layout files",
        '(SC0, N1, B1, P1, T1, G1). Prices are imported for every division (D-022, "Prices"):',
        "the main-layout files have pre-closing and closing 1X2 and O/U 2.5, the extra-league",
        "files closing 1X2 only. They feed the market-implied expected goals of the UEFA",
        "ratings. Seasons are split-year (2024/25) or calendar-year (2024).",
        "",
        "- **Stored**: rows in `matches`, repeat meetings included. In split leagues and",
        "  three- and four-round formats the same home team hosts the same away team more than",
        "  once a season; each such meeting is its own row, numbered by `meeting` in file",
        "  (date) order. **Meetings**: the most times one pairing met that season.",
        "- **Expected**: every ordered pairing of the season's clubs once, plus each further",
        "  meeting stored, plus play-off matches against lower-division visitors. For leagues",
        "  with one meeting per pairing this is teams × (teams − 1), as above. Split formats",
        "  can't be counted from the teams alone, so for a season in progress Expected is a",
        "  lower bound: later meetings count once they are played.",
        "- **Held back**: a further meeting beyond the division's `max_meetings` in",
        "  `reference.py` (the most the raw files show), kept in the raw files and",
        "  `import_issues`.",
        "- **Excluded**: stored but left out of models (bare or awarded results, D-018; play-off",
        "  matches against a lower-division visitor, whose score basis is not stated).",
        "- Errors include the held-back rows.",
        "- **Prices**: % of stored matches with a complete set of the market average's closing",
        "  1X2, Bet365's pre-closing 1X2 and the market average's closing O/U 2.5 (the full",
        "  sets for every book are in the table above for the core ten).",
        "",
        "| Division | Season | Stored | Expected | Meetings | Held back | Teams | First | Last "
        "| Excluded | Errors | Warnings | Kick-off % | HT score % | Shots % "
        "| Avg close 1X2 % | B365 pre 1X2 % | Avg close O/U2.5 % |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- | ---: | ---: | ---: | ---: "
        "| ---: | ---: | ---: | ---: | ---: |",
    ]
    order = {code: i for i, code in enumerate(DIVISIONS)}
    totals: Counter = Counter()
    for r in sorted(rows, key=lambda r: (order[r["division"]], r["season"])):
        totals["stored"] += r["matches"]
        totals["repeat"] += r["repeat_meetings"]
        totals["held_back"] += r["held_back"]
        totals["excluded"] += r["model_excluded"]
        flag = "" if r["matches"] == r["expected_matches"] else " ⚠"
        out.append(
            f"| {r['division']} {r['division_name']} | {r['season_label']} | {r['matches']}{flag} "
            f"| {r['expected_matches']} | {r['max_meeting']} | {r['held_back']} | {r['teams']} "
            f"| {r['first_date']} | {r['last_date']} "
            f"| {r['model_excluded']} | {r['issues_error']} | {r['issues_warning']} "
            f"| {_pct(r['pct_kickoff_time'])} | {_pct(r['pct_ht_score'])} "
            f"| {_pct(r['pct_shots'])} | {_pct(r['odds::Avg close 1X2'])} "
            f"| {_pct(r['odds::B365 pre 1X2'])} | {_pct(r['odds::Avg close O/U2.5'])} |"
        )
    out += [
        "",
        f"Totals: {totals['stored']:,} stored ({totals['repeat']:,} of them repeat meetings, "
        f"`meeting` > 1), {totals['held_back']:,} held back, {totals['excluded']:,} stored but "
        "excluded from models.",
        "",
    ]
    return out


def _pct(v) -> str:
    if v is None:
        return "–"
    return "0" if v == 0 else f"{v:g}"
