"""Canonical NBA tables from the raw SportsDataverse box scores.

player_games: one row per player listed in a box score (played or did-not-play). Players
ruled out before the game are usually not listed at all; `candidates` reconstructs who
could have played from recent appearances, so absence is still a recorded outcome.
"""

from __future__ import annotations

import json
import sqlite3

import numpy as np
import pandas as pd

from .. import db
from ..config import DATA_HOME
from ..util import iso, utcnow
from . import source

NBA_LAKE = DATA_HOME / "nba" / "lake"
SEASON_TYPES = {2: "REG", 3: "POST", 5: "PLAYIN"}
STATS = {"minutes": "minutes", "pts": "points", "reb": "rebounds", "ast": "assists",
         "fg3m": "three_point_field_goals_made", "fg3a": "three_point_field_goals_attempted",
         "fgm": "field_goals_made", "fga": "field_goals_attempted",
         "ftm": "free_throws_made", "fta": "free_throws_attempted",
         "oreb": "offensive_rebounds", "dreb": "defensive_rebounds", "tov": "turnovers",
         "stl": "steals", "blk": "blocks"}
POS_GROUP = {"G": "G", "PG": "G", "SG": "G", "F": "F", "SF": "F", "PF": "F", "C": "C"}


def _raw_box(con: sqlite3.Connection) -> pd.DataFrame:
    rows = con.execute(
        "SELECT part, path FROM raw_files WHERE source=? AND dataset='nba_player_box' AND "
        "is_synthetic=0 ORDER BY retrieved_at, id", (source.SOURCE,)).fetchall()
    latest = {r["part"]: r["path"] for r in rows}  # last stored version per season
    return pd.concat([pd.read_parquet(DATA_HOME / p) for p in latest.values()],
                     ignore_index=True)


def build(con: sqlite3.Connection | None = None) -> dict:
    con = con or db.connect()
    d = _raw_box(con)
    d = d[d.season_type.isin(SEASON_TYPES) & d.athlete_id.notna()].copy()
    d["game_id"] = d.game_id.astype("int64").astype(str)
    # ESPN athlete ids are integers; some season files store them as floats, so normalise to
    # plain integer strings (the same form as the injury snapshots)
    d["player_id"] = d.athlete_id.astype("int64").astype(str)
    tip = pd.to_datetime(d.game_date_time, utc=True)
    dnp = d.did_not_play.fillna(False).astype(bool)
    pg = pd.DataFrame({
        "game_id": d.game_id, "season": d.season.astype(int),
        "season_type": d.season_type.map(SEASON_TYPES), "date": pd.to_datetime(d.game_date),
        "tip_utc": tip, "player_id": d.player_id, "name": d.athlete_display_name,
        "team": d.team_abbreviation, "opp": d.opponent_team_abbreviation,
        "is_home": d.home_away.eq("home"), "starter": d.starter.fillna(False).astype(bool),
        "pos": d.athlete_position_abbreviation.map(POS_GROUP).fillna("F"),
    })
    for k, c in STATS.items():
        pg[k] = pd.to_numeric(d[c], errors="coerce")
    played = ~dnp & (pg.minutes.fillna(0) > 0)
    pg["participation"] = np.where(played, "played", "dnp")
    pg["reason"] = np.where(dnp, d.reason.fillna(""), "")
    for k in STATS:
        pg.loc[~played, k] = np.nan
    pg = pg.drop_duplicates(["game_id", "player_id"]).sort_values(["tip_utc", "game_id", "team"])
    # team-game totals and an estimate of possessions (FGA - OREB + TOV + 0.44 FTA)
    tg = pg[pg.participation == "played"].groupby(["game_id", "team"], as_index=False).agg(
        opp=("opp", "first"), season=("season", "first"), season_type=("season_type", "first"),
        date=("date", "first"), tip_utc=("tip_utc", "first"), is_home=("is_home", "first"),
        minutes=("minutes", "sum"), pts=("pts", "sum"), reb=("reb", "sum"), ast=("ast", "sum"),
        fg3m=("fg3m", "sum"), fga=("fga", "sum"), fta=("fta", "sum"), oreb=("oreb", "sum"),
        tov=("tov", "sum"))
    tg["poss"] = tg.fga - tg.oreb + tg.tov + 0.44 * tg.fta
    sc = d.groupby(["game_id", "team_abbreviation"]).team_score.first()
    tg["score"] = [sc.get((g, t)) for g, t in zip(tg.game_id, tg.team, strict=True)]
    games = tg[tg.is_home].rename(columns={"team": "home", "opp": "away", "score": "home_score"})[
        ["game_id", "season", "season_type", "date", "tip_utc", "home", "away", "home_score"]]
    away = tg[~tg.is_home].set_index("game_id")["score"]
    games["away_score"] = games.game_id.map(away)
    games["is_final"] = games.home_score.notna() & games.away_score.notna()
    NBA_LAKE.mkdir(parents=True, exist_ok=True)
    pg.to_parquet(NBA_LAKE / "player_games.parquet", index=False)
    tg.to_parquet(NBA_LAKE / "team_games.parquet", index=False)
    games.to_parquet(NBA_LAKE / "games.parquet", index=False)
    manifest = {"built_at": iso(utcnow()), "counts": {"games": len(games),
                "player_games": len(pg), "team_games": len(tg)},
                "participation": pg.participation.value_counts().to_dict(),
                "seasons": sorted(int(s) for s in games.season.unique())}
    (NBA_LAKE / "build_manifest.json").write_text(json.dumps(manifest, indent=1))
    return manifest


def load() -> dict:
    return {k: pd.read_parquet(NBA_LAKE / f"{k}.parquet")
            for k in ("games", "player_games", "team_games")}
