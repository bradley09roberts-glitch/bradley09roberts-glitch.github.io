"""UNUSED since D-046 (3 Nov 2026): nothing in the pipeline imports this module.

ESPN's terms forbid automated access (Disney Terms of Use sect. 2.B.x; D-019), so the app no
longer fetches ESPN's game feeds: box scores come only from SportsDataverse (the next morning)
and editions use SportsDataverse's daily injury snapshot. The fetchers were removed. The pure
parsers below are kept, with their tests, only so the feeds stored between 29 Oct and 3 Nov
2026 (raw store, source "espn": datasets nba_summary and nba_pregame_summary, untouched) can
still be read for audit.

What they did: `raw_rows` turned a finished game's feed into rows in the SportsDataverse
player-box layout (box_source = "espn_provisional", D-025); `injury_rows` and `apply_injuries`
read a pre-game feed's injury report in place of the daily snapshot (D-033).
"""

from __future__ import annotations

import json
import sqlite3

import numpy as np
import pandas as pd

from ..config import DATA_HOME, NEW_YORK
from ..util import utcnow

SOURCE = "espn"
DATASET = "nba_summary"
# ESPN statistic key -> SportsDataverse column; "a-b" keys hold made-attempted pairs
KEYS = {"minutes": ["minutes"], "points": ["points"], "rebounds": ["rebounds"],
        "assists": ["assists"], "turnovers": ["turnovers"], "steals": ["steals"],
        "blocks": ["blocks"], "offensiveRebounds": ["offensive_rebounds"],
        "defensiveRebounds": ["defensive_rebounds"], "fouls": ["fouls"],
        "fieldGoalsMade-fieldGoalsAttempted": ["field_goals_made", "field_goals_attempted"],
        "threePointFieldGoalsMade-threePointFieldGoalsAttempted":
            ["three_point_field_goals_made", "three_point_field_goals_attempted"],
        "freeThrowsMade-freeThrowsAttempted": ["free_throws_made", "free_throws_attempted"]}


def _num(v: str):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def raw_rows(summary: dict) -> pd.DataFrame:
    """Player rows of one finished game in the SportsDataverse player-box layout (empty if
    the game is not final)."""
    comp = summary["header"]["competitions"][0]
    if not comp["status"]["type"].get("completed"):
        return pd.DataFrame()
    side = {c["team"]["abbreviation"]: c for c in comp["competitors"]}
    tip = pd.Timestamp(comp["date"])
    tip = tip.tz_localize("UTC") if tip.tzinfo is None else tip.tz_convert("UTC")
    season = summary["header"]["season"]
    rows = []
    for tb in summary.get("boxscore", {}).get("players", []):
        team = tb["team"]["abbreviation"]
        opp = next(t for t in side if t != team)
        for grp in tb["statistics"]:
            keys = grp.get("keys") or []
            for a in grp["athletes"]:
                r = {"game_id": int(summary["header"]["id"]), "season": int(season["year"]),
                     "season_type": int(season["type"]),
                     "game_date": tip.tz_convert(NEW_YORK).strftime("%Y-%m-%d"),
                     "game_date_time": tip.tz_convert(NEW_YORK).isoformat(),
                     "athlete_id": float(a["athlete"]["id"]),
                     "athlete_display_name": a["athlete"]["displayName"],
                     "athlete_position_abbreviation":
                         (a["athlete"].get("position") or {}).get("abbreviation"),
                     "team_abbreviation": team, "opponent_team_abbreviation": opp,
                     "home_away": side[team]["homeAway"], "team_score": _num(side[team]["score"]),
                     "opponent_team_score": _num(side[opp]["score"]),
                     "starter": bool(a.get("starter")), "did_not_play": bool(a.get("didNotPlay")),
                     "reason": a.get("reason") or "", "ejected": bool(a.get("ejected"))}
                stats = a.get("stats") or []
                for k, v in zip(keys, stats, strict=False):
                    cols = KEYS.get(k)
                    if not cols:
                        continue
                    parts = v.split("-") if len(cols) == 2 else [v]
                    for c, x in zip(cols, parts, strict=False):
                        r[c] = _num(x)
                rows.append(r)
    out = pd.DataFrame(rows)
    if len(out):
        out["box_source"] = "espn_provisional"
    return out


def missing_games(sched: pd.DataFrame, have: set[str], hours_after_tip: float = 3.0) -> list[str]:
    """Scheduled games that should be over but are not in the SportsDataverse box scores."""
    tip = pd.to_datetime(sched.date, utc=True)
    ids = sched.game_id.astype("int64").astype(str)
    done = tip < pd.Timestamp(utcnow()) - pd.Timedelta(hours=hours_after_tip)
    return sorted(set(ids[done]) - have)


def stored_rows(con: sqlite3.Connection, exclude: set[str]) -> pd.DataFrame:
    """Rows from the latest stored feed of every game not in `exclude` (SportsDataverse)."""
    rows = con.execute("SELECT part, path FROM raw_files WHERE source=? AND dataset=? AND "
                       "is_synthetic=0 ORDER BY retrieved_at, id", (SOURCE, DATASET)).fetchall()
    latest = {r["part"]: r["path"] for r in rows if r["part"] not in exclude}
    frames = [raw_rows(json.loads((DATA_HOME / p).read_bytes())) for p in latest.values()]
    frames = [f for f in frames if len(f)]
    return pd.concat(frames, ignore_index=True) if frames else pd.DataFrame()


# --------------------------------------------------------------------------- injury reports
PRE_DATASET = "nba_pregame_summary"
INJ_COLS = ["player_id", "team", "status", "fantasy", "detail", "news_at"]
_FANTASY = {"OUT": "OUT", "OFS": "OFS", "GTD": "GTD", "SUSP": "OUT", "O": "OUT"}


def injury_rows(summary: dict) -> tuple[pd.DataFrame, set[str]]:
    """Players on each team's injury report in one game feed, and the teams the feed covers
    (a covered team's players who are not listed are taken as available)."""
    comp = summary["header"]["competitions"][0]
    teams = {c["team"]["abbreviation"] for c in comp["competitors"]}
    rows = []
    for t in summary.get("injuries", []):
        for i in t.get("injuries", []):
            det = i.get("details") or {}
            st = i.get("status") or ""
            fan = ((det.get("fantasyStatus") or {}).get("abbreviation") or "").upper()
            if not fan:  # no fantasy tag: Out is out, Probable/Available is playing
                low = st.lower()
                fan = "OUT" if low == "out" else "" if low in ("probable", "available", "") else "GTD"
            rows.append({"player_id": str(i["athlete"]["id"]), "team": t["team"]["abbreviation"],
                         "status": st, "fantasy": _FANTASY.get(fan, fan) or None,
                         "detail": det.get("type") or "", "news_at": i.get("date")})
    return pd.DataFrame(rows, columns=INJ_COLS), teams


def apply_injuries(x: pd.DataFrame, live: pd.DataFrame, covered: set, as_of: str) -> pd.DataFrame:
    """Replace the snapshot's injury columns with the live report for players on covered teams
    (by the team they are listed for in x); other teams keep the snapshot."""
    x = x.copy()
    cols = ["status", "fantasy", "detail", "as_of_date", "news_at"]
    on = x.team.isin(covered).to_numpy()
    x.loc[on, cols] = None
    lv = live.drop_duplicates("player_id").set_index("player_id")
    hit = on & x.player_id.isin(lv.index).to_numpy()
    for c in ("status", "fantasy", "detail", "news_at"):
        x.loc[hit, c] = x.loc[hit, "player_id"].map(lv[c]).to_numpy()
    x.loc[hit, "as_of_date"] = as_of
    x["injury_source"] = np.where(on, "espn", np.where(x.fantasy.notna(), "sportsdataverse", None))
    return x
