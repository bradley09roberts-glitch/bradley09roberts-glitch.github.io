"""Coverage per division, season and field. Counts only: no source rows or prices leave here."""

from datetime import datetime

import pandas as pd
from sqlalchemy import Engine, text

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
    if matches.empty:
        return []

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
    for (division, season), g in matches.groupby(["division", "season"], sort=False):
        teams = set(g.home_team) | set(g.away_team)
        key = f"{season}/{division}"
        n = len(g)
        row = {
            "division": division,
            "division_name": DIVISIONS[division].name,
            "tier": DIVISIONS[division].tier,
            "season": season,
            "season_label": season_label(season),
            "matches": n,
            "teams": len(teams),
            "expected_matches": len(teams) * (len(teams) - 1),
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
            row[f"odds::{label}"] = (
                round(100 * len(ids & complete[label]) / n, 1)
                if DIVISIONS[division].tier == 1
                else None
            )
        rows.append(row)
    order = {code: i for i, code in enumerate(DIVISIONS)}
    rows.sort(key=lambda r: (order[r["division"]], r["season"]))
    return rows


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
        "## Prices (% of matches with a complete set), top divisions",
        "",
        "pre = pre-closing (collected by Fri 17:00 / Tue 13:00 UK, D-012); close = closing.",
        "PS = Pinnacle (unreliable from 23 Jul 2025, D-014); BFE = Betfair Exchange;",
        "Avg = market average. Sets with implausible margins are dropped at import.",
        "",
    ]
    labels = [s[0] for s in ODDS_SETS]
    out.append("| Division | Season | " + " | ".join(labels) + " |")
    out.append("| --- | --- | " + " | ".join("---:" for _ in labels) + " |")
    for r in rows:
        if r["tier"] != 1:
            continue
        cells = " | ".join(_pct(r[f"odds::{lab}"]) for lab in labels)
        out.append(f"| {r['division']} | {r['season_label']} | {cells} |")
    out.append("")
    return "\n".join(out)


def _pct(v) -> str:
    if v is None:
        return "–"
    return "0" if v == 0 else f"{v:g}"
