"""Shot-location and free-throw tables from SportsDataverse play-by-play (ESPN game feeds).

Court geometry (feet, ESPN raw coordinates): the basket is at (25, 0); the baseline is about
5.25 ft behind it (y = -5.25); x runs 0-50 across the court. Every field-goal attempt goes
to exactly ONE zone, chosen first by its recorded type (2 or 3 points) and then by where it
was taken, so nothing is counted twice:

  2-pointers  RA     restricted area: within 4 ft of the basket
              PAINT  in the lane (16 ft wide, 19 ft from the baseline), outside the RA
              MID    every other 2-pointer (mid-range)
  3-pointers  LC3 / RC3   corners: the straight part of the line (y <= 8.75)
              LW3 / RW3   wings: above the break, more than 9 ft either side of centre
              TOP3        top of the arc: above the break, within 9 ft of centre
Left and right are as drawn (basket at the bottom); ESPN's orientation is not verified
against the shooter's own left and right.
Free throws are kept separate from court locations.
"""

from __future__ import annotations

import sqlite3

import numpy as np
import pandas as pd

from .. import db
from ..config import DATA_HOME
from . import canon, source

ZONES = ("RA", "PAINT", "MID", "LC3", "RC3", "LW3", "RW3", "TOP3")
ZONE_LABELS = {"RA": "Restricted area", "PAINT": "Paint (non-restricted)", "MID": "Mid-range",
               "LC3": "Left corner 3", "RC3": "Right corner 3", "LW3": "Left wing 3",
               "RW3": "Right wing 3", "TOP3": "Top of the arc 3"}
TWO = ("RA", "PAINT", "MID")
THREE = ("LC3", "RC3", "LW3", "RW3", "TOP3")
POSITIONS = ("G", "F", "C")
DEF_FOULS = {"Shooting Foul", "Personal Foul", "Loose Ball Foul", "Personal Take Foul",
             "Transition Take Foul", "Away from Play Foul", "Clear Path Foul",
             "Flagrant Foul Type 1", "Flagrant Foul Type 2", "Inbound Foul",
             "Double Personal Foul"}
SHOTS_LAKE = canon.NBA_LAKE


def zone_of(x: np.ndarray, y: np.ndarray, pts: np.ndarray) -> np.ndarray:
    dx = np.asarray(x, float) - 25.0
    y = np.asarray(y, float)
    d = np.hypot(dx, y)
    two, three = pts == 2, pts == 3
    z = np.full(len(dx), "", dtype=object)
    z[two] = "MID"
    z[two & (abs(dx) <= 8) & (y <= 13.75)] = "PAINT"
    z[two & (d <= 4)] = "RA"
    corner = three & (y <= 8.75)
    z[corner & (dx < 0)] = "LC3"
    z[corner & (dx >= 0)] = "RC3"
    ab = three & (y > 8.75)
    z[ab & (abs(dx) <= 9)] = "TOP3"
    z[ab & (dx < -9)] = "LW3"
    z[ab & (dx > 9)] = "RW3"
    return z


def _pbp(con: sqlite3.Connection) -> pd.DataFrame:
    rows = con.execute("SELECT part, path FROM raw_files WHERE source=? AND dataset='nba_pbp' "
                       "AND is_synthetic=0 ORDER BY retrieved_at, id", (source.SOURCE,)).fetchall()
    latest = {r["part"]: r["path"] for r in rows}
    cols = ["game_id", "season", "season_type", "type_text", "shooting_play", "scoring_play",
            "points_attempted", "score_value", "team_id", "athlete_id_1", "home_team_id",
            "away_team_id", "coordinate_x_raw", "coordinate_y_raw"]
    return pd.concat([pd.read_parquet(DATA_HOME / p, columns=cols) for p in latest.values()],
                     ignore_index=True)


def build(con: sqlite3.Connection | None = None) -> dict:
    """Per (game, defence, opposing position, zone) attempts and makes; free throws and fouls
    per (game, defence, position); and each shooter's own zone profile."""
    con = con or db.connect()
    p = _pbp(con)
    L = canon.load()
    pg, tg = L["player_games"], L["team_games"]
    p = p[p.season_type.isin(canon.SEASON_TYPES)].copy()
    p["game_id"] = p.game_id.astype("int64").astype(str)
    p = p[p.game_id.isin(set(tg.game_id))]
    # team ids -> abbreviations as used in the box scores (ESPN team ids)
    box = canon._raw_box(con)
    ids = box.drop_duplicates("team_id").set_index("team_id").team_abbreviation.to_dict()
    p["offense"] = p.team_id.map(ids)
    other = np.where(p.team_id == p.home_team_id, p.away_team_id, p.home_team_id)
    p["defense"] = pd.Series(other, index=p.index).map(ids)
    p["player_id"] = p.athlete_id_1.astype("Int64").astype(str)
    pos = pg.set_index(["game_id", "player_id"]).pos
    p["pos"] = [pos.get((g, a), "U") for g, a in zip(p.game_id, p.player_id, strict=True)]
    fg = p[p.shooting_play.fillna(False) & p.points_attempted.isin([2, 3])].copy()
    fg["zone"] = zone_of(fg.coordinate_x_raw.to_numpy(), fg.coordinate_y_raw.to_numpy(),
                         fg.points_attempted.to_numpy())
    fg["made"] = fg.scoring_play.fillna(False).astype(int)
    shots = fg.groupby(["season", "game_id", "defense", "offense", "pos", "zone"],
                       as_index=False).agg(fga=("made", "size"), fgm=("made", "sum"))
    ft = p[p.type_text.fillna("").str.startswith("Free Throw")].copy()
    ft["made"] = ft.scoring_play.fillna(False).astype(int)
    fts = ft.groupby(["season", "game_id", "defense", "offense", "pos"], as_index=False).agg(
        fta=("made", "size"), ftm=("made", "sum"))
    fo = p[p.type_text.isin(DEF_FOULS)].copy()
    # a defensive foul is committed by a player of the team named on the foul play
    fo["fouler_team"] = fo.offense
    fouls = fo.groupby(["season", "game_id", "fouler_team"], as_index=False).agg(
        fouls=("type_text", "size"),
        shooting_fouls=("type_text", lambda s: int((s == "Shooting Foul").sum())))
    fouls = fouls.rename(columns={"fouler_team": "defense"})
    prof = fg.groupby(["season", "game_id", "player_id", "offense", "zone"], as_index=False).agg(
        fga=("made", "size"), fgm=("made", "sum"))
    pft = ft.groupby(["season", "game_id", "player_id"], as_index=False).agg(
        fta=("made", "size"), ftm=("made", "sum"))
    SHOTS_LAKE.mkdir(parents=True, exist_ok=True)
    shots.to_parquet(SHOTS_LAKE / "def_shots.parquet", index=False)
    fts.to_parquet(SHOTS_LAKE / "def_ft.parquet", index=False)
    fouls.to_parquet(SHOTS_LAKE / "def_fouls.parquet", index=False)
    prof.to_parquet(SHOTS_LAKE / "player_shots.parquet", index=False)
    pft.to_parquet(SHOTS_LAKE / "player_ft.parquet", index=False)
    # coverage: play-by-play attempts against box-score attempts per team-game
    pb = shots.groupby(["game_id", "offense"]).fga.sum()
    bx = tg.set_index(["game_id", "team"]).fga
    j = pd.concat([pb.rename("pbp"), bx.rename("box")], axis=1, join="inner")
    return {"shots": int(shots.fga.sum()), "team_games": int(len(j)),
            "fga_match_share": float((j.pbp == j.box).mean()),
            "fga_within_2": float(((j.pbp - j.box).abs() <= 2).mean()),
            "unknown_position_share": float((fg.pos == "U").mean()),
            "seasons": sorted(int(s) for s in shots.season.unique())}


def load() -> dict:
    return {k: pd.read_parquet(SHOTS_LAKE / f"{k}.parquet")
            for k in ("def_shots", "def_ft", "def_fouls", "player_shots", "player_ft")}
