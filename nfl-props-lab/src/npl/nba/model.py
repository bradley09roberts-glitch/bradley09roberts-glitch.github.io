"""NBA player-prop model (N1) and baselines.

Markets: points, rebounds, assists, 3-pointers made, and PRA (points + rebounds + assists).

N1, per player and game, using only games that tipped off earlier:
  minutes  | plays ~ Normal(mu_m, sd_m) truncated to [1, 48], discretised on a grid
  stat | minutes ~ NegBin(mean = rate * minutes * pace * opponent, dispersion k_stat)
  rate     = exponentially weighted per-minute rate, shrunk to the position-group rate
  PRA      = convolution of points, rebounds and assists given the same minutes, so the
             combined market is consistent with its parts
Baselines (no minutes model, no opponent):
  A = exponentially weighted per-game average, NegBin with fitted dispersion
  C = plain average of the last 10 games played, same dispersion ("L10 average")
P(plays) is a separate logistic model on recent appearances (no injury reports).
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field

import numpy as np
import pandas as pd
from scipy.special import gammaln

MARKETS = ("pts", "reb", "ast", "fg3m", "pra")
BASE_STATS = ("pts", "reb", "ast", "fg3m")
MARKET_LABELS = {"pts": "Points", "reb": "Rebounds", "ast": "Assists", "fg3m": "3-pointers made",
                 "pra": "Pts + Reb + Ast"}
MAX_K = {"pts": 80, "reb": 35, "ast": 30, "fg3m": 15, "pra": 110}
MIN_GRID = np.arange(1, 49, 1.0)  # minutes grid for the mixture
MODEL_VERSION = "nba-0.2"  # nba-0.2: lineup-aware minutes (n07, D-036)


@dataclass
class Params:
    """Tuned on regular-season games of 2019, 2022 and 2023 (every third day), one
    parameter at a time; baseline A's dispersions were tuned the same way so the
    comparison is fair (reports/nba/EXPERIMENTS.md, n01)."""
    hl_min: float = 3.0  # half-life (games played) for minutes
    hl_rate: float = 15.0  # half-life for per-minute rates
    k_rate: float = 30.0  # prior strength (minutes) towards the position-group rate
    hl_team: float = 20.0  # team pace / defence half-life (team games)
    k_def: float = 100.0  # opponent factor shrinkage (games)
    min_sd_floor: float = 4.5
    min_sd_scale: float = 1.0
    use_pace: bool = True
    use_opp: bool = True
    min_resid: dict | None = None  # empirical minutes residuals (fit_minutes_residuals)
    lineup: dict | None = None  # lineup-aware minutes coefficients (n07); None = off
    # live only: observed / predicted missing minutes by team games played this season, from the
    # fit seasons; P(plays) over-predicts absences in a team's first games (n07)
    lineup_cal: dict | None = None
    disp: dict = field(default_factory=lambda: {"pts": 8.0, "reb": 35.0, "ast": 35.0,
                                                "fg3m": 12.0})
    disp_a: dict = field(default_factory=lambda: {"pts": 5.0, "reb": 12.0, "ast": 12.0,
                                                  "fg3m": 8.0, "pra": 8.0})


# --------------------------------------------------------------------------- features
def player_states(pg: pd.DataFrame, p: Params) -> pd.DataFrame:
    """Post-game exponentially weighted state after each game a player PLAYED (to be
    joined as-of strictly earlier tip-offs)."""
    x = pg[pg.participation == "played"].sort_values(["player_id", "tip_utc"]).copy()
    g = x.groupby("player_id", sort=False)
    out = x[["player_id", "tip_utc", "season", "team", "pos", "starter", "minutes"]].copy()
    out["n_played"] = g.cumcount() + 1
    out["n_season"] = x.groupby(["player_id", "season"]).cumcount() + 1
    ew_m = g.minutes.transform(lambda s: s.ewm(halflife=p.hl_min).mean())
    ew_m2 = g.minutes.transform(lambda s: (s ** 2).ewm(halflife=p.hl_min).mean())
    out["min_mu"] = ew_m
    out["min_sd"] = np.sqrt(np.maximum(ew_m2 - ew_m ** 2, 0))
    out["min_sum"] = g.minutes.transform(lambda s: s.ewm(halflife=p.hl_rate).sum())
    for s in BASE_STATS:
        out[f"{s}_sum"] = x.groupby("player_id", sort=False)[s].transform(
            lambda v: v.ewm(halflife=p.hl_rate).sum())
        out[f"{s}_gm"] = x.groupby("player_id", sort=False)[s].transform(
            lambda v: v.ewm(halflife=10).mean())  # baseline A
        out[f"{s}_l10"] = x.groupby("player_id", sort=False)[s].transform(
            lambda v: v.rolling(10, min_periods=1).mean())  # baseline C
    out["pra_gm"] = out.pts_gm + out.reb_gm + out.ast_gm
    pra = x.pts + x.reb + x.ast
    out["pra_l10"] = pra.groupby(x.player_id, sort=False).transform(
        lambda v: v.rolling(10, min_periods=1).mean())
    out["starter_ew"] = g.starter.transform(lambda s: s.astype(float).ewm(halflife=4).mean())
    out["last_played_tip"] = x.tip_utc
    return out.rename(columns={"team": "last_team", "starter": "last_starter",
                               "minutes": "last_minutes"})


def team_states(tg: pd.DataFrame, p: Params) -> pd.DataFrame:
    """Post-game team pace and defensive allowed-per-possession, exponentially weighted."""
    # incomplete box scores (team minutes well under regulation) would distort pace
    ok = tg[(tg.minutes >= 235) & (tg.poss >= 60)]
    o = ok[["game_id", "team", "poss", *BASE_STATS]].rename(
        columns={"team": "opp", "poss": "opp_poss", **{s: f"allowed_{s}" for s in BASE_STATS}})
    t = ok.merge(o, on=["game_id", "opp"]).sort_values(["team", "tip_utc"])
    gm = (t.poss + t.opp_poss) / 2 * 48 * 5 / t.minutes.clip(lower=240)  # per 48 minutes
    t["pace48"] = gm
    g = t.groupby("team", sort=False)
    out = t[["team", "tip_utc", "season"]].copy()
    out["pace_ew"] = g.pace48.transform(lambda s: s.ewm(halflife=p.hl_team).mean())
    order = t.sort_values("tip_utc").index
    out["lg_pace"] = t.pace48.reindex(order).ewm(halflife=300).mean().reindex(out.index)
    out["n_team"] = g.cumcount() + 1
    for s in BASE_STATS:
        per = t[f"allowed_{s}"] / t.opp_poss
        out[f"def_{s}"] = per.groupby(t.team, sort=False).transform(
            lambda v: v.ewm(halflife=p.hl_team).mean())
        # league level at the same moment (all teams, chronological), so opponent factors
        # are relative to current scoring, not to the fitting seasons
        out[f"lg_{s}"] = per.reindex(order).ewm(halflife=300).mean().reindex(out.index)
    return out


def league_rates(pg: pd.DataFrame, tg: pd.DataFrame, seasons) -> dict:
    """Per-minute rates by position group and league per-possession rates (training)."""
    x = pg[(pg.participation == "played") & pg.season.isin(seasons)]
    by = x.groupby("pos")[["minutes", *BASE_STATS]].sum()
    rates = {pos: {s: float(by.loc[pos, s] / by.loc[pos, "minutes"]) for s in BASE_STATS}
             for pos in by.index}
    t = tg[tg.season.isin(seasons)]
    per_poss = {s: float(t[s].sum() / t.poss.sum()) for s in BASE_STATS}
    return {"pos_rate": rates, "per_poss": per_poss,
            "pace48": float(((t.poss) * 48 * 5 / t.minutes.clip(lower=240)).mean())}


def _asof(left: pd.DataFrame, right: pd.DataFrame, by: str, lcol: str = "tip_utc") -> pd.DataFrame:
    # one time resolution on both sides (parquet round-trips may differ)
    left = left.assign(**{lcol: left[lcol].astype("datetime64[ns, UTC]")}).sort_values(lcol)
    right = right.assign(tip_utc=right.tip_utc.astype("datetime64[ns, UTC]")).sort_values(
        "tip_utc")
    return pd.merge_asof(left, right.rename(columns={"tip_utc": "_t"}), left_on=lcol,
                         right_on="_t", by=by, allow_exact_matches=False,
                         direction="backward").drop(columns="_t")


def candidates(pg: pd.DataFrame, games: pd.DataFrame, lookback: int = 10) -> pd.DataFrame:
    """Players who could have played each team-game: listed for that team in any of its
    previous `lookback` games and not listed for another team since. The outcome is
    'played', 'dnp' (listed, no minutes) or 'absent' (not listed)."""
    tg = pd.concat([games.assign(team=games.home, opp=games.away, is_home=True),
                    games.assign(team=games.away, opp=games.home, is_home=False)])
    tg = tg.sort_values(["team", "tip_utc"]).reset_index(drop=True)
    tg["team_game_no"] = tg.groupby("team").cumcount()
    listed = pg[["game_id", "player_id", "team", "tip_utc", "participation", "name", "pos"]]
    lt = listed.merge(tg[["game_id", "team", "team_game_no"]], on=["game_id", "team"])
    rows = []
    for k in range(1, lookback + 1):
        r = lt[["player_id", "team", "team_game_no", "name", "pos", "tip_utc"]].copy()
        r["team_game_no"] += k
        rows.append(r.rename(columns={"tip_utc": "seen_tip"}))
    cand = pd.concat(rows).sort_values("seen_tip").drop_duplicates(
        ["player_id", "team", "team_game_no"], keep="last")
    cand = cand.merge(tg[["game_id", "team", "opp", "is_home", "season", "season_type", "date",
                          "tip_utc", "team_game_no"]], on=["team", "team_game_no"])
    # drop players who have since been listed for a different team (trades, waivers)
    last_team = listed.sort_values("tip_utc")[["player_id", "tip_utc", "team"]].rename(
        columns={"team": "latest_team"})
    cand = _asof(cand, last_team, "player_id")
    cand = cand[cand.latest_team == cand.team].drop(columns="latest_team")
    out = cand.merge(listed[["game_id", "player_id", "participation"]],
                     on=["game_id", "player_id"], how="left")
    out["participation"] = out.participation.fillna("absent")
    # also include players listed in this game who were not candidates (debuts, returns)
    new = listed.merge(tg[["game_id", "team", "opp", "is_home", "season", "season_type",
                           "date"]], on=["game_id", "team"])
    new = new[~new.set_index(["game_id", "player_id"]).index.isin(
        out.set_index(["game_id", "player_id"]).index)]
    new = new.assign(seen_tip=pd.NaT, first_listing=True)
    out = pd.concat([out.assign(first_listing=False), new], ignore_index=True)
    return out


def assemble(cand: pd.DataFrame, ps: pd.DataFrame, ts: pd.DataFrame) -> pd.DataFrame:
    """Join point-in-time player and team states to each candidate row."""
    x = _asof(cand, ps.drop(columns=["season", "pos"]), "player_id")
    tcols = ["team", "tip_utc", "pace_ew", "lg_pace", "n_team"]
    x = _asof(x, ts[tcols], "team")
    o = ts[["team", "tip_utc", "pace_ew", *[f"def_{s}" for s in BASE_STATS],
            *[f"lg_{s}" for s in BASE_STATS], "n_team"]].rename(
        columns={"team": "opp", "pace_ew": "opp_pace", "n_team": "opp_n"})
    x = _asof(x, o, "opp")
    return x


def absence_adjust(x: pd.DataFrame, absent: np.ndarray, p_avail: np.ndarray, alpha: float,
                   gamma: float, p: Params, min_regular: float = 15.0, cap: float = 42.0,
                   shape: str = "minutes", same_pos: float = 0.0) -> pd.DataFrame:
    """Hand an absent regular's minutes and part of his usage to his teammates (nba-0.2 candidate).

    Rows of x are the candidates of each team-game. `absent`: known before tip to miss the game
    (live: listed Out; backtest: not listed or no minutes); regulars have expected minutes of at
    least `min_regular`. Teammates' recent states already include games he missed: what is still
    to hand out is his share weighted by 0.5 ** (games missed in a row / half-life), with the
    minutes and rate half-lives. A fraction `alpha` of those minutes goes to the other
    candidates in proportion to `p_avail` (chance of playing) x expected minutes (shape
    "minutes") or x min(minutes, 38 - minutes) (shape "tent": rotation players with room for
    more), times 1 + `same_pos` for his own position group, capped at `cap`; each stat's per-minute rate of every recipient is multiplied by 1 + gamma x the
    absent players' (still embedded) share of the team's expected production of that stat.
    """
    x = x.copy()
    for s in BASE_STATS:
        x[f"rmult_{s}"] = 1.0
    mu = x.min_mu.fillna(0).to_numpy(float)
    ms = x.missed_streak.fillna(0).to_numpy(float)
    reg_out = absent & (mu >= min_regular)
    if not reg_out.any() or (alpha == 0 and gamma == 0):
        return x
    w_min = np.where(reg_out, 0.5 ** (ms / p.hl_min), 0.0)
    w_rate = np.where(reg_out, 0.5 ** (ms / p.hl_rate), 0.0)
    rate = {s: (x[f"{s}_sum"].fillna(0) / x.min_sum.replace(0, np.nan)).fillna(0).to_numpy()
            for s in BASE_STATS}
    key = x.game_id.astype(str) + "|" + x.team.astype(str)
    codes, inv = np.unique(key.to_numpy(), return_inverse=True)
    G = len(codes)
    has = np.bincount(inv, weights=reg_out.astype(float), minlength=G) > 0
    rec = ~absent & has[inv]
    pa = np.clip(np.nan_to_num(p_avail), 0, 1)
    base = mu if shape == "minutes" else np.clip(np.minimum(mu, 38 - mu), 0, None)
    wt = np.where(rec, base * pa, 0.0)
    add = np.zeros(len(x))
    pos = x.pos.astype(str).to_numpy() if "pos" in x else np.full(len(x), "")
    members = pd.Series(np.arange(len(x))).groupby(inv).apply(np.asarray).to_dict()
    for j in np.flatnonzero(reg_out):  # each absent regular's minutes, to his own team-game
        idx = members[inv[j]]
        wj = wt[idx] * (1 + same_pos * (pos[idx] == pos[j]))
        if wj.sum() > 0:
            add[idx] += alpha * w_min[j] * mu[j] * wj / wj.sum()
    x["min_mu"] = np.where(rec, np.minimum(mu + add, np.maximum(cap, mu)), x.min_mu)
    x["absence_minutes_added"] = np.where(rec, x.min_mu.fillna(0) - mu, 0.0)
    for s in BASE_STATS:
        prod = rate[s] * mu
        exp_prod = np.where(reg_out, prod, np.where(absent, 0.0, prod * pa))
        team = np.bincount(inv, weights=exp_prod, minlength=G)
        lost = np.bincount(inv, weights=w_rate * prod, minlength=G)
        share = np.where(team > 0, lost / np.where(team > 0, team, 1), 0.0)
        x[f"rmult_{s}"] = np.where(rec, 1 + gamma * share[inv], 1.0)
    return x


# --------------------------------------------------------------------------- lineup-aware minutes
# n07: a player's recent-form minutes (min_mu) mix games in which teammates were missing with
# games at full strength. The adjustment compares the teammate minutes missing tonight with the
# teammate minutes missing in the games behind his min_mu (same weights), in total and at his
# own position group, and moves min_mu by a pooled regression on that difference.
LINEUP_FEATURES = ("d_tot", "d_tot_r", "d_tot_r2", "d_pos", "d_pos_r")


def season_active(x: pd.DataFrame, pg: pd.DataFrame) -> np.ndarray:
    """Candidate rows whose player has been listed for this team this season at or before the
    game (players who left in the summer stay candidates for a while but never appear)."""
    first = (pg.groupby(["player_id", "team", "season"]).tip_utc.min()
             .rename("first_listed").reset_index())
    f = x[["player_id", "team", "season", "tip_utc"]].merge(first, how="left",
                                                           on=["player_id", "team", "season"])
    return (f.first_listed.notna() & (f.first_listed <= f.tip_utc)).to_numpy()


def absent_minutes(x: pd.DataFrame, absent_w: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Expected minutes of teammates missing from each row's team-game (all, and same position
    group), excluding the row's own. absent_w in [0, 1]: chance the player is missing; rows with
    `active` False (if present) are not counted."""
    w = np.clip(np.nan_to_num(absent_w), 0, 1)
    if "active" in x:
        w = w * x.active.to_numpy(bool)
    mu = x.min_mu.fillna(0).to_numpy(float) * w
    key = (x.game_id.astype(str) + "|" + x.team.astype(str)).to_numpy()
    kp = key + "|" + x.pos.astype(str).to_numpy()
    tot = pd.Series(mu).groupby(key).transform("sum").to_numpy() - mu
    pos = pd.Series(mu).groupby(kp).transform("sum").to_numpy() - mu
    return tot, pos


def lineup_states(hist: pd.DataFrame, p: Params) -> pd.DataFrame:
    """Post-game exponentially weighted teammate-minutes-missing for each player, over the games
    he PLAYED (the same games and half-life as min_mu); hist needs f_tot and f_pos."""
    h = hist[hist.participation == "played"].sort_values(["player_id", "tip_utc"])
    h = h.assign(pt=h.player_id.astype(str) + "|" + h.team.astype(str))
    g = h.groupby("pt", sort=False)  # only his games for the same team
    out = h[["pt", "tip_utc"]].copy()
    for c in ("f_tot", "f_pos"):
        out[f"{c}_hist"] = g[c].transform(lambda v: v.ewm(halflife=p.hl_min).mean())
    return out


def lineup_features(x: pd.DataFrame, states: pd.DataFrame) -> pd.DataFrame:
    """Tonight (f_tot, f_pos on x) minus history (as of his previous game), and the regression
    features; rows without history get zeros (no adjustment)."""
    x = _asof(x.assign(pt=x.player_id.astype(str) + "|" + x.team.astype(str)), states, "pt")
    x = x.drop(columns="pt")
    r = (x.min_mu.fillna(0) / 36).to_numpy()
    d_tot = (x.f_tot - x.f_tot_hist).fillna(0).to_numpy()
    d_pos = (x.f_pos - x.f_pos_hist).fillna(0).to_numpy()
    x["d_tot"], x["d_pos"] = d_tot, d_pos
    x["d_tot_r"], x["d_tot_r2"], x["d_pos_r"] = d_tot * r, d_tot * r * r, d_pos * r
    return x


def lineup_cal_factor(cal: dict | None, n_games: np.ndarray) -> np.ndarray:
    """Calibration factor for predicted absences by team games played this season."""
    out = np.ones(len(n_games))
    for k, v in (cal or {}).items():
        lo, hi = (int(z) for z in k.split("-"))
        out[(n_games >= lo) & (n_games < hi)] = v
    return out


def lineup_shift(x: pd.DataFrame, coef: dict) -> np.ndarray:
    z = np.column_stack([x[f].to_numpy(float) for f in LINEUP_FEATURES])
    return z @ np.array([coef[f] for f in LINEUP_FEATURES])


def observed_absent(h: pd.DataFrame) -> np.ndarray:
    """Missing from a past game: any candidate who did not play (not listed, or no minutes), so
    that live editions can use 1 - P(plays), which predicts exactly that."""
    return (h.participation != "played").to_numpy(float)


def apply_lineup(x: pd.DataFrame, absent_w: np.ndarray, hist: pd.DataFrame, p: Params,
                 pg: pd.DataFrame) -> pd.DataFrame:
    """Move min_mu by the lineup adjustment (p.lineup). absent_w: chance each row of x misses
    its game (live: injury report; backtest: observed). hist: candidate rows of earlier games
    with participation and min_mu, whose absences are the observed ones. pg: box-score rows
    (who has been listed for which team this season). Keeps x's row order; the unadjusted value
    stays in min_mu_base."""
    x = x.copy()
    if not p.lineup:
        x["min_mu_base"] = x.min_mu
        return x
    x["_row"] = np.arange(len(x))
    x["active"] = season_active(x, pg)
    x["f_tot"], x["f_pos"] = absent_minutes(x, absent_w)
    h = hist[hist.min_mu.notna()].copy()
    h["active"] = season_active(h, pg)
    h["f_tot"], h["f_pos"] = absent_minutes(h, observed_absent(h))
    x = lineup_features(x, lineup_states(h, p)).sort_values("_row").drop(columns="_row")
    x = x.reset_index(drop=True)
    x["min_mu_base"] = x.min_mu
    x["min_mu"] = np.clip(x.min_mu + lineup_shift(x, p.lineup), 1, 46).where(x.min_mu.notna())
    return x


# --------------------------------------------------------------------------- distributions
def nb_pmf_matrix(mu: np.ndarray, k: float, kmax: int) -> np.ndarray:
    """NegBin pmf rows for means `mu` (shape (n,)) with dispersion k (var = mu + mu^2/k)."""
    mu = np.maximum(np.asarray(mu, float), 1e-6)[:, None]
    y = np.arange(kmax + 1)[None, :]
    lp = (gammaln(y + k) - gammaln(k) - gammaln(y + 1) + k * np.log(k / (k + mu))
          + y * np.log(mu / (k + mu)))
    pm = np.exp(lp)
    pm[:, -1] += np.clip(1 - pm.sum(1), 0, None)  # tail mass into the last bin
    return pm


MIN_BANDS = (0, 8, 14, 20, 26, 31, 36, 49)


def fit_minutes_residuals(x: pd.DataFrame) -> dict:
    """Empirical distribution of (actual - expected) minutes by expected-minutes band, from
    played games in the fitting seasons; captures short nights (blowouts, foul trouble,
    early injuries) that a symmetric curve misses."""
    d = x[(x.participation == "played") & x.min_mu.notna()]
    band = np.digitize(d.min_mu, MIN_BANDS[1:-1])
    res = np.clip(np.round(d.minutes_y - d.min_mu).astype(int), -48, 48)
    out = {}
    for b in range(len(MIN_BANDS) - 1):
        r = res[band == b]
        h = np.bincount(r + 48, minlength=97).astype(float)
        out[b] = (h / h.sum()).tolist()
    return out


def minutes_weights_empirical(mu: np.ndarray, resid: dict) -> np.ndarray:
    band = np.digitize(mu, MIN_BANDS[1:-1])
    base = np.round(mu).astype(int)
    W = np.zeros((len(mu), len(MIN_GRID)))
    for b, h in resid.items():
        idx = np.where(band == b)[0]
        if not len(idx):
            continue
        h = np.asarray(h)
        for j, mins in enumerate(MIN_GRID.astype(int)):
            off = mins - base[idx] + 48
            ok = (off >= 0) & (off <= 96)
            W[idx[ok], j] = h[off[ok]]
        # mass below 1 minute or above 48 folds into the end bins
        lo_off = 1 - base[idx] + 48
        W[idx, 0] += np.array([h[:max(o, 0)].sum() for o in lo_off])
        hi_off = 48 - base[idx] + 48
        W[idx, -1] += np.array([h[min(o + 1, 97):].sum() for o in hi_off])
    W /= W.sum(1, keepdims=True)
    return W


def minutes_weights(mu: np.ndarray, sd: np.ndarray) -> np.ndarray:
    """Discretised truncated-normal weights over MIN_GRID (rows sum to 1)."""
    z = (MIN_GRID[None, :] - mu[:, None]) / sd[:, None]
    w = np.exp(-0.5 * z ** 2)
    w /= w.sum(1, keepdims=True)
    return w


def n1_pmfs(x: pd.DataFrame, p: Params, lg: dict,
            only: tuple[str, ...] | None = None) -> dict[str, np.ndarray]:
    """Exact N1 pmfs for each row of x (conditional on playing)."""
    only = only or MARKETS
    n = len(x)
    mu_m = x.min_mu.fillna(12.0).to_numpy()
    sd_m = np.maximum(x.min_sd.fillna(8.0).to_numpy() * p.min_sd_scale, p.min_sd_floor)
    if p.min_resid:
        W = minutes_weights_empirical(np.clip(mu_m, 1, 48), p.min_resid)
    else:
        W = minutes_weights(np.clip(mu_m, 1, 48), sd_m)  # (n, G)
    mult = np.ones(n)
    if p.use_pace:
        # expected game pace = team * opponent / league (all as of the game); the player's
        # rate already reflects his own team's pace, so only the opponent's share moves it
        mult = (x.opp_pace / x.lg_pace).fillna(1.0).to_numpy()
    pm = {}
    for s in [q for q in BASE_STATS if q in only]:
        prior = x.pos.map(lambda q, s=s: lg["pos_rate"].get(q, lg["pos_rate"]["F"])[s]).to_numpy()
        rate = ((x[f"{s}_sum"].fillna(0) + p.k_rate * prior) /
                (x.min_sum.fillna(0) + p.k_rate)).to_numpy()
        m = mult.copy()
        if p.use_opp:
            raw = (x[f"def_{s}"] / x[f"lg_{s}"]).fillna(1.0).to_numpy()
            nn = x.opp_n.fillna(0).to_numpy()
            m = m * (1 + (raw - 1) * nn / (nn + p.k_def))
        if f"rmult_{s}" in x:  # absent-teammate usage (absence_adjust)
            m = m * x[f"rmult_{s}"].fillna(1.0).to_numpy()
        K = MAX_K[s]
        mix = np.zeros((n, K + 1))
        for j, mins in enumerate(MIN_GRID):
            if not W[:, j].any():
                continue
            mix += W[:, j:j + 1] * nb_pmf_matrix(rate * mins * m, p.disp[s], K)
        pm[s] = mix
    if "pra" not in only:
        return pm
    # PRA: convolve conditional pmfs at each minutes value, then mix
    K = MAX_K["pra"]
    pra = np.zeros((n, K + 1))
    rates = {}
    for s in ("pts", "reb", "ast"):
        prior = x.pos.map(lambda q, s=s: lg["pos_rate"].get(q, lg["pos_rate"]["F"])[s]).to_numpy()
        r = ((x[f"{s}_sum"].fillna(0) + p.k_rate * prior) / (x.min_sum.fillna(0) + p.k_rate))
        m = mult.copy()
        if p.use_opp:
            raw = (x[f"def_{s}"] / x[f"lg_{s}"]).fillna(1.0).to_numpy()
            nn = x.opp_n.fillna(0).to_numpy()
            m = m * (1 + (raw - 1) * nn / (nn + p.k_def))
        if f"rmult_{s}" in x:
            m = m * x[f"rmult_{s}"].fillna(1.0).to_numpy()
        rates[s] = r.to_numpy() * m
    for j, mins in enumerate(MIN_GRID[::2]):  # every other minute keeps this tractable
        jj = 2 * j
        w = W[:, jj] + (W[:, jj + 1] if jj + 1 < W.shape[1] else 0)
        if not w.any():
            continue
        a = nb_pmf_matrix(rates["pts"] * (mins + 0.5), p.disp["pts"], MAX_K["pts"])
        b = nb_pmf_matrix(rates["reb"] * (mins + 0.5), p.disp["reb"], MAX_K["reb"])
        c = nb_pmf_matrix(rates["ast"] * (mins + 0.5), p.disp["ast"], MAX_K["ast"])
        ab = _conv_rows(a, b)
        abc = _conv_rows(ab, c)[:, :K + 1]
        pra += w[:, None] * abc
    pm["pra"] = pra / pra.sum(1, keepdims=True)
    return pm


def _conv_rows(a: np.ndarray, b: np.ndarray) -> np.ndarray:
    n, la = a.shape
    lb = b.shape[1]
    out = np.zeros((n, la + lb - 1))
    for j in range(lb):
        out[:, j:j + la] += a * b[:, j:j + 1]
    return out


def baseline_pmfs(x: pd.DataFrame, p: Params, kind: str,
                  only: tuple[str, ...] | None = None) -> dict[str, np.ndarray]:
    col = "gm" if kind == "A" else "l10"
    return {s: nb_pmf_matrix(x[f"{s}_{col}"].fillna(0).to_numpy(), p.disp_a[s], MAX_K[s])
            for s in (only or MARKETS)}


def crps_rows(pm: np.ndarray, y: np.ndarray) -> np.ndarray:
    """Vectorised discrete CRPS: sum_k (F(k) - 1{y <= k})^2 over the support 0..K (outcomes
    above K are clipped into the last bin, which only happens for extreme games)."""
    F = np.cumsum(pm, axis=1)
    k = np.arange(pm.shape[1])[None, :]
    H = (k >= np.clip(y, 0, pm.shape[1] - 1)[:, None]).astype(float)
    extra = np.clip(y - (pm.shape[1] - 1), 0, None)  # mass beyond the grid: F = 1, H = 0
    return ((F - H) ** 2).sum(1) + extra


def pit_rows(pm: np.ndarray, y: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    F = np.cumsum(pm, axis=1)
    yi = np.clip(y.astype(int), 0, pm.shape[1] - 1)
    hi = F[np.arange(len(y)), yi]
    lo = np.where(yi > 0, F[np.arange(len(y)), np.maximum(yi - 1, 0)], 0.0)
    return lo + rng.random(len(y)) * (hi - lo)


def actual(x: pd.DataFrame, s: str) -> np.ndarray:
    if s == "pra":
        return (x.pts_y + x.reb_y + x.ast_y).to_numpy()
    return x[f"{s}_y"].to_numpy()


# --------------------------------------------------------------------------- availability
AVAIL_FEATURES = ("played_last", "missed_streak", "frac_played_10", "min_mu", "starter_ew",
                  "first_listing", "no_history", "dnp_last", "post")


def availability_features(x: pd.DataFrame, pg: pd.DataFrame, games: pd.DataFrame) -> pd.DataFrame:
    """Recent-appearance features for each candidate row (strictly before its game)."""
    tg = pd.concat([games.assign(team=games.home), games.assign(team=games.away)])
    tg = tg.sort_values(["team", "tip_utc"])
    tg["tgn"] = tg.groupby("team").cumcount()
    tgn = tg.set_index(["game_id", "team"]).tgn
    lst = pg[["game_id", "team", "player_id", "participation"]].copy()
    lst["tgn"] = [tgn.get((g, t), -1) for g, t in zip(lst.game_id, lst.team, strict=True)]
    x = x.copy()
    x["tgn"] = [tgn.get((g, t), -1) for g, t in zip(x.game_id, x.team, strict=True)]
    pl = lst[lst.participation == "played"]
    played = set(zip(pl.player_id, pl.team, pl.tgn, strict=True))
    dnp = set(zip(lst.player_id, lst.team, lst.tgn, strict=True)) - played
    keys = list(zip(x.player_id, x.team, x.tgn, strict=True))
    x["played_last"] = [(p, t, n - 1) in played for p, t, n in keys]
    x["dnp_last"] = [(p, t, n - 1) in dnp for p, t, n in keys]
    x["frac_played_10"] = [sum((p, t, n - k) in played for k in range(1, 11)) / 10
                           for p, t, n in keys]
    streak = []
    for p, t, n in keys:
        k = 0
        while k < 15 and (p, t, n - 1 - k) not in played and n - 1 - k >= 0:
            k += 1
        streak.append(k)
    x["missed_streak"] = streak
    x["no_history"] = x.n_played.isna()
    x["post"] = x.season_type.ne("REG")
    return x


def avail_matrix(x: pd.DataFrame) -> np.ndarray:
    if "min_mu_base" in x:  # P(plays) was fitted on recent-form minutes, before any lineup shift
        x = x.assign(min_mu=x.min_mu_base)
    z = x[list(AVAIL_FEATURES)].astype(float).fillna(0.0).to_numpy()
    ms = np.minimum(x.missed_streak.to_numpy(float), 15)
    return np.column_stack([z, (ms == 0), (ms == 1), (ms >= 2) & (ms <= 4), ms >= 5,
                            x.min_mu.fillna(0).to_numpy() ** 2 / 100])


# --------------------------------------------------------------------------- frozen config
def frozen_path():
    from ..config import REPORTS_DIR
    return REPORTS_DIR / "nba" / "model" / "n1_params.json"


def save_params(p: Params) -> None:
    f = frozen_path()
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(json.dumps({"model_version": MODEL_VERSION, **asdict(p)}, indent=1,
                            default=float) + "\n")


def load_params(version: str | None = None) -> Params:
    """The frozen parameters (current version), or an earlier version's kept copy."""
    f = frozen_path() if version is None else frozen_path().with_name(f"n1_params_{version}.json")
    d = json.loads(f.read_text())
    d.pop("model_version", None)
    if d.get("min_resid"):
        d["min_resid"] = {int(k): v for k, v in d["min_resid"].items()}
    return Params(**d)
