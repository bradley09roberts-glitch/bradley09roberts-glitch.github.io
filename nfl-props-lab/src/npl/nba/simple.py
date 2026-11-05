"""Simple baseline S: season average x opponent factor x rest factor x minutes trend.

A transparent rule of thumb to compare with N1 and with bookmakers' lines (D-038):

  season average   the player's per-game average this season before the game, blended with
                   last season's average as if it were 5 games (early-season stability)
  opponent factor  what the opponent has allowed per game in that stat this season, relative to
                   the league, shrunk towards 1 with 10 games
  rest factor      the team's days off before the game (0 = second night of a back-to-back, 1,
                   2, 3+), as the ratio of actual to projected on the fit seasons (2018-2023)
  minutes trend    his last 5 games' minutes over his season minutes average, kept within
                   0.75-1.25

Every input uses only games that tipped off before the one being projected. S is a single
number per prop; the "gap rule" bets the side it favours when it clears the line by a margin.
"""

from __future__ import annotations

import json

import numpy as np
import pandas as pd

from ..config import REPORTS_DIR
from . import canon

STATS = ("pts", "reb", "ast", "fg3m")
# the summed markets are built exactly as PRA: their own season average, opponent allowed and
# rest factor; the combos' rest factors were fitted the same way on 2018-2023 on 4 Nov 2026 (the
# five original markets' factors are untouched)
MARKETS = ("pts", "reb", "ast", "fg3m", "pra", "pr", "pa", "ra")
SUMS = {"pra": ("pts", "reb", "ast"), "pr": ("pts", "reb"), "pa": ("pts", "ast"),
        "ra": ("reb", "ast")}
PRIOR_GAMES = 5.0
OPP_K = 10.0
REST_PATH = REPORTS_DIR / "nba" / "model" / "simple_rest.json"


def _with_pra(d: pd.DataFrame) -> pd.DataFrame:
    """The frame with every summed market (PRA as pts + reb + ast, then pr, pa, ra)."""
    return d.assign(pra=d.pts + d.reb + d.ast, pr=d.pts + d.reb, pa=d.pts + d.ast,
                    ra=d.reb + d.ast)


def player_states(pg: pd.DataFrame) -> pd.DataFrame:
    """Post-game running season totals and last-5 minutes for every game a player played."""
    x = _with_pra(pg[pg.participation == "played"]).sort_values(["player_id", "tip_utc"]).copy()
    g = x.groupby(["player_id", "season"], sort=False)
    out = x[["player_id", "tip_utc", "season"]].copy()
    out["n_cur"] = g.cumcount() + 1
    for c in (*MARKETS, "minutes"):
        out[f"sum_{c}"] = g[c].cumsum()
    out["min_l5"] = x.groupby("player_id", sort=False).minutes.transform(
        lambda v: v.rolling(5, min_periods=1).mean())
    return out


def season_means(pg: pd.DataFrame) -> pd.DataFrame:
    x = _with_pra(pg[pg.participation == "played"])
    return x.groupby(["player_id", "season"])[[*MARKETS, "minutes"]].mean().add_prefix("prev_")


def defense_states(tg: pd.DataFrame) -> pd.DataFrame:
    """Post-game running per-game allowed (the opponent's totals) per defence and season, and the
    league's per-game level over the same season to date."""
    t = tg.merge(tg[["game_id", "team", *STATS]].rename(columns={"team": "opp"}),
                 on=["game_id", "opp"], suffixes=("", "_allowed"))
    t = t.assign(pra_allowed=t.pts_allowed + t.reb_allowed + t.ast_allowed,
                 pr_allowed=t.pts_allowed + t.reb_allowed,
                 pa_allowed=t.pts_allowed + t.ast_allowed,
                 ra_allowed=t.reb_allowed + t.ast_allowed)
    t = t.sort_values(["tip_utc"]).reset_index(drop=True)
    out = t[["team", "tip_utc", "season"]].rename(columns={"team": "opp"}).copy()
    g = t.groupby(["team", "season"], sort=False)
    out["opp_n"] = g.cumcount() + 1
    for s in MARKETS:
        col = f"{s}_allowed"
        out[f"opp_{s}"] = g[col].transform(lambda v: v.expanding().mean())
        out[f"lg_{s}"] = t.groupby("season")[col].transform(lambda v: v.expanding().mean())
    return out


def team_rest(games: pd.DataFrame) -> pd.DataFrame:
    """Days off before each team-game (0 = back-to-back), capped at 3; season openers count 3."""
    tg = pd.concat([games.assign(team=games.home), games.assign(team=games.away)])
    tg = tg.sort_values(["team", "tip_utc"])
    d = pd.to_datetime(tg.date)
    prev = d.groupby(tg.team).shift()
    days = (d - prev).dt.days - 1
    tg["rest"] = np.where(prev.isna() | (tg.season != tg.groupby("team").season.shift()), 3,
                          np.clip(days.fillna(3), 0, 3)).astype(int)
    return tg[["game_id", "team", "rest"]]


def _asof(left: pd.DataFrame, right: pd.DataFrame, by: str) -> pd.DataFrame:
    left = left.assign(tip_utc=left.tip_utc.astype("datetime64[ns, UTC]")).sort_values("tip_utc")
    right = right.assign(tip_utc=right.tip_utc.astype("datetime64[ns, UTC]")).rename(
        columns={"tip_utc": "_t", "season": f"_season_{by}"}).sort_values("_t")
    return pd.merge_asof(left, right, left_on="tip_utc", right_on="_t", by=by,
                         allow_exact_matches=False, direction="backward").drop(columns="_t")


def features(rows: pd.DataFrame, pg: pd.DataFrame, tg: pd.DataFrame, games: pd.DataFrame) -> pd.DataFrame:
    """rows: player_id, team, opp, game_id, season, tip_utc (games to project). Adds the four
    components per market, without the rest factor (rest category in `rest`)."""
    r = rows.copy()
    r["_row"] = np.arange(len(r))
    r = _asof(r, player_states(pg), "player_id")
    same = r._season_player_id == r.season
    r["_prev_season"] = r.season - 1
    r = r.merge(season_means(pg).reset_index().rename(columns={"season": "_prev_season"}),
                how="left", on=["player_id", "_prev_season"])
    n = np.where(same, r.n_cur, 0.0)
    has_prev = r.prev_minutes.notna().to_numpy()
    w = np.where(has_prev, PRIOR_GAMES, 0.0)
    for c in (*MARKETS, "minutes"):
        cur = np.where(same, r[f"sum_{c}"], 0.0)
        prev = r[f"prev_{c}"].fillna(0).to_numpy()
        den = n + w
        r[f"avg_{c}"] = np.where(den > 0, (cur + w * prev) / np.where(den > 0, den, 1), np.nan)
    r["min_trend"] = np.clip(r.min_l5 / r.avg_minutes, 0.75, 1.25).fillna(1.0)
    r = _asof(r, defense_states(tg), "opp")
    same_d = r._season_opp == r.season
    nn = np.where(same_d, r.opp_n, 0.0)
    for s in MARKETS:
        ratio = (r[f"opp_{s}"] / r[f"lg_{s}"]).where(same_d, 1.0).fillna(1.0)
        r[f"opp_factor_{s}"] = 1 + (ratio - 1) * nn / (nn + OPP_K)
    r = r.merge(team_rest(games), on=["game_id", "team"], how="left")
    r["rest"] = r.rest.fillna(1).astype(int)
    return r.sort_values("_row").drop(columns="_row").reset_index(drop=True)


def fit_rest(f: pd.DataFrame, actual: pd.DataFrame) -> dict:
    """Ratio of actual to projected (without rest) by rest category and market."""
    out = {}
    for s in MARKETS:
        base = f[f"avg_{s}"] * f[f"opp_factor_{s}"] * f.min_trend
        ok = base.notna() & actual[s].notna()
        out[s] = {int(k): float(actual[s][ok & (f.rest == k)].sum() / base[ok & (f.rest == k)].sum())
                  for k in range(4)}
    return out


def save_rest(rest: dict, note: str | None = None) -> None:
    d = {"fit_seasons": [2018, 2023], "rest": rest}
    if note:
        d["note"] = note
    REST_PATH.write_text(json.dumps(d, indent=1) + "\n")


def load_rest() -> dict:
    d = json.loads(REST_PATH.read_text())["rest"]
    return {s: {int(k): v for k, v in m.items()} for s, m in d.items()}


def project(f: pd.DataFrame, rest: dict) -> pd.DataFrame:
    """S per market: season average x opponent factor x rest factor x minutes trend."""
    out = pd.DataFrame(index=f.index)
    for s in MARKETS:
        if s not in rest:  # a market with no fitted rest factors gets no S
            continue
        rf = f.rest.map(rest[s]).fillna(1.0)
        out[s] = f[f"avg_{s}"] * f[f"opp_factor_{s}"] * rf * f.min_trend
    return out


def tonight(rows: pd.DataFrame) -> pd.DataFrame:
    """S for live candidate rows (player_id, team, opp, game_id, season, tip_utc)."""
    L = canon.load()
    return project(features(rows, L["player_games"], L["team_games"], L["games"]), load_rest())
