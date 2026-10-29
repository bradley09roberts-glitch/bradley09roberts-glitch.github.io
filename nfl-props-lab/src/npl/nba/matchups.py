"""Defensive matchups for the app: per team-game rows (so every filter is computed the same
way for all 30 teams in the browser), split-half reliability of each split, and players'
own shot profiles for matchup pages.

Positions are ESPN's single listed position per player per game box score (G, F or C); each
shot, free throw and box-score line belongs to exactly one position, so nothing is counted
twice. PG/SG/SF/PF are not listed reliably in this source (about 0.3% of rows) and are
marked unavailable rather than guessed.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from ..config import DATA_HOME
from . import canon, shots

APP_SEASONS = (2026, 2027)
SEASON_LABELS = {2026: "2025-26", 2027: "2026-27"}
POS_ALL = ("G", "F", "C", "U")
STYPE = {"REG": "R", "POST": "P", "PLAYIN": "I"}


def _team_games() -> pd.DataFrame:
    L = canon.load()
    tg = L["team_games"]
    o = tg[["game_id", "team", "poss", "score"]].rename(
        columns={"team": "opp", "poss": "opp_poss", "score": "pts_allowed"})
    return tg.merge(o, on=["game_id", "opp"])


def team_game_rows(seasons=APP_SEASONS) -> dict:
    S = shots.load()
    tg = _team_games()
    # games without play-by-play yet (usually last night's, provisional box scores) are left
    # out of every figure, so shot areas, positions and fouls all use the same games
    tg = tg[tg.season.isin(seasons) & tg.game_id.isin(set(S["def_shots"].game_id))]
    pg = canon.load()["player_games"]
    pg = pg[pg.season.isin(seasons) & (pg.participation == "played")]
    # box-score production by opposing position (points, rebounds, assists, minutes, fg)
    bx = pg.groupby(["game_id", "opp", "pos"])[["pts", "reb", "ast", "minutes", "fga", "fgm",
                                               "fg3a", "fg3m", "fta", "ftm"]].sum()
    zs = S["def_shots"].set_index(["game_id", "defense", "pos", "zone"])[["fga", "fgm"]]
    ft = S["def_ft"].set_index(["game_id", "defense", "pos"])[["fta", "ftm"]]
    fo = S["def_fouls"].set_index(["game_id", "defense"])[["fouls", "shooting_fouls"]]
    zs_d = {k: v for k, v in zs.groupby(level=[0, 1])}
    ft_d = {k: v for k, v in ft.groupby(level=[0, 1])}
    bx_d = {k: v for k, v in bx.groupby(level=[0, 1])}
    out: dict = {}
    for r in tg.sort_values("tip_utc").itertuples():
        key = (r.game_id, r.team)
        z = {}
        zz = zs_d.get(key)
        for pos in POS_ALL:
            arr = []
            for zn in shots.ZONES:
                try:
                    a = zz.loc[(r.game_id, r.team, pos, zn)] if zz is not None else None
                except KeyError:
                    a = None
                arr += [int(a.fga), int(a.fgm)] if a is not None else [0, 0]
            if any(arr):
                z[pos] = arr
        f = {}
        ff = ft_d.get(key)
        if ff is not None:
            for (_, _, pos), a in ff.iterrows():
                f[pos] = [int(a.fta), int(a.ftm)]
        b = {}
        bb = bx_d.get(key)
        if bb is not None:
            for (_, _, pos), a in bb.iterrows():
                b[pos] = [int(a.pts), int(a.reb), int(a.ast), round(float(a.minutes), 1),
                          int(a.fga), int(a.fgm), int(a.fg3a), int(a.fg3m), int(a.fta),
                          int(a.ftm)]
        fl = fo.loc[key] if key in fo.index else None
        out.setdefault(str(int(r.season)), {}).setdefault(r.team, []).append({
            "g": r.game_id, "d": r.date.strftime("%Y-%m-%d"), "h": int(bool(r.is_home)),
            "o": r.opp, "t": STYPE.get(r.season_type, "R"),
            "p": round(float(r.opp_poss), 1), "pa": int(r.pts_allowed),
            "f": int(fl.fouls) if fl is not None else None,
            "sf": int(fl.shooting_fouls) if fl is not None else None,
            "z": z, "ft": f, "bx": b})
    return out


def reliability(seasons=(2023, 2024, 2025, 2026)) -> dict:
    """Split-half reliability of team-defence measures: each regular season's games split
    odd/even by date, the measure computed on each half, correlated across the 30 teams,
    pooled over seasons, then converted to a single-game reliability rho so the reliability
    after n games is n*rho / (1 + (n-1)*rho) (Spearman-Brown)."""
    S = shots.load()
    tg = _team_games()
    tg = tg[tg.season.isin(seasons) & (tg.season_type == "REG")].sort_values("tip_utc")
    tg["half"] = tg.groupby(["season", "team"]).cumcount() % 2
    z = S["def_shots"].merge(tg[["game_id", "team", "half", "season"]].rename(
        columns={"team": "defense"}), on=["game_id", "defense", "season"])
    poss = tg.groupby(["season", "team", "half"]).opp_poss.sum()
    out = {}

    def add(name: str, a: pd.Series, b: pd.Series):
        rs = []
        for s in seasons:
            try:
                x, y = a.xs(s, level="season"), b.xs(s, level="season")
            except KeyError:
                continue
            j = pd.concat([x, y], axis=1, join="inner").dropna()
            if len(j) >= 20:
                rs.append(np.corrcoef(j.iloc[:, 0], j.iloc[:, 1])[0, 1])
        if not rs:
            return
        r_half = float(np.mean(rs))
        n_half = 41
        rho = r_half / (n_half - (n_half - 1) * r_half) if r_half < 1 else 1.0
        rho = max(rho, 0.0)
        out[name] = {"split_half": round(r_half, 3), "rho_game": round(rho, 4),
                     "at_10": round(10 * rho / (1 + 9 * rho), 2),
                     "at_82": round(82 * rho / (1 + 81 * rho), 2)}

    for pos in ("ALL", "G", "F", "C"):
        zz = z if pos == "ALL" else z[z.pos == pos]
        g = zz.groupby(["season", "defense", "half", "zone"])[["fga", "fgm"]].sum()
        for zn in shots.ZONES:
            try:
                q = g.xs(zn, level="zone")
            except KeyError:
                continue
            att = q.fga / poss.rename_axis(["season", "defense", "half"]).reindex(q.index)
            pct = q.fgm / q.fga
            add(f"{pos}|{zn}|att", att.xs(0, level="half"), att.xs(1, level="half"))
            add(f"{pos}|{zn}|pct", pct.xs(0, level="half"), pct.xs(1, level="half"))
    tot = tg.groupby(["season", "team", "half"]).pts_allowed.sum() / poss
    add("ALL|pts_per100", tot.xs(0, level="half"), tot.xs(1, level="half"))
    return out


RELIABILITY_FILE = shots.SHOTS_LAKE / "def_reliability.json"


def cached_reliability() -> dict:
    """Reliability depends only on completed seasons: computed once per shot-table build."""
    import json

    src = (shots.SHOTS_LAKE / "def_shots.parquet").stat().st_mtime
    try:
        d = json.loads(RELIABILITY_FILE.read_text())
        if d.get("source_mtime") == src:
            return d["reliability"]
    except (OSError, ValueError):
        pass
    r = reliability()
    RELIABILITY_FILE.write_text(json.dumps({"source_mtime": src, "reliability": r}))
    return r


def player_profile(pid: str, seasons=APP_SEASONS) -> dict:
    S = shots.load()
    pz = S["player_shots"]
    pz = pz[(pz.player_id == pid) & pz.season.isin(seasons)]
    pf = S["player_ft"]
    pf = pf[(pf.player_id == pid) & pf.season.isin(seasons)]
    out = {}
    for s in seasons:
        x = pz[pz.season == s]
        if x.empty:
            continue
        g = x.game_id.nunique()
        z = x.groupby("zone")[["fga", "fgm"]].sum().reindex(list(shots.ZONES), fill_value=0)
        f = pf[pf.season == s][["fta", "ftm"]].sum()
        out[str(s)] = {"games": int(g), "zones": {k: [int(v.fga), int(v.fgm)] for k, v in
                                                  z.iterrows()},
                       "ft": [int(f.fta), int(f.ftm)]}
    return out


def payload() -> dict:
    import sqlite3

    con = sqlite3.connect(DATA_HOME / "npl.sqlite3")
    upd = dict(con.execute("SELECT dataset, MAX(retrieved_at) FROM raw_files WHERE "
                           "source='sportsdataverse' AND dataset IN ('nba_pbp', "
                           "'nba_player_box') GROUP BY dataset").fetchall())
    return {"seasons": {str(s): SEASON_LABELS[s] for s in APP_SEASONS},
            "zones": list(shots.ZONES), "zone_labels": shots.ZONE_LABELS,
            "two": list(shots.TWO), "three": list(shots.THREE),
            "positions": {"G": "Guards", "F": "Forwards", "C": "Centres"},
            "unavailable_positions": ["PG", "SG", "SF", "PF"],
            "box_cols": ["pts", "reb", "ast", "min", "fga", "fgm", "fg3a", "fg3m", "fta",
                         "ftm"],
            "rows": team_game_rows(), "reliability": cached_reliability(),
            "updated": {"play_by_play": upd.get("nba_pbp"),
                        "box_scores": upd.get("nba_player_box"),
                        "note": "Games whose play-by-play is not published yet (usually last "
                                "night's) are not included until it is."},
            "definitions": {
                "zones": shots.__doc__.split("\n\n")[1] if shots.__doc__ else "",
                "positions": __doc__.split("\n\n")[1] if __doc__ else "",
                "fouls": "Fouls committed: personal, shooting, loose-ball, take, away-from-"
                         "play, clear-path and flagrant fouls by the defending team; offensive "
                         "and technical fouls are excluded. Who was fouled is not recorded in "
                         "this play-by-play, so fouls drawn by position are unavailable; free "
                         "throws by the shooter's position are shown instead.",
                "ft_rate": "Opponent free-throw rate = opponent FTA / opponent FGA.",
                "per100": "Per 100 possessions uses the opponent's estimated possessions in "
                          "each game (FGA - OREB + TOV + 0.44 x FTA).",
                "points": "Points by court area come from made field goals in the play-by-"
                          "play; free-throw points are shown separately. Points allowed to a "
                          "position use box scores."},
            "source": "SportsDataverse play-by-play and box scores (CC BY 4.0), from ESPN "
                      "game feeds"}
