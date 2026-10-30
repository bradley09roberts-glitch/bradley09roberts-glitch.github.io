"""Provisional box scores from ESPN game feeds, for games SportsDataverse has not published yet.

SportsDataverse builds its NBA files from these same ESPN feeds, usually about 12 hours after
the games, so the ids (games, athletes) and team abbreviations are the same. Each feed is stored
unchanged in the raw store; `raw_rows` turns it into rows in the SportsDataverse player-box
layout, and `canon.build` uses them only for games missing from SportsDataverse, which replaces
them as soon as it publishes the game. Rows carry box_source = "espn_provisional".

The same feed, fetched before tip-off, carries each team's current injury report. Editions use
it in place of SportsDataverse's daily injury snapshot, which can be a day old (D-033).
"""

from __future__ import annotations

import json
import sqlite3

import numpy as np
import pandas as pd

from .. import rawstore
from ..config import DATA_HOME, NEW_YORK
from ..util import FetchError, http_get, iso, log, sha256_json, utcnow

SOURCE = "espn"
DATASET = "nba_summary"
URL = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/summary?event={game_id}"
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


def fetch(con: sqlite3.Connection, game_ids: list[str]) -> dict:
    """Store the current ESPN feed of each finished game (unchanged feeds are only observed)."""
    out = {"new": [], "unchanged": [], "not_final": [], "failed": []}
    for g in game_ids:
        url = URL.format(game_id=g)
        try:
            r = http_get(url, timeout=60)
        except FetchError as e:
            out["failed"].append(g)
            rawstore.record_failure(con, source=SOURCE, dataset=DATASET, part=g, url=url,
                                    status=e.status, error=str(e))
            continue
        if raw_rows(r.json()).empty:
            out["not_final"].append(g)
            continue
        sf = rawstore.store(con, source=SOURCE, dataset=DATASET, part=g, url=url,
                            content=r.content, filename=f"summary_{g}.json",
                            content_type=r.headers.get("Content-Type"))
        (out["new"] if sf.changed else out["unchanged"]).append(g)
        log.info("espn %s %s", g, "NEW" if sf.changed else "same")
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


def fetch_injuries(con: sqlite3.Connection, game_ids: list[str]) -> tuple[pd.DataFrame, set, dict]:
    """Current injury reports for the slate's games, from their ESPN feeds (stored unchanged)."""
    now = utcnow()
    frames, covered, failed, shas = [], set(), [], []
    for g in game_ids:
        url = URL.format(game_id=g)
        try:
            r = http_get(url, timeout=30, max_tries=2)
            rows, teams = injury_rows(r.json())
        except (FetchError, ValueError, KeyError, IndexError) as e:
            failed.append(g)
            rawstore.record_failure(con, source=SOURCE, dataset=PRE_DATASET, part=g, url=url,
                                    status=getattr(e, "status", None), error=str(e))
            continue
        sf = rawstore.store(con, source=SOURCE, dataset=PRE_DATASET, part=g, url=url,
                            content=r.content, filename=f"pregame_{g}.json",
                            content_type=r.headers.get("Content-Type"))
        frames.append(rows)
        covered |= teams
        shas.append(sf.sha256)
    df = pd.concat(frames, ignore_index=True) if frames else pd.DataFrame(columns=INJ_COLS)
    meta = {"source": SOURCE, "dataset": PRE_DATASET, "retrieved_at": iso(now),
            "games": len(game_ids) - len(failed), "failed": failed, "teams": sorted(covered),
            "sha256": sha256_json(sorted(shas))}
    return df, covered, meta


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
